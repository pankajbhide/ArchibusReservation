package com.archibus.app.reservation.domain.jobs;

import java.util.List;

import javax.xml.bind.annotation.*;

import com.archibus.app.reservation.domain.RoomReservation;

/**
 * Represents the result of an asynchronous request for canceling a set of reservation.
 * <p>
 * Used by Reservations Web Service to cache and return results of asynchronous requests.
 *
 * @author Yorik Gerlo
 * @since 23.2
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "CancelReservationResult")
public class CancelReservationResult extends ReservationResult {

    /** The reservations that could not be cancelled. */
    private List<RoomReservation> failures;

    /**
     * Getter for the failures property.
     *
     * @see failures
     * @return the failures property.
     */
    public List<RoomReservation> getFailures() {
        return this.failures;
    }

    /**
     * Setter for the failures property.
     *
     * @see failures
     * @param failures the failures to set
     */
    public void setFailures(final List<RoomReservation> failures) {
        this.failures = failures;
    }

}
