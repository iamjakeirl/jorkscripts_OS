package com.jork.script.jorkHunter.state;

import com.jork.script.jorkHunter.JorkHunter;
import com.jork.script.jorkHunter.trap.TrapType;
import com.jork.script.jorkHunter.trap.TrapStateHandlingMode;
import com.jork.utils.ExceptionUtils;
import com.jork.utils.ScriptLogger;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.visual.PixelAnalyzer;
import com.osmb.api.visual.PixelCluster;
import com.osmb.api.visual.SearchablePixel;
import com.osmb.api.shape.Polygon;
import com.osmb.api.scene.RSTile;
import com.osmb.api.visual.PixelCluster.ClusterSearchResult;
import com.osmb.api.utils.RandomUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Manages tracked trap states and trap operation flags.
 */
public class TrapStateManager {

    private final JorkHunter script;
    private final TrapType trapType;
    private final ConcurrentHashMap<WorldPosition, TrapInfo> traps = new ConcurrentHashMap<>();
    private final boolean distanceBasedPrioritization;
    private final AtomicBoolean isLayingTrap = new AtomicBoolean(false);
    private volatile WorldPosition currentlyLayingPosition = null; // Track which position is being laid
    private final AtomicBoolean isResettingTrap = new AtomicBoolean(false);
    private volatile WorldPosition currentlyResettingPosition = null; // Track which position is being reset

    // State tracking for transition detection
    private Map<WorldPosition, PixelAnalyzer.RespawnCircle.Type> previousRespawnStates = new HashMap<>();

    // Grace period tracking for trap collapse detection
    private Map<WorldPosition, Long> missingTrapsTimestamp = new HashMap<>();
    // Per-trap random grace periods to handle animation delays (6-20 seconds)
    private Map<WorldPosition, Long> trapGracePeriods = new HashMap<>();

    // Per-trap randomized critical thresholds for successful traps (25-35 seconds)
    private Map<WorldPosition, Long> trapCriticalThresholds = new HashMap<>();

    // UI-occluded trap tracking for repositioning
    private Set<WorldPosition> trapsNeedingRepositioning = new HashSet<>();

    public TrapStateManager(JorkHunter script, TrapType trapType) {
        this.script = script;
        this.trapType = trapType;
        this.distanceBasedPrioritization = script.isDistanceBasedPrioritization();
    }

    /**
     * Efficiently scans for respawn circle transitions and triggers targeted pixel analysis only when needed.
     * Only performs expensive operations when trap states actually change.
     */
    public void scanAndUpdateTrapStates() {
        try {
            ScriptLogger.debug(script, "=== TRAP STATE SCAN STARTING ===");

            // Continue scanning while a trap is being laid.
            if (isLayingTrap.get() && currentlyLayingPosition != null) {
                ScriptLogger.debug(script, "Scanning during trap laying animation at " + currentlyLayingPosition);
            }

            // Log what traps we think we have
            ScriptLogger.debug(script, "Currently tracking " + traps.size() + " traps:");
            for (Map.Entry<WorldPosition, TrapInfo> entry : traps.entrySet()) {
                ScriptLogger.debug(script, "  - Trap at " + entry.getKey() + " in state " + entry.getValue().state());
            }

            // Get current respawn circles
            List<PixelAnalyzer.RespawnCircle> respawnCircles = script.getPixelAnalyzer().findRespawnCircleTypes();
            if (respawnCircles == null) {
                respawnCircles = Collections.emptyList();
            }

            // Build current state map
            Map<WorldPosition, PixelAnalyzer.RespawnCircle.Type> currentRespawnStates = new HashMap<>();

            ScriptLogger.debug(script, "Found " + respawnCircles.size() + " respawn circles in visual scan");

            for (PixelAnalyzer.RespawnCircle circle : respawnCircles) {
                PixelAnalyzer.RespawnCircle.Type circleType = circle.getType();
                int zOffset = getZOffsetForCircleType(circleType);

                List<WorldPosition> positions = script.getUtils().getWorldPositionForRespawnCircles(
                    List.of(circle.getBounds()), zOffset);

                for (WorldPosition pos : positions) {
                    currentRespawnStates.put(pos, circleType);
                    ScriptLogger.debug(script, "Respawn circle detected: " + pos + " = " + circleType);
                }
            }

            // Log what we're tracking vs what we found
            ScriptLogger.debug(script, "Previous states tracked: " + previousRespawnStates.size() + " positions");
            ScriptLogger.debug(script, "Current states found: " + currentRespawnStates.size() + " positions");
            ScriptLogger.debug(script, "Traps being tracked: " + traps.size());

            checkGracePeriods(currentRespawnStates);

            checkCollapsedTrapUrgency();

            handleStateTransitions(currentRespawnStates);

            // Update tracked circle states.
            for (Map.Entry<WorldPosition, PixelAnalyzer.RespawnCircle.Type> entry : currentRespawnStates.entrySet()) {
                previousRespawnStates.put(entry.getKey(), entry.getValue());
            }

            // Remove stale entries that no longer need tracking.
            Set<Map.Entry<WorldPosition, PixelAnalyzer.RespawnCircle.Type>> entriesToRemove = new HashSet<>();
            for (Map.Entry<WorldPosition, PixelAnalyzer.RespawnCircle.Type> entry : previousRespawnStates.entrySet()) {
                WorldPosition pos = entry.getKey();
                TrapInfo trapInfo = traps.get(pos);
                boolean isCollapsed = trapInfo != null && trapInfo.state() == TrapState.COLLAPSED;

                if (!currentRespawnStates.containsKey(pos) &&
                    (!traps.containsKey(pos) || isCollapsed) &&
                    !missingTrapsTimestamp.containsKey(pos)) {
                    entriesToRemove.add(entry);
                }
            }

            for (Map.Entry<WorldPosition, PixelAnalyzer.RespawnCircle.Type> entry : entriesToRemove) {
                previousRespawnStates.remove(entry.getKey());
            }

            ScriptLogger.debug(script, "Trap scan complete. Active: " + getActiveCount() +
                             ", Finished: " + getFinishedCount() + ", Total: " + getTotalCount());

        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.error(script, "Error during trap state scan: " + e.getMessage());
        }
    }

