package com.archibus.app.reservation.service.kiosk.impl;

import static com.archibus.app.reservation.service.kiosk.impl.FieldNameConstants.*;

import java.util.*;

import org.json.*;

import com.archibus.app.reservation.dao.IResourceStandardDataSource;
import com.archibus.app.reservation.dao.datasource.*;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.*;
import com.archibus.app.reservation.service.*;
import com.archibus.app.reservation.service.kiosk.IReservationKioskService;
import com.archibus.datasource.DataSource;
import com.archibus.datasource.data.*;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.utility.StringUtil;

/**
 * Copyright (C) ARCHIBUS, Inc. All rights reserved.
 *
 * Provides WFR's for the Essentials Tier Reservations Application.
 * <p>
 * Managed by Spring, has prototype scope. Configured in reservation-services.xml.
 *
 * @author Yorik Gerlo
 * @since 23.3
 */
public class ReservationKioskService implements IReservationKioskService {

    /** The resource standards data source. */
    private IResourceStandardDataSource resourceStandardDataSource;

    /** The reservations service for finding available rooms. */
    private IReservationService reservationService;

    /** The room reservations service for booking rooms. */
    private RoomReservationService roomReservationService;

    /** The room reservation data source. */
    private RoomReservationDataSource roomReservationDataSource;

    /** The room allocation data source. */
    private RoomAllocationDataSource roomAllocationDataSource;

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getFixedResourcesForReservationSearch(final String buildingId) {
        final List<ResourceStandard> results =
                this.resourceStandardDataSource.getFixedResourceStandards(buildingId);
        final Map<String, String> resources = new HashMap<String, String>();
        for (final ResourceStandard standard : results) {
            resources.put(standard.getId(), standard.getName());
        }
        return resources;
    }

