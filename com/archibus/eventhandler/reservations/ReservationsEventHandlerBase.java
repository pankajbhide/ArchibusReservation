package com.archibus.eventhandler.reservations;

import java.sql.Time;
import java.util.*;

import org.apache.log4j.Logger;
import org.json.JSONObject;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.TimePeriod;
import com.archibus.app.reservation.util.ReservationsContextHelper;
import com.archibus.context.ContextStore;
import com.archibus.datasource.*;
import com.archibus.datasource.data.DataRecord;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.eventhandler.helpdesk.RequestHandler;
import com.archibus.eventhandler.ondemandwork.*;
import com.archibus.jobmanager.EventHandlerContext;
import com.archibus.utility.*;

/**
 * Base class for reservations email and work request generation.
 */
public class ReservationsEventHandlerBase {

    /** Referenced By value for messages related to the Create Work Request WFR. */
    protected static final String REFERENCED_BY_WR = "CREATEWORKREQUEST_WFR";

    /** Vendor ID field name. */
    protected static final String VN_ID_FIELD = "vn_id";

    /** Trade ID field name. */
    protected static final String TR_ID_FIELD = "tr_id";

    /**
     * Field that indicates whether work requests should be generated for reservations of rooms /
     * resources associated with the trade / vendor.
     */
    protected static final String WR_FROM_RESERVE = "wr_from_reserve";

    /** Concatenate 2 strings and insert a period to end a sentence. */
    protected static final String SQL_CONCAT_DOT = " ${sql.concat} '. ' ${sql.concat} ";

    /** Concatenate 2 strings and insert a space. */
    protected static final String SQL_CONCAT_SPACE = " ${sql.concat} ' ' ${sql.concat} ";

    /** The Reservations Activity ID. */
    protected static final String ACTIVITY_ID = "AbWorkplaceReservations";

    /** Constant: WR table name. */
    protected static final String WR_TABLE = "wr";

    /** Constants: message parameter id. */
    protected static final String MESSAGE = "message";

    /** Constant: phone field name. */
    private static final String PHONE = "phone";

    /** Constant: requestor field name. */
    private static final String REQUESTOR = "requestor";

    /** Constant: time status changed field name. */
    private static final String TIME_STAT_CHG = "time_stat_chg";

    /** Constant: time assigned field name. */
    private static final String TIME_ASSIGNED = "time_assigned";

    /** Constant: date status changed field name. */
    private static final String DATE_STAT_CHG = "date_stat_chg";

    /** Constant: date assigned field name. */
    private static final String DATE_ASSIGNED = "date_assigned";

    /** Constant: wr.time_assigned. */
    private static final String WR_TIME_ASSIGNED = "wr.time_assigned";

    /** Constant: wr.status. */
    private static final String WR_STATUS = "wr.status";

    /** Constant: wr.phone. */
    private static final String WR_PHONE = "wr.phone";

    /** Constant: wr.time_requested. */
    private static final String WR_TIME_REQUESTED = "wr.time_requested";

    /** Constant: wr.date_requested. */
    private static final String WR_DATE_REQUESTED = "wr.date_requested";

    /** Constant: wr.date_assigned. */
    private static final String WR_DATE_ASSIGNED = "wr.date_assigned";

    /** Constant: wr.date_scheduled. */
    private static final String WR_DATE_SCHEDULED = "activity_log.date_scheduled";

    /** Constant: priority field name. */
    private static final String PRIORITY = "priority";

    /** Constant: activity log id field name. */
    private static final String ACTIVITY_LOG_ID = "activity_log_id";

    /** Constant: wr.priority. */
    private static final String WR_PRIORITY = "wr.priority";

    /** Constant: wr.activity_type. */
    private static final String WR_ACTIVITY_TYPE = "wr.activity_type";

    /** Constant: wr.prob_type. */
    private static final String WR_PROB_TYPE = "wr.prob_type";

    /** Constant: wr.description. */
    private static final String WR_DESCRIPTION = "wr.description";

    /** Constant: wr.dp_id. */
    private static final String WR_DP_ID = "wr.dp_id";

