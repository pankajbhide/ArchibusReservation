package com.archibus.app.reservation.service.helpers;

import java.util.*;

import com.archibus.app.reservation.dao.IRoomReservationDataSource;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.*;
import com.archibus.app.reservation.service.RecurrenceService;
import com.archibus.app.reservation.service.actions.FindNextDateOccurrenceAction;
import com.archibus.app.reservation.util.*;
import com.archibus.utility.LocalDateTimeUtil;

/**
 * The Class ReservationServiceHelper.
 */
public final class RecurrenceHelper {

    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private RecurrenceHelper() {
    }

    /**
     * Mark the given reservation as recurring.
     *
     * @param roomReservationDataSource the room reservation data source
     * @param reservation the room reservation to mark as recurring
     * @param existingReservations other reservations in the recurrence series sorted by start date
     */
    public static void markRecurring(final IRoomReservationDataSource roomReservationDataSource,
            final RoomReservation reservation, final List<RoomReservation> existingReservations) {

        // first make sure we have only one reservation per occurrence
        final List<RoomReservation> uniqueReservations = new ArrayList<RoomReservation>();
        final Set<Integer> occurrenceIndexes = new HashSet<Integer>();
        for (final RoomReservation existingReservation : existingReservations) {
            if (occurrenceIndexes.add(existingReservation.getOccurrenceIndex())) {
                uniqueReservations.add(existingReservation);
            }
        }

        Integer parentId = reservation.getReserveId();
        // Find the active reservation with the earliest start date.
        // Start by assuming it's the new one.

        // determine the occurrence index
        final int occurrenceIndex = calculateOccurrenceIndex(reservation);

        // only heed the existing reservations if there isn't already a reservation with the same
        // occurrence index
        if (occurrenceIndexes.add(occurrenceIndex)) {
            Date startDate = reservation.getStartDate();
            for (final RoomReservation existingReservation : uniqueReservations) {
                if (existingReservation.getStartDate().before(startDate)) {
                    startDate = existingReservation.getStartDate();
                    parentId = existingReservation.getParentId();
                }
            }
            roomReservationDataSource.markRecurring(reservation, parentId,
                reservation.getRecurrence().toString(), occurrenceIndex);

            // Update the parent id in the other reservations.
            for (final RoomReservation existingReservation : uniqueReservations) {
                roomReservationDataSource.markRecurring(existingReservation, parentId,
                    existingReservation.getRecurringRule(),
                    existingReservation.getOccurrenceIndex());
            }

        } else {
            roomReservationDataSource.markRecurring(reservation, parentId,
                reservation.getRecurrence().toString(), occurrenceIndex);
        }

    }

    /**
     * Calculate the occurrence index for a reservation with recurrence pattern.
     *
     * @param reservation the reservation to calculate the occurrence index for
     * @return the occurrence index
     */
    public static int calculateOccurrenceIndex(final RoomReservation reservation) {
        final Recurrence recurrence = reservation.getRecurrence();
        final List<Date> dateList = RecurrenceService.getDateList(recurrence.getStartDate(),
            recurrence.getEndDate(), recurrence.toString());
        return dateList.indexOf(TimePeriod.clearTime(reservation.getOriginalDate())) + 1;
    }

    /**
     * Modify the reservation and recurrence to start from the first occurrence which doesn't occur
     * in the past. The date of the reservation is modified to match the original date of the first
     * future reservation.
     *
     * @param reservation the reservation to modify
     * @param recurrence the recurrence to modify
     * @return number of skipped occurrences
     */
    public static int moveToNextOccurrence(final RoomReservation reservation,
            final Recurrence recurrence) {
        final int numberOfSkippedOccurrences = moveToNextOccurrence(recurrence,
            TimeZoneConverter.getTimeZoneIdForBuilding(reservation.determineBuildingId()));
        if (reservation.getStartDate().before(recurrence.getStartDate())) {
            final long endDateOffset =
                    reservation.getEndDate().getTime() - reservation.getStartDate().getTime();

            reservation.setStartDate(recurrence.getStartDate());
            reservation.setEndDate(new Date(reservation.getStartDate().getTime() + endDateOffset));
        }
        return numberOfSkippedOccurrences;
    }

