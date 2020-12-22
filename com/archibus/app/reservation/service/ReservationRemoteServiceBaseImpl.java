package com.archibus.app.reservation.service;

import java.util.List;

import com.archibus.app.common.space.domain.*;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.Recurrence;
import com.archibus.app.reservation.service.helpers.*;
import com.archibus.app.reservation.util.ReservationsContextHelper;
import com.archibus.utility.ExceptionBase;

/**
 * Base class for the Reservations Web Service. Defines the methods related to location hierarchy.
 * <p>
 * Managed by Spring, has singleton scope. Configured in reservation-webservices.xml file.
 *
 * @author Yorik Gerlo
 * @since 23.1
 *
 */
public class ReservationRemoteServiceBaseImpl {

    /** The employee service. */
    protected IEmployeeService employeeService;

    /** The location query handler. */
    protected LocationQueryHandler locationQueryHandler;

    /** The parameter helper for retrieving activity parameters and other settings. */
    protected ActivityParameterHelper activityParameterHelper;

    /**
     * {@inheritDoc}
     */
    public LocationQueryResult findLocations(final LocationQuery query) throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();
        return this.locationQueryHandler.handle(query);
    }

    /**
     * {@inheritDoc}
     */
    public final List<ArrangeType> getArrangeTypes() throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();
        return this.locationQueryHandler.getSpaceService().getArrangeTypes();
    }

    /**
     * {@inheritDoc}
     */
    public final List<ResourceStandard> getRoomAttributes() throws ExceptionBase {
        return this.locationQueryHandler.getFixedResourceStandards();
    }

    /**
     * {@inheritDoc}
     */
    public final UserLocation getUserLocation(final String email) throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();
        ReservationsContextHelper.checkPluginLicense();
        this.employeeService.findEmployee(email);
        return this.employeeService.getUserLocation();
    }

    /**
     * {@inheritDoc}
     */
    public final List<Country> findCountries(final Country filter) throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();
        return this.locationQueryHandler.getSpaceService().getCountries(filter);
    }

    /**
     * {@inheritDoc}
     */
    public final List<State> findStates(final State filter) throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();
        return this.locationQueryHandler.getSpaceService().getStates(filter);
    }

    /**
     * {@inheritDoc}
     */
    public final List<City> findCities(final City filter) throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();
        return this.locationQueryHandler.getSpaceService().getCities(filter);
    }

    /**
     * {@inheritDoc}
     */
    public final List<Site> findSites(final Site filter) throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();
        return this.locationQueryHandler.getSpaceService().getSites(filter);
    }

    /**
     * {@inheritDoc}
     */
    public final List<Building> findBuildings(final Building filter) throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();
        return this.locationQueryHandler.getSpaceService().getBuildings(filter);
    }

    /**
     * {@inheritDoc}
     */
    public final List<Floor> getFloors(final Floor filter) throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();
        return this.locationQueryHandler.getSpaceService().getFloors(filter);
    }

    /**
     * {@inheritDoc}
     */
    public final String heartBeat() {
        return "ok";
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    public final Room getRoomDetails(final String blId, final String flId, final String rmId)
            throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();
        return this.locationQueryHandler.getSpaceService().getRoomDetails(blId, flId, rmId);
    }

    /**
     * Sets the employee service.
     *
     * @param employeeService the new employee service
     */
    public final void setEmployeeService(final IEmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    /**
     * Set the location query handler.
     *
     * @param locationQueryHandler the location query handler
     */
    public final void setLocationQueryHandler(final LocationQueryHandler locationQueryHandler) {
        this.locationQueryHandler = locationQueryHandler;
    }

    /**
     * {@inheritDoc}
     */
    public final List<RoomArrangement> findAvailableRooms(final RoomReservation reservation,
            final Integer capacity, final boolean allDayEvent,
            final List<ResourceStandard> roomAttributes) throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();
        return this.locationQueryHandler.findAvailableRooms(reservation, capacity, allDayEvent,
            null, roomAttributes);
    }

    /**
     * {@inheritDoc}
     */
    public final List<RoomArrangement> findAvailableRoomsRecurrence(
            final RoomReservation reservation, final Integer capacity, final boolean allDayEvent,
            final Recurrence recurrence, final List<ResourceStandard> roomAttributes)
            throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();
        return this.locationQueryHandler.findAvailableRooms(reservation, capacity, allDayEvent,
            recurrence, roomAttributes);
    }

    /**
     * {@inheritDoc}
     */
    public final String getActivityParameter(final String identifier) throws ExceptionBase {
        return this.activityParameterHelper.getActivityParameter(identifier);
    }

    /**
     * {@inheritDoc}
     */
    public final List<String> getActivityParameters(final List<String> identifiers) {
        return this.activityParameterHelper.getActivityParameters(identifiers);
    }

    /**
     * Setter for the parameter helper.
     *
     * @param activityParameterHelper the new activity parameter helper
     */
    public final void setActivityParameterHelper(
            final ActivityParameterHelper activityParameterHelper) {
        this.activityParameterHelper = activityParameterHelper;
    }

}