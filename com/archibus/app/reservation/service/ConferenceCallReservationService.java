package com.archibus.app.reservation.service;

import java.util.*;

import com.archibus.app.reservation.dao.IResourceAllocationDataSource;
import com.archibus.app.reservation.dao.datasource.*;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.Recurrence;
import com.archibus.app.reservation.service.helpers.ReservationWfrServiceHelper;
import com.archibus.app.reservation.util.*;
import com.archibus.context.*;
import com.archibus.datasource.data.*;
import com.archibus.datasource.restriction.Restrictions;

/**
 * Conference call reservation service defining WFR's specific for conference call reservations.
 *
 * @author Yorik Gerlo
 * @since 21.3
 */
public class ConferenceCallReservationService extends RoomReservationWfrBase {

    /** HTML paragraph end tag. */
    private static final String HTML_P_END = "</p>";

    /** HTML paragraph start tag. */
    private static final String HTML_P_START = "<p>";

    /** Datasource for resource allocations. */
    private IResourceAllocationDataSource resourceAllocationDataSource;

    /**
     * Verify whether the current user can edit the conference call reservation with the given id.
     *
     * @param conferenceId conference call identifier
     * @throws ReservationException if the current user cannot edit the conference call
     */
    public final void canEditConferenceCall(final Integer conferenceId)
            throws ReservationException {
        final List<RoomReservation> reservations =
                this.reservationDataSource.getByConferenceId(conferenceId, false);

        // KB 3045424 enable site VPA for this check or disable VPA.
        if (DataSourceUtils.isVpaEnabled()) {
            this.roomAllocationDataSource.setApplyVpaRestrictions(true);
            this.roomAllocationDataSource
                .addRestriction(Restrictions.sql("${sql.getVpaRestrictionForTable('bl')}"));
        } else {
            this.roomAllocationDataSource.setApplyVpaRestrictions(false);
        }

        for (final RoomReservation reservation : reservations) {
            final RoomAllocation allocation = reservation.getRoomAllocations().get(0);
            if (this.roomAllocationDataSource.get(allocation.getId()) == null) {
                // @translatable
                throw new ReservationException(
                    "Reservation {0} cannot be edited due to your VPA settings.",
                    ConferenceCallReservationService.class, this.messagesService.getAdminService(),
                    reservation.getReserveId());
            }
            final User user = ContextStore.get().getUser();
            if (!user.isMemberOfGroup(Constants.RESERVATION_SERVICE_DESK)
                    && !user.isMemberOfGroup(Constants.RESERVATION_MANAGER)) {
                // this also throws an error if the room/resource cannot be modified
                this.roomAllocationDataSource.checkEditing(allocation);
                for (final ResourceAllocation resourceAllocation : ReservationUtils
                    .getActiveResourceAllocations(reservation)) {
                    this.resourceAllocationDataSource.checkEditing(resourceAllocation);
                }
            }
        }
        ReservationsContextHelper.ensureResultMessageIsSet();
    }

    /**
     * Get the time zone record for a given building.
     *
     * @param buildingId the building id
     * @return the time zone record
     */
    public final DataRecord getLocationTimeZone(final String buildingId) {
        return TimeZoneConverter.getTimeZoneForBuilding(buildingId);
    }

    /**
     * Get the location string for a room reservation.
     *
     * @param reservation the reservation
     * @param roomAllocations all room allocations
     * @return the location string
     */
    public String getLocationString(final DataRecord reservation,
            final DataSetList roomAllocations) {
        final StringBuffer location = new StringBuffer();
        for (final DataRecord roomAllocation : roomAllocations.getRecords()) {

            location.append(HTML_P_START);
            location.append(this.spaceService.getLocationString(this.roomAllocationDataSource
                .convertRecordToObject(roomAllocation).getRoomArrangement()));
            location.append(HTML_P_END);

        }
        return location.toString();
    }

