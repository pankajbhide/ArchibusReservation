package com.archibus.app.reservation.service.helpers;

import java.util.*;

import com.archibus.app.reservation.dao.IRoomReservationDataSource;
import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.RoomConflictsMode;
import com.archibus.app.reservation.service.ReservationService;
import com.archibus.utility.ExceptionBaseFactory;

/**
 * The Reservation conflicts helper.
 *
 * @author Yorik Gerlo
 */
public final class ReservationConflictsHelper {

    /**
     * Private default constructor. This class is not non-instantiable.
     */
    private ReservationConflictsHelper() {
    }

    /**
     * Initialize the number of conflicts in the list of room arrangements.
     *
     * @param roomArrangements the complete list of room arrangements
     * @param availableRoomArrangements the available room arrangements
     */
    public static void initializeNumberOfConflicts(final List<RoomArrangement> roomArrangements,
            final List<RoomArrangement> availableRoomArrangements) {
        final Set<RoomArrangement> roomArrangementsAvailable = new HashSet<RoomArrangement>();
        // do not specify time zone here, only used for converting day start and end
        roomArrangementsAvailable.addAll(availableRoomArrangements);
        // Count the number of conflicts.
        for (final RoomArrangement room : roomArrangements) {
            if (roomArrangementsAvailable.contains(room)) {
                room.setNumberOfConflicts(0);
            } else {
                room.setNumberOfConflicts(1);
            }
        }
    }

    /**
     * Create a conflicted reservation for a first occurrence.
     *
     * @param reservation the room reservation to save as conflicted
     * @param roomReservationDataSource the data source to use for saving
     */
    public static void createConflictedFirstOccurrence(final RoomReservation reservation,
            final IRoomReservationDataSource roomReservationDataSource) {
        // Save as conflicted.
        reservation.setStatus(Constants.STATUS_ROOM_CONFLICT);

        // Remove room and resource allocations before saving.
        final RoomAllocation roomAllocation = reservation.getRoomAllocations().get(0);
        reservation.getRoomAllocations().clear();
        final List<ResourceAllocation> resourceAllocations = reservation.getResourceAllocations();
        reservation.setResourceAllocations(null);
        reservation.setBackupBuildingId(roomAllocation.getBlId());

        roomReservationDataSource.save(reservation);

        // After saving, add room and resource allocations again for the later occurrences.
        reservation.addRoomAllocation(roomAllocation);
        reservation.setResourceAllocations(resourceAllocations);
    }

    /**
     * Process the list of available rooms: sort and filter by number of conflicts.
     *
     * @param roomArrangements list of available rooms sorted by conflict count
     * @param numberOfOccurrences number of occurrences
     * @return filtered list of available rooms
     */
    public static List<RoomArrangement> processAvailableRoomResults(
            final List<RoomArrangement> roomArrangements, final int numberOfOccurrences) {
        Collections.sort(roomArrangements, new Comparator<RoomArrangement>() {

            /**
             * Compare two room arrangements for sorting by number of conflicts. Note this
             * comparison is not consistent with equals.
             *
             * @param value1 the first room arrangement
             * @param value2 the second room arrangement
             * @return a value > 0 if value1 should be after value2, a value < 0 if value1 should be
             *         before value2
             */
            @Override
            public int compare(final RoomArrangement value1, final RoomArrangement value2) {
                return value1.getNumberOfConflicts() - value2.getNumberOfConflicts();
            }

        });
        final RoomConflictsMode conflictsMode = ReservationConflictsHelper.getConflictsMode();

        List<RoomArrangement> filteredRoomArrangements = roomArrangements;
        // Check whether there are rooms without conflicts.
        final boolean allConflicted =
                roomArrangements.isEmpty() || roomArrangements.get(0).getNumberOfConflicts() > 0;

        // Eliminate the rooms with conflicts depending on the configuration.
        if (conflictsMode.alwaysIncludeRoomConflicts()
                || (allConflicted && conflictsMode.onlyIfAllRoomsHaveConflicts())) {
            // Determine the maximum number of conflicts depending on the configuration.
            final int maxConflictsAllowed =
                    conflictsMode.getMaxConflictsAllowed(numberOfOccurrences);
            for (int i = 0; i < roomArrangements.size(); ++i) {
                if (roomArrangements.get(i).getNumberOfConflicts() > maxConflictsAllowed) {
                    filteredRoomArrangements = roomArrangements.subList(0, i);
                    break;
                }
            }
        } else {
            // Retain only the rooms without conflicts.
            for (int i = 0; i < roomArrangements.size(); ++i) {
                if (roomArrangements.get(i).getNumberOfConflicts() != 0) {
                    filteredRoomArrangements = roomArrangements.subList(0, i);
                    break;
                }
            }
        }
        return filteredRoomArrangements;
    }

    /**
     * Check whether the given reservation already has conflict status or was not saved yet.
     * Otherwise, since we don't allow going from non-conflicted to conflicted, throw an exception
     * indicating the room is not available.
     *
     * @param reservation the reservation to check
     */
    public static void checkAlreadyConflicted(final RoomReservation reservation) {
        if (reservation.getReserveId() != null && reservation.getReserveId() > 0
                && !com.archibus.app.reservation.dao.datasource.Constants.STATUS_ROOM_CONFLICT
                    .equals(reservation.getStatus())) {
            final RoomArrangement reservable =
                    reservation.getRoomAllocations().get(0).getRoomArrangement();
            throw new ReservableNotAvailableException(reservable, reservation.getReserveId(),
                ReservationService.ROOM_NOT_AVAILABLE, ReservationService.class,
                reservable.getBlId(), reservable.getFlId(), reservable.getRmId());
        }
    }

    /**
     * Verify the number of conflicts actually created is allowed for the current configuration.
     *
     * @param numberOfConflicts the number of conflicts
     * @param numberOk the number of successful reservations
     */
    public static void checkNumberOfConflictsCreated(final int numberOfConflicts,
            final int numberOk) {
        final RoomConflictsMode conflictsMode = ReservationConflictsHelper.getConflictsMode();
        if (numberOfConflicts > conflictsMode
            .getMaxConflictsAllowed(numberOk + numberOfConflicts)) {
            
            // @translatable
            throw ExceptionBaseFactory.newTranslatableException(
                "Too many conflicts occurred while creating the reservations: the room is not available for {0} of the {1} occurrences. Please select a different room.",
                new Object[] { numberOfConflicts, numberOk + numberOfConflicts });
        }
    }

    /**
     * Check whether VPA restrictions must be enabled for the Reservations Application.
     *
     * @return true if enabled, false if disabled
     */
    public static RoomConflictsMode getConflictsMode() {
        return RoomConflictsMode.getRoomConflictsMode(com.archibus.service.Configuration
            .getActivityParameterInt("AbWorkplaceReservations", "HideRoomConflicts",
                com.archibus.app.reservation.domain.recurrence.Constants.CONFLICTS_FILTERED_IF_ONLY_CONFLICTS));
    }

}
