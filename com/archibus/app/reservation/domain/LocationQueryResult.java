package com.archibus.app.reservation.domain;

import java.util.List;

import javax.xml.bind.annotation.*;

import com.archibus.app.common.space.domain.*;

/**
 * The Class LocationQueryResult. Used to return multiple levels of the location hierarchy with a
 * single web service call. The UserLocation fields indicate the restrictions applied to the lower
 * levels.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "LocationQuery")
public class LocationQueryResult extends UserLocation {
    
    /** The list of countries. */
    private List<Country> countries;
    
    /** The list of states. */
    private List<State> states;
    
    /** The list of cities. */
    private List<City> cities;
    
    /** The list of sites. */
    private List<Site> sites;
    
    /** The list of buildings. */
    private List<Building> buildings;
    
    /** The list of floors. */
    private List<Floor> floors;
    
    /** The list of arrangements. */
    private List<ArrangeType> arrangements;

    /** The list of attributes. */
    private List<ResourceStandard> attributes;

    /**
     * Getter for the attributes property.
     *
     * @see attributes
     * @return the attributes property.
     */
    public List<ResourceStandard> getAttributes() {
        return this.attributes;
    }
    
    /**
     * Setter for the attributes property.
     *
     * @see attributes
     * @param attributes the attributes to set
     */
    public void setAttributes(final List<ResourceStandard> attributes) {
        this.attributes = attributes;
    }
    
    /**
     * Get the countries.
     *
     * @return the countries
     */
    public List<Country> getCountries() {
        return this.countries;
    }

    /**
     * Set the countries.
     *
     * @param countries the countries to set
     */
    public void setCountries(final List<Country> countries) {
        this.countries = countries;
    }

    /**
     * Get the states.
     *
     * @return the states
     */
    public List<State> getStates() {
        return this.states;
    }

    /**
     * Set the states.
     *
     * @param states the states to set
     */
    public void setStates(final List<State> states) {
        this.states = states;
    }

    /**
     * Get the cities.
     *
     * @return the cities
     */
    public List<City> getCities() {
        return this.cities;
    }

    /**
     * Set the cities.
     *
     * @param cities the cities to set
     */
    public void setCities(final List<City> cities) {
        this.cities = cities;
    }

    /**
     * Get the sites.
     *
     * @return the sites
     */
    public List<Site> getSites() {
        return this.sites;
    }

    /**
     * Set the sites.
     *
     * @param sites the sites to set
     */
    public void setSites(final List<Site> sites) {
        this.sites = sites;
    }

    /**
     * Get the buildings.
     *
     * @return the buildings
     */
    public List<Building> getBuildings() {
        return this.buildings;
    }

    /**
     * Set the buildings.
     *
     * @param buildings the buildings to set
     */
    public void setBuildings(final List<Building> buildings) {
        this.buildings = buildings;
    }

    /**
     * Get the floors.
     *
     * @return the floors
     */
    public List<Floor> getFloors() {
        return this.floors;
    }

    /**
     * Set the floors.
     *
     * @param floors the floors to set
     */
    public void setFloors(final List<Floor> floors) {
        this.floors = floors;
    }

    /**
     * Get the arrangements.
     *
     * @return the arrangements
     */
    public List<ArrangeType> getArrangements() {
        return this.arrangements;
    }

    /**
     * Set the arrangements.
     *
     * @param arrangements the arrangements to set
     */
    public void setArrangements(final List<ArrangeType> arrangements) {
        this.arrangements = arrangements;
    }

}
