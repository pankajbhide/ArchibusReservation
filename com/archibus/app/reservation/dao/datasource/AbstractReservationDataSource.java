package com.archibus.app.reservation.dao.datasource;

import java.util.*;

import org.apache.commons.lang.StringUtils;

import com.archibus.app.reservation.dao.*;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.util.*;
import com.archibus.context.*;
import com.archibus.datasource.*;
import com.archibus.datasource.data.*;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.model.view.datasource.ClauseDef.*;
import com.archibus.model.view.datasource.ParsedRestrictionDef;
import com.archibus.utility.Utility;

/**
 * Abstract Reservation DataSource.
 *
 * This is a base class for reservation objects.
 *
 * @param <T> the generic type
 * @author Bart Vanderschoot
 */
public abstract class AbstractReservationDataSource<T extends AbstractReservation>
        extends ObjectDataSourceImpl<T> implements IReservationDataSource<T> {

    /**
     * Datasource for resource allocations.
     *
     * Every reservation can have resources allocated. When canceling, also resources should be
     * cancelled.
     */
    protected IResourceAllocationDataSource resourceAllocationDataSource;

    /** The resource data source. */
    protected IResourceDataSource resourceDataSource;

    /**
     * Constructor.
     *
     * @param beanName Spring bean name
     * @param tableName table name
     */
    protected AbstractReservationDataSource(final String beanName, final String tableName) {
        super(beanName, tableName);
    }

    /**
     * Cancel the reservation.
     *
     * @param reservation reservation
     * @param comments the cancellation comments
     * @throws ReservationException ReservationException
     */
    @Override
    public void cancel(final T reservation, final String comments) throws ReservationException {
        final User user = ContextStore.get().getUser();
        // check if it can be canceled....
        if (!user.isMemberOfGroup(Constants.RESERVATION_SERVICE_DESK)
                && !user.isMemberOfGroup(Constants.RESERVATION_MANAGER)) {
            checkCancelling(reservation);
        }

        // First update all active resource allocations. This updates their cost as well.
        final List<ResourceAllocation> activeResourceAllocations =
                ReservationUtils.getActiveResourceAllocations(reservation);
        for (final ResourceAllocation resourceAllocation : activeResourceAllocations) {
            this.resourceAllocationDataSource.cancel(resourceAllocation);
        }

        reservation.setStatus(Constants.STATUS_CANCELLED);
        // Compute the new total cost, including late cancellation costs.
        reservation.calculateTotalCost();
        reservation.setLastModifiedBy(user.getEmployee().getId());
        // TODO: use time zone of the building.
        reservation.setCancelledDate(Utility.currentDate());
        reservation.setLastModifiedDate(Utility.currentDate());

        // KB 3054114 Record any Reservation Cancellation Comments in the database
        if (StringUtils.isNotBlank(comments)) {
            final String prevComments = reservation.getComments();
            if (StringUtils.isNotBlank(prevComments)) {
                reservation.setComments(prevComments + '\n' + comments);
            } else {
                reservation.setComments(comments);
            }
        }

        super.update(reservation);
    }

    /**
     * Check constraints and save the reservation.
     *
     * @param reservation reservation
     * @return reservation
     * @throws ReservationException ReservationException
     */
    public final AbstractReservation checkAndSave(final T reservation) throws ReservationException {
        if (reservation.getReserveId() == null || reservation.getReserveId() == 0) {
            // add a new reservation
            final AbstractReservation savedReservation = super.save(reservation);
            // the auto-number pk will return from the saved statement

            this.log
                .debug("Saved reservation with generated id " + savedReservation.getReserveId());

            reservation.setReserveId(savedReservation.getReserveId());
        } else {
            // check if it can be modified....
            final User user = ContextStore.get().getUser();
            if (!user.isMemberOfGroup(Constants.RESERVATION_SERVICE_DESK)
                    && !user.isMemberOfGroup(Constants.RESERVATION_MANAGER)
                    && !checkStatusAndStartDate(reservation)) {
                // @translatable
                throw new ReservationException("The reservation cannot be modified.",
                    AbstractReservationDataSource.class);
            }
            reservation.setLastModifiedBy(user.getEmployee().getId());
            // TODO: timezone?
            reservation.setLastModifiedDate(Utility.currentDate());
            super.update(reservation);
        }

        // cancel all allocation that are removed from the incoming reservation
        this.resourceAllocationDataSource.cancelOther(reservation);

        final List<ResourceAllocation> activeResourceAllocations =
                ReservationUtils.getActiveResourceAllocations(reservation);

        for (final ResourceAllocation resourceAllocation : activeResourceAllocations) {
            resourceAllocation.setReservation(reservation);

            if (resourceAllocation.getId() == null || resourceAllocation.getId() == 0) {
                final ResourceAllocation savedAllocation =
                        this.resourceAllocationDataSource.save(resourceAllocation);
                resourceAllocation.setId(savedAllocation.getId());
            } else {
                this.resourceAllocationDataSource.checkAndUpdate(resourceAllocation);
            }
        }

        return reservation;
    }

    /** {@inheritDoc} */
    @Override
    public final void markRecurring(final T reservation, final Integer parentId,
            final String recurringRule, final int occurrenceIndex) {
        final T storedReservation = this.get(reservation.getReserveId());
        storedReservation.setParentId(parentId);
        storedReservation.setRecurringRule(recurringRule);
        storedReservation.setOccurrenceIndex(occurrenceIndex);
        // reservationType is updated automatically in AbstractReservation
        super.update(storedReservation);

        // Also set it in the reservation parameter to reflect the change.
        reservation.setParentId(parentId);
        reservation.setRecurringRule(recurringRule);
        reservation.setOccurrenceIndex(occurrenceIndex);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T get(final Object reserveId) {
        final T reservation = super.get(reserveId);

        if (reservation != null) {
            reservation.setResourceAllocations(
                this.resourceAllocationDataSource.getResourceAllocations(reservation));
        }
        return reservation;
    }

    /**
     * Get active reservation (including conflicted ones).
     *
     * @param reserveId reserveId
     * @return reservation (or null if no active reservation exists)
     */
    @Override
    public T getActiveReservation(final Object reserveId) {
        T activeReservation = null;

        final T reservation = super.get(reserveId);
        if (reservation != null && (Constants.STATUS_AWAITING_APP.equals(reservation.getStatus())
                || Constants.STATUS_CONFIRMED.equals(reservation.getStatus())
                || Constants.STATUS_ROOM_CONFLICT.equals(reservation.getStatus()))) {
            // the reservation is active, so set the return value and include resource allocations
            activeReservation = reservation;
            activeReservation.setResourceAllocations(
                this.resourceAllocationDataSource.getResourceAllocations(reservation));
        }

        return activeReservation;
    }

    /**
     * Adds the resource list.
     *
     * @param reservation the reservation
     * @param resourceList the resource list
     */
    @Override
    public void addResourceList(final T reservation, final DataSetList resourceList) {
        if (resourceList == null) {
            return;
        }

        for (final DataRecord record : resourceList.getRecords()) {
            record.setValue("reserve_rs.res_id", reservation.getReserveId());
            reservation.addResourceAllocation(
                this.resourceAllocationDataSource.convertRecordToObject(record));
        }
    }

    /**
     * Find active reservations that meet the provided restriction.
     *
     * @param restriction the restriction to apply
     * @param includeConflicted true to include conflicted reservations in the result
     * @return the list of reservations that match the criteria
     */
    protected List<T> getActiveReservations(final ParsedRestrictionDef restriction,
            final boolean includeConflicted) {

        // Include reservations awaiting approval or confirmed.
        restriction.addClause(this.tableName, Constants.STATUS, Constants.STATUS_AWAITING_APP,
            Operation.EQUALS, RelativeOperation.AND_BRACKET);

        restriction.addClause(this.tableName, Constants.STATUS, Constants.STATUS_CONFIRMED,
            Operation.EQUALS, RelativeOperation.OR);

        if (includeConflicted) {
            // also include conflicted reservations
            restriction.addClause(this.tableName, Constants.STATUS, Constants.STATUS_ROOM_CONFLICT,
                Operation.EQUALS, RelativeOperation.OR);
        }

        // KB 3041658 remove record limit
        this.setContext(ContextStore.get().getEventHandlerContext());
        this.setMaxRecords(0);
        final List<T> result = find(restriction);

        for (final AbstractReservation reservation : result) {
            reservation.setResourceAllocations(
                this.resourceAllocationDataSource.getResourceAllocations(reservation));
        }

        return result;
    }

    /**
     * Setter for resourceAllocationDataSource.
     *
     * @param resourceAllocationDataSource resourceAllocationDataSource to set
     */
    public final void setResourceAllocationDataSource(
            final ResourceAllocationDataSource resourceAllocationDataSource) {
        this.resourceAllocationDataSource = resourceAllocationDataSource;
    }

    /**
     * Get all active reservations linked to the same parent reservation ID.
     *
     * @param parentId parent reservation id
     * @param startDate the start date
     * @param endDate the end date
     * @param includeConflicted whether to include conflicted reservations in the result
     *
     * @return list of reservations
     */
    protected final List<T> getActiveReservationsByParentId(final Integer parentId,
            final Date startDate, final Date endDate, final boolean includeConflicted) {
        this.clearRestrictions();
        final ParsedRestrictionDef restriction = new ParsedRestrictionDef();
        restriction.addClause(this.tableName, "res_parent", parentId, Operation.EQUALS);
        // when a startDate is provided select only this one and later
        if (startDate != null) {
            restriction.addClause(this.tableName, "date_start", startDate, Operation.GTE);
        }

        if (endDate != null) {
            restriction.addClause(this.tableName, "date_end", endDate, Operation.LTE);
        }

        return getActiveReservations(restriction, includeConflicted);
    }

    /**
     * Check Approval Required and set the status for all of the reservation's resource allocations.
     *
     * @param reservation the reservation
     * @return true if approval is required for resource allocations in the reservation, false
     *         otherwise
     */
    protected boolean checkResourcesApprovalRequired(final T reservation) {
        boolean approvalRequired = false;

        final List<ResourceAllocation> activeResourceAllocations =
                ReservationUtils.getActiveResourceAllocations(reservation);
        for (final ResourceAllocation resourceAllocation : activeResourceAllocations) {
            final Resource resource =
                    this.resourceDataSource.get(resourceAllocation.getResourceId());

            if (resource.getApprovalRequired() == 1) {
                approvalRequired = true;
                resourceAllocation.setStatus(Constants.STATUS_AWAITING_APP);
            } else {
                resourceAllocation.setStatus(Constants.STATUS_CONFIRMED);
            }
        }
        return approvalRequired;
    }

    /**
     * Mapping to be compatible with version 19.
     *
     * @return mapping of fields to properties
     */
    @Override
    protected Map<String, String> createFieldToPropertyMapping() {
        final Map<String, String> mapping = new HashMap<String, String>();
        mapping.put(this.tableName + ".res_id", "reserveId");

        mapping.put(this.tableName + ".reservation_name", "reservationName");
        mapping.put(this.tableName + ".comments", "comments");
        mapping.put(this.tableName + ".attendees", "attendees");

        mapping.put(this.tableName + ".user_created_by", "createdBy");
        mapping.put(this.tableName + ".user_last_modified_by", "lastModifiedBy");
        mapping.put(this.tableName + ".user_requested_by", "requestedBy");
        mapping.put(this.tableName + ".user_requested_for", "requestedFor");

        mapping.put(this.tableName + ".contact", "contact");
        mapping.put(this.tableName + ".email", "email");
        mapping.put(this.tableName + ".phone", "phone");

        mapping.put(this.tableName + ".dv_id", "divisionId");
        mapping.put(this.tableName + ".dp_id", "departmentId");
        mapping.put(this.tableName + ".ac_id", "accountId");
        mapping.put(this.tableName + ".cost_res", "cost");

        mapping.put(this.tableName + ".date_start", "startDate");
        mapping.put(this.tableName + ".date_end", "endDate");

        mapping.put(this.tableName + ".time_start", "startTime");
        mapping.put(this.tableName + ".time_end", "endTime");

        mapping.put(this.tableName + "." + Constants.UNIQUE_ID, "uniqueId");
        mapping.put(this.tableName + ".status", Constants.STATUS);

        mapping.put(this.tableName + ".date_cancelled", "cancelledDate");
        mapping.put(this.tableName + ".date_last_modified", "lastModifiedDate");

        mapping.put(this.tableName + ".recurring_rule", "recurringRule");
        mapping.put(this.tableName + ".res_type", "reservationType");
        mapping.put(this.tableName + ".res_parent", "parentId");
        mapping.put(this.tableName + Constants.DOT + Constants.RES_CONFERENCE, "conferenceId");
        mapping.put(this.tableName + ".recurring_date_modified", "recurringDateModified");
        mapping.put(this.tableName + Constants.DOT + Constants.OCCURRENCE_INDEX_FIELD,
            "occurrenceIndex");
        mapping.put(this.tableName + ".meeting_private", "meetingPrivate");

        return mapping;
    }

    /**
     * Fields to properties mapping of the reservations data source for version 20.
     *
     * @return array of arrays.
     */
    @Override
    protected final String[][] getFieldsToProperties() {
        return DataSourceUtils.getFieldsToProperties(createFieldToPropertyMapping());
    }

    /**
     * Check the configuration restrictions for cancelling.
     *
     * @param reservation reservation object
     * @throws ReservationException reservation exception is thrown when the reservation cannot be
     *             cancelled.
     */
    protected void checkCancelling(final AbstractReservation reservation)
            throws ReservationException {
        if (!checkStatusAndStartDate(reservation)) {
            // @translatable
            throw new ReservationException("The reservation cannot be cancelled.",
                AbstractReservationDataSource.class);
        }
    }

    /**
     * Check the status and start date of the reservation to determine whether it can be cancelled
     * or modified.
     *
     * @param reservation the reservation to check
     * @return true if status and start date are OK, false otherwise
     */
    private boolean checkStatusAndStartDate(final AbstractReservation reservation) {
        final DataSource dataSrc = this.createCopy();
        if (reservation.getReserveId() == null && reservation.getUniqueId() != null) {
            // check via Outlook unique Id and start date
            dataSrc.addRestriction(
                Restrictions.eq(this.tableName, Constants.UNIQUE_ID, reservation.getUniqueId()));
            dataSrc.addRestriction(Restrictions.eq(this.tableName, Constants.DATE_START_FIELD_NAME,
                reservation.getStartDate()));
        } else {
            // check via reservation ID
            dataSrc.addRestriction(
                Restrictions.eq(this.tableName, Constants.RES_ID, reservation.getReserveId()));
        }
        dataSrc.addRestriction(Restrictions.in(this.tableName, Constants.STATUS,
            Constants.STATUS_AWAITING_APP_OR_CONFIRMED_OR_CONFLICT));
        // check if this reservation is not in the past
        dataSrc.addRestriction(Restrictions.gte(this.tableName, Constants.DATE_START_FIELD_NAME,
            Utility.currentDate()));

        return dataSrc.getRecord() != null;
    }

    /**
     * {@inheritDoc}
     */
    public void setResourceDataSource(final IResourceDataSource resourceDataSource) {
        this.resourceDataSource = resourceDataSource;
    }

}
