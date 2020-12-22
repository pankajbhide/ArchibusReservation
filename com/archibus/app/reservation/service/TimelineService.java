package com.archibus.app.reservation.service;

import java.sql.Time;
import java.util.*;

import org.apache.log4j.Logger;
import org.json.*;

import com.archibus.app.reservation.dao.datasource.*;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.*;
import com.archibus.app.reservation.service.helpers.*;
import com.archibus.app.reservation.util.*;
import com.archibus.utility.StringUtil;

/**
 * The Class TimelineService.
 */
public class TimelineService extends AdminServiceContainer {

    /** Property of the search filter indicating the time zone. */
    private static final String JSON_TIMEZONE_ID = "timezone_id";

    /** Error message when room reservation is not found. */
    // @translatable
    private static final String RESERVATION_NOT_FOUND = "Room reservation not found";

    /** The Constant RESOURCES. */
    private static final String RESOURCES = "resources";

    /** The Constant EVENTS. */
    private static final String EVENTS = "events";

    /** The Constant MESSAGE. */
    private static final String MESSAGE = "message";

    /** The Constant CALLED_FROM_KEY. */
    private static final String CALLED_FROM_KEY = "called_from";

    /** The Constant CALLED_FROM_KEY. */
    private static final String CALLED_FROM_RESERVATION_CALENDAR = "reservation_calendar";

    /** Records number. */
    private static final int RECORD_LIMIT = 100;

    /** Location from where is called. */
    private String calledFromLocation = "";

    /** Global variable used to count number of resources */
    private int resourceCounter = 0;

    /** The logger. */
    private final Logger logger = Logger.getLogger(this.getClass());

    /** The rooms timeline service helper. */
    private RoomTimelineServiceHelper roomTimelineServiceHelper;

    /** The attendee timeline service helper. */
    private AttendeeTimelineServiceHelper attendeeTimelineServiceHelper;

    /** The room reservation data source. */
    private ConferenceCallReservationDataSource reservationDataSource;

    /** The reservations service. */
    private IReservationService reservationService;

    /** The room configuration data source. */
    private RoomConfigurationDataSource roomConfigurationDataSource;

    /** Actual maximum number of rooms to load per building. */
    private int maxRoomsPerBuilding = RoomTimelineServiceHelper.DEFAULT_MAX_ROOMS;

    /**
     * Load the room arrangement timeline.
     * <p>
     * The room arrangements are filtered by location parameters and arrange type. Additional room
     * attributes (e.g. fixed resources) can also be specified.
     * </p>
     * <p>
     * For recurrent reservation the end date and the recurrence pattern are provided. The
     * recurrence pattern uses the default ARCHIBUS pattern and can have daily, weekly or monthly
     * pattern. *
     * </p>
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param startTime the start time
     * @param endTime the end time
     * @param searchFilter the search filter
     * @param fixedResourceStandards list of fixed resource standard ids
     * @param reservationId the reserve id (when editing a reservation)
     * @return timeline object
     */
    public JSONObject loadRoomArrangementTimeLine(final Date startDate, final Date endDate,
            final Time startTime, final Time endTime, final JSONObject searchFilter,
            final List<String> fixedResourceStandards, final Integer reservationId) {

        final JSONObject timeline = TimelineHelper.createTimeline();
        timeline.put(EVENTS, new JSONArray());
        timeline.put(RESOURCES, new JSONArray());

        final Integer numberOfAttendees = extractNumberAttendees(searchFilter);
        final boolean externalsMustBeAllowed = extractExternalAllowed(searchFilter);
        final String timeZoneId = this.extractTimeZoneId(searchFilter);
        final String recurrenceRule = searchFilter.optString("recurrence_rule", null);
        this.maxRoomsPerBuilding = RoomTimelineServiceHelper.getMaxRoomsPerBuilding();
        this.calledFromLocation = searchFilter.optString(CALLED_FROM_KEY, "");
        this.resourceCounter = 0;

        final List<String> buildingIds =
                RoomTimelineServiceHelper.getBuildingsToSearch(searchFilter);

        RoomReservation existingReservation = null;
        final Map<String, RoomReservation> existingReservations;
        if (reservationId != null && reservationId > 0) {
            existingReservation = this.reservationDataSource.get(reservationId);

            // Get all existing reservations on the first date to ensure their rooms are displayed
            existingReservations = ReservationUtils.mapByBuilding(this.reservationDataSource
                .getByConferenceId(existingReservation.getConferenceId(), false));
        } else {
            existingReservations = new HashMap<String, RoomReservation>();
        }

        // remember existing occurrences so we don not have to find them again for each building
        List<RoomReservation> existingOccurrences = null;

        for (final String buildingId : buildingIds) {
            searchFilter.put(com.archibus.app.reservation.dao.datasource.Constants.BL_ID_FIELD_NAME,
                buildingId);
            // create the room reservation object for the start date, using the search parameters
            final RoomReservation roomReservation =
                    this.roomTimelineServiceHelper.createRoomReservation(existingReservation,
                        startDate, startTime, endTime, searchFilter, timeZoneId);
            roomReservation.setRequestedTimeZone(timeZoneId);
            final RoomArrangement reservedRoom =
                    getReservedRoomForBuilding(existingReservations, buildingId);

            // when editing a recurrent reservation retain the dates of all occurrences
            // when editing a single occurrence the recurrence rule should be empty
            if (StringUtil.notNullOrEmpty(recurrenceRule) && reservationId != null
                    && reservationId > 0) {
                if (existingOccurrences == null) {
                    existingOccurrences = getExistingOccurrences(roomReservation, timeZoneId);
                }
                loadRoomArrangementTimeLineEditRecurrence(timeline, roomReservation,
                    numberOfAttendees, externalsMustBeAllowed, fixedResourceStandards, reservedRoom,
                    existingOccurrences);
            } else {
                if (StringUtil.notNullOrEmpty(recurrenceRule)) {
                    roomReservation.setRecurrence(
                        RecurrenceParser.parseRecurrence(startDate, endDate, recurrenceRule));
                }
                loadRoomArrangementTimeline(timeline, roomReservation, numberOfAttendees,
                    externalsMustBeAllowed, fixedResourceStandards, reservedRoom);
            }
        }
        return timeline;
    }

