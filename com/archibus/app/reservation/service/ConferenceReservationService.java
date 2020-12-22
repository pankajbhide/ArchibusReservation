package com.archibus.app.reservation.service;

import java.util.*;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.Recurrence;
import com.archibus.app.reservation.service.helpers.RecurrenceHelper;

/**
 * Provides functionality to create and update conference call reservations without updating the
 * connected calendar.
 * <p>
 * Used by ReservationRemoteServiceImpl to handle conference call requests from the Outlook Plugin
 * and by ConferenceCallReservationService for requests from the Web Central Reservations
 * Application. Managed by Spring, has prototype scope. Configured in reservation-context.xml.
 *
 * @author Yorik Gerlo
 * @since 23.1
 *
 */
public class ConferenceReservationService extends ConferenceAwareReservationService
        implements IConferenceReservationService {

    /** The cancel service. */
    private CancelReservationService cancelReservationService;

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveCompiledConferenceCallReservations(
            final List<RoomReservation> confCallReservations, final Recurrence recurrence) {
        /*
         * Create or edit non-recurring or single occurrence conference call; or create or edit
         * recurring conference call: process by location.
         */
        for (final RoomReservation confCallReservation : confCallReservations) {
            List<RoomReservation> createdReservations = null;

            if (recurrence == null) {
                // Room and Resource availability is verified by RoomReservationDataSource.
                this.saveSingleReservation(confCallReservation);

                /*
                 * If we are editing a single occurrence and adding a room, set the parent id to
                 * match the reservation id.
                 */
                if (Constants.TYPE_RECURRING.equals(confCallReservation.getReservationType())
                        && confCallReservation.getParentId() == null) {
                    this.reservationDataSource.markRecurring(confCallReservation,
                        confCallReservation.getReserveId(), confCallReservation.getRecurringRule(),
                        confCallReservation.getOccurrenceIndex());
                }

                createdReservations = new ArrayList<RoomReservation>();
                createdReservations.add(confCallReservation);
            } else {
                if (confCallReservation.getParentId() == null) {
                    confCallReservation.setParentId(confCallReservation.getReserveId());
                }
                // Room and Resource availability is verified by RoomReservationDataSource.
                // We want to update a specific series of reservations with matching parent id.
                createdReservations = this.saveRecurringReservation(confCallReservation, recurrence,
                    confCallReservation.getParentId());
            }
            // store the generated reservation instances in the reservation
            confCallReservation.setCreatedReservations(createdReservations);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SavedConferenceCall saveConferenceCall(final List<RoomReservation> reservations,
            final Recurrence recurrence, final boolean disconnectOnError) {
        final RoomReservation primaryReservation = reservations.get(0);

        Integer occurrenceIndex = null;
        if (recurrence == null && primaryReservation.getRecurrence() != null) {
            // We're saving reservations on a specific date of a recurrence series.
            // Find all relevant reservations with the same occurrence index.
            if (primaryReservation.getOccurrenceIndex() == 0) {
                primaryReservation.setOccurrenceIndex(
                    RecurrenceHelper.calculateOccurrenceIndex(primaryReservation));
            }
            occurrenceIndex = primaryReservation.getOccurrenceIndex();
            final String recurringRule = primaryReservation.getRecurrence().toString();

            // Set the recurring rule and reservation type for all reservations.
            for (final RoomReservation reservation : reservations) {
                reservation.setRecurringRule(recurringRule);
                reservation.setOccurrenceIndex(occurrenceIndex);
            }
        }
        final List<RoomReservation> reservationsToCancel =
                this.findReservationsToCancel(reservations, primaryReservation, occurrenceIndex);

        final List<RoomReservation> failures = this.cancelReservationService
            .cancelRoomReservations(reservationsToCancel, disconnectOnError);

        final SavedConferenceCall result = new SavedConferenceCall();
        if (failures == null || failures.isEmpty()) {
            this.saveCompiledConferenceCallReservations(reservations, recurrence);
            this.reservationDataSource.persistCommonIdentifiers(primaryReservation, reservations,
                false);

            // retrieve all created / updated reservations ordered by date
            final int numberOfOccurrences = primaryReservation.getCreatedReservations().size();
            final List<RoomReservation> createdReservations =
                    new ArrayList<RoomReservation>(numberOfOccurrences * reservations.size());
            for (int i = 0; i < numberOfOccurrences; ++i) {
                for (final RoomReservation confCallReservation : reservations) {
                    createdReservations.add(confCallReservation.getCreatedReservations().get(i));
                }
            }

            result.setSavedReservations(createdReservations);
        } else {
            result.setReservationsToDisconnect(failures);
        }
        return result;
    }

    /**
     * Setter for the service used to cancel reservations.
     *
     * @param cancelReservationService the cancelReservationService to set
     */
    public void setCancelReservationService(
            final CancelReservationService cancelReservationService) {
        this.cancelReservationService = cancelReservationService;
    }

    /**
     * Find existing reservations not in the new selection of rooms for the conference call.
     *
     * @param confCallReservations the new selection of conference call reservations
     * @param primaryReservation the primary reservation
     * @param occurrenceIndex when editing a specific occurrence of a recurring reservation
     * @return list of reservations to be cancelled
     */
    private List<RoomReservation> findReservationsToCancel(
            final List<RoomReservation> confCallReservations,
            final RoomReservation primaryReservation, final Integer occurrenceIndex) {

        final Map<Integer, RoomAllocation> allocationsById = new HashMap<Integer, RoomAllocation>();
        for (final RoomReservation confCallReservation : confCallReservations) {
            final RoomAllocation allocation = confCallReservation.getRoomAllocations().get(0);
            // ignore new allocations
            if (allocation.getReserveId() != null) {
                if (confCallReservation.getParentId() == null) {
                    allocationsById.put(allocation.getReserveId(), allocation);
                } else {
                    allocationsById.put(confCallReservation.getParentId(), allocation);
                }
            }
        }

        final List<RoomReservation> existingConfCallReservations = this.reservationDataSource
            .getByUniqueId(primaryReservation.getUniqueId(), null, occurrenceIndex);

        final List<RoomReservation> reservationsToCancel = new ArrayList<RoomReservation>();
        if (existingConfCallReservations != null) {
            for (final RoomReservation existingConfCallReservation : existingConfCallReservations) {
                Integer id = existingConfCallReservation.getParentId();
                if (id == null) {
                    id = existingConfCallReservation.getReserveId();
                }
                final RoomAllocation allocation = allocationsById.get(id);
                if (allocation == null) {
                    reservationsToCancel.add(existingConfCallReservation);
                }
            }
        }
        return reservationsToCancel;
    }

}
