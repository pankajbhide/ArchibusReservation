package com.archibus.app.reservation.service;

import java.util.List;

import com.archibus.app.reservation.domain.*;
import com.archibus.utility.ExceptionBase;

/**
 * Provides Calendar information from a remote service.<br/>
 * There can be several implementations, for now there is only Exchange and Web
 * Central for no integration.
 *
 * <p>
 * Represents services to create and update appointments and find free/busy
 * calendar events for attendees.
 * </p>
 *
 * <p>
 * Managed by Spring. All beans are defined with scope prototype.
 * </p>
 *
 * @author Bart Vanderschoot
 * @since 21.2
 *
 */
public interface ICalendarService {

    /**
     * Create an appointment that doesn't have a unique id yet.
     *
     * @param reservation
     *            the reservation
     * @return unique id (remote service identifier)
     *
     * @throws ExceptionBase
     *             translated exception
     */
    String createAppointment(final IReservation reservation) throws ExceptionBase;

    /**
     * Update an appointment. This can be a single appointment or a whole
     * appointment series.
     *
     * @param reservation
     *            the reservation
     *
     * @throws ExceptionBase
     *             translated exception
     */
    void updateAppointment(final IReservation reservation) throws ExceptionBase;

    /**
     * Update an appointment that is part of a recurrence series.
     *
     * @param reservation
     *            the reservation
     * @param originalReservation
     *            the original reservation
     * @throws ExceptionBase
     *             translated exception
     */
    void updateAppointmentOccurrence(final IReservation reservation, final IReservation originalReservation)
            throws ExceptionBase;

    /**
     * Update the specified occurrences of a recurring appointment series.
     *
     * @param reservation
     *            the primary updated reservation, containing all updated
     *            occurrences
     * @param originalReservations
     *            the corresponding original reservations
     * @throws ExceptionBase
     *             translated exception
     */
    void updateAppointmentSeries(final RoomReservation reservation, final List<RoomReservation> originalReservations)
            throws ExceptionBase;

    /**
     * Cancel the appointment.
     *
     * @param reservation
     *            the reservation
     * @param message
     *            cancellation message
     * @param notifyOrganizer
     *            true to notify the organizer through a separate message
     *
     * @throws ExceptionBase
     *             translated exception
     */
    void cancelAppointment(final IReservation reservation, final String message, final boolean notifyOrganizer)
            throws ExceptionBase;

    /**
     * Cancel single appointment in a recurrent meeting.
     *
     * @param reservation
     *            the reservation
     * @param message
     *            cancellation message
     * @param notifyOrganizer
     *            true to notify the organizer through a separate message
     * @throws ExceptionBase
     *             translated exception
     */
    void cancelAppointmentOccurrence(final IReservation reservation, final String message,
            final boolean notifyOrganizer) throws ExceptionBase;

}