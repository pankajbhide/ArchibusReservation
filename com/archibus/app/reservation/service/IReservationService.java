package com.archibus.app.reservation.service;

import java.util.List;

import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.Recurrence;

/**
 * Interface for reservation service.
 *
 * @author Bart Vanderschoot
 * @since 20.1
 */
public interface IReservationService {

    /**
     * Find available rooms.
     *
     * @param reservation the reservation
     * @param numberOfAttendees the number attendees
     * @param externalAllowed whether to return only rooms that are suitable for external guests
     * @param fixedResourceStandards the fixed resource standards
     * @param allDayEvent true for all day events, false for regular reservations
     * @param timeZone time zone to convert to
     * @return the list of available rooms
     * @throws ReservationException the reservation exception
     */
    List<RoomArrangement> findAvailableRooms(final RoomReservation reservation,
        final Integer numberOfAttendees, final boolean externalAllowed,
        final List<String> fixedResourceStandards, final boolean allDayEvent,
        final String timeZone) throws ReservationException;

    /**
     * Find available rooms.
     *
     * @param reservation the reservation
     * @param numberOfAttendees the number attendees
     * @param externalAllowed whether to return only rooms that are suitable for external guests
     * @param fixedResourceStandards the fixed resource standards
     * @param allDayEvent true for all day events, false for regular reservations
     * @param recurrence the recurrence pattern
     * @param timeZone time zone to convert to
     * @return the list of available rooms
     * @throws ReservationException the reservation exception
     */
    List<RoomArrangement> findAvailableRoomsRecurrence(final RoomReservation reservation,
        final Integer numberOfAttendees, final boolean externalAllowed,
        final List<String> fixedResourceStandards, final boolean allDayEvent,
        final Recurrence recurrence, final String timeZone) throws ReservationException;

    /**
     * Find available rooms when editing recurrent reservations.
     *
     * @param roomReservation the room reservation
     * @param existingReservations the existing reservations
     * @param numberOfAttendees the number of attendees
     * @param externalAllowed the external allowed
     * @param fixedResourceStandards the fixed resource standards
     * @param allDayEvent the all day event
     * @param timeZone the time zone
     * @return the list of room arrangements
     * @throws ReservationException the reservation exception
     */
    List<RoomArrangement> findAvailableRooms(final RoomReservation roomReservation,
        final List<RoomReservation> existingReservations, final Integer numberOfAttendees,
        final boolean externalAllowed, final List<String> fixedResourceStandards,
        final boolean allDayEvent, final String timeZone) throws ReservationException;

    /**
     * Save recurring reservation.
     *
     * @param reservation the reservation
     * @param recurrence the recurrence
     * @param parentId to update a specific set of reservations with the given parent id
     * @return list of created reservations
     * @throws ReservationException the reservation exception
     */
    List<RoomReservation> saveRecurringReservation(final RoomReservation reservation,
        final Recurrence recurrence, final Integer parentId) throws ReservationException;

    /**
     * Edit a recurring reservation.
     *
     * @param reservation the first reservation in the series to edit
     * @return list of edited reservations
     * @throws ReservationException the reservation exception
     */
    List<RoomReservation> editRecurringReservation(final RoomReservation reservation)
            throws ReservationException;

    /**
     * Save a single reservation.
     *
     * @param reservation the reservation to save.
     * @throws ReservationException the reservation exception
     */
    void saveReservation(final IReservation reservation) throws ReservationException;

    /**
     * Get active reservation (including conflicted ones). This treats the reservation id parameter
     * transparently to find the primary conference call reservation if the id is also a conference
     * reservation id.
     *
     * @param reserveId reserve Id (or conference id)
     * @param timeZone timeZone
     * @return RoomReservation the room reservation (null if not found)
     */
    RoomReservation getActiveReservation(final Integer reserveId, final String timeZone);

    /**
     * Get room reservation by UniqueId, possibly further limited by conference id.
     *
     * @param uniqueId unique Id
     * @param conferenceId conference Id
     * @param timeZone timeZone
     * @return list of room reservations
     */
    List<RoomReservation> getByUniqueId(final String uniqueId, final Integer conferenceId,
        final String timeZone);

    /**
     * Get room arrangement details.
     *
     * @param roomArrangements list of room arrangement primary keys
     * @return the full room arrangements
     */
    List<RoomArrangement> getRoomArrangementDetails(final List<RoomArrangement> roomArrangements);

}
