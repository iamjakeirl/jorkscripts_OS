package com.jork.script.jorkHunter;

/**
 * A central repository for all region IDs related to the JorkHunter script.
 * These regions are used to prioritize loading based on the selected creature type.
 */
public final class HunterLocationConstants {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private HunterLocationConstants() {}

    /**
     * All region IDs where bird hunting is possible.
     * Used when "Birds" is selected as the target.
     */
    public static final int[] BIRD_REGIONS = {
        6197,   // Kourend Woodland (Copper Longtail)
        8492,   // Isle of Souls (Crimson Swift)
        8750,   // Isle of Souls (Copper Longtail)
        10285,  // Feldip Hunter Area (Crimson Swift)
        10029,  // Feldip Hunter Area (Tropical Wagtail)
        14638   // Tlati Rainforest (Tropical Wagtail)
    };
    
    /**
     * All region IDs where chinchompa hunting is possible.
     * Used when "Chinchompas" is selected as the target.
     */
    public static final int[] CHINCHOMPA_REGIONS = {
        8494, 8493, 10029, 10285, 9272, 9271, 5942, 9013, 5169, 4912, 12602, 12603, 10057, 10129, 4912, 4913, 4914, 5168, 5169, 5170, 5424, 5425, 5426
    };
} 