package com.archibus.app.reservation.domain.recurrence;

import java.util.Date;

import javax.xml.bind.annotation.XmlTransient;

import com.archibus.app.reservation.domain.TimePeriod;

/**
 * Represents relevant information for a modified occurrence in a recurring reservation.
 * <p>
 * Used by Reservation Service to handle modified occurrences and skip cancelled occurrences.
 *
 * @author Yorik Gerlo
 * @since 22.1
 */
public class OccurrenceInfo {

    /** The original date of the occurrence. */
    private Date originalDate;

    /** Indicates whether the occurrence is cancelled. */
    private boolean cancelled;

    /** Indicates a modified time period for the occurrence. */
    private TimePeriod modifiedTimePeriod = new TimePeriod();

    /**
     * Getter for the originalDate property.
     *
     * @see originalDate
     * @return the originalDate property.
     */
    public Date getOriginalDate() {
        return this.originalDate;
    }

    /**
     * Setter for the originalDate property.
     *
     * @see originalDate
     * @param originalDate the originalDate to set
     */
    public void setOriginalDate(final Date originalDate) {
        this.originalDate = originalDate;
    }

    /**
     * Getter for the cancelled property.
     *
     * @see cancelled
     * @return the cancelled property.
     */
    public boolean isCancelled() {
        return this.cancelled;
    }

    /**
     * Setter for the cancelled property.
     *
     * @see cancelled
     * @param cancelled the cancelled to set
     */
    public void setCancelled(final boolean cancelled) {
        this.cancelled = cancelled;
    }

    /**
     * Getter for the modifiedTimePeriod property.
     *
     * @see modifiedTimePeriod
     * @return the modifiedTimePeriod property.
     */
    @XmlTransient
    public TimePeriod getModifiedTimePeriod() {
        return this.modifiedTimePeriod;
    }

    /**
     * Setter for the modifiedTimePeriod property.
     *
     * @see modifiedTimePeriod
     * @param modifiedTimePeriod the modifiedTimePeriod to set
     */
    public void setModifiedTimePeriod(final TimePeriod modifiedTimePeriod) {
        this.modifiedTimePeriod = modifiedTimePeriod;
    }

    /**
     * Get the current start date time for the occurrence.
     *
     * @return current start date time
     */
    public Date getModifiedStartDateTime() {
        return this.modifiedTimePeriod.getStartDateTime();
    }

    /**
     * Set the current start date time for the occurrence.
     *
     * @param modifiedStartDateTime the current start date time
     */
    public void setModifiedStartDateTime(final Date modifiedStartDateTime) {
        this.modifiedTimePeriod.setStartDateTime(modifiedStartDateTime);
    }

    /**
     * Get the current end date time for the occurrence.
     *
     * @return the current end date time
     */
    public Date getModifiedEndDateTime() {
        return this.modifiedTimePeriod.getEndDateTime();
    }

    /**
     * Set the current end date time for the occurrence.
     *
     * @param modifiedEndDateTime the current end date time
     */
    public void setModifiedEndDateTime(final Date modifiedEndDateTime) {
        this.modifiedTimePeriod.setEndDateTime(modifiedEndDateTime);
    }

    /**
     * Get the time zone identifier.
     *
     * @return the time zone identifier
     */
    public String getTimeZone() {
        return this.modifiedTimePeriod.getTimeZone();
    }

    /**
     * Set the time zone identifier.
     *
     * @param timeZone the new time zone identifier
     */
    public void setTimeZone(final String timeZone) {
        this.modifiedTimePeriod.setTimeZone(timeZone);
    }
}
