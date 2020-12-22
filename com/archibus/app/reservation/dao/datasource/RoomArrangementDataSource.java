package com.archibus.app.reservation.dao.datasource;

import java.sql.Time;
import java.util.*;

import com.archibus.app.reservation.dao.IRoomArrangementDataSource;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.util.*;
import com.archibus.context.*;
import com.archibus.datasource.DataSource;
import com.archibus.datasource.data.DataRecord;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.utility.*;

/**
 * The Class RoomArrangementDataSource.
 *
 * @author Bart Vanderschoot
 */
public class RoomArrangementDataSource extends AbstractReservableDataSource<RoomArrangement>
        implements IRoomArrangementDataSource {

    /**
     * Instantiates a new room arrangement data source.
     */
    public RoomArrangementDataSource() {
        this("roomArrangement", Constants.RM_ARRANGE_TABLE);
    }

    /**
     * Instantiates a new room arrangement data source.
     *
     * @param beanName the bean name
     * @param tableName the table name
     */
    protected RoomArrangementDataSource(final String beanName, final String tableName) {
        super(beanName, tableName);
        // join with room table
        this.addTable(Constants.ROOM_TABLE, DataSource.ROLE_STANDARD);
        // add the name of the room
        this.addField(Constants.ROOM_TABLE, Constants.NAME_FIELD_NAME);
        // add room photo field
        this.addField(Constants.ROOM_TABLE, "rm_photo");
        this.addField(Constants.ROOM_TABLE, "name");
        this.addField(Constants.ROOM_TABLE, "cap_em");
        // add the reservable property of the room
        this.addField(Constants.ROOM_TABLE, Constants.RESERVABLE_FIELD_NAME);

        // join with bl table
        this.addTable(Constants.BUILDING_TABLE, DataSource.ROLE_STANDARD);
        this.addField(Constants.BUILDING_TABLE, Constants.BL_ID_FIELD_NAME);
        this.addField(Constants.BUILDING_TABLE, Constants.NAME_FIELD_NAME);
        
        //join with rm_config table
        this.addTable(Constants.ROOM_CONFIG_TABLE, DataSource.ROLE_STANDARD);
        this.addField(Constants.ROOM_CONFIG_TABLE, Constants.EXCLUDED_CONFIG_FIELD);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<DataRecord> findAvailableRoomRecords(final RoomReservation reservation,
            final Integer numberAttendees, final boolean externalAllowed,
            final List<String> fixedResourceStandards, final boolean allDayEvent,
            final boolean allowConflicts) throws ReservationException {

        final List<RoomAllocation> rooms = reservation.getRoomAllocations();
        if (rooms.isEmpty()) {
            // @translatable
            throw new ReservationException("No rooms in reservation",
                RoomArrangementDataSource.class);
        }
        final RoomAllocation roomAllocation = rooms.get(0);
        this.convertReservationToLocalTime(reservation, roomAllocation, allDayEvent);

        /*
         * Determine the current local date. Note we need the local time zone for that, as set by
         * the above call to convertReservationToLocalTime.
         */
        final Date localCurrentDate = TimePeriod
            .clearTime(LocalDateTimeUtil.currentLocalDateForTimeZone(reservation.getTimeZone()));

        List<DataRecord> results = null;
        if (reservation.getStartDate().before(localCurrentDate)) {
            // Don't return any results if the reservation starts in the past.
            results = new ArrayList<DataRecord>(0);
        } else {
            results = findAvailableRoomRecordsInLocalTime(reservation, numberAttendees,
                externalAllowed, fixedResourceStandards, allDayEvent, allowConflicts);
        }

        // clear the time zone to indicate the reservation is now in local time
        reservation.setTimeZone(null);

        return results;
    }

    /**
     * Find available rooms for the specified reservation, which is already in the local time zone
     * of the building.
     *
     * @param reservation the reservation in the time zone of the building
     * @param numberAttendees number of attendees
     * @param externalAllowed whether to return only rooms that allow external visitors
     * @param fixedResourceStandards fixed resource standards
     * @param allDayEvent true to look for rooms available for all day events
     * @param allowConflicts whether to allow conflicts with other reservations
     * @return the list of results
     */
    private List<DataRecord> findAvailableRoomRecordsInLocalTime(final RoomReservation reservation,
            final Integer numberAttendees, final boolean externalAllowed,
            final List<String> fixedResourceStandards, final boolean allDayEvent,
            final boolean allowConflicts) {
        // since the remote service is a singleton Spring bean and data sources prototypes, we
        // create copy
        final DataSource dataSource = this.createCopy();
        if (DataSourceUtils.isVpaEnabled()) {
            dataSource.setApplyVpaRestrictions(true);
            dataSource.addRestriction(Restrictions.sql("${sql.getVpaRestrictionForTable('bl')}"));
        } else {
            dataSource.setApplyVpaRestrictions(false);
        }

        final RoomAllocation roomAllocation = reservation.getRoomAllocations().get(0);

        if (StringUtil.notNullOrEmpty(roomAllocation.getBlId())) {
            dataSource.addRestriction(Restrictions.eq(this.tableName, Constants.BL_ID_FIELD_NAME,
                roomAllocation.getBlId()));
        }
        if (StringUtil.notNullOrEmpty(roomAllocation.getFlId())) {
            dataSource.addRestriction(Restrictions.in(this.tableName, Constants.FL_ID_FIELD_NAME,
                roomAllocation.getFlId()));
        }
        if (StringUtil.notNullOrEmpty(roomAllocation.getRmId())) {
            dataSource.addRestriction(Restrictions.eq(this.tableName, Constants.RM_ID_FIELD_NAME,
                roomAllocation.getRmId()));
        }

        if (StringUtil.notNullOrEmpty(roomAllocation.getConfigId())) {
            dataSource.addRestriction(Restrictions.eq(this.tableName,
                Constants.CONFIG_ID_FIELD_NAME, roomAllocation.getConfigId()));
        }
        if (StringUtil.notNullOrEmpty(roomAllocation.getArrangeTypeId())) {
            dataSource.addRestriction(Restrictions.eq(this.tableName,
                Constants.RM_ARRANGE_TYPE_ID_FIELD_NAME, roomAllocation.getArrangeTypeId()));
        }

        // see if they are reservable (KB#3035993)
        dataSource
            .addRestriction(Restrictions.eq(this.tableName, Constants.RESERVABLE_FIELD_NAME, 1));
        // also see if they are reservable in rm (KB#3036598)
        dataSource.addRestriction(
            Restrictions.eq(Constants.ROOM_TABLE, Constants.RESERVABLE_FIELD_NAME, 1));

        addRestrictions(dataSource, reservation, allDayEvent, externalAllowed, allowConflicts);

        // extra
        addNumberOfAttendeesRestriction(numberAttendees, dataSource);
        RoomArrangementDataSourceRestrictionsHelper
            .addFixedResourcesRestriction(fixedResourceStandards, dataSource);

        // sort on building, default arrangement and capacity first
        dataSource.addSort(this.tableName, "bl_id", DataSource.SORT_ASC);
        dataSource.addSort(this.tableName, "is_default", DataSource.SORT_DESC);
        dataSource.addSort(this.tableName, Constants.MAX_CAPACITY_FIELD_NAME, DataSource.SORT_ASC);

        // then sort on floor and room
        dataSource.addSort(this.tableName, "fl_id", DataSource.SORT_ASC);
        dataSource.addSort(this.tableName, "rm_id", DataSource.SORT_ASC);

        // finally sort on arrangement and configuration for each room
        dataSource.addSort(this.tableName, "rm_arrange_type_id", DataSource.SORT_ASC);
        dataSource.addSort(this.tableName, "config_id", DataSource.SORT_ASC);

        return dataSource.getRecords();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<RoomArrangement> findAvailableRooms(final RoomReservation reservation,
            final Integer numberAttendees, final boolean externalAllowed,
            final List<String> fixedResourceStandards, final boolean allDayEvent,
            final boolean allowConflicts) throws ReservationException {
        return convertRecordsToObjects(this.findAvailableRoomRecords(reservation, numberAttendees,
            externalAllowed, fixedResourceStandards, allDayEvent, allowConflicts));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<RoomArrangement> findAvailableRooms(final String blId, final String flId,
            final String rmId, final String arrangeTypeId, final TimePeriod timePeriod,
            final Integer numberAttendees, final List<String> fixedResourceStandards)
            throws ReservationException {
        // Create the corresponding domain objects for the query.
        final RoomArrangement roomArrangement =
                new RoomArrangement(blId, flId, rmId, null, arrangeTypeId);
        final RoomReservation reservation = new RoomReservation(timePeriod, roomArrangement);

        return this.findAvailableRooms(reservation, numberAttendees, false, fixedResourceStandards,
            false, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final RoomArrangement get(final String blId, final String flId, final String rmId,
            final String configId, final String arrangeTypeId) {

        final DataSource dataSource = this.createCopy();
        dataSource.setApplyVpaRestrictions(false);

        dataSource
            .addRestriction(Restrictions.eq(this.tableName, Constants.BL_ID_FIELD_NAME, blId));
        dataSource
            .addRestriction(Restrictions.eq(this.tableName, Constants.FL_ID_FIELD_NAME, flId));
        dataSource
            .addRestriction(Restrictions.eq(this.tableName, Constants.RM_ID_FIELD_NAME, rmId));
        dataSource.addRestriction(
            Restrictions.eq(this.tableName, Constants.CONFIG_ID_FIELD_NAME, configId));
        dataSource.addRestriction(Restrictions.eq(this.tableName,
            Constants.RM_ARRANGE_TYPE_ID_FIELD_NAME, arrangeTypeId));

        final DataRecord record = dataSource.getRecord();
        RoomArrangement arrangement = null;
        if (record != null) {
            arrangement = convertRecordToObject(record);
        }
        return arrangement;
    }

    /**
     * Adds the number of attendees restriction.
     *
     * @param numberAttendees the number attendees
     * @param dataSource the ds
     */
    protected final void addNumberOfAttendeesRestriction(final Integer numberAttendees,
            final DataSource dataSource) {
        if (numberAttendees != null) {
            dataSource.addRestriction(Restrictions.gte(this.tableName,
                Constants.MAX_CAPACITY_FIELD_NAME, numberAttendees));

            // Do not add the min_required restriction for Reservation Manager and Reservation
            // Service Desk members.
            final User user = ContextStore.get().getUser();
            if (!user.isMemberOfGroup(Constants.RESERVATION_SERVICE_DESK)
                    && !user.isMemberOfGroup(Constants.RESERVATION_MANAGER)) {
                dataSource.addRestriction(
                    Restrictions.lte(this.tableName, "min_required", numberAttendees));
            }
        }
    }

    /**
     * Adds the restrictions.
     *
     * @param dataSource the data source
     * @param reservation the reservation
     * @param allDayEvent true to look for rooms available for all day events
     * @param externalAllowed whether to restrict the results to show only rooms that allow external
     *            guests
     * @param allowConflicts whether to allow conflicts with other reservations
     * @throws ReservationException the reservation exception
     */
    protected final void addRestrictions(final DataSource dataSource,
            final RoomReservation reservation, final boolean allDayEvent,
            final boolean externalAllowed, final boolean allowConflicts)
            throws ReservationException {

        addTimePeriodParameters(dataSource, reservation.getTimePeriod());

        if (!allowConflicts) {
            // checks free busy of rooms
            addTimeRestriction(dataSource, reservation);
        }

        // get the current date and time of the building location
        final Date localCurrentDate = TimePeriod
            .clearTime(LocalDateTimeUtil.currentLocalDateForTimeZone(reservation.getTimeZone()));
        final Time localCurrentTime = new Time(
            LocalDateTimeUtil.currentLocalTimeForTimeZone(reservation.getTimeZone()).getTime());

        // add restriction for announce days
        addAnnounceRestriction(dataSource, reservation.getTimePeriod(), localCurrentDate,
            localCurrentTime);
        // add restriction for maximum days ahead
        addMaxDayAheadRestriction(dataSource, reservation.getTimePeriod(), localCurrentDate);
        // add restriction for security groups
        addSecurityRestriction(dataSource);

        if (!allDayEvent) {
            // add pre and post- block start/end date
            addDayStartEndRestriction(dataSource, reservation.getTimePeriod(), false);
        }

        this.addExternalAllowedRestriction(dataSource, reservation, externalAllowed);

    }

    /**
     * Adds the time restriction.
     *
     * @param dataSource the data source
     * @param reservation the reservation
     */
    protected final void addTimeRestriction(final DataSource dataSource,
            final RoomReservation reservation) {
        Integer[] reservationIdsToExclude = null;
        TimePeriod timePeriod = null;
        if (reservation != null) {
            reservationIdsToExclude = reservation.getReservationIdsInConference();
            timePeriod = reservation.getTimePeriod();
        }

        RoomArrangementDataSourceRestrictionsHelper.addTimeRestriction(timePeriod,
            reservationIdsToExclude, dataSource);
    }

    /**
     * Add external visitors allowed.
     *
     * @param dataSource the data source
     * @param reservation the reservation
     * @param externalAllowed external visitors allowed.
     */
    protected final void addExternalAllowedRestriction(final DataSource dataSource,
            final IReservation reservation, final boolean externalAllowed) {
        // select only the room arrangements that are external visitors allowed, when
        // externalAllowed is specified.
        boolean addRestriction = externalAllowed;

        // KB#3035994
        // add restriction for rooms allowing external attendees when at least one attendee is
        // external
        if (!addRestriction && StringUtil.notNullOrEmpty(reservation.getAttendees())) {
            final String[] attendees = reservation.getAttendees().split(";");
            for (final String attendeeEmail : attendees) {
                if (!DataSourceUtils.isEmployeeEmail(attendeeEmail)) {
                    addRestriction = true;
                    break;
                }
            }
        }

        if (addRestriction) {
            dataSource.addRestriction(Restrictions.eq(this.tableName, "external_allowed", 1));
        }
    }

    /**
     * Create fields to properties mapping. To be compatible with version 19.
     *
     * @return mapping
     */
    @Override
    protected Map<String, String> createFieldToPropertyMapping() {
        final Map<String, String> mapping = super.createFieldToPropertyMapping();

        mapping.put(this.tableName + ".bl_id", "blId");
        mapping.put(this.tableName + ".fl_id", "flId");
        mapping.put(this.tableName + ".rm_id", "rmId");

        mapping.put(this.tableName + ".config_id", "configId");
        mapping.put(this.tableName + ".rm_arrange_type_id", "arrangeTypeId");

        mapping.put(this.tableName + ".min_required", "minRequired");
        mapping.put(this.tableName + ".max_capacity", "maxCapacity");

        mapping.put(this.tableName + ".res_stds_not_allowed", "standardsNotAllowed");

        mapping.put(this.tableName + ".external_allowed", "externalAllowed");
        mapping.put(this.tableName + ".is_default", "isDefault");

        // add the name of the room
        mapping.put("rm.name", Constants.NAME_FIELD_NAME);
        mapping.put("rm.rm_photo", "rmPhoto");
        mapping.put("rm.cap_em", "capEm");
        mapping.put("bl.name", "blName");
        mapping.put("rm_config.excluded_config", "excludedConfig");

        return mapping;
    }

    /**
     * Convert a reservation to local time.
     *
     * @param reservation the received reservation
     * @param roomAllocation the room allocation
     * @param allDayEvent whether the reservation is for an all-day event
     */
    private void convertReservationToLocalTime(final RoomReservation reservation,
            final RoomAllocation roomAllocation, final boolean allDayEvent) {

        // For time zone adjustment, building id is required.
        if (StringUtil.notNullOrEmpty(roomAllocation.getBlId())) {
            final String localTimeZone =
                    TimeZoneConverter.getTimeZoneIdForBuilding(roomAllocation.getBlId());
            if (allDayEvent || StringUtil.isNullOrEmpty(reservation.getTimeZone())) {
                /*
                 * Consider times being in local time * for all day events; * when no other time
                 * zone is specified.
                 */
                reservation.setTimeZone(localTimeZone);
            } else {
                TimeZoneConverter.convertToTimeZone(reservation, localTimeZone);
            }
        }
    }

}
