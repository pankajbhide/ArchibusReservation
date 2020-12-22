package com.archibus.app.reservation.util;

import java.sql.Time;
import java.util.*;

import com.archibus.app.common.organization.domain.Employee;
import com.archibus.app.common.util.SchemaUtils;
import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.utility.*;

/**
 * Utilities for processing reservation objects.
 *
 * @author Yorik Gerlo
 * @since 21.3
 */
public final class ReservationUtils {

    /**
     * Private default constructor. This class cannot be instantiated.
     */
    private ReservationUtils() {

    }

    /**
     * Copy common conference reservation properties from one reservation to another which is part
     * of the same conference call.
     *
     * @param source the source reservation
     * @param target the target reservation
     */
    public static void copyCommonConferenceProperties(final RoomReservation source,
            final RoomReservation target) {
        /*
         * Copy all common properties from the primary reservation to the existing reservations.
         * Note if additional properties are included in the views, then this code should be
         * extended to copy those as well.
         */
        target.setTimePeriod(source.getTimePeriod());
        target.setReservationName(source.getReservationName());
        target.setComments(source.getComments());
        target.setHtmlComments(source.getHtmlComments());
        target.setAttendees(source.getAttendees());
        target.setDivisionId(source.getDivisionId());
        target.setDepartmentId(source.getDepartmentId());
    }

    /**
     * Remove all properties related to recurrence from the reservation.
     *
     * @param reservation the reservation to remove the recurrence from
     */
    public static void removeRecurrence(final IReservation reservation) {
        reservation.setRecurrence(null);
        reservation.setParentId(null);
        reservation.setRecurringRule("");
        reservation.setRecurringDateModified(0);
        reservation.setOccurrenceIndex(0);
    }

    /**
     * Group a list of reservations by start date.
     *
     * @param reservations the reservations to group
     * @return the reservations grouped by date
     */
    public static Map<Date, List<RoomReservation>> groupByStartDate(
            final List<RoomReservation> reservations) {
        // group reservations by start date
        final Map<Date, List<RoomReservation>> reservationsByDate =
                new HashMap<Date, List<RoomReservation>>();
        for (final RoomReservation reservation : reservations) {
            final Date startDate = reservation.getStartDate();
            List<RoomReservation> reservationsOnDate = reservationsByDate.get(startDate);
            if (reservationsOnDate == null) {
                reservationsOnDate = new ArrayList<RoomReservation>();
                reservationsByDate.put(startDate, reservationsOnDate);
            }
            reservationsOnDate.add(reservation);
        }
        return reservationsByDate;
    }

    /**
     * Group a list of reservations by occurrence index.
     *
     * @param reservations the reservations to map
     * @param firstOccurrenceIndex the first occurrence index to include in the map
     * @return the reservations grouped by their occurrence index (excluding the ones with an
     *         occurrence index smaller than the specified firstOccurrenceIndex)
     */
    public static Map<Integer, List<RoomReservation>> groupByOccurrenceIndex(
            final List<RoomReservation> reservations, final int firstOccurrenceIndex) {
        final Map<Integer, List<RoomReservation>> mappedReservations =
                new HashMap<Integer, List<RoomReservation>>();
        for (final RoomReservation reservation : reservations) {
            final Integer occurrenceIndex = reservation.getOccurrenceIndex();

            // skip if the occurrence index is smaller than the lower limit
            if (occurrenceIndex >= firstOccurrenceIndex) {
                List<RoomReservation> reservationsWithIndex =
                        mappedReservations.get(occurrenceIndex);
                if (reservationsWithIndex == null) {
                    reservationsWithIndex = new ArrayList<RoomReservation>();
                    mappedReservations.put(occurrenceIndex, reservationsWithIndex);
                }
                reservationsWithIndex.add(reservation);
            }
        }
        return mappedReservations;
    }

