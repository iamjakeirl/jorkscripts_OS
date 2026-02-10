package com.jork.script.Ectofuntus;

import com.osmb.api.item.ItemID;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.location.area.impl.RectangleArea;
import java.util.Set;

/**
 * Constants for the Ectofuntus script.
 */
public final class EctofuntusConstants {

    private EctofuntusConstants() {
        // Utility class - no instantiation
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Item IDs
    // ═══════════════════════════════════════════════════════════════════════════

    /** Empty bucket - to be filled with slime */
    public static final int EMPTY_BUCKET = ItemID.EMPTY_BUCKET;  // 1925

    /** Bucket of slime - filled from Pool of Slime */
    public static final int BUCKET_OF_SLIME = ItemID.BUCKET_OF_SLIME;  // 4286

    /** Empty pot - for collecting bonemeal */
    public static final int EMPTY_POT = ItemID.POT;  // 1931

    /** All bonemeal variants (used for state detection/recovery) */
    public static final Set<Integer> BONEMEAL_IDS = Set.of(
        ItemID.BONEMEAL,
        ItemID.ALANS_BONEMEAL,
        ItemID.BAT_BONEMEAL,
        ItemID.BABY_DRAGON_BONEMEAL,
        ItemID.BABY_DRAGON_BONEMEAL_28984,
        ItemID.BEARDED_GORILLA_BONEMEAL,
        ItemID.BIG_BONEMEAL,
        ItemID.BURNT_BONEMEAL,
        ItemID.BURNT_JOGRE_BONEMEAL,
        ItemID.DAGANNOTHKING_BONEMEAL,
        ItemID.DRAKE_BONEMEAL,
        ItemID.DRAGON_BONEMEAL,
        ItemID.FAYRG_BONEMEAL,
        ItemID.GORILLA_BONEMEAL,
        ItemID.HYDRA_BONEMEAL,
        ItemID.JOGRE_BONEMEAL,
        ItemID.LARGE_ZOMBIE_MONKEY_BONEMEAL,
        ItemID.LAVA_DRAGON_BONEMEAL,
        ItemID.MEDIUM_NINJA_BONEMEAL,
        ItemID.MONKEY_BONEMEAL,
        ItemID.OURG_BONEMEAL,
        ItemID.RAURG_BONEMEAL,
        ItemID.SHAIKAHAN_BONEMEAL,
        ItemID.SKELETON_BONEMEAL,
        ItemID.SMALL_NINJA_BONEMEAL,
        ItemID.SMALL_ZOMBIE_MONKEY_BONEMEAL,
        ItemID.SUPERIOR_DRAGON_BONEMEAL,
        ItemID.WOLF_BONEMEAL,
        ItemID.WYRM_BONEMEAL,
        ItemID.WYVERN_BONEMEAL,
        ItemID.ZOGRE_BONEMEAL
    );

    /** Full ectophial - for teleporting */
    public static final int ECTOPHIAL = ItemID.ECTOPHIAL;  // 4251

    /** Rune pouch item ID */
    public static final int RUNE_POUCH = ItemID.RUNE_POUCH;

    /** Divine rune pouch item ID */
    public static final int DIVINE_RUNE_POUCH = ItemID.DIVINE_RUNE_POUCH;

    /** Accepted rune pouch item IDs for rune pouch mode */
    public static final Set<Integer> RUNE_POUCH_IDS = Set.of(RUNE_POUCH, DIVINE_RUNE_POUCH);

    /** Ecto-token item ID (collected from Ghost Disciple) */
    public static final int ECTO_TOKEN = ItemID.ECTOTOKEN;

    // ═══════════════════════════════════════════════════════════════════════════
    // Region & Planes
    // ═══════════════════════════════════════════════════════════════════════════
    // Plane 0 is shared by the altar level and slime dungeon map layer.
    // ═══════════════════════════════════════════════════════════════════════════

    /** Ectofuntus region ID */
    public static final int ECTOFUNTUS_REGION = 14646;

    /**
     * Altar floor area used to distinguish Ectofuntus from Port Phasmatys bank.
     */
    public static final RectangleArea ALTAR_FLOOR_AREA = new RectangleArea(3653, 3514, 13, 10, 0);

    /** Slime dungeon region ID (basement map) */
    public static final int SLIME_DUNGEON_REGION = 14746;

    /** Altar + loader regions (ground floor + top floor areas) */
    public static final Set<Integer> ALTAR_REGIONS = Set.of(14646, 14647);

    /**
     * Basement plane.
     */
    public static final int BASEMENT_PLANE = 0;

    /**
     * Altar plane.
     */
    public static final int ALTAR_PLANE = 0;

    /** Top floor plane - Bone grinder/loader location (plane 1, above ground floor) */
    public static final int GRINDER_PLANE = 1;

    // ═══════════════════════════════════════════════════════════════════════════
    // Agility Shortcut Level
    // ═══════════════════════════════════════════════════════════════════════════

    /** Agility level required for weathered wall shortcut (tier 1 to tier 2) */
    public static final int AGILITY_SHORTCUT_LEVEL = 58;

    // ═══════════════════════════════════════════════════════════════════════════
    // Object Names
    // ═══════════════════════════════════════════════════════════════════════════

    /** Pool of Slime in basement - fill buckets here */
    public static final String POOL_OF_SLIME_NAME = "Pool of Slime";

    /** Ectofuntus altar - worship here to gain Prayer XP */
    public static final String ECTOFUNTUS_ALTAR_NAME = "Ectofuntus";

    /** Bone loader/hopper on top floor - use bones on this */
    public static final String BONE_HOPPER_NAME = "Loader";

    /** Staircase object name (altar area stairs to grinder) */
    public static final String STAIRCASE_NAME = "Staircase";

    /** Stairs object name (slime dungeon stairs - different from altar Staircase) */
    public static final String STAIRS_NAME = "Stairs";

    /** Trapdoor to basement (if different from stairs) */
    public static final String TRAPDOOR_NAME = "Trapdoor";

    /** Agility shortcut in slime dungeon (level 58) */
    public static final String WEATHERED_WALL_NAME = "Weathered wall";

    // ═══════════════════════════════════════════════════════════════════════════
    // Interaction Actions
    // ═══════════════════════════════════════════════════════════════════════════

    /** Action to worship at Ectofuntus */
    public static final String ACTION_WORSHIP = "Worship";

    /** Action to climb up stairs */
    public static final String ACTION_CLIMB_UP = "Climb-up";

    /** Action to climb down stairs */
    public static final String ACTION_CLIMB_DOWN = "Climb-down";

    /** Action to use agility shortcut (weathered wall) */
    public static final String ACTION_JUMP_DOWN = "Jump-down";

    // ═══════════════════════════════════════════════════════════════════════════
    // World Positions
    // ═══════════════════════════════════════════════════════════════════════════
    /** Pool of Slime position in basement */
    public static final WorldPosition POOL_OF_SLIME_POS = new WorldPosition(3682, 9888, BASEMENT_PLANE);

    /** Ectofuntus altar position */
    public static final WorldPosition ECTOFUNTUS_ALTAR_POS = new WorldPosition(3659, 3517, ALTAR_PLANE);

    /** Ectofuntus altar tappable area for spam tap interaction */
    public static final RectangleArea ECTOFUNTUS_ALTAR_AREA = new RectangleArea(3659, 3520, 2, 1, 0);

    /** Bone hopper/loader position on top floor */
    public static final WorldPosition BONE_HOPPER_POS = new WorldPosition(3660, 3526, GRINDER_PLANE);

    /** Tier 1 approximate center (near entrance/ladder) */
    public static final WorldPosition TIER_1_CENTER = new WorldPosition(3670, 9888, 0);

    // Slime dungeon staircase/shortcut areas
    public static final RectangleArea SLIME_TIER1_TO_TIER2_TOP = new RectangleArea(3690, 9887, 1, 2, 3);
    public static final RectangleArea SLIME_TIER2_TO_TIER1_BOTTOM = new RectangleArea(3689, 9887, 2, 2, 2);
    public static final RectangleArea SLIME_TIER2_TO_TIER3_TOP = new RectangleArea(3672, 9887, 0, 2, 2);
    public static final RectangleArea SLIME_TIER3_TO_TIER2_BOTTOM = new RectangleArea(3684, 9888, 1, 0, 0);
    public static final RectangleArea SLIME_TIER3_TO_POOL_TOP = new RectangleArea(3686, 9887, 0, 1, 1);
    public static final RectangleArea SLIME_POOL_TO_TIER3_BOTTOM = new RectangleArea(3684, 9887, 2, 2, 0);
    public static final RectangleArea SLIME_SHORTCUT_TIER1_TOP = new RectangleArea(3670, 9888, 0, 1, 3);
    public static final RectangleArea SLIME_SHORTCUT_TIER2_BOTTOM = new RectangleArea(3669, 9889, 1, 0, 2);

    /** Ladder tile leading back to altar area from slime dungeon */
    public static final WorldPosition SLIME_LADDER_TILE = new WorldPosition(3668, 9888, 3);

    /**
     * Checks if a position is within the slime dungeon region.
     */
    public static boolean isInSlimeDungeon(WorldPosition pos) {
        return pos != null && pos.getRegionID() == SLIME_DUNGEON_REGION;
    }

    /**
     * Checks if a position is within the altar/loader regions.
     */
    public static boolean isInAltarRegion(WorldPosition pos) {
        return pos != null && ALTAR_REGIONS.contains(pos.getRegionID());
    }

    /**
     * Checks if the player is near the Ectofuntus altar.
     * Requires altar plane because altar and grinder overlap on X/Y.
     */
    public static boolean isNearAltar(WorldPosition pos) {
        if (!isInAltarRegion(pos)) {
            return false;
        }
        // Altar and grinder share X/Y coordinates on different planes.
        if (pos.getPlane() != ALTAR_PLANE) {
            return false;
        }
        return pos.distanceTo(ECTOFUNTUS_ALTAR_POS) <= 8;
    }

    /**
     * Checks if a position is within the Ectofuntus complex.
     */
    public static boolean isAtEctofuntusComplex(WorldPosition pos) {
        if (pos == null) {
            return false;
        }

        // 1. In slime dungeon (separate region 14746) - always valid
        if (isInSlimeDungeon(pos)) {
            return true;
        }

        // 2. On grinder floor (plane 1) near the hopper
        if (pos.getPlane() == GRINDER_PLANE && pos.distanceTo(BONE_HOPPER_POS) <= 10) {
            return true;
        }

        // 3. In the altar floor area (precise rectangle, excludes Port Phasmatys)
        if (ALTAR_FLOOR_AREA.contains(pos)) {
            return true;
        }

        return false;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Legacy Staircase Positions (Altar Building)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Staircase down to basement (from ground floor) */
    public static final WorldPosition STAIRS_TO_BASEMENT_POS = new WorldPosition(3669, 3519, 0);

    /** Staircase to top floor (from ground floor) */
    public static final WorldPosition STAIRS_TO_TOP_FLOOR_POS = new WorldPosition(3666, 3518, 0);

    /** Port Phasmatys Energy Barrier gate position (south of altar) */
    public static final WorldPosition PORT_PHASMATYS_GATE_POS = new WorldPosition(3657, 3516, 0);

    // ═══════════════════════════════════════════════════════════════════════════
    // Timing Constants
    // ═══════════════════════════════════════════════════════════════════════════

    /** Ecto tokens awarded per bonemeal worshipped */
    public static final int ECTO_TOKENS_PER_BONE = 5;

    /** Time per bone for automatic grinding process (ms) - approximately 12 seconds */
    public static final int GRIND_TIME_PER_BONE_MS = 12000;

    /** Minimum timeout for slime bucket filling (ms) */
    public static final int SLIME_FILL_TIMEOUT_MIN = 22000;

    /** Maximum timeout for slime bucket filling (ms) */
    public static final int SLIME_FILL_TIMEOUT_MAX = 24000;

    // ═══════════════════════════════════════════════════════════════════════════
    // Worship Tap Timing
    // ═══════════════════════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════════════════════
    // Worship Timing
    // ═══════════════════════════════════════════════════════════════════════════

    /** Minimum delay between worship taps - human variant (ms) */
    public static final int WORSHIP_HUMAN_TAP_MIN_DELAY = 150;

    /** Maximum delay between worship taps - human variant (ms) */
    public static final int WORSHIP_HUMAN_TAP_MAX_DELAY = 400;

    // ═══════════════════════════════════════════════════════════════════════════
    // Slime Pool Area
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Slime pool tappable area in basement.
     * RectangleArea(x, y, width, height, plane)
     * Covers the full interactable pool surface for tap randomization.
     */
    public static final RectangleArea SLIME_POOL_AREA = new RectangleArea(3677, 9884, 4, 7, 0);
}
