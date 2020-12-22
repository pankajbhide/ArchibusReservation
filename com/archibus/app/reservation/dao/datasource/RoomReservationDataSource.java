package com.archibus.app.reservation.dao.datasource;

import java.util.*;

import com.archibus.app.common.util.SchemaUtils;
import com.archibus.app.reservation.dao.*;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.util.*;
import com.archibus.context.*;
import com.archibus.datasource.data.DataRecord;
import com.archibus.model.view.datasource.ClauseDef.Operation;
import com.archibus.model.view.datasource.ParsedRestrictionDef;
import com.archibus.utility.*;

/**
 * The Class RoomReservationDataSource.
 *
 * @author Bart Vanderschoot
 */
public class RoomReservationDataSource extends AbstractReservationDataSource<RoomReservation>
        implements IRoomReservationDataSource {

    /** Error message indicating a specific resource is not available for the reservation. */
    // @translatable
    private static final String RESOURCE_NOT_AVAILABLE =
            "The resource {0} is not available for the reservation in {1}-{2}-{3}";

    /** The room allocation data source. */
    protected IRoomAllocationDataSource roomAllocationDataSource;

    /** The room arrangement data source. */
    protected IRoomArrangementDataSource roomArrangementDataSource;

    /**
     * Instantiates a new room reservation data source.
     */
    public RoomReservationDataSource() {
        super("roomReservation", "reserve");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoomReservation convertRecordToObject(final DataRecord reservationRecord,
            final DataRecord roomAllocationRecord,
            final List<DataRecord> resourceAllocationRecords) {
        final RoomReservation roomReservation = this.convertRecordToObject(reservationRecord);

        if (resourceAllocationRecords != null) {
            for (final DataRecord resourceAllocationRecord : resourceAllocationRecords) {
                final ResourceAllocation resourceAllocation = this.resourceAllocationDataSource
                    .convertRecordToObject(resourceAllocationRecord);
                roomReservation.addResourceAllocation(resourceAllocation);
            }
        }

        // expecting only one room
        final RoomAllocation roomAllocation =
                this.roomAllocationDataSource.convertRecordToObject(roomAllocationRecord);
        roomReservation.addRoomAllocation(roomAllocation);
        return roomReservation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoomReservation get(final Object reserveId) {
        final RoomReservation reservation = super.get(reserveId);
        if (reservation != null) {
            this.roomAllocationDataSource.addRoomAllocations(reservation);
        }
        return reservation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoomReservation getActiveReservation(final Object reserveId) {
        final RoomReservation reservation = super.getActiveReservation(reserveId);
        if (reservation != null) {
            this.roomAllocationDataSource.addRoomAllocations(reservation);
        }

        return reservation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<RoomReservation> getByUniqueId(final String uniqueId, final Integer conferenceId,
            final Integer occurrenceIndex) throws ReservationException {
        this.clearRestrictions();
        final ParsedRestrictionDef restriction = new ParsedRestrictionDef();
        restriction.addClause(this.tableName, Constants.UNIQUE_ID, uniqueId, Operation.EQUALS);
        if (conferenceId != null) {
            restriction.addClause(this.tableName, Constants.RES_CONFERENCE, conferenceId,
                Operation.EQUALS);
        }
        if (occurrenceIndex != null && SchemaUtils.fieldExistsInSchema(this.tableName,
            Constants.OCCURRENCE_INDEX_FIELD)) {
            restriction.addClause(this.tableName, Constants.OCCURRENCE_INDEX_FIELD, occurrenceIndex,
                Operation.EQUALS);
        }
        final List<RoomReservation> reservations = this.getActiveReservations(restriction, true);
        return this.roomAllocationDataSource.addRoomAllocations(reservations);
    }

    /**
     * Clear the unique id.
     *
     * @param reservation reservation
     * @return reservation
     * @throws ReservationException ReservationException
     */
    @Override
    public final RoomReservation clearUniqueId(final RoomReservation reservation)
            throws ReservationException {
        // Get the unmodified reservation, so we do not change anything else (KB 3037586).
        final RoomReservation storedReservation = this.get(reservation.getReserveId());

        if (storedReservation.getUniqueId() != null) {
            final User user = ContextStore.get().getUser();
            // this will insert a NULL value
            storedReservation.setUniqueId("");
            storedReservation.setLastModifiedBy(user.getEmployee().getId());
            // TODO: current date or use timezone ?
            storedReservation.setLastModifiedDate(Utility.currentDate());
            // every reservation can be casted to AbstractReservation
            super.update(storedReservation);
        }
        return storedReservation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void checkRecurringDateModified(final RoomReservation reservation) {
        // if a single occurrence is updated and the date is changed set the
        // reserve.recurring_date_modified to 1
        if (reservation.getReserveId() != null && reservation.getRecurringRule() != null
                && reservation.getParentId() != null) {
            final RoomReservation originalReservation = this.get(reservation.getReserveId());
            if (!originalReservation.getStartDate().equals(reservation.getStartDate())) {
                // Log.debug("Reservation occurrence date changed from " +
                // originalReservation.getStartDate() + " to " + roomReservation.getStartDate());
                reservation.setRecurringDateModified(1);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<RoomReservation> getByParentId(final Integer parentId, final Date startDate,
            final Date endDate, final boolean includeConflicted) {
        List<RoomReservation> result = null;

        if (parentId != null) {
            final List<RoomReservation> reservations = super.getActiveReservationsByParentId(
                parentId, startDate, endDate, includeConflicted);
            result = this.roomAllocationDataSource.addRoomAllocations(reservations);

        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoomReservation save(final RoomReservation roomReservation) throws ReservationException {
        // determine the building id for the time zone conversion
        final String buildingId = roomReservation.determineBuildingId();

        // check time zone, if not provided, we assume the requestor is making the
        // reservation in local building time.
        if (roomReservation.getTimeZone() != null && StringUtil.notNullOrEmpty(buildingId)) {
            final String localTimeZone = TimeZoneConverter.getTimeZoneIdForBuilding(buildingId);
            TimeZoneConverter.convertToTimeZone(roomReservation, localTimeZone);

            // we assume having only one room allocation
            for (final RoomAllocation roomAllocation : roomReservation.getRoomAllocations()) {
                roomAllocation.setStartDateTime(roomReservation.getStartDateTime());
                roomAllocation.setEndDateTime(roomReservation.getEndDateTime());
            }
        }

        // we assume having only one room allocation
        for (final RoomAllocation roomAllocation : roomReservation.getRoomAllocations()) {
            // also loop through all resource allocations to set the room reference
            // and to verify that the resource is available for that room at that time
            if (roomReservation.getResourceAllocations() != null) {
                moveResourceAllocations(roomReservation, roomAllocation);
            }
        }

        // check for status
        checkApprovalRequired(roomReservation);

        // check for type
        if (StringUtil.isNullOrEmpty(roomReservation.getRecurringRule())) {
            if (TimePeriod.clearTime(roomReservation.getStartDate())
                .equals(TimePeriod.clearTime(roomReservation.getEndDate()))) {
                roomReservation.setReservationType(Constants.TYPE_REGULAR);
            } else {
                roomReservation.setReservationType(Constants.TYPE_CONTINUOUS);
            }
        }

        // calculate costs for all allocations and total before saving.
        calculateCosts(roomReservation);

        // save reservation and resources
        super.checkAndSave(roomReservation);

        if (!roomReservation.getRoomAllocations().isEmpty()) {
            saveRoomAllocations(roomReservation);
        }

        return roomReservation;
    }

    /**
     * {@inheritDoc}
     */
    public double calculateCosts(final RoomReservation roomReservation) {
        for (final RoomAllocation allocation : roomReservation.getRoomAllocations()) {
            this.roomAllocationDataSource.calculateCost(allocation);
        }

        final List<ResourceAllocation> activeResourceAllocations =
                ReservationUtils.getActiveResourceAllocations(roomReservation);

        for (final ResourceAllocation allocation : activeResourceAllocations) {
            this.resourceAllocationDataSource.calculateCost(allocation);
        }

        roomReservation.calculateTotalCost();

        return roomReservation.getCost();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void canBeCancelledByCurrentUser(final RoomReservation roomReservation)
            throws ReservationException {
        final User user = ContextStore.get().getUser();
        if (!user.isMemberOfGroup(Constants.RESERVATION_SERVICE_DESK)
                && !user.isMemberOfGroup(Constants.RESERVATION_MANAGER)) {
            checkCancelling(roomReservation);

            // Get the active resource allocations and check whether they can be cancelled.
            final List<ResourceAllocation> activeResourceAllocations =
                    ReservationUtils.getActiveResourceAllocations(roomReservation);
            for (final ResourceAllocation resourceAllocation : activeResourceAllocations) {
                this.resourceAllocationDataSource.checkCancelling(resourceAllocation);
            }

            // Check whether the connected room allocation can be cancelled.
            for (final RoomAllocation roomAllocation : roomReservation.getRoomAllocations()) {
                this.roomAllocationDataSource.checkCancelling(roomAllocation);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancel(final RoomReservation roomReservation, final String comments)
            throws ReservationException {
        // Get the unmodified reservation, so we do not change anything else (KB 3037585).
        final RoomReservation unmodifiedReservation = this.get(roomReservation.getReserveId());

        // First cancel the room allocation, this updates its cost as well.
        for (final RoomAllocation roomAllocation : unmodifiedReservation.getRoomAllocations()) {
            this.roomAllocationDataSource.cancel(roomAllocation);
        }
        // Then call the super method.
        super.cancel(unmodifiedReservation, comments);
    }

    /**
     * {@inheritDoc}
     */
    public final void setRoomAllocationDataSource(
            final RoomAllocationDataSource roomAllocationDataSource) {
        this.roomAllocationDataSource = roomAllocationDataSource;
    }

    /**
     * {@inheritDoc}
     */
    public final void setRoomArrangementDataSource(
            final IRoomArrangementDataSource roomArrangementDataSource) {
        this.roomArrangementDataSource = roomArrangementDataSource;
    }

    /**
     * Save a reservation's room allocations.
     *
     * @param reservation the reservation
     * @throws ReservationException when the save failed
     */
    private void saveRoomAllocations(final RoomReservation reservation)
            throws ReservationException {
        final String attendees = reservation.getAttendees();
        int internalGuests = 0;
        int externalGuests = 0;
        if (attendees != null) {
            final String[] attendeeArr = attendees.split(";");
            for (final String attendeeEmail : attendeeArr) {
                // check for a valid email
                if (StringUtil.isNullOrEmpty(attendeeEmail)) {
                    continue;
                }
                if (DataSourceUtils.isEmployeeEmail(attendeeEmail)) {
                    ++internalGuests;
                } else {
                    ++externalGuests;
                }
            }
        }
        for (final RoomAllocation roomAllocation : reservation.getRoomAllocations()) {
            roomAllocation.setReservation(reservation);
            roomAllocation.setInternalGuests(internalGuests);
            roomAllocation.setExternalGuests(externalGuests);

            if (roomAllocation.getId() == null || roomAllocation.getId() == 0) {
                final RoomAllocation savedAllocation =
                        this.roomAllocationDataSource.save(roomAllocation);
                roomAllocation.setId(savedAllocation.getId());
            } else {
                this.roomAllocationDataSource.checkAndUpdate(roomAllocation);
            }
        }
    }

    /**
     * Move Resource Allocations according to their availability for the selected room. Resource
     * allocations that are not available for the new location are cancelled.
     *
     * @param roomReservation roomReservation
     * @param roomAllocation roomAllocation
     */
    private void moveResourceAllocations(final RoomReservation roomReservation,
            final RoomAllocation roomAllocation) {
        for (final ResourceAllocation resourceAllocation : ReservationUtils
            .getActiveResourceAllocations(roomReservation)) {
            final Resource resource =
                    this.resourceDataSource.get(resourceAllocation.getResourceId());
            final RoomArrangement arrangement = this.roomArrangementDataSource.get(
                roomAllocation.getBlId(), roomAllocation.getFlId(), roomAllocation.getRmId(),
                roomAllocation.getConfigId(), roomAllocation.getArrangeTypeId());

            // Update the resource allocation time period before checking availability.
            resourceAllocation.setReservation(roomReservation);
            resourceAllocation.adjustTimePeriod(roomReservation.getTimePeriod(), resource,
                arrangement);

            // check if the resource is allowed in the new room and if it is not reserved for the
            // new reservation date
            final boolean allowed =
                    arrangement.allowsResourceStandard(resource.getResourceStandard())
                            && this.resourceDataSource.checkResourceAvailable(
                                resourceAllocation.getResourceId(), roomReservation,
                                resourceAllocation.getTimePeriod());

            if (!allowed) {
                throw new ReservableNotAvailableException(resource, roomReservation.getReserveId(),
                    RESOURCE_NOT_AVAILABLE, RoomReservationDataSource.class,
                    resourceAllocation.getResourceId(), roomAllocation.getBlId(),
                    roomAllocation.getFlId(), roomAllocation.getRmId());
            }

            resourceAllocation.setBlId(roomAllocation.getBlId());
            resourceAllocation.setFlId(roomAllocation.getFlId());
            resourceAllocation.setRmId(roomAllocation.getRmId());

            this.resourceDataSource.checkQuantityAllowed(roomReservation.getReserveId(),
                resourceAllocation, resource);
        } // end for
    }

    /**
     * Check Approval Required and set the status for the reservation and all of its allocations.
     *
     * @param roomReservation room reservation, only with its active allocations (i.e. without
     *            cancelled and rejected ones)
     */
    protected void checkApprovalRequired(final RoomReservation roomReservation) {
        // Don't change the status if the reservation is rejected or cancelled.
        final String status = roomReservation.getStatus();
        boolean updateApprovalStatus =
                StringUtil.isNullOrEmpty(status) || Constants.STATUS_AWAITING_APP.equals(status)
                        || Constants.STATUS_CONFIRMED.equals(status);
        // Do change the status if the conflict is resolved, i.e. the reservation now has a room.
        updateApprovalStatus =
                updateApprovalStatus || (Constants.STATUS_ROOM_CONFLICT.equals(status)
                        && !roomReservation.getRoomAllocations().isEmpty());

        if (updateApprovalStatus) {
            boolean approvalRequired = checkResourcesApprovalRequired(roomReservation);

            for (final RoomAllocation roomAllocation : roomReservation.getRoomAllocations()) {
                final RoomArrangement roomArrangement = this.roomArrangementDataSource.get(
                    roomAllocation.getBlId(), roomAllocation.getFlId(), roomAllocation.getRmId(),
                    roomAllocation.getConfigId(), roomAllocation.getArrangeTypeId());
                if (roomArrangement.getApprovalRequired() == 1) {
                    approvalRequired = true;
                    roomAllocation.setStatus(Constants.STATUS_AWAITING_APP);
                } else {
                    roomAllocation.setStatus(Constants.STATUS_CONFIRMED);
                }
            }

            roomReservation.setStatus(
                approvalRequired ? Constants.STATUS_AWAITING_APP : Constants.STATUS_CONFIRMED);
        }
    }

}
