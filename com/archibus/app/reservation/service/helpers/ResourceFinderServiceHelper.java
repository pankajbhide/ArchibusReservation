package com.archibus.app.reservation.service.helpers;

import java.util.*;

import com.archibus.app.reservation.dao.IReservationDataSource;
import com.archibus.app.reservation.dao.datasource.ResourceDataSource;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.*;
import com.archibus.app.reservation.service.RecurrenceService;
import com.archibus.app.reservation.service.actions.FindAvailableResourcesOccurrenceAction;
import com.archibus.app.reservation.util.*;
import com.archibus.datasource.DataSource;
import com.archibus.datasource.data.*;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.utility.*;

/**
 * Helper for Resource Finder Service.
 * <p>
 * The class will be defined as a Spring bean and will reference other Spring beans. <br/>
 * All Spring beans are defined as prototype.
 * </p>
 *
 * @author Bart Vanderschoot
 * @since 21.2
 */
public class ResourceFinderServiceHelper extends AdminServiceContainer {

    /** Max days ahead field name. */
    private static final String MAX_DAYS_AHEAD = "max_days_ahead";

    /** The Constant RESOURCE_DS. */
    private static final String RESOURCE_DS = "resourceDs";

    /** The Constant RESOURCES_RESOURCE_STD. */
    private static final String RESOURCES_RESOURCE_STD = "resources.resource_std";

    /** The Constant BL_ID. */
    private static final String BL_ID = "bl_id";

    /** Site ID field name. */
    private static final String SITE_ID = "site_id";

    /** The Constant RESOURCES_RESOURCE_ID. */
    private static final String RESOURCES_RESOURCE_ID = "resources.resource_id";

    /** The Constant RESOURCES_QUANTITY. */
    private static final String RESOURCES_QUANTITY = "resources.quantity";

    /** The common data source view. */
    private static final String COMMON_DATASOURCE_VIEW = "ab-rr-room-reservation-datasources.axvw";

    /** The resource data source. */
    private ResourceDataSource resourceDataSource;

    /**
     * Setter for the resourceDataSource property.
     *
     * @param resourceDataSource the resourceDataSource to set
     * @see resourceDataSource
     */
    public final void setResourceDataSource(final ResourceDataSource resourceDataSource) {
        this.resourceDataSource = resourceDataSource;
    }

    /**
     * Find available reservable equipment and services.
     *
     * @param buildingId building identifier
     * @param reservation reservation object (room or resource reservation)
     * @param reservationDataSource room or resource reservation data source
     * @return list of resources (dataSet to be displayed in grid)
     */
    public DataSetList findAvailableReservableResources(final String buildingId,
            final IReservation reservation,
            final IReservationDataSource<? extends AbstractReservation> reservationDataSource) {

        final String recurrenceRule = reservation.getRecurringRule();

        // when editing recurring reservation we don't allow change in pattern and dates
        // when editing a single instance the end date should be set to start date
        List<? extends AbstractReservation> existingReservations = null;

        if (reservation.getParentId() != null
                && !reservation.getStartDate().equals(reservation.getEndDate())) {
            existingReservations = reservationDataSource.getByParentId(reservation.getParentId(),
                reservation.getStartDate(), reservation.getEndDate(), false);
        }

        // create return list of available unique and limited resources
        final List<DataRecord> availableResources = new ArrayList<DataRecord>();

        DataSourceUtils.addCustomViewFields(this.resourceDataSource, COMMON_DATASOURCE_VIEW,
            RESOURCE_DS);

        final List<DataRecord> limitedResources =
                findAvailableLimitedResources(reservation, recurrenceRule, existingReservations);

        updateQuantityForLimitedResources(reservation, recurrenceRule, existingReservations,
            limitedResources);

        final List<DataRecord> uniqueResources =
                findAvailableUniqueResources(reservation, recurrenceRule, existingReservations);

        // add to return list
        availableResources.addAll(limitedResources);
        availableResources.addAll(uniqueResources);

        // sort on resource standard
        Collections.sort(availableResources, new Comparator<DataRecord>() {
            @Override
            public int compare(final DataRecord dataRecord1, final DataRecord dataRecord2) {
                return dataRecord1.getString(RESOURCES_RESOURCE_STD)
                    .compareTo(dataRecord2.getString(RESOURCES_RESOURCE_STD));
            }
        });

        // convert to DataSet
        return new DataSetList(availableResources);
    }

