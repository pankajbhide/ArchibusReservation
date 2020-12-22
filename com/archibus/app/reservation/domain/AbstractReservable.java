package com.archibus.app.reservation.domain;

import java.sql.Time;

import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Domain class for Reservable objects.
 *
 * Every reservable object (room or resources) should inherit from this base class.
 *
 * @author Bart Vanderschoot
 *         <p>
 *         Suppressed warning "PMD.TooManyFields" in this class.
 *         <p>
 *         Justification: reservables have a large number of fields in the database
 */
@SuppressWarnings({ "PMD.TooManyFields" })
public abstract class AbstractReservable implements IReservable {

    /** The ac id. */
    protected String acId;

    /** The announce days. */
    protected Integer announceDays;

    /** The announce time. */
    @XmlJavaTypeAdapter(TimeAdapter.class)
    protected Time announceTime;

    /** check if needs approval. */
    protected Integer approvalRequired;

    /** The approval days. */
    protected Integer approvalDays;

    /** The available for group. */
    protected String availableForGroup;

    /** The cancel days. */
    protected Integer cancelDays;

    /** The cancel time. */
    @XmlJavaTypeAdapter(TimeAdapter.class)
    protected Time cancelTime;

    /** The day end. */
    protected Time dayEnd;

    /** The day start. */
    protected Time dayStart;

    /** The doc image. */
    protected String docImage;

    /** The max days ahead. */
    protected Integer maxDaysAhead;

    /** The post block. */
    protected Integer postBlock;

    /** The pre block. */
    protected Integer preBlock;

    /** The reservable. */
    protected Integer reservable;

    /** The security group name. */
    protected String securityGroupName;

    /** cost per unit. */
    protected double costPerUnit;

    /** cost per unit. */
    protected double costPerUnitExternal;

    /**
     * TODO: create enumeration, since it is unlikely this will change cost unit.
     * 0;Reservation;1;Minute;2;Hour;3;Partial Day;4;Day
     */
    protected int costUnit;

    /** cost late cancel percentage. */
    protected int costLateCancelPercentage;

    /** {@inheritDoc} */

    @Override
    public final String getAcId() {
        return this.acId;
    }

    /** {@inheritDoc} */

    @Override
    public final Integer getAnnounceDays() {
        return this.announceDays;
    }

    /** {@inheritDoc} */

    @Override
    @XmlTransient
    public final Time getAnnounceTime() {
        return this.announceTime;
    }

    /** {@inheritDoc} */

    @Override
    public final Integer getApprovalDays() {
        return this.approvalDays;
    }

    /** {@inheritDoc} */

    @Override
    public final String getAvailableForGroup() {
        return this.availableForGroup;
    }

    /** {@inheritDoc} */

    @Override
    public final Integer getCancelDays() {
        return this.cancelDays;
    }

    /** {@inheritDoc} */

    @Override
    @XmlTransient
    public final Time getCancelTime() {
        return this.cancelTime;
    }

    /** {@inheritDoc} */

    @Override
    public final Time getDayEnd() {
        return this.dayEnd;
    }

    /** {@inheritDoc} */

    @Override
    public final Time getDayStart() {
        return this.dayStart;
    }

    /** {@inheritDoc} */

    @Override
    public final String getDocImage() {
        return this.docImage;
    }

    /** {@inheritDoc} */

    @Override
    public final Integer getMaxDaysAhead() {
        return this.maxDaysAhead;
    }

    /** {@inheritDoc} */

    @Override
    public final Integer getPostBlock() {
        return this.postBlock;
    }

    /** {@inheritDoc} */

    @Override
    public final Integer getPreBlock() {
        return this.preBlock;
    }

    /** {@inheritDoc} */

    @Override
    public final Integer getReservable() {
        return this.reservable;
    }

    /** {@inheritDoc} */

    @Override
    public final String getSecurityGroupName() {
        return this.securityGroupName;
    }

    /** {@inheritDoc} */

    @Override
    public final void setAcId(final String acId) {
        this.acId = acId;
    }

    /** {@inheritDoc} */

    @Override
    public final void setAnnounceDays(final Integer announceDays) {
        this.announceDays = announceDays;
    }

    /** {@inheritDoc} */

    @Override
    public final void setAnnounceTime(final Time announceTime) {
        this.announceTime = announceTime;
    }

    /** {@inheritDoc} */

    @Override
    public final void setApprovalDays(final Integer approvalDays) {
        this.approvalDays = approvalDays;
    }

    /** {@inheritDoc} */

    @Override
    public final void setAvailableForGroup(final String availableForGroup) {
        this.availableForGroup = availableForGroup;
    }

    /** {@inheritDoc} */

    @Override
    public final void setCancelDays(final Integer cancelDays) {
        this.cancelDays = cancelDays;
    }

    /** {@inheritDoc} */

    @Override
    public final void setCancelTime(final Time cancelTime) {
        this.cancelTime = cancelTime;
    }

    /** {@inheritDoc} */

    @Override
    public final void setDayEnd(final Time dayEnd) {
        this.dayEnd = dayEnd;
    }

    /** {@inheritDoc} */

    @Override
    public final void setDayStart(final Time dayStart) {
        this.dayStart = dayStart;
    }

    /** {@inheritDoc} */

    @Override
    public final void setDocImage(final String docImage) {
        this.docImage = docImage;
    }

    /** {@inheritDoc} */

    @Override
    public final void setMaxDaysAhead(final Integer maxDaysAhead) {
        this.maxDaysAhead = maxDaysAhead;
    }

    /** {@inheritDoc} */

    @Override
    public final void setPostBlock(final Integer postBlock) {
        this.postBlock = postBlock;
    }

    /** {@inheritDoc} */

    @Override
    public final void setPreBlock(final Integer preBlock) {
        this.preBlock = preBlock;
    }

    /** {@inheritDoc} */

    @Override
    public final void setReservable(final Integer reservable) {
        this.reservable = reservable;
    }

    /** {@inheritDoc} */

    @Override
    public final void setSecurityGroupName(final String securityGroupName) {
        this.securityGroupName = securityGroupName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getApprovalRequired() {
        return this.approvalRequired;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setApprovalRequired(final Integer approvalRequired) {
        this.approvalRequired = approvalRequired;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getCostPerUnit() {
        return this.costPerUnit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCostPerUnit(final double costPerUnit) {
        this.costPerUnit = costPerUnit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getCostPerUnitExternal() {
        return this.costPerUnitExternal;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCostPerUnitExternal(final double costPerUnitExternal) {
        this.costPerUnitExternal = costPerUnitExternal;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCostUnit() {
        return this.costUnit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCostUnit(final int costUnit) {
        this.costUnit = costUnit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCostLateCancelPercentage() {
        return this.costLateCancelPercentage;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCostLateCancelPercentage(final int costLateCancelPercentage) {
        this.costLateCancelPercentage = costLateCancelPercentage;
    }

}
