package com.archibus.app.reservation.ics.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import com.archibus.app.reservation.dao.IConferenceCallReservationDataSource;
import com.archibus.app.reservation.domain.RoomAllocation;
import com.archibus.app.reservation.domain.RoomReservation;
import com.archibus.app.reservation.domain.recurrence.Recurrence;
import com.archibus.app.reservation.domain.recurrence.RecurrenceParser;
import com.archibus.app.reservation.ics.domain.MeetingLocationModel;
import com.archibus.app.reservation.service.RecurrenceService;
import com.archibus.app.reservation.util.ReservationUtils;
import com.archibus.app.reservation.util.TimeZoneCache;
import com.archibus.app.reservation.util.TimeZoneConverter;
import com.archibus.utility.Utility;

/**
 * Copyright (C) ARCHIBUS, Inc. All rights reserved.
 */
/**
 * Utility class. Provides methods to extract information from the reservation.
 * <p>
 *
 * @author PROCOS
 * @since 23.2
 *
 */
public final class MeetingInformationHelper {

    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private MeetingInformationHelper() {
    }

    /**
     * Calculate the until date time for recurring reservations.
     *
     * @param reservation the reservation
     * @param timezone the source time zone
     * @return the UTC calculated until date
     */
    public static Date calcUntilDateTime(final RoomReservation reservation,
            final String timezone) {

        Date endDate = reservation.getEndDate();

        if (reservation.getRecurrence() == null) {
            if (reservation.getCreatedReservations() != null
                    && !reservation.getCreatedReservations().isEmpty()) {
                final List<RoomReservation> createdRes =
                        reservation.getCreatedReservations();
                final RoomReservation lastReservation =
                        createdRes.get(createdRes.size() - 1);
                endDate = lastReservation.getEndDate();
            }
            final Recurrence pattern =
                    RecurrenceParser.parseRecurrence(reservation.getStartDate(),
                        endDate, reservation.getRecurringRule());
            reservation.setRecurrence(pattern);
        } else {
            endDate = reservation.getRecurrence().getEndDate();
        }

        return TimeZoneConverter.calculateDateTime(
            Utility.toDatetime(endDate, reservation.getEndTime()), timezone,
            "UTC");
    }

    /**
     * Get the cancelled recurrences from the parent reservation.
     *
     * @param roomReservation the parent reservation
     * @param reservationDs the reservations data source
     * @return the list of cancelled recurrences
     */
    public static List<Date> getCancelledRecurrences(
            final RoomReservation roomReservation,
            final IConferenceCallReservationDataSource reservationDs) {

        List<RoomReservation> occurrences;
        if (roomReservation.getConferenceId() == null) {
            occurrences = reservationDs
                .getByParentId(roomReservation.getParentId(),
                    roomReservation.getStartDate(), null, true);
        } else {
            occurrences = reservationDs
                .getConferenceCallOccurrences(roomReservation);
        }

        final List<Date> allDates = RecurrenceService.getDateList(
            roomReservation.getRecurrence().getStartDate(),
            roomReservation.getRecurrence().getEndDate(),
            roomReservation.getRecurringRule());
        final Set<Date> activeDates =
                ReservationUtils.toDateMap(occurrences).keySet();
        final List<Date> exceptionDates = new ArrayList<Date>();
        for (final Date date: allDates) {
            if (!activeDates.contains(date)) {
                exceptionDates.add(date);
            }
        }
        return exceptionDates;
    }

    /**
     * Get the MeetingLocationModel for a specific room.
     *
     * @param allocation the room allocation
     * @param timeZoneCache cache for determining the time zone
     * @return the MeetingLocationModel for the location
     */
    public static MeetingLocationModel getMeetingLocationModel(
            final RoomAllocation allocation,
            final TimeZoneCache timeZoneCache) {
        final MeetingLocationModel model = new MeetingLocationModel(
            allocation.getBlId(), allocation.getFlId(), allocation.getRmId());
        model.setTimezone(
            timeZoneCache.getBuildingTimeZone(allocation.getBlId()));
        return model;
    }

}
