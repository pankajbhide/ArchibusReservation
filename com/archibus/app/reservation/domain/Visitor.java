package com.archibus.app.reservation.domain;

import java.sql.Date;

/**
 * Domain class for Visitor.
 *
 * @author Bart Vanderschoot
 *
 */
public class Visitor {

    /** The email. */
    private String email;

    /** The visitor id. */
    private Integer visitorId;

    /** The first name. */
    private String firstName;

    /** The last name. */
    private String lastName;

    /** The company. */
    private String company;

    /** The bl id. */
    private String blId;

    /** The fl id. */
    private String flId;

    /** The rm id. */
    private String rmId;

    /** The comments. */
    private String comments;

    /** The start date. */
    private Date startDate;

    /** The end date. */
    private Date endDate;

    /**
     * Default constructor.
     */
    public Visitor() {
        super();
    }

    /**
     * Constructor with parameters.
     *
     * @param visitorId visitor id
     * @param email email address
     */
    public Visitor(final Integer visitorId, final String email) {
        super();
        this.visitorId = visitorId;
        this.email = email;
    }

    /**
     * Gets the email.
     *
     * @return the email
     */
    public final String getEmail() {
        return this.email;
    }

    /**
     * Gets the visitor id.
     *
     * @return the visitor id
     */
    public final Integer getVisitorId() {
        return this.visitorId;
    }

    /**
     * Sets the email.
     *
     * @param email the new email
     */
    public final void setEmail(final String email) {
        this.email = email;
    }

    /**
     * Sets the visitor id.
     *
     * @param visitorId the new visitor id
     */
    public final void setVisitorId(final Integer visitorId) {
        this.visitorId = visitorId;
    }

    /**
     * Gets the first name.
     *
     * @return the first name
     */
    public final String getFirstName() {
        return this.firstName;
    }

    /**
     * Sets the first name.
     *
     * @param firstName the new first name
     */
    public final void setFirstName(final String firstName) {
        this.firstName = firstName;
    }

    /**
     * Gets the last name.
     *
     * @return the last name
     */
    public final String getLastName() {
        return this.lastName;
    }

    /**
     * Sets the last name.
     *
     * @param lastName the new last name
     */
    public final void setLastName(final String lastName) {
        this.lastName = lastName;
    }

    /**
     * Gets the company.
     *
     * @return the company
     */
    public final String getCompany() {
        return this.company;
    }

    /**
     * Sets the company.
     *
     * @param company the new company
     */
    public final void setCompany(final String company) {
        this.company = company;
    }

    /**
     * Gets the bl id.
     *
     * @return the bl id
     */
    public final String getBlId() {
        return this.blId;
    }

    /**
     * Sets the bl id.
     *
     * @param blId the new bl id
     */
    public final void setBlId(final String blId) {
        this.blId = blId;
    }

    /**
     * Gets the fl id.
     *
     * @return the fl id
     */
    public final String getFlId() {
        return this.flId;
    }

    /**
     * Sets the fl id.
     *
     * @param flId the new fl id
     */
    public final void setFlId(final String flId) {
        this.flId = flId;
    }

    /**
     * Gets the rm id.
     *
     * @return the rm id
     */
    public final String getRmId() {
        return this.rmId;
    }

    /**
     * Sets the rm id.
     *
     * @param rmId the new rm id
     */
    public final void setRmId(final String rmId) {
        this.rmId = rmId;
    }

    /**
     * Gets the comments.
     *
     * @return the comments
     */
    public final String getComments() {
        return this.comments;
    }

    /**
     * Sets the comments.
     *
     * @param comments the new comments
     */
    public final void setComments(final String comments) {
        this.comments = comments;
    }

    /**
     * Gets the start date.
     *
     * @return the start date
     */
    public final Date getStartDate() {
        return this.startDate;
    }

    /**
     * Sets the start date.
     *
     * @param startDate the new start date
     */
    public final void setStartDate(final Date startDate) {
        this.startDate = startDate;
    }

    /**
     * Gets the end date.
     *
     * @return the end date
     */
    public final Date getEndDate() {
        return this.endDate;
    }

    /**
     * Sets the end date.
     *
     * @param endDate the new end date
     */
    public final void setEndDate(final Date endDate) {
        this.endDate = endDate;
    }

}
