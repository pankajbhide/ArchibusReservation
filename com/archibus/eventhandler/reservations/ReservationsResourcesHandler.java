package com.archibus.eventhandler.reservations;

import java.util.*;

import org.json.JSONObject;

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
 * Provides methods to generate and update resource reservation setup and cleanup work requests.
 *
 * @author PROCOS
 * @since 23.1
 */
public class ReservationsResourcesHandler extends ReservationsEventHandlerBase {

    /** Identifier for the localized error message. */
    private static final String ERROR_MESSAGE = "SAVERESOURCEERROR";

    /** Identifier for the localized setup description. */
    private static final String SETUP_DESC = "CREATEWORKREQUESTSETUPDESCRIPTION";

    /** Identifier for the localized cleanup description. */
    private static final String CLEANUP_DESC = "CREATEWORKREQUESTCLEANUPDESCRIPTION";

    /** Identifier for the localized reservation comments header. */
    private static final String COMMENTS_DESC = "CREATEWORKREQUESTRESERVATIONCOMMENTSDESCRIPTION";

    /** Identifier for the localized reservation quantity header. */
    private static final String QUANTITY_DESC = "CREATEWORKREQUESTRESERVATIONQUANTITY";

    /**
     * Create work requests for resource reservations.
     *
     * @param context event handler context
     * @param parentId parent reservation id to create requests for a recurring reservation
     * @param newResId single reservation id to create requests for a single reservation
     */
    public void createResourceWr(final EventHandlerContext context, final int parentId,
            final int newResId) {

        final Map<String, String> messages = ReservationsContextHelper.localizeMessages(
            REFERENCED_BY_WR, ContextStore.get().getUser().getLocale(), ERROR_MESSAGE, SETUP_DESC,
            CLEANUP_DESC, COMMENTS_DESC, QUANTITY_DESC);
        final List<Integer> listResId = getReservationIds(newResId, parentId);

        final boolean isWorkRequestOnly = WorkRequestService.isWorkRequestOnly();
        final Map<String, String> tradesByResourceId = new HashMap<String, String>();
        final Map<String, String> vendorsByResourceId = new HashMap<String, String>();

        for (final Integer resId : listResId) {
            final DataSource resourceResDs = DataSourceFactory.createDataSourceForFields(
                Constants.RESERVE_RS_TABLE, new String[] { Constants.STATUS,
                        Constants.RSRES_ID_FIELD_NAME, Constants.RESOURCE_ID_FIELD });
            resourceResDs.addRestriction(
                Restrictions.eq(resourceResDs.getMainTableName(), Constants.RES_ID, resId));
            final List<DataRecord> resourceReservations = resourceResDs.getRecords();
            for (final DataRecord record : resourceReservations) {
                final JSONObject event = new JSONObject();

                event.put(Constants.STATUS, record.getString(
                    resourceResDs.getMainTableName() + Constants.DOT + Constants.STATUS));
                event.put(Constants.RESOURCE_ID_FIELD,
                    record.getString(resourceResDs.getMainTableName() + Constants.DOT
                            + Constants.RESOURCE_ID_FIELD));
                event.put(Constants.RSRES_ID_FIELD_NAME,
                    record.getInt(resourceResDs.getMainTableName() + Constants.DOT
                            + Constants.RSRES_ID_FIELD_NAME));
                event.put(Constants.RES_ID, resId);

                createWorkRequestsForReservation(event, messages, tradesByResourceId,
                    vendorsByResourceId, isWorkRequestOnly);
            }
        }
    }

    /**
     * Create work requests for a single resource reservation.
     *
     * @param event resource reservation event
     * @param messages localized messages used for error reporting and description
     * @param tradesByResourceId cache with trades by resource id
     * @param vendorsByResourceId cache with vendors by resource id
     * @param isWorkRequestOnly indicates whether BuildingOps is using only work requests
     */
    private void createWorkRequestsForReservation(final JSONObject event,
            final Map<String, String> messages, final Map<String, String> tradesByResourceId,
            final Map<String, String> vendorsByResourceId, final boolean isWorkRequestOnly) {

        final String resourceId = event.getString("resource_id");
        final String statusOfReservation = event.getString("status");

        if (Constants.STATUS_CANCELLED.equals(statusOfReservation)
                || Constants.STATUS_REJECTED.equals(statusOfReservation)) {
            /*
             * Cancelled and rejected reservation: Cancel all work requests for this reservation by
             * not passing any trade or vendor
             */
            this.cancelAndStopOtherWorkRequests(event, null, null);
        } else {
            String tradeToCreate = "";
            String vendorToCreate = "";

            final String statusForWorkRequest =
                    this.getStatusForWorkRequest(statusOfReservation, isWorkRequestOnly);

            // Check if the resource has trade that accepts wrs from reservations
            tradeToCreate =
                    getTradeOrVendorToCreate(tradesByResourceId, resourceId, "tr", TR_ID_FIELD);
            // Check if resource has vendor that accepts wrs from reservations
            vendorToCreate =
                    getTradeOrVendorToCreate(vendorsByResourceId, resourceId, "vn", VN_ID_FIELD);

            this.cancelAndStopOtherWorkRequests(event, tradeToCreate, vendorToCreate);

            if (StringUtil.notNullOrEmpty(tradeToCreate)) {
                createWorkRequestsForTradeOrVendor(event, messages, TR_ID_FIELD, tradeToCreate,
                    statusForWorkRequest);
            }

            if (StringUtil.notNullOrEmpty(vendorToCreate)) {
                createWorkRequestsForTradeOrVendor(event, messages, VN_ID_FIELD, vendorToCreate,
                    statusForWorkRequest);
            }
        }
    }

