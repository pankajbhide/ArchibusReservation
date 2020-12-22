package com.archibus.app.reservation.util;

import java.text.MessageFormat;
import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.context.*;
import com.archibus.datasource.*;
import com.archibus.datasource.data.DataRecord;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.eventhandler.EventHandlerBase;
import com.archibus.jobmanager.EventHandlerContext;
import com.archibus.security.UserAccount;
import com.archibus.service.remoting.AdminService;
import com.archibus.utility.*;

/**
 * Utility class. Provides methods to set up the Web Central context for processing Exchange events.
 * <p>
 * Used by the Exchange Listener to set the correct user in the context.
 *
 * @author Yorik Gerlo
 * @since 21.2
 */
public final class ReservationsContextHelper {

    /** Reservation activity name. */
    public static final String RESERVATIONS_ACTIVITY = "AbWorkplaceReservations";

    /** Message parameter name in the event handler context. */
    public static final String RESULT_MESSAGE_PARAMETER = "message";

    /** Messages table name. */
    private static final String MESSAGES_TABLE = "messages";

    /** Constants: message id field. */
    private static final String MESSAGE_ID = "message_id";

    /** Constant: message text field. */
    private static final String MESSAGE_TEXT = "message_text";

    /** User name field name. */
    private static final String USER_NAME = "user_name";

    /** Table containing user account info. */
    private static final String TABLE_AFM_USERS = "afm_users";

    /** Project parameter name. */
    private static final String PROJECT_PARAMETER = "project";

    /** Default OK message to write in the message parameter. */
    private static final String RESULT_MESSAGE_OK = "OK";

    /** Trailing slash to include in Web Central URL. */
    private static final String SLASH = "/";

    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private ReservationsContextHelper() {
    }

    /**
     * Set the user account based on the user's email address.
     *
     * @param email the email address
     */
    public static void setUserFromEmail(final String email) {
        final DataSource userDs = DataSourceFactory.createDataSourceForFields(TABLE_AFM_USERS,
            new String[] { USER_NAME, Constants.EMAIL_FIELD_NAME });
        userDs.setApplyVpaRestrictions(false);
        final Context context = ContextStore.get();

        userDs.addRestriction(Restrictions.eq(TABLE_AFM_USERS, Constants.EMAIL_FIELD_NAME, email));
        final DataRecord record = userDs.getRecord();

        if (record == null) {
            final String errorMessage = String.format("No user found with email %s", email);
            throw new org.springframework.security.core.userdetails.UsernameNotFoundException(
                errorMessage);
        } else {
            final String username = record.getString(TABLE_AFM_USERS + "." + USER_NAME);
            final UserAccount.Immutable userAccount = context.getProject().loadUserAccount(username,
                context.getSession().getId(), false);
            context.setUser(userAccount.getUser());
        }
    }

    /**
     * Check project context.
     */
    public static void checkProjectContext() {
        final EventHandlerContext eventHandlerContext = ContextStore.get().getEventHandlerContext();
        if (!eventHandlerContext.parameterExistsNotEmpty(PROJECT_PARAMETER)) {
            ContextStore.get().getEventHandlerContext().addInputParameter(PROJECT_PARAMETER,
                ContextStore.get().getProject());
        }
    }

    /**
     * Check whether the Reservations Plugin for Microsoft Outlook license is enabled.
     */
    public static void checkPluginLicense() {
        boolean enabled = false;
        try {
            enabled =
                    EventHandlerBase.isActivityLicenseEnabled(null, "AbReservationsOutlookPlugin");
        } catch (final NullPointerException exception) {
            // Core API throws NPE if license is not defined.
            Logger.getLogger(ReservationsContextHelper.class).trace("while checking Plugin license",
                exception);
        }
        if (!enabled) {
            throw new ReservationException(
                // @translatable
                "Your ARCHIBUS license does not include the Reservations Outlook Extension.",
                ReservationsContextHelper.class);
        }
    }

    /**
     * Check whether the Reservations Extension for Microsoft Exchange license is enabled.
     */
    public static void checkExchangeLicense() {
        boolean enabled = false;
        try {
            enabled = EventHandlerBase.isActivityLicenseEnabled(null,
                "AbReservationsExchangeExtension");
        } catch (final NullPointerException exception) {
            // Core API throws NPE if license is not defined.
            Logger.getLogger(ReservationsContextHelper.class)
                .trace("while checking Exchange license", exception);
        }
        // Do not throw exception if context is not available.
        if (!enabled && ContextStore.get() != null) {
            throw new CalendarException(
                // @translatable
                "Your ARCHIBUS license does not include the Reservations Exchange Extension.",
                ReservationsContextHelper.class);
        }
    }

    /**
     * Check whether the EventHandlerContext has a message. If it doesn't, then add the default OK
     * message.
     */
    public static void ensureResultMessageIsSet() {
        final EventHandlerContext context = ContextStore.get().getEventHandlerContext();
        if (!context.parameterExists(RESULT_MESSAGE_PARAMETER)
                || StringUtil.isNullOrEmpty(context.getParameter(RESULT_MESSAGE_PARAMETER))) {
            context.addResponseParameter(RESULT_MESSAGE_PARAMETER, RESULT_MESSAGE_OK);
        }
    }

