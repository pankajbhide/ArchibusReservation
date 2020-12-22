package com.archibus.app.reservation.dao.datasource;

import java.util.*;

import com.archibus.app.reservation.dao.IResourceDataSource;
import com.archibus.app.reservation.domain.*;
import com.archibus.datasource.DataSourceObjectConverter;
import com.archibus.datasource.data.DataRecord;

/**
 * DataSource for Resources.
 *
 * @author Bart Vanderschoot
 */
public class ResourceDataSource extends AbstractResourceDataSource implements IResourceDataSource {

    /** Error message indicating a specific resource is not available for the reservation. */
    // @translatable
    private static final String RESOURCE_NOT_AVAILABLE =
            "The resource {0} is not available for {1}-{2}-{3}";

    /** The Constant RESOURCE_STD. */
    private static final String RESOURCE_STD = "resource_std";

    /**
     * Adds the resource standard table fields.
     */
    public void addResourceStandardTableFields() {
        // add standard table fields
        this.addTable(RESOURCE_STD);
        this.addField(RESOURCE_STD, "resource_name");
        this.addField(RESOURCE_STD, "resource_nature");
        this.addField(RESOURCE_STD, RESOURCE_STD);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void checkQuantityAllowed(final Integer reservationId,
            final ResourceAllocation resourceAllocation, final Resource resource) {
        // check number for limited resources.
        if (resource.getResourceType().equals(ResourceType.LIMITED.toString())
                && resourceAllocation.getQuantity() > 1) {
            final int reserved =
                    this.getNumberOfReservedResources(resourceAllocation.getTimePeriod(),
                        resourceAllocation.getResourceId(), reservationId, true);

            final int total = resource.getQuantity();

            if (reserved + resourceAllocation.getQuantity() > total) {
                throw new ReservableNotAvailableException(resource, reservationId,
                    RESOURCE_NOT_AVAILABLE, ResourceDataSource.class,
                    resourceAllocation.getResourceId(), resourceAllocation.getBlId(),
                    resourceAllocation.getFlId(), resourceAllocation.getRmId());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkResourceAvailable(final String resourceId, final IReservation reservation,
            final TimePeriod timePeriod) {
        final Resource resource = this.get(resourceId);

        List<Resource> resources = null;

        if (resource.getResourceType().equals(ResourceType.UNIQUE.toString())) {
            resources = this.findAvailableUniqueResources(reservation, timePeriod);
        } else if (resource.getResourceType().equals(ResourceType.LIMITED.toString())) {
            resources = this.findAvailableLimitedResources(reservation, timePeriod);
        } else if (resource.getResourceType().equals(ResourceType.UNLIMITED.toString())) {
            resources = this.findAvailableUnlimitedResources(reservation, timePeriod);
        }

        return resources != null && resources.contains(resource);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Resource> findAvailableLimitedResources(final IReservation reservation,
            final TimePeriod timePeriod) throws ReservationException {
        return convertRecordsToObjects(findAvailableLimitedResourceRecords(reservation, timePeriod));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Resource> findAvailableResources(final IReservation reservation,
            final TimePeriod timePeriod, final ResourceType resourceType)
            throws ReservationException {
        List<Resource> result = null;
        switch (resourceType) {
            case UNLIMITED:
                result = findAvailableUnlimitedResources(reservation, timePeriod);
                break;
            case LIMITED:
                result = findAvailableLimitedResources(reservation, timePeriod);
                break;
            case UNIQUE:
                result = findAvailableUniqueResources(reservation, timePeriod);
                break;
            default:
                // @translatable
                throw new ReservationException("Resource type {0} not allowed",
                    ResourceDataSource.class, resourceType);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Resource> findAvailableUniqueResources(final IReservation reservation,
            final TimePeriod timePeriod) throws ReservationException {
        return convertRecordsToObjects(findAvailableUniqueResourceRecords(reservation, timePeriod));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Resource> findAvailableUnlimitedResources(final IReservation reservation,
            final TimePeriod timePeriod) throws ReservationException {
        return convertRecordsToObjects(findAvailableUnlimitedResourceRecords(reservation,
            timePeriod, false));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DataRecord> convertObjectsToRecords(final List<Resource> resources) {
        final DataSourceObjectConverter<Resource> dataSourceObjectConverter =
                new DataSourceObjectConverter<Resource>();
        final Map<String, String> mapping = this.createFieldToPropertyMapping();

        final List<DataRecord> records = new ArrayList<DataRecord>();
        for (final Resource resource : resources) {
            final DataRecord record = this.createRecord();
            dataSourceObjectConverter.convertObjectToRecord(resource, record, mapping, true);
            records.add(record);
        }

        return records;
    }

}
