package com.archibus.app.reservation.exchange.service;

import java.net.*;
import java.util.EnumSet;

import org.apache.log4j.Logger;

import com.archibus.app.reservation.domain.CalendarException;
import com.archibus.app.reservation.exchange.util.IExchangeTraceListener;
import com.archibus.app.reservation.util.*;
import com.archibus.utility.StringUtil;

import microsoft.exchange.webservices.data.*;

/**
 * Utility Class for Exchange Service configuration.
 *
 * Managed in Spring.
 *
 * @author Bart Vanderschoot
 * @since 21.2
 */
public class ExchangeServiceHelper extends AdminServiceContainer implements ICalendarSettings {

    /** Error message indicating the connect failed. */
    // @translatable
    protected static final String CONNECT_FAILED =
            "Could not connect to Exchange. Please refer to archibus.log for details";

    /** Error message indicating connecting to Exchange is not enabled for the given email. */
    // @translatable
    protected static final String NO_EXCHANGE_EMAIL =
            "The application is not configured to connect to Exchange for email [{0}]";

    /** Error message indicating the connect failed because the URL is invalid. */
    // @translatable
    private static final String INVALID_EXCHANGE_URL = "Invalid Exchange URL [{0}]";

    /** Error message indicating the connect failed because the URL is invalid. */
    // @translatable
    private static final String INVALID_EXCHANGE_SERVICE = "Invalid Exchange Service";

    /** The logger. */
    protected final Logger logger = Logger.getLogger(this.getClass());

    /** The url. */
    private String url;

    /** The user name. */
    private String userName;

    /** The password. */
    private String password;

    /** the network domain. */
    private String domain;

    /** The proxy server. */
    private String proxyServer;

    /** The proxy port. */
    private Integer proxyPort;

    /** The version of Exchange. */
    private String version;

    /** The resource mailbox. */
    private String resourceAccount;

    /** The resource mailbox folders. */
    private String[] resourceFolders;

    /** The organizer mailbox used to create meetings for non-Exchange users. */
    private String organizerAccount;

    /** The domains known on the connected Exchange Server(s). */
    private String[] linkedDomains;

    /** The listener for Exchange tracing. */
    private IExchangeTraceListener traceListener;

    /**
     * Get an Exchange service instance for accessing the given mailbox.
     *
     * @param email the mailbox to access
     * @return the exchange service
     */
    public final ExchangeService initializeService(final String email) {
        ExchangeService exchangeService = null;
        if (this.isLinkedDomain(email)) {
            exchangeService = initializeServiceForLinkedMailbox(email);
        } else if (StringUtil.isNullOrEmpty(this.getOrganizerAccount())) {
            throw new CalendarException(NO_EXCHANGE_EMAIL, ExchangeServiceHelper.class,
                this.getAdminService(), email);
        } else {
            this.logger.debug("Exchange connection is not enabled for [" + email
                    + "]. Fall back to organizer account.");
            // No verification required for organizerAccount. Assume it's OK if specified.
            exchangeService = this.getService(this.getOrganizerAccount());
        }
        return exchangeService;
    }

    /**
     * Get an Exchange service instance for accessing the given mailbox, which according to
     * configuration is located on a connected Exchange server.
     *
     * @param email the email address
     * @return the Exchange Service
     */
    protected ExchangeService initializeServiceForLinkedMailbox(final String email) {
        return verifyEndpoint(getServiceForLinkedMailbox(email));
    }

    /**
     * Verify whether the Exchange Web Service endpoint accepts queries for the given mailbox.
     *
     * @param exchangeService the configured service to verify
     * @return the verified service, or a fallback service if verification failed
     */
    protected ExchangeService verifyEndpoint(final ExchangeService exchangeService) {
        ExchangeService verifiedService = exchangeService;
        try {
            Folder.bind(verifiedService, WellKnownFolderName.Calendar, PropertySet.IdOnly);
        } catch (final ServiceResponseException exception) {
            /*
             * Check whether the cause is a non-existent mailbox. In that case switch to the
             * organizer mailbox for non-Exchange users (if this mailbox is defined).
             */
            if (ServiceError.ErrorNonExistentMailbox.equals(exception.getErrorCode())) {
                if (StringUtil.isNullOrEmpty(this.getOrganizerAccount())) {
                    // No organizer account is defined, so report the error.
                    // @translatable
                    throw new CalendarException(
                        "Requestor [{0}] does not have a valid mailbox on Exchange Server [{1}]",
                        exception, ExchangeServiceHelper.class, this.getAdminService(),
                        verifiedService.getImpersonatedUserId().getId(), exchangeService.getUrl());
                } else {
                    // Use the organizer account for connecting to Exchange.
                    verifiedService = this.getService(this.getOrganizerAccount());
                }
            } else {
                throw new CalendarException(CONNECT_FAILED, exception, ExchangeServiceHelper.class,
                    this.getAdminService());
            }
            // CHECKSTYLE:OFF : Suppress IllegalCatch warning. Justification: third-party API
            // method throws a checked Exception, which needs to be wrapped in ExceptionBase.
        } catch (final Exception exception) {
            // CHECKSTYLE:ON
            throw new CalendarException(CONNECT_FAILED, exception, ExchangeServiceHelper.class,
                this.getAdminService());
        }
        return verifiedService;
    }

