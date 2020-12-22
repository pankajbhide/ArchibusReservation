package com.archibus.app.reservation.service.helpers;

import java.util.*;

import com.archibus.app.reservation.dao.datasource.*;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.service.*;
import com.archibus.app.reservation.util.EmailNotificationHelper;
import com.archibus.datasource.DataSource;
import com.archibus.datasource.data.*;
import com.archibus.datasource.restriction.Restrictions;

/**
 * The Class ApproveReservationServiceHelper. Although this class uses RoomReservation domain
 * objects, it can also handle Resource-only Reservations. They will be treated like
 * RoomReservations without a room allocated.
 */
public class ApproveReservationServiceHelper extends RoomReservationWfrBase {

    /** Error message when a calendar cancel for a rejected reservation failed. */
    // @translatable
    private static final String CALENDAR_REJECT_ERROR =
            "Reservation [{0}] rejected but but an error occurred updating the requestor's calendar and notifying attendees.";

    /** The resource allocation data source. */
    private ResourceAllocationDataSource resourceAllocationDataSource;

    /** The work requests service. */
    private WorkRequestService workRequestService;

    /**
     * Reject a list of resource allocations.
     *
     * @param records the resource allocations to reject
     * @param comments the comments to include in the rejection
     */
    public void rejectResourceAllocations(final DataSetList records, final String comments) {
        this.resourceAllocationDataSource.setApplyVpaRestrictions(false);
        this.resourceAllocationDataSource
            .addRestriction(Restrictions.eq(this.resourceAllocationDataSource.getMainTableName(),
                Constants.STATUS, Constants.STATUS_AWAITING_APP));
        final Date rejectedDate = new Date();
        for (final DataRecord record : records.getRecords()) {
            final Integer allocationId =
                    this.resourceAllocationDataSource.convertRecordToObject(record).getId();
            final ResourceAllocation allocation =
                    this.resourceAllocationDataSource.get(allocationId);
            Integer reservationId = null;
            if (allocation != null) {
                allocation.setStatus(Constants.STATUS_REJECTED);
                allocation.setComments(comments);
                allocation.setRejectedDate(rejectedDate);
                this.resourceAllocationDataSource.update(allocation);
                reservationId = allocation.getReserveId();
            }

            // Check whether the main reservation is now completely approved or rejected.
            if (reservationId != null) {
                final RoomReservation reservation = this.reservationDataSource.get(reservationId);
                checkMasterReservationApproval(reservation, false);

                /*
                 * APP-6941 subtract resource allocation cost from total cost if this rejection
                 * doesn't cause the entire reservation to be rejected
                 */
                if (!Constants.STATUS_REJECTED.equals(reservation.getStatus())) {
                    reservation.setCost(reservation.getCost() - allocation.getCost());
                    this.reservationDataSource.update(reservation);
                }

                // update associated wr record
                this.workRequestService.createWorkRequest(reservation, false);

                EmailNotificationHelper.sendNotifications(reservationId);
            }
        }
    }

    /**
     * Creates the work request and checks the approval status of the master reservation.
     *
     * @param reservationId the reservation id
     */
    private void createWorkRequest(final Integer reservationId) {
        // Check whether the main reservation is now completely approved.
        if (reservationId != null) {
            final RoomReservation reservation = this.reservationDataSource.get(reservationId);
            checkMasterReservationApproval(reservation, true);

            // update associated wr record
            this.workRequestService.createWorkRequest(reservation, false);
        }
    }

    /**
     * Approve a list of room allocations.
     *
     * @param records the room allocations to approve
     */
    public void approveRoomAllocations(final DataSetList records) {
        this.roomAllocationDataSource.setApplyVpaRestrictions(false);
        this.roomAllocationDataSource
            .addRestriction(Restrictions.eq(this.roomAllocationDataSource.getMainTableName(),
                Constants.STATUS, Constants.STATUS_AWAITING_APP));
        for (final DataRecord record : records.getRecords()) {
            final Integer allocationId =
                    this.roomAllocationDataSource.convertRecordToObject(record).getId();
            final RoomAllocation allocation = this.roomAllocationDataSource.get(allocationId);
            Integer reservationId = null;
            if (allocation != null) {
                allocation.setStatus(Constants.STATUS_CONFIRMED);
                this.roomAllocationDataSource.update(allocation);
                reservationId = allocation.getReserveId();
            }
            createWorkRequest(reservationId);
        }
    }

