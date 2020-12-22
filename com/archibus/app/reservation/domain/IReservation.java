package com.archibus.app.reservation.domain;

import java.util.*;

import com.archibus.app.reservation.domain.recurrence.Recurrence;

/**
 * Interface for a reservation.
 *
 * @author Bart Vanderschoot
 *
 */
public interface IReservation extends ITimePeriodBased {

    /**
     * Gets the attendees.
     *
     * @return the attendees
     */
    String getAttendees();

    /**
     * Gets the requested by.
     *
     * @return the requested by
     */
    String getRequestedBy();

    /**
     * Gets the requested for.
     *
     * @return the requested for
     */
    String getRequestedFor();

    /**
     * Gets the email.
     *
     * @return the email
     */
    String getEmail();

    /**
     * Sets the reservation name.
     *
     * @param reservationName the new reservation name
     */
    void setReservationName(String reservationName);

    /**
     * Gets the reservation name.
     *
     * @return the reservation name
     */
    String getReservationName();

    /**
     * Gets the comments.
     *
     * @return the comments
     */
    String getComments();

    /**
     * Gets the time period.
     *
     * @return the time period
     */
    TimePeriod getTimePeriod();

    /**
     * Gets the reserve id.
     *
     * @return the reserve id
     */
    Integer getReserveId();

    /**
     * Gets the resource allocations.
     *
     * @return the resource allocations
     */
    List<ResourceAllocation> getResourceAllocations();

    /**
     * Gets the status.
     *
     * @return the status
     */
    String getStatus();

    /**
     * Gets the unique id coming from Exchange/Outlook.
     *
     * @return the unique id
     */
    String getUniqueId();

    /**
     *
     * Set the unique Id coming from Exchange/Outlook.
     *
     * @param uniqueId unique Id
     */
    void setUniqueId(String uniqueId);

    /**
     * Set the last modified by.
     *
     * @param employeeId employee id
     */
    void setLastModifiedBy(String employeeId);

    /**
     * Set the last modified date.
     *
     * @param date date
     */
    void setLastModifiedDate(Date date);

    /**
     * Sets the reserve id.
     *
     * @param id the new reserve id
     */
    void setReserveId(Integer id);

    /**
     * Gets the time zone.
     *
     * @return the time zone
     */
    @Override
    String getTimeZone();

    /**
     * Get the building id for this reservation.
     *
     * @return the building id, or null if it can't be determined
     */
    String determineBuildingId();

    /**
     * Gets the recurring rule.
     *
     * @return the recurring rule
     */
    String getRecurringRule();

    /**
     * Gets the parent id.
     *
     * @return the parent id
     */
    Integer getParentId();

    /**
     * Get the conference reservation id.
     *
     * @return the conference reservation id
     */
    Integer getConferenceId();

    /**
     * Set the recurrence pattern (not for database persistence).
     *
     * @param recurrence the recurrence pattern
     */
    void setRecurrence(Recurrence recurrence);

    /**
     * Get the temporary recurrence pattern.
     *
     * @return the recurrence pattern
     */
    Recurrence getRecurrence();

    /**
     * Calculate total cost for the reservation including all allocations. The value is set in the
     * object and returned.
     *
     * @return total cost
     */
    double calculateTotalCost();

    /**
     * Sets the attendees.
     *
     * @param attendees the new attendees
     */
    void setAttendees(String attendees);

    /**
     * Sets the comments.
     *
     * @param comments the new comments
     */
    void setComments(String comments);

    /**
     * Get the meeting private flag.
     *
     * @return the flag value
     */
    int getMeetingPrivate();

    /**
     * Gets the recurring date modified.
     *
     * @return the recurring date modified
     */
    int getRecurringDateModified();

    /**
     * Get the occurrence index.
     *
     * @return the occurrenceIndex
     */
    int getOccurrenceIndex();

    /**
     * Set the meeting private flag (1 for private, 0 for public).
     *
     * @param meetingPrivate the new flag value
     */
    void setMeetingPrivate(int meetingPrivate);

    /**
     * Sets the recurring date modified.
     *
     * @param recurringDateModified the new recurring date modified
     */
    void setRecurringDateModified(int recurringDateModified);

    /**
     * Sets the recurring rule.
     *
     * @param recurringRule the new recurring rule
     */
    void setRecurringRule(String recurringRule);

    /**
     * Set the occurrence index.
     *
     * @param occurrenceIndex the occurrenceIndex to set
     */
    void setOccurrenceIndex(int occurrenceIndex);

    /**
     * Sets the parent id.
     *
     * @param parentId the new parent id
     */
    void setParentId(Integer parentId);

    /**
     * Get the comments in HTML format. These are only available when editing a reservation through
     * the web application. The HTML format is not saved to the database.
     *
     * @return reservation comments in HTML format
     */
    String getHtmlComments();

    /**
     * Set the comments in HTML format.
     *
     * @param htmlComments the comments in HTML format
     */
    void setHtmlComments(String htmlComments);
}
