package com.jork.script.jorkHunter.state;

import com.jork.script.jorkHunter.JorkHunter;
import com.jork.script.jorkHunter.trap.TrapType;
import com.jork.script.jorkHunter.trap.TrapStateHandlingMode;
import com.jork.script.jorkHunter.interaction.TrapVisibilityChecker;
import com.jork.script.jorkHunter.interaction.InteractionResult;
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
import com.osmb.api.input.MenuHook;
import com.osmb.api.input.MenuEntry;
import com.osmb.api.item.ItemID;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Thread-safe manager for tracking trap states and coordinating trap operations.
 * Eliminates race conditions through atomic operations and concurrent collections.
 */
public class TrapStateManager {
    
    private final JorkHunter script;
    private final TrapType trapType;
    private final TrapVisibilityChecker visibilityChecker;
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
        this.visibilityChecker = new TrapVisibilityChecker(script);
        this.distanceBasedPrioritization = script.isDistanceBasedPrioritization();
    }
    
    /**
     * Efficiently scans for respawn circle transitions and triggers targeted pixel analysis only when needed.
     * Only performs expensive operations when trap states actually change.
     */
    public void scanAndUpdateTrapStates() {
        try {
            ScriptLogger.debug(script, "=== TRAP STATE SCAN STARTING ===");
            
            // Note: We ALLOW scanning during trap laying to detect the respawn circle
            // The HuntTask will block actions until animation completes
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
            
            // CRITICAL: Check grace periods FIRST before discovering new traps
            // This prevents phantom traps from being counted alongside real new discoveries
            checkGracePeriods(currentRespawnStates);
            
            // Check for collapsed traps that need urgent attention (>90 seconds on ground)
            checkCollapsedTrapUrgency();
            
            // Detect and handle state transitions (new trap discovery happens here)
            handleStateTransitions(currentRespawnStates);
            
            // Update tracking for next cycle
            // CRITICAL: Don't replace the entire map - update entries selectively to preserve history
            // Update or add entries that currently have respawn circles
            for (Map.Entry<WorldPosition, PixelAnalyzer.RespawnCircle.Type> entry : currentRespawnStates.entrySet()) {
                previousRespawnStates.put(entry.getKey(), entry.getValue());
            }
            
            // Clean up entries that are no longer relevant
            // Create defensive copy to prevent modification during iteration in same poll cycle
            Set<Map.Entry<WorldPosition, PixelAnalyzer.RespawnCircle.Type>> entriesToRemove = new HashSet<>();
            for (Map.Entry<WorldPosition, PixelAnalyzer.RespawnCircle.Type> entry : previousRespawnStates.entrySet()) {
                WorldPosition pos = entry.getKey();
                TrapInfo trapInfo = traps.get(pos);
                boolean isCollapsed = trapInfo != null && trapInfo.state() == TrapState.COLLAPSED;
                
                // Remove if: not in current states AND (not tracked OR collapsed) AND not in grace period
                if (!currentRespawnStates.containsKey(pos) && 
                    (!traps.containsKey(pos) || isCollapsed) && 
                    !missingTrapsTimestamp.containsKey(pos)) {
                    entriesToRemove.add(entry);
                }
            }
            
            // Remove collected entries
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
            // Use string comparison to be more robust against enum changes
            String typeString = circleType.toString().toUpperCase();
            return switch (typeString) {
                case "YELLOW" -> TrapState.ACTIVE;
                case "GREEN", "RED" -> {
                    // Check the trap type's handling mode
                    if (trapType.getStateHandlingMode() == TrapStateHandlingMode.BINARY) {
                        yield TrapState.FINISHED; // Binary mode - treat both as FINISHED
                    } else {
                        // Ternary mode - distinguish between success and failed
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
            
            // Handle transitions
            if (previousType != currentType) {
                ScriptLogger.info(script, "STATE TRANSITION DETECTED at " + pos + ": " + previousType + " → " + currentType);
                handleStateTransition(pos, previousType, currentType);
            }
            
            // Update existing trap tracking
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
            
            // If the trap reappeared, cancel the grace period
            if (currentStates.containsKey(pos)) {
                ScriptLogger.debug(script, "Trap at " + pos + " reappeared - cancelling collapse detection");
                iterator.remove();
                trapGracePeriods.remove(pos); // Clean up the grace period
                continue;
            }
            
            // Check if grace period has elapsed using the per-trap random duration
            long missingTime = System.currentTimeMillis() - startTime;
            long gracePeriod = trapGracePeriods.getOrDefault(pos, 10000L); // Default 10s if missing
            if (missingTime >= gracePeriod) {
                ScriptLogger.info(script, "Grace period expired for trap at " + pos + " after " + missingTime + "ms (threshold: " + gracePeriod + "ms)");
                
                // Mark for verification since respawn circle is missing
                // The InteractionHandler will perform pixel detection and blind tap if needed
                markTrapAsCollapsed(pos);
                setFlag(pos, TrapFlag.PENDING_VERIFICATION);
                ScriptLogger.info(script, "Marked trap at " + pos + " as COLLAPSED with PENDING_VERIFICATION flag");
                
                // CRITICAL: Remove from previousRespawnStates to prevent handleStateTransitions 
                // from seeing stale YELLOW state and starting a new grace period
                previousRespawnStates.remove(pos);
                
                iterator.remove();
                trapGracePeriods.remove(pos); // Clean up the grace period
            }
        }
    }
    
    /**
     * Handles a specific state transition for a trap
     */
    private void handleStateTransition(WorldPosition pos, PixelAnalyzer.RespawnCircle.Type previous, PixelAnalyzer.RespawnCircle.Type current) {
        // YELLOW → GREEN/RED: Trap is ready to interact
        if (isYellow(previous) && (isGreen(current) || isRed(current))) {
            ScriptLogger.info(script, "Trap at " + pos + " ready for interaction (" + previous + " → " + current + ")");
            // Set flag to indicate trap is ready for removal
            setFlag(pos, TrapFlag.READY_FOR_REMOVAL);
            // Clear any pending collapse detection since trap is now actionable
            missingTrapsTimestamp.remove(pos);
            trapGracePeriods.remove(pos);
            
            // Track metrics for successful vs failed catches
            if (isGreen(current)) {
                script.onTrapSuccess();
                // Generate a random critical threshold for this successful trap (25-35 seconds, weighted towards 35)
                long criticalThreshold = RandomUtils.weightedRandom(25000, 35000); // 25-35 seconds in milliseconds
                trapCriticalThresholds.put(pos, criticalThreshold);
                ScriptLogger.debug(script, "Trap at " + pos + " will become critical after " + 
                                 (criticalThreshold / 1000) + " seconds");
            } else if (isRed(current)) {
                script.onTrapFailed();
            }
        }
        // Any visible state → null: Respawn circle disappeared (likely collapsed or animation)
        else if (previous != null && current == null) {
            // NOTE: We do NOT mark for repositioning here anymore!
            // Repositioning is handled by HuntTask.validateAllTrapTilesVisible()
            // A missing respawn circle often means the trap collapsed, which is normal
            
            // GRACE PERIOD LOGIC - start grace period if not already active
            // CRITICAL: Also check if trap is already marked as collapsed to prevent double grace periods
            TrapInfo existingTrap = traps.get(pos);
            boolean alreadyCollapsed = existingTrap != null && existingTrap.state() == TrapState.COLLAPSED;
            
            if (!missingTrapsTimestamp.containsKey(pos) && !alreadyCollapsed) {
                missingTrapsTimestamp.put(pos, System.currentTimeMillis());
                // Generate grace period based on trap type and previous state
                // For bird snares: 2-4s for collapses (avoids false positives while being responsive)
                // For animations: 6-10s to handle state transition animations
                long gracePeriod;
                if (trapType == TrapType.BIRD_SNARE && isYellow(previous)) {
                    // Active trap disappearing - use 2-4s to avoid false positives
                    gracePeriod = RandomUtils.uniformRandom(2151, 4216);
                } else {
                    // Finished traps or animation transitions might take longer
                    gracePeriod = RandomUtils.uniformRandom(2251, 4117);
                }
                trapGracePeriods.put(pos, gracePeriod);
                String stateDesc = previous.toString().toUpperCase();
                ScriptLogger.info(script, "GRACE PERIOD STARTED: Trap at " + pos + " disappeared from " + stateDesc + " state, starting " + gracePeriod + "ms grace period");
            } else if (alreadyCollapsed) {
                ScriptLogger.debug(script, "Trap at " + pos + " already marked as COLLAPSED - skipping grace period");
            } else {
                ScriptLogger.debug(script, "Grace period already active for trap at " + pos);
                
                // DEFENSIVE SCAN ONLY if grace period has been active for a while (not immediate transitions)
                long graceStartTime = missingTrapsTimestamp.get(pos);
                long graceDuration = System.currentTimeMillis() - graceStartTime;
                
                // Only mark for verification if grace period has been active for at least 2 seconds
                if (graceDuration > 2000) {
                    // Use the appropriate finished state based on handling mode
                    TrapState finishedState = (trapType.getStateHandlingMode() == TrapStateHandlingMode.BINARY) 
                        ? TrapState.FINISHED 
                        : TrapState.FINISHED_SUCCESS; // Default to success in ternary mode when we can't determine
                    
                    ScriptLogger.info(script, "Marking trap at " + pos + " as " + finishedState + " with verification flag after " + graceDuration + "ms grace period");
                    updateOrCreateTrap(pos, finishedState);
                    setFlag(pos, TrapFlag.PENDING_VERIFICATION);
                    // Clear the grace period since trap is now marked
                    missingTrapsTimestamp.remove(pos);
                    trapGracePeriods.remove(pos);
                }
            }
        }
        // Trap reappeared - cancel collapse detection  
        else if (previous == null && current != null && missingTrapsTimestamp.containsKey(pos)) {
            ScriptLogger.debug(script, "Trap at " + pos + " reappeared as " + current + " - cancelling collapse detection");
            missingTrapsTimestamp.remove(pos);
            trapGracePeriods.remove(pos);
        }
        // New trap discovered
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
                // Create new trap with appropriate flags based on state
                TrapFlags flags = new TrapFlags();
                switch (newState) {
                    case FINISHED, FINISHED_SUCCESS, FINISHED_FAILED -> flags.addFlag(TrapFlag.READY_FOR_REMOVAL);
                    case COLLAPSED -> flags.addFlag(TrapFlag.NEEDS_INTERACTION);
                    case LAYING -> flags.addFlag(TrapFlag.LAYING_IN_PROGRESS);
                    case UNKNOWN -> flags.addFlag(TrapFlag.PENDING_VERIFICATION);
                    default -> { /* ACTIVE - no special flags */ }
                }
                long now = System.currentTimeMillis();
                return new TrapInfo(pos, newState, trapType, flags, now, now, now);
            } else if (currentInfo.state() != newState) {
                ScriptLogger.info(script, "Trap at " + pos + " changed from " + currentInfo.state() + 
                    " (after " + currentInfo.getFormattedStateTime() + ") to " + newState);
                // Create updated trap with new flags
                TrapInfo updated = currentInfo.withState(newState).withClearedFlags();
                switch (newState) {
                    case FINISHED, FINISHED_SUCCESS, FINISHED_FAILED -> updated = updated.withFlag(TrapFlag.READY_FOR_REMOVAL);
                    case COLLAPSED -> updated = updated.withFlag(TrapFlag.NEEDS_INTERACTION);
                    case LAYING -> updated = updated.withFlag(TrapFlag.LAYING_IN_PROGRESS);
                    case UNKNOWN -> updated = updated.withFlag(TrapFlag.PENDING_VERIFICATION);
                    default -> { /* ACTIVE - no special flags */ }
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
            
            // Check collapsed traps for urgency
            if (trapInfo.state() == TrapState.COLLAPSED) {
                long collapsedDuration = currentTime - trapInfo.lastUpdated();
                
                // If collapsed for more than 90 seconds, mark as urgent (despawn at ~180 seconds)
                if (collapsedDuration > 90000 && !trapInfo.flags().hasFlag(TrapFlag.URGENT_COLLAPSED)) {
                    ScriptLogger.warning(script, "Collapsed trap at " + pos + " has been on ground for " + 
                        (collapsedDuration / 1000) + " seconds - marking as URGENT");
                    setFlag(pos, TrapFlag.URGENT_COLLAPSED);
                }
                
                // Log critical warning if approaching despawn time
                if (collapsedDuration > 150000) {
                    ScriptLogger.error(script, "CRITICAL: Collapsed trap at " + pos + " will despawn soon! (" + 
                        (collapsedDuration / 1000) + " seconds on ground)");
                }
            }
            // Check successful traps for critical timing (risk of collapsing)
            else if (trapInfo.state() == TrapState.FINISHED_SUCCESS || 
                     (trapInfo.state() == TrapState.FINISHED && trapType.getStateHandlingMode() == TrapStateHandlingMode.BINARY)) {
                long successDuration = currentTime - trapInfo.lastUpdated();
                
                // Get the randomized critical threshold for this trap (default to 30s if not found)
                long criticalThreshold = trapCriticalThresholds.getOrDefault(pos, 30000L);
                
                // If successful for more than the randomized threshold, mark as critical (risk of collapsing)
                if (successDuration > criticalThreshold && !trapInfo.flags().hasFlag(TrapFlag.CRITICAL_SUCCESS)) {
                    ScriptLogger.warning(script, "Successful trap at " + pos + " has been waiting for " + 
                        (successDuration / 1000) + " seconds (threshold: " + (criticalThreshold / 1000) + 
                        "s) - marking as CRITICAL (collapse risk)");
                    setFlag(pos, TrapFlag.CRITICAL_SUCCESS);
                }
                
                // Log warning if approaching collapse time (usually ~60 seconds for successful traps)
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
        
        currentlyLayingPosition = position; // Track which position is being laid
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
            // Note: isLayingTrap flag should be cleared by HuntTask after confirmation
        } else {
            ScriptLogger.warning(script, "Failed to lay trap at " + position + " - removing from tracking");
            traps.remove(position);
            clearLayingFlag(); // Clear flag on failure
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
            // Update trap state to RESETTING
            TrapInfo existing = traps.get(position);
            if (existing != null) {
                // Preserve existing trap info but change state to RESETTING
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
            // After successful reset, trap transitions to ACTIVE state
            TrapInfo existing = traps.get(position);
            if (existing != null) {
                TrapInfo activeTrap = existing.withState(TrapState.ACTIVE).withClearedFlags();
                traps.put(position, activeTrap);
                ScriptLogger.info(script, "Reset trap complete at " + position + " - trap is now ACTIVE");
            } else {
                // Create new trap info if not found (shouldn't happen normally)
                long now = System.currentTimeMillis();
                TrapInfo activeTrap = new TrapInfo(position, TrapState.ACTIVE, trapType, 
                    new TrapFlags(), now, now, now);
                traps.put(position, activeTrap);
                ScriptLogger.info(script, "Reset trap complete at " + position + " - trap is now ACTIVE (new)");
            }
        } else {
            // Failed reset - remove trap from tracking to force re-scan
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
            // Clean up all associated tracking data
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
     * Gets all traps that are actionable (finished or collapsed).
     * Prioritizes collapsed traps first (time-sensitive), then occluded traps.
     */
    public List<TrapInfo> getActionableTraps() {
        return traps.values().stream()
                   .filter(TrapInfo::isActionable)
                   .sorted((trap1, trap2) -> {
                       // FIRST PRIORITY: COLLAPSED state (absolute highest - time-sensitive)
                       boolean trap1Collapsed = trap1.state() == TrapState.COLLAPSED;
                       boolean trap2Collapsed = trap2.state() == TrapState.COLLAPSED;
                       
                       if (trap1Collapsed && !trap2Collapsed) {
                           return -1; // trap1 (collapsed) comes first
                       } else if (!trap1Collapsed && trap2Collapsed) {
                           return 1;  // trap2 (collapsed) comes first
                       }
                       
                       // SECOND PRIORITY: Visibility (occluded vs visible for finished traps)
                       boolean trap1Visible = visibilityChecker.isTrapVisible(trap1.position());
                       boolean trap2Visible = visibilityChecker.isTrapVisible(trap2.position());
                       
                       if (!trap1Visible && trap2Visible) {
                           return -1; // trap1 (occluded) comes first
                       } else if (trap1Visible && !trap2Visible) {
                           return 1;  // trap2 (occluded) comes first
                       }
                       
                       // THIRD PRIORITY: Last updated time (older first)
                       return Long.compare(trap1.lastUpdated(), trap2.lastUpdated());
                   })
                   .collect(Collectors.toList());
    }
    
    /**
     * Gets all traps in active state
     */
    public List<TrapInfo> getActiveTraps() {
        return traps.values().stream()
                   .filter(info -> info.state() == TrapState.ACTIVE)
                   .collect(Collectors.toList());
    }
    
    /**
     * Thread-safe count operations
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
     * Checks if any traps are currently visible on screen.
     * Used to determine if we need to walk back to hunting area during drain mode.
     */
    public boolean hasVisibleTraps() {
        for (WorldPosition pos : traps.keySet()) {
            if (isTrapOnScreen(pos)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Gets a list of all trap positions that are currently off-screen.
     * This is used to proactively move to bring traps into view before they change state.
     * @return List of off-screen trap positions
     */
    public List<WorldPosition> getOffScreenTrapPositions() {
        List<WorldPosition> offScreenTraps = new ArrayList<>();
        for (WorldPosition pos : traps.keySet()) {
            if (!isTrapOnScreen(pos)) {
                offScreenTraps.add(pos);
            }
        }
        return offScreenTraps;
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
            // Add EXPEDITE_COLLECTION flag to all traps
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
     * Gets the TrapInfo for a specific position (for debug visualization)
     * @param position The position to get trap info for
     * @return TrapInfo at this position, or null if no trap exists there
     */
    public TrapInfo getTrapInfo(WorldPosition position) {
        return traps.get(position);
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
     * Gets the visibility checker for use by other components.
     * @return The TrapVisibilityChecker instance
     */
    public TrapVisibilityChecker getVisibilityChecker() {
        return visibilityChecker;
    }
    
    /**
     * Checks if a trap position is truly off-screen (tile not in scene).
     * This is used only for suspension logic - does NOT consider UI occlusion.
     * @param pos The trap position to check
     * @return true if the trap tile is in scene, false if truly off-screen
     */
    private boolean isTrapOnScreen(WorldPosition pos) {
        if (pos == null) {
            return false;
        }
        
        RSTile trapTile = script.getSceneManager().getTile(pos);
        if (trapTile == null) {
            return false; // Tile not in scene (truly off-screen)
        }
        
        // Basic screen bounds check - this is for off-screen detection only
        return trapTile.isOnGameScreen();
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
     * Gets the first trap that needs repositioning.
     * @return WorldPosition of trap needing repositioning, or null if none
     */
    public WorldPosition getFirstTrapNeedingRepositioning() {
        return trapsNeedingRepositioning.isEmpty() ? null : trapsNeedingRepositioning.iterator().next();
    }
    
    /**
     * Clears repositioning flag for a trap (when we've moved to see it better).
     * @param pos The trap position to clear repositioning for
     */
    public void clearRepositioningFlag(WorldPosition pos) {
        trapsNeedingRepositioning.remove(pos);
        ScriptLogger.debug(script, "Cleared repositioning flag for trap at " + pos);
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
     * Get all traps that have a specific flag set.
     * @param flag The flag to filter by
     * @return List of TrapInfo for traps with the specified flag
     */
    public List<TrapInfo> getTrapsWithFlag(TrapFlag flag) {
        return traps.values().stream()
            .filter(trap -> trap.hasFlag(flag))
            .collect(Collectors.toList());
    }
    
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
    
    /**
     * Clear a flag from a trap at the specified position.
     * @param position The trap position
     * @param flag The flag to clear
     * @return true if the trap was found and flag was cleared
     */
    public boolean clearFlag(WorldPosition position, TrapFlag flag) {
        TrapInfo current = traps.get(position);
        if (current != null) {
            TrapInfo updated = current.withoutFlag(flag);
            traps.put(position, updated);
            ScriptLogger.debug(script, "Cleared flag " + flag + " from trap at " + position);
            return true;
        }
        return false;
    }
    
    /**
     * Update trap state based on an interaction result.
     * @param result The interaction result
     */
    public void updateFromInteractionResult(InteractionResult result) {
        if (result == null || result.position() == null) {
            return;
        }
        
        WorldPosition position = result.position();
        
        switch (result.type()) {
            case TRAP_LAID -> {
                // Trap was successfully laid
                completeTrapLaying(position, true);
            }
            case TRAP_CHECKED -> {
                // Trap was checked and picked up
                removeTrap(position);
                ScriptLogger.info(script, "Trap checked and removed at " + position);
            }
            case TRAP_RESET -> {
                // Collapsed trap was reset
                clearFlag(position, TrapFlag.NEEDS_INTERACTION);
                setFlag(position, TrapFlag.PENDING_VERIFICATION);
                ScriptLogger.info(script, "Trap reset at " + position);
            }
            case TRAP_REMOVED -> {
                // Trap was removed
                removeTrap(position);
                ScriptLogger.info(script, "Trap removed at " + position);
            }
            case MOVEMENT_REQUIRED -> {
                // Mark trap as needing repositioning
                setFlag(position, TrapFlag.NEEDS_REPOSITIONING);
            }
            case VERIFICATION_NEEDED -> {
                // Mark trap as needing verification
                setFlag(position, TrapFlag.PENDING_VERIFICATION);
            }
            case FAILED -> {
                // Interaction failed - may need to verify or remove
                ScriptLogger.warning(script, "Interaction failed at " + position);
            }
        }
    }
    
    /**
     * Get all traps that need immediate attention (high priority flags).
     * @return List of trap summaries sorted by priority
     */
    public List<TrapSummary> getPrioritizedTraps() {
        return traps.values().stream()
            .filter(TrapInfo::isActionable)
            .map(TrapSummary::fromTrapInfo)
            .filter(TrapSummary::isActionable)
            .sorted((a, b) -> {
                if (a.priorityFlag() == null && b.priorityFlag() == null) return 0;
                if (a.priorityFlag() == null) return 1;
                if (b.priorityFlag() == null) return -1;
                return Integer.compare(a.priorityFlag().ordinal(), b.priorityFlag().ordinal());
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Clear all flags from all traps.
     * Used when repositioning or resetting state.
     */
    public void clearAllFlags() {
        traps.replaceAll((pos, info) -> info.withClearedFlags());
        ScriptLogger.debug(script, "Cleared all flags from all traps");
    }
    
    /**
     * Update trap flags based on state transitions.
     * This method determines what flags should be set based on state changes.
     */
    private void updateFlagsForState(WorldPosition position, TrapState oldState, TrapState newState) {
        TrapInfo current = traps.get(position);
        if (current == null) return;
        
        // Clear existing action flags
        TrapInfo updated = current
            .withoutFlag(TrapFlag.NEEDS_INTERACTION)
            .withoutFlag(TrapFlag.READY_FOR_REMOVAL)
            .withoutFlag(TrapFlag.LAYING_IN_PROGRESS);
        
        // Set new flags based on state
        switch (newState) {
            case FINISHED, FINISHED_SUCCESS, FINISHED_FAILED -> {
                updated = updated.withFlag(TrapFlag.READY_FOR_REMOVAL);
            }
            case COLLAPSED -> {
                updated = updated.withFlag(TrapFlag.NEEDS_INTERACTION);
            }
            case LAYING -> {
                updated = updated.withFlag(TrapFlag.LAYING_IN_PROGRESS);
            }
            case UNKNOWN -> {
                updated = updated.withFlag(TrapFlag.PENDING_VERIFICATION);
            }
            default -> {
                // ACTIVE state - no special flags needed
            }
        }
        
        traps.put(position, updated);
    }
}
