package com.archibus.app.reservation.dao;

import java.util.List;

import com.archibus.app.reservation.domain.*;
import com.archibus.datasource.data.DataRecord;

/**
 * The Interface IRoomReservationDataSource.
 */
public interface IRoomReservationDataSource extends IReservationDataSource<RoomReservation> {

    /**
     * Get active and conflicted room reservations by unique id.
     *
     * @param uniqueId the unique id
     * @param conferenceId the conference id (may be null)
     * @param occurrenceIndex the occurrence index (may be null)
     * @return list of room reservations
     */
    List<RoomReservation> getByUniqueId(final String uniqueId, final Integer conferenceId,
            final Integer occurrenceIndex);

    /**
     * Clear the unique ID coming from Exchange.
     *
     * The reservation is de-coupled from the appointment in MS Exchange.
     *
     * @param reservation reservation object
     * @return reservation
     *
     * @throws ReservationException reservation exception is thrown when the reservation cannot be
     *             found
     */
    RoomReservation clearUniqueId(final RoomReservation reservation) throws ReservationException;

    /**
     * Check recurring date modified. If roomReservation is a recurring reservation, compare it's
     * start date to the date stored in the database. If it's different, set the recurring date
     * modified flag in the parameter. No changes are done in the database.
     *
     * @param roomReservation the room reservation
     */
    void checkRecurringDateModified(final RoomReservation roomReservation);

    /**
     * Convert data records to RoomReservation domain object.
     *
     * @param reservationRecord the reservation record
     * @param roomAllocationRecord the room allocation record
     * @param resourceAllocationRecords the resource allocation records
     * @return room reservation object
     */
    RoomReservation convertRecordToObject(final DataRecord reservationRecord,
            final DataRecord roomAllocationRecord, final List<DataRecord> resourceAllocationRecords);

}
