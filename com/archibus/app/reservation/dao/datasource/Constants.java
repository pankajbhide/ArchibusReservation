package com.archibus.app.reservation.dao.datasource;

/**
 * Public constants for Reservation DAOs implementations.
 * <p>
 * Constants here are shared between several DAO implementation classes.
 *
 *
 */
public final class Constants {

    /**
     * first year used in calendar, used for time values without date.
     */
    public static final int INIT_YEAR = 1899;

    /**
     * date used for time values without date.
     */
    public static final int INIT_DATE = 30;

    /**
     * Field Name.
     */
    public static final String EMAIL_FIELD_NAME = "email";

    /**
     * Employee Table Name.
     */
    public static final String EM_TABLE_NAME = "em";

    /** Constant 'afm_users' table name. */
    public static final String AFM_USERS_TABLE = "afm_users";

    /**
     * Building Table Name.
     */
    public static final String BUILDING_TABLE = "bl";

    /**
     * Room Table Name.
     */
    public static final String ROOM_TABLE = "rm";

    /**
     * Room Arrangement Table Name.
     */
    public static final String RM_ARRANGE_TABLE = "rm_arrange";

    /**
     * Room Configuration Table Name.
     */
    public static final String ROOM_CONFIG_TABLE = "rm_config";

    /**
     * Resources Table Name.
     */
    public static final String RESOURCES_TABLE = "resources";

    /** Room arrangement types table name. */
    public static final String RM_ARRANGE_TYPE_TABLE = "rm_arrange_type";

    /**
     * Room Configuration Table Name.
     */
    public static final String EXCLUDED_CONFIG_FIELD = "excluded_config";

    /**
     * Security group for Reservation Assistant.
     */
    public static final String RESERVATION_ASSISTANT = "RESERVATION ASSISTANT";

    /**
     * Security group for Reservation Manager.
     */
    public static final String RESERVATION_MANAGER = "RESERVATION MANAGER";

    /**
     * Security group for Reservation Service Desk.
     */
    public static final String RESERVATION_SERVICE_DESK = "RESERVATION SERVICE DESK";

    /** Comma separated status list for when a reservation can be edited or cancelled. */
    public static final String STATUS_AWAITING_APP_OR_CONFIRMED = "Awaiting App.,Confirmed";

    /**
     * Comma separated status list for when a reservation can be edited or cancelled, including
     * conflict status.
     */
    public static final String STATUS_AWAITING_APP_OR_CONFIRMED_OR_CONFLICT =
            "Awaiting App.,Confirmed,Room Conflict";

    /** Status for cancel. */
    public static final String STATUS_CANCELLED = "Cancelled";

    /** status confirmed. */
    public static final String STATUS_CONFIRMED = "Confirmed";

    /** status waiting for approval. */
    public static final String STATUS_AWAITING_APP = "Awaiting App.";

    /** Status for rejected reservation. */
    public static final String STATUS_REJECTED = "Rejected";

    /** Status for reservations with a room conflict. */
    public static final String STATUS_ROOM_CONFLICT = "Room Conflict";

    /** one day in milliseconds. */
    public static final int ONE_DAY = 24 * 60 * 60 * 1000;

    /** one minute in milliseconds. */
    public static final int ONE_MINUTE = 60000;

    /**
     * Field Name.
     */
    public static final String PRE_BLOCK_FIELD_NAME = "pre_block";

    /**
     * Field Name.
     */
    public static final String POST_BLOCK_FIELD_NAME = "post_block";

    /**
     * Parameter Name.
     */
    public static final String GROUP_PARAMETER_NAME = "group";

    /** field for conference id. */
    public static final String RES_CONFERENCE = "res_conference";

    /** field for parent id. */
    public static final String RES_PARENT = "res_parent";

    /** field for reservation id. */
    public static final String RES_ID = "res_id";

    /** field for status. */
    public static final String STATUS = "status";

    /** field for date start. */
    public static final String DATE_START_FIELD_NAME = "date_start";

    /** field for time start. */
    public static final String TIME_START_FIELD_NAME = "time_start";

    /** field for time start. */
    public static final String TIME_END_FIELD_NAME = "time_end";

    /** field for date end. */
    public static final String DATE_END_FIELD_NAME = "date_end";

    /** field for day start. */
    public static final String DAY_START_FIELD_NAME = "day_start";

    /** field for day end. */
    public static final String DAY_END_FIELD_NAME = "day_end";

    /** field for is_default. */
    public static final String IS_DEFAULT_FIELD_NAME = "is_default";

