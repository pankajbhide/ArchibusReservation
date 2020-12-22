package com.archibus.app.reservation.dao.datasource;

import java.util.*;

import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.util.DataSourceUtils;
import com.archibus.datasource.*;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.utility.StringUtil;

/**
 * Room Configurations data source. Used by the reservations room arrangement time line.
 *
 * @author Yorik Gerlo
 * @since 21.3
 */
public class RoomConfigurationDataSource extends ObjectDataSourceImpl<RoomConfiguration> {

    /**
     * Instantiates a new room arrangement data source.
     */
    public RoomConfigurationDataSource() {
        super("roomConfiguration", Constants.ROOM_CONFIG_TABLE);
    }

    /**
     * Get the configuration with given primary key values.
     *
     * @param pkeys the primary key (empty values are ignored)
     * @return the matching room configurations
     */
    public List<RoomConfiguration> get(final RoomConfiguration pkeys) {
        final DataSource dataSrc = this.createCopy();

        if (StringUtil.notNullOrEmpty(pkeys.getBuildingId())) {
            dataSrc.addRestriction(
                Restrictions.eq(this.tableName, Constants.BL_ID_FIELD_NAME, pkeys.getBuildingId()));
        }
        if (StringUtil.notNullOrEmpty(pkeys.getFloorId())) {
            dataSrc.addRestriction(
                Restrictions.eq(this.tableName, Constants.FL_ID_FIELD_NAME, pkeys.getFloorId()));
        }
        if (StringUtil.notNullOrEmpty(pkeys.getRoomId())) {
            dataSrc.addRestriction(
                Restrictions.eq(this.tableName, Constants.RM_ID_FIELD_NAME, pkeys.getRoomId()));
        }
        if (StringUtil.notNullOrEmpty(pkeys.getConfigId())) {
            dataSrc.addRestriction(Restrictions.eq(this.tableName, Constants.CONFIG_ID_FIELD_NAME,
                pkeys.getConfigId()));
        }

        return this.convertRecordsToObjects(dataSrc.getRecords());
    }

    /**
     * Get the room configurations for the rooms referenced by the given room arrangements. This
     * returns all configurations for the referenced rooms, not only the configurations actually
     * referenced by the room arrangements.
     *
     * @param roomArrangements the room arrangements
     * @return the room configurations for the rooms having an arrangement in the list
     */
    public Map<RoomConfiguration, RoomConfiguration> getRoomConfigurations(
            final List<RoomArrangement> roomArrangements) {
        final Map<RoomConfiguration, RoomConfiguration> configurations =
                new HashMap<RoomConfiguration, RoomConfiguration>();
        for (final RoomArrangement arrangement : roomArrangements) {
            final RoomConfiguration pkeys = RoomConfiguration.getConfiguration(arrangement);
            if (!configurations.containsKey(pkeys)) {
                // set config id to null to get all configurations of a room with a single db query
                pkeys.setConfigId(null);
                final List<RoomConfiguration> linkedConfigs = this.get(pkeys);
                // put all found configurations in the map for later reference
                for (final RoomConfiguration config : linkedConfigs) {
                    configurations.put(config, config);
                }
            }
        }
        return configurations;
    }

    /**
     * Create fields to properties mapping. To be compatible with version 19.
     *
     * @return mapping
     */
    @Override
    protected Map<String, String> createFieldToPropertyMapping() {
        final Map<String, String> mapping = new HashMap<String, String>();

        mapping.put(this.tableName + ".bl_id", "buildingId");
        mapping.put(this.tableName + ".fl_id", "floorId");
        mapping.put(this.tableName + ".rm_id", "roomId");

        mapping.put(this.tableName + ".config_id", "configId");
        mapping.put(this.tableName + ".config_name", "configName");

        // add the excluded configuration ids
        mapping.put(this.tableName + Constants.DOT + Constants.EXCLUDED_CONFIG_FIELD,
            "excludedConfigs");

        return mapping;
    }

    /**
     * For version 20 and later.
     *
     * @return array of arrays.
     */
    @Override
    protected String[][] getFieldsToProperties() {
        return DataSourceUtils.getFieldsToProperties(createFieldToPropertyMapping());
    }

}
