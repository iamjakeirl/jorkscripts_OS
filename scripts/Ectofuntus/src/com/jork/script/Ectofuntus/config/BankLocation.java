package com.jork.script.Ectofuntus.config;

/**
 * Enum representing different banking methods for the Ectofuntus script.
 *
 * @author jork
 */
public enum BankLocation {
    VARROCK("Varrock Teleport", -1, false),
    FALADOR("Falador Teleport", -1, false),
    CAMELOT("Camelot Teleport", -1, false),
    AMULET_OF_THE_EYE("Amulet of the Eye", 26914, true),
    WALK_PORT_PHASMATYS("Walk to Port Phasmatys", -1, false);

    private final String displayName;
    private final int itemId;  // -1 for walking (no item required)
    private final boolean isWearable;  // true if teleport can be worn (allows 9 supplies instead of 8)

    BankLocation(String displayName, int itemId, boolean isWearable) {
        this.displayName = displayName;
        this.itemId = itemId;
        this.isWearable = isWearable;
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

    public boolean isWearable() {
        return isWearable;
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
