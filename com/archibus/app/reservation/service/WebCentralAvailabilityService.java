package com.archibus.app.reservation.service;

import java.util.*;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.util.TimeZoneConverter;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.model.view.datasource.ClauseDef.Operation;
import com.archibus.model.view.datasource.ParsedRestrictionDef;
import com.archibus.utility.*;

/**
 * Provides Free-busy information based on reservations in ARCHIBUS.
 * <p>
 * Managed by Spring
 *
 * @author Yorik Gerlo
 * @since 21.2
 */
public class WebCentralAvailabilityService extends RoomReservationServiceBase
        implements IAvailabilityService {

    /** Percent symbol for LIKE restrictions. */
    private static final String PERCENT = "%";

    /** Name of the table containing reservations. */
    private static final String RESERVE_TABLE = "reserve";

    /** {@inheritDoc} */
    @Override
    public Map<String, AttendeeAvailability> findAttendeeAvailability(
            final IReservation reservation, final Date startDate, final Date endDate,
            final TimeZone requestedTimeZone, final String userEmail,
            final List<String> attendeeEmails) throws ExceptionBase {
        final Map<String, AttendeeAvailability> availabilities =
                new HashMap<String, AttendeeAvailability>();
        for (final String email : attendeeEmails) {
            final List<ICalendarEvent> events =
                    this.findAttendeeAvailability(startDate, endDate, requestedTimeZone, email);
            availabilities.put(email, new AttendeeAvailability(email, events));
        }
        return availabilities;
    }

    /**
     * Find the attendee availability. This routine returns the list of the calendar events for the
     * attendee.
     *
     * @param startDate first date in the specified requestedTimeZone
     * @param endDate last date in the specified requestedTimeZone
     * @param timeZone requested time zone
     * @param email email of the attendee
     * @return List of Calendar Events
     * @throws ExceptionBase translated exception
     */
    private List<ICalendarEvent> findAttendeeAvailability(final Date startDate, final Date endDate,
            final TimeZone timeZone, final String email) throws ExceptionBase {

        final List<ICalendarEvent> events = new ArrayList<ICalendarEvent>();
        if (!StringUtil.isNullOrEmpty(email)) {
            this.reservationDataSource.clearRestrictions();
            this.reservationDataSource.addRestriction(Restrictions.or(
                Restrictions.like(RESERVE_TABLE, "attendees", PERCENT + email + PERCENT),
                Restrictions.eq(RESERVE_TABLE, "email", email)));

            /*
             * Query for all reservations on the requested dates +/- 1 day for time zone
             * differences. Convert them to the requested time zone afterwards to check whether they
             * occur on the requested date.
             */
            final ParsedRestrictionDef restriction = new ParsedRestrictionDef();
            final Calendar calendar = Calendar.getInstance();
            calendar.setTime(startDate);
            calendar.add(Calendar.DATE, -1);
            restriction.addClause(RESERVE_TABLE, Constants.DATE_START_FIELD_NAME,
                calendar.getTime(), Operation.GTE);
            calendar.setTime(endDate);
            calendar.add(Calendar.DATE, 1);
            final Date filterEndDate = calendar.getTime();
            restriction.addClause(RESERVE_TABLE, Constants.DATE_END_FIELD_NAME, filterEndDate,
                Operation.LTE);

            restriction.addClause(RESERVE_TABLE, Constants.STATUS,
                Arrays.asList(
                    new String[] { Constants.STATUS_AWAITING_APP, Constants.STATUS_CONFIRMED }),
                Operation.IN);

            final List<RoomReservation> foundReservations =
                    this.reservationDataSource.find(restriction);

            for (final RoomReservation foundReservation : foundReservations) {
                // retrieve the reservation including the room to allow time zone conversion
                final RoomReservation reservation = this.reservationDataSource
                    .getActiveReservation(foundReservation.getReserveId());
                reservation.setTimeZone(
                    this.timeZoneCache.getBuildingTimeZone(reservation.determineBuildingId()));
                TimeZoneConverter.convertToTimeZone(reservation, timeZone.getID());

                if (filterEndDate.before(reservation.getStartDateTime())
                        || startDate.after(reservation.getEndDateTime())) {
                    // skip reservations not occurring on the requested date in the given time zone
                    continue;
                }

                final ICalendarEvent calendarEvent = new CalendarEvent();
                // Take the unique id, conference call id or reservation id as reference.
                if (StringUtil.notNullOrEmpty(reservation.getUniqueId())) {
                    calendarEvent.setEventId(reservation.getUniqueId());
                } else if (reservation.getConferenceId() == null
                        || reservation.getConferenceId() == 0) {
                    calendarEvent.setEventId(Integer.toString(reservation.getReserveId()));
                } else {
                    calendarEvent.setEventId(Integer.toString(reservation.getConferenceId()));
                }
                calendarEvent.setSubject(reservation.getReservationName());
                calendarEvent.setStartDate(reservation.getStartDate());
                calendarEvent.setEndDate(reservation.getEndDate());
                calendarEvent.setStartTime(reservation.getStartTime());
                calendarEvent.setEndTime(reservation.getEndTime());

                if (Constants.TYPE_RECURRING.equalsIgnoreCase(reservation.getReservationType())) {
                    calendarEvent.setRecurrent(true);
                } else {
                    calendarEvent.setRecurrent(false);
                }

                events.add(calendarEvent);
            }
        }

        return events;
    }

}
