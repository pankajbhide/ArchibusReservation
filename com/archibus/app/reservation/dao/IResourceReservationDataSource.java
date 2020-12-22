package com.archibus.app.reservation.dao;

import com.archibus.app.reservation.domain.ResourceReservation;

/**
 * The Interface IResourceReservationDataSource, used for reservations without an included room
 * booking.
 */
public interface IResourceReservationDataSource extends IReservationDataSource<ResourceReservation> {
    
    /**
     * Check that all resources in the given reservation are available.
     * 
     * @param resourceReservation the reservation to check
     */
    void checkResourcesAvailable(final ResourceReservation resourceReservation);

}
