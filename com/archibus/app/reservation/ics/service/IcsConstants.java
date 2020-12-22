package com.archibus.app.reservation.ics.service;

/**
 * Copyright (C) ARCHIBUS, Inc. All rights reserved.
 */
/**
 * Provides common ICS constants.
 * <p>
 *
 * @author PROCOS
 * @since 23.2
 *
 */
public final class IcsConstants {

    /** The messages activity Id. */
    public static final String ACTIVITY_ID = "AbWorkplaceReservations";

    /** The referenced by. */
    public static final String REFERENCED_BY = "SENDEMAILINVITATIONS_WFR";

    /** The error message id. */
    public static final String ERROR_MESSAGE_ID = "SENDEMAILINVITATIONSERROR";

    /** Location message id for conference calls. */
    public static final String CONF_CALL_MEETING_LOCATION = "CONFERENCE_CALL_MEETING_LOCATION";

    /** The date format string. */
    public static final String DATE_FORMAT = "yyyyMMdd";

    /** Constant 'mailto:' string. */
    public static final String MAILTO = "mailto:";

    /** Constant 'date_start' string. */
    public static final String DATE_START = "date_start";

    /** Constant value: 60. */
    public static final int CONSTANT60 = 60;

    /** Constant value: 60000. */
    public static final int CONSTANT60000 = 60000;

    /** A semicolon. */
    public static final String SEMICOLON = ";";

    /** Constant 'reserve' table string. */
    public static final String RESERVE_TBL = "reserve";

    /** Constant 'reserve_rm' table string. */
    public static final String RESERVE_RM_TBL = "reserve_rm";

    /** Constant 'messages' table string. */
    public static final String MESSAGES_TBL = "messages";

    /** Reservation name field name (a.k.a. subject). */
    public static final String RESERVATION_NAME = "reservation_name";

    /** Comments field name (a.k.a. body). */
    public static final String COMMENTS = "comments";

    /** The subject message id. */
    // ${reservation_name!""}Meeting invitation on: ${date_start}
    // ${time_start}${recurring!""}${update_or_cancel!""}
    public static final String SUBJECT_MSG_ID = "SENDEMAILINVITATIONS_SUBJECT";

    /** The email body message id. */
    // ${create_or_cancel} ${user}\n\n
    // Start Date: ${date_start}\n
    // Start Time: ${time_start} GMT${offset}\n
    // End Time: ${time_end} GMT${offset}\n\n
    // Location: ${location}\n
    // Reservation comments:\n${comments!""}\n
    // Room Reservation comments:\n${rm_comments!""}\n\n
    // Please click on the attached files to add, change or remove this meeting
    // in your calendar.
    public static final String BODY_MSG_ID = "SENDEMAILINVITATIONS_BODY";

    /** The email body message id for recurring reservations. */
    // ${create_or_cancel} ${user}\n\n
    // ${dt_list_msg}\n
    // ${dt_list!""}\n\n
    // Location: ${location}\n
    // Reservation comments:\n${comments!""}\n
    // Room Reservation comments:\n${rm_comments!""}\n\n
    // Please click on the attached files to add, change or remove this meeting
    // in your calendar.
    public static final String BODY_REC_MSG_ID =
            "SENDEMAILINVITATIONS_BODY_REC";

    /** The ICS body message id. */
    // ${create_or_cancel!""} ${user}\n
    // Start Date: ${date_start}\n
    // Start Time: ${time_start} GMT${time_offset!""}\n
    // End Time: ${time_end} GMT${time_offset!""}\n
    // Location: ${location!""}\n
    // Reservation comments:\n${comments!""}
    public static final String ICS_MSG_ID = "SENDEMAILINVITATIONS_ICS_BODY";

    /** The subject update part message id. */
    public static final String SUBJECT_UPD_MSG =
            "SENDEMAILINVITATIONS_SUBJECT_PART2";

    /** The subject cancel part message id. */
    public static final String SUBJECT_CAN_MSG =
            "SENDEMAILINVITATIONS_SUBJECT_PART3";

    /** The subject recurrent part message id. */
    public static final String SUBJECT_REC_MSG =
            "SENDEMAILINVITATIONS_SUBJECT_PART4";

    /** The invite body part message id. */
    public static final String BODY_INVITE_MSG =
            "SENDEMAILINVITATIONS_BODY_PART1";

    /** The cancel body part message id. */
    public static final String BODY_CANCEL_MSG =
            "SENDEMAILINVITATIONS_BODY_PART1_2";

    /** The invite recurring body part message id. */
    public static final String BODY_INVREC_MSG =
            "SENDEMAILINVITATIONS_BODY_PART1_3";

    /** The recurring dates list body part message id. */
    public static final String BODY_DTS_LIST_MSG =
            "SENDEMAILINVITATIONS_BODY_PART2_2";

    /** The cancel recurring dates list body part message id. */
    public static final String BODY_CAN_LIST_MSG =
            "SENDEMAILINVITATIONS_BODY_PART2_3";

    /** The start date body part message id. */
    public static final String BODY_START_DT_MSG =
            "SENDEMAILINVITATIONS_BODY_PART2";

    /** The start time body part message id. */
    public static final String BODY_START_TM_MSG =
            "SENDEMAILINVITATIONS_BODY_PART3";

    /** The end time body part message id. */
    public static final String BODY_END_TM_MSG =
            "SENDEMAILINVITATIONS_BODY_PART4";

    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private IcsConstants() {
    }

}
