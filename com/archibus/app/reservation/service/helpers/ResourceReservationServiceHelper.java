package com.archibus.app.reservation.service.helpers;

import java.util.*;

import com.archibus.app.reservation.dao.datasource.ResourceReservationDataSource;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.*;
import com.archibus.app.reservation.service.actions.SaveRecurringResourceReservationOccurrenceAction;

/**
 * The Class ResourceOnlyReservationServiceHelper.
 *
 * <p>
 * The class will be defined as a Spring bean and will reference other Spring beans. <br/>
 * All Spring beans are defined as prototype.
 * </p>
 */
public class ResourceReservationServiceHelper {

    /** The resource reservation data source. */
    private ResourceReservationDataSource resourceReservationDataSource;

    /**
     * Check whether resource allocations are all within the time period of the main reservation.
     *
     * @param reservation the reservation to check the resource allocations for
     */
    public static void checkResourceAllocations(final AbstractReservation reservation) {
        for (final ResourceAllocation resourceAllocation : reservation.getResourceAllocations()) {
            // check within room reservation time window
            if (resourceAllocation.getStartTime().before(reservation.getStartTime())
                    || resourceAllocation.getEndTime().after(reservation.getEndTime())) {
                // @translatable
                throw new ReservationException(
                    "Resource allocation exceeds the main reservation timeframe",
                    ResourceReservationServiceHelper.class);
            }
        }
    }

    /**
     * Save recurring resource reservation.
     *
     * @param resourceReservation the resource reservation
     * @param recurrence the recurrence
     * @return the list
     */
    public List<ResourceReservation> saveRecurringResourceReservation(
            final ResourceReservation resourceReservation, final Recurrence recurrence) {

        final List<ResourceReservation> savedReservations = new ArrayList<ResourceReservation>();

        // when editing, fetch the existing reservations
        // no need for timezone conversion, timezone is copied from new reservation object
        final List<ResourceReservation> existingReservations =
                this.resourceReservationDataSource.getByParentId(resourceReservation.getParentId(),
                    resourceReservation.getStartDate(), null, false);

        if (existingReservations == null) {
            insertRecurringReservations(recurrence, savedReservations, resourceReservation);
        } else {
            updateExistingReservations(savedReservations, resourceReservation, existingReservations);
        }

        return savedReservations;
    }

    /**
     * Insert recurring reservations.
     *
     * @param recurrence the recurrence
     * @param savedReservations the saved reservations
     * @param resourceReservation the resource reservation
     */
    public void insertRecurringReservations(final Recurrence recurrence,
            final List<ResourceReservation> savedReservations,
            final ResourceReservation resourceReservation) {

        // for a new recurrent reservation, the dates are calculated
        if (recurrence instanceof AbstractIntervalPattern) {
            final AbstractIntervalPattern pattern = (AbstractIntervalPattern) recurrence;
            resourceReservation.setOccurrenceIndex(1);
            // save the first base reservation
            saveReservation(resourceReservation);
            // Set its parent reservation ID.
            final int parentId =
                    resourceReservation.getParentId() == null ? resourceReservation.getReserveId()
                            : resourceReservation.getParentId();
            this.resourceReservationDataSource.markRecurring(resourceReservation, parentId,
                pattern.toString(), 1);

            // get the saved copy with the proper time zone
            final ResourceReservation activeReservation =
                    this.resourceReservationDataSource.getActiveReservation(resourceReservation
                        .getReserveId());
            savedReservations.add(activeReservation);

            // loop through the pattern using the saved copy
            pattern.loopThroughRepeats(new SaveRecurringResourceReservationOccurrenceAction(
                savedReservations, this.resourceReservationDataSource, activeReservation));
        }
    }

    /**
     * Update existing reservations for resource reservation.
     *
     * @param savedReservations the saved reservations
     * @param resourceReservation the resource reservation
     * @param existingReservations the existing reservations
     */
    public void updateExistingReservations(final List<ResourceReservation> savedReservations,
            final ResourceReservation resourceReservation,
            final List<ResourceReservation> existingReservations) {

        // when editing we loop over existing reservations
        for (final ResourceReservation existingReservation : existingReservations) {
            if (com.archibus.app.reservation.dao.datasource.Constants.STATUS_CANCELLED
                    .equals(existingReservation.getStatus())
                    || com.archibus.app.reservation.dao.datasource.Constants.STATUS_REJECTED
                            .equals(existingReservation.getStatus())) {
                // go to the next and skip this one
                continue;
            }

            // only change attributes that are allowed when editing
            resourceReservation.copyTo(existingReservation, false);
            ReservationServiceHelper.copyResourceAllocations(resourceReservation,
                existingReservation);

            this.resourceReservationDataSource.checkResourcesAvailable(existingReservation);
            // save all
            this.resourceReservationDataSource.save(existingReservation);

            savedReservations.add(existingReservation);
        } // end for
    }

    /**
     * Save reservation.
     *
     * @param resourceReservation the resource reservation
     */
    public void saveReservation(final ResourceReservation resourceReservation) {
        // Check that all resources in the reservation are available
        this.resourceReservationDataSource.checkResourcesAvailable(resourceReservation);

        // if no conflicts, is safe to save
        this.resourceReservationDataSource.save(resourceReservation);
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
}
