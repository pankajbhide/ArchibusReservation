package com.archibus.app.reservation.exchange.util;

import microsoft.exchange.webservices.data.*;

import com.archibus.app.reservation.domain.CalendarException;

/**
 * Provides time zone mapping for Exchange Appointments.
 *<p>
 *
 * Used by AppointmentHelper to set the correct appointment time zone.
 * Managed by Spring, has singleton scope. Configured in exchange-integration-context.xml file.
 *
 * @author Yorik Gerlo
 * @since 21.2
 *
 */
public class AppointmentTimeZoneMapper extends TimeZoneMapper {
    
    /**
     * Set the time zone of the appointment.
     * 
     * @param appointment the appointment to set the time zone for
     * @param timeZoneId Olson Time Zone identifier to set
     */
    public void setTimeZone(final Appointment appointment, final String timeZoneId) {
        final TimeZoneDefinition timeZone =
                TimeZoneDefinition.fromId(this.getWindowsId(timeZoneId));
        try {
            appointment.setStartTimeZone(timeZone);
            if (AppointmentSchema.EndTimeZone.getVersion().compareTo(
                appointment.getService().getRequestedServerVersion()) <= 0) {
                /*
                 * Setting end time zone is not supported in Exchange2007_SP1. That Exchange version
                 * automatically set the end time zone to match the start time zone (if the end time
                 * zone is not specified). Hence, only set the end time zone here for versions that
                 * support it.
                 */
                appointment.setEndTimeZone(timeZone);
            }
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API
            // method throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            // @translatable
            throw new CalendarException("Error setting appointment time zone to {0}", exception,
                AppointmentTimeZoneMapper.class, timeZoneId);
        }
    }
    
}
