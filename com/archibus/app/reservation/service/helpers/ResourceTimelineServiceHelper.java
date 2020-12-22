package com.archibus.app.reservation.service.helpers;

import java.sql.Time;
import java.util.*;

import org.json.*;

import com.archibus.app.common.util.SchemaUtils;
import com.archibus.app.reservation.dao.datasource.*;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.service.RecurrenceService;
import com.archibus.app.reservation.util.*;
import com.archibus.datasource.data.DataRecord;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.utility.StringUtil;

/**
 * The Class ResourceTimelineServiceHelper.
 *
 * This class should be configured as a Spring bean.
 */
public class ResourceTimelineServiceHelper extends AdminServiceContainer {

    /** The Constant RESOURCE_ID. */
    private static final String RESOURCE_ID = "resource_id";

    /** The Constant RESOURCES_RESOURCE_ID. */
    private static final String RESOURCES_RESOURCE_ID = "resources.resource_id";

    /** The Constant RESOURCES_QUANTITY. */
    private static final String RESOURCES_QUANTITY = "resources.quantity";

    /** The Constant for table reserve_rs. */
    private static final String RESERVE_RS_TABLE = "reserve_rs";

    /** The Constant EVENTS. */
    private static final String EVENTS = "events";

    /** The resource data source. */
    private ResourceDataSource resourceDataSource;

    /** The resource allocation data source. */
    private ResourceAllocationDataSource resourceAllocationDataSource;

    /**
     * Adds the resource events.
     *
     * @param timePeriod the time period
     * @param reservationId the reservation id
     * @param timeline the timeline
     * @param rowIndex the row index
     * @param recurrenceRule the recurrence rule
     * @param existingReservations the existing reservations
     * @param resourceRecord the resource record
     * @return resource record retrieved from the database, with requested quantity filled in
     */
    public DataRecord addResourceEvents(final TimePeriod timePeriod, final Integer reservationId,
            final JSONObject timeline, final int rowIndex, final String recurrenceRule,
            final List<RoomReservation> existingReservations, final DataRecord resourceRecord) {

        final JSONArray events = timeline.getJSONArray(EVENTS);

        final int requestedQuantity = resourceRecord.getInt(RESOURCES_QUANTITY);
        final String resourceId = resourceRecord.getString(RESOURCES_RESOURCE_ID);

        this.resourceAllocationDataSource.setApplyVpaRestrictions(false);
        this.resourceDataSource.setApplyVpaRestrictions(false);

        this.resourceDataSource.clearRestrictions();
        this.resourceDataSource
            .addRestriction(Restrictions.eq("resources", RESOURCE_ID, resourceId));

        final DataRecord fullResourceRecord = this.resourceDataSource.getRecord();
        final Resource resource = this.resourceDataSource.convertRecordToObject(fullResourceRecord);

        // get resource allocations for the selected reservation
        final List<ResourceAllocation> resourceAllocations =
                getAllResourceAllocations(timePeriod.getStartDate(), timePeriod.getEndDate(),
                    recurrenceRule, existingReservations, resource, reservationId);

        if (resource.getQuantity() > requestedQuantity) {
            // Loop through all allocations and build a set of times.
            final SortedSet<Time> times = new TreeSet<Time>();
            /*
             * Also keep a map of end dates and start dates against the resource allocations, to
             * show details on the timeline. If we have multiple allocation with the same start or
             * end, only one is used for displaying details on the time line. The time period of
             * this allocation will be modified to match the division of time periods computed here.
             */
            final Map<Time, ResourceAllocation> allocationsByStart =
                    new HashMap<Time, ResourceAllocation>();
            final Map<Time, ResourceAllocation> allocationsByEnd =
                    new HashMap<Time, ResourceAllocation>();
            for (final ResourceAllocation resourceAllocation : resourceAllocations) {
                times.add(resourceAllocation.getStartTime());
                times.add(resourceAllocation.getEndTime());
                allocationsByStart.put(resourceAllocation.getStartTime(), resourceAllocation);
                allocationsByEnd.put(resourceAllocation.getEndTime(), resourceAllocation);
            }
            final List<TimePeriod> periods = buildTimePeriods(timePeriod.getStartDate(), times);
            // Get the number of reserved resources for each time period.
            for (final TimePeriod period : periods) {
                final int reservedCount = this.getMaximumReserved(timePeriod.getStartDate(),
                    timePeriod.getEndDate(), recurrenceRule, existingReservations, resourceId,
                    reservationId, period);

                if (fullResourceRecord.getInt(RESOURCES_QUANTITY)
                        - reservedCount < requestedQuantity) {
                    // create an event for the resource reservation
                    ResourceAllocation allocation = allocationsByStart.get(period.getStartTime());

                    allocation = (allocation == null) ? allocationsByEnd.get(period.getEndTime())
                            : allocation;

                    allocation.setTimePeriod(period);
                    final JSONObject event = TimelineHelper.createResourceReservationEvent(timeline,
                        resourceRecord, allocation, rowIndex);
                    events.put(event);
                }
            }
            fullResourceRecord.setValue(RESOURCES_QUANTITY, requestedQuantity);
        } else if (resource.getQuantity() == requestedQuantity) {
            // loop through all resource allocations and create an event for each
            for (final ResourceAllocation resourceAllocation : resourceAllocations) {
                // create an event for the resource reservation
                final JSONObject event = TimelineHelper.createResourceReservationEvent(timeline,
                    resourceRecord, resourceAllocation, rowIndex);
                events.put(event);
            }
        } else {
            // @translatable
            throw new ReservationException(
                "The requested quantity exceeds the maximum available for {0}.",
                ResourceTimelineServiceHelper.class, this.getAdminService(), resourceId);
        }

        return fullResourceRecord;
    }