    /** Constant: wr.dv_id. */
    private static final String WR_DV_ID = "wr.dv_id";

    /** Constant: wr.requestor. */
    private static final String WR_REQUESTOR = "wr.requestor";

    /** Constant: wr.rm_id. */
    private static final String WR_RM_ID = "wr.rm_id";

    /** Constant: wr.fl_id. */
    private static final String WR_FL_ID = "wr.fl_id";

    /** Constant: wr.bl_id. */
    private static final String WR_BL_ID = "wr.bl_id";

    /** Constant: wr.site_id. */
    private static final String WR_SITE_ID = "wr.site_id";

    /** Constant: wr.wr_id. */
    private static final String WR_ID_FIELD = "wr_id";

    /** Constant: prefix for referenced_by and message_id of email messages. */
    private static final String REF_PREFIX = "NOTIFYREQUESTED";

    /** Constant: suffix for referenced_by of email messages. */
    private static final String REF_SUFFIX = "_WFR";

    /** Constant: department id field name. */
    private static final String DP_ID_FIELD = "dp_id";

    /** Constant: division id field name. */
    private static final String DV_ID_FIELD = "dv_id";

    /** Constant: site id field name. */
    private static final String SITE_ID_FIELD = "site_id";

    /** Constant: wr status cancelled. */
    private static final String WR_STATUS_CAN = "Can";

    /** Constant: wr status stopped. */
    private static final String WR_STATUS_S = "S";

    /** Constant: wr status requested. */
    private static final String WR_STATUS_R = "R";

    /** Constant: wr status assigned to work order / approved. */
    private static final String WR_STATUS_AA = "AA";

    /** Constant: wr status approved. */
    private static final String WR_STATUS_A = "A";

    /** The Constant WR_WR_ID. */
    private static final String WR_WR_ID = "wr.wr_id";

    /** Number of numbered subject messages to retrieve from the messages table. */
    private static final int MAX_SUBJECT = 4;

    /** Number of numbered body messages to retrieve from the messages table. */
    private static final int MAX_BODY = 15;

    /** The logger. */
    protected final Logger logger = Logger.getLogger(this.getClass());

    /**
     * Put all messages of mail in a map.
     *
     * @param context event handler context
     * @param standard : it can be "By" or "For" only
     * @param locale : locale of user
     * @return TreeMap with messages
     */
    public Map<String, String> getMailMessages(final EventHandlerContext context,
            final String standard, final String locale) {

        final String std = standard.toUpperCase();
        final Map<String, String> messageIds = new HashMap<String, String>();
        for (int i = 1; i <= MAX_SUBJECT; i++) {
            messageIds.put("SUBJECT" + i, REF_PREFIX + std + "_SUBJECT_PART" + i);
        }
        for (int i = 1; i <= MAX_BODY; i++) {
            messageIds.put("BODY" + i, REF_PREFIX + std + "_BODY_PART" + i);
        }
        messageIds.put("BODY11_2", REF_PREFIX + std + "_BODY_PART11_2");

        // EC - KB 3040163 - use other message content based on reservation status
        messageIds.put("BODY_PART2_CANCEL", REF_PREFIX + std + "_BODY_PART2_CANCEL");
        messageIds.put("BODY_PART2_REJECT", REF_PREFIX + std + "_BODY_PART2_REJECT");

        final String referencedBy = REF_PREFIX + std + REF_SUFFIX;
        final Map<String, String> longMessages = ReservationsContextHelper.localizeMessages(
            referencedBy, locale, messageIds.values().toArray(new String[messageIds.size()]));

        final Map<String, String> messagesByShortId = new HashMap<String, String>();
        for (final String messageId : messageIds.keySet()) {
            messagesByShortId.put(messageId, longMessages.get(messageIds.get(messageId)));
        }

        return messagesByShortId;
    }

    /**
     * This function will log the error and throw a new Exception with the desired description.
     *
     * @param context event handler context
     * @param logMessage indicates the source of the error
     * @param exceptionMessage the error message
     * @param originalException the original exception that was caught
     */
    protected static void handleError(final EventHandlerContext context, final String logMessage,
            final String exceptionMessage, final Throwable originalException) {
        context.addResponseParameter(MESSAGE, exceptionMessage);
        throw new ExceptionBase(null, exceptionMessage, originalException);
    }

