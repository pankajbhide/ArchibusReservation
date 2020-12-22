package com.archibus.app.reservation.exchange.domain;

import java.net.URI;
import java.util.Date;

/**
 * Auto discover result used for caching EWS endpoint url's.
 * @author Yorik Gerlo
 */
public class AutodiscoverResult {

    /** The date/time when this result was found. */
    private final Date dateFound;
    
    /** The URI for the discovered endpoint. */
    private final URI url;
    
    /** Indicates whether the result has been verified to be working. */
    private boolean verified;
    
    /**
     * Create an autodiscover result with the given URL.
     * @param url the url
     */
    public AutodiscoverResult(final URI url) {
        this.dateFound = new Date();
        this.url = url;
    }
    
    /**
     * Get the date/time when this result was found.
     * @return the date/time
     */
    public Date getDateFound() {
        return dateFound;
    }

    /**
     * Get the url.
     * @return the url
     */
    public URI getUrl() {
        return url;
    }

    /**
     * Check whether this result has been verified.
     * @return the verified
     */
    public boolean isVerified() {
        return verified;
    }

    /**
     * Set verification status.
     * @param verified the status to set
     */
    public void setVerified(final boolean verified) {
        this.verified = verified;
    }
    
}
