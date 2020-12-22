package com.archibus.app.reservation.util;

import java.sql.Time;
import java.text.*;
import java.util.*;

import org.json.*;

import com.archibus.app.reservation.domain.*;
import com.archibus.context.ContextStore;
import com.archibus.datasource.*;
import com.archibus.datasource.data.DataRecord;
import com.archibus.db.ViewField;
import com.archibus.jobmanager.EventHandlerContext;
import com.archibus.utility.StringUtil;

/**
 * The Class TimelineHelper.
 */
public final class TimelineHelper {

    /** JSON property name for start hour of the time line. */
    public static final String JSON_TIMELINE_START_HOUR = "timelineStartHour";

    /** JSON property name for end hour of the time line. */
    public static final String JSON_TIMELINE_END_HOUR = "timelineEndHour";

    /** JSON property name for the column index marking the end of availability of a resource. */
    static final String JSON_COLUMN_AVAILABLE_TO = "columnAvailableTo";

    /** JSON property name for the column index marking the start of availability of a resource. */
    static final String JSON_COLUMN_AVAILABLE_FROM = "columnAvailableFrom";

    /**
     * JSON property name for the column index marking the end of the block-out period of a
     * resource.
     */
    static final String JSON_COLUMN_BLOCKOUT_TO = "columnBlockoutTo";

    /**
     * JSON property name for the column index marking the start of the block-out period of a
     * resource.
     */
    static final String JSON_COLUMN_BLOCKOUT_FROM = "columnBlockoutFrom";

    /** JSON property name for post block size. */
    static final String JSON_POST_BLOCK_TIMESLOTS = "postBlockTimeslots";

    /** JSON property name for pre block size. */
    static final String JSON_PRE_BLOCK_TIMESLOTS = "preBlockTimeslots";

    /** JSON property name for excluded configuration ids of a resource. */
    private static final String JSON_EXCLUDED_CONFIGS = "excludedConfigs";

    /** Qualified field name of the post_block field in the resources table. */
    private static final String RESOURCES_POST_BLOCK = "resources.post_block";

    /** Qualified field name of the pre_block field in the resources table. */
    private static final String RESOURCES_PRE_BLOCK = "resources.pre_block";

    /** JSON property name for the name of a resource. */
    private static final String JSON_NAME = "name";

    /** JSON property name for the pre-block of a resource. */
    private static final String JSON_PRE_BLOCK = "preBlock";

    /** JSON property name for the post-block of a resource. */
    private static final String JSON_POST_BLOCK = "postBlock";

    /** JSON property name for the id of a resource. */
    private static final String JSON_RESOURCE_ID = "resourceId";

    /** JSON property name for the row index of a resource. */
    private static final String JSON_ROW = "row";

    /** JSON property name for the status of an event. */
    private static final String JSON_STATUS = "status";

    /** JSON property name for the ending column of an event. */
    private static final String JSON_COLUMN_END = "columnEnd";

    /** JSON property name for the starting column of an event. */
    private static final String JSON_COLUMN_START = "columnStart";

    /** JSON property name for the row index of an event. */
    private static final String JSON_RESOURCE_ROW = "resourceRow";

    /** JSON property name for the id of an event. */
    private static final String JSON_EVENT_ID = "eventId";

    /** JSON property name for the type of timemark. */
    private static final String JSON_TIMEMARK_TYPE = "type";

    /** JSON property name for the datetime label of a timemark. */
    private static final String JSON_DATE_TIME_LABEL = "dateTimeLabel";

    /** JSON property name for the start date/time of a timemark. */
    private static final String JSON_DATE_TIME_START = "dateTimeStart";

    /** JSON property name for the column index of a timemark. */
    private static final String JSON_COLUMN = "column";

    /** A dash. */
    private static final String DASH = "-";

    /** JSON property name for number of minor segments per major segment. */
    private static final String JSON_MINOR_TO_MAJOR_RATIO = "minorToMajorRatio";

    /** JSON property name for timemarks. */
    private static final String JSON_TIMEMARKS = "timemarks";

    /** Maximum interval in minutes for a minor segment on the time line. */
    private static final int MAX_MINOR_SEGMENT_MINUTES = 30;

    /** Default number of minor segments per hour on the time line. */
    private static final int DEFAULT_SEGMENTS_PER_HOUR = 6;

