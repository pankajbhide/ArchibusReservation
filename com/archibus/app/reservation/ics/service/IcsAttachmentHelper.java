package com.archibus.app.reservation.ics.service;

import java.io.*;
import java.util.List;

import javax.activation.DataHandler;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.log4j.Logger;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.RoomReservation;
import com.archibus.app.reservation.ics.domain.*;
import com.archibus.app.reservation.util.ReservationsContextHelper;
import com.archibus.jobmanager.EventHandlerContext;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.validate.ValidationException;

/**
 * Copyright (C) ARCHIBUS, Inc. All rights reserved.
 */
/**
 * Utility class. Provides methods to generate the ICS attachments for the invitations.
 * <p>
 *
 * @author PROCOS
 * @since 23.2
 */
public final class IcsAttachmentHelper {

    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private IcsAttachmentHelper() {
    }

    /**
     * Add the ICS Calendar to the attachments list.
     *
     * @param context the current context
     * @param icsModel the ICS model to use
     * @param attendees the attendees
     * @param startDate the formatted start date string
     * @param attachments the list where to add the MimeBodyPart attachment
     * @param requestedByIcs flag indicating if should send to the organizer
     */
    public static void addIcsToAttachments(final EventHandlerContext context,
            final IcsModel icsModel, final String[] attendees, final String startDate,
            final List<MimeBodyPart> attachments, final boolean requestedByIcs) {

        final net.fortuna.ical4j.model.Calendar icsCalendar =
                IcsCalendarBuilder.createIcsAttachment(icsModel, attendees, requestedByIcs);
        final String fileName = generateIcsFileName(icsModel.getUid().toString(), startDate,
            icsModel.getEmailModel().isCancel(), requestedByIcs);
        addAsAttachment(context, icsModel.getEmailModel().getLocale(), icsCalendar, fileName,
            attachments);
    }

    /**
     * Add the ICS object as a MimeBodyPart attachment.
     *
     * @param context the current context
     * @param locale the locale
     * @param icsCalendar the ICS object
     * @param filename the attachment filename
     * @param attachments the attachment list
     */
    private static void addAsAttachment(final EventHandlerContext context, final String locale,
            final net.fortuna.ical4j.model.Calendar icsCalendar, final String filename,
            final List<MimeBodyPart> attachments) {
        try {
            final ByteArrayOutputStream baos =
                    IcsAttachmentHelper.toByteArrayOutputStream(icsCalendar);

            final javax.activation.DataSource aAttachment =
                    new ByteArrayDataSource(baos.toByteArray(), "application/octet-stream");
            final MimeBodyPart attachmentPart = new MimeBodyPart();

            attachmentPart.setDataHandler(new DataHandler(aAttachment));
            attachmentPart.setFileName(sanitizeCrlf(filename));
            attachments.add(attachmentPart);

        } catch (IOException | MessagingException | ValidationException ex) {
            context.addResponseParameter("message", ReservationsContextHelper.localizeMessage(
                IcsConstants.REFERENCED_BY, locale, IcsConstants.ERROR_MESSAGE_ID));
            Logger.getLogger(IcsAttachmentHelper.class).error("Failed sending invitations", ex);
        }
    }

    /**
     * Create the file name for the ICS attachment.
     *
     * @param reserveId the reservation id
     * @param dateStart the start date
     * @param cancelIcs is canceling?
     * @param requestedByIcs is the requested by?
     * @return the file name for the ICS attachment
     */
    private static String generateIcsFileName(final String reserveId, final String dateStart,
            final boolean cancelIcs, final boolean requestedByIcs) {

        final StringBuilder builder =
                new StringBuilder("reservation-").append(reserveId).append('-').append(dateStart);

        if (cancelIcs) {
            builder.append("-cancel");
        }

        if (requestedByIcs) {
            builder.append("-requestedBy");
        }

        builder.append(".ics");

        return builder.toString();
    }

    /**
     * Generate the iCalendar UID for the given reservation.
     *
     * @param emailModel the email type model
     * @param reservation the reservation
     * @return the UID
     */
    public static String generateUid(final EmailModel emailModel,
            final RoomReservation reservation) {
        String uid;
        if (emailModel.isConferenceCall()) {
            uid = String.valueOf(emailModel.getConferenceId());
        } else if (emailModel.isRecurring()) {
            uid = String.valueOf(reservation.getParentId());
        } else {
            uid = String.valueOf(reservation.getReserveId());
        }
        if (!emailModel.isAllRecurrences() && reservation.getRecurringDateModified() != 0) {
            uid += Constants.DOT + reservation.getOccurrenceIndex();
        }
        return uid;
    }

    /**
     * Write to the ByteArrayOutputStream.
     *
     * @param icsCalendar the ICS object
     * @return the ByteArrayOutputStream
     * @throws IOException if unable to write to output stream
     */
    private static ByteArrayOutputStream toByteArrayOutputStream(
            final net.fortuna.ical4j.model.Calendar icsCalendar) throws IOException {
        final CalendarOutputter outputter = new CalendarOutputter();
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Read the output stream
        outputter.output(icsCalendar, baos);
        return baos;
    }

    /**
     * Sanitizes a user input string: neutralizes CRLF sequences.
     *
     * @param input The user input string.
     * @return The sanitized string.
     */
    public static String sanitizeCrlf(final String input) {
        return input.replace("\r", "").replace("\n", "");
    }
}
