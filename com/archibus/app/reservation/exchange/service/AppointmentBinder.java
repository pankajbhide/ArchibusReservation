package com.archibus.app.reservation.exchange.service;

import java.util.*;

import org.apache.log4j.Logger;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.util.ReservationUtils;
import com.archibus.utility.StringUtil;

import microsoft.exchange.webservices.data.*;

/**
 * Helper class providing functionality for binding to Exchange appointments.
 *
 * Managed by Spring.
 *
 * @author Yorik Gerlo
 * @since 21.2
 */
public class AppointmentBinder extends AbstractAppointmentHelper {

    /** Error message used when binding to an occurrence failed. */
    // @translatable
    private static final String ERROR_BINDING_TO_OCCURRENCE =
            "Error binding to appointment occurrence. Please refer to archibus.log for details";

    /** The logger. */
    private final Logger logger = Logger.getLogger(this.getClass());

    /**
     * Bind to an existing appointment on a given user's calendar with the given unique id.
     *
     * @param email the Exchange user's email address
     * @param iCalUid the unique id of the appointment in the user's Exchange calendar
     * @return the appointment object, or null if not found
     */
    public Appointment bindToAppointment(final String email, final String iCalUid) {
        final ExchangeService exchangeService = this.getInitializedService(email);
        final Appointment appointment =
                bindToAppointment(exchangeService, iCalUid, this.getAppointmentPropertiesHelper()
                    .getExtendedAppointmentPropertySet());
        this.updateCachedExchangeService(exchangeService, email);
        return appointment;
    }

    /**
     * Bind to an existing appointment on a given user's calendar with the given unique id.
     *
     * @param service the Exchange service connection
     * @param iCalUid the unique id of the appointment in the user's Exchange calendar
     * @param propertySet the property set to load (can be null to skip loading additional
     *            properties)
     * @return the appointment object, or null if not found
     */
    private Appointment bindToAppointment(final ExchangeService service, final String iCalUid,
            final PropertySet propertySet) {
        try {
            Appointment result = null;

            final FindItemsResults<Item> results =
                    service.findItems(WellKnownFolderName.Calendar, this
                        .getAppointmentPropertiesHelper().getUidFilter(iCalUid), new ItemView(1));
            final List<Item> items = results.getItems();
            if (!items.isEmpty() && items.get(0) instanceof Appointment) {
                result = (Appointment) items.get(0);
                if (propertySet != null) {
                    result.load(propertySet);
                }
            }

            return result;
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API method
            // throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            // @translatable
            throw new CalendarException(
                "Error binding to appointment. Please refer to archibus.log for details.",
                exception, AppointmentBinder.class, this.getServiceHelper().getAdminService());
        }
    }

    /**
     * Bind to an appointment occurrence based on its occurrence index or its start date and time in
     * UTC.
     *
     * @param service the Exchange service connection
     * @param originalReservation the reservation as it's currently stored in the database
     * @param recurrenceMaster the master appointment (if already bound, null is allowed)
     * @return the appointment, or null if not found
     */
    public Appointment bindToOccurrence(final ExchangeService service,
            final IReservation originalReservation, final Appointment recurrenceMaster) {
        Appointment occurrence = null;
        if (originalReservation.getOccurrenceIndex() == 0) {
            this.logger.debug("No occurrence index specified in "
                    + originalReservation.getReserveId() + ".  Binding by start date/time.");
            final Date startDateTime =
                    ReservationUtils.getTimePeriodInTimeZone(originalReservation,
                        Constants.TIMEZONE_UTC).getStartDateTime();
            occurrence =
                    this.bindToOccurrence(service, originalReservation.getUniqueId(), startDateTime);
        } else {
            Appointment master = recurrenceMaster;
            if (master == null) {
                master = this.bindToAppointment(service, originalReservation.getUniqueId(), null);
            }
            if (master == null) {
                this.logger.debug("Unable to bind to occurrence "
                        + originalReservation.getReserveId()
                        + " because master appointment is not found on the calendar");
            } else {
                occurrence =
                        this.bindToOccurrence(master, originalReservation.getOccurrenceIndex());
            }
        }

        this.updateCachedExchangeService(service, originalReservation.getEmail());
        return occurrence;
    }

