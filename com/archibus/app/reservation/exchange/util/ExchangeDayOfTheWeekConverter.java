package com.archibus.app.reservation.exchange.util;

import java.util.*;

import microsoft.exchange.webservices.data.*;

import com.archibus.app.reservation.domain.CalendarException;
import com.archibus.app.reservation.domain.recurrence.DayOfTheWeek;

/**
 * The DayOfTheWeekConverter: converts between DayOfTheWeek representation used in Web Central and
 * the DayOfTheWeek representation used in the EWS library.
 * 
 * Managed by Spring.
 */
public class ExchangeDayOfTheWeekConverter {
    
    /** Convert from weekOfMonth to the corresponding DayOfTheWeekIndex. */
    private final DayOfTheWeekIndex[] weeksOfMonth = new DayOfTheWeekIndex[] {
            DayOfTheWeekIndex.First, DayOfTheWeekIndex.Second, DayOfTheWeekIndex.Third,
            DayOfTheWeekIndex.Fourth, DayOfTheWeekIndex.Last };
    
    /** Maps DayOfTheWeekIndex values to the integer representation used in ARCHIBUS. */
    private final Map<DayOfTheWeekIndex, Integer> weekIndices =
            new HashMap<DayOfTheWeekIndex, Integer>();
    
    /** Maps Reservations DayOfTheWeek values to Exchange equivalent. */
    private final Map<DayOfTheWeek, microsoft.exchange.webservices.data.DayOfTheWeek> daysOfTheWeekToExchange =
            new HashMap<DayOfTheWeek, microsoft.exchange.webservices.data.DayOfTheWeek>();
    
    /** Maps Exchange DayOfTheWeek values to ARCHIBUS Reservations equivalent. */
    private final Map<microsoft.exchange.webservices.data.DayOfTheWeek, DayOfTheWeek> daysOfTheWeekToArchibus =
            new HashMap<microsoft.exchange.webservices.data.DayOfTheWeek, DayOfTheWeek>();
    
    /**
     * Default constructor.
     */
    public ExchangeDayOfTheWeekConverter() {
        for (int i = 0; i < this.weeksOfMonth.length; ++i) {
            this.weekIndices.put(this.weeksOfMonth[i], i + 1);
        }
        
        // Map enum values from ARCHIBUS to Exchange.
        this.daysOfTheWeekToExchange.put(DayOfTheWeek.Day,
            microsoft.exchange.webservices.data.DayOfTheWeek.Day);
        this.daysOfTheWeekToExchange.put(DayOfTheWeek.Sunday,
            microsoft.exchange.webservices.data.DayOfTheWeek.Sunday);
        this.daysOfTheWeekToExchange.put(DayOfTheWeek.Monday,
            microsoft.exchange.webservices.data.DayOfTheWeek.Monday);
        this.daysOfTheWeekToExchange.put(DayOfTheWeek.Tuesday,
            microsoft.exchange.webservices.data.DayOfTheWeek.Tuesday);
        this.daysOfTheWeekToExchange.put(DayOfTheWeek.Wednesday,
            microsoft.exchange.webservices.data.DayOfTheWeek.Wednesday);
        this.daysOfTheWeekToExchange.put(DayOfTheWeek.Thursday,
            microsoft.exchange.webservices.data.DayOfTheWeek.Thursday);
        this.daysOfTheWeekToExchange.put(DayOfTheWeek.Friday,
            microsoft.exchange.webservices.data.DayOfTheWeek.Friday);
        this.daysOfTheWeekToExchange.put(DayOfTheWeek.Saturday,
            microsoft.exchange.webservices.data.DayOfTheWeek.Saturday);
        this.daysOfTheWeekToExchange.put(DayOfTheWeek.Weekday,
            microsoft.exchange.webservices.data.DayOfTheWeek.Weekday);
        this.daysOfTheWeekToExchange.put(DayOfTheWeek.WeekendDay,
            microsoft.exchange.webservices.data.DayOfTheWeek.WeekendDay);
        
        // Map enum values from Exchange to ARCHIBUS.
        this.daysOfTheWeekToArchibus.put(microsoft.exchange.webservices.data.DayOfTheWeek.Day,
            DayOfTheWeek.Day);
        this.daysOfTheWeekToArchibus.put(microsoft.exchange.webservices.data.DayOfTheWeek.Sunday,
            DayOfTheWeek.Sunday);
        this.daysOfTheWeekToArchibus.put(microsoft.exchange.webservices.data.DayOfTheWeek.Monday,
            DayOfTheWeek.Monday);
        this.daysOfTheWeekToArchibus.put(microsoft.exchange.webservices.data.DayOfTheWeek.Tuesday,
            DayOfTheWeek.Tuesday);
        this.daysOfTheWeekToArchibus.put(
            microsoft.exchange.webservices.data.DayOfTheWeek.Wednesday, DayOfTheWeek.Wednesday);
        this.daysOfTheWeekToArchibus.put(microsoft.exchange.webservices.data.DayOfTheWeek.Thursday,
            DayOfTheWeek.Thursday);
        this.daysOfTheWeekToArchibus.put(microsoft.exchange.webservices.data.DayOfTheWeek.Friday,
            DayOfTheWeek.Friday);
        this.daysOfTheWeekToArchibus.put(microsoft.exchange.webservices.data.DayOfTheWeek.Saturday,
            DayOfTheWeek.Saturday);
        this.daysOfTheWeekToArchibus.put(microsoft.exchange.webservices.data.DayOfTheWeek.Weekday,
            DayOfTheWeek.Weekday);
        this.daysOfTheWeekToArchibus.put(
            microsoft.exchange.webservices.data.DayOfTheWeek.WeekendDay, DayOfTheWeek.WeekendDay);
    }
    
