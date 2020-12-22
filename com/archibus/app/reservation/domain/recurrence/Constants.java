package com.archibus.app.reservation.domain.recurrence;


/**
 * The Class Constants.
 */
public final class Constants { 
    
    /** Parameter value that indicates to always show all conflicts. */
    public static final int CONFLICTS_ALL = 0;
    
    /** Parameter value that indicates to always show filtered conflicts. */
    public static final int CONFLICTS_FILTERED = 1;
    
    /** Parameter value that indicates to show all conflicts only if all rooms have conflicts. */
    public static final int CONFLICTS_ALL_IF_ONLY_CONFLICTS = 2;
  
    /** Parameter value that indicates to show filtered conflicts only if all rooms have conflicts. */
    public static final int CONFLICTS_FILTERED_IF_ONLY_CONFLICTS = 3;
    
    /** Parameter value that indicates to never show conflicts. */
    public static final int CONFLICTS_NONE = 4;
    
    /** Parameter value that indicates to always show all conflicts, starting from rooms available
     * for the first occurrence. */
    public static final int CONFLICTS_FIRST = 5;
    
    /** Parameter value that indicates to always show filtered conflicts, starting from the rooms
     * available for the first occurrence. */
    public static final int CONFLICTS_FIRST_FILTERED = 6;
    
    /** Parameter value that indicates to show all conflicts only if all rooms have conflicts,
     * starting from rooms available for the first occurrence. */
    public static final int CONFLICTS_FIRST_IF_ONLY_CONFLICTS = 7;
  
    /** Parameter value that indicates to show filtered conflicts only if all rooms have conflicts,
     * starting from rooms available for the first occurrence. */
    public static final int CONFLICTS_FIRST_FILTERED_IF_ONLY_CONFLICTS = 8;
    
    /** The Constant EACH_DAY. */
    static final String EACH_DAY = "day";

    /** The Constant WEEK_DAY. */
    static final String WEEK_DAY = "weekday";

    /** The Constant WEEKEND_DAY. */
    static final String WEEKEND_DAY = "weekendday"; 
    
    /** The Constant INDEX_SUNDAY. */
    static final int INDEX_SUNDAY = 6;
    
    /**
     * Prevent instantiation a new constants class.
     */
    private Constants() { 
    } 
    

}
