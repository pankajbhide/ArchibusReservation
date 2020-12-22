/**
 * Provides classes and interfaces for domain objects for reservation module.
 * 
 * Domain objects should be published in the wsdl file for outlook integration. Time objects are
 * mapped using a custom adapter, since JAXB doesn't support Time objects out-of-the-box.
 * 
 */
@javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter(value = TimeAdapter.class,
        type = java.sql.Time.class)
package com.archibus.app.reservation.domain;

