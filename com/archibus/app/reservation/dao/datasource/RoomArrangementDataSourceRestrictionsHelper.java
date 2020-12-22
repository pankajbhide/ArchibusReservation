package com.archibus.app.reservation.dao.datasource;

import java.util.List;

import org.springframework.util.StringUtils;

import com.archibus.app.common.util.SchemaUtils;
import com.archibus.app.reservation.domain.*;
import com.archibus.datasource.DataSource;
import com.archibus.datasource.restriction.Restrictions;

/**
 * Utility class. Provides methods to add restrictions to a data source.
 * <p>
 * Used by RoomArrangmentDataSource to add specific restrictions.
 *
 * @author Yorik Gerlo
 * @since 21.2
 *
 */
public final class RoomArrangementDataSourceRestrictionsHelper {

    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private RoomArrangementDataSourceRestrictionsHelper() {
    }

    /**
     * Adds the fixed resources restriction.
     *
     * @param fixedResourceStandards the fixed resource standards
     * @param dataSource the data source
     *            <p>
     *            Suppress PMD warning "AvoidUsingSql" in this method.
     *            <p>
     *            Justification: Case #1.1: Statement with SELECT WHERE EXISTS ... pattern.
     */
    @SuppressWarnings("PMD.AvoidUsingSql")
    public static void addFixedResourcesRestriction(final List<String> fixedResourceStandards,
            final DataSource dataSource) {
        if (fixedResourceStandards != null && !fixedResourceStandards.isEmpty()) {
            int parameterIndex = 0;
            for (final String resourceStd : fixedResourceStandards) {
                // Use a different parameter name for each restriction.
                final String parameterName =
                        Constants.RESOURCE_STD_PARAMETER_NAME + ++parameterIndex;
                dataSource.addParameter(parameterName, resourceStd, DataSource.DATA_TYPE_TEXT);
                final String restriction =
                        " EXISTS (select resource_std from rm_resource_std where rm_resource_std.bl_id = rm_arrange.bl_id and rm_resource_std.fl_id = rm_arrange.fl_id and rm_resource_std.rm_id = rm_arrange.rm_id  "
                                + " and rm_resource_std.config_id = rm_arrange.config_id and rm_resource_std.rm_arrange_type_id = rm_arrange.rm_arrange_type_id and rm_resource_std.resource_std = ${parameters['"
                                + parameterName + "']}) ";

                dataSource.addRestriction(Restrictions.sql(restriction));
            }
        }
    }