    /**
     * Get the location string for a single room of a conference call reservation being edited.
     *
     * @param reservation the reservation
     * @param roomAllocation the room allocation
     * @return the location string
     */
    public String getLocationStringForSingleEdit(final DataRecord reservation,
            final DataRecord roomAllocation) {
        final RoomReservation roomReservation =
                this.reservationDataSource.convertRecordToObject(reservation);

        final StringBuffer location = new StringBuffer();
        location.append(HTML_P_START);
        location.append(this.spaceService.getLocationString(this.roomAllocationDataSource
            .convertRecordToObject(roomAllocation).getRoomArrangement()));
        location.append(HTML_P_END);
        // append other locations to the result
        final List<RoomReservation> othersInConfCall = this.reservationDataSource
            .getByConferenceId(roomReservation.getConferenceId(), false);
        location.append(HTML_P_START);
        // @translatable
        location.append(
            ReservationsContextHelper.localizeString("Other locations in this conference call:",
                ConferenceCallReservationService.class, this.messagesService.getAdminService()));
        location.append(HTML_P_END);
        location.append(HTML_P_START);
        for (final RoomReservation otherReservation : othersInConfCall) {
            if (!otherReservation.getReserveId().equals(roomReservation.getReserveId())) {
                location.append(this.spaceService.getLocationString(
                    otherReservation.getRoomAllocations().get(0).getRoomArrangement()));
                location.append("<br/>");
            }
        }
        location.append(HTML_P_END);

        return location.toString();
    }

    /**
     * Strip the conference call locations and conflicts description from the comments.
     *
     * @param email organizer email address
     * @param comments the comments to remove the locations from
     * @return the reservation comments without the conference call locations
     */
    public String stripConferenceCallLocations(final String email, final String comments) {
        final String stripped = this.messagesService.stripConferenceCallLocations(email, comments);
        return this.messagesService.stripConflictsDescription(email, stripped);
    }

    /**
     * Calculate total cost of the reservation.
     *
     * The total cost per reservation is calculated and multiplied by the number of occurrences.
     *
     * @param reservation the reservation.
     * @param roomAllocations the room allocations
     * @param numberOfOccurrences the number of occurrences
     * @return total cost of all occurrences
     */
    public Double calculateTotalCost(final DataRecord reservation,
            final DataSetList roomAllocations, final int numberOfOccurrences) {

        final RoomReservation roomReservation =
                this.reservationDataSource.convertRecordToObject(reservation);
        // make sure the date of time values are set to 1899
        roomReservation.setStartTime(TimePeriod.clearDate(roomReservation.getStartTime()));
        roomReservation.setEndTime(TimePeriod.clearDate(roomReservation.getEndTime()));

        // resource allocations are not showed and also not included in the costs
        double totalCost = 0.0;
        for (final DataRecord record : roomAllocations.getRecords()) {
            // add the room allocation to the reservation
            roomReservation
                .addRoomAllocation(this.roomAllocationDataSource.convertRecordToObject(record));
            totalCost += this.reservationDataSource.calculateCosts(roomReservation)
                    * numberOfOccurrences;

            // remove the current room allocation, to be replaced by the next one
            roomReservation.getRoomAllocations().clear();
        }

        // add the resources and catering
        return totalCost;
    }

    /**
     * Cancel a conference call reservation on a single date.
     *
     * @param conferenceId conference reservation id
     * @param comments the comments
     * @param cancelMeeting true to cancel the meeting, false to only remove the location from it --
     *            null is interpreted as true
     */
    public void cancelConferenceReservation(final Integer conferenceId, final String comments,
            final Boolean cancelMeeting) {
        List<RoomReservation> roomReservations = null;
        if (conferenceId != null && conferenceId > 0) {
            // Get the reservation in the building time zone.
            roomReservations = this.reservationDataSource.getByConferenceId(conferenceId, true);
        } else {
            throw new ReservationException(NO_RESERVATION_ID, RoomReservationWfrBase.class,
                this.messagesService.getAdminService());
        }
        if (roomReservations != null && !roomReservations.isEmpty()) {
            WorkRequestService.setFlagToSendEmailsInSingleJob();
            for (final RoomReservation reservation : roomReservations) {
                this.cancelReservationService.cancelReservation(reservation, comments);
            }
            WorkRequestService.startJobToSendEmailsInSingleJob();
            try {
                this.calendarServiceWrapper.cancelCalendarEvent(roomReservations.get(0), comments,
                    cancelMeeting == null || cancelMeeting);
            } catch (final CalendarException exception) {
                this.calendarServiceWrapper.handleCalendarException(exception,
                    roomReservations.get(0), CalendarServiceWrapper.CALENDAR_CANCEL_ERROR,
                    CalendarServiceWrapper.class);
            }
        }
        ReservationsContextHelper.ensureResultMessageIsSet();
    }

