package com.archibus.app.reservation.service.actions;

import java.sql.Time;
import java.util.*;

import com.archibus.app.reservation.dao.IResourceReservationDataSource;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.AbstractIntervalPattern;
import com.archibus.app.reservation.service.helpers.ReservationServiceHelper;

/**
 * Provides a method for saving an occurrence of a recurring reservation.
 * <p>
 *
 * Used by ResourceReservationService to save all occurrences of a recurring reservation via the
 * Recurrence Pattern definition.
 *
 * @author Yorik Gerlo
 * @since 21.2
 *
 */
public class SaveRecurringResourceReservationOccurrenceAction implements
        AbstractIntervalPattern.OccurrenceAction {
    
    /**
     * The duration in minutes of the reservations.
     */
    private final int durationInMinutes;

    /** The saved reservations. */
    private final List<ResourceReservation> savedReservations;

    /** The resource reservation data source. */
    private final IResourceReservationDataSource resourceReservationDataSource;

    /**
     * The start time of the reservations.
     */
    private final Time startTime;
    
    /**
     * The reservation object to modify for each occurrence.
     */
    private final ResourceReservation reservation;

    /**
     * Instantiates a new save recurring resource reservation occurrence action.
     *
     * @param savedReservations the saved reservations
     * @param resourceReservationDataSource the resource reservation data source
     * @param firstReservation the first reservation
     */
    public SaveRecurringResourceReservationOccurrenceAction(
            final List<ResourceReservation> savedReservations,
            final IResourceReservationDataSource resourceReservationDataSource,
            final ResourceReservation firstReservation) {
        this.resourceReservationDataSource = resourceReservationDataSource;
        this.savedReservations = savedReservations;
        this.reservation = firstReservation;
        
        this.startTime = TimePeriod.clearDate(firstReservation.getStartTime());
        this.durationInMinutes =
                (int) (firstReservation.getEndDateTime().getTime() - firstReservation
                    .getStartDateTime().getTime()) / TimePeriod.MINUTE_MILLISECONDS;
    }
    
    /**
     * {@inheritDoc}
     */
    public boolean handleOccurrence(final Date date) throws ReservationException {
        final TimePeriod timePeriod =
                new TimePeriod(date, this.startTime, this.durationInMinutes,
                    this.reservation.getTimeZone());

        // only to be used for new reservations
        final ResourceReservation recurringReservation = new ResourceReservation();

        this.reservation.copyTo(recurringReservation, true);
        recurringReservation.setTimePeriod(timePeriod);
        recurringReservation.setOccurrenceIndex(this.savedReservations.size() + 1);

        ReservationServiceHelper.copyResourceAllocations(this.reservation, recurringReservation);
        this.resourceReservationDataSource.checkResourcesAvailable(recurringReservation);
        // save all
        this.resourceReservationDataSource.save(recurringReservation);
        this.savedReservations.add(recurringReservation);

        return true;
    }
    
}
