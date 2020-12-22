package com.archibus.app.reservation.dao.datasource;

import java.util.List;

import com.archibus.app.reservation.domain.*;
import com.archibus.context.*;
import com.archibus.datasource.DataSource;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.utility.StringUtil;

/**
 * Utility class. Provides methods that add complex restrictions to a Resources data source. Used by
 * AbstractResourceDataSource to add complex restrictions to database queries.
 *
 * @author Yorik Gerlo
 * @since 20.1
 */
public final class ResourceDataSourceRestrictionsHelper {

    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private ResourceDataSourceRestrictionsHelper() {
    }

    /**
     * Add location restriction.
     *
     * @param dataSource data source
     * @param reservation reservation object
     */
    public static void addLocationRestriction(final DataSource dataSource,
            final IReservation reservation) {

        if (reservation instanceof RoomReservation) {
            final List<RoomAllocation> roomAllocations =
                    ((RoomReservation) reservation).getRoomAllocations();
            RoomAllocation roomAllocation = null;
            if (!roomAllocations.isEmpty()) {
                // take the primary room allocation
                roomAllocation = roomAllocations.get(0);
            }
            // check for location restriction on building
            if (roomAllocation != null && StringUtil.notNullOrEmpty(roomAllocation.getBlId())) {
                dataSource.addParameter(Constants.BL_ID_PARAMETER, roomAllocation.getBlId(),
                    DataSource.DATA_TYPE_TEXT);
                dataSource
                    .addRestriction(Restrictions.sql(" resources.bl_id = ${parameters['blId']} "
                            + " OR ( resources.site_id is not null and ${parameters['blId']} "
                            + " IN (select bl_id from bl where site_id = resources.site_id) ) "));
            }
        }
    }

    /**
     * Add nature restriction.
     *
     * @param nature resource nature
     * @param dataSource data source
     *
     */
    public static void addNatureRestriction(final DataSource dataSource,
            final ResourceNature nature) {
        if (nature != null) {
            dataSource.addParameter("nature", nature.toString(), DataSource.DATA_TYPE_TEXT);
            dataSource.addRestriction(Restrictions.sql(
                " EXISTS (select resource_std.resource_std from resource_std where resources.resource_std "
                        + " = resource_std.resource_std and resource_std.resource_nature = ${parameters['nature']} ) "));
        }
    }

    /**
     * Add resource allowed restriction.
     *
     * @param dataSource data source
     * @param reservation reservation object
     */
    public static void addResourceAllowedRestriction(final DataSource dataSource,
            final IReservation reservation) {

        final User user = ContextStore.get().getUser();

        dataSource.addParameter(Constants.GROUP_PARAMETER_NAME, "", DataSource.DATA_TYPE_TEXT);
        final StringBuffer restriction =
                new StringBuffer("( resources.available_for_group IS NULL ");
        for (final String group : user.getGroups()) {
            dataSource.setParameter(Constants.GROUP_PARAMETER_NAME, group);
            restriction.append(" OR resources.available_for_group LIKE ${parameters['group']} ");
        }
        restriction.append(Constants.RIGHT_PAR);
        dataSource.addRestriction(Restrictions.sql(restriction.toString()));
    }

    /**
     * Add room service group restriction.
     *
     * @param dataSource data source
     * @param reservation reservation object
     */
    public static void addRoomServiceGroupRestriction(final DataSource dataSource,
            final IReservation reservation) {
        final User user = ContextStore.get().getUser();

        final StringBuffer restriction = new StringBuffer(
            " (resources.room_service=1) AND (resources.room_service_group IS NULL ");

        dataSource.addParameter(Constants.GROUP_PARAMETER_NAME, "", DataSource.DATA_TYPE_TEXT);
        for (final String group : user.getGroups()) {
            dataSource.setParameter(Constants.GROUP_PARAMETER_NAME, group);
            restriction.append(" OR resources.room_service_group LIKE ${parameters['group']} ");
        }
        restriction.append(Constants.RIGHT_PAR);

        dataSource.addRestriction(Restrictions.sql(restriction.toString()));
    }

    /**
     * Add standards not allowed restriction.
     *
     * @param dataSource data source
     * @param reservation reservation object
     */
    public static void addStandardsNotAllowedRestriction(final DataSource dataSource,
            final IReservation reservation) {
        if (reservation instanceof RoomReservation) {
            final List<RoomAllocation> roomAllocations =
                    ((RoomReservation) reservation).getRoomAllocations();
            RoomAllocation roomAllocation = null;
            if (!roomAllocations.isEmpty()) {
                // take the primary room allocation
                roomAllocation = roomAllocations.get(0);
            }

            if (roomAllocation != null) {
                final StringBuilder restriction =
                        new StringBuilder(" EXISTS (select 1 from rm_arrange where ");
                restriction
                    .append("(res_stds_not_allowed is null or res_stds_not_allowed not like "
                            + " '%''' ${sql.concat} RTRIM(resources.resource_std) ${sql.concat} '''%')");
                addLocationRestrictions(dataSource, roomAllocation, restriction);
                restriction.append(')');
                dataSource.addRestriction(Restrictions.sql(restriction.toString()));
            }
        }
    }

    /**
     * Add location restrictions to a literal SQL restriction.
     *
     * @param dataSource the data source
     * @param room the room allocation specifying the location
     * @param restriction the SQL restriction to append to
     */
    private static void addLocationRestrictions(final DataSource dataSource,
            final RoomAllocation room, final StringBuilder restriction) {
        if (StringUtil.notNullOrEmpty(room.getBlId())) {
            dataSource.addParameter(Constants.BL_ID_PARAMETER, room.getBlId(),
                DataSource.DATA_TYPE_TEXT);
            restriction.append(" and bl_id = ${parameters['blId']} ");
        }
        if (StringUtil.notNullOrEmpty(room.getFlId())) {
            dataSource.addParameter("flId", room.getFlId(), DataSource.DATA_TYPE_TEXT);
            restriction.append(" and fl_id = ${parameters['flId']} ");
        }
        if (StringUtil.notNullOrEmpty(room.getRmId())) {
            dataSource.addParameter("rmId", room.getRmId(), DataSource.DATA_TYPE_TEXT);
            restriction.append(" and rm_id = ${parameters['rmId']} ");
        }
        if (StringUtil.notNullOrEmpty(room.getConfigId())) {
            dataSource.addParameter("configId", room.getConfigId(), DataSource.DATA_TYPE_TEXT);
            restriction.append(" and config_id = ${parameters['configId']} ");
        }
        if (StringUtil.notNullOrEmpty(room.getArrangeTypeId())) {
            dataSource.addParameter("arrangeTypeId", room.getArrangeTypeId(),
                DataSource.DATA_TYPE_TEXT);
            restriction.append(" and rm_arrange_type_id = ${parameters['arrangeTypeId']} ");
        }
    }

    /**
     * Build a restriction to query for a specific resource and add the resource id as a parameter
     * to the data source.
     *
     * @param dataSource the data source to add parameters to
     * @param bean the bean to get the resource id from
     * @return the resource restriction in SQL format
     */
    public static String buildResourceRestriction(final DataSource dataSource,
            final ResourceAllocation bean) {
        dataSource.addParameter("resourceId", bean.getResourceId(), DataSource.DATA_TYPE_TEXT);
        return " resource_id = ${parameters['resourceId']} ";
    }

}
