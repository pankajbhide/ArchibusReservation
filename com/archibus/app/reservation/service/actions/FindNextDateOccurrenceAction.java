package com.archibus.app.reservation.service.actions;

import java.util.Date;

import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.AbstractIntervalPattern.ModifiedOccurrenceAction;

/**
 * Occurrence action to determine the first date occurring on or after a given date.
 *
 * @author Yorik Gerlo
 */
public class FindNextDateOccurrenceAction implements ModifiedOccurrenceAction {

    /** The minimum date. */
    private final Date minimum;

    /**
     * The date in the pattern occurring on or after the minimum date. This is the original pattern
     * date, not the modified one if it was modified.
     */
    private Date originalNextDate;

    /**
     * The actual next date. This matches nextDate unless the occurrence originally on next date was
     * moved to a different date.
     */
    private Date actualNextDate;

    /** The number of occurrences skipped (excluding the first occurrence). */
    private int numberOfSkippedOccurrences;

    /**
     * Constructor.
     *
     * @param minimum the lower limit for the resulting date
     */
    public FindNextDateOccurrenceAction(final Date minimum) {
        this.minimum = minimum;
    }

    /**
     * Retrieve the next original occurrence date after running the action.
     *
     * @return the original next date
     */
    public Date getOriginalNextDate() {
        return this.originalNextDate;
    }

    /**
     * Get the actual next date after running the action.
     * 
     * @return the actual next date
     */
    public Date getActualNextDate() {
        return this.actualNextDate;
    }

    /**
     * Retrieve the number of skipped occurrences after running the action (this doesn't include the
     * first occurrence).
     *
     * @return the number of skipped occurrences
     */
    public int getNumberOfSkippedOccurrences() {
        return this.numberOfSkippedOccurrences;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean handleOccurrence(final Date date) throws ReservationException {
        boolean proceed = true;
        if (this.minimum.compareTo(date) <= 0) {
            this.originalNextDate = TimePeriod.clearTime(date);
            this.actualNextDate = this.originalNextDate;
            proceed = false;
        } else {
            ++this.numberOfSkippedOccurrences;
        }
        return proceed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean handleCancelledOccurrence(final Date date) throws ReservationException {
        // count cancelled occurrences also, but never stop at one
        ++this.numberOfSkippedOccurrences;
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean handleOccurrence(final Date date, final TimePeriod timePeriod)
            throws ReservationException {
        // First determine whether the modified date is in the past.
        final boolean proceed = handleOccurrence(timePeriod.getStartDate());
        if (!proceed) {
            // The modified time is not in the past, so remember the original date for the
            // recurrence pattern and remember the actual date also.
            this.originalNextDate = TimePeriod.clearTime(date);
        }
        return proceed;
    }

}
