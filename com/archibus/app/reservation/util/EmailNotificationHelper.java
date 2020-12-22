package com.archibus.app.reservation.util;

import java.util.*;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.context.ContextStore;
import com.archibus.datasource.*;
import com.archibus.datasource.data.DataRecord;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.eventhandler.reservations.ReservationsCommonHandler;
import com.archibus.jobmanager.EventHandlerContext;
import com.archibus.utility.StringUtil;

/**
 * Utility class. Provides methods to send email notifications.
 * <p>
 * Used by Reservation Application.
 *
 * @author Yorik Gerlo
 * @since 21.2
 */
public final class EmailNotificationHelper {

    /** Reservation id field name. */
    private static final String RES_ID = "res_id";

    /** Parent reservation id field name. */
    private static final String RES_PARENT = "res_parent";

    /** Event handler result parameter name. */
    private static final String MESSAGE = "message";

    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private EmailNotificationHelper() {
    }

    /**
     * Send notifications for the single reservation with the given identifier.
     *
     * @param reservationId reservation identifier
     */
    public static void sendNotifications(final Integer reservationId) {
        sendNotifications(reservationId, null, null);
    }

    /**
     * Send notifications to the requested by and requested for of the reservation.
     *
     * @param reservationId the reservation id to notify for
     * @param parentId the parent reservation id to notify for a recurring series
     * @param cancelMessage the message
     */
    public static void sendNotifications(final Integer reservationId, final Integer parentId,
            final String cancelMessage) {
        // Use the ReservationsCommonHandler to send the notifications.

        if (notificationsEnabled()) {
            final EventHandlerContext context = ContextStore.get().getEventHandlerContext();

            if (StringUtil.notNullOrEmpty(cancelMessage)) {
                context.addResponseParameter("cancel_message", cancelMessage);
            }

            if (parentId == null || parentId == 0) {
                context.addResponseParameter(RES_ID, Integer.toString(reservationId));
                context.removeResponseParameter(RES_PARENT);
            } else {
                context.addResponseParameter(RES_ID, Integer.toString(0));
                context.addResponseParameter(RES_PARENT, Integer.toString(parentId));
            }
            final ReservationsCommonHandler handler = new ReservationsCommonHandler();

            final List<String> errorMessages = new ArrayList<String>();

            if (context.parameterExists(MESSAGE)) {
                errorMessages.add(String.valueOf(context.getParameter(MESSAGE)));
            }

            handler.notifyRequestedBy(context);
            if (context.parameterExists(MESSAGE)) {
                errorMessages.add(String.valueOf(context.getParameter(MESSAGE)));
            }

            handler.notifyRequestedFor(context);
            if (context.parameterExists(MESSAGE)) {
                errorMessages.add(String.valueOf(context.getParameter(MESSAGE)));
            }

            setFullErrorMessage(context, errorMessages);
        }
    }

    /**
     * Set the full error message in the context, concatenating the errors in the list but removing
     * sequential duplicates.
     *
     * @param context context to put the error message
     * @param errorMessages the list of error messages to concatenate
     */
    private static void setFullErrorMessage(final EventHandlerContext context,
            final List<String> errorMessages) {
        String fullErrorMessage = "";
        for (int i = errorMessages.size() - 1; i > 0; --i) {
            final String errorMessage = errorMessages.get(i);
            // Skip duplicate and empty error messages.
            if (StringUtil.notNullOrEmpty(errorMessage)
                    && !errorMessage.equals(errorMessages.get(i - 1))) {
                fullErrorMessage = '\n' + errorMessage + fullErrorMessage;
            }
        }

        if (!errorMessages.isEmpty()) {
            fullErrorMessage = errorMessages.get(0) + fullErrorMessage;
        }

        if (StringUtil.notNullOrEmpty(fullErrorMessage)) {
            context.addResponseParameter(MESSAGE, fullErrorMessage);
        }
    }

    /**
     * Check whether sending email notifications is enabled for the Reservations Application.
     *
     * @return true if enabled, false otherwise
     */
    public static boolean notificationsEnabled() {
        final String sendEmailNotifications =
                com.archibus.service.Configuration.getActivityParameterString(
                    ReservationsContextHelper.RESERVATIONS_ACTIVITY, "SendEmailNotifications");

        return sendEmailNotifications != null && "YES".equals(sendEmailNotifications.toUpperCase());
    }

    /**
     * Activity Parameter Property: InternalServicesEmail.
     *
     * @return the email address
     */
    public static String getServiceEmail() {
        return com.archibus.service.Configuration.getActivityParameterString(
            ReservationsContextHelper.RESERVATIONS_ACTIVITY, "InternalServicesEmail");
    }

    /**
     * Activity Parameter Property: InternalServicesName.
     *
     * @return the name
     */
    public static String getServiceName() {
        return com.archibus.service.Configuration.getActivityParameterString(
            ReservationsContextHelper.RESERVATIONS_ACTIVITY, "InternalServicesName");
    }

    /**
     * Get the locale for a specific user.
     *
     * @param email the user email
     * @return the locale of the user
     */
    public static String getUserLocale(final String email) {
        // DataSource to search the locale of the user to notify
        final DataSource userLocaleDs = DataSourceFactory
            .createDataSourceForFields(Constants.AFM_USERS_TABLE, new String[] { "locale" });
        userLocaleDs.addRestriction(
            Restrictions.eq(Constants.AFM_USERS_TABLE, Constants.EMAIL_FIELD_NAME, email));

        final String locale;

        // Search the locale of the user to notify
        final List<DataRecord> userLocaleRecords = userLocaleDs.getRecords();
        if (userLocaleRecords.isEmpty()) {
            locale = "";
        } else {
            final DataRecord userLocale = userLocaleRecords.get(0);
            locale = userLocale.getString("afm_users.locale");
        }

        return locale;
    }

    /**
     * Get the locale to use for e-mailing a user.
     *
     * @param email the user's email address
     * @return the locale to use
     */
    public static String getLocaleForEmail(final String email) {
        // try to get the locale for the user_requested_by
        String locale = EmailNotificationHelper.getUserLocale(email);
        // else get the current user's locale
        if (StringUtil.isNullOrEmpty(locale)) {
            locale = ContextStore.get().getUser().getLocale();
        }
        return locale;
    }

}
