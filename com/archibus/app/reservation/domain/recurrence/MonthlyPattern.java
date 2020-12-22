package com.archibus.app.reservation.domain.recurrence;

import java.util.*;

import javax.xml.bind.annotation.*;

import com.archibus.app.common.recurring.*;
import com.archibus.utility.StringUtil;

/**
 * Monthly pattern for recurring reservations.
 * 
 * This pattern has two options: either a fixed date specified via the day of the month, or a
 * specific weekday specified via the day of the week and which instance of that day of the week.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "MonthlyPattern")
public class MonthlyPattern extends AbstractMonthlyPattern {

    /**
     * Default constructor.
     */
    public MonthlyPattern() {
        super();
    }

    /**
     * Constructor using parameters.
     * 
     * @param startDate start date
     * @param endDate end date
     * @param interval interval
     * @param weekOfMonth week of the month
     * @param dayOfTheWeek day of the week
     */
    public MonthlyPattern(final Date startDate, final Date endDate, final Integer interval,
            final Integer weekOfMonth, final DayOfTheWeek dayOfTheWeek) {
        super(startDate, endDate, interval, weekOfMonth, dayOfTheWeek);
    }


    /**
     * Constructor using parameters.
     * 
     * @param startDate start date
     * @param endDate end date
     * @param interval interval
     * @param dayOfMonth day of month
     */
    public MonthlyPattern(final Date startDate, final Date endDate, final Integer interval, final int dayOfMonth) {
        super(startDate, endDate, interval, dayOfMonth);
    }

    /**
     * Constructor using parameters.
     * 
     * @param startDate start date
     * @param interval interval
     * @param dayOfMonth day of month
     */
    public MonthlyPattern(final Date startDate, final Integer interval, final int dayOfMonth) {
        super(startDate, interval, dayOfMonth);
    }

    /**
     * Constructor using parameters.
     * 
     * @param startDate start date
     * @param interval interval
     * @param weekOfMonth week of month
     * @param dayOfTheWeek day of the week
     */
    public MonthlyPattern(final Date startDate, final Integer interval, final Integer weekOfMonth,
            final DayOfTheWeek dayOfTheWeek) {
        super(startDate, interval, weekOfMonth, dayOfTheWeek);
    }

    /**
     * Create XML string for recurring rule.
     * 
     * @return the string
     */
    @Override
    public final String toString() {
        String result;
        if (StringUtil.notNullOrEmpty(this.getDayOfMonth())) {
            result =
                    RecurringScheduleService.getRecurrenceXMLPattern(
                        RecurringSchedulePattern.TYPE_MONTH, getInterval(), getTotalOccurrences(),
                        null, getDayOfMonth(), -1, -1);
        } else {
            result =
                    RecurringScheduleService.getRecurrenceXMLPattern(
                        RecurringSchedulePattern.TYPE_MONTH, getInterval(), getTotalOccurrences(),
                        getDayOfTheWeek().getValue(), -1, getWeekOfMonth(), -1);
        }

        return result;
    }

}


