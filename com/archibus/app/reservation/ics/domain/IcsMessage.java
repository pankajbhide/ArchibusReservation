package com.archibus.app.reservation.ics.domain;

import java.util.*;

import com.archibus.app.reservation.domain.ReservationMessage;
import com.archibus.app.reservation.ics.service.MessageHelper;
import com.archibus.app.reservation.util.ReservationsContextHelper;

import freemarker.template.TemplateException;

/**
 * Represents the email message to send with the ICS attachments.
 * <p>
 *
 * @author PROCOS
 * @since 23.2
 *
 */
public class IcsMessage extends ReservationMessage {

    /** The email addresses where to send the email to. */
    private String[] mailTo;

    /** The sender email address. */
    private String mailFrom;

    /** The data model to apply on the templates. */
    private Map<String, Object> dataModel;

    /** The message id with for the template to use as the subject. */
    private String subjectMessageId;

    /** The message id with for the template to use as the body. */
    private String bodyMessageId;

    /** The activity id. */
    private String activityId;

    /** The referencedBy. */
    private String referencedBy;

    /**
     * Apply the templates to the current message.
     *
     * @param localeName the locale to use
     */
    public final void format(final String localeName) {
        String localName = localeName;

        if ("DEFAULT".equals(localName)) {
            localName = "";
        }

        final String subjectMsg = ReservationsContextHelper.localizeMessage(this.referencedBy, localName,
                this.subjectMessageId);
        try {
            setSubject(MessageHelper.processTemplate(this.subjectMessageId,
                subjectMsg, this.dataModel));
        } catch (final TemplateException e) {
            setSubject(subjectMsg);
        }

        final String bodyMsg = ReservationsContextHelper.localizeMessage(this.referencedBy, localName,
                this.bodyMessageId);
        try {
            setBody(MessageHelper.processTemplate(this.bodyMessageId, bodyMsg,
                this.dataModel));
        } catch (final TemplateException e) {
            setBody(bodyMsg);
        }
    }

    /**
     * Set the Activity Id.
     *
     * @param activity the activity id
     */
    public final void setActivityId(final String activity) {
        this.activityId = activity;
    }

    /**
     * Set the ReferencedBy.
     *
     * @param refBy the referenced by
     */
    public final void setReferencedBy(final String refBy) {
        this.referencedBy = refBy;
    }

    /**
     * Set the Message Id for the Subject.
     *
     * @param subjectId the subject message id
     */
    public final void setSubjectMessageId(final String subjectId) {
        this.subjectMessageId = subjectId;
    }

    /**
     * Set the Message Id for the Body.
     *
     * @param bodyId the body message id
     */
    public final void setBodyMessageId(final String bodyId) {
        this.bodyMessageId = bodyId;
    }

    /**
     * Set the data model map.
     *
     * @param dataModelMap the data model
     */
    public final void setDataModel(final Map<String, Object> dataModelMap) {
        this.dataModel = dataModelMap;
    }

    /**
     * Set the sender email address.
     *
     * @param from the sender email address
     */
    public final void setMailFrom(final String from) {
        this.mailFrom = from;
    }

    /**
     * Set the email addresses where to send the email to.
     *
     * @param toMails the to email addresses
     */
    public final void setMailTo(final String... toMails) {
        this.mailTo = toMails;
    }

    /**
     * Get the activity id.
     *
     * @return the activity id
     */
    public final String getActivityId() {
        return this.activityId;
    }

    /**
     * Get the sender email address.
     *
     * @return the sender email address
     */
    public final String getMailFrom() {
        return this.mailFrom;
    }

    /**
     * Get the email addresses where to send the email to.
     *
     * @return the email addresses string array
     */
    public final String[] getMailTo() {
        return Arrays.copyOf(this.mailTo, this.mailTo.length);
    }

    /**
     * Create a copy based on the current object.
     *
     * @return the ICS message copy
     */
    public final IcsMessage copy() {
        final IcsMessage message = new IcsMessage();

        message.setActivityId(this.activityId);
        message.setReferencedBy(this.referencedBy);
        message.setSubject(this.getSubject());
        message.setBody(this.getBody());
        message.setDataModel(this.dataModel);

        return message;
    }
}
