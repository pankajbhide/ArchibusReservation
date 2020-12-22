package com.archibus.app.reservation.service;

import java.util.*;

import org.json.JSONObject;

import com.archibus.app.reservation.domain.IReservation;
import com.archibus.context.ContextStore;
import com.archibus.datasource.*;
import com.archibus.datasource.data.DataRecord;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.eventhandler.ondemandwork.WorkRequestHandler;
import com.archibus.eventhandler.reservations.*;
import com.archibus.jobmanager.EventHandlerContext;

/**
 * The Class WorkRequestService.
 *
 * This class will create and update work request required for room reservations.
 *
 */
public class WorkRequestService {

    /** The Constant WR_TABLE. */
    private static final String WR_TABLE = "wr";

    /** The Constant STATUS. */
    private static final String STATUS = "status";

    /** The Constant WR_ID. */
    private static final String WR_ID = "wr_id";

    /** The Constant WR_STATUS. */
    private static final String WR_STATUS = "wr.status";

    /** The Constant WR_WR_ID. */
    private static final String WR_WR_ID = "wr.wr_id";

    /** The Constant STRING_0. */
    private static final String STRING_0 = "0";

    /** The Constant RES_PARENT. */
    private static final String RES_PARENT = "res_parent";

    /** The Constant RES_ID. */
    private static final String RES_ID = "res_id";

    /**
     * Creates the work request.
     *
     * @param reservation the room reservation
     * @param editRecurring the edit recurring
     */
    public void createWorkRequest(final IReservation reservation, final boolean editRecurring) {

        final EventHandlerContext context = ContextStore.get().getEventHandlerContext();

        if (reservation.getParentId() != null && editRecurring) {
            context.addResponseParameter(RES_ID, STRING_0);
            context.addResponseParameter(RES_PARENT, reservation.getParentId().toString());
        } else {
            context.addResponseParameter(RES_ID, reservation.getReserveId().toString());
            context.addResponseParameter(RES_PARENT, STRING_0);
        }

        final ReservationsRoomHandler reservationsRoomHandler = new ReservationsRoomHandler();
        reservationsRoomHandler.createWorkRequest(context);

        if (!reservation.getResourceAllocations().isEmpty()) {
            final ReservationsResourcesHandler resourceHandler = new ReservationsResourcesHandler();

            if (reservation.getParentId() == null || reservation.getParentId() == 0) {
                resourceHandler.createResourceWr(context, 0, reservation.getReserveId());
            } else {
                resourceHandler.createResourceWr(context, reservation.getParentId(), 0);
            }

        }
    }

    /**
     * Cancel work request.
     *
     * @param reservation the room reservation
     */
    public void cancelWorkRequest(final IReservation reservation) {

        if (reservation.getReserveId() == null) {
            return;
        }

        final String[] fields = { WR_ID, STATUS, RES_ID };

        final DataSource workRequestDataSource =
                DataSourceFactory.createDataSourceForFields(WR_TABLE, fields);
        workRequestDataSource.setApplyVpaRestrictions(false);
        workRequestDataSource
            .addRestriction(Restrictions.eq(WR_TABLE, RES_ID, reservation.getReserveId()));
        workRequestDataSource
            .addRestriction(Restrictions.in(WR_TABLE, STATUS, "R,Rev,A,AA,I,HP,HA,HL"));

        final List<DataRecord> records = workRequestDataSource.getRecords();

        cancelWorkRequests(workRequestDataSource, records);
    }

    /**
     * Cancel / stop the given work requests using the given data source.
     *
     * @param workRequestDataSource the work requests data source
     * @param workRequestRecords the work requests to cancel / stop
     */
    public static void cancelWorkRequests(final DataSource workRequestDataSource,
            final List<DataRecord> workRequestRecords) {
        final List<DataRecord> recordsToCancel = new ArrayList<DataRecord>();
        final List<DataRecord> recordsToStop = new ArrayList<DataRecord>();

        for (final DataRecord record : workRequestRecords) {
            if (WorkRequestService.canBeCancelled(record)) {
                recordsToCancel.add(record);
            } else {
                recordsToStop.add(record);
            }
        }

        final WorkRequestHandler workRequestHandler = new WorkRequestHandler();

        for (final DataRecord record : recordsToCancel) {
            workRequestHandler.cancelWorkRequest(record.getNeutralValue(WR_WR_ID));
        }

        for (final DataRecord record : recordsToStop) {
            workRequestHandler.updateWorkRequestStatus(new JSONObject(record.getValues()), "S");
        }
    }

    /**
     * Check whether the given work request can be cancelled.
     *
     * @param record the work request record
     * @return true if it can be cancelled, false otherwise
     */
    public static boolean canBeCancelled(final DataRecord record) {
        final String status = record.getString(WR_STATUS);
        return "R".equals(status) || "Rev".equals(status) || "A".equals(status)
                || "AA".equals(status);
    }

    /**
     * Check whether building operations is using only work requests (in this case each work request
     * must be linked to a work order). The default is to use both work orders and work requests.
     *
     * @return true if only work requests are used, false otherwise
     */
    public static boolean isWorkRequestOnly() {
        return com.archibus.service.Configuration.getActivityParameterInt("AbBldgOpsOnDemandWork",
            "WorkRequestsOnly", 0) == 1;
    }

    /**
     * Set the flag to send work request emails as a single job.
     */
    public static void setFlagToSendEmailsInSingleJob() {
        new WorkRequestHandler().setFlagToSendEmailsInSingleJob();
    }

    /**
     * Start the job to send cached work request emails.
     */
    public static void startJobToSendEmailsInSingleJob() {
        new WorkRequestHandler().startJobtoSendEmailListInContext();
    }

}
