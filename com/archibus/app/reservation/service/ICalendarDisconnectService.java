package com.archibus.app.reservation.service;

import com.archibus.app.reservation.domain.*;
import com.archibus.utility.ExceptionBase;

/**
 * Provides Calendar access to a remote service.<br/>
 * There can be several implementations, for now there is only Exchange and Web
 * Central for no integration.
 *
 * <p>
 * Represents services to disconnect appointments from reservations.
 * </p>
 *
 * <p>
 * Managed by Spring. All beans are defined with scope prototype.
 * </p>
 *
 * @author Yorik Gerlo
 * @since 23.2
 *
 */
public interface ICalendarDisconnectService {

    /**
     * Remove the location from the appointment linked to this reservation.
     *
     * @param reservation
     *            the reservation
     * @param message
     *            room cancellation message
     * @param notifyOrganizer
     *            true to notify the organizer through a separate message
     * @throws ExceptionBase
     *             translated exception
     */
    void disconnectAppointment(final IReservation reservation, final String message, final boolean notifyOrganizer)
            throws ExceptionBase;

    /**
     * Remove the location from the appointment occurrence linked to this
     * reservation.
     *
     * @param reservation
     *            the reservation
     * @param message
     *            room cancellation message
     * @param notifyOrganizer
     *            true to notify the organizer through a separate message
     * @throws ExceptionBase
     *             translated exception
     */
    void disconnectAppointmentOccurrence(final IReservation reservation, final String message,
            final boolean notifyOrganizer) throws ExceptionBase;

    /**
     * Remove the location from the appointment series linked to this
     * reservation.
     *
     * @param reservation
     *            the reservation
     * @param message
     *            room cancellation message
     * @param notifyOrganizer
     *            true to notify the organizer through a separate message
     * @throws ExceptionBase
     *             translated exception
     */
    void disconnectAppointmentSeries(final RoomReservation reservation, final String message,
            final boolean notifyOrganizer) throws ExceptionBase;

}