    /**
     * This function will store the error of Email Notification to context with the desired
     * description.
     *
     * @param context event handler context
     * @param logMessage indicates the source of the error
     * @param exceptionMessage the error message
     * @param originalException the original exception that was caught
     * @param address the email address being sent to when the error occurred
     */
    protected static void handleNotificationError(final EventHandlerContext context,
            final String logMessage, final String exceptionMessage,
            final Throwable originalException, final String address) {
        final String errorMessage;
        if (StringUtil.notNullOrEmpty(address)) {
            errorMessage = address + ": " + exceptionMessage;
        } else {
            errorMessage = exceptionMessage;
        }

        String detailMessage = originalException.getMessage();
        if (originalException instanceof ExceptionBase) {
            detailMessage = ((ExceptionBase) originalException).toStringForLogging();
        }
        Logger.getLogger(ReservationsEventHandlerBase.class).warn(detailMessage, originalException);

        context.addResponseParameter(MESSAGE, errorMessage);
    }

    /**
     * Return a value from a Map. If this value does not exist, return empty.
     *
     * @param record the record as a map
     * @param name field name to extract
     * @return the string value
     */
    protected static String getString(final Map<?, ?> record, final String name) {
        String str = (String) record.get(name);
        if (str == null) {
            str = "";
        }
        return str;
    }

    /**
     * Create a work requests data source to cancel / stop work requests for different trades and
     * vendors than the specified ones.
     *
     * @param resId the reservation id
     * @param tradeToCreate the trade for which a work request should be generated
     * @param vendorToCreate the vendor for which a work request should be generated
     * @return the data source with restrictions set
     */
    protected DataSource createDataSourceToCancelOtherWorkRequests(final int resId,
            final String tradeToCreate, final String vendorToCreate) {
        final String[] fields = { WR_ID_FIELD, Constants.RES_ID, Constants.STATUS, DATE_STAT_CHG,
                TIME_STAT_CHG, "wo_id" };

        final DataSource workRequestDataSource =
                DataSourceFactory.createDataSourceForFields(WR_TABLE, fields);
        workRequestDataSource.setApplyVpaRestrictions(false);
        workRequestDataSource.addRestriction(Restrictions.eq(WR_TABLE, Constants.RES_ID, resId));
        workRequestDataSource
            .addRestriction(Restrictions.in(WR_TABLE, Constants.STATUS, "R,Rev,A,AA,I,HP,HA,HL"));
        workRequestDataSource
            .addRestriction(Restrictions.or(Restrictions.isNotNull(WR_TABLE, TR_ID_FIELD),
                Restrictions.isNotNull(WR_TABLE, VN_ID_FIELD)));
        if (StringUtil.notNullOrEmpty(tradeToCreate)) {
            workRequestDataSource
                .addRestriction(Restrictions.or(Restrictions.isNull(WR_TABLE, TR_ID_FIELD),
                    Restrictions.ne(WR_TABLE, TR_ID_FIELD, tradeToCreate)));
        }
        if (StringUtil.notNullOrEmpty(vendorToCreate)) {
            workRequestDataSource
                .addRestriction(Restrictions.or(Restrictions.isNull(WR_TABLE, VN_ID_FIELD),
                    Restrictions.ne(WR_TABLE, VN_ID_FIELD, vendorToCreate)));
        }
        return workRequestDataSource;
    }

