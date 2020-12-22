package com.archibus.app.reservation.ics.service;

import java.util.List;

import com.archibus.app.reservation.dao.datasource.ConferenceCallReservationDataSource;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.service.ICalendarDisconnectService;
import com.archibus.app.reservation.util.EmailNotificationHelper;
import com.archibus.utility.*;

/**
 * The Class WebCentralCalendarService.
 */
public class IcsDisconnectService implements ICalendarDisconnectService {

    /** Whether to request a reply on ICS invitations. */
    private static final boolean REQUIRE_REPLY = true;

    /** The room reservation data source. */
    private ConferenceCallReservationDataSource reservationDataSource;

    /** The calendar message service for generating the ICS e-mails. */
    private CalendarMessageService calendarMessageService;

    /**
     * Get the conference id of the first occurrence in the series of the given reservation
     * occurrence.
     *
     * @param reservation the reservation
     * @return the conference id, or null if this isn't a conference call reservation
     */
    private Integer getMasterConferenceId(final IReservation reservation) {
        Integer conferenceId = reservation.getConferenceId();
        if (conferenceId != null && reservation.getParentId() != null) {
            conferenceId = this.reservationDataSource.getParentId(conferenceId);
        }
        return conferenceId;
    }

    /** {@inheritDoc} */
    @Override
    public void disconnectAppointment(final IReservation reservation, final String message,
            final boolean notifyOrganizer)
            throws ExceptionBase {
        if (StringUtil.isNullOrEmpty(reservation.getAttendees())) {
            // no attendees -> no meeting -> send the cancellation email as usual
            sendEmails(reservation, true, message);
        } else {
            this.calendarMessageService.disconnectInvitations((RoomReservation) reservation, false, REQUIRE_REPLY,
                    message, reservation.getConferenceId());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void disconnectAppointmentOccurrence(final IReservation reservation, final String message,
            final boolean notifyOrganizer) throws ExceptionBase {
        if (StringUtil.isNullOrEmpty(reservation.getAttendees())) {
            // no attendees -> no meeting -> send the cancellation email as usual
            sendEmails(reservation, false, message);
        } else {
            this.calendarMessageService.disconnectInvitations((RoomReservation) reservation, false, REQUIRE_REPLY,
                    message, this.getMasterConferenceId(reservation));
        }
    }

    /** {@inheritDoc} */
    @Override
    public void disconnectAppointmentSeries(final RoomReservation reservation, final String message,
            final boolean notifyOrganizer)
            throws ExceptionBase {
        if (StringUtil.isNullOrEmpty(reservation.getAttendees())) {
            // no attendees -> no meeting -> send the cancellation email as usual
            sendEmails(reservation, true, message);
        } else {
            final List<RoomReservation> createdReservations = reservation.getCreatedReservations();

            /*
             * Check if all reservations are still on their original date. In
             * this case we can update the full series with a single email.
             */
            boolean originalDates = true;
            for (final RoomReservation createdRes : createdReservations) {
                if (createdRes.getRecurringDateModified() > 0) {
                    originalDates = false;
                    break;
                }
            }
            if (originalDates) {
                this.calendarMessageService.disconnectInvitations(reservation, true, REQUIRE_REPLY, message,
                    this.getMasterConferenceId(reservation));
            } else {
                // disconnect each individual occurrence.
                for (final RoomReservation occurrence : reservation.getCreatedReservations()) {
                    this.disconnectAppointmentOccurrence(occurrence, message, true);
                }
            }
        }
    }

    /**
     * Sets the reservation data source.
     *
     * @param reservationDataSource the new reservation data source
     */
    public void setReservationDataSource(
            final ConferenceCallReservationDataSource reservationDataSource) {
        this.reservationDataSource = reservationDataSource;
    }

    /**
     * Sets the reservation data source.
     *
     * @param messageService the calendar message service
     */
    public final void setCalendarMessageService(final CalendarMessageService messageService) {
        this.calendarMessageService = messageService;
    }

    /**
     * Send the e-mail notifications.
     *
     * @param reservation the reservation to send a message for
     * @param allRecurrences true to send for all occurrences, false for only the given occurrence
     * @param message the message to include in the notification
     */
    private void sendEmails(final IReservation reservation,
            final boolean allRecurrences, final String message) {

        /*
         * At this point, allRecurrences could also be referring to a
         * non-recurring reservation. So check if the reservation
         * actually has a recurring rule.
         */
        final boolean recurring = allRecurrences && StringUtil
                .notNullOrEmpty(reservation.getRecurringRule());

        Integer parentId = null;
        if (recurring) {
            parentId = reservation.getParentId();
        }

        // TODO check if we need to use conference id (of the master)
        // the helper checks whether email notifications are enabled
        EmailNotificationHelper.sendNotifications(reservation.getReserveId(), parentId, message);
    }
}
