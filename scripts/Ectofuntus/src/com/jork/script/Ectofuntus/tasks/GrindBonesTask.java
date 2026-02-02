package com.jork.script.Ectofuntus.tasks;

import com.jork.script.Ectofuntus.EctofuntusConstants;
import com.jork.script.Ectofuntus.EctofuntusScript;
import com.jork.script.Ectofuntus.config.BoneType;
import com.jork.script.Ectofuntus.config.EctoConfig;
import com.jork.utils.ExceptionUtils;
import com.jork.utils.ScriptLogger;
import com.jork.utils.teleport.TeleportHandlerFactory;
import com.jork.utils.teleport.TeleportResult;
import com.jork.utils.teleport.handlers.EctophialHandler;
import com.osmb.api.input.MenuEntry;
import com.osmb.api.input.MenuHook;
import com.osmb.api.item.ItemGroupResult;
import com.osmb.api.item.ItemSearchResult;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.scene.RSObject;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles bone grinding on the top floor of the Ectofuntus.
 *
 * State machine flow:
 * CHECK_LOCATION → NAVIGATE_TO_TOP_FLOOR → USE_BONES_ON_HOPPER → WAIT_FOR_GRINDING → COMPLETE
 *
 * Key mechanic: Using bones on the hopper starts grinding ALL bones automatically.
 * Grinding is complete when empty pot count reaches 0 (pots are the limiting factor).
 * Dynamic timeout is calculated based on bone count (8s per bone + 10s buffer).
 *
 * Supports mixed bones mode: when useAllBonesInTab is enabled, processes all bone types.
 *
 * Priority: 3/4 (randomized with CollectSlimeTask)
 *
 * @author jork
 */
public class GrindBonesTask {

    // ═══════════════════════════════════════════════════════════════════════════
    // State Machine
    // ═══════════════════════════════════════════════════════════════════════════

    private enum State {
        CHECK_LOCATION,
        NAVIGATE_TO_TOP_FLOOR,
        USE_BONES_ON_HOPPER,
        WAIT_FOR_GRINDING,
        COMPLETE
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Instance Fields
    // ═══════════════════════════════════════════════════════════════════════════

    private final EctofuntusScript script;
    private final EctophialHandler ectophialHandler;
    private State currentState = State.CHECK_LOCATION;

    /** Expected number of bonemeal pots when grinding is complete */
    private int expectedBonemealCount = 0;

    /** Timestamp when grinding started */
    private long grindingStartTime = 0;

    /** Retry counter for failed interactions */
    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;