    /**
     * Extract the external allowed parameter from the search filter.
     *
     * @param searchFilter the search filter
     * @return the external allowed parameter
     */
    private boolean extractExternalAllowed(final JSONObject searchFilter) {
        final String externalAllowed = searchFilter.optString("external_allowed", null);
        boolean externalsMustBeAllowed = true;
        if (StringUtil.notNullOrEmpty(externalAllowed)) {
            externalsMustBeAllowed = Integer.parseInt(externalAllowed) > 0;
        }
        return externalsMustBeAllowed;
    }

    /**
     * Extract the number of attendees from the search filter.
     *
     * @param searchFilter the search filter
     * @return the number of attendees
     */
    private Integer extractNumberAttendees(final JSONObject searchFilter) {
        final String numberAttendees = searchFilter.optString("number_attendees", null);
        Integer numberOfAttendees = null;
        if (StringUtil.notNullOrEmpty(numberAttendees)) {
            numberOfAttendees = Integer.valueOf(numberAttendees);
        }
        return numberOfAttendees;
    }

    /**
     * Get the room arrangement of the room reserved in the given building, if any.
     *
     * @param existingReservations the existing reservations to check
     * @param buildingId id of the building to check for
     * @return the room arrangement, or null if not reserved
     */
    private RoomArrangement getReservedRoomForBuilding(
            final Map<String, RoomReservation> existingReservations, final String buildingId) {
        RoomArrangement reservedRoom = null;
        final RoomReservation existingReservation = existingReservations.get(buildingId);
        if (existingReservation != null) {
            reservedRoom = existingReservation.getRoomAllocations().get(0).getRoomArrangement();
        }
        return reservedRoom;
    }

    /**
     * Extract the specified time zone id from the search filter. Note if the time zone id is
     * specified but empty, we assume the default time zone. If no time zone id is specified in the
     * search filter, we return null.
     *
     * @param searchFilter the search filter
     * @return the time zone id
     */
    private String extractTimeZoneId(final JSONObject searchFilter) {
        // If a time zone id is specified then this is for a conference call reservation.
        String timeZoneId = null;
        if (searchFilter.has(JSON_TIMEZONE_ID)) {
            timeZoneId = searchFilter.getString(JSON_TIMEZONE_ID);
            if (StringUtil.isNullOrEmpty(timeZoneId)) {
                timeZoneId = TimeZone.getDefault().getID();
            }
        }
        return timeZoneId;
    }

