package com.jork.script.Ectofuntus.utils;

/**
 * Location states used by recovery and task selection.
 */
public enum LocationState {
    /**
     * Near the configured bank.
     */
    AT_BANK,

    /**
     * At the altar.
     */
    AT_ALTAR,

    /**
     * At the grinder floor.
     */
    AT_GRINDER,

    /**
     * In the slime basement.
     */
    AT_BASEMENT,

    /**
     * Could not classify current location.
     */
    UNKNOWN
}
