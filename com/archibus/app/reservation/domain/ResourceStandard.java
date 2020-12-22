package com.archibus.app.reservation.domain;

import java.util.*;

/**
 * Domain class for resource standards.
 * 
 * @author Yorik Gerlo
 */
public class ResourceStandard {
    
    /** Resource standard identifier. */
    private String id;
    
    /** Resource standard readable (and translatable) name. */
    private String name;
    
    /**
     * Get the resource standard id.
     * 
     * @return the resource standard id
     */
    public String getId() {
        return id;
    }
    
    /**
     * Set the resource standard id.
     * 
     * @param id the resource standard id to set
     */
    public void setId(final String id) {
        this.id = id;
    }
    
    /**
     * Get the readable name for this resource standard.
     * 
     * @return the resource standard name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Set the resource standard name.
     * 
     * @param name the resource standard name to set
     */
    public void setName(final String name) {
        this.name = name;
    }
    
    /**
     * Convert a list of resource standards to a list of resource standard primary keys.
     * @param resourceStandards list of resource standards
     * @return list of primary keys
     */
    public static List<String> toPrimaryKeyList(final List<ResourceStandard> resourceStandards) {
        List<String> results = null;
        if (resourceStandards != null) {
            results = new ArrayList<String>(resourceStandards.size());
            for (final ResourceStandard standard : resourceStandards) {
                results.add(standard.getId());
            }
        }
        return results;
    }
    
}
