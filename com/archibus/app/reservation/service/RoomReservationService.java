package com.archibus.app.reservation.service;

import java.util.*;

import org.json.JSONObject;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.Recurrence;
import com.archibus.app.reservation.service.helpers.*;
import com.archibus.app.reservation.util.*;
import com.archibus.datasource.data.*;

/**
 * Room Reservation Service for workflow rules in the new reservation module.
 * <p>
 * This class provided all workflow rule for the room reservation creation view: Called from
 * ab-rr-create-room-reservation.axvw<br/>
 * Called from ab-rr-create-room-reservation-confirm.axvw<br/>
 * <p>
 * <p>
 * The class will be defined as a Spring bean and will reference other Spring beans. <br/>
 * The Calendar service can have different implementations that implement the ICalendar interface.
 * <br/>
 * All Spring beans are defined as prototype.
 * </p>
 *
 * @author Bart Vanderschoot
 * @since 21.2
 *
 */
public class RoomReservationService extends RoomReservationWfrBase {

    /** The attendee service. */
    private IAttendeeService attendeeService;

    /**
     * Save room reservation.
     *
     * The room reservation can be a single or a recurrent reservation. When editing a recurrent
     * reservation, the recurrence pattern and reservation dates cannot change. When editing a
     * single occurrence, the date might change.
     *
     * @param reservation the reservation
     * @param roomAllocation the room allocation
     * @param resourceList the resource list
     * @param cateringList the catering list
     * @return the conflicted reservation records
     */
    public DataSetList saveRoomReservation(final DataRecord reservation,
            final DataRecord roomAllocation, final DataSetList resourceList,
            final DataSetList cateringList) {

        final RoomReservation roomReservation = this.compileRoomReservation(reservation,
            roomAllocation, resourceList, cateringList);
        ReservationUtils.truncateComments(roomReservation);

        Recurrence recurrence = null;
        List<RoomReservation> createdReservations = null;

        final List<RoomReservation> originalReservations = getOriginalReservations(roomReservation);

        WorkRequestService.setFlagToSendEmailsInSingleJob();
        if (ReservationWfrServiceHelper.isNewRecurrenceOrEditSeries(roomReservation)) {
            // prepare for new recurring reservation and correct the end date
            recurrence = ReservationWfrServiceHelper.prepareNewRecurrence(roomReservation);

            if (recurrence == null) {
                createdReservations =
                        this.reservationService.editRecurringReservation(roomReservation);
            } else {
                // Room and Resource availability is verified by RoomReservationDataSource.
                createdReservations = this.reservationService
                    .saveRecurringReservation(roomReservation, recurrence, null);
            }
        } else {
            // Room and Resource availability is verified by RoomReservationDataSource.
            this.reservationService.saveReservation(roomReservation);
            createdReservations = new ArrayList<RoomReservation>();
            createdReservations.add(roomReservation);
        }
        // store the generated reservation instances in the reservation
        roomReservation.setCreatedReservations(createdReservations);
        WorkRequestService.startJobToSendEmailsInSingleJob();

        // include a description of the conflicts in the reservation comments
        final SortedSet<Date> conflictDates = getConflictDates(createdReservations);
        this.messagesService.insertConflictsDescription(roomReservation, conflictDates);

        // determine the time zone of the building and set the local time zone
        final String buildingId = roomAllocation.getString("reserve_rm.bl_id");
        roomReservation.setTimeZone(this.timeZoneCache.getBuildingTimeZone(buildingId));
        for (final RoomReservation createdReservation : createdReservations) {
            createdReservation.setTimeZone(roomReservation.getTimeZone());
        }

        final int originalOccurrenceIndex = roomReservation.getOccurrenceIndex();

        this.calendarServiceWrapper.saveCalendarEvent(reservation, roomReservation,
            originalReservations);

        // If the reservation is now the first in the series, update the parent id.
        if (roomReservation.getOccurrenceIndex() == 1 && originalOccurrenceIndex > 1) {
            roomReservation.setParentId(roomReservation.getReserveId());
        }

        // Save the reservation(s) again to persist the appointment unique id
        // also remove the recurrence if required.
        for (final RoomReservation createdReservation : createdReservations) {
            this.updateAfterSavingCalendarEvent(createdReservation, roomReservation.getUniqueId(),
                roomReservation.getParentId(), roomReservation.getComments());
        }
        DataSetList wrappedConflictDates = null;
        if (recurrence == null) {
            wrappedConflictDates = new DataSetList();
        } else {
            wrappedConflictDates = this.wrapConflictDates(conflictDates);
        }

        this.updateReserveRecordAfterSave(reservation, roomReservation);
        ReservationsContextHelper.ensureResultMessageIsSet();

        return wrappedConflictDates;
    }

