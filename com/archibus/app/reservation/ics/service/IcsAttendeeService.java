package com.archibus.app.reservation.ics.service;

import java.util.*;

import com.archibus.app.common.organization.domain.Employee;
import com.archibus.app.reservation.dao.IVisitorDataSource;
import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.AttendeeResponseStatus.ResponseStatus;
import com.archibus.app.reservation.service.*;
import com.archibus.utility.*;

/**
 * Provides fake attendee response status for invitations sent via email.
 * <p>
 * Managed by Spring
 *
 * @author Yorik Gerlo
 * @since 23.2
 */
public class IcsAttendeeService implements IAttendeeService {

    /** The Constant SPACE. */
    private static final String SPACE = " ";

    /** The employee service. */
    private IEmployeeService employeeService;

    /** The visitors data source. */
    private IVisitorDataSource visitorDataSource;

    /**
     * Set the employee service.
     *
     * @param employeeService the new employee service
     */
    public void setEmployeeService(final IEmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    /**
     * Set the visitor data source.
     *
     * @param visitorDataSource the new visitor data source
     */
    public void setVisitorDataSource(final IVisitorDataSource visitorDataSource) {
        this.visitorDataSource = visitorDataSource;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AttendeeResponseStatus> getAttendeesResponseStatus(final IReservation reservation)
            throws ExceptionBase {
        final List<AttendeeResponseStatus> responses = new ArrayList<AttendeeResponseStatus>();
        if (reservation != null) {
            final String attendeesValue = reservation.getAttendees();
            if (StringUtil.notNullOrEmpty(attendeesValue)) {
                final String[] emails = attendeesValue.split(";");
                for (final String email : emails) {
                    final AttendeeResponseStatus responseStatus = toResponseStatus(email);
                    responses.add(responseStatus);
                }
            }

        }
        return responses;
    }

    /**
     * Get the response status object for the attendee with the given email.
     *
     * @param email the email address
     * @return the attendee response status
     */
    private AttendeeResponseStatus toResponseStatus(final String email) {
        final AttendeeResponseStatus responseStatus = new AttendeeResponseStatus();
        responseStatus.setEmail(email);
        // the response status is always unknown
        responseStatus.setResponseStatus(ResponseStatus.Unknown);

        // lookup name in employee and visitors tables
        try {
            final Employee employee = this.employeeService.findEmployee(email);
            if (StringUtil.isNullOrEmpty(employee.getFirstName())
                    && StringUtil.isNullOrEmpty(employee.getLastName())) {
                responseStatus.setName(employee.getId());
            } else {
                responseStatus.setName(StringUtil.notNull(employee.getFirstName()) + SPACE
                        + StringUtil.notNull(employee.getLastName()));
            }
        } catch (final ReservationException exception) {
            // no employee found.
            final Visitor visitor = this.visitorDataSource.findByEmail(email);
            if (visitor != null) {
                responseStatus.setName(StringUtil.notNull(visitor.getFirstName()) + SPACE
                        + StringUtil.notNull(visitor.getLastName()));
            }
        }
        return responseStatus;
    }

}
