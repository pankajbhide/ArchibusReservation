package com.archibus.app.reservation.service;

import java.awt.*;
import java.text.ParseException;
import java.util.*;

import com.archibus.datasource.DataSource;
import com.archibus.datasource.DataSourceFactory;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.model.view.datasource.AbstractRestrictionDef;
import org.json.JSONObject;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.Recurrence;
import com.archibus.app.reservation.service.helpers.*;
import com.archibus.app.reservation.util.*;
import com.archibus.datasource.data.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Room Reservation Service for workflow rules in the new reservation module.
 * <p>
 * This class provided all workflow rule for the room reservation creation view: Called from
 * ab-rr-create-room-reservation.axvw<br/>
 * Called from ab-rr-create-room-reservation-confirm.axvw<br/>
 * <p>
 * <p>
 * The class will be defined as a Spring bean and will reference other Spring beans. <br/>
 * The Calendar service can have different implementations that implement the ICalendar interface.
 * <br/>
 * All Spring beans are defined as prototype.
 * </p>
 *
 * @author Bart Vanderschoot
 * @since 21.2
 *
 */
public class RoomReservationService extends RoomReservationWfrBase {

    /** The attendee service. */
    private IAttendeeService attendeeService;

    /**
     * Save room reservation.
     *
     * The room reservation can be a single or a recurrent reservation. When editing a recurrent
     * reservation, the recurrence pattern and reservation dates cannot change. When editing a
     * single occurrence, the date might change.
     *
     * @param reservation the reservation
     * @param roomAllocation the room allocation
     * @param resourceList the resource list
     * @param cateringList the catering list
     * @return the conflicted reservation records
     */
    public DataSetList saveRoomReservation(final DataRecord reservation,
            final DataRecord roomAllocation, final DataSetList resourceList,
            final DataSetList cateringList) {

        final RoomReservation roomReservation = this.compileRoomReservation(reservation,
            roomAllocation, resourceList, cateringList);
        ReservationUtils.truncateComments(roomReservation);

        Recurrence recurrence = null;
        List<RoomReservation> createdReservations = null;

        final List<RoomReservation> originalReservations = getOriginalReservations(roomReservation);

        WorkRequestService.setFlagToSendEmailsInSingleJob();
        if (ReservationWfrServiceHelper.isNewRecurrenceOrEditSeries(roomReservation)) {
            // prepare for new recurring reservation and correct the end date
            recurrence = ReservationWfrServiceHelper.prepareNewRecurrence(roomReservation);

            if (recurrence == null) {
                createdReservations =
                        this.reservationService.editRecurringReservation(roomReservation);
            } else {
                // Room and Resource availability is verified by RoomReservationDataSource.
                createdReservations = this.reservationService
                    .saveRecurringReservation(roomReservation, recurrence, null);
            }
        } else {
            // Room and Resource availability is verified by RoomReservationDataSource.
            this.reservationService.saveReservation(roomReservation);
            createdReservations = new ArrayList<RoomReservation>();
            createdReservations.add(roomReservation);
        }
        // store the generated reservation instances in the reservation
        roomReservation.setCreatedReservations(createdReservations);
        WorkRequestService.startJobToSendEmailsInSingleJob();

        // include a description of the conflicts in the reservation comments
        final SortedSet<Date> conflictDates = getConflictDates(createdReservations);
        this.messagesService.insertConflictsDescription(roomReservation, conflictDates);

        // determine the time zone of the building and set the local time zone
        final String buildingId = roomAllocation.getString("reserve_rm.bl_id");
        roomReservation.setTimeZone(this.timeZoneCache.getBuildingTimeZone(buildingId));
        for (final RoomReservation createdReservation : createdReservations) {
            createdReservation.setTimeZone(roomReservation.getTimeZone());
        }

        final int originalOccurrenceIndex = roomReservation.getOccurrenceIndex();

        this.calendarServiceWrapper.saveCalendarEvent(reservation, roomReservation,
            originalReservations);

        // If the reservation is now the first in the series, update the parent id.
        if (roomReservation.getOccurrenceIndex() == 1 && originalOccurrenceIndex > 1) {
            roomReservation.setParentId(roomReservation.getReserveId());
        }

        // Save the reservation(s) again to persist the appointment unique id
        // also remove the recurrence if required.
        for (final RoomReservation createdReservation : createdReservations) {
            this.updateAfterSavingCalendarEvent(createdReservation, roomReservation.getUniqueId(),
                roomReservation.getParentId(), roomReservation.getComments());
        }
        DataSetList wrappedConflictDates = null;
        if (recurrence == null) {
            wrappedConflictDates = new DataSetList();
        } else {
            wrappedConflictDates = this.wrapConflictDates(conflictDates);
        }

        this.updateReserveRecordAfterSave(reservation, roomReservation);
        ReservationsContextHelper.ensureResultMessageIsSet();

        return wrappedConflictDates;
    }

    /**lbnl - Brent Hopkins - send a cancel email to any attendees removed from a reservation **/
    public void lbnlStartCancelEmails(final DataRecord reservation,
                                           final DataRecord roomAllocation, final DataRecord oReservation,
                                           final DataRecord oRoomAllocation, final DataSetList resourceList,
                                           final DataSetList cateringList) {

        final RoomReservation roomReservation = this.compileRoomReservation(reservation,
                roomAllocation, resourceList, cateringList);
        ReservationUtils.truncateComments(roomReservation);

        final RoomReservation origReservation = this.compileRoomReservation(oReservation,
                oRoomAllocation, resourceList, cateringList);
        ReservationUtils.truncateComments(roomReservation);

        List<RoomReservation> createdReservations = null;

        if(ReservationWfrServiceHelper.isNewRecurrenceOrEditSeries(roomReservation)){
            createdReservations = this.reservationService.lbnlGetRecurringReservation(roomReservation);
            origReservation.setCreatedReservations(createdReservations);
        }

        WorkRequestService.setFlagToSendEmailsInSingleJob();

        WorkRequestService.startJobToSendEmailsInSingleJob();

        this.calendarServiceWrapper.lbnlHelpCancelEmails(roomReservation, origReservation, ReservationWfrServiceHelper.isNewRecurrenceOrEditSeries(roomReservation));

        return;
    }



    /**
     * Update the unique id for the reservation after saving the calendar event. Remove the
     * recurrence from the stored reservation if the matching created reservation is not recurring.
     * Update the comments of the occurrence to match the given comments.
     *
     * @param createdReservation the created reservation
     * @param uniqueId the new unique id to save in the reservation
     * @param parentId the new parent reservation id
     * @param comments the new comments to set
     */
    private void updateAfterSavingCalendarEvent(final RoomReservation createdReservation,
                                                final String uniqueId, final Integer parentId, final String comments) {
        final RoomReservation storedReservation =
                this.reservationDataSource.get(createdReservation.getReserveId());

        if ((createdReservation.getParentId() == null || createdReservation.getParentId() == 0)
                && storedReservation.getParentId() != null) {
            // the occurrence was removed from the recurrence so it now has a different unique id
            storedReservation.setUniqueId(createdReservation.getUniqueId());
            ReservationUtils.removeRecurrence(storedReservation);
            this.reservationDataSource.update(storedReservation,
                    this.reservationDataSource.get(storedReservation.getReserveId()));
        } else {
            storedReservation.setUniqueId(uniqueId);
            /*
             * If the occurrence was not removed from the recurrence, set its parent id to match the
             * parent id of the first reservation being edited.
             */
            storedReservation.setParentId(parentId);
            storedReservation.setOccurrenceIndex(createdReservation.getOccurrenceIndex());
            storedReservation.setComments(comments);
            this.reservationDataSource.update(storedReservation);
        }
    }

    /**
     * Get the location string for a room reservation.
     *
     * @param reservation the reservation
     * @param roomAllocation the room allocation
     * @return the location string
     */
    public String getLocationString(final DataRecord reservation, final DataRecord roomAllocation) {
        return this.spaceService.getLocationString(this.roomAllocationDataSource
                .convertRecordToObject(roomAllocation).getRoomArrangement());
    }

    /**
     * Cancel single room reservation.
     *
     * @param reservationId reservation id
     * @param comments the comments
     * @param cancelMeeting true to cancel the meeting, false to only remove the location - null is
     *            interpreted as true
     */
    public void cancelRoomReservation(final Integer reservationId, final String comments,
                                      final Boolean cancelMeeting) {
        RoomReservation roomReservation = null;
        if (reservationId != null && reservationId > 0) {
            // Get the reservation in the building time zone.
            roomReservation = this.reservationDataSource.getActiveReservation(reservationId);
        } else {
            throw new ReservationException(NO_RESERVATION_ID, RoomReservationWfrBase.class,
                    this.messagesService.getAdminService());
        }

        if (roomReservation != null) {
            WorkRequestService.setFlagToSendEmailsInSingleJob();
            this.cancelReservationService.cancelReservation(roomReservation, comments);
            try {
                this.calendarServiceWrapper.cancelSingleRoomCalendarEvent(roomReservation, comments,
                        this.updateOtherReservationsInConferenceCall(roomReservation, comments, false),
                        cancelMeeting == null || cancelMeeting);
            } catch (final CalendarException exception) {
                this.calendarServiceWrapper.handleCalendarException(exception, roomReservation,
                        CalendarServiceWrapper.CALENDAR_CANCEL_ERROR, CalendarServiceWrapper.class);
            }
            WorkRequestService.startJobToSendEmailsInSingleJob();
        }
        ReservationsContextHelper.ensureResultMessageIsSet();
    }

