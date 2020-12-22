package com.archibus.eventhandler.reservations;

import java.sql.Time;
import java.text.ParseException;
import java.util.*;

import org.apache.log4j.Logger;
import org.json.JSONObject;

import com.archibus.app.common.util.SchemaUtils;
import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.util.*;
import com.archibus.context.ContextStore;
import com.archibus.datasource.*;
import com.archibus.datasource.data.*;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.datasource.restriction.Restrictions.Restriction;
import com.archibus.eventhandler.EventHandlerBase;
import com.archibus.jobmanager.EventHandlerContext;
import com.archibus.schema.TableDef;
import com.archibus.service.Configuration;
import com.archibus.utility.*;

/**
 * Contains common event handlers used in both Rooms and Resources reservation WFRs.
 *
 */
public class ReservationsCommonHandler extends ReservationsEventHandlerBase {

    /** Constant: a single new line. */
    // private static final String NEWLINE = "   \n";
    // Pankaj at LBNL
    private static final String NEWLINE = "<BR>";


    /** Constant: reservation type room. */
    private static final String RES_TYPE_ROOM = "room";

    /** Constant: a single whitespace. */
    private static final String SPACE = " ";

    /** Constant: a double newline. */
    //private static final String DOUBLE_NEWLINE = "\n\n";
    // Pankaj at LBNL
    private static final String DOUBLE_NEWLINE ="<BR><BR>";

    /** Constant: a dash surrounded by spaces. */
    private static final String DASH = " - ";

    /** Constant: prefix for fields in reserve_rm table. */
    private static final String RESERVE_RM_DOT = "reserve_rm.";

    /** Constant: prefix for fields in reserve_rs table. */
    private static final String RESERVE_RS_DOT = "reserve_rs.";

    /** Constant: prefix for fields in reserve table. */
    private static final String RESERVE_DOT = "reserve.";

    /** Constant: prefix user_requested_. */
    private static final String USER_REQ_PREFIX = "user_requested_";

    /** Constant: comments field name. */
    private static final String COMMENTS_FIELD = "comments";

    /** Constant: locale property id. */
    private static final String LOCALE = "locale";

    /** Constant: user requested by field name. */
    private static final String USER_REQUESTED_BY = "user_requested_by";

    /** Constant: user requested for field name. */
    private static final String USER_REQUESTED_FOR = "user_requested_for";

    /**
     * Prefix for referenced by and message id of messages used to notify the requestors.
     */
    private static final String NOTIFYREQUESTED_PREFIX = "NOTIFYREQUESTED";

    /**
     * Constant: referenced_by value for messages used to notify reservation approver.
     */
    private static final String NOTIFYAPPROVER_WFR = "NOTIFYAPPROVER_WFR";

    /** Constant: 0. */
    private static final String ZERO = "0";

    /** Constant: suffix for requested_by. */
    private static final String BY_SUFFIX = "by";

    /** Constant: suffix for requested_for. */
    private static final String FOR_SUFFIX = "for";

    /**
     * Number of numbered notify approver body messages to retrieve from the messages table.
     */
    private static final int MAX_APPROVE_BODY = 18;

    /**
     * Default number of allowed attendees in the room: 10.
     */
    private static final int DEFAULT_MAX_CAPACITY = 10;

    /**
     * Default max_days_ahead value, number of days in a year: 365.
     */
    private static final int DEFAULT_MAX_DAYS_AHEAD = 365;

    // ---------------------------------------------------------------------------------------------
    // BEGIN notifyRequestedBy wfr
    // ---------------------------------------------------------------------------------------------
    /**
     * This rule gets the identifier of a created, modified, cancelled or rejected reservation and
     * notifies the �requested by?user of this event. Inputs: res_id res_id (String); Outputs:
     * message error message in necesary case
     *
     * @param context Event handler context.
     */
    public void notifyRequestedBy(final EventHandlerContext context) {
        this.notifyRequestedStd(context, BY_SUFFIX);
    }

    // ---------------------------------------------------------------------------------------------
    // END notifyRequestedBy wfr
    // ---------------------------------------------------------------------------------------------

    // ---------------------------------------------------------------------------------------------
    // BEGIN notifyRequestedFor wfr
    // ---------------------------------------------------------------------------------------------
    /**
     * This rule gets the identifier of a created, modified, cancelled or rejected reservation and
     * notifies the �requested for?user of this event. Inputs: res_id res_id (String); Outputs:
     * message error message in necesary case
     *
     * @param context Event handler context.
     */
    public void notifyRequestedFor(final EventHandlerContext context) {
        this.notifyRequestedStd(context, FOR_SUFFIX);
    }

    // ---------------------------------------------------------------------------------------------
    // END notifyRequestedFor wfr
    // ---------------------------------------------------------------------------------------------

    // ---------------------------------------------------------------------------------------------
    // BEGIN notifyRequestedStd
    // ---------------------------------------------------------------------------------------------
    /**
     * This rule gets the identifier of a created, modified, cancelled or rejected reservation and
     * notifies the �requested by o for?user of this event. Inputs: context context
     * (EventHandlerContext); resId resID (String); parentId parentId (String); Std Std (String) It
     * can be "by" or "for" Outputs: message error message in necesary case
     *
     * @param context Event handler context.
     * @param std which employee to notify (requested_for or _by)
     */
    public void notifyRequestedStd(final EventHandlerContext context, final String std) {

        final String ruleId = "notifyRequested" + std;
        // Get input parameters
        final String resId = (String) context.getParameter(Constants.RES_ID);
        String parentId = ZERO;
        // If exists a res_parent parameter in context. the wfr inicialize this
        // parameter.
        if (context.parameterExists(Constants.RES_PARENT)) {
            parentId = (String) context.getParameter(Constants.RES_PARENT);
        }

        boolean allOk = true;
        final Map<String, String> valuesToMail = new HashMap<String, String>();
        final List<Map<String, String>> listResources = new ArrayList<Map<String, String>>();
        final List<Map<String, String>> listRoom = new ArrayList<Map<String, String>>();
        final boolean isRegular = parentId.equals(ZERO);
        final boolean isRecurring = !parentId.equals(ZERO);
        boolean existsRoom = false;
        boolean isCancelled = false;
        boolean isRejected = false;

        // notification rule error message
        final String errMessage = ReservationsContextHelper.localizeMessage(
                NOTIFYREQUESTED_PREFIX + std.toUpperCase() + "_WFR",
                ContextStore.get().getUser().getLocale(),
                NOTIFYREQUESTED_PREFIX + std.toUpperCase() + "ERROR");

        try {
            if (shouldSendNotification(std, resId, parentId)) {
                // BEGIN: Get reservation info and the user to notify
                retrieveUserInfo(std, resId, parentId, valuesToMail);

                final DataSource reserveRoomDs = DataSourceFactory.createDataSourceForFields(
                        Constants.RESERVE_RM_TABLE,
                        new String[] { Constants.DATE_START_FIELD_NAME, Constants.TIME_START_FIELD_NAME,
                                Constants.DATE_END_FIELD_NAME, Constants.TIME_END_FIELD_NAME,
                                Constants.STATUS, Constants.BL_ID_FIELD_NAME,
                                Constants.FL_ID_FIELD_NAME, Constants.RM_ID_FIELD_NAME,
                                Constants.CONFIG_ID_FIELD_NAME, Constants.RM_ARRANGE_TYPE_ID_FIELD_NAME,
                                Constants.ATTENDEES_IN_ROOM_FIELD, COMMENTS_FIELD });
                reserveRoomDs.addSort(Constants.DATE_START_FIELD_NAME);
                if (ZERO.equals(resId)) {
                    reserveRoomDs.addRestriction(Restrictions
                            .sql("reserve_rm.res_id IN (SELECT res_id FROM reserve WHERE res_parent = "
                                    + parentId + Constants.RIGHT_PAR));
                } else {
                    reserveRoomDs.addRestriction(
                            Restrictions.eq(Constants.RESERVE_RM_TABLE, Constants.RES_ID, resId));
                }
                final List<DataRecord> roomAllocations = reserveRoomDs.getRecords();

                // if exists room reserve
                if (roomAllocations.isEmpty()) {
                    isCancelled = Constants.STATUS_CANCELLED
                            .equals(valuesToMail.get(RESERVE_DOT + Constants.STATUS));
                    isRejected = Constants.STATUS_REJECTED
                            .equals(valuesToMail.get(RESERVE_DOT + Constants.STATUS));
                } else {
                    existsRoom = true;
                    // For each room reservation, get the information to notify
                    for (final DataRecord record : roomAllocations) {
                        final String status = extractRoomInfo(context, listRoom, record);
                        // remember if any of the rooms was rejected or cancelled
                        isCancelled = isCancelled || Constants.STATUS_CANCELLED.equals(status);
                        isRejected = isRejected || Constants.STATUS_REJECTED.equals(status);
                    }
                }
                if (!listRoom.isEmpty()) {
                    valuesToMail.putAll(listRoom.get(0));
                }

                retrieveResourcesToMail(context, resId, parentId, listResources);

                // Get all messages to Mail in a Map (It is more easy to write)
                final Map<String, String> messages =
                        this.getMailMessages(context, std, valuesToMail.get(LOCALE));

                // BEGIN: create message email
                // BEGIN: subject
                String subject = "";
                if (isRecurring) {
                    subject = compileRecurringSubject(parentId, valuesToMail, listResources,
                            existsRoom, messages);
                } else if (isRegular) {
                    subject = compileRegularSubject(resId, valuesToMail, listResources, existsRoom,
                            messages);
                }
                // END: subject

                // BEGIN: message
                String message = "";

                String strUserRequestedby=lblGetEmployeename(valuesToMail.get("reserve.user_requested_by"));
                String strUserRequestedfor=lblGetEmployeename(valuesToMail.get("reserve.user_requested_for"));
                System.out.println("by: "+strUserRequestedby+"\tfor:"+strUserRequestedfor);
                message += messages.get("BODY1") + SPACE
                        + lblGetEmployeename(valuesToMail.get("reserve.user_requested_"+std)) + "," + DOUBLE_NEWLINE;


                if (isCancelled) {
                    message += messages.get("BODY_PART2_CANCEL");
                } else if (isRejected) {
                    message += messages.get("BODY_PART2_REJECT");
                } else {
                    message += messages.get("BODY2");
                }
                message += SPACE + (parentId.equals(ZERO) ? resId : parentId) + DOUBLE_NEWLINE; // Pankaj LBNL

                if (existsRoom) {
                    message += compileRoomDetails(valuesToMail, listRoom, isRegular, messages);
                }
                if (!listResources.isEmpty()) {
                    message +=
                            compileResourceDetails(listResources, isRegular, existsRoom, messages);
                }

                message += messages.get("BODY12") + NEWLINE;
                message += ReservationsContextHelper.getWebCentralUrl();
                message +=
                        "schema/ab-system/html/url-proxy.htm?viewName=ab-rr-reservations-details-grid.axvw&fieldName=";
                if (isRegular) {
                    message += "reserve.res_id&fieldValue=" + resId;
                } else {
                    message += "reserve.res_parent&fieldValue=" + parentId;
                }
                message += DOUBLE_NEWLINE;
                message += messages.get("BODY8") + NEWLINE
                        + valuesToMail.get("reserve.comments").replaceAll("\n", NEWLINE)
                        + DOUBLE_NEWLINE;

                message += messages.get("BODY13") + DOUBLE_NEWLINE;
                message += EmailNotificationHelper.getServiceName();

                // END: message

                // END: Create message email
                sendEmail(context, subject, message,
                        valuesToMail.get("reserve.requested" + std + "mail"));
            }
        } catch (final ExceptionBase e) {
            handleNotificationError(context, ACTIVITY_ID + "-" + ruleId + ": Failed", errMessage, e,
                    "");
            allOk = false;
        }

        if (!allOk) {
            context.addResponseParameter(MESSAGE, errMessage);
        }
    }

