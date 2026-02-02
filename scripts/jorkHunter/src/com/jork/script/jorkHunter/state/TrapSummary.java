package com.jork.script.jorkHunter.state;

import com.osmb.api.location.position.types.WorldPosition;

/**
 * Summary information about a trap, primarily used for prioritization and task dispatch.
 */
public record TrapSummary(
    WorldPosition position,
    TrapFlag priorityFlag,
    TrapState state,
    TrapInfo fullInfo
) {
    /**
     * Create a summary from a TrapInfo instance.
     */
    public static TrapSummary fromTrapInfo(TrapInfo trapInfo) {
        return new TrapSummary(
            trapInfo.position(),
            trapInfo.getPriorityFlag(),
            trapInfo.state(),
            trapInfo
        );
    }
    
    /**
     * Check if this summary has an actionable flag.
     */
    public boolean isActionable() {
        return priorityFlag != null;
    }
    
    /**
     * Check if this trap needs immediate attention (high priority flags).
     */
    public boolean needsImmediateAttention() {
        return priorityFlag == TrapFlag.NEEDS_REPOSITIONING || 
               priorityFlag == TrapFlag.READY_FOR_REMOVAL;
    }
}