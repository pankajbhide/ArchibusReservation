package com.archibus.app.reservation.service.helpers;

import java.util.*;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.*;
import com.archibus.utility.StringUtil;

/**
 * Utility class. Provides methods for manipulating domain objects.
 * <p>
 * Used by Reservations WFR services.
 *
 * @author Yorik Gerlo
 * @since 22.1
 */
public final class ReservationWfrServiceHelper {

    /** Email validation pattern. */
    private static final String EMAIL_PATTERN = "[A-Za-z0-9!#$%&'*+-/=?^_`{|}~ \"(),:;<>@\\[\\\\\\]]+";

    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private ReservationWfrServiceHelper() {
    }

    /**
     * Check whether the given reservation record is a new recurring reservation or is meant to edit
     * a series of occurrences.
     *
     * @param reservation the reservation to check
     * @return true if it's a new recurring reservation or an edit of multiple occurrences, false
     *         otherwise
     */
    public static boolean isNewRecurrenceOrEditSeries(final AbstractReservation reservation) {
        // Only create recurring reservations for a new reservation or when the start date is
        // not the same as the end date. Otherwise update the occurrence specified by the
        // reservation id.
        return Constants.TYPE_RECURRING.equalsIgnoreCase(reservation.getReservationType())
                && (reservation.getReserveId() == null
                        || !reservation.getStartDate().equals(reservation.getEndDate()));
    }

    /**
     * Parse the recurrence defined by the reservation object if required. Uses the start date and
     * end date of the reservation. After parsing, the end date is reset to equal the start date.
     * The recurrence pattern is stored in the reservation and returned.
     *
     * @param reservation the reservation to parse the recurrence for
     * @return the recurrence pattern
     */
    public static Recurrence prepareNewRecurrence(final AbstractReservation reservation) {
        // We only need to parse the recurrence for a new recurring reservation.
        final Integer reservationId = reservation.getReserveId();
        Recurrence recurrence = null;
        if (reservationId == null) {
            recurrence = RecurrenceParser.parseRecurrence(reservation.getStartDate(),
                reservation.getEndDate(), reservation.getRecurringRule());
            reservation.setRecurrence(recurrence);
        }
        // The end date in the reservation was used to specify the recurrence end date.
        // Change it back to the reservation date before saving the reservations.
        reservation.setEndDate(reservation.getStartDate());
        return recurrence;
    }

    /**
     * Prepare the given reservations to keep track of all occurrences being updated and created. At
     * the same time create a map of these reservations based on their parent id, to be used for
     * updating the corresponding existing reservations for each occurrence.
     *
     * @param reservations the reservations on the first occurrence being edited
     * @param numberOfOccurrences the number of occurrences
     * @return map of the given reservations by parent id
     */
    public static Map<Integer, RoomReservation> prepareCompiledReservations(
            final List<RoomReservation> reservations, final int numberOfOccurrences) {
        final Map<Integer, RoomReservation> reservationsByParentId =
                new HashMap<Integer, RoomReservation>();
        for (final RoomReservation reservation : reservations) {
            // initialize the list of created reservations
            reservation.setCreatedReservations(new ArrayList<RoomReservation>(numberOfOccurrences));

            // track the compiled reservations by parent id
            if (reservation.getParentId() != null) {
                reservationsByParentId.put(reservation.getParentId(), reservation);
            }
        }
        return reservationsByParentId;
    }

    /**
     * Check whether the reservation email and attendees are valid email addresses. This is a simple
     * check, it only checks that each email is of the format someone@something.
     *
     * @param reservation the reservation to validate the emails for
     * @throws ReservationException when an invalid email is found
     */
    public static void validateEmails(final AbstractReservation reservation)
            throws ReservationException {
        if (!validateEmail(reservation.getEmail())) {
            // @translatable
            throw new ReservationException("Invalid requestor email: {0}",
                ReservationServiceHelper.class, reservation.getEmail());
        }
        if (StringUtil.notNullOrEmpty(reservation.getAttendees())) {
            for (final String attendee : reservation.getAttendees().split(";")) {
                if (!validateEmail(attendee)) {
                    // @translatable
                    throw new ReservationException("Invalid attendee email: {0}",
                        ReservationServiceHelper.class, attendee);
                }
            }
        }
    }

    /**
     * Check that the email address contains an @ and it's not the first or last character.
     *
     * @param emailToCheck the email to check
     *
     * @return true if valid, false if invalid
     */
    private static boolean validateEmail(final String emailToCheck) {
        final int atIndex = emailToCheck.indexOf('@');
        final boolean containsAt = atIndex >= 1 && atIndex < emailToCheck.length() - 1;
        return containsAt && emailToCheck.matches(EMAIL_PATTERN);
    }

}
