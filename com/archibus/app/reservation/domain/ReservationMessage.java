package com.archibus.app.reservation.domain;

import javax.xml.bind.annotation.*;

/**
 * A simple message containing a subject and body.
 * 
 * @author Yorik Gerlo
 * @since 21.2
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "ReservationMessage")
public class ReservationMessage {
    
    /** The subject. */
    private String subject;
    
    /** The body. */
    private String body;
    
    /**
     * Default constructor. Create an empty message.
     */
    public ReservationMessage() {
        super();
    }
    
    /**
     * Create a new message with the given content.
     * 
     * @param subject the subject
     * @param body the body
     */
    public ReservationMessage(final String subject, final String body) {
        super();
        this.subject = subject;
        this.body = body;
    }
    
    /**
     * Get the message subject.
     * 
     * @return the subject
     */
    public String getSubject() {
        return this.subject;
    }
    
    /**
     * Get the message body.
     * 
     * @return the body
     */
    public String getBody() {
        return this.body;
    }
    
    /**
     * Set the subject.
     * 
     * @param subject the subject to set
     */
    public void setSubject(final String subject) {
        this.subject = subject;
    }
    
    /**
     * Set the body.
     * 
     * @param body the body to set
     */
    public void setBody(final String body) {
        this.body = body;
    }
    
}
