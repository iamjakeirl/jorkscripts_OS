package com.jork.script.Ectofuntus.utils;

/**
 * Enum representing the player's current location state in the Ectofuntus activity.
 * Used for context-aware state detection and recovery decisions.
 *
 * @author jork
 */
public enum LocationState {
    /**
     * Near a bank location (check distance to configured bank position).
     * Indicates the player is at a banking area and can deposit/withdraw items.
     */
    AT_BANK,

    /**
     * On altar plane and near altar position.
     * Indicates the player is at the Ectofuntus altar and can worship.
     */
    AT_ALTAR,

    /**
     * On grinder plane and near hopper position.
     * Indicates the player is at the bone grinder and can process bones into bonemeal.
     */
    AT_GRINDER,

    /**
     * On basement plane and near pool position.
     * Indicates the player is at the Pool of Slime and can fill buckets.
     */
    AT_BASEMENT,

    /**
     * Cannot determine location - not near any known position.
     * May indicate an unexpected location or the player is in transit between locations.
     */
    UNKNOWN
}
