package com.archibus.app.reservation.domain.recurrence;

import java.util.Date;

import javax.xml.bind.annotation.*;

import com.archibus.app.common.recurring.*;

/**
 * Daily pattern for recurring reservations.
 * 
 * @author Bart Vanderschoot
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "DailyPattern")
public class DailyPattern extends AbstractIntervalPattern {
    
    /**
     * Default constructor.
     */
    public DailyPattern() {
        super();
    }
    
    /**
     * Constructor using parameters.
     * 
     * @param startDate start date
     * @param endDate end date
     * @param interval interval
     */
    public DailyPattern(final Date startDate, final Date endDate, final int interval) {
        super(startDate, endDate, interval);
    }
    
    /**
     * Constructor using parameters.
     * 
     * @param startDate start date
     * @param endDate end date
     * @param interval interval
     * @param numberOfOccurrences number of occurrences
     */
    public DailyPattern(final Date startDate, final Date endDate, final int interval, final int numberOfOccurrences) {
        super(startDate, endDate, interval);
        setNumberOfOccurrences(numberOfOccurrences);
    }
    
    /**
     * Constructor using parameters.
     * 
     * @param startDate start date
     * @param interval interval
     */
    public DailyPattern(final Date startDate, final int interval) {
        super(startDate, interval);
    }
    
    /**
     * Constructor using parameters.
     *
     * @param startDate start date
     * @param interval interval
     * @param numberOfOccurrences the number of occurrences
     */
    public DailyPattern(final Date startDate, final int interval, final int numberOfOccurrences) {
        super(startDate, interval);
        setNumberOfOccurrences(numberOfOccurrences);
    }
    
    /**
     * Create XML string for recurring rule.
     * 
     * @return xml string
     */
    @Override
    public final String toString() {
        return RecurringScheduleService.getRecurrenceXMLPattern(RecurringSchedulePattern.TYPE_DAY,
            getInterval(), getTotalOccurrences(), null, -1, -1, -1);
    }
    
}