    /**
     * Cancel multiple reservations.
     *
     * @param reservations the reservations to cancel
     * @param message the message
     * @param cancelMeetings true to cancel the meetings, false to only remove the location -- null
     *            is interpreted as true
     * @return list of reservation ids that could not be cancelled.
     */
    public List<Integer> cancelMultipleRoomReservations(final DataSetList reservations,
                                                        final String message, final Boolean cancelMeetings) {

        WorkRequestService.setFlagToSendEmailsInSingleJob();
        final List<Integer> failures = new ArrayList<Integer>();
        for (final DataRecord record : reservations.getRecords()) {
            // get the active reservation and all allocations
            final int reservationId = record.getInt(RESERVE_RES_ID);
            final RoomReservation roomReservation = this.reservationDataSource.get(reservationId);

            if (roomReservation == null
                    || Constants.STATUS_REJECTED.equals(roomReservation.getStatus())) {
                failures.add(record.getInt(RESERVE_RES_ID));
            } else if (!Constants.STATUS_CANCELLED.equals(roomReservation.getStatus())) {
                try {
                    this.reservationDataSource.canBeCancelledByCurrentUser(roomReservation);
                } catch (final ReservationException exception) {
                    // this one can't be cancelled, so skip and report
                    failures.add(roomReservation.getReserveId());
                    continue;
                }
                this.cancelReservationService.cancelReservation(roomReservation, message);
                try {
                    this.calendarServiceWrapper.cancelSingleRoomCalendarEvent(roomReservation,
                            message, this.updateOtherReservationsInConferenceCall(roomReservation,
                                    message, false),
                            cancelMeetings == null || cancelMeetings);
                } catch (final CalendarException exception) {
                    this.calendarServiceWrapper.handleCalendarException(exception, roomReservation,
                            CalendarServiceWrapper.CALENDAR_CANCEL_ERROR, CalendarServiceWrapper.class);
                }
            }
        }
        WorkRequestService.startJobToSendEmailsInSingleJob();

        ReservationsContextHelper.ensureResultMessageIsSet();
        return failures;
    }

    /**
     * Cancel recurring room reservation.
     *
     * @param reservationId reservation id of the first occurrence in the series to cancel
     * @param comments the comments
     * @param cancelMeeting true to cancel the corresponding meeting, false to remove the location
     *            from it -- null is interpreted as true
     * @return the list of id that failed
     */
    public List<Integer> cancelRecurringRoomReservation(final Integer reservationId,
                                                        final String comments, final Boolean cancelMeeting) {
        final List<Integer> failures = new ArrayList<Integer>();
        if (reservationId != null && reservationId > 0) {
            final RoomReservation reservation = this.reservationDataSource.get(reservationId);

            WorkRequestService.setFlagToSendEmailsInSingleJob();
            final List<List<IReservation>> cancelResult =
                    this.cancelReservationService.cancelRecurringReservation(reservation, comments);
            final List<IReservation> cancelledReservations = cancelResult.get(0);
            final List<IReservation> failedReservations = cancelResult.get(1);

            cancelRecurringSingleRoomCalendarEvent(reservation, comments,
                    cancelMeeting == null || cancelMeeting,
                    extractRoomReservations(cancelledReservations), failedReservations);
            WorkRequestService.startJobToSendEmailsInSingleJob();

            for (final IReservation failure : failedReservations) {
                failures.add(failure.getReserveId());
            }
        } else {
            throw new ReservationException(NO_RESERVATION_ID, RoomReservationWfrBase.class,
                    this.messagesService.getAdminService());
        }
        ReservationsContextHelper.ensureResultMessageIsSet();
        return failures;
    }

    /**
     * Calculate total cost of the reservation.
     *
     * The total cost per reservation is calculated and multiplied by the number of occurrences.
     *
     * @param reservation the reservation.
     * @param roomAllocation the room allocation.
     * @param resources the equipment and services to be reserved
     * @param caterings the catering resources
     * @param numberOfOccurrences the number of occurrences
     * @return total cost of all occurrences
     */
    public Double calculateTotalCost(final DataRecord reservation, final DataRecord roomAllocation,
                                     final DataSetList resources, final DataSetList caterings,
                                     final int numberOfOccurrences) {

        final RoomReservation roomReservation =
                this.reservationDataSource.convertRecordToObject(reservation);
        // make sure the date of time values are set to 1899
        roomReservation.setStartTime(TimePeriod.clearDate(roomReservation.getStartTime()));
        roomReservation.setEndTime(TimePeriod.clearDate(roomReservation.getEndTime()));

        // add the room allocation to the reservation
        roomReservation
                .addRoomAllocation(this.roomAllocationDataSource.convertRecordToObject(roomAllocation));

        // add the resources and catering
        this.reservationDataSource.addResourceList(roomReservation, caterings);
        this.reservationDataSource.addResourceList(roomReservation, resources);

        return this.reservationDataSource.calculateCosts(roomReservation) * numberOfOccurrences;
    }

    /**
     * Calculate total cost of the reservation.
     *
     * The total cost per reservation is calculated and multiplied by the number of occurrences.
     *
     * LBNL / BER
     **/

