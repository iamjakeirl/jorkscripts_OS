package com.jork.script.Ectofuntus.tasks;

import com.jork.script.Ectofuntus.EctofuntusConstants;
import com.jork.script.Ectofuntus.EctofuntusScript;
import com.jork.utils.ExceptionUtils;
import com.jork.utils.JorkTaps;
import com.jork.utils.ScriptLogger;
import com.jork.utils.teleport.TeleportHandlerFactory;
import com.jork.utils.teleport.TeleportResult;
import com.jork.utils.teleport.handlers.EctophialHandler;
import com.osmb.api.input.MenuEntry;
import com.osmb.api.input.MenuHook;
import com.osmb.api.item.ItemGroupResult;
import com.osmb.api.item.ItemSearchResult;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.scene.RSObject;
import com.osmb.api.shape.Polygon;
import com.osmb.api.utils.RandomUtils;
import java.util.List;
import java.util.Set;

/**
 * Handles slime collection in the basement of the Ectofuntus.
 *
 * State machine flow:
 * CHECK_LOCATION → NAVIGATE_TO_BASEMENT → FILL_BUCKETS → VERIFY_SLIME → COMPLETE
 *
 * Note: Ectophial refills automatically after teleporting. The ensureNearAltarStart()
 * method uses pixel analyzer to wait for the refill animation to complete before
 * proceeding with any actions.
 *
 * Priority: 3/4 (randomized with GrindBonesTask)
 *
 * @author jork
 */
public class CollectSlimeTask {

    // ═══════════════════════════════════════════════════════════════════════════
    // State Machine
    // ═══════════════════════════════════════════════════════════════════════════

    private enum State {
        CHECK_LOCATION,
        NAVIGATE_TO_BASEMENT,
        FILL_BUCKETS,
        VERIFY_SLIME,
        COMPLETE
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Instance Fields
    // ═══════════════════════════════════════════════════════════════════════════

    private final EctofuntusScript script;
    private final EctophialHandler ectophialHandler;
    private State currentState = State.CHECK_LOCATION;

