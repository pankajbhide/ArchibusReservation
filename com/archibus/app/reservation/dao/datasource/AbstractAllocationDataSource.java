package com.archibus.app.reservation.dao.datasource;

import java.sql.Time;
import java.util.*;

import com.archibus.app.common.util.SchemaUtils;
import com.archibus.app.reservation.dao.IAllocationDataSource;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.util.DataSourceUtils;
import com.archibus.context.*;
import com.archibus.datasource.*;
import com.archibus.datasource.data.DataRecord;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.utility.*;

/**
 * Abstract datasource class for allocations of rooms or resources.
 *
 * @param <T> the generic type
 * @author Bart Vanderschoot
 */
public abstract class AbstractAllocationDataSource<T extends AbstractAllocation>
        extends ObjectDataSourceImpl<T> implements IAllocationDataSource<T> {

    /** SQL and, used to link restrictions. */
    protected static final String AND = " AND ";

    /** Multiplier for a percentage value. */
    private static final Double PERCENTAGE_MULTIPLIER = 0.01;

    /** Maximum value for hour of day. */
    private static final int MAX_HOURS = 23;

    /** Maximum value for minutes. */
    private static final int MAX_MINUTES = 59;

    /** Number of minutes in an hour. */
    private static final int MINUTES_PER_HOUR = 60;

    /** Field name of the allocation id. */
    private final String idFieldName;

    /**
     * Constructor.
     *
     * @param beanName Spring bean name
     * @param tableName table name
     * @param idFieldName allocation primary key field name
     */
    protected AbstractAllocationDataSource(final String beanName, final String tableName,
            final String idFieldName) {
        super(beanName, tableName);
        this.idFieldName = idFieldName;
    }

    /**
     * Check the allocation status and time ahead (i.e. the difference between the current time and
     * the time of the allocation) to determine whether the allocation can be modified or cancelled.
     *
     * @param allocation the allocation to check
     * @param aheadDays minimum number of days before the allocation to allow changes
     * @param aheadTime minimum time before the allocation to allow changes
     * @param errorMessage the error message to use when generating an exception
     * @param clazz class where the error message is defined
     * @param reservable the reservable room/resource this allocation refers to
     * @throws ReservationException when the status or time ahead is not OK
     */
    protected void checkStatusAndTimeAhead(final AbstractAllocation allocation,
            final Integer aheadDays, final Time aheadTime, final String errorMessage,
            final Class<?> clazz, final IReservable reservable) throws ReservationException {
        final DataSource dataSource = this.createCopy();
        dataSource.setApplyVpaRestrictions(false);

        // get local time using the building location
        final Date localCurrentDate = TimePeriod
            .clearTime(LocalDateTimeUtil.currentLocalDate(null, null, null, allocation.getBlId()));
        final Time localCurrentTime =
                LocalDateTimeUtil.currentLocalTime(null, null, null, allocation.getBlId());

        // check if reservation can be modified status
        dataSource
            .addRestriction(Restrictions.eq(this.tableName, this.idFieldName, allocation.getId()));
        dataSource.addRestriction(Restrictions.in(this.tableName, Constants.STATUS,
            Constants.STATUS_AWAITING_APP_OR_CONFIRMED));
        // make sure the current date is on or before start date
        dataSource.addRestriction(
            Restrictions.gte(this.tableName, Constants.DATE_START_FIELD_NAME, localCurrentDate));
        // either the date or the time should be greater than the current date / current time
        dataSource.addRestriction(Restrictions.or(
            Restrictions.gt(this.tableName, Constants.DATE_START_FIELD_NAME, localCurrentDate),
            Restrictions.gt(this.tableName, Constants.TIME_START_FIELD_NAME, localCurrentTime)));

        final DataRecord record = dataSource.getRecord();

        if (record == null) {
            throw new ReservableNotAvailableException(reservable, allocation.getReserveId(),
                errorMessage, clazz, allocation.getBlId(), allocation.getFlId(),
                allocation.getRmId());
        }

        final long daysDifference = DataSourceUtils.getDaysDifference(allocation, localCurrentDate);

        // make sure the days before announcing / canceling is respected
        if (aheadDays != null && daysDifference < aheadDays) {
            throw new ReservableNotAvailableException(reservable, allocation.getReserveId(),
                errorMessage, clazz, allocation.getBlId(), allocation.getFlId(),
                allocation.getRmId());
        }

        // make sure the announce / cancel time is respected
        if (aheadDays != null && aheadTime != null && daysDifference == aheadDays.longValue()
                && localCurrentTime.toString().compareTo(aheadTime.toString()) > 0) {
            throw new ReservableNotAvailableException(reservable, allocation.getReserveId(),
                errorMessage, clazz, allocation.getBlId(), allocation.getFlId(),
                allocation.getRmId());
        }
    }

    /**
     * Mapping of fields to properties.
     *
     * to be compatible with version 19.
     *
     * @return mapping
     */
    @Override
    protected Map<String, String> createFieldToPropertyMapping() {
        final Map<String, String> mapping = new HashMap<String, String>();
        mapping.put(this.tableName + ".res_id", "reserveId");
        mapping.put(this.tableName + ".status", "status");
        mapping.put(this.tableName + ".comments", "comments");

        mapping.put(this.tableName + ".user_last_modified_by", "lastModifiedBy");

        mapping.put(this.tableName + ".date_created", "creationDate");
        mapping.put(this.tableName + ".date_last_modified", "lastModifiedDate");

        mapping.put(this.tableName + ".date_rejected", "rejectedDate");
        mapping.put(this.tableName + ".date_cancelled", "cancelledDate");

        mapping.put(this.tableName + ".date_start", "startDate");
        mapping.put(this.tableName + ".time_start", "startTime");
        mapping.put(this.tableName + ".date_end", "endDate");
        mapping.put(this.tableName + ".time_end", "endTime");

        mapping.put(this.tableName + ".bl_id", "blId");
        mapping.put(this.tableName + ".fl_id", "flId");
        mapping.put(this.tableName + ".rm_id", "rmId");

        return mapping;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<T> find(final IReservation reservation) {
        final DataSource dataSource = this.createCopy();
        dataSource.setApplyVpaRestrictions(false);

        if (reservation != null && reservation.getReserveId() != null) {
            dataSource.addRestriction(
                Restrictions.eq(this.tableName, "res_id", reservation.getReserveId()));
        }

        final List<DataRecord> records = dataSource.getRecords();
        return convertRecordsToObjects(records);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<T> findByParentId(final Integer parentId) {
        final DataSource dataSource = this.createCopy();
        dataSource.setApplyVpaRestrictions(false);

        if (parentId != null) {
            dataSource.addTable(Constants.RESERVE_TABLE_NAME, DataSource.ROLE_STANDARD);
            dataSource.addField(Constants.RESERVE_TABLE_NAME, Constants.RES_PARENT);
            dataSource.addRestriction(
                Restrictions.eq(Constants.RESERVE_TABLE_NAME, Constants.RES_PARENT, parentId));
        }

        final List<DataRecord> records = dataSource.getRecords();
        return convertRecordsToObjects(records);
    }

    /**
     * Find all allocations adhering to the current restrictions of the data source.
     *
     * @return list of allocations
     */
    public final List<T> findAll() {
        final DataSource dataSource = this.createCopy();
        dataSource.setApplyVpaRestrictions(false);
        final List<DataRecord> records = dataSource.getRecords();
        return convertRecordsToObjects(records);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void checkAndUpdate(final T allocation) throws ReservationException {
        final User user = ContextStore.get().getUser();

        if (!user.isMemberOfGroup(Constants.RESERVATION_SERVICE_DESK)
                && !user.isMemberOfGroup(Constants.RESERVATION_MANAGER)) {
            checkEditing(allocation);
        }
        allocation.setLastModifiedBy(user.getEmployee().getId());
        // TODO timezone??
        allocation.setLastModifiedDate(Utility.currentDate());
        this.update(allocation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancel(final T allocation) throws ReservationException {
        final User user = ContextStore.get().getUser();
        if (!user.isMemberOfGroup(Constants.RESERVATION_SERVICE_DESK)
                && !user.isMemberOfGroup(Constants.RESERVATION_MANAGER)) {
            checkCancelling(allocation);
        }

        allocation.setStatus(Constants.STATUS_CANCELLED);
        allocation.setLastModifiedBy(user.getEmployee().getId());
        calculateCancellationCost(allocation);
        allocation.setCancelledDate(Utility.currentDate());
        allocation.setLastModifiedDate(Utility.currentDate());

        // don't use the custom update method when canceling
        super.update(allocation);
    }

    /**
     * Get the reservable that corresponds to the given allocation.
     *
     * @param allocation the allocation
     * @return the reservable object
     */
    protected abstract IReservable getReservable(T allocation);

    /**
     * Calculate the cancellation cost.
     *
     * @param allocation the allocation to calculate the cost for
     */
    @Override
    public void calculateCancellationCost(final T allocation) {
        final IReservable reservable = this.getReservable(allocation);
        final Integer cancelDays = reservable.getCancelDays();
        final Time cancelTime = reservable.getCancelTime();
        final String blId = allocation.getBlId();

        // get local time using the building location
        final Date localCurrentDate =
                TimePeriod.clearTime(LocalDateTimeUtil.currentLocalDate(null, null, null, blId));
        final Time localCurrentTime = LocalDateTimeUtil.currentLocalTime(null, null, null, blId);
        final long daysDifference = DataSourceUtils.getDaysDifference(allocation, localCurrentDate);

        boolean lateCancellation = false;
        if (cancelDays != null) {
            if (daysDifference < cancelDays) {
                lateCancellation = true;
            } else if (cancelTime != null && daysDifference == cancelDays.longValue()
                    && localCurrentTime.after(cancelTime)) {
                lateCancellation = true;
            }
        }
        if (lateCancellation) {
            allocation.setCost(DataSourceUtils.round2(allocation.getCost()
                    * reservable.getCostLateCancelPercentage() * PERCENTAGE_MULTIPLIER));
        } else {
            allocation.setCost(0.0);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T save(final T allocation) {
        final User user = ContextStore.get().getUser();

        allocation.setCreatedBy(user.getEmployee().getId());
        allocation.setCreationDate(Utility.currentDate());

        this.log
            .debug("Status for allocation " + allocation.getId() + " " + allocation.getStatus());

        return super.save(allocation);
    }

    /**
     *
     * Get allocation records.
     *
     * @param startDate start date
     * @param endDate end date
     * @param blId building id
     * @param flId floor id
     * @param rmId room id
     * @return list of data records
     */
    protected List<DataRecord> getAllocationRecords(final Date startDate, final Date endDate,
            final String blId, final String flId, final String rmId) {
        final DataSource dataSource = this.createCopy();
        dataSource.setApplyVpaRestrictions(false);

        if (startDate != null) {
            final String fieldName;
            if (SchemaUtils.fieldExistsInSchema(this.tableName, Constants.DATE_END_FIELD_NAME)) {
                fieldName = Constants.DATE_END_FIELD_NAME;
            } else {
                fieldName = Constants.DATE_START_FIELD_NAME;
            }
            dataSource.addRestriction(Restrictions.gte(this.tableName, fieldName, startDate));
        }
        if (endDate != null) {
            dataSource.addRestriction(
                Restrictions.lte(this.tableName, Constants.DATE_START_FIELD_NAME, endDate));
        }

        if (blId != null) {
            dataSource.addRestriction(Restrictions.eq(this.tableName, "bl_id", blId));
        }
        if (flId != null) {
            dataSource.addRestriction(Restrictions.eq(this.tableName, "fl_id", flId));
        }
        if (rmId != null) {
            dataSource.addRestriction(Restrictions.eq(this.tableName, "rm_id", rmId));
        }

        return dataSource.getRecords();
    }

    /**
     * Get number of units for cost calculation.
     *
     * @param allocation allocation
     * @param reservable reservable object
     *
     * @return units to calculate cost
     */
    protected double getCostUnits(final IAllocation allocation, final IReservable reservable) {
        double units = 0.0;
        // type of cost unit
        switch (reservable.getCostUnit()) {
            case Constants.COST_UNIT_RESERVATION:
                units = 1.0;
                break;

            case Constants.COST_UNIT_DAY:
                // a single continuous reservation could span several days
                units = allocation.getTimePeriod().getDaysDifference() + 1.0;
                break;

            default:
                final TimePeriod singleDay = new TimePeriod(allocation.getTimePeriod());
                singleDay.setEndDate(singleDay.getStartDate());
                double minutes = 0.0;

                if (allocation.getEndDate() != null) {
                    final Calendar calendar = Calendar.getInstance();
                    calendar.setTime(singleDay.getStartDate());

                    while (allocation.getEndDate().after(singleDay.getStartDate())) {
                        singleDay.setEndDate(singleDay.getStartDate());
                        singleDay.setEndTime(reservable.getDayEnd());

                        minutes += singleDay.getMinutesDifference();
                        minutes += addLastMinuteOfDay(singleDay.getEndDateTime());

                        calendar.add(Calendar.DATE, 1);
                        singleDay.setStartDate(calendar.getTime());
                        singleDay.setStartTime(reservable.getDayStart());
                    }

                    singleDay.setEndDate(allocation.getEndDate());
                    singleDay.setEndTime(allocation.getEndTime());
                }
                minutes += singleDay.getMinutesDifference();
                minutes += addLastMinuteOfDay(singleDay.getEndDateTime());

                switch (reservable.getCostUnit()) {
                    case Constants.COST_UNIT_MINUTE:
                        units = minutes;
                        break;
                    case Constants.COST_UNIT_HOUR:
                        units = minutes / MINUTES_PER_HOUR;
                        break;
                    case Constants.COST_UNIT_PARTIAL:
                        units = minutes / (MINUTES_PER_HOUR * Constants.HALF_DAY_HOURS);
                        break;
                    default:
                        break;
                }

                break;
        }

        // Pay for each started unit.
        return Math.ceil(units);
    }

    /**
     * Add one minute in case the time period ends at 23:59.
     *
     * @param endDateTime the end date/time to check
     * @return 1 in case the time indicates 23:59, 0 otherwise
     */
    private int addLastMinuteOfDay(final Date endDateTime) {
        // KB 3052960 - Reservations that go to midnight can have the wrong cost
        // if arrangement unit cost is "by minute"
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(endDateTime);
        int result = 0;
        if (calendar.get(Calendar.HOUR_OF_DAY) == MAX_HOURS
                && calendar.get(Calendar.MINUTE) == MAX_MINUTES) {
            result = 1;
        }
        return result;
    }

    /**
     * Get field to properties for version 20.
     *
     * @return array of arrays.
     */
    @Override
    protected final String[][] getFieldsToProperties() {
        return DataSourceUtils.getFieldsToProperties(createFieldToPropertyMapping());
    }

    /**
     * Execute an SQL query that inserts or updates a record. The SQL string should be formatted
     * with parameters defined in the provided data source. Throws an exception if no records are
     * changed.
     *
     * @param dataSource the data source that defines parameters
     * @param sql the SQL query to execute
     */
    protected void executeSql(final DataSource dataSource, final String sql) {
        final String formattedSql = dataSource.addQuery(sql).formatSqlQuery(null, true);
        this.checkSetContext();
        // Throws an exception if no records are changed.
        SqlUtils.executeUpdateRequired(this.mainTableName, formattedSql);
    }

    /**
     * Retrieves the primary key for a record based on the given values.
     *
     * @param values map of field names (without table prefix) to values to identify the record
     * @param fieldName name of the primary key field
     * @return primary key for the matching record
     * @throws ExceptionBase when the primary key is not found
     */
    protected int getPrimaryKey(final Map<String, Object> values, final String fieldName)
            throws ExceptionBase {
        SqlUtils.normalizeValuesForSql(values);
        final String sql = this.createSqlForLastAddedPK(values);
        final List<DataRecord> records =
                SqlUtils.executeQuery(this.mainTableName, new String[] { fieldName }, sql);

        int primaryKey = 0;
        if (!records.isEmpty()) {
            primaryKey = records.get(0).getInt(this.mainTableName + Constants.DOT + fieldName);
        }
        if (primaryKey == 0) {
            throw new ExceptionBase("Primary key not found");
        }
        return primaryKey;
    }

}
