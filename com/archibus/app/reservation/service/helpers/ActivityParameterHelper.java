package com.archibus.app.reservation.service.helpers;

import java.lang.reflect.Method;
import java.util.*;

import javax.jws.WebMethod;

import com.archibus.app.common.util.SchemaUtils;
import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.service.ReservationRemoteService;
import com.archibus.app.reservation.util.*;
import com.archibus.context.ContextStore;

/**
 * Provides access to configuration settings such as activity parameters and properties.
 * <p>
 * Used by the reservation remote service to retrieve parameters.
 *
 * @author Yorik Gerlo
 * @since 22.1
 */
public class ActivityParameterHelper {

    /** The resource account parameter name. */
    public static final String RESOURCE_ACCOUNT_PARAMETER = "RESOURCE_ACCOUNT";

    /** The supported actions parameter name. */
    public static final String SUPPORTED_METHODS_PARAMETER = "SUPPORTED_METHODS";

    /** Prefix for a message to retrieve from the messages table. */
    public static final String MESSAGE_PREFIX = "MSG_";

    /** Reservation activity name. */
    public static final String RESERVATIONS_ACTIVITY = "AbWorkplaceReservations";

    /** Referenced by for messages defined for the Outlook Plugin. */
    private static final String REFERENCED_BY_PLUGIN = "OUTLOOK_PLUGIN";

    /**
     * This 'method' is added to the list of supported methods to indicate the server supports
     * editing a recurring reservation, instead of requiring the client to cancel all existing
     * reservation first.
     */
    private static final String EDIT_RECURRING_RESERVATION = "EDIT_RECURRING_RESERVATION";

    /** The calendar settings. */
    private ICalendarSettings calendarSettings;

    /**
     * Setter for the calendar settings object.
     *
     * @param calendarSettings the new calendar settings
     */
    public final void setCalendarSettings(final ICalendarSettings calendarSettings) {
        this.calendarSettings = calendarSettings;
    }

    /**
     * Gets the value of a reservations activity parameter or property.
     *
     * @param identifier activity parameter or property identifier
     * @return value of the activity parameter or property
     */
    public String getActivityParameter(final String identifier) {
        String value = null;
        if (RESOURCE_ACCOUNT_PARAMETER.equals(identifier)) {
            // This isn't an activity parameter but a Spring property.
            value = this.calendarSettings.getResourceAccount();
        } else if (SUPPORTED_METHODS_PARAMETER.equals(identifier)) {
            final StringBuffer buffer = new StringBuffer();
            // only support edit recurring if the occurrence index field is defined in the schema
            if (SchemaUtils.fieldExistsInSchema(Constants.RESERVE_TABLE_NAME,
                Constants.OCCURRENCE_INDEX_FIELD)) {
                buffer.append(Constants.COMMA);
                buffer.append(EDIT_RECURRING_RESERVATION);
            }
            for (final Method method : ReservationRemoteService.class.getMethods()) {
                buffer.append(Constants.COMMA);
                buffer.append(method.getAnnotation(WebMethod.class).action());
            }
            value = buffer.substring(1);
        } else if (identifier.startsWith(MESSAGE_PREFIX)) {
            final String messageId = identifier.substring(MESSAGE_PREFIX.length());
            ReservationsContextHelper.checkProjectContext();
            value = ReservationsContextHelper.localizeMessage(REFERENCED_BY_PLUGIN,
                ContextStore.get().getUser().getLocale(), messageId);
            if (messageId.equalsIgnoreCase(value)) {
                /*
                 * If the message is not defined or it matches the identifier, then we should use
                 * the default message defined in the plugin instead. Return an empty string to
                 * ensure the plugin uses the default message.
                 */
                value = "";
            }
        } else {
            ReservationsContextHelper.checkProjectContext();
            value = com.archibus.service.Configuration
                .getActivityParameterString(RESERVATIONS_ACTIVITY, identifier);
        }
        return value;
    }

    /**
     * Gets the values of reservations activity parameters and/or properties.
     *
     * @param identifiers activity parameters and/or property identifiers
     * @return values of the activity parameters/properties in the same order (null for unknowns)
     */
    public List<String> getActivityParameters(final List<String> identifiers) {
        final List<String> results = new ArrayList<String>(identifiers.size());
        for (final String identifier : identifiers) {
            results.add(this.getActivityParameter(identifier));
        }
        return results;
    }

    /**
     * Check whether the reservation link should be added to each saved reservation.
     *
     * @return true if it should be added, false otherwise
     */
    public boolean shouldAddReservationLink() {
        return "1".equals(this.getActivityParameter("PlugInAddReservationLink"));
    }

}
