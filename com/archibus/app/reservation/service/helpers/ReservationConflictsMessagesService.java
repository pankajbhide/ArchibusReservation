package com.archibus.app.reservation.service.helpers;

import java.util.*;

import com.archibus.app.common.notification.domain.Notification;
import com.archibus.app.common.notification.message.NotificationDataModel;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.util.*;
import com.archibus.utility.StringUtil;

/**
 * Service class that can build messages related to reservation conflicts.
 *
 * Managed by Spring.
 *
 * @author PROCOS
 * @since 23.2
 */
public class ReservationConflictsMessagesService extends ReservationMessagesService {

    /** New line character. */
    private static final String NEWLINE = "\n";

    /** Referenced_by value for messages used primarily in the Outlook Plugin. */
    private static final String REFERENCED_BY_PLUGIN = "OUTLOOK_PLUGIN";

    /** Referenced_by value for messages defined for the room reservation wfr. */
    private static final String REFERENCED_BY_ROOMRES_WFR = "ADDROOMRESERVATION_WFR";

    /** Separator for the room conflicts description. */
    private static final String ROOM_CONFLICTS_SEPARATOR = "ROOM_CONFLICTS_SEPARATOR";

    /** Conflicts description for a single conflict. */
    private static final String ONE_CONFLICT_DESCRIPTION = "ONE_CONFLICT_DESCRIPTION";

    /** Conflicts description for 2 or more conflicts. */
    private static final String CONFLICTS_DESCRIPTION = "CONFLICTS_DESCRIPTION";

    /** Conflicts description for a conference call reservation with room conflicts on a single date. */
    private static final String ONE_CONFLICT_DESCRIPTION_CONFCALL = "ONE_CONFLICT_DESCRIPTION_CONFCALL";

    /** Conflicts description for a conference call reservation with room conflicts on 2 or more dates. */
    private static final String CONFLICTS_DESCRIPTION_CONFCALL = "CONFLICTS_DESCRIPTION_CONFCALL";

    /** Separate message for how to resolve conflicts, to include after the specific description on a new line. */
    private static final String RESOLVE_CONFLICTS_DESCRIPTION = "RESOLVE_CONFLICTS_DESCRIPTION";

    /**
     * Create the conflicts description for the given reservation and set of conflicts.
     *
     * @param reservation the parent reservation
     * @param conflictDates the dates with conflicts
     * @return the formatted message, with the separator as the subject and description as body
     */
    public ReservationMessage createConflictsDescription(final RoomReservation reservation,
            final SortedSet<Date> conflictDates) {
        ReservationMessage message = null;
        if (conflictDates != null && !conflictDates.isEmpty()) {
            final Notification notification = initializeConflictsTemplate(reservation.getConferenceId(),
                conflictDates.size());
            final NotificationDataModel dataModel = createConflictsDataModel(reservation, conflictDates);

            // generate the resolve description and include it in the data model
            final ReservationMessage resolveDesc = formatMessage(reservation.getEmail(),
                    this.initializeResolveTemplate(), dataModel);
            dataModel.getDataModel().put("resolveDesc", resolveDesc.getBody());

            message = formatMessage(reservation.getEmail(), notification, dataModel);
        }
        return message;
    }

    /**
     * Insert the conference call locations in the reservations' comment field.
     *
     * @param reservation the parent reservation of the recurrence series
     * @param conflictDates the dates with conflicts
     */
    public void insertConflictsDescription(final RoomReservation reservation,
            final SortedSet<Date> conflictDates) {
        final ReservationMessage localizedMessage =
                createConflictsDescription(reservation, conflictDates);

        if (localizedMessage != null) {
            final String updatedBody = insertMessage(StringUtil.notNull(reservation.getComments()), localizedMessage,
                    null, false);
            final String updatedComments = StringTranscoder.stripHtml(updatedBody);
            reservation.setHtmlComments(updatedBody);
            reservation.setComments(updatedComments);
        }
    }

    /**
     * Strip the conflicts description from the reservation's comment field.
     *
     * @param email organizer email address
     * @param comments the comments to remove the conflicts description from
     * @return the reservation comments without the conflicts description
     */
    public String stripConflictsDescription(final String email, final String comments) {
        String result = comments;
        if (StringUtil.notNullOrEmpty(comments)) {
            final Notification notification = new Notification();
            // For localization, set the separator as the subject and the template as the body.
            notification.setSubject(this.getNotificationMessageDao().getByPrimaryKey(ACTIVITY_ID,
                REFERENCED_BY_PLUGIN, ROOM_CONFLICTS_SEPARATOR));

            result = stripMessage(email, comments, notification);
        }
        return result;
    }

