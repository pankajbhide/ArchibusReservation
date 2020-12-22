package com.archibus.app.reservation.domain.recurrence;

import java.util.*;

import com.archibus.app.common.recurring.*;
import com.archibus.app.reservation.domain.ReservationException;
import com.archibus.app.reservation.service.RecurrenceService;
import com.archibus.utility.StringUtil;

/**
 * Recurrence factory for parsing recurrence patterns.
 *
 * @author Yorik Gerlo
 */
public final class RecurrenceParser {
    
    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private RecurrenceParser() {
    }
    
    /**
     * Parse XML string to return an recurrence object.
     *
     * @param startDate start date
     * @param endDate end date
     * @param xmlPattern XML pattern
     *
     * @return Recurrence object
     *
     * @throws ReservationException reservation exception
     */
    public static Recurrence parseRecurrence(final Date startDate, final Date endDate,
            final String xmlPattern) throws ReservationException {
        // parse the xml
        final RecurringScheduleService recurringPattern =
                RecurrenceService.newRecurringScheduleService();
        recurringPattern.setRecurringSchedulePattern(startDate, endDate, xmlPattern);
        
        // convert to Recurrence object
        Recurrence result = null;
        if (recurringPattern.getRecurringType().equals(RecurringSchedulePattern.TYPE_DAY)) {
            result =
                    new DailyPattern(startDate, endDate, recurringPattern.getInterval(),
                        recurringPattern.getTotal());
            
        } else if (recurringPattern.getRecurringType().equals(RecurringSchedulePattern.TYPE_WEEK)) {
            // <recurring type="week" value1="1,1,0,0,0,1,0" value2="1" value3="" total="5"/>
            result = getWeeklyPattern(startDate, endDate, recurringPattern);
            
        } else if (recurringPattern.getRecurringType().equals(RecurringSchedulePattern.TYPE_MONTH)) {
            result = getMonthlyPattern(startDate, endDate, recurringPattern);
            
        } else if (recurringPattern.getRecurringType().equals(RecurringSchedulePattern.TYPE_YEAR)) {
            result = getYearlyPattern(startDate, endDate, recurringPattern);
        } else if (recurringPattern.getRecurringType().equals(RecurringSchedulePattern.TYPE_ONCE)) {
            result = new DailyPattern(startDate, startDate, 1, 1);
        } else {
            // @translatable
            throw new ReservationException("Unknown recurrence type in XML string: {0}",
                RecurrenceParser.class, recurringPattern.getRecurringType());
        }
        if (recurringPattern.getTotal() > 0) {
            result.setNumberOfOccurrences(recurringPattern.getTotal());
        }
        
        return result;
    }
    
    /**
     * Parse a monthly pattern from XML.
     *
     * @param startDate pattern start date
     * @param endDate pattern end date
     * @param recurringPattern the recurring pattern
     * @return the monthly pattern
     */
    private static Recurrence getMonthlyPattern(final Date startDate, final Date endDate,
            final RecurringScheduleService recurringPattern) {
        
        Recurrence monthlyPattern = null;
        
        // check the type of monthly pattern
        if (StringUtil.isNullOrEmpty(recurringPattern.getDaysOfWeek())) {
            // MonthDay: specified by the day in the month
            monthlyPattern =
                    new MonthlyPattern(startDate, endDate, recurringPattern.getInterval(),
                        recurringPattern.getDayOfMonth());
        } else {
            // MonthWeekIndex: specified by the week day and week index
            final DayOfTheWeek dayOfTheWeek = DayOfTheWeek.get(recurringPattern.getDaysOfWeek());
            monthlyPattern =
                    new MonthlyPattern(startDate, endDate, recurringPattern.getInterval(),
                        recurringPattern.getWeekOfMonth(), dayOfTheWeek);
        }
        
        return monthlyPattern;
    }
    
    /**
     * Parse a weekly pattern from XML.
     *
     * @param startDate pattern start date
     * @param endDate pattern end date
     * @param recurringPattern the recurring pattern
     * @return the weekly pattern
     */
    private static Recurrence getWeeklyPattern(final Date startDate, final Date endDate,
            final RecurringScheduleService recurringPattern) {
        final List<DayOfTheWeek> daysOfTheWeek = new ArrayList<DayOfTheWeek>();
        final String[] weekDays = recurringPattern.getDaysOfWeek().split(",");
        
        for (final String weekDay : weekDays) {
            // get the day of week by name
            daysOfTheWeek.add(DayOfTheWeek.get(weekDay));
        }
        
        return new WeeklyPattern(startDate, endDate, recurringPattern.getInterval(), daysOfTheWeek);
    }
    
    /**
     * Gets the yearly pattern.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param recurringPattern the recurring pattern
     * @return the yearly pattern
     */
    private static Recurrence getYearlyPattern(final Date startDate, final Date endDate,
            final RecurringScheduleService recurringPattern) {
        
        // <recurring type="year" value1="9" value2="mar" value3="1" total="3"/>
        final int monthIndex = recurringPattern.getMonthOfYear() - 1;
        final Month month = Month.get(monthIndex);
        YearlyPattern yearlyPattern = null;
        
        // check the type of yearly pattern
        if (StringUtil.isNullOrEmpty(recurringPattern.getDaysOfWeek())) {
            yearlyPattern = new YearlyPattern(startDate, month, recurringPattern.getDayOfMonth());
        } else {
            final DayOfTheWeek dayOfTheWeek = DayOfTheWeek.get(recurringPattern.getDaysOfWeek());
            yearlyPattern =
                    new YearlyPattern(startDate, month, recurringPattern.getWeekOfMonth(),
                        dayOfTheWeek);
        }
        yearlyPattern.setEndDate(endDate);
        yearlyPattern.setInterval(recurringPattern.getInterval());
        return yearlyPattern;
    }
    
}
