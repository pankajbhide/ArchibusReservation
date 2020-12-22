package com.archibus.app.reservation.service;

import java.util.List;

import javax.jws.WebMethod;

import com.archibus.app.common.space.domain.*;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.Recurrence;
import com.archibus.utility.ExceptionBase;

/**
 * The Interface ReservationRemoteService.
 *
 * @author Bart Vanderschoot
 */
public interface ReservationRemoteServiceBase {

    /**
     * Find available rooms.
     *
     * @param reservation the reservation
     * @param capacity the capacity
     * @param allDayEvent true for all day events, false for regular reservations
     * @param roomAttributes list of required room attributes
     *
     * @return the list
     *
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "findAvailableRooms")
    List<RoomArrangement> findAvailableRooms(RoomReservation reservation, Integer capacity,
            boolean allDayEvent, List<ResourceStandard> roomAttributes) throws ExceptionBase;

    /**
     * Find available rooms with recurrence.
     *
     * @param reservation the reservation
     * @param capacity the capacity
     * @param allDayEvent true for all day events, false for regular reservations
     * @param recurrence the recurrence pattern
     * @param roomAttributes list of required room attributes
     *
     * @return the list
     *
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "findAvailableRoomsRecurrence")
    List<RoomArrangement> findAvailableRoomsRecurrence(RoomReservation reservation,
            Integer capacity, boolean allDayEvent, Recurrence recurrence,
            List<ResourceStandard> roomAttributes) throws ExceptionBase;

    /**
     * Find locations from countries to room floors. Note the location filters specified in query
     * are applied if possible. If the specified id is not found then the first available id is used
     * instead.
     *
     * @param query details which levels of the location hierarchy to return and the id to select on
     *            each level if possible
     * @return results of the location query
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "findLocations")
    LocationQueryResult findLocations(LocationQuery query) throws ExceptionBase;

    /**
     * Get the countries that have reservable rooms.
     *
     * @param filter contains restrictions for the countries to return
     * @return the countries
     *
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "findCountries")
    List<Country> findCountries(Country filter) throws ExceptionBase;

    /**
     * Get the states that have reservable rooms.
     *
     * @param filter contains restrictions for the states to return
     * @return the states
     *
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "findStates")
    List<State> findStates(State filter) throws ExceptionBase;

    /**
     * Get the cities that have reservable rooms.
     *
     * @param filter contains restrictions for the cities to return
     * @return the cities
     *
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "findCities")
    List<City> findCities(City filter) throws ExceptionBase;

    /**
     * Get the sites that have reservable rooms.
     *
     * @param filter contains restrictions for the sites to return
     * @return the sites
     *
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "findSites")
    List<Site> findSites(Site filter) throws ExceptionBase;

    /**
     * Get the user's location and validate the given email address for creating reservations. An
     * employee with the given email address should exist.
     *
     * @param email user's email address
     * @return user's location, or null if no location is defined
     *
     * @throws ExceptionBase when the given email is invalid
     */
    @WebMethod(action = "getUserLocation")
    UserLocation getUserLocation(final String email) throws ExceptionBase;

    /**
     * Gets the arrange types.
     *
     * @return the arrange types
     *
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "getArrangeTypes")
    List<ArrangeType> getArrangeTypes() throws ExceptionBase;

    /**
     * Gets the available room attributes.
     *
     * @return the available room attributes
     *
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "getRoomAttributes")
    List<ResourceStandard> getRoomAttributes() throws ExceptionBase;

    /**
     * Gets the buildings.
     *
     * @param filter contains restrictions for the buildings to return
     * @return the buildings
     *
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "findBuildings")
    List<Building> findBuildings(Building filter) throws ExceptionBase;

    /**
     * Get the floors that have reservable rooms.
     *
     * @param filter contains restrictions for the floors to return
     * @return the floors
     *
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "getFloors")
    List<Floor> getFloors(Floor filter) throws ExceptionBase;

    /**
     * Gets the room details.
     *
     * @param blId the bl id
     * @param flId the floor id
     * @param rmId the room id
     *
     * @return the room details
     *
     * @throws ExceptionBase ExceptionBase
     * @deprecated Maintained for compatibility with 21.2 Outlook Plugin.
     */
    @Deprecated
    @WebMethod(action = "getRoomDetails")
    Room getRoomDetails(String blId, String flId, String rmId) throws ExceptionBase;

    /**
     * Heart beat useful for verifying connection status.
     *
     * @return the string
     */
    @WebMethod(action = "heartBeat")
    String heartBeat();

}
