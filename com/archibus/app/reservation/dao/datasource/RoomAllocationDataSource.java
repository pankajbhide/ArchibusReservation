package com.archibus.app.reservation.dao.datasource;

import java.sql.Time;
import java.util.*;

import org.springframework.util.StringUtils;

import com.archibus.app.common.util.SchemaUtils;
import com.archibus.app.reservation.dao.*;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.util.DataSourceUtils;
import com.archibus.datasource.*;
import com.archibus.datasource.data.DataRecord;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.utility.ExceptionBase;

/**
 * The Class RoomAllocationDataSource.
 *
 * @author Bart Vanderschoot
 */
public class RoomAllocationDataSource extends AbstractAllocationDataSource<RoomAllocation>
        implements IRoomAllocationDataSource {

    /** Message indicating the room is no longer available. */
    // @translatable
    private static final String ROOM_NO_LONGER_AVAILABLE =
            "The room {0}-{1}-{2} is no longer available";

    /** Name of the configuration ID property and data source parameter. */
    private static final String CONFIG_ID_PROPERTY = "configId";

    /** Name of the arrangement type id property and data source parameter. */
    private static final String ARRANGE_TYPE_PROPERTY = "arrangeTypeId";

    /** roomArrangementDataSource roomArrangementDataSource. */
    private IRoomArrangementDataSource roomArrangementDataSource;

    /**
     * Instantiates a new room allocation data source.
     */
    public RoomAllocationDataSource() {
        this("roomAllocation", "reserve_rm");
    }

    /**
     * Instantiates a new room allocation data source.
     *
     * @param beanName the bean name
     * @param tableName the table name
     */
    protected RoomAllocationDataSource(final String beanName, final String tableName) {
        super(beanName, tableName, Constants.RMRES_ID_FIELD_NAME);

        // join with bl table
        this.addTable(Constants.BUILDING_TABLE, DataSource.ROLE_STANDARD);
        this.addField(Constants.BUILDING_TABLE, Constants.BL_ID_FIELD_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void checkCancelling(final RoomAllocation allocation) throws ReservationException {
        // get the roomArrangement
        final IReservable reservable = this.getReservable(allocation);

        final Integer cancelDays = reservable.getCancelDays();
        final Time cancelTime = reservable.getCancelTime();

        // @translatable
        checkStatusAndTimeAhead(allocation, cancelDays, cancelTime,
            "The room reservation cannot be cancelled.", RoomAllocationDataSource.class,
            reservable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected IReservable getReservable(final RoomAllocation allocation) {
        return this.roomArrangementDataSource.get(allocation.getBlId(), allocation.getFlId(),
            allocation.getRmId(), allocation.getConfigId(), allocation.getArrangeTypeId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<RoomAvailability> getRoomAvailabilities(
            final List<RoomArrangement> roomArrangements, final Date startDate,
            final Date endDate) {
        final List<RoomAvailability> roomAvailibilities = new ArrayList<RoomAvailability>();

        for (final RoomArrangement roomArrangement : roomArrangements) {
            final RoomAvailability roomAvailibility =
                    new RoomAvailability(roomArrangement.getRoom(), startDate, endDate);
            // get allocations for this roomArrangement
            roomAvailibility.setRoomAllocations(getRoomAllocations(roomArrangement.getBlId(),
                roomArrangement.getFlId(), roomArrangement.getRmId(), startDate, endDate));
            roomAvailibilities.add(roomAvailibility);
        }

        return roomAvailibilities;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<RoomAllocation> getRoomAllocations(final IReservation reservation) {
        return this.find(reservation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<RoomAllocation> getRoomAllocations(final String blId, final String flId,
            final String rmId, final Date startDate, final Date endDate) {
        final List<DataRecord> records = getAllocationRecords(startDate, endDate, blId, flId, rmId);

        return convertRecordsToObjects(records);
    }

    /**
     * Get allocated rooms for this date.
     *
     * @param startDate the date to find allocations for
     * @param roomArrangement the room arrangement to find allocations for
     * @param reservationIds identifiers of the reservations to ignore
     * @return list of allocated room records
     */
    @Override
    public List<RoomAllocation> getAllocatedRooms(final Date startDate,
            final RoomArrangement roomArrangement, final Integer[] reservationIds) {
        // get room allocations for this room for this day
        final DataSource dataSource = this.createCopy();
        dataSource.setApplyVpaRestrictions(false);

        // add the configuration parameter
        dataSource.addParameter(CONFIG_ID_PROPERTY, roomArrangement.getConfigId(),
            DataSource.DATA_TYPE_TEXT);
        if (SchemaUtils.fieldExistsInSchema(this.tableName, Constants.DATE_END_FIELD_NAME)) {
            dataSource.addRestriction(
                Restrictions.lte(this.tableName, Constants.DATE_START_FIELD_NAME, startDate));
            dataSource.addRestriction(
                Restrictions.gte(this.tableName, Constants.DATE_END_FIELD_NAME, startDate));
        } else {
            dataSource.addRestriction(
                Restrictions.eq(this.tableName, Constants.DATE_START_FIELD_NAME, startDate));
        }
        dataSource.addRestriction(
            Restrictions.eq(this.tableName, Constants.BL_ID_FIELD_NAME, roomArrangement.getBlId()));
        dataSource.addRestriction(
            Restrictions.eq(this.tableName, Constants.FL_ID_FIELD_NAME, roomArrangement.getFlId()));
        dataSource.addRestriction(
            Restrictions.eq(this.tableName, Constants.RM_ID_FIELD_NAME, roomArrangement.getRmId()));
        // add the restrictions for other configurations
        dataSource
            .addRestriction(Restrictions.sql(" reserve_rm.config_id = ${parameters['configId']} "
                    + " OR EXISTS(select 1 from rm_config where reserve_rm.bl_id = rm_config.bl_id "
                    + " and reserve_rm.fl_id = rm_config.fl_id and reserve_rm.rm_id = rm_config.rm_id "
                    + " and rm_config.config_id = reserve_rm.config_id and rm_config.excluded_config like '%'${parameters['configId']}'%') "));

        // and (reserve_rm.status = 'Awaiting App.' or reserve_rm.status = 'Confirmed') ";
        dataSource.addRestriction(Restrictions
            .sql(" (reserve_rm.status = 'Awaiting App.' or reserve_rm.status = 'Confirmed') "));

        // exclude these reserved rooms when editing
        if (reservationIds != null && reservationIds.length > 0) {
            dataSource.addRestriction(Restrictions.notIn(this.tableName, "res_id",
                StringUtils.arrayToCommaDelimitedString(reservationIds)));
        }

        return convertRecordsToObjects(dataSource.getRecords());
    }

    /**
     * Calculate the total cost for the allocation.
     *
     * @param allocation room allocation
     */
    @Override
    public void calculateCost(final RoomAllocation allocation) {
        final IReservable reservable = this.getReservable(allocation);
        final double units = getCostUnits(allocation, reservable);

        // TODO: check on external
        final double costPerUnit = reservable.getCostPerUnit();
        // calculate cost and round to 2 decimals
        final double cost = DataSourceUtils.round2(costPerUnit * units);

        allocation.setCost(cost);
    }

    /**
     * Check editing.
     *
     * @param allocation the allocation
     * @throws ReservationException the reservation exception
     */
    @Override
    public final void checkEditing(final RoomAllocation allocation) throws ReservationException {
        // get the roomArrangement
        final IReservable reservable = this.getReservable(allocation);

        final Integer announceDays = reservable.getAnnounceDays();
        final Time announceTime = reservable.getAnnounceTime();

        // @translatable
        checkStatusAndTimeAhead(allocation, announceDays, announceTime,
            "The room reservation in {0}-{1}-{2} cannot be modified.",
            RoomAllocationDataSource.class, reservable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<RoomReservation> addRoomAllocations(final List<RoomReservation> reservations) {
        for (final RoomReservation reservation : reservations) {
            this.addRoomAllocations(reservation);
        }
        return reservations;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Suppress PMD warning "AvoidUsingSql" in this method.
     * <p>
     * Justification: Case #2.1: Statement with INSERT ... SELECT pattern.
     */
    @SuppressWarnings("PMD.AvoidUsingSql")
    @Override
    public RoomAllocation save(final RoomAllocation bean) {
        final DataSourceImpl dataSource = (DataSourceImpl) this.createCopy();
        final String locationRestriction = RoomArrangementDataSourceRestrictionsHelper
            .buildLocationRestriction(dataSource, bean);
        final String timeRestriction =
                RoomArrangementDataSourceRestrictionsHelper.buildTimeRestriction(dataSource, bean);

        String sql = "INSERT INTO reserve_rm ";
        if (SchemaUtils.fieldExistsInSchema(Constants.RESERVE_RM_TABLE,
            Constants.DATE_END_FIELD_NAME)) {
            sql += " (date_start, time_start, date_end, time_end, res_id, bl_id, fl_id, rm_id, config_id, rm_arrange_type_id) "
                    + " SELECT ${parameters['startDate']}, ${parameters['startTime']}, ${parameters['endDate']}, ${parameters['endTime']}, ";
        } else {
            sql += " (date_start, time_start, time_end, res_id, bl_id, fl_id, rm_id, config_id, rm_arrange_type_id) "
                    + " SELECT ${parameters['startDate']}, ${parameters['startTime']}, ${parameters['endTime']}, ";
        }
        sql += " ${parameters['reserveId']}, bl_id, fl_id, rm_id, config_id, rm_arrange_type_id "
                + " FROM rm_arrange WHERE " + locationRestriction + AND + timeRestriction;

        executeSqlForBean(bean, dataSource, sql);
        // get the primary key from the database
        this.setPrimaryKey(bean);
        super.update(bean);
        return bean;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Suppress PMD warning "AvoidUsingSql" in this method.
     * <p>
     * Justification: Case #2.2: Statement with UPDATE ... WHERE pattern.
     */
    @SuppressWarnings("PMD.AvoidUsingSql")
    @Override
    public void update(final RoomAllocation bean) {
        final DataSourceImpl dataSource = (DataSourceImpl) this.createCopy();
        final String timeRestriction =
                RoomArrangementDataSourceRestrictionsHelper.buildTimeRestriction(dataSource, bean);
        final String locationRestriction = RoomArrangementDataSourceRestrictionsHelper
            .buildLocationRestriction(dataSource, bean);
        dataSource.addParameter("rmResId", bean.getId(), DataSource.DATA_TYPE_INTEGER);

        String sql = "UPDATE reserve_rm SET bl_id = ${parameters['buildingId']}, "
                + " fl_id = ${parameters['floorId']}, " + " rm_id = ${parameters['roomId']}, "
                + " config_id = ${parameters['configId']}, "
                + " rm_arrange_type_id = ${parameters['arrangeTypeId']}, "
                + " date_start = ${parameters['startDate']}, "
                + " time_start = ${parameters['startTime']}, ";
        if (SchemaUtils.fieldExistsInSchema(Constants.RESERVE_RM_TABLE,
            Constants.DATE_END_FIELD_NAME)) {
            sql += " date_end = ${parameters['endDate']}, ";
        }
        sql += " time_end = ${parameters['endTime']} "
                + " WHERE rmres_id = ${parameters['rmResId']}"
                + " AND EXISTS (SELECT 1 FROM rm_arrange WHERE " + locationRestriction + AND
                + timeRestriction + ")";

        executeSqlForBean(bean, dataSource, sql);
        super.update(bean);
    }

    /**
     * Execute the given SQL query to save the given bean.
     *
     * @param dataSource the data source to compile the SQL query
     * @param sql the query to execute
     * @param bean the resource allocation which is saved through the query
     */
    private void executeSqlForBean(final RoomAllocation bean, final DataSourceImpl dataSource,
            final String sql) {
        if (dataSource.isOracle()) {
            /*
             * Explicitly lock the rows in rm_arrange corresponding to this room so no other
             * reservation can be created for that room until this transaction completes.
             */
            final String[] lockFields = new String[] { Constants.BL_ID_FIELD_NAME,
                    Constants.FL_ID_FIELD_NAME, Constants.RM_ID_FIELD_NAME };
            final DataSource locker = DataSourceFactory
                .createDataSourceForFields(Constants.RM_ARRANGE_TABLE, lockFields);
            locker.addRestriction(Restrictions.eq(Constants.RM_ARRANGE_TABLE,
                Constants.BL_ID_FIELD_NAME, bean.getBlId()));
            locker.addRestriction(Restrictions.eq(Constants.RM_ARRANGE_TABLE,
                Constants.FL_ID_FIELD_NAME, bean.getFlId()));
            locker.addRestriction(Restrictions.eq(Constants.RM_ARRANGE_TABLE,
                Constants.RM_ID_FIELD_NAME, bean.getRmId()));

            // Add the FOR UPDATE qualifier to lock the resulting rows.
            final String lockSql = locker.formatSqlQuery(null, true) + " for update ";
            SqlUtils.executeQuery(locker.getMainTableName(), lockFields, lockSql);
        }

        try {
            executeSql(dataSource, sql);
        } catch (final ExceptionBase exception) {
            // Provide a user-friendly error message when a conflict is detected.
            if (exception.getPattern().startsWith("No records updated")) {
                throw new ReservableNotAvailableException(bean.getRoomArrangement(),
                    bean.getReserveId(), ROOM_NO_LONGER_AVAILABLE, exception, RoomAllocation.class,
                    bean.getBlId(), bean.getFlId(), bean.getRmId());
            } else {
                // @translatable
                throw new ReservationException("Database error when saving room allocation",
                    exception, RoomAllocationDataSource.class);
            }
        }
    }

    /**
     * Find the primary key for the given bean in the database and set it in the bean.
     *
     * @param allocation the bean to set the primary key for
     */
    private void setPrimaryKey(final RoomAllocation allocation) {
        final Map<String, Object> values = new HashMap<String, Object>();
        values.put(Constants.RES_ID, allocation.getReserveId());
        values.put(Constants.DATE_START_FIELD_NAME, allocation.getStartDate());
        values.put(Constants.TIME_START_FIELD_NAME, allocation.getStartTime());
        values.put(Constants.TIME_END_FIELD_NAME, allocation.getEndTime());
        values.put(Constants.BL_ID_FIELD_NAME, allocation.getBlId());
        values.put(Constants.FL_ID_FIELD_NAME, allocation.getFlId());
        values.put(Constants.RM_ID_FIELD_NAME, allocation.getRmId());
        values.put(Constants.CONFIG_ID_FIELD_NAME, allocation.getConfigId());
        values.put(Constants.RM_ARRANGE_TYPE_ID_FIELD_NAME, allocation.getArrangeTypeId());

        allocation.setId(getPrimaryKey(values, Constants.RMRES_ID_FIELD_NAME));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addRoomAllocations(final RoomReservation reservation) {
        List<RoomAllocation> roomAllocations = this.getRoomAllocations(reservation);

        reservation.setRoomAllocations(roomAllocations);
        if (roomAllocations.isEmpty()
                && Constants.STATUS_ROOM_CONFLICT.equals(reservation.getStatus())
                && reservation.getParentId() != null) {
            // get the building id of the parent reservation's room allocation
            roomAllocations = this.findByParentId(reservation.getParentId());
            if (!roomAllocations.isEmpty()) {
                reservation.setBackupBuildingId(roomAllocations.get(0).getBlId());
            }
        }
    }

    /**
     * Create fields to properties mapping. To be compatible with version 19.
     *
     * @return mapping
     */
    @Override
    protected final Map<String, String> createFieldToPropertyMapping() {
        // get super class mapping
        final Map<String, String> mapping = super.createFieldToPropertyMapping();

        mapping.put(this.tableName + ".rmres_id", "id");
        mapping.put(this.tableName + ".cost_rmres", "cost");
        mapping.put(this.tableName + ".config_id", CONFIG_ID_PROPERTY);
        mapping.put(this.tableName + ".rm_arrange_type_id", ARRANGE_TYPE_PROPERTY);
        mapping.put(this.tableName + ".guests_external", "externalGuests");
        mapping.put(this.tableName + ".guests_internal", "internalGuests");
        mapping.put(this.tableName + Constants.DOT + Constants.ATTENDEES_IN_ROOM_FIELD,
            "attendeesInRoom");

        return mapping;
    }

    /**
     * Setter for roomArrangementDataSource.
     *
     * @param roomArrangementDataSource roomArrangementDataSource to set
     */
    public final void setRoomArrangementDataSource(
            final IRoomArrangementDataSource roomArrangementDataSource) {
        this.roomArrangementDataSource = roomArrangementDataSource;
    }

}
