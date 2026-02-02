package com.jork.script.Ectofuntus;

import com.jork.script.Ectofuntus.config.EctoConfig;
import com.jork.script.Ectofuntus.tasks.BankTask;
import com.jork.script.Ectofuntus.tasks.CollectSlimeTask;
import com.jork.script.Ectofuntus.tasks.GrindBonesTask;
import com.jork.script.Ectofuntus.tasks.WorshipTask;
import com.jork.script.Ectofuntus.ui.ScriptOptions;
import com.jork.script.Ectofuntus.utils.StateDetector;
import com.jork.script.Ectofuntus.utils.LocationState;
import com.jork.script.Ectofuntus.utils.InventoryState;
import com.jork.utils.ExceptionUtils;
import com.jork.utils.ScriptLogger;
import com.jork.utils.metrics.AbstractMetricsScript;
import com.jork.utils.metrics.core.MetricType;
import com.jork.utils.metrics.display.MetricsPanelConfig;
import com.jork.utils.teleport.TeleportDestination;
import com.jork.utils.teleport.TeleportHandler;
import com.jork.utils.teleport.TeleportHandlerFactory;
import com.jork.utils.teleport.TeleportResult;

import com.osmb.api.script.Script;
import com.osmb.api.script.ScriptDefinition;
import com.osmb.api.script.SkillCategory;
import com.osmb.api.ui.component.tabs.skill.SkillType;
import com.osmb.api.ui.GameState;
import com.osmb.api.utils.RandomUtils;
import com.osmb.api.visual.drawing.Canvas;

import javafx.scene.Scene;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ectofuntus Script - Automates prayer training at the Ectofuntus in Port Phasmatys.
 *
 * Features:
 * - Banking and teleporting with ectophial
 * - Slime collection in basement
 * - Bone grinding on top floor
 * - Worship at the altar
 * - XP failsafe and break handling
 *
 * @author jork
 * @version 1.0
 */
@ScriptDefinition(
    name = "Ectofuntus",
    author = "jork",
    version = 1.0,
    description = "Automates prayer training at the Ectofuntus",
    skillCategory = SkillCategory.PRAYER
)
public class Ectofuntus extends AbstractMetricsScript implements EctofuntusScript {

    // ───────────────────────────────────────────────────────────────────────────
    // UI Synchronization Fields
    // ───────────────────────────────────────────────────────────────────────────
    private volatile boolean settingsConfirmed = false;  // FX thread → script thread
    private boolean initialised = false;                  // Script thread internal

    private volatile EctoConfig config;  // Configuration from UI

    // ───────────────────────────────────────────────────────────────────────────
    // Task Management
    // ───────────────────────────────────────────────────────────────────────────
    private BankTask bankTask;
    private CollectSlimeTask collectSlimeTask;
    private GrindBonesTask grindBonesTask;
    private WorshipTask worshipTask;

    // ───────────────────────────────────────────────────────────────────────────
    // State Detection
    // ───────────────────────────────────────────────────────────────────────────
    private StateDetector stateDetector;
    private boolean stateDetectionCompleted = false;  // Track if startup detection ran

    // ───────────────────────────────────────────────────────────────────────────
    // State Flags
    // ───────────────────────────────────────────────────────────────────────────
    private boolean shouldBank = true;
    private boolean hasSlime = false;
    private boolean hasBoneMeal = false;

    private enum ActiveSubtask {
        NONE,
        SLIME,
        BONES
    }

    private ActiveSubtask activeSubtask = ActiveSubtask.NONE;
    private boolean isDrainingForBreak = false;

    /** Supply baseline (empty pot/bucket count after banking). Default 8, updated by BankTask. */
    private volatile int supplyBaseline = 8;

    /** Cached player agility level (detected on startup) */
    private int playerAgilityLevel = -1;

