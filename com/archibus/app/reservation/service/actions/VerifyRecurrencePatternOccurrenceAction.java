package com.archibus.app.reservation.service.actions;

import java.sql.Time;
import java.util.*;

import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.AbstractIntervalPattern;

/**
 * Provides a method that verifies whether a single occurrence defined in a recurrence pattern has a
 * matching reservation.
 * <p>
 *
 * Used by ReservationService to verify whether a series of reservations linked to a recurring
 * appointment match the recurrence pattern.
 *
 * @author Yorik Gerlo
 * @since 20.1
 */
public class VerifyRecurrencePatternOccurrenceAction implements
        AbstractIntervalPattern.OccurrenceAction {
    /**
     * Start time of each occurrence.
     */
    private final Time startTime;
    
    /**
     * End time of each occurrence.
     */
    private final Time endTime;
    
    /**
     * Contains the first date without a reservation, if found.
     */
    private Date firstDateWithoutReservation;
    
    /**
     * Map of reservations linked to the recurrence series, by date.
     */
    private final Map<Date, RoomReservation> reservationMap;
    
    /**
     * Constructor.
     *
     * @param startTime start time of each reservation
     * @param endTime end time of each reservation
     * @param reservationMap date-based map of all reservations linked to the recurrence pattern
     */
    public VerifyRecurrencePatternOccurrenceAction(final Time startTime, final Time endTime,
            final Map<Date, RoomReservation> reservationMap) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.reservationMap = reservationMap;
    }
    
    /**
     * {@inheritDoc}
     */
    public boolean handleOccurrence(final Date date) throws ReservationException {
        // this tests also reservations in conflicted status
        boolean continueLoop = false;
        final RoomReservation reservation = this.reservationMap.get(date);
        if (reservation == null
                || !reservation.getStartTime().toString().equals(this.startTime.toString())
                || !reservation.getEndTime().toString().equals(this.endTime.toString())) {
            if (this.firstDateWithoutReservation == null) {
                this.firstDateWithoutReservation = date;
            }
            continueLoop = false;
        } else {
            continueLoop = true;
        }
        return continueLoop;
    }
    
    /**
     * Get the first date in the pattern without a matching reservation. Returns null if all
     * occurrences have a reservation.
     *
     * @return the first date without a matching reservation, or null
     */
    public Date getFirstDateWithoutReservation() {
        return this.firstDateWithoutReservation;
    }
}
