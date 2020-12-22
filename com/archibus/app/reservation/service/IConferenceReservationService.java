package com.archibus.app.reservation.service;

import java.util.List;

import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.Recurrence;

/**
 * Interface for the conference reservation service. This service can process reservation requests
 * without calendar interaction. It's called from external clients such as the Outlook Plugin and
 * from the Reservations WFR's.
 * 
 * @author Yorik Gerlo
 */
public interface IConferenceReservationService extends IConferenceAwareReservationService {
    
    /**
     * Save a list of reservations representing a single conference call or a new recurring
     * conference call.
     *
     * @param confCallReservations the reservations in the conference call
     * @param recurrence the recurrence pattern
     */
    void saveCompiledConferenceCallReservations(List<RoomReservation> confCallReservations,
            final Recurrence recurrence);

    /**
     * Save a regular or recurring conference call.
     * 
     * @param reservations the list of reservations indicating which rooms to book and whether this
     *            is a new reservation or an update
     * @param recurrence the recurrence pattern (null for regular reservations)
     * @param disconnectOnError true to disconnect other reservations in the conference call that
     *            cannot be cancelled, false to return a list of failures and do nothing in said
     *            situation
     * @return result of the operation: either contains the list of created reservations or the list
     *         of reservations that could not be cancelled
     */
    SavedConferenceCall saveConferenceCall(List<RoomReservation> reservations,
            Recurrence recurrence, boolean disconnectOnError);
    
}