    /** Poll interval while waiting for grinding */
    private static final int GRINDING_POLL_INTERVAL = 1000;

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    public GrindBonesTask(EctofuntusScript script) {
        this.script = script;
        this.ectophialHandler = TeleportHandlerFactory.createEctophialHandlerTyped(script.getScript());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Public Interface
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if this task can execute.
     * Executes when we can still convert bones into bonemeal and aren't banking.
     */
    public boolean canExecute() {
        if (script.shouldBank()) {
            return false;
        }

        // If we're already mid-task (e.g., waiting for grinding), keep control
        if (currentState != State.CHECK_LOCATION) {
            return true;
        }

        EctoConfig config = script.getConfig();
        if (config == null) {
            return false;
        }

        int bonemealCount = getBonemealCount();
        int boneCount = getTotalBoneCount();
        int emptyPotCount = getInventoryCount(EctofuntusConstants.EMPTY_POT);

        // Don't run if we have bonemeal but no bones (ready for worship)
        if (bonemealCount > 0 && boneCount == 0) {
            return false;
        }

        return boneCount > 0 && emptyPotCount > 0;
    }

    /**
     * Execute the bone grinding task.
     * @return poll delay in milliseconds
     */
    public int execute() {
        switch (currentState) {
            case CHECK_LOCATION:
                return handleCheckLocation();
            case NAVIGATE_TO_TOP_FLOOR:
                return handleNavigateToTopFloor();
            case USE_BONES_ON_HOPPER:
                return handleUseBonesOnHopper();
            case WAIT_FOR_GRINDING:
                return handleWaitForGrinding();
            case COMPLETE:
                return handleComplete();
            default:
                ScriptLogger.error(script.getScript(), "Unknown GrindBonesTask state: " + currentState);
                currentState = State.CHECK_LOCATION;
                return 1000;
        }
    }

    /**
     * Resets the task state for a new cycle.
     */
    public void reset() {
        currentState = State.CHECK_LOCATION;
        expectedBonemealCount = 0;
        grindingStartTime = 0;
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
            // Already on grinder floor near hopper - skip teleport, go directly to grind
            if (isNearGrinder(pos)) {
                ScriptLogger.info(script.getScript(), "Already at grinder - continuing");
                currentState = State.USE_BONES_ON_HOPPER;
                return randomDelay(200, 400);
            }
        }

        // Not at correct location - ensure we're at altar start
        if (!ensureNearAltarStart()) {
            return randomDelay(600, 1000);
        }

        // Re-fetch position after potential teleport
        pos = script.getWorldPosition();
        if (pos == null) {
            ScriptLogger.warning(script.getScript(), "Position unavailable");
            return 1000;
        }

        int currentPlane = pos.getPlane();
        ScriptLogger.debug(script.getScript(), "GrindBones: Current plane = " + currentPlane);

        // Check if we already have bonemeal (edge case - task shouldn't run)
        int bonemealCount = getBonemealCount();
        int boneCount = getTotalBoneCount();
        if (bonemealCount > 0 && boneCount == 0) {
            ScriptLogger.info(script.getScript(), "Already have " + bonemealCount + " pot(s) of bonemeal");
            currentState = State.COMPLETE;
            return randomDelay(200, 400);
        }

        // Count bones to determine expected bonemeal
        if (boneCount == 0) {
            ScriptLogger.warning(script.getScript(), "No bones in inventory!");
            script.setShouldBank(true);
            return 1000;
        }

        // Count empty pots - we need enough for the bones
        int emptyPotCount = getInventoryCount(EctofuntusConstants.EMPTY_POT);
        if (emptyPotCount == 0) {
            ScriptLogger.warning(script.getScript(), "No empty pots in inventory!");
            script.setShouldBank(true);
            return 1000;
        }

        // Expected bonemeal = total empty pots (pots are the limiting factor)
        expectedBonemealCount = emptyPotCount;

        ScriptLogger.info(script.getScript(), "Need to grind " + boneCount + " bones into " +
            expectedBonemealCount + " bonemeal");

        // Check if we're on the top floor
        if (currentPlane == EctofuntusConstants.GRINDER_PLANE) {
            currentState = State.USE_BONES_ON_HOPPER;
        } else {
            currentState = State.NAVIGATE_TO_TOP_FLOOR;
        }

        return randomDelay(200, 400);
    }