    /** Retry counter for failed interactions */
    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    public CollectSlimeTask(EctofuntusScript script) {
        this.script = script;
        this.ectophialHandler = TeleportHandlerFactory.createEctophialHandlerTyped(script.getScript());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Public Interface
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if this task can execute.
     * Executes when we still have empty buckets to fill and aren't banking.
     */
    public boolean canExecute() {
        return !script.shouldBank() &&
            (getInventoryCount(EctofuntusConstants.EMPTY_BUCKET) > 0 || currentState != State.CHECK_LOCATION);
    }

    /**
     * Execute the slime collection task.
     * @return poll delay in milliseconds
     */
    public int execute() {
        switch (currentState) {
            case CHECK_LOCATION:
                return handleCheckLocation();
            case NAVIGATE_TO_BASEMENT:
                return handleNavigateToBasement();
            case FILL_BUCKETS:
                return handleFillBuckets();
            case VERIFY_SLIME:
                return handleVerifySlime();
            case COMPLETE:
                return handleComplete();
            default:
                ScriptLogger.error(script.getScript(), "Unknown CollectSlimeTask state: " + currentState);
                currentState = State.CHECK_LOCATION;
                return 0;
        }
    }

    /**
     * Resets the task state for a new cycle.
     */
    public void reset() {
        currentState = State.CHECK_LOCATION;
        retryCount = 0;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // State Handlers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Checks current location and determines next state.
     */
    private int handleCheckLocation() {
        // IN-PLACE RECOVERY: Check if already at correct location before teleporting
        WorldPosition pos = script.getWorldPosition();
        if (pos != null) {
            // Already in basement near pool - skip teleport, go directly to fill
            if (EctofuntusConstants.isInSlimeDungeon(pos) && isNearPool(pos)) {
                ScriptLogger.info(script.getScript(), "Already in basement near pool - continuing");
                currentState = State.FILL_BUCKETS;
                return 0;
            }
        }

        // Not at correct location - ensure we're at altar start
        if (!ensureNearAltarStart()) {
            return 0;
        }

        // Re-fetch position after potential teleport
        pos = script.getWorldPosition();
        if (pos == null) {
            ScriptLogger.warning(script.getScript(), "Position unavailable");
            return 0;
        }

        int currentPlane = pos.getPlane();
        ScriptLogger.debug(script.getScript(), "CollectSlime: Current plane = " + currentPlane);

        int slimeCount = getInventoryCount(EctofuntusConstants.BUCKET_OF_SLIME);
        int emptyBuckets = getInventoryCount(EctofuntusConstants.EMPTY_BUCKET);
        if (emptyBuckets == 0) {
            if (slimeCount > 0) {
                ScriptLogger.info(script.getScript(), "No empty buckets left, have " + slimeCount + " slime");
                currentState = State.COMPLETE;
                return 0;
            }
            ScriptLogger.warning(script.getScript(), "No empty buckets or slime in inventory!");
            script.setShouldBank(true);
            return 0;
        }

        ScriptLogger.info(script.getScript(), "Need to fill " + emptyBuckets + " buckets with slime");

        // Check if we're in the basement
        if (EctofuntusConstants.isInSlimeDungeon(pos) && isNearPool(pos)) {
            currentState = State.FILL_BUCKETS;
        } else {
            currentState = State.NAVIGATE_TO_BASEMENT;
        }

        return 0;
    }

    /**
     * Navigates to the basement where the Pool of Slime is located.
     * Attempts agility shortcut first if on tier 1 and player has level 58+ agility.
     *
     * Poll patterns:
     * - pollFramesHuman for position change after shortcut [AUDIT: misclassified, should be pollFramesUntil]
     * - pollFramesHuman for plane change (in slime dungeon) [AUDIT: misclassified, should be pollFramesUntil]
     */
    private int handleNavigateToBasement() {
        WorldPosition pos = script.getWorldPosition();
        if (pos == null) {
            ScriptLogger.warning(script.getScript(), "Position unavailable");
            return 0;
        }

        // Already in basement?
        if (EctofuntusConstants.isInSlimeDungeon(pos)) {
            ScriptLogger.info(script.getScript(), "Arrived in basement");
            currentState = State.FILL_BUCKETS;
            return 0;
        }

        // Try agility shortcut if we're on tier 1 and have required level
        if (script.canUseAgilityShortcut() && isOnTier1(pos)) {
            RSObject shortcut = findAgilityShortcut();
            if (shortcut != null) {
                ScriptLogger.actionAttempt(script.getScript(),
                    "Using agility shortcut (level " + script.getPlayerAgilityLevel() + ")");
                boolean interacted = shortcut.interact(EctofuntusConstants.ACTION_CLIMB);

                if (interacted) {
                    // Wait for movement/position change
                    boolean shortcutUsed = script.pollFramesHuman(() -> {
                        WorldPosition newPos = script.getWorldPosition();
                        return newPos != null && !isOnTier1(newPos);
                    }, RandomUtils.uniformRandom(4000, 6000));

                    if (shortcutUsed) {
                        ScriptLogger.actionSuccess(script.getScript(), "Shortcut used - skipped tier 1");
                        retryCount = 0;
                        return 0;
                    }
                }
                // Shortcut failed - fall through to regular stairs
                ScriptLogger.debug(script.getScript(), "Shortcut interaction failed, using stairs");
            }
        }

        // Find stairs/trapdoor to basement (use distance sorting for multi-staircase tiers)
        RSObject stairs = findNearestStaircase(EctofuntusConstants.ACTION_CLIMB_DOWN);
        if (stairs == null) {
            // Try walking closer to expected stair location
            ScriptLogger.navigation(script.getScript(), "Walking toward basement entrance");
            script.getScript().getWalker().walkTo(EctofuntusConstants.STAIRS_TO_BASEMENT_POS);

            if (handleRetryWithEscalation("Finding basement stairs")) {
                script.stop();
            }
            return 0;
        }

        boolean interacted;
        boolean isTrapdoor = stairs.getName() != null &&
            stairs.getName().equalsIgnoreCase(EctofuntusConstants.TRAPDOOR_NAME);

        if (isTrapdoor) {
            ScriptLogger.actionAttempt(script.getScript(), "Using trapdoor to basement");
            interacted = stairs.interact(createTrapdoorMenuHook());
        } else {
            ScriptLogger.actionAttempt(script.getScript(), "Climbing down to basement");
            interacted = stairs.interact(EctofuntusConstants.ACTION_CLIMB_DOWN);
        }

        if (!interacted) {
            if (handleRetryWithEscalation("Stair interaction")) {
                script.stop();
            }
            return 0;
        }

        // Wait for plane change
        boolean planeChanged = script.pollFramesHuman(() -> {
            WorldPosition newPos = script.getWorldPosition();
            return EctofuntusConstants.isInSlimeDungeon(newPos);
        }, RandomUtils.uniformRandom(4000, 6000));

        if (planeChanged) {
            ScriptLogger.actionSuccess(script.getScript(), "Entered basement");
            retryCount = 0;
            currentState = State.FILL_BUCKETS;
        } else {
            if (handleRetryWithEscalation("Plane change after stairs")) {
                script.stop();
            }
        }
        return 0;
    }

    /**
     * Fills empty buckets at the Pool of Slime.
     *
     * Poll patterns:
     * - pollFramesHuman for empty bucket count == 0 (slime fill) -- CORRECT, player watches fill
     */
    private int handleFillBuckets() {
        // Find the pool of slime
        RSObject pool = findPoolOfSlime();
        if (pool == null) {
            WorldPosition pos = script.getWorldPosition();
            if (pos != null && EctofuntusConstants.isInSlimeDungeon(pos)) {
                return navigateDownToPool(pos);
            }
            if (handleRetryWithEscalation("Finding Pool of Slime")) {
                script.stop();
            }
            return 0;
        }

        // Check if we still have empty buckets
        int emptyBuckets = getInventoryCount(EctofuntusConstants.EMPTY_BUCKET);
        if (emptyBuckets == 0) {
            ScriptLogger.info(script.getScript(), "No more empty buckets - verifying slime");
            currentState = State.VERIFY_SLIME;
            return 0;
        }

        // Find empty bucket in inventory for "use item on object" interaction
        ItemSearchResult bucket = findItem(EctofuntusConstants.EMPTY_BUCKET);
        if (bucket == null) {
            ScriptLogger.warning(script.getScript(), "No empty bucket found in inventory");
            currentState = State.VERIFY_SLIME;
            return 0;
        }

        ScriptLogger.actionAttempt(script.getScript(), "Using bucket on Pool of Slime (" + emptyBuckets + " buckets)");

        // Use bucket on pool - click bucket to select it
        boolean selected = bucket.interact("Use");
        if (!selected) {
            if (handleRetryWithEscalation("Selecting empty bucket")) {
                script.stop();
            }
            return 0;
        }

        // Convert slime pool area to screen-space polygon for direct tap
        Polygon poolPolygon = JorkTaps.convertAreaToPolygon(
            script.getScript(),
            EctofuntusConstants.SLIME_POOL_AREA,
            0  // cubeHeight - pool is at ground level
        );

        if (poolPolygon == null) {
            ScriptLogger.warning(script.getScript(), "Slime pool not visible on screen");
            if (handleRetryWithEscalation("Projecting pool to screen")) {
                script.stop();
            }
            return 0;
        }

        // Single tap with "Use" action on game screen (avoids UI elements)
        boolean usedOnPool = script.getScript().getFinger().tapGameScreen(poolPolygon, "Use");

        if (!usedOnPool) {
            if (handleRetryWithEscalation("Using bucket on pool")) {
                script.stop();
            }
            return 0;
        }

        // Wait for buckets to be filled (empty bucket count should reach 0)
        // ignoreTasks=true: suppress break/hop/afk during filling
        boolean filled = script.pollFramesHuman(() -> {
            int currentEmpty = getInventoryCount(EctofuntusConstants.EMPTY_BUCKET);
            return currentEmpty == 0;
        }, RandomUtils.uniformRandom(
            EctofuntusConstants.SLIME_FILL_TIMEOUT_MIN,
            EctofuntusConstants.SLIME_FILL_TIMEOUT_MAX
        ), true);

        if (filled) {
            ScriptLogger.actionSuccess(script.getScript(), "Buckets filled with slime");
            script.setHasSlime(true);
            retryCount = 0;
            currentState = State.VERIFY_SLIME;
        } else {
            if (handleRetryWithEscalation("Bucket filling")) {
                script.stop();
            }
        }
        return 0;
    }

    /**
     * Verifies that we have the expected amount of slime.
     */
    private int handleVerifySlime() {
        int slimeCount = getInventoryCount(EctofuntusConstants.BUCKET_OF_SLIME);
        int emptyBuckets = getInventoryCount(EctofuntusConstants.EMPTY_BUCKET);

        ScriptLogger.info(script.getScript(), "Slime verification: " + slimeCount + " slime buckets, " +
            emptyBuckets + " empty buckets");

        if (slimeCount > 0 && emptyBuckets == 0) {
            // All buckets filled successfully
            ScriptLogger.info(script.getScript(), "Successfully collected " + slimeCount + " buckets of slime");
            currentState = State.COMPLETE;
        } else if (emptyBuckets > 0) {
            // Still have empty buckets - try filling again
            ScriptLogger.warning(script.getScript(), "Still have " + emptyBuckets + " empty buckets - retrying fill");
            currentState = State.FILL_BUCKETS;
        } else if (slimeCount == 0) {
            // No slime and no empty buckets - something went wrong
            ScriptLogger.error(script.getScript(), "No slime collected and no empty buckets - banking");
            script.setShouldBank(true);
            reset();
        } else {
            // Edge case: have some slime but unexpected state
            currentState = State.COMPLETE;
        }

        return 0;
    }

    /**
     * Completes the slime collection task.
     */
    private int handleComplete() {
        int slimeCount = getInventoryCount(EctofuntusConstants.BUCKET_OF_SLIME);
        ScriptLogger.info(script.getScript(), "Slime collection complete: " + slimeCount + " buckets");

        // Set state flag
        script.setHasSlime(true);

        // Reset for next cycle
        reset();

        return 0;
    }

    /**
     * Ensures the player is near the altar start position via ectophial teleport.
     *
     * Poll patterns:
     * - pollFramesHuman for position change after teleport [AUDIT: debatable, should be pollFramesUntil]
     */
    private boolean ensureNearAltarStart() {
        WorldPosition startPos = script.getWorldPosition();
        if (startPos != null && EctofuntusConstants.isNearAltar(startPos)) {
            return true;
        }

        if (!ectophialHandler.canTeleport()) {
            // Use precise area check - isInAltarRegion() would match Port Phasmatys bank (region 14646)
            if (EctofuntusConstants.isAtEctofuntusComplex(startPos)) {
                return true;
            }
            ScriptLogger.warning(script.getScript(), "Cannot ecto teleport - missing ectophial");
            script.setShouldBank(true);
            return false;
        }

        ScriptLogger.actionAttempt(script.getScript(), "Ecto teleporting to altar start position");
        TeleportResult result = ectophialHandler.teleport();
        if (!result.isSuccess()) {
            ScriptLogger.warning(script.getScript(), "Ecto teleport failed: " + result);
            if (result.isFatal()) {
                script.setShouldBank(true);
            }
            return false;
        }

        // Wait for position change - teleport handler already handled visible animation
        boolean posChanged = script.pollFramesUntil(() -> {
            WorldPosition newPos = script.getWorldPosition();
            return newPos != null && !newPos.equals(startPos);
        }, RandomUtils.uniformRandom(500, 1000));

        if (!posChanged) {
            ScriptLogger.debug(script.getScript(), "Position unchanged after teleport - may still be animating");
        }

        return true;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private enum SlimeTier {
        UNKNOWN,
        TIER_1,
        TIER_2,
        TIER_3,
        POOL
    }

    /**
     * Finds the Pool of Slime object.
     */
    private RSObject findPoolOfSlime() {
        return script.getObjectManager().getRSObject(
            obj -> obj.getName() != null &&
                   obj.getName().equalsIgnoreCase(EctofuntusConstants.POOL_OF_SLIME_NAME) &&
                   obj.canReach()
        );
    }

    /**
     * Selects the best trapdoor action based on menu entries.
     * Prefers Climb-down when available, otherwise falls back to Open.
     */
    private MenuHook createTrapdoorMenuHook() {
        return (List<MenuEntry> menuEntries) -> {
            if (menuEntries == null || menuEntries.isEmpty()) {
                return null;
            }

            MenuEntry openFallback = null;
            for (MenuEntry entry : menuEntries) {
                if (entry == null) {
                    continue;
                }

                String action = entry.getAction();
                String entity = entry.getEntityName();
                if (action == null || entity == null) {
                    continue;
                }

                if (!entity.equalsIgnoreCase(EctofuntusConstants.TRAPDOOR_NAME)) {
                    continue;
                }

                if (action.equalsIgnoreCase(EctofuntusConstants.ACTION_CLIMB_DOWN)) {
                    return entry;
                }

                if (action.equalsIgnoreCase("Open")) {
                    openFallback = entry;
                }
            }

            return openFallback;
        };
    }

    /**
     * Finds the nearest staircase with the specified action.
     * Uses getUtils().getClosest() to avoid hand-rolled sorting.
     *
     * @param direction "Climb-down" for descending, "Climb-up" for ascending
     * @return nearest reachable staircase, or null if none found
     */
    private RSObject findNearestStaircase(String direction) {
        WorldPosition currentPos = script.getWorldPosition();
        if (currentPos == null) {
            ScriptLogger.warning(script.getScript(), "Position unavailable for staircase search");
            return null;
        }

        // First try trapdoors (entrance to dungeon)
        List<RSObject> trapdoors = script.getObjectManager().getObjects(obj ->
            obj != null &&
            obj.getName() != null &&
            obj.getName().equalsIgnoreCase(EctofuntusConstants.TRAPDOOR_NAME) &&
            hasAction(obj, "Open", direction) &&
            obj.canReach()
        );

        if (trapdoors != null && !trapdoors.isEmpty()) {
            RSObject closestTrapdoor = getClosestObject(trapdoors);
            if (closestTrapdoor != null) {
                ScriptLogger.debug(script.getScript(), "Found " + trapdoors.size() +
                    " trapdoor(s), using nearest");
                return closestTrapdoor;
            }
        }

        // Try staircases - check both "Stairs" (dungeon) and "Staircase" (altar area)
        List<RSObject> staircases = script.getObjectManager().getObjects(obj ->
            obj != null &&
            obj.getName() != null &&
            (obj.getName().equalsIgnoreCase(EctofuntusConstants.STAIRS_NAME) ||
             obj.getName().equalsIgnoreCase(EctofuntusConstants.STAIRCASE_NAME)) &&
            hasAction(obj, direction) &&
            obj.canReach()
        );

        if (staircases == null || staircases.isEmpty()) {
            return null;
        }

        RSObject closestStairs = getClosestObject(staircases);
        if (closestStairs == null) {
            return null;
        }

        ScriptLogger.debug(script.getScript(), "Found " + staircases.size() +
            " staircase(s), using nearest (distance: " +
            String.format("%.1f", closestStairs.distance(currentPos)) + ")");

        return closestStairs;
    }

    /**
     * Checks if an object has any of the specified actions.
     */
    private boolean hasAction(RSObject obj, String... actions) {
        String[] objActions = obj.getActions();
        if (objActions == null) return false;

        for (String action : actions) {
            if (action == null) continue;
            String actionLower = action.toLowerCase();
            for (String objAction : objActions) {
                if (objAction == null) {
                    continue;
                }
                String objLower = objAction.toLowerCase();
                if (objLower.equals(actionLower) || objLower.startsWith(actionLower + " ")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if the player is near the Pool of Slime.
     */
    private boolean isNearPool(WorldPosition pos) {
        // Check if within ~10 tiles of pool position
        return pos.distanceTo(EctofuntusConstants.POOL_OF_SLIME_POS) <= 10;
    }

    /**
     * Checks if player is on tier 1 of the slime dungeon (near shortcut).
     * @param pos current player position
     * @return true if near tier 1 area
     */
    private boolean isOnTier1(WorldPosition pos) {
        if (pos == null) return false;
        return getSlimeTier(pos) == SlimeTier.TIER_1;
    }

    /**
     * Finds the agility shortcut (weathered wall) if available.
     * @return shortcut object or null if not found/not reachable
     */
    private RSObject findAgilityShortcut() {
        return script.getObjectManager().getRSObject(obj ->
            obj != null &&
            obj.getName() != null &&
            obj.getName().equalsIgnoreCase(EctofuntusConstants.WEATHERED_WALL_NAME) &&
            hasAction(obj, EctofuntusConstants.ACTION_CLIMB) &&
            obj.canReach()
        );
    }

    private SlimeTier getSlimeTier(WorldPosition pos) {
        if (pos == null) {
            return SlimeTier.UNKNOWN;
        }
        if (EctofuntusConstants.isInSlimeDungeon(pos)) {
            int plane = pos.getPlane();
            if (plane == 3) {
                return SlimeTier.TIER_1;
            }
            if (plane == 2) {
                return SlimeTier.TIER_2;
            }
            if (plane == 1) {
                return SlimeTier.TIER_3;
            }
            if (plane == 0) {
                return SlimeTier.POOL;
            }
        }
        if (isNearPool(pos) || EctofuntusConstants.SLIME_POOL_TO_TIER3_BOTTOM.contains(pos)) {
            return SlimeTier.POOL;
        }
        if (EctofuntusConstants.SLIME_TIER3_TO_POOL_TOP.contains(pos)
            || EctofuntusConstants.SLIME_TIER3_TO_TIER2_BOTTOM.contains(pos)
            || EctofuntusConstants.SLIME_TIER3_TO_POOL_TOP.distanceTo(pos) <= 4.0
            || EctofuntusConstants.SLIME_TIER3_TO_TIER2_BOTTOM.distanceTo(pos) <= 4.0) {
            return SlimeTier.TIER_3;
        }
        if (EctofuntusConstants.SLIME_TIER2_TO_TIER3_TOP.contains(pos)
            || EctofuntusConstants.SLIME_TIER2_TO_TIER1_BOTTOM.contains(pos)
            || EctofuntusConstants.SLIME_SHORTCUT_TIER2_BOTTOM.contains(pos)
            || EctofuntusConstants.SLIME_TIER2_TO_TIER3_TOP.distanceTo(pos) <= 4.0
            || EctofuntusConstants.SLIME_TIER2_TO_TIER1_BOTTOM.distanceTo(pos) <= 4.0
            || EctofuntusConstants.SLIME_SHORTCUT_TIER2_BOTTOM.distanceTo(pos) <= 4.0) {
            return SlimeTier.TIER_2;
        }
        if (EctofuntusConstants.SLIME_TIER1_TO_TIER2_TOP.contains(pos)
            || EctofuntusConstants.SLIME_SHORTCUT_TIER1_TOP.contains(pos)
            || EctofuntusConstants.SLIME_TIER1_TO_TIER2_TOP.distanceTo(pos) <= 4.0
            || EctofuntusConstants.SLIME_SHORTCUT_TIER1_TOP.distanceTo(pos) <= 4.0
            || pos.distanceTo(EctofuntusConstants.SLIME_LADDER_TILE) <= 4) {
            return SlimeTier.TIER_1;
        }
        return SlimeTier.UNKNOWN;
    }

    private RSObject findStairsInArea(RectangleArea area, String action) {
        if (area == null || script.getObjectManager() == null) {
            return null;
        }

        List<RSObject> stairs = script.getObjectManager().getObjects(obj ->
            obj != null &&
            obj.getName() != null &&
            obj.getName().equalsIgnoreCase(EctofuntusConstants.STAIRS_NAME) &&
            hasAction(obj, action) &&
            (area.contains(obj.getWorldPosition()) || area.distanceTo(obj.getWorldPosition()) <= 3.0) &&
            obj.canReach()
        );

        if (stairs == null || stairs.isEmpty()) {
            return null;
        }

        return getClosestObject(stairs);
    }

    private RSObject getClosestObject(List<RSObject> objects) {
        if (objects == null || objects.isEmpty()) {
            return null;
        }
        Object closest = script.getScript().getUtils().getClosest(objects);
        if (closest instanceof RSObject) {
            return (RSObject) closest;
        }
        return objects.get(0);
    }

    private int navigateDownToPool(WorldPosition pos) {
        SlimeTier tier = getSlimeTier(pos);
        ScriptLogger.debug(script.getScript(), "Slime tier detected: " + tier);

        if (tier == SlimeTier.POOL) {
            return 0;
        }

        if (tier == SlimeTier.TIER_1) {
            if (script.canUseAgilityShortcut()) {
                RSObject shortcut = findAgilityShortcut();
                if (shortcut != null) {
                    ScriptLogger.actionAttempt(script.getScript(), "Using agility shortcut (tier 1 -> tier 2)");
                    if (shortcut.interact(EctofuntusConstants.ACTION_CLIMB)) {
                        script.pollFramesHuman(() -> {
                            WorldPosition newPos = script.getWorldPosition();
                            return newPos != null && getSlimeTier(newPos) != SlimeTier.TIER_1;
                        }, RandomUtils.uniformRandom(4000, 6000));
                        return 0;
                    }
                }
            }

            RSObject stairs = findStairsInArea(EctofuntusConstants.SLIME_TIER1_TO_TIER2_TOP, EctofuntusConstants.ACTION_CLIMB_DOWN);
            if (stairs != null) {
                ScriptLogger.actionAttempt(script.getScript(), "Climbing down to tier 2");
                if (stairs.interact(EctofuntusConstants.ACTION_CLIMB_DOWN)) {
                    script.pollFramesHuman(() -> {
                        WorldPosition newPos = script.getWorldPosition();
                        return newPos != null && getSlimeTier(newPos) != SlimeTier.TIER_1;
                    }, RandomUtils.uniformRandom(4000, 6000));
                }
                return 0;
            }

            ScriptLogger.navigation(script.getScript(), "Walking toward tier 1 stairs");
            script.getScript().getWalker().walkTo(EctofuntusConstants.SLIME_TIER1_TO_TIER2_TOP.getRandomPosition());
            return 0;
        }

        if (tier == SlimeTier.TIER_2) {
            RSObject stairs = findStairsInArea(EctofuntusConstants.SLIME_TIER2_TO_TIER3_TOP, EctofuntusConstants.ACTION_CLIMB_DOWN);
            if (stairs != null) {
                ScriptLogger.actionAttempt(script.getScript(), "Climbing down to tier 3");
                if (stairs.interact(EctofuntusConstants.ACTION_CLIMB_DOWN)) {
                    script.pollFramesHuman(() -> {
                        WorldPosition newPos = script.getWorldPosition();
                        return newPos != null && getSlimeTier(newPos) != SlimeTier.TIER_2;
                    }, RandomUtils.uniformRandom(4000, 6000));
                }
                return 0;
            }

            ScriptLogger.navigation(script.getScript(), "Walking toward tier 2 stairs");
            script.getScript().getWalker().walkTo(EctofuntusConstants.SLIME_TIER2_TO_TIER3_TOP.getRandomPosition());
            return 0;
        }

        if (tier == SlimeTier.TIER_3) {
            RSObject stairs = findStairsInArea(EctofuntusConstants.SLIME_TIER3_TO_POOL_TOP, EctofuntusConstants.ACTION_CLIMB_DOWN);
            if (stairs != null) {
                ScriptLogger.actionAttempt(script.getScript(), "Climbing down to pool tier");
                if (stairs.interact(EctofuntusConstants.ACTION_CLIMB_DOWN)) {
                    script.pollFramesHuman(() -> {
                        WorldPosition newPos = script.getWorldPosition();
                        return newPos != null && getSlimeTier(newPos) != SlimeTier.TIER_3;
                    }, RandomUtils.uniformRandom(4000, 6000));
                }
                return 0;
            }

            ScriptLogger.navigation(script.getScript(), "Walking toward pool stairs");
            script.getScript().getWalker().walkTo(EctofuntusConstants.SLIME_TIER3_TO_POOL_TOP.getRandomPosition());
            return 0;
        }

        ScriptLogger.navigation(script.getScript(), "Walking toward slime dungeon entry");
        script.getScript().getWalker().walkTo(EctofuntusConstants.TIER_1_CENTER);
        return 0;
    }

    /**
     * Gets the count of an item in inventory.
     */
    private int getInventoryCount(int itemId) {
        try {
            var wm = script.getWidgetManager();
            if (wm == null || wm.getInventory() == null) {
                return 0;
            }
            ItemGroupResult search = wm.getInventory().search(Set.of(itemId));
            return search != null ? search.getAmount(itemId) : 0;
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.debug(script.getScript(), "Error counting item " + itemId + ": " + e.getMessage());
            return 0;
        }
    }

    /**
     * Finds an item in inventory for interaction.
     */
    private ItemSearchResult findItem(int itemId) {
        try {
            var wm = script.getWidgetManager();
            if (wm == null || wm.getInventory() == null) {
                return null;
            }
            ItemGroupResult search = wm.getInventory().search(Set.of(itemId));
            return search != null ? search.getItem(itemId) : null;
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.debug(script.getScript(), "Error finding item " + itemId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Handles retry with escalating log levels.
     * - First failure: WARNING (something went wrong)
     * - Middle retries: DEBUG (only if debug enabled)
     * - Final attempt: WARNING (about to give up)
     * - Exhausted: ERROR (stopping script)
     *
     * @param operation Description of what failed
     * @return true if retries exhausted (should stop), false if can continue
     */
    private boolean handleRetryWithEscalation(String operation) {
        retryCount++;

        if (retryCount == 1) {
            // First failure - warn user something went wrong
            ScriptLogger.warning(script.getScript(),
                operation + " failed, retrying (1/" + MAX_RETRIES + ")");
        } else if (retryCount == MAX_RETRIES) {
            // Final attempt - warn user we're about to give up
            ScriptLogger.warning(script.getScript(),
                operation + " failed " + (MAX_RETRIES - 1) + " times, final attempt...");
        } else {
            // Middle retries - debug only
            ScriptLogger.debug(script.getScript(),
                operation + " failed, retrying (" + retryCount + "/" + MAX_RETRIES + ")");
        }

        if (retryCount >= MAX_RETRIES) {
            ScriptLogger.error(script.getScript(),
                operation + " failed after " + MAX_RETRIES + " attempts");
            return true; // Exhausted
        }

        return false; // Can continue
    }

}