    /**
     * Create a work requests data source to save / update work requests. Add restrictions to
     * retrieve the work requests for the specified reservation and trade / vendor.
     *
     * @param createFor tr_id or vn_id to handle trades or vendors
     * @param nameToCreate name of the trade or vendor to set in the restriction
     * @param resId reservation identifier
     * @return the work requests data source with restrictions set
     */
    protected DataSource createDataSourceToSaveWorkRequests(final String createFor,
            final String nameToCreate, final int resId) {
        final DataSource dsWr = DataSourceFactory.createDataSourceForFields(WR_TABLE,
            new String[] { WR_ID_FIELD, PRIORITY, "activity_type", Constants.RES_ID,
                    Constants.RMRES_ID_FIELD_NAME, Constants.RSRES_ID_FIELD_NAME, "est_labor_hours",
                    REQUESTOR, Constants.STATUS, SITE_ID_FIELD, Constants.BL_ID_FIELD_NAME,
                    Constants.FL_ID_FIELD_NAME, Constants.RM_ID_FIELD_NAME, DATE_ASSIGNED,
                    TIME_ASSIGNED, "date_requested", "time_requested", TR_ID_FIELD, VN_ID_FIELD,
                    PHONE, DV_ID_FIELD, DP_ID_FIELD, "description", "prob_type", DATE_STAT_CHG,
                    TIME_STAT_CHG });
        dsWr.setApplyVpaRestrictions(false);
        // Retrieve the existing work request records for this reservation and trade/vendor.
        dsWr.addRestriction(Restrictions.eq(WR_TABLE, Constants.RES_ID, resId));
        dsWr.addRestriction(Restrictions.eq(WR_TABLE, createFor, nameToCreate));
        dsWr.addRestriction(Restrictions.ne(WR_TABLE, Constants.STATUS, WR_STATUS_CAN));
        dsWr.addRestriction(Restrictions.ne(WR_TABLE, Constants.STATUS, WR_STATUS_S));
        dsWr.addSort(WR_TABLE, TIME_ASSIGNED, DataSource.SORT_ASC);
        return dsWr;
    }

    /**
     * Determine the matching work request status based on the room reservation status and building
     * operations application parameter 'WorkRequestsOnly'.
     *
     * @param statusOfReservation reservation status ('Awaiting App.' or 'Confirmed')
     * @param isWorkRequestOnly true if Building Operations uses only work requests
     * @return the matching work request status: R, A or AA
     */
    protected String getStatusForWorkRequest(final String statusOfReservation,
            final boolean isWorkRequestOnly) {
        final String workRequestStatus;
        if (Constants.STATUS_AWAITING_APP.equals(statusOfReservation)) {
            workRequestStatus = WR_STATUS_R;
        } else if (isWorkRequestOnly) {
            workRequestStatus = WR_STATUS_AA;
        } else {
            workRequestStatus = WR_STATUS_A;
        }
        return workRequestStatus;
    }

    /**
     * Calculate the correct time assigned for a work request / work order from the reservation
     * data. This adds the delta_time to the time_assigned. Delta_time corresponds to the room
     * arrangement pre_block for setup and is 0 for cleanup.
     *
     * @param dataRecord the reservation data
     * @param tableName table name for accessing the data
     * @param suffix the suffix indicating _setup or _cleanup for the setup or cleanup work request
     * @return the time value for time_assigned
     */
    protected Time getTimeAssigned(final DataRecord dataRecord, final String tableName,
            final String suffix) {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(dataRecord.getDate(tableName + ".time_assigned" + suffix));
        calendar.add(Calendar.MINUTE, dataRecord.getInt(tableName + ".delta_time" + suffix));
        return TimePeriod.clearDate(calendar.getTime());
    }