    // ───────────────────────────────────────────────────────────────────────────
    // XP Failsafe Warning Tracking
    // ───────────────────────────────────────────────────────────────────────────
    private long lastFailsafeWarningTime = 0;

    // ───────────────────────────────────────────────────────────────────────────
    // Metrics Tracking
    // ───────────────────────────────────────────────────────────────────────────
    private final AtomicInteger bonesProcessed = new AtomicInteger(0);

    public Ectofuntus(Object scriptCore) {
        super(scriptCore);
    }

    @Override
    protected void onMetricsStart() {
        ScriptLogger.startup(this, "1.0", "jork", "Ectofuntus Prayer Training");

        // Show settings window (NON-BLOCKING)
        ScriptOptions opts = new ScriptOptions(this);
        Scene scene = new Scene(opts);
        getStageController().show(scene, "Ectofuntus – Configuration", false);

        ScriptLogger.info(this, "Settings window opened – waiting for user confirmation…");

        // Handle window close as confirmation with defaults
        if (scene.getWindow() != null) {
            scene.getWindow().setOnHidden(e -> {
                if (!settingsConfirmed) {
                    // User closed window without confirming - use defaults
                    onSettingsConfirmed(EctoConfig.getDefault(), null);
                }
            });
        }
    }

    /**
     * Called from the JavaFX thread when settings are confirmed.
     * This method MUST be lightweight and avoid game API interactions.
     */
    public void onSettingsConfirmed(EctoConfig config, Map<String, Object> options) {
        this.config = config;

        // Apply debug logging preference immediately
        ScriptLogger.setDebugEnabled(config.isDebugLogging());
        ScriptLogger.info(this, "Debug logging " + (config.isDebugLogging() ? "ENABLED" : "DISABLED"));

        if (config.isXpFailsafeEnabled()) {
            ScriptLogger.info(this, "XP Failsafe ENABLED - will stop after " +
                config.getXpFailsafeTimeoutMinutes() + " minutes without XP" +
                (config.isXpFailsafePauseDuringLogout() ? " (pauses during logout)" : ""));
        } else {
            ScriptLogger.info(this, "XP Failsafe DISABLED");
        }

        this.settingsConfirmed = true;
    }

    /**
     * Initialize tasks after UI confirmation – runs on script thread
     */
    private void initialiseIfReady() {
        if (initialised || !settingsConfirmed) {
            return;
        }

        ScriptLogger.info(this, "Settings confirmed – initializing tasks");

        // Set baseline from config before any task runs
        updateSupplyBaselineFromConfig();

        // Open inventory if needed
        openInventoryIfNeeded();

        // Initialize metrics
        initializeMetrics();

        // Detect player agility level
        detectAgilityLevel();

        // Create task instances
        bankTask = new BankTask(this);
        collectSlimeTask = new CollectSlimeTask(this);
        grindBonesTask = new GrindBonesTask(this);
        worshipTask = new WorshipTask(this);

        // Initialize state detector
        stateDetector = new StateDetector(this);

        ScriptLogger.info(this, "Initialization complete. Starting tasks…");
        initialised = true;
    }

