package com.archibus.app.reservation.exchange.util;

import java.util.*;

import microsoft.exchange.webservices.data.*;

import com.archibus.app.reservation.domain.*;
import com.archibus.utility.StringUtil;


/**
 * Utility class. Provides methods to update the list of attendees of an Exchange appointment.
 * 
 * @author Yorik Gerlo
 * @since 21.2
 */
public final class AttendeesHelper {

    /** Error message for when updating attendees failed. */
    // @translatable
    private static final String ERROR_UPDATING_ATTENDEES = "Error updating attendees";


    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private AttendeesHelper() {
        super();
    }

    /**
     * Set the attendees in the appointment object.
     * 
     * @param reservation the reservation containing the new list of attendees
     * @param appointment the appointment that should be updated
     * @param resourceAccount email address of the reservations service account that must be added
     *            to the appointment as a resource
     * @param organizerAccount email address of the reservations service account used as organizer
     *            for non-Exchange users
     */
    public static void setAttendees(final IReservation reservation, final Appointment appointment,
            final String resourceAccount, final String organizerAccount) {
        try {
            final Map<String, Attendee> attendeeEmails = new HashMap<String, Attendee>();
            final AttendeeCollection requiredAttendees = appointment.getRequiredAttendees();
            for (final Attendee attendee : requiredAttendees) {
                attendeeEmails.put(attendee.getAddress(), attendee);
            }
            
            // If the organizer is not on our Exchange, the service is impersonating the
            // organizerAccount mailbox.
            final boolean nonExchangeUser =
                    appointment.getService().getImpersonatedUserId().getId()
                        .equals(organizerAccount);

            // If the reservation organizer is a non-Exchange user, add him as an attendee.
            if (nonExchangeUser && StringUtil.notNullOrEmpty(reservation.getEmail())
                    && attendeeEmails.remove(reservation.getEmail()) == null) {
                try {
                    requiredAttendees.add(reservation.getEmail());
                    // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API
                    // method throws a checked Exception, which needs to be wrapped in ExceptionBase.
                } catch (final Exception exception) {
                    // CHECKSTYLE:ON
                    throw new CalendarException(ERROR_UPDATING_ATTENDEES, exception,
                        AttendeesHelper.class);
                }
            }

            addAttendeeEmails(reservation, attendeeEmails, requiredAttendees);
            addResourceAccount(appointment, resourceAccount, nonExchangeUser);
        
        } catch (final ServiceLocalException exception) {
            throw new CalendarException(ERROR_UPDATING_ATTENDEES, exception, AttendeesHelper.class);
        }

    }

    /**
     * Check whether we need to add the resource account to the meeting. Only add it if the
     * reservation organizer is an Exchange user and the resource account is not empty.
     * 
     * @param appointment the appointment to add the resource account to
     * @param resourceAccount the resource account
     * @param nonExchangeUser true if the requestor is a non-Exchange user 
     * @throws CalendarException when adding the resource account failed
     */
    private static void addResourceAccount(final Appointment appointment,
            final String resourceAccount, final boolean nonExchangeUser)
            throws CalendarException {
        if (!nonExchangeUser && StringUtil.notNullOrEmpty(resourceAccount)) {
            try {
                final ArrayList<String> resourceEmails = new ArrayList<String>();
                for (final Attendee resource : appointment.getResources()) {
                    resourceEmails.add(resource.getAddress());
                }
                if (!resourceEmails.remove(resourceAccount)) {
                    appointment.getResources().add(resourceAccount);
                }
                // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API
                // method throws a checked Exception, which needs to be wrapped in ExceptionBase.
            } catch (Exception exception) { 
                // CHECKSTYLE:ON
                throw new CalendarException(ERROR_UPDATING_ATTENDEES, exception,
                    AttendeesHelper.class);
            }
        }
    }


    /**
     * Adds the attendee emails.
     *
     * @param reservation the reservation
     * @param attendeeEmails the attendee emails
     * @param requiredAttendees the required attendees
     * @throws CalendarException the exception
     */
    private static void addAttendeeEmails(final IReservation reservation,
            final Map<String, Attendee> attendeeEmails,
            final AttendeeCollection requiredAttendees) throws CalendarException {

        try {
            if (reservation.getAttendees() != null) {
                for (final String email : reservation.getAttendees().split(";")) {
                    // Add the email if it wasn't present already.
                    if (StringUtil.notNullOrEmpty(email) && attendeeEmails.remove(email) == null) { 
                        requiredAttendees.add(email); 
                    }
                }
            }
            // Remove the attendees that are no longer in the calendarEvent.
            for (final Attendee attendee : attendeeEmails.values()) {
                requiredAttendees.remove(attendee);
            }
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API
            // method throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (Exception exception) { 
            // CHECKSTYLE:ON
            throw new CalendarException(ERROR_UPDATING_ATTENDEES, exception, AttendeesHelper.class);
        }
    }

}
