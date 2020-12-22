package com.archibus.app.reservation.domain.jobs;

import java.util.List;

import javax.xml.bind.annotation.*;

import com.archibus.app.reservation.domain.RoomReservation;
import com.archibus.app.reservation.service.jobs.IReservationJobContext;

/**
 * Represents a request to cancel a set of reservations. Used by the Outlook Plugin to cancel
 * recurring reservations and conference call reservations asynchronously.
 *
 * @author Yorik Gerlo
 * @since 23.2
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "CancelReservationRequest")
public class CancelReservationRequest extends ReservationRequest {

    /** Outlook Unique id of the reservations to cancel. */
    private String uniqueId;

    /** Email address of the reservation organizer. */
    private String email;

    /** Conference reservation identifier of the reservations to cancel (may be null). */
    private Integer conferenceId;

    /** Whether to disconnect reservations that cannot be cancelled. */
    private boolean disconnectOnError;

    /**
     * Getter for the uniqueId property.
     *
     * @see uniqueId
     * @return the uniqueId property.
     */
    public String getUniqueId() {
        return this.uniqueId;
    }

    /**
     * Setter for the uniqueId property.
     *
     * @see uniqueId
     * @param uniqueId the uniqueId to set
     */
    public void setUniqueId(final String uniqueId) {
        this.uniqueId = uniqueId;
    }

    /**
     * Getter for the email property.
     *
     * @see email
     * @return the email property.
     */
    public String getEmail() {
        return this.email;
    }

    /**
     * Setter for the email property.
     *
     * @see email
     * @param email the email to set
     */
    public void setEmail(final String email) {
        this.email = email;
    }

    /**
     * Getter for the conferenceId property.
     *
     * @see conferenceId
     * @return the conferenceId property.
     */
    public Integer getConferenceId() {
        return this.conferenceId;
    }

    /**
     * Setter for the conferenceId property.
     *
     * @see conferenceId
     * @param conferenceId the conferenceId to set
     */
    public void setConferenceId(final Integer conferenceId) {
        this.conferenceId = conferenceId;
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

    /** {@inheritDoc} */
    @Override
    public ReservationResult execute(final IReservationJobContext context) {
        final List<RoomReservation> failures =
                context.getReservationRemoteService().cancelRoomReservations(this.uniqueId,
                    this.email, this.conferenceId, this.disconnectOnError);

        final CancelReservationResult result = new CancelReservationResult();
        result.setFailures(failures);
        result.setCompleted(true);
        return result;
    }

}