    /**
     * Extract the primary reservation with given occurrence index from lists of reservations
     * grouped by occurrence index.
     *
     * @param mappedReservations reservations mapped by occurrence index
     * @param occurrenceIndex the occurrence index
     * @return the primary reservation with matching occurrence index or null
     */
    public static RoomReservation getPrimaryOccurrence(
            final Map<Integer, List<RoomReservation>> mappedReservations,
            final int occurrenceIndex) {
        final List<RoomReservation> reservationsWithIndex = mappedReservations.get(occurrenceIndex);
        RoomReservation result = null;
        if (reservationsWithIndex != null && !reservationsWithIndex.isEmpty()) {
            result = reservationsWithIndex.get(0);
        }
        return result;
    }

    /**
     * Map the given list of reservations by their start date.
     *
     * @param reservations the reservations
     * @return the same reservations mapped by start date
     */
    public static Map<Date, RoomReservation> toDateMap(final List<RoomReservation> reservations) {
        final Map<Date, RoomReservation> mappedReservations = new HashMap<Date, RoomReservation>();
        if (reservations != null) {
            for (final RoomReservation occurrence : reservations) {
                mappedReservations.put(occurrence.getStartDate(), occurrence);
            }
        }
        return mappedReservations;
    }

    /**
     * Map a list of reservations by their reservation id.
     *
     * @param reservations the list of reservation to map
     * @return reservations mapped by reservation id
     */
    public static Map<Integer, RoomReservation> mapByReservationId(
            final List<RoomReservation> reservations) {
        final Map<Integer, RoomReservation> mappedReservations =
                new HashMap<Integer, RoomReservation>();
        if (reservations != null) {
            for (final RoomReservation reservation : reservations) {
                mappedReservations.put(reservation.getReserveId(), reservation);
            }
        }
        return mappedReservations;
    }

    /**
     * Map a list of reservations by their building id.
     *
     * @param reservations the list of reservation to map
     * @return reservations mapped by building id
     */
    public static Map<String, RoomReservation> mapByBuilding(
            final List<RoomReservation> reservations) {
        final Map<String, RoomReservation> mappedReservations =
                new HashMap<String, RoomReservation>();
        if (reservations != null) {
            for (final RoomReservation reservation : reservations) {
                mappedReservations.put(reservation.determineBuildingId(), reservation);
            }
        }
        return mappedReservations;
    }

    /**
     * Get the active resource allocations in the reservation, i.e. those that don't have rejected
     * or cancelled status.
     *
     * @param reservation the reservation
     * @return active resource allocations in the reservation
     */
    public static List<ResourceAllocation> getActiveResourceAllocations(
            final IReservation reservation) {
        final List<ResourceAllocation> activeAllocations = new ArrayList<ResourceAllocation>();
        for (final ResourceAllocation allocation : reservation.getResourceAllocations()) {
            if (!(Constants.STATUS_CANCELLED.equals(allocation.getStatus())
                    || Constants.STATUS_REJECTED.equals(allocation.getStatus()))) {

                activeAllocations.add(allocation);
            }
        }
        return activeAllocations;
    }

    /**
     * Get the time period of the reservation in the specified time zone. Converts from building
     * time to the given time zone except when the time zone currently in the reservation matches
     * the requested time zone.
     *
     * @param reservation the reservation
     * @param timeZoneId the time zone identifier
     * @return time period in the given time zone
     */
    public static TimePeriod getTimePeriodInTimeZone(final IReservation reservation,
            final String timeZoneId) {
        Date startDateTime = null;
        Date endDateTime = null;
        if (StringUtil.isNullOrEmpty(reservation.getTimeZone())) {
            final String building = reservation.determineBuildingId();
            reservation.setTimeZone(TimeZoneConverter.getTimeZoneIdForBuilding(building));
        }

        if (timeZoneId.equals(reservation.getTimeZone())) {
            startDateTime = reservation.getStartDateTime();
            endDateTime = reservation.getEndDateTime();
        } else {
            startDateTime = TimeZoneConverter.calculateDateTime(reservation.getStartDateTime(),
                reservation.getTimeZone(), timeZoneId);
            endDateTime = TimeZoneConverter.calculateDateTime(reservation.getEndDateTime(),
                reservation.getTimeZone(), timeZoneId);
        }

        return new TimePeriod(startDateTime, endDateTime, timeZoneId);
    }