    // LBNL Pankaj- Get Employee name
    private String lblGetEmployeename(String strEmid)
    {
        String strEmployeeName="";
        String[] fields={"em.em_number","em.name"};
        String[] tables={"em"};
        DataSource ds=DataSourceFactory.createDataSourceForFields(tables,fields);


        ds.addRestriction(Restrictions.eq("em","em_id",strEmid));
        DataRecord record=ds.getRecord();
        if (record != null)
            //strEmployeeName=record.getString("em.em_number");
            strEmployeeName=record.getString("em.name");
        record=null;
        ds=null;

        return strEmployeeName;

    }
    /**
     * Write resource details body fragment.
     *
     * @param listResources list of resources to write
     * @param isRegular true if regular, false if recurring
     * @param existsRoom whether the reservation has a room allocation
     * @param messages localized messages to build the body fragment
     * @return the fragment
     */
    private String compileResourceDetails(final List<Map<String, String>> listResources,
                                          final boolean isRegular, final boolean existsRoom, final Map<String, String> messages) {
        String message = "";

        if (isRegular) {
            if (existsRoom) {
                // BEGIN: list of resources with room
                message += messages.get("BODY10") + DOUBLE_NEWLINE;
                String rsComments = "";
                for (final Map<String, String> resources : listResources) {
                    message += resources.get("reserve_rs.resource_id") + DASH
                            + resources.get("reserve_rs.quantity") + DASH
                            + resources.get("reserve_rs.status") + DASH
                            + resources.get("reserve_rs.date_start") + SPACE
                            + resources.get("reserve_rs.time_start") + DASH
                            + resources.get("reserve_rs.date_end") + SPACE
                            + resources.get("reserve_rs.time_end") + NEWLINE;
                    rsComments = resources.get("reserve_rs.comments");
                    message += messages.get("BODY11_2") + SPACE
                            + rsComments.replaceAll("\n", NEWLINE) + DOUBLE_NEWLINE;
                }
                // END: list of resources with room
            } else {
                // BEGIN: list of resources without room
                message += messages.get("BODY10") + DOUBLE_NEWLINE;
                String rsComments = "";
                for (final Map<String, String> resources : listResources) {
                    message += resources.get("reserve_rs.resource_id") + DASH
                            + resources.get("reserve_rs.quantity") + DASH
                            + resources.get("reserve_rs.status") + DASH
                            + resources.get("reserve_rs.time_start") + DASH
                            + resources.get("reserve_rs.time_end") + DASH
                            + resources.get("reserve_rs.bl_id") + DASH
                            + resources.get("reserve_rs.fl_id") + DASH
                            + resources.get("reserve_rs.rm_id") + NEWLINE;
                    rsComments = resources.get("reserve_rs.comments");
                    message += messages.get("BODY11_2") + SPACE
                            + rsComments.replaceAll("\n", NEWLINE) + DOUBLE_NEWLINE;
                }
                // END: list of resources without room
            }
        } else {
            // BEGIN: list of resources when is recurring
            message += messages.get("BODY10") + DOUBLE_NEWLINE;
            String rsComments = "";
            for (final Map<String, String> resources : listResources) {
                message += resources.get("reserve_rs.resource_id") + DASH
                        + resources.get("reserve_rs.quantity") + DASH
                        + resources.get("reserve_rs.time_start") + DASH
                        + resources.get("reserve_rs.time_end") + DASH
                        + resources.get("reserve_rs.status") + DASH
                        + resources.get("reserve_rs.bl_id") + DASH
                        + resources.get("reserve_rs.fl_id") + DASH
                        + resources.get("reserve_rs.rm_id") + NEWLINE;
                rsComments = getString(resources, "reserve_rs.comments");
                message += messages.get("BODY11_2") + SPACE + rsComments.replaceAll("\n", NEWLINE)
                        + DOUBLE_NEWLINE;
            }
            // END: list of resources when is recurring
        }
        return message;
    }

    /**
     * Write room details body fragment.
     *
     * @param valuesToMail contains actual values of the reservation
     * @param listRoom contains a room for each occurrence in the reservation
     * @param isRegular true if regular, false if recurring
     * @param messages localized messages to build the body fragment with
     * @return written room details
     */
    private String compileRoomDetails(final Map<String, String> valuesToMail,
                                      final List<Map<String, String>> listRoom, final boolean isRegular,
                                      final Map<String, String> messages) {
        String message = "";
        if (isRegular) {
            message += messages.get("BODY3") + SPACE + valuesToMail.get("reserve_rm.bl_id") + DASH
                    + valuesToMail.get("reserve_rm.fl_id") + DASH
                    + valuesToMail.get("reserve_rm.rm_id") + DASH
                    + valuesToMail.get("reserve_rm.config_id") + DASH
                    + valuesToMail.get("reserve_rm.rm_arrange_type_id") + DOUBLE_NEWLINE; // Pankaj LBNL

            message += messages.get("BODY14") + SPACE + valuesToMail.get("reserve_rm.date_start")
                    + DOUBLE_NEWLINE;  // Pankaj LBNL
            message += messages.get("BODY4") + SPACE + convToStdTime(valuesToMail.get("reserve_rm.time_start"))
                    + DOUBLE_NEWLINE; // Pankaj Bhide
            message += messages.get("BODY15") + SPACE + valuesToMail.get("reserve_rm.date_end")
                    + DOUBLE_NEWLINE; // Pankaj LBNL
            message += messages.get("BODY5") + SPACE + convToStdTime(valuesToMail.get("reserve_rm.time_end"))
                    + DOUBLE_NEWLINE; // Pankaj LBNL
            message += messages.get("BODY6") + SPACE
                    + valuesToMail.get("reserve_rm.attendees_in_room") + DOUBLE_NEWLINE; // Pankaj LBNL
            message +=
                    messages.get("BODY7") + SPACE + valuesToMail.get("reserve_rm.status") + DOUBLE_NEWLINE; // Pankaj LBNL
            message += messages.get("BODY11") + SPACE
                    + valuesToMail.get("reserve_rm.comments").replaceAll("\n", NEWLINE)
                    + DOUBLE_NEWLINE;
        } else {
            message += messages.get("BODY9") + NEWLINE;
            String rmComments = "";
            for (final Map<String, String> rooms : listRoom) {
                message += rooms.get("reserve_rm.date_start") + DASH + rooms.get("reserve_rm.bl_id")
                        + DASH + rooms.get("reserve_rm.fl_id") + DASH
                        + rooms.get("reserve_rm.rm_id") + DASH + rooms.get("reserve_rm.config_id")
                        + DASH + rooms.get("reserve_rm.rm_arrange_type_id") + DASH
                        + convToStdTime(rooms.get("reserve_rm.time_start")) + DASH
                        + convToStdTime(rooms.get("reserve_rm.time_end")) + DASH
                        + rooms.get("reserve_rm.attendees_in_room") + DASH
                        + rooms.get("reserve_rm.status") + NEWLINE;
                rmComments = valuesToMail.get("reserve_rm.comments");
            }
            message += NEWLINE + messages.get("BODY11") + SPACE
                    + rmComments.replaceAll("\n", NEWLINE) + DOUBLE_NEWLINE;
        }
        return message;
    }

    private String convToStdTime(String milTime) {
        String stdTime = null;
        int secIndex, minIndex;
        secIndex = milTime.indexOf('.');
        minIndex = milTime.indexOf(':');

        int milHours = Integer.parseInt(milTime.substring(0, minIndex));
        int stdHours = milHours <= 12 ? (milHours==0 ? 12 : milHours) : milHours-12;
        int stdMin = Integer.parseInt(milTime.substring(minIndex+1, secIndex));
        String stdMeridiem =  milHours < 12 ? "a.m." : "p.m.";
        stdTime = Integer.toString(stdHours) + ":" + (stdMin<10 ? "0"+Integer.toString(stdMin) : Integer.toString(stdMin)) + " " + stdMeridiem;

        return stdTime;
    }

