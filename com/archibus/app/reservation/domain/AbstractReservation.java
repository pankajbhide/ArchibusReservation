package com.archibus.app.reservation.domain;

import java.util.*;

import javax.xml.bind.annotation.*;

import com.archibus.app.reservation.domain.recurrence.Recurrence;
import com.archibus.app.reservation.util.ReservationUtils;
import com.archibus.utility.StringUtil;

/**
 * Abstract reservation class.
 *
 * Implements the common reservation interface (room and resource reservations).
 *
 * @author Bart Vanderschoot
 *
 *         <p>
 *         Suppressed warning "PMD.TooManyFields" in this class.
 *         <p>
 *         Justification: reservations have a large number of fields in the database
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "AbstractReservation")
public abstract class AbstractReservation extends AbstractReservationProperties
        implements IReservation {

    /** The resource allocations. */
    private List<ResourceAllocation> resourceAllocations;

    /** The recurrence pattern for creating a recurring reservation. */
    private Recurrence recurrence;

    /** Building id to use when no room nor resource allocations are in the reservation. */
    private String backupBuildingId;

    /** Comments in HTML format for Exchange. */
    private String htmlComments;

    /**
     * Instantiates a new abstract reservation.
     */
    public AbstractReservation() {
        super();
    }

    /**
     * Instantiates a new abstract reservation.
     *
     * @param reserveId the reserve id
     */
    public AbstractReservation(final Integer reserveId) {
        super(reserveId);
    }

    /**
     * Adds the resource allocation.
     *
     * @param resourceAllocation the resource allocation
     */
    public final void addResourceAllocation(final ResourceAllocation resourceAllocation) {
        if (this.resourceAllocations == null) {
            this.resourceAllocations = new ArrayList<ResourceAllocation>();
        }
        // setReservation makes sure the date of time values are set to 1899
        this.resourceAllocations.add(resourceAllocation);
        resourceAllocation.setReservation(this);
    }

    /**
     * Copy to. Only change attributes that are allowed when editing
     *
     * @param reservation the reservation
     * @param allowDateChange the allow date change
     * @return the abstract reservation
     */
    public final AbstractReservation copyTo(final AbstractReservation reservation,
            final boolean allowDateChange) {

        if (StringUtil.isNullOrEmpty(reservation.getCreatedBy())) {
            reservation.setCreatedBy(this.getCreatedBy());
        }
        if (reservation.getCreationDate() == null) {
            reservation.setCreationDate(new Date());
        }
        if (allowDateChange) {
            reservation.setStartDate(this.getStartDate());
            reservation.setEndDate(this.getEndDate());
        }
        reservation.setEndTime(this.getEndTime());
        reservation.setStartTime(this.getStartTime());
        reservation.setRequestedBy(this.getRequestedBy());
        reservation.setRequestedFor(this.getRequestedFor());
        reservation.setReservationName(this.getReservationName());

        /*
         * Don't change the target reservation status. If required, the datasource will update the
         * status when saving changes.
         */
        if (StringUtil.isNullOrEmpty(reservation.getStatus())) {
            reservation.setStatus(this.getStatus());
        }
        reservation.setContact(this.getContact());
        reservation.setComments(this.getComments());
        reservation.setCost(this.getCost());
        reservation.setAccountId(this.getAccountId());
        reservation.setDepartmentId(this.getDepartmentId());
        reservation.setDivisionId(this.getDivisionId());
        reservation.setEmail(this.getEmail());
        reservation.setPhone(this.getPhone());

        reservation.setUniqueId(this.getUniqueId());
        reservation.setParentId(this.getParentId());
        reservation.setRecurringRule(this.getRecurringRule());
        reservation.setReservationType(this.getReservationType());

        reservation.setTimeZone(this.getTimeZone());
        reservation.setAttendees(this.getAttendees());

        return reservation;
    }

    /**
     * Gets the resource allocations.
     *
     * @return list of resource allocations
     *
     * @see com.archibus.reservation.domain.IReservation#getResourceAllocations()
     */
    @Override
    public final List<ResourceAllocation> getResourceAllocations() {
        if (this.resourceAllocations == null) {
            this.resourceAllocations = new ArrayList<ResourceAllocation>();
        }
        return this.resourceAllocations;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRecurrence(final Recurrence recurrence) {
        this.recurrence = recurrence;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Recurrence getRecurrence() {
        return this.recurrence;
    }

    /**
     * Sets the resource allocations.
     *
     * @param resourceAllocations the new resource allocations
     */
    public final void setResourceAllocations(final List<ResourceAllocation> resourceAllocations) {
        this.resourceAllocations = resourceAllocations;
    }

    /**
     * Set the building id to be used when no resource allocations are linked.
     *
     * @param buildingId the building id to set
     */
    public void setBackupBuildingId(final String buildingId) {
        this.backupBuildingId = buildingId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String determineBuildingId() {
        List<ResourceAllocation> allocations = ReservationUtils.getActiveResourceAllocations(this);
        if (allocations.isEmpty()) {
            // there are no active allocations, so check for inactive ones
            allocations = this.getResourceAllocations();
        }
        String building = null;
        if (allocations.isEmpty()) {
            building = this.backupBuildingId;
        } else {
            building = allocations.get(0).getBlId();
        }
        return building;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHtmlComments(final String htmlComments) {
        this.htmlComments = htmlComments;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @XmlTransient
    public String getHtmlComments() {
        return this.htmlComments;
    }

}
