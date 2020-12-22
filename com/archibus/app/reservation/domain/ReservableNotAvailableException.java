package com.archibus.app.reservation.domain;

import com.archibus.service.remoting.AdminService;

/**
 * Reservation exception that indicates an item is not available for reservation.
 *
 * @author Yorik Gerlo
 */
public class ReservableNotAvailableException extends ReservationException {

    /** Generated serial version ID. */
    private static final long serialVersionUID = -6483262706971726091L;

    /** The reservable that is not available. */
    private final IReservable reservable;

    /** The reservation id (if any). */
    private final Integer reservationId;

    /**
     * Create a reservation exception with localization based on the provided class. The additional
     * arguments are used for formatting the translated string.
     *
     * @param reservable the reservable that is not available
     * @param reservationId id of the reservation which could not be updated (if any)
     * @param message the message (to translate)
     * @param clazz the class where the message was defined
     * @param args additional arguments used for formatting the localized message
     */
    public ReservableNotAvailableException(final IReservable reservable,
            final Integer reservationId, final String message, final Class<?> clazz,
            final Object... args) {
        super(message, clazz, args);
        this.reservable = reservable;
        this.reservationId = reservationId;
    }

    /**
     * Create a reservation exception with localization based on the provided class. The additional
     * arguments are used for formatting the translated string.
     *
     * @param reservable the reservable that is not available
     * @param reservationId id of the reservation which could not be updated (if any)
     * @param message the message (to translate)
     * @param cause the causing exception
     * @param clazz the class where the message was defined
     * @param args additional arguments used for formatting the localized message
     */
    public ReservableNotAvailableException(final IReservable reservable,
            final Integer reservationId, final String message, final Exception cause,
            final Class<?> clazz, final Object... args) {
        this(reservable, reservationId, message, clazz, args);
        initCause(cause);
    }

    /**
     * Create a reservation exception with localization based on the provided class. The additional
     * arguments are used for formatting the translated string.
     *
     * @param reservable the reservable that is not available
     * @param reservationId id of the reservation which could not be updated (if any)
     * @param message the message (to translate)
     * @param clazz the class where the message was defined
     * @param adminService service for localization
     * @param args additional arguments used for formatting the localized message
     */
    public ReservableNotAvailableException(final IReservable reservable,
            final Integer reservationId, final String message, final Class<?> clazz,
            final AdminService adminService, final Object... args) {
        super(message, clazz, adminService, args);
        this.reservable = reservable;
        this.reservationId = reservationId;
    }

    /**
     * Create a reservation exception with localization based on the provided class. The additional
     * arguments are used for formatting the translated string.
     *
     * @param reservable the reservable that is not available
     * @param reservationId id of the reservation which could not be updated (if any)
     * @param message the message (to translate)
     * @param cause the causing exception
     * @param clazz the class where the message was defined
     * @param adminService the service for localization
     * @param args additional arguments used for formatting the localized message
     */
    public ReservableNotAvailableException(final IReservable reservable,
            final Integer reservationId, final String message, final Exception cause,
            final Class<?> clazz, final AdminService adminService, final Object... args) {
        this(reservable, reservationId, message, clazz, adminService, args);
        initCause(cause);
    }

    /**
     * Get the reservable that was the reason for the exception.
     *
     * @return the reservable that was the reason for the exception
     */
    public IReservable getReservable() {
        return this.reservable;
    }

    /**
     * Get the reservation id.
     *
     * @return the reservation id
     */
    public Integer getReservationId() {
        return this.reservationId;
    }

}
