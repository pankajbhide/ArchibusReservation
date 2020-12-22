package com.archibus.app.reservation.domain;

import java.util.SortedSet;

import com.archibus.app.reservation.domain.recurrence.Recurrence;

/**
 * The Interface ICalendarEvent.
 */
public interface ICalendarEvent extends ITimePeriodBased {
    
    /**
     * Gets the unique event id.
     * 
     * @return the event id
     */
    String getEventId();
    
    /**
     * Sets the event id.
     * 
     * @param eventId the new event id
     */
    void setEventId(String eventId);
    
    /**
     * Gets the location.
     * 
     * @return the location
     */
    String getLocation();
    
    /**
     * Sets the location.
     * 
     * @param location the new location
     */
    void setLocation(String location);
    
    /**
     * Gets the subject.
     * 
     * @return the subject
     */
    String getSubject();
    
    /**
     * Sets the subject.
     * 
     * @param subject the new subject
     */
    void setSubject(String subject);
    
    /**
     * Checks if is recurrent.
     * 
     * @return true, if is recurrent
     */
    boolean isRecurrent();
    
    /**
     * Sets the recurrent.
     * 
     * @param recurrent the new recurrent
     */
    void setRecurrent(boolean recurrent);
    
    /**
     * Checks if is confidential.
     * 
     * @return true, if is confidential
     */
    boolean isConfidential();
    
    /**
     * Sets the confidential.
     * 
     * @param confidential the new confidential
     */
    void setConfidential(boolean confidential);
    
    /**
     * Gets the status.
     * 
     * @return the status
     */
    String getStatus();
    
    /**
     * Sets the status.
     * 
     * @param status the new status
     */
    void setStatus(String status);
    
    /**
     * Gets the recurrence.
     * 
     * @return the recurrence
     */
    Recurrence getRecurrence();
    
    /**
     * Sets the recurrence.
     * 
     * @param recurrence the new recurrence
     */
    void setRecurrence(Recurrence recurrence);
    
    /**
     * Gets the email addresses.
     * 
     * @return the email addresses
     */
    SortedSet<String> getEmailAddresses();
    
    /**
     * Sets the email addresses.
     * 
     * @param emailAddresses the new email addresses
     */
    void setEmailAddresses(SortedSet<String> emailAddresses);
    
    /**
     * Gets the body.
     * 
     * @return the body
     */
    String getBody();
    
    /**
     * Sets the body.
     * 
     * @param body the new body
     */
    void setBody(String body);
    
    /**
     * Gets the organizer email.
     * 
     * @return the organizer email
     */
    String getOrganizerEmail();
    
    /**
     * Sets the organizer email.
     * 
     * @param organizerEmail the new organizer email
     */
    void setOrganizerEmail(String organizerEmail);
    
}