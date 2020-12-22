package com.archibus.app.reservation.service;

import java.util.*;

import com.archibus.app.reservation.domain.*;
import com.archibus.utility.ExceptionBase;

/**
 * Provides availability information from a remote service.<br/>
 * There can be several implementations, for now there is only Exchange and Web Central for no
 * integration.
 *
 * <p>
 * Managed by Spring. All beans are defined with scope prototype.
 * </p>
 *
 * @author Yorik Gerlo
 * @since 21.2
 */
public interface IAvailabilityService {

    /**
     * Find the attendee availability for a number of attendees. This routine returns the list of
     * the calendar events per attendee.
     *
     * @param reservation original reservation being edited
     * @param startDate the first date to get availability info for
     * @param endDate the last date to get availability info for
     * @param requestedTimeZone the time zone to present the availability information in
     * @param userEmail email address of the user requesting the information
     * @param attendeeEmails email addresses of the attendees
     * @return list of calendar events per attendee email
     * @throws ExceptionBase translated exception
     */
    Map<String, AttendeeAvailability> findAttendeeAvailability(IReservation reservation,
            Date startDate, Date endDate, TimeZone requestedTimeZone, String userEmail,
            List<String> attendeeEmails) throws ExceptionBase;

}