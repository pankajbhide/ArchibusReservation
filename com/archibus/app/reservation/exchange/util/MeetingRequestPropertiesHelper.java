package com.archibus.app.reservation.exchange.util;

import java.util.*;

import microsoft.exchange.webservices.data.*;

import com.archibus.app.reservation.domain.*;

/**
 * Provides access to the user properties in an Exchange appointment that are used for managing
 * reservations.
 *
 * @author Yorik Gerlo
 *
 */
public class MeetingRequestPropertiesHelper extends ItemPropertiesHelper {

    /**
     * Get the set of properties that must be used for processing meeting requests via EWS. This set
     * includes the relevant user properties.
     *
     * @return the extended PropertySet
     */
    public PropertySet getExtendedMeetingRequestPropertySet() {
        return new PropertySet(MeetingRequestSchema.Start, MeetingRequestSchema.End,
            MeetingRequestSchema.ICalUid, MeetingRequestSchema.Subject, MeetingRequestSchema.Body,
            MeetingRequestSchema.RequiredAttendees, MeetingRequestSchema.OptionalAttendees,
            MeetingRequestSchema.IsOutOfDate, MeetingRequestSchema.AppointmentType,
            MeetingRequestSchema.Organizer, MeetingRequestSchema.AssociatedAppointmentId,
            MeetingRequestSchema.ICalRecurrenceId, this.reservationIdProperty,
            this.recurringReservationIdsProperty);
    }

    /**
     * Get the reservation ID from the appointment's user properties. For recurring appointments,
     * look in the master appointment if required.
     *
     * @param request the appointment to get the reservation date from
     * @return the reservation ID stored in the appointment, or null if the property doesn't exist
     */
    public Integer getReservationId(final MeetingRequest request) {
        try {
            final AppointmentType appointmentType = request.getAppointmentType();
            Integer reservationId = null;
            /*
             * Valid types are recurring master, exception or single. Non-exception occurrences
             * cannot be sent as separate meeting requests.
             */
            if (AppointmentType.RecurringMaster.equals(appointmentType)) {
                // A recurrence master does not have a reservation id property.
                // Get the reservation ID from the first date in the recurrence
                // tracking state.
                reservationId =
                        getRecurringReservationIds(request).get(
                            TimePeriod.clearTime(request.getStart()));
            } else {
                // Check the property in the given request.
                reservationId = getReservationIdFromUserProperty(request);

                // If not found, check for a recurring reservation id's property.
                if (reservationId == null) {
                    reservationId =
                            this.getReservationIdFromRecurringReservationIds(request,
                                this.getRecurringReservationIds(request));
                }
            }
            return reservationId;
        } catch (final ServiceLocalException exception) {
            throw new CalendarException(
                "Error reading reservation ID properties in meeting request.", exception,
                MeetingRequestPropertiesHelper.class);
        }
    }

    /**
     * Get the matching reservation ID from the map of reservation id's.
     *
     * @param request the meeting request
     * @param recurringReservationIds map of reservation id's
     * @return the reservation id, or null if not found
     */
    public Integer getReservationIdFromRecurringReservationIds(final MeetingRequest request,
            final Map<Date, Integer> recurringReservationIds) {
        try {
            final Date recurrenceId =
                    (Date) request
                        .getObjectFromPropertyDefinition(MeetingMessageSchema.ICalRecurrenceId);
            Integer reservationId = null;
            if (recurringReservationIds != null && recurrenceId != null) {
                // Use the ICalRecurrenceId, that is the original date of the occurrence.
                reservationId = recurringReservationIds.get(TimePeriod.clearTime(recurrenceId));
            }
            return reservationId;
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API method
            // throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            throw new CalendarException("Error reading recurrence id in meeting request.",
                exception, MeetingRequestPropertiesHelper.class);
        }
    }

}
