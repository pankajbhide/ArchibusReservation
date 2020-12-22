package com.archibus.app.reservation.domain.recurrence;

import java.util.Date;

import javax.xml.bind.annotation.*;

/**
 * Represents an abstract base class for monthly recurrence patterns.
 * 
 * @author Yorik Gerlo
 * @since 20.1
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "AbstractMonthlyPattern")
public abstract class AbstractMonthlyPattern extends AbstractIntervalPattern {

    /** The day of month. */
    private Integer dayOfMonth;

    /** the day of the week, specified along with weekOfMonth. */
    private DayOfTheWeek dayOfTheWeek;

    /** indicates which instance of the weekday is meant (the first or second or ...). */
    private Integer weekOfMonth;    

    /**
     * Default constructor.
     */
    public AbstractMonthlyPattern() {
        super();
    }

    /**
     * Constructor using parameters.
     * 
     * @param startDate start date
     * @param endDate end date
     * @param interval the interval
     * @param weekOfMonth which week of the month
     * @param dayOfTheWeek which day of the week
     */
    public AbstractMonthlyPattern(final Date startDate, final Date endDate, final Integer interval,
            final Integer weekOfMonth, final DayOfTheWeek dayOfTheWeek) {
        super(startDate, endDate, interval);
        this.setWeekOfMonth(weekOfMonth);
        this.setDayOfTheWeek(dayOfTheWeek);
    }

    /**
     * Constructor using parameters.
     * 
     * @param startDate start date
     * @param endDate end date
     * @param interval interval
     * @param dayOfMonth day of month
     */
    public AbstractMonthlyPattern(final Date startDate, final Date endDate, final Integer interval, 
            final int dayOfMonth) {
        super(startDate, endDate, interval);
        this.setDayOfMonth(dayOfMonth);
    }

    /**
     * Constructor using parameters.
     * 
     * @param startDate start date
     * @param interval interval
     * @param dayOfMonth day of month
     */
    public AbstractMonthlyPattern(final Date startDate, final Integer interval, final int dayOfMonth) {
        super(startDate, interval);
        this.setDayOfMonth(dayOfMonth);
    }

    /**
     * Constructor using parameters.
     * 
     * @param startDate start date
     * @param interval interval
     * @param weekOfMonth week of month
     * @param dayOfTheWeek day of the week
     */
    public AbstractMonthlyPattern(final Date startDate, final Integer interval,
            final Integer weekOfMonth, final DayOfTheWeek dayOfTheWeek) {
        super(startDate, interval);
        this.setWeekOfMonth(weekOfMonth);
        this.setDayOfTheWeek(dayOfTheWeek);
    }

    /**
     * Get the day of the month.
     * 
     * @return day of month
     */
    public final Integer getDayOfMonth() {
        return this.dayOfMonth;
    }

    /**
     * Get the day of the week.
     * 
     * @return day of week
     */
    public final DayOfTheWeek getDayOfTheWeek() {
        return this.dayOfTheWeek;
    }

    /**
     * Get week of the month.
     * 
     * @return week of month
     */
    public final Integer getWeekOfMonth() {
        return this.weekOfMonth;
    }

    /**
     * Set day of month.
     * 
     * @param dayOfMonth day of month
     */
    public final void setDayOfMonth(final Integer dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
    }

    /**
     * Set day of week.
     * 
     * @param dayOfTheWeek day of the week
     */
    public final void setDayOfTheWeek(final DayOfTheWeek dayOfTheWeek) {
        this.dayOfTheWeek = dayOfTheWeek;
    }

    /**
     * Set week of month.
     * 
     * @param weekOfMonth week of month
     */
    public final void setWeekOfMonth(final Integer weekOfMonth) {
        this.weekOfMonth = weekOfMonth;
    }

}