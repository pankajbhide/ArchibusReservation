package com.archibus.app.reservation.domain;

import javax.xml.bind.annotation.*;

/**
 * The Class LocationQuery. Used to query multiple levels of the location hierarchy with a single
 * web service call.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "LocationQuery")
public class LocationQuery extends UserLocation {
    
    /** Which levels of the location hierarchy to return. */
    private String levelsToInclude;
    
    /**
     * Get which levels to return in the result.
     *
     * @return the levelsToReturn
     */
    public String getLevelsToInclude() {
        return this.levelsToInclude;
    }
    
    /**
     * Set which levels to return in the result.
     *
     * @param levelsToInclude the levelsToReturn to set
     */
    public void setLevelsToInclude(final String levelsToInclude) {
        this.levelsToInclude = levelsToInclude;
    }
    
    /**
     * Check whether countries must be returned in the result.
     * 
     * @return true if countries must be included, false otherwise
     */
    @XmlTransient
    public boolean includeCountries() {
        return this.levelsToInclude != null && this.levelsToInclude.contains("country");
    }
    
    /**
     * Check whether states must be returned in the result.
     * 
     * @return true if states must be included, false otherwise
     */
    @XmlTransient
    public boolean includeStates() {
        return this.levelsToInclude != null && this.levelsToInclude.contains("state");
    }
    
    /**
     * Check whether cities must be returned in the result.
     * 
     * @return true if cities must be included, false otherwise
     */
    @XmlTransient
    public boolean includeCities() {
        return this.levelsToInclude != null && this.levelsToInclude.contains("city");
    }

    /**
     * Check whether sites must be returned in the result.
     * 
     * @return true if sites must be included, false otherwise
     */
    @XmlTransient
    public boolean includeSites() {
        return this.levelsToInclude != null && this.levelsToInclude.contains("site");
    }
    
    /**
     * Check whether buildings must be returned in the result.
     * 
     * @return true if buildings must be included, false otherwise
     */
    @XmlTransient
    public boolean includeBuildings() {
        return this.levelsToInclude != null && this.levelsToInclude.contains("building");
    }
    
    /**
     * Check whether floors must be returned in the result.
     * 
     * @return true if floors must be included, false otherwise
     */
    @XmlTransient
    public boolean includeFloors() {
        return this.levelsToInclude != null && this.levelsToInclude.contains("floor");
    }
    
    /**
     * Check whether arrangements must be returned in the result.
     * 
     * @return true if arrangements must be included, false otherwise
     */
    @XmlTransient
    public boolean includeArrangements() {
        return this.levelsToInclude != null && this.levelsToInclude.contains("arrangement");
    }

    /**
     * Check whether room attributes must be returned in the result.
     * 
     * @return true if attributes must be included, false otherwise
     */
    @XmlTransient
    public boolean includeAttributes() {
        return this.levelsToInclude != null && this.levelsToInclude.contains("attributes");
    }
    
}