    /**
     * Approve a list of resource allocations.
     *
     * @param records the resource allocations to approve
     */
    public void approveResourceAllocations(final DataSetList records) {
        this.resourceAllocationDataSource.setApplyVpaRestrictions(false);
        this.resourceAllocationDataSource
            .addRestriction(Restrictions.eq(this.resourceAllocationDataSource.getMainTableName(),
                Constants.STATUS, Constants.STATUS_AWAITING_APP));
        for (final DataRecord record : records.getRecords()) {
            final Integer allocationId =
                    this.resourceAllocationDataSource.convertRecordToObject(record).getId();
            final ResourceAllocation allocation =
                    this.resourceAllocationDataSource.get(allocationId);
            Integer reservationId = null;
            if (allocation != null) {
                allocation.setStatus(Constants.STATUS_CONFIRMED);
                this.resourceAllocationDataSource.update(allocation);
                reservationId = allocation.getReserveId();
            }

            createWorkRequest(reservationId);
        }
    }

    /**
     * Check whether the status of the main reserve record needs to be changed to Approved.
     *
     * @param reservation the reservation to check
     * @param sendNotificationOnApprove true to send a notification when all has been approved,
     *            false otherwise
     */
    private void checkMasterReservationApproval(final RoomReservation reservation,
            final boolean sendNotificationOnApprove) {
        if (reservation != null && Constants.STATUS_AWAITING_APP.equals(reservation.getStatus())) {
            boolean allApproved = true;
            boolean oneApproved = false;
            for (final RoomAllocation allocation : reservation.getRoomAllocations()) {
                if (Constants.STATUS_AWAITING_APP.equals(allocation.getStatus())) {
                    allApproved = false;
                } else if (Constants.STATUS_CONFIRMED.equals(allocation.getStatus())) {
                    oneApproved = true;
                }
            }
            for (final ResourceAllocation allocation : reservation.getResourceAllocations()) {
                if (Constants.STATUS_AWAITING_APP.equals(allocation.getStatus())) {
                    allApproved = false;
                } else if (Constants.STATUS_CONFIRMED.equals(allocation.getStatus())) {
                    oneApproved = true;
                }
            }

            updateApprovalStatus(reservation, allApproved, oneApproved, sendNotificationOnApprove);
        }
    }

    /**
     * Update the reservation approval status based on the given parameters.
     *
     * @param reservation the reservation to update if required
     * @param allApproved true if all allocation have been approved (i.e. none are awaiting
     *            approval)
     * @param oneApproved true if at least one allocation has been approved
     * @param sendNotificationOnApprove true to send a notification when all has been approved,
     *            false otherwise
     */
    private void updateApprovalStatus(final RoomReservation reservation, final boolean allApproved,
            final boolean oneApproved, final boolean sendNotificationOnApprove) {
        if (allApproved && oneApproved) {
            // None of the allocations are awaiting approval and at least one is confirmed.
            reservation.setStatus(Constants.STATUS_CONFIRMED);
            this.reservationDataSource.update(reservation);

            if (sendNotificationOnApprove) {
                // Notify the organizer that his entire reservation has been approved.
                EmailNotificationHelper.sendNotifications(reservation.getReserveId());
            }
        } else if (allApproved && !oneApproved) {
            // None of the allocations are awaiting approval and none are confirmed.
            // This means all have been cancelled or rejected.
            reservation.setStatus(Constants.STATUS_REJECTED);
            this.reservationDataSource.update(reservation);

            // No need to notify organizer here, it is already called in rejectReservation
            // regardless of whether ALL allocations were rejected.
        }
    }

    /**
     * Reject a list of room allocations.
     *
     * @param records the room allocations to reject
     * @param comments the comments to include in the rejection
     */
    public void rejectRoomAllocations(final DataSetList records, final String comments) {
        this.roomAllocationDataSource.setApplyVpaRestrictions(false);
        this.roomAllocationDataSource
            .addRestriction(Restrictions.eq(this.roomAllocationDataSource.getMainTableName(),
                Constants.STATUS, Constants.STATUS_AWAITING_APP));
        for (final DataRecord record : records.getRecords()) {
            final Integer allocationId =
                    this.roomAllocationDataSource.convertRecordToObject(record).getId();
            final RoomAllocation allocation = this.roomAllocationDataSource.get(allocationId);
            Integer reservationId = null;
            if (allocation != null) {
                reservationId = allocation.getReserveId();
            }
            // When the room reservation is rejected, always reject the master reservation
            // and all associated resource reservations.
            if (reservationId != null) {
                rejectMasterReservation(reservationId, comments);

                // cancel/stop all associated wr records
                this.workRequestService
                    .cancelWorkRequest(this.reservationDataSource.get(reservationId));
            }
        }
    }

