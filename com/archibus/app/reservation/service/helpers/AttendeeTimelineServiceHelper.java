package com.archibus.app.reservation.service.helpers;

import java.util.*;

import org.apache.log4j.Logger;
import org.json.*;

import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.*;
import com.archibus.app.reservation.service.IAvailabilityService;
import com.archibus.app.reservation.util.TimelineHelper;
import com.archibus.context.ContextStore;
import com.archibus.utility.StringUtil;

/**
 * The Class AttendeeTimelineServiceHelper.
 */
public class AttendeeTimelineServiceHelper {

    /** Activity ID for reservations application. */
    private static final String ACTIVITY_ID = "AbWorkplaceReservations";

    /** The Constant EVENTS. */
    private static final String EVENTS = "events";

    /** The Constant MESSAGE. */
    private static final String MESSAGE = "message";

    /** The default max. number of occurrences to check attendee availability for. */
    private static final int DEFAULT_MAX_OCCURRENCES = 10;

    /** The availability service. */
    private IAvailabilityService availabilityService;

    /** The logger. */
    private final Logger logger = Logger.getLogger(this.getClass());

    /**
     * Get the valid emails from the given list of emails.
     *
     * @param emails the list of original email addresses
     * @return the valid emails in the original list
     */
    public static List<String> extractValidEmails(final List<String> emails) {
        final List<String> validEmails = new ArrayList<String>();
        for (final String email : emails) {
            // check for valid e-mail
            if (!StringUtil.isNullOrEmpty(email)) {
                validEmails.add(email);
            }
        }
        return validEmails;
    }

    /**
     * Get the maximum number of occurrences to check when loading attendee availability.
     *
     * @return maximum number of occurrences to check
     */
    public static int getMaxRecurrencesToCheckFreeBusy() {
        return com.archibus.service.Configuration.getActivityParameterInt(ACTIVITY_ID,
            "MaxRecurrencesToCheckFreeBusy", DEFAULT_MAX_OCCURRENCES);
    }

    /**
     * Load attendee timeline.
     *
     * @param timeline the time line JSON object
     * @param startDate the start date
     * @param endDate the end date
     * @param recurrenceRule the recurrence rule
     * @param emails the emails
     * @param reservation the existing reservation (if any)
     * @param timeZone the time zone
     */
    public void loadAttendeeTimeline(final JSONObject timeline, final Date startDate,
            final Date endDate, final String recurrenceRule, final List<String> emails,
            final RoomReservation reservation, final TimeZone timeZone) {

        Recurrence recurrence = null;
        if (StringUtil.notNullOrEmpty(recurrenceRule)) {
            recurrence = RecurrenceParser.parseRecurrence(startDate, endDate, recurrenceRule);
        }

        final int maxRecurrencesToCheckFreeBusy =
                AttendeeTimelineServiceHelper.getMaxRecurrencesToCheckFreeBusy();
        final String userEmail = ContextStore.get().getUser().getEmail();

        // Track for which attendees no information was available, avoid duplicates when recurring.
        final HashSet<String> failedAttendees = new HashSet<String>();
        // create events for the start date
        this.createAttendeeEvents(reservation, startDate, timeline, timeZone, userEmail, emails,
            failedAttendees);

        // make final variables
        final TimeZone localTimeZone = timeZone;
        final RoomReservation roomReservation = reservation;
        // final container for counting occurrences
        final int[] counter = new int[] { 1 };

        if (recurrence instanceof AbstractIntervalPattern
                && counter[0] < maxRecurrencesToCheckFreeBusy) {
            final AbstractIntervalPattern pattern = (AbstractIntervalPattern) recurrence;
            pattern.loopThroughRepeats(new AbstractIntervalPattern.OccurrenceAction() {
                // handle all occurrence events
                @Override
                public boolean handleOccurrence(final Date date) throws ReservationException {
                    // create events for this date
                    AttendeeTimelineServiceHelper.this.createAttendeeEvents(roomReservation, date,
                        timeline, localTimeZone, userEmail, emails, failedAttendees);

                    return ++counter[0] < maxRecurrencesToCheckFreeBusy;
                }
            });
        }

        final JSONArray failures = timeline.getJSONArray(MESSAGE);
        for (final String failedAttendee : failedAttendees) {
            failures.put(failedAttendee);
        }
    }

