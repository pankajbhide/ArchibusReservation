package com.archibus.app.reservation.service.actions;

import java.util.*;

import com.archibus.app.reservation.dao.IRoomArrangementDataSource;
import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.AbstractIntervalPattern;
import com.archibus.app.reservation.util.TimeZoneConverter;

/**
 * Provides a method to find available rooms for all occurrences in an IntervalPattern, via
 * implementation of the OccurrenceAction interface.
 *
 * @author Yorik Gerlo
 * @since 21.2
 */
public class FindAvailableRoomsOccurrenceAction
        implements AbstractIntervalPattern.ModifiedOccurrenceAction {

    /** All available room arrangements. */
    private final List<RoomArrangement> roomArrangements;

    /** The room arrangement data source, used to look for available rooms. */
    private final IRoomArrangementDataSource roomArrangementDataSource;

    /** Map with the existing existingReservations in the recurrence pattern. */
    private final Map<Date, RoomReservation> existingReservations;

    /** The room reservation used to look for available rooms. */
    private final RoomReservation reservation;

    /** The minimum numberAttendees of the rooms to find. */
    private final Integer numberAttendees;

    /** The required fixed resource standards. */
    private final List<String> fixedResourceStandards;

    /** The local time zone based on restrictions in the room reservation filter. */
    private final String localTimeZone;

    /** Whether we are looking for rooms available for an all day event. */
    private final boolean allDayEvent;

    /** Whether to allow conflicts in general, based on the initial parameters. */
    private final boolean allowConflicts;

    /**
     * Offset in milliseconds between the start and end date of the time period of the first
     * occurrence.
     */
    private final long endDateOffset;

    /** Number of visited occurrences. */
    private int numberOfOccurrences;

    /**
     * Constructor.
     *
     * @param firstReservation the reservation object representing the first occurrence of the
     *            interval pattern
     * @param numberAttendees the number of attendees
     * @param fixedResourceStandards the required fixed resource standards
     * @param allDayEvent whether we are looking for rooms that will be booked for an all day event
     * @param existingOccurrences the existing occurrences of the recurring reservation
     * @param roomArrangements the available room arrangements
     * @param roomArrangementDataSource the room arrangement data source to use for finding
     *            available rooms
     */
    public FindAvailableRoomsOccurrenceAction(final RoomReservation firstReservation,
            final Integer numberAttendees, final List<String> fixedResourceStandards,
            final boolean allDayEvent, final Map<Date, RoomReservation> existingOccurrences,
            final List<RoomArrangement> roomArrangements,
            final IRoomArrangementDataSource roomArrangementDataSource) {
        this.reservation = firstReservation;
        this.numberAttendees = numberAttendees;
        this.fixedResourceStandards = fixedResourceStandards;
        this.allDayEvent = allDayEvent;
        this.roomArrangements = roomArrangements;
        this.roomArrangementDataSource = roomArrangementDataSource;
        this.existingReservations = existingOccurrences;

        this.localTimeZone =
                TimeZoneConverter.getTimeZoneIdForBuilding(this.reservation.determineBuildingId());

        // KB 3045935: consider end time occurring on the next day in the requestor's time zone
        this.endDateOffset =
                this.reservation.getEndDate().getTime() - this.reservation.getStartDate().getTime();

        // KB 3051833 don't allow conflicts when editing a recurring conference call (from Outlook)
        this.allowConflicts = this.reservation.getConferenceId() == null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean handleOccurrence(final Date date) throws ReservationException {
        /*
         * Keep the original time period as starting point for each occurrence. This ensures the
         * time zone conversion can be applied for each individual occurrence according to the
         * actual DST rules.
         */
        final TimePeriod originalTimePeriod = new TimePeriod(this.reservation.getTimePeriod());
        try {
            // modify the reservation time period
            this.reservation.setStartDate(date);
            this.reservation.setEndDate(new Date(date.getTime() + this.endDateOffset));
            return handleActualOccurrence();
        } finally {
            this.reservation.setTimePeriod(originalTimePeriod);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean handleOccurrence(final Date date, final TimePeriod timePeriod)
            throws ReservationException {
        /*
         * Temporarily set the custom time period in the reservation to check for available rooms.
         * Reset to the default time period afterwards.
         */
        final TimePeriod originalTimePeriod = new TimePeriod(this.reservation.getTimePeriod());
        try {
            this.reservation.setTimePeriod(timePeriod);
            return handleActualOccurrence();
        } finally {
            this.reservation.setTimePeriod(originalTimePeriod);
        }
    }

    /**
     * Handle an occurrence that takes place on the date currently specified in the reservation.
     *
     * @return whether to continue the loop
     * @throws ReservationException when a reservation exception occurs
     */
    private boolean handleActualOccurrence() throws ReservationException {
        this.numberOfOccurrences++;

        final RoomReservation occurrence =
                this.existingReservations.get(this.reservation.getStartDate());
        Integer reservationId = null;
        Integer conferenceId = null;
        Integer[] reservationIdsInConference = null;
        if (occurrence != null) {
            reservationId = occurrence.getReserveId();
            conferenceId = occurrence.getConferenceId();
            reservationIdsInConference = occurrence.getReservationIdsInConference();
        }
        this.reservation.setReserveId(reservationId);
        this.reservation.setConferenceId(conferenceId);
        this.reservation.setReservationIdsInConference(reservationIdsInConference);
        TimeZoneConverter.convertToLocalTime(this.reservation, this.localTimeZone);

        // No need to pass timezone here, it would only be used for converting dayEnd and dayStart
        // properties.
        final List<RoomArrangement> rooms = this.roomArrangementDataSource.findAvailableRooms(
            this.reservation, this.numberAttendees, false, this.fixedResourceStandards,
            this.allDayEvent, false);

        // Put the available rooms in a set for quick reference.
        final Set<RoomArrangement> roomsForOccurrence = new HashSet<RoomArrangement>();
        roomsForOccurrence.addAll(rooms);

        /*
         * When editing a recurring reservation, we don't allow new conflicts. Remove rooms not
         * available for an existing non-conflicted occurrence from the results.
         */
        final Set<RoomArrangement> roomsToRemove = new HashSet<RoomArrangement>();
        // add 1 for each room not available
        for (final RoomArrangement room : this.roomArrangements) {
            if (!roomsForOccurrence.contains(room)) {
                room.setNumberOfConflicts(room.getNumberOfConflicts() + 1);
                if ((occurrence == null && !this.allowConflicts) || (occurrence != null
                        && !Constants.STATUS_ROOM_CONFLICT.equals(occurrence.getStatus()))) {
                    roomsToRemove.add(room);
                }
            }
        }

        // remove the rooms that would cause new conflicts
        this.roomArrangements.removeAll(roomsToRemove);

        return !this.roomArrangements.isEmpty();
    }

    /**
     * Get the number of occurrences handled by this occurrence action.
     *
     * @return the number of occurrences
     */
    public int getNumberOfHandledOccurrences() {
        return this.numberOfOccurrences;
    }

    /**
     * Ignore cancelled occurrences when looking for available rooms. They also don't count towards
     * the total number of dates used for handling conflicts. {@inheritDoc}
     */
    @Override
    public boolean handleCancelledOccurrence(final Date date) throws ReservationException {
        return true;
    }
}
