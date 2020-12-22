package com.archibus.app.reservation.domain.recurrence;

import java.util.*;

import javax.xml.bind.annotation.*;

import com.archibus.app.reservation.domain.TimePeriod;

/**
 * Recurrence base class.
 *
 * @author Bart Vanderschoot
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "Recurrence")
public class Recurrence {

    /** The number of occurrences. */
    private Integer numberOfOccurrences;

    /** The number of skipped occurrences (if applicable). */
    private int numberOfSkippedOccurrences;

    /** The start date. */
    private Date startDate;

    /** The end date. */
    private Date endDate;

    /**
     * Set of cancelled occurrence dates in the time zone of the occurrence pattern (without time).
     */
    private Set<Date> cancelledOccurrences;

    /**
     * Map of modified occurrences including the modified time period. The original start date of
     * each occurrence (without time component) in the recurrence pattern time zone is the key.
     */
    private Map<Date, TimePeriod> modifiedOccurrences;

    /**
     * Contains information on all modified occurrences. This is what's actually received from the
     * Outlook plugin.
     */
    private List<OccurrenceInfo> exceptions;

    /**
     * Get end date.
     *
     * @return end date
     */
    @XmlElement(nillable = true)
    public final Date getEndDate() {
        return this.endDate;
    }

    /**
     * Get the modified occurrences info.
     *
     * @return the modified occurrences info.
     */
    public List<OccurrenceInfo> getExceptions() {
        return this.exceptions;
    }

    /**
     * Get number of occurrences.
     *
     * @return number of occurrences
     */
    public final Integer getNumberOfOccurrences() {
        return this.numberOfOccurrences;
    }

    /**
     * Get number of skipped occurrences.
     *
     * @return number of skipped occurrences
     */
    @XmlTransient
    public final int getNumberOfSkippedOccurrences() {
        return this.numberOfSkippedOccurrences;
    }

    /**
     * Gets the start date of the recurrence pattern.
     *
     * @return the start date
     */
    public final Date getStartDate() {
        return this.startDate;
    }

    /**
     * Sets the end date.
     *
     * @param endDate the new end date
     */
    public final void setEndDate(final Date endDate) {
        this.endDate = endDate;
    }

    /**
     * Set the modified occurrences info.
     *
     * @param exceptions the modified occurrences info
     */
    public void setExceptions(final List<OccurrenceInfo> exceptions) {
        this.exceptions = exceptions;

        // update the derived properties
        if (exceptions == null) {
            if (this.cancelledOccurrences != null) {
                this.cancelledOccurrences.clear();
            }
            if (this.modifiedOccurrences != null) {
                this.modifiedOccurrences.clear();
            }
        } else {
            this.cancelledOccurrences = new HashSet<Date>();
            this.modifiedOccurrences = new HashMap<Date, TimePeriod>();
            for (final OccurrenceInfo info : exceptions) {
                if (info.isCancelled()) {
                    this.cancelledOccurrences.add(TimePeriod.clearTime(info.getOriginalDate()));
                } else {
                    this.modifiedOccurrences.put(TimePeriod.clearTime(info.getOriginalDate()),
                        info.getModifiedTimePeriod());
                }
            }
        }
    }

    /**
     * Sets the start date.
     *
     * @param startDate the new start date
     */
    public final void setStartDate(final Date startDate) {
        this.startDate = startDate;
    }

    /**
     * Sets the number of occurrences.
     *
     * @param numberOfOccurrences the new number of occurrences
     */
    public final void setNumberOfOccurrences(final Integer numberOfOccurrences) {
        this.numberOfOccurrences = numberOfOccurrences;
    }

    /**
     * Sets the number of skipped occurrences.
     *
     * @param numberOfSkippedOccurrences the number of skipped occurrences
     */
    public final void setNumberOfSkippedOccurrences(final int numberOfSkippedOccurrences) {
        this.numberOfSkippedOccurrences = numberOfSkippedOccurrences;
    }

    /**
     * Check whether the given date is marked cancelled.
     *
     * @param date the date to check
     * @return true if it's cancelled, false otherwise
     */
    public boolean isDateCancelled(final Date date) {
        return this.cancelledOccurrences != null && this.cancelledOccurrences.contains(date);
    }

    /**
     * Check whether a modified time period is defined for the given date of the recurrence pattern.
     *
     * @param date the date to check
     * @return the modified time period, or null if not defined
     */
    public TimePeriod getModifiedTimePeriod(final Date date) {
        TimePeriod modifiedTimePeriod = null;
        if (this.modifiedOccurrences != null) {
            modifiedTimePeriod = this.modifiedOccurrences.get(date);
        }
        return modifiedTimePeriod;
    }

    /**
     * Get a non-null representation of the number of occurrences.
     *
     * @return number of occurrences, or 0 if null
     */
    protected int getTotalOccurrences() {
        return getNumberOfOccurrences() == null ? 0 : getNumberOfOccurrences();
    }

}
