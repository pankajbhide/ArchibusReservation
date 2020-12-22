package com.archibus.app.reservation.exchange.service;

import microsoft.exchange.webservices.data.ExchangeService;

import com.archibus.app.reservation.domain.IReservation;
import com.archibus.app.reservation.exchange.util.AppointmentPropertiesHelper;

/**
 * Contains common dependencies for services that work on Exchange Appointments.
 *
 * @author Yorik Gerlo
 * @since Bali3
 */
public abstract class AbstractAppointmentHelper {

    /** The Exchange Service helper provides a connection to Exchange. */
    private ExchangeServiceHelper serviceHelper;

    /** The helper that provides access to the user properties used for ARCHIBUS reservations. */
    private AppointmentPropertiesHelper appointmentPropertiesHelper;

    /** Exchange service for reusing a connection. */
    private ExchangeService cachedExchangeService;

    /** Email address for whom the cached connection was created. */
    private String cachedOrganizerEmail;

    /**
     * Initialize an Exchange Service for connecting to Exchange. Reuse the cached service if
     * possible.
     *
     * @param reservation the reservation
     * @return the initialized Exchange service
     */
    public ExchangeService getInitializedService(final IReservation reservation) {
        return this.getInitializedService(reservation.getEmail());
    }

    /**
     * Initialize an Exchange Service for connecting to Exchange. Reuse the cached service if
     * possible.
     *
     * @param email the organizer's email
     * @return the initialized Exchange service
     */
    public ExchangeService getInitializedService(final String email) {
        // reuse the cached exchange service if possible
        ExchangeService initializedService = null;
        if (this.cachedExchangeService == null || this.cachedOrganizerEmail == null
                || !this.cachedOrganizerEmail.equals(email)) {
            initializedService = this.serviceHelper.initializeService(email);
        } else {
            initializedService = this.cachedExchangeService;
        }
        return initializedService;
    }

    /**
     * Update the cached Exchange service to the given service. A next call to getInitializedService
     * will reuse the cached Exchange service if possible.
     *
     * @param exchangeService the Exchange service to cache
     * @param email the organizer's email address
     */
    public void updateCachedExchangeService(final ExchangeService exchangeService,
            final String email) {
        this.cachedExchangeService = exchangeService;
        this.cachedOrganizerEmail = email;
    }

    /**
     * Sets the appointment properties helper.
     *
     * @param appointmentPropertiesHelper the new appointment properties helper
     */
    public void setAppointmentPropertiesHelper(
            final AppointmentPropertiesHelper appointmentPropertiesHelper) {
        this.appointmentPropertiesHelper = appointmentPropertiesHelper;
    }

    /**
     * Sets the service helper.
     *
     * @param serviceHelper the new service helper
     */
    public void setServiceHelper(final ExchangeServiceHelper serviceHelper) {
        this.serviceHelper = serviceHelper;
    }

    /**
     * Get the appointment properties helper.
     *
     * @return the appointment properties helper
     */
    public AppointmentPropertiesHelper getAppointmentPropertiesHelper() {
        return this.appointmentPropertiesHelper;
    }

    /**
     * Get the Exchange Service Helper.
     *
     * @return the service helper
     */
    public ExchangeServiceHelper getServiceHelper() {
        return this.serviceHelper;
    }

}