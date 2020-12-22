package com.archibus.app.reservation.domain;

import java.sql.Time;
import java.util.Date;

import javax.xml.bind.annotation.*;

/**
 * Abstract base object providing some information connected to reservations or allocations.
 *
 * @author Yorik Gerlo
 * @since 20.1
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "AbstractReservationBase")
public abstract class AbstractReservationBase implements ITimePeriodBased {

    /** The period. End date is not used for allocations at this time, there is no field. */
    protected final TimePeriod period = new TimePeriod();

    /** The cancelled date. */
    private Date cancelledDate;

    /** The comments. */
    private String comments;

    /**
     * The created by. Not used for allocations at this time, there is no field.
     */
    private String createdBy;

    /** The creation date. */
    private Date creationDate;

    /** The last modified by. */
    private String lastModifiedBy;

    /** The last modified date. */
    private Date lastModifiedDate;

    /** The reserve id. */
    private Integer reserveId;

    /** The status. */
    private String status;

    /** The cost of the allocation. */
    private double cost;

    /**
     * Default constructor.
     */
    public AbstractReservationBase() {
        super();
    }

    /**
     * Constructor.
     *
     * @param reserveId the reservation identifier for this domain object
     */
    public AbstractReservationBase(final Integer reserveId) {
        super();
        this.reserveId = reserveId;
    }

    /**
     * Gets the cancelled date.
     *
     * @return the cancelled date
     */
    @XmlElement(nillable = true, required = false)
    public final Date getCancelledDate() {
        return this.cancelledDate;
    }

    /**
     * Gets the comments.
     *
     * @return the comments
     */
    public final String getComments() {
        return this.comments;
    }

    /**
     * Gets the created by.
     *
     * @return the created by
     */
    public final String getCreatedBy() {
        return this.createdBy;
    }

    /**
     * Gets the creation date.
     *
     * @return the creation date
     */
    @XmlElement(nillable = true, required = false)
    public final Date getCreationDate() {
        return this.creationDate;
    }

    /**
     * Gets the last modified by.
     *
     * @return the last modified by
     */
    public final String getLastModifiedBy() {
        return this.lastModifiedBy;
    }

    /**
     * Gets the last modified date.
     *
     * @return the last modified date
     */
    @XmlElement(nillable = true, required = false)
    public final Date getLastModifiedDate() {
        return this.lastModifiedDate;
    }

    /**
     * Get the reserve id.
     *
     * @return reserve id
     * @see com.archibus.reservation.domain.IReservation#getReserveId()
     */
    public final Integer getReserveId() {
        return this.reserveId;
    }

    /**
     * Gets the status.
     *
     * @return status
     *
     * @see com.archibus.reservation.domain.IReservation#getResponseStatus()
     */
    public final String getStatus() {
        return this.status;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @XmlTransient
    public final Date getEndDate() {
        return this.period.getEndDate();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void setEndDate(final Date endDate) {
        this.period.setEndDate(endDate);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @XmlTransient
    public final Time getEndTime() {
        return this.period.getEndTime();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @XmlTransient
    public final Time getStartTime() {
        return this.period.getStartTime();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void setEndTime(final Time endTime) {
        this.period.setEndTime(endTime);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @XmlTransient
    public final Date getStartDate() {
        return this.period.getStartDate();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void setStartDate(final Date reservationDate) {
        this.period.setStartDate(reservationDate);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void setStartTime(final Time startTime) {
        this.period.setStartTime(startTime);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void setStartDateTime(final Date startDateTime) {
        this.period.setStartDateTime(startDateTime);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Date getStartDateTime() {
        return this.period.getStartDateTime();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void setEndDateTime(final Date endDateTime) {
        this.period.setEndDateTime(endDateTime);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Date getEndDateTime() {
        return this.period.getEndDateTime();
    }

    /**
     * Sets the cancelled date.
     *
     * @param cancelledDate the new cancelled date
     */
    public final void setCancelledDate(final Date cancelledDate) {
        this.cancelledDate = cancelledDate;
    }

    /**
     * Sets the comments.
     *
     * @param comments the new comments
     */
    public final void setComments(final String comments) {
        this.comments = comments;
    }

    /**
     * Sets the created by.
     *
     * @param createdBy the new created by
     */
    public final void setCreatedBy(final String createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * Sets the creation date.
     *
     * @param creationDate the new creation date
     */
    public final void setCreationDate(final Date creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * Sets the last modified by.
     *
     * @param lastModifiedBy the new last modified by
     */
    public final void setLastModifiedBy(final String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    /**
     * Sets the last modified date.
     *
     * @param lastModifiedDate the new last modified date
     */
    public final void setLastModifiedDate(final Date lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    /**
     * Sets the reserve id.
     *
     * @param reserveId the reserve id
     *
     * @see com.archibus.reservation.domain.IReservation#setReserveId(java.lang.Integer)
     */
    public final void setReserveId(final Integer reserveId) {
        this.reserveId = reserveId;
    }

    /**
     * Sets the status.
     *
     * @param status the new status
     */
    public final void setStatus(final String status) {
        this.status = status;
    }

    /**
     * Getter for the cost property.
     *
     * @see cost
     * @return the cost property.
     */
    public double getCost() {
        return this.cost;
    }

    /**
     * Setter for the allocation cost.
     *
     * @see cost
     * @param cost the cost to set
     */
    public void setCost(final double cost) {
        this.cost = cost;
    }

    /**
     * {@inheritDoc}
     */
    @XmlTransient
    public final TimePeriod getTimePeriod() {
        return this.period;
    }

    /**
     * Sets the time period.
     *
     * @param timePeriod the new time period
     */
    public final void setTimePeriod(final TimePeriod timePeriod) {
        this.setStartDate(timePeriod.getStartDate());
        this.setStartTime(timePeriod.getStartTime());
        this.setEndDate(timePeriod.getEndDate());
        this.setEndTime(timePeriod.getEndTime());
        this.setTimeZone(timePeriod.getTimeZone());
    }

    /**
     * Gets the time zone.
     *
     * @return the time zone
     */
    @Override
    public final String getTimeZone() {
        return this.period.getTimeZone();
    }

    /**
     * Sets the time zone.
     *
     * @param timeZone the new time zone
     */
    @Override
    public final void setTimeZone(final String timeZone) {
        this.period.setTimeZone(timeZone);
    }

}
