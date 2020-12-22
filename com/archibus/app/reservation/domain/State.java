package com.archibus.app.reservation.domain;

/**
 * Domain object representing a state in the location hierarchy.
 * 
 * @author Yorik Gerlo
 * @since 21.2
 */
public class State {
    
    /** The country code. */
    private String countryId;
    
    /** The state code. */
    private String stateId;
    
    /** The state name. */
    private String name;
    
    /**
     * Get the country id of this state.
     * 
     * @return the countryId
     */
    public String getCountryId() {
        return this.countryId;
    }
    
    /**
     * Get this state's id.
     * 
     * @return the stateId
     */
    public String getStateId() {
        return this.stateId;
    }
    
    /**
     * Get the state name.
     * 
     * @return the name
     */
    public String getName() {
        return this.name;
    }
    
    /**
     * Set the country id of this state.
     * 
     * @param countryId the countryId to set
     */
    public void setCountryId(final String countryId) {
        this.countryId = countryId;
    }
    
    /**
     * Set the state id.
     * 
     * @param stateId the stateId to set
     */
    public void setStateId(final String stateId) {
        this.stateId = stateId;
    }
    
    /**
     * Set the state name.
     * 
     * @param name the name to set
     */
    public void setName(final String name) {
        this.name = name;
    }
    
}