    /**
     * Adds the time restriction.
     *
     * @param timePeriod the time period
     * @param reservationIds the reservation ids to exclude from the check
     * @param dataSource the ds
     * @return the restriction that was applied to the data source
     *         <p>
     *         Suppress PMD warning "AvoidUsingSql" in this method.
     *         <p>
     *         Justification: Case #1.1: Statement with SELECT WHERE EXISTS ... pattern.
     */
    @SuppressWarnings("PMD.AvoidUsingSql")
    public static String addTimeRestriction(final TimePeriod timePeriod,
            final Integer[] reservationIds, final DataSource dataSource) {

        String reservationRestriction = "1=1";
        if (timePeriod != null && timePeriod.isComplete()) {
            String editRestriction = "";
            if (reservationIds != null) {
                dataSource.addParameter("reserveId",
                    StringUtils.arrayToCommaDelimitedString(reservationIds),
                    DataSource.DATA_TYPE_VERBATIM);
                editRestriction = " reserve_rm.res_id NOT IN (${parameters['reserveId']}) and ";
            }

            reservationRestriction =
                    " NOT EXISTS (select res_id from reserve_rm left outer join rm_arrange ra "
                            + " on reserve_rm.bl_id = ra.bl_id and reserve_rm.fl_id = ra.fl_id and reserve_rm.rm_id = ra.rm_id and reserve_rm.config_id = ra.config_id and reserve_rm.rm_arrange_type_id = ra.rm_arrange_type_id "
                            + " , rm_config rc " + "  where " + editRestriction
                            + " reserve_rm.bl_id = rm_arrange.bl_id "
                            + " and reserve_rm.fl_id = rm_arrange.fl_id and reserve_rm.rm_id = rm_arrange.rm_id "
                            + " and rc.bl_id=rm_arrange.bl_id AND rc.fl_id=rm_arrange.fl_id AND rc.rm_id=rm_arrange.rm_id "
                            + " and rc.config_id = reserve_rm.config_id "
                            + " and (rc.config_id = rm_arrange.config_id OR rc.excluded_config like '%'''${sql.concat}RTRIM(rm_arrange.config_id)${sql.concat}'''%' ) "
                            // don't check on arrange type
                            + " and (reserve_rm.status = 'Awaiting App.' or reserve_rm.status = 'Confirmed') ";

            // Check if the reservation overlaps other reservations.
            dataSource.addParameter("startDate", timePeriod.getStartDate(),
                DataSource.DATA_TYPE_DATE);
            dataSource.addParameter("startTime", timePeriod.getStartTime(),
                DataSource.DATA_TYPE_TIME);
            dataSource.addParameter("endDate", timePeriod.getEndDate(), DataSource.DATA_TYPE_DATE);
            dataSource.addParameter("endTime", timePeriod.getEndTime(), DataSource.DATA_TYPE_TIME);

            final String startTimeCheck;
            final String endTimeCheck;
            if (dataSource.isOracle()) {
                startTimeCheck =
                        " and ( reserve_rm.time_start - (ra.pre_block + rm_arrange.post_block) / (24*60) < ${parameters['endTime']} ) ";
                endTimeCheck =
                        " and ( reserve_rm.time_end + (rm_arrange.pre_block + ra.post_block) / (24*60) > ${parameters['startTime']} ) ";
            } else if (dataSource.isSqlServer()) {
                startTimeCheck =
                        " and ( DATEADD(mi, -ra.pre_block - rm_arrange.post_block, reserve_rm.time_start) < ${parameters['endTime']}) ";
                endTimeCheck =
                        " and ( DATEADD(mi, rm_arrange.pre_block + ra.post_block, reserve_rm.time_end) > ${parameters['startTime']}) ";
            } else {
                startTimeCheck =
                        " and ( Convert(char(10), DATEADD(mi, -ra.pre_block - rm_arrange.post_block, reserve_rm.time_start), 108) "
                                + " < Convert(char(10), ${parameters['endTime']}, 108) ) ";
                endTimeCheck =
                        " and ( Convert(char(10), DATEADD(mi, rm_arrange.pre_block + ra.post_block, reserve_rm.time_end), 108) "
                                + " > Convert(char(10), ${parameters['startTime']}, 108) ) ";
            }

            if (SchemaUtils.fieldExistsInSchema(Constants.RESERVE_RM_TABLE,
                Constants.DATE_END_FIELD_NAME)) {
                /*
                 * Continuous reservations are supported. Only consider reservations with
                 * other.endDate >= new.startDate and other.startDate <= new.endDate.
                 */
                reservationRestriction += " and reserve_rm.date_start <= ${parameters['endDate']} "
                        + " and reserve_rm.date_end >= ${parameters['startDate']} ";

                // check start time: (start date < requested end date or start date = requested end
                // date and start time < requested end time)
                reservationRestriction += " and (reserve_rm.date_start < ${parameters['endDate']} "
                        + " or reserve_rm.date_start = ${parameters['endDate']} " + startTimeCheck
                        + Constants.RIGHT_PAR;

                // check end time: (end date > requested start date or end date = requested start
                // date and end time > requested end time)
                reservationRestriction += " and (reserve_rm.date_end > ${parameters['startDate']} "
                        + " or reserve_rm.date_end = ${parameters['startDate']} " + endTimeCheck
                        + Constants.RIGHT_PAR;
            } else {
                // Continuous reservations are not supported.
                // Check that no other room reservation exists with other.endTime + preblock +
                // postblock > new.startTime and other.startTime - preblock - postblock <
                // new.endTime

                reservationRestriction += " and reserve_rm.date_start = ${parameters['startDate']} "
                        + startTimeCheck + endTimeCheck;
            }

            // end EXISTS
            reservationRestriction += Constants.RIGHT_PAR;

            dataSource.addRestriction(Restrictions.sql(reservationRestriction));
        }
        return reservationRestriction;
    }

    /**
     * Build a location restriction and add necessary location parameters from the given bean to the
     * given data source.
     *
     * @param dataSource the data source to add parameters to
     * @param bean the bean to get the location values from
     * @return the location restriction in SQL format
     */
    public static String buildLocationRestriction(final DataSource dataSource,
            final RoomAllocation bean) {
        dataSource.addParameter("buildingId", bean.getBlId(), DataSource.DATA_TYPE_TEXT);
        dataSource.addParameter("floorId", bean.getFlId(), DataSource.DATA_TYPE_TEXT);
        dataSource.addParameter("roomId", bean.getRmId(), DataSource.DATA_TYPE_TEXT);
        dataSource.addParameter("configId", bean.getConfigId(), DataSource.DATA_TYPE_TEXT);
        dataSource.addParameter("arrangeTypeId", bean.getArrangeTypeId(),
            DataSource.DATA_TYPE_TEXT);
        return " bl_id = ${parameters['buildingId']} "
                + " AND fl_id = ${parameters['floorId']} AND rm_id = ${parameters['roomId']} "
                + " AND config_id = ${parameters['configId']} "
                + " AND rm_arrange_type_id = ${parameters['arrangeTypeId']} ";
    }

    /**
     * Build the time restriction to avoid overlapping room reservations.
     *
     * @param dataSource the data source to use for building the restriction
     * @param bean the room allocation bean specifying which reservations to ignore
     * @return the restriction in SQL format
     */
    public static String buildTimeRestriction(final DataSource dataSource,
            final RoomAllocation bean) {
        Integer[] reservationIds = null;
        if (bean.getReserveId() != null) {
            reservationIds = new Integer[] { bean.getReserveId() };
        }
        return RoomArrangementDataSourceRestrictionsHelper.addTimeRestriction(bean.getTimePeriod(),
            reservationIds, dataSource);
    }

}
