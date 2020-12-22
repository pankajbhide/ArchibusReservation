package com.archibus.app.reservation.dao;

import java.util.List;

import com.archibus.app.reservation.domain.RoomReservation;

/**
 * Interface for conference call room reservation data source.
 *
 * @author Yorik Gerlo
 * @since 21.3
 */
public interface IConferenceCallReservationDataSource extends IRoomReservationDataSource {

    /**
     * Get room reservations by conference id.
     *
     * @param conferenceId the conference id
     * @param includeConflicted whether to include conflicted reservations in the result
     * @return list of room reservations with this conference id
     */
    List<RoomReservation> getByConferenceId(final Integer conferenceId,
            final boolean includeConflicted);

    /**
     * Get all active reservations with the given unique id, but return only 1 instance for each set
     * of conference call reservations.
     *
     * @param uniqueId the unique id
     * @return list of reservations
     */
    List<RoomReservation> getDistinctReservationsByUniqueId(final String uniqueId);
    
    /**
     * Get the existing occurrences for a recurring conference call reservation starting from the
     * given occurrence, without conflicted occurrences.
     *
     * @param roomReservation the first occurrence being edited
     * @return the existing occurrences in the conference call
     */
    List<RoomReservation> getConferenceCallOccurrences(final RoomReservation roomReservation);

    /**
     * Persist the given conference id in the given reservation.
     *
     * @param reservation the reservation to set the conference id for
     * @param conferenceId the conference id to set
     */
    void persistConferenceId(final RoomReservation reservation, final Integer conferenceId);

    /**
     * Save the conference call id and unique id in all reservation records part of the conference
     * call. If the conference call is recurring, all occurrences are stored in the
     * createdReservations property of each reservation in the conference call. If it's not
     * recurring, then the createdReservations property contains the reservation itself.
     *
     * @param primaryReservation the primary reservation for the first date
     * @param confCallReservations all reservations in the conference call on the first date
     * @param updateParentIds indicates whether parent id's must be updated
     */
    void persistCommonIdentifiers(final RoomReservation primaryReservation,
            final List<RoomReservation> confCallReservations, final boolean updateParentIds);

}
