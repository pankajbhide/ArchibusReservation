package com.archibus.app.reservation.exchange.util;

import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.util.ReservationUtils;

import microsoft.exchange.webservices.data.*;

/**
 * Utility class. Provides methods to check equivalence between an Exchange appointment and the
 * corresponding reservation.
 * <p>
 *
 * Used by ItemHandler to verify whether incoming requests have relevant changes.
 *
 * @author Yorik Gerlo
 * @since 21.2
 */
public final class ReservationUpdater {

    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private ReservationUpdater() {
    }

    /**
     * Compare the appointment with the reservation regarding date, time, duration, subject and
     * attendees. Update the reservation object if any of the properties is different.
     *
     * @param reservation the reservation linked to the appointment
     * @param appointment the appointment on the organizer's calendar
     * @return true if equal, false if different
     */
    public static boolean updateReservation(final IReservation reservation,
            final Appointment appointment) {
        try {
            return updateReservation(reservation,
                AppointmentEquivalenceChecker.toCalendarEvent(appointment));
        } catch (final ServiceLocalException exception) {
            // @translatable
            throw new CalendarException("Error accessing appointment properties.", exception,
                ReservationUpdater.class);
        }
    }

    /**
     * Compare the meeting request with the reservation regarding date, time, duration, subject and
     * attendees. Update the reservation object if any of the properties is different.
     *
     * @param reservation the reservation linked to the appointment
     * @param request the meeting request
     * @return true if equal, false if different
     */
    public static boolean updateReservation(final IReservation reservation,
            final MeetingRequest request) {
        try {
            return updateReservation(reservation,
                AppointmentEquivalenceChecker.toCalendarEvent(request));
        } catch (final ServiceLocalException exception) {
            // @translatable
            throw new CalendarException("Error accessing meeting request properties.", exception,
                ReservationUpdater.class);
        }
    }

    /**
     * Compare the appointment with the reservation regarding date, time, duration, subject and
     * attendees. Update the reservation object if any of the properties is different.
     *
     * @param reservation the reservation linked to the appointment
     * @param event the calendar event
     * @return true if equal, false if different
     */
    public static boolean updateReservation(final IReservation reservation,
            final ICalendarEvent event) {
        final boolean dateTimeEqual =
                AppointmentEquivalenceChecker.compareDateTime(reservation, event);
        if (!dateTimeEqual) {
            reservation.setStartDateTime(event.getStartDateTime());
            reservation.setEndDateTime(event.getEndDateTime());
        }

        final boolean subjectEqual =
                AppointmentEquivalenceChecker.compareSubject(reservation, event);
        if (!subjectEqual) {
            reservation.setReservationName(event.getSubject());
        }

        final boolean attendeesEqual = AppointmentEquivalenceChecker
            .compareToReservationAttendees(reservation, event.getEmailAddresses());
        if (!attendeesEqual) {
            // Set the updated list of attendees in the reservation.
            final StringBuffer buffer = new StringBuffer();
            for (final String attendee : event.getEmailAddresses()) {
                buffer.append(attendee);
                buffer.append(';');
            }
            reservation.setAttendees(buffer.substring(0, buffer.length() - 1));
        }
        final boolean bodyEqual = AppointmentEquivalenceChecker.compareBody(reservation, event);
        if (!bodyEqual) {
            reservation.setComments(event.getBody());
            ReservationUtils.truncateComments(reservation);
        }

        return dateTimeEqual && subjectEqual && attendeesEqual && bodyEqual;
    }

}
