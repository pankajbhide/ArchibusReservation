package com.archibus.app.reservation.domain.jobs;

import java.util.List;

import javax.xml.bind.annotation.*;

import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.Recurrence;
import com.archibus.app.reservation.service.jobs.IReservationJobContext;

/**
 * Represents a request to create a conference call reservation - single or recurring.
 * <p>
 * Used by Outlook Plugin to asynchronously create a conference call reservation.
 *
 * @author Yorik Gerlo
 * @since 23.2
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "ConferenceReservationRequest")
public class ConferenceReservationRequest extends ReservationRequest {

    /** The reservations on a single date of the conference call. */
    private List<RoomReservation> reservations;

    /** The recurrence pattern. May be null. */
    private Recurrence recurrence;

    /** Whether to disconnect other reservations that should be cancelled but can't be cancelled. */
    private boolean disconnectOnError;

    /**
     * Getter for the reservations property.
     *
     * @see reservations
     * @return the reservations property.
     */
    public List<RoomReservation> getReservations() {
        return this.reservations;
    }

    /**
     * Setter for the reservations property.
     *
     * @see reservations
     * @param reservations the reservations to set
     */
    public void setReservations(final List<RoomReservation> reservations) {
        this.reservations = reservations;
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
     * Getter for the disconnectOnError property.
     *
     * @see disconnectOnError
     * @return the disconnectOnError property.
     */
    public boolean isDisconnectOnError() {
        return this.disconnectOnError;
    }

    /**
     * Setter for the disconnectOnError property.
     *
     * @see disconnectOnError
     * @param disconnectOnError the disconnectOnError to set
     */
    public void setDisconnectOnError(final boolean disconnectOnError) {
        this.disconnectOnError = disconnectOnError;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConferenceReservationResult execute(final IReservationJobContext context) {
        final SavedConferenceCall savedConferenceCall = context.getReservationRemoteService()
            .saveConferenceCall(this.reservations, this.recurrence, this.disconnectOnError);
        final ConferenceReservationResult result = new ConferenceReservationResult();
        result.setSavedConferenceCall(savedConferenceCall);
        result.setCompleted(true);
        return result;
    }

}
