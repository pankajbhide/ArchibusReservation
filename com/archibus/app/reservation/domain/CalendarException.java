package com.archibus.app.reservation.domain;

import com.archibus.service.remoting.AdminService;

/**
 * A specific subclass of ReservationException that indicates an error in the calendar service.
 *
 * @author Yorik Gerlo
 * @since 21.2
 */
public class CalendarException extends ReservationException {

    /** Generated serial version ID. */
    private static final long serialVersionUID = -4344500297872381595L;

    /**
     * Create a reservation exception with localization based on the provided class. The additional
     * arguments are used for formatting the translated string.
     *
     * @param message the message (to translate)
     * @param clazz the class where the message was defined
     * @param args additional arguments used for formatting the localized message
     */
    public CalendarException(final String message, final Class<?> clazz, final Object... args) {
        super(message, clazz, args);
    }

    /**
     * Create a reservation exception with localization based on the provided class. The additional
     * arguments are used for formatting the translated string.
     *
     * @param message the message (to translate)
     * @param clazz the class where the message was defined
     * @param adminService the admin service for localization
     * @param args additional arguments used for formatting the localized message
     */
    public CalendarException(final String message, final Class<?> clazz, final AdminService adminService,
            final Object... args) {
        super(message, clazz, adminService, args);
    }

    /**
     * Create a reservation exception with localization based on the provided class. The additional
     * arguments are used for formatting the translated string.
     *
     * @param message the message (to translate)
     * @param cause the causing exception
     * @param clazz the class where the message was defined
     * @param args additional arguments used for formatting the localized message
     */
    public CalendarException(final String message, final Exception cause, final Class<?> clazz,
            final Object... args) {
        super(message, clazz, args);
        initCause(cause);
    }

    /**
     * Create a reservation exception with localization based on the provided class. The additional
     * arguments are used for formatting the translated string.
     *
     * @param message the message (to translate)
     * @param cause the causing exception
     * @param clazz the class where the message was defined
     * @param adminService the admin service for localization
     * @param args additional arguments used for formatting the localized message
     */
    public CalendarException(final String message, final Exception cause, final Class<?> clazz,
            final AdminService adminService, final Object... args) {
        super(message, clazz, adminService, args);
        initCause(cause);
    }

}
