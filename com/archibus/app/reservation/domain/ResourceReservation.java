package com.archibus.app.reservation.domain;

import java.util.List;

import javax.xml.bind.annotation.*;

import com.archibus.app.reservation.util.DataSourceUtils;

/**
 * Domain class for Resource (only) Reservation.
 * 
 * @author Bart Vanderschoot
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "ResourceReservation")
public class ResourceReservation extends AbstractReservation {

    /** Contains all occurrences when a recurring reservation is created via Web Central. */
    protected List<ResourceReservation> createdReservations;
    
    /**
     * Default constructor.
     */
    public ResourceReservation() {
        super();
    }
    
    /**
     * Constructor for a time period.
     * 
     * @param timePeriod the time period
     */
    public ResourceReservation(final TimePeriod timePeriod) {
        super();
        setTimePeriod(timePeriod);
    }
    
    /**
     * Constructor using primary key.
     * 
     * @param reserveId id
     */
    public ResourceReservation(final Integer reserveId) {
        super(reserveId);
    }
    
    /**
     * Calculate total cost for the resource reservation.
     * 
     * @return total cost
     */
    public double calculateTotalCost() {
        // calculate resource costs.
        double totalCost = 0.0;
        
        for (final ResourceAllocation resourceAllocation : this.getResourceAllocations()) {
            totalCost += resourceAllocation.getCost();
        }

        // Round the result to two decimals.
        totalCost = DataSourceUtils.round2(totalCost);
        this.setCost(totalCost);
        
        return totalCost;
    }
    
    /**
     * Set the created resource reservations.
     * 
     * @param createdReservations the created resource reservations to set
     */
    public void setCreatedReservations(final List<ResourceReservation> createdReservations) {
        this.createdReservations = createdReservations;
    }
    
    /**
     * Get the created reservations.
     * 
     * @return the list of created reservations (only when the recurring reservation was just
     *         created)
     */
    @XmlTransient
    public List<ResourceReservation> getCreatedReservations() {
        return this.createdReservations;
    }
    
}