    /**
     * Maps the respawn circle type enum to our trap state enum
     * Uses defensive programming to handle unknown enum values gracefully
     */
    private TrapState mapRespawnCircleTypeToTrapState(PixelAnalyzer.RespawnCircle.Type circleType) {
        if (circleType == null) {
            return TrapState.UNKNOWN;
        }

        try {
            String typeString = circleType.toString().toUpperCase();
            return switch (typeString) {
                case "YELLOW" -> TrapState.ACTIVE;
                case "GREEN", "RED" -> {
                    if (trapType.getStateHandlingMode() == TrapStateHandlingMode.BINARY) {
                        yield TrapState.FINISHED;
                    } else {
                        yield typeString.equals("GREEN") ? TrapState.FINISHED_SUCCESS : TrapState.FINISHED_FAILED;
                    }
                }
                default -> {
                    ScriptLogger.debug(script, "Unknown respawn circle type: " + typeString);
                    yield TrapState.UNKNOWN;
                }
            };
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.warning(script, "Error mapping respawn circle type: " + e.getMessage());
            return TrapState.UNKNOWN;
        }
    }

    /**
     * Helper method to get appropriate Z-offset for circle type
     */
    public int getZOffsetForCircleType(PixelAnalyzer.RespawnCircle.Type circleType) {
        if (circleType == null) return trapType.getActiveZOffset();

        String typeString = circleType.toString().toUpperCase();
        return switch (typeString) {
            case "YELLOW" -> trapType.getActiveZOffset();
            case "GREEN", "RED" -> trapType.getFinishedZOffset();
            default -> trapType.getActiveZOffset();
        };
    }

    /**
     * Handles state transitions and triggers targeted pixel analysis only when needed
     */
    private void handleStateTransitions(Map<WorldPosition, PixelAnalyzer.RespawnCircle.Type> currentStates) {
        // Check all tracked trap positions for state changes
        Set<WorldPosition> allPositions = new HashSet<>(traps.keySet());
        allPositions.addAll(currentStates.keySet());

        ScriptLogger.debug(script, "Checking state transitions for " + allPositions.size() + " positions");

        for (WorldPosition pos : allPositions) {
            PixelAnalyzer.RespawnCircle.Type previousType = previousRespawnStates.get(pos);
            PixelAnalyzer.RespawnCircle.Type currentType = currentStates.get(pos);

            if (previousType != null || currentType != null) {
                ScriptLogger.debug(script, "Position " + pos + ": " + previousType + " → " + currentType);
            }

            if (previousType != currentType) {
                ScriptLogger.info(script, "STATE TRANSITION DETECTED at " + pos + ": " + previousType + " → " + currentType);
                handleStateTransition(pos, previousType, currentType);
            }

            if (currentType != null) {
                TrapState newState = mapRespawnCircleTypeToTrapState(currentType);
                updateOrCreateTrap(pos, newState);
            }
        }
    }

