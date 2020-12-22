package com.archibus.app.reservation.domain;

/**
 * Attendee response responseStatus information.
 * 
 * @author Yorik Gerlo
 * @since 21.2
 */
public class AttendeeResponseStatus {
    
    /**
     * Possible values for the response responseStatus.
     */
    public enum ResponseStatus {        
        /** The Unknown. */
        Unknown, 
        /** The Tentative. */
        Tentative, 
        /** The Accepted. */
        Accepted, 
        /** The Declined. */
        Declined
    }
    
    /** Attendee name. */
    private String name;
    
    /** Attendee email address. */
    private String email;
    
    /** Attendee response responseStatus. */
    private ResponseStatus responseStatus;
    
    /**
     * Default constructor.
     */
    public AttendeeResponseStatus() {
        super();
    }
    
    /**
     * Constructor with initialization.
     * 
     * @param name attendee name
     * @param email attendee email address
     * @param responseStatus attendee response status
     */
    public AttendeeResponseStatus(final String name, final String email,
            final ResponseStatus responseStatus) {
        this.setName(name);
        this.setEmail(email);
        this.setResponseStatus(responseStatus);
    }
    
    /**
     * Get the email.
     * 
     * @return the email
     */
    public String getEmail() {
        return this.email;
    }
    
    /**
     * Get the response responseStatus.
     * 
     * @return the responseStatus
     */
    public ResponseStatus getResponseStatus() {
        return this.responseStatus;
    }
    
    /**
     * Set the email.
     * 
     * @param email the email to set
     */
    public final void setEmail(final String email) {
        this.email = email;
    }
    
    /**
     * Set the response status.
     *
     * @param status the new response status
     */
    public final void setResponseStatus(final ResponseStatus status) {
        this.responseStatus = status;
    }
    
    /**
     * Get the attendee name.
     * 
     * @return the name
     */
    public String getName() {
        return this.name;
    }
    
    /**
     * Set the attendee name.
     * 
     * @param name the name to set
     */
    public final void setName(final String name) {
        this.name = name;
    }
    
}