    /**
     * Load the attendees time line.
     *
     * Load all attendees and retrieve the free/busy in the calendar service (Exchange)
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param recurrenceRule the recurrence pattern
     * @param locationFilter the location filter used in the form (for time zone information based
     *            on building id)
     * @param emails the email addresses of the attendees
     * @param uniqueId the unique reference to the active reservation
     * @param reservationId the reservation id
     * @return json timeline object
     */
    public JSONObject loadAttendeeTimeline(final Date startDate, final Date endDate,
            final String recurrenceRule, final JSONObject locationFilter, final List<String> emails,
            final String uniqueId, final Integer reservationId) {

        final TimeZone timeZone = getTimeZoneFromFilter(locationFilter);
        String customTimeZoneId = null;
        if (locationFilter.has(JSON_TIMEZONE_ID)) {
            customTimeZoneId = timeZone.getID();
        }

        final List<String> validEmails = AttendeeTimelineServiceHelper.extractValidEmails(emails);
        final JSONObject timeline = TimelineHelper.createTimeline();

        if (!validEmails.isEmpty()) {
            final JSONArray events = new JSONArray();
            final JSONArray resources = new JSONArray();
            final JSONArray failures = new JSONArray();

            timeline.put(EVENTS, events);
            timeline.put(RESOURCES, resources);
            timeline.put(MESSAGE, failures);

            // loop through all attendees
            int rowIndex = 0;
            for (final String email : validEmails) {
                // create the resource
                final JSONObject resource =
                        TimelineHelper.createAttendeeResource(timeline, email, rowIndex);
                resources.put(resource);
                // next row
                rowIndex++;
            }

            try {
                // when editing use the existing dates
                if (StringUtil.notNullOrEmpty(recurrenceRule) && reservationId != null
                        && reservationId > 0) {
                    // Get only the reservation object, to retrieve the existing occurrences next.
                    final RoomReservation roomReservation =
                            this.reservationDataSource.get(reservationId);
                    this.assertRoomReservationFound(roomReservation);
                    /*
                     * If the time zone is the default for the reservations location, do not specify
                     * time zone here. We only need to get the time zone independent date and the
                     * unique id of each occurrence.
                     */
                    final List<RoomReservation> existingOccurrences =
                            this.getExistingOccurrences(roomReservation, customTimeZoneId);
                    this.attendeeTimelineServiceHelper.loadAttendeeTimelineEditRecurrence(timeline,
                        validEmails, timeZone, existingOccurrences);
                } else {
                    // Retrieve the full reservation without time zone conversion. Proper time
                    // zone conversion is applied later if required.
                    final RoomReservation reservation = this.getExistingReservation(reservationId);
                    this.attendeeTimelineServiceHelper.loadAttendeeTimeline(timeline, startDate,
                        endDate, recurrenceRule, validEmails, reservation, timeZone);
                }
            } catch (final CalendarException exception) {
                // General failure accessing the calendar: show warning for all attendee email
                // addresses.
                this.logger.warn("Could not retrieve free/busy information from calendar",
                    exception);
                for (final String email : validEmails) {
                    failures.put(email);
                }
            }
        }

        return timeline;
    }

