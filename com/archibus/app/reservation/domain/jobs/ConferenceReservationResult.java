package com.archibus.app.reservation.domain.jobs;

import javax.xml.bind.annotation.*;

import com.archibus.app.reservation.domain.SavedConferenceCall;

/**
 * Represents the result of an asynchronous request for creating a conference reservation.
 * <p>
 * Used by Reservations Web Service to cache and return results of asynchronous requests.
 *
 * @author Yorik Gerlo
 * @since 23.2
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "ConferenceReservationResult")
public class ConferenceReservationResult extends ReservationResult {

    /** Result of saving the conference call reservation. */
    private SavedConferenceCall savedConferenceCall;

    /**
     * Getter for the savedConferenceCall property.
     *
     * @see savedConferenceCall
     * @return the savedConferenceCall property.
     */
    public SavedConferenceCall getSavedConferenceCall() {
        return this.savedConferenceCall;
    }

    /**
     * Setter for the savedConferenceCall property.
     *
     * @see savedConferenceCall
     * @param savedConferenceCall the savedConferenceCall to set
     */
    public void setSavedConferenceCall(final SavedConferenceCall savedConferenceCall) {
        this.savedConferenceCall = savedConferenceCall;
    }

}
