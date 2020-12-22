package com.archibus.app.reservation.exchange.domain;

import com.archibus.app.reservation.domain.CalendarException;
import com.archibus.service.remoting.AdminService;

/**
 * Specific exception to signal auto-discover errors.
 *
 * @author Yorik Gerlo
 */
public class AutodiscoverException extends CalendarException {

    /** Generated serial ID. */
    private static final long serialVersionUID = 3931188618208007847L;

    /**
     * Create a reservation exception with localization based on the provided class. The additional
     * arguments are used for formatting the translated string.
     *
     * @param message the message (to translate)
     * @param clazz the class where the message was defined
     * @param adminService service for localization
     * @param args additional arguments used for formatting the localized message
     */
    public AutodiscoverException(final String message, final Class<?> clazz, final AdminService adminService,
            final Object... args) {
        super(message, clazz, adminService, args);
    }

    /**
     * Create an autodiscover exception with localization based on the provided class. The additional
     * arguments are used for formatting the translated string.
     *
     * @param message the message (to translate)
     * @param cause the causing exception
     * @param clazz the class where the message was defined
     * @param adminService service for localization
     * @param args additional arguments used for formatting the localized message
     */
    public AutodiscoverException(final String message, final Exception cause, final Class<?> clazz,
            final AdminService adminService, final Object... args) {
        super(message, cause, clazz, adminService, args);
    }

}
