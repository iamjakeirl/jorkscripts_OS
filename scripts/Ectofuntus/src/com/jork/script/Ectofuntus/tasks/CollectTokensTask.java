package com.jork.script.Ectofuntus.tasks;

import com.jork.script.Ectofuntus.EctofuntusConstants;
import com.jork.script.Ectofuntus.EctofuntusScript;
import com.jork.script.Ectofuntus.utils.InventoryQueries;
import com.jork.utils.ScriptLogger;
import com.osmb.api.item.ItemGroupResult;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.ui.chatbox.dialogue.Dialogue;
import com.osmb.api.utils.RandomUtils;
import com.osmb.api.visual.PixelCluster;
import com.osmb.api.visual.SearchablePixel;
import com.osmb.api.visual.color.ColorModel;
import com.osmb.api.visual.color.tolerance.impl.SingleThresholdComparator;
import com.osmb.api.walker.WalkConfig;

import com.osmb.api.shape.Rectangle;

import java.util.Set;

/**
 * Handles collecting ecto tokens from a tagged Ghost Disciple near the altar.
 *
 * Triggered by a flag set after worship completes. The user must tag one of
 * the ground-floor Ghost Disciples with a unique highlight color so the pixel
 * analyzer can distinguish it from Necrovarius and the grinder-floor disciple.
 *
 * Flow: find tagged NPC via minimap + pixel validation → tap immediately →
 * forward through continue-style dialogues → verify ecto tokens in inventory → bank.
 */
public class CollectTokensTask {

    // ═══════════════════════════════════════════════════════════════════════════
    // State Machine
    // ═══════════════════════════════════════════════════════════════════════════

    private enum State {
        INTERACT_NPC,
        HANDLE_DIALOGUE,
        VERIFY
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Ghost Disciple Detection (tagged highlight colors)
    // ═══════════════════════════════════════════════════════════════════════════

    private static final SearchablePixel[] GHOST_DISCIPLE_PIXELS = new SearchablePixel[] {
        new SearchablePixel(-14286849, new SingleThresholdComparator(0), ColorModel.HSL),
        new SearchablePixel(-14286849, new SingleThresholdComparator(0), ColorModel.HSL),
        new SearchablePixel(-10654893, new SingleThresholdComparator(0), ColorModel.HSL),
        new SearchablePixel(-6376554, new SingleThresholdComparator(0), ColorModel.HSL),
    };

    private static final PixelCluster.ClusterQuery GHOST_DISCIPLE_CLUSTER =
        new PixelCluster.ClusterQuery(1, 75, GHOST_DISCIPLE_PIXELS);

    /** Number of visibility retry batches before forcing bank */
    private static final int MAX_FIND_REPOSITION_ATTEMPTS = 1;

    // ═══════════════════════════════════════════════════════════════════════════
    // Instance Fields
    // ═══════════════════════════════════════════════════════════════════════════

    private final EctofuntusScript script;
    private State currentState = State.INTERACT_NPC;

    /** Retry counter for failed NPC find/interaction */
    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;