    /**
     * Bind to an appointment occurrence based on its start date and time in UTC.
     *
     * @param exchangeService the service connected to the Exchange user's mailbox
     * @param iCalUid the iCalendar UID of the appointment series
     * @param startDateTime the current start date and time of the appointment occurrence
     * @return the appointment, or null if not found
     */
    public Appointment bindToOccurrence(final ExchangeService exchangeService,
            final String iCalUid, final Date startDateTime) {
        try {
            final FindItemsResults<Appointment> results =
                    exchangeService.findAppointments(WellKnownFolderName.Calendar,
                        new CalendarView(startDateTime, startDateTime));

            Appointment result = null;
            for (final Appointment appointment : results.getItems()) {
                appointment.load(this.getAppointmentPropertiesHelper()
                    .getExtendedAppointmentPropertySet());
                if (appointment.getICalUid().equals(iCalUid)) {
                    result = appointment;
                    break;
                }
            }

            return result;
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API method
            // throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            throw new CalendarException(ERROR_BINDING_TO_OCCURRENCE, exception,
                AppointmentBinder.class, this.getServiceHelper().getAdminService());
        }
    }

    /**
     * Bind to an appointment occurrence based on its master appointment and occurrence index.
     *
     * @param master the master appointment
     * @param occurrenceIndex the occurrence index
     * @return the appointment, or null if not found
     */
    private Appointment bindToOccurrence(final Appointment master, final int occurrenceIndex) {
        Appointment occurrence = null;
        try {
            occurrence =
                    Appointment.bindToOccurrence(master.getService(), master.getId(),
                        occurrenceIndex, this.getAppointmentPropertiesHelper()
                            .getExtendedAppointmentPropertySet());
        } catch (final ServiceResponseException exception) {
            if (ServiceError.ErrorCalendarOccurrenceIsDeletedFromRecurrence.equals(exception
                .getResponse().getErrorCode())) {
                this.logger.debug("Unable to bind to occurrence because it is already cancelled",
                    exception);
            } else {
                throw new CalendarException(ERROR_BINDING_TO_OCCURRENCE, exception,
                    AppointmentBinder.class, this.getServiceHelper().getAdminService());
            }
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API method
            // throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            throw new CalendarException(ERROR_BINDING_TO_OCCURRENCE, exception,
                AppointmentBinder.class, this.getServiceHelper().getAdminService());
        }
        return occurrence;
    }

    /**
     * Find all appointments on the specific user's calendar during the specified time period.
     *
     * @param email user's email address
     * @param windowStart start of the time period (UTC)
     * @param windowEnd end of the time period (UTC)
     * @return list of appointments that occur during the time period
     * @throws CalendarException when the user doesn't exist on Exchange or any other error occurs
     */
    public List<Appointment> findAppointments(final String email, final Date windowStart,
            final Date windowEnd) throws CalendarException {
        // Use getService so it doesn't switch to the organizer mailbox automatically.
        // This means an Exception will be thrown when the user doesn't exist.
        final ExchangeService exchangeService = this.getServiceHelper().getService(email);
        try {
            final FindItemsResults<Appointment> results =
                    exchangeService.findAppointments(WellKnownFolderName.Calendar,
                        new CalendarView(windowStart, windowEnd));

            for (final Appointment appointment : results.getItems()) {
                appointment.load(this.getAppointmentPropertiesHelper()
                    .getExtendedAppointmentPropertySet());
            }

            return results.getItems();
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API method
            // throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            throw new CalendarException("Error finding appointments for {0}.", exception,
                AppointmentBinder.class, this.getServiceHelper().getAdminService(), email);
        }
    }

    /**
     * Creates the appointment object.
     *
     * @param reservation the reservation to create an appointment for
     * @return the appointment
     */
    public Appointment createAppointment(final IReservation reservation) {
        Appointment appointment = null;
        // First look for an existing appointment with matching unique id.
        if (StringUtil.notNullOrEmpty(reservation.getUniqueId())) {
            appointment = this.bindToAppointment(reservation.getEmail(), reservation.getUniqueId());
        }
        if (appointment == null) {
            // No matching appointment was found, so create a new one locally.
            final ExchangeService exchangeService =
                    this.getInitializedService(reservation.getEmail());
            try {
                appointment = new Appointment(exchangeService);
                // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API
                // method throws a checked Exception, which needs to be wrapped in ExceptionBase.
            } catch (final Exception exception) {
                // CHECKSTYLE:ON
                // @translatable
                throw new CalendarException(
                    "Error creating appointment. Please refer to archibus.log for details",
                    exception, AppointmentBinder.class, this.getServiceHelper().getAdminService());
            }
            this.updateCachedExchangeService(exchangeService, reservation.getEmail());
        }
        return appointment;
    }

}
