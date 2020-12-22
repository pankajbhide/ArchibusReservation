package com.archibus.app.reservation.exchange.service;

import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.archibus.app.reservation.domain.CalendarException;
import com.archibus.app.reservation.util.ReservationsContextHelper;
import com.archibus.service.remoting.AdminService;

import microsoft.exchange.webservices.data.*;

/**
 * Can handle items from the Reservations Mailbox on Exchange, to process meeting changes made via
 * Exchange. Managed by Spring.
 *
 * @author Yorik Gerlo
 * @since 21.2
 */
public class ItemHandlerImpl implements ItemHandler {

    /** Error message that indicates something went wrong handling an item. */
    private static final String ITEM_ERROR = "Error handling Exchange item";

    /** The meeting request handler. */
    private MeetingRequestHandler meetingRequestHandler;

    /** The meeting cancellation handler. */
    private MeetingCancellationHandler meetingCancellationHandler;

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleItem(final Item item) {
        if (item instanceof MeetingRequest) {
            this.meetingRequestHandler.handleMeetingRequest((MeetingRequest) item);
        } else if (item instanceof MeetingCancellation) {
            this.meetingCancellationHandler.handleMeetingCancellation((MeetingCancellation) item);
        } else {
            // Delete all other items.
            try {
                item.delete(DeleteMode.HardDelete);
                // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API
                // method throws a checked Exception, which needs to be wrapped in ExceptionBase.
            } catch (final Exception exception) {
                // CHECKSTYLE:ON
                throw new CalendarException("Unable to delete item.", exception,
                    ItemHandlerImpl.class, this.meetingRequestHandler.messagesService.getAdminService());
            }
        }
    }

    /**
     * Set the new meeting request handler.
     *
     * @param meetingRequestHandler the meeting request handler to set
     */
    public void setMeetingRequestHandler(final MeetingRequestHandler meetingRequestHandler) {
        this.meetingRequestHandler = meetingRequestHandler;
    }

    /**
     * Set the new meeting cancellation handler.
     *
     * @param meetingCancellationHandler the meeting cancellation handler to set
     */
    public void setMeetingCancellationHandler(
            final MeetingCancellationHandler meetingCancellationHandler) {
        this.meetingCancellationHandler = meetingCancellationHandler;
    }

    /**
     * Set the user based on the given email. If not found, delete the message and throw exception.
     *
     * @param organizerEmail the email of the user to set
     * @param item the item to delete when the user is not found
     * @param adminService the service for localization
     */
    protected static void setUserFromEmail(final String organizerEmail, final Item item,
            final AdminService adminService) {
        try {
            ReservationsContextHelper.setUserFromEmail(organizerEmail);
        } catch (final UsernameNotFoundException exception) {
            // If the requestor isn't a WebCentral user, ignore his messages.
            try {
                item.delete(DeleteMode.HardDelete);
                // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API
                // method throws a checked Exception, which needs to be wrapped in ExceptionBase.
            } catch (final Exception deleteException) {
                // CHECKSTYLE:ON
                throw new CalendarException(ITEM_ERROR, deleteException, ItemHandlerImpl.class,
                        adminService);
            }
            throw new UsernameNotFoundException(
                "Ignored incoming message - sender is not recognized", exception);
        }
    }

}
