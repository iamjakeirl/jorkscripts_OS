package com.jork.script.Ectofuntus.config;

/**
 * Enum representing different bone types for Ectofuntus training.
 * Contains item IDs and display names for each bone type.
 *
 */
public enum BoneType {
    BONES("Bones", 526),
    WOLF_BONES("Wolf Bones", 2859),
    BURNT_BONES("Burnt Bones", 528),
    MONKEY_BONES("Monkey Bones", 3183),
    BAT_BONES("Bat Bones", 530),
    BIG_BONES("Big Bones", 532),
    ZOGRE_BONES("Zogre Bones", 4812),
    SHAIKAHAN_BONES("Shaikahan Bones", 3123),
    BABY_DRAGON_BONES("Baby Dragon Bones", 534),
    WYVERN_BONES("Wyvern Bones", 6812),
    DRAGON_BONES("Dragon Bones", 536),
    FAYRG_BONES("Fayrg Bones", 4830),
    LAVA_DRAGON_BONES("Lava Dragon Bones", 11943),
    RAURG_BONES("Raurg Bones", 4832),
    DAGANNOTH_BONES("Dagannoth Bones", 6729),
    OURG_BONES("Ourg Bones", 4834),
    SUPERIOR_DRAGON_BONES("Superior Dragon Bones", 22124),
    WYRM_BONES("Wyrm Bones", 22780),
    DRAKE_BONES("Drake Bones", 22783),
    HYDRA_BONES("Hydra Bones", 22786);

    private final String displayName;
    private final int itemId;

    BoneType(String displayName, int itemId) {
        this.displayName = displayName;
        this.itemId = itemId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getItemId() {
        return itemId;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Get BoneType from display name
     */
    public static BoneType fromDisplayName(String name) {
        for (BoneType type : values()) {
            if (type.displayName.equals(name)) {
                return type;
            }
        }
        return DRAGON_BONES; // Default fallback
    }

    /**
     * Get all bone item IDs as a Set.
     * Useful for checking if any bone type is present in inventory/bank.
     * @return Set of all bone item IDs
     */
    public static java.util.Set<Integer> getAllItemIds() {
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (BoneType type : values()) {
            ids.add(type.itemId);
        }
        return ids;
    }
}
