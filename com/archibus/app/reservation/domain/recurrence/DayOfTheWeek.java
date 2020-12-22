package com.archibus.app.reservation.domain.recurrence;

import java.util.*;

/**
 * Specifies the day of the week. For the standard days of the week (Sunday, Monday...) the
 * DayOfTheWeek enum value is the same as the System.DayOfWeek enum type. These values can be safely
 * cast between the two enum types. The special days of the week (Day, Weekday and WeekendDay) are
 * used for monthly and yearly recurrences and cannot be cast to System.DayOfWeek values.
 */
public enum DayOfTheWeek {  
    
    /** The Sunday. value = 1 */
    Sunday(Calendar.SUNDAY), 
   
    /** The Monday. value = 2 */
    Monday(Calendar.MONDAY),    

    /** The Tuesday. */
    Tuesday(Calendar.TUESDAY),

    /** The Wednesday. */
    Wednesday(Calendar.WEDNESDAY),

    /** The Thursday. */
    Thursday(Calendar.THURSDAY),    

    /** The Friday. */
    Friday(Calendar.FRIDAY),

    /** The Saturday.  value = 7 */
    Saturday(Calendar.SATURDAY),     
    
    /** The Day: any day of the week. */
    Day(Constants.EACH_DAY), 
    
    /** The Weekday: any day of the usual business week (Monday-Friday). */
    Weekday(Constants.WEEK_DAY),

    /** The Weekend day. */
    WeekendDay(Constants.WEEKEND_DAY);

    /** The day of the week as a string. */
    private final List<String> days = Arrays.asList("sun", "mon", "tue", "wed", "thu",
            "fri", "sat", Constants.EACH_DAY, Constants.WEEK_DAY, Constants.WEEKEND_DAY);     


    /** The day of week. */
    private int dayOfWeek;    

    /** The week day. */
    private String weekDay;

    /**
     * Instantiates a new day of the week.
     */
    DayOfTheWeek() {
        // default constructor, instantiates a new day of the week
    }

    /**
     * Instantiates a new day of the week.
     * 
     * @param dayOfWeek the day of week
     */
    DayOfTheWeek(final int dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
        if (dayOfWeek < this.days.size()) { 
            this.weekDay = this.days.get(dayOfWeek - 1);
        }

    }

    /**
     * Constructor using the string representation of the day of the week.
     * 
     * @param dayName day of the week
     */
    DayOfTheWeek(final String dayName) {
        this.weekDay = dayName;
        if (this.days.contains(dayName)) {
            this.dayOfWeek = this.days.indexOf(dayName) + 1;
        }
    }

    /**
     * Get the integer value as defined in System.
     * 
     * @return the value
     */
    public int getIntValue() {
        return this.dayOfWeek;
    }

    /**
     * Gets the value.
     *
     * @return the value
     */
    public String getValue() {
        return this.weekDay;
    }

    /* (non-Javadoc)
     * @see java.lang.Enum#toString()
     */
    @Override
    public String toString() {
        return this.getValue();
    }

    /**
     * Gets the day of week.
     *
     * @param dayName the day name
     * @return the day of the week
     */
    public static DayOfTheWeek get(final String dayName) {
        DayOfTheWeek result = null; 
        for (final DayOfTheWeek dayOfTheWeek : DayOfTheWeek.values()) {
            final String weekDay = dayOfTheWeek.weekDay;
            if (dayName.equalsIgnoreCase(weekDay)) {
                result = dayOfTheWeek;
                break;
            }
        }

        return result;        
    }

}
