package com.archibus.app.reservation.exchange.util;

import microsoft.exchange.webservices.data.ITraceListener;

/**
 * Interface for the trace listener.
 *
 * @author Yorik Gerlo
 * @since 22.1
 */
public interface IExchangeTraceListener extends ITraceListener {

    /**
     * Check whether tracing is enabled for this logger.
     *
     * @return true if tracing is enabled, false otherwise
     */
    boolean isTraceEnabled();

}