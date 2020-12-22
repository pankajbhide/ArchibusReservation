package com.archibus.app.reservation.service;

import java.sql.Time;
import java.util.List;

import javax.jws.*;
import javax.xml.bind.annotation.XmlSeeAlso;

import com.archibus.app.reservation.domain.*;
import com.archibus.app.reservation.domain.jobs.*;
import com.archibus.app.reservation.domain.recurrence.*;
import com.archibus.utility.ExceptionBase;

/**
 * The Interface ReservationRemoteService.
 *
 * @author Bart Vanderschoot
 */
@WebService(name = "reservationService")
@XmlSeeAlso(value = { DailyPattern.class, WeeklyPattern.class, MonthlyPattern.class,
        YearlyPattern.class, RecurringReservationRequest.class, RecurringReservationResult.class,
        ConferenceReservationRequest.class, ConferenceReservationResult.class,
        CancelReservationRequest.class, CancelReservationResult.class })
public interface ReservationRemoteService extends ReservationRemoteServiceBase {

    /**
     * Cancel room reservation.
     *
     * @param reservation the reservation
     *
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "cancelRoomReservation")
    void cancelRoomReservation(RoomReservation reservation) throws ExceptionBase;

    /**
     * Cancel room reservation by unique id recurrence.
     *
     * @param uniqueId the unique id
     * @param email the email
     * @param disconnectOnError the disconnect on error
     * @return the list of reservations that could not be cancelled
     *
     * @throws ExceptionBase ExceptionBase
     * @deprecated Maintained for compatibility with 21.2 Outlook Plugin and still used by 21.3
     *             Outlook Plugin to remain compatible with 21.2 Web Central.
     */
    @Deprecated
    @WebMethod(action = "cancelRoomReservationRecurrence")
    List<RoomReservation> cancelRoomReservationByUniqueIdRecurrence(String uniqueId, String email,
            boolean disconnectOnError) throws ExceptionBase;

    /**
     * Cancel room reservation by unique id. If no conference id is specified, this cancels all
     * reservations with the given unique id. Otherwise only the reservations with the matching
     * conference id are cancelled.
     *
     * @param uniqueId the unique id
     * @param email the email
     * @param conferenceId the conference call identifier
     * @param disconnectOnError the disconnect on error
     * @return the list of reservations that could not be cancelled
     *
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "cancelRoomReservations")
    List<RoomReservation> cancelRoomReservations(String uniqueId, String email,
            Integer conferenceId, boolean disconnectOnError) throws ExceptionBase;

    /**
     * Disconnect room reservation: remove the appointment unique ID.
     *
     * @param reservation the reservation
     *
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "disconnectRoomReservation")
    void disconnectRoomReservation(RoomReservation reservation) throws ExceptionBase;

    /**
     * Check availability for the given selection of rooms for a conference call reservation
     * (regular or recurring). Required capacities for each room are based on the number of
     * attendees specified in the room allocations.
     *
     * @param reservations room reservations representing the rooms to be booked
     * @param allDayEvent whether to check availability for an all-day event
     * @param recurrence recurrence pattern for a recurring meeting (may be null for non-recurring)
     * @return result of the availability check, including available room arrangements, the parsed
     *         locations template and (for all-day events) the common start and end time
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "checkConferenceRoomsAvailability")
    ConferenceRoomsAvailability checkConferenceRoomsAvailability(List<RoomReservation> reservations,
            boolean allDayEvent, Recurrence recurrence) throws ExceptionBase;

    /**
     * Get room reservation by primary key.
     *
     * @param reserveId reservation id
     * @return room reservation
     *
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "getRoomReservationById")
    RoomReservation getRoomReservationById(final Integer reserveId) throws ExceptionBase;

    /**
     * Gets the room reservations by unique id.
     *
     * @param uniqueId the unique id
     * @return the room reservations by unique id
     *
     * @throws ExceptionBase ExceptionBase
     * @deprecated Maintained for compatibility with 21.2 Outlook Plugin and still used by 21.3
     *             Outlook Plugin to remain compatible with 21.2 Web Central.
     */
    @Deprecated
    @WebMethod(action = "getRoomReservationsByUniqueId")
    List<RoomReservation> getRoomReservationsByUniqueId(String uniqueId) throws ExceptionBase;

    /**
     * Gets the room reservations by unique id or conference id.
     *
     * @param uniqueId the unique id
     * @param conferenceId the conference id (may be null)
     * @return the room reservations by unique id
     *
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "getRoomReservations")
    List<RoomReservation> getRoomReservations(String uniqueId, Integer conferenceId)
            throws ExceptionBase;

    /**
     * Gets the room arrangement details for a list of room arrangements.
     *
     * @param roomArrangements list of room arrangement primary key values
     *
     * @return the full room arrangement details
     *
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "getRoomArrangementDetails")
    List<RoomArrangement> getRoomArrangementDetails(List<RoomArrangement> roomArrangements)
            throws ExceptionBase;

    /**
     * Gets the value of a reservations activity parameter or property.
     *
     * @param id activity parameter or property identifier
     * @return value of the activity parameter or property
     *
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "getActivityParameter")
    String getActivityParameter(String id) throws ExceptionBase;

    /**
     * Gets the values of reservations activity parameters and/or properties.
     *
     * @param ids activity parameters and/or property identifiers
     * @return values of the activity parameters/properties in the same order (null for unknowns)
     *
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "getActivityParameters")
    List<String> getActivityParameters(List<String> ids) throws ExceptionBase;

    /**
     * Save recurring room reservation.
     *
     * @param reservation the reservation
     * @param recurrence the recurrence
     * @return the list
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "saveRecurringRoomReservation")
    List<RoomReservation> saveRecurringRoomReservation(RoomReservation reservation,
            Recurrence recurrence) throws ExceptionBase;

    /**
     * Start an asynchronous reservation request.
     *
     * @param request the request
     * @return job identifier
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "startAsyncRequest")
    String startAsyncRequest(ReservationRequest request) throws ExceptionBase;

    /**
     * Get the result of an asynchronous reservation request.
     *
     * @param jobId job identifier
     * @return result of the request
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "getAsyncResult")
    ReservationResult getAsyncResult(String jobId) throws ExceptionBase;

    /**
     * Save room reservation.
     *
     * @param reservation the reservation
     * @return the room reservation
     *
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "saveRoomReservation")
    RoomReservation saveRoomReservation(RoomReservation reservation) throws ExceptionBase;

    /**
     * Save a recurring or non-recurring conference call reservation.
     *
     * @param reservations reservations for a single date (existing or new)
     * @param recurrence recurrence pattern (may be null for a regular reservation)
     * @param disconnectOnError whether reservations that should be cancelled may be disconnected
     *            instead if canceling is not allowed
     * @return contains the list of all saved reservations
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "saveConferenceCall")
    SavedConferenceCall saveConferenceCall(List<RoomReservation> reservations,
            Recurrence recurrence, boolean disconnectOnError) throws ExceptionBase;

    /**
     * Verify whether all reservations linked to an ID match a given recurrence pattern.
     *
     * @param uniqueId the unique id of the appointment series
     * @param recurrence the recurrence
     * @param startTime time of day that the appointments start
     * @param endTime time of day that the appointments end
     * @param timeZone the time zone
     * @return true if it matches, false if at least one reservation is different or missing
     *
     * @throws ExceptionBase ExceptionBase
     */
    @WebMethod(action = "verifyRecurrencePattern")
    boolean verifyRecurrencePattern(String uniqueId, Recurrence recurrence, Time startTime,
            Time endTime, String timeZone) throws ExceptionBase;

}
