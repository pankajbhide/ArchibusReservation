package com.archibus.app.reservation.domain;

import java.sql.Time;
import java.util.Calendar;

import javax.xml.bind.annotation.*;

/**
 * Domain class for Resource Allocation.
 *
 * Resources can be allocation to room reservations or resources can be reserved without a room.
 *
 * @author Bart Vanderschoot
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "ResourceAllocation")
public class ResourceAllocation extends AbstractAllocation {

    /** The id. */
    private Integer id;

    /** The quantity. */
    private Integer quantity;

    /** The resource id. */
    private String resourceId;

    /**
     * Instantiates a new resource allocation.
     */
    public ResourceAllocation() {
        super();
    }

    /**
     * Instantiates a new resource allocation.
     *
     * @param reservation the reservation
     */
    public ResourceAllocation(final IReservation reservation) {
        super();
        setReservation(reservation);
    }

    /**
     * Instantiates a new resource allocation.
     *
     * @param resource the resource
     * @param reservation the reservation
     * @param quantity the quantity
     */
    public ResourceAllocation(final Resource resource, final IReservation reservation,
            final Integer quantity) {
        super();
        setReservation(reservation);
        this.resourceId = resource.getResourceId();
        this.quantity = quantity;
    }

    /**
     * Copy this object's properties to the resource allocation provided as a parameter.
     *
     * @param allocation the resource allocation to modify according to this object's properties
     */
    @Override
    public void copyTo(final AbstractAllocation allocation) {
        if (allocation instanceof ResourceAllocation) {
            super.copyTo(allocation);
            final ResourceAllocation resourceAllocation = (ResourceAllocation) allocation;
            resourceAllocation.setResourceId(this.resourceId);
            resourceAllocation.setQuantity(this.quantity);
        } else {
            throw new IllegalArgumentException("Target object is not a resource allocation.");
        }
    }

    /**
     * Get the resource allocation identifier.
     *
     * @return the resource allocation identifier
     */
    @Override
    public final Integer getId() {
        return this.id;
    }

    /**
     * Gets the quantity of the resource allocation.
     *
     * @return the quantity of the resource allocation
     */
    public final Integer getQuantity() {
        return this.quantity;
    }

    /**
     * Gets the resource id of this resource allocation.
     *
     * @return the resource id
     */
    public final String getResourceId() {
        return this.resourceId;
    }

    /**
     * Sets the id of the resource allocation.
     *
     * @param id the new id
     */
    public final void setId(final Integer id) {
        this.id = id;
    }

    /**
     * Sets the allocated quantity of the resource.
     *
     * @param quantity the new quantity
     */
    public final void setQuantity(final Integer quantity) {
        this.quantity = quantity;
    }

    /**
     * Sets the resource id of the allocation.
     *
     * @param resourceId the resource id for this allocation
     */
    public final void setResourceId(final String resourceId) {
        this.resourceId = resourceId;
    }

    /**
     * {@inheritDoc} Only update the resource allocation time period if the current time period is
     * outside the main reservation time period.
     */
    @Override
    public final void setReservation(final IReservation reservation) {
        // set the id
        setReserveId(reservation.getReserveId());
        // copy from reservation
        setStartDate(reservation.getStartDate());
        setEndDate(reservation.getEndDate());

        // make sure the date of time values are set to 1899
        if (this.getStartTime() != null) {
            setStartTime(TimePeriod.clearDate(getStartTime()));
        }
        if (this.getEndTime() != null) {
            setEndTime(TimePeriod.clearDate(getEndTime()));
        }

        final Time reservationStartTime = TimePeriod.clearDate(reservation.getStartTime());
        final Time reservationEndTime = TimePeriod.clearDate(reservation.getEndTime());

        // Modify the allocation time so it is within the boundaries of the reservation time.
        if (this.getStartTime() == null || this.getStartTime().before(reservationStartTime)
                || this.getStartTime().compareTo(reservationEndTime) >= 0) {
            setStartTime(reservationStartTime);
        }
        if (this.getEndTime() == null || this.getEndTime().after(reservationEndTime)
                || this.getEndTime().compareTo(reservationStartTime) <= 0) {
            setEndTime(reservationEndTime);
        }
        setTimeZone(reservation.getTimeZone());
    }

    /**
     * Adjust the resource reservation time period so its setup and cleanup times don't extend
     * beyond the given room arrangement's setup and cleanup times. Consider the room as being
     * booked for the given time period.
     *
     * @param timePeriod the time period for which the room arrangement is booked
     * @param resource details of the resource
     * @param arrangement details of the room arrangement
     */
    public void adjustTimePeriod(final TimePeriod timePeriod, final Resource resource,
            final RoomArrangement arrangement) {
        this.adjustStartTime(timePeriod, resource, arrangement);
        this.adjustEndTime(timePeriod, resource, arrangement);
    }

    /**
     * Adjust the resource reservation end time.
     *
     * @param timePeriod the room arrangement time period
     * @param resource details of the resource
     * @param arrangement details of the room arrangement
     */
    private void adjustEndTime(final TimePeriod timePeriod, final Resource resource,
            final RoomArrangement arrangement) {
        int postBlockDelta = 0;
        if (resource.getPostBlock() != null) {
            postBlockDelta -= resource.getPostBlock();
        }
        if (arrangement.getPostBlock() != null) {
            postBlockDelta += arrangement.getPostBlock();
        }

        if (postBlockDelta < 0) {
            // compute the difference in end time
            final Time arrangementEnd = TimePeriod.clearDate(timePeriod.getEndTime());
            final Time resourceEnd = TimePeriod.clearDate(this.getEndTime());

            final int resourceEndDelta =
                    (int) ((arrangementEnd.getTime() - resourceEnd.getTime()) / TimePeriod.MINUTE_MILLISECONDS);
            postBlockDelta += resourceEndDelta;
        }
        if (postBlockDelta < 0) {
            final Calendar calendar = Calendar.getInstance();
            calendar.setTime(this.getEndTime());
            calendar.add(Calendar.MINUTE, postBlockDelta);
            this.setEndTime(new Time(calendar.getTimeInMillis()));
        }
    }

    /**
     * Adjust the resource reservation start time.
     *
     * @param timePeriod the room arrangement time period
     * @param resource details of the resource
     * @param arrangement details of the room arrangement
     */
    private void adjustStartTime(final TimePeriod timePeriod, final Resource resource,
            final RoomArrangement arrangement) {
        int preBlockDelta = 0;
        if (resource.getPreBlock() != null) {
            preBlockDelta += resource.getPreBlock();
        }
        if (arrangement.getPreBlock() != null) {
            preBlockDelta -= arrangement.getPreBlock();
        }

        if (preBlockDelta > 0) {
            // compute the difference in start time
            final Time arrangementStart = TimePeriod.clearDate(timePeriod.getStartTime());
            final Time resourceStart = TimePeriod.clearDate(this.getStartTime());

            final int resourceStartDelta =
                    (int) ((resourceStart.getTime() - arrangementStart.getTime()) / TimePeriod.MINUTE_MILLISECONDS);
            preBlockDelta -= resourceStartDelta;
        }
        if (preBlockDelta > 0) {
            final Calendar calendar = Calendar.getInstance();
            calendar.setTime(this.getStartTime());
            calendar.add(Calendar.MINUTE, preBlockDelta);
            this.setStartTime(new Time(calendar.getTimeInMillis()));
        }
    }
}