    /**
     * Sets the supply baseline based on current config.
     * Wearable teleports allow 9 supplies, otherwise 8.
     */
    private void updateSupplyBaselineFromConfig() {
        if (config == null) {
            return;
        }

        int baseline = config.getBankLocation().isWearable() ? 9 : 8;
        supplyBaseline = baseline;
        ScriptLogger.debug(this, "Supply baseline set to " + baseline + " (config)");
    }

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
        }, 2000);
    }

    @Override
    public int poll() {
        // ─── Wait for Settings Confirmation ──────────────────────────
        if (!settingsConfirmed) {
            return 1000;
        }

        // ─── Deferred Initialization ─────────────────────────────────
        initialiseIfReady();
        if (!initialised) {
            return 1000;
        }

        // ─── One-time Startup State Detection ────────────────────────
        if (!stateDetectionCompleted) {
            ScriptLogger.info(this, "Running startup state detection");
            detectAndRecoverState();
            stateDetectionCompleted = true;
            return 500;  // Allow state to settle before continuing
        }

        return runProductionMode();
    }

    /**
     * Production mode - full Ectofuntus pipeline.
     */
    private int runProductionMode() {
        // ─── XP Failsafe Check ───────────────────────────────────────
        if (config != null && config.isXpFailsafeEnabled()) {
            long timeSinceXP = getTimeSinceLastXPGain();
            long timeoutMillis = config.getXpFailsafeTimeoutMinutes() * 60 * 1000L;

            if (timeSinceXP > timeoutMillis) {
                ScriptLogger.error(this, "XP FAILSAFE TRIGGERED: No XP for " +
                    config.getXpFailsafeTimeoutMinutes() + " minutes. Stopping.");
                stop();
                return 1000;
            }

            // Warn when approaching timeout
            long warningThreshold = timeoutMillis - 60000;
            if (timeSinceXP > warningThreshold && timeSinceXP < timeoutMillis) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastFailsafeWarningTime > 30000) {
                    long secondsLeft = (timeoutMillis - timeSinceXP) / 1000;
                    ScriptLogger.warning(this, "XP Failsafe: " + secondsLeft + "s until auto-stop");
                    lastFailsafeWarningTime = currentTime;
                }
            }
        }

        // ─── Break Handling ──────────────────────────────────────────
        if (getProfileManager().isDueToBreak()) {
            if (!isDrainingForBreak) {
                ScriptLogger.info(this, "Break due - entering drain mode");
                isDrainingForBreak = true;
            }
        }

        // ─── Task Execution (Priority Order) ─────────────────────────
        // Priority 1: Banking
        if (bankTask.canExecute()) {
            activeSubtask = ActiveSubtask.NONE;
            return bankTask.execute();
        }

        // Priority 2/3: Slime and Bones (randomized order handled in tasks)
        boolean canCollectSlime = collectSlimeTask.canExecute();
        boolean canGrindBones = grindBonesTask.canExecute();
        if (hasSlime && activeSubtask == ActiveSubtask.SLIME) {
            activeSubtask = ActiveSubtask.NONE;
        }
        if (hasBoneMeal && activeSubtask == ActiveSubtask.BONES) {
            activeSubtask = ActiveSubtask.NONE;
        }

        if (canCollectSlime && canGrindBones) {
            if (activeSubtask == ActiveSubtask.NONE) {
                activeSubtask = RandomUtils.uniformRandom(0, 1) == 0
                    ? ActiveSubtask.SLIME
                    : ActiveSubtask.BONES;
                ScriptLogger.info(this, "Selected subtask: " + activeSubtask);
            }

            if (activeSubtask == ActiveSubtask.SLIME) {
                return collectSlimeTask.execute();
            }
            if (activeSubtask == ActiveSubtask.BONES) {
                return grindBonesTask.execute();
            }
        } else if (canCollectSlime) {
            activeSubtask = ActiveSubtask.SLIME;
            return collectSlimeTask.execute();
        } else if (canGrindBones) {
            activeSubtask = ActiveSubtask.BONES;
            return grindBonesTask.execute();
        }

        activeSubtask = ActiveSubtask.NONE;

        // Priority 5: Worship
        if (worshipTask.canExecute()) {
            return worshipTask.execute();
        }

        // Default poll rate
        return 500;
    }

    @Override
    public boolean promptBankTabDialogue() {
        // Enable OSMB's bank tab dialogue - user selects tab, OSMB handles switching
        return true;
    }

    @Override
    public boolean canBreak() {
        // Only allow break when we're in a safe state (at bank without resources)
        boolean canBreakNow = shouldBank && !hasSlime && !hasBoneMeal;

        if (canBreakNow && isDrainingForBreak) {
            ScriptLogger.info(this, "Drain complete - allowing break");
            isDrainingForBreak = false;
        }

        return canBreakNow;
    }

    @Override
    public void onGameStateChanged(GameState newGameState) {
        super.onGameStateChanged(newGameState);  // CRITICAL: call parent

        if (newGameState != null && newGameState != GameState.LOGGED_IN) {
            // Logged out - clear tracked state (game objects lost)
            ScriptLogger.warning(this, "Logout detected - clearing state");
            clearTrackedState();

            if (config != null && config.isXpFailsafePauseDuringLogout() && config.isXpFailsafeEnabled()) {
                pauseXPFailsafeTimer();
            }
        } else if (newGameState == GameState.LOGGED_IN) {
            // Logged back in - detect state and recover
            ScriptLogger.info(this, "Logged in - detecting state and recovering");
            detectAndRecoverState();

            if (config != null && config.isXpFailsafePauseDuringLogout() && config.isXpFailsafeEnabled()) {
                resumeXPFailsafeTimer();
            }
        }
    }

    @Override
    public void onRelog() {
        ScriptLogger.info(this, "Relog detected - clearing state and recovering");
        clearTrackedState();
        detectAndRecoverState();
    }

    /**
     * Clears all tracked state after logout/relog.
     * All game objects are destroyed on logout, so cached references must be cleared.
     */
    private void clearTrackedState() {
        // Reset state detection flag to allow re-detection after relog
        stateDetectionCompleted = false;

        // Reset drain flag on logout
        isDrainingForBreak = false;

        // No cached game object references in this script currently
        // (tasks handle their own object lookups each poll)
    }

    /**
     * Detects current state based on inventory and location after login/relog.
     * Uses StateDetector utility for robust location and inventory detection.
     */
    private void detectAndRecoverState() {
        if (!initialised || config == null || stateDetector == null) {
            ScriptLogger.debug(this, "Script not initialized yet, skipping state detection");
            return;
        }

        try {
            // Detect location and inventory state using StateDetector
            LocationState location = stateDetector.detectCurrentLocation();
            InventoryState inventory = stateDetector.detectInventoryState();

            ScriptLogger.info(this, "State detection - Location: " + location + ", Inventory: " + inventory);

            // Update state flags based on inventory
            int boneMealCount = countItemsInInventory(EctofuntusConstants.BONEMEAL_IDS);
            int slimeCount = countItemInInventory(4286);     // Bucket of slime
            hasBoneMeal = boneMealCount > 0;
            hasSlime = slimeCount > 0;

            // Handle unknown location (emergency recovery)
            if (location == LocationState.UNKNOWN) {
                handleUnknownLocationRecovery();
                return;
            }

            // Determine if we should bank based on inventory state
            switch (inventory) {
                case READY_TO_WORSHIP:
                case PARTIAL_WORSHIP_READY:
                    shouldBank = false;
                    ScriptLogger.info(this, "State: Ready to worship (" + boneMealCount + " bonemeal, " + slimeCount + " slime)");
                    break;

                case NEED_PROCESSING:
                case NEED_SLIME_ONLY:
                case NEED_DUST_ONLY:
                    shouldBank = false;
                    ScriptLogger.info(this, "State: Mid-cycle, continuing tasks");
                    break;

                case NEED_RESTOCK:
                case EMPTY_NEED_BANK:
                    shouldBank = true;
                    ScriptLogger.info(this, "State: Need to bank");
                    break;

                case UNKNOWN:
                    // Fallback to safe state
                    shouldBank = true;
                    ScriptLogger.warning(this, "Unknown inventory state - defaulting to bank");
                    break;
            }

            // Reset drain flag on login
            isDrainingForBreak = false;

        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.warning(this, "Error during state detection: " + e.getMessage());
            // Fallback to safe state (bank)
            hasSlime = false;
            hasBoneMeal = false;
            shouldBank = true;
        }
    }

    /**
     * Handles recovery when the player is at an unknown location.
     * Attempts to teleport to the configured bank location.
     * If teleport fails, stops the script.
     */
    private void handleUnknownLocationRecovery() {
        ScriptLogger.warning(this, "Unknown location detected - attempting bank teleport recovery");

        EctoConfig config = getConfig();
        if (config == null) {
            ScriptLogger.error(this, "Cannot recover - no config available");
            stop();
            return;
        }

        TeleportHandler bankTeleport = TeleportHandlerFactory.fromBankLocationName(
            this,
            config.getBankLocation().getDisplayName()
        );

        if (bankTeleport == null) {
            ScriptLogger.error(this, "Cannot create bank teleport handler - stopping");
            stop();
            return;
        }

        TeleportResult result = bankTeleport.teleport();
        if (!result.isSuccess()) {
            ScriptLogger.error(this, "Bank teleport recovery failed: " + result + " - stopping");
            stop();
            return;
        }

        ScriptLogger.info(this, "Recovery successful - now at bank");
        setShouldBank(true);
    }

    /**
     * Counts how many of a specific item are in the inventory.
     */
    private int countItemInInventory(int itemId) {
        try {
            if (getWidgetManager() == null || getWidgetManager().getInventory() == null) {
                return 0;
            }

            var searchResult = getWidgetManager().getInventory().search(Set.of(itemId));
            if (searchResult == null) {
                return 0;
            }

            return searchResult.getAmount(itemId);
        } catch (Exception e) {
            ScriptLogger.debug(this, "Error counting item " + itemId + ": " + e.getMessage());
            return 0;
        }
    }

    /**
     * Counts how many of a set of items are in the inventory.
     * Uses a single search call to avoid double-counting visually identical items.
     */
    private int countItemsInInventory(Set<Integer> itemIds) {
        try {
            if (getWidgetManager() == null || getWidgetManager().getInventory() == null) {
                return 0;
            }

            var searchResult = getWidgetManager().getInventory().search(itemIds);
            if (searchResult == null) {
                return 0;
            }

            return searchResult.getAmount(itemIds);
        } catch (Exception e) {
            ScriptLogger.debug(this, "Error counting items: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Detects player agility level from skills tab.
     * Called once during initialization (deferred to script thread).
     * Falls back to level 1 if skill tab unavailable.
     */
    private void detectAgilityLevel() {
        if (getWidgetManager() == null) {
            ScriptLogger.warning(this, "Widget manager unavailable - defaulting agility to 1");
            playerAgilityLevel = 1;
            return;
        }

        var skillTab = getWidgetManager().getSkillTab();
        if (skillTab == null) {
            ScriptLogger.warning(this, "Skills tab unavailable - defaulting agility to 1");
            playerAgilityLevel = 1;
            return;
        }

        var agilityInfo = skillTab.getSkillLevel(SkillType.AGILITY);
        if (agilityInfo == null) {
            ScriptLogger.warning(this, "Agility level info unavailable - defaulting to 1");
            playerAgilityLevel = 1;
            return;
        }

        playerAgilityLevel = agilityInfo.getLevel();
        ScriptLogger.info(this, "Detected agility level: " + playerAgilityLevel +
            (playerAgilityLevel >= EctofuntusConstants.AGILITY_SHORTCUT_LEVEL ? " (shortcut available)" : ""));
    }

    /**
     * Initialize metrics tracking
     */
    private void initializeMetrics() {
        // Activity label
        registerMetric("Activity", () -> getCurrentActivity(), MetricType.TEXT);

        // Enable Prayer XP tracking (sprite ID for Prayer skill)
        enableXPTracking(SkillType.PRAYER, 210);  // 210 = Prayer sprite

        // Bones processed
        registerMetric("Bones Used", bonesProcessed::get, MetricType.NUMBER);
        registerMetric("Bones /h", bonesProcessed::get, MetricType.RATE);

        // Time since XP (if failsafe enabled)
        if (config != null && config.isXpFailsafeEnabled()) {
            registerMetric("Since XP", () -> {
                long ms = getTimeSinceLastXPGain();
                return (ms < 60000) ? (ms / 1000) + "s" : (ms / 60000) + "m";
            }, MetricType.TEXT);

            if (config.isXpFailsafePauseDuringLogout()) {
                configureXPFailsafeTimerPause(true);
            }
        }
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
        // Custom overlay drawing if needed
    }

    private String getCurrentActivity() {
        if (!initialised) return "Initializing";
        if (shouldBank) return "Banking";
        if (!hasSlime && !hasBoneMeal) return "Teleporting";
        if (!hasSlime) return "Collecting Slime";
        if (!hasBoneMeal) return "Grinding Bones";
        return "Worshipping";
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Getters for Tasks
    // ───────────────────────────────────────────────────────────────────────────

    public EctoConfig getConfig() {
        return config;
    }

    public boolean shouldBank() {
        return shouldBank;
    }

    public void setShouldBank(boolean shouldBank) {
        this.shouldBank = shouldBank;
    }

    public boolean hasSlime() {
        return hasSlime;
    }

    public void setHasSlime(boolean hasSlime) {
        this.hasSlime = hasSlime;
    }

    public boolean hasBoneMeal() {
        return hasBoneMeal;
    }

    public void setHasBoneMeal(boolean hasBoneMeal) {
        this.hasBoneMeal = hasBoneMeal;
    }

    public boolean isDrainingForBreak() {
        return isDrainingForBreak;
    }

    @Override
    public void incrementBonesProcessed() {
        bonesProcessed.incrementAndGet();
    }

    @Override
    public void incrementBonesProcessed(int count) {
        bonesProcessed.addAndGet(count);
    }

    @Override
    public Script getScript() {
        return this;
    }

    @Override
    public int getPlayerAgilityLevel() {
        return playerAgilityLevel;
    }

    @Override
    public boolean canUseAgilityShortcut() {
        return playerAgilityLevel >= EctofuntusConstants.AGILITY_SHORTCUT_LEVEL;
    }

    /**
     * Returns region IDs to pre-load for improved script performance.
     * Always includes Ectofuntus region. Adds bank destination regions when config is available.
     *
     * @return Array of region IDs to prioritize loading
     */
    @Override
    public int[] regionsToPrioritise() {
        Set<Integer> regions = new HashSet<>();

        // Always add Ectofuntus region (script always operates here)
        regions.add(EctofuntusConstants.ECTOFUNTUS_REGION);
        regions.add(EctofuntusConstants.SLIME_DUNGEON_REGION);
        regions.addAll(EctofuntusConstants.ALTAR_REGIONS);

        // Add bank teleport destination regions if config available
        if (config != null) {
            try {
                TeleportHandler bankHandler = TeleportHandlerFactory.fromBankLocationName(
                    this,
                    config.getBankLocation().getDisplayName()
                );

                if (bankHandler != null) {
                    TeleportDestination dest = bankHandler.getDestination();
                    if (dest != null) {
                        // Add teleport landing region
                        if (dest.hasRegionId()) {
                            regions.add(dest.getRegionId());
                        }

                        // Add walk target region (may differ from landing)
                        int walkRegion = dest.getWalkTargetRegionId();
                        if (walkRegion > 0) {
                            regions.add(walkRegion);
                        }
                    }
                }
            } catch (Exception e) {
                // Config not ready or handler creation failed - just use Ectofuntus region
                ScriptLogger.debug(this, "Could not determine bank regions: " + e.getMessage());
            }
        }

        return regions.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public int getSupplyBaseline() {
        return supplyBaseline;
    }

    @Override
    public void setSupplyBaseline(int baseline) {
        this.supplyBaseline = baseline;
    }
}