    /** 60 minutes in an hour. */
    private static final int MINUTES_IN_HOUR = 60;

    /** Default starting hour for the time line. */
    private static final int DEFAULT_START_HOUR = 8;

    /** 24 hours in a day. */
    private static final int HOURS_IN_DAY = 24;

    /** Default ending hour for the time line. */
    private static final int DEFAULT_END_HOUR = 20;

    /** The Constant ACTIVITY_ID. */
    private static final String ACTIVITY_ID = "AbWorkplaceReservations";

    /** The Constant DATE_FORMAT. */
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    /** The Constant TIME_FORMAT. */
    private static final String TIME_FORMAT = "1899-12-30 HH:mm";

    /** The Constant ATTENDEE_START_BLOCK. */
    private static final int ATTENDEE_START_BLOCK = 0;

    /** The Constant ATTENDEE_POST_BLOCK. */
    private static final int ATTENDEE_POST_BLOCK = 0;

    /** The Constant ATTENDEE_PRE_BLOCK. */
    private static final int ATTENDEE_PRE_BLOCK = 0;

    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private TimelineHelper() {
    }

    /**
     * Get the time line limits.
     *
     * @param context the event handler context
     * @return the timeline JSON object containing start and end hour as integers
     */
    public static JSONObject getTimelineLimits(final EventHandlerContext context) {
        int timelineStartHour = DEFAULT_START_HOUR;
        int timelineEndHour = DEFAULT_END_HOUR;

        // Get time start and end values
        // Supported values are formatted time values that we can pull the hour out of
        final Integer startHour = getTimelineHourParam(context, ACTIVITY_ID, "TimelineStartTime");
        if (startHour != null) {
            timelineStartHour = startHour.intValue();
        }
        final Integer endHour = getTimelineHourParam(context, ACTIVITY_ID, "TimelineEndTime");
        if (endHour != null) {
            timelineEndHour = endHour.intValue();
        }

        // Error checking on start and end time parameters
        timelineStartHour = Math.max(0, timelineStartHour);
        timelineEndHour = Math.min(HOURS_IN_DAY, timelineEndHour);
        timelineStartHour = Math.min(timelineStartHour, timelineEndHour);
        timelineEndHour = Math.max(timelineStartHour, timelineEndHour);

        final JSONObject timelineLimits = new JSONObject();
        timelineLimits.put(JSON_TIMELINE_START_HOUR, timelineStartHour);
        timelineLimits.put(JSON_TIMELINE_END_HOUR, timelineEndHour);

        return timelineLimits;
    }

    /**
     * Creates the timeline.
     *
     * @return the jSON object
     */
    public static JSONObject createTimeline() {
        final EventHandlerContext context = ContextStore.get().getEventHandlerContext();

        final JSONObject timeline = getTimelineLimits(context);
        int minorSegments = DEFAULT_SEGMENTS_PER_HOUR;

        /*
         * Number of segments each hour is broken into - these will be separated by minor timemarks.
         * Valid intervals are between 1 and 30 - don't generate minor marks outside that range.
         */
        final int minutesTimeUnit = com.archibus.service.Configuration
            .getActivityParameterInt(ACTIVITY_ID, "MinutesTimeUnit", 0);
        if (minutesTimeUnit > 0 && minutesTimeUnit <= MAX_MINOR_SEGMENT_MINUTES) {
            // Number of minor marks is closest integer
            minorSegments = MINUTES_IN_HOUR / minutesTimeUnit;
        }
        timeline.put(JSON_MINOR_TO_MAJOR_RATIO, minorSegments);

        retrieveTimemarks(timeline, minorSegments);

        return timeline;
    }

    /**
     * Get the start hour of a time line.
     *
     * @param timeline the time line
     * @return the start hour
     */
    public static int getTimelineStartHour(final JSONObject timeline) {
        return timeline.getInt(JSON_TIMELINE_START_HOUR);
    }

    /**
     * Get the start hour of a time line.
     *
     * @param timeline the time line
     * @return the start hour
     */
    public static int getTimelineEndHour(final JSONObject timeline) {
        return timeline.getInt(JSON_TIMELINE_END_HOUR);
    }

