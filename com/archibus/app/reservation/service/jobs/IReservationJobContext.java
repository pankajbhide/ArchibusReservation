package com.archibus.app.reservation.service.jobs;

import com.archibus.app.reservation.service.ReservationRemoteService;

/**
 * Interface that represents the common context for executing reservation jobs asynchronously.
 *
 * @author Yorik Gerlo
 * @since 23.2
 *
 */
public interface IReservationJobContext {

    /**
     * Get the reservation remote service.
     *
     * @return the reservation remote service
     */
    ReservationRemoteService getReservationRemoteService();

}