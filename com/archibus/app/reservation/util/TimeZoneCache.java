package com.archibus.app.reservation.util;

import java.util.*;

/**
 * Represents a cache of time zone information.
 * <p>
 * Used by the Reservations Application to avoid repetitive querying to the database for determining
 * the time zone of a building. Managed by Spring, has prototype scope. Configured in
 * reservation-context.xml file.
 *
 * @author Yorik Gerlo
 * @since 22.1
 */
public class TimeZoneCache {

    /** Maps building codes to time zones. */
    private final Map<String, String> buildingTimeZones = new HashMap<String, String>();

    /**
     * Get the time zone id for the given building.
     *
     * @param buildingId the building id
     * @return the time zone id
     */
    public String getBuildingTimeZone(final String buildingId) {
        String timeZoneId;
        synchronized (this.buildingTimeZones) {
            timeZoneId = this.buildingTimeZones.get(buildingId);
        }
        if (timeZoneId == null) {
            timeZoneId = TimeZoneConverter.getTimeZoneIdForBuilding(buildingId);
            synchronized (this.buildingTimeZones) {
                this.buildingTimeZones.put(buildingId, timeZoneId);
            }
        }
        return timeZoneId;
    }

}