    /**
     * Extract room data for emailing from a room allocation record.
     *
     * @param context event handler context
     * @param listRoom container to store room data
     * @param record the room allocation record
     * @return room allocation status
     */
    private String extractRoomInfo(final EventHandlerContext context,
                                   final List<Map<String, String>> listRoom, final DataRecord record) {
        final Map<String, String> roomToMail = new HashMap<String, String>();
        roomToMail.put(RESERVE_RM_DOT + Constants.ATTENDEES_IN_ROOM_FIELD,
                record.getNeutralValue(RESERVE_RM_DOT + Constants.ATTENDEES_IN_ROOM_FIELD));
        roomToMail.put(RESERVE_RM_DOT + COMMENTS_FIELD,
                StringUtil.notNull(record.getString(RESERVE_RM_DOT + COMMENTS_FIELD)));

        roomToMail.put(RESERVE_RM_DOT + Constants.DATE_START_FIELD_NAME,
                record.getNeutralValue(RESERVE_RM_DOT + Constants.DATE_START_FIELD_NAME));
        roomToMail.put(RESERVE_RM_DOT + Constants.TIME_START_FIELD_NAME,
                record.getNeutralValue(RESERVE_RM_DOT + Constants.TIME_START_FIELD_NAME));
        roomToMail.put(RESERVE_RM_DOT + Constants.DATE_END_FIELD_NAME,
                record.getNeutralValue(RESERVE_RM_DOT + Constants.DATE_END_FIELD_NAME));
        roomToMail.put(RESERVE_RM_DOT + Constants.TIME_END_FIELD_NAME,
                record.getNeutralValue(RESERVE_RM_DOT + Constants.TIME_END_FIELD_NAME));

        final String status = record.getString(RESERVE_RM_DOT + Constants.STATUS);
        final String dsiplayStatus = ContextStore.get().getProject()
                .loadTableDef(Constants.RESERVE_RM_TABLE).findFieldDef(Constants.STATUS)
                .formatFieldValue(status, null, true, context.getLocale());
        roomToMail.put(RESERVE_RM_DOT + Constants.STATUS, dsiplayStatus);

        roomToMail.put(RESERVE_RM_DOT + Constants.BL_ID_FIELD_NAME,
                record.getString(RESERVE_RM_DOT + Constants.BL_ID_FIELD_NAME));
        roomToMail.put(RESERVE_RM_DOT + Constants.FL_ID_FIELD_NAME,
                record.getString(RESERVE_RM_DOT + Constants.FL_ID_FIELD_NAME));
        roomToMail.put(RESERVE_RM_DOT + Constants.RM_ID_FIELD_NAME,
                record.getString(RESERVE_RM_DOT + Constants.RM_ID_FIELD_NAME));
        roomToMail.put(RESERVE_RM_DOT + Constants.CONFIG_ID_FIELD_NAME,
                record.getString(RESERVE_RM_DOT + Constants.CONFIG_ID_FIELD_NAME));
        roomToMail.put(RESERVE_RM_DOT + Constants.RM_ARRANGE_TYPE_ID_FIELD_NAME,
                record.getString(RESERVE_RM_DOT + Constants.RM_ARRANGE_TYPE_ID_FIELD_NAME));
        listRoom.add(roomToMail);
        return status;
    }

    /**
     * Send an email from the reservations services address.
     *
     * @param context event handler context
     * @param subject email subject
     * @param message email body
     * @param destination destination email address
     */
    private void sendEmail(final EventHandlerContext context, final String subject,
                           final String message, final String destination) {
        final MailMessage messageToLog = new MailMessage();
        messageToLog.setActivityId(ReservationsContextHelper.RESERVATIONS_ACTIVITY);
        messageToLog.setFrom(EmailNotificationHelper.getServiceEmail());
        messageToLog.setTo(destination);
        messageToLog.setSubject(subject);
        messageToLog.setText(message);
        messageToLog.setContentType("text/html; charset=UTF-8");  // Pankaj LBNL

        messageToLog.setHost(EventHandlerBase.getEmailHost(context));
        messageToLog.setPort(EventHandlerBase.getEmailPort(context));
        messageToLog.setUser(EventHandlerBase.getEmailUserId(context));
        messageToLog.setPassword(EventHandlerBase.getEmailPassword(context));
        final MailSender mailSender = new MailSender();
        mailSender.send(messageToLog);
    }

    /**
     * Compile the email subject for notifying a requestor of a regular reservation.
     *
     * @param resId reservation id
     * @param valuesToMail contains information on the reservation to mail
     * @param listResources info about all resources in the reservation
     * @param existsRoom whether a room reservation exists
     * @param messages contains the localized messages for compiling the subject
     * @return the email subject
     */
    private String compileRegularSubject(final String resId, final Map<String, String> valuesToMail,
                                         final List<Map<String, String>> listResources, final boolean existsRoom,
                                         final Map<String, String> messages) {
        String subject =
                messages.get("SUBJECT1") + SPACE + resId + ", " + messages.get("SUBJECT2") + SPACE;
        if (existsRoom) {
            subject += valuesToMail.get("reserve_rm.date_start");
            subject += SPACE + "is" + SPACE + valuesToMail.get("reserve_rm.status");  // Pankaj LBNL

        } else if (listResources.isEmpty()) {
            subject += valuesToMail.get("reserve.date_start");
        } else {
            final Iterator<Map<String, String>> resIt = listResources.iterator();
            final Map<String, String> resourcesMap = resIt.next();
            final String dateStart = resourcesMap.get("reserve_rs.date_start");
            subject += dateStart;
        }
        return subject;
    }

    /**
     * Compile the email subject for notifying a requestor of a recurring reservation.
     *
     * @param parentId reservation id
     * @param valuesToMail contains information on the reservation to mail
     * @param listResources info about all resources in the reservation
     * @param existsRoom whether a room reservation exists
     * @param messages contains the localized messages for compiling the subject
     * @return the email subject
     */
    private String compileRecurringSubject(final String parentId,
                                           final Map<String, String> valuesToMail, final List<Map<String, String>> listResources,
                                           final boolean existsRoom, final Map<String, String> messages) {
        String subject = messages.get("SUBJECT1") + SPACE + parentId + ", "
                + messages.get("SUBJECT2") + SPACE;
        if (existsRoom) {
            subject += valuesToMail.get("reserve_rm.date_start");
        } else if (listResources.isEmpty()) {
            subject += valuesToMail.get("reserve.date_start");
        } else {
            final Iterator<Map<String, String>> resIt = listResources.iterator();
            final Map<String, String> resourcesMap = resIt.next();
            final String dateStart = resourcesMap.get("reserve_rs.date_start");
            subject += dateStart;
        }
        subject += SPACE + messages.get("SUBJECT4");
        return subject;
    }

    /**
     * Retrieve info about all resources in the reservation to include in the email.
     *
     * @param context event handler context
     * @param resId reservation identifier
     * @param parentId parent reservation identifier
     * @param listResources the list to add the resource information to
     */
    private void retrieveResourcesToMail(final EventHandlerContext context, final String resId,
                                         final String parentId, final List<Map<String, String>> listResources) {
        final DataSource reserveResourceDs =
                DataSourceFactory.createDataSourceForFields(Constants.RESERVE_RS_TABLE,
                        new String[] { Constants.DATE_START_FIELD_NAME, Constants.TIME_START_FIELD_NAME,
                                Constants.DATE_END_FIELD_NAME, Constants.TIME_END_FIELD_NAME,
                                Constants.STATUS, Constants.RESOURCE_ID_FIELD, Constants.QUANTITY_FIELD,
                                Constants.BL_ID_FIELD_NAME, Constants.FL_ID_FIELD_NAME,
                                Constants.RM_ID_FIELD_NAME, COMMENTS_FIELD });
        reserveResourceDs.addSort(Constants.DATE_START_FIELD_NAME);

        // if resId is not zero makes the query for a single a reserve,
        // but in another case makes it for a group of reserves with a common
        // father.
        if (resId.equals(ZERO)) {
            // Since we don't show the dates, only show the resources for one
            // date.
            reserveResourceDs.addRestriction(
                    Restrictions.eq(Constants.RESERVE_RS_TABLE, Constants.RES_ID, parentId));
        } else {
            reserveResourceDs.addRestriction(
                    Restrictions.eq(Constants.RESERVE_RS_TABLE, Constants.RES_ID, resId));
        }

        final List<DataRecord> resourceAllocations = reserveResourceDs.getRecords();
        if (!resourceAllocations.isEmpty()) {
            for (final DataRecord record : resourceAllocations) {
                final Map<String, String> resourcesToMail = new HashMap<String, String>();
                resourcesToMail.put(RESERVE_RS_DOT + COMMENTS_FIELD,
                        StringUtil.notNull(record.getString(RESERVE_RS_DOT + COMMENTS_FIELD)));
                resourcesToMail.put(RESERVE_RS_DOT + Constants.DATE_START_FIELD_NAME,
                        record.getNeutralValue(RESERVE_RS_DOT + Constants.DATE_START_FIELD_NAME));
                resourcesToMail.put(RESERVE_RS_DOT + Constants.TIME_START_FIELD_NAME,
                        record.getNeutralValue(RESERVE_RS_DOT + Constants.TIME_START_FIELD_NAME));
                resourcesToMail.put(RESERVE_RS_DOT + Constants.DATE_END_FIELD_NAME,
                        record.getNeutralValue(RESERVE_RS_DOT + Constants.DATE_END_FIELD_NAME));
                resourcesToMail.put(RESERVE_RS_DOT + Constants.TIME_END_FIELD_NAME,
                        record.getNeutralValue(RESERVE_RS_DOT + Constants.TIME_END_FIELD_NAME));

                final String status = record.getString(RESERVE_RS_DOT + Constants.STATUS);
                final String displayStatus = ContextStore.get().getProject()
                        .loadTableDef(Constants.RESERVE_RS_TABLE).findFieldDef(Constants.STATUS)
                        .formatFieldValue(status, null, true, context.getLocale());
                resourcesToMail.put(RESERVE_RS_DOT + Constants.STATUS, displayStatus);

                resourcesToMail.put(RESERVE_RS_DOT + Constants.RESOURCE_ID_FIELD,
                        record.getString(RESERVE_RS_DOT + Constants.RESOURCE_ID_FIELD));
                resourcesToMail.put(RESERVE_RS_DOT + Constants.QUANTITY_FIELD,
                        record.getNeutralValue(RESERVE_RS_DOT + Constants.QUANTITY_FIELD));
                resourcesToMail.put(RESERVE_RS_DOT + Constants.BL_ID_FIELD_NAME,
                        record.getString(RESERVE_RS_DOT + Constants.BL_ID_FIELD_NAME));
                resourcesToMail.put(RESERVE_RS_DOT + Constants.FL_ID_FIELD_NAME,
                        record.getString(RESERVE_RS_DOT + Constants.FL_ID_FIELD_NAME));
                resourcesToMail.put(RESERVE_RS_DOT + Constants.RM_ID_FIELD_NAME,
                        record.getString(RESERVE_RS_DOT + Constants.RM_ID_FIELD_NAME));
                listResources.add(resourcesToMail);
            }
        }
    }

