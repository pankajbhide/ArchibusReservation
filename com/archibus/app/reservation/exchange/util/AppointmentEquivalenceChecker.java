package com.archibus.app.reservation.exchange.util;

import java.util.*;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.CalendarEvent;
import com.archibus.app.reservation.exchange.domain.UpdateType;
import com.archibus.app.reservation.util.*;
import com.archibus.utility.StringUtil;

import microsoft.exchange.webservices.data.*;

/**
 * Utility class. Provides methods to check equivalence between an Exchange appointment and the
 * corresponding reservation.
 * <p>
 *
 * Used by ItemHandler to verify whether incoming requests have relevant changes. Used by
 * ExchangeCalendar service to verify whether the Exchange meeting must be updated after changes in
 * Web Central.
 *
 * @author Yorik Gerlo
 * @since 21.2
 */
public final class AppointmentEquivalenceChecker {

    /** Newline. */
    private static final String NEWLINE = "\n";

    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private AppointmentEquivalenceChecker() {
    }

    /**
     * Convert an appointment into a calendar event, including all relevant properties.
     *
     * @param appointment the appointment to convert
     * @return the calendar event
     * @throws ServiceLocalException when an appointment property could not be read
     */
    public static ICalendarEvent toCalendarEvent(final Appointment appointment)
            throws ServiceLocalException {
        final CalendarEvent event = new CalendarEvent();
        event.setStartDateTime(appointment.getStart());
        event.setEndDateTime(appointment.getEnd());
        event.setSubject(appointment.getSubject());

        final SortedSet<String> appointmentAttendees = new TreeSet<String>();
        for (final Attendee attendee : appointment.getRequiredAttendees()) {
            // only count attendees that have been resolved to an email address
            if (StringUtil.notNullOrEmpty(attendee.getAddress())) {
                appointmentAttendees.add(attendee.getAddress());
            }
        }
        for (final Attendee attendee : appointment.getOptionalAttendees()) {
            if (StringUtil.notNullOrEmpty(attendee.getAddress())) {
                appointmentAttendees.add(attendee.getAddress());
            }
        }

        event.setEmailAddresses(appointmentAttendees);
        event.setBody(extractPlainTextBody(appointment));
        return event;
    }

    /**
     * Convert a meeting request into a calendar event, including all relevant properties.
     *
     * @param request the meeting request to convert
     * @return the calendar event
     * @throws ServiceLocalException when a property could not be read in the meeting request
     */
    public static ICalendarEvent toCalendarEvent(final MeetingRequest request)
            throws ServiceLocalException {
        final CalendarEvent event = new CalendarEvent();
        event.setStartDateTime(request.getStart());
        event.setEndDateTime(request.getEnd());
        event.setSubject(request.getSubject());

        final SortedSet<String> appointmentAttendees = new TreeSet<String>();
        for (final Attendee attendee : request.getRequiredAttendees()) {
            appointmentAttendees.add(attendee.getAddress());
        }
        for (final Attendee attendee : request.getOptionalAttendees()) {
            appointmentAttendees.add(attendee.getAddress());
        }

        event.setEmailAddresses(appointmentAttendees);
        event.setBody(extractPlainTextBody(request));
        return event;
    }

    /**
     * Compare the appointment with the reservation regarding date, time, duration, subject and
     * attendees.
     *
     * @param reservation the reservation linked to the appointment
     * @param appointment the appointment on the organizer's calendar
     * @return true if equal, false if different
     */
    public static boolean isEquivalent(final IReservation reservation,
            final Appointment appointment) {
        try {
            return UpdateType.NONE.equals(getUpdateType(reservation, toCalendarEvent(appointment)));
        } catch (final ServiceLocalException exception) {
            // @translatable
            throw new CalendarException("Error accessing appointment properties.", exception,
                AppointmentEquivalenceChecker.class);
        }
    }

    /**
     * Compare the meeting request with the reservation regarding date, time, duration, subject and
     * attendees.
     *
     * @param reservation the reservation linked to the appointment
     * @param request the meeting request
     * @return true if equal, false if different
     */
    public static boolean isEquivalent(final IReservation reservation,
            final MeetingRequest request) {
        try {
            return UpdateType.NONE.equals(getUpdateType(reservation, toCalendarEvent(request)));
        } catch (final ServiceLocalException exception) {
            throw new CalendarException("Error accessing meeting request properties.", exception,
                AppointmentEquivalenceChecker.class);
        }
    }

