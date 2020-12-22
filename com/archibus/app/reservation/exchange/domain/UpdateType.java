package com.archibus.app.reservation.exchange.domain;


/**
 * Represents the type of update applied to an Exchange appointment, indicating which properties
 * have been modified in the local object representing the appointment.
 * <p>
 * Used by ExchangeCalendarService to determine which update mode is required to save the changes
 * and inform the attendees.
 *
 * @author Yorik Gerlo
 * @since 22.1
 */
public enum UpdateType {

    /** Nothing was changed in the appointment. */
    NONE,

    /** Only the list of attendees was modified. */
    ATTENDEES_ONLY,

    /** Only subject, location, body and/or attendees were modified. */
    SUBJECT_LOCATION_BODY,

    /** Modifications include attendees, subject, body, date, time and location. */
    FULL;

}
