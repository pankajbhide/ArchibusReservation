package com.archibus.app.reservation.dao.datasource;

import java.util.*;

import org.springframework.util.StringUtils;

import com.archibus.app.reservation.dao.IConferenceCallReservationDataSource;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.util.ReservationUtils;
import com.archibus.datasource.*;
import com.archibus.datasource.data.DataRecord;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.model.view.datasource.ClauseDef.Operation;
import com.archibus.model.view.datasource.ParsedRestrictionDef;
import com.archibus.service.remoting.AdminService;

/**
 * Conference Call Room Reservations data source. Contains specific methods for handling conference
 * call reservations.
 *
 * @author Yorik Gerlo
 * @since 21.3
 */
public class ConferenceCallReservationDataSource extends RoomReservationDataSource
        implements IConferenceCallReservationDataSource {

    /**
     * Get the existing occurrences for a recurring conference call reservation starting from the
     * given occurrence, without conflicted occurrences.
     *
     * @param roomReservation the first occurrence being edited
     * @return the existing occurrences in the conference call
     */
    @Override
    public List<RoomReservation> getConferenceCallOccurrences(
            final RoomReservation roomReservation) {
        final Integer conferenceParentId = this.getParentId(roomReservation.getConferenceId());
        final Integer[] conferenceIds = this.getRecurringConferenceIds(conferenceParentId,
            roomReservation.getConferenceId());
        final List<RoomReservation> existingOccurrences =
                new ArrayList<RoomReservation>(conferenceIds.length);
        for (final Integer conferenceId : conferenceIds) {
            final List<RoomReservation> confCallReservations =
                    this.getByConferenceId(conferenceId, false);
            if (confCallReservations.isEmpty()) {
                // Skip this occurrence if all reservations are conflicted.
                continue;
            }
            final RoomReservation primaryReservation = confCallReservations.get(0);
            final Integer[] reservationIds = new Integer[confCallReservations.size()];
            for (int i = 0; i < confCallReservations.size(); ++i) {
                reservationIds[i] = confCallReservations.get(i).getReserveId();
            }
            primaryReservation.setReservationIdsInConference(reservationIds);
            existingOccurrences.add(primaryReservation);
        }
        return existingOccurrences;
    }

    /**
     * Get all conference id's in the recurrence series.
     *
     * @param conferenceParentId parent id of the original primary location in the series, i.e. for
     *            the reservations where res_id = res_conference
     * @param conferenceId the conference id of one of the occurrences
     * @return array of conference id's
     */
    public Integer[] getRecurringConferenceIds(final Integer conferenceParentId,
            final Integer conferenceId) {
        final DataSource dataSource = DataSourceFactory.createDataSourceForFields(
            Constants.RESERVE_TABLE_NAME, new String[] { Constants.RES_ID, Constants.RES_PARENT,
                    Constants.DATE_START_FIELD_NAME, Constants.RES_CONFERENCE });
        /*
         * Now get all reserve records in that record's recurrence series. This yields all
         * conference id's in the recurring conference call. We must start from the original primary
         * reserve record because only that one surely has all occurrences in the conference call.
         */
        dataSource.addRestriction(Restrictions.eq(Constants.RESERVE_TABLE_NAME,
            Constants.RES_PARENT, conferenceParentId));
        if (conferenceId != null) {
            dataSource.addRestriction(Restrictions.gte(Constants.RESERVE_TABLE_NAME,
                Constants.RES_CONFERENCE, conferenceId));
        }
        dataSource.setMaxRecords(0);
        final List<DataRecord> records = dataSource.getRecords();
        // build a list of conference id's
        final Integer[] conferenceIds = new Integer[records.size()];
        for (int i = 0; i < records.size(); ++i) {
            conferenceIds[i] = records.get(i)
                .getInt(Constants.RESERVE_TABLE_NAME + Constants.DOT + Constants.RES_CONFERENCE);
        }
        return conferenceIds;
    }

    /**
     * Get the parent id for the reservation with the given id.
     *
     * @param reservationId the reservation id
     * @return the parent id
     */
    public Integer getParentId(final Integer reservationId) {
        final DataSource dataSource = DataSourceFactory.createDataSourceForFields(
            Constants.RESERVE_TABLE_NAME, new String[] { Constants.RES_ID, Constants.RES_PARENT,
                    Constants.DATE_START_FIELD_NAME, Constants.RES_CONFERENCE });
        dataSource.addRestriction(
            Restrictions.eq(Constants.RESERVE_TABLE_NAME, Constants.RES_ID, reservationId));
        final DataRecord primaryRecord = dataSource.getRecord();
        return primaryRecord
            .getInt(Constants.RESERVE_TABLE_NAME + Constants.DOT + Constants.RES_PARENT);
    }

    /**
     * Get room reservations representing the conference series being edited. Only start date and
     * conference id are returned.
     *
     * @param conferenceId the conference id of the current reservation
     * @param startDate the start date of the current reservation
     * @param sortOrder data source sort order on start date
     * @param includeConflicted whether to include conflicted occurrences in the result
     * @return room reservations in the recurring conference call, one per date, only conference id
     *         and start date specified
     */
    public List<RoomReservation> getConferenceSeries(final Integer conferenceId,
            final Date startDate, final String sortOrder, final boolean includeConflicted) {
        final Integer[] conferenceIds =
                this.getRecurringConferenceIds(getParentId(conferenceId), null);

        // Now get the current dates for each conference id.
        final DataSource dataSource =
                DataSourceFactory.createDataSourceForFields(Constants.RESERVE_TABLE_NAME,
                    new String[] { Constants.DATE_START_FIELD_NAME, Constants.RES_CONFERENCE });
        // Set distinct to return only one record per date.
        dataSource.setDistinct(true);
        dataSource.setMaxRecords(0);
        /*
         * Set restrictions to get all dates starting from the given startDate with active
         * reservations with one of the conference id's.
         */
        final String allowedStatuses;
        if (includeConflicted) {
            allowedStatuses = Constants.STATUS_AWAITING_APP_OR_CONFIRMED_OR_CONFLICT;
        } else {
            allowedStatuses = Constants.STATUS_AWAITING_APP_OR_CONFIRMED;
        }
        dataSource.addRestriction(
            Restrictions.in(Constants.RESERVE_TABLE_NAME, Constants.STATUS, allowedStatuses));
        if (startDate != null) {
            dataSource.addRestriction(Restrictions.gte(Constants.RESERVE_TABLE_NAME,
                Constants.DATE_START_FIELD_NAME, startDate));
        }
        dataSource.addRestriction(Restrictions.in(Constants.RESERVE_TABLE_NAME,
            Constants.RES_CONFERENCE, StringUtils.arrayToCommaDelimitedString(conferenceIds)));
        // sort the last date first
        dataSource.addSort(Constants.RESERVE_TABLE_NAME, Constants.DATE_START_FIELD_NAME,
            sortOrder);

        final List<DataRecord> records = dataSource.getRecords();
        // convert the records to reservation objects with only startDate and conferenceId
        final List<RoomReservation> conferenceSeries = new ArrayList<RoomReservation>();
        for (final DataRecord record : records) {
            conferenceSeries.add(this.convertRecordToObject(record));
        }
        return conferenceSeries;
    }

    /**
     * For a recurring conference call, get the original reservations by conference id.
     *
     * @param firstConferenceId the conference id of the first occurrence
     * @param includeConflicted whether conflicted reservations should be included in the result
     * @return map of original reservations by conference id including conflicted reservations
     */
    public SortedMap<Integer, List<RoomReservation>> getAllReservationsInConferenceSeries(
            final Integer firstConferenceId, final boolean includeConflicted) {
        /*
         * Return the reservations in local time, time zone conversion from local time is applied
         * automatically in the CalendarService.
         */
        final SortedMap<Integer, List<RoomReservation>> originalsByConferenceId =
                new TreeMap<Integer, List<RoomReservation>>();
        // including conflicted occurrences
        final Integer conferenceParentId = this.getParentId(firstConferenceId);
        final Integer[] conferenceIds =
                this.getRecurringConferenceIds(conferenceParentId, firstConferenceId);
        for (final Integer conferenceId : conferenceIds) {
            final List<RoomReservation> confCallReservations =
                    this.getByConferenceId(conferenceId, includeConflicted);
            originalsByConferenceId.put(conferenceId, confCallReservations);
        }
        return originalsByConferenceId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void persistCommonIdentifiers(final RoomReservation primaryReservation,
            final List<RoomReservation> confCallReservations, final boolean updateParentIds) {
        /*
         * Save the reservation(s) again to persist the appointment unique id, conference id,
         * occurrence index, occurrence date modified and comments. Occurrence index and occurrence
         * date modified should be OK; conference id only needs setting when creating a new
         * conference call; unique id can change if the meeting is not found on the connected
         * Exchange; comments can change if conflicts were generated.
         */
        /*
         * KB 3040346 also check whether the primary occurrence still has a parent id. If not then
         * remove the parent id and recurring rule from all reservations for the same occurrence.
         */
        final List<RoomReservation> primaryReservations =
                primaryReservation.getCreatedReservations();

        // track new parent id's if required
        final Map<Integer, Integer> newParentIds = new HashMap<Integer, Integer>();

        for (int i = 0; i < primaryReservations.size(); ++i) {
            final RoomReservation primaryOccurrence = primaryReservations.get(i);
            final Integer conferenceId = primaryOccurrence.getConferenceId() == null
                    ? primaryOccurrence.getReserveId() : primaryOccurrence.getConferenceId();
            // set the conference id and unique id in each conference call reservation
            // occurrence
            for (final RoomReservation confCallReservation : confCallReservations) {
                final RoomReservation confCallOccurrence =
                        confCallReservation.getCreatedReservations().get(i);
                final RoomReservation storedReservation =
                        this.get(confCallOccurrence.getReserveId());
                storedReservation.setConferenceId(conferenceId);
                storedReservation.setComments(primaryReservation.getComments());
                confCallOccurrence.setConferenceId(conferenceId);

                if ((primaryOccurrence.getParentId() == null
                        || primaryOccurrence.getParentId() == 0)
                        && storedReservation.getParentId() != null) {
                    // when removing from the series, take the new unique id specific to the
                    // occurrence
                    storedReservation.setUniqueId(primaryOccurrence.getUniqueId());
                    ReservationUtils.removeRecurrence(storedReservation);
                    // pass the old bean as well to remove the parent id
                    this.update(storedReservation, this.get(storedReservation.getReserveId()));
                } else {
                    // when remaining in the recurrence, take the unique id from the primary
                    // reservation
                    storedReservation.setUniqueId(primaryReservation.getUniqueId());
                    storedReservation.setOccurrenceIndex(primaryOccurrence.getOccurrenceIndex());
                    storedReservation
                        .setRecurringDateModified(primaryOccurrence.getRecurringDateModified());
                    this.checkUpdateParentId(storedReservation, newParentIds, updateParentIds);
                    this.update(storedReservation);
                }
            }
        }
    }

    /**
     * Persist common identifiers for the given conference call reservation occurrence.
     *
     * @param primaryReservation the primary reservation of this updated occurrence
     * @param confCallReservations all modified reservations for this occurrence
     * @param newParentIds map for new parent id's
     * @param updateParentIds indicates whether parent ids need to be updated
     */
    public void updateCommonIdentifiers(final RoomReservation primaryReservation,
            final List<RoomReservation> confCallReservations,
            final Map<Integer, Integer> newParentIds, final boolean updateParentIds) {

        for (final RoomReservation confCallReservation : confCallReservations) {
            /*
             * Originally there was no need to retrieve any reservation from db again, since all
             * were retrieved after saving the changes. However, since we need to detect whether the
             * parent id was removed and the primaryOccurrence object can be the same as the
             * confCallReservation object, we need to retrieve the unmodified reservation from the
             * database to check the parent id there.
             */
            confCallReservation.setUniqueId(primaryReservation.getUniqueId());

            final RoomReservation storedReservation = this.get(confCallReservation.getReserveId());
            if ((primaryReservation.getParentId() == null || primaryReservation.getParentId() == 0)
                    && storedReservation.getParentId() != null) {
                ReservationUtils.removeRecurrence(confCallReservation);
                // pass the old bean as well to remove the parent id
                this.update(confCallReservation, storedReservation);
            } else {
                // the reservation was and remains recurring
                // update the occurrence index
                confCallReservation.setOccurrenceIndex(primaryReservation.getOccurrenceIndex());
                this.checkUpdateParentId(confCallReservation, newParentIds, updateParentIds);

                this.update(confCallReservation);
            }
        }
    }

    /**
     * Update the reservation's parent id if required.
     *
     * @param confCallReservation the conference call reservation
     * @param newParentIds map of new parent id's
     * @param updateParentIds true to indicate parent id's must be updated
     */
    private void checkUpdateParentId(final RoomReservation confCallReservation,
            final Map<Integer, Integer> newParentIds, final boolean updateParentIds) {
        if (updateParentIds && confCallReservation.getParentId() != null) {
            // also update the parent id based on parent id map
            Integer newParentId = newParentIds.get(confCallReservation.getParentId());
            if (newParentId == null) {
                newParentId = confCallReservation.getReserveId();
                newParentIds.put(confCallReservation.getParentId(), newParentId);
            }
            confCallReservation.setParentId(newParentId);
        }
    }

    /**
     * Persist common identifiers for the given set of conference call reservations. Each occurrence
     * can be linked to a different number of reservations.
     *
     * @param primaryReservations the primary reservation of each updated occurrence
     * @param allModifiedReservations all modified reservations for each occurrence
     * @param updateParentIds whether the parent IDs must be updated
     */
    public void updateCommonIdentifiers(final List<RoomReservation> primaryReservations,
            final List<List<RoomReservation>> allModifiedReservations,
            final boolean updateParentIds) {
        final RoomReservation primaryReservation = primaryReservations.get(0);

        // track new parent id's if required
        final Map<Integer, Integer> newParentIds = new HashMap<Integer, Integer>();
        for (int i = 0; i < allModifiedReservations.size(); ++i) {
            final RoomReservation primaryOccurrence = primaryReservations.get(i);
            /*
             * Initially the unique id is only updated in the first primary reservation, so copy it
             * to this occurrence, unless this occurrence was explicitly removed from the recurrence
             * series. If that occurred then don't overwrite the new unique id for this occurrence.
             */
            if (primaryOccurrence.getParentId() != null && primaryOccurrence.getParentId() > 0) {
                primaryOccurrence.setUniqueId(primaryReservation.getUniqueId());
            }

            this.updateCommonIdentifiers(primaryOccurrence, allModifiedReservations.get(i),
                newParentIds, updateParentIds);
        }
    }

    /**
     * Verify whether the current user can cancel the conference call reservation with the given id.
     *
     * @param conferenceId conference call identifier
     * @param adminService localization service
     * @throws ReservationException if the current user cannot cancel the conference call
     */
    public void canCancelConferenceCall(final Integer conferenceId, final AdminService adminService)
            throws ReservationException {
        final List<RoomReservation> reservations = this.getByConferenceId(conferenceId, false);
        for (final RoomReservation reservation : reservations) {
            final RoomAllocation allocation = reservation.getRoomAllocations().get(0);
            if (this.roomAllocationDataSource.get(allocation.getId()) == null) {
                // @translatable
                throw new ReservationException(
                    "Reservation {0} cannot be cancelled due to your VPA settings.",
                    ConferenceCallReservationDataSource.class, adminService,
                    reservation.getReserveId());
            }
            // this also throws an exception if the reservation cannot be cancelled
            this.canBeCancelledByCurrentUser(reservation);
        }
    }

    /**
     * Extract a list of the current primary reservations on each occurrence.
     *
     * @param originalReservationsByConferenceId sorted map of reservations per conference id, i.e.
     *            per occurrence (note it can contain conflicted reservations or empty lists)
     * @return list of reservations primary reservations in occurrence order
     */
    public List<RoomReservation> extractPrimaryReservations(
            final SortedMap<Integer, List<RoomReservation>> originalReservationsByConferenceId) {
        final List<RoomReservation> primaryReservations = new ArrayList<RoomReservation>();
        for (final List<RoomReservation> reservationsInOccurrence : originalReservationsByConferenceId
            .values()) {
            // if the list is empty then this conference id / occurrence is skipped
            for (final RoomReservation reservationInOccurrence : reservationsInOccurrence) {
                // only consider non-conflicted reservations
                if (!reservationInOccurrence.getRoomAllocations().isEmpty()) {
                    primaryReservations.add(reservationInOccurrence);
                    break;
                }
            }
        }
        return primaryReservations;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<RoomReservation> getByConferenceId(final Integer conferenceId,
            final boolean includeConflicted) {
        List<RoomReservation> result = null;

        if (conferenceId != null) {
            final List<RoomReservation> reservations =
                    this.getActiveReservationsByConferenceId(conferenceId, includeConflicted);
            result = this.roomAllocationDataSource.addRoomAllocations(reservations);
        }
        return result;
    }

    /**
     * Get the identifiers of active reservations with the given conference call identifier.
     *
     * @param conferenceId the conference call identifier
     * @return list of active reservation id's in the conference call
     */
    public final Integer[] getActiveReservationIdsInConference(final Integer conferenceId) {
        final DataSource dataSource = this.createCopy();
        dataSource.addRestriction(
            Restrictions.eq(this.mainTableName, Constants.RES_CONFERENCE, conferenceId));
        dataSource.addRestriction(Restrictions.in(this.mainTableName, Constants.STATUS,
            Constants.STATUS_AWAITING_APP_OR_CONFIRMED));
        final List<DataRecord> records = dataSource.getRecords();
        final Integer[] reservationIds = new Integer[records.size()];
        for (int i = 0; i < records.size(); ++i) {
            reservationIds[i] =
                    records.get(i).getInt(this.mainTableName + Constants.DOT + Constants.RES_ID);
        }
        return reservationIds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<RoomReservation> getDistinctReservationsByUniqueId(final String uniqueId) {
        final List<RoomReservation> allReservations = this.getByUniqueId(uniqueId, null, null);

        /*
         * Keep only one reservation per conference id for the verification. We assume the others
         * are in sync with their primary.
         */
        final List<RoomReservation> reservations = new ArrayList<RoomReservation>();
        final Set<Integer> conferenceIds = new HashSet<Integer>();
        for (final RoomReservation reservation : allReservations) {
            if (reservation.getConferenceId() == null
                    || conferenceIds.add(reservation.getConferenceId())) {
                reservations.add(reservation);
            }
        }
        return reservations;
    }

    /**
     * Persist the given conference id in the given reservation.
     *
     * @param reservation the reservation to set the conference id for
     * @param conferenceId the conference id to set
     */
    @Override
    public void persistConferenceId(final RoomReservation reservation, final Integer conferenceId) {
        final RoomReservation storedReservation = this.get(reservation.getReserveId());
        storedReservation.setConferenceId(conferenceId);
        super.update(storedReservation);

        // Also set it in the reservation parameter to reflect the change.
        reservation.setConferenceId(conferenceId);
    }

    /**
     * Get all active reservations linked to the same conference reservation ID.
     *
     * @param conferenceId conference reservation id
     * @param includeConflicted whether to include conflicted reservations in the result
     *
     * @return list of reservations
     */
    protected final List<RoomReservation> getActiveReservationsByConferenceId(
            final Integer conferenceId, final boolean includeConflicted) {
        this.clearRestrictions();
        final ParsedRestrictionDef restriction = new ParsedRestrictionDef();
        restriction.addClause(this.tableName, Constants.RES_CONFERENCE, conferenceId,
            Operation.EQUALS);

        return getActiveReservations(restriction, includeConflicted);
    }
}
