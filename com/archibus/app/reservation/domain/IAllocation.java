package com.archibus.app.reservation.domain;

import java.util.Date;

/**
 * Interface for allocation of rooms or resources linked to a reservation.
 * 
 * 
 * @author Bart Vanderschoot
 * 
 */
public interface IAllocation extends ITimePeriodBased {
    /**
     * Gets the id.
     * 
     * @return the id
     */
    Integer getId();
    
    /**
     * Gets the rejected date.
     * 
     * @return the rejected date
     */
    Date getRejectedDate();
    
    /**
     * Sets the rejected date.
     * 
     * @param rejectedDate the new rejected date
     */
    void setRejectedDate(final Date rejectedDate);
    
    /**
     * Set properties of the reservation.
     * 
     * Date and time of the allocation are copied from the reservation.
     * 
     * @param reservation reservation
     */
    void setReservation(final IReservation reservation);
    
    /**
     * Copy to.
     * 
     * @param allocation the target allocation
     */
    void copyTo(final AbstractAllocation allocation);
    
    /**
     * Gets the identifier of the building where the allocation is located.
     * 
     * @return the building identifier
     */
    String getBlId();
    
    /**
     * Gets the floor code of this allocation.
     * 
     * @return the floor code
     */
    String getFlId();
    
    /**
     * Gets the room code.
     * 
     * @return the room code of this allocation
     */
    String getRmId();
    
    /**
     * Sets the building identifier of this allocation.
     * 
     * @param blId the new building code
     */
    void setBlId(final String blId);
    
    /**
     * Sets the floor code of this allocation.
     * 
     * @param flId the new floor code
     */
    void setFlId(final String flId);
    
    /**
     * Sets the room code of this allocation.
     * 
     * @param rmId the new room code
     */
    void setRmId(final String rmId);
    
    /**
     * Gets the cost.
     * 
     * @return the cost
     */
    double getCost();
    
    /**
     * Sets the cost.
     * 
     * @param cost the new cost
     */
    void setCost(double cost);
    
    /**
     * 
     * Get Time Period.
     * 
     * @return time period
     */
    TimePeriod getTimePeriod();
    
    /**
     * Set the status.
     * 
     * @param status status
     */
    void setStatus(final String status);
    
    /**
     * Get the status.
     * 
     * @return the status
     */
    String getStatus();
    
}