    /**
     * Retrieve user-related information about the reservation from the database.
     *
     * @param std which user to retrieve the info for (requested_by or _for)
     * @param resId reservation id
     * @param parentId parent reservation id (for a full recurrence series)
     * @param valuesToMail container to store the information
     */
    private void retrieveUserInfo(final String std, final String resId, final String parentId,
                                  final Map<String, String> valuesToMail) {
        final DataSource emailDs = DataSourceFactory.createDataSourceForFields(
                Constants.RESERVE_TABLE_NAME, new String[] { Constants.RES_ID, USER_REQ_PREFIX + std,
                        COMMENTS_FIELD, Constants.DATE_START_FIELD_NAME, Constants.STATUS });
        emailDs.addTable(Constants.EM_TABLE_NAME);
        emailDs.addField(Constants.EM_TABLE_NAME, Constants.EMAIL_FIELD_NAME);
        emailDs
                .addRestriction(Restrictions.sql(RESERVE_DOT + USER_REQ_PREFIX + std + " = em.em_id"));
        // if resId is not zero makes the query for a single a reserve,
        // but in another case makes it for a group of reserves with a common father.
        if (resId.equals(ZERO)) {
            emailDs.addRestriction(
                    Restrictions.eq(Constants.RESERVE_TABLE_NAME, Constants.RES_PARENT, parentId));
        } else {
            emailDs.addRestriction(
                    Restrictions.eq(Constants.RESERVE_TABLE_NAME, Constants.RES_ID, resId));
        }
        final DataRecord mailRecord = emailDs.getRecord();
        if (mailRecord != null) {
            valuesToMail.put("reserve.requested" + std + "mail", mailRecord.getString("em.email"));
            valuesToMail.put(RESERVE_DOT + Constants.RES_ID,
                    mailRecord.getNeutralValue(RESERVE_DOT + Constants.RES_ID));
            valuesToMail.put("reserve.user_requested_" + std,
                    mailRecord.getString("reserve.user_requested_" + std));
            valuesToMail.put(RESERVE_DOT + "comments",
                    StringUtil.notNull(mailRecord.getString(RESERVE_DOT + "comments")));
            valuesToMail.put(RESERVE_DOT + Constants.STATUS,
                    mailRecord.getString(RESERVE_DOT + Constants.STATUS));
            valuesToMail.put(RESERVE_DOT + Constants.DATE_START_FIELD_NAME,
                    mailRecord.getNeutralValue(RESERVE_DOT + Constants.DATE_START_FIELD_NAME));

            // Search the locale of the user to notify
            valuesToMail.put(LOCALE, EmailNotificationHelper
                    .getUserLocale(valuesToMail.get("reserve.requested" + std + "mail")));
        }

        // In case that we haven't found the locale to use in the email, we'll
        // take the default locale of the connected user
        if (StringUtil.isNullOrEmpty(valuesToMail.get(LOCALE))) {
            valuesToMail.put(LOCALE, ContextStore.get().getUser().getLocale());
        }
    }

    /**
     * Check whether the app should send a notification to the given role.
     *
     * @param std role to send to (requested by or for)
     * @param resId reservation id
     * @param parentId parent reservation id
     * @return true if notification should be sent, false otherwise
     */
    private boolean shouldSendNotification(final String std, final String resId,
                                           final String parentId) {

        boolean sendNotification = true;

        if ("".equals(resId)) {
            sendNotification = false;
        } else if (FOR_SUFFIX.equals(std)) {
            // check if requested for and requested by are the same
            final DataSource reserveDs =
                    DataSourceFactory.createDataSourceForFields(Constants.RESERVE_TABLE_NAME,
                            new String[] { USER_REQUESTED_BY, USER_REQUESTED_FOR });
            if (resId.equals(ZERO)) {
                reserveDs.addRestriction(
                        Restrictions.eq(Constants.RESERVE_TABLE_NAME, Constants.RES_PARENT, parentId));
            } else {
                reserveDs.addRestriction(
                        Restrictions.eq(Constants.RESERVE_TABLE_NAME, Constants.RES_ID, resId));
            }

            final DataRecord record = reserveDs.getRecord();
            if (record != null && record
                    .getString(Constants.RESERVE_TABLE_NAME + Constants.DOT + USER_REQUESTED_BY)
                    .equals(record.getString(
                            Constants.RESERVE_TABLE_NAME + Constants.DOT + USER_REQUESTED_FOR))) {
                sendNotification = false;
            }
        }
        return sendNotification;
    }

    // ---------------------------------------------------------------------------------------------
    // END notifyRequestedStd
    // ---------------------------------------------------------------------------------------------

    // ---------------------------------------------------------------------------------------------
    // BEGIN saveRoomOverride
    // ---------------------------------------------------------------------------------------------

    /**
     * Modify the given rooms' reservable property and create a configuration record and an
     * arrangement record if necessary.
     *
     * @param records the room records
     * @param reservable new value for reservable property
     */
    public void saveRoomsOverride(final DataSetList records, final int reservable) {
        for (final DataRecord record : records.getRecords()) {
            this.saveRoomOverride(record, reservable);
        }
    }

    /**
     * Modify the given room's reservable property and create a configuration record and an
     * arrangement record if necessary.
     *
     * @param record the room record
     * @param reservable new value for reservable property
     */
    public void saveRoomOverride(final DataRecord record, final int reservable) {
        String buildingId = "";
        String floorId = "";
        String roomId = "";
        String roomName = "";

        if (record != null) {
            // obtain room primary key values to update
            buildingId = record.getString("rm.bl_id");
            floorId = record.getString("rm.fl_id");
            roomId = record.getString("rm.rm_id");
            // If the room hasn't a name, we assign to the configuration name=id
            roomName = record.getString("rm.name");
            if (StringUtil.isNullOrEmpty(roomName)) {
                roomName = roomId;
            }
        }

        if (!StringUtil.isNullOrEmpty(buildingId) && !StringUtil.isNullOrEmpty(floorId)
                && !StringUtil.isNullOrEmpty(roomId)) {
            final DataSource roomDs =
                    DataSourceFactory.createDataSourceForFields(Constants.ROOM_TABLE,
                            new String[] { Constants.BL_ID_FIELD_NAME, Constants.FL_ID_FIELD_NAME,
                                    Constants.RM_ID_FIELD_NAME, Constants.RESERVABLE_FIELD_NAME });
            roomDs.addRestriction(
                    Restrictions.eq(Constants.ROOM_TABLE, Constants.BL_ID_FIELD_NAME, buildingId));
            roomDs.addRestriction(
                    Restrictions.eq(Constants.ROOM_TABLE, Constants.FL_ID_FIELD_NAME, floorId));
            roomDs.addRestriction(
                    Restrictions.eq(Constants.ROOM_TABLE, Constants.RM_ID_FIELD_NAME, roomId));

            final DataRecord roomRecord = roomDs.getRecord();
            roomRecord.setValue("rm.reservable", reservable);
            roomDs.updateRecord(roomRecord);

            if (reservable == 1) {
                final DataRecord configRecord =
                        createConfigurationRecord(buildingId, floorId, roomId, roomName);
                if (configRecord != null) {
                    createArrangementRecord(buildingId, floorId, roomId,
                            configRecord.getString(Constants.ROOM_CONFIG_TABLE + Constants.DOT
                                    + Constants.CONFIG_ID_FIELD_NAME),
                            "CONFERENCE");
                }

            }
        }
    }

    /**
     * Create a room configuration record if it doesn't exist one already for the room.
     *
     * @param buildingId building code
     * @param floorId floor code
     * @param roomId room code
     * @param roomName room name
     * @return existing configuration record or the new inserted one.
     */
    public DataRecord createConfigurationRecord(final String buildingId, final String floorId,
                                                final String roomId, final String roomName) {
        // Check that one configuration for the room doesn't exist first
        final DataSource configDs =
                DataSourceFactory.createDataSourceForFields(Constants.ROOM_CONFIG_TABLE,
                        new String[] { Constants.BL_ID_FIELD_NAME, Constants.FL_ID_FIELD_NAME,
                                Constants.RM_ID_FIELD_NAME, Constants.CONFIG_ID_FIELD_NAME,
                                "config_name" });
        configDs.addRestriction(
                Restrictions.eq(Constants.ROOM_CONFIG_TABLE, Constants.BL_ID_FIELD_NAME, buildingId));
        configDs.addRestriction(
                Restrictions.eq(Constants.ROOM_CONFIG_TABLE, Constants.FL_ID_FIELD_NAME, floorId));
        configDs.addRestriction(
                Restrictions.eq(Constants.ROOM_CONFIG_TABLE, Constants.RM_ID_FIELD_NAME, roomId));

        DataRecord resultRecord = configDs.getRecord();
        // If not exists, create the configuration
        if (resultRecord == null) {
            final DataRecord configRecord = configDs.createNewRecord();
            configRecord.setValue("rm_config.bl_id", buildingId);
            configRecord.setValue("rm_config.fl_id", floorId);
            configRecord.setValue("rm_config.rm_id", roomId);
            configRecord.setValue("rm_config.config_id", roomId);
            configRecord.setValue("rm_config.config_name", roomName);
            resultRecord = configDs.saveRecord(configRecord);
        }

        return resultRecord;
    }