    /**
     * Creates the room reservation event.
     *
     * @param timeline the timeline
     * @param roomArrangement the room arrangement
     * @param roomAllocation the room allocation
     * @param rowIndex the row index
     * @return the jSON object
     */
    public static JSONObject createRoomReservationEvent(final JSONObject timeline,
            final RoomArrangement roomArrangement, final RoomAllocation roomAllocation,
            final int rowIndex) {
        final int maxTimemarksColumn = ((JSONArray) timeline.get(JSON_TIMEMARKS)).length();
        final int timelineStartHour = timeline.getInt(JSON_TIMELINE_START_HOUR);
        final int minorSegments = timeline.getInt(JSON_MINOR_TO_MAJOR_RATIO);

        final JSONObject event = new JSONObject();
        event.put(JSON_EVENT_ID, roomAllocation.getId());
        event.put(JSON_RESOURCE_ROW, rowIndex);
        event.put(JSON_COLUMN_START, getTimeColumn(timelineStartHour, minorSegments,
            roomAllocation.getStartTime(), maxTimemarksColumn, false));
        event.put(JSON_COLUMN_END, getTimeColumn(timelineStartHour, minorSegments,
            roomAllocation.getEndTime(), maxTimemarksColumn, true) - 1);
        // Search for the preblock and postblock timeslots
        event.put(JSON_PRE_BLOCK_TIMESLOTS,
            getTimeSlots(roomArrangement.getPreBlock(), minorSegments));
        event.put(JSON_POST_BLOCK_TIMESLOTS,
            getTimeSlots(roomArrangement.getPostBlock(), minorSegments));

        event.put(JSON_STATUS, 0);

        return event;
    }

    /**
     * Creates the room reservation event.
     *
     * @param timeline the timeline
     * @param roomArrangement the room arrangement
     * @param roomAllocation the room allocation
     * @param rowIndex the row index
     * @return the jSON object, or null if the event occurs outside the time line limits
     */
    public static JSONObject createRoomReservationEventWithOffset(final JSONObject timeline,
            final RoomArrangement roomArrangement, final RoomAllocation roomAllocation,
            final int rowIndex) {
        final int maxTimemarksColumn = ((JSONArray) timeline.get(JSON_TIMEMARKS)).length();
        final int timelineStartHour = timeline.getInt(JSON_TIMELINE_START_HOUR);
        final int minorSegments = timeline.getInt(JSON_MINOR_TO_MAJOR_RATIO);

        final int eventStartColumn = getTimeColumn(timelineStartHour, minorSegments,
            roomAllocation.getStartTime(), maxTimemarksColumn, false);
        final int eventEndColumn = getTimeColumn(timelineStartHour, minorSegments,
            roomAllocation.getEndTime(), maxTimemarksColumn, true) - 1;

        JSONObject event = null;
        // only generate an event if it occurs within the time line limits
        if (eventStartColumn < maxTimemarksColumn && eventEndColumn > 0) {
            event = new JSONObject();
            event.put(JSON_EVENT_ID, roomAllocation.getId());
            event.put(JSON_RESOURCE_ROW, rowIndex);
            event.put(JSON_COLUMN_START, eventStartColumn);
            event.put(JSON_COLUMN_END, eventEndColumn);

            // Search for the preblock and postblock timeslots
            int preBlockTimeSlots = getTimeSlots(roomArrangement.getPreBlock(), minorSegments);
            if (eventStartColumn < preBlockTimeSlots) {
                preBlockTimeSlots = 0;
            }
            event.put(JSON_PRE_BLOCK_TIMESLOTS, preBlockTimeSlots);
            int postBlockTimeSlots = getTimeSlots(roomArrangement.getPostBlock(), minorSegments);
            if (eventEndColumn >= maxTimemarksColumn - postBlockTimeSlots) {
                postBlockTimeSlots = 0;
            }
            event.put(JSON_POST_BLOCK_TIMESLOTS, postBlockTimeSlots);

            event.put(JSON_STATUS, 0);
        }

        return event;
    }

    /**
     * Creates the attendee calendar event.
     *
     * @param timeline the timeline
     * @param calendarEvent the calendar event
     * @param rowIndex the row index
     * @param visibleDate the date which is displayed on the time line
     * @return the jSON object
     */
    public static JSONObject createAttendeeCalendarEvent(final JSONObject timeline,
            final ICalendarEvent calendarEvent, final int rowIndex, final Date visibleDate) {
        final int maxTimemarksColumn = ((JSONArray) timeline.get(JSON_TIMEMARKS)).length();
        final int timelineStartHour = timeline.getInt(JSON_TIMELINE_START_HOUR);
        final int minorSegments = timeline.getInt(JSON_MINOR_TO_MAJOR_RATIO);
        final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.ENGLISH);
        final SimpleDateFormat timeFormat = new SimpleDateFormat(TIME_FORMAT, Locale.ENGLISH);

