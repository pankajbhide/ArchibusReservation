package com.archibus.app.reservation.exchange.service;

import java.util.*;

import org.apache.log4j.Logger;

import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.exchange.domain.UpdateType;
import com.archibus.app.reservation.exchange.util.AppointmentEquivalenceChecker;
import com.archibus.app.reservation.service.ICalendarDisconnectService;
import com.archibus.app.reservation.util.EmailNotificationHelper;
import com.archibus.utility.StringUtil;

import microsoft.exchange.webservices.data.*;

/**
 * Provides Calendar information from a Exchange Service. Represents services to create and update
 * appointments.
 * <p>
 *
 * Managed by Spring
 *
 * @author Yorik Gerlo
 * @since 23.2
 *
 */
public class ExchangeDisconnectService implements ICalendarDisconnectService {


    /**  Message displayed to the user if a meeting series is not found for disconnecting. */
    // @translatable
    private static final String SERIES_NOT_FOUND_FOR_DISCONNECT = "Appointment series linked to reservation {0} not found for removing location.";

    /** Exchange 2007 major version number. */
    private static final int EXCHANGE_2007_MAJOR_VERSION = 8;

    /** The logger. */
    private final Logger logger = Logger.getLogger(this.getClass());

    /** The helper for binding to Exchange Appointments. */
    private AppointmentBinder appointmentBinder;

    /** The helper for handling Exchange Appointments. */
    private AppointmentHelper appointmentHelper;

    /** Service that creates and sends translated reservation messages. */
    private ExchangeMessagesService exchangeMessagesService;

