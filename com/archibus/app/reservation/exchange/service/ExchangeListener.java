package com.archibus.app.reservation.exchange.service;

import org.apache.log4j.Logger;

import com.archibus.app.reservation.domain.CalendarException;
import com.archibus.utility.ExceptionBase;

import microsoft.exchange.webservices.data.*;

/**
 * Exchange Listener service that receives events from Exchange when enabled.
 *
 * Managed via Spring.
 *
 * @author Yorik Gerlo
 * @since 21.2
 */
public class ExchangeListener {

    /** Number of milliseconds to wait before retrying if the subscription cannot be renewed. */
    private static final int RETRY_MILLIS = 60 * 1000 * 5;

    /** This helper provides the connection with Exchange. */
    private ExchangeServiceHelper serviceHelper;

    /** The item handler that process items received from Exchange. */
    private ItemHandler itemHandler;

    /** Indicates whether the listener should be enabled after initialization. */
    private boolean enableListener;

    /**
     * The signalling object used to wake up the WFR service when an event is received from
     * Exchange.
     */
    private final Object signal = new Object();

    /**
     * This value is set to true when an event is received from Exchange. The WFR service can check
     * this value to verify whether it missed a signal before waiting for the next signal.
     */
    private boolean wasSignalled;

    /** Indicates whether the listener should stop. */
    private boolean stopRequested;

    /** The logger. */
    private final Logger logger = Logger.getLogger(this.getClass());

    /**
     * First handle all pending events, then start the Exchange streaming notification listener.
     */
    public void run() {
        if (this.enableListener && !this.stopRequested) {
            this.logger.info("Starting Exchange listener");
            this.correctExchangeVersion();

            // Start a streaming subscription.
            this.reStartListener();

            while (!this.stopRequested) {
                // Read the inbox again to handle intermediate arrivals.
                try {
                    final ExchangeService exchangeService = this.serviceHelper
                        .initializeService(this.serviceHelper.getResourceAccount());
                    for (final String resourceFolder: this.serviceHelper.getResourceFolders()){
                        final WellKnownFolderName wellKnownFolderName = this.serviceHelper.getWellKnownFolderName(resourceFolder);
                        processResourceFolder(exchangeService, wellKnownFolderName);
                    }
                } catch (final ExceptionBase exception) {
                    this.logger.warn("Processing inbox items failed. Waiting for next signal.",
                        exception);
                }

                // Now wait for a signal from the notification handler before checking again.
                waitForSignal();
            }
        }
    }

    /**
     * Start a new subscription.
     */
    void reStartListener() {
        boolean started = false;
        do {
            try {
                if (this.enableListener && !this.stopRequested) {
                    final ExchangeService exchangeService = this.serviceHelper
                        .initializeService(this.serviceHelper.getResourceAccount());
                    new StreamingNotificationHandler(this, exchangeService);
                    signalEventReceived();
                }
                started = true;
            } catch (final CalendarException exception) {
                this.logger.warn("Could not (re)start the listener. Try again in 5 minutes",
                    exception);
                try {
                    Thread.sleep(RETRY_MILLIS);
                } catch (final InterruptedException interrupt) {
                    this.logger.warn("Interrupted while waiting to (re)start listener", interrupt);
                }
            }
        } while (!started);
    }

    /**
     * Request to stop the listener.
     */
    public void requestStop() {
        synchronized (this.signal) {
            this.stopRequested = true;
            this.signal.notifyAll();
        }
    }

    /**
     * Signal that an event was received from Exchange.
     */
    void signalEventReceived() {
        synchronized (this.signal) {
            this.wasSignalled = true;
            this.signal.notifyAll();
        }
    }

    /**
     * Wait until a signal is received or until the current thread is interrupted.
     */
    private void waitForSignal() {
        try {
            synchronized (this.signal) {
                while (!(this.wasSignalled || this.stopRequested)) {
                    this.signal.wait();
                }
                this.wasSignalled = false;
            }
        } catch (final InterruptedException exception) {
            // We can safely ignore this exception and pretend we were woken up
            // as usual. The interruption can also occur because the Job is
            // being terminated. In that case no attempt will be made to process
            // any events.
            this.logger.debug("waitForSignal was interrupted", exception);
        }
    }

    /**
     * Set whether the listener should be enabled.
     *
     * @param enableListener the enableListener to set
     */
    public void setEnableListener(final boolean enableListener) {
        this.enableListener = enableListener;
    }

    /**
     * Set the new item handler.
     *
     * @param itemHandler the new item handler
     */
    public void setItemHandler(final ItemHandler itemHandler) {
        this.itemHandler = itemHandler;
    }

    /**
     * Set the new Exchange service helper.
     *
     * @param serviceHelper the serviceHelper to set
     */
    public void setServiceHelper(final ExchangeServiceHelper serviceHelper) {
        this.serviceHelper = serviceHelper;
    }

    /**
     * Get the Exchange service helper.
     *
     * @return the current Exchange service helper
     */
    public ExchangeServiceHelper getServiceHelper() {
        return this.serviceHelper;
    }

    /**
     * Process all items in the inbox. Those items should be meeting invitations or cancellations;
     * other types of items are ignored.
     *
     * @param exchangeService the service connected to Exchange
     * @param folderName folder name
     */
    void processResourceFolder(final ExchangeService exchangeService, final WellKnownFolderName folderName) {
        Integer offset = 0;
        try {
            do {
                final ItemView itemView = new ItemView(512, offset);
                itemView.getOrderBy().add(EmailMessageSchema.DateTimeReceived,
                    SortDirection.Ascending);
                itemView.setPropertySet(PropertySet.IdOnly);
                final FindItemsResults<Item> results =
                        exchangeService.findItems(folderName, itemView);
                this.logger.debug("Processing " + results.getTotalCount()
                        + " items. Next offset is " + results.getNextPageOffset());
                for (final Item item : results.getItems()) {
                    // If an error occurs handling an individual item, then the Job should ignore
                    // this item and continue.
                    try {
                        // The itemHandler is wrapped in a proxy for transaction management via
                        // Spring.
                        this.itemHandler.handleItem(item);
                    } catch (final ExceptionBase exception) {
                        // Rollback occurs in the interceptors of ItemHanderlImpl.
                        this.logger.warn("Error handling Exchange Item.", exception);
                    }
                }
                offset = results.getNextPageOffset();
            } while (offset != null);
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API
            // method throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            throw new CalendarException("Error processing inbox items.", exception,
                ExchangeListener.class, this.serviceHelper.getAdminService());
        }
    }

    /**
     * Check whether the current Exchange API version is sufficient for running the Exchange
     * listener. If not, modify it so the listener can start. The server will report an error if it
     * cannot process the request.
     */
    void correctExchangeVersion() {
        if (ExchangeVersion.valueOf(this.serviceHelper.getVersion())
            .ordinal() < ExchangeVersion.Exchange2010_SP1.ordinal()) {
            this.logger
                .debug("Changing Exchange API version from [" + this.serviceHelper.getVersion()
                        + "] to Exchange2010_SP1 for Exchange Listener. Requires ["
                        + this.serviceHelper.getResourceAccount()
                        + "] to be located on Exchange 2010 SP1 or later");
            this.serviceHelper.setVersion(ExchangeVersion.Exchange2010_SP1.toString());
        }
    }

}
