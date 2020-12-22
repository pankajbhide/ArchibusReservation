package com.archibus.app.reservation.service;

import java.util.*;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.service.helpers.*;
import com.archibus.app.reservation.util.ReservationUtils;
import com.archibus.datasource.*;
import com.archibus.datasource.data.*;
import com.archibus.datasource.restriction.Restrictions;

/**
 * Contains common logic used by room reservation and conference call reservation WFRs.
 *
 * @author Yorik Gerlo
 * @since 21.3
 */
public class RoomReservationWfrBase extends RoomReservationServiceBase {

    /** Error message when no reservation id is provided. */
    protected static final String NO_RESERVATION_ID = "No reservation id provided";

    /** The Constant RESERVE_DATE_END. */
    protected static final String RESERVE_DATE_END = "reserve.date_end";

    /** The Constant RESERVE_DATE_START. */
    protected static final String RESERVE_DATE_START = "reserve.date_start";

    /** The Constant RESERVE_RES_ID. */
    protected static final String RESERVE_RES_ID = "reserve.res_id";

    /** The reservations service. */
    protected IConferenceReservationService reservationService;

    /** The cancel service. */
    protected CancelReservationService cancelReservationService;

    /** The space service. */
    protected ISpaceService spaceService;

    /** The reservation messages service. */
    protected ConferenceCallMessagesService messagesService;

    /** The calendar service wrapper. */
    protected CalendarServiceWrapper calendarServiceWrapper;

