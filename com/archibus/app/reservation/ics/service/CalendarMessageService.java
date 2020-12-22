package com.archibus.app.reservation.ics.service;

import java.util.Map;

import com.archibus.app.reservation.dao.IConferenceCallReservationDataSource;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.AbstractIntervalPattern;
import com.archibus.app.reservation.ics.domain.*;
import com.archibus.app.reservation.service.IEmployeeService;
import com.archibus.app.reservation.util.*;
import com.archibus.config.MailPreferences;
import com.archibus.context.ContextStore;
import com.archibus.jobmanager.EventHandlerContext;
import com.archibus.utility.StringUtil;

/**
 * Provides the ICS generation and email sending, if the Exchange Integration is disabled.
 * <p>
 *
 * @author PROCOS
 * @since 23.2
 *
 */
public class CalendarMessageService {

    /** Reservations data source. */
    private IConferenceCallReservationDataSource reservationDs;

    /** The time zones cache. */
    private TimeZoneCache timeZoneCache;

    /** Email preferences. */
    private MailPreferences mailPreferences;

    /** The Employee service. */
    private IEmployeeService employeeService;

    /**
     * Send email invitations with ICAL attachments.
     *
     * @param reservation the room reservation to send an invite for
     * @param originalReserv the original reservation
     * @param invitationType the type of invite to send
     * @param allRecurrences true to send for all occurrences, false for only the given occurrence
     * @param requireReply true to require rsvp
     * @param message the message to include in the notification
     * @param conferenceId the conference id in case of a conference call reservation (may be null)
     */
    public final void sendEmailInvitations(final RoomReservation reservation,
            final RoomReservation originalReserv, final String invitationType,
            final boolean allRecurrences, final boolean requireReply, final String message,
            final Integer conferenceId) {

        final EventHandlerContext context = ContextStore.get().getEventHandlerContext();

        final String from = this.employeeService.getEmployeeEmail(reservation.getRequestedBy());
        final String locale = EmailNotificationHelper.getLocaleForEmail(from);
        final String[] attendees = IcsAttendeesHelper.prepareAttendees(reservation);

        final Map<String, String> messages = getSendMailMessages(locale);
        final boolean recurring = StringUtil.notNullOrEmpty(reservation.getRecurringRule());

        final EmailModel emailModel = new EmailModel(invitationType, conferenceId, recurring,
            requireReply, allRecurrences, locale);

        final Map<String, Object> dataModel = DataModelHelper.prepareDataModel(messages, emailModel,
            reservation, message, this.timeZoneCache);

        if (emailModel.isChange() && originalReserv != null
                && reservation.getRecurringDateModified() == 1
                && originalReserv.getRecurringDateModified() == 0) {
            // when updating a recurring meeting occurrence and the dates
            // changed, then cancel the previous meeting
            final EmailModel cancelModel = new EmailModel(EmailModel.TYPE_CANCEL, conferenceId,
                recurring, requireReply, allRecurrences, locale);
            final Map<String, Object> cancelDataModel = DataModelHelper.prepareDataModel(messages,
                emailModel, originalReserv, message, this.timeZoneCache);
            final IcsModel cancelIcsModel = setupIcsModel(messages, cancelModel, cancelDataModel,
                originalReserv, originalReserv);

            IcsAttachmentHelper.addIcsToAttachments(context, cancelIcsModel, attendees,
                (String) cancelDataModel.get(IcsConstants.DATE_START), emailModel.getAttachments(),
                false);

            // create the organizer attachment
            IcsAttachmentHelper.addIcsToAttachments(context, cancelIcsModel, attendees,
                (String) cancelDataModel.get(IcsConstants.DATE_START),
                emailModel.getOrgAttachments(), true);

        }
        this.generateIcsAttachments(reservation, originalReserv, message, attendees, messages,
            emailModel, dataModel);

        final IcsMessage emailMessage = createEmailMessage(allRecurrences, locale, dataModel);
        // send the email
        emailMessage.setMailFrom(from);
        emailMessage.setMailTo(attendees);
        MessageHelper.sendMessage(emailModel.getAttachments(), emailMessage, this.mailPreferences);

        final IcsMessage reqEmailMessage = emailMessage.copy();

        // send to the organizer
        reqEmailMessage.setMailFrom(EmailNotificationHelper.getServiceEmail());
        reqEmailMessage.setMailTo(from);
        MessageHelper.sendMessage(emailModel.getOrgAttachments(), reqEmailMessage,
            this.mailPreferences);
    }

