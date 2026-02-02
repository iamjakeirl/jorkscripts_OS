package com.jork.script.jorkHunter.trap;

/**
 * Defines how trap finished states should be handled.
 * BINARY mode treats all finished states (success/failed) as one for simpler, more robust handling.
 * TERNARY mode distinguishes between success and failed states for more precise tracking.
 */
public enum TrapStateHandlingMode {
    /**
     * Treats all finished states (success/failed) as a single FINISHED state.
     * More robust against color detection errors, recommended for most trap types.
     */
    BINARY,
    
    /**
     * Distinguishes between FINISHED_SUCCESS and FINISHED_FAILED states.
     * Useful for trap types where different handling is needed based on outcome.
     */
    TERNARY
}