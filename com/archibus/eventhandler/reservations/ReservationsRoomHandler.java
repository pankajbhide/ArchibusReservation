package com.archibus.eventhandler.reservations;

import java.util.*;

import com.archibus.app.common.util.SchemaUtils;
import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.service.WorkRequestService;
import com.archibus.app.reservation.util.ReservationsContextHelper;
import com.archibus.context.ContextStore;
import com.archibus.datasource.*;
import com.archibus.datasource.data.DataRecord;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.jobmanager.EventHandlerContext;
import com.archibus.utility.StringUtil;

/**
 * Contains common event handlers used in Rooms reservation WFRs.
 */
public class ReservationsRoomHandler extends ReservationsEventHandlerBase {

    /** Identifier for the localized error message. */
    private static final String ERROR_MESSAGE = "CREATEWORKREQUESTERROR";

    /** Identifier for the localized setup description. */
    private static final String SETUP_DESC = "CREATEWORKREQUESTSETUPDESCRIPTION";

    /** Identifier for the localized cleanup description. */
    private static final String CLEANUP_DESC = "CREATEWORKREQUESTCLEANUPDESCRIPTION";

    /** Identifier for the localized reservation comments header. */
    private static final String COMMENTS_DESC = "CREATEWORKREQUESTRESERVATIONCOMMENTSDESCRIPTION";

    /** Identifier for the localized reservation attendees header. */
    private static final String ATTENDEES_DESC = "CREATEWORKREQUESTRESERVATIONATTENDEES";

    // ---------------------------------------------------------------------------------------------
    // BEGIN createWorkRequest wfr
    // ---------------------------------------------------------------------------------------------
    /**
     * Gets the identifier of a created or modified room reservation and generates or updates the
     * work request associated to this reservation if needed Inputs: res_id res_id (String);
     * parent_id parent_id (String); Outputs: message error message in necesary case.
     *
     * @param context Event handler context.
     */
    public void createWorkRequest(final EventHandlerContext context) {
        // this.log.info("Executing '"+ACTIVITY_ID+"-"+RULE_ID+"' .... ");
        // Get the input res_id parameter
        int reservationId = Integer.parseInt(context.getParameter(Constants.RES_ID).toString());
        final int parentId =
                Integer.parseInt(context.getParameter(Constants.RES_PARENT).toString());
        // this.log.info("'"+ACTIVITY_ID+"-"+RULE_ID+"' [res_id]: "+resId+" ");

        final Map<String, String> messages = ReservationsContextHelper.localizeMessages(
            REFERENCED_BY_WR, ContextStore.get().getUser().getLocale(), ERROR_MESSAGE, SETUP_DESC,
            CLEANUP_DESC, COMMENTS_DESC, ATTENDEES_DESC);

        // BEGIN: it gets one or more room reserve
        final List<Integer> vectorResId = getReservationIds(reservationId, parentId);
        // END: it gets one or more room reserve

        final Map<String, String> tradesByArrangement = new HashMap<String, String>();
        final Map<String, String> vendorsByArrangement = new HashMap<String, String>();
        final boolean isWorkRequestOnly = WorkRequestService.isWorkRequestOnly();

        // Create work requests for each reservation
        for (final Integer resId : vectorResId) {
            reservationId = resId.intValue();
            if (reservationId > 0) {
                createWorkRequestsForReservation(reservationId, messages, tradesByArrangement,
                    vendorsByArrangement, isWorkRequestOnly);
            }
        }
    }

