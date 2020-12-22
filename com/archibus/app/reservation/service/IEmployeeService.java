package com.archibus.app.reservation.service;

import com.archibus.app.common.organization.domain.Employee;
import com.archibus.app.reservation.domain.*;

/**
 * Interface for Employee service.
 *
 * @author Bart Vanderschoot
 * @since 20.1
 *
 */
public interface IEmployeeService {

    /**
     * Find an employee record based on his email address.
     *
     * @param email the email address
     * @return the employee
     * @throws ReservationException when no employee is found with the given email address
     */
    Employee findEmployee(String email) throws ReservationException;

    /**
     * Get the location information of the currently logged-in user.
     *
     * @return the location information, or null if the user has no location
     */
    UserLocation getUserLocation();

    /**
     * Check whether the given email address belongs to an employee.
     *
     * @param email the email address to check
     * @return true if the email address belongs to an employee, false otherwise
     */
    boolean isEmployeeEmail(String email);

    /**
     * Set the requestor information in the given room reservation based on the email property.
     *
     * @param reservation the reservation to set requestor info for
     * @throws ReservationException when the employee is not found
     */
    void setRequestor(RoomReservation reservation) throws ReservationException;

    /**
     * Gets the employee email.
     *
     * @param emId the employee id
     * @return the employee's email address
     */
    String getEmployeeEmail(String emId);

}