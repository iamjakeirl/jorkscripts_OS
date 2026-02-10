package com.jork.script.fireGiantWaterStrike;

import com.jork.script.fireGiantWaterStrike.anchor.AnchorManager;
import com.jork.script.fireGiantWaterStrike.config.CombatConfig;
import com.jork.script.fireGiantWaterStrike.ui.ScriptOptions;
import com.jork.utils.ScriptLogger;
import com.jork.utils.metrics.AbstractMetricsScript;
import com.jork.utils.metrics.core.MetricType;
import com.jork.utils.metrics.display.MetricsPanelConfig;

import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.script.ScriptDefinition;
import com.osmb.api.script.SkillCategory;
import com.osmb.api.ui.GameState;
import com.osmb.api.ui.component.tabs.skill.SkillType;
import com.osmb.api.utils.RandomUtils;
import com.osmb.api.visual.drawing.Canvas;

import javafx.scene.Scene;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fire Giant Water Strike - Automates killing Fire Giants with Water Strike in Waterfall Dungeon.
 */
@ScriptDefinition(
    name = "Fire Giant Water Strike",
    author = "jork",
    version = 1.0,
    threadUrl = "",
    skillCategory = SkillCategory.MAGIC
)
public class FireGiantWaterStrike extends AbstractMetricsScript {

    // ───────────────────────────────────────────────────────────────────────────
    // Rune Item ID Constants
    // ───────────────────────────────────────────────────────────────────────────
    private static final int WATER_RUNE_ID = 555;
    private static final int AIR_RUNE_ID = 556;
    private static final int MIND_RUNE_ID = 558;

    // ───────────────────────────────────────────────────────────────────────────
    // UI Synchronization Fields
    // ───────────────────────────────────────────────────────────────────────────
    private volatile boolean settingsConfirmed = false;  // FX thread -> script thread sync
    private volatile CombatConfig config;                 // FX thread -> script thread
    private boolean initialised = false;                  // Script thread internal guard

    // ───────────────────────────────────────────────────────────────────────────
    // Metrics Tracking
    // ───────────────────────────────────────────────────────────────────────────
    private final AtomicInteger killCount = new AtomicInteger(0);
    private final AtomicInteger lootCount = new AtomicInteger(0);

    // ───────────────────────────────────────────────────────────────────────────
    // State Machine
    // ───────────────────────────────────────────────────────────────────────────
    private ScriptState currentState = ScriptState.INIT;
    private AnchorManager anchorManager;
    private static final int MAX_WALKBACK_FAILURES = 3;
    private int consecutiveWalkbackFailures = 0;

    public FireGiantWaterStrike(Object scriptCore) {
        super(scriptCore);
    }

    @Override
    protected void onMetricsStart() {
        ScriptLogger.startup(this, "1.0", "jork", "Fire Giant Water Strike");

        ScriptOptions opts = new ScriptOptions(this);
        Scene scene = new Scene(opts);
        getStageController().show(scene, "Fire Giant Water Strike - Settings", false);

        ScriptLogger.info(this, "Settings window opened - waiting for user confirmation");

        // Safety net: if user closes window without clicking Start, apply defaults
        if (scene.getWindow() != null) {
            scene.getWindow().setOnHidden(e -> {
                if (!settingsConfirmed) {
                    onSettingsConfirmed(CombatConfig.getDefault());
                }
            });
        }
    }

    /**
     * Initialize tasks after UI confirmation - runs on script thread.
     */
    private void initialiseIfReady() {
        if (initialised || !settingsConfirmed) {
            return;
        }

        openInventoryIfNeeded();
        validateInventory();

        anchorManager = new AnchorManager(this, AnchorManager.WATERFALL_SAFESPOT);
        ScriptLogger.info(this, "Anchor set at: " + anchorManager.getAnchorPosition());

        // Log current position for in-game coordinate validation
        WorldPosition currentPos = getWorldPosition();
        if (currentPos != null) {
            ScriptLogger.info(this, "Current position: " + currentPos + " (region: " + currentPos.getRegionID() + ")");
        }

        initializeMetrics();

        ScriptLogger.info(this, "Initialization complete");
        initialised = true;
    }