    /**
     * Update the unique id for the reservation after saving the calendar event. Remove the
     * recurrence from the stored reservation if the matching created reservation is not recurring.
     * Update the comments of the occurrence to match the given comments.
     *
     * @param createdReservation the created reservation
     * @param uniqueId the new unique id to save in the reservation
     * @param parentId the new parent reservation id
     * @param comments the new comments to set
     */
    private void updateAfterSavingCalendarEvent(final RoomReservation createdReservation,
            final String uniqueId, final Integer parentId, final String comments) {
        final RoomReservation storedReservation =
                this.reservationDataSource.get(createdReservation.getReserveId());

        if ((createdReservation.getParentId() == null || createdReservation.getParentId() == 0)
                && storedReservation.getParentId() != null) {
            // the occurrence was removed from the recurrence so it now has a different unique id
            storedReservation.setUniqueId(createdReservation.getUniqueId());
            ReservationUtils.removeRecurrence(storedReservation);
            this.reservationDataSource.update(storedReservation,
                this.reservationDataSource.get(storedReservation.getReserveId()));
        } else {
            storedReservation.setUniqueId(uniqueId);
            /*
             * If the occurrence was not removed from the recurrence, set its parent id to match the
             * parent id of the first reservation being edited.
             */
            storedReservation.setParentId(parentId);
            storedReservation.setOccurrenceIndex(createdReservation.getOccurrenceIndex());
            storedReservation.setComments(comments);
            this.reservationDataSource.update(storedReservation);
        }
    }

    /**
     * Get the location string for a room reservation.
     *
     * @param reservation the reservation
     * @param roomAllocation the room allocation
     * @return the location string
     */
    public String getLocationString(final DataRecord reservation, final DataRecord roomAllocation) {
        return this.spaceService.getLocationString(this.roomAllocationDataSource
            .convertRecordToObject(roomAllocation).getRoomArrangement());
    }

    /**
     * Cancel single room reservation.
     *
     * @param reservationId reservation id
     * @param comments the comments
     * @param cancelMeeting true to cancel the meeting, false to only remove the location - null is
     *            interpreted as true
     */
    public void cancelRoomReservation(final Integer reservationId, final String comments,
            final Boolean cancelMeeting) {
        RoomReservation roomReservation = null;
        if (reservationId != null && reservationId > 0) {
            // Get the reservation in the building time zone.
            roomReservation = this.reservationDataSource.getActiveReservation(reservationId);
        } else {
            throw new ReservationException(NO_RESERVATION_ID, RoomReservationWfrBase.class,
                this.messagesService.getAdminService());
        }

        if (roomReservation != null) {
            WorkRequestService.setFlagToSendEmailsInSingleJob();
            this.cancelReservationService.cancelReservation(roomReservation, comments);
            try {
                this.calendarServiceWrapper.cancelSingleRoomCalendarEvent(roomReservation, comments,
                    this.updateOtherReservationsInConferenceCall(roomReservation, comments, false),
                    cancelMeeting == null || cancelMeeting);
            } catch (final CalendarException exception) {
                this.calendarServiceWrapper.handleCalendarException(exception, roomReservation,
                    CalendarServiceWrapper.CALENDAR_CANCEL_ERROR, CalendarServiceWrapper.class);
            }
            WorkRequestService.startJobToSendEmailsInSingleJob();
        }
        ReservationsContextHelper.ensureResultMessageIsSet();
    }

