package com.archibus.app.reservation.service;

import java.sql.Time;
import java.util.List;

import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.Recurrence;

/**
 * Interface for reservation service.
 *
 * @author Bart Vanderschoot
 * @since 20.1
 */
public interface IConferenceAwareReservationService extends IReservationService {

    /**
     * Save a reservation. Updates all reservations in the same conference call as well.
     *
     * @param reservation the reservation to save.
     * @throws ReservationException the reservation exception
     */
    void saveFullReservation(final IReservation reservation) throws ReservationException;

    /**
     * Save a recurring reservation. Update all reservations in the same conference call if there
     * are existing reservations.
     *
     * @param reservation the reservation
     * @param recurrence the recurrence
     * @return list of created reservations
     * @throws ReservationException the reservation exception
     */
    List<RoomReservation> saveFullRecurringReservation(final RoomReservation reservation,
            final Recurrence recurrence) throws ReservationException;

    /**
     * Verify whether the reservations with the given unique ID adhere to the given recurrence
     * pattern, i.e. every occurrence has a reservation and every reservation can be linked to an
     * occurrence.
     *
     * @param uniqueId the unique id of the appointment series
     * @param pattern the recurrence pattern of the appointment series
     * @param startTime time of day that the appointments start
     * @param endTime time of day that the appointments end
     * @param timeZone the time zone used to specify the recurrence pattern and times
     * @return true if the reservations match the pattern, false otherwise
     * @throws ReservationException when an error occurs
     */
    boolean verifyRecurrencePattern(final String uniqueId, final Recurrence pattern,
            final Time startTime, final Time endTime, final String timeZone)
            throws ReservationException;

}
