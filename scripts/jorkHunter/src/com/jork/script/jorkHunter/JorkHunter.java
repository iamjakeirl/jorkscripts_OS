package com.jork.script.jorkHunter;

import com.jork.script.jorkHunter.config.HuntingConfig;
import com.jork.script.jorkHunter.tasks.DropTask;
import com.jork.script.jorkHunter.tasks.TrapTask;
import com.jork.script.jorkHunter.state.TrapStateManager;

import com.jork.script.jorkHunter.trap.TrapType;
import com.jork.script.jorkHunter.utils.tasks.TaskManager;
import com.jork.script.jorkHunter.utils.placement.TrapPlacementStrategy;
import com.jork.script.jorkHunter.utils.placement.NoCardinalStrategy;
import com.jork.script.jorkHunter.utils.placement.XPatternStrategy;
import com.jork.script.jorkHunter.utils.placement.LPatternStrategy;
import com.jork.script.jorkHunter.utils.placement.LinePatternStrategy;
import com.jork.script.jorkHunter.utils.placement.CrossPatternStrategy;
import com.jork.script.jorkHunter.utils.placement.AutoPatternStrategy;
import com.jork.script.jorkHunter.utils.placement.CustomTilePickerStrategy;
import com.osmb.api.ui.tabs.Skill;
import com.osmb.api.ui.tabs.Settings;
import com.osmb.api.ui.component.tabs.skill.SkillType;
import com.osmb.api.utils.UIResult;
import com.osmb.api.ui.component.tabs.skill.SkillsTabComponent.SkillLevel;
import com.osmb.api.ui.GameState;

import com.osmb.api.ui.component.popout.PopoutPanelContainer;
import com.osmb.api.ui.component.ComponentContainerStatus;
import com.jork.script.jorkHunter.javafx.ScriptOptions;
import com.jork.utils.tilepicker.EnhancedTilePickerPanel;
import com.jork.utils.ExceptionUtils;
import com.jork.utils.ScriptLogger;
import com.jork.utils.metrics.AbstractMetricsScript;
import com.jork.utils.metrics.core.MetricType;
import com.jork.utils.metrics.display.MetricsPanelConfig;

import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.visual.drawing.Canvas;
import com.osmb.api.shape.Polygon;
import com.osmb.api.shape.Rectangle;

import com.osmb.api.utils.RandomUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// JorkHunter Script - Base Class
// The @ScriptDefinition annotations are in the variant subclasses (variants package)
// This allows each JAR to have a different name in the OSMB client
public class JorkHunter extends AbstractMetricsScript {

    private final TaskManager taskManager;
    private HuntingConfig huntingConfig; // Configuration for this variant
    private TrapTask huntTask; // Reference to access TrapStateManager
    private int maxTraps = 1; // Default to 1 trap, will be updated based on Hunter level
    // ───────────────────────────────────────────────────────────────────────────
    // UI synchronisation fields
    // These are written by the JavaFX thread and read by the script thread.
    // -------------------------------------------------------------------------
    private volatile boolean settingsConfirmed = false; // FX-thread sets → script thread reads
    private boolean initialised = false;                // script-thread internal guard

    private volatile String selectedTarget = "Unknown";
    private volatile String selectedStrategy = "X-Pattern";
    private volatile int selectedManualLevel = -1;
    private volatile Map<String, Object> strategyOptions = null;

    // --- Trap Management (Centralized) ---------------------------------------
    // This flag is used to signal that the script should stop laying new traps
    // and just clear existing ones before a break.
    private boolean isDrainingForBreak = false;
    private boolean zoomSet = false;
    
    // --- Expedite Collection Settings ----------------------------------------
    private boolean expediteCollectionEnabled = false;  // Whether expedite collection is enabled
    private int expediteCollectionChance = 50;  // Chance (0-100) to expedite collection
    private boolean hasTriggeredExpedite = false;  // Whether we've already triggered expedite for this drain
    
    // --- XP-Based Failsafe Settings ------------------------------------------
    private volatile boolean xpFailsafeEnabled = false;  // Whether XP failsafe is enabled
    private volatile int xpFailsafeTimeoutMinutes = 10;  // Minutes without XP before stopping
    private volatile boolean xpFailsafePauseDuringLogout = true;  // Whether to pause during logout
    private long lastFailsafeWarningTime = 0;  // Track when we last warned about failsafe
    
    // --- Trap Prioritization Settings ----------------------------------------
    private volatile boolean distanceBasedPrioritization = true;  // Permanently enabled for efficiency
    
    // --- Custom Anchor State Management --------------------------------------
    private volatile boolean requiresCustomAnchor = false;
    private volatile boolean customAnchorSelected = false;
    private WorldPosition customAnchorPosition = null;
    private boolean anchorSelectionInProgress = false;
    private List<WorldPosition> customTrapPositions = null;  // For Custom strategy multi-tile selection
    
    // --- Metrics Tracking -----------------------------------------------------
    private final AtomicInteger successfulCatches = new AtomicInteger(0);
    private final AtomicInteger failedCatches = new AtomicInteger(0);
    private final AtomicInteger totalChecks = new AtomicInteger(0);

    public JorkHunter(Object scriptCore) {
        super(scriptCore);
        this.taskManager = new TaskManager(this);
    }

