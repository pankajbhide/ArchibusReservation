package com.archibus.app.reservation.dao.datasource;

import java.sql.Time;
import java.util.*;

import com.archibus.app.common.util.SchemaUtils;
import com.archibus.app.reservation.dao.*;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.util.DataSourceUtils;
import com.archibus.datasource.*;
import com.archibus.datasource.data.DataRecord;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.utility.ExceptionBase;

/**
 * The Class ResourceAllocationDataSource.
 *
 * @author Bart Vanderschoot
 */
public class ResourceAllocationDataSource extends AbstractAllocationDataSource<ResourceAllocation>
        implements IResourceAllocationDataSource {

    /** Error message informing the user that a resource is no longer available. */
    // @translatable
    private static final String RESOURCE_NO_LONGER_AVAILABLE =
            "The resource {0} is no longer available";

    /** Quantity field, data source parameter and property. */
    private static final String QUANTITY = "quantity";

    /** resourceDataSource resourceDataSource. */
    private IResourceDataSource resourceDataSource;

    /**
     * Default constructor.
     */
    public ResourceAllocationDataSource() {
        this("resourceAllocation", "reserve_rs");
    }

    /**
     * Constructor using parameters.
     *
     * @param beanName the bean name
     * @param tableName the table name
     */
    protected ResourceAllocationDataSource(final String beanName, final String tableName) {
        super(beanName, tableName, Constants.RSRES_ID_FIELD_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected IReservable getReservable(final ResourceAllocation allocation) {
        return this.resourceDataSource.get(allocation.getResourceId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<ResourceAllocation> getResourceAllocations(final IReservation reservation) {
        return this.find(reservation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<ResourceAllocation> getResourceAllocations(final Date startDate,
            final Date endDate, final String blId, final String flId, final String rmId) {
        final List<DataRecord> records = getAllocationRecords(startDate, endDate, blId, flId, rmId);

        return convertRecordsToObjects(records);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<ResourceAllocation> getResourceAllocations(final Date startDate,
            final String blId, final String flId, final String rmId) {
        return getResourceAllocations(startDate, null, blId, flId, rmId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancelOther(final IReservation reservation) {
        // create a list of the new reservation
        final List<Integer> ids = new ArrayList<Integer>();

        for (final ResourceAllocation resourceAllocation : reservation.getResourceAllocations()) {
            ids.add(resourceAllocation.getId());
        }

        // loop through the resource allocations in the database
        for (final ResourceAllocation allocation : this.getResourceAllocations(reservation)) {
            // when the resource allocation is removed,
            // AD-88 avoid re-canceling of the allocation
            final Status resourceAllocationStatus = Status.fromString(allocation.getStatus());
            if (!ids.contains(allocation.getId())
                    && (Status.AWAITING_APPROVAL.equals(resourceAllocationStatus) 
                            || Status.CONFIRMED.equals(resourceAllocationStatus))) {
                this.cancel(allocation);
            }
        }
    }

    /**
     * Check canceling.
     *
     * @param allocation the allocation
     * @throws ReservationException the reservation exception
     */
    @Override
    public void checkCancelling(final ResourceAllocation allocation) throws ReservationException {
        final Resource resource = this.resourceDataSource.get(allocation.getResourceId());

        final Integer cancelDays = resource.getCancelDays();
        final Time cancelTime = resource.getCancelTime();

        // @translatable
        checkStatusAndTimeAhead(allocation, cancelDays, cancelTime,
            "The resource reservation in {0}-{1}-{2} cannot be cancelled.",
            ResourceAllocationDataSource.class, resource);
    }

    /**
     * Check editing.
     *
     * @param allocation the allocation
     * @throws ReservationException the reservation exception
     */
    @Override
    public void checkEditing(final ResourceAllocation allocation) throws ReservationException {
        final Resource resource = this.resourceDataSource.get(allocation.getResourceId());

        final Integer announceDays = resource.getAnnounceDays();
        final Time announceTime = resource.getAnnounceTime();

        // @translatable
        checkStatusAndTimeAhead(allocation, announceDays, announceTime,
            "The resource reservation in {0}-{1}-{2} cannot be modified.",
            ResourceAllocationDataSource.class, resource);
    }

    /**
     * Create fields to properties mapping. To be compatible with version 19.
     *
     * @return mapping
     */
    @Override
    public Map<String, String> createFieldToPropertyMapping() {
        final Map<String, String> mapping = super.createFieldToPropertyMapping();
        mapping.put(this.tableName + ".rsres_id", "id");
        mapping.put(this.tableName + ".resource_id", "resourceId");
        mapping.put(this.tableName + ".quantity", QUANTITY);
        mapping.put(this.tableName + ".cost_rsres", "cost");

        return mapping;
    }

    /**
     * Calculate the total cost for the allocation.
     *
     * @param allocation resource allocation
     */
    @Override
    public void calculateCost(final ResourceAllocation allocation) {
        final IReservable reservable = this.resourceDataSource.get(allocation.getResourceId());

        final double units = getCostUnits(allocation, reservable);

        // check on external ??
        final double costPerUnit = reservable.getCostPerUnit();
        final int quantity = allocation.getQuantity();
        // calculate cost and round to 2 decimals
        final double cost = DataSourceUtils.round2(costPerUnit * units * quantity);

        allocation.setCost(cost);
    }

    /**
     * Setter for ResourceDataSource.
     *
     * @param resourceDataSource resourceDataSource to set
     */
    public void setResourceDataSource(final IResourceDataSource resourceDataSource) {
        this.resourceDataSource = resourceDataSource;
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
    public ResourceAllocation save(final ResourceAllocation bean) {
        final DataSource dataSource = this.createCopy();
        addParameters(bean, dataSource);

        final String resourceRestriction =
                ResourceDataSourceRestrictionsHelper.buildResourceRestriction(dataSource, bean);
        final String timeRestriction = getTimeRestriction(bean, dataSource);

        String sql = "INSERT INTO reserve_rs ";
        if (SchemaUtils.fieldExistsInSchema(this.tableName, Constants.DATE_END_FIELD_NAME)) {
            sql += " (date_start, time_start, date_end, time_end, res_id, resource_id, quantity, bl_id, fl_id, rm_id) "
                    + " SELECT ${parameters['startDate']}, ${parameters['startTime']}, ${parameters['endDate']}, ${parameters['endTime']}, ";
        } else {
            sql += " (date_start, time_start, time_end, res_id, resource_id, quantity, bl_id, fl_id, rm_id) "
                    + " SELECT ${parameters['startDate']}, ${parameters['startTime']}, ${parameters['endTime']}, ";
        }
        sql += " ${parameters['reserveId']}, resource_id, ${parameters['quantity']}, "
                + " ${parameters['buildingId']}, ${parameters['floorId']}, ${parameters['roomId']} "
                + " FROM resources WHERE " + resourceRestriction + AND + timeRestriction;

        executeSqlForBean(dataSource, sql, bean);
        // get the primary key
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
    public void update(final ResourceAllocation bean) {
        final DataSource dataSource = this.createCopy();
        addParameters(bean, dataSource);
        dataSource.addParameter("rsResId", bean.getId(), DataSource.DATA_TYPE_INTEGER);

        final String resourceRestriction =
                ResourceDataSourceRestrictionsHelper.buildResourceRestriction(dataSource, bean);
        final String timeRestriction = getTimeRestriction(bean, dataSource);

        String sql = "UPDATE reserve_rs SET resource_id = ${parameters['resourceId']}, "
                + " bl_id = ${parameters['buildingId']}, " + " fl_id = ${parameters['floorId']}, "
                + " rm_id = ${parameters['roomId']}, " + " quantity = ${parameters['quantity']}, "
                + " date_start = ${parameters['startDate']}, "
                + " time_start = ${parameters['startTime']}, ";
        if (SchemaUtils.fieldExistsInSchema(Constants.RESERVE_RS_TABLE,
            Constants.DATE_END_FIELD_NAME)) {
            sql += " date_end = ${parameters['endDate']}, ";
        }
        sql += " time_end = ${parameters['endTime']} "
                + " WHERE rsres_id = ${parameters['rsResId']}"
                + " AND EXISTS (SELECT 1 FROM resources WHERE " + resourceRestriction + AND
                + timeRestriction + Constants.RIGHT_PAR;

        this.executeSqlForBean(dataSource, sql, bean);
        super.update(bean);
    }

    /**
     * Add data source parameters based on the given resource allocation.
     *
     * @param bean resource allocation defining the parameters
     * @param dataSource the data source to add parameters to
     */
    private static void addParameters(final ResourceAllocation bean, final DataSource dataSource) {
        dataSource.addParameter("buildingId", bean.getBlId(), DataSource.DATA_TYPE_TEXT);
        dataSource.addParameter("floorId", bean.getFlId(), DataSource.DATA_TYPE_TEXT);
        dataSource.addParameter("roomId", bean.getRmId(), DataSource.DATA_TYPE_TEXT);
        dataSource.addParameter("startDate", bean.getStartDate(), DataSource.DATA_TYPE_DATE);
        dataSource.addParameter("startTime", bean.getStartTime(), DataSource.DATA_TYPE_TIME);
        dataSource.addParameter("endDate", bean.getEndDate(), DataSource.DATA_TYPE_DATE);
        dataSource.addParameter("endTime", bean.getEndTime(), DataSource.DATA_TYPE_TIME);
    }

    /**
     * Get a time restriction to check for overlapping reservations, including a check for the
     * resource type.
     *
     * @param bean the resource allocation
     * @param dataSource the data source to define parameters
     * @return the SQL restriction
     */
    private static String getTimeRestriction(final ResourceAllocation bean,
            final DataSource dataSource) {
        final String timeRestriction = ResourceDataSource
            .getLimitedResourcesRestriction(bean.getReserveId(), dataSource, bean.getQuantity());
        return "(resource_type = 'Unlimited' OR " + timeRestriction + Constants.RIGHT_PAR;
    }

    /**
     * Find the primary key for the given bean in the database and set it in the bean.
     *
     * @param allocation the bean to set the primary key for
     */
    private void setPrimaryKey(final ResourceAllocation allocation) {
        final Map<String, Object> values = new HashMap<String, Object>();
        values.put(Constants.RES_ID, allocation.getReserveId());
        values.put(Constants.BL_ID_FIELD_NAME, allocation.getBlId());
        values.put(Constants.FL_ID_FIELD_NAME, allocation.getFlId());
        values.put(Constants.RM_ID_FIELD_NAME, allocation.getRmId());
        values.put(Constants.DATE_START_FIELD_NAME, allocation.getStartDate());
        values.put(Constants.TIME_START_FIELD_NAME, allocation.getStartTime());
        values.put(Constants.TIME_END_FIELD_NAME, allocation.getEndTime());
        values.put(Constants.RESOURCE_ID_FIELD, allocation.getResourceId());
        values.put(Constants.QUANTITY_FIELD, allocation.getQuantity());

        allocation.setId(getPrimaryKey(values, Constants.RSRES_ID_FIELD_NAME));
    }

    /**
     * Execute the given SQL query to save the given bean.
     *
     * @param dataSource the data source to compile the SQL query
     * @param sql the query to execute
     * @param bean the resource allocation which is saved through the query
     */
    private void executeSqlForBean(final DataSource dataSource, final String sql,
            final ResourceAllocation bean) {

        if (dataSource.isOracle()) {
            /*
             * Explicitly lock the rows in rm_arrange corresponding to this room so no other
             * reservation can be created for that room until this transaction completes.
             */
            final String[] lockFields = new String[] { Constants.RESOURCE_ID_FIELD };
            final DataSource locker = DataSourceFactory
                .createDataSourceForFields(Constants.RESOURCES_TABLE, lockFields);
            locker.addRestriction(Restrictions.eq(Constants.RESOURCES_TABLE,
                Constants.RESOURCE_ID_FIELD, bean.getResourceId()));

            // Add the FOR UPDATE qualifier to lock the resulting row.
            final String lockSql = locker.formatSqlQuery(null, true) + " for update ";
            SqlUtils.executeQuery(locker.getMainTableName(), lockFields, lockSql);
        }

        try {
            this.executeSql(dataSource, sql);
        } catch (final ExceptionBase exception) {
            // Provide a user-friendly error message when a conflict is detected.
            if (exception.getPattern().startsWith("No records updated")) {
                final Resource resource = new Resource();
                resource.setResourceId(bean.getResourceId());
                throw new ReservableNotAvailableException(resource, bean.getReserveId(),
                    RESOURCE_NO_LONGER_AVAILABLE, ResourceAllocation.class, bean.getResourceId());
            } else {
                // @translatable
                throw new ReservationException("Database error when saving resource allocation",
                    exception, ResourceAllocationDataSource.class);
            }
        }
    }

}
