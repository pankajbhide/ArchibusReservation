package com.archibus.app.reservation.dao;

import java.util.*;

import com.archibus.app.reservation.domain.*;
import com.archibus.core.dao.IDao;
import com.archibus.datasource.data.DataRecord;

/**
 * The Interface IResourceDataSource.
 */
public interface IResourceDataSource extends IDao<Resource> {
    
    /**
     * Check if the resource is available.
     * 
     * @param resourceId resourceId
     * @param reservation reservation
     * @param timePeriod the time period to check the availability for
     * @return true or false
     */
    boolean checkResourceAvailable(final String resourceId, final IReservation reservation,
            final TimePeriod timePeriod);
    
    /**
     * Find available limited resources.
     * 
     * @param reservation the reservation
     * @param timePeriod the time period to check the availability for
     * @return the list
     * @throws ReservationException the reservation exception
     */
    List<Resource> findAvailableLimitedResources(final IReservation reservation,
            final TimePeriod timePeriod) throws ReservationException;
    
    /**
     * Find available resources.
     * 
     * @param reservation the reservation
     * @param timePeriod the time period to check availability for
     * @param resourceType the resource type
     * @return the list
     * @throws ReservationException the reservation exception
     */
    List<Resource> findAvailableResources(final IReservation reservation,
            final TimePeriod timePeriod, final ResourceType resourceType)
            throws ReservationException;
    
    /**
     * Find available unique resources.
     * 
     * @param reservation the reservation
     * @param timePeriod the time period to check the availability for
     * @return the list
     * @throws ReservationException the reservation exception
     */
    List<Resource> findAvailableUniqueResources(final IReservation reservation,
            final TimePeriod timePeriod) throws ReservationException;
    
    /**
     * Find available unlimited resources.
     * 
     * @param reservation the reservation
     * @param timePeriod the time period to check the availability for
     * @return the list
     * @throws ReservationException the reservation exception
     */
    List<Resource> findAvailableUnlimitedResources(final IReservation reservation,
            final TimePeriod timePeriod) throws ReservationException;
    
    /**
     * Find available limited resource records.
     * 
     * @param reservation reservation object
     * @param timePeriod the time period to check the availability for
     * @return resource records
     * @throws ReservationException the reservation exception
     */
    List<DataRecord> findAvailableLimitedResourceRecords(final IReservation reservation,
            final TimePeriod timePeriod) throws ReservationException;
    
    /**
     * Find available unique resources.
     * 
     * @param reservation reservation object
     * @param timePeriod the time period to check the availability for
     * @return list of resource records.
     * @throws ReservationException the reservation exception
     */
    List<DataRecord> findAvailableUniqueResourceRecords(final IReservation reservation,
            final TimePeriod timePeriod) throws ReservationException;
    
    /**
     * Find available unlimited resource records.
     * 
     * @param reservation reservation object
     * @param timePeriod the time period to check the availability for
     * @param allowPartialAvailability whether to allow resources that are only available for part
     *            of the chosen time frame (i.e. the available range overlaps with the reservation
     *            time period)
     * @return list of resource records
     * @throws ReservationException the reservation exception
     */
    List<DataRecord> findAvailableUnlimitedResourceRecords(final IReservation reservation,
            final TimePeriod timePeriod, final boolean allowPartialAvailability)
            throws ReservationException;
    
    /**
     * Check number of reserved resources for limited resources.
     * 
     * @param timePeriod time period
     * @param resourceId resource id
     * @param reserveId reservation id (when editing)
     * @param includePreAndPostBlocks include pre- and post-blocks as reserved time
     * 
     * @return number of reserved resources for that period.
     * 
     */
    int getNumberOfReservedResources(final TimePeriod timePeriod, final String resourceId,
            final Integer reserveId, boolean includePreAndPostBlocks);
    
    /**
     * Convert objects to records.
     *
     * @param resources the resources
     * @return list of data records
     */
    List<DataRecord> convertObjectsToRecords(List<Resource> resources);

    /**
     * Check whether the resource allocation quantity is allowed for the given resource.
     * Apply this additional check if more than one resource is booked.
     * @param reservationId the reservation identifier
     * @param resourceAllocation the resource allocation
     * @param resource the resource
     */
    void checkQuantityAllowed(final Integer reservationId,
            final ResourceAllocation resourceAllocation, final Resource resource);
    
}