    /**
     * Create work requests for a single reservation.
     *
     * @param reservationId reservation id
     * @param messages localized messages
     * @param tradesByArrangement cache specifying trade by arrangement type
     * @param vendorsByArrangement cache specifying vendor by arrangement type
     * @param isWorkRequestOnly true if building operations is using only work requests
     */
    private void createWorkRequestsForReservation(final int reservationId,
            final Map<String, String> messages, final Map<String, String> tradesByArrangement,
            final Map<String, String> vendorsByArrangement, final boolean isWorkRequestOnly) {
        final DataSource reserveRmDs = DataSourceFactory
            .createDataSourceForFields(Constants.RESERVE_RM_TABLE, new String[] { Constants.RES_ID,
                    Constants.STATUS, Constants.RM_ARRANGE_TYPE_ID_FIELD_NAME });
        reserveRmDs.setApplyVpaRestrictions(false);
        reserveRmDs.addRestriction(
            Restrictions.eq(Constants.RESERVE_RM_TABLE, Constants.RES_ID, reservationId));
        final DataRecord reserveRmRecord = reserveRmDs.getRecord();

        String statusOfRoomReservation = "";
        String arrangeTypeId = "";
        if (reserveRmRecord != null) {
            statusOfRoomReservation = reserveRmRecord.getString("reserve_rm.status");
            arrangeTypeId = reserveRmRecord.getString("reserve_rm.rm_arrange_type_id");
        }
        final String statusForWorkRequest =
                this.getStatusForWorkRequest(statusOfRoomReservation, isWorkRequestOnly);

        final String tradeToCreate =
                getTradeOrVendorToCreate(tradesByArrangement, arrangeTypeId, "tr", TR_ID_FIELD);
        final String vendorToCreate =
                getTradeOrVendorToCreate(vendorsByArrangement, arrangeTypeId, "vn", VN_ID_FIELD);

        // -----------------------------------------------------------------------------------
        // CANCEL/STOP WORK REQUESTS FOR OTHER TRADES / VENDORS
        // -----------------------------------------------------------------------------------
        this.cancelAndStopOtherWorkRequests(reservationId, tradeToCreate, vendorToCreate);

        // -----------------------------------------------------------------------------------
        // WORK REQUESTS FOR TRADE
        // -----------------------------------------------------------------------------------
        if (StringUtil.notNullOrEmpty(tradeToCreate)) {
            createWorkRequestsForTradeOrVendor(reservationId, statusForWorkRequest, TR_ID_FIELD,
                tradeToCreate, messages);
        }

        // -----------------------------------------------------------------------------------
        // WORK REQUESTS FOR VENDOR
        // -----------------------------------------------------------------------------------
        if (StringUtil.notNullOrEmpty(vendorToCreate)) {
            createWorkRequestsForTradeOrVendor(reservationId, statusForWorkRequest, VN_ID_FIELD,
                vendorToCreate, messages);
        }
    }

    /**
     * Cancel (or stop) work requests for different trades and/or vendors.
     *
     * @param reservationId the reservation id
     * @param tradeToCreate cancel work requests for all trades except this one
     * @param vendorToCreate cancel work requests for all vendors except this one
     */
    private void cancelAndStopOtherWorkRequests(final int reservationId, final String tradeToCreate,
            final String vendorToCreate) {

        final DataSource workRequestDataSource = this.createDataSourceToCancelOtherWorkRequests(
            reservationId, tradeToCreate, vendorToCreate);
        workRequestDataSource
            .addRestriction(Restrictions.isNotNull("wr", Constants.RMRES_ID_FIELD_NAME));

        final List<DataRecord> records = workRequestDataSource.getRecords();
        WorkRequestService.cancelWorkRequests(workRequestDataSource, records);
    }

    /**
     * Create or update work requests for a specified trade or vendor and reservation.
     *
     * @param reservationId reservation identifier
     * @param statusForWorkRequest new status for the work requests
     * @param createFor tr_id or vn_id when creating for a trade or vendor
     * @param nameToCreate which trade or vendor to create for
     * @param messages localized messages used for error reporting and work request description
     */
    private void createWorkRequestsForTradeOrVendor(final int reservationId,
            final String statusForWorkRequest, final String createFor, final String nameToCreate,
            final Map<String, String> messages) {
        final DataSource dsWr =
                this.createDataSourceToSaveWorkRequests(createFor, nameToCreate, reservationId);
        final List<DataRecord> workRequests = dsWr.getRecords();
        DataRecord setupRequest = null;
        DataRecord cleanupRequest = null;
        if (workRequests.size() < 2) {
            // no work requests found, so create new records
            setupRequest = dsWr.createNewRecord();
            cleanupRequest = dsWr.createNewRecord();
        } else {
            setupRequest = workRequests.get(0);
            cleanupRequest = workRequests.get(1);
        }

        // retrieve all relevant data with a custom query
        final DataRecord dataRecord = this.retrieveData(reservationId, nameToCreate, createFor,
            messages, statusForWorkRequest);

        // create or update the work request records
        saveWorkRequest(dsWr, setupRequest, dataRecord, Constants.RESERVE_RM_TABLE,
            Constants.RMRES_ID_FIELD_NAME, createFor, "_setup");
        saveWorkRequest(dsWr, cleanupRequest, dataRecord, Constants.RESERVE_RM_TABLE,
            Constants.RMRES_ID_FIELD_NAME, createFor, "_cleanup");
    }

