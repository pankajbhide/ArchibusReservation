package com.archibus.app.reservation.exchange.service;

import java.util.*;

import org.apache.log4j.Logger;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.exchange.domain.UpdateType;
import com.archibus.app.reservation.exchange.util.AppointmentEquivalenceChecker;
import com.archibus.app.reservation.service.ICalendarService;
import com.archibus.app.reservation.util.*;
import com.archibus.utility.*;

import microsoft.exchange.webservices.data.*;

/**
 * Provides Calendar information from a Exchange Service. Represents services to create and update
 * appointments.
 * <p>
 *
 * Managed by Spring
 *
 * @author Bart Vanderschoot
 * @since 21.2
 *
 */
public class ExchangeCalendarService implements ICalendarService {

    /** Exchange 2007 major version number. */
    private static final int EXCHANGE_2007_MAJOR_VERSION = 8;

    /**
     * Message displayed to the user for each occurrence not found on the calendar when updating one
     * or more occurrences of a recurring meeting.
     */
    // @translatable
    private static final String OCCURRENCE_NOT_FOUND =
            "The meeting occurrence linked to reservation {0} was not found on the calendar. The reservation was removed from the recurrence and linked to a new single meeting.";

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
    public String createAppointment(final IReservation reservation) throws ExceptionBase {
        if (StringUtil.notNullOrEmpty(reservation.getUniqueId())) {
            throw new CalendarException("Reservation already has an appointment id.",
                ExchangeCalendarService.class, this.exchangeMessagesService.getAdminService());
        }

        final Appointment appointment = this.appointmentBinder.createAppointment(reservation);
        this.appointmentHelper.updateAppointment(reservation, appointment);

        // Remove location from conflicted occurrences.
        if (reservation instanceof RoomReservation
                && ((RoomReservation) reservation).getCreatedReservations() != null) {
            clearLocationForConflicts(appointment,
                ((RoomReservation) reservation).getCreatedReservations());
        }

        return reservation.getUniqueId();
    }

    /** {@inheritDoc} */
    @Override
    public void updateAppointment(final IReservation reservation) throws ExceptionBase {
        updateAppointmentImpl(reservation);
    }

