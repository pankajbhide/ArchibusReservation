package com.archibus.app.reservation.dao;

import java.util.List;

import com.archibus.app.reservation.domain.ResourceStandard;

/**
 * Interface for the resource standards data source.
 *
 * @author Yorik Gerlo
 */
public interface IResourceStandardDataSource {

    /**
     * Get the list of defined fixed resource standards.
     *
     * @return list of resource standards
     */
    List<ResourceStandard> getFixedResourceStandards();

    /**
     * Get the list of defined fixed resource standards for a specific building.
     *
     * @param buildingId the building id to filter
     * @return list of resource standards for the building
     */
    List<ResourceStandard> getFixedResourceStandards(String buildingId);

}