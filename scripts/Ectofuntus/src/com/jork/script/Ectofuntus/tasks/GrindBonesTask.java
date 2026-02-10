package com.jork.script.Ectofuntus.tasks;

import com.jork.script.Ectofuntus.EctofuntusConstants;
import com.jork.script.Ectofuntus.EctofuntusScript;
import com.jork.script.Ectofuntus.config.BoneType;
import com.jork.script.Ectofuntus.config.EctoConfig;
import com.jork.script.Ectofuntus.utils.InventoryQueries;
import com.jork.utils.ExceptionUtils;
import com.jork.utils.JorkTaps;
import com.jork.utils.ScriptLogger;
import com.jork.utils.teleport.TeleportHandlerFactory;
import com.jork.utils.teleport.TeleportResult;
import com.jork.utils.teleport.handlers.EctophialHandler;
import com.osmb.api.input.MenuEntry;
import com.osmb.api.input.MenuHook;
import com.osmb.api.item.ItemSearchResult;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.scene.RSObject;
import com.osmb.api.shape.Polygon;
import com.osmb.api.utils.RandomUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles bone grinding on the top floor of the Ectofuntus.
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

    /** Retry counter for failed interactions */
    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;


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
                return 0;
        }
    }

    /**
     * Resets the task state for a new cycle.
     */
    public void reset() {
        currentState = State.CHECK_LOCATION;
        expectedBonemealCount = 0;
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
                // Must set expectedBonemealCount before USE_BONES_ON_HOPPER (used by WAIT_FOR_GRINDING timeout)
                int potCount = getInventoryCount(EctofuntusConstants.EMPTY_POT);
                if (potCount > 0) {
                    expectedBonemealCount = potCount;
                }
                currentState = State.USE_BONES_ON_HOPPER;
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
        ScriptLogger.debug(script.getScript(), "GrindBones: Current plane = " + currentPlane);

        EctoConfig config = script.getConfig();
        Set<Integer> boneIdsForCount = null;
        Set<Integer> inventoryItemIds = new HashSet<>(EctofuntusConstants.BONEMEAL_IDS);
        inventoryItemIds.add(EctofuntusConstants.EMPTY_POT);
        if (config != null) {
            boneIdsForCount = config.isUseAllBonesInTab()
                ? BoneType.getAllItemIds()
                : Set.of(config.getBoneType().getItemId());
            inventoryItemIds.addAll(boneIdsForCount);
        }

        var inventorySnapshot = InventoryQueries.snapshot(
            script,
            inventoryItemIds,
            "Error taking inventory snapshot: "
        );

        int bonemealCount = InventoryQueries.amount(inventorySnapshot, EctofuntusConstants.BONEMEAL_IDS);
        int boneCount = boneIdsForCount != null ? InventoryQueries.amount(inventorySnapshot, boneIdsForCount) : 0;
        if (bonemealCount > 0 && boneCount == 0) {
            ScriptLogger.info(script.getScript(), "Already have " + bonemealCount + " pot(s) of bonemeal");
            currentState = State.COMPLETE;
            return 0;
        }

        // Count bones to determine expected bonemeal
        if (boneCount == 0) {
            ScriptLogger.warning(script.getScript(), "No bones in inventory!");
            script.setShouldBank(true);
            return 0;
        }

        // Count empty pots - we need enough for the bones
        int emptyPotCount = InventoryQueries.amount(inventorySnapshot, EctofuntusConstants.EMPTY_POT);
        if (emptyPotCount == 0) {
            ScriptLogger.warning(script.getScript(), "No empty pots in inventory!");
            script.setShouldBank(true);
            return 0;
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

        return 0;
    }

    /**
     * Navigates to the top floor where the bone grinder is located.
     */
    private int handleNavigateToTopFloor() {
        WorldPosition pos = script.getWorldPosition();
        if (pos == null) {
            ScriptLogger.warning(script.getScript(), "Position unavailable");
            return 0;
        }

        // Already on top floor?
        if (pos.getPlane() == EctofuntusConstants.GRINDER_PLANE) {
            ScriptLogger.info(script.getScript(), "Arrived on top floor");
            currentState = State.USE_BONES_ON_HOPPER;
            return 0;
        }

        // If in basement, need to go up twice
        if (EctofuntusConstants.isInSlimeDungeon(pos)) {
            ScriptLogger.info(script.getScript(), "In basement - need to climb up to ground floor first");
            RSObject stairs = findStairsFromBasement();
            if (stairs != null && stairs.interact(EctofuntusConstants.ACTION_CLIMB_UP)) {
                script.pollFramesHuman(() -> {
                    WorldPosition newPos = script.getWorldPosition();
                    return newPos != null && !EctofuntusConstants.isInSlimeDungeon(newPos);
                }, RandomUtils.uniformRandom(4000, 6000), true);
            }
            return 0;
        }

        // On ground floor - find stairs to top floor
        RSObject stairs = findStairsToTopFloor();
        if (stairs == null) {
            // Try walking closer to expected stair location
            ScriptLogger.navigation(script.getScript(), "Walking toward top floor stairs");
            script.getScript().getWalker().walkTo(EctofuntusConstants.STAIRS_TO_TOP_FLOOR_POS);

            if (handleRetryWithEscalation("Finding top floor stairs")) {
                script.stop();
            }
            return 0;
        }

        ScriptLogger.actionAttempt(script.getScript(), "Climbing up to top floor");
        boolean interacted = stairs.interact(EctofuntusConstants.ACTION_CLIMB_UP);

        if (!interacted) {
            if (handleRetryWithEscalation("Stair interaction")) {
                script.stop();
            }
            return 0;
        }

        // Wait for plane change with human reaction delay on arrival
        boolean planeChanged = script.pollFramesHuman(() -> {
            WorldPosition newPos = script.getWorldPosition();
            return newPos != null && newPos.getPlane() == EctofuntusConstants.GRINDER_PLANE;
        }, RandomUtils.uniformRandom(4000, 6000), true);

        if (planeChanged) {
            ScriptLogger.actionSuccess(script.getScript(), "Arrived on top floor");
            retryCount = 0;
            currentState = State.USE_BONES_ON_HOPPER;
        } else {
            if (handleRetryWithEscalation("Plane change after stairs")) {
                script.stop();
            }
        }
        return 0;
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
            return 0;
        }

        // Find bones in inventory (use first available bone type if mixed mode)
        ItemSearchResult bones = findBonesToUse(config);

        if (bones == null) {
            ScriptLogger.warning(script.getScript(), "No bones found in inventory");
            // Check if we already ground them
            if (getBonemealCount() > 0) {
                currentState = State.COMPLETE;
            } else {
                script.setShouldBank(true);
            }
            return 0;
        }

        // Find the hopper/loader
        RSObject hopper = findHopper();
        if (hopper == null) {
            if (handleRetryWithEscalation("Finding bone hopper")) {
                script.stop();
            }
            return 0;
        }

        ScriptLogger.actionAttempt(script.getScript(), "Using bones on hopper");

        // Use-on-loader is required. Do not fall back to generic loader interactions.
        boolean usedOnHopper = useBonesOnLoader(bones, hopper);
        if (!usedOnHopper) {
            // Clear any lingering "Use" selection before retry
            clearItemSelection();

            // Re-acquire item/object and retry
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(120, 500, 220, 70));

            ItemSearchResult retryBones = findBonesToUse(config);
            RSObject retryHopper = findHopper();
            if (retryBones != null && retryHopper != null) {
                usedOnHopper = useBonesOnLoader(retryBones, retryHopper);
            }
        }

        if (!usedOnHopper) {
            if (handleRetryWithEscalation("Using bones on hopper")) {
                script.stop();
            }
            return 0;
        }

        ScriptLogger.info(script.getScript(), "Bones loaded - waiting for grinding to complete");
        retryCount = 0;
        currentState = State.WAIT_FOR_GRINDING;
        return 0;
    }

    /**
     * Waits for the grinding process to complete by monitoring empty pot count.
     * Grinding is complete when all empty pots are consumed (pots are the limiting factor).
     *
     * Includes stall detection: if the bone count does not decrease within
     * 1.5x the per-bone grind time, assumes grinding has stalled (bones weren't
     * actually loaded) and transitions back to USE_BONES_ON_HOPPER to retry.
     */
    private int handleWaitForGrinding() {
        // Calculate dynamic timeout: GRIND_TIME_PER_BONE_MS per bone (total)
        int initialBones = expectedBonemealCount;  // Set in handleCheckLocation
        long dynamicTimeout = (long) initialBones * EctofuntusConstants.GRIND_TIME_PER_BONE_MS;

        // Stall detection: track bone count changes
        final int[] lastBoneCount = { getTotalBoneCount() };
        final long[] lastBoneCountChangeTime = { System.currentTimeMillis() };
        final boolean[] stallDetected = { false };
        long stallTimeout = (long) (EctofuntusConstants.GRIND_TIME_PER_BONE_MS * 1.5);

        // pollFramesHuman: waits for condition then injects human-like reaction delay
        // ignoreTasks=true: suppress break/hop/afk during grinding
        boolean done = script.pollFramesHuman(() -> {
            // Primary: all pots consumed = grinding complete
            if (getInventoryCount(EctofuntusConstants.EMPTY_POT) == 0) {
                return true;
            }

            // Secondary: bone count stall detection
            int currentBones = getTotalBoneCount();
            if (currentBones < lastBoneCount[0]) {
                // Bones are being consumed — grinding is active
                lastBoneCount[0] = currentBones;
                lastBoneCountChangeTime[0] = System.currentTimeMillis();
            }

            if (System.currentTimeMillis() - lastBoneCountChangeTime[0] > stallTimeout) {
                // No bone count change for 1.5x grind time — stall detected
                stallDetected[0] = true;
                return true;
            }

            return false;
        }, (int) dynamicTimeout, true);

        // Disambiguate between completion, stall, and timeout
        if (stallDetected[0]) {
            int remainingBones = getTotalBoneCount();
            int remainingPots = getInventoryCount(EctofuntusConstants.EMPTY_POT);
            ScriptLogger.warning(script.getScript(),
                "Grinding stall detected: " + remainingBones + " bones, " +
                remainingPots + " pots remaining — retrying");
            currentState = State.USE_BONES_ON_HOPPER;
            return 0;
        }

        if (done && getInventoryCount(EctofuntusConstants.EMPTY_POT) == 0) {
            int currentBonemeal = getBonemealCount();
            int currentBoneCount = getTotalBoneCount();
            ScriptLogger.info(script.getScript(), "Grinding complete: " + currentBonemeal + " bonemeal pots" +
                (currentBoneCount > 0 ? " (" + currentBoneCount + " excess bones)" : ""));
            currentState = State.COMPLETE;
            return 0;
        }

        // Full timeout expired without completion
        int currentBoneCount = getTotalBoneCount();
        ScriptLogger.warning(script.getScript(), "Grinding timeout after " +
            (dynamicTimeout / 1000) + "s - " + currentBoneCount + " bones still remaining");
        currentState = State.USE_BONES_ON_HOPPER;
        return 0;
    }

    /**
     * Completes the bone grinding task.
     * Note: Excess bones (bones > pots) are expected and logged as info, not warning.
     */
    private int handleComplete() {
        EctoConfig config = script.getConfig();
        Set<Integer> boneIdsForCount = null;
        Set<Integer> inventoryItemIds = new HashSet<>(EctofuntusConstants.BONEMEAL_IDS);
        inventoryItemIds.add(EctofuntusConstants.EMPTY_POT);
        if (config != null) {
            boneIdsForCount = config.isUseAllBonesInTab()
                ? BoneType.getAllItemIds()
                : Set.of(config.getBoneType().getItemId());
            inventoryItemIds.addAll(boneIdsForCount);
        }

        var inventorySnapshot = InventoryQueries.snapshot(
            script,
            inventoryItemIds,
            "Error taking inventory snapshot: "
        );

        int bonemealCount = InventoryQueries.amount(inventorySnapshot, EctofuntusConstants.BONEMEAL_IDS);
        int remainingBones = boneIdsForCount != null ? InventoryQueries.amount(inventorySnapshot, boneIdsForCount) : 0;
        int emptyPotCount = InventoryQueries.amount(inventorySnapshot, EctofuntusConstants.EMPTY_POT);

        // Only return to grinding if we still have empty pots AND bones
        // (means grinding didn't complete properly)
        if (emptyPotCount > 0 && remainingBones > 0) {
            ScriptLogger.warning(script.getScript(), "Still have " + emptyPotCount +
                " empty pots and " + remainingBones + " bones - returning to grind");
            currentState = State.USE_BONES_ON_HOPPER;
            return 0;
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

        return 0;
    }

    /**
     * Ensures the player is near the altar start position via ectophial teleport.
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
        boolean posChanged = script.pollFramesHuman(() -> {
            WorldPosition newPos = script.getWorldPosition();
            return newPos != null && !newPos.equals(startPos);
        }, RandomUtils.uniformRandom(500, 1000));

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
            return InventoryQueries.countItems(
                script,
                BoneType.getAllItemIds(),
                "Error counting bones: "
            );
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
                   obj.getName().equalsIgnoreCase(EctofuntusConstants.BONE_HOPPER_NAME) &&
                   obj.canReach()
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
                   obj.getName().equalsIgnoreCase(EctofuntusConstants.STAIRCASE_NAME) &&
                   obj.canReach() &&
                   hasAction(obj, EctofuntusConstants.ACTION_CLIMB_UP)
        );
    }

    /**
     * Finds the stairs from the basement.
     */
    private RSObject findStairsFromBasement() {
        return script.getObjectManager().getRSObject(
            obj -> obj.getName() != null &&
                   obj.getName().equalsIgnoreCase(EctofuntusConstants.STAIRS_NAME) &&
                   obj.canReach() &&
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
        return InventoryQueries.countItems(script, EctofuntusConstants.BONEMEAL_IDS, "Error counting bonemeal: ");
    }

    /**
     * Gets the count of an item in inventory.
     */
    private int getInventoryCount(int itemId) {
        return InventoryQueries.countItem(script, itemId, "Error counting item " + itemId + ": ");
    }

    /**
     * Finds a specific item in inventory.
     */
    private ItemSearchResult findItem(int itemId) {
        return InventoryQueries.findItem(script, itemId, "Error finding item " + itemId + ": ");
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
     * Finds the bone item to use based on config mode.
     */
    private ItemSearchResult findBonesToUse(EctoConfig config) {
        if (config == null) {
            return null;
        }

        if (config.isUseAllBonesInTab()) {
            for (Integer boneId : BoneType.getAllItemIds()) {
                ItemSearchResult bones = findItem(boneId);
                if (bones != null) {
                    return bones;
                }
            }
            return null;
        }

        return findItem(config.getBoneType().getItemId());
    }

    /**
     * Performs a "Use bones -> Loader" interaction via tile tap (primary) with
     * menu hook fallback. Clears item selection on failure to prevent "Use" mode
     * leaking into subsequent taps (walker, retries).
     *
     * After the interaction is accepted, verifies the character starts walking
     * toward the loader. If no position change within a randomized timeout,
     * the action was silently consumed ("eaten") and should be retried.
     */
    private boolean useBonesOnLoader(ItemSearchResult bones, RSObject hopper) {
        if (bones == null || hopper == null) {
            return false;
        }

        boolean selected = bones.interact("Use");
        if (!selected) {
            return false;
        }

        // Short delay between selecting bone and tapping loader tile
        script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(80, 400, 150, 50));

        // Capture position before interaction for action eating detection
        WorldPosition beforePos = script.getWorldPosition();

        // --- Primary: Tile tap approach ---
        boolean interactionAccepted = false;

        Polygon loaderPoly = JorkTaps.convertPositionToPolygon(
            script.getScript(),
            EctofuntusConstants.BONE_HOPPER_POS,
            0  // cubeHeight: ground-level flat tile
        );

        if (loaderPoly != null) {
            Polygon scaledPoly = loaderPoly.getResized(0.95);
            // Single tap — bones are already in "Use" mode, no long-press menu needed
            interactionAccepted = script.getScript().getFinger().tapGameScreen(scaledPoly);

            if (!interactionAccepted) {
                ScriptLogger.debug(script.getScript(),
                    "Tile tap failed — falling back to menu hook");
            }
        } else {
            ScriptLogger.debug(script.getScript(),
                "Loader tile not visible on screen — falling back to menu hook");
        }

        // --- Fallback: Menu hook approach ---
        if (!interactionAccepted) {
            interactionAccepted = hopper.interact(createUseOnLoaderMenuHook());
        }

        // If both methods failed, clear the "Use" selection and bail
        if (!interactionAccepted) {
            clearItemSelection();
            return false;
        }

        // --- Action eating detection ---
        // The character should walk from the stairs to the loader after the
        // interaction is accepted. If no position change within a randomized
        // timeout, the action was eaten — break out to retry.
        int posChangeTimeout = RandomUtils.uniformRandom(1000, 3000);
        boolean moved = script.pollFramesUntil(() -> {
            WorldPosition currentPos = script.getWorldPosition();
            return currentPos != null && beforePos != null && !currentPos.equals(beforePos);
        }, posChangeTimeout);

        if (!moved) {
            ScriptLogger.debug(script.getScript(),
                "No position change after use-on-loader — possible action eating");
            clearItemSelection();
            return false;
        }

        return true;
    }

    /**
     * Clears any lingering "Use" item selection in the inventory.
     * Prevents the selection state from leaking into subsequent taps.
     */
    private void clearItemSelection() {
        try {
            var wm = script.getWidgetManager();
            if (wm != null && wm.getInventory() != null) {
                wm.getInventory().unSelectItemIfSelected();
            }
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.debug(script.getScript(), "Failed to clear item selection: " + e.getMessage());
        }
    }

}
