package com.archibus.app.reservation.exchange.service;

import java.util.*;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.Recurrence;
import com.archibus.app.reservation.exchange.domain.UpdateType;
import com.archibus.app.reservation.exchange.util.*;
import com.archibus.app.reservation.service.ISpaceService;
import com.archibus.app.reservation.util.ReservationUtils;
import com.archibus.utility.StringUtil;

import microsoft.exchange.webservices.data.*;

/**
 * Helper class providing functionality for Exchange appointment handling.
 *
 * Managed by Spring.
 *
 * @author Yorik Gerlo
 * @since 21.2
 */
public class AppointmentHelper extends AbstractAppointmentHelper {

    /** Error message when setting the appointment properties fails. */
    // @translatable
    private static final String ERROR_SETTING_PROPERTIES =
            "Error setting appointment properties. Please refer to archibus.log for details";

    /** Error message indicating an appointment could not be updated. */
    // @translatable
    private static final String ERROR_UPDATING_APPOINTMENT =
            "Error updating appointment. Please refer to archibus.log for details";

    /** The time zone mapper. */
    private AppointmentTimeZoneMapper timeZoneMapper;

    /** The recurrence pattern converter. */
    private ExchangeRecurrenceConverter recurrenceConverter;

    /** The space service. */
    private ISpaceService spaceService;

    /**
     * Save a new appointment to the Exchange calendar and update the unique id in the reservation
     * object.
     *
     * @param reservation the corresponding reservation to set the unique id
     * @param appointment the new appointment to save
     */
    public void saveNewAppointment(final IReservation reservation, final Appointment appointment) {
        try {
            appointment.save(SendInvitationsMode.SendOnlyToAll);
            appointment.load(PropertySet.FirstClassProperties);
            reservation.setUniqueId(appointment.getICalUid());
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API method
            // throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            // @translatable
            throw new CalendarException(
                "Error creating appointment. Please refer to archibus.log for details", exception,
                AppointmentHelper.class, this.getServiceHelper().getAdminService());
        }
    }

    /**
     * Save changes to an existing appointment as indicated by the send mode.
     *
     * @param appointment the appointment to save
     * @param sendMode indicates to whom updates should be sent
     */
    public void saveUpdatedAppointment(final Appointment appointment,
            final SendInvitationsOrCancellationsMode sendMode) {
        try {
            appointment.update(ConflictResolutionMode.AlwaysOverwrite, sendMode);
        } catch (final ServiceResponseException exception) {
            if (ServiceError.ErrorOccurrenceCrossingBoundary.equals(exception.getErrorCode())) {
                // @translatable
                throw new ReservationException("Occurrence cannot skip over another occurrence.",
                    ExchangeCalendarService.class, this.getServiceHelper().getAdminService());
            } else {
                throw new CalendarException(ERROR_UPDATING_APPOINTMENT, exception,
                    ExchangeCalendarService.class, this.getServiceHelper().getAdminService());
            }
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API method
            // throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            // @translatable
            throw new CalendarException(ERROR_UPDATING_APPOINTMENT, exception,
                AppointmentHelper.class, this.getServiceHelper().getAdminService());
        }
    }

    /**
     * Determine the correct mode for updating an appointment or meeting.
     *
     * @param appointment the appointment to update
     * @param updateType the update type
     * @return the send invitations mode
     * @throws ServiceLocalException when a local error occurs in EWS
     */
    public SendInvitationsOrCancellationsMode getSendMode(final Appointment appointment,
            final UpdateType updateType) throws ServiceLocalException {
        // send to none if the appointment is not a meeting, otherwise it's changed to a meeting
        SendInvitationsOrCancellationsMode mode = SendInvitationsOrCancellationsMode.SendToNone;
        if (UpdateType.ATTENDEES_ONLY.equals(updateType)) {
            mode = SendInvitationsOrCancellationsMode.SendOnlyToChanged;
        } else if (appointment.getIsMeeting()) {
            mode = SendInvitationsOrCancellationsMode.SendOnlyToAll;
        }
        return mode;
    }

    /**
     * Get the send mode for saving a room conflict.
     *
     * @param appointment the appointment occurrence
     * @param requestorEmail email address of the reservation requestor
     * @return the send mode
     * @throws ServiceLocalException when a local error occurs in EWS
     */
    public SendInvitationsOrCancellationsMode getSendModeForRoomConflict(
            final Appointment appointment, final String requestorEmail)
                    throws ServiceLocalException {
        /*
         * KB 3048494 don't send conflicted occurrences to the attendees, unless the reservation
         * requestor is not the meeting organizer in Exchange.
         */
        SendInvitationsOrCancellationsMode sendMode;
        if (appointment.getService().getImpersonatedUserId().getId().equals(requestorEmail)) {
            sendMode = SendInvitationsOrCancellationsMode.SendToNone;
        } else {
            sendMode = this.getSendMode(appointment, UpdateType.FULL);
        }
        return sendMode;
    }

