package com.archibus.app.reservation.service;

import java.util.*;

import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.*;
import com.archibus.app.reservation.exchange.service.ExchangeCalendarService;
import com.archibus.app.reservation.service.helpers.ConferenceCallMessagesService;
import com.archibus.app.reservation.util.*;
import com.archibus.datasource.data.DataRecord;
import com.archibus.service.remoting.AdminService;
import com.archibus.utility.*;

/**
 * Provides an interface to the configurable calendar service, implementing features that are shared
 * across calendar implementations.
 * <p>
 *
 * Used by RoomReservationService to interface with the calendar service. Managed by Spring, has
 * prototype scope. Configured in reservation-services.xml.
 *
 * @author Yorik Gerlo
 * @since 22.1
 */
public class CalendarServiceWrapper extends RoomReservationServiceBase {

    /** Error message displayed when a calendar cancel failed. */
    // @translatable
    public static final String CALENDAR_CANCEL_ERROR =
            "Reservation [{0}] cancelled but an error occurred updating the requestor''s calendar and notifying attendees.";

    /** Error message displayed when a calendar copy failed. */
    // @translatable
    private static final String CALENDAR_COPY_ERROR =
            "Reservation copied but an error occurred updating the requestor''s calendar and notifying attendees.";

    /** Error message displayed when a calendar create failed. */
    // @translatable
    private static final String CALENDAR_CREATE_ERROR =
            "Reservation created but an error occurred updating the requestor''s calendar and notifying attendees.";

    /** Error message displayed when a calendar cancel failed. */
    // @translatable
    private static final String CALENDAR_CANCEL_RECURRING_ERROR =
            "Recurring reservation cancelled but an error occurred updating the requestor''s calendar and notifying attendees.";

    /** Error message displayed when a calendar update failed. */
    // @translatable
    private static final String CALENDAR_UPDATE_ERROR =
            "Reservation [{0}] updated but an error occurred updating the requestor''s calendar and notifying attendees.";

    /**
     * Message displayed to the user when the meeting series is not found while trying to update a
     * single occurrence.
     */
    private static final String SERIES_NOT_FOUND_ONE_OCCURRENCE =
            "The recurring meeting linked to reservation {0} was not found on the calendar. The reservation was removed from the recurrence and linked to a new single meeting.";

    /**
     * Message displayed to the user when the meeting series is not found while trying to update a
     * number of occurrences.
     */
    private static final String SERIES_NOT_FOUND_NEW_SERIES =
            "The recurring meeting linked to reservation {0} was not found on the calendar. The reservations were linked to a new recurring meeting.";

    /** The Constant RESERVE_RES_ID. */
    private static final String RESERVE_RES_ID = "reserve.res_id";

    /** Whitespace between two parts of error message. */
    private static final String SPACE = " ";

    /** Administration service for localization. */
    private AdminService adminService;

    /** The reservation messages service. */
    private ConferenceCallMessagesService messagesService;

    /** Service reference for disconnecting calendar events from reservations. */
    private ICalendarDisconnectService calendarDisconnectService;