    /**
     * Cancel recurring conference reservation.
     *
     * @param firstConferenceId conference reservation id of the first occurrence in the series to
     *            cancel
     * @param comments the comments
     * @param cancelMeeting true to cancel the meeting, false to only remove the location from it --
     *            null is interpreted as true
     * @return the list of id's that failed
     */
    public List<Integer> cancelRecurringConferenceReservation(final Integer firstConferenceId,
            final String comments, final Boolean cancelMeeting) {
        final List<Integer> failures = new ArrayList<Integer>();
        if (firstConferenceId != null && firstConferenceId > 0) {
            // 1. Check whether the chosen occurrence of the conference call can be cancelled.
            this.reservationDataSource.canCancelConferenceCall(firstConferenceId,
                this.messagesService.getAdminService());

            // 2. get all conference id's
            final Integer conferenceParentId =
                    this.reservationDataSource.getParentId(firstConferenceId);
            final Integer[] conferenceIds = this.reservationDataSource
                .getRecurringConferenceIds(conferenceParentId, firstConferenceId);

            WorkRequestService.setFlagToSendEmailsInSingleJob();
            final List<RoomReservation> cancelledOccurrences = new ArrayList<RoomReservation>();
            for (final Integer conferenceId : conferenceIds) {
                // 3. for each conference id get all active reservations
                final List<RoomReservation> reservations =
                        this.reservationDataSource.getByConferenceId(conferenceId, true);
                if (!reservations.isEmpty()) {
                    cancelledOccurrences.add(reservations.get(0));
                }
                // 4. for each reservation check whether it can be cancelled
                for (final RoomReservation roomReservation : reservations) {
                    try {
                        this.reservationDataSource.canBeCancelledByCurrentUser(roomReservation);
                    } catch (final ReservationException exception) {
                        // this one can't be cancelled, so skip and report
                        failures.add(roomReservation.getReserveId());
                        continue;
                    }
                    this.cancelReservationService.cancelReservation(roomReservation, comments);
                }
            }
            WorkRequestService.startJobToSendEmailsInSingleJob();
            // 5. Cancel the meeting occurrences or full series if appropriate, regardless of
            // failures.

            cancelRecurringConferenceCalendarEvent(firstConferenceId, conferenceParentId,
                cancelledOccurrences, comments, cancelMeeting == null || cancelMeeting);
        } else {
            throw new ReservationException(NO_RESERVATION_ID, RoomReservationWfrBase.class,
                this.messagesService.getAdminService());
        }
        ReservationsContextHelper.ensureResultMessageIsSet();
        return failures;
    }