    /**
     * Validates that the player has the required runes and food in inventory.
     * Logs warnings for missing items but does not hard-stop (Phase 5 owns that logic).
     */
    private void validateInventory() {
        if (getWidgetManager() == null || getWidgetManager().getInventory() == null) {
            ScriptLogger.warning(this, "Inventory unavailable - skipping validation");
            return;
        }

        // Check combat runes (Water Strike requires water, air, mind runes)
        Set<Integer> runeIds = Set.of(WATER_RUNE_ID, AIR_RUNE_ID, MIND_RUNE_ID);
        var runeResult = getWidgetManager().getInventory().search(runeIds);

        int waterCount = runeResult != null ? runeResult.getAmount(WATER_RUNE_ID) : 0;
        int airCount = runeResult != null ? runeResult.getAmount(AIR_RUNE_ID) : 0;
        int mindCount = runeResult != null ? runeResult.getAmount(MIND_RUNE_ID) : 0;

        ScriptLogger.info(this, "Rune check - Water: " + waterCount + ", Air: " + airCount + ", Mind: " + mindCount);

        if (waterCount == 0) {
            ScriptLogger.warning(this, "No water runes found in inventory");
        }
        if (airCount == 0) {
            ScriptLogger.warning(this, "No air runes found in inventory");
        }
        if (mindCount == 0) {
            ScriptLogger.warning(this, "No mind runes found in inventory");
        }

        // Check food
        if (config != null) {
            int foodId = config.foodType().getItemId();
            var foodResult = getWidgetManager().getInventory().search(Set.of(foodId));
            int foodCount = foodResult != null ? foodResult.getAmount(foodId) : 0;

            ScriptLogger.info(this, "Food check - " + config.foodType().getDisplayName() + ": " + foodCount);

            if (foodCount == 0) {
                ScriptLogger.warning(this, "No " + config.foodType().getDisplayName() + " found in inventory");
            }
        }
    }

    /**
     * Register metrics for the paint overlay.
     */
    private void initializeMetrics() {
        registerMetric("Activity", () -> currentState.name(), MetricType.TEXT);
        enableXPTracking(SkillType.MAGIC);
        registerMetric("Kills", killCount::get, MetricType.NUMBER);
        registerMetric("Kills /hr", killCount::get, MetricType.RATE);
        registerMetric("Loot", lootCount::get, MetricType.NUMBER);

        if (config != null && config.xpFailsafeEnabled()) {
            registerMetric("Since XP", () -> {
                long ms = getTimeSinceLastXPGain();
                return (ms < 60000) ? (ms / 1000) + "s" : (ms / 60000) + "m";
            }, MetricType.TEXT);

            if (config.xpFailsafePauseDuringLogout()) {
                configureXPFailsafeTimerPause(true);
            }
        }
    }

    /**
     * Opens the inventory tab if it is not already open.
     */
    private void openInventoryIfNeeded() {
        if (getWidgetManager() == null || getWidgetManager().getInventory() == null) {
            ScriptLogger.debug(this, "Inventory unavailable - skipping open attempt");
            return;
        }

        if (getWidgetManager().getInventory().isOpen()) {
            return;
        }

        pollFramesHuman(() -> {
            if (getWidgetManager() == null || getWidgetManager().getInventory() == null) {
                return false;
            }
            if (!getWidgetManager().getInventory().isOpen()) {
                return getWidgetManager().getInventory().open();
            }
            return true;
        }, RandomUtils.uniformRandom(1800, 2600));
    }

