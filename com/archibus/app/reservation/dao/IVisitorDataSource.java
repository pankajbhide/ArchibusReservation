package com.archibus.app.reservation.dao;

import java.util.List;

import com.archibus.app.reservation.domain.Visitor;
import com.archibus.core.dao.IDao;

/**
 * The Interface IVisitorDataSource.
 */
public interface IVisitorDataSource extends IDao<Visitor> {
    
    /**
     * Gets the all visitors.
     * 
     * @return the all visitors
     */
    List<Visitor> getAllVisitors();
    
    /**
     * Find a visitor with the given email address.
     * 
     * @param email the email address
     * @return the corresponding visitor, or null if not found
     */
    Visitor findByEmail(final String email);
    
}
