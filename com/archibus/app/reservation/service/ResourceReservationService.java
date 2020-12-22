package com.archibus.app.reservation.service;

import java.util.*;

import com.archibus.app.reservation.dao.datasource.*;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.Recurrence;
import com.archibus.app.reservation.service.helpers.*;
import com.archibus.app.reservation.util.*;
import com.archibus.datasource.data.*;

/**
 * Resource Reservation Service for workflow rules in the new reservation module.
 * <p>
 * This class provided all workflow rule for the resource reservation creation view: Called from
 * ab-rr-create-resource-reservation.axvw<br/>
 * <p>
 * <p>
 * The class will be defined as a Spring bean and will reference other Spring beans. <br/>
 * All Spring beans are defined as prototype.
 * </p>
 *
 * @author Bart Vanderschoot
 * @since 21.2
 *
 */
public class ResourceReservationService {

    /** No reservation id error message. */
    // @translatable
    private static final String NO_RESERVATION_ID = "No reservation id provided";

    /** The Constant RESERVE_RES_ID. */
    private static final String RESERVE_RES_ID = "reserve.res_id";

    /** The Constant RESERVE_DATE_START. */
    private static final String RESERVE_DATE_START = "reserve.date_start";

    /** The Constant RESERVE_DATE_END. */
    private static final String RESERVE_DATE_END = "reserve.date_end";

    /** The resource reservation data source. */
    private ResourceReservationDataSource resourceReservationDataSource;

    /** The cancel service. */
    private CancelReservationService cancelReservationService;

    /** The work request service. */
    private WorkRequestService workRequestService;

    /** The resource only reservation service helper. */
    private ResourceReservationServiceHelper resourceReservationServiceHelper;

    /**
     * Save resource reservation.
     *
     * The resource reservation can be a single or a recurrent reservation. When editing a recurrent
     * reservation, the recurrence pattern and reservation dates cannot change. When editing a
     * single occurrence, the date might change.
     *
     * @param reservation the reservation
     * @param resourceList the resource list
     * @param cateringList the catering list
     * @return the reservation record
     */
    public DataRecord saveResourceReservation(final DataRecord reservation,
            final DataSetList resourceList, final DataSetList cateringList) {

        if (reservation.getDate(RESERVE_DATE_END) == null) {
            reservation.setValue(RESERVE_DATE_END, reservation.getDate(RESERVE_DATE_START));
        }

        final ResourceReservation resourceReservation =
                this.resourceReservationDataSource.convertRecordToObject(reservation);

        // make sure the date of time values are set to 1899
        resourceReservation.setStartTime(TimePeriod.clearDate(resourceReservation.getStartTime()));
        resourceReservation.setEndTime(TimePeriod.clearDate(resourceReservation.getEndTime()));

        this.resourceReservationDataSource.addResourceList(resourceReservation, cateringList);
        this.resourceReservationDataSource.addResourceList(resourceReservation, resourceList);
        ReservationWfrServiceHelper.validateEmails(resourceReservation);

        // check the start and end time window
        ResourceReservationServiceHelper.checkResourceAllocations(resourceReservation);

        List<ResourceReservation> createdReservations = null;

        WorkRequestService.setFlagToSendEmailsInSingleJob();
        if (ReservationWfrServiceHelper.isNewRecurrenceOrEditSeries(resourceReservation)) {
            // prepare for new recurring reservation and correct the end date
            final Recurrence recurrence =
                    ReservationWfrServiceHelper.prepareNewRecurrence(resourceReservation);

            // save and return the generated reservation instances
            createdReservations = this.resourceReservationServiceHelper
                .saveRecurringResourceReservation(resourceReservation, recurrence);

            // create or update the work request
            this.workRequestService.createWorkRequest(resourceReservation, true);
        } else {
            this.resourceReservationServiceHelper.saveReservation(resourceReservation);
            // create or update the work request
            this.workRequestService.createWorkRequest(resourceReservation, false);

            createdReservations = new ArrayList<ResourceReservation>();
            createdReservations.add(resourceReservation);
        }
        WorkRequestService.startJobToSendEmailsInSingleJob();
        // store the generated reservation instances in the reservation
        resourceReservation.setCreatedReservations(createdReservations);

        final Integer reservationId = resourceReservation.getReserveId();
        final Integer parentId = resourceReservation.getParentId();
        if (reservationId == null || parentId == null || parentId.equals(reservationId)) {
            // this is a new reservation or an edit of the entire series
            EmailNotificationHelper.sendNotifications(reservationId, parentId, null);
        } else {
            // this is a partial recurring edit: send a notification for each created reservation
            for (final ResourceReservation createdReservation : createdReservations) {
                EmailNotificationHelper.sendNotifications(createdReservation.getReserveId(), null,
                    null);
            }
        }

        // update the reservation record to return
        reservation.setValue(this.resourceReservationDataSource.getMainTableName() + Constants.DOT
                + Constants.RES_ID,
            resourceReservation.getReserveId());
        // reservation.setValue("reserve.res_parent", resourceReservation.getParentId());
        reservation.setNew(false);

        ReservationsContextHelper.ensureResultMessageIsSet();
        return reservation;
    }

