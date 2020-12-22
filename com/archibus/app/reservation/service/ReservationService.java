package com.archibus.app.reservation.service;

import java.util.*;

import org.apache.log4j.Logger;

import com.archibus.app.reservation.dao.*;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.*;
import com.archibus.app.reservation.service.actions.FindAvailableRoomsOccurrenceAction;
import com.archibus.app.reservation.service.helpers.*;
import com.archibus.app.reservation.util.*;
import com.archibus.utility.StringUtil;

/**
 * Reservation Service class.
 *
 * The service class is a business logic layer used for different front-end handlers. Both event
 * handlers and remote services can use service class.
 *
 * @author Bart Vanderschoot
 *
 */
public class ReservationService extends AdminServiceContainer implements IReservationService {

    /** Error message when a room is not available. */
    // @translatable
    public static final String ROOM_NOT_AVAILABLE = "The room {0}-{1}-{2} is not available.";

    /** Error message when a room is not available for external guests. */
    // @translatable
    public static final String ROOM_NOT_AVAILABLE_FOR_EXTERNAL =
            "The room {0}-{1}-{2} is not available for external guests.";

    /** The room reservation data source. */
    private IConferenceCallReservationDataSource reservationDataSource;

    /** The room arrangement data source. */
    private IRoomArrangementDataSource roomArrangementDataSource;

    /** The room allocation data source. */
    private IRoomAllocationDataSource roomAllocationDataSource;

    /** The Work Request service. */
    private WorkRequestService workRequestService;

    /** The logger. */
    private final Logger logger = Logger.getLogger(ReservationService.class);

