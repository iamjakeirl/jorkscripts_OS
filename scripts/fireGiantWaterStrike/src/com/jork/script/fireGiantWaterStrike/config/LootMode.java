package com.jork.script.fireGiantWaterStrike.config;

/**
 * Loot collection strategy for post-kill item pickup.
 */
public enum LootMode {

    TELEGRAB("Telegrab"),
    MANUAL_PICKUP("Manual Pickup"),
    XP_ONLY("XP Only");

    private final String displayName;

    LootMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Get LootMode from display name.
     *
     * @param name the display name to match
     * @return the matching LootMode, or TELEGRAB as default fallback
     */
    public static LootMode fromDisplayName(String name) {
        for (LootMode mode : values()) {
            if (mode.displayName.equals(name)) {
                return mode;
            }
        }
        return TELEGRAB; // Default fallback
    }
}
