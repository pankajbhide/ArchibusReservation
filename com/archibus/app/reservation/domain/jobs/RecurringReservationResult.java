package com.archibus.app.reservation.domain.jobs;

import java.util.List;

import javax.xml.bind.annotation.*;

import com.archibus.app.reservation.domain.RoomReservation;

/**
 * Represents the result of a recurring reservation request.
 *
 * @author Yorik Gerlo
 * @since 23.2
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "RecurringReservationResult")
public class RecurringReservationResult extends ReservationResult {

    /** All reservations that were created or updated, listed per date. */
    private List<RoomReservation> savedReservations;

    /**
     * Get the created reservations.
     *
     * @return the created reservations
     */
    public List<RoomReservation> getSavedReservations() {
        return this.savedReservations;
    }

    /**
     * Set the saved reservations.
     *
     * @param savedReservations the reservations to set
     */
    public void setSavedReservations(final List<RoomReservation> savedReservations) {
        this.savedReservations = savedReservations;
    }

}