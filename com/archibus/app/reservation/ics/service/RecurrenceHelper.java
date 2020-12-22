package com.archibus.app.reservation.ics.service;

import java.text.ParseException;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

import com.archibus.app.reservation.domain.recurrence.*;

import net.fortuna.ical4j.model.*;
import net.fortuna.ical4j.model.WeekDay.Day;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.*;

/**
 * Copyright (C) ARCHIBUS, Inc. All rights reserved.
 */
/**
 * Utility class. Provides methods to create the recurring rule pattern for ICS
 * parsing.
 * <p>
 *
 * @author PROCOS
 * @since 23.2
 *
 */
public final class RecurrenceHelper {

    /** ICS identifier for a yearly pattern. */
    private static final String YEARLY = "YEARLY";

    /** ICS identifier for a monthly pattern. */
    private static final String MONTHLY = "MONTHLY";

    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private RecurrenceHelper() {
    }

    /**
     * Add the Recurring Rule to the meeting.
     *
     * @param meeting
     *            the meeting event
     * @param recurrence
     *            the recurrence pattern
     * @param until
     *            the until date
     */
    public static void addRecurringRule(final VEvent meeting, final AbstractIntervalPattern recurrence,
            final DateTime until) {
        try {
            final Recur recur = new Recur(RecurrenceHelper.getRecurringRulePattern(recurrence, until));
            recur.setUntil(until);
            recur.setInterval(recurrence.getInterval());
            recur.setWeekStartDay(Day.SU);
            meeting.getProperties().add(new RRule(recur));
        } catch (final ParseException e) {
            Logger.getLogger(RecurrenceHelper.class).error(e.getMessage(), e);
            // @translatable
            throw new com.archibus.app.reservation.domain.CalendarException("Generating ICS recurrence pattern failed",
                    e, RecurrenceHelper.class);
        }

    }

    /**
     * Add the exception dates for cancelled occurrences, when updating
     * recurring meetings.
     *
     * @param meeting
     *            the meeting event
     * @param cancellations
     *            the exception dates to add
     */
    public static void addExceptionDates(final VEvent meeting, final List<Date> cancellations) {
        final ParameterList parameterList = new ParameterList();
        parameterList.add(net.fortuna.ical4j.model.parameter.Value.DATE);
        for (final Date cancellation : cancellations) {
            addExceptionDate(meeting, parameterList, cancellation);
        }
    }

    /**
     * Add the Exception Date.
     *
     * @param meeting
     *            the meeting event
     * @param parameterList
     *            the parameter list
     * @param canceledDate
     *            the canceled date to add as exception
     */
    private static void addExceptionDate(final VEvent meeting, final ParameterList parameterList,
            final Date canceledDate) {
        try {
            final ExDate exDate = new ExDate(parameterList,
                    new java.text.SimpleDateFormat(IcsConstants.DATE_FORMAT).format(canceledDate));
            meeting.getProperties().add(exDate);
        } catch (final ParseException e) {
            Logger.getLogger(RecurrenceHelper.class).error(e.getMessage(), e);
            // @translatable
            throw new com.archibus.app.reservation.domain.CalendarException(
                    "Adding recurrence exception to ICS meeting failed", e, RecurrenceHelper.class);
        }
    }

