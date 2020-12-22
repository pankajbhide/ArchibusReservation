package com.archibus.app.reservation.exchange.service;

import java.util.*;

import org.apache.log4j.Logger;

import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.exchange.util.ExchangeObjectHelper;
import com.archibus.app.reservation.service.IAttendeeService;
import com.archibus.app.reservation.util.AdminServiceContainer;
import com.archibus.utility.ExceptionBase;

import microsoft.exchange.webservices.data.*;

/**
 * Provides Free-busy information from a Exchange Server.
 * <p>
 * Managed by Spring
 *
 * @author Yorik Gerlo
 * @since 21.2
 */
public class ExchangeAttendeeService extends AdminServiceContainer implements IAttendeeService {

    /** The logger. */
    private final Logger logger = Logger.getLogger(this.getClass());

    /** The helper for binding to Exchange Appointments. */
    private AppointmentBinder appointmentBinder;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AttendeeResponseStatus> getAttendeesResponseStatus(final IReservation reservation)
            throws ExceptionBase {
        final String uniqueId = reservation.getUniqueId();
        final List<AttendeeResponseStatus> responses = new ArrayList<AttendeeResponseStatus>();
        // bind to the appointment, go over all required and optional attendees
        try {
            Appointment appointment = null;
            if (reservation.getParentId() == null || reservation.getParentId() == 0) {
                appointment =
                        this.appointmentBinder.bindToAppointment(reservation.getEmail(), uniqueId);
            } else {
                appointment = this.appointmentBinder.bindToOccurrence(
                    this.appointmentBinder.getInitializedService(reservation), reservation, null);
            }
            if (appointment == null) {
                this.logger.warn("Error retrieving response status. Appointment not found.");
            } else {
                for (final Attendee attendee : appointment.getRequiredAttendees()) {
                    responses.add(ExchangeObjectHelper.createResponseStatus(attendee));
                }
                for (final Attendee attendee : appointment.getOptionalAttendees()) {
                    responses.add(ExchangeObjectHelper.createResponseStatus(attendee));
                }
            }
        } catch (final ServiceLocalException exception) {
            throw new CalendarException("Error retrieving attendee response status", exception,
                ExchangeCalendarService.class, this.getAdminService());
        }
        return responses;
    }

    /**
     * Sets the appointment binder to be used for finding free-busy info on the calendar.
     *
     * @param appointmentBinder the new appointment binder
     */
    public void setAppointmentBinder(final AppointmentBinder appointmentBinder) {
        this.appointmentBinder = appointmentBinder;
    }

}
