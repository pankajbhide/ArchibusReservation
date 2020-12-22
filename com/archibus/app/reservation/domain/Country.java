package com.archibus.app.reservation.domain;

/**
 * Domain object representing a country in the location hierarchy.
 * 
 * @author Yorik Gerlo
 * @since 21.2
 */
public class Country {
    
    /** The country code. */
    private String countryId;
    
    /** The country name. */
    private String name;
    
    /**
     * Get this country's id.
     * 
     * @return the countryId
     */
    public String getCountryId() {
        return this.countryId;
    }
    
    /**
     * Get the country name.
     * 
     * @return the name
     */
    public String getName() {
        return this.name;
    }
    
    /**
     * Set the country id.
     * 
     * @param countryId the countryId to set
     */
    public void setCountryId(final String countryId) {
        this.countryId = countryId;
    }
    
    /**
     * Set the country name.
     * 
     * @param name the name to set
     */
    public void setName(final String name) {
        this.name = name;
    }
    
}
