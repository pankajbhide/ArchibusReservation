package com.archibus.app.reservation.service;

import java.util.*;

import com.archibus.app.reservation.dao.*;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.util.*;

/**
 * The Class CancelReservationService.
 */
public class CancelReservationService extends AdminServiceContainer {

    /** Error message when a room reservation is expected. */
    // @translatable
    private static final String NO_VALID_RESERVATION =
            "The reservation must be a room or resource reservation";

    /** The room reservation data source. */
    private IRoomReservationDataSource roomReservationDataSource;

    /** The room reservation data source. */
    private IResourceReservationDataSource resourceReservationDataSource;

    /** The Work Request service. */
    private WorkRequestService workRequestService;

    /**
     * Cancel reservations by unique id: regular, recurring or conference.
     *
     * @param uniqueId the unique id
     * @param email the email
     * @param conferenceId the conference id
     * @param disconnectOnError the disconnect on error
     * @return the list of room reservations that could not be cancelled
     * @throws ReservationException the reservation exception
     */
    public final List<RoomReservation> cancelRoomReservationsByUniqueId(final String uniqueId,
            final String email, final Integer conferenceId, final boolean disconnectOnError)
                    throws ReservationException {

        final List<RoomReservation> reservations =
                this.roomReservationDataSource.getByUniqueId(uniqueId, conferenceId, null);

        List<RoomReservation> failureList = null;
        if (reservations == null || reservations.isEmpty()) {
            // no reservations found, so no failures
            failureList = new ArrayList<RoomReservation>(0);
        } else {
            // reservations found: try to cancel them and return the list of failures
            failureList = cancelRoomReservations(reservations, disconnectOnError);
        }

        return failureList;
    }

