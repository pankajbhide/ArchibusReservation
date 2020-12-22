package com.archibus.app.reservation.service;

import java.util.*;

import com.archibus.app.common.space.domain.*;
import com.archibus.app.reservation.domain.*;

/**
 * Interface for space service.
 *
 * @author Bart Vanderschoot
 * @since 20.1
 *
 */
public interface ISpaceService {

    /**
     * Get the countries that have reservable rooms.
     *
     * @param filter filtering object that can be used to look for a specific country
     * @return the countries
     */
    List<Country> getCountries(Country filter);

    /**
     * Get the states that have reservable rooms.
     *
     * @param filter filtering object that can be used to look for specific states
     * @return the states
     */
    List<State> getStates(State filter);

    /**
     * Get the cities that have reservable rooms.
     *
     * @param filter filtering object that can be used to look for specific cities
     * @return the cities
     */
    List<City> getCities(City filter);

    /**
     * Gets the sites that have reservable rooms.
     *
     * @param filter filtering object that can be used to look for specific sites
     * @return the sites
     */
    List<Site> getSites(Site filter);

    /**
     * Gets the buildings that match the given filter.
     *
     * @param filter restriction represented in a building object
     * @return the buildings
     */
    List<Building> getBuildings(final Building filter);

    /**
     * Get details on the building with the given identifier.
     *
     * @param blId identifier of the building
     * @return building details
     */
    Building getBuildingDetails(final String blId);

    /**
     * Gets the floors of a building.
     *
     * @param filter restriction represented in a floor object
     * @return the floors
     */
    List<Floor> getFloors(final Floor filter);

    /**
     * get room details.
     *
     * @param blId the building id
     * @param flId the floor id
     * @param rmId the room id
     *
     * @return the room
     */
    Room getRoomDetails(final String blId, final String flId, final String rmId);

    /**
     * Gets the arrange types.
     *
     * @return the arrange types
     */
    List<ArrangeType> getArrangeTypes();

    /**
     * Get the location string for a room reservation. Returns a custom string for conflicted
     * reservations and for conference call reservations.
     *
     * @param reservation the reservation
     * @return the location string
     */
    String getLocationString(final RoomReservation reservation);

    /**
     * Get the location for a room arrangement.
     *
     * @param roomArrangement the arrangement
     * @return the location string
     */
    String getLocationString(final RoomArrangement roomArrangement);

    /**
     * Get the location data model for a room reservation, including site and building name and room
     * details.
     *
     * @param reservation the room reservation to get the data model for
     * @return the data model
     */
    Map<String, Object> getLocationDataModel(final RoomReservation reservation);

    /**
     * Set the location string in the given room arrangements.
     *
     * @param roomArrangements the room arrangements
     */
    void setLocationString(final List<RoomArrangement> roomArrangements);
}