    /**
     * Generate ICS attachments and add them to the email model.
     *
     * @param reservation the primary reservation
     * @param originalReserv the original primary reservation
     * @param message the user's message to include
     * @param attendees the attendees
     * @param messages localized messages to use
     * @param emailModel the email model specifying what action the email reports
     * @param dataModel the data model containing reservation information to include in the ICS
     */
    private void generateIcsAttachments(final RoomReservation reservation,
            final RoomReservation originalReserv, final String message, final String[] attendees,
            final Map<String, String> messages, final EmailModel emailModel,
            final Map<String, Object> dataModel) {
        final IcsModel icsModel =
                setupIcsModel(messages, emailModel, dataModel, reservation, originalReserv);

        if (emailModel.isCancel() && emailModel.isAllRecurrences()) {
            addCancelledExceptionsIcs(reservation, message, attendees, messages, emailModel);
        }
        final String startDate = (String) dataModel.get(IcsConstants.DATE_START);

        if (icsModel != null) {
            final EventHandlerContext context = ContextStore.get().getEventHandlerContext();
            IcsAttachmentHelper.addIcsToAttachments(context, icsModel, attendees, startDate,
                emailModel.getAttachments(), false);
            // create the organizer attachment
            IcsAttachmentHelper.addIcsToAttachments(context, icsModel, attendees, startDate,
                emailModel.getOrgAttachments(), true);

            if (emailModel.isChange() && emailModel.isAllRecurrences()
                    && !emailModel.isDisconnect()) {
                /*
                 * Check if any of the occurrences don't match the series. Add a separate ICS for
                 * them.
                 */
                addModifiedExceptionsIcs(reservation, message, attendees, messages, emailModel);
            }
        }
    }

    /**
     * Send invitations that remove the room from the meeting.
     *
     * @param reservation the reservation
     * @param allRecurrences whether to disconnect all occurrences
     * @param requireReply whether to request a reply
     * @param message room cancellation message
     * @param conferenceId conference call identifier
     */
    public void disconnectInvitations(final RoomReservation reservation,
            final boolean allRecurrences, final boolean requireReply, final String message,
            final Integer conferenceId) {
        final EventHandlerContext context = ContextStore.get().getEventHandlerContext();

        final String from = this.employeeService.getEmployeeEmail(reservation.getRequestedBy());
        final String locale = EmailNotificationHelper.getLocaleForEmail(from);
        final String[] attendees = IcsAttendeesHelper.prepareAttendees(reservation);
        final String invitationType = EmailModel.TYPE_DISCONNECT;

        final Map<String, String> messages = getSendMailMessages(locale);
        final boolean recurring = StringUtil.notNullOrEmpty(reservation.getRecurringRule());

        final EmailModel emailModel = new EmailModel(invitationType, conferenceId, recurring,
            requireReply, allRecurrences, locale);

        final Map<String, Object> dataModel = DataModelHelper.prepareDataModel(messages, emailModel,
            reservation, message, this.timeZoneCache);
        final IcsModel icsModel =
                setupIcsModel(messages, emailModel, dataModel, reservation, reservation);

        final String startDate = (String) dataModel.get(IcsConstants.DATE_START);
        IcsAttachmentHelper.addIcsToAttachments(context, icsModel, attendees, startDate,
            emailModel.getAttachments(), false);
        // create the organizer attachment
        IcsAttachmentHelper.addIcsToAttachments(context, icsModel, attendees, startDate,
            emailModel.getOrgAttachments(), true);

        final IcsMessage emailMessage = createEmailMessage(allRecurrences, locale, dataModel);
        // send the email
        emailMessage.setMailFrom(from);
        emailMessage.setMailTo(attendees);
        MessageHelper.sendMessage(emailModel.getAttachments(), emailMessage, this.mailPreferences);

        final IcsMessage reqEmailMessage = emailMessage.copy();

        // send to the organizer
        reqEmailMessage.setMailFrom(EmailNotificationHelper.getServiceEmail());
        reqEmailMessage.setMailTo(from);
        MessageHelper.sendMessage(emailModel.getOrgAttachments(), reqEmailMessage,
            this.mailPreferences);
    }

