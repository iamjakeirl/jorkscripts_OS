package com.jork.script.fireGiantWaterStrike;

/**
 * State machine states for the Fire Giant Water Strike combat cycle.
 * States are ordered to match the natural combat flow.
 */
public enum ScriptState {

    /** Initialize runtime/session data and anchor defaults. */
    INIT,

    /** Ensure anchor exists and player is inside anchor tolerance. */
    ENSURE_ANCHOR,

    /** Find next valid fire giant target from safe-spot context. */
    ACQUIRE_TARGET,

    /** Initiate attack on selected fire giant. */
    ENGAGE_TARGET,

    /** Track ongoing combat and update last-known target position. */
    MONITOR_COMBAT,

    /** Re-attack the same locked target UUID after returning to anchor. */
    REENGAGE_LOCKED_TARGET,

    /** Evaluate loot mode, apply inventory policy, dispatch loot. */
    POST_KILL,

    /** Execute telegrab loop from anchor. */
    LOOT_TELEGRAB,

    /** Navigate to loot cluster, pickup, return. */
    LOOT_MANUAL,

    /** Centralized retry backoff + intent reset. */
    RECOVERY,

    /** Terminal state. */
    STOP
}
