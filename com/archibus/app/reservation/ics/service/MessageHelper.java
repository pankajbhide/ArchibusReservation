package com.archibus.app.reservation.ics.service;

import java.io.*;
import java.util.*;

import javax.mail.internet.MimeBodyPart;

import com.archibus.app.reservation.ics.domain.IcsMessage;
import com.archibus.config.MailPreferences;
import com.archibus.context.ContextStore;
import com.archibus.jobmanager.JobManager;

import freemarker.core.Environment;
import freemarker.template.*;

/**
 * Utility class. Provides methods to send emails with MimeBodyPart attachments.
 */
public final class MessageHelper {

    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private MessageHelper() {
    }

    /**
     * Sends the email message.
     *
     * @param attachments the attachments to include
     * @param emailMessage the email message
     * @param mailPreferences mail preferences
     */
    public static void sendMessage(final List<MimeBodyPart> attachments,
            final IcsMessage emailMessage, final MailPreferences mailPreferences) {

        final EmailJob emailJob = new EmailJob(emailMessage, attachments, mailPreferences);
        final JobManager.ThreadSafe jobManager = ContextStore.get().getJobManager();
        jobManager.startJob(emailJob);
    }

    /**
     * Process the message template and replace FreeMarker variables.
     *
     * @param name the message code
     * @param template the message template code
     * @param datamodel data model object
     * @return the formatted text string
     * @throws TemplateException FreeMarker Template exception
     */
    public static String processTemplate(final String name,
            final String template, final Map<String, Object> datamodel) throws TemplateException {

        try {
            final Configuration cfg = new Configuration();
            // fix KB3031484- do not format the number type field (Guo 2011/6/3)
            cfg.setNumberFormat("#");

            final java.io.Reader reader = new StringReader(template);

            final Template tpl = new Template(name, reader, cfg);

            final StringWriter swriter = new StringWriter();
            tpl.process(datamodel, swriter);

            final StringBuffer buffer = swriter.getBuffer();
            return buffer.toString();
        } catch (final IOException exception) {
            throw new TemplateException(exception.getMessage(), exception, Environment.getCurrentEnvironment());
        }
    }

}
