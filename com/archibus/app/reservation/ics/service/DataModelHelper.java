package com.archibus.app.reservation.ics.service;

import java.text.DecimalFormat;
import java.util.*;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.ics.domain.*;
import com.archibus.app.reservation.service.helpers.WebCentralCalendarServiceHelper;
import com.archibus.app.reservation.util.TimeZoneCache;
import com.archibus.utility.StringUtil;

import net.fortuna.ical4j.model.TimeZoneRegistryFactory;

/**
 * Copyright (C) ARCHIBUS, Inc. All rights reserved.
 */
/**
 * Utility class. Provides methods to set the email data model
 * <p>
 *
 * @author PROCOS
 * @since 23.2
 *
 */
public final class DataModelHelper {

    /** Date list data model id. */
    private static final String DT_LIST_MSG = "dt_list_msg";

    /** Room comments data model id. */
    private static final String RM_COMMENTS = "rm_comments";

    /** Timezone offset data model id. */
    private static final String OFFSET = "offset";

    /** Location data model id. */
    private static final String LOCATION = "location";

    /** Data model id for the update or cancel message fragment. */
    private static final String UPDATE_OR_CANCEL = "update_or_cancel";

    /** Data model id for the create or cancel message fragment. */
    private static final String CREATE_OR_CANCEL = "create_or_cancel";

    /** User message data model id. */
    private static final String USER_MSG = "user_msg";

    /** Reservation name (subject) data model id. */
    private static final String RESERVATION_NAME = "reservation_name";

    /** User data model container id. */
    private static final String USER = "user";

    /** GMT offset prefix. */
    private static final String GMT = "GMT";

    /** Separator inserted between fragments of single-line text. */
    private static final String SEPARATOR = " - ";

    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private DataModelHelper() {
    }

    /**
     * Add the required information to the data model to send in the email.
     *
     * @param messages the messages to use
     * @param emailModel the email type model
     * @param reservation the reservation
     * @param message the user message to add
     * @param timeZoneCache cache for determining building time zones
     * @return the data model to be used
     */
    public static Map<String, Object> prepareDataModel(final Map<String, String> messages,
            final EmailModel emailModel, final RoomReservation reservation, final String message,
            final TimeZoneCache timeZoneCache) {

        final Map<String, Object> dataModel = new HashMap<String, Object>();

        /*
         * Always set the requested by employee, even if cancelled by someone else. Attendees don't
         * need to know who pushed the button.
         */
        dataModel.put(USER, reservation.getRequestedBy());

        // always set the ${reservation_name!""} parameter for the subject
        if (StringUtil.notNullOrEmpty(reservation.getReservationName())) {
            dataModel.put(RESERVATION_NAME, reservation.getReservationName() + SEPARATOR);
        } else {
            dataModel.put(RESERVATION_NAME, "");
        }

        if (StringUtil.notNullOrEmpty(message)) {
            dataModel.put(USER_MSG, message);
        } else {
            dataModel.put(USER_MSG, "");
        }

        MeetingLocationModel primaryLocation = null;
        // Indicate that's a recurrent reservation
        if (emailModel.isAllRecurrences()) {
            primaryLocation =
                    addRecurringInfo(messages, emailModel, reservation, dataModel, timeZoneCache);

            dataModel.put(CREATE_OR_CANCEL, messages.get(IcsConstants.BODY_INVREC_MSG));
        } else {
            primaryLocation = addSingleInfo(reservation, timeZoneCache, dataModel);

            dataModel.put(CREATE_OR_CANCEL, messages.get(IcsConstants.BODY_INVITE_MSG));
        }

        if (emailModel.isCancel()) {
            // Indicate that's a cancelled reservation
            dataModel.put(UPDATE_OR_CANCEL, SEPARATOR + messages.get(IcsConstants.SUBJECT_CAN_MSG));
            dataModel.put(CREATE_OR_CANCEL, messages.get(IcsConstants.BODY_CANCEL_MSG));
        } else if (emailModel.isChange()) {
            // Indicate that's an updated reservation
            dataModel.put(UPDATE_OR_CANCEL, SEPARATOR + messages.get(IcsConstants.SUBJECT_UPD_MSG));
        }

        if (emailModel.isDisconnect() || reservation.getRoomAllocations().isEmpty()) {
            dataModel.put(LOCATION, "");
        } else if (emailModel.isConferenceCall()) {
            dataModel.put(LOCATION, messages.get(IcsConstants.CONF_CALL_MEETING_LOCATION));
        } else {
            dataModel.put(LOCATION, String.format("%s-%s-%s", primaryLocation.getBuilding(),
                primaryLocation.getFloor(), primaryLocation.getRoom()));
        }

        final Date startDate = getStartDate(emailModel, reservation);

        dataModel.put(IcsConstants.DATE_START,
            WebCentralCalendarServiceHelper.getDateFormatted(reservation.getStartDate()));
        dataModel.put(Constants.TIME_START_FIELD_NAME,
            WebCentralCalendarServiceHelper.getTimeFormatted(reservation.getStartTime()));
        dataModel.put(Constants.TIME_END_FIELD_NAME,
            WebCentralCalendarServiceHelper.getTimeFormatted(reservation.getEndTime()));

        final String tzone = primaryLocation.getTimezone();
        dataModel.put("timezone", tzone);
        final TimePeriod timePeriod = new TimePeriod(startDate, null, reservation.getStartTime(),
            reservation.getEndTime());

        // calculate the offset for this date from the time zone information
        dataModel.put(OFFSET,
            formatTimezoneOffset(getOffset(tzone, timePeriod.getStartDateTime().getTime())));

        dataModel.put(IcsConstants.COMMENTS, reservation.getComments());

        return dataModel;
    }

