package com.archibus.app.reservation.exchange.service;

import java.net.URI;

import com.archibus.app.reservation.exchange.domain.*;
import com.archibus.utility.StringUtil;

import microsoft.exchange.webservices.data.*;

/**
 * Subclass of the standard Exchange Service helper which adds auto-discover support.
 *
 * @author Yorik Gerlo
 * @since 21.3
 */
class AutodiscoverExchangeServiceHelper extends ExchangeServiceHelper
        implements IAutodiscoverRedirectionUrl {

    /** Closing bracket used in log messages. */
    private static final String CLOSING_BRACKET = "]";

    /** Error message indicating auto-discover failed. */
    // @translatable
    private static final String AUTO_DISCOVER_FAILED =
            "Auto-discover failed for [{0}]. Please refer to archibus.log for details";

    /** The number of milliseconds after which an auto-discover result is considered expired. */
    private static final long EXPIRE_MILLIS = 24 * 60 * 60 * 1000;

    /** Timeout for auto-discover requests in milliseconds. */
    private static final int AUTO_DISCOVER_TIMEOUT = 5 * 1000;

    /** The auto-discover cache. */
    private AutodiscoverCachingService autodiscoverCache;

    /**
     * Get an Exchange service instance for accessing the given mailbox.
     *
     * @param email the mailbox to access
     * @return the exchange service
     */
    @Override
    protected ExchangeService initializeServiceForLinkedMailbox(final String email) {
        ExchangeService service = null;

        if (StringUtil.isNullOrEmpty(this.getUrl())) {
            service = this.intializeByAutodiscover(email);
        } else {
            service = super.initializeServiceForLinkedMailbox(email);
        }
        return service;
    }

    /**
     * Initialize the exchange service using auto-discover.
     *
     * @param email the email address for the mailbox to connect with
     * @return the exchange service
     */
    private ExchangeService intializeByAutodiscover(final String email) {
        ExchangeService exchangeService = super.getServiceForLinkedMailbox(email);
        URI url = this.getAutodiscoveredUrl(email);
        if (url == null) {
            if (StringUtil.isNullOrEmpty(this.getOrganizerAccount())
                    || email.equals(this.getOrganizerAccount())) {
                throw new AutodiscoverException(AUTO_DISCOVER_FAILED,
                    AutodiscoverExchangeServiceHelper.class, this.getAdminService(), email);
            } else {
                exchangeService = super.getServiceForLinkedMailbox(this.getOrganizerAccount());
                url = this.getAutodiscoveredUrl(this.getOrganizerAccount());
                exchangeService.setUrl(url);
            }
        } else {
            exchangeService.setUrl(url);
            final AutodiscoverResult cachedResult = this.autodiscoverCache.get(email);
            // avoid verifying twice in succession
            if (!cachedResult.isVerified()) {
                exchangeService = this.verifyEndpoint(exchangeService);
                // verification was successful if an exchange service is returned for the same
                // mailbox
                cachedResult
                    .setVerified(email.equals(exchangeService.getImpersonatedUserId().getId()));
            }
        }
        return exchangeService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ExchangeService getServiceForLinkedMailbox(final String email) {
        ExchangeService exchangeService = null;

        if (StringUtil.isNullOrEmpty(this.getUrl())) {
            exchangeService = this.getServiceByAutodiscover(email);
        } else {
            // do not apply autodiscover
            exchangeService = super.getServiceForLinkedMailbox(email);
        }

        return exchangeService;
    }

    /**
     * Get a service reference using auto-discover to determine the end point.
     *
     * @param email the mailbox to connect to
     * @return the service reference
     */
    private ExchangeService getServiceByAutodiscover(final String email) {
        final ExchangeService exchangeService = super.getServiceForLinkedMailbox(email);
        final URI url = this.getAutodiscoveredUrl(email);
        if (url == null) {
            throw new AutodiscoverException(AUTO_DISCOVER_FAILED,
                AutodiscoverExchangeServiceHelper.class, this.getAdminService(), email);
        }
        exchangeService.setUrl(url);
        return exchangeService;
    }

    /**
     * Set the auto-discover cache.
     *
     * @param autodiscoverCache the autodiscoverCache to set
     */
    public void setAutodiscoverCache(final AutodiscoverCachingService autodiscoverCache) {
        this.autodiscoverCache = autodiscoverCache;
    }

    /**
     * Get the correct URL for connecting to this mailbox, either from cache or from a new
     * auto-discover operation. If an URL is cached, test it and start a new autodiscover operation
     * if the connection to the cached URL fails.
     *
     * @param email the mailbox to get the URL for
     * @return the url, or null if no endpoint is found
     */
    private URI getAutodiscoveredUrl(final String email) {
        URI url = null;
        // Check autodiscover cache.
        final AutodiscoverResult cachedResult = this.autodiscoverCache.get(email);
        try {
            if (cachedResult == null || System.currentTimeMillis()
                    - cachedResult.getDateFound().getTime() >= EXPIRE_MILLIS) {
                // 1. No cached URL found. Run auto-discover now, throw errors, cache on success.
                // 2. Cached is URL expired. Run auto-discover, on error try old value, cache on
                // success.
                url = this.runAutodiscover(email);
                this.autodiscoverCache.put(email, url);
            } else if (cachedResult.getUrl() == null) {
                // 3. Cache is valid but it indicates to fall back to the organizer account.
                // Returning null ensures the organizer account is used.
                this.logger.debug("Autodiscover cache indicates no endpoint available for [" + email
                        + "]. Fall back to organizer account");
            } else {
                // 4. Cache is valid and indicates an URL to connect to. Try this URL via EWS.
                url = verifyCachedResult(email, cachedResult);
            }
        } catch (final AutodiscoverException exception) {
            // Fall back to the cached URL for this mailbox. Note the cached URL could be null
            // if we must use the organizer account for this user.
            if (cachedResult == null) {
                this.logger.warn("Auto-discover failed for [" + email + CLOSING_BRACKET, exception);
            } else {
                url = cachedResult.getUrl();
                this.logger.warn("Update of auto-discover URL failed for [" + email
                        + "]. Using old cached URL [" + url + CLOSING_BRACKET,
                    exception);
            }
            this.autodiscoverCache.put(email, url);
        }
        return url;
    }

    /**
     * Verify whether connecting to a cached URL works. If it fails, run auto-discover again to
     * detect a mailbox move.
     *
     * @param email the email address
     * @param cachedResult the cached result
     * @return the URL to connect to
     */
    private URI verifyCachedResult(final String email, final AutodiscoverResult cachedResult) {
        URI url = null;
        try {
            final ExchangeService service = super.getServiceForLinkedMailbox(email);
            service.setUrl(cachedResult.getUrl());

            Folder.bind(service, WellKnownFolderName.Calendar, PropertySet.IdOnly);

            url = cachedResult.getUrl();
            cachedResult.setVerified(true);
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party
            // API method throws a checked Exception, which needs to be wrapped in
            // ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            this.logger.warn("Error testing cached URL [" + cachedResult.getUrl()
                    + "]. Restarting autodiscover for [" + email + CLOSING_BRACKET,
                exception);
            // 5. EWS test failed, retry perform auto-discover now to find a new URL.
            // 5.a> If a new URL is found, update the cache and try that one.
            // 5.b> if the same URL is found or autodiscover fails, the old URL will be tried again
            // in
            // initializeService, possibly falling back to the organizer account
            url = this.runAutodiscover(email);
            if (cachedResult.getUrl().equals(url) && cachedResult.isVerified()) {
                this.logger.warn(
                    "Could not connect to Exchange using cached URL [" + cachedResult.getUrl()
                            + "] for [" + email
                            + "] although auto-discover confirms this is the correct URL. "
                            + "Switching to the organizer account if the next check fails",
                    exception);
            }
            // Insert the discovered URL in the cache, replacing the old value. Rely on the usual
            // check in initializeService.
            this.autodiscoverCache.put(email, url);
        }
        return url;
    }

    /**
     * Auto-discover the EWS endpoint for the given email.
     *
     * @param email the email to get an endpoint for
     * @return the endpoint URI
     * @throws AutodiscoverException if an error occurred during auto-discover
     */
    private URI runAutodiscover(final String email) throws AutodiscoverException {
        final ExchangeService exchangeService = super.getService();
        exchangeService.setTimeout(AUTO_DISCOVER_TIMEOUT);
        try {
            exchangeService.autodiscoverUrl(email, this);
            return exchangeService.getUrl();
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party
            // API method throws a checked Exception, which needs to be wrapped in
            // ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            throw new AutodiscoverException(AUTO_DISCOVER_FAILED, exception,
                AutodiscoverExchangeServiceHelper.class, this.getAdminService(), email);
        }
    }

    /**
     * Validates an autodiscover redirect URL. All redirects to https URLs are accepted.
     * {@inheritDoc}
     */
    @Override
    public boolean autodiscoverRedirectionUrlValidationCallback(final String redirectionUrl)
            throws AutodiscoverLocalException {
        // allow all redirections using https
        final boolean redirectOk = redirectionUrl != null && redirectionUrl.startsWith("https://");
        if (redirectOk) {
            this.logger.debug("Accepting auto-discover redirect to " + redirectionUrl);
        } else {
            this.logger.warn("Refusing auto-discover redirect to " + redirectionUrl);
        }
        return redirectOk;
    }

}
