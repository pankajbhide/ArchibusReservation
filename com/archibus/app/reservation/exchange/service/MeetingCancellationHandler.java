package com.archibus.app.reservation.exchange.service;

import java.util.*;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.service.CancelReservationService;

import microsoft.exchange.webservices.data.*;

/**
 * Can handle meeting cancellations from the Reservations Mailbox on Exchange. Managed by Spring.
 *
 * @author Yorik Gerlo
 * @since 21.2
 */
public class MeetingCancellationHandler extends MeetingItemHandler {

    /** Error message that indicates something went wrong handling a cancellation message. */
    private static final String ITEM_ERROR = "Error handling meeting cancellation";

    /** The cancel reservation service. */
    private CancelReservationService cancelReservationService;

    /**
     * Sets the cancel reservation service for handling events from Exchange.
     *
     * @param cancelReservationService the cancel reservation service for handling Exchange events
     */
    public void setCancelReservationService(
            final CancelReservationService cancelReservationService) {
        this.cancelReservationService = cancelReservationService;
    }

    /**
     * Handle a meeting cancellation.
     *
     * @param cancellation the meeting cancellation
     */
    public void handleMeetingCancellation(final MeetingCancellation cancellation) {
        try {
            cancellation.load(new PropertySet(MeetingMessageSchema.AssociatedAppointmentId,
                MeetingMessageSchema.ICalUid, MeetingMessageSchema.ICalRecurrenceId));

            final ItemId appointmentId = (ItemId) cancellation
                .getObjectFromPropertyDefinition(MeetingMessageSchema.AssociatedAppointmentId);
            final String uniqueId = (String) cancellation
                .getObjectFromPropertyDefinition(MeetingMessageSchema.ICalUid);
            if (appointmentId == null) {
                /*
                 * Check the WebC database for any reservation with this unique id to get the
                 * correct organizer address.
                 */
                final List<RoomReservation> reservations = this.reservationService
                    .getByUniqueId(uniqueId, null, Constants.TIMEZONE_UTC);
                if (!reservations.isEmpty()) {
                    final String organizerEmail = reservations.get(0).getEmail();
                    ItemHandlerImpl.setUserFromEmail(organizerEmail, cancellation,
                            this.messagesService.getAdminService());
                    final Date occurrenceDateTime = (Date) cancellation
                        .getObjectFromPropertyDefinition(MeetingMessageSchema.ICalRecurrenceId);
                    /*
                     * If the organizer's calendar still has a master, cancel only the occurrence
                     * specified in the message. Otherwise cancel all occurrences.
                     */
                    cancelReservationOccurrence(occurrenceDateTime, uniqueId, organizerEmail,
                        cancellation);
                }
            } else {
                final Appointment appointment =
                        Appointment.bind(cancellation.getService(), appointmentId);
                final String organizerEmail = appointment.getOrganizer().getAddress();
                ItemHandlerImpl.setUserFromEmail(organizerEmail, cancellation, this.messagesService.getAdminService());

                if (AppointmentType.RecurringMaster.equals(appointment.getAppointmentType())
                        || AppointmentType.Single.equals(appointment.getAppointmentType())) {
                    // For Master appointments, cancel the whole series. For single appointments,
                    // also cancel all reservations in the database with this unique ID.
                    cancelReservationsByUniqueId(cancellation, uniqueId, organizerEmail, null);
                } else {
                    cancelReservationOccurrence(appointment.getICalRecurrenceId(), uniqueId,
                        organizerEmail, cancellation);
                }
            }
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API
            // method throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            throw new CalendarException(ITEM_ERROR, exception, MeetingCancellationHandler.class,
                    this.messagesService.getAdminService());
        }

