package com.archibus.app.reservation.domain.recurrence;

/**
 * Room conflicts mode configured via the activity parameter
 * AbWorkplaceReservations-HideRoomConflicts.
 *
 * @author Yorik Gerlo
 */
public enum RoomConflictsMode {

    /** Always show all rooms available for at least one occurrence. */
    SHOW_ALL(Constants.CONFLICTS_ALL),

    /** Always show all rooms available for at least 50% of the occurrences. */
    SHOW_FILTERED(Constants.CONFLICTS_FILTERED),

    /**
     * Show rooms available for all occurrences (if any), otherwise show all rooms available for at
     * least one occurrence.
     */
    SHOW_ALL_IF_ONLY_CONFLICTS(Constants.CONFLICTS_ALL_IF_ONLY_CONFLICTS),

    /**
     * Show rooms available for all occurrences (if any), otherwise show all rooms available for at
     * least 50% of the occurrences.
     */
    SHOW_FILTERED_IF_ONLY_CONFLICTS(Constants.CONFLICTS_FILTERED_IF_ONLY_CONFLICTS),

    /** Always show only rooms available for all occurrences. */
    SHOW_NONE(Constants.CONFLICTS_NONE),

    /** Show rooms available at least for the first occurrence. */
    SHOW_FIRST(Constants.CONFLICTS_FIRST),

    /** Show rooms available for the first occurrence and at least 50% of the occurrences. */
    SHOW_FIRST_FILTERED(Constants.CONFLICTS_FIRST_FILTERED),

    /**
     * Show rooms available for all occurrences (if any), otherwise show all rooms available for at
     * least the first occurrence.
     */
    SHOW_FIRST_IF_ONLY_CONFLICTS(Constants.CONFLICTS_FIRST_IF_ONLY_CONFLICTS),

    /**
     * Show rooms available for all occurrences (if any), otherwise show all rooms available for the
     * first occurrence and at least 50% of the occurrences.
     */
    SHOW_FIRST_FILTERED_IF_ONLY_CONFLICTS(Constants.CONFLICTS_FIRST_FILTERED_IF_ONLY_CONFLICTS);

    /** Integer representation of the configuration mode. */
    private final int mode;

    /**
     * Initialize the room conflicts mode.
     *
     * @param mode integer value of the configuration mode
     */
    private RoomConflictsMode(final int mode) {
        this.mode = mode;
    }

    /**
     * Get the integer representation of the configuration mode.
     *
     * @return the integer representation of the configuration mode
     */
    public int getMode() {
        return this.mode;
    }

    /**
     * Check whether conflicts should always be included.
     *
     * @return true if rooms with conflicts should always be showed, false otherwise
     */
    public boolean alwaysIncludeRoomConflicts() {
        return this == SHOW_ALL || this == SHOW_FILTERED || this == SHOW_FIRST
                || this == SHOW_FIRST_FILTERED;
    }

    /**
     * Check whether conflicts should be included only if all rooms have conflicts.
     *
     * @return true if rooms with conflicts should only be showed if all rooms have conflicts, false
     *         if they should always be showed
     */
    public boolean onlyIfAllRoomsHaveConflicts() {
        return this == SHOW_ALL_IF_ONLY_CONFLICTS || this == SHOW_FILTERED_IF_ONLY_CONFLICTS
                || this == SHOW_FIRST_IF_ONLY_CONFLICTS
                || this == SHOW_FIRST_FILTERED_IF_ONLY_CONFLICTS;
    }

    /**
     * Check whether conflicts on the first occurrence are allowed.
     *
     * @return true if allowed, false if not allowed
     */
    public boolean allowConflictsOnFirstOccurrence() {
        boolean allowed = false;
        switch (this) {
            case SHOW_ALL:
            case SHOW_FILTERED:
            case SHOW_ALL_IF_ONLY_CONFLICTS:
            case SHOW_FILTERED_IF_ONLY_CONFLICTS:
                allowed = true;
                break;
            default:
                break;
        }
        return allowed;
    }

