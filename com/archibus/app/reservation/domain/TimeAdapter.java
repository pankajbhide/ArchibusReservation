package com.archibus.app.reservation.domain;

import java.sql.Time;
import java.util.Date;

import javax.xml.bind.annotation.adapters.XmlAdapter;


/**
 * Adapter for the Date class, since standard java.sql.Date is not supported in JAXB.
 * 
 * @author Bart Vanderschoot
 * 
 */
public class TimeAdapter extends XmlAdapter<Date, Time> {
    
    /**
     * marchal the Time value.
     * @param time value
     * @return date value
     */
    @Override
    public final Date marshal(final Time time) {
        return new Date(time.getTime());
    }
    
    /**
     * unmarchal the Time value.
     * @param date value
     * @return time value
     */
    @Override
    public final Time unmarshal(final Date date) {
        return new Time(date.getTime());
    }
}