    /** {@inheritDoc} */
    @Override
    public RoomReservation getActiveReservation(final Integer reserveId, final String timeZone) {
        final RoomReservation reservation =
                this.reservationDataSource.getActiveReservation(reserveId);
        if (reservation != null && StringUtil.notNullOrEmpty(timeZone)) {
            TimeZoneConverter.convertToTimeZone(reservation, timeZone);
        }
        return reservation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<RoomReservation> getByUniqueId(final String uniqueId, final Integer conferenceId,
            final String timeZone) {
        final List<RoomReservation> reservations =
                this.reservationDataSource.getByUniqueId(uniqueId, conferenceId, null);

        if (reservations.isEmpty() && conferenceId != null) {
            // find an exact match by reservation id
            final RoomReservation reservation = this.getActiveReservation(conferenceId, null);
            if (reservation != null) {
                reservations.add(reservation);
            }
        }

        TimeZoneConverter.convertToTimeZone(reservations, timeZone);
        return reservations;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<RoomArrangement> getRoomArrangementDetails(
            final List<RoomArrangement> roomArrangements) {
        final List<RoomArrangement> details =
                new ArrayList<RoomArrangement>(roomArrangements.size());
        for (final RoomArrangement roomArrangement : roomArrangements) {
            details.add(this.roomArrangementDataSource.get(roomArrangement.getBlId(),
                roomArrangement.getFlId(), roomArrangement.getRmId(), roomArrangement.getConfigId(),
                roomArrangement.getArrangeTypeId()));
        }
        return details;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<RoomArrangement> findAvailableRooms(final RoomReservation reservation,
            final Integer numberAttendees, final boolean externalAllowed,
            final List<String> fixedResourceStandards, final boolean allDayEvent,
            final String timeZone) throws ReservationException {

        final List<RoomArrangement> results =
                this.roomArrangementDataSource.findAvailableRooms(reservation, numberAttendees,
                    externalAllowed, fixedResourceStandards, allDayEvent, false);

        // Convert dayStart / dayEnd to requested time zone.
        TimeZoneConverter.convertToTimeZone(results, reservation.getStartDate(),
            reservation.determineBuildingId(), timeZone);

        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<RoomArrangement> findAvailableRoomsRecurrence(
            final RoomReservation reservation, final Integer numberOfAttendees,
            final boolean externalAllowed, final List<String> fixedResourceStandards,
            final boolean allDayEvent, final Recurrence recurrence, final String timeZone)
            throws ReservationException {

        RecurrenceHelper.moveToNextOccurrence(reservation, recurrence);

        // when editing provide the existing occurrences, so their allocations can be ignored
        // in the availability check
        List<RoomReservation> existingReservations = null;
        if (reservation.getParentId() != null) {
            if (reservation.getConferenceId() == null) {
                existingReservations = this.reservationDataSource
                    .getByParentId(reservation.getParentId(), null, null, false);
            } else {
                existingReservations =
                        this.reservationDataSource.getConferenceCallOccurrences(reservation);
            }
            TimeZoneConverter.convertToTimeZone(existingReservations, reservation.getTimeZone());
        }
        // look for all linked reservations
        final Map<Date, RoomReservation> existingOccurrences =
                ReservationUtils.toDateMap(existingReservations);
        /*
         * If the time period is modified for the first occurrence, create a copy of the original
         * and set the modified time period in the reservation object. Restore the original time
         * period before proceeding with the other occurrences.
         */
        final TimePeriod originalTimePeriod = new TimePeriod(reservation.getTimePeriod());
        final TimePeriod modifiedTimePeriod =
                recurrence.getModifiedTimePeriod(reservation.getStartDate());
        if (modifiedTimePeriod != null) {
            reservation.setTimePeriod(modifiedTimePeriod);
        }
        final Date firstDate = reservation.getStartDate();

        List<RoomArrangement> roomArrangements =
                this.findAvailableRoomsFirstOccurrence(reservation, numberOfAttendees,
                    externalAllowed, fixedResourceStandards, allDayEvent, existingOccurrences);

        if (!roomArrangements.isEmpty() && recurrence instanceof AbstractIntervalPattern) {
            /*
             * Restore the original time period (if the first occurrence had a custom time period).
             * If it had a custom time zone, make sure this time zone is used on each occurrence
             * date to apply the correct DST rules.
             */
            reservation.setTimePeriod(originalTimePeriod);

            // Loop through the pattern to further restrict the list of available arrangements.
            final AbstractIntervalPattern pattern = (AbstractIntervalPattern) recurrence;
            final FindAvailableRoomsOccurrenceAction action =
                    new FindAvailableRoomsOccurrenceAction(reservation, numberOfAttendees,
                        fixedResourceStandards, allDayEvent, existingOccurrences, roomArrangements,
                        this.roomArrangementDataSource);
            pattern.loopThroughRepeats(action);

            // total number of occurrences = first occurrence + number of visited occurrences
            roomArrangements = ReservationConflictsHelper.processAvailableRoomResults(
                roomArrangements, action.getNumberOfHandledOccurrences() + 1);
        }

        // Convert remaining dayStart / dayEnd to requested time zone.
        TimeZoneConverter.convertToTimeZone(roomArrangements, firstDate,
            reservation.determineBuildingId(), timeZone);

        return roomArrangements;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<RoomArrangement> findAvailableRooms(final RoomReservation roomReservation,
            final List<RoomReservation> existingReservations, final Integer numberOfAttendees,
            final boolean externalAllowed, final List<String> fixedResourceStandards,
            final boolean allDayEvent, final String timeZone) throws ReservationException {

        // Get the room arrangements in the correct requested time zone.
        List<RoomArrangement> roomArrangements = null;
        final TimePeriod originalTimePeriod = roomReservation.getTimePeriod();

        for (final RoomReservation reservation : existingReservations) {
            // start from the original time period and time zone
            roomReservation.setTimePeriod(originalTimePeriod);
            roomReservation.setStartDate(reservation.getStartDate());
            roomReservation.setEndDate(reservation.getEndDate());

            roomReservation.setReserveId(reservation.getReserveId());
            roomReservation
                .setReservationIdsInConference(reservation.getReservationIdsInConference());

            final List<RoomArrangement> rooms = this.roomArrangementDataSource.findAvailableRooms(
                roomReservation, numberOfAttendees, externalAllowed, fixedResourceStandards,
                allDayEvent, false);

            if (roomArrangements == null) {
                roomArrangements = rooms;
            } else {
                roomArrangements.retainAll(rooms);
            }

        }

        // Convert remaining dayStart / dayEnd to requested time zone.
        TimeZoneConverter.convertToTimeZone(roomArrangements, originalTimePeriod.getStartDate(),
            roomReservation.determineBuildingId(), timeZone);

        return roomArrangements;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<RoomReservation> saveRecurringReservation(final RoomReservation reservation,
            final Recurrence recurrence, final Integer parentId) throws ReservationException {
        final List<RoomReservation> savedReservations = new ArrayList<RoomReservation>();

        if (reservation.getRoomAllocations().isEmpty()) {
            // @translatable
            throw new ReservationException("Room reservation has no room allocated.",
                ReservationService.class, this.getAdminService());
        }

        final Date originalStartDate = reservation.getStartDate();
        final Date originalEndDate = reservation.getEndDate();
        final int firstOccurrenceIndex =
                RecurrenceHelper.moveToNextOccurrence(reservation, recurrence) + 1;

        this.checkReservationCreationTimeOut(reservation, firstOccurrenceIndex);

        RoomReservation actualFirstReservation = reservation;
        Map<Integer, List<RoomReservation>> existingReservations = null;
        if (StringUtil.notNullOrEmpty(reservation.getUniqueId())
                && reservation.getReserveId() != null) {
            final List<RoomReservation> reservations;
            if (parentId == null) {
                reservations = this.getByUniqueId(reservation.getUniqueId(), null, null);
            } else {
                reservations = this.reservationDataSource.getByParentId(parentId, null, null, true);
            }
            existingReservations =
                    ReservationUtils.groupByOccurrenceIndex(reservations, firstOccurrenceIndex);
            actualFirstReservation = RecurrenceHelper.prepareFirstReservation(reservation,
                firstOccurrenceIndex, existingReservations);
        }

        ReservationServiceHelper.insertRecurringReservations(recurrence, savedReservations,
            actualFirstReservation, firstOccurrenceIndex, existingReservations,
            this.reservationDataSource, this.roomArrangementDataSource);

        if (existingReservations != null) {
            // cancel all remaining reservations
            for (final List<RoomReservation> reservationsWithIndex : existingReservations
                .values()) {
                for (final RoomReservation reservationToCancel : reservationsWithIndex) {
                    this.reservationDataSource.cancel(reservationToCancel, null);
                    this.workRequestService.cancelWorkRequest(reservationToCancel);
                }
            }
        }

        // reset the start and end date of the parameters
        if (firstOccurrenceIndex > 1) {
            reservation.setStartDate(originalStartDate);
            reservation.setEndDate(originalEndDate);
            recurrence.setStartDate(originalStartDate);
        }

        // create or update the work request
        this.workRequestService.createWorkRequest(actualFirstReservation, true);

        return savedReservations;
    }

    /**
     * Check whether reservation creation has timed out (i.e. another thread is already creating
     * reservations).
     *
     * @param reservation the master reservation of the recurrence series being saved
     * @param firstOccurrenceIndex occurrence index of the first reservation being saved
     */
    private void checkReservationCreationTimeOut(final RoomReservation reservation,
            final int firstOccurrenceIndex) {
        // if creating new reservation, make sure there are no leftover reservations with the
        // same unique id and location
        // TODO extend this check to detect timeout when editing a recurring reservation
        boolean timeoutDetected = false;
        if (StringUtil.notNullOrEmpty(reservation.getUniqueId())
                && (reservation.getReserveId() == null || reservation.getReserveId() == 0)) {
            /*
             * Check for an existing reservation for the first occurrence being created. If this one
             * already exists while the parameter doesn't have a reservation id, a timeout has
             * occurred. Verify the existing reservation is for the same room to avoid a false
             * positive when creating a recurring conference call reservation.
             */
            final List<RoomReservation> reservations = this.reservationDataSource
                .getByUniqueId(reservation.getUniqueId(), null, firstOccurrenceIndex);
            if (reservations != null) {
                final RoomArrangement arrangement =
                        reservation.getRoomAllocations().get(0).getRoomArrangement();
                // check that none of the existing reservations are for the same room
                timeoutDetected = ReservationServiceHelper.containsRoom(reservations, arrangement,
                    this.roomAllocationDataSource);
            }
        }
        if (timeoutDetected) {
            // @translatable
            throw new ReservationException(
                "Reservation creation has timed out. \n\nTo resolve this please contact your ARCHIBUS Administrator to reduce the maximum number of occurrences that can be created. Afterwards, delete the appointment to cancel all orphaned reservations.\n",
                ReservationService.class, this.getAdminService());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<RoomReservation> editRecurringReservation(final RoomReservation reservation)
            throws ReservationException {
        final List<RoomReservation> savedReservations = new ArrayList<RoomReservation>();

        // when editing, fetch the existing reservations starting on this date
        // no need for timezone conversion, timezone is copied from new reservation object
        final List<RoomReservation> existingReservations = this.reservationDataSource
            .getByParentId(reservation.getParentId(), reservation.getStartDate(), null, false);

        ReservationServiceHelper.updateExistingReservations(this.reservationDataSource,
            this.roomArrangementDataSource, savedReservations, reservation, existingReservations);
        // create or update the work request
        this.workRequestService.createWorkRequest(reservation, true);

        return savedReservations;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void saveReservation(final IReservation reservation) throws ReservationException {
        if (reservation instanceof RoomReservation) {
            final RoomReservation roomReservation = (RoomReservation) reservation;

            // Mark the reservation as recurring if it contains a recurrence pattern.
            if (roomReservation.getRecurrence() != null
                    && (roomReservation.getParentId() == null || roomReservation.getParentId() == 0)
                    && StringUtil.notNullOrEmpty(roomReservation.getUniqueId())) {
                final List<RoomReservation> existingReservations =
                        getByUniqueId(roomReservation.getUniqueId(), null, null);

                this.saveSingleReservation(roomReservation);

                RecurrenceHelper.markRecurring(this.reservationDataSource, roomReservation,
                    existingReservations);
            } else {
                this.saveSingleReservation(roomReservation);
            }
        } else {
            // @translatable
            throw new ReservationException("This is no room reservation.", ReservationService.class,
                this.getAdminService());
        }
    }

    /**
     * Save a single reservation without marking it as recurring.
     *
     * @param reservation the reservation to save
     * @throws ReservationException when an error occurs
     */
    protected void saveSingleReservation(final RoomReservation reservation)
            throws ReservationException {
        final RoomReservation roomReservation = reservation;
        this.checkRoomBeforeSave(roomReservation);

        // when a single occurrence is updated, check the date is changed
        this.reservationDataSource.checkRecurringDateModified(roomReservation);
        // Resource availability is checked in the RoomReservationDataSource.
        // if no conflicts, is safe to save
        this.reservationDataSource.save(roomReservation);

        // create or update the work request
        this.workRequestService.createWorkRequest(roomReservation, false);
    }

    /**
     * Check room availability before saving.
     *
     * @param roomReservation the room reservation to check
     */
    private void checkRoomBeforeSave(final RoomReservation roomReservation) {
        if (com.archibus.app.reservation.dao.datasource.Constants.STATUS_ROOM_CONFLICT.equals(
            roomReservation.getStatus()) && roomReservation.getRoomAllocations().isEmpty()) {
            // This is an update to a conflicted occurrence without resolving the conflict.
            // Determine the target time zone: get the building id from an other reservation in the
            // series.
            final RoomReservation storedReservation =
                    this.reservationDataSource.getActiveReservation(roomReservation.getReserveId());
            final String buildingId = storedReservation.determineBuildingId();
            roomReservation.setBackupBuildingId(buildingId);
            this.logger.debug("Updating conflicted reservation " + roomReservation.getReserveId()
                    + " in time zone of " + buildingId);
        } else {
            // We're currently not updating a conflicted occurrence without resolving the conflict.
            // check possible conflicts for rooms
            final List<RoomArrangement> roomArrangements = this.roomArrangementDataSource
                .findAvailableRooms(roomReservation, null, false, null, false, false);

            if (roomArrangements.isEmpty()) {
                // The room is not available. Report this through an exception.

                /*
                 * KB 3049896 if the room is available for internal use (i.e. without attendees), we
                 * can indicate the room is not available specifically for external guests.
                 */
                final boolean roomAvailableForInternal =
                        ReservationServiceHelper.checkRoomAvailableForInternalUse(roomReservation,
                            this.roomArrangementDataSource);
                final RoomArrangement reservable =
                        roomReservation.getRoomAllocations().get(0).getRoomArrangement();

                final String errorMessage = roomAvailableForInternal
                        ? ROOM_NOT_AVAILABLE_FOR_EXTERNAL : ROOM_NOT_AVAILABLE;
                throw new ReservableNotAvailableException(reservable,
                    roomReservation.getReserveId(), errorMessage, ReservationService.class,
                    this.getAdminService(), reservable.getBlId(), reservable.getFlId(),
                    reservable.getRmId());
            }
        }
    }

    /**
     * Find available rooms for the first occurrence (includes conflicts if configured).
     *
     * @param reservation the reservation configured for the first occurrence
     * @param numberOfAttendees the number of attendees
     * @param externalAllowed whether to show only rooms that allow external attendees
     * @param fixedResourceStandards the fixed resource standards required in the room
     * @param allDayEvent whether to find rooms available the entire day
     * @param existingOccurrences the existing occurrences (if any)
     * @return rooms for the first occurrence (includes conflicts if configured)
     */
    private List<RoomArrangement> findAvailableRoomsFirstOccurrence(
            final RoomReservation reservation, final Integer numberOfAttendees,
            final boolean externalAllowed, final List<String> fixedResourceStandards,
            final boolean allDayEvent, final Map<Date, RoomReservation> existingOccurrences) {
        // set the matching reservation id to ignore in the first availability check
        final RoomReservation occurrence = existingOccurrences.get(reservation.getStartDate());
        boolean allowConflictsOnFirstOccurrence =
                ReservationConflictsHelper.getConflictsMode().allowConflictsOnFirstOccurrence()
                        && (occurrence != null || reservation.getConferenceId() == null);
        if (allowConflictsOnFirstOccurrence && occurrence != null) {
            reservation.setReserveId(occurrence.getReserveId());
            reservation.setConferenceId(occurrence.getConferenceId());
            reservation.setReservationIdsInConference(occurrence.getReservationIdsInConference());
            /*
             * Don't allow conflicts on the first occurrence when editing an existing series that
             * doesn't have a conflict on the first occurrence.
             */
            allowConflictsOnFirstOccurrence =
                    com.archibus.app.reservation.dao.datasource.Constants.STATUS_ROOM_CONFLICT
                        .equals(occurrence.getStatus());
        }

        // Get the room arrangements potentially with conflicts.
        final List<RoomArrangement> roomArrangements = this.roomArrangementDataSource
            .findAvailableRooms(reservation, numberOfAttendees, externalAllowed,
                fixedResourceStandards, allDayEvent, allowConflictsOnFirstOccurrence);

        // remove all results if the reservation spans multiple days in local time
        if (!reservation.getStartDate().equals(reservation.getEndDate())) {
            roomArrangements.clear();
            // also don't check a second time for detecting conflicts
            allowConflictsOnFirstOccurrence = false;
        }

        final List<RoomArrangement> availableRoomArrangements;
        if (allowConflictsOnFirstOccurrence) {
            // Get the arrangements actually available.
            availableRoomArrangements = this.roomArrangementDataSource.findAvailableRooms(
                reservation, numberOfAttendees, externalAllowed, fixedResourceStandards,
                allDayEvent, false);
        } else {
            availableRoomArrangements = roomArrangements;
        }
        ReservationConflictsHelper.initializeNumberOfConflicts(roomArrangements,
            availableRoomArrangements);
        return roomArrangements;
    }

    /**
     * Setter ReservationDataSource.
     *
     * @param reservationDataSource reservation Data Source to set
     */
    public void setReservationDataSource(
            final IConferenceCallReservationDataSource reservationDataSource) {
        this.reservationDataSource = reservationDataSource;
    }

    /**
     * Setter for RoomArrangementDataSource.
     *
     * @param roomArrangementDataSource roomArrangementDataSource to set
     */
    public final void setRoomArrangementDataSource(
            final IRoomArrangementDataSource roomArrangementDataSource) {
        this.roomArrangementDataSource = roomArrangementDataSource;
    }

    /**
     * Setter for RoomAllocationDataSource.
     *
     * @param roomAllocationDataSource the room allocation data source to set
     */
    public final void setRoomAllocationDataSource(
            final IRoomAllocationDataSource roomAllocationDataSource) {
        this.roomAllocationDataSource = roomAllocationDataSource;
    }

    /**
     * Sets the work request service.
     *
     * @param workRequestService the new work request service
     */
    public void setWorkRequestService(final WorkRequestService workRequestService) {
        this.workRequestService = workRequestService;
    }

}
