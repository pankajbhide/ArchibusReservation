package com.archibus.app.reservation.service.actions;

import java.sql.Time;
import java.util.*;

import org.apache.log4j.Logger;

import com.archibus.app.reservation.dao.*;
import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.AbstractIntervalPattern;
import com.archibus.app.reservation.service.helpers.ReservationServiceHelper;
import com.archibus.app.reservation.util.TimeZoneConverter;

/**
 * Provides a method for saving an occurrence of a recurring reservation.
 * <p>
 *
 * Used by ReservationService to save all occurrences of a recurring reservation via the Recurrence
 * Pattern definition.
 *
 * @author Yorik Gerlo
 * @since 20.1
 *
 */
public class SaveRecurringReservationOccurrenceAction
        implements AbstractIntervalPattern.ModifiedOccurrenceAction {

    /** The logger. */
    protected final Logger logger = Logger.getLogger(this.getClass());

    /**
     * The duration in minutes of the reservations.
     */
    private final int durationInMinutes;

    /**
     * The room arrangement data source, for checking for available rooms.
     */
    private final IRoomArrangementDataSource roomArrangementDataSource;

    /**
     * The list of saved reservations: all saved instances are added to this list.
     */
    private final List<RoomReservation> savedReservations;

    /**
     * The room reservation data source: used for saving the reservations.
     */
    private final IRoomReservationDataSource roomReservationDataSource;

    /**
     * The room allocation of the first occurrence.
     */
    private final RoomAllocation roomAllocation;

    /**
     * The start time of the reservations.
     */
    private final Time startTime;

    /**
     * The reservation object to modify for each occurrence.
     */
    private final RoomReservation reservation;

    /** The local time zone id. */
    private final String localTimeZone;

    /** Occurrence index of the current occurrence (to be incremented). */
    private int occurrenceIndex;

    /**
     * Constructor.
     *
     * @param savedReservations list to store the saved reservations
     * @param roomReservationDataSource data source to use for saving reservations
     * @param roomArrangementDataSource data source used for checking room availability
     * @param firstReservation the reservation for the first occurrence, already booked
     */
    public SaveRecurringReservationOccurrenceAction(final List<RoomReservation> savedReservations,
            final IRoomReservationDataSource roomReservationDataSource,
            final IRoomArrangementDataSource roomArrangementDataSource,
            final RoomReservation firstReservation) {
        this.roomArrangementDataSource = roomArrangementDataSource;
        this.savedReservations = savedReservations;
        this.roomReservationDataSource = roomReservationDataSource;
        this.reservation = firstReservation;

        this.startTime = TimePeriod.clearDate(firstReservation.getStartTime());
        this.durationInMinutes = (int) (firstReservation.getEndDateTime().getTime()
                - firstReservation.getStartDateTime().getTime()) / TimePeriod.MINUTE_MILLISECONDS;

        // we assume having one room
        this.roomAllocation = this.reservation.getRoomAllocations().get(0);
        this.localTimeZone =
                TimeZoneConverter.getTimeZoneIdForBuilding(this.roomAllocation.getBlId());

        // start from the occurrence index of the first reservation
        this.occurrenceIndex = this.reservation.getOccurrenceIndex();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean handleOccurrence(final Date date, final TimePeriod timePeriod)
            throws ReservationException {
        /*
         * Temporarily set the custom time period in the reservation. Reset to the default time
         * period afterwards.
         */
        final TimePeriod originalTimePeriod = new TimePeriod(this.reservation.getTimePeriod());
        try {
            final RoomReservation occurrence =
                    handleActualOccurrence(timePeriod, ++this.occurrenceIndex);
            this.savedReservations.add(occurrence);
        } finally {
            this.reservation.setTimePeriod(originalTimePeriod);
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean handleCancelledOccurrence(final Date date) throws ReservationException {
        ++this.occurrenceIndex;
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean handleOccurrence(final Date date) throws ReservationException {
        final TimePeriod timePeriod = new TimePeriod(date, this.startTime, this.durationInMinutes,
            this.reservation.getTimeZone());
        return handleOccurrence(date, timePeriod);
    }

    /**
     * Get the local Time Zone.
     *
     * @return the local Time Zone identified
     */
    public String getLocalTimeZone() {
        return this.localTimeZone;
    }

    /**
     * Actually handle an occurrence, after setting the correct time period.
     *
     * @param timePeriod the time period for this occurrence
     * @param currentIndex the occurrence index for this occurrence
     * @return true to continue processing, false to stop after this occurrence
     */
    protected RoomReservation handleActualOccurrence(final TimePeriod timePeriod,
            final int currentIndex) {

        // only to be used for new reservations
        final RoomReservation recurringReservation = new RoomReservation();

        this.reservation.copyTo(recurringReservation, true);
        recurringReservation.setTimePeriod(timePeriod);
        TimeZoneConverter.convertToLocalTime(recurringReservation, this.localTimeZone);
        // increment and assign the occurrence index
        recurringReservation.setOccurrenceIndex(currentIndex);

        if (this.isRoomAvailable(recurringReservation)) {
            final RoomAllocation recurringRoomAllocation = new RoomAllocation();
            this.roomAllocation.copyTo(recurringRoomAllocation);
            // addRoomAllocation also links the allocation to the reservation
            recurringReservation.addRoomAllocation(recurringRoomAllocation);
            ReservationServiceHelper.copyResourceAllocations(this.reservation,
                recurringReservation);
        } else {
            this.logger.debug("The room is not available on " + timePeriod.getStartDate());
            recurringReservation.setStatus(Constants.STATUS_ROOM_CONFLICT);
            recurringReservation.setBackupBuildingId(this.roomAllocation.getBlId());
        }

        // save all
        this.roomReservationDataSource.save(recurringReservation);

        // set the local time zone again so it doesn't have to be looked up
        recurringReservation.setTimeZone(this.localTimeZone);
        return recurringReservation;
    }

    /**
     * Get the master room allocation indicating which room should be booked for each occurrence.
     *
     * @return the room allocation
     */
    protected final RoomAllocation getRoomAllocation() {
        return this.roomAllocation;
    }

    /**
     * Get the master reservation for the recurrence.
     *
     * @return the master reservation
     */
    protected final RoomReservation getReservation() {
        return this.reservation;
    }

    /**
     * Check whether the master room allocation is available for the given time period.
     *
     * @param occurrence the reservation containing time period and reservation id to check
     * @return true if the room is available, false otherwise
     */
    protected boolean isRoomAvailable(final RoomReservation occurrence) {
        // Create the corresponding domain objects for the query.
        final RoomArrangement roomArrangement = this.roomAllocation.getRoomArrangement();
        final RoomReservation reservationForCheck =
                new RoomReservation(occurrence.getTimePeriod(), roomArrangement);
        reservationForCheck.setReserveId(occurrence.getReserveId());

        return !this.roomArrangementDataSource
            .findAvailableRooms(reservationForCheck, null, false, null, false, false).isEmpty();
    }

    /**
     * Get the room reservation data source used for this action.
     *
     * @return the room reservation data source
     */
    protected IRoomReservationDataSource getReservationDataSource() {
        return this.roomReservationDataSource;
    }

}
