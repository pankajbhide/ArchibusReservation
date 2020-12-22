package com.archibus.app.reservation.service;

import org.apache.log4j.Logger;

import com.archibus.app.reservation.dao.datasource.*;
import com.archibus.app.reservation.util.TimeZoneCache;

/**
 * The Class RoomReservationServiceBase.
 */
public class RoomReservationServiceBase {

    /** The room reservation data source. */
    protected ConferenceCallReservationDataSource reservationDataSource;

    /** The room allocation data source. */
    protected RoomAllocationDataSource roomAllocationDataSource;

    /** The Calendar Service. */
    protected ICalendarService calendarService;

    /** The time zone cache. */
    protected TimeZoneCache timeZoneCache;

    /** The logger. */
    protected final Logger logger = Logger.getLogger(getClass().getName());

    /**
     * Sets the room reservation data source.
     *
     * @param reservationDataSource the new room reservation data source
     */
    public void setReservationDataSource(
            final ConferenceCallReservationDataSource reservationDataSource) {
        this.reservationDataSource = reservationDataSource;
    }

    /**
     * Sets the room allocation data source.
     *
     * @param roomAllocationDataSource the new room allocation data source
     */
    public void setRoomAllocationDataSource(final RoomAllocationDataSource roomAllocationDataSource) {
        this.roomAllocationDataSource = roomAllocationDataSource;
    }

    /**
     * Sets the calendar service.
     *
     * @param calendarService the new calendar service
     */
    public void setCalendarService(final ICalendarService calendarService) {
        this.calendarService = calendarService;
    }

    /**
     * Sets the time zone cache.
     *
     * @param timeZoneCache the new time zone cache
     */
    public void setTimeZoneCache(final TimeZoneCache timeZoneCache) {
        this.timeZoneCache = timeZoneCache;
    }

}