    /** field for cancel_time. */
    public static final String CANCEL_TIME_FIELD_NAME = "cancel_time";

    /** field for announce_time. */
    public static final String ANNOUNCE_TIME_FIELD_NAME = "announce_time";

    /** field for max_days_ahead. */
    public static final String MAX_DAYS_AHEAD_FIELD_NAME = "max_days_ahead";

    /** field for external_allowed. */
    public static final String EXTERNAL_ALLOWED_FIELD_NAME = "external_allowed";

    /** field for unique id. */
    public static final String UNIQUE_ID = "outlook_unique_id";

    /** Field for attendees in room. */
    public static final String ATTENDEES_IN_ROOM_FIELD = "attendees_in_room";

    /** Field for occurrence index. */
    public static final String OCCURRENCE_INDEX_FIELD = "occurrence_index";

    /**
     * Field Name.
     */
    public static final String RSRES_ID_FIELD_NAME = "rsres_id";

    /**
     * Parameter name.
     */
    public static final String BL_ID_PARAMETER = "blId";

    /**
     * .
     */
    public static final String DOT = ".";

    /**
     * ,.
     */
    public static final String COMMA = ",";

    /**
     * - sign.
     */
    public static final String MINUS = "-";

    /**
     * ) right parenthesis.
     */
    public static final String RIGHT_PAR = ")";

    /**
     * RmRes Id Field Name.
     */
    public static final String RMRES_ID_FIELD_NAME = "rmres_id";

    /**
     * Field Name.
     */
    public static final String NAME_FIELD_NAME = "name";

    /**
     * Parameter Name.
     */
    public static final String RESOURCE_STD_PARAMETER_NAME = "resourceStd";

    /**
     * resource id parameter.
     */
    public static final String RESOURCE_ID = "resourceId";

    /**
     * resource id field.
     */
    public static final String RESOURCE_ID_FIELD = "resource_id";

    /** Quantity field. */
    public static final String QUANTITY_FIELD = "quantity";

    /**
     * Field Name.
     */
    public static final String SITE_ID_FIELD_NAME = "site_id";

    /**
     * Field Name.
     */
    public static final String BL_ID_FIELD_NAME = "bl_id";

    /**
     * Field Name.
     */
    public static final String FL_ID_FIELD_NAME = "fl_id";

    /**
     * Field Name.
     */
    public static final String RM_ID_FIELD_NAME = "rm_id";

    /**
     * Field Name.
     */
    public static final String CONFIG_ID_FIELD_NAME = "config_id";

    /**
     * Field Name.
     */
    public static final String RM_ARRANGE_TYPE_ID_FIELD_NAME = "rm_arrange_type_id";

    /**
     * Field Name.
     */
    public static final String MAX_CAPACITY_FIELD_NAME = "max_capacity";

    /**
     * Field Name.
     */
    public static final String EM_ID_FIELD_NAME = "em_id";

    /**
     * Table Name.
     */
    public static final String RESERVE_TABLE_NAME = "reserve";

    /**
     * Table Name.
     */
    public static final String RESERVE_RM_TABLE = "reserve_rm";

    /**
     * Table Name.
     */
    public static final String RESERVE_RS_TABLE = "reserve_rs";

    /**
     * Field Name.
     */
    public static final String RESERVABLE_FIELD_NAME = "reservable";

    /** cost calculation unit per reservation. */
    public static final int COST_UNIT_RESERVATION = 0;

    /** cost calculation unit per minute. */
    public static final int COST_UNIT_MINUTE = 1;

    /** cost calculation unit per hour. */
    public static final int COST_UNIT_HOUR = 2;

    /** cost calculation unit partial day. */
    public static final int COST_UNIT_PARTIAL = 3;

    /** cost calculation unit per day. */
    public static final int COST_UNIT_DAY = 4;

    /** half day in hours. */
    public static final int HALF_DAY_HOURS = 4;

    /** UTC time zone ID. */
    public static final String TIMEZONE_UTC = "UTC";

    /** Reservation type recurring. */
    public static final String TYPE_RECURRING = "recurring";

    /** Reservation type regular. */
    public static final String TYPE_REGULAR = "regular";

    /** Reservation type continuous (spanning multiple days). */
    public static final String TYPE_CONTINUOUS = "continuous";

    /**
     * Private default constructor: utility class is non-instantiable.
     *
     * @throws InstantiationException always, since this constructor should never be called.
     */
    private Constants() throws InstantiationException {
        throw new InstantiationException(
            "Never instantiate " + this.getClass().getName() + "; use static methods!");
    }
}