        final JSONObject event = new JSONObject();
        event.put("startDate", dateFormat.format(visibleDate));
        event.put("endDate", dateFormat.format(visibleDate));

        Date startTime = null;
        if (visibleDate.equals(TimePeriod.clearTime(calendarEvent.getStartDate()))) {
            startTime = calendarEvent.getStartTime();
        } else {
            startTime = visibleDate;
        }
        event.put("startTime", timeFormat.format(startTime));

        Date endTime = null;
        if (visibleDate.equals(TimePeriod.clearTime(calendarEvent.getEndDate()))) {
            endTime = calendarEvent.getEndTime();
        } else {
            final Calendar calendar = Calendar.getInstance();
            calendar.setTime(visibleDate);
            calendar.add(Calendar.DATE, 1);
            calendar.add(Calendar.MINUTE, -1);
            endTime = calendar.getTime();
        }
        event.put("endTime", timeFormat.format(endTime));

        event.put(JSON_EVENT_ID, calendarEvent.getEventId());
        event.put(JSON_RESOURCE_ROW, rowIndex);
        event.put(JSON_COLUMN_START, TimelineHelper.getTimeColumn(timelineStartHour, minorSegments,
            TimePeriod.clearDate(startTime), maxTimemarksColumn, false));
        event.put(JSON_COLUMN_END, TimelineHelper.getTimeColumn(timelineStartHour, minorSegments,
            TimePeriod.clearDate(endTime), maxTimemarksColumn, true) - 1);
        // Attendees do not have pre- and postblocks.
        event.put(JSON_PRE_BLOCK_TIMESLOTS, 0);
        event.put(JSON_POST_BLOCK_TIMESLOTS, 0);
        event.put(JSON_STATUS, 0);