    /**
     * Verify that a valid room reservation was found.
     *
     * @param roomReservation the room reservation to check
     */
    private void assertRoomReservationFound(final RoomReservation roomReservation) {
        if (roomReservation == null) {
            throw new ReservationException(RESERVATION_NOT_FOUND, TimelineService.class,
                this.getAdminService());
        }
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
     * Sets the room reservation data source for retrieving existing reservations.
     *
     * @param reservationDataSource the new room reservation data source
     */
    public void setReservationDataSource(
            final ConferenceCallReservationDataSource reservationDataSource) {
        this.reservationDataSource = reservationDataSource;
    }

    /**
     * Sets the reservation service.
     *
     * @param reservationService the new reservation service
     */
    public void setReservationService(final IReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * Sets the room timeline service helper.
     *
     * @param roomTimelineServiceHelper the new room timeline service helper
     */
    public void setRoomTimelineServiceHelper(
            final RoomTimelineServiceHelper roomTimelineServiceHelper) {
        this.roomTimelineServiceHelper = roomTimelineServiceHelper;
    }

    /**
     * Sets the attendee timeline service helper.
     *
     * @param attendeeTimelineServiceHelper the new attendee timeline service helper
     */
    public void setAttendeeTimelineServiceHelper(
            final AttendeeTimelineServiceHelper attendeeTimelineServiceHelper) {
        this.attendeeTimelineServiceHelper = attendeeTimelineServiceHelper;
    }

    /**
     * Sets the room configuration data source.
     *
     * @param roomConfigurationDataSource the new room configuration data source
     */
    public void setRoomConfigurationDataSource(
            final RoomConfigurationDataSource roomConfigurationDataSource) {
        this.roomConfigurationDataSource = roomConfigurationDataSource;
    }

    /**
     * Load room arrangement timeline.
     *
     * @param timeline time line JSON object
     * @param reservation the room reservation
     * @param numberOfAttendees the number of attendees
     * @param externalsMustBeAllowed the externals must be allowed
     * @param fixedResourceStandards the fixed resource standards
     * @param reservedRoom the room already reserved, to include in the results if possible
     */
    private void loadRoomArrangementTimeline(final JSONObject timeline,
            final RoomReservation reservation, final Integer numberOfAttendees,
            final boolean externalsMustBeAllowed, final List<String> fixedResourceStandards,
            final RoomArrangement reservedRoom) {

        // Copy the start date because it is overwritten while looking for available rooms.
        final Date startDate = reservation.getStartDate();
        List<RoomArrangement> roomArrangements = null;

        final Recurrence recurrence = reservation.getRecurrence();
        if (recurrence == null) {
            // search for available room arrangements
            roomArrangements = this.reservationService.findAvailableRooms(reservation,
                numberOfAttendees, externalsMustBeAllowed, fixedResourceStandards, false,
                reservation.getRequestedTimeZone());
        } else {
            // search for available room arrangements for recurrence
            roomArrangements = this.reservationService.findAvailableRoomsRecurrence(reservation,
                numberOfAttendees, externalsMustBeAllowed, fixedResourceStandards, false,
                recurrence, reservation.getRequestedTimeZone());
        }

        // build a map of room configurations to determine excluded configurations later on
        final Map<RoomConfiguration, RoomConfiguration> configurations =
                this.roomConfigurationDataSource.getRoomConfigurations(roomArrangements);
        final String localTimeZoneId =
                TimeZoneConverter.getTimeZoneIdForBuilding(reservation.determineBuildingId());

        final JSONArray resources = timeline.getJSONArray(RESOURCES);
        int rowIndex = resources.length();
        boolean reservedRoomFound = reservedRoom == null;
        if (CALLED_FROM_RESERVATION_CALENDAR.equals(this.calledFromLocation)) {
            this.maxRoomsPerBuilding = roomArrangements.size();
        }

        // loop through available rooms to find allocations for this day
        for (int i = 0; i < roomArrangements.size() && i < this.maxRoomsPerBuilding; ++i) {
            final RoomArrangement roomArrangement;
            if (i == this.maxRoomsPerBuilding - 1 && !reservedRoomFound) {
                roomArrangement = this.findReservedRoom(reservedRoom,
                    roomArrangements.subList(i, roomArrangements.size()));
            } else {
                roomArrangement = roomArrangements.get(i);
                reservedRoomFound = reservedRoomFound || roomArrangement.equals(reservedRoom);
            }

            final JSONObject resource = TimelineHelper.createRoomArrangementResource(timeline,
                roomArrangement,
                configurations.get(RoomConfiguration.getConfiguration(roomArrangement)), rowIndex);
            resources.put(resource);

            // create for the first occurrence
            this.roomTimelineServiceHelper.createRoomAllocationEvents(startDate,
                reservation.getReservationIdsInConference(), timeline, rowIndex, roomArrangement,
                localTimeZoneId, reservation.getRequestedTimeZone());

            // make a final variable
            final int currentIndex = rowIndex;

            if (recurrence instanceof AbstractIntervalPattern) {
                final AbstractIntervalPattern pattern = (AbstractIntervalPattern) recurrence;
                pattern.loopThroughRepeats(new AbstractIntervalPattern.OccurrenceAction() {
                    // handle all occurrence events
                    @Override
                    public boolean handleOccurrence(final Date date) throws ReservationException {
                        // this is a new recurrent reservation, so no reservation id
                        TimelineService.this.roomTimelineServiceHelper.createRoomAllocationEvents(
                            date, null, timeline, currentIndex, roomArrangement, localTimeZoneId,
                            reservation.getRequestedTimeZone());

                        return true;
                    }
                });
            }
            // next row
            rowIndex++;
        }
    }

    /**
     * Find the reserved room in the list of available room arrangements. If not found, return the
     * first available one.
     *
     * @param reservedRoom the arrangement to look for
     * @param roomArrangements the list of available room arrangements
     * @return the room arrangement from the list that matches the reserved room, or the first room
     *         arrangement from the list
     */
    private RoomArrangement findReservedRoom(final RoomArrangement reservedRoom,
            final List<RoomArrangement> roomArrangements) {
        RoomArrangement result = null;
        for (final RoomArrangement arrangement : roomArrangements) {
            if (arrangement.equals(reservedRoom)) {
                result = arrangement;
                break;
            }
        }
        if (result == null) {
            result = roomArrangements.get(0);
        }
        return result;
    }

    /**
     * Load the room arrangement timeline when editing a recurrent reservation.
     *
     * @param timeline the JSON time line object
     * @param reservation the room reservation
     * @param numberOfAttendees the number of attendees
     * @param externalsMustBeAllowed the externals must be allowed
     * @param fixedResourceStandards the fixed resource standards
     * @param reservedRoom the room already reserved, to include in the results if possible
     * @param existingOccurrences the existing occurrences for the recurring reservation
     */
    private void loadRoomArrangementTimeLineEditRecurrence(final JSONObject timeline,
            final RoomReservation reservation, final Integer numberOfAttendees,
            final boolean externalsMustBeAllowed, final List<String> fixedResourceStandards,
            final RoomArrangement reservedRoom, final List<RoomReservation> existingOccurrences) {

        this.assertRoomReservationFound(reservation);

        final List<RoomArrangement> roomArrangements = this.reservationService.findAvailableRooms(
            reservation, existingOccurrences, numberOfAttendees, externalsMustBeAllowed,
            fixedResourceStandards, false, reservation.getRequestedTimeZone());
        // build a map of room configurations to determine excluded configurations later on
        final Map<RoomConfiguration, RoomConfiguration> configurations =
                this.roomConfigurationDataSource.getRoomConfigurations(roomArrangements);
        final String localTimeZoneId =
                TimeZoneConverter.getTimeZoneIdForBuilding(reservation.determineBuildingId());

        final JSONArray resources = timeline.getJSONArray(RESOURCES);
        int rowIndex = resources.length();
        boolean reservedRoomFound = reservedRoom == null;

        for (int i = 0; i < roomArrangements.size() && i < this.maxRoomsPerBuilding; ++i) {
            final RoomArrangement roomArrangement;
            if (i == this.maxRoomsPerBuilding - 1 && !reservedRoomFound) {
                roomArrangement = this.findReservedRoom(reservedRoom,
                    roomArrangements.subList(i, roomArrangements.size()));
            } else {
                roomArrangement = roomArrangements.get(i);
                reservedRoomFound = reservedRoomFound || roomArrangement.equals(reservedRoom);
            }

            final JSONObject resource = TimelineHelper.createRoomArrangementResource(timeline,
                roomArrangement,
                configurations.get(RoomConfiguration.getConfiguration(roomArrangement)), rowIndex);
            resources.put(resource);

            for (final RoomReservation existingReservation : existingOccurrences) {
                // for existing reservations, add the reservation id to ignore
                this.roomTimelineServiceHelper.createRoomAllocationEvents(
                    existingReservation.getStartDate(),
                    existingReservation.getReservationIdsInConference(), timeline, rowIndex,
                    roomArrangement, localTimeZoneId, reservation.getRequestedTimeZone());
            }

            // next row
            rowIndex++;
        }
    }

    /**
     * Get the existing occurrences for a recurring reservation starting from the given occurrence.
     *
     * @param roomReservation the first occurrence being edited
     * @param timeZoneId the requested time zone id (null for building time)
     * @return the existing occurrences
     */
    private List<RoomReservation> getExistingOccurrences(final RoomReservation roomReservation,
            final String timeZoneId) {
        List<RoomReservation> existingOccurrences = null;
        if (timeZoneId == null) {
            existingOccurrences = this.reservationDataSource.getByParentId(
                roomReservation.getParentId(), roomReservation.getStartDate(), null, false);
        } else {
            existingOccurrences =
                    this.reservationDataSource.getConferenceCallOccurrences(roomReservation);
            for (final RoomReservation reservation : existingOccurrences) {
                reservation.setTimeZone(this.roomTimelineServiceHelper.timeZoneCache
                    .getBuildingTimeZone(reservation.determineBuildingId()));
            }
        }
        return existingOccurrences;
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
            reservation = this.reservationDataSource.getActiveReservation(reservationId);
        }
        return reservation;
    }

}
