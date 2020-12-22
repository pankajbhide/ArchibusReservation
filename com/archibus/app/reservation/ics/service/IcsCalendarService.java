package com.archibus.app.reservation.ics.service;

import java.util.List;

import org.apache.log4j.Logger;

import com.archibus.app.reservation.dao.datasource.*;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.ics.domain.EmailModel;
import com.archibus.app.reservation.service.ICalendarService;
import com.archibus.app.reservation.util.EmailNotificationHelper;
import com.archibus.utility.*;

/**
 * The Class WebCentralCalendarService.
 */
public class IcsCalendarService implements ICalendarService {

    /** Whether to request a reply on ICS invitations. */
    private static final boolean REQUIRE_REPLY = true;

    /** The room reservation data source. */
    private ConferenceCallReservationDataSource reservationDataSource;

    /** The calendar message service for generating the ICS e-mails. */
    private CalendarMessageService calendarMessageService;

    /** The logger. */
    private final Logger logger = Logger.getLogger(this.getClass());

    /** {@inheritDoc} */
    @Override
    public String createAppointment(final IReservation reservation) throws ExceptionBase {
        // send emails to attendees, only recurring if recurrence property is set
        sendEmails(reservation, reservation, EmailModel.TYPE_NEW,
            reservation.getRecurrence() != null, null, reservation.getConferenceId());
        // return empty string for appointment identifier
        return "";
    }

    /** {@inheritDoc} */
    @Override
    public void updateAppointment(final IReservation reservation) throws ExceptionBase {
        // send emails to attendees.
        sendEmails(reservation, reservation, EmailModel.TYPE_UPDATE, true, null,
            reservation.getConferenceId());
    }

    /** {@inheritDoc} */
    @Override
    public void cancelAppointment(final IReservation reservation, final String message,
            final boolean notifyOrganizer) throws ExceptionBase {
        // send emails to attendees
        sendEmails(reservation, reservation, EmailModel.TYPE_CANCEL, true, message,
            this.getMasterConferenceId(reservation));
    }

    /** {@inheritDoc} */
    @Override
    public void cancelAppointmentOccurrence(final IReservation reservation, final String message,
            final boolean notifyOrganizer) throws ExceptionBase {
        // send emails to attendees
        sendEmails(reservation, reservation, EmailModel.TYPE_CANCEL, false, message,
            this.getMasterConferenceId(reservation));
    }

    /** {@inheritDoc} */
    @Override
    public void updateAppointmentOccurrence(final IReservation reservation,
            final IReservation originalReservation) throws ExceptionBase {
        // send emails to attendees
        sendEmails(reservation, originalReservation, EmailModel.TYPE_UPDATE, false, null,
            this.getMasterConferenceId(reservation));
    }

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
    public void updateAppointmentSeries(final RoomReservation reservation,
            final List<RoomReservation> originalReservations) throws ExceptionBase {

        if (StringUtil.isNullOrEmpty(reservation.getAttendees())) {
            // For simple email the full series can be sent again in a single message
            this.updateAppointment(reservation);
        } else {
            final List<RoomReservation> createdReservations = reservation.getCreatedReservations();

            /*
             * Check if all reservations are still on their original date. In this case we can
             * update the full series with a single email. Note in that case individual occurrences
             * can still be different, but only when canceling a single location in a recurring
             * conference call (APP-2131). The CalendarMessageService checks for this case and adds
             * additional ICS files for those occurrences, which the user should add to his calendar
             * after adding the series update. This way there's still only one email for the series
             * instead of an email for each occurrence.
             */
            boolean originalDates = true;
            for (final RoomReservation createdRes : createdReservations) {
                if (createdRes.getRecurringDateModified() > 0) {
                    originalDates = false;
                    break;
                }
            }
            if (originalDates) {
                sendEmails(reservation, reservation, EmailModel.TYPE_UPDATE, true, null,
                    this.getMasterConferenceId(reservation));
            } else {
                // Update each individual occurrence.
                for (int index = 0; index < originalReservations.size(); ++index) {
                    this.updateAppointmentOccurrence(createdReservations.get(index),
                        originalReservations.get(index));
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
     * @param originalReservation the original reservation
     * @param invitationType the type of invite to send
     * @param allRecurrences true to send for all occurrences, false for only the given occurrence
     * @param message the message to include in the notification
     * @param conferenceId the conference id in case of a conference call reservation (may be null)
     */
    private void sendEmails(final IReservation reservation, final IReservation originalReservation,
            final String invitationType, final boolean allRecurrences, final String message,
            final Integer conferenceId) {

        /*
         * At this point, allRecurrences could also be referring to a non-recurring reservation. So
         * check if the reservation actually has a recurring rule.
         */
        final boolean recurring =
                allRecurrences && StringUtil.notNullOrEmpty(reservation.getRecurringRule());

        // KB 3046144 don't create ICS invitations when there are no attendees.
        if (StringUtil.isNullOrEmpty(reservation.getAttendees())) {
            Integer parentId = null;
            if (recurring) {
                parentId = reservation.getParentId();
            }

            if (Constants.STATUS_REJECTED.equalsIgnoreCase(reservation.getStatus())) {
                // this email is already sent from the approval service
                this.logger
                    .debug("Calendar Service doesn't need to send email for rejected reservation "
                            + reservation.getReserveId() + " without attendees");
            } else {
                /*
                 * TODO do something with conference id? Better probably to check in CommonHandler
                 * what to send as 'reservation id'
                 */
                // the helper checks whether email notifications are enabled
                EmailNotificationHelper.sendNotifications(reservation.getReserveId(), parentId,
                    message);
            }

        } else if (EmailNotificationHelper.notificationsEnabled()) {
            // KB3040764 refactor ICS generation using ical4j
            // call using the calendarMessageService
            // all parameters are in context
            // code was modified to use ical4j to generate the ICS attachments
            // and reduce/remove the EventHandlerBase dependency
            this.calendarMessageService.sendEmailInvitations((RoomReservation) reservation,
                (RoomReservation) originalReservation, invitationType, recurring, REQUIRE_REPLY,
                message, conferenceId);

        }
    }
}
