package com.archibus.app.reservation.domain.recurrence;

import java.util.*;

import javax.xml.bind.annotation.*;

import com.archibus.app.common.recurring.*;


/**
 * Weekly pattern for recurring reservations.
 * 
 * @author Bart Vanderschoot
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "WeeklyPattern")
public class WeeklyPattern extends AbstractIntervalPattern {
    
    /** The days of the week. */
    private List<DayOfTheWeek> daysOfTheWeek = new ArrayList<DayOfTheWeek>();
    
    /**
     * Default constructor.
     */
    public WeeklyPattern() {
        super();
    }
    
    /**
     * Constructor using parameters.
     * 
     * @param startDate start date
     * @param endDate end date
     * @param interval interval
     * @param daysOfTheWeek days of the week
     */
    public WeeklyPattern(final Date startDate, final Date endDate, final int interval,
            final List<DayOfTheWeek> daysOfTheWeek) {
        super(startDate, endDate, interval);
        this.daysOfTheWeek = daysOfTheWeek;
    }
    
    /**
     * Constructor using parameters.
     * 
     * @param startDate start date
     * @param interval interval
     */
    public WeeklyPattern(final Date startDate, final int interval) {
        super(startDate, interval);
    }
    
    /**
     * Constructor using parameters.
     * 
     * @param startDate start date start date
     * @param interval interval interval
     * @param daysOfTheWeeks the days of the weeks
     */
    public WeeklyPattern(final Date startDate, final int interval,
            final DayOfTheWeek... daysOfTheWeeks) {
        super(startDate, interval);
        
        for (final DayOfTheWeek day : daysOfTheWeeks) {
            this.daysOfTheWeek.add(day);
        }
    }
    
    /**
     * Constructor using parameters.
     * 
     * @param startDate start date
     * @param interval interval
     * @param daysOfTheWeek days of the week
     */
    public WeeklyPattern(final Date startDate, final int interval,
            final List<DayOfTheWeek> daysOfTheWeek) {
        super(startDate, interval);
        this.daysOfTheWeek = daysOfTheWeek;
    }
    
    /**
     * Get selected days of the week.
     * 
     * @return days of the week
     */
    public final List<DayOfTheWeek> getDaysOfTheWeek() {
        return this.daysOfTheWeek;
    }
    
    /**
     * Set days selected of the week.
     * 
     * @param daysOfTheWeek days of the week
     */
    public final void setDaysOfTheWeek(final List<DayOfTheWeek> daysOfTheWeek) {
        this.daysOfTheWeek = daysOfTheWeek;
    }
    
    /**
     * Create XML string for recurring rule.
     * 
     * @return xml string
     */
    @Override
    public final String toString() {
        final StringBuffer daysOfWeek = new StringBuffer();
        for (final DayOfTheWeek dayOfTheWeek : getDaysOfTheWeekWithoutSpecials()) {
            daysOfWeek.append(dayOfTheWeek.getValue());
        }
        
        return RecurringScheduleService.getRecurrenceXMLPattern(RecurringSchedulePattern.TYPE_WEEK,
            getInterval(), getTotalOccurrences(), daysOfWeek.toString(), -1, -1, -1);
    }
    
    /**
     * Get the days of the week that are part of the recurrence pattern. Convert specials such as
     * 'any week day' to a list that contains only real week days.
     * 
     * @return the list of days of the week in the pattern, without specials
     */
    private List<DayOfTheWeek> getDaysOfTheWeekWithoutSpecials() {
        final List<DayOfTheWeek> dayList = getDaysOfTheWeek();
        // Check for three special cases. If any of those is present,
        // the rest is removed!
        if (dayList.contains(DayOfTheWeek.Day)) {
            dayList.clear();
            // add every day to the list
            dayList.add(DayOfTheWeek.Monday);
            dayList.add(DayOfTheWeek.Tuesday);
            dayList.add(DayOfTheWeek.Wednesday);
            dayList.add(DayOfTheWeek.Thursday);
            dayList.add(DayOfTheWeek.Friday);
            dayList.add(DayOfTheWeek.Saturday);
            dayList.add(DayOfTheWeek.Sunday);
        } else if (dayList.contains(DayOfTheWeek.Weekday)) {
            dayList.clear();
            
            // add every weekday to the list
            dayList.add(DayOfTheWeek.Monday);
            dayList.add(DayOfTheWeek.Tuesday);
            dayList.add(DayOfTheWeek.Wednesday);
            dayList.add(DayOfTheWeek.Thursday);
            dayList.add(DayOfTheWeek.Friday);
        } else if (dayList.contains(DayOfTheWeek.WeekendDay)) {
            dayList.clear();
            
            // add every weekend day to the list separately
            dayList.add(DayOfTheWeek.Saturday);
            dayList.add(DayOfTheWeek.Sunday);
        }
        return dayList;
    }
    
}