    /**
     * Navigates to the top floor where the bone grinder is located.
     */
    private int handleNavigateToTopFloor() {
        WorldPosition pos = script.getWorldPosition();
        if (pos == null) {
            ScriptLogger.warning(script.getScript(), "Position unavailable");
            return 1000;
        }

        // Already on top floor?
        if (pos.getPlane() == EctofuntusConstants.GRINDER_PLANE) {
            ScriptLogger.info(script.getScript(), "Arrived on top floor");
            currentState = State.USE_BONES_ON_HOPPER;
            return randomDelay(300, 500);
        }

        // If in basement, need to go up twice
        if (EctofuntusConstants.isInSlimeDungeon(pos)) {
            ScriptLogger.info(script.getScript(), "In basement - need to climb up to ground floor first");
            RSObject stairs = findStairsFromBasement();
            if (stairs != null) {
                stairs.interact(EctofuntusConstants.ACTION_CLIMB_UP);
                script.pollFramesHuman(() -> {
                    WorldPosition newPos = script.getWorldPosition();
                    return newPos != null && !EctofuntusConstants.isInSlimeDungeon(newPos);
                }, EctofuntusConstants.PLANE_CHANGE_TIMEOUT);
            }
            return randomDelay(600, 1000);
        }

        // On ground floor - find stairs to top floor
        RSObject stairs = findStairsToTopFloor();
        if (stairs == null) {
            // Try walking closer to expected stair location
            ScriptLogger.navigation(script.getScript(), "Walking toward top floor stairs");
            script.getScript().getWalker().walkTo(EctofuntusConstants.STAIRS_TO_TOP_FLOOR_POS);

            if (handleRetryWithEscalation("Finding top floor stairs")) {
                script.stop();
                return 1000;
            }
            return randomDelay(800, 1200);
        }

        ScriptLogger.actionAttempt(script.getScript(), "Climbing up to top floor");
        boolean interacted = stairs.interact(EctofuntusConstants.ACTION_CLIMB_UP);

        if (!interacted) {
            if (handleRetryWithEscalation("Stair interaction")) {
                script.stop();
                return 1000;
            }
            return randomDelay(600, 1000);
        }

        // Wait for plane change
        boolean planeChanged = script.pollFramesHuman(() -> {
            WorldPosition newPos = script.getWorldPosition();
            return newPos != null && newPos.getPlane() == EctofuntusConstants.GRINDER_PLANE;
        }, EctofuntusConstants.PLANE_CHANGE_TIMEOUT);

        if (planeChanged) {
            ScriptLogger.actionSuccess(script.getScript(), "Arrived on top floor");
            retryCount = 0;
            currentState = State.USE_BONES_ON_HOPPER;
            return randomDelay(400, 700);
        } else {
            if (handleRetryWithEscalation("Plane change after stairs")) {
                script.stop();
                return 1000;
            }
            return randomDelay(600, 1000);
        }
    }

    /**
     * Uses bones on the hopper to start the grinding process.
     * This initiates grinding for ALL bones in inventory.
     */
    private int handleUseBonesOnHopper() {
        EctoConfig config = script.getConfig();
        if (config == null) {
            ScriptLogger.error(script.getScript(), "Config not available");
            script.stop();
            return 1000;
        }

        // Find bones in inventory (use first available bone type if mixed mode)
        ItemSearchResult bones = null;
        if (config.isUseAllBonesInTab()) {
            for (Integer boneId : BoneType.getAllItemIds()) {
                bones = findItem(boneId);
                if (bones != null) break;
            }
        } else {
            bones = findItem(config.getBoneType().getItemId());
        }

        if (bones == null) {
            ScriptLogger.warning(script.getScript(), "No bones found in inventory");
            // Check if we already ground them
            if (getBonemealCount() > 0) {
                currentState = State.COMPLETE;
            } else {
                script.setShouldBank(true);
            }
            return randomDelay(400, 600);
        }

        // Find the hopper/loader
        RSObject hopper = findHopper();
        if (hopper == null) {
            if (handleRetryWithEscalation("Finding bone hopper")) {
                script.stop();
                return 1000;
            }
            return randomDelay(600, 1000);
        }

        ScriptLogger.actionAttempt(script.getScript(), "Using bones on hopper");

        // Use bones on hopper - click bone to select it
        boolean selected = bones.interact("Use");
        if (!selected) {
            if (handleRetryWithEscalation("Selecting bones")) {
                script.stop();
                return 1000;
            }
            return randomDelay(400, 600);
        }

        // Small delay for item selection visual
        script.pollFramesHuman(() -> true, 300);

        // Now click the hopper
        boolean usedOnHopper = hopper.interact(createUseOnLoaderMenuHook());
        if (!usedOnHopper) {
            // Fallback to action-based interaction
            usedOnHopper = hopper.interact("Use");
            if (!usedOnHopper) {
                usedOnHopper = hopper.interact();
            }
        }

        if (!usedOnHopper) {
            if (handleRetryWithEscalation("Using bones on hopper")) {
                script.stop();
                return 1000;
            }
            return randomDelay(400, 600);
        }

        // Wait briefly for the grinding to start
        script.pollFramesHuman(() -> true, 500);

        ScriptLogger.info(script.getScript(), "Bones loaded - waiting for grinding to complete");
        retryCount = 0;
        grindingStartTime = System.currentTimeMillis();
        currentState = State.WAIT_FOR_GRINDING;
        return randomDelay(500, 800);
    }

