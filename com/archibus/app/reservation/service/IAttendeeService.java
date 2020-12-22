package com.archibus.app.reservation.service;

import java.util.List;

import com.archibus.app.reservation.domain.*;
import com.archibus.utility.ExceptionBase;

/**
 * Provides attendee information from a remote service.<br/>
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
public interface IAttendeeService {

    /**
     * Get the attendees' response status.
     *
     * @param reservation
     *            the reservation to get the response status for
     * @return list of attendee response status
     * @throws ExceptionBase
     *             translated exception
     */
    List<AttendeeResponseStatus> getAttendeesResponseStatus(final IReservation reservation) throws ExceptionBase;

}