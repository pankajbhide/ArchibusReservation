package com.archibus.app.reservation.service;

import java.util.List;

import com.archibus.app.common.organization.dao.datasource.EmployeeDataSource;
import com.archibus.app.common.organization.domain.Employee;
import com.archibus.app.common.space.domain.Building;
import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.util.*;
import com.archibus.context.ContextStore;
import com.archibus.context.User.EmployeeVO.SpaceVO;
import com.archibus.datasource.restriction.Restrictions;
import com.archibus.model.view.datasource.ClauseDef.Operation;
import com.archibus.model.view.datasource.ParsedRestrictionDef;
import com.archibus.security.UserAccount.Immutable;
import com.archibus.utility.StringUtil;

/**
 * Provides methods to retrieve employee / user information via email address.
 *
 * @author Yorik Gerlo
 * @since 20.1
 *
 */
public class EmployeeService extends AdminServiceContainer implements IEmployeeService {

    /** The employee data source. */
    private EmployeeDataSource employeeDataSource;

    /** The space service. */
    private ISpaceService spaceService;

    /**
     * {@inheritDoc}
     */
    @Override
    public Employee findEmployee(final String email) {
        if (StringUtil.isNullOrEmpty(email)) {
            throw new ReservationException("No email address specified: [{0}]",
                EmployeeService.class, this.getAdminService(), email);
        }

        this.employeeDataSource.setApplyVpaRestrictions(false);
        this.employeeDataSource.clearRestrictions();
        final ParsedRestrictionDef restriction = new ParsedRestrictionDef();
        restriction.addClause("em", "email", email, Operation.EQUALS);
        final List<Employee> employees = this.employeeDataSource.find(restriction);

        Employee employee = null;
        if (employees.isEmpty()) {
            // @translatable
            throw new ReservationException("No employee found with the email [{0}].",
                EmployeeService.class, this.getAdminService(), email);
        } else {
            employee = employees.get(0);
        }
        return employee;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserLocation getUserLocation() {
        UserLocation userLocation = null;
        final Immutable userAccount = ContextStore.get().getUserAccount();

        if (userAccount.getUser().getEmployee() != null
                && userAccount.getUser().getEmployee().getSpace() != null) {
            final SpaceVO spaceVo = userAccount.getUser().getEmployee().getSpace();

            userLocation = new UserLocation();
            userLocation.setCountryId(spaceVo.getCountryId());
            userLocation.setSiteId(spaceVo.getSiteId());
            userLocation.setBuildingId(spaceVo.getBuildingId());
            userLocation.setFloorId(spaceVo.getFloorId());
            userLocation.setRoomId(spaceVo.getRoomId());

            insertStateAndCity(userLocation);
        }

        return userLocation;
    }

    /**
     * Insert state and city id into the user location.
     *
     * @param userLocation the user location to set state and city id for
     */
    private void insertStateAndCity(final UserLocation userLocation) {
        final String siteId = userLocation.getSiteId();
        final String buildingId = userLocation.getBuildingId();

        if (StringUtil.notNullOrEmpty(siteId) || StringUtil.notNullOrEmpty(buildingId)) {
            // Find the state and city id's for this location.
            final Building filter = new Building();
            filter.setSiteId(siteId);
            filter.setBuildingId(buildingId);
            final List<Building> buildings = this.spaceService.getBuildings(filter);
            if (!buildings.isEmpty()) {
                userLocation.setStateId(buildings.get(0).getStateId());
                userLocation.setCityId(buildings.get(0).getCityId());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmployeeEmail(final String email) {
        this.employeeDataSource.setApplyVpaRestrictions(false);
        this.employeeDataSource.clearRestrictions();
        this.employeeDataSource.addRestriction(
            Restrictions.eq(Constants.EM_TABLE_NAME, Constants.EMAIL_FIELD_NAME, email));
        return !this.employeeDataSource.getRecords().isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getEmployeeEmail(final String emId) {
        this.employeeDataSource.setApplyVpaRestrictions(false);
        this.employeeDataSource.clearRestrictions();
        final Employee employee = this.employeeDataSource.get(emId);

        String email = null;
        if (employee != null) {
            email = employee.getEmail();
        }

        return email;
    }

    /**
     * Sets the employee data source.
     *
     * @param employeeDataSource the new employee data source
     */
    public void setEmployeeDataSource(final EmployeeDataSource employeeDataSource) {
        this.employeeDataSource = employeeDataSource;
    }

    /**
     * Sets the space service.
     *
     * @param spaceService the new space service
     */
    public void setSpaceService(final ISpaceService spaceService) {
        this.spaceService = spaceService;
    }

    /** {@inheritDoc} */
    @Override
    public void setRequestor(final RoomReservation reservation) throws ReservationException {
        final Employee requestor = this.findEmployee(reservation.getEmail());
        final String creatorId = ContextStore.get().getUser().getEmployee().getId();
        ReservationUtils.setCreator(reservation, requestor, creatorId);
    }

}
