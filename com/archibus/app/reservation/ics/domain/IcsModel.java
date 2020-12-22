package com.archibus.app.reservation.ics.domain;

import java.util.*;

import com.archibus.app.reservation.domain.TimePeriod;
import com.archibus.app.reservation.domain.recurrence.AbstractIntervalPattern;

import net.fortuna.ical4j.model.property.Uid;

/**
 * Represents the model for the ICS object generation.
 * <p>
 *
 * @author PROCOS
 * @since 23.2
 *
 */
public final class IcsModel {

    /** The location model. */
    private final MeetingLocationModel locationModel;

    /** The email type model. */
    private final EmailModel emailModel;

    /** The summary to use. */
    private final String summary;

    /** The description to use. */
    private final String description;

    /** The from email address. */
    private final String from;

    /** The UID to use. */
    private final String uid;

    /** The list of cancelled dates from a recurring meeting. */
    private List<Date> exceptionDates;

    /** The recurrence pattern, if applicable. */
    private AbstractIntervalPattern recurrence;

    /** The date until the recurrence is active, if applicable. */
    private Date untilDate;

    /** The meeting time period. */
    private final TimePeriod timePeriod;

    /** The recurrence identifier for a single recurring meeting occurrence. */
    private Date recurrenceId;

    /**
     * The default constructor.
     *
     * @param location the location model
     * @param emailType the email type model
     * @param subject the summary
     * @param body the description
     * @param fromEmail the from email address
     * @param theUid the UID
     * @param period the time period
     */
    public IcsModel(final MeetingLocationModel location,
            final EmailModel emailType, final String subject, final String body,
            final String fromEmail, final String theUid,
            final TimePeriod period) {
        super();
        this.locationModel = location;
        this.emailModel = emailType;
        this.summary = subject;
        this.description = body;
        this.from = fromEmail;
        this.uid = theUid;
        this.timePeriod = period;
    }

    /**
     * Getter for the locationModel property.
     *
     * @return the locationModel property.
     */
    public MeetingLocationModel getLocationModel() {
        return this.locationModel;
    }

    /**
     * Getter for the summary property.
     *
     * @return the summary property.
     */
    public String getSummary() {
        return this.summary;
    }

    /**
     * Getter for the description property.
     *
     * @return the description property.
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * Getter for the from property.
     *
     * @return the from property.
     */
    public String getFrom() {
        return this.from;
    }

    /**
     * Getter for the emailModel property.
     *
     * @return the emailModel property.
     */
    public EmailModel getEmailModel() {
        return this.emailModel;
    }

    /**
     * Getter for the UID property.
     *
     * @return the UID property.
     */
    public String getUid() {
        return this.uid;
    }

    /**
     * Get the UID in ICS format.
     * @return the UID is ICS format
     */
    public Uid getIcsUid() {
        return new Uid(getUid().toString());
    }

    /**
     * Getter for the exceptionDates property.
     *
     * @return the exceptionDates property.
     */
    public List<Date> getExceptionDates() {
        return this.exceptionDates;
    }

    /**
     * Setter for the exceptionDates property.
     *
     * @param cancelledDates the exceptionDates to set.
     */
    public void setExceptionDates(final List<Date> cancelledDates) {
        this.exceptionDates = Collections.unmodifiableList(cancelledDates);
    }

    /**
     * Getter for the recurrence property.
     *
     * @return the recurrence property.
     */
    public AbstractIntervalPattern getRecurrence() {
        return this.recurrence;
    }

    /**
     * Check if is with a recurring pattern.
     *
     * @return the flag indicating if is with a recurring pattern
     */
    public boolean isRecurring() {
        return this.emailModel.isRecurring() && this.recurrence != null;
    }

    /**
     * Setter for the recurrence property.
     *
     * @param pattern the recurrence pattern to set.
     */
    public void setRecurrence(final AbstractIntervalPattern pattern) {
        this.recurrence = pattern;
    }

    /**
     * Getter for the untilDate property.
     *
     * @return the untilDate property.
     */
    public Date getUntilDate() {
        return this.untilDate;
    }

    /**
     * Setter for the untilDate property.
     *
     * @param until the untilDate to set.
     */
    public void setUntilDate(final Date until) {
        this.untilDate = until;
    }

    /**
     * Get the time period start date time.
     *
     * @return the time period start date time
     */
    public Date getStartDateTime() {
        return this.timePeriod.getStartDateTime();
    }

    /**
     * Get the time period end date time.
     *
     * @return the time period end date time
     */
    public Date getEndDateTime() {
        return this.timePeriod.getEndDateTime();
    }

    /**
     * Set the recurrence id for a single occurrence of a recurring meeting.
     *
     * @param identifier the recurrence id
     */
    public void setRecurrenceId(final Date identifier) {
        this.recurrenceId = identifier;
    }

    /**
     * Get the recurrence identifier (if applicable).
     *
     * @return the recurrence identifier
     */
    public Date getRecurrenceId() {
        return this.recurrenceId;
    }

}