    /**
     * Get the id of the trade or vendor for which to create work requests.
     *
     * @param namesByResourceId cache mapping resource id's to the corresponding trade or vendor
     * @param resourceId resource id to get the trade or vendor for
     * @param tableName tr or vn to handle trades or vendors
     * @param fieldName tr_id or vn_id to handle trades or vendors
     * @return id of the trade or vendor, empty string if none
     */
    private String getTradeOrVendorToCreate(final Map<String, String> namesByResourceId,
            final String resourceId, final String tableName, final String fieldName) {
        String nameToCreate = namesByResourceId.get(resourceId);
        if (nameToCreate == null) {
            final DataSource dataSource = DataSourceFactory
                .createDataSourceForFields("resource_std", new String[] { fieldName });
            dataSource.addTable(tableName, DataSource.ROLE_STANDARD);
            dataSource.addField(tableName, fieldName);
            dataSource.addRestriction(Restrictions.eq(tableName, WR_FROM_RESERVE, 1));
            dataSource.addRestriction(Restrictions.sql(
                "resource_std.resource_std IN (SELECT resource_std FROM resources WHERE resource_id = "
                        + SqlUtils.formatValueForSql(resourceId) + ")"));
            final DataRecord record = dataSource.getRecord();
            if (record != null) {
                nameToCreate = record.getString("resource_std." + fieldName);
            }

            if (nameToCreate == null) {
                nameToCreate = "";
            }
            namesByResourceId.put(resourceId, nameToCreate);
        }
        return nameToCreate;
    }

    /**
     * Cancel or stop work requests for other trades and vendors.
     *
     * @param event resource reservation event
     * @param tradeToCreate cancel work requests for all trades except this one
     * @param vendorToCreate cancel work requests for all vendors except this one
     */
    private void cancelAndStopOtherWorkRequests(final JSONObject event, final String tradeToCreate,
            final String vendorToCreate) {

        final int resId = event.getInt(Constants.RES_ID);
        final int rsResId = event.getInt(Constants.RSRES_ID_FIELD_NAME);

        // Retrieve matching work requests from the database
        final DataSource workRequestDataSource = this
            .createDataSourceToCancelOtherWorkRequests(resId, tradeToCreate, vendorToCreate);
        workRequestDataSource
            .addRestriction(Restrictions.eq(WR_TABLE, Constants.RSRES_ID_FIELD_NAME, rsResId));

        final List<DataRecord> records = workRequestDataSource.getRecords();
        WorkRequestService.cancelWorkRequests(workRequestDataSource, records);
    }

    /**
     * Create or update work requests for a specified trade or vendor and reservation.
     *
     * @param event resource reservation event
     * @param statusForWorkRequest new status for the work requests
     * @param createFor tr_id or vn_id when creating for a trade or vendor
     * @param nameToCreate which trade or vendor to create for
     * @param messages localized messages used for error reporting and work request description
     */
    private void createWorkRequestsForTradeOrVendor(final JSONObject event,
            final Map<String, String> messages, final String createFor, final String nameToCreate,
            final String statusForWorkRequest) {

        final int resId = event.getInt(Constants.RES_ID);
        final int rsResId = event.getInt(Constants.RSRES_ID_FIELD_NAME);

        final DataSource dsWr = createDataSourceToSaveWorkRequests(createFor, nameToCreate, resId);
        dsWr.addRestriction(Restrictions.eq(WR_TABLE, Constants.RSRES_ID_FIELD_NAME, rsResId));
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
        final DataRecord dataRecord =
                this.retrieveData(event, nameToCreate, createFor, messages, statusForWorkRequest);

        // create or update the work request records
        saveWorkRequest(dsWr, setupRequest, dataRecord, Constants.RESERVE_RS_TABLE,
            Constants.RSRES_ID_FIELD_NAME, createFor, "_setup");
        saveWorkRequest(dsWr, cleanupRequest, dataRecord, Constants.RESERVE_RS_TABLE,
            Constants.RSRES_ID_FIELD_NAME, createFor, "_cleanup");
    }

