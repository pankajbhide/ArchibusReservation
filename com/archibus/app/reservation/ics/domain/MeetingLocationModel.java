package com.archibus.app.reservation.ics.domain;

import net.fortuna.ical4j.model.property.Location;

/**
 * Copyright (C) ARCHIBUS, Inc. All rights reserved.
 */
/**
 * Represents the location for the meeting.
 * <p>
 *
 * @author PROCOS
 * @since 23.2
 *
 */
public class MeetingLocationModel {

    /** The building id. */
    private String building;

    /** The floor id. */
    private String floor;

    /** The room id. */
    private String room;

    /** The time zone. */
    private String timezone;

    /** The location description. */
    private String location;

    /**
     * Default constructor.
     */
    public MeetingLocationModel() {
        super();
    }

    /**
     * Default constructor specifying a room location.
     *
     * @param buildingId the building id
     * @param floorId the floor id
     * @param roomId the room id
     */
    public MeetingLocationModel(final String buildingId, final String floorId,
            final String roomId) {
        super();
        this.building = buildingId;
        this.floor = floorId;
        this.room = roomId;
    }

    /**
     * Getter for the timezone property.
     *
     * @return the timezone property.
     */
    public final String getTimezone() {
        return this.timezone;
    }

    /**
     * Setter for the timezone property.
     *
     * @param tzone the timezone to set.
     */
    public final void setTimezone(final String tzone) {
        this.timezone = tzone;
    }

    /**
     * Getter for the location property.
     *
     * @return the location property.
     */
    public final String getLocation() {
        return this.location;
    }

    /**
     * Setter for the location property.
     *
     * @param locationDescr the location to set.
     */
    public final void setLocation(final String locationDescr) {
        this.location = locationDescr;
    }

    /**
     * Getter for the building property.
     *
     * @return the building property.
     */
    public final String getBuilding() {
        return this.building;
    }

    /**
     * Getter for the floor property.
     *
     * @return the floor property.
     */
    public final String getFloor() {
        return this.floor;
    }

    /**
     * Getter for the room property.
     *
     * @return the room property.
     */
    public final String getRoom() {
        return this.room;
    }

    /**
     * Get the location in ICS format.
     * @return the location in ICS format
     */
    public Location getIcsLocation() {
        return new Location(this.getLocation());
    }

}