    /**
     * Find available catering resources for the given filters.
     *
     * @param buildingId the building id for local time check
     * @param reservation the reservation object
     * @return the data set for displaying in a grid
     */
    public DataSetList findAvailableCateringResources(final String buildingId,
            final AbstractReservation reservation) {
        this.resourceDataSource.addResourceStandardTableFields();

        // add restriction for end date and days max ahead for recurrence
        final String recurrenceRule = reservation.getRecurringRule();
        final Date endDate = reservation.getEndDate();

        if (StringUtil.notNullOrEmpty(recurrenceRule) && buildingId != null && endDate != null) {
            // get the local date
            final Date localCurrentDate =
                    LocalDateTimeUtil.currentLocalDate(null, null, null, buildingId);
            // calculate the difference in days between now and the last occurrence date
            final int daysDifference = (int) (endDate.getTime() - localCurrentDate.getTime())
                    / com.archibus.app.reservation.dao.datasource.Constants.ONE_DAY;
            this.resourceDataSource.addRestriction(Restrictions
                .gte(this.resourceDataSource.getMainTableName(), MAX_DAYS_AHEAD, daysDifference));
        }

        // find unlimited catering resources for the location.
        final List<DataRecord> records = this.resourceDataSource
            .findAvailableUnlimitedResourceRecords(reservation, reservation.getTimePeriod(), true);
        // convert to DataSet
        return new DataSetList(records);
    }

    /**
     * Find available unique resources.
     *
     * @param reservation the reservation
     * @param recurrenceRule the recurrence rule
     * @param existingReservations the existing reservations
     * @return the list of data records
     */
    private List<DataRecord> findAvailableUniqueResources(final IReservation reservation,
            final String recurrenceRule, final List<? extends IReservation> existingReservations) {
        // get the unique resources
        List<DataRecord> uniqueResources = null;

        if (existingReservations == null) {
            if (StringUtil.notNullOrEmpty(recurrenceRule) && reservation.getReserveId() == null) {
                // get unique resources for recurrence, all natures
                uniqueResources =
                        findAvailableUniqueResourcesRecurrence(reservation, recurrenceRule);
            } else {
                // get unique resources, all natures
                uniqueResources = this.resourceDataSource
                    .findAvailableUniqueResourceRecords(reservation, reservation.getTimePeriod());
            }

        } else {
            // when editing a recurring reservation
            uniqueResources = this.resourceDataSource.findAvailableResourceRecords(reservation,
                existingReservations, ResourceType.UNIQUE);
        }
        return uniqueResources;
    }

    /**
     * Update quantity for limited resources.
     *
     * @param reservation the reservation
     * @param recurrenceRule the recurrence rule
     * @param existingReservations the existing reservations
     * @param limitedResources the limited resources
     */
    private void updateQuantityForLimitedResources(final IReservation reservation,
            final String recurrenceRule, final List<? extends IReservation> existingReservations,
            final List<DataRecord> limitedResources) {
        // update the available quantities for limited resources when the time start and end are
        // defined
        if (reservation.getStartTime() != null && reservation.getEndTime() != null) {
            // update the available quantities
            for (final DataRecord record : limitedResources) {
                // create a new time period
                final TimePeriod timePeriod = new TimePeriod(reservation.getTimePeriod());
                // when recurring reservations check for availability on all occurrence dates
                int maxReserved = 0;
                if (existingReservations == null) {
                    maxReserved =
                            getMaximumReserved(reservation, recurrenceRule, timePeriod, record);
                } else {
                    for (final IReservation existingReservation : existingReservations) {
                        timePeriod.setStartDate(existingReservation.getStartDate());
                        timePeriod.setEndDate(existingReservation.getEndDate());

                        final int reserved = this.resourceDataSource.getNumberOfReservedResources(
                            timePeriod, record.getString(RESOURCES_RESOURCE_ID),
                            existingReservation.getReserveId(), true);

                        maxReserved = Math.max(maxReserved, reserved);
                    }

                }

                final int available = record.getInt(RESOURCES_QUANTITY) - maxReserved;

                record.setValue(RESOURCES_QUANTITY, available);

            }
        }
    }

    /**
     * Gets the maximum reserved.
     *
     * @param reservation the reservation
     * @param recurrenceRule the recurrence rule
     * @param timePeriod the time period
     * @param record the record
     * @return the maximum reserved
     */
    private int getMaximumReserved(final IReservation reservation, final String recurrenceRule,
            final TimePeriod timePeriod, final DataRecord record) {

        int maxReserved = 0;
        if (StringUtil.isNullOrEmpty(recurrenceRule)) {
            maxReserved = this.resourceDataSource.getNumberOfReservedResources(timePeriod,
                record.getString(RESOURCES_RESOURCE_ID), reservation.getReserveId(), true);
        } else {
            // check for all occurrences
            final List<Date> dateList = RecurrenceService.getDateList(reservation.getStartDate(),
                reservation.getEndDate(), recurrenceRule);

            if (dateList != null) {
                for (final Date date : dateList) {
                    timePeriod.setStartDate(date);
                    timePeriod.setEndDate(date);

                    final int reserved = this.resourceDataSource.getNumberOfReservedResources(
                        timePeriod, record.getString(RESOURCES_RESOURCE_ID),
                        reservation.getReserveId(), true);

                    maxReserved = Math.max(maxReserved, reserved);
                }
            }
        }
        return maxReserved;
    }