    /**
     * Cancel single resource reservation.
     *
     * @param reservationId reservation id
     * @param comments the comments
     */
    public void cancelResourceReservation(final Integer reservationId, final String comments) {
        ResourceReservation resourceReservation = null;
        if (reservationId != null && reservationId > 0) {
            // Get the reservation in the building time zone.
            resourceReservation =
                    this.resourceReservationDataSource.getActiveReservation(reservationId);
        } else {
            throw new ReservationException(NO_RESERVATION_ID, ResourceReservationService.class,
                this.cancelReservationService.getAdminService());
        }
        if (resourceReservation != null) {
            WorkRequestService.setFlagToSendEmailsInSingleJob();
            this.cancelReservationService.cancelReservation(resourceReservation, comments);
            WorkRequestService.startJobToSendEmailsInSingleJob();
            // send notification
            EmailNotificationHelper.sendNotifications(resourceReservation.getReserveId(), null,
                comments);
        }
        ReservationsContextHelper.ensureResultMessageIsSet();
    }

    /**
     * Cancel multiple reservations.
     *
     * @param reservations the reservations to cancel
     * @param comments the comments
     * @return list of reservation ids that could not be cancelled.
     */
    public List<Integer> cancelMultipleResourceReservations(final DataSetList reservations,
            final String comments) {

        WorkRequestService.setFlagToSendEmailsInSingleJob();
        final List<Integer> failures = new ArrayList<Integer>();
        for (final DataRecord record : reservations.getRecords()) {
            // get the active reservation and all allocations
            final int reservationId = record.getInt(RESERVE_RES_ID);
            final ResourceReservation resourceReservation =
                    this.resourceReservationDataSource.get(reservationId);

            if (resourceReservation == null
                    || Constants.STATUS_REJECTED.equals(resourceReservation.getStatus())) {
                failures.add(record.getInt(RESERVE_RES_ID));
            } else if (!Constants.STATUS_CANCELLED.equals(resourceReservation.getStatus())) {
                try {
                    this.resourceReservationDataSource
                        .canBeCancelledByCurrentUser(resourceReservation);
                } catch (final ReservationException exception) {
                    // this one can't be cancelled, so skip and report
                    failures.add(resourceReservation.getReserveId());
                    continue;
                }
                this.cancelReservationService.cancelReservation(resourceReservation, comments);
                // send notification
                EmailNotificationHelper.sendNotifications(resourceReservation.getReserveId(), null,
                    comments);
            }
        }
        WorkRequestService.startJobToSendEmailsInSingleJob();

        ReservationsContextHelper.ensureResultMessageIsSet();
        return failures;
    }

    /**
     * Cancel recurring resource reservation.
     *
     * @param reservationId reservation id of the first occurrence in the series to cancel
     * @param comments the comments
     * @return the list of id that failed
     */
    public List<Integer> cancelRecurringResourceReservation(final Integer reservationId,
            final String comments) {
        final List<Integer> failures = new ArrayList<Integer>();
        if (reservationId != null && reservationId > 0) {
            final IReservation reservation = this.resourceReservationDataSource.get(reservationId);

            WorkRequestService.setFlagToSendEmailsInSingleJob();
            final List<List<IReservation>> cancelResult =
                    this.cancelReservationService.cancelRecurringReservation(reservation, comments);
            WorkRequestService.startJobToSendEmailsInSingleJob();

            final List<IReservation> cancelledReservations = cancelResult.get(0);
            final List<IReservation> failedReservations = cancelResult.get(1);

            // Check if this is the parent reservation, then all occurrences are cancelled.
            // If there are no more active reservations with same parent id, a notification can
            // be sent for the series as a whole.
            if (reservationId.equals(reservation.getParentId()) && failedReservations.isEmpty()) {
                EmailNotificationHelper.sendNotifications(reservationId, reservationId, comments);
            } else {
                for (final IReservation cancelledReservation : cancelledReservations) {
                    EmailNotificationHelper.sendNotifications(cancelledReservation.getReserveId(),
                        null, comments);
                }
            }

            for (final IReservation failure : failedReservations) {
                failures.add(failure.getReserveId());
            }
        } else {
            throw new ReservationException(NO_RESERVATION_ID, ResourceReservationService.class,
                this.cancelReservationService.getAdminService());
        }
        ReservationsContextHelper.ensureResultMessageIsSet();
        return failures;
    }

