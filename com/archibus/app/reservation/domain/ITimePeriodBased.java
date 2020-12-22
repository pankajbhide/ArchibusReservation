package com.archibus.app.reservation.domain;

import java.sql.Time;
import java.util.Date;

/**
 * Interface for domain objects based on a time period.
 * 
 * @author Yorik Gerlo
 * 
 */
public interface ITimePeriodBased {
    
    /**
     * Gets the end date.
     * 
     * @return the end date
     */
    Date getEndDate();
    
    /**
     * Gets the end time.
     * 
     * @return the end time
     */
    Time getEndTime();
    
    /**
     * Gets the start date.
     * 
     * @return the start date
     */
    Date getStartDate();
    
    /**
     * Gets the start time.
     * 
     * @return the start time
     */
    Time getStartTime();
    
    /**
     * Sets the end date.
     * 
     * @param endDate the new end date
     */
    void setEndDate(final Date endDate);
    
    /**
     * Sets the end time.
     * 
     * @param endTime the new end time
     */
    void setEndTime(final Time endTime);
    
    /**
     * Sets the start date.
     * 
     * @param startDate the new start date
     */
    void setStartDate(final Date startDate);
    
    /**
     * Sets the start time.
     * 
     * @param startTime the new start time
     */
    void setStartTime(final Time startTime);
    
    /**
     * Sets the start date and time of the allocation.
     * 
     * @param startDateTime the new start date/time
     */
    void setStartDateTime(final Date startDateTime);
    
    /**
     * Sets the end date and time of the allocation.
     * 
     * @param endDateTime the new end date/time
     */
    void setEndDateTime(final Date endDateTime);
    
    /**
     * Gets the start date and time.
     * 
     * @return the start date/time stamp.
     */
    Date getStartDateTime();
    
    /**
     * Gets the end date and time.
     * 
     * @return the end date/time stamp.
     */
    Date getEndDateTime();
    
    /**
     * Get the time zone identifier.
     * 
     * @return the time zone identifier
     */
    String getTimeZone();
    
    /**
     * Set the time zone identifier.
     * 
     * @param timeZone the new time zone identifier
     */
    void setTimeZone(String timeZone);
    
}