    /**
     * Save conference call reservation.
     *
     * The conference call reservation can be a single or a recurrent reservation. When editing a
     * recurrent reservation, the recurrence pattern and reservation dates cannot change. When
     * editing a single occurrence, the date might change.
     *
     * @param reservation the reservation
     * @param roomAllocations all room allocations
     * @param timeZoneId identifier for the time zone of the reservation times
     * @return the conflicted reservation records
     */
    public DataSetList saveReservation(final DataRecord reservation,
            final DataSetList roomAllocations, final String timeZoneId) {

        RoomReservation primaryReservation = this.convertReservationRecordToObject(reservation);
        primaryReservation.setTimeZone(timeZoneId);
        final TimePeriod requestedTimePeriod = new TimePeriod(primaryReservation.getTimePeriod());
        requestedTimePeriod.setEndDate(requestedTimePeriod.getStartDate());

        final boolean editRecurring = primaryReservation.getParentId() != null
                && !primaryReservation.getStartDate().equals(primaryReservation.getEndDate());

        WorkRequestService.setFlagToSendEmailsInSingleJob();
        // original reservations are important for updating the calendar and for edit recurring
        List<RoomReservation> originalReservations = null;
        List<RoomReservation> confCallReservations = null;
        if (editRecurring) {
            final SortedMap<Integer, List<RoomReservation>> originalReservationsByConferenceId =
                    this.reservationDataSource.getAllReservationsInConferenceSeries(
                        primaryReservation.getConferenceId(), true);
            originalReservations = this.reservationDataSource
                .extractPrimaryReservations(originalReservationsByConferenceId);
            confCallReservations = this.compileConferenceReservations(reservation, roomAllocations,
                primaryReservation);

            editRecurringConferenceCallReservations(requestedTimePeriod, originalReservations,
                confCallReservations, originalReservationsByConferenceId);
        } else {
            originalReservations = this.getOriginalReservations(primaryReservation);
            confCallReservations = this.compileConferenceReservations(reservation, roomAllocations,
                primaryReservation);
            saveConferenceCallReservations(primaryReservation, confCallReservations);
        }
        WorkRequestService.startJobToSendEmailsInSingleJob();
        /*
         * Set the primary reservation to the first in the list of reservations. This is either the
         * first room allocation or the existing reservation with the lowest id (which was not
         * cancelled).
         */
        primaryReservation = confCallReservations.get(0);
        // Copy the time period from the created reservation to ensure it's in local time.
        primaryReservation
            .setTimePeriod(primaryReservation.getCreatedReservations().get(0).getTimePeriod());

        /*
         * KB 3045785 While saving the reservation was converted to local time. We want the meeting
         * to be created in the time zone entered by the user, but for conversion to UTC the times
         * should remain in local time. Hence we leave the times and time zone in local time but
         * indicate the time zone requested by the user in a separate property of the reservations.
         */
        primaryReservation.setRequestedTimeZone(requestedTimePeriod.getTimeZone());
        final String primaryTimeZone =
                this.timeZoneCache.getBuildingTimeZone(primaryReservation.determineBuildingId());
        primaryReservation.setTimeZone(primaryTimeZone);

        for (int i = 0; i < primaryReservation.getCreatedReservations().size(); ++i) {
            final RoomReservation primaryOccurrence =
                    primaryReservation.getCreatedReservations().get(i);
            primaryOccurrence.setRequestedTimeZone(requestedTimePeriod.getTimeZone());
            primaryOccurrence.setTimeZone(primaryTimeZone);
            for (final RoomReservation confCallReservation : confCallReservations) {
                final RoomReservation confCallOccurrence =
                        confCallReservation.getCreatedReservations().get(i);
                if (confCallOccurrence.getRoomAllocations().isEmpty()) {
                    primaryOccurrence.setRoomConflictInConferenceCall(true);
                    // once we found a conflict, don't look for another on the same date
                    break;
                }
            }
        }

        final int originalOccurrenceIndex = primaryReservation.getOccurrenceIndex();
        if (primaryReservation.getConferenceId() == null) {
            /*
             * Set the conference id of the primary reservation to ensure the reservation is treated
             * as a conference call in the calendar service.
             */
            primaryReservation.setConferenceId(primaryReservation.getReserveId());
        }

        SortedSet<Date> conflictDates = null;
        if (primaryReservation.getCreatedReservations().size() > 1) {
            conflictDates = getConflictDates(primaryReservation.getCreatedReservations());
        }
        this.messagesService.insertConflictsDescription(primaryReservation, conflictDates);

        this.calendarServiceWrapper.saveCalendarEvent(reservation, primaryReservation,
            originalReservations);

        final boolean updateParentIds =
                primaryReservation.getOccurrenceIndex() == 1 && originalOccurrenceIndex > 1;
        this.reservationDataSource.persistCommonIdentifiers(primaryReservation,
            confCallReservations, updateParentIds);

        updateReserveRecordAfterSave(reservation, primaryReservation);
        ReservationsContextHelper.ensureResultMessageIsSet();

        return this.wrapConflictDates(conflictDates);
    }

