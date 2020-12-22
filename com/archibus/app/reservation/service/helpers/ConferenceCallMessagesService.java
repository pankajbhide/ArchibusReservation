package com.archibus.app.reservation.service.helpers;

import java.util.*;

import com.archibus.app.common.notification.domain.Notification;
import com.archibus.app.common.notification.message.NotificationDataModel;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.service.ISpaceService;
import com.archibus.app.reservation.util.StringTranscoder;
import com.archibus.utility.StringUtil;

/**
 * Service class that can build messages related to conferencec call reservations.
 *
 * Managed by Spring.
 *
 * @author Yorik Gerlo
 * @since 21.3
 */
public class ConferenceCallMessagesService extends ReservationConflictsMessagesService {

    /** Separator for inserting the conference call locations in a reservation comment. */
    private static final String CONFERENCE_CALL_LOCATIONS_SEPARATOR =
            "CONFERENCE_CALL_LOCATIONS_SEPARATOR";

    /** Referenced_by value for the messages used for conference call reservations. */
    private static final String REFERENCED_BY_CONFCALL = "CONFCALL_WFR";

    /**
     * Insert the conference call locations in the reservations' comment field.
     *
     * @param reservations the reservations in the conference call
     * @param spaceService the service interface to get the location description
     * @param additionalComments additional comments to place in the locations template (optional)
     */
    public void insertConferenceCallLocations(final List<RoomReservation> reservations,
            final ISpaceService spaceService, final String additionalComments) {
        final ReservationMessage localizedMessage =
                processLocationTemplate(reservations, spaceService);
        final String originalBody = StringUtil.notNull(reservations.get(0).getComments());

        final String updatedBody =
                insertMessage(originalBody, localizedMessage, additionalComments, true);
        final String updatedComments = StringTranscoder.stripHtml(updatedBody);
        for (final RoomReservation reservation1 : reservations) {
            reservation1.setHtmlComments(updatedBody);
            reservation1.setComments(updatedComments);
        }
    }

    /**
     * Process the conference call locations template to a reservation message. The subject contains
     * the localized separator and the body contains the localized list of locations.
     *
     * @param reservations the reservations with the rooms to include
     * @param spaceService space service to retrieve location details
     * @return message with the separator as the subject and the locations as the body
     */
    public ReservationMessage processLocationTemplate(final List<RoomReservation> reservations,
            final ISpaceService spaceService) {
        final RoomReservation primaryReservation = reservations.get(0);
        final Notification notification = new Notification();
        // For localization, set the separator as the subject and the template as the body.
        notification.setSubject(this.getNotificationMessageDao().getByPrimaryKey(ACTIVITY_ID,
            REFERENCED_BY_CONFCALL, CONFERENCE_CALL_LOCATIONS_SEPARATOR));
        notification.setBody(this.getNotificationMessageDao().getByPrimaryKey(ACTIVITY_ID,
            REFERENCED_BY_CONFCALL, "CONFERENCE_CALL_LOCATIONS"));

        // Build the location data model for all rooms.
        final List<Map<String, Object>> locations =
                new ArrayList<Map<String, Object>>(reservations.size());
        for (final RoomReservation reservation : reservations) {
            locations.add(spaceService.getLocationDataModel(reservation));
        }
        final NotificationDataModel dataModel = new NotificationDataModel();
        dataModel.setDataModel(new HashMap<String, Object>());
        dataModel.getDataModel().put("locations", locations);

        // Localize the template and fill in the data.
        return formatMessage(primaryReservation.getEmail(), notification, dataModel);
    }

    /**
     * Strip the conference call locations from the reservation's comment field.
     *
     * @param email organizer email address
     * @param comments the comments to remove the locations from
     * @return the reservation comments without the conference call locations
     */
    public String stripConferenceCallLocations(final String email, final String comments) {
        String result = comments;
        if (StringUtil.notNullOrEmpty(comments)) {
            final Notification notification = new Notification();
            // For localization, set the separator as the subject and the template as the body.
            notification.setSubject(this.getNotificationMessageDao().getByPrimaryKey(ACTIVITY_ID,
                REFERENCED_BY_CONFCALL, CONFERENCE_CALL_LOCATIONS_SEPARATOR));

            result = stripMessage(email, comments, notification);
        }
        return result;
    }

}
