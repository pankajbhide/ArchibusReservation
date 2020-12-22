package com.archibus.app.reservation.ics.service;

import java.util.*;

import javax.mail.*;
import javax.mail.internet.*;

import org.apache.commons.lang.StringUtils;

import com.archibus.app.reservation.ics.domain.IcsMessage;
import com.archibus.config.MailPreferences;
import com.archibus.utility.*;

/**
 * Copyright (C) ARCHIBUS, Inc. All rights reserved.
 */
/**
 * Utility class. Provides methods to initialize email related objects.
 *<p>
 *
 * Used by Reservations to send ICS e-mails.
 *
 * @author PROCOS
 * @since 23.1
 */
public final class EmailBuilder {

    /** Property id for enabling or disabling SMTP authentication. */
    private static final String MAIL_SMTP_AUTH = "mail.smtp.auth";

    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private EmailBuilder() {
    }

    /**
     * Create a multipart body from the given message body and attachments.
     * @param body the message body
     * @param attachments the attachments
     * @return the multipart body
     * @throws MessagingException when building the multipart body fails
     */
    public static Multipart buildMultipartMessage(final String body, final List<MimeBodyPart> attachments)
            throws MessagingException {
        final Multipart multipart = new MimeMultipart();

        // set body
        final MimeBodyPart messageBodyPart = new MimeBodyPart();
        multipart.addBodyPart(messageBodyPart);
        messageBodyPart.setText(body);

        for (final MimeBodyPart attachment : attachments) {
            if (attachment != null) {
                multipart.addBodyPart(attachment);
            }
        }

        return multipart;
    }

    /**
     * Build a mail session for sending an email.
     *
     * @param preferences the connection information
     * @return the mail session
     */
    public static Session buildMailSession(final MailPreferences preferences) {
        final Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.host", preferences.getHostName());
        props.put("mail.smtp.port", preferences.getHostPort());
        props.put("mail.smtp.user", preferences.getHostUsername());
        props.put("mail.smtp.starttls.enable", preferences.getStarttls());

        Session mailSession;
        if (StringUtil.notNullOrEmpty(preferences.getHostPassword())) {
            props.put(MAIL_SMTP_AUTH, "true");
            final String userName = preferences.getHostUsername();
            final String password = preferences.getHostPassword();

            final EMailAuthenticator auth = new EMailAuthenticator();
            auth.setUser(userName);
            auth.setPassword(password);

            mailSession = Session.getDefaultInstance(props, auth);
        } else {
            props.put(MAIL_SMTP_AUTH, "false");
            mailSession = Session.getDefaultInstance(props);
        }
        return mailSession;
    }

    /**
     * Convert an ICS message to a sendable / loggable mail message without attachments.
     *
     * @param message the ICS message to convert
     * @param connectionInfo the connection info
     * @return the MailMessage to log
     */
    public static MailMessage buildMailMessage(final IcsMessage message,
            final MailPreferences connectionInfo) {
        final MailMessage messageToLog = new MailMessage();
        messageToLog.setActivityId(message.getActivityId());
        messageToLog.setFrom(message.getMailFrom());
        messageToLog.setTo(StringUtils.join(message.getMailTo(), ','));
        messageToLog.setSubject(message.getSubject());
        messageToLog.setText(message.getBody());

        messageToLog.setHost(connectionInfo.getHostName());
        messageToLog.setPort(connectionInfo.getHostPort());
        messageToLog.setUser(connectionInfo.getHostUsername());
        messageToLog.setPassword(connectionInfo.getHostPassword());

        return messageToLog;
    }

}