    /** Ecto token count before collection (for verification) */
    private int tokenCountBefore = 0;
    /** Number of times we repositioned after visibility retries in this cycle */
    private int findRepositionAttempts = 0;

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    public CollectTokensTask(EctofuntusScript script) {
        this.script = script;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Public Interface
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if this task should execute.
     * Runs when the shouldCollectTokens flag is set (after worship).
     */
    public boolean canExecute() {
        return script.shouldCollectTokens();
    }

    /**
     * Execute the token collection task.
     * If tokens since last collection are below threshold, skip straight to banking.
     */
    public int execute() {
        // Threshold gate: skip collection if not enough tokens accumulated
        if (script.getTokensSinceLastCollection() < script.getTokenCollectionThreshold()) {
            ScriptLogger.debug(script.getScript(), "Tokens since last collection (" +
                script.getTokensSinceLastCollection() + ") below threshold (" +
                script.getTokenCollectionThreshold() + ") - skipping to bank");
            finishAndGoToBank();
            return 0;
        }

        switch (currentState) {
            case INTERACT_NPC:
                return handleInteractNpc();
            case HANDLE_DIALOGUE:
                return handleDialogue();
            case VERIFY:
                return handleVerify();
            default:
                ScriptLogger.error(script.getScript(), "Unknown CollectTokensTask state: " + currentState);
                finishAndGoToBank();
                return 0;
        }
    }

    /**
     * Resets the task state.
     */
    public void reset() {
        currentState = State.INTERACT_NPC;
        retryCount = 0;
        tokenCountBefore = 0;
        findRepositionAttempts = 0;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // State Handlers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Finds the tagged Ghost Disciple by scanning the entire screen for the
     * pixel cluster, then taps it.
     */
    private int handleInteractNpc() {
        ScriptLogger.info(script.getScript(), "Collecting ecto tokens - searching for tagged Ghost Disciple");

        // Snapshot ecto token count before collection for verification
        tokenCountBefore = getEctoTokenCount();

        // Full-screen cluster search for the tagged NPC highlight
        PixelCluster.ClusterSearchResult clusterResult =
            script.getScript().getPixelAnalyzer().findClusters(GHOST_DISCIPLE_CLUSTER);
        if (clusterResult == null || clusterResult.getClusters().isEmpty()) {
            ScriptLogger.warning(script.getScript(), "No tagged Ghost Disciple found on screen");
            return handleFindRetryOrReposition("Finding tagged Ghost Disciple");
        }

        // Use the largest cluster as the tap target
        PixelCluster bestCluster = clusterResult.getClusters().stream()
            .max((a, b) -> Integer.compare(a.getPoints().size(), b.getPoints().size()))
            .orElse(null);
        if (bestCluster == null || bestCluster.getBounds() == null) {
            ScriptLogger.warning(script.getScript(), "Cluster bounds unavailable");
            return handleRetryOrGiveUp("Getting cluster bounds");
        }

        Rectangle clusterBounds = bestCluster.getBounds();
        ScriptLogger.debug(script.getScript(),
            "Found tagged Ghost Disciple (cluster size: " + bestCluster.getPoints().size() + ")");

        ScriptLogger.actionAttempt(script.getScript(), "Tapping Ghost Disciple to collect tokens");

        if (!script.getScript().getFinger().tapGameScreen(clusterBounds)) {
            ScriptLogger.warning(script.getScript(), "Tap failed");
            return handleRetryOrGiveUp("Tapping Ghost Disciple");
        }

        // Wait for dialogue to appear
        boolean dialogueAppeared = script.pollFramesHuman(
            () -> {
                Dialogue d = script.getWidgetManager().getDialogue();
                return d != null && d.isVisible();
            },
            RandomUtils.uniformRandom(4000, 6000)
        );

        if (!dialogueAppeared) {
            ScriptLogger.warning(script.getScript(), "Dialogue did not appear after tap");
            return handleRetryOrGiveUp("Waiting for dialogue");
        }

        retryCount = 0;
        currentState = State.HANDLE_DIALOGUE;
        return 0;
    }

    /**
     * Forwards through the Ghost Disciple's continue-style dialogues.
     * There are no player options - just "tap to continue" style dialogues.
     */
    private int handleDialogue() {
        Dialogue dialogue = script.getWidgetManager().getDialogue();
        if (dialogue == null || !dialogue.isVisible()) {
            ScriptLogger.debug(script.getScript(), "Dialogue not visible - checking result");
            currentState = State.VERIFY;
            return 0;
        }

        // Advance dialogue one step per poll tick to avoid tight multi-continue loops.
        ScriptLogger.debug(script.getScript(), "Continuing dialogue");
        boolean advanced = dialogue.continueChatDialogue();
        if (!advanced) {
            ScriptLogger.debug(script.getScript(), "continueChatDialogue returned false");
            script.pollFramesUntil(() -> false, RandomUtils.uniformRandom(300, 600));
            return 0;
        }

        // Wait briefly for next dialogue frame/state update.
        script.pollFramesUntil(() -> false, RandomUtils.uniformRandom(180, 420));

        Dialogue nextDialogue = script.getWidgetManager().getDialogue();
        if (nextDialogue == null || !nextDialogue.isVisible()) {
            currentState = State.VERIFY;
        }
        return 0;
    }

    /**
     * Verifies collection succeeded by checking for ecto tokens in inventory.
     * On success or failure, clears the flag and proceeds to banking.
     */
    private int handleVerify() {
        // Check if dialogue is still open
        Dialogue dialogue = script.getWidgetManager().getDialogue();
        if (dialogue != null && dialogue.isVisible()) {
            ScriptLogger.warning(script.getScript(), "Dialogue still open - continuing");
            currentState = State.HANDLE_DIALOGUE;
            return 0;
        }

        // Verify ecto tokens appeared in inventory
        int tokenCountAfter = getEctoTokenCount();
        if (tokenCountAfter > tokenCountBefore) {
            int gained = tokenCountAfter - tokenCountBefore;
            ScriptLogger.actionSuccess(script.getScript(), "Collected " + gained +
                " ecto tokens (now have " + tokenCountAfter + " in inventory)");
            script.markTokensCollected();
        } else {
            ScriptLogger.warning(script.getScript(), "No ecto tokens detected in inventory " +
                "(before: " + tokenCountBefore + ", after: " + tokenCountAfter + ")");
        }

        finishAndGoToBank();
        return 0;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets current ecto token count in inventory.
     */
    private int getEctoTokenCount() {
        ItemGroupResult snapshot = InventoryQueries.snapshot(
            script,
            Set.of(EctofuntusConstants.ECTO_TOKEN),
            "Error counting ecto tokens: "
        );
        return InventoryQueries.amount(snapshot, EctofuntusConstants.ECTO_TOKEN);
    }

    /**
     * Clears the collection flag, sets shouldBank, and resets task state.
     */
    private void finishAndGoToBank() {
        script.setShouldCollectTokens(false);
        script.setShouldBank(true);
        reset();
    }

    /**
     * Handles retry logic. On exhaustion, gives up and proceeds to banking.
     */
    private int handleRetryOrGiveUp(String operation) {
        retryCount++;
        if (retryCount >= MAX_RETRIES) {
            ScriptLogger.warning(script.getScript(), operation + " failed after " +
                MAX_RETRIES + " attempts - skipping token collection, proceeding to bank");
            finishAndGoToBank();
            return 0;
        }
        ScriptLogger.debug(script.getScript(), operation + " - retry " +
            retryCount + "/" + MAX_RETRIES);
        script.pollFramesUntil(() -> false, RandomUtils.uniformRandom(400, 800));
        return 0;
    }

    /**
     * Retry strategy for NPC visibility checks:
     * 1) Retry up to MAX_RETRIES
     * 2) Reposition toward stairs/trapdoor zone and retry the scan
     * 3) Give up and proceed to bank
     */
    private int handleFindRetryOrReposition(String operation) {
        retryCount++;
        if (retryCount < MAX_RETRIES) {
            ScriptLogger.debug(script.getScript(), operation + " - retry " +
                retryCount + "/" + MAX_RETRIES);
            script.pollFramesUntil(() -> false, RandomUtils.uniformRandom(400, 800));
            return 0;
        }

        if (findRepositionAttempts < MAX_FIND_REPOSITION_ATTEMPTS) {
            findRepositionAttempts++;
            retryCount = 0;
            ScriptLogger.info(script.getScript(),
                operation + " failed " + MAX_RETRIES + " times - repositioning toward Port Phasmatys gate and retrying");
            repositionForTokenScan();
            currentState = State.INTERACT_NPC;
            return 0;
        }

        ScriptLogger.warning(script.getScript(), operation + " failed after retries + reposition - skipping token collection, proceeding to bank");
        finishAndGoToBank();
        return 0;
    }

    /**
     * Walks toward Port Phasmatys gate to change camera/object visibility for NPC scan.
     */
    private void repositionForTokenScan() {
        // Close inventory tab to maximize game screen visibility for NPC scan
        try {
            script.getWidgetManager().getTabManager().closeContainer();
        } catch (Exception e) {
            ScriptLogger.debug(script.getScript(), "Could not close tab container: " + e.getMessage());
        }

        WorldPosition target = EctofuntusConstants.PORT_PHASMATYS_GATE_POS;

        int breakDistance = RandomUtils.uniformRandom(2, 4);
        ScriptLogger.navigation(script.getScript(), "Repositioning for token scan: moving toward " + target);

        WalkConfig.Builder walkConfig = new WalkConfig.Builder()
            .tileRandomisationRadius(RandomUtils.uniformRandom(1, 2))
            .breakCondition(() -> {
                WorldPosition pos = script.getWorldPosition();
                return pos != null && pos.distanceTo(target) <= breakDistance;
            });

        script.getScript().getWalker().walkTo(target, walkConfig.build());
        script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(250, 900, 450, 120));
    }
}
