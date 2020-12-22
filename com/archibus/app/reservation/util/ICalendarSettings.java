package com.archibus.app.reservation.util;

/**
 * Common interface for the calendar service helper.
 * 
 * @author Yorik Gerlo
 * @since 21.2
 */
public interface ICalendarSettings {
    
    /**
     * Get the resource account that should be added to all calendar events to track changes.
     * 
     * @return the resource account, or null if no such account is configured
     */
    String getResourceAccount();
    
}
