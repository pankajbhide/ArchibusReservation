package com.archibus.app.reservation.service.helpers;

import java.util.*;

import org.apache.log4j.Logger;

import com.archibus.app.reservation.dao.*;
import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.*;
import com.archibus.app.reservation.service.ReservationService;
import com.archibus.app.reservation.service.actions.*;
import com.archibus.app.reservation.util.ReservationUtils;

/**
 * The Class ReservationServiceHelper.
 */
public final class ReservationServiceHelper {

    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private ReservationServiceHelper() {
    }

    /**
     * Update existing reservations for room reservation.
     *
     * @param roomReservationDataSource the room reservation data source
     * @param roomArrangementDataSource the room arrangement data source
     * @param savedReservations the saved reservations
     * @param roomReservation the room reservation
     * @param existingReservations the existing reservations
     */
    public static void updateExistingReservations(
            final IRoomReservationDataSource roomReservationDataSource,
            final IRoomArrangementDataSource roomArrangementDataSource,
            final List<RoomReservation> savedReservations, final RoomReservation roomReservation,
            final List<RoomReservation> existingReservations) {
        final RoomAllocation roomAllocation = roomReservation.getRoomAllocations().get(0);

        // when editing we loop over existing reservations
        for (final RoomReservation existingReservation : existingReservations) {
            if (Constants.STATUS_CANCELLED.equals(existingReservation.getStatus())
                    || Constants.STATUS_REJECTED.equals(existingReservation.getStatus())) {
                // go to the next and skip this one
                continue;
            }

            // only change attributes that are allowed when editing
            roomReservation.copyTo(existingReservation, false);
            final RoomAllocation existingRoomAllocation =
                    existingReservation.getRoomAllocations().get(0);
            roomAllocation.copyTo(existingRoomAllocation);
            existingRoomAllocation.setReservation(existingReservation);

            final List<RoomArrangement> roomArrangements =
                    roomArrangementDataSource.findAvailableRooms(existingReservation, null, false,
                        null, false, false);
            if (roomArrangements.isEmpty()) {
                final RoomArrangement reservable =
                        roomReservation.getRoomAllocations().get(0).getRoomArrangement();
                throw new ReservableNotAvailableException(reservable, roomReservation.getReserveId(),
                        ReservationService.ROOM_NOT_AVAILABLE, ReservationService.class, reservable.getBlId(),
                        reservable.getFlId(), reservable.getRmId());
            }

            copyResourceAllocations(roomReservation, existingReservation);
            // save all
            roomReservationDataSource.save(existingReservation);

            savedReservations.add(existingReservation);
        }
    }

    /**
     * Copy resource allocations from one reservation to another reservation. The date is adjusted.
     * The time frame is only modified if required, i.e. only if the original time frame is outside
     * the target reservation time frame. Existing allocations for the same resources in the target
     * reservation are modified, existing allocations for other resources are removed.
     *
     * @param sourceReservation the source reservation
     * @param targetReservation the target reservation
     */
    public static void copyResourceAllocations(final AbstractReservation sourceReservation,
            final AbstractReservation targetReservation) {

        // create a hash map to check if the resources are in the target reservation
        final Map<String, ResourceAllocation> targetResources =
                new HashMap<String, ResourceAllocation>();

        // take only the ones that are not cancelled or rejected
        for (final ResourceAllocation resourceAllocation : ReservationUtils
            .getActiveResourceAllocations(targetReservation)) {
            targetResources.put(resourceAllocation.getResourceId(), resourceAllocation);
        }

        // if the target reservation has the same resources, update and not insert
        for (final ResourceAllocation sourceAllocation : sourceReservation.getResourceAllocations()) {
            // Create a new resource allocation for the same resource and time period.
            ResourceAllocation targetAllocation = null;

            if (targetResources.containsKey(sourceAllocation.getResourceId())) {
                targetAllocation = targetResources.remove(sourceAllocation.getResourceId());
            } else {
                targetAllocation = new ResourceAllocation();
                targetReservation.addResourceAllocation(targetAllocation);
            }

            // Assign the new allocation to the recurring reservation. The date is
            // modified, the time frame stays the same.
            sourceAllocation.copyTo(targetAllocation);
        }

        // Remove all remaining allocations from the target reservation.
        for (final String resourceId : targetResources.keySet()) {
            targetReservation.getResourceAllocations().remove(targetResources.get(resourceId));
        }
    }

