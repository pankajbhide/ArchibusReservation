package com.archibus.app.reservation.service.jobs;

import com.archibus.app.reservation.domain.jobs.ReservationResult;
import com.archibus.jobmanager.JobStatus.JobResult;

/**
 * Wrapper for storing a reservations job result as a common job result.
 *
 * @author Yorik Gerlo
 * @since 23.2
 */
public class ResultWrapper extends JobResult {

    /** The reservation job result. */
    private final ReservationResult result;

    /**
     * Create a new Result Wrapper.
     *
     * @param title result title
     * @param result reservation job result
     */
    public ResultWrapper(final String title, final ReservationResult result) {
        super(title);
        this.result = result;
    }

    /**
     * Get the reservation job result.
     *
     * @return the result
     */
    public ReservationResult getResult() {
        return this.result;
    }

}
