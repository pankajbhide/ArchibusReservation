package com.archibus.app.reservation.dao.datasource;

import java.sql.Time;
import java.util.*;

import com.archibus.app.common.util.SchemaUtils;
import com.archibus.app.reservation.dao.IResourceDataSource;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.util.*;
import com.archibus.datasource.*;
import com.archibus.datasource.data.DataRecord;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.model.view.datasource.ClauseDef.*;
import com.archibus.model.view.datasource.ParsedRestrictionDef;

/**
 * DataSource for Resources.
 *
 * @author Bart Vanderschoot
 */
public abstract class AbstractResourceDataSource extends AbstractReservableDataSource<Resource>
        implements IResourceDataSource {

    /** Field for resource type. */
    private static final String RESOURCE_TYPE_FIELD = "resource_type";

    /** Field for quantity. */
    private static final String QUANTITY = "quantity";

    /** Resource reservations table name. */
    private static final String RESERVE_RS_TABLE = "reserve_rs";

    /**
     * Default Constructor.
     */
    public AbstractResourceDataSource() {
        this("resourceBean", Constants.RESOURCES_TABLE);
    }

    /**
     * Private constructor.
     *
     * @param beanName Spring bean name
     * @param tableName table name
     */
    private AbstractResourceDataSource(final String beanName, final String tableName) {
        super(beanName, tableName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DataRecord> findAvailableLimitedResourceRecords(final IReservation reservation,
            final TimePeriod timePeriod) throws ReservationException {

        final DataSource dataSource = this.createCopy();
        dataSource.setApplyVpaRestrictions(DataSourceUtils.isVpaEnabled());

        dataSource.addRestriction(
            Restrictions.eq(this.tableName, RESOURCE_TYPE_FIELD, ResourceType.LIMITED.toString()));

        if (timePeriod.isComplete()) {
            final String limitedReserved =
                    getLimitedResourcesRestriction(reservation.getReserveId(), dataSource, 1);

            dataSource.addRestriction(Restrictions.sql(limitedReserved));
        }

        addRestrictions(dataSource, reservation, timePeriod, false,
            ReservationUtils.determineCurrentLocalDate(reservation),
            ReservationUtils.determineCurrentLocalTime(reservation));
        return dataSource.getRecords();

    }

    /**
     * Get the restriction to find available limited resources. Can also be used for unique
     * resources.
     *
     * @param reservationId the reservation to exclude from the check
     * @param dataSource the data source to define parameters in
     * @param quantity the quantity required (specify 1 if not yet known)
     * @return the restriction
     *         <p>
     *         Suppress PMD warning "AvoidUsingSql" in this method.
     *         <p>
     *         Justification: Case #1.1: Statement with SELECT WHERE EXISTS ... pattern.
     */
    @SuppressWarnings("PMD.AvoidUsingSql")
    static String getLimitedResourcesRestriction(final Integer reservationId,
            final DataSource dataSource, final int quantity) {
        final String editRestriction = defineEditRestriction(reservationId, dataSource);
        dataSource.addParameter(QUANTITY, quantity, DataSource.DATA_TYPE_INTEGER);

        // no resources have been reserved for this date/time
        String limitedReserved = getUniqueResourcesRestriction(reservationId, dataSource);

        // or there are still available
        limitedReserved +=
                " OR EXISTS (SELECT reserve_rs.resource_id FROM reserve_rs WHERE (reserve_rs.status = 'Awaiting App.' or reserve_rs.status = 'Confirmed') AND "
                        + editRestriction + " reserve_rs.resource_id = resources.resource_id AND "
                        + getOverlappingReservationRestriction(dataSource)
                        + " GROUP BY reserve_rs.resource_id"
                        + " HAVING resources.quantity - ${parameters['quantity']} >= SUM(reserve_rs.quantity) )";
        return limitedReserved;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DataRecord> findAvailableUniqueResourceRecords(final IReservation reservation,
            final TimePeriod timePeriod) throws ReservationException {
        final DataSource dataSource = this.createCopy();
        dataSource.setApplyVpaRestrictions(DataSourceUtils.isVpaEnabled());
        dataSource.addRestriction(
            Restrictions.eq(this.tableName, RESOURCE_TYPE_FIELD, ResourceType.UNIQUE.toString()));

        if (reservation.getStartDate() != null && reservation.getStartTime() != null
                && reservation.getEndTime() != null) {

            final String uniqueNotReserved =
                    getUniqueResourcesRestriction(reservation.getReserveId(), dataSource);
            dataSource.addRestriction(Restrictions.sql(uniqueNotReserved));
        }

        addRestrictions(dataSource, reservation, timePeriod, false,
            ReservationUtils.determineCurrentLocalDate(reservation),
            ReservationUtils.determineCurrentLocalTime(reservation));
        return dataSource.getRecords();
    }

    /**
     * Get the restriction for finding available unique resources.
     *
     * @param reservationId the reservation id to exclude from the check
     * @param dataSource the data source for adding parameters
     * @return the restriction in SQL
     *         <p>
     *         Suppress PMD warning "AvoidUsingSql" in this method.
     *         <p>
     *         Justification: Case #1.1: Statement with SELECT WHERE EXISTS ... pattern.
     */
    @SuppressWarnings("PMD.AvoidUsingSql")
    static String getUniqueResourcesRestriction(final Integer reservationId,
            final DataSource dataSource) {
        final String editRestriction = defineEditRestriction(reservationId, dataSource);

        // check if the reservation overlaps other reservations.
        return " NOT EXISTS (SELECT res_id FROM reserve_rs WHERE " + editRestriction
                + " reserve_rs.resource_id = resources.resource_id "
                + " AND (reserve_rs.status = 'Awaiting App.' or reserve_rs.status = 'Confirmed') AND "
                + getOverlappingReservationRestriction(dataSource) + Constants.RIGHT_PAR;
    }

    /**
     * Find available resource records for existing recurring reservation.
     *
     * @param reservation the reservation currently being edited (whose time period to use)
     * @param reservations the recurring reservations
     * @param resourceType the resource type
     * @return the list of data records
     * @throws ReservationException the reservation exception
     */
    public List<DataRecord> findAvailableResourceRecords(final IReservation reservation,
            final Collection<? extends IReservation> reservations, final ResourceType resourceType)
            throws ReservationException {
        // only resource objects can be used with retainAll
        List<Resource> resources = null;
        for (final IReservation occurrence : reservations) {
            // get the unique resources available for this occurrence
            List<Resource> results = null;
            occurrence.setStartTime(reservation.getStartTime());
            occurrence.setEndTime(reservation.getEndTime());

            if (resourceType.equals(ResourceType.LIMITED)) {
                results = findAvailableLimitedResources(occurrence, occurrence.getTimePeriod());

            } else if (resourceType.equals(ResourceType.UNIQUE)) {
                results = findAvailableUniqueResources(occurrence, occurrence.getTimePeriod());
            }

            if (resources == null) {
                // initialize
                resources = results;
            } else {
                resources.retainAll(results);
            }
        }

        // return as data records
        return this.convertObjectsToRecords(resources);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumberOfReservedResources(final TimePeriod timePeriod, final String resourceId,
            final Integer reserveId, final boolean includePreAndPostBlocks) {
        final DataSourceGroupingImpl dataSource = new DataSourceGroupingImpl();
        dataSource.setApplyVpaRestrictions(false);

        // Join the reserve_rs and resources tables.
        dataSource.addTable(RESERVE_RS_TABLE);
        dataSource.addTable(this.tableName, DataSource.ROLE_STANDARD);

        // Define the calculated total number of reserved resources, grouped by resource_id.
        dataSource.addCalculatedField(this.tableName, "total", DataSource.DATA_TYPE_INTEGER,
            DataSourceGroupingImpl.FORMULA_SUM, RESERVE_RS_TABLE + Constants.DOT + QUANTITY);
        dataSource.addGroupByField(this.tableName, Constants.RESOURCE_ID_FIELD,
            DataSource.DATA_TYPE_TEXT);

        // Ignore cancelled and rejected reservations.
        dataSource.addRestriction(
            Restrictions.notIn(RESERVE_RS_TABLE, Constants.STATUS, "Cancelled,Rejected"));

        if (reserveId != null) {
            // Ignore the resources reserved for the given reservation.
            dataSource
                .addRestriction(Restrictions.ne(RESERVE_RS_TABLE, Constants.RES_ID, reserveId));
        }

        // Get the count for a particular resource and time period.
        /*
         * Note the query doesn't check whether all reservations within the time period overlap.
         * E.g. when looking for a limited resource for a full day, this query will return 2
         * instances reserved if reserved 1x in the morning and 1x in the afternoon.
         */
        dataSource.addRestriction(
            Restrictions.eq(RESERVE_RS_TABLE, Constants.RESOURCE_ID_FIELD, resourceId));

        DataRecord record = null;
        // Count the existing reservations that overlap the given time period.
        if (timePeriod.isComplete()) {
            if (includePreAndPostBlocks) {
                dataSource.addParameter("startDate", timePeriod.getStartDate(),
                    DataSource.DATA_TYPE_DATE);
                dataSource.addParameter("startTime", timePeriod.getStartTime(),
                    DataSource.DATA_TYPE_TIME);
                dataSource.addParameter("endDate", timePeriod.getEndDate(),
                    DataSource.DATA_TYPE_DATE);
                dataSource.addParameter("endTime", timePeriod.getEndTime(),
                    DataSource.DATA_TYPE_TIME);
                dataSource.addRestriction(
                    Restrictions.sql(getOverlappingReservationRestriction(dataSource)));
                record = dataSource.getRecord();
            } else {
                record = calculateRealOverlap(dataSource, timePeriod);
            }
        }

        return (record == null) ? 0 : record.getInt(this.tableName + ".total");
    }

    /**
     * Calculate the reserved count for the given time period with strict overlap (i.e. without pre-
     * and post-blocks).
     *
     * @param dataSource the grouping data source configured for calculation
     * @param timePeriod the time period to check
     * @return the calculated record
     */
    private static DataRecord calculateRealOverlap(final DataSourceGroupingImpl dataSource,
            final TimePeriod timePeriod) {
        DataRecord result = null;
        if (SchemaUtils.fieldExistsInSchema(RESERVE_RS_TABLE, Constants.DATE_END_FIELD_NAME)) {
            // only count reservations that really overlap with the given time period

            dataSource.addRestriction(Restrictions.lte(RESERVE_RS_TABLE,
                Constants.DATE_START_FIELD_NAME, timePeriod.getEndDate()));
            dataSource.addRestriction(Restrictions.gte(RESERVE_RS_TABLE,
                Constants.DATE_END_FIELD_NAME, timePeriod.getStartDate()));

            final ParsedRestrictionDef restriction = new ParsedRestrictionDef();
            restriction.addClause(RESERVE_RS_TABLE, Constants.DATE_START_FIELD_NAME,
                timePeriod.getEndDate(), Operation.LT, RelativeOperation.AND_BRACKET);
            restriction.addClause(RESERVE_RS_TABLE, Constants.DATE_START_FIELD_NAME,
                timePeriod.getEndDate(), Operation.EQUALS, RelativeOperation.OR);
            restriction.addClause(RESERVE_RS_TABLE, Constants.TIME_START_FIELD_NAME,
                timePeriod.getEndTime(), Operation.LT, RelativeOperation.AND_BRACKET);

            restriction.addClause(RESERVE_RS_TABLE, Constants.DATE_END_FIELD_NAME,
                timePeriod.getStartDate(), Operation.GT, RelativeOperation.AND_BRACKET);
            restriction.addClause(RESERVE_RS_TABLE, Constants.DATE_END_FIELD_NAME,
                timePeriod.getStartDate(), Operation.EQUALS, RelativeOperation.OR);
            restriction.addClause(RESERVE_RS_TABLE, Constants.TIME_END_FIELD_NAME,
                timePeriod.getStartTime(), Operation.GT, RelativeOperation.AND_BRACKET);

            final List<DataRecord> records = dataSource.getRecords(restriction);
            if (!records.isEmpty()) {
                result = records.get(0);
            }
        } else {
            // only count reservations that really overlap with the given time period
            dataSource.addRestriction(
                Restrictions.eq(RESERVE_RS_TABLE, "date_start", timePeriod.getStartDate()));
            dataSource.addRestriction(
                Restrictions.gt(RESERVE_RS_TABLE, "time_end", timePeriod.getStartTime()));
            dataSource.addRestriction(
                Restrictions.lt(RESERVE_RS_TABLE, "time_start", timePeriod.getEndTime()));
            result = dataSource.getRecord();
        }
        return result;
    }

    /**
     * Get Overlapping Reservation Restriction.
     *
     * @param dataSource dataSource
     * @return sql restriction
     */
    private static String getOverlappingReservationRestriction(final DataSource dataSource) {
        final String startTimeCheck;
        final String endTimeCheck;
        if (dataSource.isOracle()) {
            startTimeCheck =
                    " and ( reserve_rs.time_start - (resources.pre_block + resources.post_block) / (24*60) < ${parameters['endTime']} ) ";
            endTimeCheck =
                    " and ( reserve_rs.time_end + (resources.pre_block + resources.post_block) / (24*60) > ${parameters['startTime']} )  ";
        } else if (dataSource.isSqlServer()) {
            startTimeCheck =
                    " and ( DATEADD(mi, -resources.pre_block - resources.post_block, reserve_rs.time_start) < ${parameters['endTime']}) ";
            endTimeCheck =
                    " and ( DATEADD(mi, resources.pre_block + resources.post_block, reserve_rs.time_end) > ${parameters['startTime']}) ";
        } else {
            startTimeCheck =
                    " and ( Convert(char(10), DATEADD(mi, -resources.pre_block - resources.post_block, reserve_rs.time_start), 108) < Convert(char(10), ${parameters['endTime']}, 108) ) ";
            endTimeCheck =
                    " and ( Convert(char(10), DATEADD(mi, resources.pre_block + resources.post_block, reserve_rs.time_end), 108) > Convert(char(10), ${parameters['startTime']}, 108) ) ";
        }

        String sql;
        if (SchemaUtils.fieldExistsInSchema(Constants.RESOURCES_TABLE,
            Constants.DATE_END_FIELD_NAME)) {
            // Continuous reservations are supported.
            sql = " reserve_rs.date_start <= ${parameters['endDate']} "
                    + " and reserve_rs.date_end >= ${parameters['startDate']} ";
            sql += " and (reserve_rs.date_start < ${parameters['endDate']} "
                    + " or reserve_rs.date_start = ${parameters['endDate']} " + startTimeCheck
                    + Constants.RIGHT_PAR;
            sql += " and (reserve_rs.date_end > ${parameters['startDate']} "
                    + " or reserve_rs.date_end = ${parameters['startDate']} " + endTimeCheck
                    + Constants.RIGHT_PAR;
        } else {
            // Continuous reservations are not supported
            sql = " reserve_rs.date_start = ${parameters['startDate']} " + startTimeCheck
                    + endTimeCheck;
        }

        return sql;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DataRecord> findAvailableUnlimitedResourceRecords(final IReservation reservation,
            final TimePeriod timePeriod, final boolean allowPartialAvailability)
            throws ReservationException {
        final DataSource dataSource = this.createCopy();
        dataSource.setApplyVpaRestrictions(DataSourceUtils.isVpaEnabled());

        dataSource.addRestriction(Restrictions.eq(this.tableName, RESOURCE_TYPE_FIELD,
            ResourceType.UNLIMITED.toString()));

        addRestrictions(dataSource, reservation, timePeriod, allowPartialAvailability,
            ReservationUtils.determineCurrentLocalDate(reservation),
            ReservationUtils.determineCurrentLocalTime(reservation));

        return dataSource.getRecords();
    }

    /**
     * Add Restrictions.
     *
     * @param dataSource data source
     * @param reservation reservation object
     * @param timePeriod the time period to use for the restrictions
     * @param allowPartialAvailability whether to return resources that are only available for part
     *            of the chosen time frame
     * @param localCurrentDate local current date
     * @param localCurrentTime local current time
     * @throws ReservationException the reservation exception
     */
    protected void addRestrictions(final DataSource dataSource, final IReservation reservation,
            final TimePeriod timePeriod, final boolean allowPartialAvailability,
            final Date localCurrentDate, final Time localCurrentTime) throws ReservationException {
        dataSource.addRestriction(Restrictions.eq(this.tableName, "reservable", 1));

        addTimePeriodParameters(dataSource, timePeriod);
        ResourceDataSourceRestrictionsHelper.addLocationRestriction(dataSource, reservation);
        ResourceDataSourceRestrictionsHelper.addStandardsNotAllowedRestriction(dataSource,
            reservation);

        addAnnounceRestriction(dataSource, timePeriod, localCurrentDate, localCurrentTime);
        addMaxDayAheadRestriction(dataSource, timePeriod, localCurrentDate);

        addDayStartEndRestriction(dataSource, timePeriod, allowPartialAvailability);

        addSecurityRestriction(dataSource);
    }

    /**
     * Mapping of fields to properties.
     *
     * @return mapping
     */
    @Override
    public Map<String, String> createFieldToPropertyMapping() {
        final Map<String, String> mapping = super.createFieldToPropertyMapping();

        mapping.put(this.tableName + ".resource_id", "resourceId");

        mapping.put(this.tableName + ".resource_type", "resourceType");
        mapping.put(this.tableName + ".resource_std", "resourceStandard");
        mapping.put(this.tableName + ".resource_name", "resourceName");

        mapping.put(this.tableName + ".site_id", "siteId");
        mapping.put(this.tableName + ".bl_id", Constants.BL_ID_PARAMETER);

        mapping.put(this.tableName + ".quantity", QUANTITY);

        return mapping;
    }

    /**
     * Define a restriction that excludes resources reserved for the given reservation.
     *
     * @param reservationId the reservation id to exclude
     * @param dataSource the data source to define the restriction for
     * @return SQL string containing the restriction: either it's empty or it contains the
     *         restriction and ends with AND.
     */
    private static String defineEditRestriction(final Integer reservationId,
            final DataSource dataSource) {
        String editRestriction = "";
        if (reservationId != null) {
            dataSource.addParameter("reserveId", reservationId, DataSource.DATA_TYPE_INTEGER);
            editRestriction = " reserve_rs.res_id <> ${parameters['reserveId']} and ";
        }
        return editRestriction;
    }

}
