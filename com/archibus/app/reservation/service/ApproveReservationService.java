package com.archibus.app.reservation.service;

import java.util.List;

import org.apache.log4j.Logger;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.service.helpers.ApproveReservationServiceHelper;
import com.archibus.app.reservation.util.*;
import com.archibus.context.ContextStore;
import com.archibus.datasource.data.*;
import com.archibus.eventhandler.reservations.ReservationsCommonHandler;
import com.archibus.jobmanager.EventHandlerContext;


/**
 * Room Reservation Service for workflow rules in the new reservation module.
 * <p>
 * This class provided all workflow rule for the room reservation approval view: Called from
 * ab-rr-approve-reservations.axvw<br/>
 * Approve and rejecting room reservations will have different behaviour in the module and need
 * different workflow rules to call.
 * <p>
 * <p>
 * The class will be defined as a Spring bean and will reference other Spring beans. <br/>
 * The Calendar service can have different implementations that implement the ICalendar interface. <br/>
 * All Spring beans are defined as prototype.
 * </p>
 *
 * @author Bart Vanderschoot
 * @since 21.2
 *
 */
public class ApproveReservationService {

    /** Field name for approval expired action. */
    public static final String ACTION_APPROVAL_EXPIRED = "action_approval_expired";

    /** Used to indicate that the reservations to approve are room reservations. */
    protected static final String RESERVATION_TYPE_ROOM = "room";

    /** Used to indicate that the reservations to approve are resource reservations. */
    protected static final String RESERVATION_TYPE_RESOURCE = "resource";

    /** Approve when approval time has expired. */
    protected static final int EXPIRE_APPROVE = 0;

    /** Reject when approval time has expired. */
    protected static final int EXPIRE_REJECT = 1;

    /** Notify user when approval time has expired. */
    protected static final int EXPIRE_NOTIFY = 2;

    /** Room arrangements table. */
    private static final String RM_ARRANGE_TABLE = "rm_arrange";

    /** Resources table. */
    private static final String RESOURCES_TABLE = "resources";

    /** Room allocations table. */
    private static final String RESERVE_RM_TABLE = "reserve_rm";

    /** Resource allocations table. */
    private static final String RESERVE_RS_TABLE = "reserve_rs";

    /** The approve reservation service helper. */
    private ApproveReservationServiceHelper approveReservationServiceHelper;

    /** The logger. */
    private final Logger logger = Logger.getLogger(this.getClass());

    /**
     * Approve room reservation.
     *
     * No interaction with calendar service when approving. The invitations are sent at creation.
     * The organizer does receive an email notification when his entire reservation has been
     * approved.
     *
     * @param reservationType the type of reservations (room or resource)
     * @param records the reservations to approve
     */
    public void approveReservation(final String reservationType, final DataSetList records) {
        // Approval security groups are checked in the view file.

        if (RESERVATION_TYPE_ROOM.equals(reservationType)) {
            this.approveReservationServiceHelper.approveRoomAllocations(records);
        } else if (RESERVATION_TYPE_RESOURCE.equals(reservationType)) {
            this.approveReservationServiceHelper.approveResourceAllocations(records);
        }

        // no interaction with calendar service

        // Set result message to indicate success.
        ReservationsContextHelper.ensureResultMessageIsSet();
    }

    /**
     * Reject room reservation.
     *
     * The appointment will be cancelled.
     *
     * @param reservationType the type of reservations to reject
     * @param records the reservations to reject
     * @param comments the comments
     */
    public void rejectReservation(final String reservationType, final DataSetList records,
            final String comments) {
        // Approval security group is checked in the view file.

        if (RESERVATION_TYPE_ROOM.equals(reservationType)) {
            this.approveReservationServiceHelper.rejectRoomAllocations(records, comments);
        } else if (RESERVATION_TYPE_RESOURCE.equals(reservationType)) {
            this.approveReservationServiceHelper.rejectResourceAllocations(records, comments);
        }

        // Set result message to indicate success.
        ReservationsContextHelper.ensureResultMessageIsSet();
    }