    /**
     * When canceling a full series, add attachments to cancel occurrences that were moved to a
     * different date.
     *
     * @param reservation the primary reservation being cancelled
     * @param message the user's cancellation message
     * @param attendees the attendees to notify
     * @param messages localized messages to use in the ics file
     * @param emailModel the email model
     */
    private void addCancelledExceptionsIcs(final RoomReservation reservation, final String message,
            final String[] attendees, final Map<String, String> messages,
            final EmailModel emailModel) {

        final EventHandlerContext context = ContextStore.get().getEventHandlerContext();
        final EmailModel exceptionModel =
                new EmailModel(EmailModel.TYPE_CANCEL, emailModel.getConferenceId(), true,
                    emailModel.isRequireReply(), false, emailModel.getLocale());

        for (final RoomReservation cancelledRes : reservation.getCreatedReservations()) {
            if (cancelledRes.getRecurringDateModified() != 0) {
                final Map<String, Object> exDataModel = DataModelHelper.prepareDataModel(messages,
                    exceptionModel, cancelledRes, message, this.timeZoneCache);
                final IcsModel exIcsModel = setupIcsModel(messages, exceptionModel, exDataModel,
                    cancelledRes, cancelledRes);
                IcsAttachmentHelper.addIcsToAttachments(context, exIcsModel, attendees,
                    (String) exDataModel.get(IcsConstants.DATE_START), emailModel.getAttachments(),
                    false);
                IcsAttachmentHelper.addIcsToAttachments(context, exIcsModel, attendees,
                    (String) exDataModel.get(IcsConstants.DATE_START),
                    emailModel.getOrgAttachments(), true);
            }
        }
    }

    /**
     * When modifying a full series, add attachments for occurrences that don't match the series.
     *
     * @param reservation the primary reservation being edited
     * @param message the user's message to include in the update
     * @param attendees the attendees to notify
     * @param messages localized messages to use in the ics file
     * @param emailModel the email model
     */
    private void addModifiedExceptionsIcs(final RoomReservation reservation, final String message,
            final String[] attendees, final Map<String, String> messages,
            final EmailModel emailModel) {

        final EventHandlerContext context = ContextStore.get().getEventHandlerContext();
        final EmailModel exceptionModel =
                new EmailModel(EmailModel.TYPE_UPDATE, emailModel.getConferenceId(), true,
                    emailModel.isRequireReply(), false, emailModel.getLocale());

        for (final RoomReservation occurrence : reservation.getCreatedReservations()) {
            if (!ReservationEquivalenceChecker.isEquivalent(reservation, occurrence)) {
                final Map<String, Object> exDataModel = DataModelHelper.prepareDataModel(messages,
                    exceptionModel, occurrence, message, this.timeZoneCache);
                final IcsModel exIcsModel = setupIcsModel(messages, exceptionModel, exDataModel,
                    occurrence, occurrence);
                IcsAttachmentHelper.addIcsToAttachments(context, exIcsModel, attendees,
                    (String) exDataModel.get(IcsConstants.DATE_START), emailModel.getAttachments(),
                    false);
                IcsAttachmentHelper.addIcsToAttachments(context, exIcsModel, attendees,
                    (String) exDataModel.get(IcsConstants.DATE_START),
                    emailModel.getOrgAttachments(), true);
            }
        }
    }