    /**
     * Get the maximum number of occurrences allowed for this mode.
     *
     * @param numberOfOccurrences the number of occurrences in the recurrence pattern
     * @return the maximum number of room conflicts allowed for a room
     */
    public int getMaxConflictsAllowed(final int numberOfOccurrences) {
        int maxConflictsAllowed = 0;
        switch (this) {
            case SHOW_ALL:
            case SHOW_ALL_IF_ONLY_CONFLICTS:
            case SHOW_FIRST:
            case SHOW_FIRST_IF_ONLY_CONFLICTS:
                // Retain all rooms available at least once.
                maxConflictsAllowed = numberOfOccurrences - 1;
                break;
            case SHOW_FILTERED:
            case SHOW_FILTERED_IF_ONLY_CONFLICTS:
            case SHOW_FIRST_FILTERED:
            case SHOW_FIRST_FILTERED_IF_ONLY_CONFLICTS:
                // Retain only the rooms available for at least 50% of the occurrences.
                maxConflictsAllowed = numberOfOccurrences / 2;
                break;
            case SHOW_NONE:
                // Never retain rooms with conflicts.
                maxConflictsAllowed = 0;
                break;
            default:
                break;
        }
        return maxConflictsAllowed;
    }

    /**
     * Get the corresponding enumeration constant from the given integer value.
     *
     * @param mode the configuration mode as an integer
     * @return the configuration mode as an enum constant
     */
    public static RoomConflictsMode getRoomConflictsMode(final int mode) {
        RoomConflictsMode conflictsMode = null;
        switch (mode) {
            case Constants.CONFLICTS_FIRST:
                conflictsMode = RoomConflictsMode.SHOW_FIRST;
                break;
            case Constants.CONFLICTS_FIRST_FILTERED:
                conflictsMode = RoomConflictsMode.SHOW_FIRST_FILTERED;
                break;
            case Constants.CONFLICTS_FIRST_IF_ONLY_CONFLICTS:
                conflictsMode = RoomConflictsMode.SHOW_FIRST_IF_ONLY_CONFLICTS;
                break;
            case Constants.CONFLICTS_FIRST_FILTERED_IF_ONLY_CONFLICTS:
                conflictsMode = RoomConflictsMode.SHOW_FIRST_FILTERED_IF_ONLY_CONFLICTS;
                break;
            default:
                // if not one of the new options, check the original options
                conflictsMode = getOriginalRoomConflictsMode(mode);
                break;
        }
        return conflictsMode;
    }

    /**
     * Get the corresponding enumeration constant from the given integer value. This supports only
     * the original conflict modes without restrictions on availability for the first occurrence.
     *
     * @param mode the configuration mode as an integer
     * @return the configuration mode as an enum constant
     */
    private static RoomConflictsMode getOriginalRoomConflictsMode(final int mode) {
        RoomConflictsMode conflictsMode = null;
        switch (mode) {
            case Constants.CONFLICTS_ALL:
                conflictsMode = RoomConflictsMode.SHOW_ALL;
                break;
            case Constants.CONFLICTS_FILTERED:
                conflictsMode = RoomConflictsMode.SHOW_FILTERED;
                break;
            case Constants.CONFLICTS_ALL_IF_ONLY_CONFLICTS:
                conflictsMode = RoomConflictsMode.SHOW_ALL_IF_ONLY_CONFLICTS;
                break;
            case Constants.CONFLICTS_FILTERED_IF_ONLY_CONFLICTS:
                conflictsMode = RoomConflictsMode.SHOW_FILTERED_IF_ONLY_CONFLICTS;
                break;
            case Constants.CONFLICTS_NONE:
                conflictsMode = RoomConflictsMode.SHOW_NONE;
                break;
            default:
                conflictsMode = RoomConflictsMode.SHOW_FILTERED_IF_ONLY_CONFLICTS;
                break;
        }
        return conflictsMode;
    }
}