    /**
     * Append an error message in the event handler context.
     *
     * @param error the error message to append
     */
    public static void appendResultError(final String error) {
        final EventHandlerContext context = ContextStore.get().getEventHandlerContext();
        String message = context.getString(RESULT_MESSAGE_PARAMETER, "");
        // Don't append if the same error was already in the context.
        if (!message.equals(error)) {
            if (StringUtil.notNullOrEmpty(message)) {
                message += "\n";
            }
            message += error;
        }

        context.addResponseParameter(RESULT_MESSAGE_PARAMETER, message);
    }

    /**
     * Get the Web Central URL for external users - with trailing /.
     *
     * @return the external Web Central URL
     */
    public static String getWebCentralUrl() {
        String url = ContextStore.get().getConfigManager()
            .getAttribute("/*/preferences/@absoluteAppPath");
        if (StringUtil.notNullOrEmpty(url) && !url.endsWith(SLASH)) {
            url = url + SLASH;
        }
        return url;
    }

    /**
     * Localize the given string that was marked translatable in the given class.
     *
     * @param referencedBy identifies what the message is used for
     * @param locale the locale to get the message for
     * @param messageId message identifier
     * @return the localized string
     */
    public static String localizeMessage(final String referencedBy, final String locale,
            final String messageId) {
        final Map<String, String> messages = localizeMessages(referencedBy, locale, messageId);
        String messageText = messages.get(messageId);
        if (messageText == null) {
            messageText = messageId;
        }
        return messageText;
    }

    /**
     * Lookup the localized messages to use.
     *
     * @param referencedBy the messages referenced by value
     * @param locale the locale to get the messages for
     * @param messageIds the message id's
     * @return the localized messages
     */
    public static Map<String, String> localizeMessages(final String referencedBy,
            final String locale, final String... messageIds) {

        final String inMessageIds = StringUtils.join(messageIds, ',');

        final StringBuilder localeField = new StringBuilder(MESSAGE_TEXT);
        if (StringUtil.notNullOrEmpty(locale)) {
            final String dbExtension = Utility.getDbExtension(locale);
            if (StringUtil.notNullOrEmpty(dbExtension)) {
                localeField.append('_').append(dbExtension).toString();
            }
        }

        final DataSource datasource = DataSourceFactory.createDataSourceForFields(MESSAGES_TABLE,
            new String[] { MESSAGE_ID, MESSAGE_TEXT, localeField.toString() });

        datasource
            .addRestriction(Restrictions.eq(MESSAGES_TABLE, "activity_id", RESERVATIONS_ACTIVITY));
        datasource.addRestriction(Restrictions.eq(MESSAGES_TABLE, "referenced_by", referencedBy));
        datasource.addRestriction(Restrictions.in(MESSAGES_TABLE, MESSAGE_ID, inMessageIds));

        final List<DataRecord> records = datasource.getAllRecords();

        final Map<String, String> messages = new HashMap<String, String>();
        String messageText;
        String messageId;
        for (final DataRecord dataRecord : records) {
            messageId = dataRecord.getString(MESSAGES_TABLE + '.' + MESSAGE_ID);

            messageText = dataRecord.getString(MESSAGES_TABLE + '.' + localeField);
            if (messageText == null) {
                messageText = dataRecord.getString(MESSAGES_TABLE + '.' + MESSAGE_TEXT);
            }

            if (messageText == null) {
                messages.put(messageId, messageId);
            } else {
                messages.put(messageId, messageText);
            }
        }

        return messages;

    }

    /**
     * Localize the given string that was marked translatable in the given class.
     *
     * @param message the string to localize
     * @param clazz the class where the message was defined and marked translatable
     * @param args additional arguments used for formatting the localized message
     * @return the localized string
     */
    public static String localizeString(final String message, final Class<?> clazz,
            final Object... args) {
        return MessageFormat.format(message, args);
    }

    /**
     * Localize the given message defined in the given class via the given admin service.
     *
     * @param message the message to localize
     * @param clazz the class where the message is defined
     * @param adminService the admin service that supports localization
     * @param args additional arguments used for formatting the localized message
     * @return the localized string
     */
    public static String localizeString(final String message, final Class<?> clazz,
            final AdminService adminService, final Object... args) {

        final String localizedMessage;
        if (ContextStore.get() == null || ContextStore.get().getUser() == null
                || ContextStore.get().getSecurityController() == null) {
            // don't try to localize if we don't have the necessary context
            localizedMessage = message;
        } else {
            localizedMessage = adminService.loadLocalizedString(null, clazz.getName(), message,
                ContextStore.get().getUser().getLocale(), false);
        }
        return MessageFormat.format(localizedMessage, args);
    }

}
