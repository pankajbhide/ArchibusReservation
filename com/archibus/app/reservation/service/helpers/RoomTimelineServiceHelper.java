package com.archibus.app.reservation.service.helpers;

import java.sql.Time;
import java.util.*;

import org.json.*;

import com.archibus.app.reservation.dao.IRoomArrangementDataSource;
import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.service.RoomReservationServiceBase;
import com.archibus.app.reservation.util.*;
import com.archibus.utility.StringUtil;

/**
 * The Class TimelineServiceHelper.
 */
public class RoomTimelineServiceHelper extends RoomReservationServiceBase {

    /** The default max. number of rooms to display per building. */
    public static final int DEFAULT_MAX_ROOMS = 10;

    /** Activity ID for reservations application. */
    private static final String ACTIVITY_ID = "AbWorkplaceReservations";

    /** The Constant EVENTS. */
    private static final String EVENTS = "events";

    /** The room arrangement data source. */
    private IRoomArrangementDataSource roomArrangementDataSource;

    /**
     * Get the maximum number of rooms to display on the time line for each building.
     *
     * @return maximum number of rooms to display
     */
    public static int getMaxRoomsPerBuilding() {
        return com.archibus.service.Configuration.getActivityParameterInt(ACTIVITY_ID,
            "TimelineMaxRoomsPerBuilding", DEFAULT_MAX_ROOMS);
    }

    /**
     * Get a list of unique building ids to search for available rooms.
     *
     * @param locationFilter the location filter received from the client
     * @return the list of unique building ids
     */
    public static List<String> getBuildingsToSearch(final JSONObject locationFilter) {
        final List<String> buildingIds = new ArrayList<String>();
        buildingIds.add(locationFilter
            .optString(com.archibus.app.reservation.dao.datasource.Constants.BL_ID_FIELD_NAME, ""));
        final Set<String> uniqueBuildingIds = new HashSet<String>(buildingIds);

        final JSONArray conferenceBuildingsFilter =
                locationFilter.optJSONArray("conference_bl_ids");
        if (conferenceBuildingsFilter != null) {
            for (int i = 0; i < conferenceBuildingsFilter.length(); ++i) {
                final String buildingId = conferenceBuildingsFilter.getString(i);
                if (StringUtil.notNullOrEmpty(buildingId) && uniqueBuildingIds.add(buildingId)) {
                    buildingIds.add(buildingId);
                }
            }
        }
        return buildingIds;
    }

    /**
     * Create Room Allocation events in a specific time zone.
     *
     * @param startDate the start date
     * @param reservationIds the reservation ids to ignore
     * @param timeline the time line
     * @param rowIndex the row index
     * @param roomArrangement the room arrangement
     * @param localTimeZoneId the local time zone id (null to use local time)
     * @param timeZoneId the time zone id (null to use local time)
     */
    public void createRoomAllocationEvents(final Date startDate, final Integer[] reservationIds,
            final JSONObject timeline, final int rowIndex, final RoomArrangement roomArrangement,
            final String localTimeZoneId, final String timeZoneId) {
        if (timeZoneId == null) {
            this.createRoomAllocationEventsInLocalTime(startDate, reservationIds, timeline,
                rowIndex, roomArrangement);
        } else {
            this.createRoomAllocationEventsWithTimeZone(startDate, reservationIds, timeline,
                rowIndex, roomArrangement, localTimeZoneId, timeZoneId);
        }
    }

    /**
     * Create Room Allocation events.
     *
     * @param startDate the start date
     * @param reservationIds the reservation id
     * @param timeline the time line
     * @param rowIndex the row index
     * @param roomArrangement the room arrangement
     */
    private void createRoomAllocationEventsInLocalTime(final Date startDate,
            final Integer[] reservationIds, final JSONObject timeline, final int rowIndex,
            final RoomArrangement roomArrangement) {

        final JSONArray events = timeline.getJSONArray(EVENTS);
        final List<RoomAllocation> roomAllocations = this.roomAllocationDataSource
            .getAllocatedRooms(startDate, roomArrangement, reservationIds);

        for (final RoomAllocation roomAllocation : roomAllocations) {
            final RoomArrangement allocatedArrangement = this.roomArrangementDataSource.get(
                roomAllocation.getBlId(), roomAllocation.getFlId(), roomAllocation.getRmId(),
                roomAllocation.getConfigId(), roomAllocation.getArrangeTypeId());

            if (roomAllocation.getStartDate().before(startDate)) {
                roomAllocation.setStartTime(allocatedArrangement.getDayStart());
            }
            if (roomAllocation.getEndDate().after(startDate)) {
                roomAllocation.setEndTime(allocatedArrangement.getDayEnd());
            }

            events.put(TimelineHelper.createRoomReservationEvent(timeline, allocatedArrangement,
                roomAllocation, rowIndex));
        }
    }

