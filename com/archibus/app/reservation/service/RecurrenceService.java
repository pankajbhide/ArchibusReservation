package com.archibus.app.reservation.service;

import java.util.*;

import com.archibus.app.common.recurring.RecurringScheduleService;
import com.archibus.app.reservation.dao.datasource.ConferenceCallReservationDataSource;
import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.recurrence.*;
import com.archibus.app.reservation.util.AdminServiceContainer;
import com.archibus.datasource.DataSource;
import com.archibus.datasource.data.DataRecord;

/**
 * The Class RecurrenceService.
 */
public class RecurrenceService extends AdminServiceContainer {

    /**
     * Constant: Default Maximum Number of Occurrences for recurring reservations if not defined in
     * the activity parameter.
     */
    private static final int DEFAULT_MAX_OCCURRENCES = 500;

    /** The Constant RESERVE_DATE_START. */
    private static final String RESERVE_DATE_START = "reserve.date_start";

    /** The Constant RESERVE_DATE_END. */
    private static final String RESERVE_DATE_END = "reserve.date_end";

    /** The Constant RESERVE_RES_PARENT. */
    private static final String RESERVE_RES_PARENT = "reserve.res_parent";

    /** The Constant RESERVE_COMMENTS. */
    private static final String RESERVE_COMMENTS = "reserve.comments";

    /** Number of days in a week. */
    private static final int DAYS_IN_WEEK = 7;

    /** The room reservation data source. */
    private ConferenceCallReservationDataSource reservationDataSource;

    /**
     * Get the start and end date of the recurrent reservation and the number of occurrences.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param recurringRule the recurring rule
     * @param parentId the parent id if this is for an existing recurrence series
     * @param conferenceId the conference id if this for a recurring conference call
     * @return the first and last date of the recurrent reservation and the number of occurrences
     */
    public DataRecord getFirstAndLastDate(final Date startDate, final Date endDate,
            final String recurringRule, final Integer parentId, final Integer conferenceId) {

        Date firstDate = null;
        Date lastDate = null;
        Integer totalOccurrences = null;

        final RecurringScheduleService recurringScheduleService = newRecurringScheduleService();
        if (parentId == null || parentId == 0) {
            final List<Date> dateList =
                    cropDateList(recurringScheduleService.getDatesList(startDate, endDate,
                        recurringRule));

            if (dateList.isEmpty()) {
                // @translatable
                throw new ReservationException(
                    "The recurrence pattern does not yield any occurrences for your current selection. Please review the pattern.",
                    RecurrenceService.class, this.getAdminService());
            }
            firstDate = dateList.get(0);
            totalOccurrences = dateList.size();
            lastDate = dateList.get(totalOccurrences - 1);
        } else {
            firstDate = startDate;
            List<RoomReservation> existingReservations = null;
            if (conferenceId == null || conferenceId == 0) {
                // clear all sorts before adding the new one
                this.reservationDataSource.getSortFields().clear();
                this.reservationDataSource.addSort(this.reservationDataSource.getMainTableName(),
                    Constants.DATE_START_FIELD_NAME, DataSource.SORT_DESC);
                existingReservations =
                        this.reservationDataSource.getByParentId(parentId, startDate, null, false);
            } else {
                // custom code for getting the correct dates in case of conference call
                existingReservations =
                        this.reservationDataSource.getConferenceSeries(conferenceId, startDate,
                            DataSource.SORT_DESC, false);
            }
            if (existingReservations.isEmpty()) {
                // @translatable
                throw new ReservationException("No reservations found with parent id {0}.",
                    RecurrenceService.class, this.getAdminService(), parentId);
            }
            totalOccurrences = existingReservations.size();
            lastDate = existingReservations.get(0).getStartDate();

            recurringScheduleService
                .setRecurringSchedulePattern(firstDate, lastDate, recurringRule);
        }

        final DataRecord record = this.reservationDataSource.createNewRecord();
        record.setValue(RESERVE_DATE_START, firstDate);
        record.setValue(RESERVE_DATE_END, lastDate);
        record.setValue(RESERVE_RES_PARENT, totalOccurrences);

        // Create a modified recurring rule for getting a description with an accurate number of
        // occurrences, even when editing part of the recurrence series.
        final String modifiedRecurringRule =
                RecurringScheduleService.getRecurrenceXMLPattern(
                    recurringScheduleService.getRecurringType(),
                    recurringScheduleService.getInterval(), totalOccurrences,
                    recurringScheduleService.getDaysOfWeek(),
                    recurringScheduleService.getDayOfMonth(),
                    recurringScheduleService.getWeekOfMonth(),
                    recurringScheduleService.getMonthOfYear());

        record.setValue(RESERVE_COMMENTS,
            recurringScheduleService.getRecurringPatternDescription(modifiedRecurringRule));

        return record;
    }

