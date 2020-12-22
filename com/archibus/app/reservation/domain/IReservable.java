package com.archibus.app.reservation.domain;

import java.sql.Time;

/**
 * Interface for a reservable object.
 * 
 * @author Bart Vanderschoot
 * @since 20.1
 * 
 */
public interface IReservable {
    
    /**
     * Gets the account code.
     * 
     * @return the account code
     */
    String getAcId();
    
    /**
     * Gets the announce days.
     * 
     * @return the announce days
     */
    Integer getAnnounceDays();
    
    /**
     * Gets the announce time.
     * 
     * @return the announce time
     */
    Time getAnnounceTime();
    
    /**
     * Gets the approval days.
     * 
     * @return the approval days
     */
    Integer getApprovalDays();
    
    /**
     * Gets the available for group.
     * 
     * @return the available for group
     */
    String getAvailableForGroup();
    
    /**
     * Gets the cancel days.
     * 
     * @return the cancel days
     */
    Integer getCancelDays();
    
    /**
     * Gets the cancel time.
     * 
     * @return the cancel time
     */
    Time getCancelTime();
    
    /**
     * Gets the day end.
     * 
     * @return the day end
     */
    Time getDayEnd();
    
    /**
     * Gets the day start.
     * 
     * @return the day start
     */
    Time getDayStart();
    
    /**
     * Gets the doc image.
     * 
     * @return the doc image
     */
    String getDocImage();
    
    /**
     * Gets the max days ahead.
     * 
     * @return the max days ahead
     */
    Integer getMaxDaysAhead();
    
    /**
     * Gets the post block.
     * 
     * @return the post block
     */
    Integer getPostBlock();
    
    /**
     * Gets the pre block.
     * 
     * @return the pre block
     */
    Integer getPreBlock();
    
    /**
     * Gets the reservable.
     * 
     * @return the reservable
     */
    Integer getReservable();
    
    /**
     * Gets the security group name.
     * 
     * @return the security group name
     */
    String getSecurityGroupName();
    
    /**
     * Sets the ac id.
     * 
     * @param acId the new ac id
     */
    void setAcId(final String acId);
    
    /**
     * Sets the announce days.
     * 
     * @param announceDays the new announce days
     */
    void setAnnounceDays(final Integer announceDays);
    
    /**
     * Sets the announce time.
     * 
     * @param announceTime the new announce time
     */
    void setAnnounceTime(final Time announceTime);
    
    /**
     * Sets the approval days.
     * 
     * @param approvalDays the new approval days
     */
    void setApprovalDays(final Integer approvalDays);
    
    /**
     * Sets the available for group.
     * 
     * @param availableForGroup the new available for group
     */
    void setAvailableForGroup(final String availableForGroup);
    
    /**
     * Sets the cancel days.
     * 
     * @param cancelDays the new cancel days
     */
    void setCancelDays(final Integer cancelDays);
    
    /**
     * Sets the cancel time.
     * 
     * @param cancelTime the new cancel time
     */
    void setCancelTime(final Time cancelTime);
    
    /**
     * Sets the day end.
     * 
     * @param dayEnd the new day end
     */
    void setDayEnd(final Time dayEnd);
    
    /**
     * Sets the day start.
     * 
     * @param dayStart the new day start
     */
    void setDayStart(final Time dayStart);
    
    /**
     * Sets the doc image.
     * 
     * @param docImage the new doc image
     */
    void setDocImage(final String docImage);
    
    /**
     * Sets the max days ahead.
     * 
     * @param maxDaysAhead the new max days ahead
     */
    void setMaxDaysAhead(final Integer maxDaysAhead);
    
    /**
     * Sets the post block.
     * 
     * @param postBlock the new post block
     */
    void setPostBlock(final Integer postBlock);
    
    /**
     * Sets the pre block.
     * 
     * @param preBlock the new pre block
     */
    void setPreBlock(final Integer preBlock);
    
    /**
     * Sets the reservable.
     * 
     * @param reservable the new reservable
     */
    void setReservable(final Integer reservable);
    
    /**
     * Sets the security group name.
     * 
     * @param securityGroupName the new security group name
     */
    void setSecurityGroupName(final String securityGroupName);
    
    /**
     * Getter for the approvalRequired property.
     * 
     * @see approvalRequired
     * @return the approvalRequired property.
     */
    Integer getApprovalRequired();
    
    /**
     * Setter for the approvalRequired property.
     * 
     * @see approvalRequired
     * @param approvalRequired the approvalRequired to set
     */
    void setApprovalRequired(Integer approvalRequired);
    
    /**
     * Getter for the costPerUnit property.
     * 
     * @see costPerUnit
     * @return the costPerUnit property.
     */
    double getCostPerUnit();
    
    /**
     * Setter for the costPerUnit property.
     * 
     * @see costPerUnit
     * @param costPerUnit the costPerUnit to set
     */
    
    void setCostPerUnit(final double costPerUnit);
    
    /**
     * Getter for the costPerUnitExternal property.
     * 
     * @see costPerUnitExternal
     * @return the costPerUnitExternal property.
     */
    double getCostPerUnitExternal();
    
    /**
     * Setter for the costPerUnitExternal property.
     * 
     * @see costPerUnitExternal
     * @param costPerUnitExternal the costPerUnitExternal to set
     */
    
    void setCostPerUnitExternal(final double costPerUnitExternal);
    
    /**
     * Getter for the costUnit property.
     * 
     * @see costUnit
     * @return the costUnit property.
     */
    int getCostUnit();
    
    /**
     * Setter for the costUnit property.
     * 
     * @see costUnit
     * @param costUnit the costUnit to set
     */
    
    void setCostUnit(final int costUnit);
    
    /**
     * Getter for the costLateCancelPercentage property.
     * 
     * @see costLateCancelPercentage
     * @return the costLateCancelPercentage property.
     */
    int getCostLateCancelPercentage();
    
    /**
     * Setter for the costLateCancelPercentage property.
     * 
     * @see costLateCancelPercentage
     * @param costLateCancelPercentage the costLateCancelPercentage to set
     */
    
    void setCostLateCancelPercentage(final int costLateCancelPercentage);
    
}