    /**
     * Setter for the reservationService property.
     *
     * @param reservationService the reservationService to set
     * @see reservationService
     */
    public void setReservationService(final IConferenceReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * Setter for the cancelReservationService property.
     *
     * @param cancelReservationService the cancelReservationService to set
     * @see cancelReservationService
     */
    public void setCancelReservationService(
            final CancelReservationService cancelReservationService) {
        this.cancelReservationService = cancelReservationService;
    }

    /**
     * Setter for the space service.
     *
     * @param spaceService the new space service
     */
    public void setSpaceService(final ISpaceService spaceService) {
        this.spaceService = spaceService;
    }

    /**
     * Setter for the calendar service wrapper.
     *
     * @param calendarServiceWrapper the calendar service wrapper
     */
    public void setCalendarServiceWrapper(final CalendarServiceWrapper calendarServiceWrapper) {
        this.calendarServiceWrapper = calendarServiceWrapper;
    }

    /**
     * Strip the conflicts description from the comments.
     *
     * @param email organizer email address
     * @param comments the comments to remove the description from
     * @return the reservation comments without the conflicts description
     */
    public String stripConflictsDescription(final String email, final String comments) {
        return this.messagesService.stripConflictsDescription(email, comments);
    }

    /**
     * Update the other reservations in the conference call that contains the given room
     * reservation. This method checks the database for other active reservations in the conference
     * call. If none are found the method returns null.
     *
     * @param roomReservation the room reservation for which to update the other reservations
     * @param comments the comments to add to the reservation
     * @param checkForChangedTimes true if the times of the reservation may have changed, requiring
     *            a full save with availability check for each room in the conference call
     * @return the updated conference call reservations, or null if no active reservations remain
     */
    protected List<RoomReservation> updateOtherReservationsInConferenceCall(
            final IReservation roomReservation, final String comments,
            final boolean checkForChangedTimes) {
        final List<RoomReservation> confCallReservations = this.reservationDataSource
            .getByConferenceId(roomReservation.getConferenceId(), true);
        if (confCallReservations != null && !confCallReservations.isEmpty()) {
            // update calendar event and comments in each remaining reservation
            this.messagesService.insertConferenceCallLocations(confCallReservations,
                this.spaceService, comments);

            // track whether any of the other reservations has a room conflict
            boolean hasRoomConflictInConferenceCall = false;

            // Save the conference call reservations to persist the comments.
            for (final RoomReservation confCallReservation : confCallReservations) {
                hasRoomConflictInConferenceCall = hasRoomConflictInConferenceCall
                        || confCallReservation.getRoomAllocations().isEmpty();
                /*
                 * Check whether the times must be updated. At this point that's no longer required
                 * for the location being edited. Compare in the time zone of the location being
                 * edited.
                 */
                if (!checkForChangedTimes
                        || confCallReservation.getReserveId().equals(roomReservation.getReserveId())
                        || this.isTimePeriodEqual(roomReservation, confCallReservation)) {
                    this.reservationDataSource.update(confCallReservation);
                } else {
                    // full-fledged update with room availability check
                    confCallReservation.setTimePeriod(roomReservation.getTimePeriod());
                    this.reservationService.saveReservation(confCallReservation);
                }
            }

            // prepare the primary reservation for updating the calendar
            final RoomReservation primaryReservation = confCallReservations.get(0);
            final String buildingId = primaryReservation.determineBuildingId();
            primaryReservation.setTimeZone(this.timeZoneCache.getBuildingTimeZone(buildingId));
            primaryReservation.setRoomConflictInConferenceCall(hasRoomConflictInConferenceCall);
        }
        return confCallReservations;
    }

    /**
     * Compare the time period of the two reservations. Use the time zone cache to determine the
     * local time zone of the second reservation.
     *
     * @param primaryReservation the room reservation with specified time zone
     * @param linkedReservation the other reservation in local time
     * @return true if the time periods match
     */
    private boolean isTimePeriodEqual(final IReservation primaryReservation,
            final IReservation linkedReservation) {

        linkedReservation.setTimeZone(
            this.timeZoneCache.getBuildingTimeZone(linkedReservation.determineBuildingId()));

        return primaryReservation.getTimePeriod().equals(ReservationUtils
            .getTimePeriodInTimeZone(linkedReservation, primaryReservation.getTimeZone()));
    }

    /**
     * Cancel the corresponding calendar events for a recurring cancellation of a single room.
     *
     * @param reservation the first room reservation indicated for cancelling
     * @param comments the comments entered by the user
     * @param cancelMeeting true to cancel the meeting, false to remove the location from it
     * @param cancelledReservations the reservations that were actually cancelled
     * @param failedReservations the reservations which could not be cancelled
     */
    protected void cancelRecurringSingleRoomCalendarEvent(final RoomReservation reservation,
            final String comments, final boolean cancelMeeting,
            final List<RoomReservation> cancelledReservations,
            final List<IReservation> failedReservations) {
        /*
         * Check if this is the parent reservation, and if it's a recurring conference call check
         * there are no other reservations in the conference call, which means all occurrences are
         * cancelled. In this situation the meeting could be removed from the calendar in a single
         * operation.
         */
        if (reservation.getReserveId().equals(reservation.getParentId())
                && failedReservations.isEmpty() && this.conferenceSeriesIsEmpty(reservation)) {

            if (cancelledReservations != null && !cancelledReservations.isEmpty()) {
                // copy the cancelled room reservations to the master (for ics)
                reservation.setCreatedReservations(cancelledReservations);
            }

            this.calendarServiceWrapper.cancelRecurringCalendarEvent(reservation, comments,
                cancelMeeting);
        } else {
            removeRoomRecurring(cancelledReservations, comments, cancelMeeting);
        }
    }

    /**
     * Remove a single room from a recurring meeting. Meeting occurrences only having this room are
     * cancelled (depending on the cancelMeeting parameter). Reservations for other rooms are
     * updated so they no longer refer to the cancelled room, and the corresponding meeting
     * occurrences are also updated. If possible, the calendar update is applied to the full series
     * in a single operation.
     *
     * @param cancelledReservations the reservations being cancelled
     * @param comments the comments entered by the user, to be included in the cancellation
     * @param cancelMeeting whether the meeting should be cancelled if no other rooms are booked for
     *            an occurrence - when set to false the location is removed instead
     */
    private void removeRoomRecurring(final List<RoomReservation> cancelledReservations,
            final String comments, final boolean cancelMeeting) {
        final List<RoomReservation> createdReservations = new ArrayList<RoomReservation>();
        for (final RoomReservation cancelledReservation : cancelledReservations) {
            final List<RoomReservation> confCallReservations = this
                .updateOtherReservationsInConferenceCall(cancelledReservation, comments, false);
            if (confCallReservations == null || confCallReservations.isEmpty()) {
                try {
                    this.calendarServiceWrapper.cancelCalendarEvent(cancelledReservation, comments,
                        cancelMeeting);
                } catch (final CalendarException exception) {
                    this.calendarServiceWrapper.handleCalendarException(exception,
                        cancelledReservation, CalendarServiceWrapper.CALENDAR_CANCEL_ERROR,
                        CalendarServiceWrapper.class);
                }
            } else {
                createdReservations.add(confCallReservations.get(0));
            }
        }

        /*
         * APP-2131 If we are updating all remaining occurrences, update the series in one go.
         * However, note that occurrences that are already exceptions might have different rooms
         * booked already.
         */
        if (!createdReservations.isEmpty()) {
            final RoomReservation primaryReservation = createdReservations.get(0);
            primaryReservation.setCreatedReservations(createdReservations);
            final DataSource reserveDs = this.reservationDataSource.createCopy();
            reserveDs.addRestriction(Restrictions.eq(reserveDs.getMainTableName(), Constants.RES_ID,
                primaryReservation.getReserveId()));

            this.calendarServiceWrapper.saveCalendarEvent(reserveDs.getRecord(), primaryReservation,
                createdReservations);
        }
    }

    /**
     * Extract all room reservation from a given list of reservation.
     *
     * @param reservations the reservations
     * @return the room reservations
     */
    protected List<RoomReservation> extractRoomReservations(final List<IReservation> reservations) {
        final List<RoomReservation> roomReservations =
                new ArrayList<RoomReservation>(reservations.size());
        for (final IReservation cancelledRes : reservations) {
            if (cancelledRes instanceof RoomReservation) {
                roomReservations.add((RoomReservation) cancelledRes);
            }
        }
        return roomReservations;
    }

    /**
     * Check there are no more active reservations in the conference call series linked to the given
     * reservation.
     *
     * @param reservation the reservation to check
     * @return true if the conference series is empty, false if active reservations remain
     */
    private boolean conferenceSeriesIsEmpty(final RoomReservation reservation) {
        return reservation.getConferenceId() == null || reservation.getConferenceId() == 0
                || this.reservationDataSource.getConferenceSeries(reservation.getConferenceId(),
                    null, DataSource.SORT_ASC, true).isEmpty();
    }

    /**
     * Cancel the appropriate calendar events for a cancelled recurring conference reservation.
     *
     * @param firstConferenceId the first conference id that was cancelled
     * @param conferenceParentId the parent conference id (id of the first occurrence in the full
     *            series)
     * @param cancelledOccurrences the occurrences that were cancelled
     * @param comments the user's comments
     * @param cancelMeeting true to cancel the meeting, false to only remove the location from it
     */
    protected void cancelRecurringConferenceCalendarEvent(final Integer firstConferenceId,
            final Integer conferenceParentId, final List<RoomReservation> cancelledOccurrences,
            final String comments, final boolean cancelMeeting) {
        // Check if this is the parent reservation, then all occurrences are cancelled.
        // If there are no more active reservations with same parent id, the meeting could
        // be removed from the calendar in a single operation.
        if (firstConferenceId.equals(conferenceParentId)) {
            final RoomReservation firstReservation = cancelledOccurrences.get(0);
            firstReservation.setCreatedReservations(cancelledOccurrences);

            this.calendarServiceWrapper.cancelRecurringCalendarEvent(firstReservation, comments,
                cancelMeeting);
        } else {
            for (final IReservation roomReservation : cancelledOccurrences) {
                try {
                    this.calendarServiceWrapper.cancelCalendarEvent(roomReservation, comments,
                        cancelMeeting);
                } catch (final CalendarException exception) {
                    this.calendarServiceWrapper.handleCalendarException(exception, roomReservation,
                        CalendarServiceWrapper.CALENDAR_CANCEL_ERROR, CalendarServiceWrapper.class);
                }
            }
        }
    }

    /**
     * Compile a room reservation based on the parameters passed to the WFR.
     *
     * @param reservation the reserve data record
     * @param roomAllocation the reserve_rm data record
     * @param resourceList the list of equipment & services
     * @param cateringList the list of catering
     * @return the room reservation object
     */
    protected RoomReservation compileRoomReservation(final DataRecord reservation,
            final DataRecord roomAllocation, final DataSetList resourceList,
            final DataSetList cateringList) {
        final RoomReservation roomReservation = convertReservationRecordToObject(reservation);

        // add the room allocation to the reservation
        roomReservation
            .addRoomAllocation(this.roomAllocationDataSource.convertRecordToObject(roomAllocation));

        this.reservationDataSource.addResourceList(roomReservation, cateringList);
        this.reservationDataSource.addResourceList(roomReservation, resourceList);

        // check the start and end time window
        ResourceReservationServiceHelper.checkResourceAllocations(roomReservation);
        return roomReservation;
    }

    /**
     * Convert the room reservation data record to the equivalent object.
     *
     * @param reservation room reservation data record
     * @return room reservation object
     */
    protected RoomReservation convertReservationRecordToObject(final DataRecord reservation) {
        if (reservation.getDate(RESERVE_DATE_END) == null) {
            reservation.setValue(RESERVE_DATE_END, reservation.getDate(RESERVE_DATE_START));
        }

        final RoomReservation roomReservation =
                this.reservationDataSource.convertRecordToObject(reservation);

        // make sure the date of time values are set to 1899
        roomReservation.setStartTime(TimePeriod.clearDate(roomReservation.getStartTime()));
        roomReservation.setEndTime(TimePeriod.clearDate(roomReservation.getEndTime()));

        ReservationWfrServiceHelper.validateEmails(roomReservation);
        return roomReservation;
    }

    /**
     * Gets the original reservations.
     *
     * @param roomReservation the room reservation
     * @return the original reservations
     */
    protected List<RoomReservation> getOriginalReservations(final RoomReservation roomReservation) {
        // If this is an update in a recurrence series, get the original before updating.
        List<RoomReservation> originalReservations = null;
        if (roomReservation.getParentId() != null) {
            if (roomReservation.getStartDate().equals(roomReservation.getEndDate())) {
                // for edit single occurrence, get original date from database
                // including conflicted occurrences
                originalReservations = new ArrayList<RoomReservation>();
                originalReservations.add(this.reservationDataSource
                    .getActiveReservation(roomReservation.getReserveId()));
            } else {
                // not including conflicted occurrences
                originalReservations =
                        this.reservationDataSource.getByParentId(roomReservation.getParentId(),
                            roomReservation.getStartDate(), roomReservation.getEndDate(), false);
            }
        }
        return originalReservations;
    }

    /**
     * Convert the set of conflict dates to data records to be returned as WFR result.
     *
     * @param conflictDates the conflicted dates (may be null or empty)
     * @return the list of dates in reserve DataRecord format wrapped in a data set list
     */
    protected DataSetList wrapConflictDates(final Set<Date> conflictDates) {
        final DataSetList wrappedConflictDates = new DataSetList();
        if (conflictDates != null && !conflictDates.isEmpty()) {
            final List<DataRecord> conflictDateRecords = new ArrayList<DataRecord>();
            final DataSource datesDs = DataSourceFactory.createDataSourceForFields("reserve",
                new String[] { "date_start" });
            for (final Date conflictDate : conflictDates) {
                final DataRecord record = datesDs.createNewRecord();
                record.setValue(RESERVE_DATE_START, conflictDate);
                conflictDateRecords.add(record);
            }
            wrappedConflictDates.addRecords(conflictDateRecords);
        }
        return wrappedConflictDates;
    }

    /**
     * Get the dates for which the room could not be allocated.
     *
     * @param createdReservations the created reservations
     * @return the list of dates in reserve DataRecord format
     */
    protected SortedSet<Date> getConflictDates(final List<RoomReservation> createdReservations) {
        final SortedSet<Date> conflictDates = new TreeSet<Date>();
        for (final RoomReservation reservation : createdReservations) {
            if (reservation.getRoomAllocations().isEmpty()
                    || reservation.hasRoomConflictInConferenceCall()) {
                conflictDates.add(reservation.getStartDate());
            }
        }

        return conflictDates;
    }

    /**
     * Update the reserve record passed as parameter to a save WFR.
     *
     * @param reservation the reserve record
     * @param roomReservation the corresponding object
     */
    protected void updateReserveRecordAfterSave(final DataRecord reservation,
            final RoomReservation roomReservation) {
        // update the reservation record (for testing)
        reservation.setValue(
            this.reservationDataSource.getMainTableName() + Constants.DOT + Constants.RES_ID,
            roomReservation.getReserveId());
        reservation.setValue(
            this.reservationDataSource.getMainTableName() + Constants.DOT + Constants.UNIQUE_ID,
            roomReservation.getUniqueId());
        reservation.setNew(false);
    }

    /**
     * Set the Conference Call messages service.
     *
     * @param messagesService the messages service to set
     */
    public void setMessagesService(final ConferenceCallMessagesService messagesService) {
        this.messagesService = messagesService;
    }

}