    public int calculateAvailableCapacityStr(String pmBlId, String pmFlId, String pmRmId, String pmConfigId, String pmRmArrangeId, String pmDateStart, String pmDateEnd, String pmTimeStart, String pmTimeEnd, String recurXml) throws ParseException {
        SimpleDateFormat displayFormat = new SimpleDateFormat("HH:mm:ss");
        SimpleDateFormat parseFormat = new SimpleDateFormat("hh:mm a");

        Date startTime = parseFormat.parse(pmTimeStart);
        String fPmTimeStart = displayFormat.format(startTime);

        Date endTime = parseFormat.parse(pmTimeEnd);
        String fPmTimeEnd = displayFormat.format(endTime);

        String[] occurrenceList = parseRecurrenceXml(recurXml, pmDateStart, pmDateEnd);
        boolean recurrence = occurrenceList.length>0;

        DataSource dsc = DataSourceFactory.createDataSource();
        dsc.addTable("rm_arrange");
        dsc.addField("max_capacity");

        String capacityRest = "bl_id = '" + pmBlId + "' AND fl_id = '" + pmFlId +"' AND rm_id = '" + pmRmId +"' AND config_id = '" + pmConfigId +"' AND rm_arrange_type_id = '" + pmRmArrangeId  + "'";

        DataRecord mCapacityRec = dsc.getRecord(capacityRest);
        int maxCapacity = mCapacityRec.getInt("rm_arrange.max_capacity");
        // System.out.println(maxCapacity);
        int sumAttendees = 0;

        // System.out.println(pmDateStart);
        // System.out.println("' AND  date_start <= to_date('" + pmDateEnd + "','MM/DD/YYYY')");
        // System.out.println(fPmTimeEnd);
        // System.out.println(" AND time_end > to_date('12-30-1899 "+ fPmTimeStart +"','MM-DD-YYYY HH24:MI:SS')");

        String overlap = "bl_id = '" + pmBlId + "' AND fl_id = '" + pmFlId +"' AND rm_id = '" + pmRmId +
                "' AND config_id = '" + pmConfigId +"' AND rm_arrange_type_id = '" + pmRmArrangeId +
                "' AND  date_start <= to_date('" + pmDateStart + "','MM/DD/YYYY')"+ " AND date_end >= to_date('"+ pmDateStart +
                "','MM/DD/YYYY')" + " AND  time_start < to_date('12-30-1899 "+ fPmTimeEnd +"','MM-DD-YYYY HH24:MI:SS')" +
                " AND time_end > to_date('12-30-1899 "+ fPmTimeStart +"','MM-DD-YYYY HH24:MI:SS')" +  " AND status='Confirmed'";

        DataSource ds = DataSourceFactory.createDataSource();
        ds.addTable("reserve_rm");
        ds.addField("bl_id");
        ds.addField("fl_id");
        ds.addField("rm_id");
        ds.addField("config_id");
        ds.addField("rm_arrange_type_id");
        ds.addField("date_start");
        ds.addField("date_end");
        ds.addField("time_start");
        ds.addField("time_end");
        ds.addField("status");
        ds.addField("attendees_in_room");

        // System.out.println("ds created");
        // System.out.println("restrictions created");

        ds.addRestriction(Restrictions.sql(overlap));
        List<DataRecord> concurrentRecs = ds.getRecords();

        // System.out.println("restriction works");
        // System.out.println(ds.getRestrictions());
        // System.out.println(concurrentRecs);

        String overlap2;

        List<DataRecord> concurrentRes2;

        int tempSum;

        for (DataRecord d:concurrentRecs) {
            tempSum = 0;
            tempSum = d.getInt("reserve_rm.attendees_in_room");

            System.out.println(d.toString());
            System.out.println(d.getInt("reserve_rm.attendees_in_room"));

            for(DataRecord y:concurrentRecs){
                if(d.equals(y));
                else{
                    if(y.getDate("reserve_rm.time_start").compareTo(d.getDate("reserve_rm.time_end")) >= 0 || y.getDate("reserve_rm.time_end").compareTo(d.getDate("reserve_rm.time_start")) <= 0);
                    else tempSum+=y.getInt("reserve_rm.attendees_in_room");
                }

            }

            if(sumAttendees<tempSum)sumAttendees=tempSum;
        }

        // System.out.println(sumAttendees);

        int minSpace = Integer.MAX_VALUE;
        if(recurrence){
            for(String reccurDate : occurrenceList){
                DataSource recdsc = DataSourceFactory.createDataSource();
                recdsc.addTable("rm_arrange");
                recdsc.addField("max_capacity");

                String recCapacityRest = "bl_id = '" + pmBlId + "' AND fl_id = '" + pmFlId +"' AND rm_id = '" + pmRmId +"' AND config_id = '" + pmConfigId +"' AND rm_arrange_type_id = '" + pmRmArrangeId  + "'";

                DataRecord recmCapacityRec = recdsc.getRecord(recCapacityRest);
                int recMaxCapacity = recmCapacityRec.getInt("rm_arrange.max_capacity");
                int recSumAttendees = 0;

                String recoverlap = "bl_id = '" + pmBlId + "' AND fl_id = '" + pmFlId +"' AND rm_id = '" + pmRmId +
                        "' AND config_id = '" + pmConfigId +"' AND rm_arrange_type_id = '" + pmRmArrangeId +
                        "' AND  date_start <= to_date('" + reccurDate + "','MM/DD/YYYY')"+ " AND date_end >= to_date('"+ reccurDate +
                        "','MM/DD/YYYY')" + " AND  time_start < to_date('12-30-1899 "+ fPmTimeEnd +"','MM-DD-YYYY HH24:MI:SS')" +
                        " AND time_end > to_date('12-30-1899 "+ fPmTimeStart +"','MM-DD-YYYY HH24:MI:SS')" +  " AND status='Confirmed'";

                DataSource recds = DataSourceFactory.createDataSource();
                recds.addTable("reserve_rm");
                recds.addField("bl_id");
                recds.addField("fl_id");
                recds.addField("rm_id");
                recds.addField("config_id");
                recds.addField("rm_arrange_type_id");
                recds.addField("date_start");
                recds.addField("date_end");
                recds.addField("time_start");
                recds.addField("time_end");
                recds.addField("status");
                recds.addField("attendees_in_room");

                recds.addRestriction(Restrictions.sql(recoverlap));
                List<DataRecord> recConcurrentRecs = recds.getRecords();

                for (DataRecord d:recConcurrentRecs) {
                    recSumAttendees+=d.getInt("reserve_rm.attendees_in_room");
                }
                if (recMaxCapacity-recSumAttendees<minSpace) minSpace = recMaxCapacity-recSumAttendees;
            }
        }
        int availCap = recurrence ? minSpace : maxCapacity-sumAttendees;
        return availCap>0 ? availCap : 0;
    }

