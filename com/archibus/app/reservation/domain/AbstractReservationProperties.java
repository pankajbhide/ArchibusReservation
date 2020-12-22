package com.archibus.app.reservation.domain;

import javax.xml.bind.annotation.XmlTransient;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.utility.StringUtil;

/**
 * Copyright (C) ARCHIBUS, Inc. All rights reserved.
 */
/**
 * Represents an abstract reservation - properties only.
 *
 * @author Yorik Gerlo
 * @since 24.1
 *
 *        <p>
 *        Suppressed warning "PMD.TooManyFields" in this class.
 *        <p>
 *        Justification: reservations have a large number of fields in the database
 */
@SuppressWarnings({ "PMD.TooManyFields" })
public abstract class AbstractReservationProperties extends AbstractReservationBase
        implements IReservation {

    /** The account id. */
    private String accountId;

    /** The attendees. */
    private String attendees;

    /** The contact. */
    private String contact;

    /** The department id. */
    private String departmentId;

    /** The division id. */
    private String divisionId;

    /** The email. */
    private String email;

    /** The requested by. */
    private String requestedBy;

    /** The requested for. */
    private String requestedFor;

    /**
     * Parent ID when there is a recurrent reservation.
     */
    private Integer parentId;

    /** Conference call reservation identifier. */
    private Integer conferenceId;

    /** The phone. */
    private String phone;

    /**
     * The recurring rule is an XML format that describes the recurring type and settings.
     */
    private String recurringRule;

    /**
     * The occurrence index of this reservation in a recurring series.
     */
    private int occurrenceIndex;

    /** The reservation name. */
    private String reservationName;

    /**
     * The reservation type might be 'regular' or 'recurring'.
     */
    private String reservationType;

    /**
     * Unique ID for reservation used in MS Exchange.
     */
    private String uniqueId;

    /** The recurring date modified. */
    private int recurringDateModified;

    /** The meeting private flag. */
    private int meetingPrivate;

    /**
     * Default constructor for initialization via Spring.
     */
    public AbstractReservationProperties() {
        super();
    }

    /**
     * Constructor specifying reservation id.
     *
     * @param reserveId the reservation id
     */
    public AbstractReservationProperties(final Integer reserveId) {
        super(reserveId);
    }

    /**
     * Gets the account id.
     *
     * @return the account id
     */
    public final String getAccountId() {
        return this.accountId;
    }

    /**
     *
     * Gets the attendees.
     *
     * @return the attendees
     *
     * @see com.archibus.reservation.domain.IReservation#getAttendees()
     */
    @Override
    public final String getAttendees() {
        return this.attendees;
    }

    /**
     * Gets the contact.
     *
     * @return the contact
     */
    public final String getContact() {
        return this.contact;
    }

    /**
     * Gets the department id.
     *
     * @return the department id
     */
    public final String getDepartmentId() {
        return this.departmentId;
    }

    /**
     * Gets the division id.
     *
     * @return the division id
     */
    public final String getDivisionId() {
        return this.divisionId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final String getEmail() {
        return this.email;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Integer getParentId() {
        return this.parentId;
    }

    /**
     * Gets the phone.
     *
     * @return the phone
     */
    public final String getPhone() {
        return this.phone;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final String getRecurringRule() {
        return this.recurringRule;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final String getReservationName() {
        return this.reservationName;
    }

    /**
     * Gets the reservation type.
     *
     * @return the reservation type
     */
    public final String getReservationType() {
        return this.reservationType;
    }

    /**
     * Gets the unique id.
     *
     * @return unique id
     *
     * @see com.archibus.reservation.domain.IReservation#getUniqueId()
     */
    @Override
    public final String getUniqueId() {
        return this.uniqueId;
    }

    /**
     * Get the occurrence index.
     *
     * @return the occurrenceIndex
     */
    @Override
    public int getOccurrenceIndex() {
        return this.occurrenceIndex;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setOccurrenceIndex(final int occurrenceIndex) {
        this.occurrenceIndex = occurrenceIndex;
    }

    /**
     * Sets the account id.
     *
     * @param accountId the new account id
     */
    public final void setAccountId(final String accountId) {
        this.accountId = accountId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void setAttendees(final String attendees) {
        this.attendees = attendees;
    }

    /**
     * Get the conference reservation id.
     *
     * @return the conference reservation id
     */
    @Override
    public Integer getConferenceId() {
        return this.conferenceId;
    }

    /**
     * Set the conference reservation id.
     *
     * @param conferenceId the conference reservation id to set
     */
    public void setConferenceId(final Integer conferenceId) {
        this.conferenceId = conferenceId;
    }

    /**
     * Sets the contact.
     *
     * @param contact the new contact
     */
    public final void setContact(final String contact) {
        this.contact = contact;
    }

    /**
     * Sets the department id.
     *
     * @param departmentId the new department id
     */
    public final void setDepartmentId(final String departmentId) {
        this.departmentId = departmentId;
    }

    /**
     * Sets the division id.
     *
     * @param divisionId the new division id
     */
    public final void setDivisionId(final String divisionId) {
        this.divisionId = divisionId;
    }

    /**
     * Sets the email of the organizer.
     *
     * @param email the new email
     */
    public final void setEmail(final String email) {
        this.email = email;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void setParentId(final Integer parentId) {
        this.parentId = parentId;
    }

    /**
     * Sets the phone.
     *
     * @param phone the new phone
     */
    public final void setPhone(final String phone) {
        this.phone = phone;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void setRecurringRule(final String recurringRule) {
        this.recurringRule = recurringRule;
        if (StringUtil.notNullOrEmpty(recurringRule)) {
            this.setReservationType(Constants.TYPE_RECURRING);
        } else {
            this.setReservationType(Constants.TYPE_REGULAR);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void setReservationName(final String reservationName) {
        this.reservationName = reservationName;
    }

    /**
     * Sets the reservation type.
     *
     * @param reservationType the new reservation type
     */
    public final void setReservationType(final String reservationType) {
        this.reservationType = reservationType;
    }

    /**
     * Sets the unique id.
     *
     * @param uniqueId the new unique id
     */
    @Override
    public final void setUniqueId(final String uniqueId) {
        this.uniqueId = uniqueId;
    }

    /**
     * Gets the requested by.
     *
     * @return requested by
     *
     * @see com.archibus.reservation.domain.IReservation#getRequestedBy()
     */
    @Override
    public final String getRequestedBy() {
        return this.requestedBy;
    }

    /**
     * Gets the requested for.
     *
     * @return requested for
     *
     * @see com.archibus.reservation.domain.IReservation#getRequestedFor()
     */
    @Override
    public final String getRequestedFor() {
        return this.requestedFor;
    }

    /**
     * Sets the requested by.
     *
     * @param requestedBy the new requested by
     */
    public final void setRequestedBy(final String requestedBy) {
        this.requestedBy = requestedBy;
    }

    /**
     * Sets the requested for.
     *
     * @param requestedFor the new requested for
     */
    public final void setRequestedFor(final String requestedFor) {
        this.requestedFor = requestedFor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @XmlTransient
    public int getRecurringDateModified() {
        return this.recurringDateModified;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRecurringDateModified(final int recurringDateModified) {
        this.recurringDateModified = recurringDateModified;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @XmlTransient
    public int getMeetingPrivate() {
        return this.meetingPrivate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMeetingPrivate(final int meetingPrivate) {
        this.meetingPrivate = meetingPrivate;
    }

}