    /**
     * Create or update a work request corresponding to reservation setup or cleanup, for a trade or
     * vendor.
     *
     * @param wrDataSource work request data source (to save the work request record)
     * @param wrRecord work request record (new or existing)
     * @param dataRecord the reservation data
     * @param tableName table name for accessing the data
     * @param pkeyFieldName primary key field name for the data
     * @param createFor tr_id or vn_id when creating for a trade or vendor
     * @param suffix _setup or _cleanup when creating for setup or cleanup
     */
    protected void saveWorkRequest(final DataSource wrDataSource, final DataRecord wrRecord,
            final DataRecord dataRecord, final String tableName, final String pkeyFieldName,
            final String createFor, final String suffix) {

        // remember the original status to determine if it's being changed
        final String originalStatus = wrRecord.getString(WR_STATUS);
        wrRecord.setValue(WR_STATUS, dataRecord.getValue(tableName + ".status"));
        final String newStatus = wrRecord.getString(WR_STATUS);

        // copy all relevant values to the work request
        wrRecord.setValue("wr.res_id", dataRecord.getValue(tableName + ".res_id"));
        wrRecord.setValue(WR_TABLE + Constants.DOT + pkeyFieldName,
            dataRecord.getValue(tableName + Constants.DOT + pkeyFieldName));
        wrRecord.setValue(WR_SITE_ID, dataRecord.getValue(tableName + ".site_id"));
        wrRecord.setValue(WR_BL_ID, dataRecord.getValue(tableName + ".bl_id"));
        wrRecord.setValue(WR_FL_ID, dataRecord.getValue(tableName + ".fl_id"));
        wrRecord.setValue(WR_RM_ID, dataRecord.getValue(tableName + ".rm_id"));
        wrRecord.setValue(WR_REQUESTOR, dataRecord.getValue(tableName + ".requestor"));
        wrRecord.setValue(WR_DATE_REQUESTED, Utility.currentDate());
        wrRecord.setValue(WR_TIME_REQUESTED, Utility.currentTime());
        wrRecord.setValue(WR_PHONE, dataRecord.getValue(tableName + ".phone"));
        wrRecord.setValue(WR_DV_ID, dataRecord.getValue(tableName + ".dv_id"));
        wrRecord.setValue(WR_DP_ID, dataRecord.getValue(tableName + ".dp_id"));
        wrRecord.setValue(WR_TABLE + Constants.DOT + createFor,
            dataRecord.getValue(tableName + Constants.DOT + createFor));
        wrRecord.setValue("wr.est_labor_hours",
            dataRecord.getValue(tableName + ".est_labor_hours" + suffix));
        wrRecord.setValue(WR_DATE_ASSIGNED,
            dataRecord.getValue(tableName + ".date_assigned" + suffix));
        wrRecord.setValue(WR_TIME_ASSIGNED, getTimeAssigned(dataRecord, tableName, suffix));
        wrRecord.setValue(WR_DESCRIPTION, dataRecord.getValue(tableName + ".description" + suffix));
        wrRecord.setValue(WR_PROB_TYPE, dataRecord.getValue(tableName + ".prob_type" + suffix));
        // activity type is fixed
        wrRecord.setValue(WR_ACTIVITY_TYPE, "SERVICE DESK - MAINTENANCE");

        final Map<String, Object> wrValues = wrRecord.getValues();

        // convert dates and times to strings in the wrValues map
        wrValues.put(WR_DATE_ASSIGNED, wrRecord.getNeutralValue(WR_DATE_ASSIGNED));
        wrValues.put(WR_TIME_ASSIGNED, wrRecord.getNeutralValue(WR_TIME_ASSIGNED));
        wrValues.put(WR_DATE_REQUESTED, wrRecord.getNeutralValue(WR_DATE_REQUESTED));
        wrValues.put(WR_TIME_REQUESTED, wrRecord.getNeutralValue(WR_TIME_REQUESTED));

        // remove the status value, we only set that directly via status manager
        wrValues.remove(WR_STATUS);

        if (wrRecord.isNew()) {
            submitNewWorkRequest(wrRecord, createFor, wrValues);
        } else if (ReservationsEventHandlerBase.shouldUpdateStatus(originalStatus, newStatus)) {
            // Use the status manager to change the status of an existing work request
            final OnDemandWorkStatusManager statusManager = new OnDemandWorkStatusManager(
                ContextStore.get().getEventHandlerContext(), wrRecord.getInt(WR_WR_ID));
            statusManager.updateStatus(newStatus);
        }

        // Update all relevant work request fields to match the reservation.
        final WorkRequestHandler workRequestHandler = new WorkRequestHandler();
        replaceNulls(wrValues);
        workRequestHandler.editRequestParameters(new JSONObject(wrValues));
    }