    /**
     * Update the appointment linked to the given reservation and return which type of update was
     * applied.
     *
     * @param reservation the reservation for which to update the appointment
     * @return the update type that was applied
     * @throws ExceptionBase when an error occurs accessing the calendar
     */
    private UpdateType updateAppointmentImpl(final IReservation reservation) throws ExceptionBase {
        try {
            final Appointment appointment = this.appointmentBinder.createAppointment(reservation);
            UpdateType updateType = null;

            if (appointment.isNew()) {
                // The appointment with the given id no longer exists, so create
                // a new one and update the ID.
                this.appointmentHelper.updateAppointment(reservation, appointment);
                updateType = UpdateType.FULL;
            } else {
                final ICalendarEvent event =
                        AppointmentEquivalenceChecker.toCalendarEvent(appointment);
                updateType = this.appointmentHelper.adjustUpdateTypeForLocation(reservation,
                    appointment, AppointmentEquivalenceChecker.getUpdateType(reservation, event));

                if (UpdateType.NONE.equals(updateType)) {
                    this.logger
                        .debug("Not updating linked appointment because it's still equivalent.");
                } else if (UpdateType.FULL.equals(updateType)) {
                    this.appointmentHelper.updateAppointment(reservation, appointment);
                } else {
                    this.appointmentHelper.updateAppointmentExceptTime(reservation, event,
                        appointment);
                }
            }
            return updateType;
        } catch (final ServiceLocalException exception) {
            // @translatable
            throw new CalendarException(
                "Error updating appointment. Please refer to archibus.log for details", exception,
                ExchangeCalendarService.class, this.exchangeMessagesService.getAdminService());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void updateAppointmentOccurrence(final IReservation reservation,
            final IReservation originalReservation) {
        final Appointment master = this.appointmentBinder.bindToAppointment(reservation.getEmail(),
            reservation.getUniqueId());

        if (master == null) {
            // @translatable
            throw new SeriesNotFoundException(
                "Appointment series linked to reservation {0} not found for updating.",
                ExchangeCalendarService.class, this.exchangeMessagesService.getAdminService(),
                reservation.getReserveId());
        }

        final Appointment appointment = this.appointmentBinder.bindToOccurrence(master.getService(),
            originalReservation, master);
        if (appointment == null) {
            this.logger.debug(
                "Occurrence not found for updating. Create a new single meeting for reservation "
                        + reservation.getReserveId());
            ReservationUtils.removeRecurrence(reservation);
            reservation.setUniqueId("");
            this.updateAppointment(reservation);

            final String localizedMessage = ReservationsContextHelper.localizeString(OCCURRENCE_NOT_FOUND,
                    ExchangeCalendarService.class, this.exchangeMessagesService.getAdminService(),
                    reservation.getReserveId());
            ReservationsContextHelper.appendResultError(localizedMessage);
        } else {
            try {
                final ICalendarEvent event =
                        AppointmentEquivalenceChecker.toCalendarEvent(appointment);
                final UpdateType updateType =
                        this.appointmentHelper.adjustUpdateTypeForLocation(reservation, appointment,
                            AppointmentEquivalenceChecker.getUpdateType(reservation, event));

                if (UpdateType.NONE.equals(updateType)) {
                    this.logger
                        .debug("Not updating meeting occurrence because it's still equivalent.");
                } else if (UpdateType.FULL.equals(updateType)) {
                    this.appointmentHelper.updateAppointment(reservation, appointment);
                } else {
                    this.appointmentHelper.updateAppointmentExceptTime(reservation, event,
                        appointment);
                }
            } catch (final ServiceLocalException exception) {
                // @translatable
                throw new CalendarException(
                    "Error updating appointment occurrence. Please refer to archibus.log for details",
                    exception, ExchangeCalendarService.class, this.exchangeMessagesService.getAdminService());
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void updateAppointmentSeries(final RoomReservation reservation,
            final List<RoomReservation> originalReservations) {

        final Appointment appointment = this.appointmentBinder.createAppointment(reservation);

        try {
            if (appointment.isNew()) {
                /*
                 * Recreating a recurring reservation is not supported at this service level.
                 * Specifically this requires significant implementation effort if the reservations
                 * don't match the original pattern any more.
                 */
                // @translatable
                throw new SeriesNotFoundException(
                    "Appointment series linked to recurring reservation {0} not found for updating.",
                    ExchangeCalendarService.class, this.exchangeMessagesService.getAdminService(),
                    reservation.getParentId());
            } else {
                final TimePeriod masterTimePeriod = new TimePeriod(appointment.getStart(),
                    appointment.getEnd(), Constants.TIMEZONE_UTC);
                // copy the original reservation time period to restore later on
                final TimePeriod originalTimePeriod = new TimePeriod(reservation.getTimePeriod());
                // Set the requested time zone so we don't change the series time zone to UTC.
                if (StringUtil.isNullOrEmpty(reservation.getRequestedTimeZone())) {
                    reservation.setRequestedTimeZone(originalTimePeriod.getTimeZone());
                }
                final TimePeriod reservationTimePeriod = ReservationUtils.getUtcTimePeriodForDate(
                    reservation, masterTimePeriod.getStartDate(), originalTimePeriod);

                UpdateType updateType = null;

                if (this.appointmentHelper.hasNoExceptions(appointment)) {
                    // set the time period for the master so the dates don't change
                    reservation.setTimePeriod(reservationTimePeriod);
                    updateType = this.updateAppointmentImpl(reservation);
                    // restore the actual time period of the reservation
                    reservation.setTimePeriod(originalTimePeriod);
                } else if (appointment.getService().getServerInfo()
                    .getMajorVersion() > EXCHANGE_2007_MAJOR_VERSION) {
                    /*
                     * KB 3050736 don't do series update on Exchange 2007 if it has exceptions. The
                     * series update would arrive as 'not supported calendar message.ics' for
                     * external attendees.
                     */
                    updateType = this.appointmentHelper.updateAppointmentExceptTime(reservation,
                        AppointmentEquivalenceChecker.toCalendarEvent(appointment), appointment);
                    // After updating the series without changing the time, update each occurrence
                    // separately. Time changes are applied per occurrence since updating the time
                    // of the series also removes exceptions.
                }
                if (!UpdateType.FULL.equals(updateType)) {
                    // KB 3049352: even if the times of the master are the same, still check the
                    // times for all occurrences. This is to detect time changes due to different
                    // DST rules in different time zones.
                    this.updateAppointmentOccurrences(reservation,
                        reservation.getCreatedReservations(), originalReservations);
                }
            }
        } catch (final ServiceLocalException exception) {
            // @translatable
            throw new CalendarException(
                "Error updating appointment series. Please refer to archibus.log for details",
                exception, ExchangeCalendarService.class, this.exchangeMessagesService.getAdminService());
        }
    }

    /**
     * Update the individual appointment occurrences.
     *
     * @param reservation the primary reservation
     * @param createdReservations the created reservations for which we need to update the
     *            corresponding occurrences
     * @param originalReservations the original reservations matching the created reservations
     */
    private void updateAppointmentOccurrences(final RoomReservation reservation,
            final List<RoomReservation> createdReservations,
            final List<? extends IReservation> originalReservations) {
        final List<Integer> failedReservationIds = new ArrayList<Integer>();

        for (int index = 0; index < originalReservations.size(); ++index) {
            final RoomReservation createdReservation = createdReservations.get(index);
            createdReservation.setTimeZone(reservation.getTimeZone());
            try {
                this.updateAppointmentOccurrence(createdReservation,
                    originalReservations.get(index));
            } catch (final CalendarException exception) {
                this.logger.warn("Error updating appointment occurrence "
                        + createdReservation.getOccurrenceIndex(),
                    exception);
                failedReservationIds.add(createdReservation.getReserveId());
            }
        }

        if (!failedReservationIds.isEmpty()) {
            // @translatable
            throw new CalendarException(
                "Could not update {0} occurrences of the recurring meeting. Please refer to archibus.log for details",
                ExchangeCalendarService.class, this.exchangeMessagesService.getAdminService(),
                failedReservationIds.size());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void cancelAppointment(final IReservation reservation, final String message,
            final boolean notifyOrganizer) throws ExceptionBase {

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
                "Appointment linked to reservation {0} not found for cancelling.",
                ExchangeCalendarService.class, this.exchangeMessagesService.getAdminService(),
                reservation.getReserveId());
        } else {
            this.cancelAppointmentImpl(appointment, reservation, message, notifyOrganizer);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void cancelAppointmentOccurrence(final IReservation reservation, final String message,
            final boolean notifyOrganizer) throws ExceptionBase {
        final String uniqueId = reservation.getUniqueId();
        if (StringUtil.isNullOrEmpty(uniqueId)) {
            // not linked to an appointment, so return
            return;
        }

        final ExchangeService initializedService =
                this.appointmentBinder.getInitializedService(reservation);
        final Appointment appointment =
                this.appointmentBinder.bindToOccurrence(initializedService, reservation, null);
        if (appointment == null) {
            // @translatable
            throw new CalendarException(
                "Appointment occurrence linked to reservation {0} not found for cancelling.",
                ExchangeCalendarService.class, this.exchangeMessagesService.getAdminService(),
                reservation.getReserveId());
        } else {
            this.cancelAppointmentImpl(appointment, reservation, message, notifyOrganizer);
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
     * Clear the location string from the conflicted occurrences.
     *
     * @param master the master appointment
     * @param createdReservations the created reservations
     */
    private void clearLocationForConflicts(final Appointment master,
            final List<RoomReservation> createdReservations) {

        for (final RoomReservation reservation : createdReservations) {
            if (reservation.getRoomAllocations().isEmpty()
                    || reservation.hasRoomConflictInConferenceCall()) {
                final Appointment appointment = this.appointmentBinder
                    .bindToOccurrence(master.getService(), reservation, master);
                try {
                    appointment.setLocation(
                        this.appointmentHelper.getSpaceService().getLocationString(reservation));

                    this.appointmentHelper.saveUpdatedAppointment(appointment,
                        this.appointmentHelper.getSendModeForRoomConflict(appointment,
                            reservation.getEmail()));
                    // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party
                    // API method throws a checked Exception, which needs to be wrapped in
                    // ExceptionBase.
                } catch (final Exception exception) {
                    // CHECKSTYLE:ON
                    this.logger.warn("Error removing the location from occurrence "
                            + reservation.getOccurrenceIndex(),
                        exception);
                    // @translatable
                    throw new CalendarException(
                        "The location could not be removed from all conflicted meeting occurrences in Exchange.",
                        exception, ExchangeCalendarService.class, this.exchangeMessagesService.getAdminService());
                }
            }
        }
    }

    /**
     * Cancel the given appointment and notify the organizer.
     *
     * @param appointment the appointment to cancel
     * @param reservation the reservation linked to the appointment
     * @param message the message to include in the cancellation
     * @param notifyOrganizer whether to send a cancel notification to the organizer
     * @throws CalendarException when an error occurs
     */
    private void cancelAppointmentImpl(final Appointment appointment,
            final IReservation reservation, final String message, final boolean notifyOrganizer)
                    throws CalendarException {
        try {
            /*
             * KB 3041208: use delete when invitations have not been sent. Exchange server will
             * report an error if we use 'cancelMeeting' for an appointment without attendees.
             */
            if (appointment.getMeetingRequestWasSent()) {
                if (StringUtil.isNullOrEmpty(message)) {
                    appointment.cancelMeeting();
                } else {
                    appointment.cancelMeeting(message);
                }
            } else {
                appointment.delete(DeleteMode.MoveToDeletedItems, SendCancellationsMode.SendToNone);
            }
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API
            // method throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            // @translatable
            throw new CalendarException(
                "Error cancelling appointment. Please refer to archibus.log for details", exception,
                ExchangeCalendarService.class, this.exchangeMessagesService.getAdminService());
        }
        if (notifyOrganizer) {
            this.exchangeMessagesService.sendCancelNotification(reservation, message, appointment,
                this.appointmentHelper.getServiceHelper());
        }
    }

    /**lbnl - Brent Hopkins - send a cancel email to any attendees removed from a reservation **/
    @Override
    public void lbnlCancelCalendarEventHelp(RoomReservation reservation, RoomReservation origReserv, boolean allRecurrences, String message)
            throws ExceptionBase {
        return; //this should never be called
    }

}
