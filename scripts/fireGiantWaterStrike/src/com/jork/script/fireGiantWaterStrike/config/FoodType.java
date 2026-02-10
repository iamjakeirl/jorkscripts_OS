package com.jork.script.fireGiantWaterStrike.config;

/**
 * Food types for healing during Fire Giant combat.
 * Item IDs and heal amounts sourced from OSRS Wiki.
 */
public enum FoodType {

    TROUT("Trout", 333, 7),
    SALMON("Salmon", 329, 9),
    TUNA("Tuna", 361, 10),
    LOBSTER("Lobster", 379, 12),
    SWORDFISH("Swordfish", 373, 14),
    MONKFISH("Monkfish", 7946, 16),
    SHARK("Shark", 385, 20),
    MANTA_RAY("Manta ray", 391, 22),
    ANGLERFISH("Anglerfish", 13441, 22),
    KARAMBWAN("Karambwan", 3144, 18);

    private final String displayName;
    private final int itemId;
    private final int healAmount;

    FoodType(String displayName, int itemId, int healAmount) {
        this.displayName = displayName;
        this.itemId = itemId;
        this.healAmount = healAmount;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getItemId() {
        return itemId;
    }

    public int getHealAmount() {
        return healAmount;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Get FoodType from display name.
     *
     * @param name the display name to match
     * @return the matching FoodType, or LOBSTER as default fallback
     */
    public static FoodType fromDisplayName(String name) {
        for (FoodType type : values()) {
            if (type.displayName.equals(name)) {
                return type;
            }
        }
        return LOBSTER; // Default fallback
    }
}