    /**
     * Cancel multiple reservations.
     *
     * @param reservations the reservations to cancel
     * @param message the message
     * @param cancelMeetings true to cancel the meetings, false to only remove the location -- null
     *            is interpreted as true
     * @return list of reservation ids that could not be cancelled.
     */
    public List<Integer> cancelMultipleRoomReservations(final DataSetList reservations,
            final String message, final Boolean cancelMeetings) {

        WorkRequestService.setFlagToSendEmailsInSingleJob();
        final List<Integer> failures = new ArrayList<Integer>();
        for (final DataRecord record : reservations.getRecords()) {
            // get the active reservation and all allocations
            final int reservationId = record.getInt(RESERVE_RES_ID);
            final RoomReservation roomReservation = this.reservationDataSource.get(reservationId);

            if (roomReservation == null
                    || Constants.STATUS_REJECTED.equals(roomReservation.getStatus())) {
                failures.add(record.getInt(RESERVE_RES_ID));
            } else if (!Constants.STATUS_CANCELLED.equals(roomReservation.getStatus())) {
                try {
                    this.reservationDataSource.canBeCancelledByCurrentUser(roomReservation);
                } catch (final ReservationException exception) {
                    // this one can't be cancelled, so skip and report
                    failures.add(roomReservation.getReserveId());
                    continue;
                }
                this.cancelReservationService.cancelReservation(roomReservation, message);
                try {
                    this.calendarServiceWrapper.cancelSingleRoomCalendarEvent(roomReservation,
                        message, this.updateOtherReservationsInConferenceCall(roomReservation,
                            message, false),
                        cancelMeetings == null || cancelMeetings);
                } catch (final CalendarException exception) {
                    this.calendarServiceWrapper.handleCalendarException(exception, roomReservation,
                        CalendarServiceWrapper.CALENDAR_CANCEL_ERROR, CalendarServiceWrapper.class);
                }
            }
        }
        WorkRequestService.startJobToSendEmailsInSingleJob();

        ReservationsContextHelper.ensureResultMessageIsSet();
        return failures;
    }

    /**
     * Cancel recurring room reservation.
     *
     * @param reservationId reservation id of the first occurrence in the series to cancel
     * @param comments the comments
     * @param cancelMeeting true to cancel the corresponding meeting, false to remove the location
     *            from it -- null is interpreted as true
     * @return the list of id that failed
     */
    public List<Integer> cancelRecurringRoomReservation(final Integer reservationId,
            final String comments, final Boolean cancelMeeting) {
        final List<Integer> failures = new ArrayList<Integer>();
        if (reservationId != null && reservationId > 0) {
            final RoomReservation reservation = this.reservationDataSource.get(reservationId);

            WorkRequestService.setFlagToSendEmailsInSingleJob();
            final List<List<IReservation>> cancelResult =
                    this.cancelReservationService.cancelRecurringReservation(reservation, comments);
            final List<IReservation> cancelledReservations = cancelResult.get(0);
            final List<IReservation> failedReservations = cancelResult.get(1);

            cancelRecurringSingleRoomCalendarEvent(reservation, comments,
                cancelMeeting == null || cancelMeeting,
                extractRoomReservations(cancelledReservations), failedReservations);
            WorkRequestService.startJobToSendEmailsInSingleJob();

            for (final IReservation failure : failedReservations) {
                failures.add(failure.getReserveId());
            }
        } else {
            throw new ReservationException(NO_RESERVATION_ID, RoomReservationWfrBase.class,
                this.messagesService.getAdminService());
        }
        ReservationsContextHelper.ensureResultMessageIsSet();
        return failures;
    }

    /**
     * Calculate total cost of the reservation.
     *
     * The total cost per reservation is calculated and multiplied by the number of occurrences.
     *
     * @param reservation the reservation.
     * @param roomAllocation the room allocation.
     * @param resources the equipment and services to be reserved
     * @param caterings the catering resources
     * @param numberOfOccurrences the number of occurrences
     * @return total cost of all occurrences
     */
    public Double calculateTotalCost(final DataRecord reservation, final DataRecord roomAllocation,
            final DataSetList resources, final DataSetList caterings,
            final int numberOfOccurrences) {

        final RoomReservation roomReservation =
                this.reservationDataSource.convertRecordToObject(reservation);
        // make sure the date of time values are set to 1899
        roomReservation.setStartTime(TimePeriod.clearDate(roomReservation.getStartTime()));
        roomReservation.setEndTime(TimePeriod.clearDate(roomReservation.getEndTime()));

        // add the room allocation to the reservation
        roomReservation
            .addRoomAllocation(this.roomAllocationDataSource.convertRecordToObject(roomAllocation));

        // add the resources and catering
        this.reservationDataSource.addResourceList(roomReservation, caterings);
        this.reservationDataSource.addResourceList(roomReservation, resources);

        return this.reservationDataSource.calculateCosts(roomReservation) * numberOfOccurrences;
    }

