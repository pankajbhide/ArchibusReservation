package com.archibus.app.reservation.domain;

import java.util.List;

/**
 * The Attendee Availability will be used in the free/busy view.
 * 
 * For a specific employee and time period, it will show all his calendar events. Free/busy can be
 * obtained from Exchange.
 * 
 * @author Bart Vanderschoot
 */
public class AttendeeAvailability {
    
    /**
     * Calendar events registered in Exchange.
     */
    private List<ICalendarEvent> calendarEvents;
    
    /** The email. */
    private String email;
    
    /** Set to true when information was successfully retrieved, false otherwise. */
    private boolean succesfull;
    
    /** Contains error details in case of failure. */
    private String errorDetails;
    
    /**
     * Default constructor.
     */
    public AttendeeAvailability() {
        // default constructor
    }
    
    /**
     * Constructor to use when free-busy information was successfully retrieved.
     * 
     * @param email the email
     * @param calendarEvents the calendar events
     */
    public AttendeeAvailability(final String email, final List<ICalendarEvent> calendarEvents) {
        this.email = email;
        this.succesfull = true;
        this.calendarEvents = calendarEvents;
    }
    
    /**
     * Constructor to use when no free-busy info could be retrieved.
     * 
     * @param email the email
     * @param errorDetails error details
     */
    public AttendeeAvailability(final String email, final String errorDetails) {
        this.email = email;
        this.succesfull = false;
        this.errorDetails = errorDetails;
    }
    
    /**
     * Gets the calendar events.
     * 
     * @return the calendar events
     */
    public final List<ICalendarEvent> getCalendarEvents() {
        return this.calendarEvents;
    }
    
    /**
     * Gets the attendee email.
     * 
     * @return the email
     */
    public final String getEmail() {
        return this.email;
    }
    
    /**
     * Get the error details in case of failure.
     * @return the error details
     */
    public String getErrorDetails() {
        return this.errorDetails;
    }
    
    /**
     * Indicates whether the free-busy information was successfully retrieved or not.
     * 
     * @return true if successful, false otherwise
     */
    public boolean isSuccessful() {
        return this.succesfull;
    }
    
    /**
     * Sets the calendar events.
     * 
     * @param calendarEvents the new calendar events
     */
    public final void setCalendarEvents(final List<ICalendarEvent> calendarEvents) {
        this.calendarEvents = calendarEvents;
    }
    
    /**
     * Sets the email address of the attendee.
     * 
     * @param email the email address
     */
    public final void setEmail(final String email) {
        this.email = email;
    }
    
    /**
     * Set the boolean which indicates the free-busy was retrieved without errors.
     * @param successful true if successful, false on error
     */
    public void setSuccessful(final boolean successful) {
        this.succesfull = successful;
    }
    
    /**
     * Set the error details.
     * @param errorDetails the error details
     */
    public void setErrorDetails(final String errorDetails) {
        this.errorDetails = errorDetails;
    }
    
}
