package com.archibus.app.reservation.service.mobile.impl;

import static com.archibus.app.common.mobile.util.FieldNameConstantsCommon.*;
import static com.archibus.app.common.mobile.util.ServiceConstants.*;
import static com.archibus.app.common.mobile.util.TableNameConstants.*;

import java.text.ParseException;
import java.util.Map;

import org.springframework.util.StringUtils;

import com.archibus.app.reservation.dao.datasource.*;
import com.archibus.datasource.data.DataRecord;
import com.archibus.utility.*;

/**
 *
 * Utility class. Provides methods related with data sources for Workplace Services Portal mobile
 * services, Reservations module.
 *
 * @author Cristina Moldovan
 * @since 21.2
 *
 */
final class DataSourceUtilities {

    /**
     * Hide default constructor.
     */
    private DataSourceUtilities() {
    }

    /**
     * Creates a reservation record from the parameters.
     *
     * @param requestParameters reservation parameters
     * @return the reservation record
     */
    static DataRecord createRoomReservation(final Map<String, String> requestParameters) {
        final RoomReservationDataSource roomReservationDataSource = new RoomReservationDataSource();
        final DataRecord reservation = roomReservationDataSource.createNewRecord();
        final DateAndTimeUtilities util = new DateAndTimeUtilities();
        final String tableDot = RESERVE_TABLE + SQL_DOT;
        String reservationName = requestParameters.get(RESERVATION_NAME);
        if (StringUtils.isEmpty(reservationName)) {
            reservationName =
                    requestParameters.get(BL_ID) + LETTER_PIPE + requestParameters.get(FL_ID)
                    + LETTER_PIPE + requestParameters.get(RM_ID);
        }

        try {
            reservation.setValue(tableDot + DATE_START,
                util.createDate(requestParameters.get(DATE_START)));
            reservation.setValue(tableDot + DATE_END,
                util.createDate(requestParameters.get(DATE_START)));
            reservation.setValue(tableDot + TIME_START,
                util.createTime(requestParameters.get(TIME_START)));
            reservation.setValue(tableDot + TIME_END,
                util.createTime(requestParameters.get(TIME_END)));
            reservation.setValue(tableDot + RESERVATION_NAME, reservationName);
            reservation.setValue(tableDot + USER_CREATED_BY, requestParameters.get(EM_ID));
            reservation.setValue(tableDot + USER_REQUESTED_FOR, requestParameters.get(EM_ID));
            reservation.setValue(tableDot + USER_REQUESTED_BY, requestParameters.get(EM_ID));

            final String[] fields =
                { EMAIL, PHONE, DV_ID, DP_ID, RESERVATION_ATTENDEES, RES_ID, STATUS };
            for (final String fieldName : fields) {
                reservation.setValue(tableDot + fieldName, requestParameters.get(fieldName));
            }
            
        } catch (final ParseException e) {
            final ExceptionBase exception =
                    ExceptionBaseFactory.newNonTranslatableException("Invalid date format", null);
            exception.setNested(e);

            throw exception;
        }

        return reservation;
    }

    /**
     * Creates a room allocation record from the parameters.
     *
     * @param requestParameters reservation parameters
     * @return the room allocation record
     */
    static DataRecord createRoomAllocation(final Map<String, String> requestParameters) {
        final RoomAllocationDataSource roomAllocationDataSource = new RoomAllocationDataSource();
        final DataRecord roomAllocation = roomAllocationDataSource.createNewRecord();
        final String tableDot = RESERVE_RM_TABLE + SQL_DOT;

        roomAllocation.setValue(tableDot + BL_ID, requestParameters.get(BL_ID));
        roomAllocation.setValue(tableDot + FL_ID, requestParameters.get(FL_ID));
        roomAllocation.setValue(tableDot + RM_ID, requestParameters.get(RM_ID));
        roomAllocation.setValue(tableDot + CONFIG_ID, requestParameters.get(CONFIG_ID));
        roomAllocation.setValue(tableDot + RM_ARRANGE_TYPE_ID,
            requestParameters.get(RM_ARRANGE_TYPE_ID));
        
        if (requestParameters.get(RMRES_ID) != null) {
            roomAllocation.setValue(tableDot + RMRES_ID, requestParameters.get(RMRES_ID));
        }

        return roomAllocation;
    }
}