    /**
     * Verify whether a list of reservations contains the given room arrangement.
     * @param reservations the reservations to check
     * @param arrangement the room arrangement to look for
     * @param roomAllocationDataSource the room allocation data source to look for additional room allocations
     * @return true if the room arrangement is found, false if not found
     */
    public static boolean containsRoom(final List<RoomReservation> reservations,
            final RoomArrangement arrangement, final IRoomAllocationDataSource roomAllocationDataSource) {
        boolean timeoutDetected = false;
        for (final RoomReservation existingReservation : reservations) {
            List<RoomAllocation> allocations = existingReservation.getRoomAllocations();
            if (allocations.isEmpty()) {
                // retrieve all room allocations for this parent and verify if the room is there
                allocations = roomAllocationDataSource.findByParentId(existingReservation.getParentId());
            }
            for (final RoomAllocation allocation : allocations) {
                if (arrangement.equals(allocation.getRoomArrangement())) {
                    timeoutDetected = true;
                    break;
                }
            }
        }
        return timeoutDetected;
    }

    /**
     * Insert recurring reservations.
     *
     * @param recurrence the recurrence
     * @param savedReservations all saved reservations will be added to this list in local time
     * @param roomReservation the room reservation
     * @param firstOccurrenceIndex occurrence index of the first reservation to create
     * @param existingReservations the existing reservations mapped by occurrence index
     * @param reservationDataSource the reservation data source
     * @param roomArrangementDataSource the room arrangement data source
     */
    public static void insertRecurringReservations(final Recurrence recurrence,
            final List<RoomReservation> savedReservations, final RoomReservation roomReservation,
            final int firstOccurrenceIndex,
            final Map<Integer, List<RoomReservation>> existingReservations,
            final IRoomReservationDataSource reservationDataSource,
            final IRoomArrangementDataSource roomArrangementDataSource) {

        if (recurrence == null) {
            // @translatable
            throw new ReservationException("Recurrence is not defined.", ReservationService.class);
        }

        // for a new recurrent reservation, the dates are calculated
        if (recurrence instanceof AbstractIntervalPattern) {
            final AbstractIntervalPattern pattern = (AbstractIntervalPattern) recurrence;

            // save the first base reservation
            saveFirstNewOccurrence(roomReservation, pattern, reservationDataSource, roomArrangementDataSource);
            // Set its parent reservation ID.
            final int parentId =
                    roomReservation.getParentId() == null ? roomReservation.getReserveId()
                            : roomReservation.getParentId();
            reservationDataSource.markRecurring(roomReservation, parentId, pattern.toString(),
                firstOccurrenceIndex);

            // Add the first occurrence in the list, to be replaced after the others are created
            // with an occurrence in local time.
            savedReservations.add(roomReservation);

            SaveRecurringReservationOccurrenceAction action = null;
            if (existingReservations == null || existingReservations.isEmpty()) {
                // loop through the pattern with the default save occurrence action
                action =
                        new SaveRecurringReservationOccurrenceAction(savedReservations,
                            reservationDataSource, roomArrangementDataSource,
                            roomReservation);
            } else {
                roomReservation.setCreatedReservations(existingReservations.remove(roomReservation
                    .getOccurrenceIndex()));

                // loop through the pattern with the update occurrence action
                action =
                        new UpdateRecurringReservationOccurrenceAction(savedReservations,
                            reservationDataSource, roomArrangementDataSource,
                            roomReservation, existingReservations);
            }
            pattern.loopThroughRepeats(action);

            // get the saved copy of the first occurrence with the proper status etc. in local time
            final RoomReservation firstSavedReservation =
                    reservationDataSource.getActiveReservation(roomReservation.getReserveId());
            firstSavedReservation.setTimeZone(action.getLocalTimeZone());
            firstSavedReservation.setCreatedReservations(roomReservation.getCreatedReservations());
            savedReservations.set(0, firstSavedReservation);
        }

        int numberOfConflicts = 0;
        int numberOk = 0;
        for (final RoomReservation savedReservation : savedReservations) {
            if (com.archibus.app.reservation.dao.datasource.Constants.STATUS_ROOM_CONFLICT
                .equals(savedReservation.getStatus())) {
                ++numberOfConflicts;
            } else {
                ++numberOk;
            }
        }

        ReservationConflictsHelper.checkNumberOfConflictsCreated(numberOfConflicts, numberOk);
    }