    /**
     * Create the email message for sending to the attendees.
     *
     * @param allRecurrences if the email is for an entire recurring meeting
     * @param locale the locale to use
     * @param dataModel the data model for the meeting
     * @return the message
     */
    private IcsMessage createEmailMessage(final boolean allRecurrences, final String locale,
            final Map<String, Object> dataModel) {
        final String bodyTemplate;
        if (allRecurrences) {
            bodyTemplate = IcsConstants.BODY_REC_MSG_ID;
        } else {
            bodyTemplate = IcsConstants.BODY_MSG_ID;
        }
        final IcsMessage emailMessage =
                createMessage(IcsConstants.SUBJECT_MSG_ID, bodyTemplate, dataModel, locale);
        // outlook remove line breaks hack
        emailMessage.setBody(fixLineBreaks(emailMessage.getBody()));
        return emailMessage;
    }

    /**
     * Add extra spaces to line breaks so they display correctly in Outlook.
     *
     * @param text the text to expand
     * @return expanded text
     */
    private String fixLineBreaks(final String text) {
        return text.replaceAll("\r\n", "   \r\n").replaceAll("\n", "   \n");
    }

    /**
     * Setter for the conference call reservation DataSource.
     *
     * @param dataSource reservation data source to set
     */
    public final void setReservationDataSource(
            final IConferenceCallReservationDataSource dataSource) {
        this.reservationDs = dataSource;
    }

    /**
     * Setter for the time zone cache.
     *
     * @param cache the time zone cache to set
     */
    public final void setTimeZoneCache(final TimeZoneCache cache) {
        this.timeZoneCache = cache;
    }

    /**
     * Set the mail preferences.
     *
     * @param mailPreferences the mail preferences
     */
    public final void setMailPreferences(final MailPreferences mailPreferences) {
        this.mailPreferences = mailPreferences;
    }

