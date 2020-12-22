package com.archibus.app.reservation.service.actions;

import java.util.*;

import com.archibus.app.reservation.dao.IResourceDataSource;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.AbstractIntervalPattern;

/**
 * Provides a method to find available resources for all occurrences in an IntervalPattern, via
 * implementation of the OccurrenceAction interface.
 *
 * @author Bart Vanderschoot
 *
 * @since 21.2
 */
public class FindAvailableResourcesOccurrenceAction implements
        AbstractIntervalPattern.OccurrenceAction {
    
    /**
     * The remaining possible resources. Only the the resources that are available for all
     * occurrences are retained.
     */
    private final List<Resource> resources;
    
    /** The resources data source, used to look for available resources. */
    private final IResourceDataSource resourceDataSource;
    
    /** The reservation used to look for available resources. */
    private final IReservation reservation;
    
    /** The resource type. */
    private final ResourceType resourceType;
    
    /**
     * Constructor.
     *
     * @param firstReservation the reservation object representing the first occurrence of the
     *            interval pattern
     * @param resources the resources
     * @param resourceType the resource type
     * @param resourceDataSource the resource data source
     */
    public FindAvailableResourcesOccurrenceAction(final IReservation firstReservation,
            final List<Resource> resources, final ResourceType resourceType,
            final IResourceDataSource resourceDataSource) {
        this.reservation = firstReservation;
        this.resources = resources;
        this.resourceDataSource = resourceDataSource;
        this.resourceType = resourceType;
    }
    
    /**
     * {@inheritDoc}
     */
    public boolean handleOccurrence(final Date date) throws ReservationException {
        
        final TimePeriod timePeriod =
                new TimePeriod(date, date, this.reservation.getStartTime(),
                    this.reservation.getEndTime());
        
        final List<Resource> myResources =
                this.resourceDataSource.findAvailableResources(this.reservation, timePeriod,
                    this.resourceType);
        this.resources.retainAll(myResources);
        
        return !this.resources.isEmpty();
    }
}