    /**
     * Modify the recurrence to start from the first occurrence which doesn't occur in the past.
     *
     * @param recurrence the recurrence to modify
     * @param timeZone time zone id for the recurrence pattern
     * @return number of skipped occurrences
     */
    public static int moveToNextOccurrence(final Recurrence recurrence, final String timeZone) {
        final Date currentLocalDate =
                TimePeriod.clearTime(LocalDateTimeUtil.currentLocalDateForTimeZone(timeZone));
        int numberOfSkippedOccurrences = 0;

        // check whether the first date is modified before checking whether it's in the past
        Date actualStartDate = recurrence.getStartDate();
        final TimePeriod modifiedTimePeriod = recurrence.getModifiedTimePeriod(actualStartDate);
        if (modifiedTimePeriod != null) {
            actualStartDate = modifiedTimePeriod.getStartDate();
        }

        /*
         * Find the next suitable date if - the current actual start date is in the past; - the
         * first occurrence is cancelled.
         */
        if ((actualStartDate.before(currentLocalDate)
                || recurrence.isDateCancelled(actualStartDate))
                && recurrence instanceof AbstractIntervalPattern) {
            final FindNextDateOccurrenceAction action =
                    new FindNextDateOccurrenceAction(currentLocalDate);
            ((AbstractIntervalPattern) recurrence).loopThroughRepeats(action);
            final Date futureStartDate = action.getOriginalNextDate();
            if (futureStartDate != null) {
                recurrence.setStartDate(futureStartDate);
                numberOfSkippedOccurrences = 1 + action.getNumberOfSkippedOccurrences();
                recurrence.setNumberOfSkippedOccurrences(numberOfSkippedOccurrences);
            }
        }
        return numberOfSkippedOccurrences;
    }

    /**
     * Prepare the first reservation based on the received reservation. Copy the room, reservation
     * name, comments, attendees and time period.
     *
     * @param receivedReservation the reservation received from the client (already modified to the
     *            correct first date)
     * @param firstOccurrenceIndex the first occurrence index to save in the recurrence series
     * @param existingReservations the existing reservations mapped by occurrence index
     * @return the actual first reservation to save (with correct id's or id's cleared if required)
     */
    public static RoomReservation prepareFirstReservation(final RoomReservation receivedReservation,
            final int firstOccurrenceIndex,
            final Map<Integer, List<RoomReservation>> existingReservations) {

        // begin by assuming the received reservation is the actual first reservation
        RoomReservation actualFirstReservation = receivedReservation;

        final RoomReservation firstOccurrence =
                ReservationUtils.getPrimaryOccurrence(existingReservations, firstOccurrenceIndex);
        if (firstOccurrence == null) {
            if (actualFirstReservation.getReserveId() != null
                    && actualFirstReservation.getOccurrenceIndex() > 0) {
                /*
                 * No first occurrence was found so that occurrence is currently not linked to a
                 * reservation. In practice this means the occurrence index of the received
                 * reservation will not match or it will be 0, or the plugin has somehow sent an
                 * outdated reservation record which is no longer valid. So clear the id's and
                 * resource allocations to prevent updating the wrong records.
                 */
                actualFirstReservation.setReserveId(null);
                actualFirstReservation.setConferenceId(null);
                actualFirstReservation.setParentId(null);
                actualFirstReservation.getRoomAllocations().get(0).setId(null);
                actualFirstReservation.getResourceAllocations().clear();
                actualFirstReservation.setOccurrenceIndex(firstOccurrenceIndex);
            }
        } else if (!firstOccurrence.getReserveId().equals(actualFirstReservation.getReserveId())) {
            /*
             * A first occurrence was found and the reservation id is different. This is either
             * because the Outlook Plugin has outdated information or because the plugin sent an
             * occurrence which takes place in the past.
             */
            actualFirstReservation = firstOccurrence;
            if (actualFirstReservation.getRoomAllocations().isEmpty()) {
                actualFirstReservation.getRoomAllocations()
                    .add(new RoomAllocation(
                        receivedReservation.getRoomAllocations().get(0).getRoomArrangement(),
                        actualFirstReservation));
            } else {
                final RoomAllocation allocation =
                        actualFirstReservation.getRoomAllocations().get(0);
                receivedReservation.getRoomAllocations().get(0).copyTo(allocation);
            }

            actualFirstReservation.setTimePeriod(receivedReservation.getTimePeriod());
            actualFirstReservation.setReservationName(receivedReservation.getReservationName());
            actualFirstReservation.setComments(receivedReservation.getComments());
            actualFirstReservation.setAttendees(receivedReservation.getAttendees());
        }

        return actualFirstReservation;
    }

}
