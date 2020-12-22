package com.archibus.app.reservation.exchange.util;

import java.util.*;

import org.apache.commons.codec.DecoderException;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.util.StringTranscoder;

import microsoft.exchange.webservices.data.*;

/**
 * Provides access to the user properties in an Exchange appointment that are used for managing
 * reservations.
 *
 * @author Yorik Gerlo
 *
 */
public class AppointmentPropertiesHelper extends ItemPropertiesHelper {

    /** MAPI Property ID of the ICal UI Property. */
    private static final int UID_PROPERTY = 0x03;

    /**
     * Extended set of properties to retrieve via EWS when binding to an Appointment, including the
     * relevant user properties also used by the Outlook Plugin.
     */
    private final PropertySet extendedAppointmentPropertySet;

    /** Definition of the ical UID property. */
    private final ExtendedPropertyDefinition icalUidProperty;

    /**
     * Create an instance of the Appointment Properties Helper.
     */
    public AppointmentPropertiesHelper() {
        super();
        try {
            this.icalUidProperty =
                    new ExtendedPropertyDefinition(DefaultExtendedPropertySet.Meeting,
                        UID_PROPERTY, MapiPropertyType.Binary);
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API method
            // throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            throw new CalendarException("Error setting up appointment user properties.", exception,
                AppointmentPropertiesHelper.class);
        }
        this.extendedAppointmentPropertySet =
                new PropertySet(PropertySet.FirstClassProperties.getBasePropertySet(),
                    this.reservationIdProperty, this.recurringReservationIdsProperty);
    }

    /**
     * Get the set of properties that must be used for binding to appointments via EWS. This set
     * includes the relevant user properties.
     *
     * @return the extended PropertySet
     */
    public PropertySet getExtendedAppointmentPropertySet() {
        return this.extendedAppointmentPropertySet;
    }

    /**
     * Get the reservation ID from the appointment's user properties. For recurring appointments,
     * look in the master appointment if required.
     *
     * @param appointment the appointment to get the reservation id from
     * @return the reservation ID stored in the appointment, or null if the property doesn't exist
     */
    public Integer getReservationId(final Appointment appointment) {
        try {
            final AppointmentType appointmentType = appointment.getAppointmentType();
            Integer reservationId = null;
            if (AppointmentType.RecurringMaster.equals(appointmentType)) {
                // A recurrence master does not have a reservation id property.
                // Get the reservation ID from the first date in the recurrence
                // tracking state.
                reservationId =
                        getRecurringReservationIds(appointment).get(
                            TimePeriod.clearTime(appointment.getStart()));
            } else if (AppointmentType.Occurrence.equals(appointmentType)) {
                // Bind to the master and check there.
                final Appointment master =
                        Appointment.bindToRecurringMaster(appointment.getService(),
                            appointment.getId(), getExtendedAppointmentPropertySet());
                reservationId =
                        getRecurringReservationIds(master).get(
                            TimePeriod.clearTime(appointment.getStart()));
            } else {
                // Check the property in the given appointment.
                reservationId = getReservationIdFromUserProperty(appointment);

                // If not found and the appointment is an Exception, check in
                // the master.
                if (reservationId == null && AppointmentType.Exception.equals(appointmentType)) {
                    final Appointment master =
                            Appointment.bindToRecurringMaster(appointment.getService(),
                                appointment.getId(), getExtendedAppointmentPropertySet());
                    // Use the ICalRecurrenceId, that is the original date of the occurrence.
                    reservationId =
                            getRecurringReservationIds(master).get(
                                TimePeriod.clearTime(appointment.getICalRecurrenceId()));
                }
            }
            return reservationId;
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API method
            // throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            // @translatable
            throw new CalendarException("Error reading reservation ID properties in appointment.",
                exception, AppointmentPropertiesHelper.class, this.getAdminService());
        }
    }

    /**
     * Set the reservation ID in the appointment's user properties.
     *
     * @param appointment the appointment to modify
     * @param reservationId the reservation id to set
     */
    public void setReservationId(final Appointment appointment, final Integer reservationId) {
        try {
            appointment.setExtendedProperty(this.reservationIdProperty, reservationId);
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API method
            // throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            // @translatable
            throw new CalendarException("Error setting reservation ID in appointment.", exception,
                AppointmentPropertiesHelper.class, this.getAdminService());
        }
    }

    /**
     * Remove the reservation ID user property.
     *
     * @param appointment the appointment to remove the reservation id from
     * @return true if the property was removed, false if not present
     */
    public boolean removeReservationId(final Appointment appointment) {
        try {
            return appointment.removeExtendedProperty(this.reservationIdProperty);
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API method
            // throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            // @translatable
            throw new CalendarException("Error removing reservation ID from appointment.",
                exception, AppointmentPropertiesHelper.class, this.getAdminService());
        }
    }

    /**
     * Remove the recurring reservation IDs user property.
     *
     * @param appointment the appointment to remove the recurring reservation ids from
     * @return true if the property was removed, false if not present
     */
    public boolean removeRecurringReservationIds(final Appointment appointment) {
        try {
            return appointment.removeExtendedProperty(this.recurringReservationIdsProperty);
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API method
            // throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            // @translatable
            throw new CalendarException(
                "Error removing recurring reservation IDs from appointment.", exception,
                AppointmentPropertiesHelper.class, this.getAdminService());
        }
    }

    /**
     * Set the recurring reservation ids user property in the appointment.
     *
     * @param appointment the recurring appointment to set the exception dates for
     * @param reservation master reservation containing the reservations created according to the
     *            recurrence pattern (in building time)
     */
    public void setRecurringReservationIds(final Appointment appointment,
            final RoomReservation reservation) {
        final Map<Date, Integer> reservationIds =
                reservation.getCreatedReservationIds(Constants.TIMEZONE_UTC);
        setRecurringReservationIds(appointment, reservationIds);
    }

    /**
     * Set the recurring reservation ids user property in the appointment.
     *
     * @param appointment the recurring appointment to set the exception dates for
     * @param reservationIds reservation id's by date
     */
    private void setRecurringReservationIds(final Appointment appointment,
            final Map<Date, Integer> reservationIds) {
        final ArrayList<String> serializedReservationIds =
                new ArrayList<String>(reservationIds.size());
        for (final Map.Entry<Date, Integer> entry : reservationIds.entrySet()) {
            final String originalDate = this.dateFormat.format(entry.getKey());
            final String reservationId = String.valueOf(entry.getValue());
            serializedReservationIds.add(originalDate + DATE_SEPARATOR + reservationId);
        }

        try {
            appointment.setExtendedProperty(this.recurringReservationIdsProperty,
                serializedReservationIds.toArray(new String[serializedReservationIds.size()]));
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API method
            // throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            // @translatable
            throw new CalendarException("Error setting recurring reservation IDs in appointment.",
                exception, AppointmentPropertiesHelper.class, this.getAdminService());
        }
    }

    /**
     * Get a search filter to find an appointment by it's unique ID.
     *
     * @param uid the unique id
     * @return the search filter
     * @throws DecoderException when the appointment UID could not be transcoded.
     */
    public SearchFilter getUidFilter(final String uid) throws DecoderException {
        final String encodedUid = StringTranscoder.transcodeHexToBase64(uid);
        return new SearchFilter.IsEqualTo(this.icalUidProperty, encodedUid);
    }

    /**
     * Remove the given reservation from the recurring reservation id's stored in the given master appointment.
     * @param master the appointment
     * @param reservation the reservation
     * @return true if removed, false if not found
     */
    public boolean removeFromRecurringReservationIds(final Appointment master, final IReservation reservation) {
        Integer reservationId = reservation.getConferenceId();
        if (reservationId == null) {
            reservationId = reservation.getReserveId();
        }
        boolean removed = false;
        final Map<Date, Integer> reservationIds = this.getRecurringReservationIds(master);
        if (reservationIds != null && reservationId != null) {
            for (final Map.Entry<Date, Integer> entry : reservationIds.entrySet()) {
                if (reservationId.equals(entry.getValue())) {
                    reservationIds.remove(entry.getKey());
                    removed = true;
                    break;
                }
            }
            if (removed) {
                this.setRecurringReservationIds(master, reservationIds);
            }
        }
        return removed;
    }

}