    /**
     * Find available limited resources.
     *
     * @param reservation the reservation
     * @param recurrenceRule the recurrence rule
     * @param existingReservations the existing reservations
     * @return the list of data records
     */
    private List<DataRecord> findAvailableLimitedResources(final IReservation reservation,
            final String recurrenceRule, final List<? extends IReservation> existingReservations) {
        // get the limited resources
        List<DataRecord> limitedResources = null;

        if (existingReservations == null) {
            if (StringUtil.notNullOrEmpty(recurrenceRule) && reservation.getReserveId() == null) {
                final Recurrence recurrence = RecurrenceParser.parseRecurrence(
                    reservation.getStartDate(), reservation.getEndDate(), recurrenceRule);

                limitedResources = findAvailableLimitedResourcesRecurrence(reservation, recurrence);
            } else {
                // get limited resources, all natures
                limitedResources = this.resourceDataSource
                    .findAvailableLimitedResourceRecords(reservation, reservation.getTimePeriod());
            }
        } else {
            limitedResources = this.resourceDataSource.findAvailableResourceRecords(reservation,
                existingReservations, ResourceType.LIMITED);
        }
        return limitedResources;
    }

    /**
     * Gets the available limited resource records recurrence.
     *
     * @param reservation the reservation
     * @param recurrence the recurrence
     * @return the available limited resource records recurrence
     */
    private List<DataRecord> findAvailableLimitedResourcesRecurrence(final IReservation reservation,
            final Recurrence recurrence) {

        List<DataRecord> limitedResources = null;

        if (recurrence instanceof AbstractIntervalPattern) {
            final AbstractIntervalPattern pattern = (AbstractIntervalPattern) recurrence;
            // get limited resources
            final List<Resource> resources = this.resourceDataSource
                .findAvailableLimitedResources(reservation, reservation.getTimePeriod());

            pattern.loopThroughRepeats(new FindAvailableResourcesOccurrenceAction(reservation,
                resources, ResourceType.LIMITED, this.resourceDataSource));

            limitedResources = this.resourceDataSource.convertObjectsToRecords(resources);
        } else {
            // @translatable
            throw new ReservationException("The recurrence pattern is invalid",
                ResourceFinderServiceHelper.class, this.getAdminService());
        }
        return limitedResources;
    }

    /**
     * Find available unique resource records recurrence.
     *
     * @param reservation the reservation
     * @param recurrenceRule the recurrence rule
     * @return the list of unique resource records
     */
    private List<DataRecord> findAvailableUniqueResourcesRecurrence(final IReservation reservation,
            final String recurrenceRule) {
        final Recurrence recurrence = RecurrenceParser.parseRecurrence(reservation.getStartDate(),
            reservation.getEndDate(), recurrenceRule);

        List<DataRecord> uniqueResources = null;

        if (recurrence instanceof AbstractIntervalPattern) {
            final AbstractIntervalPattern pattern = (AbstractIntervalPattern) recurrence;
            // get unique resources
            final List<Resource> resources = this.resourceDataSource
                .findAvailableUniqueResources(reservation, reservation.getTimePeriod());

            pattern.loopThroughRepeats(new FindAvailableResourcesOccurrenceAction(reservation,
                resources, ResourceType.UNIQUE, this.resourceDataSource));

            uniqueResources = this.resourceDataSource.convertObjectsToRecords(resources);
        }

        return uniqueResources;
    }

    /**
     * Clear any restrictions on the internal data source.
     */
    public void clearRestrictions() {
        this.resourceDataSource.clearRestrictions();
    }

    /**
     * Adds the resource location restrictions.
     *
     * @param locationFilter the location filter
     * @param roomService whether to restrict to resources available for room service
     */
    public void addLocationRestrictions(final Map<String, String> locationFilter,
            final boolean roomService) {
        // if there are extra location filters beside the building
        if (locationFilter.containsKey(SITE_ID)
                && StringUtil.notNullOrEmpty(locationFilter.get(SITE_ID))) {
            this.resourceDataSource.addRestriction(Restrictions.eq(
                this.resourceDataSource.getMainTableName(), SITE_ID, locationFilter.get(SITE_ID)));
        }

        // add building restriction
        if (locationFilter.containsKey(BL_ID)
                && StringUtil.notNullOrEmpty(locationFilter.get(BL_ID))) {

            this.resourceDataSource.addParameter(
                com.archibus.app.reservation.dao.datasource.Constants.BL_ID_PARAMETER,
                locationFilter.get(BL_ID), DataSource.DATA_TYPE_TEXT);
            this.resourceDataSource.addRestriction(Restrictions
                .sql(" resources.bl_id = ${parameters['blId']} OR ( resources.bl_id is null AND "
                        + " resources.site_id is not null AND ${parameters['blId']} "
                        + " IN (select bl_id from bl where site_id = resources.site_id) ) "));
        }

        if (roomService) {
            // add room service restriction
            this.resourceDataSource.addRestriction(
                Restrictions.eq(this.resourceDataSource.getMainTableName(), "room_service", 1));
        }
    }

}