    @Override
    protected void onMetricsStart() {
        // Load configuration for this variant
        huntingConfig = HuntingConfig.load(this);
        
        // Log script startup with variant name
        ScriptLogger.startup(this, "1.0", "jork", huntingConfig.getVariantName());

        // ── Show settings window (NON-BLOCKING) ────────────────────────────
        ScriptOptions opts = new ScriptOptions(this, huntingConfig);
        javafx.scene.Scene scene = new javafx.scene.Scene(opts);
        getStageController().show(scene, "JorkHunter – Options", false);

        ScriptLogger.info(this, "Settings window opened – waiting for user confirmation…");

        // If the user closes the window via the X button treat it as confirmation
        if (scene.getWindow() != null) {
            scene.getWindow().setOnHidden(e -> {
                Map<String, Object> defaultOptions = new HashMap<>();
                // Strategy defaults
                defaultOptions.put("maxCenterDistance", 0);
                defaultOptions.put("recenterOnEmpty", false);
                defaultOptions.put("requiresCustomAnchor", true);
                // Logging/expedite defaults to mirror UI
                defaultOptions.put("debugLogging", false);
                defaultOptions.put("expediteCollection", false);
                defaultOptions.put("expediteChance", 50);
                // Failsafe defaults to mirror UI (enabled, 5 minutes)
                defaultOptions.put("xpFailsafeEnabled", true);
                defaultOptions.put("xpFailsafeTimeout", 5);
                onSettingsSelected(this.selectedTarget, null, this.selectedStrategy, false, -1, defaultOptions);
            });
        }

        // No further game interaction here – wait for UI confirmation
    }

    /**
     * Called from the JavaFX thread when the user presses the Start button or
     * closes the settings window. This method MUST be lightweight and avoid
     * interacting with the game API. It only stores the selections and sets a
     * volatile flag that the script thread will act upon in its next poll.
     */
    public void onSettingsSelected(String target, String area, String strategy, boolean manual, int lvl, Map<String, Object> options) {
        this.selectedTarget = target;
        this.selectedStrategy = strategy;
        this.strategyOptions = options;
        
        // Always use TilePicker now
        this.requiresCustomAnchor = true;
        ScriptLogger.info(this, "TilePicker mode enabled - tile selection will occur after initialization");
        
        // Extract expedite collection settings if present
        if (options != null) {
            this.expediteCollectionEnabled = Boolean.TRUE.equals(options.get("expediteCollection"));
            Object chanceObj = options.get("expediteChance");
            if (chanceObj instanceof Integer) {
                this.expediteCollectionChance = (Integer) chanceObj;
            }
            // Apply debug logging toggle if provided
            Object dbgObj = options.get("debugLogging");
            if (dbgObj instanceof Boolean) {
                com.jork.utils.ScriptLogger.setDebugEnabled((Boolean) dbgObj);
            }
            // Distance-based prioritization is now permanently enabled
            // Keeping parsing code commented for potential future use:
            // Object distPriorObj = options.get("distanceBasedPrioritization");
            // if (distPriorObj instanceof Boolean) {
            //     this.distanceBasedPrioritization = (Boolean) distPriorObj;
            // }
            this.distanceBasedPrioritization = true; // Always enabled
        }
        
        if (expediteCollectionEnabled) {
            ScriptLogger.info(this, "Expedite collection enabled with " + expediteCollectionChance + "% chance");
        }
        
        // Always log that distance-based prioritization is enabled
        ScriptLogger.info(this, "Distance-based trap prioritization ENABLED");
        
        // Extract XP failsafe settings if present
        if (options != null) {
            Object failsafeObj = options.get("xpFailsafeEnabled");
            ScriptLogger.debug(this, "XP Failsafe enabled value from UI: " + failsafeObj);
            if (failsafeObj instanceof Boolean) {
                this.xpFailsafeEnabled = (Boolean) failsafeObj;
            }
            Object timeoutObj = options.get("xpFailsafeTimeout");
            ScriptLogger.debug(this, "XP Failsafe timeout value from UI: " + timeoutObj + " (type: " + 
                             (timeoutObj != null ? timeoutObj.getClass().getSimpleName() : "null") + ")");
            if (timeoutObj instanceof Integer) {
                this.xpFailsafeTimeoutMinutes = (Integer) timeoutObj;
            }
            Object pauseDuringLogoutObj = options.get("xpFailsafePauseDuringLogout");
            if (pauseDuringLogoutObj instanceof Boolean) {
                this.xpFailsafePauseDuringLogout = (Boolean) pauseDuringLogoutObj;
            }
        }
        
        if (xpFailsafeEnabled) {
            ScriptLogger.info(this, "XP Failsafe ENABLED - will stop after " + 
                             xpFailsafeTimeoutMinutes + " minutes without XP gains" +
                             (xpFailsafePauseDuringLogout ? " (pauses during logout)" : ""));
        } else {
            ScriptLogger.info(this, "XP Failsafe DISABLED");
        }

        if (manual && lvl > 0) {
            this.selectedManualLevel = lvl;
        }
        this.settingsConfirmed = true;
    }

    // ───────────────────────────────────────────────────────────────────────
    // Initialise after UI confirmation – runs on script thread
    // -----------------------------------------------------------------------
    private void initialiseIfReady() {
        if (initialised || !settingsConfirmed) {
            return;
        }

        ScriptLogger.info(this, "Settings confirmed – Target: " + selectedTarget + " | Strategy: " + selectedStrategy);

        // No need to set expected region ID - will be determined after TilePicker

        // Ensure the inventory is open before beginning tasks
        openInventoryIfNeeded();

        // Determine hunter level either manual or automatic
        if (selectedManualLevel > 0) {
            int level = selectedManualLevel;
            maxTraps = calculateTrapsForLevel(level);
            ScriptLogger.info(this, "Manual Hunter Level: " + level + " | Max Traps: " + maxTraps);
        } else {
            calculateMaxTraps();
        }

        // Show hotkeys panel (naming reflects actual behavior)
        showHotkeysPanel();
        
        // Initialize metrics
        initializeMetrics();

        ScriptLogger.info(this, "Initialisation complete. Starting tasks…");
        initialised = true;
    }

