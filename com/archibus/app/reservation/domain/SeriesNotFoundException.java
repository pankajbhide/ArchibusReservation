package com.archibus.app.reservation.domain;

import com.archibus.service.remoting.AdminService;

/**
 * A specific subclass of CalendarException that indicates a recurring meeting series is not found
 * by the calendar service. This specific error is reported through a specific exception to allow
 * specific handling of the error.
 *
 * @author Yorik Gerlo
 * @since 22.1
 */
public class SeriesNotFoundException extends CalendarException {

    /** Property: serialVersionUID. */
    private static final long serialVersionUID = 367655998972976801L;

    /**
     * Create a reservation exception with localization based on the provided class. The additional
     * arguments are used for formatting the translated string.
     *
     * @param message the message (to translate)
     * @param clazz the class where the message was defined
     * @param adminService the admin service used for localization
     * @param args additional arguments used for formatting the localized message
     */
    public SeriesNotFoundException(final String message, final Class<?> clazz, final AdminService adminService,
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
     * @param adminService the admin service used for localization
     * @param args additional arguments used for formatting the localized message
     */
    public SeriesNotFoundException(final String message, final Exception cause,
            final Class<?> clazz, final AdminService adminService, final Object... args) {
        super(message, cause, clazz, adminService, args);
    }

}
