package com.archibus.app.reservation.exchange.util;

import java.text.*;
import java.util.*;

import org.apache.log4j.Logger;

import com.archibus.app.reservation.domain.CalendarException;
import com.archibus.app.reservation.util.AdminServiceContainer;

import microsoft.exchange.webservices.data.*;

/**
 * Base class for accessing properties of Exchange items.
 *
 * @author Yorik Gerlo
 * @since 21.2
 */
public class ItemPropertiesHelper extends AdminServiceContainer {

    /**
     * Name of the Appointment user property that contains the identifier of the reservation linked
     * to the appointment.
     */
    protected static final String RESERVATION_ID_PROPERTYNAME = "ReservationID-Archibus";

    /**
     * Name of the Appointment user property that indicates the identifiers of reservations linked
     * to a recurring appointment, stored in the recurrence master appointment. Each occurrence is
     * linked to its reservation via the occurrence's original date.
     */
    protected static final String RECURRING_RESERVATION_IDS_PROPERTYNAME = "RecurringReservationIDs-Archibus";

    /**
     * Separator used in the recurring reservation IDs property, between the original date and the
     * reservation ID of each occurrence.
     */
    protected static final String DATE_SEPARATOR = "|";

    /** Date format used in the user properties. */
    private static final String DATE_FORMAT_STRING = "yyyy-MM-dd";

    /**
     * Regular expression pattern format of the separator used in the recurring reservation IDs
     * property, between the original date and the reservation ID of each occurrence.
     */
    private static final String DATE_SEPARATOR_PATTERN = "\\|";

    /** The date formatter used for all Date <-> String conversions. */
    protected final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_STRING,
            Locale.ENGLISH);

    /** Definition of the reservation ID user property. */
    protected final ExtendedPropertyDefinition reservationIdProperty;

    /** Definition of the recurring reservation IDs user property. */
    protected final ExtendedPropertyDefinition recurringReservationIdsProperty;

    /** The logger. */
    protected final Logger logger = Logger.getLogger(this.getClass());

    /**
     * Create a new Exchange Item Properties helper.
     */
    public ItemPropertiesHelper() {
        super();
        try {
            this.reservationIdProperty =
                    new ExtendedPropertyDefinition(DefaultExtendedPropertySet.PublicStrings,
                        RESERVATION_ID_PROPERTYNAME, MapiPropertyType.Integer);
            this.recurringReservationIdsProperty =
                    new ExtendedPropertyDefinition(DefaultExtendedPropertySet.PublicStrings,
                        RECURRING_RESERVATION_IDS_PROPERTYNAME, MapiPropertyType.StringArray);
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API method
            // throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            throw new CalendarException("Error setting up appointment user properties.", exception,
                ItemPropertiesHelper.class);
        }
    }

    /**
     * Get the reservation ID stored in the given appointment or meeting request's user property. Do
     * not perform a lookup for recurring appointments.
     *
     * @param appointment the appointment or meeting request to get the reservation date from
     * @return the reservation ID stored in the user property, or null if the property doesn't exist
     * @throws ServiceLocalException when an EWS error occurs
     */
    public Integer getReservationIdFromUserProperty(final Item appointment) throws ServiceLocalException {
        final Object value = findPropertyValue(appointment, this.reservationIdProperty);
        Integer reservationId = null;
        if (value instanceof Integer) {
            reservationId = (Integer) value;
        }
        return reservationId;
    }

    /**
     * Get the recurrence reservation IDs from the appointment or meeting request's user property.
     *
     * @param appointment the appointment or meeting request to get the dates from
     * @return a map with keys representing the occurrences' original date and values indicating
     *         their reservation id
     */
    public Map<Date, Integer> getRecurringReservationIds(final Item appointment) {
        Map<Date, Integer> reservationIds = null;
        final Object value = findPropertyValue(appointment, this.recurringReservationIdsProperty);
        if (value instanceof Object[]) {
            reservationIds = new HashMap<Date, Integer>();

            for (final Object pair : (Object[]) value) {
                final String[] splitPair = pair.toString().split(DATE_SEPARATOR_PATTERN);
                if (splitPair.length == 2) {
                    try {
                        final Date originalDate = this.dateFormat.parse(splitPair[0]);
                        final Integer reservationId = Integer.valueOf(splitPair[1]);
                        reservationIds.put(originalDate, reservationId);
                    } catch (final ParseException exception) {
                        // ignore this pair
                        this.logger.warn("Invalid date '" + splitPair[0]
                                + "' in recurring reservation ids.", exception);
                    }
                } else {
                    this.logger.warn("No date separator '" + DATE_SEPARATOR + "' in '" + pair
                            + "'.");
                }
            }
        }

        return reservationIds;
    }

    /**
     * Find the value of the user property with the given name in the item's user properties.
     *
     * @param item the Exchange item
     * @param propertyDef definition of the user property to get the value for
     * @return the value, or null if it doesn't exist
     */
    private Object findPropertyValue(final Item item, final ExtendedPropertyDefinition propertyDef) {
        Object propertyValue = null;
        try {
            for (final ExtendedProperty property : item.getExtendedProperties()) {
                if (propertyDef.equals(property.getPropertyDefinition())) {
                    propertyValue = property.getValue();
                    break;
                }
            }
        } catch (final ServiceLocalException exception) {
            // @translatable
            throw new CalendarException("Error reading extended appointment properties", exception,
                ItemPropertiesHelper.class, this.getAdminService());
        }
        return propertyValue;
    }

}