    /**
     * Save the first occurrence for a new recurring reservation, allowing conflicts.
     *
     * @param reservation the reservation to save
     * @param recurrence the recurrence pattern
     * @param reservationDataSource the reservation data source
     * @param roomArrangementDataSource the room arrangement data source
     * @throws ReservationException when the save failed
     */
    private static void saveFirstNewOccurrence(final RoomReservation reservation,
            final Recurrence recurrence, final IRoomReservationDataSource reservationDataSource,
            final IRoomArrangementDataSource roomArrangementDataSource) throws ReservationException {
        // Copy the time period to reset it after creating the reservation.
        final TimePeriod timePeriod = new TimePeriod(reservation.getTimePeriod());

        // check whether the time period of the first occurrence is modified
        final TimePeriod modifiedTimePeriod =
                recurrence.getModifiedTimePeriod(reservation.getStartDate());
        if (modifiedTimePeriod != null) {
            reservation.setTimePeriod(modifiedTimePeriod);
        }

        // check possible conflicts for rooms
        final List<RoomArrangement> roomArrangements =
                roomArrangementDataSource.findAvailableRooms(reservation, null, false, null,
                    false, false);
        if (roomArrangements.isEmpty()) {
            /*
             * KB 3053689 - Error message does not indicate that no external visitors are allowed
             * when creating a recurring conference call
             * KB 3054635 - External guests restriction not applied correctly for recurring meetings
             */
            if (checkRoomAvailableForInternalUse(reservation, roomArrangementDataSource)) {
                Logger.getLogger(ReservationServiceHelper.class).debug("The room is not available for externals");
                final RoomArrangement reservable = reservation.getRoomAllocations().get(0).getRoomArrangement();
                throw new ReservableNotAvailableException(reservable, reservation.getReserveId(),
                        ReservationService.ROOM_NOT_AVAILABLE_FOR_EXTERNAL, ReservationService.class,
                        reservable.getBlId(), reservable.getFlId(), reservable.getRmId());
            }
            Logger.getLogger(ReservationServiceHelper.class)
                    .debug("The room is not available on the first occurrence date " + reservation.getStartDate());

            // Don't allow a conflict if the reservation already existed
            // without a conflict.
            ReservationConflictsHelper.checkAlreadyConflicted(reservation);
            ReservationConflictsHelper.createConflictedFirstOccurrence(reservation, reservationDataSource);
        } else {
            // Resource availability is checked in the RoomReservationDataSource.
            // if no conflicts, is safe to save
            reservationDataSource.save(reservation);
        }

        // reset the time period for creating the other occurrences
        reservation.setTimePeriod(timePeriod);
    }

    /**
     * Check whether the room is available for internal use.
     * @param reservation the room reservation for which to check the room
     * @param roomArrangementDataSource the data source to use for checking availability
     * @return true if available, false if not available
     */
    public static boolean checkRoomAvailableForInternalUse(final RoomReservation reservation,
            final IRoomArrangementDataSource roomArrangementDataSource) {
        final String attendees = reservation.getAttendees();
        List<RoomArrangement> roomArrangementsInternal = null;
        try {
            reservation.setAttendees(null);
            roomArrangementsInternal = roomArrangementDataSource.findAvailableRooms(reservation,
                null, false, null, false, false);
        } finally {
            reservation.setAttendees(attendees);
        }
        return !roomArrangementsInternal.isEmpty();
    }


}