    /**
     * Retrieve reservation data to create the work requests.
     *
     * @param event resource reservation
     * @param createForId trade or vendor identifier
     * @param createFor tr_id or vn_id when creating for a trade or vendor
     * @param messages localized messages used in the work request description
     * @param statusForWorkRequest new status for the work request
     * @return the reservations data in a reserve_rm record with a lot of virtual fields
     */
    private DataRecord retrieveData(final JSONObject event, final String createForId,
            final String createFor, final Map<String, String> messages,
            final String statusForWorkRequest) {

        final String resId = event.getString(Constants.RES_ID);
        final String rsResId = event.getString(Constants.RSRES_ID_FIELD_NAME);

        String querySql = " SELECT reserve_rs.res_id, reserve_rs.rsres_id, "
                + " reserve_rs.bl_id, reserve_rs.fl_id, reserve_rs.rm_id, "
                + " bl.site_id ${sql.as} site_id, "
                + " reserve.user_requested_by ${sql.as} requestor, "
                + SqlUtils.formatValueForSql(statusForWorkRequest)
                + " ${sql.as} status, reserve_rs.date_start ${sql.as} date_assigned_setup, "
                + " reserve_rs.date_end ${sql.as} date_assigned_cleanup, "
                + SqlUtils.formatValueForSql(createForId) + " ${sql.as} " + createFor + ", "
                + " reserve.phone ${sql.as} phone, reserve.dv_id ${sql.as} dv_id, reserve.dp_id ${sql.as} dp_id, "
                + "reserve_rs.time_start ${sql.as} time_assigned_setup, "
                + "reserve_rs.time_end ${sql.as} time_assigned_cleanup, "
                + " ${sql.isNull('-resources.pre_block', 0)} ${sql.as} delta_time_setup, "
                + " 0 ${sql.as} delta_time_cleanup, "
                + " ${sql.isNull('resources.pre_block', 0)}/60 ${sql.as} est_labor_hours_setup, "
                + " ${sql.isNull('resources.post_block', 0)}/60 ${sql.as} est_labor_hours_cleanup, "
                + getWrDescriptionSql(messages.get(SETUP_DESC), messages.get(QUANTITY_DESC),
                    messages.get(COMMENTS_DESC))
                + " ${sql.as} description_setup, "
                + getWrDescriptionSql(messages.get(CLEANUP_DESC), messages.get(QUANTITY_DESC),
                    messages.get(COMMENTS_DESC))
                + " ${sql.as} description_cleanup, " + " 'RES. SETUP' ${sql.as} prob_type_setup, "
                + " 'RES. CLEANUP' ${sql.as} prob_type_cleanup "
                + " FROM reserve_rs, reserve, resources, bl "
                + " WHERE reserve_rs.res_id=reserve.res_id " + " AND reserve_rs.res_id= " + resId
                + " AND reserve_rs.rsres_id= " + rsResId
                + " AND reserve_rs.resource_id=resources.resource_id "
                + " AND reserve_rs.bl_id = bl.bl_id ";

        if (!SchemaUtils.fieldExistsInSchema("reserve_rs", "date_end")) {
            querySql = querySql.replace("reserve_rs.date_end", "reserve_rs.date_start");
        }

        final DataSource ds0 = DataSourceFactory.createDataSource();
        ds0.addTable(Constants.RESERVE_RS_TABLE, DataSource.ROLE_MAIN);
        ds0.setApplyVpaRestrictions(false);
        ds0.addQuery(querySql);

        // Add all virtual fields
        addVirtualFields(ds0, Constants.RESERVE_RS_TABLE, Constants.RSRES_ID_FIELD_NAME, createFor);

        return ds0.getRecord();
    }

    /**
     * Get the SQL string for the work request description.
     *
     * @param headerDesc header for the description
     * @param reservationQuantity title for quantity
     * @param reservationComments title for reservation comments
     * @return SQL string
     */
    private String getWrDescriptionSql(final String headerDesc, final String reservationQuantity,
            final String reservationComments) {
        final StringBuffer wrDescriptionSql = new StringBuffer();
        wrDescriptionSql.append(SqlUtils.formatValueForSql(headerDesc) + SQL_CONCAT_DOT);

        wrDescriptionSql.append(SqlUtils.formatValueForSql(reservationQuantity) + SQL_CONCAT_SPACE
                + " CAST(reserve_rs.quantity AS VARCHAR(5)) " + SQL_CONCAT_DOT);

        wrDescriptionSql.append(SqlUtils.formatValueForSql(reservationComments) + SQL_CONCAT_SPACE
                + " ${sql.trim(sql.isNull('reserve.comments','\\'\\''))} " + SQL_CONCAT_DOT
                + " ${sql.trim(sql.isNull('reserve_rs.comments','\\'\\''))}");

        return wrDescriptionSql.toString();
    }

}
