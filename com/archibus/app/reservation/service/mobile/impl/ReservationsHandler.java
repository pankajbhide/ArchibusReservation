package com.archibus.app.reservation.service.mobile.impl;

import static com.archibus.app.common.mobile.util.FieldNameConstantsCommon.*;
import static com.archibus.app.common.mobile.util.ServiceConstants.*;

import java.util.*;

import org.json.*;

import com.archibus.app.reservation.dao.datasource.RoomArrangementDataSource;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.service.RoomReservationService;
import com.archibus.context.ContextStore;
import com.archibus.datasource.*;
import com.archibus.datasource.data.DataRecord;

/**
 * Handles the Reservations within the Workplace Services Portal mobile app.
 *
 * @author Cristina Reghina
 * @since 21.2
 *
 */
public class ReservationsHandler {

    /**
     * Constructor.
     */
    ReservationsHandler() {
        // Auto-generated constructor stub
    }

    /**
     * Searchs for available reservation rooms.
     *
     * @param userName User Name
     * @param requestParameters parameters of the request
     * @return a list of room arrangements
     *
     */
    public List<Map<String, Object>> searchAvailableReservationRooms(final String userName,
            final Map<String, Object> requestParameters) {

        final List<RoomArrangement> rooms = findAvailableRooms(requestParameters);

        return createAvailableRoomsResult(rooms, requestParameters);
    }

    /**
     * Finds available rooms.
     *
     * @param requestParameters request parameters
     * @return list of room arrangements
     */
    protected List<RoomArrangement> findAvailableRooms(final Map<String, Object> requestParameters) {
        final int capacity = Integer.parseInt(requestParameters.get(CAPACITY).toString());
        final String rmArrangeTypeId = (String) requestParameters.get(RM_ARRANGE_TYPE_ID);
        final String blId = (String) requestParameters.get(BL_ID);
        final String flId = (String) requestParameters.get(FL_ID);
        final String rmId = (String) requestParameters.get(RM_ID);
        final boolean externalAllowed =
                Integer.parseInt(requestParameters.get(EXTERNAL_ALLOWED).toString()) == 1 ? true
                        : false;
        final JSONArray resources = (JSONArray) requestParameters.get(RESOURCE_STD);
        final List<String> resourceList = new ArrayList<String>();
        for (int i = 0; i < resources.length(); i++) {
            final JSONObject resource = (JSONObject) resources.get(i);
            resourceList.add(resource.getString(RESOURCE_STD));
        }

        final DateAndTimeUtilities util = new DateAndTimeUtilities();
        final TimePeriod timePeriod = util.createTimePeriod(requestParameters);

        final RoomArrangement roomArrangement =
                new RoomArrangement(blId, flId, rmId, null, rmArrangeTypeId);
        final RoomReservation reservation = new RoomReservation(timePeriod, roomArrangement);

        final RoomArrangementDataSource roomArrangementDataSource =
                (RoomArrangementDataSource) ContextStore.get().getBean("roomArrangementDataSource");
        List<RoomArrangement> rooms = null;
        rooms =
                roomArrangementDataSource.findAvailableRooms(reservation, capacity,
                    externalAllowed, resourceList, false, false);

        return rooms;
    }

    /**
     * Reserves the room.
     *
     * @param userName User Name
     * @param requestParameters parameters of the reservation request
     */
    public void reserveRoom(final String userName, final Map<String, String> requestParameters) {
        final DataRecord reservation = DataSourceUtilities.createRoomReservation(requestParameters);
        final DataRecord roomAllocation =
                DataSourceUtilities.createRoomAllocation(requestParameters);

        final RoomReservationService roomReservationService =
                (RoomReservationService) ContextStore.get().getBean(ROOM_RESERVATION_SERVICE);
        roomReservationService.saveRoomReservation(reservation, roomAllocation, null, null);
    }

    /**
     * Cancel the room reservation.
     *
     * @param userName User Name
     * @param requestParameters the reservation id
     */
    public void cancelRoomReservation(final String userName,
            final Map<String, String> requestParameters) {
        final Integer reservationId = Integer.valueOf(requestParameters.get(RES_ID));
        final RoomReservationService roomReservationService =
                (RoomReservationService) ContextStore.get().getBean(ROOM_RESERVATION_SERVICE);
        roomReservationService.cancelRoomReservation(reservationId, "", true);
    }

    /**
     * Cancel all occurrences of a recurring room reservation.
     *
     * @param userName User Name
     * @param requestParameters the reservation id
     */
    public void cancelRecurringRoomReservation(final String userName,
            final Map<String, String> requestParameters) {
        final Integer reservationId = Integer.valueOf(requestParameters.get(RES_ID));
        final RoomReservationService roomReservationService =
                (RoomReservationService) ContextStore.get().getBean(ROOM_RESERVATION_SERVICE);
        roomReservationService.cancelRecurringRoomReservation(reservationId, "", true);
    }

    /**
     * Creates the result list from the rooms list.
     *
     * @param roomArrangements Rooms arrangements
     * @param requestParameters parameters of the reservation request
     * @return the room arrangements to be returned by the WFR
     */
    protected List<Map<String, Object>> createAvailableRoomsResult(
            final List<RoomArrangement> roomArrangements,
            final Map<String, Object> requestParameters) {
        final List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();

        for (final RoomArrangement roomArrangement : roomArrangements) {
            final Map<String, Object> resultRoom = new HashMap<String, Object>();
            // roomArrangement does not contain the date
            resultRoom.put(DAY_START, requestParameters.get(DAY_START));
            // roomArrangement has total availability times for the room
            resultRoom.put(TIME_START, requestParameters.get(TIME_START));
            resultRoom.put(TIME_END, requestParameters.get(TIME_END));
            resultRoom.put(BL_ID, roomArrangement.getBlId());
            resultRoom.put(FL_ID, roomArrangement.getFlId());
            resultRoom.put(RM_ID, roomArrangement.getRmId());
            resultRoom.put(CONFIG_ID, roomArrangement.getConfigId());
            resultRoom.put(RM_ARRANGE_TYPE_ID, roomArrangement.getArrangeTypeId());

            result.add(resultRoom);
        }

        return result;
    }

    /**
     * Check in the room reservation: set 'verified' to 1.
     *
     * @param requestParameters parameters of the reservation request
     */
    protected void checkInRoomReservation(final Map<String, String> requestParameters) {

        final DataSource dataSource = DataSourceFactory.createDataSourceForTable("reserve_rm");
        final DataRecord record =
                dataSource.getRecord(" reserve_rm.res_id="
                        + Integer.valueOf(requestParameters.get("res_id")));
        record.setValue("reserve_rm.verified", 1);
        dataSource.saveRecord(record);
    }
}
