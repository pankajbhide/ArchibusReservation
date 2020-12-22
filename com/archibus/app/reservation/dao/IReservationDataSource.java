package com.archibus.app.reservation.dao;

import java.util.*;

import com.archibus.app.reservation.domain.*;
import com.archibus.core.dao.IDao;
import com.archibus.datasource.data.DataSetList;

/**
 * The Interface IReservationDataSource.
 *
 * @param <T> the generic type
 */
public interface IReservationDataSource<T extends AbstractReservation> extends IDao<T> {

    /**
     * Add a list of resource to the given room reservation.
     *
     * @param reservation the reservation
     * @param resourceList the list of resource to add
     */
    void addResourceList(final T reservation, final DataSetList resourceList);

    /**
     * Check if the current user is authorized to cancel the reservation. Check the configuration
     * restrictions for cancelling.
     *
     * @param reservation reservation object
     * @throws ReservationException reservation exception
     */
    void canBeCancelledByCurrentUser(final T reservation) throws ReservationException;

    /**
     * Get room reservations by parent id.
     *
     * @param parentId the parent id
     * @param startDate the start date
     * @param endDate the end date (can be null to get all reservations starting from the startDate)
     * @param includeConflicted whether to include conflicted reservations in the result
     * @return list of room reservations with this parent id
     */
    List<T> getByParentId(final Integer parentId, final Date startDate, final Date endDate,
            final boolean includeConflicted);

    /**
     * Get the active reservation with the given ID.
     *
     * @param reserveId reservation id
     * @return reservation object, or null if an active reservation doesn't exist
     */
    T getActiveReservation(final Object reserveId);

    /**
     * Try to cancel the reservation. If not allowed, an exception is thrown.
     *
     * All costs are reset when cancelling.
     *
     * @param reservation reservation object
     * @param comments the cancellation comments
     * @throws ReservationException reservation exception is thrown when the reservation cannot be
     *             cancelled
     */
    void cancel(final T reservation, final String comments) throws ReservationException;

    /**
     * Mark the given reservation as recurring in the database without updating any other fields.
     *
     * @param reservation the reservation to update
     * @param parentId the parent id to set
     * @param recurringRule the recurring rule to set
     * @param occurrenceIndex the occurrence index to set
     */
    void markRecurring(final T reservation, final Integer parentId, final String recurringRule,
            final int occurrenceIndex);
}