    /**
     * Waits for the grinding process to complete by monitoring empty pot count.
     * Grinding is complete when all empty pots are used (pots are the limiting factor).
     * This handles the case where bones > pots (uses available pots, leaves excess bones).
     */
    private int handleWaitForGrinding() {
        int currentBoneCount = getTotalBoneCount();
        int currentBonemeal = getBonemealCount();
        int emptyPotCount = getInventoryCount(EctofuntusConstants.EMPTY_POT);

        ScriptLogger.debug(script.getScript(), "Grinding progress: " + currentBoneCount + " bones, " +
            emptyPotCount + " empty pots, " + currentBonemeal + " bonemeal (expected " + expectedBonemealCount + ")");

        // Grinding complete when all empty pots are used (pots are limiting factor)
        if (emptyPotCount == 0) {
            ScriptLogger.info(script.getScript(), "Grinding complete: " + currentBonemeal + " bonemeal pots" +
                (currentBoneCount > 0 ? " (" + currentBoneCount + " excess bones)" : ""));
            currentState = State.COMPLETE;
            return randomDelay(300, 500);
        }

        // Calculate dynamic timeout: 10s per bone (total)
        int initialBones = expectedBonemealCount;  // Set in handleCheckLocation
        long dynamicTimeout = (long) initialBones * EctofuntusConstants.GRIND_TIME_PER_BONE_MS;

        if (grindingStartTime > 0 && System.currentTimeMillis() - grindingStartTime > dynamicTimeout) {
            ScriptLogger.warning(script.getScript(), "Grinding timeout after " + (dynamicTimeout / 1000) + "s - " + currentBoneCount + " bones still remaining");
            // Try using bones on hopper again
            currentState = State.USE_BONES_ON_HOPPER;
            return randomDelay(600, 1000);
        }

        // Still grinding - continue waiting
        return GRINDING_POLL_INTERVAL;
    }

    /**
     * Completes the bone grinding task.
     * Note: Excess bones (bones > pots) are expected and logged as info, not warning.
     */
    private int handleComplete() {
        int bonemealCount = getBonemealCount();
        int remainingBones = getTotalBoneCount();
        int emptyPotCount = getInventoryCount(EctofuntusConstants.EMPTY_POT);

        // Only return to grinding if we still have empty pots AND bones
        // (means grinding didn't complete properly)
        if (emptyPotCount > 0 && remainingBones > 0) {
            ScriptLogger.warning(script.getScript(), "Still have " + emptyPotCount +
                " empty pots and " + remainingBones + " bones - returning to grind");
            currentState = State.USE_BONES_ON_HOPPER;
            return randomDelay(400, 600);
        }

        // Log if we have excess bones (more bones than pots - this is OK)
        if (remainingBones > 0) {
            ScriptLogger.info(script.getScript(), remainingBones + " excess bones (pots were limiting factor)");
        }

        ScriptLogger.info(script.getScript(), "Bone grinding complete: " + bonemealCount + " pots");

        // Set state flag
        script.setHasBoneMeal(true);

        // Reset for next cycle
        reset();

        return randomDelay(300, 500);
    }

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

        // Wait for position change (design doc: 500-1000ms timeout)
        int timeout = 500 + ThreadLocalRandom.current().nextInt(500);
        boolean posChanged = script.pollFramesHuman(() -> {
            WorldPosition newPos = script.getWorldPosition();
            return newPos != null && !newPos.equals(startPos);
        }, timeout);

        if (!posChanged) {
            ScriptLogger.debug(script.getScript(), "Position unchanged after teleport - may still be animating");
        }

        return true;
    }