    /**
     * Set the resource standards data source.
     *
     * @param resourceStandardDataSource the data source to set
     */
    public void setResourceStandardDataSource(
            final IResourceStandardDataSource resourceStandardDataSource) {
        this.resourceStandardDataSource = resourceStandardDataSource;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Map<String, Object>> searchAvailableReservationRooms(
            final Map<String, Object> requestParameters) {
        return this.createAvailableRoomsResult(this.findAvailableRooms(requestParameters),
            requestParameters);
    }

    /**
     * Set the reservation service.
     *
     * @param reservationService the reservation service
     */
    public void setReservationService(final IReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * Finds available rooms.
     *
     * @param requestParameters request parameters
     * @return list of room arrangements
     */
    protected List<RoomArrangement> findAvailableRooms(
            final Map<String, Object> requestParameters) {
        Integer capacity = null;
        final Object capacityParam = requestParameters.get(CAPACITY);
        if (capacityParam != null) {
            capacity = Integer.valueOf(capacityParam.toString());
        }

        final String rmArrangeTypeId = (String) requestParameters.get(RM_ARRANGE_TYPE_ID);
        final String blId = (String) requestParameters.get(BL_ID);
        final String flId = (String) requestParameters.get(FL_ID);
        final String rmId = (String) requestParameters.get(RM_ID);

        boolean externalAllowed = false;
        final Object externalAllowedParam = requestParameters.get(EXTERNAL_ALLOWED);
        if (externalAllowedParam != null) {
            externalAllowed = Integer.parseInt(externalAllowedParam.toString()) == 1;
        }

        final List<String> resourceList = new ArrayList<String>();
        final JSONArray resources = (JSONArray) requestParameters.get(RESOURCE_STD);
        if (resources != null) {
            for (int i = 0; i < resources.length(); i++) {
                final JSONObject resource = (JSONObject) resources.get(i);
                resourceList.add(resource.getString(RESOURCE_STD));
            }
        }

        final DateAndTimeUtilities util = new DateAndTimeUtilities();
        final TimePeriod timePeriod = util.createTimePeriod(requestParameters);

        final RoomArrangement roomArrangement =
                new RoomArrangement(blId, flId, rmId, null, rmArrangeTypeId);
        final RoomReservation reservation = new RoomReservation(timePeriod, roomArrangement);

        final String recurringRule = (String) requestParameters.get(RECURRING_RULE);
        Recurrence recurrence = null;
        if (StringUtil.notNullOrEmpty(recurringRule)) {
            recurrence = RecurrenceParser.parseRecurrence(reservation.getStartDate(),
                reservation.getEndDate(), recurringRule);
        }

        final List<RoomArrangement> rooms;
        if (recurrence == null) {
            rooms = this.reservationService.findAvailableRooms(reservation, capacity,
                externalAllowed, resourceList, false, null);
        } else {
            rooms = this.reservationService.findAvailableRoomsRecurrence(reservation, capacity,
                externalAllowed, resourceList, false, recurrence, null);
        }

        return rooms;
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
            resultRoom.put(BL_ID, roomArrangement.getBlId());
            resultRoom.put(FL_ID, roomArrangement.getFlId());
            resultRoom.put(RM_ID, roomArrangement.getRmId());
            resultRoom.put(CONFIG_ID, roomArrangement.getConfigId());
            resultRoom.put(RM_ARRANGE_TYPE_ID, roomArrangement.getArrangeTypeId());
            resultRoom.put(CONFLICTS_COUNT, roomArrangement.getNumberOfConflicts());

            result.add(resultRoom);
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataRecord saveRoomReservation(final DataRecord reservation,
            final DataRecord roomAllocation, final DataSetList reservationExceptions,
            final DataSetList roomAllocationExceptions) {
        this.roomReservationService.saveRoomReservation(reservation, roomAllocation, null, null);

        final int parentId = reservation
            .getInt(this.roomReservationDataSource.getMainTableName() + DOT + RES_ID);

        if (reservationExceptions != null && roomAllocationExceptions != null) {
            for (int i = 0; i < reservationExceptions.getRecords().size(); ++i) {
                final RoomReservation reservationDelta = this.roomReservationDataSource
                    .convertRecordToObject(reservationExceptions.getRecord(i),
                        roomAllocationExceptions.getRecord(i), null);

                // fetch the saved reserve record from the db
                final DataSource reserveDs = this.roomReservationDataSource.createCopy();
                reserveDs.addRestriction(
                    Restrictions.eq(reserveDs.getMainTableName(), RES_PARENT, parentId));
                reserveDs.addRestriction(Restrictions.eq(reserveDs.getMainTableName(),
                    OCCURRENCE_INDEX, reservationDelta.getOccurrenceIndex()));
                final DataRecord reserveRecord = reserveDs.getRecord();
                final int resId = reserveRecord.getInt(reserveDs.getMainTableName() + DOT + RES_ID);

                // and insert the modified values
                reserveRecord.setValue(reserveDs.getMainTableName() + DOT + DATE_START,
                    reservationDelta.getStartDate());
                reserveRecord.setValue(reserveDs.getMainTableName() + DOT + DATE_END,
                    reservationDelta.getEndDate());
                reserveRecord.setValue(reserveDs.getMainTableName() + DOT + TIME_START,
                    reservationDelta.getStartTime());
                reserveRecord.setValue(reserveDs.getMainTableName() + DOT + TIME_END,
                    reservationDelta.getEndTime());

                // fetch the saved reserve_rm record from the db
                final DataSource reserveRmDs = this.roomAllocationDataSource.createCopy();
                reserveRmDs
                    .addRestriction(Restrictions.eq(reserveRmDs.getMainTableName(), RES_ID, resId));
                DataRecord reserveRmRecord = reserveRmDs.getRecord();

                if (reserveRmRecord == null) {
                    // if the occurrence was a conflict, use the delta record directly
                    reserveRmRecord = roomAllocationExceptions.getRecord(i);
                } else {
                    // if it was not a conflict, insert the modified values
                    final RoomAllocation room = reservationDelta.getRoomAllocations().get(0);
                    reserveRmRecord.setValue(reserveRmDs.getMainTableName() + DOT + BL_ID,
                        room.getBlId());
                    reserveRmRecord.setValue(reserveRmDs.getMainTableName() + DOT + FL_ID,
                        room.getFlId());
                    reserveRmRecord.setValue(reserveRmDs.getMainTableName() + DOT + RM_ID,
                        room.getRmId());
                    reserveRmRecord.setValue(reserveRmDs.getMainTableName() + DOT + CONFIG_ID,
                        room.getConfigId());
                    reserveRmRecord.setValue(
                        reserveRmDs.getMainTableName() + DOT + RM_ARRANGE_TYPE_ID,
                        room.getArrangeTypeId());
                }

                // save occurrence update
                this.roomReservationService.saveRoomReservation(reserveRecord, reserveRmRecord,
                    null, null);
            }
        }

        // cancel all remaining occurrences that have a room conflict
        if (TYPE_RECURRING.equalsIgnoreCase(reservation
            .getString(this.roomReservationDataSource.getMainTableName() + DOT + RES_TYPE))) {
            final DataSource reserveDs = this.roomReservationDataSource.createCopy();
            reserveDs.addRestriction(
                Restrictions.eq(reserveDs.getMainTableName(), RES_PARENT, parentId));
            reserveDs.addRestriction(
                Restrictions.eq(reserveDs.getMainTableName(), STATUS, STATUS_ROOM_CONFLICT));

            this.roomReservationService.cancelMultipleRoomReservations(
                new DataSetList(reserveDs.getRecords()), null, true);
        }

        // return the primary created reservation
        return reservation;
    }

    /**
     * Set the room reservation service.
     *
     * @param roomReservationService the reservation service
     */
    public void setRoomReservationService(final RoomReservationService roomReservationService) {
        this.roomReservationService = roomReservationService;
    }

    /**
     * Set the room reservation data source.
     *
     * @param roomReservationDataSource the reservation data source
     */
    public void setRoomReservationDataSource(
            final RoomReservationDataSource roomReservationDataSource) {
        this.roomReservationDataSource = roomReservationDataSource;
    }

    /**
     * Set the room allocation data source.
     *
     * @param roomAllocationDataSource the room allocation data source
     */
    public void setRoomAllocationDataSource(
            final RoomAllocationDataSource roomAllocationDataSource) {
        this.roomAllocationDataSource = roomAllocationDataSource;
    }

}
