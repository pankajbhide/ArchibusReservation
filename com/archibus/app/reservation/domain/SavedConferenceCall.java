package com.archibus.app.reservation.domain;

import java.util.List;

import javax.xml.bind.annotation.*;

/**
 * Container for returning the results of creating / updating a conference call reservation.
 * 
 * @author Yorik Gerlo
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "SavedConferenceReservation")
public class SavedConferenceCall {
    
    /** All reservations that were created or updated, listed per date. */
    private List<RoomReservation> savedReservations;
    
    /**
     * List of reservations that need to be disconnected from the meeting to proceed. These
     * reservations were created earlier for rooms not in the current selection, and they cannot be
     * cancelled per the room arrangement settings and user rights.
     */
    private List<RoomReservation> reservationsToDisconnect;
    
    /**
     * Get the created reservations.
     * 
     * @return the created reservations
     */
    public List<RoomReservation> getSavedReservations() {
        return savedReservations;
    }
    
    /**
     * Set the saved reservations.
     * 
     * @param savedReservations the reservations to set
     */
    public void setSavedReservations(final List<RoomReservation> savedReservations) {
        this.savedReservations = savedReservations;
    }
    
    /**
     * Get the list of reservations that need to be disconnected.
     * 
     * @return the reservations to disconnect
     */
    public List<RoomReservation> getReservationsToDisconnect() {
        return reservationsToDisconnect;
    }
    
    /**
     * Set the list of reservations that need to be disconnected.
     * 
     * @param reservationsToDisconnect the reservations to disconnect
     */
    public void setReservationsToDisconnect(final List<RoomReservation> reservationsToDisconnect) {
        this.reservationsToDisconnect = reservationsToDisconnect;
    }
    
    /**
     * Check whether the reservations were created successfully.
     * 
     * @return the completed true if completed, false if reservations need to be disconnected
     */
    public boolean isCompleted() {
        return this.savedReservations != null;
    }
    
}
