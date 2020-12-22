package com.archibus.app.reservation.ics.domain;

import java.util.*;

import javax.mail.internet.MimeBodyPart;

import net.fortuna.ical4j.model.property.Method;

/**
 * Represents the Email model for the ICS generation.
 * <p>
 *
 * @author PROCOS
 * @since 23.2
 *
 */
public class EmailModel {

    /** Invitation type for new invitations. */
    public static final String TYPE_NEW = "new";

    /** Invitation type for cancel invitations. */
    public static final String TYPE_CANCEL = "cancel";

    /** Invitation type for invitation updates. */
    public static final String TYPE_UPDATE = "update";

    /** Invitation type for invitation updates meant to remove the room from the meeting. */
    public static final String TYPE_DISCONNECT = "disconnect";

    /** The email invitation type. */
    private final String invitationType;

    /** Conference Reservation ID if it is a conference call invitation. */
    private final Integer conferenceId;

    /** Flag to check if it is a recurring meeting. */
    private final boolean recurring;

    /** Flag to check if the invite requires a reply. */
    private final boolean requireReply;

    /** Flag to check if the changes affect all the recurrences. */
    private final boolean allRecurrences;

    /** The locale to use. */
    private final String locale;

    /** Attachments to send to the attendees. */
    private final List<MimeBodyPart> attachments =
            new ArrayList<MimeBodyPart>();

    /** Attachments to send to the meeting organizer. */
    private final List<MimeBodyPart> orgAttachments =
            new ArrayList<MimeBodyPart>();

    /**
     * Create an email model based on the parameters.

     * @param inviteType The email invitation type
     * @param confCallId conference call identifier
     * @param recur Is it a recurring meeting?
     * @param reqReply Does the invite requires a reply?
     * @param allRecurs Do the changes affect all the recurrences?
     * @param theLocale in which language the message should be sent
     */
    public EmailModel(final String inviteType, final Integer confCallId,
            final boolean recur, final boolean reqReply,
            final boolean allRecurs, final String theLocale) {
        this.invitationType = inviteType;
        this.conferenceId = confCallId;
        this.recurring = recur;
        this.requireReply = reqReply;
        this.allRecurrences = allRecurs;
        this.locale = theLocale;

        if (!isCancel() && !isChange() && !isNew()) {
            throw new IllegalArgumentException(
                "Invalid invitation type for EmailModel");
        }
    }

    /**
     * Returns the method type for the ICS object.
     *
     * @return The Method type
     */
    public final Method getMethod() {
        Method method;
        if (isCancel()) {
            method = Method.CANCEL;
        } else {
            method = Method.REQUEST;
        }
        return method;
    }

    /**
     * Is the email model a cancellation type?
     *
     * @return the type assessment
     */
    public final boolean isCancel() {
        return this.invitationType.equals(TYPE_CANCEL);
    }

    /**
     * Is the email model an update type?
     *
     * @return the type assessment
     */
    public final boolean isChange() {
        return this.invitationType.equals(TYPE_UPDATE) || this.isDisconnect();
    }

    /**
     * Is the email model a new invitation type?
     *
     * @return the type assessment
     */
    public final boolean isNew() {
        return this.invitationType.equals(TYPE_NEW);
    }

    /**
     * Is the email model a disconnect invitation type?
     * @return the type assessment
     */
    public final boolean isDisconnect() {
        return this.invitationType.equals(TYPE_DISCONNECT);
    }

    /**
     * Is the email model for a recurring meeting?
     *
     * @return the type assessment
     */
    public final boolean isRecurring() {
        return this.recurring;
    }

    /**
     * Is the email model for a conference call?
     *
     * @return the type assessment
     */
    public final boolean isConferenceCall() {
        return this.conferenceId != null;
    }

    /**
     * Get the conference identifier for this email. In case of a
     * recurring reservation, this is the conference id of the
     * first occurrence.
     *
     * @return the conference identifier
     */
    public final Integer getConferenceId() {
        return this.conferenceId;
    }

    /**
     * Does the email model require a reply to the invitation?
     *
     * @return the assessment
     */
    public final boolean isRequireReply() {
        return this.requireReply;
    }

    /**
     * Is the email model for a change that affects all the recurrences?
     *
     * @return the assessment
     */
    public final boolean isAllRecurrences() {
        return this.allRecurrences;
    }

    /**
     * Get the locale to use.
     * @return the locale
     */
    public final String getLocale() {
        return this.locale;
    }

    /**
     * Get the attachments to send to the attendees.
     *
     * @return the attachments
     */
    public final List<MimeBodyPart> getAttachments() {
        return this.attachments;
    }

    /**
     * Get the attachments to send to the organizer.
     *
     * @return the attachments
     */
    public final List<MimeBodyPart> getOrgAttachments() {
        return this.orgAttachments;
    }

}
