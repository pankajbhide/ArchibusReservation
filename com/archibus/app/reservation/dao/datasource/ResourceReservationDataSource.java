package com.archibus.app.reservation.dao.datasource;

import java.util.*;

import com.archibus.app.reservation.dao.IResourceReservationDataSource;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.util.ReservationUtils;
import com.archibus.context.*;
import com.archibus.utility.StringUtil;

/**
 * The Class ResourceReservationDataSource.
 *
 * @author Bart Vanderschoot
 */
public class ResourceReservationDataSource
        extends AbstractReservationDataSource<ResourceReservation>
        implements IResourceReservationDataSource {

    /** Error message indicating a specific resource is not available for the reservation. */
    // @translatable
    private static final String RESOURCE_NOT_AVAILABLE =
            "The resource {0} is not available for this reservation";

    /**
     * Instantiates a new resource reservation data source.
     */
    public ResourceReservationDataSource() {
        this("resourceReservation", "reserve");
    }

    /**
     * Instantiates a new resource reservation data source.
     *
     * @param beanName the bean name
     * @param tableName the table name
     */
    protected ResourceReservationDataSource(final String beanName, final String tableName) {
        super(beanName, tableName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResourceReservation save(final ResourceReservation resourceReservation)
            throws ReservationException {
        // check for status
        checkApprovalRequired(resourceReservation);

        // calculate costs for all allocations and total before saving.
        calculateCosts(resourceReservation);

        // save reservation and resources
        super.checkAndSave(resourceReservation);

        return resourceReservation;
    }

    /**
     * {@inheritDoc}
     */
    public double calculateCosts(final ResourceReservation resourceReservation) {
        final List<ResourceAllocation> activeResourceAllocations =
                ReservationUtils.getActiveResourceAllocations(resourceReservation);

        for (final ResourceAllocation allocation : activeResourceAllocations) {
            this.resourceAllocationDataSource.calculateCost(allocation);
        }

        resourceReservation.calculateTotalCost();

        return resourceReservation.getCost();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void canBeCancelledByCurrentUser(final ResourceReservation resourceReservation)
            throws ReservationException {
        final User user = ContextStore.get().getUser();
        if (!user.isMemberOfGroup(Constants.RESERVATION_SERVICE_DESK)
                && !user.isMemberOfGroup(Constants.RESERVATION_MANAGER)) {
            checkCancelling(resourceReservation);

            // Get the active resource allocations and check whether they can be cancelled.
            final List<ResourceAllocation> activeResourceAllocations =
                    ReservationUtils.getActiveResourceAllocations(resourceReservation);
            for (final ResourceAllocation resourceAllocation : activeResourceAllocations) {
                this.resourceAllocationDataSource.checkCancelling(resourceAllocation);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancel(final ResourceReservation resourceReservation, final String comments)
            throws ReservationException {
        // Get the unmodified reservation, so we do not change anything else (KB 3037585).
        final ResourceReservation unmodifiedReservation =
                this.get(resourceReservation.getReserveId());

        // Then call the super method.
        super.cancel(unmodifiedReservation, comments);
    }

    /**
     * Check Approval Required and set the status for the reservation and all of its allocations.
     * Ignores cancelled and rejected allocations.
     *
     * @param resourceReservation resource reservation
     */
    protected void checkApprovalRequired(final ResourceReservation resourceReservation) {
        // if status is rejected or cancelled, don't change the status
        if (StringUtil.isNullOrEmpty(resourceReservation.getStatus())
                || Constants.STATUS_AWAITING_APP.equals(resourceReservation.getStatus())
                || Constants.STATUS_CONFIRMED.equals(resourceReservation.getStatus())) {

            final boolean approvalRequired = checkResourcesApprovalRequired(resourceReservation);

            resourceReservation.setStatus(
                approvalRequired ? Constants.STATUS_AWAITING_APP : Constants.STATUS_CONFIRMED);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ResourceReservation> getByParentId(final Integer parentId, final Date startDate,
            final Date endDate, final boolean includeConflicted) {
        List<ResourceReservation> result = null;

        if (parentId != null) {
            result = super.getActiveReservationsByParentId(parentId, startDate, endDate,
                includeConflicted);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void checkResourcesAvailable(final ResourceReservation resourceReservation) {
        for (final ResourceAllocation resourceAllocation : ReservationUtils
            .getActiveResourceAllocations(resourceReservation)) {
            final Resource resource =
                    this.resourceDataSource.get(resourceAllocation.getResourceId());
            // Update the resource allocation time period before checking availability.
            resourceAllocation.setReservation(resourceReservation);

            // check if the resource is available for the new location and if it is not reserved for
            // the
            // new reservation date
            final boolean allowed = this.resourceDataSource.checkResourceAvailable(
                resourceAllocation.getResourceId(), resourceReservation,
                resourceAllocation.getTimePeriod());

            if (!allowed) {
                throw new ReservableNotAvailableException(resource,
                    resourceReservation.getReserveId(), RESOURCE_NOT_AVAILABLE,
                    ResourceReservationDataSource.class, resourceAllocation.getResourceId());
            }

            // check number for limited resources.
            this.resourceDataSource.checkQuantityAllowed(resourceReservation.getReserveId(),
                resourceAllocation, resource);
        } // end for
    }

}
