package com.archibus.app.reservation.service;

import java.io.StringReader;
import java.util.*;

import org.apache.log4j.Logger;
import org.dom4j.*;
import org.dom4j.io.SAXReader;

import com.archibus.app.common.recurring.*;
import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.ReservationException;
import com.archibus.datasource.*;
import com.archibus.datasource.data.DataRecord;
import com.archibus.datasource.restriction.Restrictions;

/**
 * WFR service that provides tools to upgrade existing data for working with the 21.2 Reservations
 * module.
 *
 * @author Yorik Gerlo
 * @since 21.2
 */
public class ReservationUpgradeService {

    /** Comma symbol. */
    private static final String COMMA = ",";

    /** Symbol used to make the old recurring format valid XML. */
    private static final String UNDERSCORE = "_";

    /** Reservation type field name. */
    private static final String RES_TYPE = "res_type";

    /** Recurring rule field name. */
    private static final String RECURRING_RULE = "recurring_rule";

    /** Parent reservation id field name. */
    private static final String RES_PARENT = "res_parent";

    /** Reserve table name. */
    private static final String RESERVE_TABLE = "reserve";

    /** Resources table name. */
    private static final String RESOURCES_TABLE = "resources";

    /** Update command. */
    private static final String UPDATE = "UPDATE";

    /** A single whitespace. */
    private static final String WHITESPACE = " ";

    /** The logger. */
    private final Logger logger = Logger.getLogger(this.getClass());

    /**
     * Convert existing resources so all catering resources are defined Unlimited and all
     * non-catering resources are define Unique or Limited. 1. Each catering resource is converted
     * to an Unlimited resource and the quantity field is set to 0. 2. Each non-catering resource
     * currently defined as Unlimited resource is converted to a limited resource and the quantity
     * is set to 999.
     * <p>
     * Suppress warning PMD.AvoidUsingSQL in this method.
     * <p>
     * Justification: Case #2.2: Statement with UPDATE ... WHERE pattern.
     */
    @SuppressWarnings("PMD.AvoidUsingSql")
    public void convertResources() {
        SqlUtils.executeUpdate(RESOURCES_TABLE,
            UPDATE + WHITESPACE + RESOURCES_TABLE
                    + " SET resource_type = 'Unlimited', quantity = 0 WHERE resource_std IN ("
                    + " SELECT resource_std FROM resource_std WHERE resource_nature = 'Catering')");

        SqlUtils.executeUpdate(RESOURCES_TABLE, UPDATE + WHITESPACE + RESOURCES_TABLE
                + " SET resource_type = 'Limited', quantity = 999 "
                + " WHERE resource_type = 'Unlimited' AND resource_std IN ("
                + " SELECT resource_std FROM resource_std WHERE resource_nature <> 'Catering')");
    }

    /**
     * Convert the recurring rule in existing reservations from the old format to the new format.
     */
    public void convertRecurringRule() {
        final DataSource reservationDataSource = DataSourceFactory.createDataSourceForFields(
            RESERVE_TABLE, new String[] { RES_PARENT, RECURRING_RULE, RES_TYPE });
        reservationDataSource.addRestriction(Restrictions.isNotNull(RESERVE_TABLE, RES_PARENT));
        reservationDataSource
            .addRestriction(Restrictions.eq(RESERVE_TABLE, RES_TYPE, Constants.TYPE_RECURRING));
        reservationDataSource
            .addRestriction(Restrictions.like(RESERVE_TABLE, RECURRING_RULE, "<options%"));
        reservationDataSource.setDistinct(true);
        final List<DataRecord> records = reservationDataSource.getRecords();
        for (final DataRecord record : records) {
            final int parentId = record.getInt("reserve.res_parent");
            try {
                convertRecurringRule(parentId, record.getString("reserve.recurring_rule"));
            } catch (final DocumentException exception) {
                this.logger.warn("Syntax Error in recurring_rule for res_parent = " + parentId,
                    exception);
            } catch (final ReservationException exception) {
                this.logger.warn("Error converting recurring_rule for res_parent = " + parentId,
                    exception);
            }
        }
    }

    /**
     * This method calls all upgrade methods defined in this class.
     */
    public void run() {
        convertResources();
        convertRecurringRule();
    }

