package com.archibus.app.reservation.service.helpers;

import java.sql.Time;
import java.util.*;

import com.archibus.app.common.space.domain.*;
import com.archibus.app.reservation.dao.IResourceStandardDataSource;
import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.Recurrence;
import com.archibus.app.reservation.service.*;
import com.archibus.utility.*;

/**
 * Helper class for the Web Service to handle a combined location query.
 *
 * @author Yorik Gerlo
 * @since 22.1
 */
public class LocationQueryHandler {

    /** The space service. */
    private ISpaceService spaceService;

    /** The reservation service. */
    private IReservationService reservationService;

    /** The resource standards data source. */
    private IResourceStandardDataSource resourceStandardDataSource;

    /**
     * Set the resource standards data source.
     *
     * @param resourceStandardDataSource the resourceStandardDataSource to set
     */
    public void setResourceStandardDataSource(
            final IResourceStandardDataSource resourceStandardDataSource) {
        this.resourceStandardDataSource = resourceStandardDataSource;
    }

    /**
     * Gets the fixed resource standards.
     *
     * @return the fixed resource standards
     */
    public final List<ResourceStandard> getFixedResourceStandards() {
        return this.resourceStandardDataSource.getFixedResourceStandards();
    }

    /**
     * Sets the space service.
     *
     * @param spaceService the new space service
     */
    public final void setSpaceService(final ISpaceService spaceService) {
        this.spaceService = spaceService;
    }

    /**
     * Get the space service.
     *
     * @return the space service
     */
    public ISpaceService getSpaceService() {
        return this.spaceService;
    }

