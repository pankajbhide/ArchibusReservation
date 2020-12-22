package com.archibus.app.reservation.domain;

import java.sql.Time;
import java.util.*;

/**
 * Container for the response to a query to find available rooms for a conference call.
 * 
 * @author Yorik Gerlo
 * @since Bali5
 */
public class ConferenceRoomsAvailability {
    
    /** List of available room arrangements. */
    private final List<RoomArrangement> roomArrangements = new ArrayList<RoomArrangement>();
    
    /** Processed location template containing all rooms. */
    private String location;
    
    /** Time period of the availability check. */
    private final TimePeriod timePeriod = new TimePeriod();
    
    /** Separator to include above and below the processed locations template. */
    private String locationSeparator;
    
    /**
     * Create a new instance of a conference call room availability result.
     * 
     * @return the new instance
     */
    public static ConferenceRoomsAvailability newInstance() {
        return new ConferenceRoomsAvailability();
    }
    
    /**
     * Get the room arrangements.
     * 
     * @return the room arrangements
     */
    public List<RoomArrangement> getRoomArrangements() {
        return this.roomArrangements;
    }
    
    /**
     * Add a room arrangement.
     * 
     * @param roomArrangement the room arrangement to add
     */
    public void addRoomArrangement(final RoomArrangement roomArrangement) {
        this.roomArrangements.add(roomArrangement);
    }
    
    /**
     * Get the processed location template.
     * 
     * @return the location
     */
    public String getLocation() {
        return location;
    }
    
    /**
     * Set the processed location template.
     * 
     * @param location the location to set
     */
    public void setLocation(final String location) {
        this.location = location;
    }
    
    /**
     * Get the common start time (for use with all day events).
     * 
     * @return start time
     */
    public Time getStartTime() {
        return this.timePeriod.getStartTime();
    }
    
    /**
     * Set the common start time.
     * 
     * @param startTime the start time
     */
    public void setStartTime(final Time startTime) {
        this.timePeriod.setStartTime(startTime);
    }
    
    /**
     * Get the common end time (for use with all day events).
     * 
     * @return end time
     */
    public Time getEndTime() {
        return this.timePeriod.getEndTime();
    }
    
    /**
     * Set the common end time.
     * 
     * @param endTime the end time
     */
    public void setEndTime(final Time endTime) {
        this.timePeriod.setEndTime(endTime);
    }
    
    /**
     * Get the serialized start time.
     * 
     * @return start time in serialized form
     */
    public String getSerializedStartTime() {
        return TimePeriod.serializeTime(this.getStartTime());
    }
    
    /**
     * Get the serialized end time.
     * 
     * @return end time in serialized form
     */
    public String getSerializedEndTime() {
        return TimePeriod.serializeTime(this.getEndTime());
    }
    
    /**
     * Get the locations template separator.
     * 
     * @return the location separator
     */
    public String getLocationSeparator() {
        return this.locationSeparator;
    }
    
    /**
     * Set the locations template separator.
     * 
     * @param locationSeparator the location separator to set
     */
    public void setLocationSeparator(final String locationSeparator) {
        this.locationSeparator = locationSeparator;
    }
    
}