    /**
     * Sets the resource data source.
     *
     * @param resourceDataSource the new resource data source
     */
    public void setResourceDataSource(final ResourceDataSource resourceDataSource) {
        this.resourceDataSource = resourceDataSource;
    }

    /**
     * Sets the resource allocation data source used to find existing resource allocations.
     *
     * @param resourceAllocationDataSource the new resource allocation data source
     */
    public void setResourceAllocationDataSource(
            final ResourceAllocationDataSource resourceAllocationDataSource) {
        this.resourceAllocationDataSource = resourceAllocationDataSource;
    }

    /**
     * Build a list of time periods from a sequential list of times, creating a time period for each
     * interval.
     *
     * @param date the date to set in the time periods
     * @param times the list of times to build periods for
     * @return list of time periods
     */
    private List<TimePeriod> buildTimePeriods(final Date date, final SortedSet<Time> times) {
        // Convert the set of times to a list of time periods.
        final List<TimePeriod> periods = new ArrayList<TimePeriod>();
        Time time1 = null;
        Time time2 = null;
        for (final Time time : times) {
            time2 = time;
            if (time1 != null) {
                periods.add(new TimePeriod(date, date, time1, time2));
            }
            time1 = time;
        }
        return periods;
    }

    /**
     * Gets the all resource allocations.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param recurrenceRule the recurrence rule
     * @param existingReservations the existing reservations
     * @param resource the resource
     * @param reservationId the reservation id
     * @return the all resource allocations
     */
    private List<ResourceAllocation> getAllResourceAllocations(final Date startDate,
            final Date endDate, final String recurrenceRule,
            final List<RoomReservation> existingReservations, final Resource resource,
            final Integer reservationId) {

        final List<ResourceAllocation> resourceAllocations = new ArrayList<ResourceAllocation>();

        // loop through all dates for getting all resource allocations when recurring

        if (existingReservations == null) {
            if (StringUtil.isNullOrEmpty(recurrenceRule)) {
                // check only the first date, with the given reservation id
                resourceAllocations
                    .addAll(getResourceAllocations(startDate, reservationId, resource));
            } else {
                // check all dates in the recurrence for a new recurring reservation
                final List<Date> dateList =
                        RecurrenceService.getDateList(startDate, endDate, recurrenceRule);
                for (final Date date : dateList) {
                    resourceAllocations.addAll(getResourceAllocations(date, null, resource));
                }
            }
        } else {
            for (final RoomReservation existingReservation : existingReservations) {
                // get the resource allocations for this occurrence
                final List<ResourceAllocation> allocations =
                        getResourceAllocations(existingReservation.getStartDate(),
                            existingReservation.getReserveId(), resource);
                // add them to the list
                resourceAllocations.addAll(allocations);
            }
        }

        return resourceAllocations;
    }

