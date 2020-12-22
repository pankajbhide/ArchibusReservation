package com.archibus.app.reservation.service;

import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.log4j.Logger;
import org.json.*;

import com.archibus.app.reservation.dao.IRoomReservationDataSource;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.service.helpers.AttendeeTimelineServiceHelper;
import com.archibus.app.reservation.util.TimeZoneConverter;
import com.archibus.context.ContextStore;
import com.archibus.utility.StringUtil;

/**
 * Copyright (C) ARCHIBUS, Inc. All rights reserved.
 */
/**
 * Provides information on attendee availability to display to the user wanting to create a
 * reservation.
 * <p>
 * Managed by Spring, has prototype scope. Configured in reservation-services.xml file.
 *
 * @author Yorik Gerlo
 * @since 24.3
 */
public class AttendeeCalendarService {

    /** The Constant DATE_FORMAT. */
    protected static final String DATE_FORMAT = "yyyy-MM-dd";

    /** The Constant TIME_FORMAT. */
    protected static final String TIME_FORMAT = "1899-12-30 HH:mm";

    /** Property of the search filter indicating the time zone. */
    private static final String JSON_TIMEZONE_ID = "timezone_id";

    /** JSON property name for the id of an event. */
    private static final String JSON_EVENT_ID = "eventId";

    /** JSON property name for the id of a resource. */
    private static final String JSON_RESOURCE_ID = "resourceId";

    /** JSON property name for the row index of a resource. */
    private static final String JSON_ROW = "row";

    /** The Constant ATTENDEES. */
    private static final String RESOURCES = "resources";

    /** The Constant EVENTS. */
    private static final String EVENTS = "events";

    /** The Constant MESSAGE. */
    private static final String MESSAGE = "message";

    /** The availability service. */
    private IAvailabilityService availabilityService;

    /** The room reservation data source. */
    private IRoomReservationDataSource roomReservationDataSource;

    /** The logger. */
    private final Logger logger = Logger.getLogger(this.getClass());

