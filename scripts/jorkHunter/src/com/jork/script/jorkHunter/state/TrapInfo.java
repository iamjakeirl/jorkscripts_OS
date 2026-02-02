package com.jork.script.jorkHunter.state;

import com.jork.script.jorkHunter.trap.TrapType;
import com.osmb.api.location.position.types.WorldPosition;

/**
 * Immutable record representing the state and metadata of a single trap.
 * Now includes flag-based priority system for action management.
 */
public record TrapInfo(
    WorldPosition position,
    TrapState state,
    TrapType trapType,
    TrapFlags flags,
    long lastUpdated,
    long createdAt,
    long stateChangedAt  // NEW: Timestamp when state last changed
) {
    
    /**
     * Creates a new TrapInfo for a trap that is currently being laid
     */
    public static TrapInfo laying(WorldPosition position, TrapType trapType) {
        long now = System.currentTimeMillis();
        TrapFlags flags = new TrapFlags(TrapFlag.LAYING_IN_PROGRESS);
        return new TrapInfo(position, TrapState.LAYING, trapType, flags, now, now, now);
    }
    
    /**
     * Creates a new TrapInfo with an updated state while preserving creation time and flags
     */
    public TrapInfo withState(TrapState newState) {
        long now = System.currentTimeMillis();
        // Update stateChangedAt when state actually changes
        long newStateChangedAt = (newState != state) ? now : stateChangedAt;
        return new TrapInfo(position, newState, trapType, flags.copy(), now, createdAt, newStateChangedAt);
    }
    
    /**
     * Creates a new TrapInfo with an added flag
     */
    public TrapInfo withFlag(TrapFlag flag) {
        TrapFlags newFlags = flags.copy();
        newFlags.addFlag(flag);
        return new TrapInfo(position, state, trapType, newFlags, System.currentTimeMillis(), createdAt, stateChangedAt);
    }
    
    /**
     * Creates a new TrapInfo without a specific flag
     */
    public TrapInfo withoutFlag(TrapFlag flag) {
        TrapFlags newFlags = flags.copy();
        newFlags.removeFlag(flag);
        return new TrapInfo(position, state, trapType, newFlags, System.currentTimeMillis(), createdAt, stateChangedAt);
    }
    
    /**
     * Creates a new TrapInfo with completely replaced flags
     */
    public TrapInfo withFlags(TrapFlags newFlags) {
        return new TrapInfo(position, state, trapType, newFlags.copy(), System.currentTimeMillis(), createdAt, stateChangedAt);
    }
    
    /**
     * Creates a new TrapInfo with cleared flags
     */
    public TrapInfo withClearedFlags() {
        return new TrapInfo(position, state, trapType, new TrapFlags(), System.currentTimeMillis(), createdAt, stateChangedAt);
    }
    
    /**
     * Returns the age of this trap in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - createdAt;
    }
    
    /**
     * Returns the time since last update in milliseconds
     */
    public long getTimeSinceUpdate() {
        return System.currentTimeMillis() - lastUpdated;
    }
    
    /**
     * Returns the time this trap has been in its current state in milliseconds
     */
    public long getTimeInCurrentState() {
        return System.currentTimeMillis() - stateChangedAt;
    }
    
    /**
     * Checks if this trap is in a finished state (success, failed, or combined FINISHED)
     */
    public boolean isFinished() {
        return state == TrapState.FINISHED ||
               state == TrapState.FINISHED_SUCCESS || 
               state == TrapState.FINISHED_FAILED || 
               state == TrapState.COLLAPSED;
    }
    
    /**
     * Checks if this trap is actionable (has any flags requiring action)
     */
    public boolean isActionable() {
        // A trap is actionable if it has any priority flags set
        return flags.hasAnyFlags() && !flags.hasFlag(TrapFlag.LAYING_IN_PROGRESS);
    }
    
    /**
     * Check if trap has a specific flag
     */
    public boolean hasFlag(TrapFlag flag) {
        return flags.hasFlag(flag);
    }
    
    /**
     * Get the highest priority flag for this trap
     */
    public TrapFlag getPriorityFlag() {
        return flags.getHighestPriorityFlag().orElse(null);
    }
    
    /**
     * Get formatted string showing time in current state
     */
    public String getFormattedStateTime() {
        long ms = getTimeInCurrentState();
        long seconds = ms / 1000;
        if (seconds < 60) {
            return seconds + "s";
        } else {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            return minutes + "m" + remainingSeconds + "s";
        }
    }
}