    /**
     * Determine for which trade or vendor the given room arrangement type needs work requests.
     *
     * @param namesByArrangement cache of room arrangement types for which we already know the
     *            corresponding trade or vendor identifier
     * @param arrangeTypeId the room arrangement type identifier
     * @param tableName tr or vn when looking for a trade or vendor
     * @param fieldName tr_id or vn_id when looking for a trade or vendor
     * @return the trade or vendor identifier for the given room arrangement type (or empty if no
     *         work requests should be generated for any trade / vendor)
     */
    private String getTradeOrVendorToCreate(final Map<String, String> namesByArrangement,
            final String arrangeTypeId, final String tableName, final String fieldName) {
        String nameToCreate = namesByArrangement.get(arrangeTypeId);
        if (nameToCreate == null) {
            final DataSource arrangeTypeDs =
                    DataSourceFactory.createDataSourceForFields(Constants.RM_ARRANGE_TYPE_TABLE,
                        new String[] { Constants.RM_ARRANGE_TYPE_ID_FIELD_NAME, fieldName });
            arrangeTypeDs.addTable(tableName, DataSource.ROLE_STANDARD);
            arrangeTypeDs.addField(tableName, WR_FROM_RESERVE);

            arrangeTypeDs.addRestriction(Restrictions.eq(Constants.RM_ARRANGE_TYPE_TABLE,
                Constants.RM_ARRANGE_TYPE_ID_FIELD_NAME, arrangeTypeId));
            arrangeTypeDs
                .addRestriction(Restrictions.isNotNull(Constants.RM_ARRANGE_TYPE_TABLE, fieldName));
            arrangeTypeDs.addRestriction(Restrictions.eq(tableName, WR_FROM_RESERVE, 1));
            final DataRecord arrangeRecord = arrangeTypeDs.getRecord();
            if (arrangeRecord == null) {
                nameToCreate = "";
            } else {
                nameToCreate = arrangeRecord
                    .getString(Constants.RM_ARRANGE_TYPE_TABLE + Constants.DOT + fieldName);
            }
            namesByArrangement.put(arrangeTypeId, nameToCreate);
        }
        return nameToCreate;
    }

