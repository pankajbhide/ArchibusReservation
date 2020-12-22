package com.archibus.app.reservation.exchange.service;

import java.util.*;

import org.apache.log4j.Logger;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.AttendeeAvailability;
import com.archibus.app.reservation.exchange.util.ExchangeObjectHelper;
import com.archibus.app.reservation.service.IAvailabilityService;
import com.archibus.app.reservation.util.*;
import com.archibus.utility.*;

import microsoft.exchange.webservices.data.*;

/**
 * Provides Free-busy information from a Exchange Server.
 * <p>
 * Managed by Spring
 *
 * @author Yorik Gerlo
 * @since 21.2
 */
public class ExchangeAvailabilityService extends AdminServiceContainer
        implements IAvailabilityService {

    /** The Constant HOURS_24. */
    private static final int HOURS_24 = 24;

    /** The logger. */
    private final Logger logger = Logger.getLogger(this.getClass());

    /** The helper for binding to Exchange Appointments. */
    private AppointmentBinder appointmentBinder;

    /** {@inheritDoc} */
    @Override
    public Map<String, AttendeeAvailability> findAttendeeAvailability(
            final IReservation reservation, final Date startDate, final Date endDate,
            final TimeZone requestedTimeZone, final String userEmail,
            final List<String> attendeeEmails) throws ExceptionBase {

        // Create a time window from 0:00 on the date until 0:00 on the day after.
        // Adjust for the requested time zone to get a UTC time window.
        final Calendar cal = Calendar.getInstance();
        cal.setTime(startDate);
        cal.add(Calendar.MILLISECOND, -requestedTimeZone.getOffset(startDate.getTime()));
        final Date windowStart = cal.getTime();
        cal.setTime(endDate);
        cal.add(Calendar.HOUR, HOURS_24);
        cal.add(Calendar.MINUTE, -1);
        cal.add(Calendar.MILLISECOND, -requestedTimeZone.getOffset(cal.getTimeInMillis()));
        final Date windowEnd = cal.getTime();

        /*
         * The free-busy query is in UTC and only sends dates, not times. So modify the windowEnd to
         * be the beginning of the day after the required windowEnd.
         */
        final Date windowStartDate = TimePeriod.clearTime(windowStart);
        Date windowEndDate = windowEnd;
        if (!TimePeriod.clearTime(windowEnd).equals(windowEnd)) {
            cal.add(Calendar.DATE, 1);
            windowEndDate = TimePeriod.clearTime(cal.getTime());
        }

        // build the list of attendees for sending to Exchange
        final List<AttendeeInfo> attendees = new ArrayList<AttendeeInfo>();
        for (final String email : attendeeEmails) {
            attendees.add(AttendeeInfo.getAttendeeInfoFromString(email));
        }

        Map<String, AttendeeAvailability> availabilities = null;
        // Request FreeBusy information with individual calendar events.
        final AvailabilityOptions options = new AvailabilityOptions();
        options.setRequestedFreeBusyView(FreeBusyViewType.FreeBusy);

        /*
         * Initialize the Exchange Service for the given user. This will fall back to the organizer
         * account if the user doesn't exist in Exchange.
         */
        final ExchangeService exchangeService =
                this.appointmentBinder.getServiceHelper().initializeService(userEmail);

        // Get the free-busy information for the defined time window and attendees.
        final ServiceResponseCollection<microsoft.exchange.webservices.data.AttendeeAvailability> results;
        try {
            results = exchangeService.getUserAvailability(attendees,
                new TimeWindow(windowStartDate, windowEndDate), AvailabilityData.FreeBusy, options)
                .getAttendeesAvailability();
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API method
            // throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            // @translatable
            throw new CalendarException("Error retrieving attendee availability", exception,
                ExchangeAvailabilityService.class, this.getAdminService());
        }
        availabilities = ExchangeObjectHelper.convertAvailability(attendeeEmails, results,
            requestedTimeZone, windowStart, windowEnd);

        /*
         * If we are editing a reservation which is already linked to a meeting in Exchange, mark
         * the corresponding calendar events in the free-busy information, so they can be skipped
         * for display on the time line. Instead the current reservation is displayed in green
         * according to the user's selection.
         */
        if (reservation != null && StringUtil.notNullOrEmpty(reservation.getUniqueId())) {
            this.markCurrentMeeting(reservation, requestedTimeZone, availabilities);
        }

        return availabilities;
    }

    /**
     * Sets the appointment binder to be used for finding free-busy info on the calendar.
     *
     * @param appointmentBinder the new appointment binder
     */
    public void setAppointmentBinder(final AppointmentBinder appointmentBinder) {
        this.appointmentBinder = appointmentBinder;
    }

    /**
     * Mark the meeting linked to the given reservation in the attendees' availabilities. Only the
     * attendees of the given reservation are considered.
     *
     * @param reservation the reservation to mark the meeting for
     * @param availabilityTimeZone the time zone of the availability info
     * @param availabilities the availability info
     */
    private void markCurrentMeeting(final IReservation reservation,
            final TimeZone availabilityTimeZone,
            final Map<String, AttendeeAvailability> availabilities) {
        final TimePeriod timePeriod =
                ReservationUtils.getTimePeriodInTimeZone(reservation, availabilityTimeZone.getID());
        final TimePeriod timePeriodUtc =
                ReservationUtils.getTimePeriodInTimeZone(reservation, Constants.TIMEZONE_UTC);
        final String uniqueId = reservation.getUniqueId();

        if (StringUtil.notNullOrEmpty(reservation.getAttendees())) {
            for (final String email : reservation.getAttendees().split(";")) {
                final AttendeeAvailability availability = availabilities.get(email);
                if (availability != null && availability.isSuccessful()) {
                    // This attendee must be checked.
                    this.markCurrentMeeting(timePeriod, timePeriodUtc, uniqueId, availability);
                }
            }
        }

        // also check the organizer (in case the user wants to review the organizer's availability)
        final AttendeeAvailability organizerAvailability =
                availabilities.get(reservation.getEmail());
        if (organizerAvailability != null && organizerAvailability.isSuccessful()) {
            this.markCurrentMeeting(timePeriod, timePeriodUtc, uniqueId, organizerAvailability);
        }
    }

    /**
     * Mark the meeting with the given time period and unique id in the given availability info.
     *
     * @param timePeriod the time period in local time matching the availability times
     * @param timePeriodUtc the time period in UTC
     * @param uniqueId the unique id of the meeting
     * @param availability the availability information
     */
    private void markCurrentMeeting(final TimePeriod timePeriod, final TimePeriod timePeriodUtc,
            final String uniqueId, final AttendeeAvailability availability) {
        try {
            boolean reservationMatched = false;
            // look for a calendar event with time period that matches the reservation
            for (final ICalendarEvent event : availability.getCalendarEvents()) {
                if (event.getStartDateTime().equals(timePeriod.getStartDateTime())
                        && event.getEndDateTime().equals(timePeriod.getEndDateTime())) {
                    // found it, so check whether it really is the same appointment
                    reservationMatched = findMeetingForEvent(event, timePeriodUtc, uniqueId,
                        availability.getEmail());
                }
                if (reservationMatched) {
                    break;
                }
            }
        } catch (final ServiceLocalException exception) {
            this.logger.warn(
                "Error checking unique id of appointment on calendar of " + availability.getEmail(),
                exception);
            availability.setSuccessful(false);
            availability.setErrorDetails(
                "Error checking unique id of appointment - " + exception.toString());
        }
    }

    /**
     * Find the meeting corresponding to the given calendar event on the attendee's calendar. If an
     * error occurs trying to access the attendee's calendar,
     *
     * @param event the event reported by the free-busy query
     * @param timePeriodUtc the time period of the reservation in UTC (corresponds to the event
     *            time)
     * @param uniqueId identifier of the meeting in Exchange
     * @param email the attendee's email address
     * @return true if the meeting was found, false if the meeting was not found
     * @throws ServiceLocalException if the unique id was not retrieved from Exchange
     */
    private boolean findMeetingForEvent(final ICalendarEvent event, final TimePeriod timePeriodUtc,
            final String uniqueId, final String email) throws ServiceLocalException {
        final List<Appointment> appointments = this.appointmentBinder.findAppointments(email,
            timePeriodUtc.getStartDateTime(), timePeriodUtc.getEndDateTime());
        boolean meetingFound = false;
        for (final Appointment appointment : appointments) {
            if (appointment.getICalUid().equals(uniqueId)) {
                event.setEventId(uniqueId);
                meetingFound = true;
                break;
            }
        }
        return meetingFound;
    }

}
