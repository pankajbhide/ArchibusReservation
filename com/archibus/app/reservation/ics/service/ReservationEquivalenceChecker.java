package com.archibus.app.reservation.ics.service;

import org.apache.commons.lang.StringUtils;

import com.archibus.app.reservation.domain.*;
import com.archibus.utility.StringUtil;

/**
 * Utility class. Provides methods to check equivalence between two reservations except for the
 * date.
 * <p>
 *
 * Used by IcsCalendarService to verify whether individual occurrences have relevant changes
 * compared to the first occurrence.
 *
 * @author Yorik Gerlo
 * @since 24.1
 */
public final class ReservationEquivalenceChecker {

    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private ReservationEquivalenceChecker() {
    }

    /**
     * Compare 2 reservations regarding time, duration, subject, location and attendees.
     *
     * @param reservation1 the first reservation
     * @param reservation2 the second reservation
     * @return true if equal, false if different
     */
    public static boolean isEquivalent(final RoomReservation reservation1,
            final RoomReservation reservation2) {

        boolean equivalent = compareTime(reservation1, reservation2);
        equivalent = equivalent && compareName(reservation1, reservation2);
        equivalent = equivalent && compareAttendees(reservation1, reservation2);
        equivalent = equivalent && compareComments(reservation1, reservation2);
        equivalent = equivalent && compareLocation(reservation1, reservation2);

        return equivalent;
    }

    /**
     * Compare the reservation names.
     *
     * @param reservation1 the first reservation object
     * @param reservation2 the second reservation object
     * @return true if equal, false if different
     */
    public static boolean compareName(final IReservation reservation1,
            final IReservation reservation2) {
        final String name1 = StringUtil.notNull(reservation1.getReservationName()).trim();
        final String name2 = StringUtil.notNull(reservation2.getReservationName()).trim();
        return name1.equals(name2);
    }

    /**
     * Compare the start and end time of the reservations.
     *
     * @param reservation1 the first reservation object
     * @param reservation2 the second reservation object
     * @return true if equivalent, false if different
     */
    public static boolean compareTime(final IReservation reservation1,
            final IReservation reservation2) {
        boolean timeEqual =
                StringUtils.equals(reservation1.getTimeZone(), reservation2.getTimeZone());
        timeEqual = timeEqual && reservation1.getStartTime().equals(reservation2.getStartTime());
        timeEqual = timeEqual && reservation1.getEndTime().equals(reservation2.getEndTime());
        return timeEqual;
    }

    /**
     * Compare the attendees in the given reservations.
     *
     * @param reservation1 the first reservation for which to compare attendees
     * @param reservation2 the second reservation for which to compare attendees
     * @return true if equivalent, false if different
     */
    public static boolean compareAttendees(final IReservation reservation1,
            final IReservation reservation2) {
        return StringUtils.equals(reservation1.getAttendees(), reservation2.getAttendees());
    }

    /**
     * Compare the reservation comments.
     *
     * @param reservation1 the first reservation
     * @param reservation2 the second reservation
     * @return true if equal, false if different
     */
    public static boolean compareComments(final IReservation reservation1,
            final IReservation reservation2) {
        return StringUtils.equals(reservation1.getComments(), reservation2.getComments());
    }

    /**
     * Compare the location of both reservations.
     *
     * @param reservation1 the first reservation
     * @param reservation2 the second reservation
     * @return true if the location matches, false otherwise
     */
    public static boolean compareLocation(final RoomReservation reservation1,
            final RoomReservation reservation2) {

        boolean locationEqual = true;

        if (reservation1.getRoomAllocations().isEmpty()) {
            locationEqual = reservation2.getRoomAllocations().isEmpty();
        } else {
            final RoomArrangement room1 =
                    reservation1.getRoomAllocations().get(0).getRoomArrangement();
            final RoomArrangement room2 =
                    reservation2.getRoomAllocations().get(0).getRoomArrangement();
            locationEqual = room1.equals(room2);
        }

        return locationEqual;
    }

}
