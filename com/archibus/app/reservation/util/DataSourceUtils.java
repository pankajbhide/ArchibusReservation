package com.archibus.app.reservation.util;

import java.util.*;

import com.archibus.app.reservation.dao.datasource.Constants;
import com.archibus.app.reservation.domain.*;
import com.archibus.datasource.*;
import com.archibus.datasource.DataSourceImpl.TableAndRole;
import com.archibus.datasource.restriction.Restrictions;

/**
 * Utilities for datasources.
 *
 * @author Bart Vanderschoot
 *
 */
public final class DataSourceUtils {

    /** One hundred. */
    private static final int HUNDRED = 100;

    /**
     * Private default constructor: utility class is non-instantiable.
     *
     * @throws InstantiationException always, since this constructor should never be called.
     */
    private DataSourceUtils() throws InstantiationException {
        throw new InstantiationException(
            "Never instantiate " + this.getClass().getName() + "; use static methods!");
    }

    /**
     * Gets the fields to properties.
     *
     * @param mapping the mapping
     * @return the fields to properties
     */
    public static String[][] getFieldsToProperties(final Map<String, String> mapping) {
        final String[][] fieldsToProperties = new String[mapping.size()][2];
        int index = 0;
        for (final String key : mapping.keySet()) {
            fieldsToProperties[index++] = new String[] { key, mapping.get(key) };
        }

        return fieldsToProperties;
    }

    /**
     * Generate SQL statement to add to date fields.
     *
     * @param dataSource DataSource
     * @param tableName Table
     * @param dateField First Field : date value
     * @param deltaFieldMinutes Second Field : value in minutes
     * @param add boolean if true, add, if false, substract
     * @return generated SQL
     */
    public static String generateDateAddSql(final DataSource dataSource, final String tableName,
            final String dateField, final String deltaFieldMinutes, final boolean add) {
        final StringBuffer sql = new StringBuffer();

        if (dataSource.isOracle()) {
            sql.append(tableName + Constants.DOT + dateField);
            if (add) {
                sql.append(" + ");
            } else {
                sql.append(Constants.MINUS);
            }
            sql.append(tableName + Constants.DOT + deltaFieldMinutes + " / (24*60)");
        } else if (dataSource.isSqlServer()) {
            sql.append("DATEADD (minute,");
            if (!add) {
                sql.append(Constants.MINUS);
            }
            sql.append(tableName + Constants.DOT + deltaFieldMinutes + Constants.COMMA + tableName
                    + Constants.DOT + dateField + ")");

        } else {
            sql.append("convert(char(10), DATEADD (minute,");
            if (!add) {
                sql.append(Constants.MINUS);
            }
            sql.append(tableName + Constants.DOT + deltaFieldMinutes + Constants.COMMA + tableName
                    + Constants.DOT + dateField + "), 108)");

        }

        return sql.toString();
    }

    /**
     * Check whether an email address belongs to an employee.
     *
     * @param email the email address
     * @return true if it's an employee's email address, false otherwise
     */
    public static boolean isEmployeeEmail(final String email) {
        final DataSource employeeDataSource =
                DataSourceFactory.createDataSourceForFields(Constants.EM_TABLE_NAME,
                    new String[] { Constants.EM_ID_FIELD_NAME, Constants.EMAIL_FIELD_NAME, });
        employeeDataSource.setApplyVpaRestrictions(false);
        employeeDataSource.addRestriction(
            Restrictions.eq(Constants.EM_TABLE_NAME, Constants.EMAIL_FIELD_NAME, email));
        return !employeeDataSource.getRecords().isEmpty();
    }

    /**
     * Find the difference in days between the start date of an allocation and a current date.
     *
     * @param allocation allocation
     * @param localCurrentDate current date
     *
     * @return days difference
     */
    public static long getDaysDifference(final ITimePeriodBased allocation,
            final Date localCurrentDate) {
        if (allocation.getStartDate() == null) {
            // @translatable
            throw new ReservationException("No reservation date available", DataSourceUtils.class);
        }

        return (allocation.getStartDate().getTime() - localCurrentDate.getTime())
                / Constants.ONE_DAY;
    }

    /**
     * Round double number to 2 decimals.
     *
     * @param value double value
     *
     * @return value rounded to 2 decimals
     */
    public static double round2(final double value) {
        double result = value * HUNDRED;
        result = Math.round(result);
        return result / HUNDRED;
    }

    /**
     * Add custom fields defined in the view file and add them to the data source.
     *
     * @param dataSource the data source to add to
     * @param viewName the view file name
     * @param dataSourceName name of the data source in the view file
     */
    public static void addCustomViewFields(final DataSource dataSource, final String viewName,
            final String dataSourceName) {

        final DataSource viewDataSource =
                DataSourceFactory.loadDataSourceFromFile(viewName, dataSourceName);

        final List<String> fieldNames = viewDataSource.getFieldNames();

        final List<TableAndRole> tableAndRoles = viewDataSource.getTablesAndRoles();

        for (final TableAndRole tableAndRole : tableAndRoles) {
            dataSource.addTable(tableAndRole.name, tableAndRole.role);
        }

        for (final String fullFieldName : fieldNames) {
            if (dataSource.findField(fullFieldName) == null
                    && fullFieldName.indexOf(Constants.DOT) > 1) {

                final int index = fullFieldName.indexOf(Constants.DOT);
                final String tableName = fullFieldName.substring(0, index);
                final String fieldName = fullFieldName.substring(index + 1);

                dataSource.addField(tableName, fieldName);
            }
        }
    }

    /**
     * Check whether VPA restrictions must be enabled for the Reservations Application.
     *
     * @return true if enabled, false if disabled
     */
    public static boolean isVpaEnabled() {
        return com.archibus.service.Configuration.getActivityParameterInt("AbWorkplaceReservations",
            "ApplyVpaRestrictions", 1) == 1;
    }

}