    /**
     * Create Room Allocation events in a specific time zone.
     *
     * @param startDate the start date
     * @param reservationIds the reservation ids to ignore
     * @param timeline the timeline
     * @param rowIndex the row index
     * @param roomArrangement the room arrangement
     * @param localTimeZoneId the local time zone id for this room
     * @param timeZoneId the time zone id
     */
    private void createRoomAllocationEventsWithTimeZone(final Date startDate,
            final Integer[] reservationIds, final JSONObject timeline, final int rowIndex,
            final RoomArrangement roomArrangement, final String localTimeZoneId,
            final String timeZoneId) {

        final JSONArray events = timeline.getJSONArray(EVENTS);
        final List<RoomAllocation> visibleRoomAllocations = getVisibleRoomAllocations(startDate,
            reservationIds, timeline, roomArrangement, localTimeZoneId, timeZoneId);

        for (final RoomAllocation roomAllocation : visibleRoomAllocations) {
            // The allocated arrangement could be different from the one we're asking about.
            RoomArrangement allocatedArrangement = null;
            if (roomArrangement.equals(roomAllocation.getRoomArrangement())) {
                allocatedArrangement = roomArrangement;
            } else {
                // no time zone conversion, we only need to get the correct pre- and postblock
                allocatedArrangement = this.roomArrangementDataSource.get(roomAllocation.getBlId(),
                    roomAllocation.getFlId(), roomAllocation.getRmId(),
                    roomAllocation.getConfigId(), roomAllocation.getArrangeTypeId());
            }
            final JSONObject event = TimelineHelper.createRoomReservationEventWithOffset(timeline,
                allocatedArrangement, roomAllocation, rowIndex);
            if (event != null) {
                events.put(event);
            }
        }
    }

    /**
     * Get the visible room allocations for the time line.
     *
     * @param startDate time line date
     * @param reservationIds existing reservation ids
     * @param timeline the time line JSON object
     * @param roomArrangement the room arrangement to get allocations for
     * @param localTimeZoneId the local time zone id for the room arrangement
     * @param timeZoneId the time zone id to display on the time line
     * @return visible room allocations
     */
    private List<RoomAllocation> getVisibleRoomAllocations(final Date startDate,
            final Integer[] reservationIds, final JSONObject timeline,
            final RoomArrangement roomArrangement, final String localTimeZoneId,
            final String timeZoneId) {
        final int timeZoneOffset = TimeZoneConverter.getCombinedOffset(startDate,
            roomArrangement.getDayStart(), localTimeZoneId, timeZoneId);

        List<RoomAllocation> visibleRoomAllocations = null;
        if (timeZoneOffset == 0) {
            visibleRoomAllocations = this.roomAllocationDataSource.getAllocatedRooms(startDate,
                roomArrangement, reservationIds);
        } else {
            // determine time line start date/time and start date for the current timezone offset
            final Calendar calendar = Calendar.getInstance();
            calendar.setTime(startDate);
            calendar.set(Calendar.HOUR_OF_DAY, TimelineHelper.getTimelineStartHour(timeline));
            calendar.add(Calendar.MILLISECOND, -timeZoneOffset);
            final Date timelineStartDateTime = calendar.getTime();
            final Date timelineStartDate = TimePeriod.clearTime(timelineStartDateTime);

            // determine time line end date/time and end date for the current timezone offset
            calendar.setTime(startDate);
            calendar.set(Calendar.HOUR_OF_DAY, TimelineHelper.getTimelineEndHour(timeline));
            calendar.add(Calendar.MILLISECOND, -timeZoneOffset);
            final Date timelineEndDateTime = calendar.getTime();
            final Date timelineEndDate = TimePeriod.clearTime(timelineEndDateTime);

            // get the room allocation which could be in range of the time line
            final List<RoomAllocation> roomAllocations = getRoomAllocationsInRange(startDate,
                reservationIds, roomArrangement, timelineStartDate, timelineEndDate);

            visibleRoomAllocations = new ArrayList<RoomAllocation>(roomAllocations.size());
            /*
             * Apply the correct time zone offset to all allocations on this date. Skip allocations
             * which are not visible on the time line.
             */
            for (final RoomAllocation allocation : roomAllocations) {
                allocation.setEndDate(allocation.getStartDate());
                Date startDateTime = allocation.getStartDateTime();
                if (startDateTime.after(timelineEndDateTime)) {
                    // this allocation starts after the visible period, so skip
                    continue;
                } else if (startDateTime.before(timelineStartDateTime)) {
                    startDateTime = timelineStartDateTime;
                }
                calendar.setTime(startDateTime);
                calendar.add(Calendar.MILLISECOND, timeZoneOffset);
                allocation.setStartDateTime(calendar.getTime());

                Date endDateTime = allocation.getEndDateTime();
                if (endDateTime.before(timelineStartDateTime)) {
                    // this allocation ends before the visible period, so skip
                    continue;
                } else if (endDateTime.after(timelineEndDateTime)) {
                    endDateTime = timelineEndDateTime;
                }
                calendar.setTime(endDateTime);
                calendar.add(Calendar.MILLISECOND, timeZoneOffset);
                allocation.setEndDateTime(calendar.getTime());
                visibleRoomAllocations.add(allocation);
            }
        }
        return visibleRoomAllocations;
    }

