package com.archibus.app.reservation.service.actions;

import java.util.*;

import com.archibus.app.reservation.dao.*;
import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.service.helpers.ReservationConflictsHelper;
import com.archibus.app.reservation.util.*;

/**
 * Provides a method for updating an occurrence of an existing recurring reservation. This action
 * doesn't cancel or copy any resource allocations associated with the occurrences.
 * <p>
 * Used by ReservationService to update all occurrences of a recurring reservation via the
 * Recurrence Pattern definition.
 *
 * @author Yorik Gerlo
 * @since 22.1
 */
public class UpdateRecurringReservationOccurrenceAction
        extends SaveRecurringReservationOccurrenceAction {

    /** Existing reservations mapped by occurrence index. */
    private final Map<Integer, List<RoomReservation>> existingReservations;

    /**
     * Constructor.
     *
     * @param savedReservations list to store the saved reservations
     * @param roomReservationDataSource data source to use for saving reservations
     * @param roomArrangementDataSource data source used for checking room availability
     * @param firstReservation the reservation for the first occurrence, already booked
     * @param existingReservations the existing reservations mapped by occurrence index
     */
    public UpdateRecurringReservationOccurrenceAction(final List<RoomReservation> savedReservations,
            final IRoomReservationDataSource roomReservationDataSource,
            final IRoomArrangementDataSource roomArrangementDataSource,
            final RoomReservation firstReservation,
            final Map<Integer, List<RoomReservation>> existingReservations) {
        super(savedReservations, roomReservationDataSource, roomArrangementDataSource,
            firstReservation);
        this.existingReservations = existingReservations;
    }

    /**
     * Actually handle an occurrence, after setting the correct time period.
     *
     * @param timePeriod the time period for this occurrence
     * @param occurrenceIndex the occurrence index for this occurrence
     * @return true to continue processing, false to stop after this occurrence
     */
    @Override
    protected RoomReservation handleActualOccurrence(final TimePeriod timePeriod,
            final int occurrenceIndex) {
        RoomReservation recurringReservation =
                ReservationUtils.getPrimaryOccurrence(this.existingReservations, occurrenceIndex);
        Integer originalParentId = null;
        if (recurringReservation == null) {
            // only to be used for new reservations
            recurringReservation = new RoomReservation();
            // assign the occurrence index
            recurringReservation.setOccurrenceIndex(occurrenceIndex);
        } else if (recurringReservation.getConferenceId() != null) {
            /*
             * For conference call reservations, remember the original parent id. This is required
             * when another reservation already exists with the new parent id and current occurrence
             * index (otherwise we end up with 2 reservations with the same parent id and occurrence
             * index). If the original reservation with the new parent id was cancelled, retaining
             * the parent id is optional but simpler than finding out whether there's a conflict.
             */
            /*
             * For conference call reservations the parent id determines which reservations are
             * edited from Web Central when you choose to edit 'only this location' of a recurrence
             * series.
             */
            originalParentId = recurringReservation.getParentId();
        }
        this.getReservation().copyTo(recurringReservation, true);

        if (originalParentId != null) {
            recurringReservation.setParentId(originalParentId);
        }
        recurringReservation.setTimePeriod(timePeriod);
        TimeZoneConverter.convertToLocalTime(recurringReservation, this.getLocalTimeZone());

        if (this.isRoomAvailable(recurringReservation)) {
            if (recurringReservation.getRoomAllocations().isEmpty()) {
                final RoomAllocation recurringRoomAllocation = new RoomAllocation();
                this.getRoomAllocation().copyTo(recurringRoomAllocation);
                // addRoomAllocation also links the allocation to the reservation
                recurringReservation.addRoomAllocation(recurringRoomAllocation);
                // don't copy resource allocations when updating a series
            } else {
                this.getRoomAllocation().copyTo(recurringReservation.getRoomAllocations().get(0));
            }
        } else {
            this.logger.debug("The room is not available on " + timePeriod.getStartDate());
            ReservationConflictsHelper.checkAlreadyConflicted(recurringReservation);

            recurringReservation.setStatus(Constants.STATUS_ROOM_CONFLICT);
            recurringReservation.setBackupBuildingId(this.getRoomAllocation().getBlId());
        }

        // save all
        this.getReservationDataSource().save(recurringReservation);
        /*
         * Attach the other reservations with the same occurrence index to this reservation for
         * later processing.
         */
        recurringReservation
            .setCreatedReservations(this.existingReservations.remove(occurrenceIndex));
        recurringReservation.setTimeZone(this.getLocalTimeZone());
        return recurringReservation;
    }
}
