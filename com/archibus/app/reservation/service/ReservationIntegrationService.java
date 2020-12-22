package com.archibus.app.reservation.service;

import java.util.List;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.datasource.*;
import com.archibus.datasource.data.DataRecord;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.eventhandler.reservations.ReservationsCommonHandler;
import com.archibus.utility.StringUtil;

/**
 * Copyright (C) ARCHIBUS, Inc. All rights reserved.
 */
/**
 * Provides services for integrating other systems with reservations.
 *
 * Used by scheduled WFR for SV-AI Integration.
 *
 * @author Ana Albu
 * @since 24.2
 *
 */
public class ReservationIntegrationService {

    /**
     * For each reservable room (rm.reservable=1) create a configuration record and an arrangement
     * record if necessary.
     */
    public void updateReservableRooms() {
        final DataSource roomDs = DataSourceFactory.createDataSourceForFields(Constants.ROOM_TABLE,
            new String[] { Constants.BL_ID_FIELD_NAME, Constants.FL_ID_FIELD_NAME,
                    Constants.RM_ID_FIELD_NAME, Constants.RESERVABLE_FIELD_NAME,
                    Constants.NAME_FIELD_NAME });
        roomDs.addRestriction(
            Restrictions.eq(Constants.ROOM_TABLE, Constants.RESERVABLE_FIELD_NAME, 1));
        final List<DataRecord> reservableRoomRecords = roomDs.getRecords();

        String buildingId = "";
        String floorId = "";
        String roomId = "";
        String roomName = "";
        final ReservationsCommonHandler reservationsCommonHandler = new ReservationsCommonHandler();

        for (final DataRecord record : reservableRoomRecords) {
            buildingId = record.getString("rm.bl_id");
            floorId = record.getString("rm.fl_id");
            roomId = record.getString("rm.rm_id");
            // If the room hasn't a name, we assign to the configuration name=id
            roomName = record.getString("rm.name");
            if (StringUtil.isNullOrEmpty(roomName)) {
                roomName = roomId;
            }

            final DataRecord configRecord = reservationsCommonHandler
                .createConfigurationRecord(buildingId, floorId, roomId, roomName);
            if (configRecord != null) {
                reservationsCommonHandler.createArrangementRecord(buildingId, floorId, roomId,
                    configRecord.getString(Constants.ROOM_CONFIG_TABLE + Constants.DOT
                            + Constants.CONFIG_ID_FIELD_NAME),
                    "CONFERENCE");
            }
        }
    }
}
