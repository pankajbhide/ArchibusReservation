package com.archibus.app.reservation.exchange.service;

import org.apache.log4j.Logger;

import com.archibus.app.reservation.service.IConferenceAwareReservationService;

/**
 * Base class for handling Meeting Items from Exchange.
 * <p>
 * Used by Exchange Listener to handle meeting items. Managed by Spring, has prototype scope.
 *
 * @author Yorik Gerlo
 * @since 21.2
 */
public class MeetingItemHandler {

    /** The appointment binder can bind to appointments in Exchange. */
    protected AppointmentBinder appointmentBinder;

    /** The reservation service for accessing the Web Central reservations. */
    protected IConferenceAwareReservationService reservationService;

    /** The messages service that builds and sends messages to report actions taken by the listener. */
    protected ExchangeMessagesService messagesService;

    /** The logger. */
    protected final Logger logger = Logger.getLogger(this.getClass());

    /**
     * Set the new appointment binder.
     *
     * @param appointmentBinder the appointmentBinder to set
     */
    public void setAppointmentBinder(final AppointmentBinder appointmentBinder) {
        this.appointmentBinder = appointmentBinder;
    }

    /**
     * Set the new Reservation service.
     *
     * @param reservationService the reservationService to set
     */
    public void setReservationService(final IConferenceAwareReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * Set the new messages service.
     *
     * @param messagesService the messages service to set
     */
    public void setMessagesService(final ExchangeMessagesService messagesService) {
        this.messagesService = messagesService;
    }

}