    /**
     * Insert a given message in the given body text. Can be used to insert a
     * new fragment, to replace an existing fragment or to append to an existing
     * fragment.
     *
     * @param originalBody
     *            original body text
     * @param localizedMessage
     *            localized message to insert
     * @param additionalComments
     *            additional comments to insert with the message
     * @param replace
     *            whether to replace any existing fragment that uses the same
     *            separators
     * @return updated body text
     */
    protected String insertMessage(final String originalBody, final ReservationMessage localizedMessage,
            final String additionalComments, final boolean replace) {
        // add a newline to the separator to ensure better matching
        final String separator = StringUtil.notNull(localizedMessage.getSubject());
        String textToInsert = StringUtil.notNull(localizedMessage.getBody());
        if (!textToInsert.endsWith(NEWLINE)) {
            textToInsert += NEWLINE;
        }
        if (StringUtil.notNullOrEmpty(additionalComments)) {
            textToInsert = textToInsert + NEWLINE + additionalComments + NEWLINE;
        }

        // Determine whether the current body already contains a fragment with the same separator.
        final int indexOfTopSeparator = originalBody.indexOf(separator);
        final int indexOfBottomSeparator =
                originalBody.indexOf(separator, indexOfTopSeparator + separator.length());
        final StringBuffer buffer = new StringBuffer();
        if (indexOfTopSeparator > -1 && indexOfTopSeparator < indexOfBottomSeparator) {
            // The separator was found twice.
            if (replace) {
                // Replace what's in between.
                buffer.append(originalBody.substring(0, indexOfTopSeparator));
                buffer.append(separator);
                buffer.append(NEWLINE);
                buffer.append(textToInsert);
                buffer.append(separator);
                buffer.append(originalBody.substring(indexOfBottomSeparator + separator.length()));
            } else {
                // Insert above the second separator.
                buffer.append(originalBody.substring(0, indexOfBottomSeparator));
                buffer.append(NEWLINE);
                buffer.append(textToInsert);
                buffer.append(originalBody.substring(indexOfBottomSeparator));
            }
        } else {
            // The separator was not found, so place the template at the beginning.
            buffer.append(separator);
            buffer.append(NEWLINE);
            buffer.append(textToInsert);
            buffer.append(separator);
            buffer.append(NEWLINE);
            buffer.append(originalBody);
        }

        return buffer.toString();
    }

    /**
     * Strip a message indicated by a separator from the comments field.
     * @param email recipient email address for localization
     * @param comments the comments to remove the message from
     * @param notification identifies the message to remove by the separator set as subject
     * @return the comments without the message
     */
    protected String stripMessage(final String email, final String comments, final Notification notification) {
        // Localize the template and fill in the data.
        final ReservationMessage localizedMessage =
                formatMessage(email, notification, new NotificationDataModel());
        final String separator = StringUtil.notNull(localizedMessage.getSubject());

        // Determine whether the current body contains the locations template.
        final int indexOfTopSeparator = comments.indexOf(separator);
        final int indexOfBottomSeparator =
                comments.indexOf(separator, indexOfTopSeparator + separator.length());

        String strippedComments = null;
        if (indexOfTopSeparator > -1 && indexOfTopSeparator < indexOfBottomSeparator) {
            final StringBuffer buffer = new StringBuffer();
            // The separator was found twice. Remove what's in between.
            buffer.append(comments.substring(0, indexOfTopSeparator));
            String part2 = comments.substring(indexOfBottomSeparator + separator.length(),
                comments.length());
            // If present, remove the extra newline which was added after the bottom separator.
            if (part2.startsWith(NEWLINE)) {
                part2 = part2.substring(1);
            }
            buffer.append(part2);
            strippedComments = buffer.toString();
        } else {
            strippedComments = comments;
        }
        return strippedComments;
    }

    /**
     * Initialize the conflicts description with the correct template id's.
     * @param conferenceId the conference id, for checking if it's a conference call reservation
     * @param numberOfConflicts the number of conflicts to report
     * @return the notification template for building the conflicts description
     */
    private Notification initializeConflictsTemplate(final Integer conferenceId, final int numberOfConflicts) {
        final Notification notification = new Notification();
        notification.setSubject(this.getNotificationMessageDao().getByPrimaryKey(ACTIVITY_ID,
                REFERENCED_BY_PLUGIN, ROOM_CONFLICTS_SEPARATOR));

        String bodyTemplateId = null;
        if (conferenceId != null && conferenceId > 0) {
            if (numberOfConflicts == 1) {
                bodyTemplateId = ONE_CONFLICT_DESCRIPTION_CONFCALL;
            } else {
                bodyTemplateId = CONFLICTS_DESCRIPTION_CONFCALL;
            }
        } else {
            if (numberOfConflicts == 1) {
                bodyTemplateId = ONE_CONFLICT_DESCRIPTION;
            } else {
                bodyTemplateId = CONFLICTS_DESCRIPTION;
            }
        }
        notification.setBody(this.getNotificationMessageDao().getByPrimaryKey(ACTIVITY_ID,
                REFERENCED_BY_ROOMRES_WFR, bodyTemplateId));

        return notification;
    }

    /**
     * Initialize a notification object for formatting the template that describes how to resolve a conflict.
     * @return the notification object for formatting the resolution text
     */
    private Notification initializeResolveTemplate() {
        final Notification notification = new Notification();
        notification.setBody(this.getNotificationMessageDao().getByPrimaryKey(ACTIVITY_ID,
                REFERENCED_BY_ROOMRES_WFR, RESOLVE_CONFLICTS_DESCRIPTION));
        return notification;
    }

    /**
     * Create the data model for a conflicts description.
     * @param reservation the reservation (to determine locale and number of occurrences)
     * @param conflictDates the dates with conflicts
     * @return the data model
     */
    private NotificationDataModel createConflictsDataModel(final RoomReservation reservation,
            final SortedSet<Date> conflictDates) {
        final Map<String, Object> dataModel = new HashMap<String, Object>();
        dataModel.put("numOK", reservation.getCreatedReservations().size() - conflictDates.size());
        dataModel.put("numC", conflictDates.size());
        dataModel.put("isConfCall", reservation.getConferenceId() != null && reservation.getConferenceId() > 0);

        // Convert dates to sql dates so freemarker knows to ignore the time part.
        final ArrayList<java.sql.Date> dates = new ArrayList<java.sql.Date>(conflictDates.size());
        for (final Date date: conflictDates) {
            dates.add(new java.sql.Date(date.getTime()));
        }
        if (conflictDates.size() == 1) {
            dataModel.put("cDate", dates.get(0));
        }
        dataModel.put("cDates", dates);
        dataModel.put("url", ReservationsContextHelper.getWebCentralUrl());

        final NotificationDataModel container = new NotificationDataModel();
        container.setDataModel(dataModel);
        return container;
    }

}
