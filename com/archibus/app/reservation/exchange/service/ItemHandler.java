package com.archibus.app.reservation.exchange.service;

import microsoft.exchange.webservices.data.Item;

/**
 * Can handle items from the Reservations Mailbox on Exchange, to process meeting changes made via Exchange.
 * Managed by Spring.
 *
 * @author Yorik Gerlo
 * @since 21.2
 */
public interface ItemHandler {
    
    /**
     * Handle an item in the resource account inbox.
     * 
     * @param item the item to handle
     */
    void handleItem(final Item item);
    
}
