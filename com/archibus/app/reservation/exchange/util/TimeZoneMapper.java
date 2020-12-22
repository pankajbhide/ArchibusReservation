package com.archibus.app.reservation.exchange.util;

import java.io.InputStream;
import java.util.*;

import org.apache.log4j.Logger;
import org.dom4j.*;
import org.dom4j.io.SAXReader;

import com.archibus.app.reservation.domain.CalendarException;

/**
 * Time zone mapper between Windows and Olson (Java) IDs. Used to interface with Exchange.
 * 
 * @author Yorik Gerlo
 * @since 21.2
 */
public class TimeZoneMapper {
    
    /** Arrow used for logging. */
    private static final String ARROW = "->";
    
    /**
     * Name of the xml file containing the time zone mapping information (on the classpath).
     */
    private static final String TIMEZONE_FILE = "windowsZones.xml";
    
    /** Maps Windows Time Zone IDs to the default corresponding Olson ID. */
    private final Map<String, String> windowsToOlson = new HashMap<String, String>();
    
    /** Maps Olson Time Zone IDs to the default corresponding Windows ID. */
    private final Map<String, String> olsonToWindows = new HashMap<String, String>();
    
    /** The logger. */
    private final Logger logger = Logger.getLogger(this.getClass());
    
    /**
     * Instantiate a new time zone mapper (managed via Spring).
     */
    public TimeZoneMapper() {
        try {
            parseDocument();
        } catch (DocumentException exception) {
            throw new CalendarException("Error parsing time zone conversions.", exception,
                TimeZoneMapper.class);
        }
    }
    
    /**
     * Parse the time zones xml document.
     * 
     * @throws DocumentException when the parsing failed
     */
    private void parseDocument() throws DocumentException {
        final InputStream xmlDocument = TimeZoneMapper.class.getResourceAsStream(TIMEZONE_FILE);
        
        final Document document = new SAXReader().read(xmlDocument);
        
        // XPath: "/supplementalData/windowsZones/mapTimezones/mapZone"
        final Iterator<?> mapZones =
                document.getRootElement().element("windowsZones").element("mapTimezones")
                    .elementIterator("mapZone");
        
        while (mapZones.hasNext()) {
            final Element element = (Element) mapZones.next();
            final String[] olsonIds = element.attributeValue("type").split(" ");
            final String windowsId = element.attributeValue("other");
            final String territory = element.attributeValue("territory");
            
            for (final String olsonId : olsonIds) {
                final String existingValue = this.olsonToWindows.get(olsonId);
                if (existingValue == null) {
                    this.olsonToWindows.put(olsonId, windowsId);
                } else if (!windowsId.equals(existingValue)) {
                    logger.warn("Ignoring Olson to Windows mapping (got '" + olsonId + ARROW
                            + existingValue + "' already): " + windowsId);
                }
            }
            
            if ("001".equals(territory) && olsonIds.length > 0) {
                final String existingValue = this.windowsToOlson.get(windowsId);
                if (existingValue == null) {
                    this.windowsToOlson.put(windowsId, olsonIds[0]);
                } else if (!olsonIds[0].equals(existingValue)) {
                    logger.warn("Ignoring Windows to Olson mapping (got " + windowsId + ARROW
                            + existingValue + " already): " + olsonIds[0]);
                }
            }
        }
    }
    
    /**
     * Get the corresponding Olson ID for a given Windows ID.
     * 
     * @param windowsId the Windows ID to convert
     * @return the corresponding Olson ID, or null if not found
     */
    public String getOlsonId(final String windowsId) {
        final String olsonId = this.windowsToOlson.get(windowsId);
        
        if (olsonId == null) {
            logger.warn("Windows ID '" + windowsId
                    + "' could not be mapped to an Olson equivalent.");
        }
        
        return olsonId;
    }
    
    /**
     * Get the corresponding Windows ID for a given Olson ID. If the Olson ID is not mapped to a
     * Windows ID directly, look for an Olson ID with the same time zone rules that does have a
     * mapping to a Windows ID. Return null when no mapping is found.
     * 
     * @param olsonId the Olson ID to convert
     * @return the corresponding Windows ID, or null if not found
     */
    public String getWindowsId(final String olsonId) {
        String windowsId = this.olsonToWindows.get(olsonId);
        
        // Check equivalent time zones if no match is found for the given ID.
        if (windowsId == null && olsonId != null) {
            final TimeZone timezone = TimeZone.getTimeZone(olsonId);
            final String[] matchingTimeZones = TimeZone.getAvailableIDs(timezone.getRawOffset());
            for (int i = 0; i < matchingTimeZones.length && windowsId == null; ++i) {
                if (timezone.hasSameRules(TimeZone.getTimeZone(matchingTimeZones[i]))) {
                    windowsId = this.olsonToWindows.get(matchingTimeZones[i]);
                }
            }
        }
        
        if (windowsId == null) {
            logger.warn("Olson ID '" + olsonId + "' could not be mapped to a Windows equivalent.");
        }
        
        return windowsId;
    }
    
}
