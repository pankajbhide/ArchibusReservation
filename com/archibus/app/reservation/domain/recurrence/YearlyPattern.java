package com.archibus.app.reservation.domain.recurrence;

import java.util.*;

import javax.xml.bind.annotation.*;

import com.archibus.app.common.recurring.*;
import com.archibus.utility.StringUtil;

/**
 * Yearly pattern for recurring reservations.
 * 
 * @author Bart Vanderschoot
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "YearlyPattern")
public class YearlyPattern extends AbstractMonthlyPattern {

    /** The month. */
    private Month month;

    /**
     * Default constructor.
     */
    public YearlyPattern() {
        super();
    }

    /**
     * Constructor using parameters.
     * 
     * @param startDate start date
     * @param endDate end date
     * @param month month
     * @param weekOfMonth week of month
     * @param dayOfTheWeek day of the week
     */
    public YearlyPattern(final Date startDate, final Date endDate, final Month month,
            final int weekOfMonth, final DayOfTheWeek dayOfTheWeek) {
        super(startDate, endDate, 1, weekOfMonth, dayOfTheWeek);
        this.month = month;
    }

    /**
     * Constructor using parameters.
     * 
     * @param startDate start date
     * @param month month
     * @param dayOfMonth day of month
     */
    public YearlyPattern(final Date startDate, final Month month, final int dayOfMonth) {
        super(startDate, 1, dayOfMonth);
        this.month = month;
    }

    /**
     * Constructor using parameters.
     * 
     * @param startDate start date
     * @param month month
     * @param weekOfMonth week of month
     * @param dayOfTheWeek day of the week
     */
    public YearlyPattern(final Date startDate, final Month month, final int weekOfMonth,
            final DayOfTheWeek dayOfTheWeek) {
        super(startDate, 1, weekOfMonth, dayOfTheWeek);
        this.month = month;
    }

    /**
     * Get month.
     * 
     * @return month
     */
    public final Month getMonth() {
        return this.month;
    }

    /**
     * Set month.
     * 
     * @param month month
     */
    public final void setMonth(final Month month) {
        this.month = month;
    }

    /**
     * Create XML string for recurring rule.
     * 
     * @return string string
     */
    @Override
    public final String toString() {
        String result;
        if (StringUtil.notNullOrEmpty(this.getDayOfMonth())) {
            result =
                    RecurringScheduleService.getRecurrenceXMLPattern(
                        RecurringSchedulePattern.TYPE_YEAR, getInterval(), getTotalOccurrences(),
                        null, getDayOfMonth(), -1, getMonth().getIntValue() + 1);
        } else {
            result =
                    RecurringScheduleService.getRecurrenceXMLPattern(
                        RecurringSchedulePattern.TYPE_YEAR, getInterval(), getTotalOccurrences(),
                        getDayOfTheWeek().getValue(), -1, getWeekOfMonth(), getMonth()
                            .getIntValue() + 1);
        }
        
        return result;
    }

}