    /**
     * Add info to the data model that's specific for a single reservation.
     *
     * @param reservation the reservation
     * @param timeZoneCache the time zone cache
     * @param dataModel the data model
     * @return the populated location data model
     */
    private static MeetingLocationModel addSingleInfo(final RoomReservation reservation,
            final TimeZoneCache timeZoneCache, final Map<String, Object> dataModel) {
        final MeetingLocationModel primaryLocation;
        // APP-1983 - reservation might not have a room
        if (reservation.getRoomAllocations().isEmpty()) {
            final RoomAllocation allocation = new RoomAllocation();
            allocation.setBlId(reservation.determineBuildingId());
            primaryLocation =
                    MeetingInformationHelper.getMeetingLocationModel(allocation, timeZoneCache);
        } else {
            final RoomAllocation allocation = reservation.getRoomAllocations().get(0);
            primaryLocation =
                    MeetingInformationHelper.getMeetingLocationModel(allocation, timeZoneCache);
            addRoomComments(dataModel, allocation);
        }
        return primaryLocation;
    }

    /**
     * Prepare a simplified data model for the ICS summary and description.
     *
     * @param messages the messages map
     * @param emailModel the email type model
     * @param dataModel the email data model
     * @return the ICS simplified data model map
     */
    public static Map<String, Object> getIcsDataModelMap(final Map<String, String> messages,
            final EmailModel emailModel, final Map<String, Object> dataModel) {

        final Map<String, Object> icsDataModel = new HashMap<String, Object>();
        if (dataModel.containsKey(IcsConstants.RESERVATION_NAME)) {
            icsDataModel.put(IcsConstants.RESERVATION_NAME,
                dataModel.get(IcsConstants.RESERVATION_NAME));
        }

        icsDataModel.put(IcsConstants.DATE_START, dataModel.get(IcsConstants.DATE_START));
        icsDataModel.put(Constants.TIME_START_FIELD_NAME,
            dataModel.get(Constants.TIME_START_FIELD_NAME));
        icsDataModel.put(Constants.TIME_END_FIELD_NAME,
            dataModel.get(Constants.TIME_END_FIELD_NAME));
        icsDataModel.put("time_offset", dataModel.get(OFFSET));

        if (dataModel.containsKey(Constants.TYPE_RECURRING)) {
            icsDataModel.put(Constants.TYPE_RECURRING, dataModel.get(Constants.TYPE_RECURRING));
        } else {
            icsDataModel.put(Constants.TYPE_RECURRING, "");
        }

        if (emailModel.isCancel()) {
            // Indicate that's a cancelled reservation
            icsDataModel.put(UPDATE_OR_CANCEL,
                SEPARATOR + messages.get(IcsConstants.SUBJECT_CAN_MSG));
        } else if (emailModel.isChange()) {
            // Indicate that's an updated reservation
            icsDataModel.put(UPDATE_OR_CANCEL,
                SEPARATOR + messages.get(IcsConstants.SUBJECT_UPD_MSG));
        }

        final String messageId;
        if (emailModel.isCancel()) {
            messageId = messages.get(IcsConstants.BODY_CANCEL_MSG);
        } else if (emailModel.isRecurring()) {
            messageId = messages.get(IcsConstants.BODY_INVREC_MSG);
        } else {
            messageId = messages.get(IcsConstants.BODY_INVITE_MSG);
        }
        icsDataModel.put(CREATE_OR_CANCEL, messageId);

        icsDataModel.put(USER, dataModel.get(USER));

        icsDataModel.put(LOCATION, dataModel.get(LOCATION));

        if (dataModel.containsKey(IcsConstants.COMMENTS)) {
            icsDataModel.put(IcsConstants.COMMENTS, dataModel.get(IcsConstants.COMMENTS));
        }

        if (dataModel.containsKey(RM_COMMENTS)) {
            icsDataModel.put(RM_COMMENTS, dataModel.get(RM_COMMENTS));
        }

        return icsDataModel;
    }

    /**
     * Get the Start Date for the reservation or recurring reservation.
     *
     * @param emailModel the email type model
     * @param reservation the reservation
     * @return the start date
     */
    private static Date getStartDate(final EmailModel emailModel,
            final RoomReservation reservation) {
        final Date startDate;
        if (emailModel.isAllRecurrences() && reservation.getRecurrence() != null) {
            startDate = reservation.getRecurrence().getStartDate();
        } else {
            startDate = reservation.getStartDate();
        }
        return startDate;
    }