    /**
     * Create a room arrangement record if it doesn't exist one already for the room.
     *
     * @param buildingId building code
     * @param floorId floor code
     * @param roomId room code
     * @param configId room configuration code
     * @param rmArrangeTypeId room arrangement type code
     * @return existing arrangement record or the new inserted one.
     */
    public DataRecord createArrangementRecord(final String buildingId, final String floorId,
                                              final String roomId, final String configId, final String rmArrangeTypeId) {
        // Check that one arrangement for the room doesn't exist first
        final DataSource arrangeDs =
                DataSourceFactory.createDataSourceForFields(Constants.RM_ARRANGE_TABLE,
                        new String[] { Constants.BL_ID_FIELD_NAME, Constants.FL_ID_FIELD_NAME,
                                Constants.RM_ID_FIELD_NAME, Constants.CONFIG_ID_FIELD_NAME,
                                Constants.RM_ARRANGE_TYPE_ID_FIELD_NAME, Constants.DATE_END_FIELD_NAME,
                                Constants.DAY_END_FIELD_NAME, Constants.DAY_START_FIELD_NAME,
                                Constants.IS_DEFAULT_FIELD_NAME, Constants.RESERVABLE_FIELD_NAME,
                                Constants.CANCEL_TIME_FIELD_NAME, Constants.MAX_CAPACITY_FIELD_NAME,
                                Constants.ANNOUNCE_TIME_FIELD_NAME, Constants.MAX_DAYS_AHEAD_FIELD_NAME,
                                Constants.EXTERNAL_ALLOWED_FIELD_NAME });

        arrangeDs.addRestriction(
                Restrictions.eq(Constants.RM_ARRANGE_TABLE, Constants.BL_ID_FIELD_NAME, buildingId));
        arrangeDs.addRestriction(
                Restrictions.eq(Constants.RM_ARRANGE_TABLE, Constants.FL_ID_FIELD_NAME, floorId));
        arrangeDs.addRestriction(
                Restrictions.eq(Constants.RM_ARRANGE_TABLE, Constants.RM_ID_FIELD_NAME, roomId));
        arrangeDs.addRestriction(
                Restrictions.eq(Constants.RM_ARRANGE_TABLE, Constants.CONFIG_ID_FIELD_NAME, configId));
        arrangeDs.addRestriction(Restrictions.eq(Constants.RM_ARRANGE_TABLE,
                Constants.RM_ARRANGE_TYPE_ID_FIELD_NAME, rmArrangeTypeId));

        DataRecord resultRecord = arrangeDs.getRecord();
        // If not exists, create the arrangement
        if (resultRecord == null) {

            final DataRecord arrangeRecord = arrangeDs.createNewRecord();
            arrangeRecord.setValue(
                    Constants.RM_ARRANGE_TABLE + Constants.DOT + Constants.BL_ID_FIELD_NAME,
                    buildingId);
            arrangeRecord.setValue(
                    Constants.RM_ARRANGE_TABLE + Constants.DOT + Constants.FL_ID_FIELD_NAME, floorId);
            arrangeRecord.setValue(
                    Constants.RM_ARRANGE_TABLE + Constants.DOT + Constants.RM_ID_FIELD_NAME, roomId);
            arrangeRecord.setValue(
                    Constants.RM_ARRANGE_TABLE + Constants.DOT + Constants.CONFIG_ID_FIELD_NAME,
                    configId);
            arrangeRecord.setValue(Constants.RM_ARRANGE_TABLE + Constants.DOT
                            + Constants.RM_ARRANGE_TYPE_ID_FIELD_NAME,
                    rmArrangeTypeId);
            setDefaultValuesForArrangementRecord(arrangeRecord);
            resultRecord = arrangeDs.saveRecord(arrangeRecord);
        }

        return resultRecord;
    }

    /**
     * Add default values into rm_arrange record. Default values were specified in APP-6070.
     *
     * @param arrangeRecord rm_arrange record
     */
    private void setDefaultValuesForArrangementRecord(final DataRecord arrangeRecord) {
        final EventHandlerContext context = ContextStore.get().getEventHandlerContext();
        final JSONObject timelineHours = TimelineHelper.getTimelineLimits(context);

        final int timelineStart = timelineHours.getInt(TimelineHelper.JSON_TIMELINE_START_HOUR);
        final Calendar cal = Calendar.getInstance();
        cal.set(0, 0, 0, timelineStart, 0, 0);
        final Time timelineStartTime = new Time(cal.getTimeInMillis());

        final int timelineEnd = timelineHours.getInt(TimelineHelper.JSON_TIMELINE_END_HOUR);
        cal.set(0, 0, 0, timelineEnd, 0, 0);
        final Time timelineEndTime = new Time(cal.getTimeInMillis());

        arrangeRecord.setValue(
                Constants.RM_ARRANGE_TABLE + Constants.DOT + Constants.DAY_END_FIELD_NAME,
                timelineEndTime);
        arrangeRecord.setValue(
                Constants.RM_ARRANGE_TABLE + Constants.DOT + Constants.DAY_START_FIELD_NAME,
                timelineStartTime);
        arrangeRecord.setValue(
                Constants.RM_ARRANGE_TABLE + Constants.DOT + Constants.IS_DEFAULT_FIELD_NAME, 1);
        arrangeRecord.setValue(
                Constants.RM_ARRANGE_TABLE + Constants.DOT + Constants.RESERVABLE_FIELD_NAME, 1);
        arrangeRecord.setValue(
                Constants.RM_ARRANGE_TABLE + Constants.DOT + Constants.CANCEL_TIME_FIELD_NAME,
                timelineEndTime);
        arrangeRecord.setValue(
                Constants.RM_ARRANGE_TABLE + Constants.DOT + Constants.MAX_CAPACITY_FIELD_NAME,
                DEFAULT_MAX_CAPACITY);
        arrangeRecord.setValue(
                Constants.RM_ARRANGE_TABLE + Constants.DOT + Constants.ANNOUNCE_TIME_FIELD_NAME,
                timelineEndTime);
        arrangeRecord.setValue(
                Constants.RM_ARRANGE_TABLE + Constants.DOT + Constants.MAX_DAYS_AHEAD_FIELD_NAME,
                DEFAULT_MAX_DAYS_AHEAD);
        arrangeRecord.setValue(
                Constants.RM_ARRANGE_TABLE + Constants.DOT + Constants.EXTERNAL_ALLOWED_FIELD_NAME, 1);
    }

    // ---------------------------------------------------------------------------------------------
    // END saveRoomOverride
    // ---------------------------------------------------------------------------------------------

