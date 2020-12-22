package com.archibus.app.reservation.service.helpers;

import java.util.*;

import com.archibus.app.common.notification.domain.Notification;
import com.archibus.app.common.notification.message.NotificationDataModel;
import com.archibus.app.common.space.dao.IRoomDao;
import com.archibus.app.common.space.dao.datasource.BuildingDataSource;
import com.archibus.app.common.space.domain.*;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.util.*;
import com.archibus.utility.StringUtil;

/**
 * Service class that can build messages related to the reservation link to insert in the meeting
 * body.
 *
 * Managed by Spring.
 *
 * @author Yorik Gerlo
 * @since 23.1
 */
public class ReservationLinkService extends ReservationMessagesService {

    /** Activity parameter id for the single reservation view. */
    private static final String SINGLE_RESERVATION_VIEW = "PlugInSingleReservationView";

    /** ID property in the freemarker data model. */
    private static final String LINK_ID = "id";

    /** Trailing slash to check for in URLs. */
    private static final String SLASH = "/";

    /** URL property in the freemarker data model. */
    private static final String URL = "url";

    /** Referenced by value for the messages used for the Outlook Plugin. */
    private static final String REF_BY_OUTLOOK_PLUGIN = "OUTLOOK_PLUGIN";

    /** Separator for inserting the conference call locations in a reservation comment. */
    private static final String CONFERENCE_CALL_LOCATIONS_SEPARATOR =
            "CONFERENCE_CALL_LOCATIONS_SEPARATOR";

    /** Referenced_by value for the messages used for conference call reservations. */
    private static final String REFERENCED_BY_CONFCALL = "CONFCALL_WFR";

    /** The building data source. */
    private BuildingDataSource buildingDataSource;

    /** The rooms data source. */
    private IRoomDao roomDao;

    /**
     * Setter for Room Dao.
     *
     * @param roomDao room data source to set
     */
    public final void setRoomDao(final IRoomDao roomDao) {
        this.roomDao = roomDao;
    }

    /**
     * Set the building data source.
     *
     * @param buildingDataSource the data source
     */
    public final void setBuildingDataSource(final BuildingDataSource buildingDataSource) {
        this.buildingDataSource = buildingDataSource;
    }

    /**
     * Process the reservation link template to a reservation message. The subject contains the
     * localized separator and the body contains the localized link.
     *
     * @param reservation the reservation for which to generate the link
     * @return message with the separator as the subject and the locations as the body
     */
    public ReservationMessage processLinkTemplate(final RoomReservation reservation) {
        final Notification notification = new Notification();
        // For localization, set the separator as the subject and the template as the body.
        notification.setSubject(this.getNotificationMessageDao().getByPrimaryKey(ACTIVITY_ID,
            REFERENCED_BY_CONFCALL, CONFERENCE_CALL_LOCATIONS_SEPARATOR));
        notification.setBody(this.getNotificationMessageDao().getByPrimaryKey(ACTIVITY_ID,
            REF_BY_OUTLOOK_PLUGIN, "RESERVATION_LINK"));

        NotificationDataModel dataModel = null;
        if (reservation.getConferenceId() == null || reservation.getConferenceId() == 0) {
            dataModel = buildLinkDataModel(reservation.getReserveId(), SINGLE_RESERVATION_VIEW);
        } else {
            dataModel = buildLinkDataModel(reservation.getConferenceId(),
                "PlugInConfCallReservationView");
        }

        // Localize the template and fill in the data.
        return formatMessage(reservation.getEmail(), notification, dataModel);
    }

