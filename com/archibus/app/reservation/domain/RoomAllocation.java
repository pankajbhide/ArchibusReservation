package com.archibus.app.reservation.domain;

import javax.xml.bind.annotation.*;

/**
 * Domain class for Room Allocation for a room reservation.
 * 
 * @author Bart Vanderschoot
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "RoomAllocation")
public class RoomAllocation extends AbstractAllocation {
    
    /** The arrange type id of this allocation. */
    private String arrangeTypeId;
    
    /** The config id. */
    private String configId;
    
    /** # of external guests. */
    private int externalGuests;
    
    /** The id. */
    private Integer id;
    
    /** # of internal guests. */
    private int internalGuests;
    
    /** Number of attendees that will be present in the room. */
    private int attendeesInRoom;
    
    /**
     * Default constructor.
     */
    public RoomAllocation() {
        super();
    }
    
    /**
     * Instantiates a new room allocation.
     * 
     * @param roomArrangement the room arrangement
     */
    public RoomAllocation(final RoomArrangement roomArrangement) {
        super();
        setBlId(roomArrangement.getBlId());
        setFlId(roomArrangement.getFlId());
        setRmId(roomArrangement.getRmId());
        this.configId = roomArrangement.getConfigId();
        this.arrangeTypeId = roomArrangement.getArrangeTypeId();
    }
    
    /**
     * Instantiates a new room allocation.
     * 
     * @param roomArrangement the room arrangement
     * @param reservation the reservation
     */
    public RoomAllocation(final RoomArrangement roomArrangement, final IReservation reservation) {
        this(roomArrangement);
        // set the reservation, always setter
        setReservation(reservation);
    }
    
    /**
     * Instantiates a new room allocation.
     * 
     * @param blId the building code
     * @param flId the floor code
     * @param rmId the room code
     * @param configId the config id
     * @param arrangeTypeId the arrange type id
     */
    public RoomAllocation(final String blId, final String flId, final String rmId,
            final String configId, final String arrangeTypeId) {
        super(blId, flId, rmId);
        this.configId = configId;
        this.arrangeTypeId = arrangeTypeId;
    }
    
    /**
     * Instantiates a new room allocation.
     * 
     * @param blId the building code
     * @param flId the floor code
     * @param rmId the room code
     * @param configId the config id
     * @param arrangeTypeId the arrange type id
     * @param reservation the reservation
     */
    public RoomAllocation(final String blId, final String flId, final String rmId,
            final String configId, final String arrangeTypeId, final IReservation reservation) {
        this(blId, flId, rmId, configId, arrangeTypeId);
        // set the reservation, always setter
        setReservation(reservation);
    }
    
    /**
     * Copy this object's properties to the room allocation provided as a parameter.
     * 
     * @param allocation the room allocation to modify according to this object's properties
     */
    @Override
    public void copyTo(final AbstractAllocation allocation) {
        if (allocation instanceof RoomAllocation) {
            super.copyTo(allocation);
            final RoomAllocation roomAllocation = (RoomAllocation) allocation;
            roomAllocation.setConfigId(this.getConfigId());
            roomAllocation.setArrangeTypeId(this.getArrangeTypeId());
            roomAllocation.setAttendeesInRoom(this.getAttendeesInRoom());
        } else {
            throw new IllegalArgumentException("Target object is not a room allocation.");
        }
    }
    
    /**
     * Gets the arrange type identifier.
     * 
     * @return the arrange type identifier
     */
    public final String getArrangeTypeId() {
        return this.arrangeTypeId;
    }
    
    /**
     * Gets the configuration identifier of the allocated room.
     * 
     * @return the configuration identifier
     */
    public final String getConfigId() {
        return this.configId;
    }
    
    /**
     * Gets the number of external guests.
     * 
     * @return the number of external guests
     */
    public final int getExternalGuests() {
        return this.externalGuests;
    }
    
    /**
     * Get the primary key.
     * 
     * @return primary key
     * 
     */
    @Override
    public final Integer getId() {
        return this.id;
    }
    
    /**
     * Gets the number of internal guests.
     * 
     * @return the number of internal guests
     */
    public final int getInternalGuests() {
        return this.internalGuests;
    }
    
    /**
     * Gets the room arrangement.
     * 
     * @return the room arrangement
     */
    @XmlTransient
    public final RoomArrangement getRoomArrangement() {
        return new RoomArrangement(getBlId(), getFlId(), getRmId(), this.configId,
            this.arrangeTypeId);
    }
    
    /**
     * Sets the room arrangement.
     * 
     * @param roomArrangement the room arrangement
     */
    public void setRoomArrangement(final RoomArrangement roomArrangement) {
        setBlId(roomArrangement.getBlId());
        setFlId(roomArrangement.getFlId());
        setRmId(roomArrangement.getRmId());
        setConfigId(roomArrangement.getConfigId());
        setArrangeTypeId(roomArrangement.getArrangeTypeId());
    }
    
    /**
     * Sets the arrange type id.
     * 
     * @param arrangeTypeId the new arrange type id to set
     */
    public final void setArrangeTypeId(final String arrangeTypeId) {
        this.arrangeTypeId = arrangeTypeId;
    }
    
    /**
     * Sets the configuration identifier of this room allocation.
     * 
     * @param configId the new config id
     */
    public final void setConfigId(final String configId) {
        this.configId = configId;
    }
    
    /**
     * Sets the number of external guests.
     * 
     * @param externalGuests the new number of external guests
     */
    public final void setExternalGuests(final int externalGuests) {
        this.externalGuests = externalGuests;
    }
    
    /**
     * Sets the id.
     * 
     * @param id the new id
     */
    public final void setId(final Integer id) {
        this.id = id;
    }
    
    /**
     * Sets the number of internal guests.
     * 
     * @param internalGuests the new number of internal guests
     */
    public final void setInternalGuests(final int internalGuests) {
        this.internalGuests = internalGuests;
    }
    
    /**
     * Get the number of attendees in the room.
     * 
     * @return the number of attendees in the room
     */
    public int getAttendeesInRoom() {
        return this.attendeesInRoom;
    }
    
    /**
     * Set the number of attendees in the room.
     * 
     * @param attendeesInRoom the number of attendees to set
     */
    public void setAttendeesInRoom(final int attendeesInRoom) {
        this.attendeesInRoom = attendeesInRoom;
    }
    
}
