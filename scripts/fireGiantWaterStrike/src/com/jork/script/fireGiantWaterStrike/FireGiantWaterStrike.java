package com.jork.script.fireGiantWaterStrike;

import com.jork.script.fireGiantWaterStrike.anchor.AnchorManager;
import com.jork.script.fireGiantWaterStrike.combat.CombatConstants;
import com.jork.script.fireGiantWaterStrike.combat.CombatTracker;
import com.jork.script.fireGiantWaterStrike.config.CombatConfig;
import com.jork.script.fireGiantWaterStrike.ui.ScriptOptions;
import com.jork.utils.ScriptLogger;
import com.jork.utils.metrics.AbstractMetricsScript;
import com.jork.utils.metrics.core.MetricType;
import com.jork.utils.metrics.display.MetricsPanelConfig;

import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.script.ScriptDefinition;
import com.osmb.api.script.SkillCategory;
import com.osmb.api.shape.Rectangle;
import com.osmb.api.ui.GameState;
import com.osmb.api.visual.PixelCluster;
import com.osmb.api.ui.chatbox.dialogue.DialogueType;
import com.osmb.api.ui.component.tabs.skill.SkillType;
import com.osmb.api.ui.minimap.MinimapOrbs;
import com.osmb.api.ui.overlay.HealthOverlay;
import com.osmb.api.utils.RandomUtils;
import com.osmb.api.utils.UIResultList;
import com.osmb.api.visual.drawing.Canvas;

