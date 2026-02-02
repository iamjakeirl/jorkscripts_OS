package com.jork.script.Ectofuntus.tasks;

import com.jork.script.Ectofuntus.EctofuntusConstants;
import com.jork.script.Ectofuntus.EctofuntusScript;
import com.jork.utils.ExceptionUtils;
import com.jork.utils.JorkTaps;
import com.jork.utils.ScriptLogger;
import com.jork.utils.teleport.TeleportHandlerFactory;
import com.jork.utils.teleport.TeleportResult;
import com.jork.utils.teleport.handlers.EctophialHandler;
import com.osmb.api.item.ItemGroupResult;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.scene.RSObject;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles worship at the Ectofuntus altar to gain Prayer XP.
 *
 * State machine flow:
 * CHECK_LOCATION → NAVIGATE_TO_ALTAR → WORSHIP → VERIFY_COMPLETE → SET_BANK_FLAG
 *
 * Key mechanic: Worshipping at the altar consumes one bucket of slime and one pot of bonemeal
 * per worship action. We repeat until both are empty.
 *
 * Priority: 5 (Lowest - only after both slime and bonemeal are ready)
 *
 * @author jork
 */
public class WorshipTask {

    // ═══════════════════════════════════════════════════════════════════════════
    // State Machine
    // ═══════════════════════════════════════════════════════════════════════════

    private enum State {
        CHECK_LOCATION,
        NAVIGATE_TO_ALTAR,
        WORSHIP,
        VERIFY_COMPLETE,
        SET_BANK_FLAG
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Instance Fields
    // ═══════════════════════════════════════════════════════════════════════════

    private final EctofuntusScript script;
    private final EctophialHandler ectophialHandler;
    private State currentState = State.CHECK_LOCATION;

    /** Count of bones processed this worship session (for metrics) */
    private int bonesProcessedThisSession = 0;