    /**
     * Set the reservation service for handling room queries.
     *
     * @param reservationService the new reservation service
     */
    public final void setReservationService(final IReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * Handle a location query.
     *
     * @param query details which levels of the location hierarchy to return and the id to select on
     *            each level if possible
     * @return results of the location query
     * @throws ExceptionBase ExceptionBase
     */
    public LocationQueryResult handle(final LocationQuery query) throws ExceptionBase {
        final LocationQueryResult result = new LocationQueryResult();

        if (query.includeArrangements()) {
            result.setArrangements(this.spaceService.getArrangeTypes());
        }

        if (query.includeAttributes()) {
            result.setAttributes(this.getFixedResourceStandards());
        }

        handleLocationHierarchy(query, result);

        return result;
    }

    /**
     * Add all requested levels of the location hierarchy.
     *
     * @param query the location query
     * @param result the location query result
     */
    private void handleLocationHierarchy(final LocationQuery query,
            final LocationQueryResult result) {
        if (query.includeCountries()) {
            includeCountries(query, result);
        } else {
            result.setCountryId(query.getCountryId());
        }

        if (query.includeStates()) {
            includeStates(query, result);
        } else {
            result.setStateId(query.getStateId());
        }

        if (query.includeCities()) {
            includeCities(query, result);
        } else {
            result.setCityId(query.getCityId());
        }

        if (query.includeSites()) {
            includeSites(query, result);
        } else {
            result.setSiteId(query.getSiteId());
        }

        if (query.includeBuildings()) {
            includeBuildings(query, result);
        } else {
            result.setBuildingId(query.getBuildingId());
        }

        if (query.includeFloors()) {
            includeFloors(query, result);
        }
        // No need to set the floor id if not included since it's the lowest level.
    }

    /**
     * Find available rooms.
     *
     * @param reservation the reservation
     * @param capacity the required capacity
     * @param allDayEvent whether to find rooms available for all-day events
     * @param recurrence recurrence pattern for recurring reservations
     * @param roomAttributes list of resource standard identifiers required as fixed resources in
     *            the rooms
     * @return list of available room arrangements
     */
    public List<RoomArrangement> findAvailableRooms(final RoomReservation reservation,
            final Integer capacity, final boolean allDayEvent, final Recurrence recurrence,
            final List<ResourceStandard> roomAttributes) {

        final List<String> resourceStandards = ResourceStandard.toPrimaryKeyList(roomAttributes);
        final List<RoomArrangement> roomArrangements;
        if (recurrence == null) {
            roomArrangements = this.reservationService.findAvailableRooms(reservation, capacity,
                false, resourceStandards, allDayEvent, Constants.TIMEZONE_UTC);
        } else {
            roomArrangements =
                    this.reservationService.findAvailableRoomsRecurrence(reservation, capacity,
                        false, resourceStandards, allDayEvent, recurrence, Constants.TIMEZONE_UTC);
        }
        this.spaceService.setLocationString(roomArrangements);
        return roomArrangements;
    }

    /**
     * Handle a query for room availability for a conference call.
     *
     * @param reservations room reservations representing the rooms to be booked
     * @param allDayEvent whether to check availability for an all-day event
     * @param recurrence recurrence pattern for a recurring meeting (may be null for non-recurring)
     * @return result of the availability check, including available room arrangements and (for
     *         all-day events) the common start and end time
     */
    public ConferenceRoomsAvailability getConferenceRoomsAvailability(
            final List<RoomReservation> reservations, final boolean allDayEvent,
            final Recurrence recurrence) {
        final ConferenceRoomsAvailability result = ConferenceRoomsAvailability.newInstance();

        for (final RoomReservation reservation : reservations) {
            final RoomAllocation allocation = reservation.getRoomAllocations().get(0);
            Integer capacity = null;
            // Only use the number of attendees as a filter if non-zero.
            if (allocation.getAttendeesInRoom() > 0) {
                capacity = allocation.getAttendeesInRoom();
            }

            // should yield 0 or 1 result since we're looking for a specific arrangement
            result.getRoomArrangements().addAll(
                this.findAvailableRooms(reservation, capacity, allDayEvent, recurrence, null));
        }

        if (allDayEvent) {
            for (final RoomArrangement room : result.getRoomArrangements()) {
                final Time roomStartTime = addBlock(room.getDayStart(), room.getPreBlock(), 1);
                if (result.getStartTime() == null || result.getStartTime().before(roomStartTime)) {
                    result.setStartTime(roomStartTime);
                }

                final Time roomEndTime = addBlock(room.getDayEnd(), room.getPostBlock(), -1);
                if (result.getEndTime() == null || result.getEndTime().after(roomEndTime)) {
                    result.setEndTime(roomEndTime);
                }
            }
        }

        return result;
    }

    /**
     * Add a time block to a given time.
     *
     * @param time the time
     * @param block the block to add
     * @param signum 1 to add, -1 to subtract the block
     * @return the time including the block
     */
    private Time addBlock(final Time time, final Integer block, final int signum) {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(time);
        if (block != null) {
            calendar.add(Calendar.MINUTE, block * signum);
        }
        return TimePeriod.clearDate(calendar.getTime());
    }

    /**
     * Retrieve the available floors.
     *
     * @param query details which levels of the location hierarchy to return and the id to select on
     *            each level if possible
     * @param result container for results of the location query, including the results of
     *            higher-level queries
     */
    private void includeFloors(final LocationQuery query, final LocationQueryResult result) {
        final Floor floorFilter = new Floor();
        floorFilter.setBuildingId(result.getBuildingId());
        result.setFloors(this.spaceService.getFloors(floorFilter));

        if (StringUtil.notNullOrEmpty(query.getFloorId())) {
            // find the floor id in the result
            for (final Floor floor : result.getFloors()) {
                if (query.getFloorId().equals(floor.getFloorId())) {
                    result.setFloorId(floor.getFloorId());
                    break;
                }
            }
        }
        // don't choose any floor if the requested floor is not found
    }

    /**
     * Retrieve the available buildings.
     *
     * @param query details which levels of the location hierarchy to return and the id to select on
     *            each level if possible
     * @param result container for results of the location query, including the results of
     *            higher-level queries
     */
    private void includeBuildings(final LocationQuery query, final LocationQueryResult result) {
        final Building buildingFilter = new Building();
        buildingFilter.setCtryId(result.getCountryId());
        buildingFilter.setStateId(result.getStateId());
        buildingFilter.setCityId(result.getCityId());
        buildingFilter.setSiteId(result.getSiteId());
        result.setBuildings(this.spaceService.getBuildings(buildingFilter));

        if (StringUtil.notNullOrEmpty(query.getBuildingId())) {
            // find the building id in the result
            for (final Building building : result.getBuildings()) {
                if (query.getBuildingId().equals(building.getBuildingId())) {
                    result.setBuildingId(building.getBuildingId());
                    break;
                }
            }
        }
        if (result.getBuildingId() == null && !result.getBuildings().isEmpty()) {
            result.setBuildingId(result.getBuildings().get(0).getBuildingId());
        }
    }

    /**
     * Retrieve the available sites.
     *
     * @param query details which levels of the location hierarchy to return and the id to select on
     *            each level if possible
     * @param result container for results of the location query, including the results of
     *            higher-level queries
     */
    private void includeSites(final LocationQuery query, final LocationQueryResult result) {
        final Site siteFilter = new Site();
        siteFilter.setCtryId(result.getCountryId());
        siteFilter.setStateId(result.getStateId());
        siteFilter.setCityId(result.getCityId());
        result.setSites(this.spaceService.getSites(siteFilter));

        if (StringUtil.notNullOrEmpty(query.getSiteId())) {
            // find the site id in the result
            for (final Site site : result.getSites()) {
                if (query.getSiteId().equals(site.getSiteId())) {
                    result.setSiteId(site.getSiteId());
                    break;
                }
            }
        }
        if (result.getSiteId() == null && !result.getSites().isEmpty()) {
            result.setSiteId(result.getSites().get(0).getSiteId());
        }
    }

    /**
     * Retrieve the available cities.
     *
     * @param query details which levels of the location hierarchy to return and the id to select on
     *            each level if possible
     * @param result container for results of the location query, including the results of
     *            higher-level queries
     */
    private void includeCities(final LocationQuery query, final LocationQueryResult result) {
        final City cityFilter = new City();
        cityFilter.setCountryId(result.getCountryId());
        cityFilter.setStateId(result.getStateId());
        result.setCities(this.spaceService.getCities(cityFilter));

        if (StringUtil.notNullOrEmpty(query.getCityId())) {
            // find the city id in the result
            for (final City city : result.getCities()) {
                if (query.getCityId().equals(city.getCityId())) {
                    result.setCityId(city.getCityId());
                    break;
                }
            }
        }
        if (result.getCityId() == null && !result.getCities().isEmpty()) {
            result.setCityId(result.getCities().get(0).getCityId());
        }
    }

    /**
     * Retrieve the available states.
     *
     * @param query details which levels of the location hierarchy to return and the id to select on
     *            each level if possible
     * @param result container for results of the location query, including the results of
     *            higher-level queries
     */
    private void includeStates(final LocationQuery query, final LocationQueryResult result) {
        final State stateFilter = new State();
        stateFilter.setCountryId(result.getCountryId());
        result.setStates(this.spaceService.getStates(stateFilter));

        if (StringUtil.notNullOrEmpty(query.getStateId())) {
            // find the state id in the result
            for (final State state : result.getStates()) {
                if (query.getStateId().equals(state.getStateId())) {
                    result.setStateId(state.getStateId());
                    break;
                }
            }
        }
        if (result.getStateId() == null && !result.getStates().isEmpty()) {
            result.setStateId(result.getStates().get(0).getStateId());
        }
    }

    /**
     * Retrieve the available countries.
     *
     * @param query details which levels of the location hierarchy to return and the id to select on
     *            each level if possible
     * @param result container for results of the location query, including the results of
     *            higher-level queries
     */
    private void includeCountries(final LocationQuery query, final LocationQueryResult result) {
        final Country countryFilter = new Country();
        result.setCountries(this.spaceService.getCountries(countryFilter));

        if (StringUtil.notNullOrEmpty(query.getCountryId())) {
            // find the country id in the result
            for (final Country country : result.getCountries()) {
                if (query.getCountryId().equals(country.getCountryId())) {
                    result.setCountryId(country.getCountryId());
                    break;
                }
            }
        }
        if (result.getCountryId() == null && !result.getCountries().isEmpty()) {
            result.setCountryId(result.getCountries().get(0).getCountryId());
        }
    }

}
