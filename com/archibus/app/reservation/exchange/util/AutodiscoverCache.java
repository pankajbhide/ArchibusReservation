package com.archibus.app.reservation.exchange.util;

import java.util.LinkedHashMap;
import java.util.Map.Entry;

import com.archibus.app.reservation.exchange.domain.AutodiscoverResult;

/**
 * Linked hash map that removes old elements automatically when a new element is added.
 * 
 * This implementation is not synchronized.
 * 
 * @author Yorik Gerlo
 */
public class AutodiscoverCache extends LinkedHashMap<String, AutodiscoverResult> {
    
    /** Generated serial version id. */
    private static final long serialVersionUID = -8858164811141198353L;
    
    /** Initial capacity for the cache. */
    private static final int INITIAL_CAPACITY = 1000;
    
    /** Cache load factor. */
    private static final float LOAD_FACTOR = 0.75f;
    
    /** The timeout in millis after which an element may be removed. */
    private final long removeTimeout;
    
    /**
     * Constructor. Sets up the cache to use access order instead of the default ordering.
     * 
     * @param removeTimeout the timeout in millis after which an element may be removed
     */
    public AutodiscoverCache(final long removeTimeout) {
        super(INITIAL_CAPACITY, LOAD_FACTOR, true);
        this.removeTimeout = removeTimeout;
    }
    
    /**
     * Determine whether an element should be removed to replace a newly added element.
     * 
     * @param eldest the eldest element which is up for removal
     * @return true if it can be removed, false to retain it
     */
    @Override
    protected boolean removeEldestEntry(final Entry<String, AutodiscoverResult> eldest) {
        final AutodiscoverResult result = eldest.getValue();
        boolean remove = true;
        if (result != null
                && System.currentTimeMillis() - result.getDateFound().getTime() < removeTimeout) {
            remove = false;
        }
        return remove;
    }
    
}