    /**
     * Set the employee service.
     *
     * @param employeeService the employee service
     */
    public final void setEmployeeService(final IEmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    /**
     * Prepare a simplified data model and generate the summary and description for the ICS file.
     *
     * @param messages the messages map
     * @param emailModel the email type model
     * @param dataModel the email data model
     * @param reservation the meeting reservation
     * @param originalReserv the original meeting reservation, if applicable
     * @return the ICS email model
     */
    private IcsModel setupIcsModel(final Map<String, String> messages, final EmailModel emailModel,
            final Map<String, Object> dataModel, final RoomReservation reservation,
            final RoomReservation originalReserv) {

        final String uid = IcsAttachmentHelper.generateUid(emailModel, reservation);
        final Map<String, Object> icsDataModel =
                DataModelHelper.getIcsDataModelMap(messages, emailModel, dataModel);

        final IcsMessage ics = createMessage(IcsConstants.SUBJECT_MSG_ID, IcsConstants.ICS_MSG_ID,
            icsDataModel, emailModel.getLocale());
        // outlook remove line breaks hack
        ics.setBody(fixLineBreaks(ics.getBody()));

        final MeetingLocationModel location = new MeetingLocationModel();
        location.setTimezone((String) dataModel.get("timezone"));
        location.setLocation((String) dataModel.get("location"));

        final IcsModel model;
        if (emailModel.isCancel()) {
            model = createCancelIcsModel(emailModel, reservation, uid, ics, location);
        } else {
            model = createIcsModel(emailModel, reservation, originalReserv, uid, ics, location);
        }
        return model;
    }

    /**
     * Create an ICS model that represents a new invitation or update.
     *
     * @param emailModel the email model
     * @param reservation the reservation
     * @param originalReserv the original reservation (matches the reservation unless updating an
     *            occurrence)
     * @param uid the ICAL UID to use
     * @param ics the ICAL subject and body
     * @param location the meeting location model
     * @return the ICS model
     */
    private IcsModel createIcsModel(final EmailModel emailModel, final RoomReservation reservation,
            final RoomReservation originalReserv, final String uid, final IcsMessage ics,
            final MeetingLocationModel location) {
        final IcsModel model = new IcsModel(location, emailModel, ics.getSubject(), ics.getBody(),
            this.employeeService.getEmployeeEmail(reservation.getRequestedBy()), uid,
            reservation.getTimePeriod());

        if (emailModel.isAllRecurrences()) {
            model.setUntilDate(
                MeetingInformationHelper.calcUntilDateTime(reservation, location.getTimezone()));
            model.setRecurrence((AbstractIntervalPattern) reservation.getRecurrence());

            if (emailModel.isChange() && !emailModel.isDisconnect()) {
                model.setExceptionDates(MeetingInformationHelper
                    .getCancelledRecurrences(reservation, this.reservationDs));
            }
        } else if (emailModel.isRecurring() && reservation.getRecurringDateModified() == 0) {
            model.setRecurrenceId(originalReserv.getStartDateTime());
        }

        return model;
    }

    /**
     * Create an ICS model that represents a meeting cancellation.
     *
     * @param emailModel the email model
     * @param reservation the reservation
     * @param uid the ICAL UID to use
     * @param ics the ICAL subject and body
     * @param location the meeting location model
     * @return the ICS model - can return null if there's nothing to cancel, when all occurrences
     *         have been moved to a different date
     */
    private IcsModel createCancelIcsModel(final EmailModel emailModel,
            final RoomReservation reservation, final String uid, final IcsMessage ics,
            final MeetingLocationModel location) {

        TimePeriod timePeriod = null;
        if (emailModel.isAllRecurrences() && reservation.getRecurringDateModified() != 0) {
            // set start date to the first occurrence having its original date
            // If not found, i.e. all occurrence are on modified dates are
            // modified, do not generate a series ICS.
            for (final RoomReservation occurrence : reservation.getCreatedReservations()) {
                if (occurrence.getRecurringDateModified() == 0) {
                    // use this one's date for the ics
                    timePeriod = occurrence.getTimePeriod();
                    break;
                }
            }
        } else {
            timePeriod = reservation.getTimePeriod();
        }

        IcsModel model = null;
        if (timePeriod != null) {
            model = new IcsModel(location, emailModel, ics.getSubject(), ics.getBody(),
                this.employeeService.getEmployeeEmail(reservation.getRequestedBy()), uid,
                timePeriod);

            if (emailModel.isAllRecurrences()) {
                model.setUntilDate(MeetingInformationHelper.calcUntilDateTime(reservation,
                    location.getTimezone()));
                model.setRecurrence((AbstractIntervalPattern) reservation.getRecurrence());

            } else if (emailModel.isRecurring() && reservation.getRecurringDateModified() == 0) {
                model.setRecurrenceId(reservation.getStartDateTime());
            }
        }

        return model;
    }

    /**
     * Creates the email message.
     *
     * @param subjectId the subject message id
     * @param bodyId the body message id
     * @param dataModel the data model
     * @param locale the locale
     * @return the email message
     */
    private IcsMessage createMessage(final String subjectId, final String bodyId,
            final Map<String, Object> dataModel, final String locale) {
        final IcsMessage message = new IcsMessage();

        message.setActivityId(IcsConstants.ACTIVITY_ID);
        message.setReferencedBy(IcsConstants.REFERENCED_BY);
        message.setSubjectMessageId(subjectId);
        message.setBodyMessageId(bodyId);
        message.setDataModel(dataModel);
        message.format(locale);

        return message;
    }

    /**
     * Put all messages of mail for send mail in a map.
     *
     * @param locale locale of user
     * @return Map with the messages
     */
    private Map<String, String> getSendMailMessages(final String locale) {
        final Map<String, String> messages = ReservationsContextHelper.localizeMessages(
            IcsConstants.REFERENCED_BY, locale, IcsConstants.SUBJECT_REC_MSG,
            IcsConstants.SUBJECT_CAN_MSG, IcsConstants.SUBJECT_UPD_MSG,
            IcsConstants.BODY_INVITE_MSG, IcsConstants.BODY_CANCEL_MSG,
            IcsConstants.BODY_INVREC_MSG, IcsConstants.BODY_DTS_LIST_MSG,
            IcsConstants.BODY_CAN_LIST_MSG, IcsConstants.BODY_START_DT_MSG,
            IcsConstants.BODY_START_TM_MSG, IcsConstants.BODY_END_TM_MSG);

        messages.put(IcsConstants.CONF_CALL_MEETING_LOCATION, ReservationsContextHelper
            .localizeMessage("OUTLOOK_PLUGIN", locale, IcsConstants.CONF_CALL_MEETING_LOCATION));

        return messages;
    }

}
