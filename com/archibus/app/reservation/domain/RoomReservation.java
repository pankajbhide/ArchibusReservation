package com.archibus.app.reservation.domain;

import java.text.SimpleDateFormat;
import java.util.*;

import javax.xml.bind.annotation.*;

import com.archibus.app.reservation.util.*;

/**
 * Domain class for Room Reservation.
 *
 * @author Bart Vanderschoot
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "RoomReservation")
public class RoomReservation extends AbstractReservation {

    /** Date and time format used for serializing. */
    private static final String DATETIME_FORMAT_STRING = "yyyy-MM-dd'T'HH:mm";

    /**
     * Room allocations for this room. Normally there will be one room allocation for a room
     * reservation.
     */
    protected List<RoomAllocation> roomAllocations;

    /** Contains all occurrences when a recurring reservation is created via Web Central. */
    protected List<RoomReservation> createdReservations;

    /**
     * The time zone requested by the user, to be used for creating the meeting in Exchange. It can
     * be different from the local time zone for conference call reservations.
     */
    private String requestedTimeZone;

    /**
     * Original date used to determine the occurrence index for reservations created via Outlook
     * Plugin.
     */
    private Date originalDate;

    /** Reservation identifiers of all reservations with the same conference id. */
    private Integer[] reservationIdsInConference;
    
    /**
     * Flag that indicates a room conflict for a reservation in the same conference call on the same
     * date as this reservation.
     */
    private boolean roomConflictInConferenceCall;

    /** Contains the reservation ID as a clickable link. Used in the Outlook Plugin. */
    private ReservationMessage reservationLink;

    /**
     * Default constructor.
     */
    public RoomReservation() {
        super();
    }

    /**
     * Constructor for a time period.
     *
     * @param timePeriod the time period
     */
    public RoomReservation(final TimePeriod timePeriod) {
        super();
        setTimePeriod(timePeriod);
    }

    /**
     * Constructor using parameters.
     *
     * @param timePeriod the time period
     * @param blId building id
     * @param flId floor id
     * @param rmId room id
     * @param configId configuration id
     * @param arrangeTypeId arrange type id
     */
    public RoomReservation(final TimePeriod timePeriod, final String blId, final String flId,
            final String rmId, final String configId, final String arrangeTypeId) {
        this(timePeriod);
        final RoomAllocation roomAllocation =
                new RoomAllocation(blId, flId, rmId, configId, arrangeTypeId, this);
        addRoomAllocation(roomAllocation);
    }

    /**
     * Constructor using primary key.
     *
     * @param reserveId id
     */
    public RoomReservation(final Integer reserveId) {
        super(reserveId);
    }

    /**
     * Constructor using objects.
     *
     * @param timePeriod time period
     * @param roomArrangement room arrangement
     */
    public RoomReservation(final TimePeriod timePeriod, final RoomArrangement roomArrangement) {
        super();
        setTimePeriod(timePeriod);
        final RoomAllocation roomAllocation = new RoomAllocation(roomArrangement, this);
        addRoomAllocation(roomAllocation);
    }

    /**
     * Add a room allocation to the reservation.
     *
     * @param roomAllocation room allocation
     */
    public final void addRoomAllocation(final RoomAllocation roomAllocation) {
        if (this.roomAllocations == null) {
            this.roomAllocations = new ArrayList<RoomAllocation>();
        }
        this.roomAllocations.add(roomAllocation);

        // Copy the time period and id of the reservation to the allocation,
        // so no need to modify the date of the time values here.
        roomAllocation.setReservation(this);
    }

    /**
     * Get room allocations.
     *
     * @return room allocations
     */
    public final List<RoomAllocation> getRoomAllocations() {
        if (this.roomAllocations == null) {
            this.roomAllocations = new ArrayList<RoomAllocation>();
        }
        return this.roomAllocations;
    }

    /**
     * Set room allocations.
     *
     * @param roomAllocations room allocations
     */
    public final void setRoomAllocations(final List<RoomAllocation> roomAllocations) {
        this.roomAllocations = roomAllocations;
    }

    /**
     * Calculate total cost for the room reservation.
     *
     * @return total cost
     */
    @Override
    public double calculateTotalCost() {
        // calculate resource costs.
        double totalCost = 0.0;
        for (final ResourceAllocation resourceAllocation : this.getResourceAllocations()) {
            totalCost += resourceAllocation.getCost();
        }

        // add room cost.
        for (final RoomAllocation roomAllocation : this.getRoomAllocations()) {
            totalCost += roomAllocation.getCost();
        }
        // Round the result to two decimals.
        totalCost = DataSourceUtils.round2(totalCost);
        this.setCost(totalCost);

        return totalCost;
    }

    /**
     * Set the created / linked room reservations.
     *
     * @param createdReservations the created room reservations to set
     */
    public void setCreatedReservations(final List<RoomReservation> createdReservations) {
        this.createdReservations = createdReservations;
    }

    /**
     * Get the created / linked reservations. After creating a new recurring reservation this list
     * contains all reservation occurrences just created for a recurring reservation. When editing a
     * recurring conference call, this list can refer to the other reservations that exist for this
     * conference call occurrence.
     *
     * @return the list of created / linked reservations
     */
    @XmlTransient
    public List<RoomReservation> getCreatedReservations() {
        return this.createdReservations;
    }

    /**
     * Get the reservation IDs of all created reservations.
     *
     * @param timeZone the time zone id
     * @return map of reservation IDs by their start date/time in the requested time zone
     */
    @XmlTransient
    public Map<Date, Integer> getCreatedReservationIds(final String timeZone) {
        final Map<Date, Integer> reservationIds = new HashMap<Date, Integer>();

        if (this.createdReservations == null) {
            Integer reservationId = this.getConferenceId();
            if (reservationId == null) {
                reservationId = this.getReserveId();
            }
            // only add the ID of the main reservation
            reservationIds.put(ReservationUtils.getTimePeriodInTimeZone(this, timeZone)
                .getStartDate(), reservationId);
        } else {
            // add the IDs of all created reservations
            for (final RoomReservation createdReservation : this.createdReservations) {
                Integer reservationId = createdReservation.getConferenceId();
                if (reservationId == null) {
                    reservationId = createdReservation.getReserveId();
                }
                reservationIds.put(
                    ReservationUtils.getTimePeriodInTimeZone(createdReservation, timeZone)
                        .getStartDate(), reservationId);
            }
        }
        return reservationIds;
    }

    /**
     * Set the array of reservation id's that are part of the same conference call.
     *
     * @param reservationIdsInConference the reservation id's
     */
    public void setReservationIdsInConference(final Integer[] reservationIdsInConference) {
        if (reservationIdsInConference != null) {
            this.reservationIdsInConference = reservationIdsInConference.clone();
        }
    }

    /**
     * Get an array of reservation id's that are part of the same conference call. Returns the
     * current reservation's identifier if no other conference call id's are set.
     *
     * @return reservation id's
     */
    @XmlTransient
    public Integer[] getReservationIdsInConference() {
        Integer[] reservationIds = this.reservationIdsInConference;
        if (reservationIds == null && this.getReserveId() != null) {
            reservationIds = new Integer[] { this.getReserveId() };
        }
        return reservationIds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String determineBuildingId() {
        // If a room allocation is present, return that building id.
        String buildingId = null;
        if (this.getRoomAllocations().isEmpty()) {
            buildingId = super.determineBuildingId();
        } else {
            buildingId = this.getRoomAllocations().get(0).getBlId();
        }
        return buildingId;
    }

    /**
     * Get the requested time zone.
     *
     * @return the requested time zone
     */
    @XmlTransient
    public String getRequestedTimeZone() {
        return this.requestedTimeZone;
    }
    
    /**
     * Set the flag that indicates a room conflict in this reservation's conference call.
     * 
     * @param roomConflictInConferenceCall true to indicate a room conflict
     */
    public void setRoomConflictInConferenceCall(final boolean roomConflictInConferenceCall) {
        this.roomConflictInConferenceCall = roomConflictInConferenceCall;
    }
    
    /**
     * Verify whether this reservation (or a sibling in the same conference call) has a room
     * conflict.
     * 
     * @return true if a room conflict exists, false if all rooms are booked correctly
     */
    public boolean hasRoomConflictInConferenceCall() {
        return this.roomConflictInConferenceCall;
    }

    /**
     * Set the requested time zone.
     *
     * @param requestedTimeZone the requested time zone
     */
    public void setRequestedTimeZone(final String requestedTimeZone) {
        this.requestedTimeZone = requestedTimeZone;
    }

    /**
     * Get the original date.
     *
     * @return the originalDate
     */
    public Date getOriginalDate() {
        return this.originalDate;
    }

    /**
     * Set the original date.
     *
     * @param originalDate the originalDate to set
     */
    public void setOriginalDate(final Date originalDate) {
        this.originalDate = originalDate;
    }

    /**
     * Get the reservation link for this reservation.
     *
     * @return the reservation link
     */
    public ReservationMessage getReservationLink() {
        return this.reservationLink;
    }

    /**
     * Set the reservation link for this reservation.
     *
     * @param reservationLink the reservation link to set
     */
    public void setReservationLink(final ReservationMessage reservationLink) {
        this.reservationLink = reservationLink;
    }

    /**
     * Get the serialized start date/time for this reservation.
     *
     * @return start in serialized form
     */
    public String getSerializedStartDateTime() {
        return serializeDateTime(this.getStartDateTime());
    }

    /**
     * Get the serialized end date/time for this reservation.
     *
     * @return end in serialized form
     */
    public String getSerializedEndDateTime() {
        return serializeDateTime(this.getEndDateTime());
    }
    
    /**
     * Serialize a date/time to a string.
     *
     * @param dateTime the date/time to serialize
     * @return the serialized form, or null if the date is null
     */
    private String serializeDateTime(final Date dateTime) {
        String serializedForm = null;
        if (dateTime != null) {
            final SimpleDateFormat formatter =
                    new SimpleDateFormat(DATETIME_FORMAT_STRING, Locale.ENGLISH);
            serializedForm = formatter.format(dateTime);
        }
        return serializedForm;
    }

}
