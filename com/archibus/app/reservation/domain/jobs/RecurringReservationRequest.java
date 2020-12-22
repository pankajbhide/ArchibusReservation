package com.archibus.app.reservation.domain.jobs;

import java.util.List;

import javax.xml.bind.annotation.*;

import com.archibus.app.reservation.domain.RoomReservation;
import com.archibus.app.reservation.domain.recurrence.Recurrence;
import com.archibus.app.reservation.service.jobs.IReservationJobContext;

/**
 * Represents a request to create a recurring reservation.
 * <p>
 * Used by Outlook Plugin to request a recurring reservation asynchronously.
 *
 * @author Yorik Gerlo
 * @since 23.2
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "RecurringReservationRequest")
public class RecurringReservationRequest extends ReservationRequest {

    /** The room reservation. */
    private RoomReservation reservation;

    /** The recurrence pattern. */
    private Recurrence recurrence;

    /**
     * Getter for the reservation property.
     *
     * @see reservation
     * @return the reservation property.
     */
    public RoomReservation getReservation() {
        return this.reservation;
    }

    /**
     * Setter for the reservation property.
     *
     * @see reservation
     * @param reservation the reservation to set
     */

    public void setReservation(final RoomReservation reservation) {
        this.reservation = reservation;
    }

    /**
     * Getter for the recurrence property.
     *
     * @see recurrence
     * @return the recurrence property.
     */
    public Recurrence getRecurrence() {
        return this.recurrence;
    }

    /**
     * Setter for the recurrence property.
     *
     * @see recurrence
     * @param recurrence the recurrence to set
     */
    public void setRecurrence(final Recurrence recurrence) {
        this.recurrence = recurrence;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RecurringReservationResult execute(final IReservationJobContext context) {
        final List<RoomReservation> reservations = context.getReservationRemoteService()
            .saveRecurringRoomReservation(this.reservation, this.recurrence);
        final RecurringReservationResult result = new RecurringReservationResult();
        result.setSavedReservations(reservations);
        result.setCompleted(true);
        return result;
    }

}
