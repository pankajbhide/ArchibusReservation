package com.archibus.app.reservation.service.helpers;

import java.sql.Time;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.archibus.app.reservation.util.ReservationsContextHelper;
import com.archibus.jobmanager.EventHandlerContext;
import com.archibus.utility.StringUtil;

/**
 * The Class WebCentralCalendarServiceHelper.
 *
 * This class contains static methods only.
 */
public final class WebCentralCalendarServiceHelper {

    /** The Constant ISO_DATE_FORMAT. */
    private static final String ISO_DATE_FORMAT = "yyyy-MM-dd";

    /** The Constant TIME_FORMAT. */
    private static final String TIME_FORMAT = "HH:mm:ss";

    /**
     * Prevent instantiation of a new web central calendar service helper.
     */
    private WebCentralCalendarServiceHelper() {
    }

    /**
     * Sets the result message.
     *
     * @param context the context
     * @param resultMessage the result message
     */
    public static void setResultMessage(final EventHandlerContext context,
            final String resultMessage) {
        if (StringUtil.notNullOrEmpty(resultMessage)
                && context
                    .parameterExistsNotEmpty(ReservationsContextHelper.RESULT_MESSAGE_PARAMETER)) {
            // Check whether the result message has changed.
            final String newResultMessage =
                    context.getString(ReservationsContextHelper.RESULT_MESSAGE_PARAMETER);
            if (!resultMessage.equals(newResultMessage)) {
                // Concatenate the two messages, separated by newline.
                context.addResponseParameter(ReservationsContextHelper.RESULT_MESSAGE_PARAMETER,
                    resultMessage + '\n' + newResultMessage);
            }
        }
    }

    /**
     * Gets the date formatted.
     *
     * @param date the date
     * @return the date formatted
     */
    public static String getDateFormatted(final Date date) {
        /** For formatting date strings. */
        final DateFormat dateFormatter = new SimpleDateFormat(ISO_DATE_FORMAT, Locale.ENGLISH);

        return dateFormatter.format(date);
    }

    /**
     * Gets the time formatted.
     *
     * @param time the time
     * @return the time formatted
     */
    public static String getTimeFormatted(final Time time) {
        /** For formatting time string. */
        final DateFormat timeFormatter = new SimpleDateFormat(TIME_FORMAT, Locale.ENGLISH);

        return timeFormatter.format(time);
    }

    /**
     * Check result message.
     *
     * @param context the context
     * @return the string
     */
    public static String checkResultMessage(final EventHandlerContext context) {
        String resultMessage = null;
        if (context.parameterExistsNotEmpty(ReservationsContextHelper.RESULT_MESSAGE_PARAMETER)) {
            resultMessage = context.getString(ReservationsContextHelper.RESULT_MESSAGE_PARAMETER);
        }
        return resultMessage;
    }

}
