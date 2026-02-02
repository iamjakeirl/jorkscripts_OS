package com.jork.utils.teleport;

/**
 * Result of a teleport operation attempt.
 * Used by TeleportHandler implementations to indicate operation outcome.
 *
 * @author jork
 */
public enum TeleportResult {

    /** Successfully teleported to destination */
    SUCCESS,

    /** Already at or near the destination - no teleport needed */
    ALREADY_AT_DESTINATION,

    /** Teleport item not found in inventory */
    ITEM_NOT_FOUND,

    /** Item found but interaction failed (click didn't work) */
    INTERACTION_FAILED,

    /** Menu didn't appear or couldn't find the correct menu option */
    MENU_SELECTION_FAILED,

    /** Teleported but didn't arrive at expected destination within timeout */
    ARRIVAL_TIMEOUT,

    /** Teleport item has no charges remaining */
    NO_CHARGES,

    /** This handler doesn't use teleportation (walking handler) */
    NOT_APPLICABLE,

    /** WidgetManager or other API unavailable */
    API_UNAVAILABLE,

    /** Unspecified failure */
    UNKNOWN_FAILURE;

    /**
     * Checks if this result indicates a successful teleport.
     * @return true if teleport succeeded or was unnecessary
     */
    public boolean isSuccess() {
        return this == SUCCESS || this == ALREADY_AT_DESTINATION;
    }

    /**
     * Checks if this result is a fatal failure that should stop retries.
     * @return true if no point retrying (item missing, no charges, etc.)
     */
    public boolean isFatal() {
        return this == ITEM_NOT_FOUND ||
               this == NO_CHARGES ||
               this == API_UNAVAILABLE;
    }
}
