package com.jork.script.fireGiantWaterStrike.anchor;

import com.jork.utils.Navigation;
import com.jork.utils.ScriptLogger;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.script.Script;
import com.osmb.api.utils.RandomUtils;

/**
 * Manages the safespot anchor position for the Fire Giant Water Strike script.
 * Stateless helper -- owns position, displacement detection, and walk-back delegation.
 * Retry tracking belongs in the main class's handleEnsureAnchor.
 */
public class AnchorManager {

    /** Player is "at anchor" if within this many tiles. */
    private static final int DISPLACEMENT_TOLERANCE = 1;

    /** Randomized walk-back timeout range in milliseconds. */
    private static final int WALKBACK_TIMEOUT_MIN = 3800;
    private static final int WALKBACK_TIMEOUT_MAX = 6200;

    /** Walk back to exact tile for maximum safespot precision. */
    private static final int WALKBACK_TOLERANCE = 0;

    /** Waterfall Dungeon fire giant safespot (validated in-game 2026-02-10). */
    public static final WorldPosition WATERFALL_SAFESPOT = new WorldPosition(2568, 9893, 0);

    private final Script script;
    private final Navigation navigation;
    private final WorldPosition anchorPosition;

    /**
     * Creates an AnchorManager for the given script and anchor position.
     *
     * @param script         the running script instance
     * @param anchorPosition the safespot tile to anchor to
     */
    public AnchorManager(Script script, WorldPosition anchorPosition) {
        this.script = script;
        this.navigation = new Navigation(script);
        this.anchorPosition = anchorPosition;
    }

    /**
     * Checks whether the player is displaced from the anchor position.
     * Null-safe: returns false if current position is unavailable
     * (avoids false walk-backs during loading screens).
     *
     * @return true if the player is more than {@link #DISPLACEMENT_TOLERANCE} tiles from anchor
     */
    public boolean isDisplaced() {
        WorldPosition currentPos = script.getWorldPosition();
        if (currentPos == null) {
            return false;
        }
        return currentPos.distanceTo(anchorPosition) > DISPLACEMENT_TOLERANCE;
    }

    /**
     * Attempts to walk back to the anchor position using screen-based movement.
     * Delegates to {@link Navigation#simpleMoveTo} with tight tolerance for safespot precision.
     *
     * @return true if the walk-back completed successfully
     */
    public boolean walkBack() {
        int timeoutMs = RandomUtils.uniformRandom(WALKBACK_TIMEOUT_MIN, WALKBACK_TIMEOUT_MAX);
        ScriptLogger.navigation(script, "Walking back to anchor at " + anchorPosition);
        return navigation.simpleMoveToFastTap(anchorPosition, timeoutMs, WALKBACK_TOLERANCE);
    }

    /**
     * Returns the anchor position this manager is tracking.
     *
     * @return the anchor WorldPosition
     */
    public WorldPosition getAnchorPosition() {
        return anchorPosition;
    }
}
