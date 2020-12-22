package com.archibus.app.reservation.service.jobs;

import com.archibus.app.reservation.domain.jobs.*;
import com.archibus.app.reservation.service.ReservationRemoteService;
import com.archibus.jobmanager.*;
import com.archibus.utility.ExceptionBase;

/**
 * Base class for running asynchronous reservation request.
 *
 * @author Yorik Gerlo
 * @since 23.2
 */
public class ReservationJob extends JobBase implements IReservationJobContext {

    /** The remote service that processes the job. */
    private final ReservationRemoteService reservationRemoteService;

    /** The request to execute. */
    private final ReservationRequest request;

    /**
     * Create a new reservation job.
     *
     * @param request the request to execute
     * @param reservationRemoteService the remote service that can fulfill the request
     */
    public ReservationJob(final ReservationRequest request,
            final ReservationRemoteService reservationRemoteService) {
        super();
        this.request = request;
        this.reservationRemoteService = reservationRemoteService;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        try {
            final ReservationResult result = this.request.execute(this);
            this.status.setCode(JobStatus.JOB_COMPLETE);
            this.status.setResult(new ResultWrapper(result.getClass().getSimpleName(), result));
        } catch (final ExceptionBase exception) {
            final ReservationResult result = new ReservationResult();
            // capture exception here in order to pass it to the client
            result.setException(exception);
            this.status.setResult(new ResultWrapper(result.getClass().getSimpleName(), result));
            // rethrow to trigger rollback
            throw new ExceptionBase("Job failed", exception);
        }
    }

    /** {@inheritDoc} */
    @Override
    public ReservationRemoteService getReservationRemoteService() {
        return this.reservationRemoteService;
    }

}