    /**
     * Determine the type of update required for the appointment by comparing with the reservation
     * regarding date, time, duration, subject and attendees.
     *
     * @param reservation the reservation linked to the appointment
     * @param event the calendar event
     * @return appointment update type (NONE if appointment and reservation are equivalent)
     */
    public static UpdateType getUpdateType(final IReservation reservation,
            final ICalendarEvent event) {
        final boolean dateTimeEqual = compareDateTime(reservation, event);
        final boolean subjectEqual = compareSubject(reservation, event);
        final boolean attendeesEqual =
                compareToReservationAttendees(reservation, event.getEmailAddresses());
        final boolean bodyEqual = compareBody(reservation, event);

        UpdateType updateType = UpdateType.NONE;

        if (dateTimeEqual && subjectEqual && bodyEqual && !attendeesEqual) {
            updateType = UpdateType.ATTENDEES_ONLY;
        } else if (dateTimeEqual && !(subjectEqual && bodyEqual)) {
            updateType = UpdateType.SUBJECT_LOCATION_BODY;
        } else if (!dateTimeEqual) {
            updateType = UpdateType.FULL;
        }

        return updateType;
    }

    /**
     * Compare the reservation name to the appointment subject.
     *
     * @param reservation the reservation object
     * @param appointment the Exchange appointment
     * @return true if equal, false if different
     */
    public static boolean compareSubject(final IReservation reservation,
            final ICalendarEvent appointment) {
        final String reservationName = StringUtil.notNull(appointment.getSubject()).trim();
        final String subject = StringUtil.notNull(reservation.getReservationName()).trim();
        return reservationName.equals(subject);
    }

    /**
     * Compare the start and end date/time of the appointment to the reservation. Update the
     * reservation object if differences are found and updateReservation is true.
     *
     * @param reservation the reservation object
     * @param appointment the Exchange appointment
     * @return true if equivalent, false if different
     */
    public static boolean compareDateTime(final IReservation reservation,
            final ICalendarEvent appointment) {
        boolean dateTimeEqual = true;

        // Get the UTC time period. Do not convert again if already in UTC.
        TimePeriod timePeriod = reservation.getTimePeriod();
        if (!Constants.TIMEZONE_UTC.equals(reservation.getTimeZone())) {
            timePeriod =
                    ReservationUtils.getTimePeriodInTimeZone(reservation, Constants.TIMEZONE_UTC);
        }

        if (!timePeriod.getStartDateTime().equals(appointment.getStartDateTime())) {
            dateTimeEqual = false;
        }
        if (!timePeriod.getEndDateTime().equals(appointment.getEndDateTime())) {
            dateTimeEqual = false;
        }
        return dateTimeEqual;
    }

    /**
     * Compare the attendees in the given reservation to the given set of email addresses. Ignores
     * the presence of the organizer email in the list of exchange attendees if it's not already in
     * the list of reservation attendees.
     *
     * @param reservation the reservation for which to compare attendees
     * @param appointmentAttendees the list of attendees
     * @return true if equivalent, false if different
     */
    public static boolean compareToReservationAttendees(final IReservation reservation,
            final SortedSet<String> appointmentAttendees) {

        final SortedSet<String> reservationAttendees = new TreeSet<String>();
        if (reservation.getAttendees() != null) {
            final String[] attendees = reservation.getAttendees().split(";");
            for (final String attendee : attendees) {
                if (StringUtil.notNullOrEmpty(attendee)) {
                    reservationAttendees.add(attendee.trim());
                }
            }
        }
        if (!reservationAttendees.contains(reservation.getEmail())) {
            /*
             * Don't count the organizer as an attendee when checking equivalence. He's listed as an
             * attendee in the meeting if he doesn't have a mailbox on the connected Exchange
             * server. In WebCentral the organizer shouldn't be in the list of attendees.
             */
            appointmentAttendees.remove(reservation.getEmail());
        }

        return appointmentAttendees.size() == reservationAttendees.size()
                && appointmentAttendees.containsAll(reservationAttendees)
                && reservationAttendees.containsAll(appointmentAttendees);
    }

    /**
     * Compare the body text of the appointment with the reservation comments.
     *
     * @param reservation the reservation
     * @param appointment the appointment
     * @return true if equal, false if different
     */
    public static boolean compareBody(final IReservation reservation,
            final ICalendarEvent appointment) {

        /*
         * Since Exchange apparently inserts newlines before the hyperlinks in the conference calls
         * template, ignore all newlines when checking for changes. Note carriage returns have
         * already been removed.
         */
        final String body =
                StringUtil.notNull(appointment.getBody()).replaceAll(NEWLINE, "").trim();
        final String comments = StringUtil.notNull(reservation.getComments())
            .replaceAll(" " + NEWLINE, "").replaceAll(NEWLINE, "").trim();
        return comments.equals(body);
    }

    /**
     * Extract the plain text body from the appointment.
     *
     * @param appointment the appointment to get the body for
     * @return plain text body content
     * @throws ServiceLocalException when the body cannot be read
     */
    private static String extractPlainTextBody(final Item appointment)
            throws ServiceLocalException {
        final MessageBody body = appointment.getBody();
        String bodyText = body.toString();
        if (BodyType.HTML.equals(body.getBodyType())) {
            bodyText = StringTranscoder.stripHtml(bodyText);
        }
        return bodyText.trim();
    }

}