        return event;
    }

    /**
     * Creates the resource reservation event.
     *
     * @param timeline the timeline
     * @param resource the resource
     * @param resourceAllocation the resource allocation
     * @param rowIndex the row index
     * @return the jSON object
     */
    public static JSONObject createResourceReservationEvent(final JSONObject timeline,
            final DataRecord resource, final ResourceAllocation resourceAllocation,
            final int rowIndex) {
        final int maxTimemarksColumn = ((JSONArray) timeline.get(JSON_TIMEMARKS)).length();
        final int timelineStartHour = timeline.getInt(JSON_TIMELINE_START_HOUR);
        final int minorSegments = timeline.getInt(JSON_MINOR_TO_MAJOR_RATIO);

        final JSONObject event = new JSONObject();
        event.put(JSON_EVENT_ID, resourceAllocation.getId());
        event.put(JSON_RESOURCE_ROW, rowIndex);
        event.put(JSON_COLUMN_START, getTimeColumn(timelineStartHour, minorSegments,
            resourceAllocation.getStartTime(), maxTimemarksColumn, false));
        event.put(JSON_COLUMN_END, getTimeColumn(timelineStartHour, minorSegments,
            resourceAllocation.getEndTime(), maxTimemarksColumn, true) - 1);
        // Search for the preblock and postblock timeslots
        event.put(JSON_PRE_BLOCK_TIMESLOTS,
            getTimeSlots(resource.getInt(RESOURCES_PRE_BLOCK), minorSegments));
        event.put(JSON_POST_BLOCK_TIMESLOTS,
            getTimeSlots(resource.getInt(RESOURCES_POST_BLOCK), minorSegments));

        event.put(JSON_STATUS, 0);

        return event;
    }

    /**
     * Creates the room arrangement resource.
     *
     * @param timeline the timeline
     * @param roomArrangement the room arrangement
     * @param configuration the room configuration
     * @param rowIndex the row index
     * @return the jSON object
     */
    public static JSONObject createRoomArrangementResource(final JSONObject timeline,
            final RoomArrangement roomArrangement, final RoomConfiguration configuration,
            final int rowIndex) {
        final int maxTimemarksColumn = ((JSONArray) timeline.get(JSON_TIMEMARKS)).length();
        final int timelineStartHour = timeline.getInt(JSON_TIMELINE_START_HOUR);
        final int minorSegments = timeline.getInt(JSON_MINOR_TO_MAJOR_RATIO);

        // this is the unique key
        final String resourceId = "<record bl_id='" + roomArrangement.getBlId() + "' fl_id='"
                + roomArrangement.getFlId() + "' rm_id='" + roomArrangement.getRmId()
                + "' config_id='" + roomArrangement.getConfigId() + "' rm_arrange_type_id='"
                + roomArrangement.getArrangeTypeId() + "'/>";
        // the name will be displayed
        final String resourceName = roomArrangement.getBlId() + DASH + roomArrangement.getFlId()
                + DASH + roomArrangement.getRmId();

        final JSONObject resource = new JSONObject();
        resource.put(JSON_ROW, rowIndex);
        resource.put(JSON_RESOURCE_ID, resourceId);

        resource.put(JSON_NAME, resourceName);
        resource.put(JSON_PRE_BLOCK, roomArrangement.getPreBlock());
        resource.put(JSON_POST_BLOCK, roomArrangement.getPostBlock());

        resource.put("buildingId", roomArrangement.getBlId());
        resource.put("floorId", roomArrangement.getFlId());
        resource.put("roomId", roomArrangement.getRmId());
        resource.put("configId", roomArrangement.getConfigId());
        resource.put("configName", configuration.getConfigName());
        resource.put("arrangeTypeId", roomArrangement.getArrangeTypeId());
        resource.put("docImage", roomArrangement.getDocImage());
        resource.put("rmPhoto", roomArrangement.getRmPhoto());
        resource.put("rmName", roomArrangement.getName());
        resource.put("maxCapacity", roomArrangement.getMaxCapacity());
        resource.put("minCapacity", roomArrangement.getMinRequired());
        resource.put("capEm", roomArrangement.getCapEm());
        resource.put("buildingName", roomArrangement.getBlName());
        resource.put("isDefault", roomArrangement.getIsDefault());
        resource.put("conflictsNo", roomArrangement.getNumberOfConflicts());
        resource.put("excludedConfig", roomArrangement.getExcludedConfig());

        // KB 3045710 check for null or empty excluded configs
        if (configuration == null || StringUtil.isNullOrEmpty(configuration.getExcludedConfigs())) {
            resource.put(JSON_EXCLUDED_CONFIGS, "");
        } else {
            resource.put(JSON_EXCLUDED_CONFIGS, configuration.getExcludedConfigs());
        }

        if (roomArrangement.getNumberOfConflicts() != null) {
            resource.put("numberOfConflicts", roomArrangement.getNumberOfConflicts().toString());
        }

        resource.put(JSON_PRE_BLOCK_TIMESLOTS,
            getTimeSlots(roomArrangement.getPreBlock(), minorSegments));
        resource.put(JSON_POST_BLOCK_TIMESLOTS,
            getTimeSlots(roomArrangement.getPostBlock(), minorSegments));

        int dayEndColumn = getTimeColumn(timelineStartHour, minorSegments,
            roomArrangement.getDayEnd(), maxTimemarksColumn, true);
        int dayStartColumn = getTimeColumn(timelineStartHour, minorSegments,
            roomArrangement.getDayStart(), maxTimemarksColumn, true);

        if (dayEndColumn <= dayStartColumn) {
            dayStartColumn = getFixedDayStartColumn(dayStartColumn, dayEndColumn,
                maxTimemarksColumn, roomArrangement);
            dayEndColumn = getFixedDayEndColumn(dayStartColumn, dayEndColumn, maxTimemarksColumn,
                roomArrangement);
            setAdjustedDayLimits(resource, dayStartColumn, dayEndColumn, maxTimemarksColumn);
        } else {
            resource.put(JSON_COLUMN_AVAILABLE_FROM, dayStartColumn);
            resource.put(JSON_COLUMN_AVAILABLE_TO, dayEndColumn);
        }

        return resource;
    }

    /**
     * Set adjusted day_start and day_end column limits for the given resource according to the
     * range of the visible time line. This will insert a block-out period if required and might
     * modify/swap the limits to occur within the time line range.
     *
     * @param resource JSON representation of the time line resource
     * @param dayStartColumn calculated day_start column
     * @param dayEndColumn calculated day_end column
     * @param maxTimemarksColumn end column of the visible time line
     */
    static void setAdjustedDayLimits(final JSONObject resource, final int dayStartColumn,
            final int dayEndColumn, final int maxTimemarksColumn) {

        int dayStartCol = dayStartColumn;
        int dayEndCol = dayEndColumn;

        if (dayEndColumn == 0 && dayStartColumn < maxTimemarksColumn) {
            // day_start is visible, day_end isn't -> place day_end at the end of the time line
            dayEndCol = maxTimemarksColumn;
        } else if (dayStartColumn == maxTimemarksColumn && dayEndColumn > 0) {
            // day_end is visible, day_start isn't -> place day_start at the beginning
            dayStartCol = 0;
        } else {
            // day_start nor day_end is visible or both are visible -> block out a range in the
            // middle of the time line
            resource.put(JSON_COLUMN_BLOCKOUT_FROM, dayEndColumn);
            resource.put(JSON_COLUMN_BLOCKOUT_TO, dayStartColumn);

            // KB 3046512 if both are invisible, the entire timeline is blocked out so we can
            // remove the pre- and post-block
            if (dayEndColumn == 0 && dayStartColumn == maxTimemarksColumn) {
                resource.put(JSON_PRE_BLOCK_TIMESLOTS, 0);
                resource.put(JSON_POST_BLOCK_TIMESLOTS, 0);
            }

            // place day_start and day_end at the beginning and end of the time line
            dayStartCol = 0;
            dayEndCol = maxTimemarksColumn;
        }
        resource.put(JSON_COLUMN_AVAILABLE_FROM, dayStartCol);
        resource.put(JSON_COLUMN_AVAILABLE_TO, dayEndCol);
    }

    /**
     * Move the day_end column to the beginning of the time line in case both day_start and day_end
     * are after the time line and day_start precedes day_end.
     *
     * @param dayStartColumn calculated day_start column
     * @param dayEndColumn calculated day_end column
     * @param maxTimemarksColumn last column of the time line
     * @param roomArrangement the room arrangement
     * @return fixed day_end column
     */
    static int getFixedDayEndColumn(final int dayStartColumn, final int dayEndColumn,
            final int maxTimemarksColumn, final RoomArrangement roomArrangement) {
        int dayEndCol = dayEndColumn;
        if ((dayStartColumn == maxTimemarksColumn) && (dayEndColumn == maxTimemarksColumn)
                && (roomArrangement.getDayStart().toString()
                    .compareTo(roomArrangement.getDayEnd().toString()) <= 0)) {
            dayEndCol = 0;
        }
        return dayEndCol;
    }

    /**
     * Move the day_start column to the end of the time line in case both day_start and day_end are
     * before the time line and day_start precedes day_end.
     *
     * @param dayStartColumn calculated day_start column
     * @param dayEndColumn calculated day_end column
     * @param maxTimemarksColumn last column of the time line
     * @param roomArrangement the room arrangement
     * @return fixed day_start column
     */
    static int getFixedDayStartColumn(final int dayStartColumn, final int dayEndColumn,
            final int maxTimemarksColumn, final RoomArrangement roomArrangement) {
        int dayStartCol = dayStartColumn;
        if ((dayStartColumn == 0) && (dayEndColumn == 0) && (roomArrangement.getDayStart()
            .toString().compareTo(roomArrangement.getDayEnd().toString()) <= 0)) {
            dayStartCol = maxTimemarksColumn;
        }
        return dayStartCol;
    }

    /**
     * Creates the attendee resource.
     *
     * @param timeline the timeline
     * @param email the email
     * @param rowIndex the row index
     * @return the jSON object
     */
    public static JSONObject createAttendeeResource(final JSONObject timeline, final String email,
            final int rowIndex) {

        final int maxTimemarksColumn = ((JSONArray) timeline.get(JSON_TIMEMARKS)).length();

        final JSONObject resource = new JSONObject();
        resource.put(JSON_ROW, rowIndex);
        resource.put(JSON_RESOURCE_ID, email);
        resource.put("email", email);
        resource.put(JSON_NAME, "");
        resource.put(JSON_PRE_BLOCK_TIMESLOTS, ATTENDEE_PRE_BLOCK);
        resource.put(JSON_POST_BLOCK_TIMESLOTS, ATTENDEE_POST_BLOCK);
        resource.put(JSON_COLUMN_AVAILABLE_FROM, ATTENDEE_START_BLOCK);
        resource.put(JSON_COLUMN_AVAILABLE_TO, maxTimemarksColumn);

        return resource;
    }

    /**
     * Creates the reservable resource.
     *
     * @param timeline the timeline
     * @param reservableResource the reservable resource
     * @param rowIndex the row index
     * @return the jSON object
     */
    public static JSONObject createReservableResource(final JSONObject timeline,
            final DataRecord reservableResource, final int rowIndex) {
        final int maxTimemarksColumn = ((JSONArray) timeline.get(JSON_TIMEMARKS)).length();
        final int timelineStartHour = timeline.getInt(JSON_TIMELINE_START_HOUR);
        final int minorSegments = timeline.getInt(JSON_MINOR_TO_MAJOR_RATIO);

        // this is the unique key
        final String resourceId = reservableResource.getString("resources.resource_id");
        // the name will be displayed
        final String resourceName = reservableResource.getString("resources.resource_name");
        final String resourceStd = reservableResource.getString("resources.resource_std");

        // use the quantity field to store the required quantity
        final int quantity = reservableResource.getInt("resources.quantity");

        final JSONObject resource = new JSONObject();
        resource.put(JSON_ROW, rowIndex);
        resource.put(JSON_RESOURCE_ID, resourceId);
        resource.put("resourceName", resourceName);
        resource.put("resourceStd", resourceStd);
        resource.put(JSON_NAME, resourceName);
        // place holder for quantity
        resource.put("quantity", String.valueOf(quantity));

        resource.put(JSON_PRE_BLOCK_TIMESLOTS,
            getTimeSlots(reservableResource.getInt(RESOURCES_PRE_BLOCK), minorSegments));
        resource.put(JSON_POST_BLOCK_TIMESLOTS,
            getTimeSlots(reservableResource.getInt(RESOURCES_POST_BLOCK), minorSegments));
        resource.put(JSON_COLUMN_AVAILABLE_FROM,
            getTimeColumn(timelineStartHour, minorSegments,
                new Time(reservableResource.getDate("resources.day_start").getTime()),
                maxTimemarksColumn, true));
        resource.put(JSON_COLUMN_AVAILABLE_TO,
            getTimeColumn(timelineStartHour, minorSegments,
                new Time(reservableResource.getDate("resources.day_end").getTime()),
                maxTimemarksColumn, false));

        return resource;
    }

    /**
     * Gets the time column.
     *
     * @param timelineStartHour the timeline start hour
     * @param minorSegments the minor segments
     * @param timeOfDay the time of day
     * @param maxTimemarksColumn the max timemarks column
     * @param roundUp round to the next segment or to the previous segment
     * @return the time column
     */
    public static int getTimeColumn(final int timelineStartHour, final int minorSegments,
            final Time timeOfDay, final int maxTimemarksColumn, final boolean roundUp) {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(timeOfDay);
        final int resStartHour = calendar.get(Calendar.HOUR_OF_DAY);
        final int resStartMin = calendar.get(Calendar.MINUTE);

        // Calculate column to nearest hour
        int columnAvailableFrom = (resStartHour - timelineStartHour) * minorSegments;

        // Add additional segments for minutes
        final double minuteSegments = resStartMin * minorSegments / (double) MINUTES_IN_HOUR;
        if (roundUp) {
            columnAvailableFrom += (int) Math.ceil(minuteSegments);
        } else {
            columnAvailableFrom += (int) Math.floor(minuteSegments);
        }

        // if the resource is available after the timeline end time, assume column
        // MaxTimemarksColumn-1
        if (columnAvailableFrom >= maxTimemarksColumn) {
            columnAvailableFrom = maxTimemarksColumn;
        }
        // if the resource is available before the timeline start time, assume column 0
        // negative column values are not allowed
        if (columnAvailableFrom < 0) {
            columnAvailableFrom = 0;
        }
        return columnAvailableFrom;
    }

    /**
     * Gets the time slots.
     *
     * @param val the val
     * @param minorSegments the minor segments
     * @return the time slots
     */
    private static int getTimeSlots(final Integer val, final int minorSegments) {
        int slots = 0;
        if (val != null) {
            // KB 3018952, for preBlock less than 1, make it equal to 1. Modified by ZY, 2008-08-05.
            final double temp = val.doubleValue() * minorSegments / MINUTES_IN_HOUR;
            slots = (int) Math.ceil(temp);
        }
        return slots;
    }

    /**
     * Retrieves a timeline start or end hour from the afm_activity_params table.
     *
     * @param context the context
     * @param activityId the activity id
     * @param paramId the param id
     * @return the timeline hour param
     */
    private static Integer getTimelineHourParam(final EventHandlerContext context,
            final String activityId, final String paramId) {
        Integer val = null;
        final String timelineHourParam =
                com.archibus.service.Configuration.getActivityParameterString(activityId, paramId);
        if (StringUtil.notNullOrEmpty(timelineHourParam)) {
            // see if it's a valid Time value
            try {
                final SimpleDateFormat format = new SimpleDateFormat("HH:mm.ss", Locale.ENGLISH);
                final Time baseValue = new Time(format.parse("00:00.00").getTime());
                final Time timeValue = new Time(format.parse(timelineHourParam).getTime());
                val = (int) ((timeValue.getTime() - baseValue.getTime())
                        / TimePeriod.HOUR_MILLISECONDS);
            } catch (final ParseException e) {
                // Invalid format - log error
                // activity parameter error message
                final String errMessage =
                        ReservationsContextHelper.localizeMessage("LOADTIMELINE_WFR",
                            ContextStore.get().getUser().getLocale(), "INVALIDPARAMETERERROR");
                context.addResponseParameter("message", errMessage + " " + paramId);
            }
        }
        return val;
    }

    /**
     * Retrieve timemarks. Start and end hour should be in the time line object.
     *
     * @param timeline the timeline containing start and end hour
     * @param minorSegments the minor segments
     */
    private static void retrieveTimemarks(final JSONObject timeline, final int minorSegments) {

        final int timelineStartHour = timeline.getInt(JSON_TIMELINE_START_HOUR);
        final int timelineEndHour = timeline.getInt(JSON_TIMELINE_END_HOUR);

        // generate major and minor timemarks and timeslots
        final JSONArray timemarks = new JSONArray();
        final Calendar calendar = Calendar.getInstance();
        final DataSource timeDs = DataSourceFactory.createDataSourceForFields("reserve",
            new String[] { "time_start" });
        timeDs.setContext();
        final ViewField.Immutable timeField = timeDs.findField("reserve.time_start");

        int column = 0;
        for (int hour = timelineStartHour; hour < timelineEndHour; hour++) {
            calendar.clear();
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            final Time time = new Time(calendar.getTimeInMillis());
            final String dateTimeStart = time.toString();
            final String dateTimeLabel = timeField.formatFieldValue(time);

            final JSONObject timemark = new JSONObject();
            timemark.put(JSON_COLUMN, column++);
            timemark.put(JSON_DATE_TIME_START, dateTimeStart);
            timemark.put(JSON_DATE_TIME_LABEL, dateTimeLabel);
            timemark.put(JSON_TIMEMARK_TYPE, "major");
            timemarks.put(timemark);

            // Create minor timemarks for the intervals for all but the last hour
            for (int segment = 1; segment < minorSegments; segment++) {
                final int minutes = segment * (MINUTES_IN_HOUR / minorSegments);
                calendar.set(Calendar.MINUTE, minutes);
                final Time tMinor = new Time(calendar.getTimeInMillis());
                final String minorTimeLabel = timeField.formatFieldValue(tMinor);

                final JSONObject minorTimemark = new JSONObject();
                minorTimemark.put(JSON_COLUMN, column++);
                minorTimemark.put(JSON_DATE_TIME_START, tMinor.toString());
                minorTimemark.put(JSON_DATE_TIME_LABEL, minorTimeLabel);
                minorTimemark.put(JSON_TIMEMARK_TYPE, "minor");
                timemarks.put(minorTimemark);
            }
        }
        timeline.put(JSON_TIMEMARKS, timemarks);
        calendar.clear();
        calendar.set(Calendar.HOUR_OF_DAY, timelineEndHour);
        if (timelineEndHour == HOURS_IN_DAY) {
            calendar.add(Calendar.MINUTE, -1);
        }
        final Time endTime = new Time(calendar.getTimeInMillis());
        timeline.put("dateTimeEnd", endTime.toString());
        timeline.put("dateTimeEndLabel", timeField.formatFieldValue(endTime));
    }
}