    /**
     * Edit a recurring conference call reservation, creating / updating / cancelling all required
     * room reservations.
     *
     * @param primaryTimePeriod the time period requested by the user
     * @param originalReservations the original primary reservation occurrences being updated (i.e.
     *            one active reservation for each occurrence)
     * @param confCallReservations the conference call reservations to be saved for the first
     *            occurrence being edited
     * @param originalReservationsByConferenceId map containing each active reservation per
     *            conference id
     */
    private void editRecurringConferenceCallReservations(final TimePeriod primaryTimePeriod,
            final List<RoomReservation> originalReservations,
            final List<RoomReservation> confCallReservations,
            final Map<Integer, List<RoomReservation>> originalReservationsByConferenceId) {
        final Map<Integer, RoomReservation> confCallReservationsByParentId =
                ReservationWfrServiceHelper.prepareCompiledReservations(confCallReservations,
                    originalReservations.size());

        // Edit recurring conference call: process by date taken from originalReservations
        for (final RoomReservation originalOccurrence : originalReservations) {
            final List<RoomReservation> existingReservationsForOccurrence =
                    originalReservationsByConferenceId.get(originalOccurrence.getConferenceId());

            // original occurrences are in local time -> determine time zone via cache
            originalOccurrence.setTimeZone(
                this.timeZoneCache.getBuildingTimeZone(originalOccurrence.determineBuildingId()));
            final TimePeriod timePeriodForOccurrence = ReservationUtils
                .getTimePeriodInTimeZone(originalOccurrence, primaryTimePeriod.getTimeZone());
            timePeriodForOccurrence.setStartTime(primaryTimePeriod.getStartTime());
            timePeriodForOccurrence.setEndTime(primaryTimePeriod.getEndTime());
            final Set<RoomReservation> confCallReservationsPendingForOccurrence =
                    new HashSet<RoomReservation>(confCallReservations);

            for (final RoomReservation existingOccurrence : existingReservationsForOccurrence) {
                // find the corresponding confCallReservation by res_parent
                final RoomReservation matchingConfCallReservation =
                        confCallReservationsByParentId.get(existingOccurrence.getParentId());
                if (matchingConfCallReservation == null) {
                    // res_parent not found in any of the confCallReservations
                    // cancel this occurrence
                    this.cancelReservationService.cancelReservation(existingOccurrence, null);
                } else {
                    // update the occurrence: all properties and room allocation (pkey, comments,
                    // attendees_in_room)
                    ReservationUtils.copyCommonConferenceProperties(matchingConfCallReservation,
                        existingOccurrence);
                    existingOccurrence.setTimePeriod(timePeriodForOccurrence);
                    RoomAllocation roomAllocation = null;
                    if (existingOccurrence.getRoomAllocations().isEmpty()) {
                        roomAllocation = new RoomAllocation();
                        existingOccurrence.addRoomAllocation(roomAllocation);
                    } else {
                        roomAllocation = existingOccurrence.getRoomAllocations().get(0);
                    }
                    matchingConfCallReservation.getRoomAllocations().get(0).copyTo(roomAllocation);

                    // save the changes
                    this.reservationService.saveReservation(existingOccurrence);

                    // mark the corresponding confCall res as OK for this occurrence
                    confCallReservationsPendingForOccurrence.remove(matchingConfCallReservation);
                    /*
                     * Remember the reservation which was updated in the createdReservations
                     * property, for later reference when persisting common identifiers.
                     */
                    matchingConfCallReservation.getCreatedReservations().add(existingOccurrence);
                }
            }

            // For each confCallReservation that doesn't have a corresponding updated occurrence,
            // create a new occurrence of the reservation, with same parent id (or new parent id for
            // first occurrence)
            // Create new ones not in the database, including valid parent id.
            for (final RoomReservation confCallReservation : confCallReservationsPendingForOccurrence) {
                if (confCallReservation.getReserveId() == null) {
                    // this is the first occurrence, save new reservation
                    confCallReservation.setTimePeriod(timePeriodForOccurrence);
                    this.reservationService.saveReservation(confCallReservation);
                    confCallReservation.setParentId(confCallReservation.getReserveId());

                    // persist the parent id
                    this.reservationDataSource.markRecurring(confCallReservation,
                        confCallReservation.getReserveId(), originalOccurrence.getRecurringRule(),
                        originalOccurrence.getOccurrenceIndex());
                    confCallReservation.getCreatedReservations().add(confCallReservation);
                } else {
                    // Create a new reservation object for this occurrence
                    final RoomReservation confCallOccurrence = new RoomReservation();
                    confCallReservation.copyTo(confCallOccurrence, false);
                    confCallOccurrence.setTimePeriod(timePeriodForOccurrence);
                    confCallOccurrence.setOccurrenceIndex(originalOccurrence.getOccurrenceIndex());
                    confCallOccurrence
                        .setRecurringDateModified(originalOccurrence.getRecurringDateModified());
                    confCallOccurrence.setConferenceId(originalOccurrence.getConferenceId());
                    final RoomAllocation roomAllocation = new RoomAllocation();
                    confCallOccurrence.addRoomAllocation(roomAllocation);
                    confCallReservation.getRoomAllocations().get(0).copyTo(roomAllocation);

                    // save new reservation
                    this.reservationService.saveReservation(confCallOccurrence);
                    // Remember it for persisting the common identifiers later.
                    confCallReservation.getCreatedReservations().add(confCallOccurrence);
                }
            }
        }
    }

