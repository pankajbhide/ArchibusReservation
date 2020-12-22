package com.archibus.app.reservation.service.kiosk;

import java.util.*;

import com.archibus.datasource.data.*;

/**
 * Copyright (C) ARCHIBUS, Inc. All rights reserved.
 *
 * Interface describing the WFR's for the Essentials Tier Reservations Application.
 * <p>
 * Managed by Spring, has prototype singleton scope. Configured in reservation-services.xml file.
 *
 * @author Yorik Gerlo
 * @since 23.3
 */
public interface IReservationKioskService {

    /**
     * Get the fixed room resources for reservation search form in a specific building.
     *
     * @param buildingId the building code to restrict the search to
     * @return the fixed resources available in the given building
     */
    Map<String, String> getFixedResourcesForReservationSearch(String buildingId);

    /**
     * Searches available reservation rooms.
     *
     * @param requestParameters parameters of the request
     * @return the list of room arrangements, including number of conflicts for recurring
     */
    List<Map<String, Object>> searchAvailableReservationRooms(
            Map<String, Object> requestParameters);

    /**
     * Reserves the room.
     *
     * @param reservation reservation data record
     * @param roomAllocation primary room allocation
     * @param reservationExceptions list of reservation records that deviate from the recurrence
     *            pattern (only for recurring reservations with exceptions)
     * @param roomAllocationExceptions list of room allocation (reserve_rm) records that deviate
     *            from the recurrence pattern (only for recurring reservation with exceptions)
     * @return the primary created reservation
     */
    DataRecord saveRoomReservation(DataRecord reservation, DataRecord roomAllocation,
            DataSetList reservationExceptions, DataSetList roomAllocationExceptions);

}