    /**
     * Submit a new work request, by first generating an activity log record and submitting that.
     *
     * @param wrRecord the wr record to store the generated id's
     * @param createFor vn_id or tr_id to create for vendor or trade
     * @param wrValues values of the wr record to submit
     */
    private void submitNewWorkRequest(final DataRecord wrRecord, final String createFor,
            final Map<String, Object> wrValues) {
        // copy only the fields that exist in activity log to another map
        final Map<String, Object> activityLogValues = new HashMap<String, Object>();
        activityLogValues.put(WR_SITE_ID, wrValues.get(WR_SITE_ID));
        activityLogValues.put(WR_BL_ID, wrValues.get(WR_BL_ID));
        activityLogValues.put(WR_FL_ID, wrValues.get(WR_FL_ID));
        activityLogValues.put(WR_RM_ID, wrValues.get(WR_RM_ID));
        activityLogValues.put(WR_REQUESTOR, wrValues.get(WR_REQUESTOR));
        activityLogValues.put(WR_DV_ID, wrValues.get(WR_DV_ID));
        activityLogValues.put(WR_DP_ID, wrValues.get(WR_DP_ID));
        activityLogValues.put(WR_TABLE + Constants.DOT + createFor,
            wrValues.get(WR_TABLE + Constants.DOT + createFor));
        activityLogValues.put(WR_DESCRIPTION, wrValues.get(WR_DESCRIPTION));
        activityLogValues.put(WR_PROB_TYPE, wrValues.get(WR_PROB_TYPE));
        activityLogValues.put(WR_ACTIVITY_TYPE, wrValues.get(WR_ACTIVITY_TYPE));
        activityLogValues.put(WR_DATE_SCHEDULED, wrValues.get(WR_DATE_ASSIGNED));

        // Submit the activity log request so BldgOps can generate the work request
        final RequestHandler requestHandler = new RequestHandler();
        replaceNulls(activityLogValues);
        requestHandler.submitRequest("", new JSONObject(activityLogValues));
        final int activityLogId =
                ContextStore.get().getEventHandlerContext().getInt("activity_log.activity_log_id");

        // Query the wr table by activity_log_id to find the wr_id and priority
        final DataSource dataSource = DataSourceFactory.createDataSourceForFields(WR_TABLE,
            new String[] { WR_ID_FIELD, PRIORITY, ACTIVITY_LOG_ID });
        dataSource.addRestriction(Restrictions.eq(WR_TABLE, ACTIVITY_LOG_ID, activityLogId));
        final DataRecord savedRecord = dataSource.getRecord();
        wrRecord.setValue(WR_WR_ID, savedRecord.getInt(WR_WR_ID));
        wrRecord.setValue(WR_PRIORITY, savedRecord.getInt(WR_PRIORITY));
        wrValues.put(WR_WR_ID, wrRecord.getInt(WR_WR_ID));
        wrValues.put(WR_PRIORITY, wrRecord.getInt(WR_PRIORITY));
    }

    /**
     * Replace null values with empty strings in the given record.
     *
     * @param record the record to replace nulls for
     */
    private static void replaceNulls(final Map<String, Object> record) {
        for (final Map.Entry<String, Object> field : record.entrySet()) {
            if (field.getValue() == null) {
                field.setValue("");
            }
        }
    }

    /**
     * Check whether we should update the work request status. This isn't required between A and AA.
     *
     * @param currentStatus the current work request status
     * @param newStatus the new work request status
     * @return true if it should be updated, false if not
     */
    private static boolean shouldUpdateStatus(final String currentStatus, final String newStatus) {
        boolean updateStatus = false;
        if (WR_STATUS_A.equals(newStatus) || WR_STATUS_AA.equals(newStatus)) {
            updateStatus ^= WR_STATUS_A.equals(currentStatus) || WR_STATUS_AA.equals(currentStatus);
        } else {
            updateStatus ^= currentStatus.equals(newStatus);
        }
        return updateStatus;
    }