    /**
     * Save a list of reservations representing a single conference call or a new recurring
     * conference call.
     *
     * @param primaryReservation the primary reservation in the conference call (corresponds to one
     *            of the confCallReservations)
     * @param confCallReservations the reservations in the conference call
     */
    private void saveConferenceCallReservations(final RoomReservation primaryReservation,
            final List<RoomReservation> confCallReservations) {
        /*
         * Create or edit non-recurring or single occurrence conference call; or create recurring
         * conference call: process by location.
         */
        Recurrence recurrence = null;
        if (primaryReservation.getConferenceId() == null
                && ReservationWfrServiceHelper.isNewRecurrenceOrEditSeries(primaryReservation)) {
            // Preparation: parse the recurrence pattern and correct the end date for all locations.
            // The recurrence pattern will be the same for all locations.
            recurrence = ReservationWfrServiceHelper.prepareNewRecurrence(primaryReservation);
            for (final RoomReservation confCallReservation : confCallReservations) {
                confCallReservation.setRecurrence(recurrence);
                confCallReservation.setEndDate(confCallReservation.getStartDate());
            }
        }
        this.reservationService.saveCompiledConferenceCallReservations(confCallReservations,
            recurrence);
    }

    /**
     * Compile a list of reservations to be saved for a conference call and cancel any existing
     * reservations not in the new selection of rooms.
     *
     * @param reservation the reservation data record
     * @param roomAllocations the room allocation data records
     * @param primaryReservation the reservation in object form
     * @return list of conference call reservations to be saved
     */
    private List<RoomReservation> compileConferenceReservations(final DataRecord reservation,
            final DataSetList roomAllocations, final RoomReservation primaryReservation) {
        // Create objects for the other room reservations.
        final List<RoomReservation> confCallReservations =
                new ArrayList<RoomReservation>(roomAllocations.getRecords().size());

        // Get the existing conference call reservations from the database, including resource
        // allocations.
        // Note resource allocations are updated automatically when these are saved via
        // roomReservationDataSource.
        final List<RoomReservation> existingConfCallReservations = this.reservationDataSource
            .getByConferenceId(primaryReservation.getConferenceId(), true);
        final Map<Integer, RoomAllocation> allocationsById = new HashMap<Integer, RoomAllocation>();
        final List<RoomAllocation> newAllocations = new ArrayList<RoomAllocation>();
        for (int i = 0; i < roomAllocations.getRecords().size(); ++i) {
            final RoomAllocation allocation = this.roomAllocationDataSource
                .convertRecordToObject(roomAllocations.getRecord(i));
            if (allocation.getReserveId() == null) {
                newAllocations.add(allocation);
            } else {
                allocationsById.put(allocation.getReserveId(), allocation);
            }
        }
        if (existingConfCallReservations != null) {
            for (final RoomReservation existingConfCallReservation : existingConfCallReservations) {
                final RoomAllocation allocation =
                        allocationsById.get(existingConfCallReservation.getReserveId());
                if (allocation == null) {
                    this.cancelReservationService.cancelReservation(existingConfCallReservation,
                        null);
                } else {
                    ReservationUtils.copyCommonConferenceProperties(primaryReservation,
                        existingConfCallReservation);
                    existingConfCallReservation.getRoomAllocations().clear();
                    existingConfCallReservation.addRoomAllocation(allocation);
                    confCallReservations.add(existingConfCallReservation);
                }
            }
        }
        // Create reservation objects for the new room allocations.
        for (final RoomAllocation allocation : newAllocations) {
            final RoomReservation confCallReservation =
                    this.reservationDataSource.convertRecordToObject(reservation);
            // clear the id's to ensure no existing reservation is overwritten
            confCallReservation.setReserveId(null);
            confCallReservation.setParentId(null);
            // do copy the conference id (can be null for a new conference call)
            confCallReservation.setConferenceId(primaryReservation.getConferenceId());
            // also clear the date fields and last modified by
            confCallReservation.setCreationDate(null);
            confCallReservation.setLastModifiedBy(null);
            confCallReservation.setLastModifiedDate(null);
            confCallReservation.setTimePeriod(primaryReservation.getTimePeriod());
            confCallReservation.addRoomAllocation(allocation);
            confCallReservations.add(confCallReservation);
            // Occurrence index and all other fields should match the primary reservation.
        }
        /*
         * Set the locations template in the body. Note the existing reservation with the lowest
         * reservation id, or the first room allocation provided as a parameter is considered the
         * primary reservation.
         */
        this.messagesService.insertConferenceCallLocations(confCallReservations, this.spaceService,
            null);
        return confCallReservations;
    }