    /**
     * Create a new EWS Exchange Service for the impersonated user.
     *
     * @param impersonatedUserEmail the email address that should be impersonated
     * @return service EWS Exchange Service
     */
    public final ExchangeService getService(final String impersonatedUserEmail) {
        if (this.isLinkedDomain(impersonatedUserEmail)) {
            return getServiceForLinkedMailbox(impersonatedUserEmail);
        } else {
            throw new CalendarException(NO_EXCHANGE_EMAIL, ExchangeServiceHelper.class,
                this.getAdminService(), impersonatedUserEmail);
        }
    }

    /**
     * Get an Exchange service for a mailbox which according to configuration is on a connected
     * Exchange Server.
     *
     * @param email the email address
     * @return the Exchange service
     */
    protected ExchangeService getServiceForLinkedMailbox(final String email) {
        final ExchangeService exchangeService = getService();
        exchangeService
            .setImpersonatedUserId(new ImpersonatedUserId(ConnectingIdType.SmtpAddress, email));

        // KB 3049661
        exchangeService.getHttpHeaders().put("X-AnchorMailbox", email);

        return exchangeService;
    }

    /**
     * Create instance of the Exchange Service without impersonation and without setting the URL.
     *
     * @return service EWS Exchange Service
     */
    protected ExchangeService getService() {
        ReservationsContextHelper.checkExchangeLicense();

        final ExchangeVersion exchangeVersion;
        if (this.version == null) {
            // default value
            exchangeVersion = ExchangeVersion.Exchange2010_SP1;
        } else {
            exchangeVersion = ExchangeVersion.valueOf(this.version);
        }

        ExchangeService exchangeService = null;
        try {
            exchangeService = new ExchangeService(exchangeVersion);
        } catch (final Exception exception) {
            throw new CalendarException(INVALID_EXCHANGE_SERVICE, ExchangeServiceHelper.class,
                this.getAdminService(), exchangeVersion.toString());
        }
        // Only try to set the URL if it was specified.
        if (StringUtil.notNullOrEmpty(this.getUrl())) {
            try {
                exchangeService.setUrl(new URI(this.getUrl()));
            } catch (final URISyntaxException exception) {
                throw new CalendarException(INVALID_EXCHANGE_URL, exception,
                    ExchangeServiceHelper.class, this.getAdminService(), this.getUrl());
            }
        }
        // TODO optional: set user agent
        // exchangeService.setUserAgent("ARCHIBUS");

        // TODO: use encryption for password
        // final Decoder1 decoder = new Decoder1();
        // final String passwordDecrypted = decoder.decode(this.password);

        if (StringUtil.notNullOrEmpty(this.domain)) {
            exchangeService
                .setCredentials(new WebCredentials(this.userName, this.password, this.domain));
        } else {
            exchangeService.setCredentials(new WebCredentials(this.userName, this.password));
        }

        if (StringUtil.notNullOrEmpty(this.proxyServer) && this.proxyPort != 0) {
            final WebProxy proxy = new WebProxy(this.proxyServer, this.proxyPort);
            proxy.setCredentials(this.userName, this.password, this.domain);
            exchangeService.setWebProxy(proxy);
        }

        if (this.traceListener != null && this.traceListener.isTraceEnabled()) {
            exchangeService.setTraceFlags(EnumSet.of(TraceFlags.EwsRequest, TraceFlags.EwsResponse,
                TraceFlags.DebugMessage, TraceFlags.EwsRequestHttpHeaders,
                TraceFlags.EwsResponseHttpHeaders, TraceFlags.AutodiscoverConfiguration,
                TraceFlags.AutodiscoverRequest, TraceFlags.AutodiscoverRequestHttpHeaders,
                TraceFlags.AutodiscoverResponse, TraceFlags.AutodiscoverResponseHttpHeaders));
            exchangeService.setTraceListener(this.traceListener);
            exchangeService.setTraceEnabled(true);
        }

        return exchangeService;
    }

    /**
     * Getter for the Exchange URL property.
     *
     * @return the Exchange URL property.
     */
    public String getUrl() {
        return this.url;
    }

    /**
     * Setter for the Exchange URL property.
     *
     * @param url the URL to set
     */
    public void setUrl(final String url) {
        this.url = url;
    }

