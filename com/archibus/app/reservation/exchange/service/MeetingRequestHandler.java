package com.archibus.app.reservation.exchange.service;

import java.util.*;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.exchange.util.*;
import com.archibus.app.reservation.util.ReservationUtils;
import com.archibus.utility.LocalDateTimeUtil;

import microsoft.exchange.webservices.data.*;

/**
 * Can handle meeting requests from the Reservations Mailbox on Exchange, to process meeting changes
 * made via Exchange. Managed by Spring.
 *
 * @author Yorik Gerlo
 * @since 21.2
 */
public class MeetingRequestHandler extends MeetingItemHandler {

    /** Error message that indicates something went wrong handling a meeting request. */
    private static final String ITEM_ERROR = "Error handling meeting request";

    /** The meeting request properties helper. */
    private MeetingRequestPropertiesHelper meetingRequestPropertiesHelper;

    /**
     * Handle a meeting request.
     *
     * @param request the meeting request
     */
    public void handleMeetingRequest(final MeetingRequest request) {
        try {
            request
                .load(this.meetingRequestPropertiesHelper.getExtendedMeetingRequestPropertySet());
            // Ignore this message if it is already out of date or already processed.
            final boolean outOfDate = (Boolean) request
                .getObjectFromPropertyDefinition(MeetingMessageSchema.IsOutOfDate);
            if (outOfDate) {
                request.delete(DeleteMode.HardDelete);
            } else {
                // Check whether the appointment is linked to a reservation id.
                final String iCalUid = (String) request
                    .getObjectFromPropertyDefinition(MeetingMessageSchema.ICalUid);
                final String organizerEmail = request.getOrganizer().getAddress();
                ItemHandlerImpl.setUserFromEmail(organizerEmail, request,
                    this.messagesService.getAdminService());
                final ItemId appointmentId = (ItemId) request
                    .getObjectFromPropertyDefinition(MeetingRequestSchema.AssociatedAppointmentId);
                Appointment appointment = null;
                AppointmentType appointmentType = request.getAppointmentType();
                if (appointmentId != null) {
                    appointment = Appointment.bind(request.getService(), appointmentId);
                }
                if (appointment != null) {
                    // We already have the corresponding appointment on the calendar, get the
                    // appointment type from there.
                    // This is necessary to detect occurrence updates.
                    appointmentType = appointment.getAppointmentType();
                }
                switch (appointmentType) {
                    case Single:
                        /*
                         * 1. Regular: bind to the original item, check reservation equivalence and
                         * update if required. Reject the request if the update failed, otherwise
                         * accept.
                         */
                        checkSingleMeeting(request, organizerEmail, iCalUid);
                        break;
                    case RecurringMaster:
                        /*
                         * 2. Master: bind to the original master, check equivalence for all
                         * occurrences (mind modified and cancelled ones, although none of those
                         * should exist). If it doesn't match the reservations in the database, just
                         * reject for now. -> only dates/times need to match, other fields can be
                         * updated in the database. Alternative as in Outlook: when dates/times do
                         * not match (i.e. the pattern was changed), take the first active
                         * reservation, cancel all reservations and use the first reservation to
                         * create a new series that matches the new recurrence pattern in the
                         * master.
                         */
                        handleRecurringMeetingRequest(request, iCalUid);
                        break;
                    default:
                        // cases Exception and Occurrence
                        /*
                         * 3. Occurrence: bind to the original occurrence, if it's an exception
                         * first check there for the reservation id. Otherwise or if no reservation
                         * id is found, bind to the master to get the reservation id for this
                         * occurrence. Verify equivalence between the reservation and occurrence,
                         * update the reservation if required. Reject if the update fails. (probably
                         * best to reject by catching and rethrowing ReservationException)
                         */
                        checkSingleMeeting(request, organizerEmail, iCalUid);
                        break;

                }
            }
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API
            // method throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            throw new CalendarException(ITEM_ERROR, exception, MeetingRequestHandler.class,
                this.messagesService.getAdminService());
        }
    }

    /**
     * Set the meeting request properties helper.
     *
     * @param meetingRequestPropertiesHelper the meeting request properties helper
     */
    public void setMeetingRequestPropertiesHelper(
            final MeetingRequestPropertiesHelper meetingRequestPropertiesHelper) {
        this.meetingRequestPropertiesHelper = meetingRequestPropertiesHelper;
    }

    /**
     * Check the equivalence of a single meeting with its corresponding reservation. This can be a
     * single meeting or an occurrence.
     *
     * @param request the request
     * @param organizerEmail the meeting organizer's email address
     * @param uniqueId unique id of the meeting in Exchange
     * @throws ServiceLocalException when the appointment type cannot be determined
     */
    private void checkSingleMeeting(final MeetingRequest request, final String organizerEmail,
            final String uniqueId) throws ServiceLocalException {
        Integer reservationId = this.meetingRequestPropertiesHelper.getReservationId(request);
        RoomReservation reservation = null;

        if (reservationId == null
                && AppointmentType.Exception.equals(request.getAppointmentType())) {
            /*
             * No reservation id included in the meeting request. Per KB 3048099 this could be a
             * meeting occurrence update coming from Outlook for a meeting created via Web Central.
             * Bind to the meeting on the organizer's calendar to retrieve the reservation id.
             */
            final Appointment organizerMasterAppointment =
                    this.appointmentBinder.bindToAppointment(organizerEmail, uniqueId);
            final Map<Date, Integer> recurringReservationIds = this.meetingRequestPropertiesHelper
                .getRecurringReservationIds(organizerMasterAppointment);
            reservationId = this.meetingRequestPropertiesHelper
                .getReservationIdFromRecurringReservationIds(request, recurringReservationIds);
        }

        reservation = findSingleReservation(request.getAppointmentType(), reservationId, uniqueId);

        if (reservation == null) {
            try {
                request.decline(true);
                // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API
                // method throws a checked Exception, which needs to be wrapped in ExceptionBase.
            } catch (final Exception exception) {
                // CHECKSTYLE:ON
                throw new CalendarException("Error declining meeting request", exception,
                    MeetingRequestHandler.class, this.messagesService.getAdminService());
            }
        } else {
            // Check if the reservation must be updated.
            if (!ReservationUpdater.updateReservation(reservation, request)) {
                // Try to update the reservation.
                try {
                    this.reservationService.saveFullReservation(reservation);
                } catch (final ReservationException exception) {
                    sendUpdateFailure(request.createDeclineMessage(), reservation.getReserveId(),
                        exception);

                    // rethrow the reservation exception to ensure nothing is changed
                    throw new ReservationException(
                        "Reservation could not be updated to match meeting request, organizer was notified",
                        exception, MeetingRequestHandler.class,
                        this.messagesService.getAdminService());
                }
            }
            // The reservation is now equivalent, so accept the request.
            try {
                request.accept(true);
                // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party
                // API method throws a checked Exception, which needs to be wrapped in
                // ExceptionBase.
            } catch (final Exception exception) {
                // CHECKSTYLE:ON
                throw new CalendarException("Error accepting meeting request.", exception,
                    MeetingRequestHandler.class, this.messagesService.getAdminService());
            }
        }
    }

    /**
     * Find a single reservation in the database.
     *
     * @param appointmentType the meeting type
     * @param reservationId the reservation id
     * @param uniqueId the unique id of the meeting
     * @return the room reservation, or null if not found
     */
    private RoomReservation findSingleReservation(final AppointmentType appointmentType,
            final Integer reservationId, final String uniqueId) {
        RoomReservation reservation = null;
        if (reservationId == null && AppointmentType.Single.equals(appointmentType)) {
            final List<RoomReservation> reservations =
                    this.reservationService.getByUniqueId(uniqueId, null, Constants.TIMEZONE_UTC);
            if (!reservations.isEmpty()) {
                reservation = reservations.get(0);
            }
        } else if (reservationId != null) {
            reservation = this.reservationService.getActiveReservation(reservationId,
                Constants.TIMEZONE_UTC);
        }
        return reservation;
    }

    /**
     * Decline the meeting request because of a ReservationException.
     *
     * @param declineMessage the decline message to send
     * @param reservationId id of the reservation that could not be updated
     * @param exception indicates the cause of the failure
     */
    private void sendUpdateFailure(final DeclineMeetingInvitationMessage declineMessage,
            final Integer reservationId, final ReservationException exception) {
        /*
         * Actually the parameter is the id of the primary reservation that could not be updated.
         * The exception should contain the correct reservation id, also in case of conference call
         * reservations.
         */
        Integer actualReservationId = null;
        if (exception instanceof ReservableNotAvailableException) {
            actualReservationId = ((ReservableNotAvailableException) exception).getReservationId();
        }
        if (actualReservationId == null) {
            actualReservationId = reservationId;
        }

        // Get the unmodified reservation.
        final RoomReservation reservation =
                this.reservationService.getActiveReservation(actualReservationId, null);

        // Inform the requestor, indicating the cause of the error.
        this.messagesService.sendUpdateFailure(declineMessage, reservation, exception);
    }

    /**
     * Handle the request for a recurring meeting. It is only accepted if all reservations match
     * their appointment occurrence. Updates of date/time are not supported, other changes are
     * applied.
     *
     * @param request the request
     * @param iCalUid unique id of the recurring meeting in Exchange
     */
    private void handleRecurringMeetingRequest(final MeetingRequest request, final String iCalUid) {
        try {
            final List<RoomReservation> reservations =
                    this.reservationService.getByUniqueId(iCalUid, null, Constants.TIMEZONE_UTC);
            final Appointment master = request.accept(false).getAppointment();
            master.load(this.appointmentBinder.getAppointmentPropertiesHelper()
                .getExtendedAppointmentPropertySet());

            // group reservations by date
            final Map<Date, List<RoomReservation>> reservationsByDate =
                    ReservationUtils.groupByStartDate(reservations);

            // never accept if meeting is not found or doesn't have a last occurrence
            final Date lastOccurrenceDate = this.getLastOccurrenceStart(master);
            final Date today = TimePeriod
                .clearTime(LocalDateTimeUtil.currentLocalDateForTimeZone(Constants.TIMEZONE_UTC));
            boolean accept = lastOccurrenceDate != null;

            int occurrenceIndex = 0;
            Date occurrenceStart = null;
            while (accept && !lastOccurrenceDate.equals(occurrenceStart)) {
                ++occurrenceIndex;
                final Appointment occurrence = this.getOccurrence(master, occurrenceIndex);
                /*
                 * Skip cancelled occurrences. If reservations exist for those occurrences they will
                 * be detected after the loop.
                 */
                if (occurrence != null) {
                    occurrenceStart = occurrence.getStart();
                    final List<RoomReservation> reservationsOnDate =
                            reservationsByDate.remove(TimePeriod.clearTime(occurrenceStart));
                    if (reservationsOnDate == null) {
                        // ignore occurrences without reservation if they are in the past
                        accept = occurrenceStart.before(today);
                    } else if (occurrenceStart.before(today)) {
                        this.logger.debug("Not checking equivalence for an occurrence in the past");
                    } else {
                        // check that each reservation corresponds to the meeting occurrence, update
                        // if required
                        for (final RoomReservation reservation : reservationsOnDate) {
                            this.checkEquivalence(occurrence, reservation, master);
                        }
                    }
                }
            }

            // we have checked all occurrences or accept is false
            applyResultToRecurringMeetingRequest(master, accept, reservationsByDate,
                occurrenceIndex);
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API
            // method throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            throw new CalendarException("Error verifying recurring meeting request", exception,
                MeetingRequestHandler.class, this.messagesService.getAdminService());
        }
    }

    /**
     * Get the occurrence with given index from the master appointment. Returns null if the
     * occurrence is cancelled.
     *
     * @param master the recurrence master appointment
     * @param occurrenceIndex the occurrence index
     * @return the occurrence with the specified index, or null if cancelled
     */
    private Appointment getOccurrence(final Appointment master, final int occurrenceIndex) {
        Appointment occurrence = null;
        try {
            occurrence = Appointment.bindToOccurrence(master.getService(), master.getId(),
                occurrenceIndex, this.appointmentBinder.getAppointmentPropertiesHelper()
                    .getExtendedAppointmentPropertySet());
        } catch (final ServiceResponseException exception) {
            if (ServiceError.ErrorCalendarOccurrenceIsDeletedFromRecurrence
                .equals(exception.getErrorCode())) {
                this.logger.debug("Skipping cancelled occurrence for recurring meeting request",
                    exception);
            } else {
                throw new CalendarException(
                    "Exchange Server Error binding to occurrence {0} of recurring meeting request",
                    exception, MeetingRequestHandler.class, this.messagesService.getAdminService(),
                    occurrenceIndex);
            }
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API
            // method throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            throw new CalendarException(
                "Unknown error binding to occurrence {0} of recurring meeting request", exception,
                MeetingRequestHandler.class, this.messagesService.getAdminService(),
                occurrenceIndex);
        }
        return occurrence;
    }

    /**
     * Apply the acceptance result to the given recurring meeting request.
     *
     * @param master the recurrence master appointment on the resource account calendar
     * @param accept true to accept, false to decline
     * @param reservationsByDate linked reservations not matched to an occurrence
     * @param occurrenceIndex index of the last occurrence that was verified to be OK (if accept is
     *            true) or invalid (if accept is false)
     */
    private void applyResultToRecurringMeetingRequest(final Appointment master,
            final boolean accept, final Map<Date, List<RoomReservation>> reservationsByDate,
            final int occurrenceIndex) {
        try {
            if (accept) {
                // KB 3045704 check if any reservations haven't yet been compared to a meeting
                // occurrence.
                for (final List<RoomReservation> unlinkedReservations : reservationsByDate
                    .values()) {
                    final Integer reservationId = unlinkedReservations.get(0).getReserveId();
                    // @translatable
                    final ReservationException exception = new ReservationException(
                        "This reservation occurrence does not correspond to a meeting occurrence. Use WebCentral or Outlook Plugin to update the timing of a recurrence series.",
                        MeetingRequestHandler.class, this.messagesService.getAdminService());
                    sendUpdateFailure(master.createDeclineMessage(), reservationId, exception);

                    // throw the exception to ensure any changes to other reservations are not saved
                    throw exception;
                }
                // If there are no unlinked reservations then we are OK to accept.
                master.accept(true);
            } else {
                final String errorMessage;
                if (occurrenceIndex == 0) {
                    // @translatable
                    errorMessage = "The recurring meeting doesn't specify an end date.";
                } else {
                    // @translatable
                    errorMessage =
                            "Meeting occurrence {0} does not correspond to any reservation. Use WebCentral or Outlook Plugin to update the timing of a recurrence series.";
                }
                final ReservationException exception =
                        new ReservationException(errorMessage, MeetingRequestHandler.class,
                            this.messagesService.getAdminService(), occurrenceIndex);
                this.messagesService.sendUpdateFailure(master.createDeclineMessage(), exception);
                throw exception;
            }
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API
            // method throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            throw new CalendarException("Error reporting result of recurring meeting request.",
                exception, MeetingRequestHandler.class, this.messagesService.getAdminService());
        }
    }

    /**
     * Get the start date/time of the last occurrence for the given master appointment. Return null
     * if master is null or master doesn't have a last occurrence.
     *
     * @param master the master appointment
     * @return start date/time of the last occurrence, or null if that doesn't exist
     */
    private Date getLastOccurrenceStart(final Appointment master) {
        Date date = null;
        try {
            if (master != null && master.getLastOccurrence() != null) {
                date = master.getLastOccurrence().getStart();
            }
        } catch (final ServiceLocalException exception) {
            // the master doesn't have a last occurrence, so return null
            this.logger.debug("No last occurrence in master appointment", exception);
        }
        return date;
    }

    /**
     * Check equivalence between an occurrence and a reservation.
     *
     * @param occurrence the meeting occurrence
     * @param reservation the reservation
     * @param master the master appointment of the recurrence series
     */
    private void checkEquivalence(final Appointment occurrence, final RoomReservation reservation,
            final Appointment master) {
        if (!ReservationUpdater.updateReservation(reservation, occurrence)) {
            // do allow time changes and changes to subject, attendees and body
            try {
                this.reservationService.saveReservation(reservation);
            } catch (final ReservationException exception) {
                try {
                    sendUpdateFailure(master.createDeclineMessage(), reservation.getReserveId(),
                        exception);
                    // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party
                    // API method throws a checked Exception, which needs to be wrapped in
                    // ExceptionBase.
                } catch (final Exception declineException) {
                    // CHECKSTYLE:ON
                    throw new CalendarException(
                        "Error reporting decline of recurring meeting request", declineException,
                        MeetingRequestHandler.class, this.messagesService.getAdminService());
                }
                // If the decline was successfully sent, rethrow the reservation exception to ensure
                // nothing is changed in the database.
                throw new ReservationException(
                    "Reservation occurrence could not be updated, organizer was notified",
                    exception, MeetingRequestHandler.class, this.messagesService.getAdminService());
            }
        }
    }

}
