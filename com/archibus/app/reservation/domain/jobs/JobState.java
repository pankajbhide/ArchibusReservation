package com.archibus.app.reservation.domain.jobs;

import com.archibus.jobmanager.JobStatus;

/**
 * Represents a long running job's status.
 * <p>
 * Used by Reservations web service to provide status information about a running job.
 *
 * @author Yorik Gerlo
 * @since 23.2
 */
public class JobState {

    /** Indicates whether the job has finished (succeeded or failed). */
    private boolean finished;

    /** Job status message. */
    private String message;

    /** Job status details. */
    private String details;

    /** Total number of steps in the job. */
    private long totalNumber;

    /** Step currently active. */
    private long currentNumber;

    /** Job status code. */
    private int statusCode;

    /** Title for the current job status code. */
    private String statusCodeTitle;

    /**
     * Create a new Job State from a Web Central JobStatus.
     *
     * @param status the job status in Web Central
     */
    public JobState(final JobStatus status) {
        this.currentNumber = status.getCurrentNumber();
        this.totalNumber = status.getTotalNumber();
        this.details = status.getDetails();
        this.message = status.getMessage();
        this.statusCode = status.getCode();
        this.statusCodeTitle = status.getTitle();
        this.finished = status.canCollect();
    }

    /**
     * Getter for the finished property.
     *
     * @see finished
     * @return the finished property.
     */
    public boolean isFinished() {
        return this.finished;
    }

    /**
     * Setter for the finished property.
     *
     * @see finished
     * @param finished the finished to set
     */
    public void setFinished(final boolean finished) {
        this.finished = finished;
    }

    /**
     * Getter for the message property.
     *
     * @see message
     * @return the message property.
     */
    public String getMessage() {
        return this.message;
    }

    /**
     * Setter for the message property.
     *
     * @see message
     * @param message the message to set
     */
    public void setMessage(final String message) {
        this.message = message;
    }

    /**
     * Getter for the details property.
     *
     * @see details
     * @return the details property.
     */
    public String getDetails() {
        return this.details;
    }

    /**
     * Setter for the details property.
     *
     * @see details
     * @param details the details to set
     */
    public void setDetails(final String details) {
        this.details = details;
    }

    /**
     * Getter for the totalNumber property.
     *
     * @see totalNumber
     * @return the totalNumber property.
     */
    public long getTotalNumber() {
        return this.totalNumber;
    }

    /**
     * Setter for the totalNumber property.
     *
     * @see totalNumber
     * @param totalNumber the totalNumber to set
     */
    public void setTotalNumber(final long totalNumber) {
        this.totalNumber = totalNumber;
    }

    /**
     * Getter for the currentNumber property.
     *
     * @see currentNumber
     * @return the currentNumber property.
     */
    public long getCurrentNumber() {
        return this.currentNumber;
    }

    /**
     * Setter for the currentNumber property.
     *
     * @see currentNumber
     * @param currentNumber the currentNumber to set
     */
    public void setCurrentNumber(final long currentNumber) {
        this.currentNumber = currentNumber;
    }

    /**
     * Getter for the statusCode property.
     *
     * @see statusCode
     * @return the statusCode property.
     */
    public int getStatusCode() {
        return this.statusCode;
    }

    /**
     * Setter for the statusCode property.
     *
     * @see statusCode
     * @param statusCode the statusCode to set
     */
    public void setStatusCode(final int statusCode) {
        this.statusCode = statusCode;
    }

    /**
     * Getter for the statusCodeTitle property.
     *
     * @see statusCodeTitle
     * @return the statusCodeTitle property.
     */
    public String getStatusCodeTitle() {
        return this.statusCodeTitle;
    }

    /**
     * Setter for the statusCodeTitle property.
     *
     * @see statusCodeTitle
     * @param statusCodeTitle the statusCodeTitle to set
     */
    public void setStatusCodeTitle(final String statusCodeTitle) {
        this.statusCodeTitle = statusCodeTitle;
    }

}
