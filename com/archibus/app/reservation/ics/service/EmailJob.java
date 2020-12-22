package com.archibus.app.reservation.ics.service;

import java.util.*;

import javax.mail.*;
import javax.mail.internet.*;

import org.apache.log4j.Logger;

import com.archibus.app.reservation.domain.TimePeriod;
import com.archibus.app.reservation.ics.domain.IcsMessage;
import com.archibus.config.MailPreferences;
import com.archibus.jobmanager.JobBase;
import com.archibus.model.mail.service.MailLoggerDatabaseImpl;
import com.archibus.utility.*;

/**
 * Provides email sending job implementation.
 * <p>
 *
 * @author PROCOS
 * @since 23.2
 *
 */
public class EmailJob extends JobBase {

    /**
     * The email message to be sent.
     */
    private final IcsMessage message;

    /**
     * The attachment MimeBodyPart array.
     */
    private final List<MimeBodyPart> attachments;

    /** The logger. */
    private final Logger logger = Logger.getLogger(EmailJob.class);

    /** Email preferences. */
    private final MailPreferences mailPreferences;

    /**
     * Creates a new Email Job.
     *
     * @param theAttachments the attachments to include, if any
     * @param theMessage the message to send
     * @param mailPreferences email preferences
     */
    public EmailJob(final IcsMessage theMessage, final List<MimeBodyPart> theAttachments,
            final MailPreferences mailPreferences) {
        super();
        this.message = theMessage;
        this.attachments = theAttachments;
        this.mailPreferences = mailPreferences;
    }

    /**
     * The runner method to send the email with or without attachments.
     */
    @Override
    public final void run() {
        try {
            sendEmailWithAttachments();
        } catch (final MessagingException e) {
            this.logger.error("Could not send email with attachment - try without", e);
            sendEmailWithoutAttachments();
        }
    }

    /**
     * Auxiliary method to send the email without attachments. Uses the standard mail sender
     * functionality.
     */
    private void sendEmailWithoutAttachments() {
        final MailMessage email = EmailBuilder.buildMailMessage(this.message, this.mailPreferences);
        final MailSender mailSender = new MailSender();
        mailSender.send(email);
    }

    /**
     * Auxiliary method to send the email with attachments. Adds the message in the notifications
     * log.
     *
     * @throws MessagingException the messaging exception
     */
    private void sendEmailWithAttachments() throws MessagingException {
        final Session mailSession = EmailBuilder.buildMailSession(this.mailPreferences);

        final String subject = this.message.getSubject();
        final String body = this.message.getBody();

        final MimeMessage mimeMessage = new MimeMessage(mailSession);
        mimeMessage.setSubject(IcsAttachmentHelper.sanitizeCrlf(subject));
        mimeMessage.setFrom(new InternetAddress(this.message.getMailFrom()));
        for (final String emailAddress : this.message.getMailTo()) {
            addAsToRecipients(mimeMessage, emailAddress);
        }
        mimeMessage.setContent(EmailBuilder.buildMultipartMessage(body, this.attachments));
        mimeMessage.setSentDate(new Date());

        Transport.send(mimeMessage);

        // log to table afm_notifications_log
        final MailMessage messageToLog =
                EmailBuilder.buildMailMessage(this.message, this.mailPreferences);
        final Date dateSent = new Date();
        messageToLog.setDateSent(dateSent);
        messageToLog.setTimeSent(TimePeriod.clearDate(dateSent));
        messageToLog.setStatus("SENT");
        new MailLoggerDatabaseImpl().logMessage(messageToLog);
    }

    /**
     * Adds the email address as a recipient (To) for the email message.
     *
     * @param mimeMessage the mime message
     * @param emailAddress the email address
     * @throws MessagingException the messaging exception
     */
    private void addAsToRecipients(final MimeMessage mimeMessage, final String emailAddress)
            throws MessagingException {
        mimeMessage.addRecipient(javax.mail.Message.RecipientType.TO,
            new InternetAddress(emailAddress));
    }

}
