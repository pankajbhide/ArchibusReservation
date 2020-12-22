package com.archibus.app.reservation.util;

/**
 * Utility class for non-integration option. Holds configuration properties.
 * This class contains one non-static method: getResourceAccount Returns null, because this is only
 * used for Exchange integration.
 *
 * @author Yorik Gerlo
 * @since 21.2
 */
public class WebCentralCalendarSettings implements ICalendarSettings {

    /**
     * {@inheritDoc} This type of calendar doesn't use a resource account.
     */
    @Override
    public String getResourceAccount() {
        return null;
    }

}