    /**
     * Checks if the player is near the bone grinder (hopper).
     */
    private boolean isNearGrinder(WorldPosition pos) {
        if (pos == null) return false;
        if (pos.getPlane() != EctofuntusConstants.GRINDER_PLANE) return false;
        return pos.distanceTo(EctofuntusConstants.BONE_HOPPER_POS) <= 10;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets total bone count based on config mode.
     * If useAllBonesInTab is enabled, counts all bone types.
     * Otherwise counts only the configured bone type.
     */
    private int getTotalBoneCount() {
        EctoConfig config = script.getConfig();
        if (config == null) return 0;

        if (config.isUseAllBonesInTab()) {
            // Single search for all bone types to avoid double-counting
            try {
                var wm = script.getWidgetManager();
                if (wm == null || wm.getInventory() == null) return 0;
                java.util.Set<Integer> allBoneIds = BoneType.getAllItemIds();
                var search = wm.getInventory().search(allBoneIds);
                return search != null ? search.getAmount(allBoneIds) : 0;
            } catch (Exception e) {
                ExceptionUtils.rethrowIfTaskInterrupted(e);
                ScriptLogger.debug(script.getScript(), "Error counting bones: " + e.getMessage());
                return 0;
            }
        } else {
            // Count only configured bone type
            return getInventoryCount(config.getBoneType().getItemId());
        }
    }

    /**
     * Finds the bone hopper/loader object.
     */
    private RSObject findHopper() {
        return script.getObjectManager().getRSObject(
            obj -> obj.getName() != null &&
                   obj.getName().equals(EctofuntusConstants.BONE_HOPPER_NAME)
        );
    }

    private MenuHook createUseOnLoaderMenuHook() {
        return (List<MenuEntry> menuEntries) -> {
            if (menuEntries == null || menuEntries.isEmpty()) {
                return null;
            }

            MenuEntry fallback = null;
            for (MenuEntry entry : menuEntries) {
                if (entry == null) {
                    continue;
                }

                String raw = entry.getRawText();
                String action = entry.getAction();
                String entity = entry.getEntityName();

                if (raw != null) {
                    String rawLower = raw.toLowerCase();
                    if (rawLower.contains("use") && rawLower.contains("->") && rawLower.contains("loader")) {
                        return entry;
                    }
                }

                if (action != null && entity != null
                    && action.equalsIgnoreCase("Use")
                    && entity.equalsIgnoreCase(EctofuntusConstants.BONE_HOPPER_NAME)) {
                    fallback = entry;
                }
            }

            return fallback;
        };
    }

    /**
     * Finds the stairs to the top floor.
     */
    private RSObject findStairsToTopFloor() {
        return script.getObjectManager().getRSObject(
            obj -> obj.getName() != null &&
                   obj.getName().equals(EctofuntusConstants.STAIRCASE_NAME) &&
                   hasAction(obj, EctofuntusConstants.ACTION_CLIMB_UP)
        );
    }

    /**
     * Finds the stairs from the basement.
     */
    private RSObject findStairsFromBasement() {
        return script.getObjectManager().getRSObject(
            obj -> obj.getName() != null &&
                   obj.getName().equals(EctofuntusConstants.STAIRS_NAME) &&
                   hasAction(obj, EctofuntusConstants.ACTION_CLIMB_UP)
        );
    }

    /**
     * Checks if an object has any of the specified actions.
     */
    private boolean hasAction(RSObject obj, String... actions) {
        String[] objActions = obj.getActions();
        if (objActions == null) return false;

        for (String action : actions) {
            for (String objAction : objActions) {
                if (objAction != null && objAction.equalsIgnoreCase(action)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets the count of bonemeal in inventory (all variants).
     * Uses a single search call to avoid double-counting visually identical items.
     */
    private int getBonemealCount() {
        try {
            var wm = script.getWidgetManager();
            if (wm == null || wm.getInventory() == null) {
                return 0;
            }
            var search = wm.getInventory().search(EctofuntusConstants.BONEMEAL_IDS);
            return search != null ? search.getAmount(EctofuntusConstants.BONEMEAL_IDS) : 0;
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.debug(script.getScript(), "Error counting bonemeal: " + e.getMessage());
            return 0;
        }
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
     * Finds a specific item in inventory.
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

    /**
     * Generates a random delay between min and max values.
     */
    private int randomDelay(int min, int max) {
        return min + ThreadLocalRandom.current().nextInt(max - min);
    }
}
