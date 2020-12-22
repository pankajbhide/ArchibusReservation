package com.archibus.app.reservation.service.mobile.impl;

import static com.archibus.app.common.mobile.util.FieldNameConstantsCommon.*;

import java.sql.Time;
import java.text.*;
import java.util.*;

import org.apache.log4j.Logger;

import com.archibus.app.reservation.domain.TimePeriod;

/**
 * 
 * Utility class. Provides methods related with data sources for Workplace Services Portal mobile
 * services, Reservations module.
 * 
 * @author Cristina Moldovan
 * @since 21.2
 * 
 */
public class DateAndTimeUtilities {
    /**
     * Date formatter.
     */
    protected final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd",
        Locale.ENGLISH);
    
    /**
     * Time formatter.
     */
    protected final SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm", Locale.ENGLISH);
    
    /** The logger. */
    private final Logger logger = Logger.getLogger(this.getClass());
    
    /**
     * Default constructor.
     */
    DateAndTimeUtilities() {
        // default constructor
    }
    
    /**
     * Finds available rooms.
     * 
     * @param requestParameters request parameters
     * @return list of room arrangements
     */
    protected TimePeriod createTimePeriod(final Map<String, Object> requestParameters) {
        TimePeriod timePeriod = null;
        
        Date dayStart;
        Time startTime;
        Time endTime;
        try {
            dayStart = createDate(requestParameters.get(DAY_START).toString());
            startTime = createTime(requestParameters.get(TIME_START).toString());
            endTime = createTime(requestParameters.get(TIME_END).toString());
            timePeriod = new TimePeriod(dayStart, dayStart, startTime, endTime);
            
        } catch (final ParseException e) {
            this.logger.error("Invalid time", e);
        }
        
        return timePeriod;
    }
    
    /**
     * Create a time object representing the given time string.
     * 
     * @param formattedDate the time as yyyy-MM-dd
     * @return the date object
     * @throws ParseException when the parameter is an invalid date
     */
    protected Date createDate(final String formattedDate) throws ParseException {
        return new Date(this.dateFormatter.parse(formattedDate).getTime());
    }
    
    /**
     * Create a time object representing the given time string.
     * 
     * @param formattedTime the time as HH:MM
     * @return the time object
     * @throws ParseException when the parameter is an invalid time
     */
    protected Time createTime(final String formattedTime) throws ParseException {
        return new Time(this.timeFormatter.parse(formattedTime).getTime());
    }
    
    /**
     * Converts a Date into a String "yyyy-MM-dd".
     * 
     * @param date the date
     * @return the string
     */
    protected String formatDate(final Date date) {
        return this.dateFormatter.format(date);
    }
    
    /**
     * Converts a Date into a String "HH:mm".
     * 
     * @param time the time
     * @return the string
     */
    protected String formatTime(final Time time) {
        return this.timeFormatter.format(time);
    }
}