    /**
     * Update the appointment to reflect the properties of the reservation. The update is
     * immediately applied in Exchange.
     *
     * @param reservation the calendar event
     * @param appointment the appointment to update
     */
    public void updateAppointment(final IReservation reservation, final Appointment appointment) {
        TimePeriod timePeriodUtc = null;
        String timeZone = null;
        if (reservation instanceof RoomReservation) {
            final RoomReservation roomReservation = (RoomReservation) reservation;
            final String location = this.spaceService.getLocationString(roomReservation);
            try {
                appointment.setLocation(location);
                // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification:
                // third-party API method throws a checked Exception, which needs to be
                // wrapped in ExceptionBase.
            } catch (final Exception exception) {
                // CHECKSTYLE:ON
                // @translatable
                throw new CalendarException(
                    "Error setting appointment location. Please refer to archibus.log for details",
                    exception, AppointmentHelper.class, this.getServiceHelper().getAdminService());
            }
            timePeriodUtc = ReservationUtils.getTimePeriodInTimeZone(roomReservation,
                Constants.TIMEZONE_UTC);

            timeZone = roomReservation.getRequestedTimeZone();
        } else {
            timePeriodUtc = reservation.getTimePeriod();
        }

        // KB 3045785 if the user didn't request a specific time zone, set the local time zone.
        if (StringUtil.isNullOrEmpty(timeZone)) {
            timeZone = reservation.getTimeZone();
        }

        try {
            appointment.setSubject(reservation.getReservationName());
            appointment.setBody(ExchangeObjectHelper.newMessageBody(reservation));
            appointment.setStart(timePeriodUtc.getStartDateTime());
            appointment.setEnd(timePeriodUtc.getEndDateTime());
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification:
            // third-party API method throws a checked Exception, which needs to be
            // wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            throw new CalendarException(ERROR_SETTING_PROPERTIES, exception,
                AppointmentHelper.class, this.getServiceHelper().getAdminService());
        }
        AttendeesHelper.setAttendees(reservation, appointment,
            this.getServiceHelper().getResourceAccount(),
            this.getServiceHelper().getOrganizerAccount());

