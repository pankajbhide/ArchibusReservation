package com.archibus.app.reservation.exchange.util;

import microsoft.exchange.webservices.data.*;

import com.archibus.app.reservation.domain.recurrence.*;

/**
 * Utility class. Provides methods to convert between patterns used in Exchange and in ARCHIBUS
 * Reservations.
 * <p>
 * Used by ExchangeRecurrenceConverter.
 * 
 * @author Yorik Gerlo
 * @since 21.2
 * 
 */
final class ExchangeRecurrenceHelper {
    
    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private ExchangeRecurrenceHelper() {
    }
    
    /**
     * Helper method to convert a monthly recurrence pattern to its Exchange equivalent.
     * 
     * @param yearlyPattern the yearly pattern to convert
     * @param dayOfTheWeekConverter the converter for the day of the week
     * @return the corresponding Exchange recurrence pattern
     * @throws ArgumentOutOfRangeException when a parameter is out of range
     */
    public static microsoft.exchange.webservices.data.Recurrence convertYearlyPattern(
            final YearlyPattern yearlyPattern,
            final ExchangeDayOfTheWeekConverter dayOfTheWeekConverter)
            throws ArgumentOutOfRangeException {
        microsoft.exchange.webservices.data.Recurrence exchangeRecurrence = null;
        /*
         * No support for custom interval with yearly patterns: always one-year patterns in EWS API.
         * Only Outlook supports specifying longer intervals. Possible solution: cancel the
         * occurrences that should not be included. Before sending the invitations? Alternative:
         * simply ignore and treat as equivalent...?
         */
        if (yearlyPattern.getInterval() > 1) {
            // Exchange yearly patterns do not support intervals > 1 year. Use monthly instead.
            final MonthlyPattern monthlyPattern =
                    new MonthlyPattern(yearlyPattern.getStartDate(), yearlyPattern.getEndDate(),
                        yearlyPattern.getInterval() * 12, yearlyPattern.getDayOfMonth());
            monthlyPattern.setDayOfTheWeek(yearlyPattern.getDayOfTheWeek());
            monthlyPattern.setWeekOfMonth(yearlyPattern.getWeekOfMonth());
            monthlyPattern.setNumberOfOccurrences(yearlyPattern.getNumberOfOccurrences());
            exchangeRecurrence = convertMonthlyPattern(monthlyPattern, dayOfTheWeekConverter);
        } else if (yearlyPattern.getDayOfMonth() == null) {
            // relative pattern
            final DayOfTheWeekIndex dayOfTheWeekIndex =
                    dayOfTheWeekConverter.convertDayOfTheWeekIndex(yearlyPattern.getWeekOfMonth());
            exchangeRecurrence =
                    new microsoft.exchange.webservices.data.Recurrence.RelativeYearlyPattern(
                        yearlyPattern.getStartDate(),
                        microsoft.exchange.webservices.data.Month.valueOf(yearlyPattern.getMonth()
                            .toString()), dayOfTheWeekConverter.convertDayOfTheWeek(yearlyPattern
                            .getDayOfTheWeek()), dayOfTheWeekIndex);
        } else {
            // absolute pattern
            exchangeRecurrence =
                    new microsoft.exchange.webservices.data.Recurrence.YearlyPattern(
                        yearlyPattern.getStartDate(),
                        microsoft.exchange.webservices.data.Month.valueOf(yearlyPattern.getMonth()
                            .toString()), yearlyPattern.getDayOfMonth());
        }
        return exchangeRecurrence;
    }
    
    /**
     * Helper method to convert a monthly recurrence pattern to its Exchange equivalent.
     * 
     * @param monthlyPattern the monthly pattern to convert
     * @param dayOfTheWeekConverter the converter for the day of the week
     * @return the corresponding Exchange recurrence pattern
     * @throws ArgumentOutOfRangeException when a parameter is out of range
     */
    public static microsoft.exchange.webservices.data.Recurrence convertMonthlyPattern(
            final MonthlyPattern monthlyPattern,
            final ExchangeDayOfTheWeekConverter dayOfTheWeekConverter)
            throws ArgumentOutOfRangeException {
        microsoft.exchange.webservices.data.Recurrence exchangeRecurrence = null;
        if (monthlyPattern.getDayOfMonth() == null) {
            // relative pattern
            final DayOfTheWeekIndex dayOfTheWeekIndex =
                    dayOfTheWeekConverter.convertDayOfTheWeekIndex(monthlyPattern.getWeekOfMonth());
            exchangeRecurrence =
                    new microsoft.exchange.webservices.data.Recurrence.RelativeMonthlyPattern(
                        monthlyPattern.getStartDate(),
                        monthlyPattern.getInterval(),
                        dayOfTheWeekConverter.convertDayOfTheWeek(monthlyPattern.getDayOfTheWeek()),
                        dayOfTheWeekIndex);
        } else {
            // absolute pattern
            exchangeRecurrence =
                    new microsoft.exchange.webservices.data.Recurrence.MonthlyPattern(
                        monthlyPattern.getStartDate(), monthlyPattern.getInterval(),
                        monthlyPattern.getDayOfMonth());
        }
        return exchangeRecurrence;
    }
}