    /** {@inheritDoc} */
    @Override
    public void disconnectAppointment(final IReservation reservation, final String message,
            final boolean notifyOrganizer) {
        final String uniqueId = reservation.getUniqueId();
        if (StringUtil.isNullOrEmpty(uniqueId)) {
            // not linked to an appointment, so return
            return;
        }

        final Appointment appointment =
                this.appointmentBinder.bindToAppointment(reservation.getEmail(), uniqueId);
        if (appointment == null) {
            // @translatable
            throw new CalendarException(
                "Appointment linked to reservation {0} not found for removing location.",
                ExchangeDisconnectService.class, this.exchangeMessagesService.getAdminService(),
                reservation.getReserveId());
        } else {
            this.disconnectAppointmentImpl(appointment, reservation);
            if (notifyOrganizer) {
                EmailNotificationHelper.sendNotifications(reservation.getReserveId(), null, message);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void disconnectAppointmentOccurrence(final IReservation reservation, final String message,
            final boolean notifyOrganizer) {
        this.disconnectAppointmentOccurrenceImpl(reservation, message, notifyOrganizer, true);
    }

    /**
     * Implementation for disconnecting an appointment occurrence from its
     * reservation. Can also be used internally when disconnecting a whole
     * series, with or without removing the reservation id from the master.
     *
     * @param reservation
     *            the room reservation
     * @param message
     *            room cancellation message
     * @param notifyOrganizer
     *            whether to notify the organizer in a separate email
     * @param updateLinkedReservationIds
     *            whether to remove this occurrence from the linked reservation
     *            IDs in the master appointment
     */
    private void disconnectAppointmentOccurrenceImpl(final IReservation reservation, final String message,
            final boolean notifyOrganizer, final boolean updateLinkedReservationIds) {
        final Appointment master = this.appointmentBinder.bindToAppointment(reservation.getEmail(),
                reservation.getUniqueId());
        if (master == null) {
            throw new SeriesNotFoundException(SERIES_NOT_FOUND_FOR_DISCONNECT, ExchangeDisconnectService.class,
                    this.exchangeMessagesService.getAdminService(), reservation.getReserveId());
        }
        if (updateLinkedReservationIds && this.appointmentBinder.getAppointmentPropertiesHelper()
                .removeFromRecurringReservationIds(master, reservation)) {
            this.appointmentHelper.saveUpdatedAppointment(master, SendInvitationsOrCancellationsMode.SendToNone);
        }

        final Appointment appointment = this.appointmentBinder.bindToOccurrence(master.getService(),
            reservation, master);
        if (appointment == null) {
            // @translatable
            throw new CalendarException("Occurrence linked to reservation {0} not found for removing location.",
                    ExchangeDisconnectService.class, this.exchangeMessagesService.getAdminService());
        }

        this.disconnectAppointmentImpl(appointment, reservation);

        if (notifyOrganizer) {
            EmailNotificationHelper.sendNotifications(reservation.getReserveId(), null, message);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void disconnectAppointmentSeries(final RoomReservation reservation, final String message,
            final boolean notifyOrganizer) {
        final Appointment appointment = this.appointmentBinder.bindToAppointment(reservation.getEmail(),
                reservation.getUniqueId());

        if (appointment == null) {
            throw new SeriesNotFoundException(SERIES_NOT_FOUND_FOR_DISCONNECT, ExchangeDisconnectService.class,
                    this.exchangeMessagesService.getAdminService(), reservation.getReserveId());
        }

        try {
            final List<Integer> failedReservationIds = new ArrayList<Integer>();
            if (this.appointmentHelper.hasNoExceptions(appointment)) {
                this.disconnectAppointmentImpl(appointment, reservation);
            } else  {
                if (appointment.getService().getServerInfo().getMajorVersion() > EXCHANGE_2007_MAJOR_VERSION) {
                    /*
                     * KB 3050736 don't do series update on Exchange 2007 if it has exceptions. The
                     * series update would arrive as 'not supported calendar message.ics' for
                     * external attendees.
                     */
                    this.disconnectAppointmentImpl(appointment, reservation);
                }

                for (final RoomReservation occurrence : reservation.getCreatedReservations()) {
                    try {
                        this.disconnectAppointmentOccurrenceImpl(occurrence, message, false, false);
                    } catch (final CalendarException exception) {
                        this.logger.warn("Error removing location from appointment occurrence "
                                + occurrence.getOccurrenceIndex(),
                            exception);
                        failedReservationIds.add(occurrence.getReserveId());
                    }
                }
            }
            if (notifyOrganizer) {
                EmailNotificationHelper.sendNotifications(null, reservation.getParentId(), message);
            }
            if (!failedReservationIds.isEmpty()) {
                // @translatable
                throw new CalendarException(
                        "Could not remove location from {0} occurrences of the recurring meeting. Please refer to archibus.log for details",
                        ExchangeDisconnectService.class, this.exchangeMessagesService.getAdminService(),
                        failedReservationIds.size());
            }
        } catch (final ServiceLocalException exception) {
            // @translatable
            throw new CalendarException(
                    "Error removing location from appointment series. Please refer to archibus.log for details",
                    exception, ExchangeDisconnectService.class, this.exchangeMessagesService.getAdminService());
        }
    }

    /**
     * Sets the appointment helper.
     *
     * @param appointmentHelper the new appointment helper
     */
    public void setAppointmentHelper(final AppointmentHelper appointmentHelper) {
        this.appointmentHelper = appointmentHelper;
    }

    /**
     * Sets the appointment binder to be used for binding to appointments on the calendar.
     *
     * @param appointmentBinder the new appointment binder
     */
    public void setAppointmentBinder(final AppointmentBinder appointmentBinder) {
        this.appointmentBinder = appointmentBinder;
    }

    /**
     * Set the Exchange messages service.
     *
     * @param exchangeMessagesService the new Exchange messages service
     */
    public void setExchangeMessagesService(final ExchangeMessagesService exchangeMessagesService) {
        this.exchangeMessagesService = exchangeMessagesService;
    }

    /**
     * Remove the location from the given appointment.
     * @param appointment the appointment to remove the location from
     * @param reservation the corresponding reservation
     * @throws CalendarException when an error occurs
     */
    private void disconnectAppointmentImpl(final Appointment appointment, final IReservation reservation)
            throws CalendarException {
        try {
            final ICalendarEvent event = AppointmentEquivalenceChecker.toCalendarEvent(appointment);
            final UpdateType updateType = this.appointmentHelper.disconnectAppointment(reservation, event, appointment);

            if (updateType != UpdateType.NONE) {
                this.appointmentHelper.saveUpdatedAppointment(appointment,
                        this.appointmentHelper.getSendMode(appointment, updateType));
            }
        } catch (final ServiceLocalException exception) {
            // @translatable
            throw new CalendarException(
                "Error removing location from appointment. Please refer to archibus.log for details", exception,
                ExchangeDisconnectService.class, this.exchangeMessagesService.getAdminService());
        }
    }

}
