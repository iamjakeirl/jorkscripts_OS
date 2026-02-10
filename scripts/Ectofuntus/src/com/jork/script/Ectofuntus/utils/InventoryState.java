package com.jork.script.Ectofuntus.utils;

/**
 * Inventory states used by recovery and task selection.
 */
public enum InventoryState {
    /**
     * Has enough materials to worship.
     */
    READY_TO_WORSHIP,

    /**
     * Has partial worship materials.
     */
    PARTIAL_WORSHIP_READY,

    /**
     * Has bones and containers ready for processing.
     */
    NEED_PROCESSING,

    /**
     * Has bonemeal but still needs slime.
     */
    NEED_SLIME_ONLY,

    /**
     * Has slime but still needs bonemeal.
     */
    NEED_DUST_ONLY,

    /**
     * Missing required supplies and needs banking.
     */
    NEED_RESTOCK,

    /**
     * Inventory is depleted and needs full restock.
     */
    EMPTY_NEED_BANK,

    /**
     * Could not classify current inventory.
     */
    UNKNOWN
}