    /**
     * Load the attendee time line when editing recurrent reservation.
     *
     * @param timeline the time line JSON object
     * @param emails the email addresses of the attendees
     * @param timeZone the time zone
     * @param existingOccurrences the existing reservation occurrences to load availability for
     */
    public void loadAttendeeTimelineEditRecurrence(final JSONObject timeline,
            final List<String> emails, final TimeZone timeZone,
            final List<RoomReservation> existingOccurrences) {

        final int maxRecurrencesToCheckFreeBusy =
                AttendeeTimelineServiceHelper.getMaxRecurrencesToCheckFreeBusy();
        final String userEmail = ContextStore.get().getUser().getEmail();

        // Track for which attendees no information was available, avoid duplicates when recurring.
        final HashSet<String> failedAttendees = new HashSet<String>();

        // loop through all occurrences
        int occurrenceIndex = 0;
        for (final RoomReservation existingReservation : existingOccurrences) {
            this.createAttendeeEvents(existingReservation, existingReservation.getStartDate(),
                timeline, timeZone, userEmail, emails, failedAttendees);

            ++occurrenceIndex;
            if (occurrenceIndex >= maxRecurrencesToCheckFreeBusy) {
                break;
            }
        }

        final JSONArray failures = timeline.getJSONArray(MESSAGE);
        for (final String failedAttendee : failedAttendees) {
            failures.put(failedAttendee);
        }
    }

    /**
     * Set the Attendee Availability Service.
     *
     * @param availabilityService the availability service
     */
    public void setAvailabilityService(final IAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    /**
     * Create attendee events.
     *
     * @param reservation the reservation being edited
     * @param date the date
     * @param timeline the timeline
     * @param timeZone the time zone
     * @param userEmail the current user's email address
     * @param emails the attendee email addresses
     * @param failedAttendees set of email addresses for whom no info is available (in and out)
     */
    private void createAttendeeEvents(final RoomReservation reservation, final Date date,
            final JSONObject timeline, final TimeZone timeZone, final String userEmail,
            final List<String> emails, final Set<String> failedAttendees) {

        final String uniqueId = this.getUniqueIdForAttendeeEvents(reservation);

        // get the calendar events for the attendees
        final Map<String, AttendeeAvailability> calendarEventsPerAttendee = this.availabilityService
            .findAttendeeAvailability(reservation, date, date, timeZone, userEmail, emails);

        int currentIndex = 0;
        for (final String email : emails) {
            final AttendeeAvailability availability = calendarEventsPerAttendee.get(email);
            if (availability.isSuccessful()) {
                final List<ICalendarEvent> calendarEvents = availability.getCalendarEvents();
                for (final ICalendarEvent calendarEvent : calendarEvents) {
                    if (!(StringUtil.notNullOrEmpty(uniqueId)
                            && uniqueId.equals(calendarEvent.getEventId()))) {
                        final JSONArray events = timeline.getJSONArray(EVENTS);
                        events.put(TimelineHelper.createAttendeeCalendarEvent(timeline,
                            calendarEvent, currentIndex, date));
                    }
                }
            } else {
                // no info was available for this email
                this.logger.debug(
                    "No free/busy info for " + email + " - " + availability.getErrorDetails());
                failedAttendees.add(email);
            }
            ++currentIndex;
        }
    }

    /**
     * Get the unique id to use for attendee events. It can be the outlook unique id, conference id
     * or reservation id.
     *
     * @param reservation the reservation
     * @return the unique id for the reservation
     */
    private String getUniqueIdForAttendeeEvents(final RoomReservation reservation) {
        String uniqueId = null;
        if (reservation != null) {
            if (StringUtil.notNullOrEmpty(reservation.getUniqueId())) {
                uniqueId = reservation.getUniqueId();
            } else if (reservation.getConferenceId() == null
                    || reservation.getConferenceId() == 0) {
                uniqueId = String.valueOf(reservation.getReserveId());
            } else {
                uniqueId = String.valueOf(reservation.getConferenceId());
            }
        }
        return uniqueId;
    }

}