    @Override
    public int poll() {
        if (!settingsConfirmed) {
            return 0;
        }

        initialiseIfReady();
        if (!initialised) {
            return 0;
        }

        // Global interrupts placeholder (Phase 5)

        // State dispatch
        switch (currentState) {
            case INIT -> handleInit();
            case ENSURE_ANCHOR -> handleEnsureAnchor();
            case ACQUIRE_TARGET -> handleAcquireTarget();
            case ENGAGE_TARGET -> handleEngageTarget();
            case MONITOR_COMBAT -> handleMonitorCombat();
            case POST_KILL -> handlePostKill();
            case LOOT_TELEGRAB -> handleLootTelegrab();
            case LOOT_MANUAL -> handleLootManual();
            case RECOVERY -> handleRecovery();
            case STOP -> handleStop();
        }

        return 0;
    }

    // ───────────────────────────────────────────────────────────────────────────
    // State Handlers
    // ───────────────────────────────────────────────────────────────────────────

    private void handleInit() {
        ScriptLogger.info(this, "INIT: Transitioning to ENSURE_ANCHOR");
        currentState = ScriptState.ENSURE_ANCHOR;
    }

    private void handleEnsureAnchor() {
        if (anchorManager == null) {
            ScriptLogger.warning(this, "ENSURE_ANCHOR: AnchorManager not initialized");
            return;
        }

        if (!anchorManager.isDisplaced()) {
            consecutiveWalkbackFailures = 0;
            ScriptLogger.debug(this, "ENSURE_ANCHOR: At anchor, transitioning to ACQUIRE_TARGET");
            setState(ScriptState.ACQUIRE_TARGET);
            return;
        }

        ScriptLogger.info(this, "ENSURE_ANCHOR: Displaced from anchor, walking back");
        boolean success = anchorManager.walkBack();

        if (success && !anchorManager.isDisplaced()) {
            ScriptLogger.info(this, "ENSURE_ANCHOR: Walk-back successful");
            consecutiveWalkbackFailures = 0;
            setState(ScriptState.ACQUIRE_TARGET);
            return;
        }

        consecutiveWalkbackFailures++;
        ScriptLogger.warning(this, "ENSURE_ANCHOR: Walk-back failed ("
            + consecutiveWalkbackFailures + "/" + MAX_WALKBACK_FAILURES + ")");

        if (consecutiveWalkbackFailures >= MAX_WALKBACK_FAILURES) {
            ScriptLogger.error(this, "ENSURE_ANCHOR: Exceeded walk-back retry budget, entering RECOVERY");
            setState(ScriptState.RECOVERY);
            return;
        }
        // Stay in ENSURE_ANCHOR -- will retry on next poll cycle
    }

    private void handleAcquireTarget() {
        ScriptLogger.debug(this, "ACQUIRE_TARGET: stub");
    }

    private void handleEngageTarget() {
        ScriptLogger.debug(this, "ENGAGE_TARGET: stub");
    }

    private void handleMonitorCombat() {
        ScriptLogger.debug(this, "MONITOR_COMBAT: stub");
    }

    private void handlePostKill() {
        ScriptLogger.debug(this, "POST_KILL: stub - routing through ENSURE_ANCHOR");
        // Phase 6 will add loot mode evaluation and dispatch here
        setState(ScriptState.ENSURE_ANCHOR);
    }

    private void handleLootTelegrab() {
        ScriptLogger.debug(this, "LOOT_TELEGRAB: stub - routing through ENSURE_ANCHOR");
        // Phase 7 will add telegrab execution here
        setState(ScriptState.ENSURE_ANCHOR);
    }

    private void handleLootManual() {
        ScriptLogger.debug(this, "LOOT_MANUAL: stub - routing through ENSURE_ANCHOR");
        // Phase 7 will add manual pickup execution here
        setState(ScriptState.ENSURE_ANCHOR);
    }

    private void handleRecovery() {
        ScriptLogger.debug(this, "RECOVERY: stub - routing through ENSURE_ANCHOR");
        // Phase 5 will add retry backoff and budget tracking here
        setState(ScriptState.ENSURE_ANCHOR);
    }

    private void handleStop() {
        ScriptLogger.info(this, "STOP: Script stopping");
        stop();
    }

    // ───────────────────────────────────────────────────────────────────────────
    // State Accessors
    // ───────────────────────────────────────────────────────────────────────────

