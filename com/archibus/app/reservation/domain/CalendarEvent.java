package com.archibus.app.reservation.domain;

import java.sql.Time;
import java.util.*;

import com.archibus.app.reservation.domain.recurrence.Recurrence;

/**
 * The Class CalendarEvent.
 */
public class CalendarEvent implements ICalendarEvent {
    
    /** The event id. */
    private String eventId;
    
    /** The time period. */
    private final TimePeriod timePeriod = new TimePeriod();
    
    /** The location. */
    private String location;
    
    /** The subject. */
    private String subject;
    
    /** The recurrent. */
    private boolean recurrent;
    
    /** The confidential. */
    private boolean confidential;
    
    /** The status. */
    private String status;
    
    /** Email address of the organizer. */
    private String organizerEmail;
    
    /** The body message. */
    private String body;
    
    /** The recurrence. */
    private Recurrence recurrence;
    
    /** The email addresses. */
    private SortedSet<String> emailAddresses;
    
    /**
     * {@inheritDoc}
     */
    public String getLocation() {
        return location;
    }
    
    /**
     * {@inheritDoc}
     */
    public void setLocation(final String location) {
        this.location = location;
    }
    
    /**
     * {@inheritDoc}
     */
    public String getSubject() {
        return subject;
    }
    
    /**
     * {@inheritDoc}
     */
    public void setSubject(final String subject) {
        this.subject = subject;
    }
    
    /**
     * {@inheritDoc}
     */
    public String getEventId() {
        return eventId;
    }
    
    /**
     * {@inheritDoc}
     */
    public void setEventId(final String eventId) {
        this.eventId = eventId;
    }
    
    /**
     * {@inheritDoc}
     */
    public Date getStartDate() {
        return this.timePeriod.getStartDate();
    }
    
    /**
     * {@inheritDoc}
     */
    public void setStartDate(final Date startDate) {
        this.timePeriod.setStartDate(startDate);
    }
    
    /**
     * {@inheritDoc}
     */
    public Date getEndDate() {
        return this.timePeriod.getEndDate();
    }
    
    /**
     * {@inheritDoc}
     */
    public void setEndDate(final Date endDate) {
        this.timePeriod.setEndDate(endDate);
    }
    
    /**
     * {@inheritDoc}
     */
    public Time getStartTime() {
        return this.timePeriod.getStartTime();
    }
    
    /**
     * {@inheritDoc}
     */
    public void setStartTime(final Time startTime) {
        this.timePeriod.setStartTime(startTime);
    }
    
    /**
     * {@inheritDoc}
     */
    public Time getEndTime() {
        return this.timePeriod.getEndTime();
    }
    
    /**
     * {@inheritDoc}
     */
    public void setEndTime(final Time endTime) {
        this.timePeriod.setEndTime(endTime);
    }
    
    /**
     * {@inheritDoc}
     */
    public boolean isRecurrent() {
        return recurrent;
    }
    
    /**
     * {@inheritDoc}
     */
    public void setRecurrent(final boolean recurrent) {
        this.recurrent = recurrent;
    }
    
    /**
     * {@inheritDoc}
     */
    public boolean isConfidential() {
        return confidential;
    }
    
    /**
     * {@inheritDoc}
     */
    public void setConfidential(final boolean confidential) {
        this.confidential = confidential;
    }
    
    /**
     * Gets the status.
     * 
     * @return the status
     */
    public String getStatus() {
        return status;
    }
    
    /**
     * Sets the status.
     * 
     * @param status the new status
     */
    public void setStatus(final String status) {
        this.status = status;
    }
    
    /**
     * Gets the recurrence.
     * 
     * @return the recurrence
     */
    public Recurrence getRecurrence() {
        return recurrence;
    }
    
    /**
     * Sets the recurrence.
     * 
     * @param recurrence the new recurrence
     */
    public void setRecurrence(final Recurrence recurrence) {
        this.recurrence = recurrence;
    }
    
    /**
     * Gets the email addresses.
     * 
     * @return the email addresses
     */
    public SortedSet<String> getEmailAddresses() {
        return emailAddresses;
    }
    
    /**
     * Sets the email addresses.
     * 
     * @param emailAddresses the new email addresses
     */
    public void setEmailAddresses(final SortedSet<String> emailAddresses) {
        this.emailAddresses = emailAddresses;
    }
    
    /**
     * Gets the body.
     * 
     * @return the body
     */
    public String getBody() {
        return body;
    }
    
    /**
     * Sets the body.
     * 
     * @param body the new body
     */
    public void setBody(final String body) {
        this.body = body;
    }
    
    /**
     * {@inheritDoc}
     */
    public String getOrganizerEmail() {
        return organizerEmail;
    }
    
    /**
     * {@inheritDoc}
     */
    public void setOrganizerEmail(final String organizerEmail) {
        this.organizerEmail = organizerEmail;
    }
    
    /**
     * Get the start date/time. This is null if either start date or start time is null.
     * 
     * @return start date/time.
     */
    public final Date getStartDateTime() {
        return this.timePeriod.getStartDateTime();
    }
    
    /**
     * Get the end date/time. This is null if either end date or end time is null.
     * 
     * @return end date/time.
     */
    public final Date getEndDateTime() {
        return this.timePeriod.getEndDateTime();
    }
    
    /**
     * {@inheritDoc}
     */
    public String getTimeZone() {
        return this.timePeriod.getTimeZone();
    }
    
    /**
     * {@inheritDoc}
     */
    public void setTimeZone(final String timeZone) {
        this.timePeriod.setTimeZone(timeZone);
    }
    
    /** {@inheritDoc} */
    public void setStartDateTime(final Date startDateTime) {
        this.timePeriod.setStartDateTime(startDateTime);
    }
    
    /** {@inheritDoc} */
    public void setEndDateTime(final Date endDateTime) {
        this.timePeriod.setEndDateTime(endDateTime);
    }
    
}
