package com.archibus.app.reservation.util;

import java.sql.Time;
import java.util.*;

import org.apache.log4j.Logger;
import org.json.JSONObject;

import com.archibus.app.reservation.domain.*;
import com.archibus.datasource.*;
import com.archibus.datasource.data.DataRecord;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.utility.*;

/**
 * Utility class. Provides methods to convert reservations to a given time zone.
 * <p>
 *
 * Used by ReservationService.
 *
 * @author Yorik Gerlo
 * @since 20.1
 *
 */
public final class TimeZoneConverter {

    /** Time zones table name. */
    private static final String AFM_TIMEZONES = "afm_timezones";

    /** Time zone ID field name. */
    private static final String TIMEZONE_ID = "timezone_id";

    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private TimeZoneConverter() {
    }

    /**
     * Gets the time zone. If the building id is null or the building doesn't have a time zone,
     * returns the default time zone of the server.
     *
     * @param buildingId the building id
     * @return the time zone if of the building, or the default time zone id
     */
    public static String getTimeZoneIdForBuilding(final String buildingId) {
        String timeZoneId = null;
        if (StringUtil.isNullOrEmpty(buildingId)) {
            timeZoneId = TimeZone.getDefault().getID();
            Logger.getLogger(TimeZoneConverter.class)
                .debug("No building ID specified, using default timezone.");
        } else {
            timeZoneId = LocalDateTimeUtil.getLocationTimeZone(null, null, null, buildingId);
            if (timeZoneId == null) {
                timeZoneId = TimeZone.getDefault().getID();
                Logger.getLogger(TimeZoneConverter.class).debug(
                    "Building '" + buildingId + "' has no time zone, using default timezone.");
            }
        }

        return timeZoneId;
    }

    /**
     * Get the time zone record for a given building.
     *
     * @param buildingId the building id
     * @return the time zone data record
     */
    public static DataRecord getTimeZoneForBuilding(final String buildingId) {
        final DataSource dataSource = DataSourceFactory.createDataSourceForFields(AFM_TIMEZONES,
            new String[] { TIMEZONE_ID });
        dataSource.addRestriction(Restrictions.eq(AFM_TIMEZONES, TIMEZONE_ID,
            TimeZoneConverter.getTimeZoneIdForBuilding(buildingId)));
        return dataSource.getRecord();
    }

    /**
     * Get the current local date/time for the given buildings.
     *
     * @param buildingIds list of building IDs
     * @return JSON mapping of each building id to the current date/time in that building
     */
    public static JSONObject getCurrentLocalDateTime(final List<String> buildingIds) {
        final Set<String> uniqueBuildingIds = new HashSet<String>(buildingIds);
        final JSONObject localDateTimes = new JSONObject();

        for (final String buildingId : uniqueBuildingIds) {
            final JSONObject buildingDateTime = new JSONObject();
            buildingDateTime.put("date",
                LocalDateTimeUtil.currentLocalDate(null, null, null, buildingId).toString());
            buildingDateTime.put("time",
                LocalDateTimeUtil.currentLocalTime(null, null, null, buildingId).toString());

            localDateTimes.put(buildingId, buildingDateTime);
        }

        return localDateTimes;
    }

    /**
     * Change the time zone of the reservations to the time zone of the requestor. The dates and
     * times in the current reservation objects are modified to reflect the same absolute time as
     * before, but specified in the time zone of the requestor.
     *
     * @param reservations the reservations to modify
     * @param timeZone the target time zone (time zone of the requestor)
     * @return modified reservations mapped according to (start)date
     */
    public static Map<Date, RoomReservation> toRequestorTimeZone(
            final List<RoomReservation> reservations, final String timeZone) {
        convertToTimeZone(reservations, timeZone);

        final Map<Date, RoomReservation> reservationMap =
                new HashMap<Date, RoomReservation>(reservations.size());
        for (final RoomReservation reservation : reservations) {
            reservationMap.put(reservation.getStartDate(), reservation);
        }
        return reservationMap;
    }

    /**
     * Convert the given list of reservations to the given time zone. If the reservations don't
     * specify a time zone already, the times are considered in local time.
     *
     * @param reservations the reservations to convert
     * @param timeZone the target time zone
     */
    public static void convertToTimeZone(final List<RoomReservation> reservations,
            final String timeZone) {
        if (reservations != null && StringUtil.notNullOrEmpty(timeZone)) {
            final TimeZoneCache timeZoneCache = new TimeZoneCache();
            for (final RoomReservation reservation : reservations) {
                if (StringUtil.isNullOrEmpty(reservation.getTimeZone())) {
                    reservation.setTimeZone(
                        timeZoneCache.getBuildingTimeZone(reservation.determineBuildingId()));
                }
                convertToTimeZone(reservation, timeZone);
            }
        }
    }