        try {
            checkSetTimeZone(appointment, reservation, timeZone);

            // Set the reservation properties and recurrence.
            final Recurrence recurrence = reservation.getRecurrence();
            if (recurrence == null) {
                setSingleReservationId(reservation, appointment);
            } else {
                if (appointment.isNew()) {
                    // Only set the recurrence pattern for new appointments.
                    // We do not support changing the recurrence pattern from Web Central.
                    this.recurrenceConverter.setRecurrence(appointment, recurrence);

                    this.getAppointmentPropertiesHelper().setRecurringReservationIds(appointment,
                        (RoomReservation) reservation);
                }
                this.getAppointmentPropertiesHelper().removeReservationId(appointment);
            }

            if (appointment.isNew()) {
                this.saveNewAppointment(reservation, appointment);
            } else {
                this.saveUpdatedAppointment(appointment,
                    this.getSendMode(appointment, UpdateType.FULL));
            }
        } catch (final ServiceLocalException exception) {
            throw new CalendarException(ERROR_SETTING_PROPERTIES, exception,
                AppointmentHelper.class, this.getServiceHelper().getAdminService());
        }
    }

    /**
     * Set the time zone in the appointment if required.
     *
     * @param appointment the appointment
     * @param reservation the reservation
     * @param timeZone the time zone to set if required
     * @throws ServiceLocalException when setting the time zone failed
     */
    private void checkSetTimeZone(final Appointment appointment, final IReservation reservation,
            final String timeZone) throws ServiceLocalException {
        /*
         * KB 3045101 don't modify the appointment time zone for individual meeting occurrences. On
         * the other hand, including the time zone is required for Exchange 2007 API.
         */
        /*
         * KB 3049352 always indicate the time zone when editing a recurrence master, to ensure DST
         * is applied correctly.
         */
        boolean timeZoneSet = false;
        if (appointment.getService().getRequestedServerVersion()
            .equals(ExchangeVersion.Exchange2007_SP1) || appointment.isNew()
                || StringUtil.isNullOrEmpty(reservation.getRecurringRule())
                || AppointmentType.RecurringMaster.equals(appointment.getAppointmentType())) {
            /*
             * Get the correct Windows Time Zone ID, either from the time zone requested
             * specifically by the user, or from the reservation's local time zone.
             */
            this.timeZoneMapper.setTimeZone(appointment, timeZone);
            timeZoneSet = true;
        }

        /*
         * KB 3049345 when we change the time zone for a recurring master, also reset the end date
         * to avoid removing the last occurrence.
         */
        if (timeZoneSet && !appointment.isNew()
                && AppointmentType.RecurringMaster.equals(appointment.getAppointmentType())) {
            final Date endDate = appointment.getRecurrence().getEndDate();
            if (endDate != null) {
                /*
                 * If the original time zone matches the WebC server's default time zone, the value
                 * would match the previous value and be ignored. Add 1 minute to avoid this and
                 * ensure the end date is sent to Exchange.
                 */
                final Calendar calendar = Calendar.getInstance();
                calendar.setTime(endDate);
                calendar.add(Calendar.MINUTE, 1);
                appointment.getRecurrence().setEndDate(calendar.getTime());
            }
        }
    }

    /**
     * Set the single reservation id in the appointment.
     *
     * @param reservation the reservation
     * @param appointment the appointment
     * @throws ServiceLocalException when properties of the appointment cannot be accessed
     */
    private void setSingleReservationId(final IReservation reservation,
            final Appointment appointment) throws ServiceLocalException {
        if (reservation.getReserveId() == null) {
            // @translatable
            throw new ReservationException("A non-recurring appointment needs one reservation id",
                AppointmentHelper.class, this.getServiceHelper().getAdminService());
        } else if (appointment.isNew()
                || !AppointmentType.RecurringMaster.equals(appointment.getAppointmentType())) {
            // never set a single reservation id in a recurrence master appointment
            if (reservation.getConferenceId() == null || reservation.getConferenceId() == 0) {
                this.getAppointmentPropertiesHelper().setReservationId(appointment,
                    reservation.getReserveId());
            } else {
                // Set the conference id for conference call reservations
                this.getAppointmentPropertiesHelper().setReservationId(appointment,
                    reservation.getConferenceId());
            }
        }
    }

    /**
     * Update the given appointment if its subject, location, body or attendees don't match the
     * reservation. Only send invitations to added or deleted attendees if only the attendees are
     * different.
     *
     * @param reservation the primary reservation corresponding to the master time period
     * @param event the calendar event corresponding to the appointment
     * @param appointment the Exchange master appointment
     * @return which type of update was applied
     * @throws ServiceLocalException when a local error occurred in the EWS library
     */
    public UpdateType updateAppointmentExceptTime(final IReservation reservation,
            final ICalendarEvent event, final Appointment appointment)
                    throws ServiceLocalException {
        // the series location and time matches, check whether other changes are required
        final boolean subjectLocationBodyDifferent =
                this.updateSubjectLocationBody(reservation, event, appointment);

        final boolean attendeesDifferent = !AppointmentEquivalenceChecker
            .compareToReservationAttendees(reservation, event.getEmailAddresses());
        if (attendeesDifferent) {
            AttendeesHelper.setAttendees(reservation, appointment,
                this.getServiceHelper().getResourceAccount(),
                this.getServiceHelper().getOrganizerAccount());
        }
        UpdateType updateType = UpdateType.NONE;
        if (subjectLocationBodyDifferent) {
            updateType = UpdateType.SUBJECT_LOCATION_BODY;
        } else if (attendeesDifferent) {
            updateType = UpdateType.ATTENDEES_ONLY;
        }
        if (!UpdateType.NONE.equals(updateType)) {
            this.saveUpdatedAppointment(appointment, this.getSendMode(appointment, updateType));
        }
        return updateType;
    }

    /**
     * Disconnect the appointment from the given reservation.
     * @param reservation the reservation
     * @param event calendar event corresponding to the appointment
     * @param appointment the appointment to disconnect
     * @return the update type required to persist the changes (can be NONE or SUBJECT_LOCATION_BODY)
     * @throws ServiceLocalException when a local error occurs in the EWS library
     */
    public UpdateType disconnectAppointment(final IReservation reservation, final ICalendarEvent event,
            final Appointment appointment) throws ServiceLocalException {
        UpdateType updateType = UpdateType.NONE;
        try {
            if (!StringUtil.isNullOrEmpty(appointment.getLocation())) {
                appointment.setLocation("");
                updateType = UpdateType.SUBJECT_LOCATION_BODY;
            }
            if (!AppointmentEquivalenceChecker.compareBody(reservation, event)) {
                appointment.setBody(ExchangeObjectHelper.newMessageBody(reservation));
                updateType = UpdateType.SUBJECT_LOCATION_BODY;
            }
            if (this.getAppointmentPropertiesHelper().removeReservationId(appointment)) {
                updateType = UpdateType.SUBJECT_LOCATION_BODY;
            }
            if (this.getAppointmentPropertiesHelper().removeRecurringReservationIds(appointment)) {
                updateType = UpdateType.SUBJECT_LOCATION_BODY;
            }

            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API method
            // throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            // @translatable
            throw new CalendarException(
                "Error updating location or body of the appointment. Please refer to archibus.log for details",
                exception, AppointmentHelper.class, this.getServiceHelper().getAdminService());
        }

        return updateType;
    }

    /**
     * Update the subject, body and location of the meeting.
     *
     * @param reservation the reservation
     * @param event the calendar event corresponding to the appointment
     * @param appointment the actual appointment to apply changes to
     * @return true if changes were applied, false if all was equivalent
     */
    private boolean updateSubjectLocationBody(final IReservation reservation,
            final ICalendarEvent event, final Appointment appointment) {
        boolean subjectLocationBodyDifferent = false;

        try {
            if (!AppointmentEquivalenceChecker.compareSubject(reservation, event)) {
                appointment.setSubject(reservation.getReservationName());
                subjectLocationBodyDifferent = true;
            }
            if (!AppointmentEquivalenceChecker.compareBody(reservation, event)) {
                appointment.setBody(ExchangeObjectHelper.newMessageBody(reservation));
                subjectLocationBodyDifferent = true;
            }
            if (reservation instanceof RoomReservation) {
                final String newLocation =
                        this.spaceService.getLocationString((RoomReservation) reservation);
                if (!appointment.getLocation().equals(newLocation)) {
                    appointment.setLocation(newLocation);
                    subjectLocationBodyDifferent = true;
                }
            }
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API method
            // throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            // @translatable
            throw new CalendarException(
                "Error updating subject, location or body of the appointment. Please refer to archibus.log for details",
                exception, AppointmentHelper.class, this.getServiceHelper().getAdminService());
        }
        return subjectLocationBodyDifferent;
    }

    /**
     * Compare the location line of the appointment with the location in the reservation.
     *
     * @param reservation the reservation object
     * @param appointment the appointment object
     * @return true if equivalent, false if different
     * @throws ServiceLocalException when the appointment location property could not be read
     */
    public boolean isLocationEquivalent(final IReservation reservation,
            final Appointment appointment) throws ServiceLocalException {
        boolean equivalent = true;
        if (reservation instanceof RoomReservation) {
            final RoomReservation roomReservation = (RoomReservation) reservation;
            equivalent = appointment.getLocation()
                .equals(this.spaceService.getLocationString(roomReservation));
        }
        return equivalent;
    }

    /**
     * Check whether all occurrences of the given recurring appointment still match the master.
     *
     * @param appointment the appointment
     * @return true if all occurrences match the master, false if there's at least one exception
     * @throws ServiceLocalException when the properties could not be read
     */
    public boolean hasNoExceptions(final Appointment appointment) throws ServiceLocalException {
        return (appointment.getDeletedOccurrences() == null
                || appointment.getDeletedOccurrences().getCount() == 0)
                && (appointment.getModifiedOccurrences() == null
                        || appointment.getModifiedOccurrences().getCount() == 0);
    }

    /**
     * Modify the given update type to account for a change in the location, if the location is
     * different.
     *
     * @param reservation the reservation
     * @param appointment the linked appointment
     * @param updateType the current update type determined based on other relevant meeting
     *            properties
     * @return the new update type taking a possible change of location into account
     * @throws ServiceLocalException when the appointment properties cannot be accessed
     */
    public UpdateType adjustUpdateTypeForLocation(final IReservation reservation,
            final Appointment appointment, final UpdateType updateType)
                    throws ServiceLocalException {
        UpdateType newUpdateType = updateType;
        if ((UpdateType.NONE.equals(updateType) || UpdateType.ATTENDEES_ONLY.equals(updateType))
                && !this.isLocationEquivalent(reservation, appointment)) {
            newUpdateType = UpdateType.SUBJECT_LOCATION_BODY;
        }
        return newUpdateType;
    }

    /**
     * Sets the time zone mapper.
     *
     * @param timeZoneMapper the new time zone mapper
     */
    public void setTimeZoneMapper(final AppointmentTimeZoneMapper timeZoneMapper) {
        this.timeZoneMapper = timeZoneMapper;
    }

    /**
     * Set the Recurrence converter.
     *
     * @param exchangeRecurrenceConverter the new recurrence converter
     */
    public void setRecurrenceConverter(
            final ExchangeRecurrenceConverter exchangeRecurrenceConverter) {
        this.recurrenceConverter = exchangeRecurrenceConverter;
    }

    /**
     * Sets the space service.
     *
     * @param spaceService the new space service
     */
    public void setSpaceService(final ISpaceService spaceService) {
        this.spaceService = spaceService;
    }

    /**
     * Get the recurrence converter.
     *
     * @return the recurrence converter
     */
    public ExchangeRecurrenceConverter getRecurrenceConverter() {
        return this.recurrenceConverter;
    }

    /**
     * Get the space service.
     *
     * @return the space service
     */
    public ISpaceService getSpaceService() {
        return this.spaceService;
    }

}