    /**
     * Set the administration service.
     * @param adminService the admin service
     */
    public void setAdminService(final AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * Get the administration service.
     * @return the admin service
     */
    public AdminService getAdminService() {
        return this.adminService;
    }

    /**
     * Set the Conference Call messages service.
     *
     * @param messagesService the messages service to set
     */
    public void setMessagesService(final ConferenceCallMessagesService messagesService) {
        this.messagesService = messagesService;
    }

    /**
     * Save a new calendar event for a copied reservation.
     *
     * @param reservation the reservation to create a calendar event for
     */
    public void saveCopiedCalendarEvent(final RoomReservation reservation) {
        // for a new reservation create the appointment
        try {
            this.calendarService.createAppointment(reservation);
        } catch (final CalendarException exception) {
            handleCalendarException(exception, reservation, CALENDAR_COPY_ERROR,
                CalendarServiceWrapper.class);
        }
    }

    /**
     * Save the reservation as a calendar event using the Calendar Service.
     *
     * @param reservation the DataRecord representing the reservation
     * @param roomReservation the object representing the reservation
     * @param originalReservations the original reservations
     */
    public void saveCalendarEvent(final DataRecord reservation,
            final RoomReservation roomReservation,
            final List<RoomReservation> originalReservations) {
        // check new using the incoming reservation data record
        if (reservation.isNew() || reservation.getInt(RESERVE_RES_ID) == 0) {
            roomReservation.setUniqueId(null);
            // for a new reservation create the appointment
            try {
                this.calendarService.createAppointment(roomReservation);
            } catch (final CalendarException exception) {
                handleCalendarException(exception, roomReservation, CALENDAR_CREATE_ERROR,
                    CalendarServiceWrapper.class);
            }
        } else if (roomReservation.getParentId() == null || roomReservation.getParentId() == 0) {
            // Update a regular reservation.
            try {
                this.calendarService.updateAppointment(roomReservation);
            } catch (final CalendarException exception) {
                handleCalendarException(exception, roomReservation, CALENDAR_UPDATE_ERROR,
                    CalendarServiceWrapper.class);
            }
        } else {
            updateRecurringCalendarEvent(roomReservation, originalReservations);
        }
    }

    /**
     * Update a recurring calendar event.
     *
     * @param roomReservation the first reservation occurrence being edited
     * @param originalReservations the corresponding original reservations
     */
    private void updateRecurringCalendarEvent(final RoomReservation roomReservation,
            final List<RoomReservation> originalReservations) {
        if (isFullRemainingSeries(roomReservation)) {
            /*
             * Use updateAppointmentSeries if (1) all reservations are updated or (2) all future
             * reservations are updated. (1) is cheaper to check than (2)
             */
            try {
                this.calendarService.updateAppointmentSeries(roomReservation, originalReservations);
            } catch (final SeriesNotFoundException exception) {
                this.logger.debug("Cannot update full series.", exception);
                this.recreateRecurringCalendarEvent(roomReservation, originalReservations);
            } catch (final CalendarException exception) {
                handleCalendarException(exception, roomReservation, CALENDAR_UPDATE_ERROR,
                    CalendarServiceWrapper.class);
            }
        } else {
            // Update 1 or more occurrences of a recurring reservation.
            final List<RoomReservation> createdReservations =
                    roomReservation.getCreatedReservations();

            for (int index = 0; index < originalReservations.size(); ++index) {
                final RoomReservation createdReservation = createdReservations.get(index);
                createdReservation.setTimeZone(roomReservation.getTimeZone());
                try {
                    this.calendarService.updateAppointmentOccurrence(createdReservation,
                        originalReservations.get(index));
                } catch (final SeriesNotFoundException exception) {
                    this.logger.debug("Cannot update occurrence: series not found.", exception);
                    this.recreateRecurringCalendarEvent(roomReservation, originalReservations);
                    break;
                } catch (final CalendarException exception) {
                    handleCalendarException(exception, createdReservation, CALENDAR_UPDATE_ERROR,
                        CalendarServiceWrapper.class);
                }
            }
        }
    }

    /**
     * Create a new (recurring) calendar event for the given reservation series. If only one
     * occurrence is edited, a new single meeting is created. If multiple occurrences are edited, a
     * new recurring meeting is created and individual occurrences are updated / cancelled to match
     * the reservations if required.
     *
     * @param reservation the primary reservation of the first occurrence being edited
     * @param originalReservations the original reservation for each occurrence
     */
    private void recreateRecurringCalendarEvent(final RoomReservation reservation,
            final List<RoomReservation> originalReservations) {
        final List<RoomReservation> createdReservations = reservation.getCreatedReservations();
        if (createdReservations.size() == 1) {
            // remove the reservation from the recurrence, create a new single meeting
            this.logger
                .debug("Create a new single meeting for reservation " + reservation.getReserveId());
            ReservationUtils.removeRecurrence(reservation);
            reservation.setUniqueId("");
            this.calendarService.updateAppointment(reservation);

            final String localizedMessage = ReservationsContextHelper.localizeString(SERIES_NOT_FOUND_ONE_OCCURRENCE,
                    ExchangeCalendarService.class, this.adminService, reservation.getReserveId());
            ReservationsContextHelper.appendResultError(localizedMessage);
        } else {
            /*
             * Create a new recurring meeting and update each occurrence to match the reservations.
             * Also cancel the occurrences that don't correspond to a reservation.
             */
            this.logger.debug("Create a new recurring meeting starting from reservation "
                    + reservation.getReserveId());
            final RoomReservation firstReservation = createdReservations.get(0);
            final RoomReservation lastReservation =
                    createdReservations.get(createdReservations.size() - 1);
            // set the recurrence pattern
            final Recurrence recurrence = RecurrenceParser.parseRecurrence(
                firstReservation.getStartDate(), null, reservation.getRecurringRule());
            // Use occurrence index to include cancelled occurrences in the occurrence count.
            recurrence.setNumberOfOccurrences(
                lastReservation.getOccurrenceIndex() - firstReservation.getOccurrenceIndex() + 1);
            reservation.setRecurrence(recurrence);
            reservation.setUniqueId("");

            // the difference between the original occurrence index and the new occurrence index
            final int occurrenceDelta = reservation.getOccurrenceIndex() - 1;

            /*
             * Map the new occurrence index to the reservation date to determine whether we got the
             * correct start date for the recurrence pattern.
             */
            final Map<Integer, Date> originalDates = new HashMap<Integer, Date>();
            final Map<Integer, Date> actualDates = new HashMap<Integer, Date>();
            // Update the created reservations to the new occurrence index
            for (final RoomReservation occurrence : createdReservations) {
                occurrence.setOccurrenceIndex(occurrence.getOccurrenceIndex() - occurrenceDelta);
                if (occurrence.getRecurringDateModified() == 0) {
                    originalDates.put(occurrence.getOccurrenceIndex(),
                        occurrence.getStartDateTime());
                }
                actualDates.put(occurrence.getOccurrenceIndex(), occurrence.getStartDateTime());
            }
            // also update the primary reservation occurrence index
            reservation.setOccurrenceIndex(1);

            final List<Date> recurrenceDates =
                    RecurrenceService.getFixedDateList(recurrence, originalDates);
            // add the original occurrence dates to the map for reservations having a modified date
            for (int index = 0; index < recurrenceDates.size(); ++index) {
                originalDates.put(index + 1,
                    new java.sql.Date(recurrenceDates.get(index).getTime()));
            }

            // modify the created reservations objects to match the calculated recurrence dates
            for (final RoomReservation createdReservation : createdReservations) {
                createdReservation
                    .setStartDate(originalDates.get(createdReservation.getOccurrenceIndex()));
                createdReservation.setEndDate(createdReservation.getStartDate());
            }

            // Create the series based on the original dates
            final String uniqueId = this.calendarService.createAppointment(reservation);
            final String timeZone = reservation.getTimeZone();

            // change the created reservations dates back to the actual dates
            for (final RoomReservation createdReservation : createdReservations) {
                createdReservation
                    .setStartDate(actualDates.get(createdReservation.getOccurrenceIndex()));
                createdReservation.setEndDate(createdReservation.getStartDate());
            }

            this.updateAllOccurrences(createdReservations, originalReservations, uniqueId,
                timeZone);
            final String localizedMessage = ReservationsContextHelper.localizeString(SERIES_NOT_FOUND_NEW_SERIES,
                    ExchangeCalendarService.class, this.adminService, reservation.getReserveId());
            ReservationsContextHelper.appendResultError(localizedMessage);
        }
    }

    /**
     * Update the calendar for all provided occurrences, one by one.
     *
     * @param createdReservations the occurrences just created
     * @param originalReservations original version of the occurrences
     * @param uniqueId the current unique id of the series
     * @param timeZone the local time zone of the occurrences
     */
    private void updateAllOccurrences(final List<RoomReservation> createdReservations,
            final List<RoomReservation> originalReservations, final String uniqueId,
            final String timeZone) {
        int newOccurrenceIndex = 0;
        for (int index = 0; index < createdReservations.size(); ++index) {
            final RoomReservation occurrence = createdReservations.get(index);
            final int occurrenceIndexToUpdate = occurrence.getOccurrenceIndex();
            occurrence.setUniqueId(uniqueId);
            ++newOccurrenceIndex;
            // cancel all meeting occurrences in between that don't correspond to a reservation
            while (newOccurrenceIndex < occurrenceIndexToUpdate) {
                // cancel the occurrence with newOccurrenceIndex
                occurrence.setOccurrenceIndex(newOccurrenceIndex);
                this.calendarService.cancelAppointmentOccurrence(occurrence, null, false);

                // increment new occurrence index for each cancelled occurrence
                ++newOccurrenceIndex;
            }
            if (StringUtil.isNullOrEmpty(occurrence.getTimeZone())) {
                /*
                 * Only set the time zone if it was not set yet. The ConferenceCall WFR does set the
                 * time zone in each occurrence when updating a single location of a recurring
                 * conference call, because the primary room of each occurrence could have a
                 * different time zone for each occurrence.
                 */
                occurrence.setTimeZone(timeZone);
            }
            occurrence.setOccurrenceIndex(newOccurrenceIndex);

            /*
             * Modify the unique id and occurrence index of the original reservation. Specifically
             * the occurrence index is used to bind to the correct occurrence.
             */
            final RoomReservation originalOccurrence = originalReservations.get(index);
            originalOccurrence.setOccurrenceIndex(occurrence.getOccurrenceIndex());
            originalOccurrence.setUniqueId(occurrence.getUniqueId());

            // update the occurrence on the calendar if required
            this.calendarService.updateAppointmentOccurrence(occurrence, originalOccurrence);
        }
    }

    /**
     * Check whether we're updating all remaining occurrences of a recurring appointment. Returns
     * false if only one occurrence is being updated or if there's another occurrence scheduled for
     * today or later that occurs before the first occurrence being edited.
     *
     * @param roomReservation the first occurrence being edited, including a list of created
     *            reservations
     * @return true if all remaining occurrences are being edited, false otherwise
     */
    private boolean isFullRemainingSeries(final RoomReservation roomReservation) {
        /*
         * Never consider a single occurrence as being the full series. When only a single
         * occurrence must be updated, we shouldn't bother doing any further checks.
         */
        boolean isFullSeries = roomReservation.getCreatedReservations().size() > 1;
        if (isFullSeries && !roomReservation.getReserveId().equals(roomReservation.getParentId())) {
            /*
             * We're not starting from the first occurrence, so check for other occurrences between
             * today and the first reservation being edited. Only the first reservation being edited
             * should be returned by this query. If more than one reservation is returned, we're not
             * editing the full series.
             */
            final List<RoomReservation> reservationsInBetween =
                    this.reservationDataSource.getByParentId(
                        roomReservation.getParentId(), LocalDateTimeUtil
                            .currentLocalDateForTimeZone(roomReservation.getTimeZone()),
                    roomReservation.getEndDate(), true);
            isFullSeries = reservationsInBetween.size() == 1;
        }
        return isFullSeries;
    }

    /**lbnl - Brent Hopkins - send a cancel email to any attendees removed from a reservation **/
    public final void lbnlHelpCancelEmails(RoomReservation reservation, RoomReservation origReservation, Boolean allRecurrences){

        if(!origReservation.getComments().equals(reservation.getComments()))
            this.calendarService.lbnlCancelCalendarEventHelp(reservation, origReservation, allRecurrences, "Cancellation comment: " + reservation.getComments());
        else this.calendarService.lbnlCancelCalendarEventHelp(reservation, origReservation, allRecurrences, "");
    }

    /**
     * Handle an exception that occurred interacting with the calendar service.
     *
     * @param exception the calendar exception
     * @param reservation the reservation for which we tried to update the calendar event
     * @param messagePattern translatable pattern for the error message to report
     * @param clazz class where the message pattern is defined
     */
    public void handleCalendarException(final CalendarException exception,
            final IReservation reservation, final String messagePattern, final Class<?> clazz) {
        // Do not block the workflow, only report the error.
        final String localizedMessage = ReservationsContextHelper.localizeString(messagePattern, clazz,
                this.adminService, reservation.getReserveId());
        this.logger.warn(localizedMessage, exception);
        ReservationsContextHelper
            .appendResultError(localizedMessage + SPACE + exception.getPattern());
    }

    /**
     * Cancel a single calendar event.
     *
     * @param roomReservation the room reservation linked to the calendar event
     * @param comments the comments to send with the cancellation
     * @param cancelMeeting whether to really cancel the meeting or to disconnect it from the reservation
     */
    public void cancelCalendarEvent(final IReservation roomReservation, final String comments,
            final boolean cancelMeeting) {
        final Integer parentId = roomReservation.getParentId();
        if (cancelMeeting) {
            if (parentId != null && parentId > 0) {
                // Cancel a single occurrence.
                this.calendarService.cancelAppointmentOccurrence(roomReservation, comments, true);
            } else {
                this.calendarService.cancelAppointment(roomReservation, comments, true);
            }
        } else {
            final Integer conferenceId = roomReservation.getConferenceId();
            // first check if we must strip the locations template
            if (conferenceId != null && conferenceId > 0) {
                roomReservation.setComments(this.messagesService
                        .stripConferenceCallLocations(roomReservation.getEmail(), roomReservation.getComments()));
            }
            if (parentId != null && parentId > 0) {
                // also check for stripping the conflicts description (only when recurring)
                roomReservation.setComments(this.messagesService
                        .stripConflictsDescription(roomReservation.getEmail(), roomReservation.getComments()));
                // cancel a single occurrence
                this.calendarDisconnectService.disconnectAppointmentOccurrence(roomReservation, comments, true);
            } else {
                this.calendarDisconnectService.disconnectAppointment(roomReservation, comments, true);
            }
        }
    }

    /**
     * Cancel the recurring calendar event linked to this reservation.
     *
     * @param reservation the reservation that was cancelled
     * @param comments the comments to include in the cancellation of the calendar event
     * @param cancelMeeting whether to cancel the meeting or only remove the location
     */
    public void cancelRecurringCalendarEvent(final RoomReservation reservation,
            final String comments, final boolean cancelMeeting) {
        try {
            if (cancelMeeting) {
                this.calendarService.cancelAppointment(reservation, comments, true);
            } else {
                final Integer conferenceId = reservation.getConferenceId();
                final boolean isConferenceCall = conferenceId != null && conferenceId > 0;
                // first check if we must strip the locations template
                if (isConferenceCall) {
                    reservation.setComments(this.messagesService
                            .stripConferenceCallLocations(reservation.getEmail(), reservation.getComments()));
                }
                reservation.setComments(this.messagesService.stripConflictsDescription(reservation.getEmail(),
                        reservation.getComments()));

                // also remove the locations template / conflicts description from each occurrence
                for (final RoomReservation occurrence : reservation.getCreatedReservations()) {
                    if (isConferenceCall) {
                        occurrence.setComments(this.messagesService
                                .stripConferenceCallLocations(occurrence.getEmail(), occurrence.getComments()));
                    }
                    occurrence.setComments(this.messagesService.stripConflictsDescription(occurrence.getEmail(),
                            occurrence.getComments()));
                }

                this.calendarDisconnectService.disconnectAppointmentSeries(reservation, comments, true);
            }
        } catch (final CalendarException exception) {
            this.handleCalendarException(exception, reservation, CALENDAR_CANCEL_RECURRING_ERROR,
                CalendarServiceWrapper.class);
        }
    }

    /**
     * Cancel the calendar event for a single room reservation on a single date. This checks whether
     * the room reservation is part of a conference call. The calendar event is only cancelled if no
     * other rooms are still active.
     *
     * @param reservation the room reservation to cancel
     * @param comments the cancel comments
     * @param confCallReservations remaining active reservations in the conference call (if any)
     * @param cancelMeeting whether to cancel the meeting if there are no more rooms active
     */
    public void cancelSingleRoomCalendarEvent(final IReservation reservation, final String comments,
            final List<RoomReservation> confCallReservations, final boolean cancelMeeting) {
        if (confCallReservations == null || confCallReservations.isEmpty()) {
            this.cancelCalendarEvent(reservation, comments, cancelMeeting);
        } else {
            // the primary reservation is already prepared for updating the calendar
            final RoomReservation primaryReservation = confCallReservations.get(0);
            if (primaryReservation.getParentId() == null || primaryReservation.getParentId() == 0) {
                // Update a regular reservation.
                this.calendarService.updateAppointment(primaryReservation);
            } else {
                // Update an occurrence of a recurring reservation.
                this.calendarService.updateAppointmentOccurrence(primaryReservation,
                    primaryReservation);
            }

            /*
             * The calendar service might have generated a new appointment, so save the conference
             * call reservations again to persist the appointment unique id.
             */
            this.reservationDataSource.updateCommonIdentifiers(primaryReservation,
                confCallReservations, null, false);
        }
    }

    /**
     * Sets the calendar disconnect service.
     *
     * @param calendarDisconnectService the new calendar disconnect service
     */
    public void setCalendarDisconnectService(final ICalendarDisconnectService calendarDisconnectService) {
        this.calendarDisconnectService = calendarDisconnectService;
    }

}