    public void setState(ScriptState newState) {
        ScriptLogger.debug(this, "State: " + currentState + " -> " + newState);
        this.currentState = newState;
    }

    public ScriptState getState() {
        return currentState;
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Settings Callback (called from JavaFX thread)
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Called by ScriptOptions when the user confirms settings.
     * Runs on JavaFX thread -- only set volatile flags, no game API calls.
     *
     * @param config the confirmed combat configuration
     */
    public void onSettingsConfirmed(CombatConfig config) {
        this.config = config;

        ScriptLogger.setDebugEnabled(config.debugLogging());
        ScriptLogger.info(this, "Debug logging " + (config.debugLogging() ? "ENABLED" : "DISABLED"));
        ScriptLogger.info(this, "Loot mode: " + config.lootMode());
        ScriptLogger.info(this, "Food type: " + config.foodType().getDisplayName()
            + " (ID: " + config.foodType().getItemId()
            + ", heals " + config.foodType().getHealAmount() + ")");

        if (config.xpFailsafeEnabled()) {
            ScriptLogger.info(this, "XP Failsafe ENABLED - will stop after "
                + config.xpFailsafeTimeoutMinutes() + " minutes without XP"
                + (config.xpFailsafePauseDuringLogout() ? " (pauses during logout)" : ""));
        } else {
            ScriptLogger.info(this, "XP Failsafe DISABLED");
        }

        this.settingsConfirmed = true;
    }

    /**
     * Returns the current combat configuration.
     *
     * @return the CombatConfig, or null if settings have not been confirmed
     */
    public CombatConfig getConfig() {
        return config;
    }

    @Override
    protected MetricsPanelConfig createMetricsConfig() {
        MetricsPanelConfig panelConfig = MetricsPanelConfig.darkTheme();
        panelConfig.setCustomPosition(10, 110);
        panelConfig.setMinWidth(180);
        panelConfig.setBackgroundColor(new java.awt.Color(0, 0, 0, 220));
        return panelConfig;
    }

    @Override
    protected void onMetricsPaint(Canvas canvas) {
        if (!initialised || anchorManager == null) return;

        WorldPosition anchor = anchorManager.getAnchorPosition();
        com.osmb.api.shape.Polygon tilePoly = getSceneProjector().getTilePoly(anchor);

        if (tilePoly != null) {
            canvas.fillPolygon(tilePoly, 0x00FF00, 0.3);   // Green fill 30% opacity
            canvas.drawPolygon(tilePoly, 0x00FF00, 1.0);    // Green outline full opacity

            com.osmb.api.shape.Rectangle bounds = tilePoly.getBounds();
            if (bounds != null) {
                int cx = bounds.x + bounds.width / 2;
                int cy = bounds.y + bounds.height / 2;
                canvas.drawText("ANCHOR", cx - 25, cy - 10, 0x00FF00,
                    new java.awt.Font("Arial", java.awt.Font.BOLD, 12));
            }
        }
    }

    @Override
    public void onGameStateChanged(GameState newGameState) {
        super.onGameStateChanged(newGameState);  // CRITICAL: call parent first

        ScriptLogger.warning(this, "Game state changed: " + newGameState);
        // Stub body for future state clearing
    }

    @Override
    public void onRelog() {
        ScriptLogger.info(this, "Relog detected");
        // Stub body for future state clearing
    }

    @Override
    public boolean canBreak() {
        return true;  // Phase 1 stub, will be restricted in Phase 8
    }

    @Override
    public int[] regionsToPrioritise() {
        if (anchorManager != null && anchorManager.getAnchorPosition() != null) {
            return new int[]{ anchorManager.getAnchorPosition().getRegionID() };
        }
        return new int[]{};
    }

    /**
     * Returns the AnchorManager for external access (e.g., global interrupt in Phase 5).
     *
     * @return the AnchorManager, or null if not yet initialized
     */
    public AnchorManager getAnchorManager() {
        return anchorManager;
    }
}
