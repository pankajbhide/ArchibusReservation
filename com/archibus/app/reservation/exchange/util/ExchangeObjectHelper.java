package com.archibus.app.reservation.exchange.util;

import java.util.*;

import microsoft.exchange.webservices.data.*;

import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.AttendeeResponseStatus.ResponseStatus;
import com.archibus.app.reservation.domain.AttendeeAvailability;
import com.archibus.app.reservation.domain.CalendarEvent;

/**
 * Utility class. Provides methods to convert availability information from Exchange format to
 * WebCentral format and to convert other data types.
 * 
 * @author Yorik Gerlo
 * @since 21.2
 */
public final class ExchangeObjectHelper {
    
    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private ExchangeObjectHelper() {
        super();
    }
    
    /**
     * Convert Exchange Availability information to Web Central format.
     * 
     * @param attendeeEmails the attendee email addresses
     * @param freeBusyResults availability information in the same order as the attendee emails
     * @param requestedTimeZone time zone in which the information should be presented
     * @param start UTC beginning of the time frame to filter events
     * @param end UTC end of the time frame to filter events
     * @return availability information in Web Central format for each of the attendees
     */
    public static Map<String, AttendeeAvailability> convertAvailability(final List<String> attendeeEmails, 
            final ServiceResponseCollection<microsoft.exchange.webservices.data.AttendeeAvailability> freeBusyResults,
            final TimeZone requestedTimeZone, final Date start, final Date end) {
        
        final Map<String, AttendeeAvailability> attendeesAvailability = new HashMap<String, AttendeeAvailability>();
        
        final Iterator<String> attendeeEmailsIterator = attendeeEmails.iterator();
        for (final microsoft.exchange.webservices.data.AttendeeAvailability freeBusyResult : freeBusyResults) {
            final String attendeeEmail = attendeeEmailsIterator.next();
            AttendeeAvailability availability = null;
            if (freeBusyResult.getErrorCode() == ServiceError.NoError) {
                final List<ICalendarEvent> calendarEvents = new ArrayList<ICalendarEvent>();
                availability = new AttendeeAvailability(attendeeEmail, calendarEvents);
                for (final microsoft.exchange.webservices.data.CalendarEvent event : freeBusyResult
                    .getCalendarEvents()) {
                    
                    // Ignore events marked as Free and events that occur outside the time frame.
                    final LegacyFreeBusyStatus status = event.getFreeBusyStatus();
                    if (LegacyFreeBusyStatus.Free.equals(status) || event.getStartTime().after(end)
                            || event.getEndTime().before(start)) {
                        continue;
                    }
                    
                    // dates are received in UTC and should be converted
                    final Calendar cal = Calendar.getInstance();
                    cal.setTime(event.getStartTime());
                    cal.add(Calendar.MILLISECOND, requestedTimeZone.getOffset(cal.getTimeInMillis()));
                    final Date startDateTime = cal.getTime();
                    cal.setTime(event.getEndTime());
                    cal.add(Calendar.MILLISECOND, requestedTimeZone.getOffset(cal.getTimeInMillis()));
                    final Date endDateTime = cal.getTime();
                    
                    // create an ARCHIBUS calendar event
                    final ICalendarEvent calendarEvent = new CalendarEvent();
                    calendarEvent.setStartDate(startDateTime);
                    calendarEvent.setEndDate(endDateTime);
                    calendarEvent.setStartTime(new java.sql.Time(startDateTime.getTime()));
                    calendarEvent.setEndTime(new java.sql.Time(endDateTime.getTime()));
                    calendarEvent.setTimeZone(requestedTimeZone.getID());
                    
                    calendarEvents.add(calendarEvent);
                }
            } else {
                // No information, store the error message for logging later.
                availability =
                        new AttendeeAvailability(attendeeEmail, freeBusyResult.getErrorCode()
                            .toString() + " - " + freeBusyResult.getErrorMessage());
            }
            attendeesAvailability.put(attendeeEmail, availability);
        }
        
        return attendeesAvailability;
    }
    
    /**
     * Create a new HTML message body, adding HTML newlines to all existing plain newlines if the
     * comments are available in HTML format.
     * 
     * @param reservation the reservation containing the message body text in plain text and optionally HTML
     * @return the message body in plain text or HTML
     */
    public static MessageBody newMessageBody(final IReservation reservation) {
        MessageBody body = null;
        if (reservation.getHtmlComments() == null) {
            body = new MessageBody(BodyType.Text, reservation.getComments());
        } else {
            final String htmlText =
                    reservation.getHtmlComments().replaceAll("\n", "<br/>\n");
            body = new MessageBody(BodyType.HTML, htmlText);
        }
        return body;
    }
    
    /**
     * Create attendee response status from the given Exchange attendee.
     * 
     * @param attendee the Exchange attendee to convert.
     * @return the attendee response status
     */
    public static AttendeeResponseStatus createResponseStatus(final Attendee attendee) {
        final AttendeeResponseStatus response = new AttendeeResponseStatus();
        response.setName(attendee.getName());
        response.setEmail(attendee.getAddress());
        switch (attendee.getResponseType()) {
            case Accept:
                response.setResponseStatus(ResponseStatus.Accepted);
                break;
            case Decline:
                response.setResponseStatus(ResponseStatus.Declined);
                break;
            case Tentative:
                response.setResponseStatus(ResponseStatus.Tentative);
                break;
            case NoResponseReceived:
            case Unknown:
            default:
                response.setResponseStatus(ResponseStatus.Unknown);
                break;
        }
        return response;
    }
}
