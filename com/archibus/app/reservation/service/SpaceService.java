package com.archibus.app.reservation.service;

import static com.archibus.app.reservation.dao.datasource.Constants.*;

import java.util.*;

import com.archibus.app.common.space.dao.datasource.*;
import com.archibus.app.common.space.domain.*;
import com.archibus.app.reservation.dao.datasource.ArrangeTypeDataSource;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.util.*;
import com.archibus.context.ContextStore;
import com.archibus.datasource.*;
import com.archibus.datasource.data.DataRecord;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.utility.StringUtil;

/**
 * Implementation of space service.
 *
 * @author Bart Vanderschoot
 * @since 20.1
 *
 */
public class SpaceService extends AdminServiceContainer implements ISpaceService {

    /** Messages referenced by the Outlook Plugin. */
    private static final String REFERENCED_BY_PLUGIN = "OUTLOOK_PLUGIN";

    /** Conflict location message id. */
    private static final String MSG_CONFLICT_LOCATION = "CONFLICT_LOCATION";

    /** Conflict location message id for conference calls. */
    private static final String MSG_CONFLICT_LOCATION_CONF_CALL =
            "CONFLICT_LOCATION_CONFERENCE_CALL";

    /** Location message id for conference calls. */
    private static final String MSG_CONF_CALL_MEETING_LOCATION = "CONFERENCE_CALL_MEETING_LOCATION";

    /** Room Information view name used in location data model. */
    private static final String PARAM_ROOM_INFO_VIEW = "PlugInRoomInformationView";

    /** White space used in location string. */
    private static final String WHITESPACE = " ";

    /** Dash used in location string. */
    private static final String DASH = " - ";

    /** Floor in location string. */
    // @translatable
    private static final String FLOOR = "Floor";

    /** Room in location string. */
    // @translatable
    private static final String ROOM = "Room";

    /**
     * Last part of the exists restriction that retrieves only location entities that contain
     * reservable rooms.
     */
    private static final String EXISTS_IN_BL_RM_ARRANGE_PART2 = " and rm_arrange.reservable = 1 ) ";

    /**
     * First part of the exists restriction to retrieve only location entities that contain
     * reservable rooms.
     * <p>
     * Suppress warning PMD.AvoidUsingSql.
     * <p>
     * Justification: Case #1.1: Statement with SELECT WHERE EXISTS ... pattern.
     */
    @SuppressWarnings("PMD.AvoidUsingSql")
    private static final String EXISTS_IN_BL_RM_ARRANGE_PART1 =
            " EXISTS (SELECT 1 FROM bl, rm_arrange WHERE rm_arrange.bl_id = bl.bl_id ";

    /** Floor id field name. */
    private static final String FLOOR_ID = "fl_id";

    /** Building id field name. */
    private static final String BUILDING_ID = "bl_id";

    /** Site id field name. */
    private static final String SITE_ID = "site_id";

    /** City id field name. */
    private static final String CITY_ID = "city_id";

    /** State id field name. */
    private static final String STATE_ID = "state_id";

    /** Name field name. */
    private static final String NAME = "name";

    /** Country id field name. */
    private static final String COUNTRY_ID = "ctry_id";

    /** The site data source. */
    private SiteDataSource siteDataSource;

    /** The building data source. */
    private BuildingDataSource buildingDataSource;

    /** The floor data source. */
    private FloorDataSource floorDataSource;

    /** The room data source. */
    private RoomDataSource roomDataSource;

