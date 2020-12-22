package com.archibus.app.reservation.domain;

import java.util.*;

import com.archibus.app.common.space.domain.Room;

/**
 * The Room Availability will be used in the free/busy view. For a specific room and time period, it
 * will show all room allocations.
 *
 * @author Bart Vanderschoot
 *
 */
public class RoomAvailability {

    /** The end date. */
    private Date endDate;

    /** The room. */
    private Room room;

    /**
     * Room allocations for this room and time period.
     */
    private List<RoomAllocation> roomAllocations;

    /** The start date. */
    private Date startDate;

    /**
     * Default constructor.
     */
    public RoomAvailability() {
        super();
    }

    /**
     * Constructor using parameters.
     *
     * @param room the room
     * @param startDate the start date
     * @param endDate the end date
     */
    public RoomAvailability(final Room room, final Date startDate, final Date endDate) {
        this.room = room;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * Gets the end date.
     *
     * @return the end date
     */
    public final Date getEndDate() {
        return this.endDate;
    }

    /**
     * Gets the room.
     *
     * @return the room
     */
    public final Room getRoom() {
        return this.room;
    }

    /**
     * Gets the room allocations.
     *
     * @return the room allocations
     */
    public final List<RoomAllocation> getRoomAllocations() {
        return this.roomAllocations;
    }

    /**
     * Gets the start date.
     *
     * @return the start date
     */
    public final Date getStartDate() {
        return this.startDate;
    }

    /**
     * Sets the end date.
     *
     * @param endDate the new end date
     */
    public final void setEndDate(final Date endDate) {
        this.endDate = endDate;
    }

    /**
     * Sets the room.
     *
     * @param room the new room
     */
    public final void setRoom(final Room room) {
        this.room = room;
    }

    /**
     * Sets the room allocations.
     *
     * @param roomAllocations the new room allocations
     */
    public final void setRoomAllocations(final List<RoomAllocation> roomAllocations) {
        this.roomAllocations = roomAllocations;
    }

    /**
     * Sets the start date.
     *
     * @param startDate the new start date
     */
    public final void setStartDate(final Date startDate) {
        this.startDate = startDate;
    }

}
