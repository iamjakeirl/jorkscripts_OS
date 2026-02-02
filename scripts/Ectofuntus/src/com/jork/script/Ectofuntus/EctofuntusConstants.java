package com.jork.script.Ectofuntus;

import com.osmb.api.item.ItemID;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.location.area.impl.RectangleArea;
import java.util.Set;

/**
 * Constants for the Ectofuntus script.
 * Contains item IDs, object names, and positions used across all tasks.
 *
 * @author jork
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

    /** Bonemeal (generic) - result of grinding bones */
    public static final int BONEMEAL = ItemID.BONEMEAL;  // 1854

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

    /** Empty ectophial - needs refilling at Pool of Slime */
    public static final int ECTOPHIAL_EMPTY = ItemID.ECTOPHIAL_4252;  // 4252

    // ═══════════════════════════════════════════════════════════════════════════
    // Region & Planes
    // ═══════════════════════════════════════════════════════════════════════════
    // IMPORTANT: OSRS uses plane 0 for BOTH the altar level and slime dungeon.
    // Plane/Y are NOT reliable for location detection here. Use region IDs instead.
    // ═══════════════════════════════════════════════════════════════════════════

    /** Ectofuntus region ID */
    public static final int ECTOFUNTUS_REGION = 14646;

    /**
     * Altar floor area - covers the main Ectofuntus building ground floor.
     * Used for precise location checks (not region-based) to distinguish from Port Phasmatys.
     * RectangleArea(x, y, width, height, plane)
     */
    public static final RectangleArea ALTAR_FLOOR_AREA = new RectangleArea(3653, 3514, 13, 10, 0);

    /** Slime dungeon region ID (basement map) */
    public static final int SLIME_DUNGEON_REGION = 14746;

    /** Altar + loader regions (ground floor + top floor areas) */
    public static final Set<Integer> ALTAR_REGIONS = Set.of(14646, 14647);

    /**
     * Basement plane - Pool of Slime location.
     * NOTE: Same as ALTAR_PLANE (both 0). Plane/Y are unreliable; use region IDs instead.
     */
    public static final int BASEMENT_PLANE = 0;

    /**
     * Altar plane - Ectofuntus worship location.
     * NOTE: Same as BASEMENT_PLANE (both 0). Plane/Y are unreliable; use region IDs instead.
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
    // TODO: Verify all object names in-game
    // ═══════════════════════════════════════════════════════════════════════════

    /** Pool of Slime in basement - fill buckets here */
    public static final String POOL_OF_SLIME_NAME = "Pool of Slime";  // TODO: Verify name

    /** Ectofuntus altar - worship here to gain Prayer XP */
    public static final String ECTOFUNTUS_ALTAR_NAME = "Ectofuntus";  // TODO: Verify name

    /** Bone loader/hopper on top floor - use bones on this */
    public static final String BONE_HOPPER_NAME = "Loader";  // TODO: Verify name

    /** Staircase object name (altar area stairs to grinder) */
    public static final String STAIRCASE_NAME = "Staircase";  // TODO: Verify name

    /** Stairs object name (slime dungeon stairs - different from altar Staircase) */
    public static final String STAIRS_NAME = "Stairs";

    /** Trapdoor to basement (if different from stairs) */
    public static final String TRAPDOOR_NAME = "Trapdoor";  // TODO: Verify name

    /** Agility shortcut in slime dungeon (level 58) */
    public static final String WEATHERED_WALL_NAME = "Weathered wall";  // TODO: Verify name in-game

    // ═══════════════════════════════════════════════════════════════════════════
    // Interaction Actions
    // TODO: Verify all action names in-game
    // ═══════════════════════════════════════════════════════════════════════════

    /** Action to worship at Ectofuntus */
    public static final String ACTION_WORSHIP = "Worship";  // TODO: Verify action

    /** Action to climb up stairs */
    public static final String ACTION_CLIMB_UP = "Climb-up";  // TODO: Verify action

    /** Action to climb down stairs */
    public static final String ACTION_CLIMB_DOWN = "Climb-down";  // TODO: Verify action

    /** Action to use agility shortcut */
    public static final String ACTION_CLIMB = "Climb";  // TODO: Verify action in-game

    // ═══════════════════════════════════════════════════════════════════════════
    // World Positions
    // TODO: Verify all positions in-game
    // ═══════════════════════════════════════════════════════════════════════════
    // Use these positions with distanceTo() for location detection, not just plane:
    //   pos.distanceTo(POOL_OF_SLIME_POS) <= 10  // Near slime pool (basement)
    //   pos.distanceTo(ECTOFUNTUS_ALTAR_POS) <= 8  // Near altar (ground floor)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Pool of Slime position in basement */
    public static final WorldPosition POOL_OF_SLIME_POS = new WorldPosition(3682, 9888, BASEMENT_PLANE);  // TODO: Verify

    /** Ectofuntus altar position */
    public static final WorldPosition ECTOFUNTUS_ALTAR_POS = new WorldPosition(3659, 3517, ALTAR_PLANE);  // TODO: Verify

    /** Ectofuntus altar tappable area for spam tap interaction */
    public static final RectangleArea ECTOFUNTUS_ALTAR_AREA = new RectangleArea(3659, 3520, 2, 1, 0);

    /** Bone hopper/loader position on top floor */
    public static final WorldPosition BONE_HOPPER_POS = new WorldPosition(3660, 3525, GRINDER_PLANE);

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
     * WARNING: Region 14646 includes Port Phasmatys bank - use isAtEctofuntusComplex() for fallback checks.
     */
    public static boolean isInAltarRegion(WorldPosition pos) {
        return pos != null && ALTAR_REGIONS.contains(pos.getRegionID());
    }

    /**
     * Checks if the player is near the Ectofuntus altar.
     * Must be on altar plane (0), not grinder plane (1) - they're vertically stacked.
     *
     * @param pos The position to check
     * @return true if near altar
     */
    public static boolean isNearAltar(WorldPosition pos) {
        if (!isInAltarRegion(pos)) {
            return false;
        }
        // Must be on altar plane (0), not grinder plane (1)
        // XY distance alone would match both floors since they're stacked
        if (pos.getPlane() != ALTAR_PLANE) {
            return false;
        }
        return pos.distanceTo(ECTOFUNTUS_ALTAR_POS) <= 8;
    }

    /**
     * Checks if a position is anywhere within the Ectofuntus complex (valid for in-place recovery).
     * Unlike isInAltarRegion(), this uses precise area checks to exclude Port Phasmatys bank.
     *
     * Returns TRUE for:
     * - Slime dungeon (region 14746)
     * - Grinder floor (plane 1 near hopper)
     * - Altar floor area (specific rectangle, not region-based)
     *
     * Returns FALSE for:
     * - Port Phasmatys bank (even though it's in region 14646)
     * - Anywhere else
     *
     * @param pos The position to check
     * @return true if at Ectofuntus complex
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

    /** Distance threshold for tier detection (tiles) */
    public static final int TIER_DETECTION_RADIUS = 15;

    // ═══════════════════════════════════════════════════════════════════════════
    // Legacy Staircase Positions (Altar Building)
    // TODO: Verify all staircase positions in-game
    // ═══════════════════════════════════════════════════════════════════════════

    /** Staircase down to basement (from ground floor) */
    public static final WorldPosition STAIRS_TO_BASEMENT_POS = new WorldPosition(3669, 3519, 0);  // TODO: Verify

    /** Staircase to top floor (from ground floor) */
    public static final WorldPosition STAIRS_TO_TOP_FLOOR_POS = new WorldPosition(3666, 3518, 0);

    // ═══════════════════════════════════════════════════════════════════════════
    // Timing Constants
    // ═══════════════════════════════════════════════════════════════════════════

    /** Time per bone for automatic grinding process (ms) - approximately 12 seconds */
    public static final int GRIND_TIME_PER_BONE_MS = 12000;

    /** Timeout for waiting for plane change after using stairs (ms) */
    public static final int PLANE_CHANGE_TIMEOUT = 5000;

    /** Minimum timeout for slime bucket filling (ms) */
    public static final int SLIME_FILL_TIMEOUT_MIN = 22000;

    /** Maximum timeout for slime bucket filling (ms) */
    public static final int SLIME_FILL_TIMEOUT_MAX = 24000;

    // ═══════════════════════════════════════════════════════════════════════════
    // Worship Tap Timing
    // ═══════════════════════════════════════════════════════════════════════════

    /** Minimum delay between worship taps (ms) */
    public static final int WORSHIP_TAP_MIN_DELAY = 37;

    /** Maximum delay between worship taps (ms) */
    public static final int WORSHIP_TAP_MAX_DELAY = 184;

    // ═══════════════════════════════════════════════════════════════════════════
    // Human Variant Worship Timing (Phase 08)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Minimum delay between worship taps - human variant (ms) */
    public static final int WORSHIP_HUMAN_TAP_MIN_DELAY = 150;

    /** Maximum delay between worship taps - human variant (ms) */
    public static final int WORSHIP_HUMAN_TAP_MAX_DELAY = 400;

    // ═══════════════════════════════════════════════════════════════════════════
    // Slime Pool Area (for direct tap interaction - Phase 08)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Slime pool tappable area in basement.
     * RectangleArea(x, y, width, height, plane)
     * Covers the full interactable pool surface for tap randomization.
     */
    public static final RectangleArea SLIME_POOL_AREA = new RectangleArea(3677, 9884, 4, 7, 0);
}