    /**
     * Gets the maximum reserved count of a resource for a recurring reservation.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param recurrenceRule the recurrence rule
     * @param existingReservations the existing reservations
     * @param resourceId the resource id
     * @param reservationId the reservation id
     * @param timePeriod the time period to check
     * @return the maximum number of reserved resources
     */
    private int getMaximumReserved(final Date startDate, final Date endDate,
            final String recurrenceRule, final List<RoomReservation> existingReservations,
            final String resourceId, final Integer reservationId, final TimePeriod timePeriod) {

        int maxReserved = 0;

        if (existingReservations == null) {
            if (StringUtil.isNullOrEmpty(recurrenceRule)) {
                // check only the first date, with the given reservation id
                maxReserved = this.resourceDataSource.getNumberOfReservedResources(timePeriod,
                    resourceId, reservationId, false);
            } else {
                // check all dates in the recurrence
                final List<Date> dateList =
                        RecurrenceService.getDateList(startDate, endDate, recurrenceRule);
                for (final Date date : dateList) {
                    timePeriod.setStartDate(date);
                    timePeriod.setEndDate(date);
                    // get the actual reserved count for this date, not editing an existing
                    // reservation
                    final int reservedCount = this.resourceDataSource
                        .getNumberOfReservedResources(timePeriod, resourceId, null, false);
                    maxReserved = Math.max(maxReserved, reservedCount);
                }
            }
        } else {
            for (final RoomReservation existingReservation : existingReservations) {
                timePeriod.setStartDate(existingReservation.getStartDate());
                timePeriod.setEndDate(existingReservation.getStartDate());
                // get the maximum reserved count for this occurrence
                final int reservedCount = this.resourceDataSource.getNumberOfReservedResources(
                    timePeriod, resourceId, existingReservation.getReserveId(), false);
                // compare against what we have so far
                maxReserved = Math.max(maxReserved, reservedCount);
            }
        }

        return maxReserved;
    }

    /**
     * Gets the resource allocations.
     *
     * @param startDate the start date
     * @param reservationId the reservation id
     * @param resource the resource
     * @return the resource allocations
     */
    private List<ResourceAllocation> getResourceAllocations(final Date startDate,
            final Integer reservationId, final Resource resource) {
        // search for resource allocations for this day
        this.resourceAllocationDataSource.clearRestrictions();
        this.resourceAllocationDataSource.addRestriction(Restrictions
            .sql("(reserve_rs.status = 'Awaiting App.' or reserve_rs.status = 'Confirmed')"));

        this.resourceAllocationDataSource.addRestriction(
            Restrictions.eq(RESERVE_RS_TABLE, RESOURCE_ID, resource.getResourceId()));
        if (SchemaUtils.fieldExistsInSchema(RESERVE_RS_TABLE, Constants.DATE_END_FIELD_NAME)) {
            this.resourceAllocationDataSource.addRestriction(
                Restrictions.lte(RESERVE_RS_TABLE, Constants.DATE_START_FIELD_NAME, startDate));
            this.resourceAllocationDataSource.addRestriction(
                Restrictions.gte(RESERVE_RS_TABLE, Constants.DATE_END_FIELD_NAME, startDate));
        } else {
            this.resourceAllocationDataSource.addRestriction(
                Restrictions.eq(RESERVE_RS_TABLE, Constants.DATE_START_FIELD_NAME, startDate));
        }

        // don't include the active reservation
        if (reservationId != null && reservationId > 0) {
            this.resourceAllocationDataSource
                .addRestriction(Restrictions.ne(RESERVE_RS_TABLE, "res_id", reservationId));
        }

        // find all resource allocations except the selected one
        final List<ResourceAllocation> allocations = this.resourceAllocationDataSource.findAll();
        // change start and end times for allocations spanning multiple days
        final Date date = TimePeriod.clearTime(startDate);
        for (final ResourceAllocation alloc : allocations) {
            if (date.after(alloc.getStartDate())) {
                alloc.setStartTime(resource.getDayStart());
            }
            if (alloc.getEndDate() != null && date.before(alloc.getEndDate())) {
                alloc.setEndTime(resource.getDayEnd());
            }
        }
        return allocations;
    }

}
