package com.archibus.app.reservation.service;

import java.sql.Time;
import java.util.List;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.jobs.*;
import com.archibus.app.reservation.domain.recurrence.Recurrence;
import com.archibus.app.reservation.service.helpers.*;
import com.archibus.app.reservation.service.jobs.*;
import com.archibus.app.reservation.util.*;
import com.archibus.context.ContextStore;
import com.archibus.jobmanager.JobStatus;
import com.archibus.utility.ExceptionBase;

/**
 * The Class ReservationRemoteServiceImpl.
 *
 * @author Bart Vanderschoot
 */
public class ReservationRemoteServiceImpl extends ReservationRemoteServiceBaseImpl
        implements ReservationRemoteService {

    /** The reservation service. */
    private IConferenceReservationService reservationService;

    /** The cancel service. */
    private CancelReservationService cancelReservationService;

    /** The reservation messages service. */
    private ConferenceCallMessagesService messagesService;

    /** The reservation link messages service. */
    private ReservationLinkService linkService;

    /**
     * {@inheritDoc}
     */
    @Override
    public final void cancelRoomReservation(final RoomReservation reservation)
            throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();
        this.cancelReservationService.cancelReservation(reservation, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Deprecated
    public final List<RoomReservation> cancelRoomReservationByUniqueIdRecurrence(
            final String uniqueId, final String email, final boolean disconnectOnError)
            throws ExceptionBase {
        return this.cancelRoomReservations(uniqueId, email, null, disconnectOnError);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<RoomReservation> cancelRoomReservations(final String uniqueId,
            final String email, final Integer conferenceId, final boolean disconnectOnError)
            throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();

        WorkRequestService.setFlagToSendEmailsInSingleJob();
        final List<RoomReservation> result = this.cancelReservationService
            .cancelRoomReservationsByUniqueId(uniqueId, email, conferenceId, disconnectOnError);
        WorkRequestService.startJobToSendEmailsInSingleJob();
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void disconnectRoomReservation(final RoomReservation reservation)
            throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();

        // get the original reservation, so any changes in the object received from the client
        // are not saved
        final RoomReservation roomReservation = this.reservationService
            .getActiveReservation(reservation.getReserveId(), Constants.TIMEZONE_UTC);

        this.cancelReservationService.disconnectReservation(roomReservation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConferenceRoomsAvailability checkConferenceRoomsAvailability(
            final List<RoomReservation> reservations, final boolean allDayEvent,
            final Recurrence recurrence) throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();
        final ConferenceRoomsAvailability result = this.locationQueryHandler
            .getConferenceRoomsAvailability(reservations, allDayEvent, recurrence);

        // also fill in the location
        final ReservationMessage container = this.messagesService
            .processLocationTemplate(reservations, this.locationQueryHandler.getSpaceService());
        result.setLocationSeparator(container.getSubject());
        result.setLocation(container.getBody());

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final RoomReservation getRoomReservationById(final Integer reserveId)
            throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();
        return this.reservationService.getActiveReservation(reserveId, Constants.TIMEZONE_UTC);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Deprecated
    public final List<RoomReservation> getRoomReservationsByUniqueId(final String uniqueId)
            throws ExceptionBase {
        return this.getRoomReservations(uniqueId, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<RoomReservation> getRoomReservations(final String uniqueId,
            final Integer conferenceId) throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();

        return this.reservationService.getByUniqueId(uniqueId, conferenceId,
            Constants.TIMEZONE_UTC);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final RoomReservation saveRoomReservation(final RoomReservation reservation)
            throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();
        // only set the requestor for new reservaions
        if (reservation.getReserveId() == null || reservation.getReserveId() == 0) {
            this.employeeService.setRequestor(reservation);
        }
        ReservationUtils.truncateComments(reservation);

        WorkRequestService.setFlagToSendEmailsInSingleJob();
        this.reservationService.saveFullReservation(reservation);
        WorkRequestService.startJobToSendEmailsInSingleJob();

        final RoomReservation savedReservation =
                this.getRoomReservationById(reservation.getReserveId());
        if (this.activityParameterHelper.shouldAddReservationLink()) {
            savedReservation
                .setReservationLink(this.linkService.processLinkTemplate(savedReservation));
        }
        return savedReservation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean verifyRecurrencePattern(final String uniqueId, final Recurrence recurrence,
            final Time startTime, final Time endTime, final String timeZone) throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();
        return this.reservationService.verifyRecurrencePattern(uniqueId, recurrence, startTime,
            endTime, timeZone);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<RoomArrangement> getRoomArrangementDetails(
            final List<RoomArrangement> roomArrangements) throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();
        final List<RoomArrangement> details =
                this.reservationService.getRoomArrangementDetails(roomArrangements);
        this.locationQueryHandler.getSpaceService().setLocationString(details);
        return details;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<RoomReservation> saveRecurringRoomReservation(
            final RoomReservation reservation, final Recurrence recurrence) throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();
        // Set the requestor and save the reservation series.
        // only set the requestor for new reservaions
        if (reservation.getReserveId() == null || reservation.getReserveId() == 0) {
            this.employeeService.setRequestor(reservation);
        }
        //AD-91
        ReservationUtils.truncateComments(reservation);
        
        WorkRequestService.setFlagToSendEmailsInSingleJob();
        final List<RoomReservation> savedReservations =
                this.reservationService.saveFullRecurringReservation(reservation, recurrence);
        WorkRequestService.startJobToSendEmailsInSingleJob();

        if (this.activityParameterHelper.shouldAddReservationLink()
                && !savedReservations.isEmpty()) {
            savedReservations.get(0).setReservationLink(
                this.linkService.processRecurringLinkTemplate(savedReservations));
        }

        // Return the reservations with UTC time zone.
        for (final RoomReservation savedReservation : savedReservations) {
            TimeZoneConverter.convertToTimeZone(savedReservation, Constants.TIMEZONE_UTC);
        }
        return savedReservations;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public SavedConferenceCall saveConferenceCall(final List<RoomReservation> reservations,
            final Recurrence recurrence, final boolean disconnectOnError) throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();
        for (final RoomReservation reservation : reservations) {
            // Set the requestor for new reservations.
            if (reservation.getReserveId() == null || reservation.getReserveId() == 0) {
                this.employeeService.setRequestor(reservation);
            }
            //AD-91
            ReservationUtils.truncateComments(reservation);
        }

        WorkRequestService.setFlagToSendEmailsInSingleJob();
        final SavedConferenceCall result = this.reservationService.saveConferenceCall(reservations,
            recurrence, disconnectOnError);
        WorkRequestService.startJobToSendEmailsInSingleJob();
        if (result.isCompleted()) {
            boolean addReservationLink = this.activityParameterHelper.shouldAddReservationLink();

            if (addReservationLink && recurrence != null) {
                result.getSavedReservations().get(0).setReservationLink(
                    this.linkService.processRecurringLinkTemplate(result.getSavedReservations()));
                addReservationLink = false;
            }

            // convert all reservations to UTC and add the link
            for (final RoomReservation reservation : result.getSavedReservations()) {
                if (addReservationLink) {
                    reservation
                        .setReservationLink(this.linkService.processLinkTemplate(reservation));
                }
                TimeZoneConverter.convertToTimeZone(reservation, Constants.TIMEZONE_UTC);
            }
        }
        return result;
    }

    /**
     * Sets the reservation service.
     *
     * @param reservationService the new reservation service
     */
    public final void setReservationService(
            final IConferenceReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * Sets the cancel reservation service.
     *
     * @param cancelReservationService the new cancel reservation service
     */
    public final void setCancelReservationService(
            final CancelReservationService cancelReservationService) {
        this.cancelReservationService = cancelReservationService;
    }

    /**
     * Sets the messages reservation service.
     *
     * @param messagesService the new conference call messages reservation service
     */
    public final void setMessagesService(final ConferenceCallMessagesService messagesService) {
        this.messagesService = messagesService;
    }

    /**
     * Sets the messages reservation service.
     *
     * @param linkService the new conference call messages reservation service
     */
    public final void setLinkService(final ReservationLinkService linkService) {
        this.linkService = linkService;
    }

    /** {@inheritDoc} */
    @Override
    public String startAsyncRequest(final ReservationRequest request) throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();
        final ReservationJob job = new ReservationJob(request, this);
        return ContextStore.get().getJobManager().startJob(job);
    }

    /** {@inheritDoc} */
    @Override
    public ReservationResult getAsyncResult(final String jobId) throws ExceptionBase {
        ReservationsContextHelper.checkProjectContext();

        final JobStatus status = ContextStore.get().getJobManager().getJobStatus(jobId);
        ReservationResult result = null;
        if (status != null) {
            if (status.getResult() instanceof ResultWrapper) {
                result = ((ResultWrapper) status.getResult()).getResult();
            } else {
                result = new ReservationResult();
            }
            result.setJobId(jobId);
            result.setJobState(new JobState(status));
        }
        return result;
    }

}
