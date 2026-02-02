package com.jork.script.Ectofuntus.utils;

/**
 * Enum representing the player's current inventory state in the Ectofuntus activity.
 * Used for context-aware state detection and recovery decisions.
 *
 * Key insight: bone count + bone dust count together represent "bones processed".
 * This allows smart recovery from partial states after logout/relog or manual intervention.
 *
 * @author jork
 */
public enum InventoryState {
    /**
     * Has bonemeal AND bucket of slime (can worship now).
     * Ready to complete full worship cycle at Ectofuntus altar.
     */
    READY_TO_WORSHIP,

    /**
     * Has some bonemeal AND some slime (partial progress).
     * Can complete partial worship cycle, then may need to bank.
     */
    PARTIAL_WORSHIP_READY,

    /**
     * Has bones but no dust yet (need to grind).
     * Should navigate to bone grinder to process bones.
     */
    NEED_PROCESSING,

    /**
     * Has dust but no slime (need to collect slime).
     * Should navigate to Pool of Slime in basement.
     */
    NEED_SLIME_ONLY,

    /**
     * Has slime but no dust (need to grind bones).
     * Should navigate to bone grinder to process bones.
     */
    NEED_DUST_ONLY,

    /**
     * Missing critical supplies (need to bank).
     * Has some materials but missing essential items (empty pots/buckets).
     */
    NEED_RESTOCK,

    /**
     * Inventory depleted (need full restock).
     * No worship-ready materials in inventory.
     */
    EMPTY_NEED_BANK,

    /**
     * Cannot determine state.
     * May indicate missing configuration or unexpected inventory state.
     */
    UNKNOWN
}