    /**
     * Calculate total cost of the reservation.
     *
     * The total cost per reservation is calculated and multiplied by the number of occurrences.
     *
     * LBNL / BER
     **/
    public int calculateAvailableCapacity(final DataRecord reservation, final DataRecord roomAllocation) {

        final RoomReservation roomReservation =
                this.reservationDataSource.convertRecordToObject(reservation);
        // add the room allocation to the reservation
        roomReservation.addRoomAllocation(this.roomAllocationDataSource.convertRecordToObject(roomAllocation));
        RoomAllocation roomA = roomReservation.getRoomAllocations().get(0);

        DataSource dsc = DataSourceFactory.createDataSource();
        dsc.addTable("rm_arrange");

        dsc.addField("max_capacity");

        // System.out.println("look here right now"); System.out.println(reservation.toString());


        String capacityRest = "bl_id = '" + roomA.getBlId() + "' AND fl_id = '" + roomA.getFlId() +"' AND rm_id = '" + roomA.getRmId() +"' AND config_id = '" + roomA.getConfigId() +"' AND rm_arrange_type_id = '" + roomA.getRoomArrangement().getArrangeTypeId()  + "'";

        DataRecord mCapacityRec = dsc.getRecord(capacityRest);

        int maxCapacity = mCapacityRec.getInt("rm_arrange.max_capacity");

        // System.out.println(maxCapacity);

        int sumAttendees = 0;

        // System.out.println(roomA.getStartDate().toString());
        // System.out.println("' AND  date_start >= to_date('" + roomA.getEndDate().toString() + "','MM-DD-YYYY')");
        // System.out.println(roomA.getEndTime().toString());
        // System.out.println(" AND time_end <= to_date('12-30-1899 "+ roomA.getStartTime().toString() +"','MM-DD-YYYY HH24:MI:SS')");

        String overlap = "bl_id = '" + roomA.getBlId() + "' AND fl_id = '" + roomA.getFlId() +"' AND rm_id = '" + roomA.getRmId() +
                "' AND config_id = '" + roomA.getConfigId() +"' AND rm_arrange_type_id = '" + roomA.getRoomArrangement().getArrangeTypeId() +
                "' AND  date_start <= to_date('" + roomA.getEndDate().toString() + "','YYYY-MM-DD')"+ " AND date_end >= to_date('"+ roomA.getStartDate().toString() +
                "','YYYY-MM-DD')" + " AND  time_start < to_date('12-30-1899 "+ roomA.getEndTime().toString() +"','MM-DD-YYYY HH24:MI:SS')" +
                " AND time_end > to_date('12-30-1899 "+ roomA.getStartTime().toString() +"','MM-DD-YYYY HH24:MI:SS')" +  " AND status='Confirmed'";

        DataSource ds = DataSourceFactory.createDataSource();
        ds.addTable("reserve_rm");
        ds.addField("bl_id");
        ds.addField("fl_id");
        ds.addField("rm_id");
        ds.addField("config_id");
        ds.addField("rm_arrange_type_id");
        ds.addField("date_start");
        ds.addField("date_end");
        ds.addField("time_start");
        ds.addField("time_end");
        ds.addField("status");
        ds.addField("attendees_in_room");

        // System.out.println("ds created");
        // System.out.println("restrictions created");

        ds.addRestriction(Restrictions.sql(overlap));

        List<DataRecord> concurrentRecs = ds.getRecords();

        // System.out.println("restriction works");
        // System.out.println(ds.getRestrictions());
        // System.out.println(concurrentRecs);

        String overlap2;

        List<DataRecord> concurrentRes2;

        int tempSum;

        for (DataRecord d:concurrentRecs) {
            tempSum = 0;
            tempSum = d.getInt("reserve_rm.attendees_in_room");

            System.out.println(d.toString());
            System.out.println(d.getInt("reserve_rm.attendees_in_room"));

            for(DataRecord y:concurrentRecs){
                if(d.equals(y));
                else{
                    if(y.getDate("reserve_rm.time_start").compareTo(d.getDate("reserve_rm.time_end")) >= 0 || y.getDate("reserve_rm.time_end").compareTo(d.getDate("reserve_rm.time_start")) <= 0);
                    else tempSum+=y.getInt("reserve_rm.attendees_in_room");
                }

            }

            if(sumAttendees<tempSum)sumAttendees=tempSum;
        }

        // System.out.println(sumAttendees);

        //LBL added rec logic

        String recurXml = reservation.getString("reserve.recurring_rule");
        String isoDateStart = reservation.getDate("reserve.date_start").toString();
        String isoDateEnd = reservation.getDate("reserve.date_end").toString();

        String pmDateStart = "";
        String pmDateEnd = "";

        pmDateStart += isoDateStart.substring(isoDateStart.indexOf("-") + 1, isoDateStart.indexOf("-") + 3) + "/"
                + isoDateStart.substring(isoDateStart.indexOf("-", isoDateStart.indexOf("-") + 1) + 1) + "/"
                + isoDateStart.substring(0, isoDateStart.indexOf("-"));

        pmDateEnd += isoDateEnd.substring(isoDateEnd.indexOf("-") + 1, isoDateEnd.indexOf("-") + 3) + "/"
                + isoDateEnd.substring(isoDateEnd.indexOf("-", isoDateEnd.indexOf("-") + 1) + 1) + "/"
                + isoDateEnd.substring(0, isoDateEnd.indexOf("-"));

        boolean recurrence = false;
        String[] occurrenceList = parseRecurrenceXml(recurXml, pmDateStart, pmDateEnd);
        recurrence = occurrenceList.length>0;

        String pmBlId = roomA.getBlId();
        String pmFlId = roomA.getFlId();
        String pmRmId = roomA.getRmId();
        String pmConfigId = roomA.getConfigId();
        String pmRmArrangeId = roomA.getRoomArrangement().getArrangeTypeId();
        String fPmTimeStart = roomA.getStartTime().toString();
        String fPmTimeEnd = roomA.getEndTime().toString();

        int minSpace = Integer.MAX_VALUE;
        if(recurrence){
            for(String reccurDate : occurrenceList){
                DataSource recdsc = DataSourceFactory.createDataSource();
                recdsc.addTable("rm_arrange");
                recdsc.addField("max_capacity");

                String recCapacityRest = "bl_id = '" + pmBlId + "' AND fl_id = '" + pmFlId +"' AND rm_id = '" + pmRmId +"' AND config_id = '" + pmConfigId +"' AND rm_arrange_type_id = '" + pmRmArrangeId  + "'";

                DataRecord recmCapacityRec = recdsc.getRecord(recCapacityRest);
                int recMaxCapacity = recmCapacityRec.getInt("rm_arrange.max_capacity");
                int recSumAttendees = 0;

                String recoverlap = "bl_id = '" + pmBlId + "' AND fl_id = '" + pmFlId +"' AND rm_id = '" + pmRmId +
                        "' AND config_id = '" + pmConfigId +"' AND rm_arrange_type_id = '" + pmRmArrangeId +
                        "' AND  date_start <= to_date('" + reccurDate + "','MM/DD/YYYY')"+ " AND date_end >= to_date('"+ reccurDate +
                        "','MM/DD/YYYY')" + " AND  time_start < to_date('12-30-1899 "+ fPmTimeEnd +"','MM-DD-YYYY HH24:MI:SS')" +
                        " AND time_end > to_date('12-30-1899 "+ fPmTimeStart +"','MM-DD-YYYY HH24:MI:SS')" +  " AND status='Confirmed'";

                DataSource recds = DataSourceFactory.createDataSource();
                recds.addTable("reserve_rm");
                recds.addField("bl_id");
                recds.addField("fl_id");
                recds.addField("rm_id");
                recds.addField("config_id");
                recds.addField("rm_arrange_type_id");
                recds.addField("date_start");
                recds.addField("date_end");
                recds.addField("time_start");
                recds.addField("time_end");
                recds.addField("status");
                recds.addField("attendees_in_room");

                recds.addRestriction(Restrictions.sql(recoverlap));
                List<DataRecord> recConcurrentRecs = recds.getRecords();

                for (DataRecord d:recConcurrentRecs) {
                    recSumAttendees+=d.getInt("reserve_rm.attendees_in_room");
                }
                if (recMaxCapacity-recSumAttendees<minSpace) minSpace = recMaxCapacity-recSumAttendees;
            }
        }
        int availCap = recurrence ? minSpace : maxCapacity-sumAttendees;
        return availCap;

        //LBL added rec logic
//        if((maxCapacity-sumAttendees)>0)return maxCapacity-sumAttendees;
//        else return 0;
    }
    /**
     * Calculate total cost of the reservation.
     *
     * The total cost per reservation is calculated and multiplied by the number of occurrences.
     *
     * LBNL / BER
     **/
    public int calculateAvailableCapacityEdit(final DataRecord reservation, final DataRecord roomAllocation, String res_id) {
        //  int res_id = Integer.parseInt(reservation.getString("reserve.res_id"));
        int parent_id = reservation.getInt("reserve.res_parent");

        String recurXml = reservation.getString("reserve.recurring_rule");
        String isoDateStart = reservation.getDate("reserve.date_start").toString();
        String isoDateEnd = reservation.getDate("reserve.date_end").toString();

        String pmDateStart = "";
        String pmDateEnd = "";

        pmDateStart += isoDateStart.substring(isoDateStart.indexOf("-") + 1, isoDateStart.indexOf("-") + 3) + "/"
                + isoDateStart.substring(isoDateStart.indexOf("-", isoDateStart.indexOf("-") + 1) + 1) + "/"
                + isoDateStart.substring(0, isoDateStart.indexOf("-"));

        pmDateEnd += isoDateEnd.substring(isoDateEnd.indexOf("-") + 1, isoDateEnd.indexOf("-") + 3) + "/"
                + isoDateEnd.substring(isoDateEnd.indexOf("-", isoDateEnd.indexOf("-") + 1) + 1) + "/"
                + isoDateEnd.substring(0, isoDateEnd.indexOf("-"));

        boolean recurrence = false;
        String[] initOccurrenceList;
        String[] occurrenceList = new String[0];
        initOccurrenceList = parseRecurrenceXml(recurXml, pmDateStart, pmDateEnd);
        if (initOccurrenceList.length>0) {
            occurrenceList = new String[initOccurrenceList.length - (Integer.parseInt(res_id) - parent_id)];
            for (int i = 0; i < occurrenceList.length; i++) occurrenceList[i] = initOccurrenceList[i];
            recurrence = occurrenceList.length>0;
        }

        final RoomReservation roomReservation =
                this.reservationDataSource.convertRecordToObject(reservation);
//        // add the room allocation to the reservation
        roomReservation.addRoomAllocation(this.roomAllocationDataSource.convertRecordToObject(roomAllocation));
        RoomAllocation roomA = roomReservation.getRoomAllocations().get(0);
        String pmBlId = roomA.getBlId();
        String pmFlId = roomA.getFlId();
        String pmRmId = roomA.getRmId();
        String pmConfigId = roomA.getConfigId();
        String pmRmArrangeId = roomA.getRoomArrangement().getArrangeTypeId();
        String fPmTimeStart = roomA.getStartTime().toString();
        String fPmTimeEnd = roomA.getEndTime().toString();

        DataSource dsc = DataSourceFactory.createDataSource();

        dsc.addTable("rm_arrange");

        dsc.addField("max_capacity");

        System.out.println("look here right now");

        System.out.println(reservation.toString());

        String capacityRest = "bl_id = '" + roomA.getBlId() + "' AND fl_id = '" + roomA.getFlId() +"' AND rm_id = '" + roomA.getRmId() +"' AND config_id = '" + roomA.getConfigId() +"' AND rm_arrange_type_id = '" + roomA.getRoomArrangement().getArrangeTypeId()  + "'";

        DataRecord mCapacityRec = dsc.getRecord(capacityRest);

        int maxCapacity = mCapacityRec.getInt("rm_arrange.max_capacity");

        System.out.println(maxCapacity);

        int sumAttendees = 0;

        System.out.println(roomA.getStartDate().toString());
        System.out.println("' AND  date_start >= to_date('" + roomA.getEndDate().toString() + "','MM-DD-YYYY')");

        System.out.println(roomA.getEndTime().toString());
        System.out.println(" AND time_end <= to_date('12-30-1899 "+ roomA.getStartTime().toString() +"','MM-DD-YYYY HH24:MI:SS')");

        String overlap = "bl_id = '" + roomA.getBlId() + "' AND fl_id = '" + roomA.getFlId() +"' AND rm_id = '" + roomA.getRmId() +
                "' AND config_id = '" + roomA.getConfigId() +"' AND rm_arrange_type_id = '" + roomA.getRoomArrangement().getArrangeTypeId() +
                "' AND  date_start <= to_date('" + roomA.getEndDate().toString() + "','YYYY-MM-DD')"+ " AND date_end >= to_date('"+ roomA.getStartDate().toString() +
                "','YYYY-MM-DD')" + " AND  time_start < to_date('12-30-1899 "+ roomA.getEndTime().toString() +"','MM-DD-YYYY HH24:MI:SS')" +
                " AND time_end > to_date('12-30-1899 "+ roomA.getStartTime().toString() +"','MM-DD-YYYY HH24:MI:SS')" +  " AND status='Confirmed'"
                + "AND res_id != '" + res_id +"'";

        DataSource ds = DataSourceFactory.createDataSource();
        ds.addTable("reserve_rm");
        ds.addField("bl_id");
        ds.addField("fl_id");
        ds.addField("rm_id");
        ds.addField("config_id");
        ds.addField("rm_arrange_type_id");
        ds.addField("date_start");
        ds.addField("date_end");
        ds.addField("time_start");
        ds.addField("time_end");
        ds.addField("status");
        ds.addField("attendees_in_room");

        System.out.println("ds created");

        System.out.println("restrictions created");

        ds.addRestriction(Restrictions.sql(overlap));

        List<DataRecord> concurrentRecs = ds.getRecords();

        System.out.println("restriction works");

        System.out.println(ds.getRestrictions());

        System.out.println(concurrentRecs);

        String overlap2;

        List<DataRecord> concurrentRes2;

        int tempSum;

        for (DataRecord d:concurrentRecs) {
            tempSum = 0;
            tempSum = d.getInt("reserve_rm.attendees_in_room");

            System.out.println(d.toString());
            System.out.println(d.getInt("reserve_rm.attendees_in_room"));

            for(DataRecord y:concurrentRecs){
                if(d.equals(y));
                else{
                    if(y.getDate("reserve_rm.time_start").compareTo(d.getDate("reserve_rm.time_end")) >= 0 || y.getDate("reserve_rm.time_end").compareTo(d.getDate("reserve_rm.time_start")) <= 0);
                    else tempSum+=y.getInt("reserve_rm.attendees_in_room");
                }

            }

            if(sumAttendees<tempSum)sumAttendees=tempSum;
        }

        System.out.println(sumAttendees);
        int minSpace = Integer.MAX_VALUE;
        if(recurrence){
            int resCounter = Integer.parseInt(res_id);
            for(String reccurDate : occurrenceList){
                if(resCounter > parent_id + initOccurrenceList.length) break;
                DataSource recdsc = DataSourceFactory.createDataSource();
                recdsc.addTable("rm_arrange");
                recdsc.addField("max_capacity");

                String recCapacityRest = "bl_id = '" + pmBlId + "' AND fl_id = '" + pmFlId +"' AND rm_id = '" + pmRmId +"' AND config_id = '" + pmConfigId +"' AND rm_arrange_type_id = '" + pmRmArrangeId  + "'";

                DataRecord recmCapacityRec = recdsc.getRecord(recCapacityRest);
                int recMaxCapacity = recmCapacityRec.getInt("rm_arrange.max_capacity");
                int recSumAttendees = 0;

                String recoverlap = "bl_id = '" + pmBlId + "' AND fl_id = '" + pmFlId +"' AND rm_id = '" + pmRmId +
                        "' AND config_id = '" + pmConfigId +"' AND rm_arrange_type_id = '" + pmRmArrangeId +
                        "' AND  date_start <= to_date('" + reccurDate + "','MM/DD/YYYY')"+ " AND date_end >= to_date('"+ reccurDate +
                        "','MM/DD/YYYY')" + " AND  time_start < to_date('12-30-1899 "+ fPmTimeEnd +"','MM-DD-YYYY HH24:MI:SS')" +
                        " AND time_end > to_date('12-30-1899 "+ fPmTimeStart +"','MM-DD-YYYY HH24:MI:SS')" +  " AND status='Confirmed'"
                        + "AND res_id != '" + resCounter++ +"'";

                DataSource recds = DataSourceFactory.createDataSource();
                recds.addTable("reserve_rm");
                recds.addField("bl_id");
                recds.addField("fl_id");
                recds.addField("rm_id");
                recds.addField("config_id");
                recds.addField("rm_arrange_type_id");
                recds.addField("date_start");
                recds.addField("date_end");
                recds.addField("time_start");
                recds.addField("time_end");
                recds.addField("status");
                recds.addField("attendees_in_room");

                recds.addRestriction(Restrictions.sql(recoverlap));
                List<DataRecord> recConcurrentRecs = recds.getRecords();

                for (DataRecord d:recConcurrentRecs) {
                    recSumAttendees+=d.getInt("reserve_rm.attendees_in_room");
                }
                if (recMaxCapacity-recSumAttendees<minSpace) minSpace = recMaxCapacity-recSumAttendees;
            }
        }
        int availCap = recurrence ? minSpace : maxCapacity-sumAttendees;
        return availCap>0 ? availCap : 0;
//        if((maxCapacity-sumAttendees)>0)return maxCapacity-sumAttendees;
//        else return 0;
    }

