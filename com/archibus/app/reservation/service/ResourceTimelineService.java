package com.archibus.app.reservation.service;

import java.util.*;

import org.json.*;

import com.archibus.app.reservation.dao.datasource.RoomReservationDataSource;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.service.helpers.ResourceTimelineServiceHelper;
import com.archibus.app.reservation.util.TimelineHelper;
import com.archibus.datasource.data.*;

/**
 * The Class ResourceTimelineService.
 */
public class ResourceTimelineService {

    /** The room reservation data source. Also used for resource-only reservations. */
    private RoomReservationDataSource roomReservationDataSource;

    /** The resource timeline service helper. */
    private ResourceTimelineServiceHelper resourceTimelineServiceHelper;

    /**
     * Load the resource timeline for equipment and services.
     *
     * These resources are always unique or limited and can be reserved with the room reservation or
     * resource reservation.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param searchFilter the search filter
     * @param resourceList list of selected resources
     * @param reservationId reservation id (when editing)
     * @return json timeline object with resources and events
     */
    public JSONObject loadResourceTimeLine(final Date startDate, final Date endDate,
            final Map<String, String> searchFilter, final DataSetList resourceList,
            final Integer reservationId) {

        final JSONObject timeline = TimelineHelper.createTimeline();

        final JSONArray resources = new JSONArray();
        final JSONArray events = new JSONArray();
        timeline.put("events", events);
        timeline.put("resources", resources);

        int rowIndex = 0;

        final String recurrenceRule = searchFilter.get("recurrence_rule");

        final RoomReservation roomReservation = this.roomReservationDataSource.get(reservationId);

        List<RoomReservation> existingReservations = null;

        if (roomReservation != null) {
            existingReservations =
                    this.roomReservationDataSource.getByParentId(roomReservation.getParentId(),
                        startDate, endDate, false);
        }

        // loop through all selected resources
        for (final DataRecord cachedRecord : resourceList.getRecords()) {
            // 1. Add the events for the resource and retrieve the resource record from db.
            final DataRecord resourceRecord =
                    this.resourceTimelineServiceHelper.addResourceEvents(new TimePeriod(startDate,
                        endDate, null, null), reservationId, timeline, rowIndex, recurrenceRule,
                        existingReservations, cachedRecord);

            // 2. Create a resource object for the timeline using the record retrieved from the db,
            // but which still has the requested quantity in resources.quantity.
            final JSONObject reservableResource =
                    TimelineHelper.createReservableResource(timeline, resourceRecord, rowIndex);
            resources.put(reservableResource);
            rowIndex++;
        }
        return timeline;
    }

    /**
     * Sets the room reservation data source for checking existing resource reservations.
     *
     * @param roomReservationDataSource the new room reservation data source
     */
    public void setRoomReservationDataSource(
            final RoomReservationDataSource roomReservationDataSource) {
        this.roomReservationDataSource = roomReservationDataSource;
    }

    /**
     * Sets the resource timeline service helper.
     *
     * @param resourceTimelineServiceHelper the new resource timeline service helper
     */
    public void setResourceTimelineServiceHelper(
            final ResourceTimelineServiceHelper resourceTimelineServiceHelper) {
        this.resourceTimelineServiceHelper = resourceTimelineServiceHelper;
    }

}
