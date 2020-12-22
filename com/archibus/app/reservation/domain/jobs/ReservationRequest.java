package com.archibus.app.reservation.domain.jobs;

import com.archibus.app.reservation.service.jobs.IReservationJobContext;

/**
 * Represents a request for reservations application that can be started asynchronously.
 * <p>
 * Used by ReservationRemoteService to run asynchronous requests.
 *
 * @author Yorik Gerlo
 * @since 23.2
 */
public class ReservationRequest {

    /**
     * Execute the request directly.
     *
     * @param context the context to execute the request
     * @return the result
     */
    public ReservationResult execute(final IReservationJobContext context) {
        final ReservationResult result = new ReservationResult();
        result.setCompleted(true);
        return result;
    }

}
