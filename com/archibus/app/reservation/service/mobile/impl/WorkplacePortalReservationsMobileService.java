package com.archibus.app.reservation.service.mobile.impl;

import java.util.*;

import com.archibus.app.reservation.dao.datasource.ResourceStandardDataSource;
import com.archibus.app.reservation.domain.ResourceStandard;
import com.archibus.app.reservation.service.mobile.IWorkplacePortalReservationsMobileService;

/**
 * Implementation of the WorkplacePortal Reservations Workflow Rule Service for Workplace Services
 * Portal mobile application.
 * <p>
 * Registered in the ARCHIBUS Workflow Rules table as
 * 'AbWorkplacePortal-WorkplacePortalReservationsMobileService'.
 * <p>
 * Provides methods for find, reserve and cancel rooms
 * <p>
 * Invoked by mobile client.
 *
 * @author Cristina Moldovan
 * @since 21.2
 *
 */
public class WorkplacePortalReservationsMobileService implements
IWorkplacePortalReservationsMobileService {

    /**
     * {@inheritDoc}.
     *
     */
    @Override
    public List<Map<String, Object>> searchAvailableReservationRooms(final String userName,
        final Map<String, Object> requestParameters) {

        final ReservationsHandler reservationsUpdate = new ReservationsHandler();
        final List<Map<String, Object>> result =
                reservationsUpdate.searchAvailableReservationRooms(userName, requestParameters);

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reserveRoom(final String userName, final Map<String, String> requestParameters) {
        final ReservationsHandler reservationsUpdate = new ReservationsHandler();

        reservationsUpdate.reserveRoom(userName, requestParameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancelRoomReservation(final String userName,
            final Map<String, String> requestParameters) {
        final ReservationsHandler reservationsUpdate = new ReservationsHandler();

        reservationsUpdate.cancelRoomReservation(userName, requestParameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancelRecurringRoomReservation(final String userName,
            final Map<String, String> requestParameters) {
        final ReservationsHandler reservationsUpdate = new ReservationsHandler();

        reservationsUpdate.cancelRecurringRoomReservation(userName, requestParameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getFixedResourcesForReservationSearch() {
        final ResourceStandardDataSource resourceStandardDs = new ResourceStandardDataSource();
        final List<ResourceStandard> results = resourceStandardDs.getFixedResourceStandards();
        final Map<String, String> resources = new HashMap<String, String>();
        for (final ResourceStandard standard : results) {
            resources.put(standard.getId(), standard.getName());
        }
        return resources;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void checkInRoomReservation(final String userName,
            final Map<String, String> requestParameters) {

        final ReservationsHandler reservationsUpdate = new ReservationsHandler();
        reservationsUpdate.checkInRoomReservation(requestParameters);

    }
}
