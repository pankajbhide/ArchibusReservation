package com.archibus.app.reservation.exchange.service;

import java.util.*;

import org.apache.log4j.Logger;

import com.archibus.app.reservation.domain.CalendarException;

import microsoft.exchange.webservices.data.*;
import microsoft.exchange.webservices.data.StreamingSubscriptionConnection.*;

/**
 * A notification handler instance receives notifications from Exchange 2010 or later.
 */
class StreamingNotificationHandler
        implements INotificationEventDelegate, ISubscriptionErrorDelegate {

    /**
     * Duration of the subscription in minutes. After this time the error delegate will be called,
     * from which the subscription is restarted.
     */
    private static final int SUBSCRIPTION_DURATION = 30;

    /** The Exchange listener Spring bean. */
    private final ExchangeListener exchangeListener;

    /** The connection that runs the subscription. */
    private final StreamingSubscriptionConnection connection;

    /** The subscription being handled by this instance. */
    private final StreamingSubscription subscription;

    /** The logger. */
    private final Logger logger = Logger.getLogger(this.getClass());

    /**
     * Create a new notification handler to receive notifications from Exchange.
     *
     * @param exchangeService the Exchange Service that manages the streaming connection
     * @param exchangeListener the listener that needs to be notified when a notification arrives
     *            from Exchange
     */
    public StreamingNotificationHandler(final ExchangeListener exchangeListener,
            final ExchangeService exchangeService) {
        this.exchangeListener = exchangeListener;

        try {
            this.connection =
                    new StreamingSubscriptionConnection(exchangeService, SUBSCRIPTION_DURATION);
            this.connection.addOnNotificationEvent(this);
            this.connection.addOnSubscriptionError(this);
            this.connection.addOnDisconnect(this);

            final List<FolderId> folders = new ArrayList<FolderId>();
            final String[] resourceFolders = this.exchangeListener.getServiceHelper().getResourceFolders();
            for (final String resourceFolder: resourceFolders) {
                folders.add(new FolderId(this.exchangeListener.getServiceHelper().getWellKnownFolderName(resourceFolder)));
            }
            this.subscription =
                    exchangeService.subscribeToStreamingNotifications(folders, EventType.NewMail);
            this.connection.addSubscription(this.subscription);
            this.connection.open();
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API
            // method throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            throw new CalendarException("Unable to start the Exchange listener.", exception,
                StreamingNotificationHandler.class,
                this.exchangeListener.getServiceHelper().getAdminService());
        }
    }

    /**
     * Stop receiving events and close the streaming connection.
     */
    public void stopStreaming() {
        try {
            if (this.connection.getIsOpen()) {
                this.subscription.unsubscribe();
            } else {
                this.logger.debug("Connection was already closed.");
            }
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API
            // method throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            throw new CalendarException("Error stopping the Exchange listener.", exception,
                StreamingNotificationHandler.class,
                this.exchangeListener.getServiceHelper().getAdminService());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notificationEventDelegate(final Object sender, final NotificationEventArgs events) {
        /*
         * Since we do not use any of the specific event info, only forward the signal once if any
         * number of item events is received.
         */
        for (final NotificationEvent notificationEvent : events.getEvents()) {
            if (notificationEvent instanceof ItemEvent) {
                this.logger.debug(" - " + notificationEvent.getEventType().toString());
                this.exchangeListener.signalEventReceived();
                break;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void subscriptionErrorDelegate(final Object sender,
            final SubscriptionErrorEventArgs args) {
        // When an error occurs, stop receiving events, then start a new subscription.
        this.connection.removeNotificationEvent(this);
        this.exchangeListener.reStartListener();
    }
    

}