import javafx.scene.Scene;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
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
    private static final int FIRE_RUNE_ID = 554;
    private static final int WATER_RUNE_ID = 555;
    private static final int AIR_RUNE_ID = 556;
    private static final int EARTH_RUNE_ID = 557;
    private static final int MIND_RUNE_ID = 558;
    private static final int MAX_ENGAGE_MENU_MISS_RETRIES = 3;

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
    private CombatTracker combatTracker;
    private HealthOverlay healthOverlay;
    private static final int MAX_WALKBACK_FAILURES = 3;
    private int consecutiveWalkbackFailures = 0;

    /** Stored cluster bounds from ACQUIRE_TARGET for use in ENGAGE_TARGET. */
    private Rectangle targetClusterBounds;

    // ───────────────────────────────────────────────────────────────────────────
    // Safety Interrupt Fields
    // ───────────────────────────────────────────────────────────────────────────
    private String stopReason;                    // Set before STOP for reason logging
    private long nextResourceCheckTime = 0;       // Throttle hard-stop inventory checks
    private long nextEatAllowedTime = 0;          // Eat cooldown timer
    private int hitpointsToEat;                   // Current randomized eat threshold (regenerated after each eat)
    private int eatLow;                           // Lower bound for eat threshold range
    private int eatHigh;                          // Upper bound for eat threshold range

    // Combined resource check set for inventory snapshots (required runes + elemental coverage toggles + food)
    private Set<Integer> resourceCheckIds;

    // Global retry tracking (RECV-02)
    private int globalRetryCount = 0;
    private long overlayDisappearGraceMs = 0;

    // First-cast + locked-target re-engage flow
    private boolean awaitingFirstCast = false;
    private long firstCastDeadlineMs = 0;
    private boolean reengageLockedTarget = false;
    private int reengageAttempts = 0;
    private int engageMenuMissRetries = 0;

    // Post-kill UUID lockout to avoid retargeting a dying giant animation.
    private String recentlyKilledUuid = null;
    private long recentlyKilledUuidUntilMs = 0;

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

        combatTracker = new CombatTracker();
        ScriptLogger.info(this, "CombatTracker initialized with NpcTracker");

        healthOverlay = new HealthOverlay(this);
        ScriptLogger.info(this, "HealthOverlay initialized");

        // Initialize safety interrupt thresholds
        resourceCheckIds = buildResourceCheckIds();
        eatLow = config.foodType().getHealAmount() + 3;
        eatHigh = config.foodType().getHealAmount() + 8;
        hitpointsToEat = RandomUtils.uniformRandom(eatLow, eatHigh);
        ScriptLogger.info(this, "Eat threshold initialized: " + hitpointsToEat + " (range " + eatLow + "-" + eatHigh + ")");

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

        ScriptLogger.info(this, "Rune check - Water: " + waterCount
            + (isRuneCoveredByStaff(WATER_RUNE_ID) ? " (staff-covered)" : "")
            + ", Air: " + airCount
            + (isRuneCoveredByStaff(AIR_RUNE_ID) ? " (staff-covered)" : "")
            + ", Mind: " + mindCount);

        if (waterCount == 0 && !isRuneCoveredByStaff(WATER_RUNE_ID)) {
            ScriptLogger.warning(this, "No water runes found in inventory");
        }
        if (airCount == 0 && !isRuneCoveredByStaff(AIR_RUNE_ID)) {
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

    private Set<Integer> buildResourceCheckIds() {
        Set<Integer> ids = new HashSet<>();
        ids.add(AIR_RUNE_ID);
        ids.add(WATER_RUNE_ID);
        ids.add(MIND_RUNE_ID);
        ids.add(config.foodType().getItemId());

        // Keep all elemental rune IDs searchable in one snapshot for diagnostics/future reuse.
        ids.add(FIRE_RUNE_ID);
        ids.add(EARTH_RUNE_ID);
        return ids;
    }

    private boolean isRuneCoveredByStaff(int runeId) {
        if (config == null) return false;
        return switch (runeId) {
            case AIR_RUNE_ID -> config.staffCoversAirRune();
            case WATER_RUNE_ID -> config.staffCoversWaterRune();
            case EARTH_RUNE_ID -> config.staffCoversEarthRune();
            case FIRE_RUNE_ID -> config.staffCoversFireRune();
            default -> false;
        };
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

        // STOP must execute even if interrupts keep triggering.
        if (currentState == ScriptState.STOP) {
            handleStop();
            return 0;
        }

        // === Global Interrupts (priority order) ===
        // Each returns true if it handled the interrupt -- skip state dispatch that tick
        if (handleHardStopConditions()) return 0;      // P2: depleted runes/food -> STOP
        if (handleEatFood()) return 0;                  // P3: low HP -> eat food
        if (handleDisplacementInterrupt()) return 0;    // P4: off-anchor -> ENSURE_ANCHOR
        if (handleDialogueInterrupt()) return 0;        // P5: level-up/dialogue -> dismiss
        if (handleXpFailsafe()) return 0;              // XP watchdog: no XP gain -> STOP

        // Update NPC tracker once per poll cycle (before any state handler reads it)
        updateNpcTracker();

        // State dispatch
        switch (currentState) {
            case INIT -> handleInit();
            case ENSURE_ANCHOR -> handleEnsureAnchor();
            case ACQUIRE_TARGET -> handleAcquireTarget();
            case ENGAGE_TARGET -> handleEngageTarget();
            case MONITOR_COMBAT -> handleMonitorCombat();
            case REENGAGE_LOCKED_TARGET -> handleReengageLockedTarget();
            case POST_KILL -> handlePostKill();
            case LOOT_TELEGRAB -> handleLootTelegrab();
            case LOOT_MANUAL -> handleLootManual();
            case RECOVERY -> handleRecovery();
            case STOP -> handleStop();
        }

        return 0;
    }

    // ───────────────────────────────────────────────────────────────────────────
    // NPC Tracker Update
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Queries minimap NPC positions and feeds them to the NPC tracker.
     * Called once per poll cycle before state dispatch.
     * Gracefully handles null minimap (loading screens, transitions).
     */
    private void updateNpcTracker() {
        if (combatTracker == null) return;

        var widgetManager = getWidgetManager();
        if (widgetManager == null) return;
        var minimap = widgetManager.getMinimap();
        if (minimap == null) return;

        UIResultList<WorldPosition> npcPositions = minimap.getNPCPositions();
        if (npcPositions == null || npcPositions.isNotFound()) {
            // Minimap unavailable -- feed empty list so tracker can age out stale entries
            combatTracker.getNpcTracker().update(Collections.emptyList());
            return;
        }

        combatTracker.getNpcTracker().update(npcPositions.asList());
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Screen Projection Helpers
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Projects the FIRE_GIANT_AREA to screen-space for constrained pixel search.
     * Gets tile polys for all 4 corners and unions their bounds into a single Rectangle.
     *
     * @return screen-space Rectangle covering the combat area, or null if not enough corners are visible
     */
    private Rectangle getFireGiantScreenBounds() {
        int areaX = CombatConstants.FIRE_GIANT_AREA.getX();
        int areaY = CombatConstants.FIRE_GIANT_AREA.getY();
        int areaW = CombatConstants.FIRE_GIANT_AREA.getWidth();
        int areaH = CombatConstants.FIRE_GIANT_AREA.getHeight();
        int plane = CombatConstants.FIRE_GIANT_AREA.getPlane();

        return projectAreaBounds(areaX, areaY, areaW, areaH, plane, 0, 80);
    }

    /**
     * Projects a 3x3 tile area around the player for local projectile detection.
     *
     * @return screen-space Rectangle for the near-player spell scan, or null if player/tiles are not visible
     */
    private Rectangle getPlayerLocalSpellScanBounds() {
        WorldPosition myPos = getWorldPosition();
        if (myPos == null) return null;

        int radius = CombatConstants.FIRST_CAST_SCAN_RADIUS_TILES;
        int minX = myPos.getX() - radius;
        int minY = myPos.getY() - radius;
        int size = (radius * 2) + 1;

        return projectAreaBounds(
            minX,
            minY,
            size,
            size,
            myPos.getPlane(),
            CombatConstants.FIRST_CAST_SCAN_HORIZONTAL_PAD,
            CombatConstants.FIRST_CAST_SCAN_VERTICAL_PAD
        );
    }

    private Rectangle projectAreaBounds(int x, int y, int width, int height, int plane, int horizontalPad, int verticalPad) {
        WorldPosition[] corners = {
            new WorldPosition(x, y, plane),
            new WorldPosition(x + width - 1, y, plane),
            new WorldPosition(x, y + height - 1, plane),
            new WorldPosition(x + width - 1, y + height - 1, plane)
        };

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        int projected = 0;

        for (WorldPosition corner : corners) {
            com.osmb.api.shape.Polygon poly = getSceneProjector().getTilePoly(corner);
            if (poly == null) continue;
            Rectangle bounds = poly.getBounds();
            if (bounds == null) continue;

            minX = Math.min(minX, bounds.x);
            minY = Math.min(minY, bounds.y);
            maxX = Math.max(maxX, bounds.x + bounds.width);
            maxY = Math.max(maxY, bounds.y + bounds.height);
            projected++;
        }

        if (projected < 2) return null;

        minX -= horizontalPad;
        maxX += horizontalPad;
        minY -= verticalPad;

        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    /**
     * Shrinks a rectangle around its center to reduce edge-tap bias.
     */
    private Rectangle shrinkBounds(Rectangle bounds, double scale) {
        if (bounds == null) return null;
        if (scale <= 0.0 || scale > 1.0) return bounds;

        int originalWidth = Math.max(bounds.width, 2);
        int originalHeight = Math.max(bounds.height, 2);
        int newWidth = Math.max((int) Math.round(originalWidth * scale), 8);
        int newHeight = Math.max((int) Math.round(originalHeight * scale), 8);

        int centerX = bounds.x + (originalWidth / 2);
        int centerY = bounds.y + (originalHeight / 2);
        int newX = centerX - (newWidth / 2);
        int newY = centerY - (newHeight / 2);

        return new Rectangle(newX, newY, newWidth, newHeight);
    }

    private String mapClusterToUuid(Rectangle clusterBounds, Map<String, WorldPosition> activeNpcs) {
        if (clusterBounds == null || activeNpcs == null || activeNpcs.isEmpty()) {
            return null;
        }

        String bestUuid = null;
        int bestOverlap = -1;
        double bestCenterDist = Double.MAX_VALUE;
        int blockedByRecentKillLock = 0;
        int projectedCandidates = 0;

        for (Map.Entry<String, WorldPosition> entry : activeNpcs.entrySet()) {
            String uuid = entry.getKey();
            if (isRecentKillUuidLocked(uuid)) {
                blockedByRecentKillLock++;
                continue;
            }

            Rectangle npcBounds = getProjectedTrackedNpcBounds(entry.getValue());
            if (npcBounds == null) {
                continue;
            }
            projectedCandidates++;

            int overlapArea = getIntersectionArea(clusterBounds, npcBounds);
            double centerDist = getCenterDistance(clusterBounds, npcBounds);

            boolean betterCandidate = overlapArea > bestOverlap
                || (overlapArea == bestOverlap && centerDist < bestCenterDist);
            if (betterCandidate) {
                bestUuid = uuid;
                bestOverlap = overlapArea;
                bestCenterDist = centerDist;
            }
        }

        if (bestUuid == null) {
            if (blockedByRecentKillLock > 0) {
                ScriptLogger.debug(this, "ACQUIRE_TARGET: Waiting for post-kill UUID lockout to expire");
            } else if (projectedCandidates == 0) {
                ScriptLogger.debug(this, "ACQUIRE_TARGET: No projected tracked NPC bounds available for UUID mapping");
            }
            return null;
        }

        double mappingDistanceThreshold = Math.max(45.0, Math.max(clusterBounds.width, clusterBounds.height) * 2.2);
        boolean confidencePass = bestOverlap > 0 || bestCenterDist <= mappingDistanceThreshold;
        if (!confidencePass) {
            ScriptLogger.debug(this, "ACQUIRE_TARGET: UUID mapping low confidence (best overlap=" + bestOverlap
                + ", centerDist=" + (int) bestCenterDist + "px) - waiting for better match");
            return null;
        }

        ScriptLogger.debug(this, "ACQUIRE_TARGET: Mapped cluster to UUID=" + bestUuid.substring(0, 8)
            + " (overlap=" + bestOverlap + ", centerDist=" + (int) bestCenterDist + "px)");
        return bestUuid;
    }

    private Rectangle getProjectedTrackedNpcBounds(WorldPosition position) {
        if (position == null) return null;

        com.osmb.api.shape.Polygon cube = getSceneProjector().getTileCube(position, CombatConstants.FIRE_GIANT_CUBE_HEIGHT);
        if (cube != null && cube.getBounds() != null) {
            return cube.getBounds();
        }

        com.osmb.api.shape.Polygon tile = getSceneProjector().getTilePoly(position);
        if (tile != null) {
            return tile.getBounds();
        }
        return null;
    }

    private int getIntersectionArea(Rectangle a, Rectangle b) {
        if (a == null || b == null) return 0;
        int left = Math.max(a.x, b.x);
        int top = Math.max(a.y, b.y);
        int right = Math.min(a.x + a.width, b.x + b.width);
        int bottom = Math.min(a.y + a.height, b.y + b.height);
        int w = right - left;
        int h = bottom - top;
        if (w <= 0 || h <= 0) return 0;
        return w * h;
    }

    private double getCenterDistance(Rectangle a, Rectangle b) {
        if (a == null || b == null) return Double.MAX_VALUE;
        double ax = a.x + (a.width / 2.0);
        double ay = a.y + (a.height / 2.0);
        double bx = b.x + (b.width / 2.0);
        double by = b.y + (b.height / 2.0);
        return Math.hypot(ax - bx, ay - by);
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
            ScriptState nextState = reengageLockedTarget
                ? ScriptState.REENGAGE_LOCKED_TARGET
                : ScriptState.ACQUIRE_TARGET;
            ScriptLogger.debug(this, "ENSURE_ANCHOR: At anchor, transitioning to " + nextState);
            setState(nextState);
            return;
        }

        ScriptLogger.info(this, "ENSURE_ANCHOR: Displaced from anchor, walking back");
        boolean success = anchorManager.walkBack();

        if (success && !anchorManager.isDisplaced()) {
            ScriptLogger.info(this, "ENSURE_ANCHOR: Walk-back successful");
            consecutiveWalkbackFailures = 0;
            ScriptState nextState = reengageLockedTarget
                ? ScriptState.REENGAGE_LOCKED_TARGET
                : ScriptState.ACQUIRE_TARGET;
            setState(nextState);
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
        // Project combat area to screen-space for constrained pixel search
        Rectangle searchBounds = getFireGiantScreenBounds();
        if (searchBounds == null) {
            ScriptLogger.debug(this, "ACQUIRE_TARGET: Combat area not visible on screen");
            return;
        }

        // Cluster search constrained to the projected combat area (excludes off-zone fire giants)
        PixelCluster.ClusterSearchResult clusterResult =
            getPixelAnalyzer().findClusters(searchBounds, CombatConstants.TAGGED_NPC_CLUSTER);

        if (clusterResult == null || clusterResult.getClusters().isEmpty()) {
            ScriptLogger.debug(this, "ACQUIRE_TARGET: No tagged NPC clusters found in combat area");
            return;
        }

        // Select the largest cluster (most visible tagged NPC)
        PixelCluster bestCluster = clusterResult.getClusters().stream()
            .max((a, b) -> Integer.compare(a.getPoints().size(), b.getPoints().size()))
            .orElse(null);

        if (bestCluster == null || bestCluster.getBounds() == null) {
            ScriptLogger.debug(this, "ACQUIRE_TARGET: Cluster bounds unavailable");
            return;
        }

        targetClusterBounds = shrinkBounds(bestCluster.getBounds(), 0.70);
        ScriptLogger.info(this, "ACQUIRE_TARGET: Tagged NPC cluster found (size="
            + bestCluster.getPoints().size() + ", bounds=" + targetClusterBounds + ")");

        Map<String, WorldPosition> activeNpcs = combatTracker.getNpcTracker().getActiveNpcs();
        if (activeNpcs.isEmpty()) {
            ScriptLogger.debug(this, "ACQUIRE_TARGET: No active tracked NPCs available for UUID mapping");
            return;
        }

        String mappedUuid = mapClusterToUuid(targetClusterBounds, activeNpcs);
        if (mappedUuid == null) {
            return;
        }

        combatTracker.setTarget(mappedUuid);
        engageMenuMissRetries = 0;
        ScriptLogger.debug(this, "ACQUIRE_TARGET: Associated UUID="
            + mappedUuid.substring(0, 8) + " (cluster-reconciled)");
        setState(ScriptState.ENGAGE_TARGET);
    }

    private void handleEngageTarget() {
        String targetUuid = combatTracker.getTargetUuid();
        if (targetUuid == null) {
            ScriptLogger.warning(this, "ENGAGE_TARGET: Missing mapped UUID - returning to ACQUIRE");
            targetClusterBounds = null;
            setState(ScriptState.ACQUIRE_TARGET);
            return;
        }

        com.osmb.api.shape.Polygon uuidTapArea = null;
        WorldPosition targetPos = combatTracker.getTargetPosition();
        if (targetPos == null) {
            targetPos = combatTracker.getTargetLastKnownPosition();
        }
        if (targetPos != null) {
            uuidTapArea = getSceneProjector().getTileCube(targetPos, CombatConstants.FIRE_GIANT_CUBE_HEIGHT);
            if (uuidTapArea != null) {
                com.osmb.api.shape.Polygon resized = uuidTapArea.getResized(0.70);
                if (resized != null) {
                    uuidTapArea = resized;
                }
            }
        }

        if (uuidTapArea == null && targetClusterBounds == null) {
            ScriptLogger.warning(this, "ENGAGE_TARGET: No tap area available for UUID=" + targetUuid.substring(0, 8));
            setState(ScriptState.ACQUIRE_TARGET);
            return;
        }

        // Attack via MenuHook with action + target name validation (same validation as before)
        boolean[] attackMenuFound = {false};
        boolean tapped;
        if (uuidTapArea != null) {
            tapped = getFinger().tapGameScreen(uuidTapArea, (menuEntries) -> {
                for (var entry : menuEntries) {
                    String action = entry.getAction();
                    String rawText = entry.getRawText();
                    if (action == null || rawText == null) continue;

                    if (action.toLowerCase().contains("attack")
                            && rawText.toLowerCase().contains("fire giant")) {
                        attackMenuFound[0] = true;
                        return entry;
                    }
                }
                return null; // No valid attack option
            });
        } else {
            tapped = getFinger().tapGameScreen(targetClusterBounds, (menuEntries) -> {
                for (var entry : menuEntries) {
                    String action = entry.getAction();
                    String rawText = entry.getRawText();
                    if (action == null || rawText == null) continue;

                    if (action.toLowerCase().contains("attack")
                            && rawText.toLowerCase().contains("fire giant")) {
                        attackMenuFound[0] = true;
                        return entry;
                    }
                }
                return null; // No valid attack option
            });
        }

        if (tapped && attackMenuFound[0]) {
            ScriptLogger.info(this, "ENGAGE_TARGET: Attack tap succeeded, waiting for combat confirmation");
            engageMenuMissRetries = 0;
            targetClusterBounds = null; // Clear stored bounds after use

            // Wait for HealthOverlay to appear (confirms we actually entered combat)
            int combatConfirmTimeout = RandomUtils.uniformRandom(
                CombatConstants.HEALTH_OVERLAY_VISIBLE_TIMEOUT_MIN_MS,
                CombatConstants.HEALTH_OVERLAY_VISIBLE_TIMEOUT_MAX_MS
            );
            boolean combatConfirmed = pollFramesHuman(
                () -> healthOverlay != null && healthOverlay.isVisible(),
                combatConfirmTimeout
            );

            if (combatConfirmed) {
                ScriptLogger.info(this, "ENGAGE_TARGET: Combat confirmed via HealthOverlay");
                combatTracker.markEngaged();
                startFirstCastGate();
                setState(ScriptState.MONITOR_COMBAT);
            } else {
                // HealthOverlay didn't appear -- tap may have missed, or target died instantly
                if (combatTracker.isTargetActive()) {
                    ScriptLogger.warning(this, "ENGAGE_TARGET: HealthOverlay not visible but target UUID active - proceeding to MONITOR");
                    combatTracker.markEngaged();
                    startFirstCastGate();
                    setState(ScriptState.MONITOR_COMBAT);
                } else {
                    ScriptLogger.warning(this, "ENGAGE_TARGET: No combat confirmation - returning to ACQUIRE");
                    combatTracker.reset();
                    setState(ScriptState.ACQUIRE_TARGET);
                }
            }
        } else {
            engageMenuMissRetries++;
            if (engageMenuMissRetries < MAX_ENGAGE_MENU_MISS_RETRIES) {
                ScriptLogger.debug(this, "ENGAGE_TARGET: Attack failed for UUID="
                    + targetUuid.substring(0, 8) + " (retry " + engageMenuMissRetries + "/"
                    + (MAX_ENGAGE_MENU_MISS_RETRIES - 1) + ")");
                targetClusterBounds = null;
                pollFramesUntil(() -> false, RandomUtils.uniformRandom(120, 260));
                setState(ScriptState.ACQUIRE_TARGET);
                return;
            }

            ScriptLogger.debug(this, "ENGAGE_TARGET: Attack retries exhausted for UUID="
                + targetUuid.substring(0, 8) + " - resetting target and reacquiring");
            engageMenuMissRetries = 0;
            targetClusterBounds = null;
            combatTracker.reset();
            setState(ScriptState.ACQUIRE_TARGET);
        }
    }

    private void handleReengageLockedTarget() {
        if (!reengageLockedTarget) {
            setState(ScriptState.ACQUIRE_TARGET);
            return;
        }
        if (combatTracker == null || combatTracker.getTargetUuid() == null) {
            ScriptLogger.warning(this, "REENGAGE_LOCKED_TARGET: No locked UUID - fallback acquire");
            clearReengageLock();
            setState(ScriptState.ACQUIRE_TARGET);
            return;
        }

        long staleMs = combatTracker.timeSinceTargetSeen();
        if (staleMs > CombatConstants.LOCKED_TARGET_STALE_TIMEOUT_MS) {
            ScriptLogger.warning(this, "REENGAGE_LOCKED_TARGET: Locked UUID stale (" + staleMs + "ms) - fallback acquire");
            clearReengageLock();
            combatTracker.reset();
            setState(ScriptState.ACQUIRE_TARGET);
            return;
        }

        WorldPosition lockedPos = combatTracker.getTargetPosition();
        if (lockedPos == null) {
            reengageAttempts++;
            ScriptLogger.debug(this, "REENGAGE_LOCKED_TARGET: UUID not active this tick, retry "
                + reengageAttempts + "/" + CombatConstants.REENGAGE_MAX_ATTEMPTS);
            if (reengageAttempts >= CombatConstants.REENGAGE_MAX_ATTEMPTS) {
                clearReengageLock();
                combatTracker.reset();
                setState(ScriptState.ACQUIRE_TARGET);
                return;
            }
            pollFramesUntil(() -> false, RandomUtils.uniformRandom(
                CombatConstants.REENGAGE_RETRY_MIN_MS,
                CombatConstants.REENGAGE_RETRY_MAX_MS
            ));
            return;
        }

        if (!isLikelyFireGiantOnLockedTile(lockedPos)) {
            reengageAttempts++;
            ScriptLogger.debug(this, "REENGAGE_LOCKED_TARGET: Locked tile verification failed, retry "
                + reengageAttempts + "/" + CombatConstants.REENGAGE_MAX_ATTEMPTS);
            if (reengageAttempts >= CombatConstants.REENGAGE_MAX_ATTEMPTS) {
                clearReengageLock();
                combatTracker.reset();
                setState(ScriptState.ACQUIRE_TARGET);
            }
            return;
        }

        com.osmb.api.shape.Polygon tapArea = getSceneProjector().getTileCube(lockedPos, CombatConstants.FIRE_GIANT_CUBE_HEIGHT);
        if (tapArea == null) {
            ScriptLogger.debug(this, "REENGAGE_LOCKED_TARGET: Tile cube not visible");
            return;
        }
        com.osmb.api.shape.Polygon resized = tapArea.getResized(CombatConstants.CUBE_RESIZE_FACTOR);
        if (resized != null) {
            tapArea = resized;
        }

        boolean[] attackMenuFound = {false};
        boolean tapped = getFinger().tapGameScreen(tapArea, (menuEntries) -> {
            for (var entry : menuEntries) {
                String action = entry.getAction();
                String rawText = entry.getRawText();
                if (action == null || rawText == null) continue;
                if (action.toLowerCase().contains("attack") && rawText.toLowerCase().contains("fire giant")) {
                    attackMenuFound[0] = true;
                    return entry;
                }
            }
            return null;
        });

        if (tapped && attackMenuFound[0]) {
            ScriptLogger.info(this, "REENGAGE_LOCKED_TARGET: Re-attacked locked UUID "
                + combatTracker.getTargetUuid().substring(0, 8));
            clearReengageLock();
            combatTracker.markEngaged();
            startFirstCastGate();
            setState(ScriptState.MONITOR_COMBAT);
            return;
        }

        reengageAttempts++;
        if (reengageAttempts >= CombatConstants.REENGAGE_MAX_ATTEMPTS) {
            ScriptLogger.warning(this, "REENGAGE_LOCKED_TARGET: Attack retries exhausted - fallback acquire");
            clearReengageLock();
            combatTracker.reset();
            setState(ScriptState.ACQUIRE_TARGET);
            return;
        }

        pollFramesUntil(() -> false, RandomUtils.uniformRandom(
            CombatConstants.REENGAGE_RETRY_MIN_MS,
            CombatConstants.REENGAGE_RETRY_MAX_MS
        ));
    }

    private boolean isLikelyFireGiantOnLockedTile(WorldPosition position) {
        com.osmb.api.shape.Polygon tileCube = getSceneProjector().getTileCube(position, CombatConstants.FIRE_GIANT_CUBE_HEIGHT);
        if (tileCube == null) return false;

        com.osmb.api.shape.Polygon probeArea = tileCube.getResized(CombatConstants.CUBE_RESIZE_FACTOR);
        if (probeArea == null) {
            probeArea = tileCube;
        }

        PixelCluster.ClusterSearchResult result = getPixelAnalyzer().findClusters(
            probeArea,
            CombatConstants.TAGGED_NPC_TILE_CLUSTER
        );
        return result != null && result.getClusters() != null && !result.getClusters().isEmpty();
    }

    private void startFirstCastGate() {
        awaitingFirstCast = true;
        firstCastDeadlineMs = System.currentTimeMillis() + RandomUtils.uniformRandom(
            CombatConstants.FIRST_CAST_CONFIRM_MIN_MS,
            CombatConstants.FIRST_CAST_CONFIRM_MAX_MS
        );
        ScriptLogger.debug(this, "First-cast gate armed for UUID="
            + (combatTracker.getTargetUuid() != null ? combatTracker.getTargetUuid().substring(0, 8) : "null")
            + " deadline in " + (firstCastDeadlineMs - System.currentTimeMillis()) + "ms");
    }

    private void clearFirstCastGate() {
        awaitingFirstCast = false;
        firstCastDeadlineMs = 0;
    }

    private void clearReengageLock() {
        reengageLockedTarget = false;
        reengageAttempts = 0;
    }

    private int getPostKillUuidLockoutMs() {
        return RandomUtils.uniformRandom(3000, 4200);
    }

    private void setRecentKillUuidLockout(String uuid) {
        if (uuid == null) return;
        recentlyKilledUuid = uuid;
        recentlyKilledUuidUntilMs = System.currentTimeMillis() + getPostKillUuidLockoutMs();
    }

    private void clearRecentKillUuidLockout() {
        recentlyKilledUuid = null;
        recentlyKilledUuidUntilMs = 0;
    }

    private boolean isRecentKillUuidLocked(String uuid) {
        if (uuid == null || recentlyKilledUuid == null) return false;
        if (!recentlyKilledUuid.equals(uuid)) return false;
        if (System.currentTimeMillis() < recentlyKilledUuidUntilMs) return true;
        clearRecentKillUuidLockout();
        return false;
    }

    private boolean handleFirstCastGate() {
        if (!awaitingFirstCast) return false;

        boolean castDetected = detectFirstCastProjectileNearPlayer();
        boolean timedOut = System.currentTimeMillis() >= firstCastDeadlineMs;

        if (!castDetected && !timedOut) {
            return true;
        }

        if (castDetected) {
            ScriptLogger.info(this, "First-cast projectile confirmed");
        } else {
            ScriptLogger.warning(this, "First-cast confirmation timed out - proceeding with fallback");
        }

        clearFirstCastGate();

        if (anchorManager != null && anchorManager.isDisplaced()) {
            reengageLockedTarget = true;
            reengageAttempts = 0;
            ScriptLogger.info(this, "First cast resolved while off-anchor - returning to anchor for locked re-engage");
            setState(ScriptState.ENSURE_ANCHOR);
            return true;
        }

        return false;
    }

    private boolean detectFirstCastProjectileNearPlayer() {
        Rectangle scanBounds = getPlayerLocalSpellScanBounds();
        if (scanBounds == null) return false;

        PixelCluster.ClusterSearchResult result = getPixelAnalyzer().findClusters(
            scanBounds,
            CombatConstants.WATER_STRIKE_PROJECTILE_CLUSTER
        );
        return result != null && result.getClusters() != null && !result.getClusters().isEmpty();
    }

    /**
     * Human-like delay after kill confirmation before reacquiring targets.
     * Centered near 2500ms with occasional shorter/longer reactions.
     */
    private int getPostKillReactionDelayMs() {
        return RandomUtils.gaussianRandom(1000, 6000, 2500, 950);
    }

    private void handleMonitorCombat() {
        if (handleFirstCastGate()) {
            return;
        }

        // === Layer 1: HealthOverlay signals (highest confidence) ===
        Integer currentHp = getHealthOverlayHitpoints();
        boolean overlayVisible = healthOverlay != null && healthOverlay.isVisible();

        // Feed HP to combat tracker for stale detection.
        if (overlayVisible) {
            combatTracker.updateHp(currentHp);
            combatTracker.markOverlayVisible();
            overlayDisappearGraceMs = 0;
        } else if (combatTracker.getLastKnownHp() != null && combatTracker.isEngaged()) {
            combatTracker.markOverlayMissing();
            if (overlayDisappearGraceMs == 0) {
                overlayDisappearGraceMs = RandomUtils.uniformRandom(
                    CombatConstants.OVERLAY_DISAPPEAR_GRACE_MIN_MS,
                    CombatConstants.OVERLAY_DISAPPEAR_GRACE_MAX_MS
                );
            }
        }

        // === Layer 2: UUID tracker signals (second signal for ambiguous cases) ===
        boolean targetActive = combatTracker.isTargetActive();

        // Player animation state with sustained idle tracking
        boolean playerAnimating = getPixelAnalyzer().isPlayerAnimating(
            CombatConstants.PLAYER_ANIMATING_THRESHOLD);

        if (playerAnimating) {
            combatTracker.clearIdleStart();
        } else {
            combatTracker.markIdleStart();
        }

        long sustainedIdleMs = combatTracker.getSustainedIdleMs();
        boolean playerSustainedIdle = sustainedIdleMs >= CombatConstants.SUSTAINED_IDLE_MS;
        long timeSinceSeen = combatTracker.timeSinceTargetSeen();
        boolean overlayShowsAlive = overlayVisible && currentHp != null && currentHp > 0;

        // HP = 0 -> definitive kill.
        if (combatTracker.isHpDead()) {
            ScriptLogger.info(this, "MONITOR_COMBAT: Kill detected (HP reached 0)");
            handleKillDetected();
            return;
        }

        // Overlay disappearance is only meaningful with a second signal.
        if (!overlayVisible && combatTracker.getLastKnownHp() != null && combatTracker.isEngaged()) {
            long missingMs = combatTracker.getOverlayMissingMs();
            boolean secondSignal = !targetActive || playerSustainedIdle;
            if (missingMs >= overlayDisappearGraceMs && secondSignal) {
                ScriptLogger.info(this, "MONITOR_COMBAT: Kill detected (overlay missing "
                    + missingMs + "ms + second signal, last HP=" + combatTracker.getLastKnownHp() + ")");
                handleKillDetected();
                return;
            }
        }

        // HP hasn't changed for too long -> combat stall.
        long hpStaleMs = combatTracker.getHpStaleMs();
        if (hpStaleMs > CombatConstants.HP_STALE_TIMEOUT_MS) {
            ScriptLogger.warning(this, "MONITOR_COMBAT: Combat stall (HP unchanged for " + hpStaleMs + "ms)");
            handleTargetLost();
            return;
        }

        // If overlay still shows positive HP, do not allow UUID-loss fallback kill/lost paths.
        if (overlayShowsAlive) {
            ScriptLogger.debug(this, "MONITOR_COMBAT: Overlay shows target alive (HP=" + currentHp
                + "), ignoring UUID-loss fallback signals this tick");
            return;
        }

        // UUID gone + player idle -> HIGH confidence kill (fallback).
        if (!targetActive && playerSustainedIdle) {
            ScriptLogger.info(this, "MONITOR_COMBAT: Kill detected [fallback] (UUID gone + player idle "
                + sustainedIdleMs + "ms)");
            handleKillDetected();
        } else if (!targetActive && timeSinceSeen > CombatConstants.KILL_CONFIRM_TIMEOUT_MS) {
            // UUID gone for extended time -> MEDIUM confidence kill (fallback)
            ScriptLogger.info(this, "MONITOR_COMBAT: Kill detected [fallback] (UUID gone for "
                + timeSinceSeen + "ms timeout)");
            handleKillDetected();
        } else if (playerSustainedIdle && timeSinceSeen > CombatConstants.MAX_COMBAT_IDLE_MS) {
            // Player stopped fighting for too long -> target lost
            ScriptLogger.warning(this, "MONITOR_COMBAT: Target lost (idle "
                + sustainedIdleMs + "ms, not seen " + timeSinceSeen + "ms)");
            handleTargetLost();
        } else if (targetActive || overlayVisible) {
            // Normal combat tick -- log state
            WorldPosition pos = combatTracker.getTargetPosition();
            String hpStr = currentHp != null ? "HP=" + currentHp : "HP=?";
            ScriptLogger.debug(this, "MONITOR_COMBAT: Tracking target UUID="
                + (combatTracker.getTargetUuid() != null
                    ? combatTracker.getTargetUuid().substring(0, 8) : "null")
                + " at " + pos + " " + hpStr);
        }
        // else: target not active but not timed out yet -- keep waiting
    }

    private void handleKillDetected() {
        setRecentKillUuidLockout(combatTracker.getTargetUuid());
        engageMenuMissRetries = 0;
        clearFirstCastGate();
        clearReengageLock();
        combatTracker.captureDeathPosition();
        killCount.incrementAndGet();
        ScriptLogger.info(this, "Kill #" + killCount.get() + " at "
            + combatTracker.getLastDeathCenterPos());
        pollFramesUntil(() -> false, getPostKillReactionDelayMs());
        combatTracker.reset();  // Clear target state but keep death position and NpcTracker
        setState(ScriptState.POST_KILL);
    }

    private void handleTargetLost() {
        ScriptLogger.warning(this, "Target lost - returning to acquire");
        engageMenuMissRetries = 0;
        clearFirstCastGate();
        clearReengageLock();
        targetClusterBounds = null;
        combatTracker.reset();
        setState(ScriptState.ACQUIRE_TARGET);
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

    /**
     * Centralized recovery handler with exponential backoff and global retry budget.
     *
     * On each entry:
     * 1. Increments the global retry counter
     * 2. Checks if budget is exceeded -> STOP
     * 3. Resets combat intent (clears target, engagement, idle state via combatTracker.reset())
     * 4. Applies randomized exponential backoff delay
     * 5. Transitions to ENSURE_ANCHOR for position reconciliation
     *
     * The global retry counter is NOT reset between kills. It tracks cumulative failures
     * across the entire session. This prevents infinite retry loops from compound failures.
     */
    private void handleRecovery() {
        globalRetryCount++;

        if (globalRetryCount >= CombatConstants.MAX_GLOBAL_RETRIES) {
            ScriptLogger.error(this, "RECOVERY: Global retry budget exceeded ("
                + globalRetryCount + "/" + CombatConstants.MAX_GLOBAL_RETRIES + ") - stopping");
            stopReason = "Global retry budget exceeded (" + globalRetryCount + " retries)";
            setState(ScriptState.STOP);
            return;
        }

        // RECV-03: Reset combat intent
        if (combatTracker != null) {
            combatTracker.reset();
        }
        engageMenuMissRetries = 0;
        clearFirstCastGate();
        clearReengageLock();
        targetClusterBounds = null;

        // Apply randomized exponential backoff
        long backoffMs = RandomUtils.exponentialRandom(
            CombatConstants.RECOVERY_BACKOFF_BASE_MS,
            CombatConstants.RECOVERY_BACKOFF_DECAY_MS,
            CombatConstants.RECOVERY_BACKOFF_MAX_MS
        );
        ScriptLogger.info(this, "RECOVERY: Retry " + globalRetryCount + "/" + CombatConstants.MAX_GLOBAL_RETRIES
            + ", backoff " + backoffMs + "ms");
        pollFramesUntil(() -> false, (int) backoffMs);

        // After backoff, go through ENSURE_ANCHOR to verify position before re-entering combat
        setState(ScriptState.ENSURE_ANCHOR);
    }

    private void handleStop() {
        if (stopReason != null) {
            ScriptLogger.error(this, "STOP: " + stopReason);
        } else {
            ScriptLogger.info(this, "STOP: Script stopping (no reason specified)");
        }
        stop();
    }

    // ───────────────────────────────────────────────────────────────────────────
    // HealthOverlay Helpers
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Read current HP from the HealthOverlay.
     *
     * @return current hitpoints, or null if overlay not visible or health unavailable
     */
    private Integer getHealthOverlayHitpoints() {
        if (healthOverlay == null || !healthOverlay.isVisible()) return null;
        HealthOverlay.HealthResult healthResult =
            (HealthOverlay.HealthResult) healthOverlay.getValue(HealthOverlay.HEALTH);
        if (healthResult == null) return null;
        return healthResult.getCurrentHitpoints();
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Player HP Helper
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Read the PLAYER's current hitpoints from the minimap health orb.
     * NOTE: This is NOT the same as getHealthOverlayHitpoints() which reads the NPC's HP.
     * Source: MinimapOrbs.getHitpoints() via getWidgetManager().getMinimapOrbs()
     * (MCP-discovered: com.osmb.api.ui.minimap.MinimapOrbs#getHitpoints())
     *
     * @return current player HP, or null if unavailable
     */
    private Integer getPlayerHitpoints() {
        var widgetManager = getWidgetManager();
        if (widgetManager == null) return null;
        MinimapOrbs orbs = widgetManager.getMinimapOrbs();
        if (orbs == null) return null;
        return orbs.getHitpoints();
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Global Interrupt Handlers
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Checks if critical resources (runes or food) are depleted.
     * Throttled to avoid inventory snapshot spam (checks every 2-4s).
     * Priority 2 interrupt -- fires before eat/displacement/dialogue.
     */
    private boolean handleHardStopConditions() {
        if (config == null || resourceCheckIds == null) return false;
        if (System.currentTimeMillis() < nextResourceCheckTime) return false;

        var inventory = getWidgetManager() != null ? getWidgetManager().getInventory() : null;
        if (inventory == null) return false;

        var snapshot = inventory.search(resourceCheckIds);
        if (snapshot == null) return false;

        int waterCount = snapshot.getAmount(WATER_RUNE_ID);
        int airCount = snapshot.getAmount(AIR_RUNE_ID);
        int mindCount = snapshot.getAmount(MIND_RUNE_ID);
        boolean missingWater = !isRuneCoveredByStaff(WATER_RUNE_ID) && waterCount == 0;
        boolean missingAir = !isRuneCoveredByStaff(AIR_RUNE_ID) && airCount == 0;
        boolean missingMind = mindCount == 0;

        // Check combat runes (Water Strike): water + air + mind. Elemental staff coverage is user-configured.
        if (missingWater || missingAir || missingMind) {
            ScriptLogger.error(this, "HARD STOP: Out of required combat runes "
                + "(W:" + waterCount + (isRuneCoveredByStaff(WATER_RUNE_ID) ? " staff-covered" : "")
                + " A:" + airCount + (isRuneCoveredByStaff(AIR_RUNE_ID) ? " staff-covered" : "")
                + " M:" + mindCount + ")");
            stopReason = "Out of combat runes";
            setState(ScriptState.STOP);
            return true;
        }

        // Check food
        if (snapshot.getAmount(config.foodType().getItemId()) == 0) {
            ScriptLogger.error(this, "HARD STOP: Out of food (" + config.foodType().getDisplayName() + ")");
            stopReason = "Out of food";
            setState(ScriptState.STOP);
            return true;
        }

        // Schedule next check with randomized interval
        nextResourceCheckTime = System.currentTimeMillis()
            + RandomUtils.uniformRandom(CombatConstants.RESOURCE_CHECK_MIN_MS, CombatConstants.RESOURCE_CHECK_MAX_MS);
        return false;
    }

    /**
     * Eats food when player HP drops below the randomized threshold.
     * Respects eat cooldown timer. Regenerates both cooldown and threshold after each eat.
     * Priority 3 interrupt -- fires after hard stop (so we don't eat when food count is already 0).
     */
    private boolean handleEatFood() {
        if (config == null) return false;

        // Check eat cooldown
        if (System.currentTimeMillis() < nextEatAllowedTime) return false;

        // Read PLAYER HP (not NPC HP)
        Integer playerHp = getPlayerHitpoints();
        if (playerHp == null) return false;  // HP not readable -- don't eat blindly

        if (playerHp > hitpointsToEat) return false;  // Above threshold, no need to eat

        // Find food in inventory
        var inventory = getWidgetManager() != null ? getWidgetManager().getInventory() : null;
        if (inventory == null) return false;

        var snapshot = inventory.search(Set.of(config.foodType().getItemId()));
        if (snapshot == null) return false;

        var food = snapshot.getRandomItem(config.foodType().getItemId());
        if (food == null) return false;  // No food left -- handleHardStopConditions will catch this

        int foodId = config.foodType().getItemId();
        int beforeFoodCount = snapshot.getAmount(foodId);
        int beforeHp = playerHp;

        ScriptLogger.info(this, "INTERRUPT: Eating " + config.foodType().getDisplayName()
            + " at HP=" + playerHp + " (threshold=" + hitpointsToEat + ")");
        food.interact("eat");

        boolean eatConfirmed = pollFramesHuman(() -> {
            Integer hpNow = getPlayerHitpoints();
            if (hpNow != null && hpNow > beforeHp) {
                return true;
            }
            var invNow = getWidgetManager() != null ? getWidgetManager().getInventory() : null;
            if (invNow == null) return false;
            var postSnapshot = invNow.search(Set.of(foodId));
            if (postSnapshot == null) return false;
            return postSnapshot.getAmount(foodId) < beforeFoodCount;
        }, RandomUtils.uniformRandom(CombatConstants.EAT_CONFIRM_MIN_MS, CombatConstants.EAT_CONFIRM_MAX_MS));

        if (eatConfirmed) {
            // Regenerate cooldown and threshold only after confirmed success.
            nextEatAllowedTime = System.currentTimeMillis() + RandomUtils.uniformRandom(1500, 2500);
            hitpointsToEat = RandomUtils.uniformRandom(eatLow, eatHigh);
        } else {
            ScriptLogger.warning(this, "INTERRUPT: Eat action not confirmed - will retry soon");
            nextEatAllowedTime = System.currentTimeMillis() + RandomUtils.uniformRandom(400, 800);
        }

        return true;
    }

    /**
     * Detects anchor displacement as a global interrupt.
     * Forces ENSURE_ANCHOR when player is off-anchor, EXCEPT during:
     * - LOOT_MANUAL (player is intentionally off-anchor for pickup)
     * - RECOVERY (already handling a failure)
     * - STOP (script is shutting down)
     * - ENSURE_ANCHOR (already walking back)
     * - INIT (not yet initialized)
     * Priority 4 interrupt.
     */
    private boolean handleDisplacementInterrupt() {
        if (anchorManager == null) return false;

        // During first-cast gate, allow temporary off-anchor movement until cast is confirmed.
        if (awaitingFirstCast) return false;

        // Skip displacement check for states where player may be intentionally off-anchor
        if (currentState == ScriptState.LOOT_MANUAL
                || currentState == ScriptState.RECOVERY
                || currentState == ScriptState.STOP
                || currentState == ScriptState.ENSURE_ANCHOR
                || currentState == ScriptState.INIT) {
            return false;
        }

        if (!anchorManager.isDisplaced()) return false;

        if ((currentState == ScriptState.MONITOR_COMBAT || currentState == ScriptState.ENGAGE_TARGET)
                && combatTracker != null
                && combatTracker.getTargetUuid() != null) {
            reengageLockedTarget = true;
            reengageAttempts = 0;
        }

        ScriptLogger.warning(this, "INTERRUPT: Displaced from anchor during " + currentState
            + " - forcing ENSURE_ANCHOR");
        setState(ScriptState.ENSURE_ANCHOR);
        return true;
    }

    /**
     * Dismisses level-up and chat dialogues that block gameplay.
     * Handles TAP_HERE_TO_CONTINUE and CHAT_DIALOGUE types.
     * Adds a brief random delay before dismissing (anti-detection).
     * Priority 5 interrupt (lowest priority -- dismissing a dialogue is less urgent than eating).
     */
    private boolean handleDialogueInterrupt() {
        var widgetManager = getWidgetManager();
        if (widgetManager == null) return false;

        var dialogue = widgetManager.getDialogue();
        if (dialogue == null) return false;

        var dialogueType = dialogue.getDialogueType();
        if (dialogueType == null) return false;

        // Only auto-dismiss "tap to continue" and chat dialogues (level-ups, NPC chat)
        if (dialogueType == DialogueType.TAP_HERE_TO_CONTINUE
                || dialogueType == DialogueType.CHAT_DIALOGUE) {
            ScriptLogger.info(this, "INTERRUPT: Dismissing dialogue (" + dialogueType + ")");
            // Brief human-like delay before dismissing (anti-detection)
            pollFramesUntil(() -> false, RandomUtils.uniformRandom(300, 800));
            dialogue.continueChatDialogue();
            return true;
        }

        return false;
    }

    /**
     * Checks if XP failsafe should trigger.
     * Auto-stops the script after the configured minutes without any XP gain.
     * Logs a warning when approaching the timeout threshold (60s before).
     * Only active when xpFailsafeEnabled is true in config.
     *
     * Runs AFTER other interrupts but BEFORE state dispatch -- XP failsafe is a
     * background watchdog, not a high-priority interrupt like eating.
     */
    private boolean handleXpFailsafe() {
        if (config == null || !config.xpFailsafeEnabled()) return false;

        long timeSinceXP = getTimeSinceLastXPGain();
        long timeoutMillis = (long) config.xpFailsafeTimeoutMinutes() * 60 * 1000L;

        if (timeSinceXP > timeoutMillis) {
            ScriptLogger.error(this, "XP FAILSAFE: No XP gain for "
                + config.xpFailsafeTimeoutMinutes() + " minutes - stopping");
            stopReason = "XP failsafe: no XP for " + config.xpFailsafeTimeoutMinutes() + " minutes";
            setState(ScriptState.STOP);
            return true;
        }

        // Warn when approaching timeout (within 60 seconds of trigger)
        long warningThresholdMs = timeoutMillis - (CombatConstants.XP_FAILSAFE_WARNING_SECONDS * 1000L);
        if (timeSinceXP > warningThresholdMs) {
            long secondsLeft = (timeoutMillis - timeSinceXP) / 1000;
            ScriptLogger.warning(this, "XP Failsafe: " + secondsLeft + "s until auto-stop");
        }

        return false;
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
        ScriptLogger.info(this, "Staff rune coverage: air=" + config.staffCoversAirRune()
            + ", water=" + config.staffCoversWaterRune()
            + ", earth=" + config.staffCoversEarthRune()
            + ", fire=" + config.staffCoversFireRune());

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

        // ── Anchor overlay (green) ──────────────────────────────────────────
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

        // ── Target overlay (orange) ─────────────────────────────────────────
        if (combatTracker != null && combatTracker.getTargetPosition() != null) {
            WorldPosition targetPos = combatTracker.getTargetPosition();
            com.osmb.api.shape.Polygon targetPoly = getSceneProjector().getTilePoly(targetPos);
            if (targetPoly != null) {
                canvas.fillPolygon(targetPoly, 0xFF6600, 0.4);   // Orange fill 40% opacity
                canvas.drawPolygon(targetPoly, 0xFF3300, 1.0);    // Dark orange outline

                com.osmb.api.shape.Rectangle tBounds = targetPoly.getBounds();
                if (tBounds != null) {
                    int tx = tBounds.x + tBounds.width / 2;
                    int ty = tBounds.y + tBounds.height / 2;
                    String label = combatTracker.isEngaged() ? "TARGET" : "ACQUIRING";
                    drawOverlayText(canvas, label, tx - 32, ty - 18, 0xFFAA33, true);

                    String targetUuid = combatTracker.getTargetUuid();
                    if (targetUuid != null && targetUuid.length() >= 4) {
                        drawOverlayText(canvas, "ID " + targetUuid.substring(0, 4), tx - 26, ty - 4, 0xFFFFFF, true);
                    }
                }
            }
        }

        // ── Death position overlay (red) ────────────────────────────────────
        if (combatTracker != null && combatTracker.getLastDeathCenterPos() != null) {
            WorldPosition deathPos = combatTracker.getLastDeathCenterPos();
            com.osmb.api.shape.Polygon deathPoly = getSceneProjector().getTilePoly(deathPos);
            if (deathPoly != null) {
                canvas.fillPolygon(deathPoly, 0xFF0000, 0.3);    // Red fill 30% opacity
                canvas.drawPolygon(deathPoly, 0xCC0000, 1.0);     // Dark red outline

                com.osmb.api.shape.Rectangle dBounds = deathPoly.getBounds();
                if (dBounds != null) {
                    int dx = dBounds.x + dBounds.width / 2;
                    int dy = dBounds.y + dBounds.height / 2;
                    canvas.drawText("DEATH", dx - 20, dy - 10, 0xFF0000,
                        new java.awt.Font("Arial", java.awt.Font.BOLD, 12));
                }
            }
        }

        // ── All tracked NPCs overlay (yellow) ──────────────────────────────
        if (combatTracker != null) {
            Map<String, WorldPosition> allActive = combatTracker.getNpcTracker().getActiveNpcs();
            String targetUuid = combatTracker.getTargetUuid();

            for (Map.Entry<String, WorldPosition> entry : allActive.entrySet()) {
                // Skip the target -- already drawn in orange above
                if (entry.getKey().equals(targetUuid)) continue;

                WorldPosition npcPos = entry.getValue();
                com.osmb.api.shape.Polygon npcPoly = getSceneProjector().getTilePoly(npcPos);
                if (npcPoly != null) {
                    canvas.fillPolygon(npcPoly, 0xFFFF00, 0.2);   // Yellow fill 20% opacity
                    canvas.drawPolygon(npcPoly, 0xCCCC00, 0.6);    // Dark yellow outline

                    com.osmb.api.shape.Rectangle nBounds = npcPoly.getBounds();
                    if (nBounds != null) {
                        int nx = nBounds.x + nBounds.width / 2;
                        int ny = nBounds.y + nBounds.height / 2;
                        // Show first 4 chars of UUID for identity verification
                        drawOverlayText(canvas, "ID " + entry.getKey().substring(0, 4), nx - 18, ny - 10, 0xFFFFFF, false);
                    }
                }
            }
        }
    }

    private void drawOverlayText(Canvas canvas, String text, int x, int y, int color, boolean bold) {
        java.awt.Font font = new java.awt.Font("Monospaced", bold ? java.awt.Font.BOLD : java.awt.Font.PLAIN, 12);
        // Small black shadow to keep labels readable over bright overlays/backgrounds.
        canvas.drawText(text, x + 1, y + 1, 0x000000, font);
        canvas.drawText(text, x, y, color, font);
    }

    @Override
    protected void onMetricsGameStateChanged(GameState newGameState) {
        if (newGameState != null && newGameState != GameState.LOGGED_IN) {
            ScriptLogger.warning(this, "Logout/world-hop detected (" + newGameState + ") - clearing combat state");
            clearTransientCombatState(false);
        } else if (newGameState == GameState.LOGGED_IN) {
            ScriptLogger.info(this, "Logged in - clearing stale combat state and re-anchoring");
            clearTransientCombatState(true);
        }
    }

    @Override
    public void onRelog() {
        ScriptLogger.info(this, "Relog detected - clearing combat state and re-anchoring");
        clearTransientCombatState(true);
    }

    private void clearTransientCombatState(boolean transitionToAnchor) {
        targetClusterBounds = null;
        consecutiveWalkbackFailures = 0;
        overlayDisappearGraceMs = 0;
        globalRetryCount = 0;
        engageMenuMissRetries = 0;
        clearFirstCastGate();
        clearReengageLock();
        clearRecentKillUuidLockout();
        nextEatAllowedTime = 0;
        nextResourceCheckTime = 0;
        stopReason = null;

        if (combatTracker != null) {
            combatTracker.fullReset();
        }

        if (initialised && transitionToAnchor) {
            setState(ScriptState.ENSURE_ANCHOR);
        }
    }

    @Override
    public boolean canBreak() {
        return true;  // Phase 1 stub, will be restricted in Phase 8
    }

    @Override
    public int[] regionsToPrioritise() {
        // Waterfall safespot anchor region (tile 2568,9893 -> region 10394)
        return new int[]{10394};
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