    /**
     * Checks grace periods for all missing traps and marks them as collapsed if timeout elapsed
     */
    private void checkGracePeriods(Map<WorldPosition, PixelAnalyzer.RespawnCircle.Type> currentStates) {
        Iterator<Map.Entry<WorldPosition, Long>> iterator = missingTrapsTimestamp.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<WorldPosition, Long> entry = iterator.next();
            WorldPosition pos = entry.getKey();
            Long startTime = entry.getValue();

            if (currentStates.containsKey(pos)) {
                ScriptLogger.debug(script, "Trap at " + pos + " reappeared - cancelling collapse detection");
                iterator.remove();
                trapGracePeriods.remove(pos);
                continue;
            }

            long missingTime = System.currentTimeMillis() - startTime;
            long gracePeriod = trapGracePeriods.getOrDefault(pos, 10000L);
            if (missingTime >= gracePeriod) {
                ScriptLogger.info(script, "Grace period expired for trap at " + pos + " after " + missingTime + "ms (threshold: " + gracePeriod + "ms)");

                markTrapAsCollapsed(pos);
                setFlag(pos, TrapFlag.PENDING_VERIFICATION);
                ScriptLogger.info(script, "Marked trap at " + pos + " as COLLAPSED with PENDING_VERIFICATION flag");

                previousRespawnStates.remove(pos);

                iterator.remove();
                trapGracePeriods.remove(pos);
            }
        }
    }

    /**
     * Handles a specific state transition for a trap
     */
    private void handleStateTransition(WorldPosition pos, PixelAnalyzer.RespawnCircle.Type previous, PixelAnalyzer.RespawnCircle.Type current) {
        // Ready-to-interact transition.
        if (isYellow(previous) && (isGreen(current) || isRed(current))) {
            ScriptLogger.info(script, "Trap at " + pos + " ready for interaction (" + previous + " → " + current + ")");
            setFlag(pos, TrapFlag.READY_FOR_REMOVAL);
            missingTrapsTimestamp.remove(pos);
            trapGracePeriods.remove(pos);

            if (isGreen(current)) {
                script.onTrapSuccess();
                long criticalThreshold = RandomUtils.weightedRandom(25000, 35000);
                trapCriticalThresholds.put(pos, criticalThreshold);
                ScriptLogger.debug(script, "Trap at " + pos + " will become critical after " +
                                 (criticalThreshold / 1000) + " seconds");
            } else if (isRed(current)) {
                script.onTrapFailed();
            }
        }
        // Missing circle transition.
        else if (previous != null && current == null) {
            TrapInfo existingTrap = traps.get(pos);
            boolean alreadyCollapsed = existingTrap != null && existingTrap.state() == TrapState.COLLAPSED;

            if (!missingTrapsTimestamp.containsKey(pos) && !alreadyCollapsed) {
                missingTrapsTimestamp.put(pos, System.currentTimeMillis());
                long gracePeriod;
                if (trapType == TrapType.BIRD_SNARE && isYellow(previous)) {
                    gracePeriod = RandomUtils.uniformRandom(2151, 4216);
                } else {
                    gracePeriod = RandomUtils.uniformRandom(2251, 4117);
                }
                trapGracePeriods.put(pos, gracePeriod);
                String stateDesc = previous.toString().toUpperCase();
                ScriptLogger.info(script, "GRACE PERIOD STARTED: Trap at " + pos + " disappeared from " + stateDesc + " state, starting " + gracePeriod + "ms grace period");
            } else if (alreadyCollapsed) {
                ScriptLogger.debug(script, "Trap at " + pos + " already marked as COLLAPSED - skipping grace period");
            } else {
                ScriptLogger.debug(script, "Grace period already active for trap at " + pos);

                long graceStartTime = missingTrapsTimestamp.get(pos);
                long graceDuration = System.currentTimeMillis() - graceStartTime;

                if (graceDuration > 2000) {
                    TrapState finishedState = (trapType.getStateHandlingMode() == TrapStateHandlingMode.BINARY)
                        ? TrapState.FINISHED
                        : TrapState.FINISHED_SUCCESS;

                    ScriptLogger.info(script, "Marking trap at " + pos + " as " + finishedState + " with verification flag after " + graceDuration + "ms grace period");
                    updateOrCreateTrap(pos, finishedState);
                    setFlag(pos, TrapFlag.PENDING_VERIFICATION);
                    missingTrapsTimestamp.remove(pos);
                    trapGracePeriods.remove(pos);
                }
            }
        }
        // Trap reappeared.
        else if (previous == null && current != null && missingTrapsTimestamp.containsKey(pos)) {
            ScriptLogger.debug(script, "Trap at " + pos + " reappeared as " + current + " - cancelling collapse detection");
            missingTrapsTimestamp.remove(pos);
            trapGracePeriods.remove(pos);
        }
        // Newly observed trap.
        else if (previous == null && current != null) {
            TrapState state = mapRespawnCircleTypeToTrapState(current);
            ScriptLogger.info(script, "Discovered new trap at " + pos + " in state " + state);
        }
    }

    private boolean isYellow(PixelAnalyzer.RespawnCircle.Type type) {
        return type != null && "YELLOW".equals(type.toString().toUpperCase());
    }

    private boolean isGreen(PixelAnalyzer.RespawnCircle.Type type) {
        return type != null && "GREEN".equals(type.toString().toUpperCase());
    }

    private boolean isRed(PixelAnalyzer.RespawnCircle.Type type) {
        return type != null && "RED".equals(type.toString().toUpperCase());
    }

    /**
     * Updates or creates trap tracking entry with appropriate flags
     */
    private void updateOrCreateTrap(WorldPosition pos, TrapState newState) {
        traps.compute(pos, (position, currentInfo) -> {
            if (currentInfo == null) {
                TrapFlags flags = new TrapFlags();
                switch (newState) {
                    case FINISHED, FINISHED_SUCCESS, FINISHED_FAILED -> flags.addFlag(TrapFlag.READY_FOR_REMOVAL);
                    case COLLAPSED -> flags.addFlag(TrapFlag.NEEDS_INTERACTION);
                    case LAYING -> flags.addFlag(TrapFlag.LAYING_IN_PROGRESS);
                    case UNKNOWN -> flags.addFlag(TrapFlag.PENDING_VERIFICATION);
                    default -> { }
                }
                long now = System.currentTimeMillis();
                return new TrapInfo(pos, newState, trapType, flags, now, now, now);
            } else if (currentInfo.state() != newState) {
                ScriptLogger.info(script, "Trap at " + pos + " changed from " + currentInfo.state() +
                    " (after " + currentInfo.getFormattedStateTime() + ") to " + newState);
                TrapInfo updated = currentInfo.withState(newState).withClearedFlags();
                switch (newState) {
                    case FINISHED, FINISHED_SUCCESS, FINISHED_FAILED -> updated = updated.withFlag(TrapFlag.READY_FOR_REMOVAL);
                    case COLLAPSED -> updated = updated.withFlag(TrapFlag.NEEDS_INTERACTION);
                    case LAYING -> updated = updated.withFlag(TrapFlag.LAYING_IN_PROGRESS);
                    case UNKNOWN -> updated = updated.withFlag(TrapFlag.PENDING_VERIFICATION);
                    default -> { }
                }
                return updated;
            } else {
                return currentInfo;
            }
        });
    }

    /**
     * Checks collapsed traps and successful traps for urgency based on time thresholds
     */
    private void checkCollapsedTrapUrgency() {
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<WorldPosition, TrapInfo> entry : traps.entrySet()) {
            WorldPosition pos = entry.getKey();
            TrapInfo trapInfo = entry.getValue();

            if (trapInfo.state() == TrapState.COLLAPSED) {
                long collapsedDuration = currentTime - trapInfo.lastUpdated();

                if (collapsedDuration > 90000 && !trapInfo.flags().hasFlag(TrapFlag.URGENT_COLLAPSED)) {
                    ScriptLogger.warning(script, "Collapsed trap at " + pos + " has been on ground for " +
                        (collapsedDuration / 1000) + " seconds - marking as URGENT");
                    setFlag(pos, TrapFlag.URGENT_COLLAPSED);
                }

                if (collapsedDuration > 150000) {
                    ScriptLogger.error(script, "CRITICAL: Collapsed trap at " + pos + " will despawn soon! (" +
                        (collapsedDuration / 1000) + " seconds on ground)");
                }
            }
            else if (trapInfo.state() == TrapState.FINISHED_SUCCESS ||
                     (trapInfo.state() == TrapState.FINISHED && trapType.getStateHandlingMode() == TrapStateHandlingMode.BINARY)) {
                long successDuration = currentTime - trapInfo.lastUpdated();

                long criticalThreshold = trapCriticalThresholds.getOrDefault(pos, 30000L);

                if (successDuration > criticalThreshold && !trapInfo.flags().hasFlag(TrapFlag.CRITICAL_SUCCESS)) {
                    ScriptLogger.warning(script, "Successful trap at " + pos + " has been waiting for " +
                        (successDuration / 1000) + " seconds (threshold: " + (criticalThreshold / 1000) +
                        "s) - marking as CRITICAL (collapse risk)");
                    setFlag(pos, TrapFlag.CRITICAL_SUCCESS);
                }

                if (successDuration > 55000) {
                    ScriptLogger.error(script, "CRITICAL: Successful trap at " + pos + " about to collapse! (" +
                        (successDuration / 1000) + " seconds since catch)");
                }
            }
        }
    }

    /**
     * Marks a trap as collapsed with appropriate flags
     */
    private void markTrapAsCollapsed(WorldPosition pos) {
        traps.compute(pos, (position, currentInfo) -> {
            if (currentInfo != null) {
                return currentInfo.withState(TrapState.COLLAPSED)
                    .withClearedFlags()
                    .withFlag(TrapFlag.NEEDS_INTERACTION);
            }
            long now = System.currentTimeMillis();
            return new TrapInfo(pos, TrapState.COLLAPSED, trapType,
                new TrapFlags(TrapFlag.NEEDS_INTERACTION),
                now, now, now);
        });
    }

    /**
     * Atomically starts laying a trap at the specified position
     */
    public boolean startLayingTrap(WorldPosition position) {
        if (!isLayingTrap.compareAndSet(false, true)) {
            ScriptLogger.warning(script, "Cannot start laying trap - already laying a trap");
            return false;
        }

        currentlyLayingPosition = position;
        TrapInfo layingInfo = TrapInfo.laying(position, trapType);
        traps.put(position, layingInfo);
        ScriptLogger.info(script, "Started laying trap at " + position + " - blocking state scans until animation completes");
        return true;
    }

    /**
     * Atomically completes the trap laying process
     */
    public void completeTrapLaying(WorldPosition position, boolean success) {
        if (success) {
            traps.compute(position, (pos, info) -> {
                if (info != null && info.state() == TrapState.LAYING) {
                    ScriptLogger.info(script, "Successfully completed laying trap at " + pos);
                    return info.withState(TrapState.ACTIVE);
                }
                return info;
            });
        } else {
            ScriptLogger.warning(script, "Failed to lay trap at " + position + " - removing from tracking");
            traps.remove(position);
            clearLayingFlag();
        }
    }

    /**
     * Clears the trap laying flag and position after animation completes
     */
    public void clearLayingFlag() {
        if (isLayingTrap.compareAndSet(true, false)) {
            ScriptLogger.debug(script, "Cleared trap laying flag - ready for next action");
            currentlyLayingPosition = null;
        }
    }

    public void clearResetFlag() {
        if (isResettingTrap.compareAndSet(true, false)) {
            ScriptLogger.debug(script, "Cleared trap reset flag - ready for next action");
            currentlyResettingPosition = null;
        }
    }

    /**
     * Starts the reset process for a trap (chinchompa-specific).
     * This marks a trap as being reset, which is a compound action.
     * @param position The position of the trap being reset
     * @return true if reset was started, false if already resetting
     */
    public boolean startResettingTrap(WorldPosition position) {
        if (isResettingTrap.compareAndSet(false, true)) {
            currentlyResettingPosition = position;
            TrapInfo existing = traps.get(position);
            if (existing != null) {
                TrapInfo resettingTrap = existing.withState(TrapState.RESETTING);
                traps.put(position, resettingTrap);
                ScriptLogger.info(script, "Started resetting trap at " + position);
            }
            return true;
        }
        return false;
    }

    /**
     * Completes the reset process for a trap.
     * @param position The position where the trap was reset
     * @param success Whether the reset was successful
     */
    public void completeResetTrap(WorldPosition position, boolean success) {
        isResettingTrap.set(false);
        currentlyResettingPosition = null;

        if (success) {
            TrapInfo existing = traps.get(position);
            if (existing != null) {
                TrapInfo activeTrap = existing.withState(TrapState.ACTIVE).withClearedFlags();
                traps.put(position, activeTrap);
                ScriptLogger.info(script, "Reset trap complete at " + position + " - trap is now ACTIVE");
            } else {
                long now = System.currentTimeMillis();
                TrapInfo activeTrap = new TrapInfo(position, TrapState.ACTIVE, trapType,
                    new TrapFlags(), now, now, now);
                traps.put(position, activeTrap);
                ScriptLogger.info(script, "Reset trap complete at " + position + " - trap is now ACTIVE (new)");
            }
        } else {
            traps.remove(position);
            ScriptLogger.warning(script, "Reset trap failed at " + position + " - removed from tracking");
        }
    }

    /**
     * Removes a trap from tracking (when picked up)
     */
    public boolean removeTrap(WorldPosition position) {
        TrapInfo removed = traps.remove(position);
        if (removed != null) {
            missingTrapsTimestamp.remove(position);
            trapGracePeriods.remove(position);
            trapCriticalThresholds.remove(position);
            trapsNeedingRepositioning.remove(position);
            ScriptLogger.info(script, "Removed trap at " + position + " from tracking");
            return true;
        }
        return false;
    }

    /**
     * Gets the number of traps in active state.
     */
    public int getActiveCount() {
        return (int) traps.values().stream().filter(info -> info.state() == TrapState.ACTIVE).count();
    }

    public int getFinishedCount() {
        return (int) traps.values().stream().filter(TrapInfo::isFinished).count();
    }

    public int getTotalCount() {
        return traps.size();
    }

    public boolean isCurrentlyLayingTrap() {
        return isLayingTrap.get();
    }

    public boolean isCurrentlyResettingTrap() {
        return isResettingTrap.get();
    }

    public WorldPosition getCurrentlyResettingPosition() {
        return currentlyResettingPosition;
    }

    /**
     * Gets the position where a trap is currently being laid
     * @return The position, or null if not laying a trap
     */
    public WorldPosition getCurrentlyLayingPosition() {
        return currentlyLayingPosition;
    }

    public boolean isEmpty() {
        return traps.isEmpty();
    }

    /**
     * Clears all trap tracking (for initialization)
     */
    public void clearAllTraps() {
        traps.clear();
        isLayingTrap.set(false);
        currentlyLayingPosition = null;
        isResettingTrap.set(false);
        currentlyResettingPosition = null;
        previousRespawnStates.clear();
        missingTrapsTimestamp.clear();
        trapGracePeriods.clear();
        trapCriticalThresholds.clear();
        trapsNeedingRepositioning.clear();
        ScriptLogger.info(script, "Cleared all trap tracking data");
    }

    /**
     * Marks all traps for expedited collection, regardless of their current state.
     * This is used when preparing for a break/hop to force collection of all traps quickly.
     * Active traps will be dismantled, finished traps will be collected.
     */
    public void markAllTrapsForExpediteCollection() {
        int markedCount = 0;
        for (Map.Entry<WorldPosition, TrapInfo> entry : traps.entrySet()) {
            TrapInfo trap = entry.getValue();
            setFlag(trap.position(), TrapFlag.EXPEDITE_COLLECTION);
            markedCount++;

            ScriptLogger.debug(script, "Marked trap at " + trap.position() +
                " (state: " + trap.state() + ") for expedited collection");
        }

        ScriptLogger.info(script, "Marked " + markedCount + " traps for expedited collection before break/hop");
    }

    /**
     * Gets a defensive copy of all tracked trap positions for compatibility
     */
    public List<WorldPosition> getLaidTrapPositions() {
        return new ArrayList<>(traps.keySet());
    }

    /**
     * Checks if there are any pending grace periods that could result in trap pickups
     */
    public boolean hasPendingGracePeriods() {
        return !missingTrapsTimestamp.isEmpty();
    }

    /**
     * Gets the count of traps with pending grace periods
     */
    public int getPendingGracePeriodsCount() {
        return missingTrapsTimestamp.size();
    }

    /**
     * Marks a trap as needing repositioning due to UI occlusion or being off-screen.
     * This is called when a trap tile cannot be seen properly.
     * @param pos The trap position that needs better viewing angle
     */
    public void markTrapForRepositioning(WorldPosition pos) {
        if (!trapsNeedingRepositioning.contains(pos)) {
            trapsNeedingRepositioning.add(pos);
            ScriptLogger.debug(script, "Marked trap at " + pos + " for repositioning due to visibility issues");
        }
    }

    /**
     * Checks if any traps need repositioning due to UI occlusion.
     * @return true if repositioning is needed, false otherwise
     */
    public boolean hasTrapsNeedingRepositioning() {
        return !trapsNeedingRepositioning.isEmpty();
    }

    /**
     * Clears all repositioning flags when we move to an optimal viewing position.
     */
    public void clearAllRepositioningFlags() {
        if (!trapsNeedingRepositioning.isEmpty()) {
            ScriptLogger.debug(script, "Clearing " + trapsNeedingRepositioning.size() + " repositioning flags");
            trapsNeedingRepositioning.clear();
        }
    }

    /**
     * Verifies and removes phantom traps during drain mode.
     * A phantom trap is one that we're tracking but doesn't actually exist anymore.
     * A real trap will have EITHER:
     * - A respawn circle (for ACTIVE/FINISHED states)
     * - Collapsed trap pixels (for COLLAPSED state)
     * - Standing trap pixels (any valid trap)
     */
    public void verifyPhantomTraps() {
        if (traps.isEmpty()) {
            return;
        }

        // First get all visible respawn circles
        List<PixelAnalyzer.RespawnCircle> visibleCircles = script.getPixelAnalyzer().findRespawnCircleTypes();
        Set<WorldPosition> circlePositions = new HashSet<>();

        if (visibleCircles != null && !visibleCircles.isEmpty()) {
            for (PixelAnalyzer.RespawnCircle circle : visibleCircles) {
                List<WorldPosition> circlePositionsList = script.getUtils().getWorldPositionForRespawnCircles(
                    List.of(circle.getBounds()),
                    getZOffsetForCircleType(circle.getType())
                );
                if (circlePositionsList != null && !circlePositionsList.isEmpty()) {
                    circlePositions.add(circlePositionsList.get(0));
                }
            }
        }

        Set<WorldPosition> phantomPositions = new HashSet<>();

        for (Map.Entry<WorldPosition, TrapInfo> entry : traps.entrySet()) {
            WorldPosition pos = entry.getKey();
            TrapInfo info = entry.getValue();

            // If there's a respawn circle at this position, it's definitely not phantom
            if (circlePositions.contains(pos)) {
                continue;
            }

            // Check if trap tile is on screen
            RSTile tile = script.getSceneManager().getTile(pos);
            if (tile == null) {
                // Trap is off-screen, can't verify - skip it
                continue;
            }

            Polygon trapArea = tile.getTilePoly();
            if (trapArea == null) {
                // Tile polygon not available - skip
                continue;
            }

            // No respawn circle found, check for trap model pixels
            boolean hasVisibleTrap = false;

            // Check for standing trap pixels first (using multiple clusters if available)
            SearchablePixel[][] standingClusters = trapType.getStandingPixelClusters();
            for (SearchablePixel[] cluster : standingClusters) {
                if (cluster == null || cluster.length == 0) continue;

                PixelCluster.ClusterQuery standingQuery = new PixelCluster.ClusterQuery(
                    (int) trapType.getClusterDistance(),
                    trapType.getMinClusterSize(),
                    cluster
                );
                ClusterSearchResult standingResult = script.getPixelAnalyzer().findClusters(trapArea, standingQuery);
                if (standingResult != null && standingResult.getClusters() != null && !standingResult.getClusters().isEmpty()) {
                    hasVisibleTrap = true;
                    break;
                }
            }

            // If no standing trap found, check for collapsed trap pixels
            if (!hasVisibleTrap) {
                SearchablePixel[][] collapsedClusters = trapType.getCollapsedPixelClusters();
                for (SearchablePixel[] cluster : collapsedClusters) {
                    if (cluster == null || cluster.length == 0) continue;

                    PixelCluster.ClusterQuery collapsedQuery = new PixelCluster.ClusterQuery(
                        (int) trapType.getClusterDistance(),
                        trapType.getMinClusterSize(),
                        cluster
                    );
                    ClusterSearchResult collapsedResult = script.getPixelAnalyzer().findClusters(trapArea, collapsedQuery);
                    if (collapsedResult != null && collapsedResult.getClusters() != null && !collapsedResult.getClusters().isEmpty()) {
                        hasVisibleTrap = true;
                        break;
                    }
                }
            }

            // If no respawn circle AND no trap model pixels, this is a phantom trap
            if (!hasVisibleTrap) {
                ScriptLogger.info(script, "Detected phantom trap at " + pos + " (state: " + info.state() +
                                ") - no respawn circle or trap pixels found");
                phantomPositions.add(pos);
            }
        }

        // Remove all phantom traps
        for (WorldPosition phantomPos : phantomPositions) {
            removeTrap(phantomPos);
            ScriptLogger.info(script, "Removed phantom trap at " + phantomPos + " from tracking");
        }

        if (!phantomPositions.isEmpty()) {
            ScriptLogger.info(script, "Cleaned up " + phantomPositions.size() + " phantom trap(s) during drain mode");
        }
    }

    // ==================== FLAG-BASED API METHODS ====================

    /**
     * Get the trap with the highest priority flag.
     * For urgent/critical traps, uses pure priority-based selection.
     * For non-critical finished traps, applies distance-based selection.
     * @return Optional containing the highest priority trap, or empty if no traps with flags
     */
    public Optional<TrapSummary> getHighestPriorityTrap() {
        // First check for any urgent or critical traps (no distance logic)
        Optional<TrapSummary> urgentTrap = getUrgentOrCriticalTrap();
        if (urgentTrap.isPresent()) {
            return urgentTrap;
        }

        // Now apply distance-based logic only to non-critical finished traps
        return getNearestFinishedTrap();
    }

    /**
     * Gets any trap with urgent or critical priority flags.
     * This includes URGENT_COLLAPSED, CRITICAL_SUCCESS, NEEDS_REPOSITIONING, and NEEDS_INTERACTION.
     * Uses the original priority-based selection without any distance logic.
     * @return Optional containing the highest priority urgent/critical trap
     */
    private Optional<TrapSummary> getUrgentOrCriticalTrap() {
        return traps.values().stream()
            .filter(TrapInfo::isActionable)
            .map(TrapSummary::fromTrapInfo)
            .filter(TrapSummary::isActionable)
            .filter(summary -> {
                TrapFlag flag = summary.priorityFlag();
                // Include all high-priority flags and collapsed traps
                return flag == TrapFlag.URGENT_COLLAPSED ||
                       flag == TrapFlag.CRITICAL_SUCCESS ||
                       flag == TrapFlag.NEEDS_REPOSITIONING ||
                       flag == TrapFlag.NEEDS_INTERACTION; // All collapsed traps
            })
            .min((a, b) -> {
                // Use original priority comparison for urgent/critical traps
                return Integer.compare(a.priorityFlag().ordinal(), b.priorityFlag().ordinal());
            });
    }

    /**
     * Gets the nearest non-critical finished trap using distance-based selection.
     * Only applies to traps with READY_FOR_REMOVAL flag (finished states).
     * Prioritizes: GREEN > RED > distance > age > random selection.
     * @return Optional containing the best finished trap to handle
     */
    private Optional<TrapSummary> getNearestFinishedTrap() {
        WorldPosition playerPos = script.getWorldPosition();
        if (playerPos == null) {
            // Fall back to age-based selection if player position unavailable
            return traps.values().stream()
                .filter(TrapInfo::isActionable)
                .filter(trap -> trap.hasFlag(TrapFlag.READY_FOR_REMOVAL))
                .map(TrapSummary::fromTrapInfo)
                .min((a, b) -> Long.compare(a.fullInfo().lastUpdated(), b.fullInfo().lastUpdated()));
        }

        // Get all finished traps
        List<TrapSummary> finishedTraps = traps.values().stream()
            .filter(TrapInfo::isActionable)
            .filter(trap -> trap.hasFlag(TrapFlag.READY_FOR_REMOVAL))
            .map(TrapSummary::fromTrapInfo)
            .collect(Collectors.toList());

        if (finishedTraps.isEmpty()) {
            return Optional.empty();
        }

        // Sort with our custom comparator
        finishedTraps.sort((a, b) -> {
            TrapInfo trapA = a.fullInfo();
            TrapInfo trapB = b.fullInfo();

            // First priority (optional): GREEN (FINISHED_SUCCESS) over RED (FINISHED_FAILED)
            // Only apply this prioritization if distanceBasedPrioritization is FALSE
            if (!distanceBasedPrioritization) {
                boolean aIsGreen = trapA.state() == TrapState.FINISHED_SUCCESS;
                boolean bIsGreen = trapB.state() == TrapState.FINISHED_SUCCESS;

                if (aIsGreen && !bIsGreen) {
                    return -1; // A (green) comes first
                } else if (!aIsGreen && bIsGreen) {
                    return 1;  // B (green) comes first
                }
            }

            // Next priority: Distance from player (with epsilon for "equidistant")
            double distanceA = playerPos.distanceTo(trapA.position());
            double distanceB = playerPos.distanceTo(trapB.position());
            double distanceDiff = Math.abs(distanceA - distanceB);

            if (distanceDiff > 0.5) { // Not equidistant
                return Double.compare(distanceA, distanceB); // Closer trap first
            }

            // Third priority: Age (oldest trap first for equidistant traps)
            int ageComparison = Long.compare(trapA.lastUpdated(), trapB.lastUpdated());
            if (ageComparison != 0) {
                return ageComparison;
            }

            // Final tie-breaker: Random selection for identical cases
            return RandomUtils.uniformRandom(0, 1) == 0 ? -1 : 1;
        });

        // Return the best trap (first in sorted list)
        TrapSummary selected = finishedTraps.get(0);

        // Log the selection decision for debugging
        if (finishedTraps.size() > 1) {
            double selectedDistance = playerPos.distanceTo(selected.fullInfo().position());
            String selectedType = selected.fullInfo().state() == TrapState.FINISHED_SUCCESS ? "GREEN" : "RED";
            String priorityMode = distanceBasedPrioritization ? "distance-based" : "green-first";
            ScriptLogger.debug(script, "Selected " + selectedType + " trap at " + selected.position() +
                             " (distance: " + String.format("%.1f", selectedDistance) + ") from " +
                             finishedTraps.size() + " finished traps using " + priorityMode + " prioritization");
        }

        return Optional.of(selected);
    }

    /**
     * Set a flag on a trap at the specified position.
     * @param position The trap position
     * @param flag The flag to set
     * @return true if the trap was found and flag was set
     */
    public boolean setFlag(WorldPosition position, TrapFlag flag) {
        TrapInfo current = traps.get(position);
        if (current != null) {
            TrapInfo updated = current.withFlag(flag);
            traps.put(position, updated);
            ScriptLogger.debug(script, "Set flag " + flag + " on trap at " + position);
            return true;
        }
        return false;
    }

}