    /**
     * For a single occurrence in a recurring reservation, get the minimum and maximum date allowed
     * while not skipping over another occurrence.
     *
     * @param reservationId id of the reservation to get the min and max date for
     * @param parentId parent reservation id
     * @param conferenceId conference reservation id (only for conference reservations)
     * @return data record containing the min and max date as reserve.date_start and
     *         reserve.date_end
     */
    public DataRecord getMinAndMaxDate(final Integer reservationId, final Integer parentId,
            final Integer conferenceId) {
        Date minDate = null;
        Date maxDate = null;

        List<RoomReservation> reservations = null;
        int index = 0;

        if (conferenceId == null || conferenceId == 0) {
            reservations = this.reservationDataSource.getByParentId(parentId, null, null, true);
            // find the previous reservation
            for (final RoomReservation reservation : reservations) {
                if (reservation.getReserveId().equals(reservationId)) {
                    break;
                }
                minDate = reservation.getStartDate();
                ++index;
            }
        } else {
            reservations =
                    this.reservationDataSource.getConferenceSeries(conferenceId, null,
                        DataSource.SORT_ASC, true);
            // find the previous reservation
            for (final RoomReservation reservation : reservations) {
                if (reservation.getConferenceId().equals(conferenceId)) {
                    break;
                }
                minDate = reservation.getStartDate();
                ++index;
            }
        }
        // move past the current reservation
        ++index;
        // the next reservation determines the max date
        if (index < reservations.size()) {
            maxDate = reservations.get(index).getStartDate();
            final Calendar calendar = Calendar.getInstance();
            calendar.setTime(maxDate);
            calendar.add(Calendar.DATE, -1);
            maxDate = calendar.getTime();
        }
        if (minDate != null) {
            final Calendar calendar = Calendar.getInstance();
            calendar.setTime(minDate);
            calendar.add(Calendar.DATE, 1);
            minDate = calendar.getTime();
        }

        final DataRecord record = this.reservationDataSource.createNewRecord();
        record.setValue(RESERVE_DATE_START, minDate);

        record.setValue(RESERVE_DATE_END, maxDate);

        return record;
    }

    /**
     * Gets the date list.
     *
     * @param reservation the reservation
     * @param recurrenceRule the recurrence rule
     * @return the date list
     */
    public static List<Date> getDateList(final DataRecord reservation, final String recurrenceRule) {
        return getDateList(reservation.getDate(RESERVE_DATE_START),
            reservation.getDate(RESERVE_DATE_END), recurrenceRule);
    }

    /**
     * Gets the date list.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param recurrenceRule the recurrence rule
     * @return the date list
     */
    public static List<Date> getDateList(final Date startDate, final Date endDate,
            final String recurrenceRule) {
        final RecurringScheduleService recurringScheduleService = newRecurringScheduleService();

        final List<Date> dateList =
                recurringScheduleService.getDatesList(startDate, endDate, recurrenceRule);
        return cropDateList(dateList);
    }

    /**
     * Crop the date list for the maximum number of occurrences in reservations.
     *
     * @param dateList the list of dates
     * @return the cropped list of dates
     */
    private static List<Date> cropDateList(final List<Date> dateList) {
        final int maxOccurrences = getMaxOccurrences();
        List<Date> result;
        if (dateList.size() > maxOccurrences) {
            result = dateList.subList(0, maxOccurrences);
        } else {
            result = dateList;
        }
        return result;
    }

