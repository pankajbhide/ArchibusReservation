package com.archibus.app.reservation.service.mobile;

import java.util.*;

/**
 * API of the Workplace Portal Reservations Workflow Rule Service for mobile Workplace Services
 * Portal application.
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
 */
public interface IWorkplacePortalReservationsMobileService {
    /**
     * Searches available reservation rooms.
     *
     * @param userName User Name
     * @param requestParameters parameters of the request
     * @return the list of room arrangements
     */
    List<Map<String, Object>> searchAvailableReservationRooms(final String userName,
        Map<String, Object> requestParameters);

    /**
     * Reserves the room.
     *
     * @param userName User Name
     * @param requestParameters parameters of the reservation request
     */
    void reserveRoom(final String userName, Map<String, String> requestParameters);

    /**
     * Check-in the room reservation.
     *
     * @param userName User Name
     * @param requestParameters parameters of the reservation request
     */
    void checkInRoomReservation(final String userName, Map<String, String> requestParameters);

    /**
     * Cancel room reservation.
     *
     * @param userName User Name
     * @param requestParameters the reservation id
     */
    void cancelRoomReservation(final String userName, Map<String, String> requestParameters);

    /**
     * Cancel all occurrences of a recurring room reservation.
     *
     * @param userName User Name
     * @param requestParameters the reservation id
     */
    void cancelRecurringRoomReservation(final String userName, Map<String, String> requestParameters);

    /**
     * Get the fixed room resources for reservation search form.
     *
     * @return the fixed resources.
     */
    Map<String, String> getFixedResourcesForReservationSearch();
}