    // ---------------------------------------------------------------------------------------------
    // BEGIN saveArrangementFixedResource wfr
    // ---------------------------------------------------------------------------------------------
    /**
     * Save the defined fixed resource to all the room arrangements First, update if exists the
     * records of arrangements of this rooms. Second, insert if don't exist the records for the
     * arrangements of this rooms.
     *
     * Kb# 3015539 Added by Keven
     *
     * @param rmResourceStd resource standard identifier
     */
    public void saveArrangementFixedResource(final String rmResourceStd) {
        try {
            final JSONObject roomResourceStandard = new JSONObject(rmResourceStd + ")");
            SqlUtils.executeUpdate("rm_resource_std",
                    "UPDATE rm_resource_std SET resource_std = "
                            + SqlUtils.formatValueForSql(roomResourceStandard.getString("resource_std"))
                            + ", eq_id = "
                            + SqlUtils.formatValueForSql(roomResourceStandard.getString("eq_id"))
                            + ", description = "
                            + SqlUtils.formatValueForSql(roomResourceStandard.getString("description"))
                            + " WHERE bl_id = "
                            + SqlUtils.formatValueForSql(
                            roomResourceStandard.getString(Constants.BL_ID_FIELD_NAME))
                            + " AND fl_id = "
                            + SqlUtils.formatValueForSql(
                            roomResourceStandard.getString(Constants.FL_ID_FIELD_NAME))
                            + " AND rm_id = "
                            + SqlUtils.formatValueForSql(
                            roomResourceStandard.getString(Constants.RM_ID_FIELD_NAME))
                            + " AND config_id = "
                            + SqlUtils.formatValueForSql(
                            roomResourceStandard.getString(Constants.CONFIG_ID_FIELD_NAME))
                            + " AND fixed_resource_id = " + SqlUtils.formatValueForSql(
                            roomResourceStandard.getString("fixed_resource_id")));

            SqlUtils.executeUpdate("rm_resource_std",
                    "INSERT INTO rm_resource_std (rm_arrange_type_id, bl_id, fl_id, rm_id, config_id, fixed_resource_id, resource_std, eq_id, description) "
                            + " SELECT a.rm_arrange_type_id AS rm_arrange_type_id, "
                            + SqlUtils.formatValueForSql(
                            roomResourceStandard.getString(Constants.BL_ID_FIELD_NAME))
                            + " AS bl_id, "
                            + SqlUtils.formatValueForSql(
                            roomResourceStandard.getString(Constants.FL_ID_FIELD_NAME))
                            + " AS fl_id, "
                            + SqlUtils.formatValueForSql(
                            roomResourceStandard.getString(Constants.RM_ID_FIELD_NAME))
                            + " AS rm_id, "
                            + SqlUtils.formatValueForSql(
                            roomResourceStandard.getString(Constants.CONFIG_ID_FIELD_NAME))
                            + " AS config_id, "
                            + SqlUtils
                            .formatValueForSql(roomResourceStandard.getString("fixed_resource_id"))
                            + " AS fixed_resource_id, "
                            + SqlUtils.formatValueForSql(roomResourceStandard.getString("resource_std"))
                            + " AS resource_std, "
                            + SqlUtils.formatValueForSql(roomResourceStandard.getString("eq_id"))
                            + " AS eq_id, "
                            + SqlUtils.formatValueForSql(roomResourceStandard.getString("description"))
                            + " AS description FROM rm_arrange a WHERE a.bl_id = "
                            + SqlUtils.formatValueForSql(
                            roomResourceStandard.getString(Constants.BL_ID_FIELD_NAME))
                            + " AND a.fl_id = "
                            + SqlUtils.formatValueForSql(
                            roomResourceStandard.getString(Constants.FL_ID_FIELD_NAME))
                            + " AND a.rm_id = "
                            + SqlUtils.formatValueForSql(
                            roomResourceStandard.getString(Constants.RM_ID_FIELD_NAME))
                            + " AND a.config_id = "
                            + SqlUtils.formatValueForSql(
                            roomResourceStandard.getString(Constants.CONFIG_ID_FIELD_NAME))
                            + " AND NOT EXISTS (SELECT 1 FROM rm_resource_std b WHERE b.bl_id = "
                            + SqlUtils.formatValueForSql(
                            roomResourceStandard.getString(Constants.BL_ID_FIELD_NAME))
                            + " AND b.fl_id = "
                            + SqlUtils.formatValueForSql(
                            roomResourceStandard.getString(Constants.FL_ID_FIELD_NAME))
                            + " AND b.rm_id = "
                            + SqlUtils.formatValueForSql(
                            roomResourceStandard.getString(Constants.RM_ID_FIELD_NAME))
                            + " AND b.config_id = "
                            + SqlUtils.formatValueForSql(
                            roomResourceStandard.getString(Constants.CONFIG_ID_FIELD_NAME))
                            + " AND b.fixed_resource_id = "
                            + SqlUtils
                            .formatValueForSql(roomResourceStandard.getString("fixed_resource_id"))
                            + " AND b.rm_arrange_type_id = a.rm_arrange_type_id)");
        } catch (final ParseException exception) {
            throw new ExceptionBase("Parsing JSON expression failed", exception);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // END saveArrangementFixedResource wfr
    // ---------------------------------------------------------------------------------------------

    // ---------------------------------------------------------------------------------------------
    // BEGIN closeReservations wfr
    // ---------------------------------------------------------------------------------------------
    /**
     * The data from tables reserve, reserve_rm, reserve_rs and wr will be moved to the historical
     * data tables hreserve, hreserve_rm, hreserve_rs and hwr if the current date is X days after
     * the meeting date. Inputs: context context (EventHandlerContext); Outputs:
     *
     * @param context Event handler context.
     */
    public void closeReservations(final EventHandlerContext context) {

        // First of all the system must get the DaysBeforeArchiving value to
        // take into account in the process from the reservation parameters table
        final int daysBeforeArchiving = Configuration.getActivityParameterInt(
                ReservationsContextHelper.RESERVATIONS_ACTIVITY, "DaysBeforeArchiving", 0);

        if (daysBeforeArchiving > 0) {
            // BEGIN: Move to HRESERVE_RM historical table
            SqlUtils.executeUpdate("hreserve_rm", buildSqlToArchiveReserveRm(daysBeforeArchiving));
            setArchiveStatus("hreserve_rm");
            // Remove the inserted reservations into the historical table from
            // the original table
            SqlUtils.executeUpdate("reserve_rm",
                    "DELETE FROM reserve_rm WHERE ${sql.daysBeforeCurrentDate('date_start')} >= "
                            + SqlUtils.formatValueForSql(daysBeforeArchiving));
            // END: Move to HRESERVE_RM historical table

            // BEGIN: Move to HRESERVE_RS historical table
            SqlUtils.executeUpdate("hreserve_rs", buildSqlToArchiveReserveRs(daysBeforeArchiving));
            setArchiveStatus("hreserve_rs");
            SqlUtils.executeUpdate("reserve_rs",
                    "DELETE FROM reserve_rs WHERE ${sql.daysBeforeCurrentDate('date_start')} >= "
                            + SqlUtils.formatValueForSql(daysBeforeArchiving));
            // END: Move to HRESERVE_RS historical table

            // BEGIN: Move to HRESERVE historical table
            SqlUtils.executeUpdate("hreserve", buildSqlToArchiveReserve(daysBeforeArchiving));
            setArchiveStatus("hreserve");
            SqlUtils.executeUpdate(Constants.RESERVE_TABLE_NAME,
                    "DELETE FROM reserve WHERE ${sql.daysBeforeCurrentDate('date_start')} >= "
                            + SqlUtils.formatValueForSql(daysBeforeArchiving));
            // END: Move to HRESERVE historical table
        }
    }

    /**
     * Build SQL statement to archive the reserve table.
     *
     * @param daysBeforeArchiving the number of days old a reservation must be before archiving
     * @return SQL statement
     */
    static String buildSqlToArchiveReserve(final int daysBeforeArchiving) {
        final TableDef.ThreadSafe reserveTable =
                ContextStore.get().getProject().loadTableDef(Constants.RESERVE_TABLE_NAME);
        final String[] newFieldNames =
                new String[] { "outlook_unique_id", "occurrence_index", "res_conference" };

        // Insert the reservations that meet the criteria into the historical
        // table
        String sql =
                " INSERT INTO hreserve (res_id, user_created_by, user_requested_by, user_requested_for,"
                        + " user_last_modified_by, cost_res, date_created, date_last_modified, date_cancelled,"
                        + " dv_id, dp_id, ac_id, phone, email, reservation_name, comments, date_start, date_end,"
                        + " time_start, time_end, contact, doc_event," + " recurring_rule, status ";
        sql += addNewFieldsToArchive(reserveTable, newFieldNames);
        sql += ") SELECT res_id, user_created_by, user_requested_by, user_requested_for,"
                + " user_last_modified_by, cost_res, date_created, date_last_modified, date_cancelled,"
                + " dv_id, dp_id, ac_id, phone, email, reservation_name, comments, date_start, date_end,"
                + " time_start, time_end, contact, doc_event, recurring_rule, status ";
        sql += addNewFieldsToArchive(reserveTable, newFieldNames);
        sql += " FROM reserve WHERE ${sql.daysBeforeCurrentDate('date_start')} >= "
                + SqlUtils.formatValueForSql(daysBeforeArchiving);
        return sql;
    }

    /**
     * Build SQL statement to archive the reserve_rm table.
     *
     * @param daysBeforeArchiving the number of days old a reservation must be before archiving
     * @return SQL statement
     */
    static String buildSqlToArchiveReserveRm(final int daysBeforeArchiving) {
        final TableDef.ThreadSafe reserveRmTable =
                ContextStore.get().getProject().loadTableDef("reserve_rm");
        final String[] newFieldNames = new String[] { "verified", "attendees_in_room", "date_end" };
        // Insert the room reservations that meet the criteria into the
        // historical table
        String sql = "INSERT INTO hreserve_rm (res_id, rmres_id, date_start, time_start, time_end, "
                + "cost_rmres, user_last_modified_by, date_last_modified, date_created, "
                + "date_cancelled, date_rejected, bl_id, fl_id, rm_id, config_id, "
                + "rm_arrange_type_id, recurring_order, comments, status, guests_internal, guests_external ";
        sql += addNewFieldsToArchive(reserveRmTable, newFieldNames);
        sql += ") SELECT res_id, rmres_id, date_start, time_start, time_end, cost_rmres,"
                + " user_last_modified_by, date_last_modified, date_created, date_cancelled,"
                + " date_rejected, bl_id, fl_id, rm_id, config_id, rm_arrange_type_id,"
                + " recurring_order, comments, status, guests_internal, guests_external ";
        sql += addNewFieldsToArchive(reserveRmTable, newFieldNames);
        sql += " FROM reserve_rm WHERE ${sql.daysBeforeCurrentDate('date_start')} >= "
                + SqlUtils.formatValueForSql(daysBeforeArchiving);
        return sql;
    }

    /**
     * Build SQL statement to archive the reserve_rs table.
     *
     * @param daysBeforeArchiving the number of days old a reservation must be before archiving
     * @return SQL statement
     */
    static String buildSqlToArchiveReserveRs(final int daysBeforeArchiving) {
        final TableDef.ThreadSafe reserveRsTable =
                ContextStore.get().getProject().loadTableDef("reserve_rs");
        final String[] newFieldNames = new String[] { "date_end" };
        // Insert the resource reservations that meet the criteria into the
        // historical table
        String sql = "INSERT INTO hreserve_rs (res_id, rsres_id, date_start, time_start, time_end,"
                + " cost_rsres, user_last_modified_by, date_last_modified, date_created,"
                + " date_cancelled, date_rejected, bl_id, fl_id, rm_id, resource_id,"
                + " quantity, recurring_order, comments, status ";
        sql += addNewFieldsToArchive(reserveRsTable, newFieldNames);
        sql += ") SELECT res_id, rsres_id, date_start, time_start, time_end, cost_rsres,"
                + " user_last_modified_by, date_last_modified, date_created, date_cancelled,"
                + " date_rejected, bl_id, fl_id, rm_id, resource_id, quantity,"
                + " recurring_order, comments, status ";
        sql += addNewFieldsToArchive(reserveRsTable, newFieldNames);
        sql += " FROM reserve_rs WHERE ${sql.daysBeforeCurrentDate('date_start')} >= "
                + SqlUtils.formatValueForSql(daysBeforeArchiving);

        return sql;
    }

    /**
     * Add new fields to the archive statement depending on whether they exist in the schema.
     *
     * @param tableDef the table definition
     * @param fieldNames field names to check
     * @return comma-separated list of fieldNames that exist in the given table
     */
    private static String addNewFieldsToArchive(final TableDef.ThreadSafe tableDef,
                                                final String[] fieldNames) {

        String sql = "";
        for (final String fieldName : fieldNames) {
            if (tableDef.findFieldDef(fieldName) != null) {
                sql += ", " + fieldName;
            }
        }
        return sql;
    }

    /**
     * Set the status value of all Awaiting App. and Confirmed records in the given table to Closed.
     * KB#3030979
     *
     * @param tableName table to update
     */
    private void setArchiveStatus(final String tableName) {
        SqlUtils.executeUpdate(tableName, "UPDATE " + tableName
                + " SET status = 'Closed' WHERE status = 'Awaiting App.' OR status = 'Confirmed'");
    }
    // ---------------------------------------------------------------------------------------------
    // END closeReservations wfr
    // ---------------------------------------------------------------------------------------------

    // ---------------------------------------------------------------------------------------------
    // BEGIN getTimelineLimits
    // ---------------------------------------------------------------------------------------------
    /**
     * Gets the afm_activity_params values for timelineStartTime and timelineEndTime.
     */
    public void getTimelineLimits() {
        final EventHandlerContext context = ContextStore.get().getEventHandlerContext();
        final JSONObject timelineHours = TimelineHelper.getTimelineLimits(context);

        final JSONObject timelineLimits = new JSONObject();
        timelineLimits.put("TimelineStartTime",
                String.format("%02d", timelineHours.getInt(TimelineHelper.JSON_TIMELINE_START_HOUR))
                        + ":00.00");
        timelineLimits.put("TimelineEndTime",
                String.format("%02d", timelineHours.getInt(TimelineHelper.JSON_TIMELINE_END_HOUR))
                        + ":00.00");

        context.addResponseParameter("jsonExpression", timelineLimits.toString());
    }

    // ---------------------------------------------------------------------------------------------
    // END getTimelineLimits
    // ---------------------------------------------------------------------------------------------

    // ---------------------------------------------------------------------------------------------
    // BEGIN getNumberPendingReservations
    // ---------------------------------------------------------------------------------------------
    /**
     * Gets the number of pending reservations for a room using specified field values.
     *
     * @param record the room arrangement record
     */
    public void getNumberPendingReservations(final DataRecord record) {
        // get input parameter containing <record> XML string
        final EventHandlerContext context = ContextStore.get().getEventHandlerContext();
        // this.log.info("Input parameter: " + recordXmlString);
        String blId = "";
        String flId = "";
        String rmId = "";
        String configId = "";
        String rmArrangeTypeId = "";
        Integer reservable = Integer.valueOf(0);

        // getNumberPendingReservations rule error message
        final String errMessage =
                ReservationsContextHelper.localizeMessage("GETNUMBERPENDINGRESERVATIONS_WFR",
                        ContextStore.get().getUser().getLocale(), "GETNUMBERPENDINGRESERVATIONSERROR");

        if (record != null) {
            // obtain room primary key values and the reservable field
            blId = record.getString("rm_arrange.bl_id");
            flId = record.getString("rm_arrange.fl_id");
            rmId = record.getString("rm_arrange.rm_id");
            configId = record.getString("rm_arrange.config_id");
            rmArrangeTypeId = record.getString("rm_arrange.rm_arrange_type_id");
            reservable = record.getInt("rm_arrange.reservable");
        }

        if (!("".equals(blId) || "".equals(flId) || "".equals(rmId) || "".equals(configId)
                || "".equals(rmArrangeTypeId)) && (reservable == 0)) {
            final Restriction restriction = Restrictions
                    .and(new com.archibus.datasource.restriction.Restrictions.Restriction.Clause[] {
                            Restrictions.ne(Constants.RESERVE_RM_TABLE, Constants.STATUS,
                                    Constants.STATUS_CANCELLED),
                            Restrictions.ne(Constants.RESERVE_RM_TABLE, Constants.STATUS,
                                    Constants.STATUS_REJECTED),
                            Restrictions.eq(Constants.RESERVE_RM_TABLE, Constants.BL_ID_FIELD_NAME,
                                    blId),
                            Restrictions.eq(Constants.RESERVE_RM_TABLE, Constants.FL_ID_FIELD_NAME,
                                    flId),
                            Restrictions.eq(Constants.RESERVE_RM_TABLE, Constants.RM_ID_FIELD_NAME,
                                    rmId),
                            Restrictions.eq(Constants.RESERVE_RM_TABLE, Constants.CONFIG_ID_FIELD_NAME,
                                    configId),
                            Restrictions.eq(Constants.RESERVE_RM_TABLE,
                                    Constants.RM_ARRANGE_TYPE_ID_FIELD_NAME, rmArrangeTypeId) });
            final int pending = DataStatistics.getInt(Constants.RESERVE_RM_TABLE,
                    Constants.RMRES_ID_FIELD_NAME, "count", restriction);
            final JSONObject pendingReservations = new JSONObject();
            pendingReservations.put("numberPendingRes", String.valueOf(pending));
            context.addResponseParameter("jsonExpression", pendingReservations.toString());
        } else {
            context.addResponseParameter(MESSAGE, errMessage);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // END getNumberPendingReservations
    // ---------------------------------------------------------------------------------------------

    // ---------------------------------------------------------------------------------------------
    // BEGIN getNumberPendingResourceReservations
    // ---------------------------------------------------------------------------------------------
    /**
     * Gets the number of pending reservations for a resource using specified field values.
     *
     * @param record the resource record
     */
    public void getNumberPendingResourceReservations(final DataRecord record) {
        final EventHandlerContext context = ContextStore.get().getEventHandlerContext();
        String resourceId = "";
        Integer reservable = Integer.valueOf(0);

        // getNumberPendingResourceReservations rule error message
        final String errMessage = ReservationsContextHelper.localizeMessage(
                "GETNUMBERPENDINGRESOURCERESERVATIONS_WFR", ContextStore.get().getUser().getLocale(),
                "GETNUMBERPENDINGRESOURCERESERVATIONSERROR");

        if (record != null) {
            // obtain resource primary key values and the reservable field
            resourceId = record.getString("resources.resource_id");
            reservable = record.getInt("resources.reservable");
        }

        if (!"".equals(resourceId) && reservable == 0) {
            final Restriction restriction = Restrictions
                    .and(new com.archibus.datasource.restriction.Restrictions.Restriction.Clause[] {
                            Restrictions.ne(Constants.RESERVE_RS_TABLE, Constants.STATUS,
                                    Constants.STATUS_CANCELLED),
                            Restrictions.ne(Constants.RESERVE_RS_TABLE, Constants.STATUS,
                                    Constants.STATUS_REJECTED),
                            Restrictions.eq(Constants.RESERVE_RS_TABLE, Constants.RESOURCE_ID_FIELD,
                                    resourceId) });
            final int pending = DataStatistics.getInt(Constants.RESERVE_RS_TABLE,
                    Constants.RSRES_ID_FIELD_NAME, "count", restriction);
            final JSONObject pendingReservations = new JSONObject();
            pendingReservations.put("numberPendingRes", String.valueOf(pending));
            context.addResponseParameter("jsonExpression", pendingReservations.toString());
        } else {
            context.addResponseParameter(MESSAGE, errMessage);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // END getNumberPendingResourcesReservations
    // ---------------------------------------------------------------------------------------------

    // ---------------------------------------------------------------------------------------------
    // BEGIN notifyApprover
    // ---------------------------------------------------------------------------------------------
    /**
     * This rule gets the identifier of a room or resource reservation which approval time has
     * expired and notifies the selected user. Inputs: context (EventHandlerContext); res_id
     * (String) the res_id value; rmrsres_id (String) can be a rmres_id or a rsres_id value;
     * res_type (String) can be "room" or "resource"; user_to_notify (String) Outputs: message error
     * message in necesary case
     *
     * @param context Event handler context.
     * @param resType reservation time room or resource
     */
    public void notifyApprover(final EventHandlerContext context, final String resType) {

        context.getParameter(Constants.RES_ID);
        final String rmrsResId = (String) context.getParameter("rmrsres_id");
        final String userToNotify = (String) context.getParameter("user_to_notify");

        boolean allQueriesOk = true;
        final Map<String, String> valuesToMail = new HashMap<String, String>();

        // notification rule error message
        final String errMessage = ReservationsContextHelper.localizeMessage(NOTIFYAPPROVER_WFR,
                ContextStore.get().getUser().getLocale(), "NOTIFYAPPROVERERROR");

        try {

            // BEGIN: Only enter if exists a room or resource reservation
            // identifier and a user to notify.
            if ((!"".equals(rmrsResId)) && (!"".equals(userToNotify))) {
                findApproverInfo(userToNotify, valuesToMail);
            }

            // BEGIN: In case that we have the email direction to notify
            if (!"".equals(valuesToMail.get("approveremail"))) {
                // In case that we still haven't found the locale we'll take
                // English
                if ("".equals(valuesToMail.get(LOCALE))) {
                    valuesToMail.put(LOCALE, "en_US");
                }

                allQueriesOk = false;

                // Get room reservation info if it's a room reservation
                if (RES_TYPE_ROOM.equals(resType)) {
                    allQueriesOk = retrieveRoomInfo(rmrsResId, valuesToMail);
                } else if ("resource".equals(resType)) {
                    allQueriesOk = retrieveResourceInfo(rmrsResId, valuesToMail);
                }

                if (allQueriesOk) {
                    final String locale = valuesToMail.get(LOCALE);
                    // Get all messages to Mail in a Map (It is more easy to
                    // write)
                    final Map<String, String> messages = loadApproveMessages(locale);

                    // BEGIN: create message email
                    String subject = "";
                    String message = "";

                    // BEGIN: subject
                    subject += messages.get("SUBJECT");
                    // END: subject

                    // BEGIN: message

                    message += (RES_TYPE_ROOM.equals(resType) ? messages.get("BODY1")
                            : messages.get("BODY2")) + NEWLINE;

                    message += ReservationsContextHelper.getWebCentralUrl();
                    message +=
                            "/schema/ab-system/html/url-proxy.htm?viewName=ab-rr-approve-reservations.axvw";
                    message += DOUBLE_NEWLINE;

                    message += (RES_TYPE_ROOM.equals(resType) ? messages.get("BODY3")
                            : messages.get("BODY4")) + DOUBLE_NEWLINE;

                    message += (RES_TYPE_ROOM.equals(resType)
                            ? messages.get("BODY5") + SPACE + valuesToMail.get("rmres_id")
                            : messages.get("BODY6") + SPACE + valuesToMail.get("rsres_id"))
                            + NEWLINE;

                    message += messages.get("BODY7") + SPACE
                            + valuesToMail.get(Constants.DATE_START_FIELD_NAME) + NEWLINE;
                    message += messages.get("BODY8") + SPACE
                            + valuesToMail.get(Constants.TIME_START_FIELD_NAME) + NEWLINE;
                    message += includeEndDate(resType, valuesToMail, messages);
                    message += messages.get("BODY9") + SPACE
                            + valuesToMail.get(Constants.TIME_END_FIELD_NAME) + NEWLINE;
                    message += messages.get("BODY10") + SPACE + valuesToMail.get(USER_REQUESTED_FOR)
                            + NEWLINE;

                    if (RES_TYPE_ROOM.equals(resType)) {
                        message += messages.get("BODY11") + SPACE + valuesToMail.get("bl_id")
                                + NEWLINE;
                        message += messages.get("BODY12") + SPACE + valuesToMail.get("fl_id")
                                + NEWLINE;
                        message += messages.get("BODY13") + SPACE + valuesToMail.get("rm_id")
                                + NEWLINE;
                        message += messages.get("BODY14") + SPACE + valuesToMail.get("config_id")
                                + NEWLINE;
                        message += messages.get("BODY15") + SPACE
                                + valuesToMail.get("rm_arrange_type_id") + DOUBLE_NEWLINE;
                    } else {
                        message += messages.get("BODY16") + SPACE + valuesToMail.get("resource_id")
                                + NEWLINE;
                        message += messages.get("BODY17") + SPACE + valuesToMail.get("quantity")
                                + DOUBLE_NEWLINE;
                    }

                    message += EmailNotificationHelper.getServiceName();
                    // END: message
                    // END: Create message email

                    sendEmail(context, subject, message, valuesToMail.get("approveremail"));
                }
            } // END: In case that we have the email direction to notify

        } catch (final ExceptionBase e) {
            // KB#3036675: only log the email notification error of
            // notifyApprover during execution
            // of scheduled rule.
            Logger.getLogger(this.getClass()).error(errMessage, e);
        }
    }

    /**
     * Include end date in the email notification (if it exists in the schema).
     *
     * @param resType reservation type
     * @param valuesToMail contains the data of the reservation
     * @param messages the localized messages for approval
     * @return the date end or an empty string
     */
    private String includeEndDate(final String resType, final Map<String, String> valuesToMail,
                                  final Map<String, String> messages) {
        String endDateLine = "";
        if (RES_TYPE_ROOM.equals(resType)
                && SchemaUtils.fieldExistsInSchema(Constants.RESERVE_RM_TABLE,
                Constants.DATE_END_FIELD_NAME)
                || SchemaUtils.fieldExistsInSchema(Constants.RESERVE_RS_TABLE,
                Constants.DATE_END_FIELD_NAME)) {
            endDateLine += messages.get("BODY18") + SPACE
                    + valuesToMail.get(Constants.DATE_END_FIELD_NAME) + NEWLINE;
        }
        return endDateLine;
    }

    /**
     * Load all messages for notifying an approver from the database.
     *
     * @param locale locale to get the messages in
     * @return map with all messages by short id
     */
    private Map<String, String> loadApproveMessages(final String locale) {
        final Map<String, String> messageIds = new HashMap<String, String>();
        messageIds.put("SUBJECT", "NOTIFYAPPROVER_SUBJECT");
        for (int i = 1; i <= MAX_APPROVE_BODY; i++) {
            messageIds.put("BODY" + i, "NOTIFYAPPROVER_BODY_PART" + i);
        }
        // localize all in a single query
        final Map<String, String> longMessages = ReservationsContextHelper.localizeMessages(
                NOTIFYAPPROVER_WFR, locale, messageIds.values().toArray(new String[messageIds.size()]));
        // map back to the short id's used below
        final Map<String, String> messages = new HashMap<String, String>();
        for (final String messageId : messageIds.keySet()) {
            messages.put(messageId, longMessages.get(messageIds.get(messageId)));
        }
        return messages;
    }

    /**
     * Retrieve resource information for notifying the approver.
     *
     * @param rmrsResId resource allocation id
     * @param valuesToMail container to store the information
     * @return true if the info was found, false if nothing was found
     */
    private boolean retrieveResourceInfo(final String rmrsResId,
                                         final Map<String, String> valuesToMail) {
        boolean allQueriesOk = false;
        // Get resource reservation info if it's a resource
        // reservation
        final DataSource reserveResourceDs =
                DataSourceFactory.createDataSourceForFields(Constants.RESERVE_RS_TABLE,
                        new String[] { Constants.RSRES_ID_FIELD_NAME, Constants.DATE_START_FIELD_NAME,
                                Constants.TIME_START_FIELD_NAME, Constants.DATE_END_FIELD_NAME,
                                Constants.TIME_END_FIELD_NAME, Constants.RESOURCE_ID_FIELD,
                                Constants.QUANTITY_FIELD });
        reserveResourceDs.addTable(Constants.RESERVE_TABLE_NAME, DataSource.ROLE_STANDARD);
        reserveResourceDs.addField(Constants.RESERVE_TABLE_NAME, USER_REQUESTED_FOR);
        reserveResourceDs.addRestriction(
                Restrictions.eq(Constants.RESERVE_RS_TABLE, Constants.RSRES_ID_FIELD_NAME, rmrsResId));

        final DataRecord alloc = reserveResourceDs.getRecord();
        if (alloc != null) {
            valuesToMail.put(Constants.RSRES_ID_FIELD_NAME,
                    alloc.getNeutralValue(RESERVE_RS_DOT + Constants.RSRES_ID_FIELD_NAME));
            valuesToMail.put(USER_REQUESTED_FOR,
                    alloc.getString(Constants.RESERVE_TABLE_NAME + Constants.DOT + USER_REQUESTED_FOR));
            valuesToMail.put(Constants.DATE_START_FIELD_NAME,
                    alloc.getNeutralValue(RESERVE_RS_DOT + Constants.DATE_START_FIELD_NAME));
            valuesToMail.put(Constants.TIME_START_FIELD_NAME,
                    alloc.getNeutralValue(RESERVE_RS_DOT + Constants.TIME_START_FIELD_NAME));
            valuesToMail.put(Constants.DATE_END_FIELD_NAME,
                    alloc.getNeutralValue(RESERVE_RS_DOT + Constants.DATE_END_FIELD_NAME));
            valuesToMail.put(Constants.TIME_END_FIELD_NAME,
                    alloc.getNeutralValue(RESERVE_RS_DOT + Constants.TIME_END_FIELD_NAME));
            valuesToMail.put(Constants.RESOURCE_ID_FIELD,
                    alloc.getString(RESERVE_RS_DOT + Constants.RESOURCE_ID_FIELD));
            valuesToMail.put(Constants.QUANTITY_FIELD,
                    alloc.getNeutralValue(RESERVE_RS_DOT + Constants.QUANTITY_FIELD));
            allQueriesOk = true;
        }
        return allQueriesOk;
    }

    /**
     * Retrieve room information for notifying the approver.
     *
     * @param rmrsResId room allocation id
     * @param valuesToMail container to store the information
     * @return true if the info was found, false if nothing was found
     */
    private boolean retrieveRoomInfo(final String rmrsResId,
                                     final Map<String, String> valuesToMail) {
        boolean allQueriesOk = false;
        final DataSource reserveRoomDs = DataSourceFactory.createDataSourceForFields(
                Constants.RESERVE_RM_TABLE,
                new String[] { Constants.RMRES_ID_FIELD_NAME, Constants.DATE_START_FIELD_NAME,
                        Constants.TIME_START_FIELD_NAME, Constants.DATE_END_FIELD_NAME,
                        Constants.TIME_END_FIELD_NAME, Constants.BL_ID_FIELD_NAME,
                        Constants.FL_ID_FIELD_NAME, Constants.RM_ID_FIELD_NAME,
                        Constants.CONFIG_ID_FIELD_NAME, Constants.RM_ARRANGE_TYPE_ID_FIELD_NAME });
        reserveRoomDs.addTable(Constants.RESERVE_TABLE_NAME, DataSource.ROLE_STANDARD);
        reserveRoomDs.addField(Constants.RESERVE_TABLE_NAME, USER_REQUESTED_FOR);
        reserveRoomDs.addRestriction(
                Restrictions.eq(Constants.RESERVE_RM_TABLE, Constants.RMRES_ID_FIELD_NAME, rmrsResId));

        final DataRecord alloc = reserveRoomDs.getRecord();

        if (alloc != null) {
            valuesToMail.put(Constants.RMRES_ID_FIELD_NAME,
                    alloc.getNeutralValue(RESERVE_RM_DOT + Constants.RMRES_ID_FIELD_NAME));
            valuesToMail.put(USER_REQUESTED_FOR,
                    alloc.getString(Constants.RESERVE_TABLE_NAME + Constants.DOT + USER_REQUESTED_FOR));
            valuesToMail.put(Constants.DATE_START_FIELD_NAME,
                    alloc.getNeutralValue(RESERVE_RM_DOT + Constants.DATE_START_FIELD_NAME));
            valuesToMail.put(Constants.TIME_START_FIELD_NAME,
                    alloc.getNeutralValue(RESERVE_RM_DOT + Constants.TIME_START_FIELD_NAME));
            valuesToMail.put(Constants.DATE_END_FIELD_NAME,
                    alloc.getNeutralValue(RESERVE_RM_DOT + Constants.DATE_END_FIELD_NAME));
            valuesToMail.put(Constants.TIME_END_FIELD_NAME,
                    alloc.getNeutralValue(RESERVE_RM_DOT + Constants.TIME_END_FIELD_NAME));
            valuesToMail.put(Constants.BL_ID_FIELD_NAME,
                    alloc.getString(RESERVE_RM_DOT + Constants.BL_ID_FIELD_NAME));
            valuesToMail.put(Constants.FL_ID_FIELD_NAME,
                    alloc.getString(RESERVE_RM_DOT + Constants.FL_ID_FIELD_NAME));
            valuesToMail.put(Constants.RM_ID_FIELD_NAME,
                    alloc.getString(RESERVE_RM_DOT + Constants.RM_ID_FIELD_NAME));
            valuesToMail.put(Constants.CONFIG_ID_FIELD_NAME,
                    alloc.getString(RESERVE_RM_DOT + Constants.CONFIG_ID_FIELD_NAME));
            valuesToMail.put(Constants.RM_ARRANGE_TYPE_ID_FIELD_NAME,
                    alloc.getString(RESERVE_RM_DOT + Constants.RM_ARRANGE_TYPE_ID_FIELD_NAME));
            allQueriesOk = true;
        }
        return allQueriesOk;
    }

    /**
     * Find approver info in the database.
     *
     * @param userToNotify user name of the approver
     * @param valuesToMail container to store the approver info
     */
    private void findApproverInfo(final String userToNotify,
                                  final Map<String, String> valuesToMail) {
        // Search the email and locale of the user to notify
        final DataSource userDs = DataSourceFactory.createDataSourceForFields(
                Constants.AFM_USERS_TABLE, new String[] { Constants.EMAIL_FIELD_NAME, LOCALE });
        userDs
                .addRestriction(Restrictions.eq(Constants.AFM_USERS_TABLE, "user_name", userToNotify));
        final DataRecord userRecord = userDs.getRecord();

        // If the email and locale is found
        if (userRecord != null) {
            valuesToMail.put(LOCALE,
                    userRecord.getString(Constants.AFM_USERS_TABLE + Constants.DOT + LOCALE));
            valuesToMail.put("approveremail", userRecord
                    .getString(Constants.AFM_USERS_TABLE + Constants.DOT + Constants.EMAIL_FIELD_NAME));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // END notifyApprover
    // ---------------------------------------------------------------------------------------------

} // Class
