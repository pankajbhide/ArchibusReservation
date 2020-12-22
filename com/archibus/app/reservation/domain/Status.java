package com.archibus.app.reservation.domain;

import org.apache.xpath.operations.Equals;

import com.archibus.utility.EnumTemplate;

/**
 * Enumeration for status values that are used in reservation.
 * 
 * 
 * @author Ioan Draghici
 * @since 24.3
 */

public enum Status {
    /**
     * Awaiting Approval.
     */
    AWAITING_APPROVAL,
    /**
     * Rejected.
     */
    REJECTED,
    /**
     * Cancelled.
     */
    CANCELLED,
    /**
     * Confirmed.
     */
    CONFIRMED,
    /**
     * Closed.
     */
    CLOSED;
    /**
     * Mapping to String values.
     */
    private static final Object[][] STRINGS_TO_ENUMS =
            { { "Awaiting App.", AWAITING_APPROVAL }, { "Rejected", REJECTED }, { "Cancelled", CANCELLED },
                    { "Confirmed", CONFIRMED }, { "Closed", CLOSED } };

    /**
     * Converts given string to enum object.
     *
     * @param source string to convert to enum.
     * @return result of conversion.
     */
    public static Status fromString(final String source) {
        return (Status) EnumTemplate.fromString(source, STRINGS_TO_ENUMS,
            Status.class);
    }

    
    @Override
    public String toString() {
        return EnumTemplate.toString(STRINGS_TO_ENUMS, this);
    }

}