        this.removeFromExchange(cancellation);
    }

    /**
     * Remove the cancellation and it's meeting from Exchange.
     *
     * @param cancellation the cancellation to remove
     */
    private void removeFromExchange(final MeetingCancellation cancellation) {
        // KB 3042136 for Exchange 2013 compatibility, catch errors cleaning up in Exchange
        // separately.
        try {
            cancellation.removeMeetingFromCalendar();
            cancellation.delete(DeleteMode.HardDelete);
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API
            // method throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            this.logger.debug("Error removing cancelled meeting from the resourceAccount calendar",
                exception);
        }
    }

    /**
     * Handle the cancellation of a single meeting or of a recurring meeting series.
     *
     * @param cancellation the cancellation message
     * @param uniqueId unique id of the meeting
     * @param organizerEmail email of the organizer
     * @param conferenceId the conference identifier
     */
    private void cancelReservationsByUniqueId(final MeetingCancellation cancellation,
            final String uniqueId, final String organizerEmail, final Integer conferenceId) {
        // Get the currently active reservations in the database.
        final List<RoomReservation> reservations =
                this.reservationService.getByUniqueId(uniqueId, conferenceId, null);
        final List<? extends IReservation> failures = this.cancelReservationService
            .cancelRoomReservationsByUniqueId(uniqueId, organizerEmail, conferenceId, false);
        if (!failures.isEmpty()) {
            // Cancel anyway. The failures are disconnected from the Exchange appointment.
            this.cancelReservationService.cancelRoomReservationsByUniqueId(uniqueId, organizerEmail,
                conferenceId, true);
        }

        // Build a list of reservation IDs for the failures.
        final List<Integer> failureIds = new ArrayList<Integer>(failures.size());
        for (final IReservation failure : failures) {
            failureIds.add(failure.getReserveId());
        }

        final List<RoomReservation> cancelledReservations = new ArrayList<RoomReservation>();
        for (final RoomReservation reservation : reservations) {
            if (!failureIds.contains(reservation.getReserveId())) {
                cancelledReservations.add(reservation);
            }
        }

        // Send a success message for the reservations successfully cancelled.
        for (final RoomReservation cancelledReservation : cancelledReservations) {
            this.messagesService.sendCancelledConfirmation(cancellation, cancelledReservation);
        }

        // Send a failure message for the reservations that could not be cancelled.
        for (final IReservation remainingReservation : failures) {
            this.messagesService.sendCancelledFailure(cancellation,
                (RoomReservation) remainingReservation);
        }
    }

    /**
     * Cancel a single reservation occurrence. If the series master does not exist on the
     * organizer's calendar, cancel all reservations instead.
     *
     * @param iCalRecurrenceId original date and time of the appointment being cancelled
     * @param uniqueId unique id of the appointment series
     * @param organizerEmail organizer of the appointment series
     * @param cancellation the meeting cancellation message
     */
    private void cancelReservationOccurrence(final Date iCalRecurrenceId, final String uniqueId,
            final String organizerEmail, final MeetingCancellation cancellation) {
        try {
            final Appointment organizerMasterAppointment =
                    this.appointmentBinder.bindToAppointment(organizerEmail, uniqueId);
            if (organizerMasterAppointment == null) {
                // The master appointment is not on the organizer's calendar. This means we
                // should cancel all connected reservations.
                cancelReservationsByUniqueId(cancellation, uniqueId, organizerEmail, null);
            } else {
                // Cancel only the reservation(s) corresponding to the occurrence.
                final Map<Date, Integer> reservationIds =
                        this.appointmentBinder.getAppointmentPropertiesHelper()
                            .getRecurringReservationIds(organizerMasterAppointment);
                final Integer reservationId =
                        reservationIds.get(TimePeriod.clearTime(iCalRecurrenceId));
                if (reservationId != null) {
                    cancelReservationById(uniqueId, organizerEmail, cancellation, reservationId);
                }
            }
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: Exchange third-party
            // API method throws a checked Exception, which should be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            throw new CalendarException(ITEM_ERROR, exception, MeetingCancellationHandler.class,
                    this.messagesService.getAdminService());
        }
    }

    /**
     * Cancel a single occurrence for which we found the specified reservation id.
     *
     * @param uniqueId the unique id of the meeting
     * @param organizerEmail organizer's email
     * @param cancellation the cancellation message
     * @param reservationId the reservation id saved in the meeting
     */
    private void cancelReservationById(final String uniqueId, final String organizerEmail,
            final MeetingCancellation cancellation, final Integer reservationId) {
        final RoomReservation roomReservation =
                this.reservationService.getActiveReservation(reservationId, null);
        if (roomReservation != null) {
            if (roomReservation.getConferenceId() == null
                    || roomReservation.getConferenceId() == 0) {
                try {
                    this.cancelReservationService.cancelReservation(roomReservation, null);
                    this.messagesService.sendCancelledConfirmation(cancellation, roomReservation);
                } catch (final ReservationException exception) {
                    // Send a cancellation failure message.
                    this.messagesService.sendCancelledFailure(cancellation, roomReservation);
                    // If the send succeeded, remove the meeting from the calendar before
                    // rethrowing.
                    this.removeFromExchange(cancellation);
                    throw new ReservationException(
                        "Cancelling reservation failed, organizer has been notified", exception,
                        MeetingCancellationHandler.class, this.messagesService.getAdminService());
                }
            } else {
                this.cancelReservationsByUniqueId(cancellation, uniqueId, organizerEmail,
                    roomReservation.getConferenceId());
            }
        }
    }

}
