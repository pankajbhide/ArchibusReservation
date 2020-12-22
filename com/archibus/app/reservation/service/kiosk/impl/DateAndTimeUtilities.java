package com.archibus.app.reservation.service.kiosk.impl;

import static com.archibus.app.common.mobile.util.FieldNameConstantsCommon.*;

import java.sql.Time;
import java.text.*;
import java.util.*;

import org.apache.log4j.Logger;

import com.archibus.app.reservation.domain.TimePeriod;
import com.archibus.utility.StringUtil;

/**
 * Utility class. Provides methods related with data sources for Essentials Tier WFRs, Reservations
 * module.
 *
 * @author Yorik Gerlo
 * @since 23.3
 */
public class DateAndTimeUtilities {
    /**
     * Date formatter.
     */
    protected final SimpleDateFormat dateFormatter =
            new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

    /**
     * Time formatter.
     */
    protected final SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm", Locale.ENGLISH);

    /** The logger. */
    private final Logger logger = Logger.getLogger(this.getClass());

    /**
     * Default constructor.
     */
    DateAndTimeUtilities() {
        // default constructor
    }

    /**
     * Create a time period from the request parameters.
     *
     * @param requestParameters request parameters
     * @return the time period
     */
    protected TimePeriod createTimePeriod(final Map<String, Object> requestParameters) {
        TimePeriod timePeriod = null;

        try {
            final Object dateParam = requestParameters.get(DATE_START);
            Date date = null;
            if (dateParam != null) {
                date = createDate(dateParam.toString());
            }

            final Object startTimeParam = requestParameters.get(TIME_START);
            Time startTime = null;
            if (StringUtil.notNullOrEmpty(startTimeParam)) {
                startTime = createTime(startTimeParam.toString());
            }

            final Object endTimeParam = requestParameters.get(TIME_END);
            Time endTime = null;
            if (StringUtil.notNullOrEmpty(endTimeParam)) {
                endTime = createTime(endTimeParam.toString());
            }
            timePeriod = new TimePeriod(date, date, startTime, endTime);

        } catch (final ParseException e) {
            this.logger.error("Invalid time", e);
        }

        return timePeriod;
    }

    /**
     * Create a time object representing the given time string.
     *
     * @param formattedDate the time as yyyy-MM-dd
     * @return the date object
     * @throws ParseException when the parameter is an invalid date
     */
    protected Date createDate(final String formattedDate) throws ParseException {
        return new Date(this.dateFormatter.parse(formattedDate).getTime());
    }

    /**
     * Create a time object representing the given time string.
     *
     * @param formattedTime the time as HH:MM
     * @return the time object
     * @throws ParseException when the parameter is an invalid time
     */
    protected Time createTime(final String formattedTime) throws ParseException {
        return new Time(this.timeFormatter.parse(formattedTime).getTime());
    }

    /**
     * Converts a Date into a String "yyyy-MM-dd".
     *
     * @param date the date
     * @return the string
     */
    protected String formatDate(final Date date) {
        return this.dateFormatter.format(date);
    }

    /**
     * Converts a Date into a String "HH:mm".
     *
     * @param time the time
     * @return the string
     */
    protected String formatTime(final Time time) {
        return this.timeFormatter.format(time);
    }
}
