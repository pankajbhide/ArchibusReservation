package com.archibus.app.reservation.exchange.util;

import org.apache.log4j.Logger;

/**
 * Can log message traces for communications between Exchange and Web Central. The trace messages
 * are logged at TRACE level, which can be enabled for the com.archibus.app.reservation.exchange
 * package (or specifically for this class) in logging.xml.
 * <p>
 * Used by Exchange Service Helper to configure tracing. Managed by Spring, has prototype scope.
 * Configured in exchange-integration-context.xml file.
 *
 * @author Yorik Gerlo
 * @since 22.1
 */
public class ExchangeTraceListener implements IExchangeTraceListener {

    /** The logger that writes the trace messages. */
    private final Logger logger = Logger.getLogger(this.getClass());

    /** {@inheritDoc} */
    @Override
    public void trace(final String traceType, final String traceMessage) {
        // we can ignore the traceType since it's included in the traceMessage as well
        this.logger.trace(traceMessage);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isTraceEnabled() {
        return this.logger.isTraceEnabled();
    }
}