    /**
     * Get the room allocations in range of the time line according to the given dates.
     *
     * @param startDate time line date
     * @param reservationIds existing reservation id's to ignore
     * @param roomArrangement the room arrangement to get allocations for
     * @param timelineStartDate date for the time line start (in the current room's time zone)
     * @param timelineEndDate date for the time line end (in the current room's time zone)
     * @return room allocations in range of the given dates
     */
    private List<RoomAllocation> getRoomAllocationsInRange(final Date startDate,
            final Integer[] reservationIds, final RoomArrangement roomArrangement,
            final Date timelineStartDate, final Date timelineEndDate) {
        final List<RoomAllocation> roomAllocations = this.roomAllocationDataSource
            .getAllocatedRooms(startDate, roomArrangement, reservationIds);
        // query for events from next / previous day if required
        if (timelineStartDate != null && timelineStartDate.before(startDate)) {
            roomAllocations.addAll(this.roomAllocationDataSource
                .getAllocatedRooms(timelineStartDate, roomArrangement, reservationIds));
        } else if (timelineEndDate != null && timelineEndDate.after(startDate)) {
            roomAllocations.addAll(this.roomAllocationDataSource.getAllocatedRooms(timelineEndDate,
                roomArrangement, reservationIds));
        }
        return roomAllocations;
    }

    /**
     * Creates the room reservation object for the given parameters.
     *
     * @param existingReservation the existing reservation
     * @param startDate the start date
     * @param startTime the start time of the reservation
     * @param endTime the end time of the reservation
     * @param locationFilter the location filter
     * @param timeZoneId the time zone id (can be null to use local time)
     * @return the room reservation
     */
    public RoomReservation createRoomReservation(final RoomReservation existingReservation,
            final Date startDate, final Time startTime, final Time endTime,
            final JSONObject locationFilter, final String timeZoneId) {

        final TimePeriod timePeriod = new TimePeriod(startDate, startDate, startTime, endTime);
        if (startTime != null && endTime != null) {
            // only apply time zone conversions if a time period is provided
            timePeriod.setTimeZone(timeZoneId);
        }

        final String blId = locationFilter.optString(Constants.BL_ID_FIELD_NAME, "");
        final String flId = locationFilter.optString(Constants.FL_ID_FIELD_NAME, "");
        final String rmId = locationFilter.optString(Constants.RM_ID_FIELD_NAME, "");
        final String configId = locationFilter.optString(Constants.CONFIG_ID_FIELD_NAME, "");
        final String arrangeTypeId = locationFilter.optString("rm_arrange_type_id", "");

        final RoomReservation roomReservation =
                new RoomReservation(timePeriod, blId, flId, rmId, configId, arrangeTypeId);

        // when editing a recurrent reservation we need the parent id
        // when editing any reservation we need the conference id
        if (existingReservation != null) {
            roomReservation.setReserveId(existingReservation.getReserveId());
            roomReservation.setParentId(existingReservation.getParentId());
            roomReservation.setConferenceId(existingReservation.getConferenceId());

            // We only need the conference id's if a time zone is specified.
            if (timeZoneId != null && roomReservation.getConferenceId() != null) {
                roomReservation.setReservationIdsInConference(this.reservationDataSource
                    .getActiveReservationIdsInConference(roomReservation.getConferenceId()));
            }
        }

        return roomReservation;
    }

    /**
     * Sets the room arrangement data source for getting detailed arrangement info to use on the
     * timeline.
     *
     * @param roomArrangementDataSource the new room arrangement data source that will provide
     *            detailed room arrangement info
     */
    public void setRoomArrangementDataSource(
            final IRoomArrangementDataSource roomArrangementDataSource) {
        this.roomArrangementDataSource = roomArrangementDataSource;
    }

}