    /**
     * Retrieve reservation data to create the work requests.
     *
     * @param reservationId reservation id
     * @param createForId trade or vendor identifier
     * @param createFor tr_id or vn_id when creating for a trade or vendor
     * @param messages localized messages used in the work request description
     * @param statusForWorkRequest new status for the work request
     * @return the reservations data in a reserve_rm record with a lot of virtual fields
     */
    private DataRecord retrieveData(final int reservationId, final String createForId,
            final String createFor, final Map<String, String> messages,
            final String statusForWorkRequest) {

        String querySql =

                " SELECT reserve_rm.res_id, reserve_rm.rmres_id, "
                        + " reserve_rm.bl_id, reserve_rm.fl_id, reserve_rm.rm_id, "
                        + " bl.site_id ${sql.as} site_id, "
                        + " reserve.user_requested_by ${sql.as} requestor, "
                        + SqlUtils.formatValueForSql(statusForWorkRequest)
                        + " ${sql.as} status, reserve_rm.date_start ${sql.as} date_assigned_setup, "
                        + " reserve_rm.date_end ${sql.as} date_assigned_cleanup, "
                        + SqlUtils.formatValueForSql(createForId) + " ${sql.as} " + createFor + ", "
                        + " reserve.phone ${sql.as} phone, reserve.dv_id ${sql.as} dv_id, reserve.dp_id ${sql.as} dp_id, "
                        + "reserve_rm.time_start ${sql.as} time_assigned_setup, "
                        + "reserve_rm.time_end ${sql.as} time_assigned_cleanup, "
                        + " ${sql.isNull('-rm_arrange.pre_block', 0)} ${sql.as} delta_time_setup, "
                        + " 0 ${sql.as} delta_time_cleanup, "
                        + " ${sql.isNull('rm_arrange.pre_block', 0)}/60 ${sql.as} est_labor_hours_setup, "
                        + " ${sql.isNull('rm_arrange.post_block', 0)}/60 ${sql.as} est_labor_hours_cleanup, "
                        + getWrDescriptionSql(messages.get(SETUP_DESC),
                            messages.get(ATTENDEES_DESC), messages.get(COMMENTS_DESC))
                        + " ${sql.as} description_setup, "
                        + getWrDescriptionSql(messages.get(CLEANUP_DESC),
                            messages.get(ATTENDEES_DESC), messages.get(COMMENTS_DESC))
                        + " ${sql.as} description_cleanup, "
                        + " 'RES. SETUP' ${sql.as} prob_type_setup, "
                        + " 'RES. CLEANUP' ${sql.as} prob_type_cleanup "
                        + " FROM reserve_rm, reserve, rm_arrange, bl "
                        + " WHERE reserve_rm.res_id=reserve.res_id " + " AND reserve_rm.res_id= "
                        + reservationId + " AND reserve_rm.bl_id=rm_arrange.bl_id "
                        + " AND reserve_rm.fl_id=rm_arrange.fl_id "
                        + " AND reserve_rm.rm_id=rm_arrange.rm_id "
                        + " AND reserve_rm.config_id=rm_arrange.config_id "
                        + " AND reserve_rm.rm_arrange_type_id=rm_arrange.rm_arrange_type_id "
                        + " AND reserve_rm.bl_id=bl.bl_id ";

        if (!SchemaUtils.fieldExistsInSchema("reserve_rm", "date_end")) {
            querySql = querySql.replace("reserve_rm.date_end", "reserve_rm.date_start");
        }

        final DataSource ds0 = DataSourceFactory.createDataSource();
        ds0.addTable(Constants.RESERVE_RM_TABLE, DataSource.ROLE_MAIN);
        ds0.setApplyVpaRestrictions(false);
        ds0.addQuery(querySql);

        // Add all virtual fields
        this.addVirtualFields(ds0, Constants.RESERVE_RM_TABLE, Constants.RMRES_ID_FIELD_NAME,
            createFor);

        return ds0.getRecord();
    }

    /**
     * Get the SQL string for the work request description.
     *
     * @param headerDesc header for the description
     * @param reservationAttendees title for number of attendees
     * @param reservationComments title for reservation comments
     * @return SQL string
     */
    private String getWrDescriptionSql(final String headerDesc, final String reservationAttendees,
            final String reservationComments) {
        final StringBuffer wrDescriptionSql = new StringBuffer();
        wrDescriptionSql.append(SqlUtils.formatValueForSql(headerDesc) + SQL_CONCAT_DOT);

        // KB 3043641 - include number of attendees - only if the new field is defined in DB schema.
        if (ContextStore.get().getProject().loadTableDef(Constants.RESERVE_RM_TABLE)
            .findFieldDef("attendees_in_room") != null) {
            wrDescriptionSql
                .append(SqlUtils.formatValueForSql(reservationAttendees) + SQL_CONCAT_SPACE
                        + " CAST(reserve_rm.attendees_in_room AS VARCHAR(5)) " + SQL_CONCAT_DOT);
        }

        wrDescriptionSql.append(SqlUtils.formatValueForSql(reservationComments) + SQL_CONCAT_SPACE
                + " ${sql.trim(sql.isNull('reserve.comments','\\'\\''))} " + SQL_CONCAT_DOT
                + " ${sql.trim(sql.isNull('reserve_rm.comments','\\'\\''))}");

        return wrDescriptionSql.toString();
    }

}
