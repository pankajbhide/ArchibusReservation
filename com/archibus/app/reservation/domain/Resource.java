package com.archibus.app.reservation.domain;

import javax.xml.bind.annotation.*;

/**
 * Domain class for reservable resource objects.
 * 
 * @author Bart Vanderschoot
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "Resource")
public class Resource extends AbstractReservable {
    
    /** The bl id. */
    private String blId;
    
    /** The equipment code. */
    private String eqId;
    
    /** The quantity. */
    private Integer quantity;
    
    /** The resource id. */
    private String resourceId;
    
    /** The resource name. */
    private String resourceName;
    
    /** The resource standard. */
    private String resourceStandard;
    
    /** The resource type. */
    private String resourceType;
    
    /** The site id. */
    private String siteId;
    
    /**
     * Gets the bl id.
     * 
     * @return the bl id
     */
    public final String getBlId() {
        return this.blId;
    }
    
    /**
     * Gets the equipment code.
     * 
     * @return the equipment code
     */
    public final String getEqId() {
        return this.eqId;
    }
    
    /**
     * Gets the quantity.
     * 
     * @return the quantity
     */
    public final Integer getQuantity() {
        return this.quantity;
    }
    
    /**
     * Gets the resource id.
     * 
     * @return the resource id
     */
    public final String getResourceId() {
        return this.resourceId;
    }
    
    /**
     * Gets the resource name.
     * 
     * @return the resource name
     */
    public final String getResourceName() {
        return this.resourceName;
    }
    
    /**
     * Gets the resource standard.
     * 
     * @return the resource standard
     */
    public final String getResourceStandard() {
        return this.resourceStandard;
    }
    
    /**
     * Gets the resource type.
     * 
     * @return the resource type
     */
    public final String getResourceType() {
        return this.resourceType;
    }
    
    /**
     * Gets the site id.
     * 
     * @return the site id
     */
    public String getSiteId() {
        return this.siteId;
    }
    
    /**
     * Sets the bl id.
     * 
     * @param blId the new bl id
     */
    public final void setBlId(final String blId) {
        this.blId = blId;
    }
    
    /**
     * Sets the equipment code.
     * 
     * @param eqId the new equipment code
     */
    public final void setEqId(final String eqId) {
        this.eqId = eqId;
    }
    
    /**
     * Sets the quantity.
     * 
     * @param quantity the new quantity
     */
    public final void setQuantity(final Integer quantity) {
        this.quantity = quantity;
    }
    
    /**
     * Sets the resource id.
     * 
     * @param resourceId the new resource id
     */
    public final void setResourceId(final String resourceId) {
        this.resourceId = resourceId;
    }
    
    /**
     * Sets the resource name.
     * 
     * @param resourceName the new resource name
     */
    public final void setResourceName(final String resourceName) {
        this.resourceName = resourceName;
    }
    
    /**
     * Sets the resource standard.
     * 
     * @param resourceStandard the new resource standard
     */
    public final void setResourceStandard(final String resourceStandard) {
        this.resourceStandard = resourceStandard;
    }
    
    /**
     * Sets the resource type.
     * 
     * @param resourceType the new resource type
     */
    public final void setResourceType(final String resourceType) {
        this.resourceType = resourceType;
    }
    
    /**
     * Sets the site id.
     * 
     * @param siteId the new site id
     */
    public final void setSiteId(final String siteId) {
        this.siteId = siteId;
    }
    
    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        int hash = 0;
        if (this.resourceId != null) {
            hash = this.resourceId.hashCode();
        }
        return hash;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        boolean same = true;
        if (this == obj) {
            same = true;
        } else if (obj == null || getClass() != obj.getClass()) {
            same = false;
        } else {
            final Resource other = (Resource) obj;
            if (this.resourceId == null) {
                // CHECKSTYLE:OFF Justification: nested if is required for equals method.
                // This implementation is based on Eclipse's implementation, refactored
                // to have only one exit point (to avoid the PMD.OnlyOneReturn warning).
                if (other.resourceId != null) {
                    same = false;
                }
                // CHECKSTYLE:ON
            } else if (!this.resourceId.equals(other.resourceId)) {
                same = false;
            }
        }
        
        return same;
    }
    
}