    /** The arrange type data source. */
    private ArrangeTypeDataSource arrangeTypeDataSource;

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<Country> getCountries(final Country filter) {
        final DataSource dataSource = DataSourceFactory.createDataSourceForFields("ctry",
            new String[] { COUNTRY_ID, NAME });

        dataSource.setApplyVpaRestrictions(DataSourceUtils.isVpaEnabled());
        if (StringUtil.notNullOrEmpty(filter.getCountryId())) {
            dataSource.addRestriction(
                Restrictions.eq(dataSource.getMainTableName(), COUNTRY_ID, filter.getCountryId()));
        }

        dataSource.addRestriction(Restrictions.sql(EXISTS_IN_BL_RM_ARRANGE_PART1
                + " and bl.ctry_id = ctry.ctry_id " + EXISTS_IN_BL_RM_ARRANGE_PART2));

        final List<DataRecord> records = dataSource.getRecords();
        final List<Country> countries = new ArrayList<Country>(records.size());
        for (final DataRecord record : records) {
            final Country country = new Country();
            country.setCountryId(record.getString("ctry.ctry_id"));
            country.setName(record.getString("ctry.name"));
            countries.add(country);
        }
        return countries;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<State> getStates(final State filter) {
        final DataSource dataSource = DataSourceFactory.createDataSourceForFields("state",
            new String[] { COUNTRY_ID, STATE_ID, NAME });

        dataSource.setApplyVpaRestrictions(DataSourceUtils.isVpaEnabled());
        if (StringUtil.notNullOrEmpty(filter.getCountryId())) {
            dataSource.addRestriction(
                Restrictions.eq(dataSource.getMainTableName(), COUNTRY_ID, filter.getCountryId()));
        }
        if (StringUtil.notNullOrEmpty(filter.getStateId())) {
            dataSource.addRestriction(
                Restrictions.eq(dataSource.getMainTableName(), STATE_ID, filter.getStateId()));
        }

        dataSource.addRestriction(Restrictions.sql(EXISTS_IN_BL_RM_ARRANGE_PART1
                + " and bl.state_id = state.state_id " + EXISTS_IN_BL_RM_ARRANGE_PART2));

        final List<DataRecord> records = dataSource.getRecords();
        final List<State> states = new ArrayList<State>(records.size());
        for (final DataRecord record : records) {
            final State state = new State();
            state.setCountryId(record.getString("state.ctry_id"));
            state.setStateId(record.getString("state.state_id"));
            state.setName(record.getString("state.name"));
            states.add(state);
        }
        return states;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<City> getCities(final City filter) {
        final DataSource dataSource = DataSourceFactory.createDataSourceForFields("city",
            new String[] { COUNTRY_ID, STATE_ID, CITY_ID, NAME });

        dataSource.setApplyVpaRestrictions(DataSourceUtils.isVpaEnabled());
        if (StringUtil.notNullOrEmpty(filter.getCountryId())) {
            dataSource.addRestriction(
                Restrictions.eq(dataSource.getMainTableName(), COUNTRY_ID, filter.getCountryId()));
        }
        if (StringUtil.notNullOrEmpty(filter.getStateId())) {
            dataSource.addRestriction(
                Restrictions.eq(dataSource.getMainTableName(), STATE_ID, filter.getStateId()));
        }
        if (StringUtil.notNullOrEmpty(filter.getCityId())) {
            dataSource.addRestriction(
                Restrictions.eq(dataSource.getMainTableName(), CITY_ID, filter.getCityId()));
        }

        dataSource.addRestriction(Restrictions.sql(EXISTS_IN_BL_RM_ARRANGE_PART1
                + " and bl.city_id = city.city_id and bl.state_id = city.state_id "
                + EXISTS_IN_BL_RM_ARRANGE_PART2));

        final List<DataRecord> records = dataSource.getRecords();
        final List<City> cities = new ArrayList<City>(records.size());
        for (final DataRecord record : records) {
            final City city = new City();
            city.setCountryId(record.getString("city.ctry_id"));
            city.setStateId(record.getString("city.state_id"));
            city.setCityId(record.getString("city.city_id"));
            city.setName(record.getString("city.name"));
            cities.add(city);
        }
        return cities;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<Site> getSites(final Site filter) {
        this.siteDataSource.setApplyVpaRestrictions(DataSourceUtils.isVpaEnabled());
        this.siteDataSource.clearRestrictions();

        if (StringUtil.notNullOrEmpty(filter.getCtryId())) {
            this.siteDataSource.addRestriction(Restrictions
                .eq(this.siteDataSource.getMainTableName(), COUNTRY_ID, filter.getCtryId()));
        }
        if (StringUtil.notNullOrEmpty(filter.getStateId())) {
            this.siteDataSource.addRestriction(Restrictions
                .eq(this.siteDataSource.getMainTableName(), STATE_ID, filter.getStateId()));
        }
        if (StringUtil.notNullOrEmpty(filter.getCityId())) {
            this.siteDataSource.addRestriction(Restrictions
                .eq(this.siteDataSource.getMainTableName(), CITY_ID, filter.getCityId()));
        }
        if (StringUtil.notNullOrEmpty(filter.getSiteId())) {
            this.siteDataSource.addRestriction(Restrictions
                .eq(this.siteDataSource.getMainTableName(), SITE_ID, filter.getSiteId()));
        }

        this.siteDataSource.addRestriction(Restrictions.sql(EXISTS_IN_BL_RM_ARRANGE_PART1
                + " and bl.site_id = site.site_id " + EXISTS_IN_BL_RM_ARRANGE_PART2));

        return this.siteDataSource.find(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<Building> getBuildings(final Building filter) {
        final DataSource dataSource = this.buildingDataSource.createCopy();

        if (StringUtil.notNullOrEmpty(filter.getCtryId())) {
            dataSource.addRestriction(
                Restrictions.eq(dataSource.getMainTableName(), COUNTRY_ID, filter.getCtryId()));
        }
        if (StringUtil.notNullOrEmpty(filter.getStateId())) {
            dataSource.addRestriction(
                Restrictions.eq(dataSource.getMainTableName(), STATE_ID, filter.getStateId()));
        }
        if (StringUtil.notNullOrEmpty(filter.getCityId())) {
            dataSource.addRestriction(
                Restrictions.eq(dataSource.getMainTableName(), CITY_ID, filter.getCityId()));
        }
        if (StringUtil.notNullOrEmpty(filter.getSiteId())) {
            dataSource.addRestriction(
                Restrictions.eq(dataSource.getMainTableName(), SITE_ID, filter.getSiteId()));
        }

        boolean applyVpaRestrictions = false;
        if (StringUtil.notNullOrEmpty(filter.getBuildingId())) {
            // Do not apply VPA restrictions if the building code is specified.
            dataSource.addRestriction(Restrictions.eq(dataSource.getMainTableName(), BUILDING_ID,
                filter.getBuildingId()));
        } else {
            // No building code is specified, so apply VPA according to the parameter.
            applyVpaRestrictions = DataSourceUtils.isVpaEnabled();
        }
        dataSource.setApplyVpaRestrictions(applyVpaRestrictions);

        dataSource.setDistinct(true);
        dataSource.addTable(RM_ARRANGE_TABLE, DataSource.ROLE_STANDARD);
        dataSource.addField(RM_ARRANGE_TABLE, RESERVABLE_FIELD_NAME);
        dataSource.addRestriction(Restrictions.eq(RM_ARRANGE_TABLE, RESERVABLE_FIELD_NAME, 1));

        final List<DataRecord> records = dataSource.getRecords();
        final List<Building> buildings = new ArrayList<Building>(records.size());
        for (final DataRecord record : records) {
            buildings.add(this.buildingDataSource.convertRecordToObject(record));
        }
        return buildings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Building getBuildingDetails(final String blId) {
        final DataSource dataSource = this.buildingDataSource.createCopy();
        dataSource.setApplyVpaRestrictions(false);
        dataSource
            .addRestriction(Restrictions.eq(dataSource.getMainTableName(), BUILDING_ID, blId));
        return this.buildingDataSource.convertRecordToObject(dataSource.getRecord());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<Floor> getFloors(final Floor filter) {
        final DataSource dataSource = this.floorDataSource.createCopy();
        if (StringUtil.notNullOrEmpty(filter.getBuildingId())) {
            dataSource.addRestriction(Restrictions.eq(dataSource.getMainTableName(), BUILDING_ID,
                filter.getBuildingId()));
        }

        boolean applyVpaRestrictions = false;
        if (StringUtil.notNullOrEmpty(filter.getFloorId())) {
            // Do not apply VPA if the floor code is specified.
            dataSource.addRestriction(
                Restrictions.eq(dataSource.getMainTableName(), FLOOR_ID, filter.getFloorId()));
        } else {
            // No floor code specified, so apply VPA according to the parameter.
            applyVpaRestrictions = DataSourceUtils.isVpaEnabled();
        }
        dataSource.setApplyVpaRestrictions(applyVpaRestrictions);

        dataSource.setDistinct(true);
        dataSource.addTable(RM_ARRANGE_TABLE, DataSource.ROLE_STANDARD);
        dataSource.addField(RM_ARRANGE_TABLE, RESERVABLE_FIELD_NAME);
        dataSource.addRestriction(Restrictions.eq(RM_ARRANGE_TABLE, RESERVABLE_FIELD_NAME, 1));

        final List<DataRecord> records = dataSource.getRecords();
        final List<Floor> floors = new ArrayList<Floor>(records.size());
        for (final DataRecord record : records) {
            floors.add(this.floorDataSource.convertRecordToObject(record));
        }
        return floors;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Room getRoomDetails(final String blId, final String flId, final String rmId) {
        this.roomDataSource.setApplyVpaRestrictions(false);

        final Room room = new Room();
        room.setBuildingId(blId);
        room.setFloorId(flId);
        room.setId(rmId);

        return this.roomDataSource.getByPrimaryKey(room);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLocationString(final List<RoomArrangement> roomArrangements) {
        String buildingId = null;
        String buildingName = null;
        String siteName = null;

        for (final RoomArrangement roomArrangement : roomArrangements) {
            // if the building id is different from the previous room arrangement, get the new names
            if (buildingId == null || !buildingId.equals(roomArrangement.getBlId())) {
                buildingId = roomArrangement.getBlId();
                final Building building = this.getBuildingDetails(buildingId);
                buildingName = getBuildingName(buildingId, building);
                siteName = getSiteName(building);
            }
            roomArrangement.setLocation(getLocationString(siteName, buildingName, roomArrangement));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLocationString(final RoomReservation reservation) {
        String location = null;
        if (reservation.hasRoomConflictInConferenceCall()) {
            location = ReservationsContextHelper.localizeMessage(REFERENCED_BY_PLUGIN,
                    ContextStore.get().getUser().getLocale(), MSG_CONFLICT_LOCATION_CONF_CALL);
        } else if (reservation.getRoomAllocations().isEmpty()) {
            location = ReservationsContextHelper.localizeMessage(REFERENCED_BY_PLUGIN,
                    ContextStore.get().getUser().getLocale(), MSG_CONFLICT_LOCATION);
        } else if (reservation.getConferenceId() == null) {
            final RoomArrangement roomArrangement =
                    reservation.getRoomAllocations().get(0).getRoomArrangement();
            final Building building = this.getBuildingDetails(roomArrangement.getBlId());

            final String buildingName = getBuildingName(roomArrangement.getBlId(), building);
            final String siteName = getSiteName(building);

            location = getLocationString(siteName, buildingName, roomArrangement);

        } else {
            location = ReservationsContextHelper.localizeMessage(REFERENCED_BY_PLUGIN,
                    ContextStore.get().getUser().getLocale(), MSG_CONF_CALL_MEETING_LOCATION);
        }
        return location;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLocationString(final RoomArrangement roomArrangement) {
        final Building building = this.getBuildingDetails(roomArrangement.getBlId());

        final String buildingName = getBuildingName(roomArrangement.getBlId(), building);
        final String siteName = getSiteName(building);

        return getLocationString(siteName, buildingName, roomArrangement);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> getLocationDataModel(final RoomReservation reservation) {
        final Map<String, Object> model = new HashMap<String, Object>();
        if (!reservation.getRoomAllocations().isEmpty()) {
            final RoomArrangement roomArrangement =
                    reservation.getRoomAllocations().get(0).getRoomArrangement();
            final Building building = this.getBuildingDetails(roomArrangement.getBlId());

            final String buildingName = getBuildingName(roomArrangement.getBlId(), building);
            final String siteName = getSiteName(building);

            model.put("siteName", siteName);
            model.put("buildingName", buildingName);
            model.put("floorId", roomArrangement.getFlId());
            model.put("roomId", roomArrangement.getRmId());
            model.put("configId", roomArrangement.getConfigId());
            model.put("arrangeTypeId", roomArrangement.getArrangeTypeId());

            final Room room = this.getRoomDetails(roomArrangement.getBlId(),
                roomArrangement.getFlId(), roomArrangement.getRmId());
            if (room != null) {
                model.put("roomName", room.getName());
            }

            // build the information URL
            final StringBuffer infoUrl = new StringBuffer(ReservationsContextHelper.getWebCentralUrl());
            infoUrl.append(com.archibus.service.Configuration.getActivityParameterString(
                ReservationsContextHelper.RESERVATIONS_ACTIVITY, PARAM_ROOM_INFO_VIEW));
            infoUrl.append("?rm.bl_id=");
            infoUrl.append(roomArrangement.getBlId());
            infoUrl.append("&rm.fl_id=");
            infoUrl.append(roomArrangement.getFlId());
            infoUrl.append("&rm.rm_id=");
            infoUrl.append(roomArrangement.getRmId());
            infoUrl.append("&rm_arrange.config_id=");
            infoUrl.append(roomArrangement.getConfigId());
            infoUrl.append("&rm_arrange.rm_arrange_type_id=");
            infoUrl.append(roomArrangement.getArrangeTypeId());
            model.put("infoUrl", infoUrl.toString());
        }
        return model;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<ArrangeType> getArrangeTypes() {
        this.arrangeTypeDataSource.sortByDisplayOrder();
        return this.arrangeTypeDataSource.find(null);
    }

    /**
     * Sets the site data source.
     *
     * @param siteDataSource the new site data source
     */
    public final void setSiteDataSource(final SiteDataSource siteDataSource) {
        this.siteDataSource = siteDataSource;
    }

    /**
     * Sets the building data source.
     *
     * @param buildingDataSource the new building data source
     */
    public final void setBuildingDataSource(final BuildingDataSource buildingDataSource) {
        this.buildingDataSource = buildingDataSource;
    }

    /**
     * Sets the floor data source.
     *
     * @param floorDataSource the new floor data source
     */
    public final void setFloorDataSource(final FloorDataSource floorDataSource) {
        this.floorDataSource = floorDataSource;
    }

    /**
     * Sets the room data source.
     *
     * @param roomDataSource the new room data source
     */
    public final void setRoomDataSource(final RoomDataSource roomDataSource) {
        this.roomDataSource = roomDataSource;
    }

    /**
     * Setter for the arrangeTypeDataSource property.
     *
     * @param arrangeTypeDataSource the arrangeTypeDataSource to set
     * @see arrangeTypeDataSource
     */
    public final void setArrangeTypeDataSource(final ArrangeTypeDataSource arrangeTypeDataSource) {
        this.arrangeTypeDataSource = arrangeTypeDataSource;
    }

    /**
     * Get the location string for the given room arrangement, using the given site and building
     * name.
     *
     * @param siteName the site name
     * @param buildingName the building name
     * @param roomArrangement the room arrangement
     * @return the location string
     */
    private String getLocationString(final String siteName, final String buildingName,
            final RoomArrangement roomArrangement) {
        final StringBuffer location = new StringBuffer();

        if (StringUtil.notNullOrEmpty(siteName)) {
            location.append(siteName);
            location.append(DASH);
        }

        if (StringUtil.notNullOrEmpty(buildingName)) {
            location.append(buildingName);
            location.append(DASH);
        }

        // floor id and room id
        location.append(ReservationsContextHelper.localizeString(FLOOR, SpaceService.class, this.getAdminService()));
        location.append(WHITESPACE);
        location.append(roomArrangement.getFlId());
        location.append(WHITESPACE);
        location.append(ReservationsContextHelper.localizeString(ROOM, SpaceService.class, this.getAdminService()));
        location.append(WHITESPACE);
        location.append(roomArrangement.getRmId());

        // room name
        if (StringUtil.isNullOrEmpty(roomArrangement.getName())) {
            final Room room = this.getRoomDetails(roomArrangement.getBlId(),
                roomArrangement.getFlId(), roomArrangement.getRmId());
            if (room != null && StringUtil.notNullOrEmpty(room.getName())) {
                location.append(DASH);
                location.append(room.getName());
            }
        } else {
            // do not query for the room name if it's already in the roomArrangement
            location.append(DASH);
            location.append(roomArrangement.getName());
        }

        // configuration id
        if (!roomArrangement.getRmId().equals(roomArrangement.getConfigId())) {
            location.append(WHITESPACE);
            location.append(roomArrangement.getConfigId());
        }

        return location.toString();
    }

    /**
     * Get the site name or ID if the name is not set.
     *
     * @param building the building for which to retrieve the site name
     * @return the site name or id, or null if the building has no site defined
     */
    private String getSiteName(final Building building) {
        String siteName = null;
        if (building != null && StringUtil.notNullOrEmpty(building.getSiteId())) {
            this.siteDataSource.setApplyVpaRestrictions(DataSourceUtils.isVpaEnabled());
            final Site site = this.siteDataSource.get(building.getSiteId());
            if (site == null || StringUtil.isNullOrEmpty(site.getName())) {
                siteName = building.getSiteId();
            } else {
                siteName = site.getName();
            }
        }
        return siteName;
    }

    /**
     * Get the building name or ID if the name is not set.
     *
     * @param buildingId the building ID to return if the name is not set
     * @param building the building details
     * @return the building name or ID
     */
    private String getBuildingName(final String buildingId, final Building building) {
        String buildingName;
        if (building == null || StringUtil.isNullOrEmpty(building.getName())) {
            buildingName = buildingId;
        } else {
            buildingName = building.getName();
        }
        return buildingName;
    }

}