    /**
     * Add the Room Comments to the data model, if any.
     *
     * @param dataModel the data model where to add the comments
     * @param allocation the room allocation to check for comments
     */
    private static void addRoomComments(final Map<String, Object> dataModel,
            final RoomAllocation allocation) {
        if (StringUtil.notNullOrEmpty(allocation.getComments())) {
            dataModel.put(RM_COMMENTS, allocation.getComments());
        }
    }

    /**
     * Add the information for the recurring invitations.
     *
     * @param messages the messages map
     * @param emailModel the email type model
     * @param reservation the reservation
     * @param dataModel the data model where to add the information
     * @param timeZoneCache cache for determining the time zone of a building
     * @return the location model
     */
    private static MeetingLocationModel addRecurringInfo(final Map<String, String> messages,
            final EmailModel emailModel, final RoomReservation reservation,
            final Map<String, Object> dataModel, final TimeZoneCache timeZoneCache) {

        dataModel.put("recurring", SEPARATOR + messages.get(IcsConstants.SUBJECT_REC_MSG));
        MeetingLocationModel primaryLocation = null;

        // add the message for the list of dates that is being reserved
        // or canceled
        if (emailModel.isCancel()) {
            dataModel.put(DT_LIST_MSG, messages.get(IcsConstants.BODY_CAN_LIST_MSG));
        } else {
            dataModel.put(DT_LIST_MSG, messages.get(IcsConstants.BODY_DTS_LIST_MSG));
        }

        final StringBuilder datesList = new StringBuilder();
        for (final RoomReservation occurrence : reservation.getCreatedReservations()) {
            if (occurrence.getRoomAllocations().isEmpty()) {
                // this occurrence has a conflict, don't show it in the list
                continue;
            }
            final RoomAllocation allocation = occurrence.getRoomAllocations().get(0);
            final MeetingLocationModel locationModel =
                    MeetingInformationHelper.getMeetingLocationModel(allocation, timeZoneCache);

            addDateListEntry(datesList, allocation, messages, locationModel.getTimezone());

            if (primaryLocation == null) {
                primaryLocation = locationModel;
                if (!dataModel.containsKey(RM_COMMENTS)) {
                    addRoomComments(dataModel, allocation);
                }
            }
        }

        if (datesList.length() > 0) {
            // remove extra newline at the end
            datesList.setLength(datesList.length() - 1);
        }
        dataModel.put("dt_list", datesList.toString());
        return primaryLocation;
    }

    /**
     * Add the entry information to the dates list.
     *
     * @param builder the dates list builder
     * @param allocation the allocation data record
     * @param messages the messages map
     * @param timezone the time zone string
     */
    private static void addDateListEntry(final StringBuilder builder,
            final RoomAllocation allocation, final Map<String, String> messages,
            final String timezone) {

        // calculate the offset for this date
        final int offset = getOffset(timezone, allocation.getStartDateTime().getTime());

        builder.append("  * ").append(messages.get(IcsConstants.BODY_START_DT_MSG)).append(' ')
            .append(WebCentralCalendarServiceHelper.getDateFormatted(allocation.getStartDate()))
            .append(' ').append(messages.get(IcsConstants.BODY_START_TM_MSG)).append(' ')
            .append(WebCentralCalendarServiceHelper.getTimeFormatted(allocation.getStartTime()))
            .append(' ').append(GMT).append(formatTimezoneOffset(offset)).append(' ')
            .append(messages.get(IcsConstants.BODY_END_TM_MSG)).append(' ')
            .append(WebCentralCalendarServiceHelper.getTimeFormatted(allocation.getEndTime()))
            .append(' ').append(GMT).append(formatTimezoneOffset(offset)).append('\n');
    }

    /**
     * Convert time zone offset to readable hour difference.
     *
     * @param offset the time zone offset
     * @return the formatted offset in hours
     */
    private static String formatTimezoneOffset(final int offset) {

        final DecimalFormat timeZoneFormatter = new DecimalFormat("00");
        final int minutes = -(offset / IcsConstants.CONSTANT60000);
        final int hourlyOffset = -minutes / IcsConstants.CONSTANT60;

        final int absOffset = Math.abs(minutes);

        final StringBuilder builder = new StringBuilder();

        if (minutes < 0) {
            builder.append('+');
        }
        builder.append(timeZoneFormatter.format(hourlyOffset));

        if ((absOffset % IcsConstants.CONSTANT60) > 0) {
            builder.append(':')
                .append(timeZoneFormatter.format(absOffset % IcsConstants.CONSTANT60));
        }

        return builder.toString();
    }

    /**
     * Get the time zone offset for a date/time.
     *
     * @param timezone the time zone
     * @param datetime the date/time
     * @return the offset
     */
    private static int getOffset(final String timezone, final long datetime) {
        final TimeZone timeZone =
                TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone(timezone);
        if (timeZone == null) {
            // @translatable
            throw new CalendarException("Unknown time zone [{0}] - cannot determine offset",
                DataModelHelper.class, timezone);
        }
        return timeZone.getOffset(datetime);
    }

}
