package com.archibus.app.reservation.domain.jobs;

import com.archibus.utility.ExceptionBase;

/**
 * Base class for asynchronous reservation results. Used by ReservationRemoteService.
 *
 * @author Yorik Gerlo
 * @since 23.2
 */
public class ReservationResult {

    /** Identifier of the job for which this is the result. */
    private String jobId;

    /** Job state. */
    private JobState jobState;

    /** The exception that occurred while running the job (if any). */
    private ExceptionBase exception;

    /** Whether the job completed normally. */
    private boolean completed;

    /**
     * Getter for the jobId property.
     *
     * @see jobId
     * @return the jobId property.
     */
    public String getJobId() {
        return this.jobId;
    }

    /**
     * Setter for the jobId property.
     *
     * @see jobId
     * @param jobId the jobId to set
     */
    public void setJobId(final String jobId) {
        this.jobId = jobId;
    }

    /**
     * Getter for the jobState property.
     *
     * @see jobState
     * @return the jobState property.
     */
    public JobState getJobState() {
        return this.jobState;
    }

    /**
     * Setter for the jobState property.
     *
     * @see jobState
     * @param jobState the jobState to set
     */
    public void setJobState(final JobState jobState) {
        this.jobState = jobState;
    }

    /**
     * Getter for the exception property. To better match original behavior, the server could
     * rethrow this exception instead of returning a result containing this exception.
     *
     * @see exception
     * @return the exception property.
     */
    public ExceptionBase getException() {
        return this.exception;
    }

    /**
     * Setter for the exception property.
     *
     * @see exception
     * @param exception the exception to set
     */
    public void setException(final ExceptionBase exception) {
        this.exception = exception;
    }

    /**
     * Check whether the request was completed successfully.
     *
     * @return true if completed successfully, false otherwise
     */
    public boolean isCompleted() {
        return this.completed;
    }

    /**
     * Setter for the completed property.
     * 
     * @param completed true to set completed, false if not completed
     */
    public void setCompleted(final boolean completed) {
        this.completed = completed;
    }

}