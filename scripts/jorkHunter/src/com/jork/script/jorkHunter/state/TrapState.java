package com.jork.script.jorkHunter.state;

/**
 * Represents the various states a trap can be in during the hunting process.
 */
public enum TrapState {
    /**
     * Trap is currently being laid by the player (transitional state)
     */
    LAYING,
    
    /**
     * Trap is active and waiting for prey (yellow respawn circle visible)
     */
    ACTIVE,
    
    /**
     * Trap has finished (green or red respawn circle visible) - used in BINARY mode
     * Combines both success and failed states for simpler, more robust handling
     */
    FINISHED,
    
    /**
     * Trap has successfully caught prey (green respawn circle visible) - used in TERNARY mode
     */
    FINISHED_SUCCESS,
    
    /**
     * Trap has failed to catch prey (red respawn circle visible) - used in TERNARY mode
     */
    FINISHED_FAILED,
    
    /**
     * Trap has collapsed and needs to be picked up (no respawn circle, collapsed model)
     */
    COLLAPSED,
    
    /**
     * Trap is being reset (chinchompa-specific compound action: check + re-lay)
     * This is a transitional state that occurs when using the "reset" action
     */
    RESETTING,
    
    /**
     * Trap state is unknown or could not be determined
     */
    UNKNOWN
}