    /**
     * Edit a single room reservation in a conference call.
     *
     * @param reservation the reservation
     * @param roomAllocation the room allocation
     * @param resourceList the resource list
     * @param cateringList the catering list
     * @return the conflicted reservation records
     */
    public DataSetList editSingleRoomReservation(final DataRecord reservation,
            final DataRecord roomAllocation, final DataSetList resourceList,
            final DataSetList cateringList) {

        final RoomReservation roomReservation =
                compileRoomReservation(reservation, roomAllocation, resourceList, cateringList);

        List<RoomReservation> createdReservations = null;
        final List<RoomReservation> originalReservations = getOriginalReservations(roomReservation);

        WorkRequestService.setFlagToSendEmailsInSingleJob();
        // Room and Resource availability is verified by RoomReservationDataSource.
        if (ReservationWfrServiceHelper.isNewRecurrenceOrEditSeries(roomReservation)) {
            // prepare for new recurring reservation and correct the end date
            ReservationWfrServiceHelper.prepareNewRecurrence(roomReservation);

            // Room and Resource availability is verified by RoomReservationDataSource.
            createdReservations = this.reservationService.editRecurringReservation(roomReservation);
        } else {
            this.reservationService.saveReservation(roomReservation);
            createdReservations = Arrays.asList(roomReservation);
        }

        final String timeZoneId =
                this.timeZoneCache.getBuildingTimeZone(roomReservation.determineBuildingId());
        RoomReservation primaryReservation = null;
        final List<List<RoomReservation>> allModifiedReservations =
                new ArrayList<List<RoomReservation>>();
        for (final RoomReservation createdReservation : createdReservations) {
            createdReservation.setTimeZone(timeZoneId);
            final List<RoomReservation> confCallReservations =
                    this.updateOtherReservationsInConferenceCall(createdReservation, null, true);

            // the reservation with lowest reservation id is used as the primary
            if (primaryReservation == null) {
                primaryReservation = confCallReservations.get(0);
                primaryReservation.setCreatedReservations(
                    new ArrayList<RoomReservation>(createdReservations.size()));
            }
            // KB 3045776 Track all primary reservations and use those to update the calendar, to
            // ensure the primary location is set correctly.
            primaryReservation.getCreatedReservations().add(confCallReservations.get(0));

            /*
             * Keep track of all modified reservations as we might need to update their unique id
             * and possibly remove them from the recurrence (KB 3040346). Since the number of
             * reservations can be different for each occurrence, we need to track the modified
             * reservations for each occurrence separately.
             */
            allModifiedReservations.add(confCallReservations);
        }
        WorkRequestService.startJobToSendEmailsInSingleJob();

        final int originalOccurrenceIndex = primaryReservation.getOccurrenceIndex();

        /*
         * Call saveCalendarEvent with the current primary reservation record. This ensures the best
         * matching time zone is used and that the primary location is set correctly.
         */
        this.calendarServiceWrapper.saveCalendarEvent(reservation, primaryReservation,
            originalReservations);

        /*
         * The calendar service might have generated a new appointment, so save the conference call
         * reservations again to persist the appointment unique id. Also update the parent id's and
         * remove individual occurrences from the recurrence if required. Updating parent id's is
         * only required if the occurrence index has changed.
         */
        final boolean updateParentIds =
                primaryReservation.getOccurrenceIndex() == 1 && originalOccurrenceIndex > 1;
        this.reservationDataSource.updateCommonIdentifiers(
            primaryReservation.getCreatedReservations(), allModifiedReservations, updateParentIds);

        roomReservation.setUniqueId(primaryReservation.getUniqueId());
        updateReserveRecordAfterSave(reservation, roomReservation);
        ReservationsContextHelper.ensureResultMessageIsSet();

        // no conflicts can occur
        return this.wrapConflictDates(null);
    }

    /**
     * Set the resourceAllocation DataSource.
     *
     * @param resourceAllocationDataSource the new resourceAllocation DataSource
     */
    public final void setResourceAllocationDataSource(
            final ResourceAllocationDataSource resourceAllocationDataSource) {
        this.resourceAllocationDataSource = resourceAllocationDataSource;
    }

}