    /**
     * Convert the given recurring rule and set it to all reserve records with the given parent id.
     *
     * @param parentId parent reservation id of the recurring reservation to convert
     * @param recurringRule the recurring rule to convert
     * @throws DocumentException when the recurring rule is not valid xml
     *             <p>
     *             Suppress warning PMD.AvoidUsingSQL in this method.
     *             <p>
     *             Justification: Case #2.2: Statement with UPDATE ... WHERE pattern.
     */
    @SuppressWarnings("PMD.AvoidUsingSql")
    private void convertRecurringRule(final int parentId, final String recurringRule)
            throws DocumentException {
        // modify the string to make it valid xml: add a _ in front of attributes starting with a
        // digit
        final String modifiedRule =
                recurringRule.replace("1st", UNDERSCORE + RecurringSchedulePattern.FIRST)
                    .replace("2nd", UNDERSCORE + RecurringSchedulePattern.SECOND)
                    .replace("3rd", UNDERSCORE + RecurringSchedulePattern.THIRD)
                    .replace("4th", UNDERSCORE + RecurringSchedulePattern.FORTH);
        // parse the xml format recurring rule to xml document and element
        final Document recordXmlDoc = new SAXReader().read(new StringReader(modifiedRule));
        final Element rootElement = recordXmlDoc.getRootElement();
        final String recurrenceType = rootElement.attributeValue("type");

        String newRecurringRule = null;
        if ("day".equals(recurrenceType)) {
            final Element ndaysElement = rootElement.element("ndays");
            final String ndaysValue = ndaysElement.attributeValue("value");
            final int interval = parseInterval(ndaysValue, parentId);
            newRecurringRule = RecurringScheduleService.getRecurrenceXMLPattern(
                RecurringSchedulePattern.TYPE_DAY, interval, -1, null, -1, -1, -1);
        } else if ("week".equals(recurrenceType)) {
            final Element weeklyElement = rootElement.element("weekly");
            newRecurringRule = RecurringScheduleService.getRecurrenceXMLPattern(
                RecurringSchedulePattern.TYPE_WEEK, 1, -1, getDaysOfTheWeek(weeklyElement), -1, -1,
                -1);
        } else if ("month".equals(recurrenceType)) {
            final Element monthlyElement = rootElement.element("monthly");
            newRecurringRule = RecurringScheduleService.getRecurrenceXMLPattern(
                RecurringSchedulePattern.TYPE_MONTH, 1, -1, getDaysOfTheWeek(monthlyElement), -1,
                getWeekOfMonth(monthlyElement), -1);
        } else {
            throw new ReservationException("Invalid recurrence type in old format: {0}",
                ReservationUpgradeService.class, recurrenceType);
        }

        SqlUtils.executeUpdate(RESERVE_TABLE, "UPDATE reserve SET recurring_rule = '"
                + newRecurringRule + "' WHERE res_parent = " + parentId);
    }

    /**
     * Parse the days of the week present as attributes in the given xml element into the new days
     * of the week format string: days of the week in comma delimited (mon,wed,sat).
     *
     * @param element the xml element containing the days of the week as attributes
     * @return list of days of the week in the new format
     */
    private String getDaysOfTheWeek(final Element element) {
        final StringBuffer daysOfWeek = new StringBuffer();

        for (final String dayOfWeek : RecurringSchedulePattern.WEEK_VALUE_ARRAY) {
            if (Boolean.parseBoolean(element.attributeValue(dayOfWeek))) {
                daysOfWeek.append(dayOfWeek + COMMA);
            }
        }

        if (daysOfWeek.length() > 0) {
            daysOfWeek.setLength(daysOfWeek.length() - 1);
        }

        return daysOfWeek.toString();
    }

    /**
     * Parse an interval string. If it can't be parsed as an integer, default to 1.
     *
     * @param value string representation of the interval
     * @param parentId parent reservation id (for logging failure)
     * @return integer representation of the interval, or 1 if parsing failed
     */
    private int parseInterval(final String value, final int parentId) {
        int result = 1;
        try {
            result = Integer.parseInt(value);
        } catch (final NumberFormatException exception) {
            this.logger.warn("Invalid interval in old recurrence format: '" + value
                    + "' for res_parent=" + parentId + ". Default to interval=1 for conversion.");
        }
        return result;
    }

    /**
     * Get the week of month from the given element.
     *
     * @param element has attributes determining the week of month in the old format
     * @return the week of month in the new recurring rule format (1-5 where 5 is last)
     */
    private int getWeekOfMonth(final Element element) {
        final List<String> weekAttributeNames = new ArrayList<String>();
        weekAttributeNames.add(RecurringSchedulePattern.FIRST);
        weekAttributeNames.add(RecurringSchedulePattern.SECOND);
        weekAttributeNames.add(RecurringSchedulePattern.THIRD);
        weekAttributeNames.add(RecurringSchedulePattern.FORTH);
        weekAttributeNames.add(RecurringSchedulePattern.LAST);

        // find the first week attribute with value "true"
        int weekOfMonth = 0;
        for (int index = 0; index < weekAttributeNames.size(); ++index) {
            if (Boolean
                .parseBoolean(element.attributeValue(UNDERSCORE + weekAttributeNames.get(index)))) {
                // old attribute name corresponds with new attribute value
                weekOfMonth = index + 1;
                break;
            }
        }

        if (weekOfMonth == 0) {
            throw new ReservationException("Invalid monthly pattern. No week of month specified.",
                ReservationUpgradeService.class);
        }
        return weekOfMonth;
    }

}
