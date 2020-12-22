package com.archibus.app.reservation.exchange.service;

import java.net.URI;

import com.archibus.app.reservation.exchange.domain.AutodiscoverResult;
import com.archibus.app.reservation.exchange.util.AutodiscoverCache;

/**
 * Caching service for determining Exchange Web Services endpoint per mailbox. This should be
 * configured as a singleton bean. This implementation is thread-safe.
 * 
 * @author Yorik Gerlo
 */
public class AutodiscoverCachingService {
    
    /**
     * The number of milliseconds after which a cached auto-discover result is considered for
     * removal.
     */
    private static final long REMOVE_MILLIS = 36 * 60 * 60 * 1000;
    
    /** The auto discover cache in a map that automatically removes old elements. */
    private final AutodiscoverCache cache = new AutodiscoverCache(REMOVE_MILLIS);
    
    /**
     * Get a cached result for the given email.
     * 
     * @param email the email to get the result for
     * @return the cached result, or null if not found
     */
    public AutodiscoverResult get(final String email) {
        synchronized (this) {
            return (AutodiscoverResult) cache.get(email);
        }
    }
    
    /**
     * Store an auto-discover result in the cache.
     * 
     * @param email the email
     * @param url the URI pointing to the Exchange Web Service
     */
    public void put(final String email, final URI url) {
        final AutodiscoverResult result = new AutodiscoverResult(url);
        synchronized (this) {
            cache.put(email, result);
        }
    }
    
}