    /**
     * Create a copy of the room reservation.
     *
     * @param reservationId the reservation id
     * @param reservationName the reservation name
     * @param startDate the start date
     * @return new reservation id of the copy
     */
    public Integer copyRoomReservation(final int reservationId, final String reservationName,
                                       final Date startDate) {
        final RoomReservation sourceReservation =
                this.reservationDataSource.getActiveReservation(reservationId);

        if (sourceReservation == null) {
            // @translatable
            throw new ReservationException("Room reservation has been cancelled or rejected.",
                    RoomReservationService.class, this.messagesService.getAdminService());
        }

        if (sourceReservation.getRoomAllocations().isEmpty()) {
            // @translatable
            throw new ReservationException("Room reservation has no room allocated.",
                    RoomReservationService.class, this.messagesService.getAdminService());
        }

        final RoomReservation newReservation = new RoomReservation();
        sourceReservation.copyTo(newReservation, true);

        newReservation.setStartDate(startDate);
        newReservation.setEndDate(startDate);
        newReservation.setReservationName(reservationName);
        // when copying the new reservation is always a single regular reservation
        newReservation.setReservationType("regular");
        newReservation.setRecurringRule("");
        newReservation.setParentId(null);
        newReservation.setUniqueId(null);

        String timeZoneId = null;

        // copy room allocations
        for (final RoomAllocation roomAllocation : sourceReservation.getRoomAllocations()) {
            // get the room arrangement
            final RoomArrangement roomArrangement = roomAllocation.getRoomArrangement();
            final RoomAllocation roomAllocationCopy =
                    new RoomAllocation(roomArrangement, newReservation);
            roomAllocationCopy.setAttendeesInRoom(roomAllocation.getAttendeesInRoom());
            newReservation.addRoomAllocation(roomAllocationCopy);

            // determine the time zone of the building and set the local time zone
            final String buildingId = roomAllocation.getBlId();
            timeZoneId = TimeZoneConverter.getTimeZoneIdForBuilding(buildingId);
        }

        // copy resource allocations
        ReservationServiceHelper.copyResourceAllocations(sourceReservation, newReservation);

        // availability will be checked
        WorkRequestService.setFlagToSendEmailsInSingleJob();
        this.reservationService.saveReservation(newReservation);
        WorkRequestService.startJobToSendEmailsInSingleJob();

        // set the time zone to match the building
        newReservation.setTimeZone(timeZoneId);
        this.calendarServiceWrapper.saveCopiedCalendarEvent(newReservation);

        // Set the unique id of the new reservation.
        final RoomReservation storedReservation =
                this.reservationDataSource.get(newReservation.getReserveId());
        storedReservation.setUniqueId(newReservation.getUniqueId());
        this.reservationDataSource.update(storedReservation);

        ReservationsContextHelper.ensureResultMessageIsSet();
        return newReservation.getReserveId();
    }

    /**
     * Get the attendees response status for the reservation with given id.
     *
     * @param reservationId the reservation id
     * @return response status array
     */
    public List<JSONObject> getAttendeesResponseStatus(final int reservationId) {
        final List<JSONObject> results = new ArrayList<JSONObject>();

        final RoomReservation reservation = this.reservationDataSource.get(reservationId);

        try {
            final List<AttendeeResponseStatus> responses =
                    this.attendeeService.getAttendeesResponseStatus(reservation);
            for (final AttendeeResponseStatus response : responses) {
                final JSONObject result = new JSONObject();
                result.put("name", response.getName());
                result.put("email", response.getEmail());
                result.put("response", response.getResponseStatus().toString());
                results.add(result);
            }
        } catch (final CalendarException exception) {
            this.logger.warn("Error retrieving attendee response status", exception);
            ReservationsContextHelper.appendResultError(exception.getPattern());
        }
        return results;
    }

    /**
     * Get the current local date/time for the given buildings.
     *
     * @param buildingIds list of building IDs
     * @return JSON mapping of each building id to the current date/time in that building
     */
    public JSONObject getCurrentLocalDateTime(final List<String> buildingIds) {
        return TimeZoneConverter.getCurrentLocalDateTime(buildingIds);
    }

    /**
     * Set the Attendee Service.
     *
     * @param attendeeService the attendee service
     */
    public void setAttendeeService(final IAttendeeService attendeeService) {
        this.attendeeService = attendeeService;
    }

