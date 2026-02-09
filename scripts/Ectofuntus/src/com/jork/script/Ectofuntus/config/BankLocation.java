package com.jork.script.Ectofuntus.config;

/**
 * Enum representing different banking methods for the Ectofuntus script.
 *
 * @author jork
 */
public enum BankLocation {
    VARROCK("Varrock Teleport", -1),
    FALADOR("Falador Teleport", -1),
    CAMELOT("Camelot Teleport", -1),
    WALK_PORT_PHASMATYS("Walk to Port Phasmatys", -1);

    private final String displayName;
    private final int itemId;  // -1 for walking (no item required)

    BankLocation(String displayName, int itemId) {
        this.displayName = displayName;
        this.itemId = itemId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getItemId() {
        return itemId;
    }

    public boolean requiresItem() {
        return itemId != -1;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Get BankLocation from display name
     */
    public static BankLocation fromDisplayName(String name) {
        for (BankLocation location : values()) {
            if (location.displayName.equals(name)) {
                return location;
            }
        }
        return VARROCK; // Default fallback
    }
}
