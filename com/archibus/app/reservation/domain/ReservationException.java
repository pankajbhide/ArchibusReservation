package com.archibus.app.reservation.domain;

import com.archibus.app.reservation.util.ReservationsContextHelper;
import com.archibus.service.remoting.AdminService;
import com.archibus.utility.ExceptionBase;

/**
 * Base class for Reservation Exception.
 *
 * @author Bart Vanderschoot
 *
 */
public class ReservationException extends ExceptionBase {

    /** serializable. */
    private static final long serialVersionUID = 1L;

    /**
     * Create a reservation exception with localization based on the provided class.
     *
     * @param message the message (to translate)
     * @param cause the causing exception
     * @param clazz the class where the message was defined
     */
    public ReservationException(final String message, final Exception cause, final Class<?> clazz) {
        this(message, clazz);
        initCause(cause);
    }

    /**
     * Create a reservation exception with localization based on the provided class.
     *
     * @param message the message (to translate)
     * @param cause the causing exception
     * @param clazz the class where the message was defined
     * @param adminService the admin service used for localization
     */
    public ReservationException(final String message, final Exception cause, final Class<?> clazz,
            final AdminService adminService) {
        this(message, clazz, adminService);
        initCause(cause);
    }

    /**
     * Create a reservation exception with localization based on the provided class. The additional
     * arguments are used for formatting the translated string.
     *
     * @param message the message (to translate)
     * @param clazz the class where the message was defined
     * @param args additional arguments used for formatting the localized message
     */
    public ReservationException(final String message, final Class<?> clazz, final Object... args) {
        super(ReservationsContextHelper.localizeString(message, clazz, args));
        this.setForUser(true);
    }

    /**
     * Create a reservation exception with localization based on the provided class. The additional
     * arguments are used for formatting the translated string.
     *
     * @param message the message (to translate)
     * @param clazz the class where the message was defined
     * @param adminService the admin service used for localization
     * @param args additional arguments used for formatting the localized message
     */
    public ReservationException(final String message, final Class<?> clazz, final AdminService adminService,
            final Object... args) {
        super(ReservationsContextHelper.localizeString(message, clazz, adminService, args));
        this.setForUser(true);
    }

}
