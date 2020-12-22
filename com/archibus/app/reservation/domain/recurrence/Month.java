package com.archibus.app.reservation.domain.recurrence;

import java.util.Calendar;

/**
 * Defines months of the year.
 */
public enum Month {

    /** The January. */
    January(Calendar.JANUARY),

    /** The February. */
    February(Calendar.FEBRUARY),

    /** The March. */
    March(Calendar.MARCH),

    /** The April. */
    April(Calendar.APRIL),

    /** The May. */
    May(Calendar.MAY),

    /** The June. */
    June(Calendar.JUNE),

    /** The July. */
    July(Calendar.JULY),

    /** The August. */
    August(Calendar.AUGUST),

    /** The September. */
    September(Calendar.SEPTEMBER),

    /** The October. */
    October(Calendar.OCTOBER),

    /** The November. */
    November(Calendar.NOVEMBER),

    /** The December. */
    December(Calendar.DECEMBER);

    /** The month. */
    private final int month;

    /**
     * Instantiates a new month.
     * 
     * @param month the month
     */
    Month(final int month) {
        this.month = month;
    }

    /**
     * Get the integer value as defined in System.
     * 
     * @return the value
     */
    public int getIntValue() {
        return this.month;
    }

    /**
     * Gets the month.
     *
     * @param monthIndex the month index
     * @return the month
     */
    public static Month get(final int monthIndex) {
        Month result = null;
        for (Month month : Month.values()) { 
            if (month.month == monthIndex) {
                result = month;
            }
        }
        return result;
    }
}
