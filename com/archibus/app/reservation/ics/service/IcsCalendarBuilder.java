package com.archibus.app.reservation.ics.service;

import java.util.Date;
import java.util.List;

import com.archibus.app.reservation.ics.domain.*;
import com.archibus.app.reservation.util.TimeZoneConverter;
import com.archibus.datasource.DataSource;
import com.archibus.datasource.DataSourceFactory;
import com.archibus.datasource.data.DataRecord;
import com.archibus.datasource.restriction.Restrictions;

import net.fortuna.ical4j.model.*;
import net.fortuna.ical4j.model.component.*;
import net.fortuna.ical4j.model.property.*;
import net.fortuna.ical4j.util.CompatibilityHints;

/**
 * Copyright (C) ARCHIBUS, Inc. All rights reserved.
 */
/**
 * Utility class. Provides methods to build an ICS calendar (event) for a reservation.
 * <p>
 * Used by reservations to build ICS attachments.
 *
 * @author PROCOS
 * @since 23.2
 *
 */
public final class IcsCalendarBuilder {

    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private IcsCalendarBuilder() {
    }

    /**
     * Create the ICS object to attach.
     *
     * @param model the ICS model to be used
     * @param attendees the attendees email addresses
     * @param addOrganizer flag indicating if should add the organizer
     * @return the ICS object
     */
    static Calendar createIcsAttachment(final IcsModel model, final String[] attendees,
                                        final boolean addOrganizer) {

        // set the compatibility with Outlook
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_OUTLOOK_COMPATIBILITY, true);

        final String organizerEmail = model.getFrom();
        final String tzone = model.getLocationModel().getTimezone();
        final EmailModel emailModel = model.getEmailModel();

        // timezone information
        final net.fortuna.ical4j.model.TimeZone timezone =
                TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone(tzone);


          
        // enter the time in server time zone
        final DateTime start = getTimezonedDateTime(model.getStartDateTime(), timezone);

        // calculate the end date in server time zone
        final DateTime end = getTimezonedDateTime(model.getEndDateTime(), timezone);

        // the day of end is the same as start
        final VEvent meeting = createEvent(model.getSummary(), model.getDescription(),
                timezone.getVTimeZone(), start, end);

        final PropertyList<Property> propertyList = meeting.getProperties();

        
        //Added by Pankaj@LBNL
        // Get the latest revision number of the reservation or the parent reservation (in case of recurring one)
        int intRevision=0;
      
        String strWhere=" (res_id = '" + model.getUid() +"' OR res_parent='" + model.getUid() +"') " ;
               strWhere += " and (lbl_revision_number =(select max(b.lbl_revision_number) from reserve b where ";
               strWhere += " (b.res_id = '" + model.getUid() +"' OR b.res_parent='" + model.getUid() +"')))" ;
                              
               DataSource ds = DataSourceFactory.createDataSource();
               ds.addTable("reserve");
               ds.addField("lbl_revision_number");
               ds.addSort("reserve","lbl_revision_number", DataSource.SORT_DESC);
               ds.addRestriction(Restrictions.sql(strWhere));
             	 List <DataRecord> records=ds.getRecords();


               for (DataRecord record : records) {
            	   intRevision=record.getInt("reserve.lbl_revision_number");
            	   break;
                 }
         
          intRevision++;
          ds=null;
        
        final int sequenceNumber;
        /*  Commented by Pankaj@LBNL     
            if (emailModel.isCancel()) {
         
            propertyList.add(net.fortuna.ical4j.model.property.Status.VEVENT_CANCELLED);
            sequenceNumber = 2;  
           } else if (emailModel.isChange() && emailModel.isRecurring()
                && !emailModel.isAllRecurrences()) {
            sequenceNumber = 1; 
        	
        } else {
            sequenceNumber = 0;  
            
        }*/
        sequenceNumber=intRevision; // Added by Pankaj@LBNL

        // Pankaj@ LBNL added property
        propertyList.add(net.fortuna.ical4j.model.property.Transp.TRANSPARENT);


        // 0 for new or update, 1 for occurrence update and 2 for cancel
        propertyList.add(new Sequence(sequenceNumber));
        propertyList.add(model.getIcsUid());
        propertyList.add(model.getLocationModel().getIcsLocation());

        // add attendees
        IcsAttendeesHelper.addAttendees(meeting, attendees, emailModel.isRequireReply(),
                organizerEmail, addOrganizer);

        if (model.getRecurrenceId() != null) {
            propertyList
                    .add(new RecurrenceId(getTimezonedDateTime(model.getRecurrenceId(), timezone)));
        }
        if (model.isRecurring()) {
            RecurrenceHelper.addRecurringRule(meeting, model.getRecurrence(),
                    new DateTime(model.getUntilDate()));
            if (model.getExceptionDates() != null) {
                RecurrenceHelper.addExceptionDates(meeting, model.getExceptionDates());
            }
        }

        // Create the calendar
        return createIcsCalendar(meeting, timezone, emailModel.getMethod());
    }

    /**
     * Create the ICS Calendar Object.
     *
     * @param meeting the meeting event
     * @param timezone the source time zone
     * @param method the invitation type method
     * @return the ICS Calendar Object
     */
    private static Calendar createIcsCalendar(final VEvent meeting,
                                              final net.fortuna.ical4j.model.TimeZone timezone, final Method method) {
        final Calendar icsCalendar = new Calendar();

        final PropertyList<Property> icsPropertyList = icsCalendar.getProperties();
        icsPropertyList.add(new ProdId("-//Events Calendar//iCal4j 2.0//EN"));
        icsPropertyList.add(net.fortuna.ical4j.model.property.CalScale.GREGORIAN);
        icsPropertyList.add(net.fortuna.ical4j.model.property.Version.VERSION_2_0);
        icsPropertyList.add(method);

        // add the time zone definition
        final ComponentList<CalendarComponent> components = icsCalendar.getComponents();
        components.add(timezone.getVTimeZone());
        // add the meeting to the calendar
        components.add(meeting);

        return icsCalendar;
    }

    /**
     * Calculate the date and time for the source time zone.
     *
     * @param dateTime the date and time to calculate
     * @param timezone the time zone object
     * @return the date and time at the requested time zone
     */
    private static DateTime getTimezonedDateTime(final Date dateTime,
                                                 final net.fortuna.ical4j.model.TimeZone timezone) {
        final DateTime date = new DateTime(
                TimeZoneConverter.calculateDateTime(dateTime, timezone.getID(), null).getTime());
        // set time zone, and calculate the date
        date.setTimeZone(timezone);
        return date;
    }

    /**
     * Create the meeting event.
     *
     * @param subject the subject
     * @param body the body
     * @param vtz the time zone
     * @param start the start date
     * @param end the end date
     * @return the meeting event
     */
    private static VEvent createEvent(final String subject, final String body, final VTimeZone vtz,
                                      final DateTime start, final DateTime end) {
        final VEvent meeting = new VEvent(start, end, subject);

        meeting.getProperties().add(vtz.getTimeZoneId());
        meeting.getProperties().add(new Description(body));
        meeting.getProperties().add(net.fortuna.ical4j.model.property.Clazz.PUBLIC);
        return meeting;
    }

}
