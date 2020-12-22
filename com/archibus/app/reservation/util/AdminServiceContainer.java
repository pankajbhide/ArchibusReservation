package com.archibus.app.reservation.util;

import com.archibus.service.remoting.AdminService;

/**
 * Copyright (C) ARCHIBUS, Inc. All rights reserved.
 */
/**
 * Provides access to an admin service for localization.
 *<p>
 * Managed by Spring, has prototype scope. Configured in reservation-context.xml file.
 *
 * @author PROCOS
 * @since 23.2
 */
public class AdminServiceContainer {

    /** Administration service for localization. */
    private AdminService adminService;

    /**
     * Set the administration service.
     * @param adminService the admin service
     */
    public void setAdminService(final AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * Get the administration service.
     * @return the admin service
     */
    public AdminService getAdminService() {
        return this.adminService;
    }

}
