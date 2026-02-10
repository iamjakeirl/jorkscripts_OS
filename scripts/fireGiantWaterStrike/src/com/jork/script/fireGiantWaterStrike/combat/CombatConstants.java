package com.jork.script.fireGiantWaterStrike.combat;

import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.visual.PixelCluster;
import com.osmb.api.visual.SearchablePixel;
import com.osmb.api.visual.color.ColorModel;
import com.osmb.api.visual.color.tolerance.impl.SingleThresholdComparator;

/**
 * All combat-related constants for Fire Giant Water Strike.
 * Calibration-dependent values are marked with TODO comments.
 */
public final class CombatConstants {

    private CombatConstants() {
        // Non-instantiable constants class
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Spatial Constants
    // ───────────────────────────────────────────────────────────────────────────

    /** 3x4 tile zone covering the two target fire giant spawns (validated in-game 2026-02-10). */
    public static final RectangleArea FIRE_GIANT_AREA = new RectangleArea(2567, 9887, 3, 4, 0);

    /** Tile cube height for 2x2 fire giants (2x standard 130). */
    // TODO: Calibrate in-game -- fire giants are 2x2 NPCs
    public static final int FIRE_GIANT_CUBE_HEIGHT = 260;

    // ───────────────────────────────────────────────────────────────────────────
    // Pixel Constants
    // ───────────────────────────────────────────────────────────────────────────

    /** Fire giant HSL color profile for pixel-based detection. */
    // TODO: CALIBRATE IN-GAME -- sample fire giant colors with OSMB debug tools
    public static final SearchablePixel[] FIRE_GIANT_PIXELS = new SearchablePixel[] {
        new SearchablePixel(-2039584, new SingleThresholdComparator(6), ColorModel.HSL),
        new SearchablePixel(-3158064, new SingleThresholdComparator(6), ColorModel.HSL),
        new SearchablePixel(-4276544, new SingleThresholdComparator(6), ColorModel.HSL),
        new SearchablePixel(-1447446, new SingleThresholdComparator(6), ColorModel.HSL),
    };

    /** Standard shrink factor for NPC tile cubes before pixel analysis. */
    public static final double CUBE_RESIZE_FACTOR = 0.5;

    // ───────────────────────────────────────────────────────────────────────────
    // Tagged NPC Cluster Constants (cyan highlight -- same colors as all tagged NPCs)
    // ───────────────────────────────────────────────────────────────────────────

    /** Fire giant detection pixel profile (calibrated). */
    public static final SearchablePixel[] TAGGED_NPC_PIXELS = new SearchablePixel[] {
        new SearchablePixel(-14155777, new SingleThresholdComparator(3), ColorModel.HSL),
    };

    /** Cluster query for fire giant detection. */
    public static final PixelCluster.ClusterQuery TAGGED_NPC_CLUSTER =
        new PixelCluster.ClusterQuery(10, 3, TAGGED_NPC_PIXELS);

    /** Fire giant cluster query for single-tile verification taps. */
    public static final PixelCluster.ClusterQuery TAGGED_NPC_TILE_CLUSTER =
        new PixelCluster.ClusterQuery(10, 3, TAGGED_NPC_PIXELS);

    /**
     * Calibrated Water Strike projectile color profile.
     */
    public static final SearchablePixel[] WATER_STRIKE_PROJECTILE_PIXELS = new SearchablePixel[] {
        new SearchablePixel(-12770587, new SingleThresholdComparator(3), ColorModel.HSL),
        new SearchablePixel(-10991642, new SingleThresholdComparator(3), ColorModel.HSL),
        new SearchablePixel(-9543450, new SingleThresholdComparator(3), ColorModel.HSL),
        new SearchablePixel(-7042565, new SingleThresholdComparator(3), ColorModel.HSL),
        new SearchablePixel(-3685889, new SingleThresholdComparator(3), ColorModel.HSL),
    };

    /** Cluster query for first-cast projectile detection near the player. */
    public static final PixelCluster.ClusterQuery WATER_STRIKE_PROJECTILE_CLUSTER =
        new PixelCluster.ClusterQuery(10, 3, WATER_STRIKE_PROJECTILE_PIXELS);

    // ───────────────────────────────────────────────────────────────────────────
    // NPC Tracker Constants (adapted from CharacterTracker)
    // ───────────────────────────────────────────────────────────────────────────

    /** Maximum NPC movement speed in tiles per game tick. */
    public static final double MAX_TILES_PER_TICK = 5.0;

    /** Time-to-live for unmatched tracked NPCs before eviction (ms). */
    public static final long NPC_TTL_MS = 1800L;

    /** Game tick duration in milliseconds. */
    public static final double TICK_MS = 600.0;

    /** Exponential smoothing weight for new velocity measurement. */
    public static final double VELOCITY_WEIGHT_NEW = 0.8;

    /** Exponential smoothing weight for prior velocity estimate. */
    public static final double VELOCITY_WEIGHT_OLD = 0.2;

    /** Movement threshold below which an NPC is considered stationary. */
    public static final double STILL_THRESHOLD = 0.05;

    // ───────────────────────────────────────────────────────────────────────────
    // Kill Detection Timing Constants
    // ───────────────────────────────────────────────────────────────────────────

    /** Minimum sustained player idle time before considering a kill (ms). */
    public static final long SUSTAINED_IDLE_MS = 1500L;

    /** Maximum time to wait for kill confirmation after idle detected (ms). */
    public static final long KILL_CONFIRM_TIMEOUT_MS = 4000L;

    /** Maximum time player can be idle during combat before forced recovery (ms). */
    public static final long MAX_COMBAT_IDLE_MS = 6000L;

    /** Fraction of recent frames where player must be animating to count as "in combat". */
    public static final double PLAYER_ANIMATING_THRESHOLD = 0.15;

    /** Time window for first-cast confirmation after attack tap (ms). */
    public static final int FIRST_CAST_CONFIRM_MIN_MS = 1600;
    public static final int FIRST_CAST_CONFIRM_MAX_MS = 2600;

    /** 3x3 tile scan centered on player for first-cast projectile detection. */
    public static final int FIRST_CAST_SCAN_RADIUS_TILES = 1;

    /** Extra vertical pad for near-player projectile scan bounds. */
    public static final int FIRST_CAST_SCAN_VERTICAL_PAD = 120;

    /** Extra horizontal pad for near-player projectile scan bounds. */
    public static final int FIRST_CAST_SCAN_HORIZONTAL_PAD = 40;

    /** Maximum target UUID staleness allowed for locked re-engage (ms). */
    public static final long LOCKED_TARGET_STALE_TIMEOUT_MS = 3600L;

    /** Maximum retry attempts for locked UUID re-engage before fallback reacquire. */
    public static final int REENGAGE_MAX_ATTEMPTS = 4;

    /** Delay range between locked re-engage retries (ms). */
    public static final int REENGAGE_RETRY_MIN_MS = 180;
    public static final int REENGAGE_RETRY_MAX_MS = 420;

    // ───────────────────────────────────────────────────────────────────────────
    // HealthOverlay Constants
    // ───────────────────────────────────────────────────────────────────────────

    /** Randomized timeout range for waiting HealthOverlay visibility after attack tap (ms). */
    public static final int HEALTH_OVERLAY_VISIBLE_TIMEOUT_MIN_MS = 2400;
    public static final int HEALTH_OVERLAY_VISIBLE_TIMEOUT_MAX_MS = 3800;

    /** Randomized grace range before treating missing HealthOverlay as meaningful (ms). */
    public static final int OVERLAY_DISAPPEAR_GRACE_MIN_MS = 800;
    public static final int OVERLAY_DISAPPEAR_GRACE_MAX_MS = 1500;

    /** Time without HP change before treating combat as stalled (ms). */
    public static final long HP_STALE_TIMEOUT_MS = 8000L;

    /** Maximum time to wait for overall kill via HP monitoring (ms). Longer than goblin -- fire giants are tanky. */
    public static final long HP_KILL_TIMEOUT_MS = 30000L;

    /** Randomized timeout range to confirm a food-eat actually applied (ms). */
    public static final int EAT_CONFIRM_MIN_MS = 1200;
    public static final int EAT_CONFIRM_MAX_MS = 2200;

    // ───────────────────────────────────────────────────────────────────────────
    // Resource Check Constants
    // ───────────────────────────────────────────────────────────────────────────

    /** Minimum interval between resource inventory checks (ms). */
    public static final int RESOURCE_CHECK_MIN_MS = 2000;

    /** Maximum interval between resource inventory checks (ms). */
    public static final int RESOURCE_CHECK_MAX_MS = 4000;

    // ───────────────────────────────────────────────────────────────────────────
    // XP Failsafe Constants
    // ───────────────────────────────────────────────────────────────────────────

    /** Seconds before XP failsafe timeout to start logging warnings. */
    public static final long XP_FAILSAFE_WARNING_SECONDS = 60;

    // ───────────────────────────────────────────────────────────────────────────
    // Recovery Constants
    // ───────────────────────────────────────────────────────────────────────────

    /** Maximum number of global retries before the script gives up and stops. */
    public static final int MAX_GLOBAL_RETRIES = 5;

    /** Base delay for exponential backoff in recovery (ms). */
    public static final int RECOVERY_BACKOFF_BASE_MS = 2000;

    /** Minimum variance/decay for exponential random backoff (ms). */
    public static final int RECOVERY_BACKOFF_DECAY_MS = 500;

    /** Maximum backoff cap for exponential random (ms). */
    public static final int RECOVERY_BACKOFF_MAX_MS = 8000;
}