    /**
     * Sets the zoom level to a random value between MIN_ZOOM and MAX_ZOOM.
     * This method is called once at script start with highest priority.
     */
    private void setZoom() {
        log("Checking zoom level...");
        if (getWidgetManager() == null) {
            log("WidgetManager not ready; delaying zoom check");
            return;
        }
        Settings settings = getWidgetManager().getSettings();

        // Define our desired zoom range (27-34% for hunting)
        final int MIN_ZOOM = 24;
        final int MAX_ZOOM = 31;

        // First check if zoom is already in acceptable range
        if (settings != null) {
            UIResult<Integer> currentZoomResult = settings.getZoomLevel();
            if (currentZoomResult.isFound()) {
                int currentZoom = currentZoomResult.get();
                log("Current zoom level: " + currentZoom + "%");
                if (currentZoom >= MIN_ZOOM && currentZoom <= MAX_ZOOM) {
                    log("Zoom already in acceptable range: " + currentZoom + "%");
                    zoomSet = true;
                    return;
                }
            }
        }
        // Generate random zoom level between MIN_ZOOM and MAX_ZOOM
        int targetZoom = RandomUtils.uniformRandom(MIN_ZOOM, MAX_ZOOM);
        log("Setting zoom to: " + targetZoom + "%");

        // Attempt to set zoom with a single backoff retry
        if (settings != null && settings.setZoomLevel(targetZoom)) {
            log("Zoom set successfully to: " + targetZoom + "%");
            zoomSet = true;
            return;
        }

        // Backoff and retry once if settings is null or first attempt failed
        try { Thread.sleep(RandomUtils.weightedRandom(250, 400)); } catch (InterruptedException ignored) {}
        Settings retrySettings = getWidgetManager() != null ? getWidgetManager().getSettings() : null;
        if (retrySettings != null && retrySettings.setZoomLevel(targetZoom)) {
            log("Zoom set successfully (retry) to: " + targetZoom + "%");
            zoomSet = true;
        }
    }

    private void openInventoryIfNeeded() {
        // Early exit if inventory is already open - no need for human task
        if (getWidgetManager().getInventory().isOpen()) {
            return;
        }
        
        pollFramesHuman(() -> {
            if (!getWidgetManager().getInventory().isOpen()) {
                return getWidgetManager().getInventory().open();
            }
            return true;
        }, 2000);
    }

    /**
     * Shows the hotkeys panel by collapsing the PopoutPanelContainer.
     * Note: Naming reflects current API behavior; does not toggle tap-to-drop.
     */
    private void showHotkeysPanel() {
        ScriptLogger.debug(this, "Checking hotkey panel visibility...");
        
        try {
            // Get the PopoutPanelContainer - when EXPANDED, it shows the button to open hotkeys
            PopoutPanelContainer popoutPanelContainer = (PopoutPanelContainer) getWidgetManager().getComponent(PopoutPanelContainer.class);
            
            if (popoutPanelContainer != null) {
                Rectangle bounds = popoutPanelContainer.getBounds();
                if (bounds != null) {
                    ComponentContainerStatus containerStatus = popoutPanelContainer.getResult().getComponentImage().getGameFrameStatusType();
                    ScriptLogger.debug(this, "PopoutPanelContainer status: " + containerStatus);
                    
                    if (containerStatus == ComponentContainerStatus.EXPANDED) {
                        // When EXPANDED, we can see the button to toggle hotkeys
                        // Tap it to collapse the container and reveal the hotkeys
                        ScriptLogger.info(this, "PopoutPanel expanded - tapping to reveal hotkeys");
                        
                        Rectangle bottomButton = new Rectangle(bounds.x + 5, bounds.y + bounds.height - 38, bounds.width - 10, 35);
                        if (getFinger().tap(bottomButton)) {
                            ScriptLogger.debug(this, "Tapped hotkey button, waiting for collapse...");
                            
                            // Wait until the popout panel has collapsed (which reveals the hotkeys)
                            submitTask(() -> {
                                Rectangle boundsPresent = popoutPanelContainer.getBounds();
                                if (boundsPresent == null) {
                                    return false;
                                }
                                return popoutPanelContainer.getResult().getComponentImage().getGameFrameStatusType() == ComponentContainerStatus.COLLAPSED;
                            }, RandomUtils.uniformRandom(1000, 2000));
                            
                            ScriptLogger.info(this, "Hotkey panel revealed");
                            
                            // Give a short delay for UI to update
                            Thread.sleep(RandomUtils.weightedRandom(500, 800));
                        } else {
                            ScriptLogger.warning(this, "Failed to tap the hotkey button");
                        }
                    } else if (containerStatus == ComponentContainerStatus.COLLAPSED) {
                        ScriptLogger.debug(this, "PopoutPanel collapsed (hotkeys visible)");
                    }
                } else {
                    ScriptLogger.warning(this, "PopoutPanelContainer bounds are null");
                }
            } else {
                ScriptLogger.warning(this, "PopoutPanelContainer is null");
            }
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.exception(this, "showing hotkeys panel", e);
        }
    }

    private void calculateMaxTraps() {
        ScriptLogger.debug(this, "Starting Hunter level calculation...");
        
        // Null checks first
        if (getWidgetManager() == null) {
            ScriptLogger.error(this, "WidgetManager is null!");
            return;
        }
        
        // Use concrete type from the start
        Skill skillTab = getWidgetManager().getSkillTab();
        if (skillTab == null) {
            ScriptLogger.error(this, "SkillTab is null!");
            return;
        }
        
        ScriptLogger.debug(this, "SkillTab obtained, attempting to get Hunter level...");
        
        // Try with retries
        int maxRetries = 2;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            if (attempt > 0) {
                ScriptLogger.debug(this, "Retrying Hunter level read (attempt " + (attempt + 1) + "/" + maxRetries + ")");
            }
            
            // Get skill level
            SkillLevel hunterInfo = skillTab.getSkillLevel(SkillType.HUNTER);
            if (hunterInfo == null) {
                ScriptLogger.warning(this, "HunterInfo is null - skills tab may not be accessible");
                continue; // Try again
            }
            
            int level = hunterInfo.getLevel();
            ScriptLogger.debug(this, "Raw Hunter level from API: " + level);
            
            // Calculate max traps using helper method
            maxTraps = calculateTrapsForLevel(level);
            ScriptLogger.info(this, "Hunter Level: " + level + " | Calculated Max Traps: " + maxTraps);
            return; // Success
        }
        