    /**
     * Convert the weekOfMonth index to a value in the enumeration list used by EWS.
     * 
     * @param weekOfMonth indicates the index of the week day for a recurrence pattern
     * @return DayOfTheWeekIndex enum value corresponding to the weekOfMonth
     */
    public DayOfTheWeekIndex convertDayOfTheWeekIndex(final Integer weekOfMonth) {
        if (weekOfMonth < 1 || weekOfMonth > this.weeksOfMonth.length) {
            // @translatable
            throw new CalendarException(
                "Invalid week of month in recurrence pattern for converting to Exchange equivalent",
                ExchangeDayOfTheWeekConverter.class);
        } else {
            return this.weeksOfMonth[weekOfMonth - 1];
        }
    }
    
    /**
     * Convert the given DayOfTheWeekIndex to its corresponding integer value used in ARCHIBUS.
     * 
     * @param dayOfTheWeekIndex the EWS representation of an index of the day of the week
     * @return the corresponding integer used in ARCHIBUS
     */
    public Integer convertDayOfTheWeekIndex(final DayOfTheWeekIndex dayOfTheWeekIndex) {
        return this.weekIndices.get(dayOfTheWeekIndex);
    }
    
    /**
     * Convert a list with days of the week to its EWS equivalent.
     * 
     * @param originalDaysOfTheWeek list with days of the week
     * @return EWS equivalent days of the week
     */
    public microsoft.exchange.webservices.data.DayOfTheWeek[] convertDaysOfTheWeek(
            final List<DayOfTheWeek> originalDaysOfTheWeek) {
        final microsoft.exchange.webservices.data.DayOfTheWeek[] convertedDaysOfTheWeek =
                new microsoft.exchange.webservices.data.DayOfTheWeek[originalDaysOfTheWeek.size()];
        for (int i = 0; i < originalDaysOfTheWeek.size(); ++i) {
            convertedDaysOfTheWeek[i] = convertDayOfTheWeek(originalDaysOfTheWeek.get(i));
        }
        return convertedDaysOfTheWeek;
    }
    
    /**
     * Convert a collection of days of the week to its ARCHIBUS equivalent.
     * 
     * @param originalDaysOfTheWeek the EWS collection of days
     * @return the ARCHIBUS equivalent list of days
     */
    public List<DayOfTheWeek> convertDaysOfTheWeek(
            final DayOfTheWeekCollection originalDaysOfTheWeek) {
        final List<DayOfTheWeek> daysOfTheWeek =
                new ArrayList<DayOfTheWeek>(originalDaysOfTheWeek.getCount());
        for (final microsoft.exchange.webservices.data.DayOfTheWeek dayOfTheWeek : originalDaysOfTheWeek) {
            daysOfTheWeek.add(convertDayOfTheWeek(dayOfTheWeek));
        }
        return daysOfTheWeek;
    }
    
    /**
     * Convert the DayOfTheWeek to its EWS equivalent.
     * 
     * @param originalDayOfTheWeek the dayOfTheWeek to convert
     * @return the EWS equivalent of the dayOfTheWeek
     */
    public microsoft.exchange.webservices.data.DayOfTheWeek convertDayOfTheWeek(
            final DayOfTheWeek originalDayOfTheWeek) {
        return this.daysOfTheWeekToExchange.get(originalDayOfTheWeek);
    }
    
    /**
     * Convert the EWS DayOfTheWeek to its ARCHIBUS equivalent.
     * 
     * @param originalDayOfTheWeek the EWS dayOfTheWeek to convert
     * @return the ARCHIBUS equivalent of the dayOfTheWeek
     */
    public DayOfTheWeek convertDayOfTheWeek(
            final microsoft.exchange.webservices.data.DayOfTheWeek originalDayOfTheWeek) {
        return this.daysOfTheWeekToArchibus.get(originalDayOfTheWeek);
    }
}