    /**
     * Set the Attendee Availability Service.
     *
     * @param availabilityService the availability service
     */
    public void setAvailabilityService(final IAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    /**
     * Sets the reservation data source for retrieving existing reservations.
     *
     * @param roomReservationDataSource the reservation data source
     */
    public void setRoomReservationDataSource(
            final IRoomReservationDataSource roomReservationDataSource) {
        this.roomReservationDataSource = roomReservationDataSource;
    }

    /**
     * Retrieve free-busy information for the given attendee email addresses for the given date
     * range (inclusive) in the time zone determined by the location filter.
     *
     * @param startDate start of the date range
     * @param endDate end of the date range (inclusive)
     * @param attendeeEmails list of attendee email addresses
     * @param locationFilter location filter to determine the time zone (via timezone_id or bl_id)
     * @param reservationId reservation id of the existing reservation to exclude from the result
     * @return attendee free-busy info
     */
    public JSONObject loadAttendeeEvents(final Date startDate, final Date endDate,
            final JSONObject locationFilter, final List<String> attendeeEmails,
            final Integer reservationId) {

        final List<String> validEmails =
                AttendeeTimelineServiceHelper.extractValidEmails(attendeeEmails);
        final JSONObject result = new JSONObject();
        final JSONArray resources = new JSONArray();
        final JSONArray events = new JSONArray();
        final JSONArray failures = new JSONArray();
        result.put(RESOURCES, resources);
        result.put(EVENTS, events);
        result.put(MESSAGE, failures);

        int rowIndex = 0;
        for (final String email : validEmails) {
            resources.put(createAttendeeResource(email, rowIndex));
            ++rowIndex;
        }

        final String userEmail = ContextStore.get().getUser().getEmail();
        final TimeZone timeZone = getTimeZoneFromFilter(locationFilter);

        try {
            final RoomReservation reservation = this.getExistingReservation(reservationId);
            final Map<String, AttendeeAvailability> availabilities =
                    this.availabilityService.findAttendeeAvailability(reservation, startDate,
                        endDate, timeZone, userEmail, validEmails);

            final String uniqueId = getUniqueIdForAttendeeEvents(reservation);
            rowIndex = 0;
            for (final String email : validEmails) {
                this.processAvailability(email, availabilities.get(email), events, rowIndex,
                    failures, uniqueId);
                ++rowIndex;
            }
        } catch (final CalendarException exception) {
            // General failure accessing the calendar: show warning for all attendee email
            // addresses.
            this.logger.warn("Could not retrieve free/busy information from calendar", exception);
            for (final String email : validEmails) {
                failures.put(email);
            }
        }

        return result;
    }

    /**
     * Process the availability info for a single attendee.
     * 
     * @param email the email of the attendee
     * @param availability the availability info
     * @param events container for storing events
     * @param rowIndex the row for this attendee
     * @param failures container for storing failures
     * @param uniqueId the unique id to exclude
     */
    private void processAvailability(final String email, final AttendeeAvailability availability,
            final JSONArray events, final int rowIndex, final JSONArray failures,
            final String uniqueId) {
        if (availability == null) {
            this.logger.debug("No free/busy info for " + email);
            failures.put(email);
        } else if (availability.isSuccessful()) {
            for (final ICalendarEvent calendarEvent : availability.getCalendarEvents()) {
                if (!(StringUtil.notNullOrEmpty(uniqueId)
                        && uniqueId.equals(calendarEvent.getEventId()))) {
                    events.put(createAttendeeCalendarEvent(calendarEvent, rowIndex));
                }
            }
        } else {
            // no info was available for this email
            this.logger.debug("Retrieving free/busy info for " + email + " failed - "
                    + availability.getErrorDetails());
            failures.put(email);
        }
    }

    /**
     * Retrieve the existing reservation with the given id from the database (no time zone
     * conversion). Returns null if not found and if reservationId is null.
     *
     * @param reservationId the reservation id
     * @return the room reservation (if found)
     */
    private RoomReservation getExistingReservation(final Integer reservationId) {
        RoomReservation reservation = null;
        if (reservationId != null && reservationId > 0) {
            reservation = this.roomReservationDataSource.getActiveReservation(reservationId);
        }
        return reservation;
    }

    /**
     * Get the requested time zone from the given JSON location filter. It can be explicitly
     * requested or derived from the building id.
     *
     * @param locationFilter the JSON location filter
     * @return the time zone
     */
    private TimeZone getTimeZoneFromFilter(final JSONObject locationFilter) {
        TimeZone timeZone = null;
        if (locationFilter.has(JSON_TIMEZONE_ID)) {
            final String timeZoneId = locationFilter.getString(JSON_TIMEZONE_ID);
            if (StringUtil.isNullOrEmpty(timeZoneId)) {
                timeZone = TimeZone.getDefault();
            } else {
                timeZone = TimeZone.getTimeZone(timeZoneId);
            }
        } else {
            final String buildingId = locationFilter
                .getString(com.archibus.app.reservation.dao.datasource.Constants.BL_ID_FIELD_NAME);
            final String timeZoneId = TimeZoneConverter.getTimeZoneIdForBuilding(buildingId);
            timeZone = TimeZone.getTimeZone(timeZoneId);
        }
        return timeZone;
    }

    /**
     * Creates the attendee resource.
     *
     * @param email the email
     * @param rowIndex the row index
     * @return the JSON object
     */
    public static JSONObject createAttendeeResource(final String email, final int rowIndex) {
        final JSONObject resource = new JSONObject();
        resource.put(JSON_ROW, rowIndex);
        resource.put(JSON_RESOURCE_ID, email);
        resource.put("email", email);

        return resource;
    }

    /**
     * Creates the attendee calendar event in JSON format.
     *
     * @param calendarEvent the calendar event
     * @param rowIndex the row index for matching with the attendee definition
     * @return the JSON object
     */
    private static JSONObject createAttendeeCalendarEvent(final ICalendarEvent calendarEvent,
            final int rowIndex) {
        final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.ENGLISH);
        final SimpleDateFormat timeFormat = new SimpleDateFormat(TIME_FORMAT, Locale.ENGLISH);

        final JSONObject event = new JSONObject();
        event.put(JSON_ROW, rowIndex);
        event.put(JSON_EVENT_ID, calendarEvent.getEventId());
        event.put("startDate", dateFormat.format(calendarEvent.getStartDate()));
        event.put("startTime", timeFormat.format(calendarEvent.getStartTime()));

        String endTime = timeFormat.format(calendarEvent.getEndTime());
        if ("1899-12-30 00:00".equals(endTime)) {
            endTime = "1899-12-30 23:59";
            final Calendar calendar = Calendar.getInstance();
            calendar.setTime(calendarEvent.getEndDate());
            calendar.add(Calendar.DATE, -1);
            calendarEvent.setEndDate(calendar.getTime());
        }

        event.put("endDate", dateFormat.format(calendarEvent.getEndDate()));
        event.put("endTime", endTime);

        return event;
    }

    /**
     * Get the unique id to use for attendee events. It can be the outlook unique id, conference id
     * or reservation id.
     *
     * @param reservation the reservation
     * @return the unique id for the reservation
     */
    private static String getUniqueIdForAttendeeEvents(final RoomReservation reservation) {
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