    /**
     * Create a copy of the room reservation.
     *
     * @param reservationId the reservation id
     * @param reservationName the reservation name
     * @param startDate the start date
     * @return new reservation id of the copy
     */
    public Integer copyRoomReservation(final int reservationId, final String reservationName,
            final Date startDate) {
        final RoomReservation sourceReservation =
                this.reservationDataSource.getActiveReservation(reservationId);

        if (sourceReservation == null) {
            // @translatable
            throw new ReservationException("Room reservation has been cancelled or rejected.",
                RoomReservationService.class, this.messagesService.getAdminService());
        }

        if (sourceReservation.getRoomAllocations().isEmpty()) {
            // @translatable
            throw new ReservationException("Room reservation has no room allocated.",
                RoomReservationService.class, this.messagesService.getAdminService());
        }

        final RoomReservation newReservation = new RoomReservation();
        sourceReservation.copyTo(newReservation, true);

        newReservation.setStartDate(startDate);
        newReservation.setEndDate(startDate);
        newReservation.setReservationName(reservationName);
        // when copying the new reservation is always a single regular reservation
        newReservation.setReservationType("regular");
        newReservation.setRecurringRule("");
        newReservation.setParentId(null);
        newReservation.setUniqueId(null);

        String timeZoneId = null;

        // copy room allocations
        for (final RoomAllocation roomAllocation : sourceReservation.getRoomAllocations()) {
            // get the room arrangement
            final RoomArrangement roomArrangement = roomAllocation.getRoomArrangement();
            final RoomAllocation roomAllocationCopy =
                    new RoomAllocation(roomArrangement, newReservation);
            roomAllocationCopy.setAttendeesInRoom(roomAllocation.getAttendeesInRoom());
            newReservation.addRoomAllocation(roomAllocationCopy);

            // determine the time zone of the building and set the local time zone
            final String buildingId = roomAllocation.getBlId();
            timeZoneId = TimeZoneConverter.getTimeZoneIdForBuilding(buildingId);
        }

        // copy resource allocations
        ReservationServiceHelper.copyResourceAllocations(sourceReservation, newReservation);

        // availability will be checked
        WorkRequestService.setFlagToSendEmailsInSingleJob();
        this.reservationService.saveReservation(newReservation);
        WorkRequestService.startJobToSendEmailsInSingleJob();

        // set the time zone to match the building
        newReservation.setTimeZone(timeZoneId);
        this.calendarServiceWrapper.saveCopiedCalendarEvent(newReservation);

        // Set the unique id of the new reservation.
        final RoomReservation storedReservation =
                this.reservationDataSource.get(newReservation.getReserveId());
        storedReservation.setUniqueId(newReservation.getUniqueId());
        this.reservationDataSource.update(storedReservation);

        ReservationsContextHelper.ensureResultMessageIsSet();
        return newReservation.getReserveId();
    }

    /**
     * Get the attendees response status for the reservation with given id.
     *
     * @param reservationId the reservation id
     * @return response status array
     */
    public List<JSONObject> getAttendeesResponseStatus(final int reservationId) {
        final List<JSONObject> results = new ArrayList<JSONObject>();

        final RoomReservation reservation = this.reservationDataSource.get(reservationId);

        try {
            final List<AttendeeResponseStatus> responses =
                    this.attendeeService.getAttendeesResponseStatus(reservation);
            for (final AttendeeResponseStatus response : responses) {
                final JSONObject result = new JSONObject();
                result.put("name", response.getName());
                result.put("email", response.getEmail());
                result.put("response", response.getResponseStatus().toString());
                results.add(result);
            }
        } catch (final CalendarException exception) {
            this.logger.warn("Error retrieving attendee response status", exception);
            ReservationsContextHelper.appendResultError(exception.getPattern());
        }
        return results;
    }

    /**
     * Get the current local date/time for the given buildings.
     *
     * @param buildingIds list of building IDs
     * @return JSON mapping of each building id to the current date/time in that building
     */
    public JSONObject getCurrentLocalDateTime(final List<String> buildingIds) {
        return TimeZoneConverter.getCurrentLocalDateTime(buildingIds);
    }

    /**
     * Set the Attendee Service.
     *
     * @param attendeeService the attendee service
     */
    public void setAttendeeService(final IAttendeeService attendeeService) {
        this.attendeeService = attendeeService;
    }

}