    /**
     * Get the room allocation records that are past their approval time.
     *
     * @param standardTableName name of the table containing approval related fields
     * @return room allocation records, including approval related fields from the room arrangement
     *         table
     */
    public List<DataRecord> getExpiredRoomAllocationRecords(final String standardTableName) {
        return getExpiredAllocationRecords(this.roomAllocationDataSource, standardTableName);
    }

    /**
     * Get the resource allocation records that are past their approval time.
     *
     * @param standardTableName name of the table containing approval related fields
     * @return resource allocation records, including approval related fields from the resources
     *         table
     */
    public List<DataRecord> getExpiredResourceAllocationRecords(final String standardTableName) {
        return getExpiredAllocationRecords(this.resourceAllocationDataSource, standardTableName);
    }

    /**
     * Sets the data source for approving / rejecting resource allocations.
     *
     * @param resourceAllocationDataSource the resource allocation data source to approve / reject
     *            resource allocations
     */
    public void setResourceAllocationDataSource(
            final ResourceAllocationDataSource resourceAllocationDataSource) {
        this.resourceAllocationDataSource = resourceAllocationDataSource;
    }

    /**
     * Sets the work request service for updating work requests when reservations are approved or
     * rejected.
     *
     * @param workRequestService the new work request service
     */
    public void setWorkRequestService(final WorkRequestService workRequestService) {
        this.workRequestService = workRequestService;
    }

    /**
     * Reject the master reservation and the attached room and resource allocations.
     *
     * @param reservationId identifier of the master reservation
     * @param comments the comments to include in the rejection
     */
    private void rejectMasterReservation(final Integer reservationId, final String comments) {
        final RoomReservation reservation = this.reservationDataSource.get(reservationId);
        final Date rejectedDate = new Date();

        for (final RoomAllocation allocation : reservation.getRoomAllocations()) {
            allocation.setStatus(Constants.STATUS_REJECTED);
            allocation.setComments(comments);
            allocation.setRejectedDate(rejectedDate);
            this.roomAllocationDataSource.update(allocation);
        }

        for (final ResourceAllocation allocation : reservation.getResourceAllocations()) {
            allocation.setStatus(Constants.STATUS_REJECTED);
            allocation.setRejectedDate(rejectedDate);
            this.resourceAllocationDataSource.update(allocation);
        }

        reservation.setStatus(Constants.STATUS_REJECTED);
        this.reservationDataSource.update(reservation);

        // cancel the calendar event
        try {
            this.calendarServiceWrapper.cancelSingleRoomCalendarEvent(reservation, comments,
                this.updateOtherReservationsInConferenceCall(reservation, comments, false), true);
        } catch (final CalendarException exception) {
            this.calendarServiceWrapper.handleCalendarException(exception, reservation,
                CALENDAR_REJECT_ERROR, ApproveReservationServiceHelper.class);
        }

        // Send notifications in addition to canceling the calendar event.
        EmailNotificationHelper.sendNotifications(reservationId);
    }

    /**
     * Get the allocation records that are past their approval time from the given data source.
     *
     * @param dataSource data source containing allocation records
     * @param standardTableName name of the table containing approval related fields
     * @return allocation records, including approval related fields from the standard table
     */
    private static List<DataRecord> getExpiredAllocationRecords(
            final AbstractAllocationDataSource<?> dataSource, final String standardTableName) {
        dataSource.setApplyVpaRestrictions(false);
        dataSource.addTable(standardTableName, DataSource.ROLE_STANDARD);
        dataSource.addField(standardTableName, "approve_days");
        dataSource.addField(standardTableName, ApproveReservationService.ACTION_APPROVAL_EXPIRED);
        dataSource.addField(standardTableName, "user_approval_expired");
        dataSource.addRestriction(Restrictions.eq(dataSource.getMainTableName(), Constants.STATUS,
            Constants.STATUS_AWAITING_APP));

        final String expiredRestriction = " -(${sql.daysBeforeCurrentDate('"
                + dataSource.getMainTableName() + Constants.DOT + Constants.DATE_START_FIELD_NAME
                + "')}) < " + standardTableName + ".approve_days";
        dataSource.addRestriction(Restrictions.sql(expiredRestriction));
        return dataSource.getRecords();
    }

}