    /**
     * The room arrangements and resources tables both have defined the approve_days value, which
     * indicates number of days to approve the reservation if it's in status "Awaiting App.". When
     * time expired, then this WFR will check which action to execute (action_approval_expired):
     * automatically Cancel it, Approve it, or Notify (the user defined in user_approval_expired)
     */
    public void checkRoomAndResourcesApproval() {
        processExpiredReservations(
            this.approveReservationServiceHelper.getExpiredRoomAllocationRecords(RM_ARRANGE_TABLE),
            RESERVATION_TYPE_ROOM, RM_ARRANGE_TABLE);

        processExpiredReservations(
            this.approveReservationServiceHelper.getExpiredResourceAllocationRecords(RESOURCES_TABLE),
            RESERVATION_TYPE_RESOURCE, RESOURCES_TABLE);
    }

    /**
     * Sets the approve reservation service helper.
     *
     * @param approveReservationServiceHelper the new approve reservation service helper
     */
    public void setApproveReservationServiceHelper(
            final ApproveReservationServiceHelper approveReservationServiceHelper) {
        this.approveReservationServiceHelper = approveReservationServiceHelper;
    }

    /**
     * Process a set of expired reservations.
     *
     * @param records the expired reservations (reserve_rm or reserve_rs records)
     * @param reservationType the type of reservations
     * @param tableName rm_arrange or resources
     */
    private void processExpiredReservations(final List<DataRecord> records,
            final String reservationType, final String tableName) {
        final String rejectMessage = ReservationsContextHelper.localizeMessage("CHECKROOMANDRESOURCESAPPROVAL_WFR",
                ContextStore.get().getUser().getLocale(), "CHECKROOMANDRESOURCESAPPROVALREJECTMESSAGE");

        final DataSetList recordsToApprove = new DataSetList();
        final DataSetList recordsToReject = new DataSetList();
        final DataSetList recordsToNotify = new DataSetList();
        for (final DataRecord record : records) {
            final int action = record.getInt(tableName + Constants.DOT + ACTION_APPROVAL_EXPIRED);
            switch (action) {
                case EXPIRE_APPROVE:
                    recordsToApprove.addRecord(record);
                    break;
                case EXPIRE_REJECT:
                    recordsToReject.addRecord(record);
                    break;
                case EXPIRE_NOTIFY:
                    recordsToNotify.addRecord(record);
                    break;
                default:
                    // do nothing
                    this.logger.warn("Skipping invalid action_approval_expired in " + tableName + ": "
                            + action);
                    break;
            }
        }

        this.approveReservation(reservationType, recordsToApprove);
        this.notifyApprovers(recordsToNotify.getRecords(), reservationType, tableName);
        this.rejectReservation(reservationType, recordsToReject, rejectMessage);
    }

    /**
     * Notify the approvers for the given allocations.
     *
     * @param records the allocation to notify the approvers for
     * @param reservationType the type of reservations
     * @param standardTableName rm_arrange or resources
     */
    private void notifyApprovers(final List<DataRecord> records, final String reservationType,
            final String standardTableName) {
        if (EmailNotificationHelper.notificationsEnabled()) {

            final EventHandlerContext context = ContextStore.get().getEventHandlerContext();
            final ReservationsCommonHandler handler = new ReservationsCommonHandler();
            String reservationIdFieldName = null;
            String primaryKeyFieldName = null;

            // Get the correct field names depending on which table we're processing.
            if (RESERVATION_TYPE_ROOM.equals(reservationType)) {
                reservationIdFieldName = RESERVE_RM_TABLE + Constants.DOT + Constants.RES_ID;
                primaryKeyFieldName =
                        RESERVE_RM_TABLE + Constants.DOT + Constants.RMRES_ID_FIELD_NAME;
            } else if (RESERVATION_TYPE_RESOURCE.equals(reservationType)) {
                reservationIdFieldName = RESERVE_RS_TABLE + Constants.DOT + Constants.RES_ID;
                primaryKeyFieldName =
                        RESERVE_RS_TABLE + Constants.DOT + Constants.RSRES_ID_FIELD_NAME;
            }

            for (final DataRecord record : records) {
                context.addResponseParameter("res_id",
                    record.getNeutralValue(reservationIdFieldName));
                context.addResponseParameter("rmrsres_id",
                    record.getNeutralValue(primaryKeyFieldName));
                context.addResponseParameter("user_to_notify",
                    record.getString(standardTableName + ".user_approval_expired"));
                handler.notifyApprover(context, reservationType);
            }
        }
    }

}
