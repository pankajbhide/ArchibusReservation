package com.archibus.app.reservation.domain;

import javax.xml.bind.annotation.*;

/**
 * The Class UserLocation.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "UserLocation")
public class UserLocation {
    
    /** The country id. */
    private String countryId;
    
    /** The state id. */
    private String stateId;
    
    /** The city id. */
    private String cityId;
    
    /** The site id. */
    private String siteId;
    
    /** The building id. */
    private String buildingId;
    
    /** The floor id. */
    private String floorId;
    
    /** The room id. */
    private String roomId;
    
    /**
     * Gets the country id.
     * 
     * @return the country id
     */
    public final String getCountryId() {
        return this.countryId;
    }
    
    /**
     * Sets the country id.
     * 
     * @param countryId the new country id
     */
    public final void setCountryId(final String countryId) {
        this.countryId = countryId;
    }
    
    /**
     * Get the state id.
     * 
     * @return the stateId
     */
    public final String getStateId() {
        return this.stateId;
    }
    
    /**
     * Set the state id.
     * 
     * @param stateId the stateId to set
     */
    public final void setStateId(final String stateId) {
        this.stateId = stateId;
    }
    
    /**
     * Get the city id.
     * 
     * @return the cityId
     */
    public final String getCityId() {
        return this.cityId;
    }
    
    /**
     * Set the city id.
     * 
     * @param cityId the cityId to set
     */
    public final void setCityId(final String cityId) {
        this.cityId = cityId;
    }
    
    /**
     * Gets the site id.
     * 
     * @return the site id
     */
    public final String getSiteId() {
        return this.siteId;
    }
    
    /**
     * Sets the site id.
     * 
     * @param siteId the new site id
     */
    public final void setSiteId(final String siteId) {
        this.siteId = siteId;
    }
    
    /**
     * Gets the building id.
     * 
     * @return the building id
     */
    public final String getBuildingId() {
        return this.buildingId;
    }
    
    /**
     * Sets the building id.
     * 
     * @param buildingId the new building id
     */
    public final void setBuildingId(final String buildingId) {
        this.buildingId = buildingId;
    }
    
    /**
     * Gets the floor id.
     * 
     * @return the floor id
     */
    public final String getFloorId() {
        return this.floorId;
    }
    
    /**
     * Sets the floor id.
     * 
     * @param floorId the new floor id
     */
    public final void setFloorId(final String floorId) {
        this.floorId = floorId;
    }
    
    /**
     * Gets the room id.
     * 
     * @return the room id
     */
    public final String getRoomId() {
        return this.roomId;
    }
    
    /**
     * Sets the room id.
     * 
     * @param roomId the new room id
     */
    public final void setRoomId(final String roomId) {
        this.roomId = roomId;
    }
    
}
