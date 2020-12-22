package com.archibus.app.reservation.domain;

/**
 * Domain object representing a city in the location hierarchy.
 * 
 * @author Yorik Gerlo
 * @since 21.2
 */
public class City {
    
    /** The country code. */
    private String countryId;
    
    /** The state code. */
    private String stateId;
    
    /** The city code. */
    private String cityId;
    
    /** The city name. */
    private String name;
    
    /**
     * Get the country id for this city.
     * 
     * @return the countryId
     */
    public String getCountryId() {
        return this.countryId;
    }
    
    /**
     * Get the state id for this city.
     * 
     * @return the stateId
     */
    public String getStateId() {
        return this.stateId;
    }
    
    /**
     * Get the city id.
     * 
     * @return the cityId
     */
    public String getCityId() {
        return this.cityId;
    }
    
    /**
     * Get the city name.
     * 
     * @return the name
     */
    public String getName() {
        return this.name;
    }
    
    /**
     * Set the country id for this city.
     * 
     * @param countryId the countryId to set
     */
    public void setCountryId(final String countryId) {
        this.countryId = countryId;
    }
    
    /**
     * Set the state id for this city.
     * 
     * @param stateId the stateId to set
     */
    public void setStateId(final String stateId) {
        this.stateId = stateId;
    }
    
    /**
     * Set the city id.
     * 
     * @param cityId the cityId to set
     */
    public void setCityId(final String cityId) {
        this.cityId = cityId;
    }
    
    /**
     * Set the city name.
     * 
     * @param name the name to set
     */
    public void setName(final String name) {
        this.name = name;
    }
    
}
