package com.jork.script.jorkHunter.state;

/**
 * Flags representing actions that need to be taken on a trap.
 * Priority is determined by ordinal position (lower ordinal = higher priority).
 */
public enum TrapFlag {
    EXPEDITE_COLLECTION,    // Priority 0 - Force collection of trap for break/hop (highest priority)
    URGENT_COLLAPSED,       // Priority 1 - Collapsed trap at risk of despawning (>90 seconds)
    CRITICAL_SUCCESS,       // Priority 2 - Successful trap at risk of collapsing (>40 seconds)
    NEEDS_REPOSITIONING,    // Priority 3 - Trap occluded by UI or needs to be moved
    NEEDS_INTERACTION,      // Priority 4 - Trap collapsed and needs to be reset
    READY_FOR_REMOVAL,      // Priority 5 - Trap has caught something and needs to be checked
    PENDING_VERIFICATION,   // Priority 6 - Trap state uncertain, needs verification
    LAYING_IN_PROGRESS;     // Priority 7 - Currently in the process of laying this trap
    
    /**
     * Get the priority of this flag (lower number = higher priority).
     */
    public int getPriority() {
        return ordinal();
    }
    
    /**
     * Check if this flag has higher priority than another flag.
     */
    public boolean hasHigherPriorityThan(TrapFlag other) {
        return this.ordinal() < other.ordinal();
    }
}