    /**
     * Cancel recurring reservation.
     *
     * @param reservation the reservation
     * @param comments the cancellation comments
     * @return the list of room reservations
     * @throws ReservationException the reservation exception
     */
    public final List<List<IReservation>> cancelRecurringReservation(final IReservation reservation,
            final String comments) throws ReservationException {

        // return the list of cancelled reservations and list of failures
        final List<IReservation> cancelledReservations = new ArrayList<IReservation>();
        final List<IReservation> failures = new ArrayList<IReservation>();
        final List<List<IReservation>> result = new ArrayList<List<IReservation>>(2);
        result.add(cancelledReservations);
        result.add(failures);

        if (reservation instanceof RoomReservation) {
            // cancel all occurrences starting from this one
            cancelRecurringRoomReservation(reservation, cancelledReservations, failures, comments);
        } else if (reservation instanceof ResourceReservation) {
            // cancel all occurrences starting from this one
            cancelRecurringResourceReservation(reservation, cancelledReservations, failures,
                comments);
        } else {
            throw new ReservationException(NO_VALID_RESERVATION, CancelReservationService.class,
                    this.getAdminService());
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public final void cancelReservation(final IReservation reservation, final String comments)
            throws ReservationException {
        if (reservation instanceof RoomReservation) {
            this.roomReservationDataSource.cancel((RoomReservation) reservation, comments);

        } else if (reservation instanceof ResourceReservation) {
            this.resourceReservationDataSource.cancel((ResourceReservation) reservation, comments);

        } else {
            throw new ReservationException(NO_VALID_RESERVATION, CancelReservationService.class,
                    this.getAdminService());
        }

        // cancel the work request
        this.workRequestService.cancelWorkRequest(reservation);
    }

    /**
     * {@inheritDoc}
     */
    public final void disconnectReservation(final RoomReservation reservation)
            throws ReservationException {
        this.roomReservationDataSource.clearUniqueId(reservation);
    }

    /**
     * Sets the room reservation data source.
     *
     * @param roomReservationDataSource the new room reservation data source
     */
    public void setRoomReservationDataSource(
            final IRoomReservationDataSource roomReservationDataSource) {
        this.roomReservationDataSource = roomReservationDataSource;
    }

    /**
     * Sets the resource reservation data source.
     *
     * @param resourceReservationDataSource the new resource reservation data source
     */
    public void setResourceReservationDataSource(
            final IResourceReservationDataSource resourceReservationDataSource) {
        this.resourceReservationDataSource = resourceReservationDataSource;
    }

    /**
     * Sets the work request service for cancelling related work requests.
     *
     * @param workRequestService the new work request service for cancelling
     */
    public void setWorkRequestService(final WorkRequestService workRequestService) {
        this.workRequestService = workRequestService;
    }

    /**
     * Cancel recurring room reservation.
     *
     * @param reservation the reservation
     * @param cancelledReservations the cancelled reservations
     * @param failures the failures
     * @param comments the cancellation comments
     */
    private void cancelRecurringRoomReservation(final IReservation reservation,
            final List<IReservation> cancelledReservations, final List<IReservation> failures,
            final String comments) {
        final Date startDate = reservation.getStartDate();
        final Integer parentId = reservation.getParentId() == null ? reservation.getReserveId()
                : reservation.getParentId();

        // Get all active reservations starting from (and including) the specified one.
        final List<RoomReservation> reservations =
                this.roomReservationDataSource.getByParentId(parentId, startDate, null, true);

        for (final RoomReservation roomReservation : reservations) {
            try {
                this.roomReservationDataSource.canBeCancelledByCurrentUser(roomReservation);
            } catch (final ReservationException exception) {
                // this one can't be cancelled, so skip and report
                failures.add(roomReservation);
                continue;
            }
            this.cancelReservation(roomReservation, comments);
            cancelledReservations.add(roomReservation);
        }
    }

    /**
     * Cancel recurring resource reservation.
     *
     * @param reservation the reservation
     * @param cancelledReservations the cancelled reservations
     * @param failures the failures
     * @param comments the cancellation comments
     */
    private void cancelRecurringResourceReservation(final IReservation reservation,
            final List<IReservation> cancelledReservations, final List<IReservation> failures,
            final String comments) {
        final Date startDate = reservation.getStartDate();
        final Integer parentId = reservation.getParentId() == null ? reservation.getReserveId()
                : reservation.getParentId();

        // Get all active reservations starting from (and including) the specified one.
        final List<ResourceReservation> reservations =
                this.resourceReservationDataSource.getByParentId(parentId, startDate, null, true);

        for (final ResourceReservation resourceReservation : reservations) {
            try {
                this.resourceReservationDataSource.canBeCancelledByCurrentUser(resourceReservation);
            } catch (final ReservationException exception) {
                // this one can't be cancelled, so skip and report
                failures.add(resourceReservation);
                continue;
            }
            this.cancelReservation(resourceReservation, comments);
            cancelledReservations.add(resourceReservation);
        }
    }

    /**
     * Cancels a list of reservations. If disconnectOnError is false, first checks that all
     * reservations can be cancelled. If not all reservations can be cancelled, no reservations are
     * cancelled and the list of failures is returned.
     *
     * @param reservations the reservations to cancel
     * @param disconnectOnError true if reservations that cannot be cancelled must be disconnected
     * @return list of reservations that cannot be cancelled
     * @throws ReservationException when an error occurs
     */
    public List<RoomReservation> cancelRoomReservations(final List<RoomReservation> reservations,
            final boolean disconnectOnError) throws ReservationException {
        final List<RoomReservation> failureList = new ArrayList<RoomReservation>();
        final List<RoomReservation> successList = new ArrayList<RoomReservation>();
        final List<RoomReservation> historyList = new ArrayList<RoomReservation>();
        // check that all instances can be cancelled
        for (final RoomReservation reservation : reservations) {
            try {
                if (reservation.getStartDate()
                    .before(ReservationUtils.determineCurrentLocalDate(reservation))) {
                    // never cancel a reservation occurring in the past
                    historyList.add(reservation);
                } else {
                    this.roomReservationDataSource.canBeCancelledByCurrentUser(reservation);
                    // this reservation can be cancelled, so add it to the success list
                    successList.add(reservation);
                }
            } catch (final ReservationException exception) {
                // this reservation cannot be cancelled, so add it to the failure list
                // or disconnect it if the user requested it
                if (disconnectOnError) {
                    this.roomReservationDataSource.clearUniqueId(reservation);
                } else {
                    failureList.add(reservation);
                }
            }
        }

        // If there were no failures, proceed with canceling the other reservations.
        if (failureList.isEmpty()) {
            for (final RoomReservation reservation : successList) {
                this.roomReservationDataSource.cancel(reservation, null);
                // cancel the work request, one by one
                this.workRequestService.cancelWorkRequest(reservation);
            }
            for (final RoomReservation reservation : historyList) {
                this.roomReservationDataSource.clearUniqueId(reservation);
            }
        }

        return failureList;
    }

}
