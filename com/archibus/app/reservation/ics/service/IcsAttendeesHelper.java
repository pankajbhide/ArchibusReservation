package com.archibus.app.reservation.ics.service;

import com.archibus.app.reservation.domain.IReservation;
import com.archibus.app.reservation.util.EmailNotificationHelper;
import com.archibus.utility.StringUtil;

import net.fortuna.ical4j.model.*;
import net.fortuna.ical4j.model.component.VEvent;

/**
 * Copyright (C) ARCHIBUS, Inc. All rights reserved.
 */
/**
 *
 * Utility class. Provides methods to add attendees to an icalendar meeting.
 * <p>
 * Used by reservations to generate ICS invitations.
 *
 * @author PROCOS
 * @since 23.2
 */
public final class IcsAttendeesHelper {

    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private IcsAttendeesHelper() {
    }

    /**
     * Adds the attendees to the meeting event.
     *
     * @param meeting the meeting event
     * @param attendees the attendees
     * @param requireReply the invitation requires reply?
     * @param organizerEmail the organizer email address
     * @param addOrganizer should add the organizer if not already there?
     */
    public static void addAttendees(final VEvent meeting, final String[] attendees,
            final boolean requireReply, final String organizerEmail, final boolean addOrganizer) {

        final PropertyList<Property> propertyList = meeting.getProperties();

        // add the organizer
        if (addOrganizer) {
            propertyList.add(new net.fortuna.ical4j.model.property.Organizer(java.net.URI
                .create(IcsConstants.MAILTO + EmailNotificationHelper.getServiceEmail())));
        } else {
            propertyList.add(new net.fortuna.ical4j.model.property.Organizer(
                java.net.URI.create(IcsConstants.MAILTO + organizerEmail)));
        }

        boolean orgInAttendees = false;
        for (final String email : attendees) {
            if (StringUtil.notNullOrEmpty(email)) {
                propertyList.add(getAttendee(email, requireReply));
                if (!orgInAttendees && email.equals(organizerEmail)) {
                    orgInAttendees = true;
                }
            }
        }

        if (addOrganizer && !orgInAttendees) {
            // does not require reply
            meeting.getProperties().add(getAttendee(organizerEmail, false));
        }

    }

    /**
     * Setup the array of email addresses to send.
     *
     * @param reservation the reservation
     * @return the array with the attendee emails
     */
    public static String[] prepareAttendees(final IReservation reservation) {
        final String invitations = reservation.getAttendees();
        return invitations.split(IcsConstants.SEMICOLON);
    }

    /**
     * Create a meeting attendee from the email address.
     *
     * @param email the email address
     * @param requireReply the invitation requires reply?
     * @return the meeting attendee
     */
    private static net.fortuna.ical4j.model.property.Attendee getAttendee(final String email,
            final boolean requireReply) {
        final net.fortuna.ical4j.model.property.Attendee attendee =
                new net.fortuna.ical4j.model.property.Attendee(
                    java.net.URI.create(IcsConstants.MAILTO + email));
        attendee.getParameters().add(net.fortuna.ical4j.model.parameter.Role.REQ_PARTICIPANT);
        attendee.getParameters().add(new net.fortuna.ical4j.model.parameter.Cn(email));
        attendee.getParameters().add(new net.fortuna.ical4j.model.parameter.Rsvp(requireReply));
        return attendee;
    }

}