    /** Retry counter for failed interactions */
    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    public WorshipTask(EctofuntusScript script) {
        this.script = script;
        this.ectophialHandler = TeleportHandlerFactory.createEctophialHandlerTyped(script.getScript());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Public Interface
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if this task can execute.
     * Execute when we have both slime and bonemeal.
     */
    public boolean canExecute() {
        return !script.shouldBank() && script.hasSlime() && script.hasBoneMeal();
    }

    /**
     * Execute the worship task.
     * @return poll delay in milliseconds
     */
    public int execute() {
        switch (currentState) {
            case CHECK_LOCATION:
                return handleCheckLocation();
            case NAVIGATE_TO_ALTAR:
                return handleNavigateToAltar();
            case WORSHIP:
                return handleWorship();
            case VERIFY_COMPLETE:
                return handleVerifyComplete();
            case SET_BANK_FLAG:
                return handleSetBankFlag();
            default:
                ScriptLogger.error(script.getScript(), "Unknown WorshipTask state: " + currentState);
                currentState = State.CHECK_LOCATION;
                return 1000;
        }
    }

    /**
     * Resets the task state for a new cycle.
     */
    public void reset() {
        currentState = State.CHECK_LOCATION;
        bonesProcessedThisSession = 0;
        retryCount = 0;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // State Handlers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Checks current location and inventory state.
     */
    private int handleCheckLocation() {
        // IN-PLACE RECOVERY: Check if already at correct location before teleporting
        WorldPosition pos = script.getWorldPosition();
        if (pos != null && EctofuntusConstants.isNearAltar(pos)) {
            // Already near altar - check if altar is visible and go directly to worship
            RSObject altar = findAltar();
            if (altar != null) {
                ScriptLogger.info(script.getScript(), "Already at altar - continuing");
                currentState = State.WORSHIP;
                return randomDelay(200, 400);
            }
            // Near altar but can't see it - fall through to navigation
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

        ScriptLogger.debug(script.getScript(), "Worship: Current region = " + pos.getRegionID());

        // Verify we have supplies
        int bonemealCount = getBonemealCount();
        int slimeCount = getSlimeCount();

        if (bonemealCount == 0 || slimeCount == 0) {
            ScriptLogger.warning(script.getScript(), "Missing supplies - bonemeal: " + bonemealCount +
                ", slime: " + slimeCount);
            // Mark supplies as consumed and go to banking
            if (bonemealCount == 0) {
                script.setHasBoneMeal(false);
            }
            if (slimeCount == 0) {
                script.setHasSlime(false);
            }
            script.setShouldBank(true);
            reset();
            return 1000;
        }

        ScriptLogger.info(script.getScript(), "Ready to worship with " + bonemealCount +
            " bonemeal and " + slimeCount + " slime");

        // Check if we need to navigate to altar or can worship directly
        if (EctofuntusConstants.isNearAltar(pos)) {
            RSObject altar = findAltar();
            if (altar != null) {
                currentState = State.WORSHIP;
                return randomDelay(200, 400);
            }
        }

        currentState = State.NAVIGATE_TO_ALTAR;
        return randomDelay(200, 400);
    }

    /**
     * Navigates to the Ectofuntus altar using ectophial teleport.
     */
    private int handleNavigateToAltar() {
        // Use ectophial to teleport to altar - ensureNearAltarStart() handles this
        if (!ensureNearAltarStart()) {
            return randomDelay(600, 1000);
        }

        // Now at altar - check if we can find it
        RSObject altar = findAltar();
        if (altar != null) {
            ScriptLogger.info(script.getScript(), "Found altar");
            retryCount = 0;
            currentState = State.WORSHIP;
            return randomDelay(300, 500);
        }

        // Can't find altar visually - walk closer
        ScriptLogger.navigation(script.getScript(), "Walking toward Ectofuntus altar");
        script.getScript().getWalker().walkTo(EctofuntusConstants.ECTOFUNTUS_ALTAR_POS);

        if (handleRetryWithEscalation("Finding altar")) {
            script.stop();
            return 1000;
        }

        return randomDelay(800, 1200);
    }

    /**
     * Worships at the Ectofuntus altar using JorkTaps spam tap pattern.
     * Uses position-based tapping with human variant delays (150-400ms) until all supplies are consumed.
     */
    private int handleWorship() {
        // Check supplies before worship
        int bonemealCount = getBonemealCount();
        int slimeCount = getSlimeCount();

        if (bonemealCount == 0 || slimeCount == 0) {
            ScriptLogger.info(script.getScript(), "Supplies depleted - worship complete");
            currentState = State.VERIFY_COMPLETE;
            return randomDelay(300, 500);
        }

        // Find the altar
        RSObject altar = findAltar();
        if (altar == null) {
            ScriptLogger.warning(script.getScript(), "Cannot find altar");
            currentState = State.NAVIGATE_TO_ALTAR;
            return randomDelay(600, 1000);
        }

        ScriptLogger.actionAttempt(script.getScript(), "Spam tapping altar (bonemeal: " +
            bonemealCount + ", slime: " + slimeCount + ")");

        // Spam tap until supplies are depleted
        int previousBonemeal = bonemealCount;
        int maxTaps = bonemealCount * 3;  // Safety limit: ~3 taps per bone max

        // Spam tap using JorkTaps with human variant (per Phase 08 context)
        // Uses area-based tapping instead of RSObject.interact()
        boolean success = JorkTaps.spamTapArea(
            script.getScript(),
            EctofuntusConstants.ECTOFUNTUS_ALTAR_AREA,
            100,  // cubeHeight - standard object height
            EctofuntusConstants.WORSHIP_HUMAN_TAP_MIN_DELAY,
            EctofuntusConstants.WORSHIP_HUMAN_TAP_MAX_DELAY,
            maxTaps,
            () -> isWorshipComplete(),  // Existing baseline check condition
            true  // human = true (context requirement: human variant ONLY)
        );

        if (success) {
            // Count final bones processed based on bonemeal change
            int finalBonemeal = getBonemealCount();
            if (finalBonemeal < previousBonemeal) {
                bonesProcessedThisSession += (previousBonemeal - finalBonemeal);
            }
            ScriptLogger.actionSuccess(script.getScript(), "Worship complete via spam tap (" +
                bonesProcessedThisSession + " bones processed)");
            retryCount = 0;
            return handleSetBankFlag();
        }

        // Safety: If spam tap returned false, max taps reached without completion
        ScriptLogger.warning(script.getScript(), "Spam tap returned false - verifying state");
        currentState = State.VERIFY_COMPLETE;
        return randomDelay(300, 500);
    }

    /**
     * Verifies that worship is complete using container-based verification.
     * Complete when empty pots AND empty buckets return to baseline.
     */
    private int handleVerifyComplete() {
        int bonemealCount = getBonemealCount();
        int slimeCount = getSlimeCount();
        int baseline = script.getSupplyBaseline();
        int emptyPots = getEmptyPotCount();
        int emptyBuckets = getEmptyBucketCount();

        ScriptLogger.info(script.getScript(), "Worship verification - emptyPots: " + emptyPots +
            ", emptyBuckets: " + emptyBuckets + ", baseline: " + baseline);

        if (emptyPots >= baseline && emptyBuckets >= baseline) {
            // Containers back at baseline - worship complete
            ScriptLogger.info(script.getScript(), "Containers at baseline - worship complete");
            currentState = State.SET_BANK_FLAG;
            return randomDelay(200, 400);
        } else if (bonemealCount > 0 && slimeCount > 0) {
            // Still have supplies to use
            ScriptLogger.warning(script.getScript(), "Still have supplies (bonemeal: " + bonemealCount +
                ", slime: " + slimeCount + ") - continuing worship");
            currentState = State.WORSHIP;
            return randomDelay(300, 500);
        } else {
            // Partial completion - mark complete to avoid loops
            ScriptLogger.warning(script.getScript(), "Partial completion - empties: " + emptyPots +
                "/" + emptyBuckets + " vs baseline " + baseline);
            currentState = State.SET_BANK_FLAG;
            return randomDelay(200, 400);
        }
    }

    /**
     * Sets flags to indicate we need to bank and updates metrics.
     */
    private int handleSetBankFlag() {
        ScriptLogger.info(script.getScript(), "Worship session complete - processed " +
            bonesProcessedThisSession + " bones this session");

        // Update metrics
        if (bonesProcessedThisSession > 0) {
            script.incrementBonesProcessed(bonesProcessedThisSession);
        }

        // Reset state flags
        script.setHasSlime(false);
        script.setHasBoneMeal(false);
        script.setShouldBank(true);

        // Reset task state
        reset();

        return randomDelay(300, 500);
    }

    private boolean ensureNearAltarStart() {
        WorldPosition startPos = script.getWorldPosition();
        if (startPos != null) {
            ScriptLogger.debug(script.getScript(), "ensureNearAltarStart: pos=" + startPos +
                ", plane=" + startPos.getPlane() +
                ", inComplex=" + EctofuntusConstants.isAtEctofuntusComplex(startPos) +
                ", nearAltar=" + EctofuntusConstants.isNearAltar(startPos));
        } else {
            ScriptLogger.debug(script.getScript(), "ensureNearAltarStart: pos=null");
        }
        if (startPos != null && EctofuntusConstants.isNearAltar(startPos)) {
            return true;
        }

        if (!ectophialHandler.canTeleport()) {
            // Only accept complex if we're already on the altar plane
            if (startPos != null &&
                EctofuntusConstants.isAtEctofuntusComplex(startPos) &&
                startPos.getPlane() == EctofuntusConstants.ALTAR_PLANE) {
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

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Finds the Ectofuntus altar object.
     */
    private RSObject findAltar() {
        return script.getObjectManager().getRSObject(
            obj -> obj.getName() != null &&
                   obj.getName().equals(EctofuntusConstants.ECTOFUNTUS_ALTAR_NAME)
        );
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
     * Gets the count of bucket of slime in inventory.
     */
    private int getSlimeCount() {
        return getInventoryCount(EctofuntusConstants.BUCKET_OF_SLIME);
    }

    /**
     * Gets the count of empty buckets in inventory.
     */
    private int getEmptyBucketCount() {
        return getInventoryCount(EctofuntusConstants.EMPTY_BUCKET);
    }

    /**
     * Gets the count of empty pots in inventory.
     */
    private int getEmptyPotCount() {
        return getInventoryCount(EctofuntusConstants.EMPTY_POT);
    }

    /**
     * Checks if worship is complete based on container-based completion.
     * Worship is complete when empty pots AND empty buckets return to baseline.
     * @return true if worship is complete
     */
    private boolean isWorshipComplete() {
        int emptyPots = getInventoryCount(EctofuntusConstants.EMPTY_POT);
        int emptyBuckets = getInventoryCount(EctofuntusConstants.EMPTY_BUCKET);
        int baseline = script.getSupplyBaseline();

        // Complete when both container types are back at baseline
        return emptyPots >= baseline && emptyBuckets >= baseline;
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