    /**
     * Get the Exchange user name property.
     *
     * @return the userName property.
     */
    public String getUserName() {
        return this.userName;
    }

    /**
     * Setter for the userName property.
     *
     * @param userName the userName to set
     */
    public void setUserName(final String userName) {
        this.userName = userName;
    }

    /**
     * Get the Exchange password property.
     *
     * @return the password property.
     */
    public String getPassword() {
        return this.password;
    }

    /**
     * Setter for the password property.
     *
     * @param password the password to set
     */
    public void setPassword(final String password) {
        this.password = password;
    }

    /**
     * Getter for the domain property.
     *
     * @return the domain property.
     */
    public String getDomain() {
        return this.domain;
    }

    /**
     * Setter for the domain property.
     *
     * @param domain the domain to set
     */
    public void setDomain(final String domain) {
        this.domain = domain;
    }

    /**
     * Getter for the proxyServer property.
     *
     * @return the proxyServer property.
     */
    public String getProxyServer() {
        return this.proxyServer;
    }

    /**
     * Setter for the proxyServer property.
     *
     * @param proxyServer the proxyServer to set
     */
    public void setProxyServer(final String proxyServer) {
        this.proxyServer = proxyServer;
    }

    /**
     * Getter for the proxyPort property.
     *
     * @return the proxyPort property.
     */
    public Integer getProxyPort() {
        return this.proxyPort;
    }

    /**
     * Setter for the proxyPort property.
     *
     * @param proxyPort the proxyPort to set
     */
    public void setProxyPort(final Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    /**
     * Getter for the version property.
     *
     * @return the version property.
     */
    public String getVersion() {
        return this.version;
    }

    /**
     * Setter for the version property.
     *
     * @param version the version to set
     */
    public void setVersion(final String version) {
        this.version = version;
    }

    /**
     * Get the email address of the resource account.
     *
     * @return resource account email address
     */
    @Override
    public String getResourceAccount() {
        return this.resourceAccount;
    }

    /**
     * Set the resource account email address.
     *
     * @param resourceAccount the new resource account email address
     */
    public void setResourceAccount(final String resourceAccount) {
        this.resourceAccount = resourceAccount;
    }

    /**
     * Get the email address of the organizer account.
     *
     * @return organizer account email address
     */
    public String getOrganizerAccount() {
        return this.organizerAccount;
    }

    /**
     * Set the organizer account email address.
     *
     * @param organizerAccount the new organizer account email address
     */
    public void setOrganizerAccount(final String organizerAccount) {
        this.organizerAccount = organizerAccount;
    }

    /**
     * Set the linked domains.
     *
     * @param linkedDomains the linked domains to set
     */
    public void setLinkedDomains(final String[] linkedDomains) {
        this.linkedDomains = linkedDomains.clone();
    }

    /**
     * Set the trace listener for tracing Exchange communications.
     *
     * @param traceListener the trace listener to set
     */
    public void setTraceListener(final IExchangeTraceListener traceListener) {
        this.traceListener = traceListener;
    }

    /**
     * Set the resource folders.
     *
     * @param resourceFolders the resource folders to set
     */
    public void setResourceFolders(final String[] resourceFolders) {
        this.resourceFolders = resourceFolders.clone();
    }

    /**
     * Get the resource folders of resource account.
     *
     * @return resource folders list
     */
    public String[] getResourceFolders() {
        return this.resourceFolders;
    }

    /**
     * Check whether the given email matches one of the linked domains.
     *
     * @param email the email address
     * @return true if a match is found or no linked domains are specified, false otherwise
     */
    protected boolean isLinkedDomain(final String email) {
        boolean isLinkedDomain = false;
        if (this.linkedDomains == null || this.linkedDomains.length == 0) {
            isLinkedDomain = true;
        } else if (StringUtil.notNullOrEmpty(email)) {
            for (final String linkedDomain : this.linkedDomains) {
                if (email.toLowerCase().endsWith(linkedDomain.toLowerCase())) {
                    isLinkedDomain = true;
                    break;
                }
            }
        }
        return isLinkedDomain;
    }

    /**
     * Get WellKnownFolderName of resource folder.
     * 
     * @param folder resource folder
     * @return {@link WellKnownFolderName}
     */
    public WellKnownFolderName getWellKnownFolderName(final String folder) {
        WellKnownFolderName wellKnownFolderName = WellKnownFolderName.Inbox;
        if ("inbox".equals(folder)) {
            wellKnownFolderName = WellKnownFolderName.Inbox;
        } else if ("deleted".equals(folder)) {
            wellKnownFolderName = WellKnownFolderName.DeletedItems;
        }
        return wellKnownFolderName;
    }
}