    /**
     * Calculate total cost of the reservation.
     *
     * The total cost per reservation is calculated and multiplied by the number of occurrences.
     *
     * @param reservation the reservation.
     * @param resources the equipment and services to be reserved
     * @param caterings the catering resources
     * @param numberOfOccurrences the number of occurrences
     * @return total cost of all occurrences
     */
    public Double calculateTotalCost(final DataRecord reservation, final DataSetList resources,
            final DataSetList caterings, final int numberOfOccurrences) {

        final ResourceReservation resourceReservation =
                this.resourceReservationDataSource.convertRecordToObject(reservation);
        // make sure the date of time values are set to 1899
        resourceReservation.setStartTime(TimePeriod.clearDate(resourceReservation.getStartTime()));
        resourceReservation.setEndTime(TimePeriod.clearDate(resourceReservation.getEndTime()));

        // add the resources and catering
        this.resourceReservationDataSource.addResourceList(resourceReservation, caterings);
        this.resourceReservationDataSource.addResourceList(resourceReservation, resources);

        return this.resourceReservationDataSource.calculateCosts(resourceReservation)
                * numberOfOccurrences;
    }

    /**
     * Create a copy of the resource reservation.
     *
     * @param reservationId the reservation id
     * @param reservationName the reservation name
     * @param startDate the start date
     * @return reservation id of the copy
     */
    public Integer copyResourceReservation(final int reservationId, final String reservationName,
            final Date startDate) {
        final ResourceReservation sourceReservation =
                this.resourceReservationDataSource.getActiveReservation(reservationId);

        if (sourceReservation == null) {
            // @translatable
            throw new ReservationException("Resource reservation has been cancelled or rejected.",
                ResourceReservationService.class, this.cancelReservationService.getAdminService());
        }

        final ResourceReservation newReservation = new ResourceReservation();
        sourceReservation.copyTo(newReservation, true);

        newReservation.setStartDate(startDate);
        newReservation.setEndDate(startDate);
        newReservation.setReservationName(reservationName);
        // when copying the new reservation is always a single regular reservation
        newReservation.setReservationType("regular");
        newReservation.setRecurringRule("");
        newReservation.setParentId(null);
        newReservation.setUniqueId(null);

        // copy resource allocations
        ReservationServiceHelper.copyResourceAllocations(sourceReservation, newReservation);

        // check availability of all resources
        this.resourceReservationDataSource.checkResourcesAvailable(newReservation);
        final ResourceReservation copy = this.resourceReservationDataSource.save(newReservation);

        WorkRequestService.setFlagToSendEmailsInSingleJob();
        this.workRequestService.createWorkRequest(copy, false);
        WorkRequestService.startJobToSendEmailsInSingleJob();
        EmailNotificationHelper.sendNotifications(copy.getReserveId(), null, null);
        ReservationsContextHelper.ensureResultMessageIsSet();
        return copy.getReserveId();
    }

    /**
     * Sets the resource reservation data source.
     *
     * @param resourceReservationDataSource the new resource reservation data source
     */
    public void setResourceReservationDataSource(
            final ResourceReservationDataSource resourceReservationDataSource) {
        this.resourceReservationDataSource = resourceReservationDataSource;
    }

    /**
     * Sets the cancel reservation service used to cancel resource reservations.
     *
     * @param cancelReservationService the new cancel reservation service
     */
    public void setCancelReservationService(
            final CancelReservationService cancelReservationService) {
        this.cancelReservationService = cancelReservationService;
    }

    /**
     * Sets the work request service for creating and updating work requests.
     *
     * @param workRequestService the work request service for creating and updating work requests
     */
    public void setWorkRequestService(final WorkRequestService workRequestService) {
        this.workRequestService = workRequestService;
    }

    /**
     * Sets the resource reservation service helper.
     *
     * @param resourceReservationServiceHelper the new resource reservation service helper
     */
    public void setResourceReservationServiceHelper(
            final ResourceReservationServiceHelper resourceReservationServiceHelper) {
        this.resourceReservationServiceHelper = resourceReservationServiceHelper;
    }

}