    /**
     * Get the time period of the reservation in UTC on the given date.
     *
     * @param reservation the reservation
     * @param date the target start date
     * @param originalTimePeriod the original time period of the reservation in local time
     * @return the time period of the reservation moved to the master date, in UTC time
     */
    public static TimePeriod getUtcTimePeriodForDate(final RoomReservation reservation,
            final Date date, final TimePeriod originalTimePeriod) {
        TimePeriod reservationTimePeriod = null;
        if (TimePeriod.clearTime(reservation.getStartDate()).equals(date)) {
            /*
             * The first reservation is on the original date of the first occurrence, so we can
             * safely convert the reservation time to UTC to compare with the meeting time.
             */
            reservationTimePeriod =
                    ReservationUtils.getTimePeriodInTimeZone(reservation, Constants.TIMEZONE_UTC);
        } else {
            /*
             * The date doesn't match, so first move the reservation time period to the master date
             * before applying the time zone conversion. Restore the original time period
             * afterwards.
             */
            // determine the duration in minutes so we can set the correct endDateTime
            final int durationInMinutes = (int) (reservation.getEndDateTime().getTime()
                    - reservation.getStartDateTime().getTime()) / TimePeriod.MINUTE_MILLISECONDS;
            // move the reservation to the master date
            reservation.setStartDate(date);
            final Calendar calendar = Calendar.getInstance();
            calendar.setTime(reservation.getStartDateTime());
            calendar.add(Calendar.MINUTE, durationInMinutes);
            reservation.setEndDateTime(calendar.getTime());
            reservationTimePeriod =
                    ReservationUtils.getTimePeriodInTimeZone(reservation, Constants.TIMEZONE_UTC);

            // restore the actual time period of the reservation (in case it was changed)
            reservation.setTimePeriod(originalTimePeriod);
        }
        return reservationTimePeriod;
    }

    /**
     * Determines the current local date for this reservation.
     *
     * @param reservation the reservation
     * @return the current local date
     */
    public static Date determineCurrentLocalDate(final IReservation reservation) {
        Date currentDate = null;

        final String building = reservation.determineBuildingId();
        if (StringUtil.isNullOrEmpty(building)) {
            currentDate = TimePeriod.clearTime(Utility.currentDate());
        } else {
            currentDate = TimePeriod
                .clearTime(LocalDateTimeUtil.currentLocalDate(null, null, null, building));
        }
        return currentDate;
    }

    /**
     * Determines the current local time for this reservation.
     *
     * @param reservation the reservation
     * @return the current local time
     */
    public static Time determineCurrentLocalTime(final IReservation reservation) {
        Time currentTime = null;

        final String building = reservation.determineBuildingId();
        if (StringUtil.isNullOrEmpty(building)) {
            currentTime = Utility.currentTime();
        } else {
            currentTime = LocalDateTimeUtil.currentLocalTime(null, null, null, building);
        }
        return currentTime;
    }

    /**
     * Set the requestor and creator of this reservation to be the given employees.
     *
     * @param reservation the reservation
     * @param requestor the employee to set as requestor of the reservation.
     * @param creatorId id of the employee to set as creator of the reservation
     */
    public static void setCreator(final AbstractReservation reservation, final Employee requestor,
            final String creatorId) {
        reservation.setRequestedBy(requestor.getId());
        reservation.setRequestedFor(requestor.getId());
        reservation.setDepartmentId(requestor.getDepartmentId());
        reservation.setDivisionId(requestor.getDivisionId());
        reservation.setCreatedBy(creatorId);
        reservation.setCreationDate(new Date());
        reservation.setPhone(requestor.getPhone());
    }

    /**
     * Truncate the comments in the given reservation so it doesn't exceed the maximum length.
     *
     * @param reservation the reservation for which to truncate the comments
     */
    public static void truncateComments(final IReservation reservation) {
        final int maxLength = SchemaUtils.getFieldDef("reserve", "comments").getSize();
        if (StringUtil.notNullOrEmpty(reservation.getComments())
                && reservation.getComments().getBytes().length > maxLength) {
            final byte[] byteComments = reservation.getComments().getBytes();
            final byte[] truncated = new byte[maxLength];
            System.arraycopy(byteComments, 0, truncated, 0, maxLength);
            final String truncatedComment = new String(truncated);
            reservation.setComments(truncatedComment);
        }
    }

}