    /**
     * Add virtual field definitions to the reservations data source used to populate the work
     * requests and work orders.
     *
     * @param ds0 the reservations data source
     * @param tableName the main table name for the data source
     * @param pkeyFieldName field name of the primary key of the main table
     * @param createFor tr_id or vn_id to handle trades or vendors
     */
    protected void addVirtualFields(final DataSource ds0, final String tableName,
            final String pkeyFieldName, final String createFor) {
        ds0.addVirtualField(tableName, Constants.RES_ID, DataSource.DATA_TYPE_INTEGER);
        ds0.addVirtualField(tableName, pkeyFieldName, DataSource.DATA_TYPE_INTEGER);
        ds0.addVirtualField(tableName, Constants.SITE_ID_FIELD_NAME, DataSource.DATA_TYPE_TEXT);
        ds0.addVirtualField(tableName, Constants.BL_ID_FIELD_NAME, DataSource.DATA_TYPE_TEXT);
        ds0.addVirtualField(tableName, Constants.FL_ID_FIELD_NAME, DataSource.DATA_TYPE_TEXT);
        ds0.addVirtualField(tableName, Constants.RM_ID_FIELD_NAME, DataSource.DATA_TYPE_TEXT);

        ds0.addVirtualField(tableName, Constants.STATUS, DataSource.DATA_TYPE_TEXT);
        ds0.addVirtualField(tableName, createFor, DataSource.DATA_TYPE_TEXT);

        ds0.addVirtualField(tableName, REQUESTOR, DataSource.DATA_TYPE_TEXT);
        ds0.addVirtualField(tableName, PHONE, DataSource.DATA_TYPE_TEXT);
        ds0.addVirtualField(tableName, DV_ID_FIELD, DataSource.DATA_TYPE_TEXT);
        ds0.addVirtualField(tableName, DP_ID_FIELD, DataSource.DATA_TYPE_TEXT);

        ds0.addVirtualField(tableName, "est_labor_hours_setup", DataSource.DATA_TYPE_NUMBER);
        ds0.addVirtualField(tableName, "delta_time_setup", DataSource.DATA_TYPE_INTEGER);
        ds0.addVirtualField(tableName, "date_assigned_setup", DataSource.DATA_TYPE_DATE);
        ds0.addVirtualField(tableName, "time_assigned_setup", DataSource.DATA_TYPE_TIME);
        ds0.addVirtualField(tableName, "description_setup", DataSource.DATA_TYPE_TEXT);
        ds0.addVirtualField(tableName, "prob_type_setup", DataSource.DATA_TYPE_TEXT);

        ds0.addVirtualField(tableName, "est_labor_hours_cleanup", DataSource.DATA_TYPE_NUMBER);
        ds0.addVirtualField(tableName, "delta_time_cleanup", DataSource.DATA_TYPE_INTEGER);
        ds0.addVirtualField(tableName, "date_assigned_cleanup", DataSource.DATA_TYPE_DATE);
        ds0.addVirtualField(tableName, "time_assigned_cleanup", DataSource.DATA_TYPE_TIME);
        ds0.addVirtualField(tableName, "description_cleanup", DataSource.DATA_TYPE_TEXT);
        ds0.addVirtualField(tableName, "prob_type_cleanup", DataSource.DATA_TYPE_TEXT);
    }

    /**
     * Get the reservation id's linked to the given reservation id and parent id. If the parent id
     * is 0 then only the reservation id is returned.
     *
     * @param reservationId the single reservation id
     * @param parentId the parent reservation id
     * @return list of reservation id's in string format
     */
    protected List<Integer> getReservationIds(final int reservationId, final int parentId) {
        final List<Integer> vectorResId = new ArrayList<Integer>();
        if (parentId == 0) {
            vectorResId.add(reservationId);
        } else {
            final DataSource reservationDs = DataSourceFactory.createDataSourceForFields(
                Constants.RESERVE_TABLE_NAME, new String[] { Constants.RES_ID });
            reservationDs.addRestriction(
                Restrictions.eq(Constants.RESERVE_TABLE_NAME, Constants.RES_PARENT, parentId));
            final List<DataRecord> recordsSql0 = reservationDs.getRecords();

            if (recordsSql0.isEmpty()) {
                vectorResId.add(0);
            } else {
                for (final DataRecord record : recordsSql0) {
                    vectorResId.add(record
                        .getInt(Constants.RESERVE_TABLE_NAME + Constants.DOT + Constants.RES_ID));
                }
            }
        }
        return vectorResId;
    }

}