    /**
     * Convert the given room arrangements (currently in the given building's local time) to the
     * requested target time zone.
     *
     * @param roomArrangements the room arrangements to convert
     * @param date the date on which to base the conversion (for time-only properties)
     * @param buildingId the building id to use to determine the local time zone of the room
     *            arrangements
     * @param targetTimeZone the target time zone to convert to (null to skip conversion)
     */
    public static void convertToTimeZone(final List<RoomArrangement> roomArrangements,
            final Date date, final String buildingId, final String targetTimeZone) {
        // Convert the dayStart and dayEnd properties to the requested time zone.
        if (StringUtil.notNullOrEmpty(targetTimeZone)) {
            final String sourceTimeZone = getTimeZoneIdForBuilding(buildingId);
            for (final RoomArrangement arrangement : roomArrangements) {
                if (arrangement.getDayStart() != null) {
                    final Date dayStart = Utility.toDatetime(date, arrangement.getDayStart());
                    arrangement.setDayStart(new Time(TimeZoneConverter
                        .calculateDateTime(dayStart, sourceTimeZone, targetTimeZone).getTime()));
                }
                if (arrangement.getDayEnd() != null) {
                    final Date dayEnd = Utility.toDatetime(date, arrangement.getDayEnd());
                    arrangement.setDayEnd(new Time(TimeZoneConverter
                        .calculateDateTime(dayEnd, sourceTimeZone, targetTimeZone).getTime()));
                }
            }
        }
    }

    /**
     * Convert a reservation to the given time zone. The times of linked allocations are not
     * converted. Assume the reservation is in local time.
     *
     * @param reservation the reservation to convert
     * @param timeZoneId the target time zone
     */
    public static void convertToTimeZone(final RoomReservation reservation,
            final String timeZoneId) {
        reservation
            .setTimePeriod(ReservationUtils.getTimePeriodInTimeZone(reservation, timeZoneId));
    }

    /**
     * Calculate date time between two time zones.
     *
     * For web interface reservations will always be in the time zone of the building/site.
     * Therefore the site is always required when making a search. The requestor time zone will be
     * the site time zone.
     *
     * For reservations coming from Outlook, the requestor time zone will be GMT/UTC.
     *
     * @param startDateTime the start date time
     * @param sourceTimeZoneId the source time zone id, current time zone of the date/time
     * @param targetTimeZoneId the target time zone id to convert to
     *
     * @return the date/time in the target time zone
     */
    public static Date calculateDateTime(final Date startDateTime, final String sourceTimeZoneId,
            final String targetTimeZoneId) {
        TimeZone targetTimeZone = null;

        if (StringUtil.notNullOrEmpty(targetTimeZoneId)) {
            targetTimeZone = TimeZone.getTimeZone(targetTimeZoneId);
        } else {
            // if the time zone is not defined for the building, we assume the time zone of the
            // server.
            targetTimeZone = TimeZone.getDefault();
        }

        // offset in milliseconds
        final int targetOffset = targetTimeZone.getOffset(startDateTime.getTime());

        TimeZone sourceTimeZone = null;

        if (StringUtil.notNullOrEmpty(sourceTimeZoneId)) {
            sourceTimeZone = TimeZone.getTimeZone(sourceTimeZoneId);
        } else {
            sourceTimeZone = TimeZone.getDefault();
        }

        // offset in milliseconds
        final int sourceOffset = sourceTimeZone.getOffset(startDateTime.getTime());

        final Calendar cal = Calendar.getInstance();
        cal.setTime(startDateTime);
        cal.add(Calendar.MILLISECOND, targetOffset);
        cal.add(Calendar.MILLISECOND, -sourceOffset);
        return cal.getTime();
    }

    /**
     * Calculate the total offset in milliseconds between two time zones.
     *
     * @param date the date
     * @param time the time
     * @param sourceTimeZoneId the source time zone id
     * @param targetTimeZoneId the target time zone id
     *
     * @return the combined offset
     */
    public static int getCombinedOffset(final Date date, final Time time,
            final String sourceTimeZoneId, final String targetTimeZoneId) {
        final TimeZone sourceTimeZone = TimeZone.getTimeZone(sourceTimeZoneId);
        final TimeZone targetTimeZone = TimeZone.getTimeZone(targetTimeZoneId);
        final Date dateTime = Utility.toDatetime(date, time);

        return targetTimeZone.getOffset(dateTime.getTime())
                - sourceTimeZone.getOffset(dateTime.getTime());
    }

    /**
     * Set the time period in the reservation to local time and clear the time zone id afterwards to
     * indicate the times are local. Only applies a conversion if the time zone is currently set in
     * the reservation.
     *
     * @param reservation the reservation to convert to local time
     * @param localTimeZone the local time zone
     */
    public static void convertToLocalTime(final RoomReservation reservation,
            final String localTimeZone) {
        if (StringUtil.notNullOrEmpty(reservation.getTimeZone())) {
            convertToTimeZone(reservation, localTimeZone);
            // remove the time zone to indicate the times are in local time
            reservation.setTimeZone(null);
        }
    }

}