        // All retries failed
        ScriptLogger.warning(this, "Could not read Hunter level after " + maxRetries + " attempts, defaulting to 1 trap");
        maxTraps = 1;
    }
    
    /**
     * Helper method to calculate max traps based on Hunter level.
     * Formula: 1 trap at level 1-19, 2 at 20-39, 3 at 40-59, 4 at 60-79, 5 at 80+
     */
    private int calculateTrapsForLevel(int level) {
        return Math.min(5, level / 20 + 1);
    }

    public int getMaxTraps() {
        // If maxTraps is still 1 and we're above level 20, try to recalculate
        if (maxTraps == 1) {
            Skill skillTab = getWidgetManager().getSkillTab();
            if (skillTab != null) {
                SkillLevel hunterInfo = skillTab.getSkillLevel(SkillType.HUNTER);
                if (hunterInfo != null) {
                    int level = hunterInfo.getLevel();
                    if (level >= 20) {
                        maxTraps = calculateTrapsForLevel(level);
                        ScriptLogger.info(this, "Recalculated - Hunter Level: " + level + " | Max Traps: " + maxTraps);
                    }
                }
            }
        }
        return maxTraps;
    }

    public void initializeTasks() {
        // Determine trap type based on selected target and configuration
        TrapType trapType = determineTrapType();
        
        // Create placement strategy based on user selection
        TrapPlacementStrategy strategy = createStrategyFromSelection(selectedStrategy);
        
        // Determine hunting zones based on strategy
        List<RectangleArea> huntingZones;
        if ("Custom".equals(selectedStrategy) && strategy instanceof CustomTilePickerStrategy) {
            // For Custom strategy, use the bounding area from selected positions
            CustomTilePickerStrategy customStrategy = (CustomTilePickerStrategy) strategy;
            RectangleArea boundingArea = customStrategy.getBoundingArea();
            if (boundingArea != null) {
                huntingZones = Collections.singletonList(boundingArea);
                ScriptLogger.info(this, "Using custom bounding area from " + 
                                customStrategy.getSelectedPositions().size() + " selected positions");
            } else {
                ScriptLogger.warning(this, "Custom strategy has no bounding area, using default");
                huntingZones = Collections.emptyList();
            }
        } else if (customAnchorPosition != null) {
            // For other strategies, create a 5x5 area centered on the custom anchor
            int areaSize = 5;
            RectangleArea customZone = new RectangleArea(
                customAnchorPosition.getX() - areaSize / 2,
                customAnchorPosition.getY() - areaSize / 2,
                areaSize,
                areaSize,
                customAnchorPosition.getPlane()
            );
            huntingZones = Collections.singletonList(customZone);
            ScriptLogger.info(this, "Using custom 5x5 hunting zone centered at " + customAnchorPosition);
        } else {
            // Fallback: create default zone around player position
            WorldPosition playerPos = getWorldPosition();
            if (playerPos != null) {
                ScriptLogger.warning(this, "Custom anchor not selected properly - creating default 5x5 zone around current position");
                int areaSize = 5;
                RectangleArea fallbackZone = new RectangleArea(
                    playerPos.getX() - areaSize / 2,
                    playerPos.getY() - areaSize / 2,
                    areaSize,
                    areaSize,
                    playerPos.getPlane()
                );
                huntingZones = Collections.singletonList(fallbackZone);
                ScriptLogger.info(this, "Using fallback 5x5 hunting zone centered at player position: " + playerPos);
            } else {
                ScriptLogger.error(this, "No custom anchor and player position unavailable - using empty hunting zones");
                huntingZones = Collections.emptyList();
            }
        }
        
        // Create and store reference to HuntTask
        huntTask = new TrapTask(this, trapType, maxTraps, huntingZones, strategy);
        
        // Add common tasks
        taskManager.addTasks(
            new DropTask(this, trapType.getDropItems(), null), // No predefined location
            huntTask
        );

        // Add other hunter-specific tasks here in the future
    }

    @Override
    public int[] regionsToPrioritise() {
        // Return all possible region IDs based on creature type
        String targetLower = selectedTarget != null ? selectedTarget.toLowerCase() : "";
        
        // Check if it's a bird type
        if (targetLower.contains("swift") || targetLower.contains("longtail") || 
            targetLower.contains("wagtail") || targetLower.contains("twitch")) {
            return HunterLocationConstants.BIRD_REGIONS;
        } 
        // Check if it's a chinchompa type
        else if (targetLower.contains("chinchompa")) {
            return HunterLocationConstants.CHINCHOMPA_REGIONS;
        }
        return new int[] {}; // Return empty array if no target selected
    }

    @Override
    public int poll() {
        // --- Highest Priority: Set zoom level (only once) -------------------
        if (!zoomSet) {
            setZoom();
            return 0; // Immediate retry
        }
        
        // --- XP Failsafe Check ------------------------------------------------
        if (xpFailsafeEnabled && initialised) {
            long timeSinceXP = getTimeSinceLastXPGain();
            long timeoutMillis = xpFailsafeTimeoutMinutes * 60 * 1000L;
            
            // Only trigger failsafe if we've been running for at least the timeout period
            // This prevents false triggers at script start
            if (timeSinceXP > timeoutMillis) {
                ScriptLogger.error(this, "XP FAILSAFE TRIGGERED: No XP gained for " + 
                    xpFailsafeTimeoutMinutes + " minutes. Stopping script.");
                ScriptLogger.error(this, "Last XP gain was: " + getTimeSinceLastXPGainFormatted() + " ago");
                stop();
                return 1000;
            }
            
            // Log warning when approaching timeout (every 30 seconds in the last minute)
            long warningThreshold = timeoutMillis - (60 * 1000); // 1 minute before timeout
            if (timeSinceXP > warningThreshold && timeSinceXP < timeoutMillis) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastFailsafeWarningTime > 30000) { // Warn every 30 seconds
                    ScriptLogger.warning(this, "XP Failsafe warning: " + 
                        ((timeoutMillis - timeSinceXP) / 1000) + " seconds until auto-stop");
                    lastFailsafeWarningTime = currentTime;
                }
            }
        }
        
        // --- Pre-Task-Execution Checks (runs every poll) ---------------------
        
        // Proactively check if a break is due, and if so, start draining traps.
        if (getProfileManager().isDueToBreak()) {
            if (!this.isDrainingForBreak) {
                ScriptLogger.info(this, "Break is due. Entering drain mode to clear active traps.");
                this.isDrainingForBreak = true;
                
                // Check if we should trigger expedited collection
                if (expediteCollectionEnabled && !hasTriggeredExpedite && huntTask != null) {
                    int roll = RandomUtils.uniformRandom(1, 100);
                    if (roll <= expediteCollectionChance) {
                        ScriptLogger.info(this, "Expedite collection triggered! (Roll: " + roll + "/" + expediteCollectionChance + ")");
                        huntTask.expediteTrapsForBreak();
                        hasTriggeredExpedite = true;
                    } else {
                        ScriptLogger.info(this, "Expedite collection not triggered (Roll: " + roll + "/" + expediteCollectionChance + ")");
                    }
                }
            }
        }


        // --- Settings Confirmation Check -------------------------------------
        // Do nothing until the user has confirmed the settings in the UI.
        if (!settingsConfirmed) {
            return 1000; // Keep waiting until initialised
        }

        // Handle deferred initialisation once the UI has been confirmed
        initialiseIfReady();

        if (!initialised) {
            return 1000; // Keep waiting until initialised
        }

        // --- Custom Anchor Selection (after position is established) ----------
        if (requiresCustomAnchor && !customAnchorSelected && !anchorSelectionInProgress) {
            return handleCustomAnchorSelection();
        }
        
        // Wait for anchor if needed
        if (requiresCustomAnchor && !customAnchorSelected) {
            return 500; // Wait for anchor selection
        }

        // If settings are confirmed and the task manager is ready, execute tasks
        if (taskManager != null) {
            return taskManager.executeNextTask();
        }
        return 200; // Default poll rate before initialization
    }

    /**
     * Handles the custom anchor selection process using TilePicker.
     * @return Poll delay in milliseconds
     */
    private int handleCustomAnchorSelection() {
        // Ensure we're in a valid state
        if (getWorldPosition() == null) {
            ScriptLogger.warning(this, "World position not yet established, waiting...");
            return 1000;
        }
        
        anchorSelectionInProgress = true;
        
        try {
            // Check if Custom strategy is selected - use multi-tile selection
            if ("Custom".equals(selectedStrategy)) {
                ScriptLogger.info(this, "Opening tile picker for custom multi-tile selection...");
                
                // Call enhanced tile picker for multiple selection
                List<WorldPosition> selectedPositions = EnhancedTilePickerPanel.showMultiple(this);
                
                if (selectedPositions != null && !selectedPositions.isEmpty()) {
                    customTrapPositions = selectedPositions;
                    customAnchorPosition = selectedPositions.get(0); // Use first as anchor for compatibility
                    customAnchorSelected = true;
                    ScriptLogger.info(this, "Custom positions selected: " + selectedPositions.size() + " tiles");
                    ScriptLogger.debug(this, "First position (anchor): " + customAnchorPosition);
                    
                    // Reinitialize tasks with the custom positions
                    reinitializeTasksWithCustomAnchor();
                } else {
                    ScriptLogger.warning(this, "Tile selection cancelled, using NoCardinal fallback");
                    requiresCustomAnchor = false;
                    initializeTasks();
                }
            } else {
                // Standard single-tile selection for other strategies
                ScriptLogger.info(this, "Opening tile picker for custom anchor selection...");
                
                WorldPosition selected = EnhancedTilePickerPanel.show(this);
                
                if (selected != null) {
                    customAnchorPosition = selected;
                    customAnchorSelected = true;
                    ScriptLogger.info(this, "Custom anchor selected at: " + selected);
                    
                    // Reinitialize tasks with the custom anchor
                    reinitializeTasksWithCustomAnchor();
                } else {
                    ScriptLogger.warning(this, "Tile selection cancelled, using dynamic center");
                    requiresCustomAnchor = false; // Fall back to dynamic center
                    // Initialize tasks without a custom anchor
                    initializeTasks();
                }
            }
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.exception(this, "selecting custom anchor", e);
            requiresCustomAnchor = false; // Fall back
        } finally {
            anchorSelectionInProgress = false;
        }
        
        return 200;
    }
    
    /**
     * Reinitializes tasks after custom anchor selection.
     */
    private void reinitializeTasksWithCustomAnchor() {
        ScriptLogger.info(this, "Reinitializing tasks with custom anchor at: " + customAnchorPosition);
        
        // Clear existing tasks
        taskManager.clearTasks();
        
        // Recreate tasks with the new anchor
        initializeTasks();
    }

        // ───────────────────────────────────────────────────────────────────────
        // │ BREAK MANAGEMENT                                                     │
        // ───────────────────────────────────────────────────────────────────────

    @Override
    public boolean canBreak() {
        // Only prevent breaks if we have traps out (in any state)
        TrapStateManager trapManager = getTrapStateManager();
        if (trapManager == null) {
            return true;
        }
        
        boolean hasTraps = !trapManager.isEmpty();
        boolean hasPendingTransitions = trapManager.hasPendingGracePeriods();
        
        // Prevent break if we have traps OR if traps are in transition (grace period)
        boolean canBreakNow = !hasTraps && !hasPendingTransitions;
        
        // Only log state transitions
        if (canBreakNow && isDrainingForBreak) {
            ScriptLogger.info(this, "All traps cleared and no pending transitions - allowing break");
            isDrainingForBreak = false; // Reset flag after successful break
            hasTriggeredExpedite = false; // Reset expedite flag for next break
        } else if (!hasTraps && hasPendingTransitions) {
            ScriptLogger.debug(this, "No visible traps but " + trapManager.getPendingGracePeriodsCount() + 
                             " trap(s) in state transition - preventing break");
        }
        
        return canBreakNow;
    }

    // --- Getter methods for tasks to access script state ---
    
    /**
     * Gets the TrapStateManager from the HuntTask for break/hop logic.
     * @return TrapStateManager instance, or null if tasks not initialized
     */
    private TrapStateManager getTrapStateManager() {
        return huntTask != null ? huntTask.getTrapStateManager() : null;
    }

    public boolean isDrainingForBreak() {
        return isDrainingForBreak;
    }
    
    public boolean isDistanceBasedPrioritization() {
        return distanceBasedPrioritization;
    }

    @Override
    protected void onMetricsPaint(Canvas canvas) {
        try {
            // Only draw debug visuals if we're initialized and have a hunting task
            if (!initialised || huntTask == null) {
                return;
            }

            TrapStateManager trapManager = huntTask.getTrapStateManager();
            if (trapManager == null) {
                return;
            }

            // Draw tracked trap positions in neon orange
            drawTrackedTrapPositions(canvas, trapManager);
            
            // Draw custom anchor position if set
            if (customAnchorPosition != null) {
                drawCustomAnchor(canvas);
            }
            
            // Draw respawn circles with z-offset visualization
            // drawRespawnCirclesWithZOffset(canvas);  // Commented out - debugging tool

        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            // Silently catch any painting errors to avoid disrupting the script
            ScriptLogger.debug(this, "Error in onPaint: " + e.getMessage());
        }
    }

    /**
     * Draw the custom anchor position with a green highlight
     */
    private void drawCustomAnchor(Canvas canvas) {
        if (customAnchorPosition == null) {
            return;
        }
        
        // Get the tile polygon for the custom anchor position
        Polygon tilePoly = getSceneProjector().getTilePoly(customAnchorPosition);
        
        if (tilePoly != null) {
            // Fill with semi-transparent green
            canvas.fillPolygon(tilePoly, 0x00FF00, 0.3); // Green with 30% opacity
            
            // Draw bright green outline
            canvas.drawPolygon(tilePoly, 0x00FF00, 1.0); // Bright green outline
            
            // Draw a center marker
            Rectangle bounds = tilePoly.getBounds();
            if (bounds != null) {
                int centerX = bounds.x + bounds.width / 2;
                int centerY = bounds.y + bounds.height / 2;
                
                // Draw crosshair at center
                canvas.drawLine(centerX - 5, centerY, centerX + 5, centerY, 0x00FF00, 2);
                canvas.drawLine(centerX, centerY - 5, centerX, centerY + 5, 0x00FF00, 2);
                
                // Draw text label
                canvas.drawText("ANCHOR", centerX - 25, centerY - 10, 0x00FF00, 
                    new java.awt.Font("Arial", java.awt.Font.BOLD, 12));
            }
        }
    }
    
    /**
     * Draw neon orange highlights for all tracked trap positions
     */
    private void drawTrackedTrapPositions(Canvas canvas, TrapStateManager trapManager) {
        // Get all tracked trap positions from the TrapStateManager
        List<WorldPosition> trapPositions = trapManager.getLaidTrapPositions();
        
        if (trapPositions == null || trapPositions.isEmpty()) {
            return;
        }

        // Draw neon orange highlight for each tracked trap position
        for (WorldPosition position : trapPositions) {
            if (position == null) continue;

            // Get the tile polygon for this world position using the scene projector
            Polygon tilePoly = getSceneProjector().getTilePoly(position);
            
            if (tilePoly != null) {
                // Fill the polygon with neon orange (semi-transparent)
                canvas.fillPolygon(tilePoly, 0xFF6600, 0.4); // Neon orange with 40% opacity
                
                // Draw the polygon outline with bright neon orange for better visibility
                canvas.drawPolygon(tilePoly, 0xFF3300, 1.0); // Bright orange-red outline
            }
        }
    }


    /**
     * DEBUG METHOD: Draws black squares at detected trap positions with z-offset values
     * Purpose: Visualizes where the system thinks traps are located based on respawn circle detection
     * and shows the z-offset being used for screen-to-world coordinate conversion.
     * Uncomment the call in onPaint() to enable this debugging visualization.
     */
    /*
    private void drawRespawnCirclesWithZOffset(Canvas canvas) {
        // Get all respawn circles detected by the pixel analyzer
        List<PixelAnalyzer.RespawnCircle> respawnCircles = getPixelAnalyzer().findRespawnCircleTypes();
        
        if (respawnCircles == null || respawnCircles.isEmpty()) {
            return;
        }

        for (PixelAnalyzer.RespawnCircle circle : respawnCircles) {
            Rectangle circleBounds = circle.getBounds();
            PixelAnalyzer.RespawnCircle.Type circleType = circle.getType();
            
            // Get the appropriate z-offset for this circle type
            int zOffset = getTrapStateManager().getZOffsetForCircleType(circleType);
            
            // Convert respawn circle to world positions using the utils method
            List<WorldPosition> positions = getUtils().getWorldPositionForRespawnCircles(
                List.of(circleBounds), zOffset);
            
            
            // For each detected world position, draw a 3x3 black square at the tile center + z-offset indicator
            for (WorldPosition position : positions) {
                if (position == null) continue;
                
                // Convert to local position to get coordinates for getTilePoint
                LocalPosition localPos = position.toLocalPosition(this);
                if (localPos == null) continue;
                
                // Get the tile center point with the same z-offset as the respawn circle
                java.awt.Point tileCenter = getSceneProjector().getTilePoint(
                    localPos.getPreciseX(), localPos.getPreciseY(), localPos.getPlane(), null, zOffset);
                if (tileCenter == null) continue;
                
                // Draw a 3x3 black square at the tile center with the correct z-offset
                canvas.fillRect(tileCenter.x - 1, tileCenter.y - 1, 3, 3, 0x000000); // Black square
                
                // Draw the z-offset text near the square
                String zOffsetText = String.valueOf(zOffset);
                canvas.drawText(zOffsetText, tileCenter.x + 5, tileCenter.y - 5, 0xFFFFFF, 
                    new java.awt.Font("Arial", java.awt.Font.BOLD, 12));
            }
        }
    }
    */


    
    /**
     * Creates a TrapPlacementStrategy instance based on the selected strategy name.
     * @param strategyName The name of the strategy selected by the user
     * @return The corresponding strategy instance
     */
    private TrapPlacementStrategy createStrategyFromSelection(String strategyName) {
        if (strategyName == null || customAnchorPosition == null) {
            ScriptLogger.warning(this, "No strategy or anchor selected. Using default.");
            return new NoCardinalStrategy();
        }
        
        return switch (strategyName) {
            case "Auto" -> {
                ScriptLogger.info(this, "Creating Auto pattern strategy for " + maxTraps + " traps");
                yield new AutoPatternStrategy(this, huntTask, customAnchorPosition, maxTraps);
            }
            case "X-Pattern" -> {
                ScriptLogger.info(this, "Creating X-Pattern strategy with customAnchor=" + customAnchorPosition);
                // Always use custom anchor, no dynamic recentering
                yield new XPatternStrategy(this, huntTask, 0, false, customAnchorPosition);
            }
            case "L-Pattern" -> {
                ScriptLogger.info(this, "Creating L-Pattern strategy with customAnchor=" + customAnchorPosition);
                yield new LPatternStrategy(this, customAnchorPosition, maxTraps);
            }
            case "Line" -> {
                // Check for line orientation option
                String orientation = "Horizontal"; // default
                if (strategyOptions != null) {
                    Object orientationObj = strategyOptions.get("lineOrientation");
                    if (orientationObj instanceof String) {
                        orientation = (String) orientationObj;
                    }
                }
                
                LinePatternStrategy.Orientation lineOrientation = 
                    "Vertical".equals(orientation) ? LinePatternStrategy.Orientation.VERTICAL : 
                                                    LinePatternStrategy.Orientation.HORIZONTAL;
                
                ScriptLogger.info(this, "Creating Line pattern strategy with orientation=" + orientation);
                yield new LinePatternStrategy(this, customAnchorPosition, maxTraps, lineOrientation);
            }
            case "Cross" -> {
                ScriptLogger.info(this, "Creating Cross pattern strategy for " + maxTraps + " traps");
                yield new CrossPatternStrategy(this, customAnchorPosition, maxTraps);
            }
            case "Custom" -> {
                if (customTrapPositions == null || customTrapPositions.isEmpty()) {
                    ScriptLogger.warning(this, "No custom positions selected, using NoCardinal fallback");
                    yield new NoCardinalStrategy();
                }
                ScriptLogger.info(this, "Creating Custom tile picker strategy with " + customTrapPositions.size() + " positions");
                yield new CustomTilePickerStrategy(this, customTrapPositions);
            }
            case "No Cardinal" -> new NoCardinalStrategy();
            default -> {
                ScriptLogger.warning(this, "Unknown strategy: " + strategyName + ". Using Auto as fallback.");
                yield new AutoPatternStrategy(this, huntTask, customAnchorPosition, maxTraps);
            }
        };
    }
    
    /**
     * Determines the trap type based on the selected target and available types in config.
     * @return The trap type to use for hunting
     */
    private TrapType determineTrapType() {
        // Map selected targets to trap types (handle both UI dropdown values and specific animal names)
        TrapType requestedType = switch (selectedTarget.toLowerCase()) {
            // UI dropdown values
            case "birds" -> TrapType.BIRD_SNARE;
            case "chinchompas" -> TrapType.CHINCHOMPA;
            // Specific animal names (legacy support)
            case "crimson swift", "copper longtail", "tropical wagtail", "cerulean twitch" -> TrapType.BIRD_SNARE;
            case "grey chinchompa", "red chinchompa", "black chinchompa", "chinchompa" -> TrapType.CHINCHOMPA;
            // Box trap creatures (jerboas and ferrets)
            case "embertailed jerboa", "ferret" -> TrapType.CHINCHOMPA;
            default -> TrapType.BIRD_SNARE; // Default fallback
        };
        
        // Check if the requested type is enabled in the configuration
        if (!huntingConfig.isTypeEnabled(requestedType)) {
            ScriptLogger.warning(this, "Requested trap type " + requestedType + " is not enabled in this variant.");
            // Fall back to first enabled type
            for (TrapType type : huntingConfig.getEnabledTypes()) {
                ScriptLogger.info(this, "Using fallback trap type: " + type);
                return type;
            }
        }
        
        return requestedType;
    }
    
    /**
     * Gets the hunting configuration for this script variant.
     * @return The hunting configuration
     */
    public HuntingConfig getHuntingConfig() {
        return huntingConfig;
    }
    
    /**
     * Initializes the metrics tracking system
     */
    private void initializeMetrics() {
        // Register hunting target as first metric (displays at top)
        registerMetric("Hunting", () -> selectedTarget, MetricType.TEXT);
        
        // Enable Hunter XP tracking with sprite ID 220
        enableXPTracking(SkillType.HUNTER, 220);
        
        // Register custom metrics
        registerMetric("Total Catches", successfulCatches::get, MetricType.NUMBER);
        registerMetric("Total Misses", failedCatches::get, MetricType.NUMBER);
        registerMetric("Caught /h", successfulCatches::get, MetricType.RATE);
        registerMetric("Missed /h", failedCatches::get, MetricType.RATE);
        registerMetric("Success Rate", this::calculateSuccessRate, MetricType.PERCENTAGE);
        registerMetric("Total Checked", totalChecks::get, MetricType.NUMBER);
        
        // Add failsafe metric if enabled
        if (xpFailsafeEnabled) {
            registerMetric("Since XP", 
                          () -> {
                              long ms = getTimeSinceLastXPGain();
                              if (ms < 60000) return (ms/1000) + "s";
                              else return (ms/60000) + "m " + ((ms%60000)/1000) + "s";
                          }, 
                          MetricType.TEXT);
            
            // Configure pause during logout if enabled
            if (xpFailsafePauseDuringLogout) {
                configureXPFailsafeTimerPause(true);
            }
        }
    }
    
    /**
     * Calculates the success rate percentage
     */
    private double calculateSuccessRate() {
        int total = successfulCatches.get() + failedCatches.get();
        if (total == 0) return 0.0;
        return (successfulCatches.get() * 100.0) / total;
    }
    
    /**
     * Called when a trap successfully catches something
     */
    public void onTrapSuccess() {
        successfulCatches.incrementAndGet();
        totalChecks.incrementAndGet();
        // XP tracking is now handled automatically by OCR-based XPMetricProvider
    }
    
    /**
     * Called when a trap fails to catch
     */
    public void onTrapFailed() {
        failedCatches.incrementAndGet();
        totalChecks.incrementAndGet();
    }
    
    /**
     * Called when the game state changes (login/logout/world hop).
     * Clears trap tracking when we're no longer in game since traps are lost on logout.
     */
    @Override
    public void onGameStateChanged(GameState newGameState) {
        // IMPORTANT: Call parent implementation to ensure proper framework registration
        super.onGameStateChanged(newGameState);
        
        // Check if we've transitioned away from being logged in
        if (newGameState != null && newGameState != GameState.LOGGED_IN) {
            // We've logged out or are on login screen - all traps are lost
            if (huntTask != null) {
                TrapStateManager trapManager = huntTask.getTrapStateManager();
                if (trapManager != null && !trapManager.isEmpty()) {
                    ScriptLogger.warning(this, "Detected logout/world hop (GameState: " + newGameState + 
                                       ") - clearing " + trapManager.getTotalCount() + " tracked traps");
                    trapManager.clearAllTraps();
                    
                    // Reset any laying/resetting flags to prevent stuck states
                    trapManager.clearLayingFlag();
                    trapManager.clearResetFlag();
                }
            }
            
            // Reset drain mode flags if we were preparing for break
            if (isDrainingForBreak) {
                isDrainingForBreak = false;
                hasTriggeredExpedite = false;
                ScriptLogger.info(this, "Reset drain mode flags due to logout");
            }
            
            // Pause XP failsafe timer when not logged in
            if (xpFailsafePauseDuringLogout && xpFailsafeEnabled) {
                pauseXPFailsafeTimer();
                ScriptLogger.debug(this, "Paused XP failsafe timer - GameState: " + newGameState);
            }
        } else if (newGameState == GameState.LOGGED_IN) {
            // We've logged back in
            ScriptLogger.info(this, "Logged back in - trap tracking cleared, will rescan for traps");
            
            // Resume XP failsafe timer when logged back in
            if (xpFailsafePauseDuringLogout && xpFailsafeEnabled) {
                resumeXPFailsafeTimer();
                ScriptLogger.debug(this, "Resumed XP failsafe timer - GameState: LOGGED_IN");
            }
        }
    }
    
    /**
     * Called when the script relogs after a disconnect or world hop.
     * This ensures trap tracking is cleared since all traps are lost on logout.
     */
    @Override
    public void onRelog() {
        ScriptLogger.info(this, "=== RELOG DETECTED - Clearing trap tracking ===");
        
        // Clear all trap tracking since traps are lost on logout/world hop
        if (huntTask != null) {
            TrapStateManager trapManager = huntTask.getTrapStateManager();
            if (trapManager != null) {
                int trapCount = trapManager.getTotalCount();
                if (trapCount > 0) {
                    ScriptLogger.warning(this, "Clearing " + trapCount + " tracked traps after relog");
                }
                trapManager.clearAllTraps();
                
                // Also clear laying/resetting flags
                trapManager.clearLayingFlag();
                trapManager.clearResetFlag();
            }
        }
        
        // Reset drain mode if it was active
        if (isDrainingForBreak) {
            isDrainingForBreak = false;
            hasTriggeredExpedite = false;
            ScriptLogger.info(this, "Reset drain mode flags after relog");
        }
        
        ScriptLogger.info(this, "Trap tracking cleared - will rescan for new traps");
    }
    
    /**
     * Creates the metrics panel configuration
     */
    @Override
    protected MetricsPanelConfig createMetricsConfig() {
        MetricsPanelConfig config = MetricsPanelConfig.darkTheme();
        config.setCustomPosition(10, 110);
        config.setMinWidth(180);
        
        // Set pure black background
        config.setBackgroundColor(new java.awt.Color(0, 0, 0, 220)); // Black with slight transparency
        
        // Add logo image - try as resource name that will be in JAR
        config.setLogoImage("jorkhunter_logo.png", 30);  // Reduced size to fit within panel
        config.setLogoOpacity(1.0);
        
        // Disable text overlay since we're using pure black
        config.setUseTextOverlay(false);
        
        return config;
    }

} 
