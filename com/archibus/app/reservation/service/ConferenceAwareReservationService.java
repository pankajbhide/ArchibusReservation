package com.archibus.app.reservation.service;

import java.sql.Time;
import java.util.*;

import com.archibus.app.reservation.dao.IConferenceCallReservationDataSource;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.*;
import com.archibus.app.reservation.service.actions.VerifyRecurrencePatternOccurrenceAction;
import com.archibus.app.reservation.service.helpers.RecurrenceHelper;
import com.archibus.app.reservation.util.TimeZoneConverter;
import com.archibus.utility.StringUtil;

/**
 * Conference aware Reservation Service class.
 *
 * The service class is a business logic layer used for different front-end handlers. Both event
 * handlers and remote services can use the service class.
 *
 * This subclass of ReservationService provides methods to front-end handlers allowing them to
 * handle conference call reservations transparently, i.e. updating reservations in the same
 * conference call along with the provided room reservation.
 *
 * @author Yorik Gerlo
 *
 */
public class ConferenceAwareReservationService extends ReservationService
        implements IConferenceAwareReservationService {

    /** The room reservation data source. */
    protected IConferenceCallReservationDataSource reservationDataSource;

    /** {@inheritDoc} */
    @Override
    public RoomReservation getActiveReservation(final Integer reserveId, final String timeZone) {
        RoomReservation roomReservation = super.getActiveReservation(reserveId, timeZone);
        if (roomReservation == null) {
            final List<RoomReservation> confCallReservations =
                    this.reservationDataSource.getByConferenceId(reserveId, true);
            // loop onto the first reservation in the list (if not empty)
            for (final RoomReservation confCallReservation : confCallReservations) {
                roomReservation = confCallReservation;
                if (StringUtil.notNullOrEmpty(timeZone)) {
                    TimeZoneConverter.convertToTimeZone(roomReservation, timeZone);
                }
                break;
            }
        }

        return roomReservation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<RoomReservation> saveFullRecurringReservation(final RoomReservation reservation,
            final Recurrence recurrence) throws ReservationException {
        /*
         * Here we don't want to update reservations with a specific parent id. We need the primary
         * reservation on each occurrence regardless of parent id. Hence pass null as the parent id.
         */
        final List<RoomReservation> savedReservations =
                super.saveRecurringReservation(reservation, recurrence, null);

        /*
         * If there are other reservations linked to the created reservations, update them as in
         * saveFullReservation. Also set the conference id in the main reservation if it's not set.
         */
        for (final RoomReservation savedReservation : savedReservations) {
            final List<RoomReservation> linkedReservations =
                    savedReservation.getCreatedReservations();
            if (linkedReservations != null) {
                this.updateLinkedReservations(savedReservation, linkedReservations);
                this.copyConferenceIdToPrimaryReservation(savedReservation, linkedReservations);
            }
        }

        return savedReservations;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveFullReservation(final IReservation reservation) {
        if (reservation.getConferenceId() != null) {
            final List<RoomReservation> confCallReservations = this.reservationDataSource
                .getByConferenceId(reservation.getConferenceId(), true);
            updateLinkedReservations(reservation, confCallReservations);
        }
        super.saveReservation(reservation);
    }

    /**
     * Update linked reservations to match time period, name, comments and attendees. If the primary
     * reservation is included in the list of linked reservations then it will be ignored.
     *
     * @param reservation the primary reservation
     * @param linkedReservations the reservations to change to match the primary
     */
    private void updateLinkedReservations(final IReservation reservation,
            final List<RoomReservation> linkedReservations) {

        /*
         * Ensure the primary reservation specifies a time zone, since it's local time zone could be
         * different from the local time zone of the linked reservations.
         */
        if (StringUtil.isNullOrEmpty(reservation.getTimeZone())) {
            reservation.setTimeZone(
                TimeZoneConverter.getTimeZoneIdForBuilding(reservation.determineBuildingId()));
        }

        for (final RoomReservation linkedReservation : linkedReservations) {
            // skip the one given as parameter, it's saved separately
            if (!linkedReservation.getReserveId().equals(reservation.getReserveId())) {
                linkedReservation.setTimePeriod(reservation.getTimePeriod());
                linkedReservation.setReservationName(reservation.getReservationName());
                linkedReservation.setComments(reservation.getComments());
                linkedReservation.setAttendees(reservation.getAttendees());
                this.saveReservation(linkedReservation);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean verifyRecurrencePattern(final String uniqueId, final Recurrence pattern,
            final Time startTime, final Time endTime, final String timeZone)
            throws ReservationException {

        RecurrenceHelper.moveToNextOccurrence(pattern, timeZone);
        final List<RoomReservation> reservations =
                this.reservationDataSource.getDistinctReservationsByUniqueId(uniqueId);

        // Begin by assuming the reservations match the pattern.
        boolean reservationsMatchThePattern = true;

        if (pattern.getNumberOfOccurrences() == null
                || pattern.getNumberOfOccurrences() < reservations.size()) {
            // The number of reservations doesn't equal the the number of occurrences.
            reservationsMatchThePattern = false;
        } else if (pattern instanceof AbstractIntervalPattern) {
            final Map<Date, RoomReservation> reservationMap =
                    TimeZoneConverter.toRequestorTimeZone(reservations, timeZone);

            final AbstractIntervalPattern intervalPattern = (AbstractIntervalPattern) pattern;

            final RoomReservation reservation = reservationMap.get(intervalPattern.getStartDate());
            if (reservation == null
                    || !reservation.getStartTime().toString().equals(startTime.toString())
                    || !reservation.getEndTime().toString().equals(endTime.toString())) {
                reservationsMatchThePattern = false;
            } else {
                final VerifyRecurrencePatternOccurrenceAction action =
                        new VerifyRecurrencePatternOccurrenceAction(startTime, endTime,
                            reservationMap);
                intervalPattern.loopThroughRepeats(action);
                reservationsMatchThePattern = action.getFirstDateWithoutReservation() == null;
            }
        }

        return reservationsMatchThePattern;
    }

    /**
     * Setter for the conference call Reservation DataSource.
     *
     * @param reservationDataSource conference call reservation Data Source to set
     */
    @Override
    public void setReservationDataSource(
            final IConferenceCallReservationDataSource reservationDataSource) {
        super.setReservationDataSource(reservationDataSource);
        this.reservationDataSource = reservationDataSource;
    }

    /**
     * Copy the conference id from the linked reservations to the primary reservation if the primary
     * currently doesn't contain a conference id.
     *
     * @param reservation the reservation to set the conference id for
     * @param linkedReservations the list of linked reservations
     */
    private void copyConferenceIdToPrimaryReservation(final RoomReservation reservation,
            final List<RoomReservation> linkedReservations) {
        if (reservation.getConferenceId() == null || reservation.getConferenceId() == 0) {
            Integer conferenceId = null;
            for (final RoomReservation linkedReservation : linkedReservations) {
                conferenceId = linkedReservation.getConferenceId();
                if (conferenceId != null && conferenceId > 0) {
                    break;
                }
            }

            if (conferenceId != null && conferenceId > 0) {
                this.reservationDataSource.persistConferenceId(reservation, conferenceId);
            }
        }
    }

}