    /**
     * Process the reservation link template to a reservation message. The subject contains the
     * localized separator and the body contains the localized link.
     *
     * @param reservations the reservations for which to generate the link template
     * @return message with the separator as the subject and the links as the body
     */
    public ReservationMessage processRecurringLinkTemplate(
            final List<RoomReservation> reservations) {
        final Notification notification = new Notification();
        // For localization, set the separator as the subject and the template as the body.
        notification.setSubject(this.getNotificationMessageDao().getByPrimaryKey(ACTIVITY_ID,
            REFERENCED_BY_CONFCALL, CONFERENCE_CALL_LOCATIONS_SEPARATOR));
        notification.setBody(this.getNotificationMessageDao().getByPrimaryKey(ACTIVITY_ID,
            REF_BY_OUTLOOK_PLUGIN, "RECURRING_RESERVATION_LINK"));

        final RoomReservation primary = reservations.get(0);
        final Map<Integer, List<RoomReservation>> reservationsByOccurrenceIndex =
                ReservationUtils.groupByOccurrenceIndex(reservations, 0);

        // build a comma-separated list of parent ids
        final StringBuilder parentIds = new StringBuilder();
        for (final RoomReservation reservation : reservationsByOccurrenceIndex
            .get(primary.getOccurrenceIndex())) {
            parentIds.append(reservation.getReserveId());
            parentIds.append(',');
        }
        parentIds.deleteCharAt(parentIds.length() - 1);

        final NotificationDataModel dataModel = new NotificationDataModel();
        dataModel.setDataModel(new HashMap<String, Object>());
        dataModel.getDataModel().put(LINK_ID, parentIds);
        // build the URL
        final String appUrl = buildAppUrl();
        final String recurringView = com.archibus.service.Configuration.getActivityParameterString(
            ReservationsContextHelper.RESERVATIONS_ACTIVITY, "PlugInRecurringReservationView");
        dataModel.getDataModel().put(URL, appUrl + recurringView);

        // Build the data model for each occurrence.
        final List<Map<String, Object>> occurrences =
                new ArrayList<Map<String, Object>>(reservationsByOccurrenceIndex.size());
        dataModel.getDataModel().put("occs", occurrences);

        // Caches for the room name and building name
        final Map<Room, Room> roomCache = new HashMap<Room, Room>();
        final Map<String, Building> buildingCache = new HashMap<String, Building>();
        final String singleView = com.archibus.service.Configuration.getActivityParameterString(
            ReservationsContextHelper.RESERVATIONS_ACTIVITY, SINGLE_RESERVATION_VIEW);

        for (final Integer occurrenceIndex : new TreeSet<Integer>(
            reservationsByOccurrenceIndex.keySet())) {
            final List<RoomReservation> onOccurrence =
                    reservationsByOccurrenceIndex.get(occurrenceIndex);
            final RoomReservation occurrence = onOccurrence.get(0);
            final Map<String, Object> occurrenceDataModel = new HashMap<String, Object>();
            occurrences.add(occurrenceDataModel);
            occurrenceDataModel.put("date", new java.sql.Date(occurrence.getStartDate().getTime()));
            occurrenceDataModel.put("start", occurrence.getStartTime());
            occurrenceDataModel.put("end", occurrence.getEndTime());

            final List<Map<String, Object>> locations =
                    new ArrayList<Map<String, Object>>(onOccurrence.size());
            occurrenceDataModel.put("locs", locations);

            // loop over all reservations and add location info
            for (final RoomReservation reservation : onOccurrence) {
                final Map<String, Object> reservationDataModel = new HashMap<String, Object>();
                locations.add(reservationDataModel);
                reservationDataModel.put(LINK_ID, reservation.getReserveId());
                reservationDataModel.put(URL, appUrl + singleView);
                if (!reservation.getRoomAllocations().isEmpty()) {
                    final RoomAllocation alloc = reservation.getRoomAllocations().get(0);
                    final Room pkey = new Room();
                    pkey.setBuildingId(alloc.getBlId());
                    pkey.setFloorId(alloc.getFlId());
                    pkey.setId(alloc.getRmId());

                    reservationDataModel.put("bl", getBuildingName(buildingCache, pkey));
                    reservationDataModel.put("fl", pkey.getFloorId());
                    reservationDataModel.put("rm", pkey.getId());
                    reservationDataModel.put("name", getRoomName(roomCache, pkey));
                }
            }
        }

        // Localize the template and fill in the data.
        return formatMessage(primary.getEmail(), notification, dataModel);
    }

    /**
     * Get the name of the building.
     *
     * @param buildingCache cache to use for avoiding access to the database
     * @param pkey room primary key
     * @return building name (or id if name is not set)
     */
    private String getBuildingName(final Map<String, Building> buildingCache, final Room pkey) {
        Building building = buildingCache.get(pkey.getBuildingId());
        if (building == null) {
            building = this.buildingDataSource.get(pkey.getBuildingId());
            buildingCache.put(pkey.getBuildingId(), building);
        }
        final String buildingName;
        if (StringUtil.isNullOrEmpty(building.getName())) {
            buildingName = pkey.getBuildingId();
        } else {
            buildingName = building.getName();
        }
        return buildingName;
    }

    /**
     * Get the room name (can be null if not set).
     *
     * @param roomCache rooms cache to use
     * @param pkey room primary key
     * @return the room name
     */
    private String getRoomName(final Map<Room, Room> roomCache, final Room pkey) {
        Room room = roomCache.get(pkey);
        if (room == null) {
            room = this.roomDao.getByPrimaryKey(pkey);
            roomCache.put(pkey, room);
        }
        return room.getName();
    }

    /**
     * Build the external URL for the Web Central application.
     *
     * @return external URL string
     */
    private String buildAppUrl() {
        final StringBuffer url = new StringBuffer();
        url.append(ReservationsContextHelper.getWebCentralUrl());
        if (!url.toString().endsWith(SLASH)) {
            url.append(SLASH);
        }
        return url.toString();
    }

    /**
     * Build the data model for processing the reservation link template.
     *
     * @param id the reservation id
     * @param paramId id of the activity parameter containing the view name
     * @return the data model
     */
    private NotificationDataModel buildLinkDataModel(final Integer id, final String paramId) {
        // Build the data model with url and ID.
        final NotificationDataModel dataModel = new NotificationDataModel();
        dataModel.setDataModel(new HashMap<String, Object>());
        dataModel.getDataModel().put(LINK_ID, id);

        // build the URL
        final String appUrl = buildAppUrl();
        final String singleView = com.archibus.service.Configuration
            .getActivityParameterString(ReservationsContextHelper.RESERVATIONS_ACTIVITY, paramId);
        dataModel.getDataModel().put(URL, appUrl + singleView);
        return dataModel;
    }

}