    public String[] parseRecurrenceXml(String recurXml, String pmDateStart, String pmDateEnd){
        // Parse reucrrence XML
        String type, value1, value2, value3, total;
        type="none";
        value1=value2=value3=total="";
        if(!recurXml.equals("")) {
            type = recurXml.substring(recurXml.indexOf("=", recurXml.indexOf("type")) + 2, recurXml.indexOf("value1") - 2);
            value1 = recurXml.substring(recurXml.indexOf("=", recurXml.indexOf("value1")) + 2, recurXml.indexOf("value2") - 2);
            value2 = recurXml.substring(recurXml.indexOf("=", recurXml.indexOf("value2")) + 2, recurXml.indexOf("value3") - 2);
            // String value3 = type.equals("none") ? "" : recurXml.substring(recurXml.indexOf("=",recurXml.indexOf("value3"))+2,recurXml.indexOf("isSeasonal")-2);
            if (type.equals("none")) {
                value3 = "";
            } else {
                if (recurXml.contains("isSeasonal"))
                    value3 = recurXml.substring(recurXml.indexOf("=", recurXml.indexOf("value3")) + 2, recurXml.indexOf("isSeasonal") - 2);
                else if (recurXml.contains("value4"))
                    value3 = recurXml.substring(recurXml.indexOf("=", recurXml.indexOf("value3")) + 2, recurXml.indexOf("value4") - 2);
                else value3 = "";
            }
            total = recurXml.substring(recurXml.indexOf("=", recurXml.indexOf("total")) + 2, recurXml.indexOf("/") - 1);
            // System.out.println("TYPE   : "+type+"\nVALUE1 : "+value1+"\nVALUE2 : "+value2+"\nVALUE3 : "+value3+"\nTOTAL  : "+total);
        }
        // Used by all recurrences
        int occurrences = total.equals("") ? -1 : Integer.parseInt(total);

        // Used by type day
        int everyXdays = 0;

        // Used by type weeks
        int everyXweeks = 0;
        int[] weekdays = new int[7];

        // Used by type month
        String day;
        int everyXmonths = 0;

        // Used by type month/year
        int dayOfMonth = 0;

        // Used by type year
        String month;
        int everyXyears = 0;

        String[] chronValsStart = pmDateStart.split("/");
        int mm = Integer.parseInt(chronValsStart[0])-1;
        int dd = Integer.parseInt(chronValsStart[1]);
        int yyyy = Integer.parseInt(chronValsStart[2]);
        Calendar startDate = Calendar.getInstance();
        startDate.setFirstDayOfWeek(Calendar.MONDAY);
        startDate.set(yyyy,mm,dd);
        Calendar nextDate = Calendar.getInstance();
        nextDate.setFirstDayOfWeek(Calendar.MONDAY);
        nextDate.set(yyyy,mm,dd);
        Calendar endDate = Calendar.getInstance();

        int occurrencesCount = 0;
        boolean definedOccurrences = true;

        String[] occurrenceList = new String[0];
        if (occurrences>0) {
            occurrenceList = new String[occurrences];
        } else if (occurrences==-1) {
            String[] chronValsEnd = pmDateEnd.split("/");
            int mmE = Integer.parseInt(chronValsEnd[0])-1;
            int ddE = Integer.parseInt(chronValsEnd[1]);
            int yyyyE = Integer.parseInt(chronValsEnd[2]);
            endDate.setFirstDayOfWeek(Calendar.MONDAY);
            endDate.set(yyyyE,mmE,ddE);
            definedOccurrences = false;
        } else {
            occurrenceList = new String[0];
        }

        String mmStr = null;
        String ddStr = null;
        String yyyyStr = null;

        System.out.println("____________________Date End "+pmDateEnd+"____________________");
        // System.out.println("____________________TYPE "+type+"____________________");
        boolean recurrence = !type.equals("none");
        switch (type){
            case "none":
                occurrences = 0;
                break;
            case "day":
                everyXdays = value1.equals("") ? 0 : Integer.parseInt(value1);

                if(!definedOccurrences){
                    while(startDate.before(endDate)||startDate.equals(endDate)){
                        occurrencesCount++;
                        startDate.add(Calendar.DATE,everyXdays);
                    }
                    occurrenceList = new String[occurrencesCount];
                    occurrences = occurrenceList.length;
                }

                // System.out.println("====================DATES TO CHECK=======================");
                for(int i=0;i<occurrences;i++){
                    mmStr = Integer.toString(nextDate.get(Calendar.MONTH)+1);
                    ddStr = Integer.toString(nextDate.get(Calendar.DATE));
                    yyyyStr = Integer.toString(nextDate.get(Calendar.YEAR));
                    String dateStr = mmStr + "/" + ddStr + "/" + yyyyStr;
                    occurrenceList[i] = dateStr;
                    // System.out.print(occurrenceList[i]); System.out.print(", ");
                    nextDate.add(Calendar.DATE,everyXdays);
                }
                // System.out.println("\n===========================================================");
                break;
            case "week":
                for(int i=0;i<value1.length();i+=2) {
                    if (value1.charAt(i) == '1') weekdays[i/2] = 1;
                    else weekdays[i/2] = 0;
                }
                int initDayofWeek = nextDate.get(Calendar.DAY_OF_WEEK);
                int dowIndex=0;

                if(initDayofWeek==Calendar.MONDAY) dowIndex = 0;
                else if(initDayofWeek==Calendar.TUESDAY) dowIndex = 1;
                else if(initDayofWeek==Calendar.WEDNESDAY) dowIndex = 2;
                else if(initDayofWeek==Calendar.THURSDAY) dowIndex = 3;
                else if(initDayofWeek==Calendar.FRIDAY) dowIndex = 4;
                else if(initDayofWeek==Calendar.SATURDAY) dowIndex = 5;
                else if(initDayofWeek==Calendar.SUNDAY) dowIndex = 6;
                int initDowIndex = dowIndex;

                everyXweeks = value2.equals("") ? 0 : Integer.parseInt(value2);

                if(!definedOccurrences) {
                    while (startDate.before(endDate) || startDate.equals(endDate)) {
                        if(dowIndex==6 && (startDate.before(endDate) || startDate.equals(endDate))) {
                            if(weekdays[dowIndex]==1) occurrencesCount++;
                            startDate.add(Calendar.DATE,1);
                            dowIndex=0;
                        }
                        while (dowIndex<6 && (startDate.before(endDate) || startDate.equals(endDate))) {
                            if(weekdays[dowIndex]==1) occurrencesCount++;
                            startDate.add(Calendar.DATE,1);
                            dowIndex++;
                        }
                        dowIndex=6;
                        startDate.add(Calendar.WEEK_OF_YEAR, everyXweeks-1);
                    }
                    occurrenceList = new String[occurrencesCount];
                    occurrences = occurrenceList.length;
                }

                dowIndex = initDowIndex;
                // System.out.println("====================DATES TO CHECK=======================");
                for(int i=0;i<occurrences;){
                    if (dowIndex==6){
                        if(weekdays[dowIndex]==1) {
                            mmStr = Integer.toString(nextDate.get(Calendar.MONTH) + 1);
                            ddStr = Integer.toString(nextDate.get(Calendar.DATE));
                            yyyyStr = Integer.toString(nextDate.get(Calendar.YEAR));
                            String dateStr = mmStr + "/" + ddStr + "/" + yyyyStr;
                            occurrenceList[i] = dateStr;
                            // System.out.print(occurrenceList[i]); System.out.print(", ");
                            i++;
                        }
                        nextDate.add(Calendar.DATE,1);
                        dowIndex=0;
                    }
                    for(int w=dowIndex;w<6 && i<occurrences;w++){
                        if (weekdays[w]==1) {
                            mmStr = Integer.toString(nextDate.get(Calendar.MONTH) + 1);
                            ddStr = Integer.toString(nextDate.get(Calendar.DATE));
                            yyyyStr = Integer.toString(nextDate.get(Calendar.YEAR));
                            String dateStr = mmStr + "/" + ddStr + "/" + yyyyStr;
                            occurrenceList[i] = dateStr;
                            // System.out.print(occurrenceList[i]); System.out.print(", ");
                            i++;
                        }
                        nextDate.add(Calendar.DATE,1);
                    }
                    dowIndex=6;
                    nextDate.add(Calendar.WEEK_OF_YEAR,everyXweeks-1);
                }
                // System.out.println("\n===========================================================");
                break;
            case "month":
                everyXmonths = value3.equals("") ? 0 : Integer.parseInt(value3);

                // System.out.println("====================DATES TO CHECK=======================");
                if(value2.isEmpty()){
                    // on day of month
                    dayOfMonth = value1.equals("") ? 0 : Integer.parseInt(value1);

                    if(!definedOccurrences) {
                        while (startDate.before(endDate) || startDate.equals(endDate)) {
                            occurrencesCount++;
                            startDate.add(Calendar.MONTH, everyXmonths);
                        }
                        occurrenceList = new String[occurrencesCount];
                        occurrences = occurrenceList.length;
                    }

                    for(int i=0;i<occurrences;i++) {
                        mmStr = Integer.toString(nextDate.get(Calendar.MONTH) + 1);
                        ddStr = Integer.toString(nextDate.get(Calendar.DATE));
                        yyyyStr = Integer.toString(nextDate.get(Calendar.YEAR));
                        String dateStr = mmStr + "/" + ddStr + "/" + yyyyStr;
                        occurrenceList[i] = dateStr;
                        // System.out.print(occurrenceList[i]); System.out.print(", ");
                        nextDate.add(Calendar.MONTH,everyXmonths);
                    }
                } else {
                    // on ordinal value of month
                    int ordinal = 0;
                    int counter = 0;
                    int occCounter = 0;

                    day = value2;
                    if(value1.equals("1st")) ordinal = 1;
                    else if(value1.equals("2nd")) ordinal = 2;
                    else if(value1.equals("3rd")) ordinal = 3;
                    else if(value1.equals("4th")) ordinal = 4;
                    else if(value1.equals("last")) ordinal = -1;

                    int dayOfWeek=0;
                    if(day.equals("mon")) dayOfWeek=Calendar.MONDAY;
                    else if(day.equals("tue")) dayOfWeek=Calendar.TUESDAY;
                    else if(day.equals("wed")) dayOfWeek=Calendar.WEDNESDAY;
                    else if(day.equals("thu")) dayOfWeek=Calendar.THURSDAY;
                    else if(day.equals("fri")) dayOfWeek=Calendar.FRIDAY;
                    else if(day.equals("sat")) dayOfWeek=Calendar.SATURDAY;
                    else if(day.equals("sun")) dayOfWeek=Calendar.SUNDAY;

                    switch (day){
                        case "mon":
                        case "tue":
                        case "wed":
                        case "thu":
                        case "fri":
                        case "sat":
                        case "sun":
                            int currMonth = nextDate.get(Calendar.MONTH);
                            int currYear = nextDate.get(Calendar.YEAR);
                            Calendar nextMonth = Calendar.getInstance();
                            nextMonth.setFirstDayOfWeek(Calendar.MONDAY);
                            nextMonth.set(currYear,currMonth,1);

                            if(!definedOccurrences){
                                while (startDate.before(endDate) || startDate.equals(endDate)) {
                                    if (ordinal>0) {
                                        while (counter < ordinal) {
                                            if (startDate.get(Calendar.DAY_OF_WEEK) == dayOfWeek) counter++;
                                            if (counter == ordinal && !startDate.after(endDate)) occurrencesCount++;
                                            if (counter<ordinal) startDate.add(Calendar.DATE,1);
                                        }
                                        int prevMonth = startDate.get(Calendar.MONTH);
                                        while(startDate.get(Calendar.MONTH)==prevMonth) startDate.add(Calendar.DATE, 1);
                                        if (occurrencesCount>0) startDate.add(Calendar.MONTH,everyXmonths-1);
                                        counter=0;
                                    } else if (ordinal==-1) {
                                        int prevMonth = startDate.get(Calendar.MONTH);
                                        while (startDate.get(Calendar.MONTH)==prevMonth) {
                                            startDate.add(Calendar.DATE, 1);
                                        }
                                        // if (startDate.before(endDate) || startDate.equals(endDate))
                                        occurrencesCount++;
                                        if (occurrencesCount>0) startDate.add(Calendar.MONTH,everyXmonths-1);
                                    }
                                }
                                counter=0;
                                occurrenceList = new String[occurrencesCount];
                            }

                            while(occCounter<occurrenceList.length){
                                if (ordinal>0) {
                                    while (counter < ordinal) {
                                        if (nextMonth.get(Calendar.DAY_OF_WEEK) == dayOfWeek) counter++;
                                        if (counter == ordinal && !nextMonth.before(nextDate)) {
                                            mmStr = Integer.toString(nextMonth.get(Calendar.MONTH) + 1);
                                            ddStr = Integer.toString(nextMonth.get(Calendar.DATE));
                                            yyyyStr = Integer.toString(nextMonth.get(Calendar.YEAR));
                                            String dateStr = mmStr + "/" + ddStr + "/" + yyyyStr;
                                            occurrenceList[occCounter] = dateStr;
                                            occCounter++;
                                        }
                                        if(counter<ordinal) nextMonth.add(Calendar.DATE, 1);
                                    }
                                    int prevMonth = nextMonth.get(Calendar.MONTH);
                                    while(nextMonth.get(Calendar.MONTH)==prevMonth) nextMonth.add(Calendar.DATE, 1);
                                    if (occCounter>0) nextMonth.add(Calendar.MONTH,everyXmonths-1);
                                    counter=0;
                                } else if (ordinal==-1){
                                    int prevMonth = nextMonth.get(Calendar.MONTH);
                                    while (nextMonth.get(Calendar.MONTH)==prevMonth) {
                                        if (nextMonth.get(Calendar.DAY_OF_WEEK) == dayOfWeek){
                                            mmStr = Integer.toString(nextMonth.get(Calendar.MONTH) + 1);
                                            ddStr = Integer.toString(nextMonth.get(Calendar.DATE));
                                            yyyyStr = Integer.toString(nextMonth.get(Calendar.YEAR));
                                        }
                                        nextMonth.add(Calendar.DATE, 1);
                                    }
                                    String dateStr = mmStr + "/" + ddStr + "/" + yyyyStr;
                                    occurrenceList[occCounter] = dateStr;
                                    occCounter++;

                                    if (occCounter>0) nextMonth.add(Calendar.MONTH,everyXmonths-1);
                                }
                            }
                            break;
                        case "day":
                            currMonth = nextDate.get(Calendar.MONTH);
                            currYear = nextDate.get(Calendar.YEAR);
                            nextMonth = Calendar.getInstance();
                            nextMonth.setFirstDayOfWeek(Calendar.MONDAY);
                            nextMonth.set(currYear,currMonth,1);

                            if(!definedOccurrences){
                                while (startDate.before(endDate) || startDate.equals(endDate)) {
                                    if (ordinal>0) {
                                        while (counter < ordinal) {
                                            counter++;
                                            if (counter == ordinal && !startDate.after(endDate)) occurrencesCount++;
                                            if (counter<ordinal) startDate.add(Calendar.DATE,1);
                                        }
                                        int prevMonth = startDate.get(Calendar.MONTH);
                                        while(startDate.get(Calendar.MONTH)==prevMonth) startDate.add(Calendar.DATE, 1);
                                        if (occurrencesCount>0) startDate.add(Calendar.MONTH,everyXmonths-1);
                                        counter=0;
                                    } else if (ordinal==-1) {
                                        int prevMonth = startDate.get(Calendar.MONTH);
                                        while (startDate.get(Calendar.MONTH)==prevMonth) {
                                            startDate.add(Calendar.DATE, 1);
                                        }
                                        // if (startDate.before(endDate) || startDate.equals(endDate))
                                        occurrencesCount++;
                                        if (occurrencesCount>0) startDate.add(Calendar.MONTH,everyXmonths-1);
                                    }
                                }
                                counter=0;
                                occurrenceList = new String[occurrencesCount];
                            }

                            while(occCounter<occurrenceList.length){
                                if (ordinal>0) {
                                    while (counter < ordinal) {
                                        counter++;
                                        if (counter == ordinal && !nextMonth.before(nextDate)) {
                                            mmStr = Integer.toString(nextMonth.get(Calendar.MONTH) + 1);
                                            ddStr = Integer.toString(nextMonth.get(Calendar.DATE));
                                            yyyyStr = Integer.toString(nextMonth.get(Calendar.YEAR));
                                            String dateStr = mmStr + "/" + ddStr + "/" + yyyyStr;
                                            occurrenceList[occCounter] = dateStr;
                                            occCounter++;
                                        }
                                        if(counter<ordinal) nextMonth.add(Calendar.DATE, 1);
                                    }
                                    int prevMonth = nextMonth.get(Calendar.MONTH);
                                    while(nextMonth.get(Calendar.MONTH)==prevMonth) nextMonth.add(Calendar.DATE, 1);
                                    if (occCounter>0) nextMonth.add(Calendar.MONTH,everyXmonths-1);
                                    counter=0;
                                } else if (ordinal==-1){
                                    int prevMonth = nextMonth.get(Calendar.MONTH);
                                    while (nextMonth.get(Calendar.MONTH)==prevMonth) {
                                        mmStr = Integer.toString(nextMonth.get(Calendar.MONTH) + 1);
                                        ddStr = Integer.toString(nextMonth.get(Calendar.DATE));
                                        yyyyStr = Integer.toString(nextMonth.get(Calendar.YEAR));
                                        nextMonth.add(Calendar.DATE, 1);
                                    }
                                    String dateStr = mmStr + "/" + ddStr + "/" + yyyyStr;
                                    occurrenceList[occCounter] = dateStr;
                                    occCounter++;

                                    if (occCounter>0) nextMonth.add(Calendar.MONTH,everyXmonths-1);
                                }
                            }
                            break;
                        case "weekday":
                            currMonth = nextDate.get(Calendar.MONTH);
                            currYear = nextDate.get(Calendar.YEAR);
                            nextMonth = Calendar.getInstance();
                            nextMonth.setFirstDayOfWeek(Calendar.MONDAY);
                            nextMonth.set(currYear,currMonth,1);

                            if(!definedOccurrences){
                                while (startDate.before(endDate) || startDate.equals(endDate)) {
                                    if (ordinal>0) {
                                        while (counter < ordinal) {
                                            if (nextMonth.get(Calendar.DAY_OF_WEEK) > Calendar.SUNDAY && nextMonth.get(Calendar.DAY_OF_WEEK) < Calendar.SATURDAY) counter++;
                                            if (counter == ordinal && !startDate.after(endDate)) occurrencesCount++;
                                            if (counter<ordinal) startDate.add(Calendar.DATE,1);
                                        }
                                        int prevMonth = startDate.get(Calendar.MONTH);
                                        while(startDate.get(Calendar.MONTH)==prevMonth) startDate.add(Calendar.DATE, 1);
                                        if (occurrencesCount>0) startDate.add(Calendar.MONTH,everyXmonths-1);
                                        counter=0;
                                    } else if (ordinal==-1) {
                                        int prevMonth = startDate.get(Calendar.MONTH);
                                        while (startDate.get(Calendar.MONTH)==prevMonth) {
                                            startDate.add(Calendar.DATE, 1);
                                        }
                                        // if (startDate.before(endDate) || startDate.equals(endDate))
                                        occurrencesCount++;
                                        if (occurrencesCount>0) startDate.add(Calendar.MONTH,everyXmonths-1);
                                    }
                                }
                                counter=0;
                                occurrenceList = new String[occurrencesCount];
                            }

                            while(occCounter<occurrenceList.length){
                                if (ordinal>0) {
                                    while (counter < ordinal) {
                                        if (nextMonth.get(Calendar.DAY_OF_WEEK) > Calendar.SUNDAY && nextMonth.get(Calendar.DAY_OF_WEEK) < Calendar.SATURDAY) counter++;
                                        if (counter == ordinal && !nextMonth.before(nextDate)) {
                                            mmStr = Integer.toString(nextMonth.get(Calendar.MONTH) + 1);
                                            ddStr = Integer.toString(nextMonth.get(Calendar.DATE));
                                            yyyyStr = Integer.toString(nextMonth.get(Calendar.YEAR));
                                            String dateStr = mmStr + "/" + ddStr + "/" + yyyyStr;
                                            occurrenceList[occCounter] = dateStr;
                                            occCounter++;
                                        }
                                        if(counter<ordinal) nextMonth.add(Calendar.DATE, 1);
                                    }
                                    int prevMonth = nextMonth.get(Calendar.MONTH);
                                    while(nextMonth.get(Calendar.MONTH)==prevMonth) nextMonth.add(Calendar.DATE, 1);
                                    if (occCounter>0) nextMonth.add(Calendar.MONTH,everyXmonths-1);
                                    counter=0;
                                } else if (ordinal==-1){
                                    int prevMonth = nextMonth.get(Calendar.MONTH);
                                    while (nextMonth.get(Calendar.MONTH)==prevMonth) {
                                        if (nextMonth.get(Calendar.DAY_OF_WEEK) > Calendar.SUNDAY && nextMonth.get(Calendar.DAY_OF_WEEK) < Calendar.SATURDAY){
                                            mmStr = Integer.toString(nextMonth.get(Calendar.MONTH) + 1);
                                            ddStr = Integer.toString(nextMonth.get(Calendar.DATE));
                                            yyyyStr = Integer.toString(nextMonth.get(Calendar.YEAR));
                                        }
                                        nextMonth.add(Calendar.DATE, 1);
                                    }
                                    String dateStr = mmStr + "/" + ddStr + "/" + yyyyStr;
                                    occurrenceList[occCounter] = dateStr;
                                    occCounter++;

                                    if (occCounter>0) nextMonth.add(Calendar.MONTH,everyXmonths-1);
                                }
                            }
                            break;
                        case "weekendday":
                            currMonth = nextDate.get(Calendar.MONTH);
                            currYear = nextDate.get(Calendar.YEAR);
                            nextMonth = Calendar.getInstance();
                            nextMonth.setFirstDayOfWeek(Calendar.MONDAY);
                            nextMonth.set(currYear,currMonth,1);

                            if(!definedOccurrences){
                                while (startDate.before(endDate) || startDate.equals(endDate)) {
                                    if (ordinal>0) {
                                        while (counter < ordinal) {
                                            if (startDate.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY || startDate.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) counter++;
                                            if (counter == ordinal && !startDate.after(endDate)) occurrencesCount++;
                                            if (counter<ordinal) startDate.add(Calendar.DATE,1);
                                        }
                                        int prevMonth = startDate.get(Calendar.MONTH);
                                        while(startDate.get(Calendar.MONTH)==prevMonth) startDate.add(Calendar.DATE, 1);
                                        if (occurrencesCount>0) startDate.add(Calendar.MONTH,everyXmonths-1);
                                        counter=0;
                                    } else if (ordinal==-1) {
                                        int prevMonth = startDate.get(Calendar.MONTH);
                                        while (startDate.get(Calendar.MONTH)==prevMonth) {
                                            startDate.add(Calendar.DATE, 1);
                                        }
                                        // if (startDate.before(endDate) || startDate.equals(endDate))
                                        occurrencesCount++;
                                        if (occurrencesCount>0) startDate.add(Calendar.MONTH,everyXmonths-1);
                                    }
                                }
                                counter=0;
                                occurrenceList = new String[occurrencesCount];
                            }

                            while(occCounter<occurrenceList.length){
                                if (ordinal>0) {
                                    while (counter < ordinal) {
                                        if (nextMonth.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY || nextMonth.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) counter++;
                                        if (counter == ordinal && !nextMonth.before(nextDate)) {
                                            mmStr = Integer.toString(nextMonth.get(Calendar.MONTH) + 1);
                                            ddStr = Integer.toString(nextMonth.get(Calendar.DATE));
                                            yyyyStr = Integer.toString(nextMonth.get(Calendar.YEAR));
                                            String dateStr = mmStr + "/" + ddStr + "/" + yyyyStr;
                                            occurrenceList[occCounter] = dateStr;
                                            occCounter++;
                                        }
                                        if(counter<ordinal) nextMonth.add(Calendar.DATE, 1);
                                    }
                                    int prevMonth = nextMonth.get(Calendar.MONTH);
                                    while(nextMonth.get(Calendar.MONTH)==prevMonth) nextMonth.add(Calendar.DATE, 1);
                                    if (occCounter>0) nextMonth.add(Calendar.MONTH,everyXmonths-1);
                                    counter=0;
                                } else if (ordinal==-1){
                                    int prevMonth = nextMonth.get(Calendar.MONTH);
                                    while (nextMonth.get(Calendar.MONTH)==prevMonth) {
                                        if (nextMonth.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY || nextMonth.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY){
                                            mmStr = Integer.toString(nextMonth.get(Calendar.MONTH) + 1);
                                            ddStr = Integer.toString(nextMonth.get(Calendar.DATE));
                                            yyyyStr = Integer.toString(nextMonth.get(Calendar.YEAR));
                                        }
                                        nextMonth.add(Calendar.DATE, 1);
                                    }
                                    String dateStr = mmStr + "/" + ddStr + "/" + yyyyStr;
                                    occurrenceList[occCounter] = dateStr;
                                    occCounter++;

                                    if (occCounter>0) nextMonth.add(Calendar.MONTH,everyXmonths-1);
                                }
                            }
                            break;
                    }

                }
                // System.out.println("\n===========================================================");
                break;
            case "year":
                dayOfMonth = value2.equals("") ? 0 : Integer.parseInt(value1);
                month = value2;
                everyXyears = value3.equals("") ? 0 : Integer.parseInt(value3);

                if(!definedOccurrences) {
                    while (startDate.before(endDate) || startDate.equals(endDate)) {
                        occurrencesCount++;
                        startDate.add(Calendar.YEAR, everyXyears);
                    }
                    occurrenceList = new String[occurrencesCount];
                    occurrences = occurrenceList.length;
                }

                // System.out.println("====================DATES TO CHECK=======================");
                for(int i=0;i<occurrences;i++) {
                    mmStr = Integer.toString(nextDate.get(Calendar.MONTH) + 1);
                    ddStr = Integer.toString(nextDate.get(Calendar.DATE));
                    yyyyStr = Integer.toString(nextDate.get(Calendar.YEAR));
                    String dateStr = mmStr + "/" + ddStr + "/" + yyyyStr;
                    occurrenceList[i] = dateStr;
                    // System.out.print(occurrenceList[i]); System.out.print(", ");
                    nextDate.add(Calendar.YEAR,everyXyears);
                }
                // System.out.println("\n===========================================================");
                break;
            default:
                break;
        }

        for(int i=0;i<occurrenceList.length;i++){
            if(occurrenceList[i].length()<10){
                String[] chronVals = occurrenceList[i].split("/");
                if(chronVals[0].length()==1) chronVals[0]="0"+chronVals[0];
                if(chronVals[1].length()==1) chronVals[1]="0"+chronVals[1];
                occurrenceList[i] = chronVals[0]+"/"+chronVals[1]+"/"+chronVals[2];
            }
        }


        System.out.println("================ Date List After XML Parse ================");
        System.out.println("==  type: "+type+"   v1: "+value1+"   v2: "+value2+"   v3: "+value3+"   tot: "+(total.equals("") ? occurrences : total)+"  ==");
        System.out.println("===========================================================");
        for(int i=0;i<occurrenceList.length;i++) {
            if (i%5==0&&i!=0) System.out.println();
            System.out.print(occurrenceList[i]);
            if (i!= occurrenceList.length-1) System.out.print(", ");
        }
        System.out.println("\n===========================================================");

        return occurrenceList;
    }
}