    /**
     * Initialize the common recurring schedule service without year-based limits.
     *
     * @return recurring schedule service instance
     */
    public static RecurringScheduleService newRecurringScheduleService() {
        final RecurringScheduleService recurringScheduleService = new RecurringScheduleService();
        recurringScheduleService.setSchedulingLimits(-1, -1, -1, -1);
        return recurringScheduleService;
    }

    /**
     * Get the maximum number of occurrences for a recurring reservation.
     *
     * @return the maximum number of occurrences.
     */
    public static int getMaxOccurrences() {
        return com.archibus.service.Configuration.getActivityParameterInt(
            "AbWorkplaceReservations", "MaxRecurrencesToCreate", DEFAULT_MAX_OCCURRENCES);
    }

    /**
     * Get the list of original dates for the new recurrence pattern. Check whether the recurrence
     * start date is correctly set. Specifically if the first occurrence is not on it's original
     * date, we might need to change the recurrence start date so the calculated occurrence indexes
     * match the occurrence indexes of the remaining occurrences that are still on their original
     * date.
     *
     * @param recurrence the recurrence pattern
     * @param originalDates maps occurrence indexes to start dates for the reservation occurrences
     *            still on their original date
     * @return list of dates in the recurrence pattern
     */
    public static List<Date> getFixedDateList(final Recurrence recurrence,
            final Map<Integer, Date> originalDates) {
        List<Date> calculatedDates =
                RecurrenceService.getDateList(recurrence.getStartDate(), null,
                    recurrence.toString());

        /*
         * We only need to check one occurrence that's still on its original date, so loop onto the
         * first element. If there aren't any unmodified occurrences, skip the check because we'll
         * have to move every occurrence anyway.
         */
        for (final Integer occurrenceIndex : originalDates.keySet()) {

            final Date occurrenceDate = TimePeriod.clearTime(originalDates.get(occurrenceIndex));
            Date calculatedDate = TimePeriod.clearTime(calculatedDates.get(occurrenceIndex - 1));
            while (calculatedDate.after(occurrenceDate)) {
                RecurrenceService.moveBackwardsOneInterval(recurrence);

                calculatedDates =
                        RecurrenceService.getDateList(recurrence.getStartDate(), null,
                            recurrence.toString());
                calculatedDate = TimePeriod.clearTime(calculatedDates.get(occurrenceIndex - 1));
            }

            if (calculatedDate.before(occurrenceDate)) {
                // we need to start at a later date, find out which one
                int offset = 0;
                do {
                    ++offset;
                } while (calculatedDates.size() > occurrenceIndex + offset
                        && calculatedDates.get(occurrenceIndex + offset).before(occurrenceDate));

                // immediately calculate the dates again, we're not repeating
                recurrence.setStartDate(calculatedDates.get(offset));
                calculatedDates =
                        RecurrenceService.getDateList(recurrence.getStartDate(), null,
                            recurrence.toString());
            }
            // checking one original date is enough
            break;
        }

        return calculatedDates;
    }

    /**
     * Move the recurrence pattern start date backwards by one interval of the recurrence pattern.
     *
     * @param recurrence the recurrence pattern to move
     */
    private static void moveBackwardsOneInterval(final Recurrence recurrence) {
        // we need to start at an earlier date, move back one unit
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(recurrence.getStartDate());
        int interval = 1;
        if (recurrence instanceof AbstractIntervalPattern) {
            interval = ((AbstractIntervalPattern) recurrence).getInterval();
        }
        int field = Calendar.DATE;
        if (recurrence instanceof WeeklyPattern) {
            interval *= DAYS_IN_WEEK;
        } else if (recurrence instanceof MonthlyPattern) {
            field = Calendar.MONTH;
        } else if (recurrence instanceof YearlyPattern) {
            field = Calendar.YEAR;
        }
        calendar.add(field, -interval);
        recurrence.setStartDate(calendar.getTime());
    }

    /**
     * Sets the room reservation data source .
     *
     * @param reservationDataSource the new room reservation data source
     */
    public void setReservationDataSource(
            final ConferenceCallReservationDataSource reservationDataSource) {
        this.reservationDataSource = reservationDataSource;
    }

}
