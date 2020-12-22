package com.archibus.app.reservation.service;

import java.util.Map;

import com.archibus.app.reservation.dao.datasource.*;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.service.helpers.ResourceFinderServiceHelper;
import com.archibus.datasource.data.*;

/**
 * Resource Finder Service for workflow rules in the new reservation module.
 * <p>
 * This class provides workflow rules for the room reservation and resource reservation creation
 * views for finding available Equipment & Services and Catering.
 * <p>
 * <p>
 * The class will be defined as a Spring bean and will reference other Spring beans. <br/>
 * All Spring beans are defined as prototype.
 * </p>
 *
 * @author Bart Vanderschoot
 * @since 21.2
 */
public class ResourceFinderService {

    /** The Constant BL_ID. */
    private static final String BL_ID = "bl_id";

    /** The resource reservation data source. */
    private ResourceReservationDataSource resourceReservationDataSource;

    /** The room reservation data source. */
    private RoomReservationDataSource roomReservationDataSource;

    /** The resource finder service helper. */
    private ResourceFinderServiceHelper resourceFinderServiceHelper;

    /**
     * Find available catering resources for room reservation.
     *
     * Catering resources are always unlimited, only depends on the location site or building.
     *
     * @param locationFilter the location filter for site_id, ..
     * @param reservation the reservation
     * @param roomAllocation the room allocation
     *
     * @return list of resources (dataSet to be displayed in grid)
     */
    public final DataSetList findAvailableCateringResourcesForRoom(
            final Map<String, String> locationFilter, final DataRecord reservation,
            final DataRecord roomAllocation) {
        final RoomReservation roomReservation =
                this.roomReservationDataSource.convertRecordToObject(reservation, roomAllocation,
                    null);

        this.resourceFinderServiceHelper.clearRestrictions();
        this.resourceFinderServiceHelper.addLocationRestrictions(locationFilter, false);

        return this.resourceFinderServiceHelper.findAvailableCateringResources(
            locationFilter.get(BL_ID), roomReservation);
    }

    /**
     * Find available equipment, furniture and services for room reservation.
     *
     * These resources are limited or unique. Depends on the location site or building and the
     * reservation date/time and the recurrence pattern.
     *
     * @param locationFilter the location filter for site_id, ..
     * @param reservation the reservation
     * @param roomAllocation the room allocation
     *
     * @return list of resources (dataSet to be displayed in grid)
     */
    public final DataSetList findAvailableReservableResourcesForRoom(
            final Map<String, String> locationFilter, final DataRecord reservation,
            final DataRecord roomAllocation) {

        final RoomReservation roomReservation =
                this.roomReservationDataSource.convertRecordToObject(reservation, roomAllocation,
                    null);

        this.resourceFinderServiceHelper.clearRestrictions();
        this.resourceFinderServiceHelper.addLocationRestrictions(locationFilter, false);

        return this.resourceFinderServiceHelper.findAvailableReservableResources(
            locationFilter.get(BL_ID), roomReservation, this.roomReservationDataSource);
    }

    /**
     * Find available catering resources for a resource only reservation.
     *
     * @param locationFilter the location filter
     * @param reservation the reservation
     * @return the data set
     */
    public final DataSetList findAvailableCateringResources(
            final Map<String, String> locationFilter, final DataRecord reservation) {

        final ResourceReservation resourceReservation =
                this.resourceReservationDataSource.convertRecordToObject(reservation);

        final String buildingId = locationFilter.get(BL_ID);
        resourceReservation.setBackupBuildingId(buildingId);

        this.resourceFinderServiceHelper.clearRestrictions();
        this.resourceFinderServiceHelper.addLocationRestrictions(locationFilter, true);

        return this.resourceFinderServiceHelper.findAvailableCateringResources(buildingId,
            resourceReservation);
    }

    /**
     * Find available equipment, furniture and services for resource only reservation.
     *
     * These resources are limited or unique. Depends on the location site or building and the
     * reservation date/time and the recurrence pattern.
     *
     * @param locationFilter the location filter for site_id, ..
     * @param reservation the reservation
     * @return list of resources (dataSet to be displayed in grid)
     */
    public final DataSetList findAvailableReservableResources(
            final Map<String, String> locationFilter, final DataRecord reservation) {

        final ResourceReservation resourceReservation =
                this.resourceReservationDataSource.convertRecordToObject(reservation);

        final String buildingId = locationFilter.get(BL_ID);
        resourceReservation.setBackupBuildingId(buildingId);

        this.resourceFinderServiceHelper.clearRestrictions();
        this.resourceFinderServiceHelper.addLocationRestrictions(locationFilter, true);

        return this.resourceFinderServiceHelper.findAvailableReservableResources(buildingId,
            resourceReservation, this.resourceReservationDataSource);
    }

    /**
     * Sets the resource reservation data source.
     *
     * @param resourceReservationDataSource the new resource reservation data source
     */
    public final void setResourceReservationDataSource(
            final ResourceReservationDataSource resourceReservationDataSource) {
        this.resourceReservationDataSource = resourceReservationDataSource;
    }

    /**
     * Sets the room reservation data source.
     *
     * @param roomReservationDataSource the new room reservation data source
     */
    public final void setRoomReservationDataSource(
            final RoomReservationDataSource roomReservationDataSource) {
        this.roomReservationDataSource = roomReservationDataSource;
    }

    /**
     * Sets the resource finder service helper.
     *
     * @param resourceFinderServiceHelper the resource finder service helper
     */
    public final void setResourceFinderServiceHelper(
            final ResourceFinderServiceHelper resourceFinderServiceHelper) {
        this.resourceFinderServiceHelper = resourceFinderServiceHelper;
    }

}