    /**
     * Get the recurring rule pattern for ICS parsing.
     *
     * @param pattern the interval pattern
     * @param untilDate the until date
     * @return the recurring rule pattern string
     */
    public static String getRecurringRulePattern(
            final AbstractIntervalPattern pattern, final DateTime untilDate) {
        String recurStr;

        final StringBuilder builder = new StringBuilder(41);
        builder.append("FREQ=%s;UNTIL=%s");
        final String until = new java.text.SimpleDateFormat(IcsConstants.DATE_FORMAT).format(untilDate);

        if (pattern instanceof DailyPattern) {
            recurStr = String.format(builder.toString(), "DAILY", until);
        } else if (pattern instanceof WeeklyPattern) {
            builder.append(";BYDAY=%s");
            recurStr = String.format(builder.toString(), "WEEKLY", until,
                    RecurrenceHelper.getWeekDays(((WeeklyPattern) pattern).getDaysOfTheWeek()));
        } else if (pattern instanceof MonthlyPattern) {

            final MonthlyPattern monthlyPattern = (MonthlyPattern) pattern;
            if (RecurrenceHelper.isOnDayOfMonth(monthlyPattern)) {
                builder.append(";BYMONTHDAY=%s");
                recurStr = String.format(builder.toString(), MONTHLY, until, monthlyPattern.getDayOfMonth());
            } else if (RecurrenceHelper.isOnDayOfWeek(monthlyPattern)) {
                builder.append(";BYDAY=%s;BYSETPOS=%s");
                recurStr = String.format(builder.toString(), MONTHLY, until,
                        RecurrenceHelper.getWeekDay(monthlyPattern.getDayOfTheWeek()), monthlyPattern.getWeekOfMonth());
            } else {
                // @translatable
                throw new com.archibus.app.reservation.domain.CalendarException("Invalid monthly recurrence pattern",
                        RecurrenceHelper.class);
            }
        } else if (pattern instanceof YearlyPattern) {
            final YearlyPattern yearlyPattern = (YearlyPattern) pattern;

            if (isOnDayOfWeek(yearlyPattern)) {
                // if occurrence is on day of week in a specific month
                builder.append(";BYDAY=%s;BYSETPOS=%s;BYMONTH=%s");
                recurStr = String.format(builder.toString(), YEARLY, until,
                        RecurrenceHelper.getWeekDay(yearlyPattern.getDayOfTheWeek()), yearlyPattern.getWeekOfMonth(),
                        yearlyPattern.getMonth().getIntValue() + 1);

            } else if (isOnDayOfMonth(yearlyPattern)) {
                // if occurrence is on day of month
                builder.append(";BYMONTH=%s;BYMONTHDAY=%s");
                recurStr = String.format(builder.toString(), YEARLY, until, yearlyPattern.getMonth().getIntValue() + 1,
                        yearlyPattern.getDayOfMonth());
            } else {
                // @translatable
                throw new com.archibus.app.reservation.domain.CalendarException("Invalid yearly recurrence pattern",
                        RecurrenceHelper.class);
            }
        } else {
            // @translatable
            throw new com.archibus.app.reservation.domain.CalendarException("Invalid recurrence pattern",
                    RecurrenceHelper.class);
        }
        return recurStr;
    }

    /**
     * Check if the month or yearly pattern is on a specific day of the week.
     *
     * @param <T> a monthly recurrence pattern
     * @param pattern the pattern
     * @return the flag if is on a day of the week
     */
    private static <T extends AbstractMonthlyPattern> boolean isOnDayOfWeek(
            final T pattern) {
        return pattern.getWeekOfMonth() != null;
    }

    /**
     * Check if the month or yearly pattern is on a specific day of the month.
     *
     * @param <T> a monthly recurrence pattern
     * @param pattern the pattern
     * @return the flag if is on a day of the month
     */
    private static <T extends AbstractMonthlyPattern> boolean isOnDayOfMonth(
            final T pattern) {
        return pattern.getDayOfMonth() != null;
    }

    /**
     * Convert the day of the week to the ICS format.
     *
     * @param dayOfTheWeek the day of the week
     * @return the ICS formatted day of week string
     */
    private static String getWeekDay(final DayOfTheWeek dayOfTheWeek) {
        String weekDay;
        switch (dayOfTheWeek.getIntValue()) {
            case java.util.Calendar.SUNDAY:
                weekDay = "SU";
                break;
            case java.util.Calendar.MONDAY:
                weekDay = "MO";
                break;
            case java.util.Calendar.TUESDAY:
                weekDay = "TU";
                break;
            case java.util.Calendar.WEDNESDAY:
                weekDay = "WE";
                break;
            case java.util.Calendar.THURSDAY:
                weekDay = "TH";
                break;
            case java.util.Calendar.FRIDAY:
                weekDay = "FR";
                break;
            case java.util.Calendar.SATURDAY:
                weekDay = "SA";
                break;
            default:
                weekDay = "";
                break;
        }
        return weekDay;
    }

    /**
     * Convert the list of days of the week to the ICS format.
     *
     * @param daysOfTheWeek the days of the week
     * @return a comma separated string with the days of the week
     */
    private static String getWeekDays(final List<DayOfTheWeek> daysOfTheWeek) {
        final StringBuilder builder = new StringBuilder();
        for (final DayOfTheWeek dayOfTheWeek : daysOfTheWeek) {
            final String icsWeekDay = getWeekDay(dayOfTheWeek);
            if (builder.length() > 0) {
                builder.append(',').append(icsWeekDay);
            } else {
                builder.append(icsWeekDay);
            }
        }
        return builder.toString();
    }

}
