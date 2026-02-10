package com.jork.script.jorkHunter.tasks;

//Jork Modules
import com.jork.script.jorkHunter.JorkHunter;
import com.jork.script.jorkHunter.trap.TrapType;
import com.jork.script.jorkHunter.tasks.base.AbstractHuntingTask;
import com.jork.script.jorkHunter.utils.placement.TrapPlacementStrategy;
import com.jork.script.jorkHunter.utils.placement.NoCardinalStrategy;
import com.jork.script.jorkHunter.interaction.TrapVisibilityChecker;
import com.jork.script.jorkHunter.interaction.InteractionResult;
import com.jork.script.jorkHunter.state.TrapInfo;
import com.jork.script.jorkHunter.state.TrapState;
import com.jork.script.jorkHunter.state.TrapFlag;
import com.jork.script.jorkHunter.state.TrapSummary;
import com.jork.utils.ScriptLogger;
import com.jork.utils.Navigation;

//OSMB Modules
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.walker.pathing.CollisionManager;
import com.osmb.api.shape.Polygon;
import com.osmb.api.item.ItemGroupResult;
import com.osmb.api.item.ItemID;
import com.osmb.api.item.ItemSearchResult;
import com.osmb.api.utils.RandomUtils;
import com.osmb.api.shape.Rectangle;
import com.osmb.api.scene.RSTile;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.visual.PixelCluster.ClusterSearchResult;
import com.osmb.api.visual.PixelCluster;
import com.osmb.api.visual.SearchablePixel;
import com.osmb.api.visual.PixelAnalyzer;
import com.osmb.api.input.MenuEntry;

//Java Modules
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

//OSMB Walker Config
import com.osmb.api.walker.WalkConfig;

/**
 * Generic trap hunting task implementation for all trap types.
 * Handles both bird snares and box traps (chinchompas).
 */
public class TrapTask extends AbstractHuntingTask {

    // Track player position when starting trap laying (simpler than tracking trap position)
    private WorldPosition trapLayingStartPosition = null;
    private long trapLayingStartTime = 0;

    // Track when we've committed to laying a trap (started walking to position)
    private boolean committedToLayingTrap = false;
    private WorldPosition committedTrapPosition = null;

    public TrapTask(JorkHunter script, TrapType trapType, int maxTraps, List<RectangleArea> huntingZones) {
        this(script, trapType, maxTraps, huntingZones, new NoCardinalStrategy());
    }

    public TrapTask(JorkHunter script, TrapType trapType, int maxTraps, List<RectangleArea> huntingZones, TrapPlacementStrategy placementStrategy) {
        super(script, trapType, maxTraps, huntingZones, placementStrategy);
    }

    @Override
    protected String getTaskName() {
        return "TrapTask";
    }

    @Override
    public void resetExecutionState() {
        committedToLayingTrap = false;
        committedTrapPosition = null;
        trapLayingStartPosition = null;
        trapLayingStartTime = 0;
    }

    @Override
    public boolean canExecute() {
        return true;
    }

    /**
     * Triggers expedited collection of all traps for quick break preparation.
     * This will mark all traps with EXPEDITE_COLLECTION flag, causing them to be
     * immediately dismantled/collected regardless of state.
     */
    public void expediteTrapsForBreak() {
        ScriptLogger.info(script, "=== EXPEDITED TRAP COLLECTION ACTIVATED ===");
        ScriptLogger.info(script, "Forcing collection of all traps for break preparation");

        trapManager.markAllTrapsForExpediteCollection();
    }

    @Override
    public int execute() {
        // Handle in-progress lay/reset actions first.
        if (trapManager.isCurrentlyLayingTrap() || trapManager.isCurrentlyResettingTrap()) {
            WorldPosition trapPos = trapManager.getCurrentlyLayingPosition();
            boolean isResetting = false;
            if (trapPos == null) {
                trapPos = trapManager.getCurrentlyResettingPosition();
                isResetting = true;
            }

            if (trapPos == null) {
                ScriptLogger.warning(script, "No trap position during laying/resetting - clearing flags");
                trapManager.clearLayingFlag();
                trapManager.clearResetFlag();
                return RandomUtils.weightedRandom(500, 800);
            }

            final WorldPosition finalTrapPos = trapPos;
            final boolean finalIsResetting = isResetting;

            ScriptLogger.debug(script, "Waiting for respawn circle to appear at " + finalTrapPos);

            boolean circleAppeared = script.submitHumanTask(() ->
                detectRespawnCircleAtPosition(finalTrapPos), 5000);

            if (circleAppeared) {
                long timeTaken = trapLayingStartTime > 0 ?
                    System.currentTimeMillis() - trapLayingStartTime : 0;

                if (finalIsResetting) {
                    ScriptLogger.debug(script, "Trap reset complete - yellow circle detected at " + finalTrapPos +
                                     " (time: " + timeTaken + "ms)");
                    trapManager.completeResetTrap(finalTrapPos, true);
                    trapManager.clearResetFlag();
                } else {
                    ScriptLogger.debug(script, "Trap laying complete - yellow circle detected at " + finalTrapPos +
                                     " (time: " + timeTaken + "ms)");
                    trapManager.completeTrapLaying(finalTrapPos, true);
                    trapManager.clearLayingFlag();
                }

                trapManager.scanAndUpdateTrapStates();
                trapLayingStartPosition = null;
                trapLayingStartTime = 0;

                return RandomUtils.weightedRandom(400, 700);
            } else {
                ScriptLogger.warning(script, "Respawn circle detection timed out at " + finalTrapPos);

                if (trapLayingStartPosition != null) {
                    WorldPosition currentPos = script.getWorldPosition();
                    if (currentPos != null) {
                        double distance = currentPos.distanceTo(trapLayingStartPosition);
                        if (distance >= 1.0) {
                            ScriptLogger.debug(script, "Using movement fallback - player moved " + distance + " tiles");

                            if (finalIsResetting) {
                                trapManager.completeResetTrap(finalTrapPos, true);
                                trapManager.clearResetFlag();
                            } else {
                                trapManager.completeTrapLaying(finalTrapPos, true);
                                trapManager.clearLayingFlag();
                            }

                            trapManager.scanAndUpdateTrapStates();
                            trapLayingStartPosition = null;
                            trapLayingStartTime = 0;

                            return RandomUtils.weightedRandom(400, 700);
                        }
                    }
                }

                if (finalIsResetting) {
                    trapManager.completeResetTrap(finalTrapPos, false);
                    trapManager.clearResetFlag();
                } else {
                    trapManager.completeTrapLaying(finalTrapPos, false);
                    trapManager.clearLayingFlag();
                }
                trapLayingStartPosition = null;
                trapLayingStartTime = 0;

                return RandomUtils.weightedRandom(500, 800);
            }
        }

        // Clear trap-laying commitment when drain mode starts.
        if (script.isDrainingForBreak() && committedToLayingTrap) {
            ScriptLogger.info(script, "Drain mode activated - clearing trap laying commitment at " + committedTrapPosition);
            committedToLayingTrap = false;
            committedTrapPosition = null;
        }

        // If committed to laying, finish that movement first.
        if (committedToLayingTrap && committedTrapPosition != null) {
            if (trapManager.isCurrentlyLayingTrap()) {
                ScriptLogger.debug(script, "Trap laying has started, clearing commitment");
                committedToLayingTrap = false;
                committedTrapPosition = null;
            } else {
                WorldPosition currentPos = script.getWorldPosition();
                if (currentPos != null) {
                    double distance = currentPos.distanceTo(committedTrapPosition);
                    if (distance > 10) {
                        ScriptLogger.warning(script, "Moved too far from committed trap position, clearing commitment");
                        committedToLayingTrap = false;
                        committedTrapPosition = null;
                    } else if (distance > 1.0) {
                        ScriptLogger.debug(script, "Still moving to committed trap position at " + committedTrapPosition + " (distance: " + distance + ")");
                        return RandomUtils.weightedRandom(200, 400);
                    } else {
                        ScriptLogger.debug(script, "At committed trap position, continuing to lay trap");
                    }
                }
            }
        }

        trapManager.scanAndUpdateTrapStates();

        // Skip trap handling while committed to laying.
        boolean skipTrapHandling = committedToLayingTrap && committedTrapPosition != null;

        Optional<TrapSummary> priorityTrap = trapManager.getHighestPriorityTrap();
        if (!skipTrapHandling && priorityTrap.isPresent()) {
            TrapSummary summary = priorityTrap.get();
            TrapInfo trapToHandle = summary.fullInfo();

            if (trapToHandle != null) {
                TrapFlag highestFlag = summary.priorityFlag();

                // Treat recently-finished traps as low urgency while filling trap count.
                boolean isFreshFinished = (highestFlag == TrapFlag.READY_FOR_REMOVAL ||
                                          highestFlag == TrapFlag.NEEDS_INTERACTION);

                if (isFreshFinished && !script.isDrainingForBreak() && !trapManager.isCurrentlyLayingTrap()
                    && !committedToLayingTrap) {
                    long timeInState = trapToHandle.getTimeInCurrentState();
                    int totalTraps = trapManager.getTotalCount();

                    long freshnessThreshold = RandomUtils.uniformRandom(14000, 34000);

                    boolean isUrgent = (highestFlag == TrapFlag.NEEDS_INTERACTION && timeInState > 60000) ||
                                      (highestFlag == TrapFlag.URGENT_COLLAPSED) ||
                                      (highestFlag == TrapFlag.CRITICAL_SUCCESS);

                    if (!isUrgent && timeInState < freshnessThreshold && totalTraps < maxTraps) {
                        String trapType = (highestFlag == TrapFlag.READY_FOR_REMOVAL) ? "Finished" : "Collapsed";
                        ScriptLogger.info(script, trapType + " trap at " + summary.position() +
                            " is fresh (" + trapToHandle.getFormattedStateTime() + " < " + (freshnessThreshold/1000) + "s). " +
                            "Prioritizing new trap laying (" + (totalTraps + 1) + "/" + maxTraps + ")");

                        layNewTrap();
                        return RandomUtils.weightedRandom(100, 200);
                    }
                }

                ScriptLogger.info(script, "Handling trap at " + summary.position() +
                    " with priority flag: " + highestFlag + " (state: " + trapToHandle.state() +
                    ", time in state: " + trapToHandle.getFormattedStateTime() + ")");

                // When possible, lay from the current tile before walking away.
                boolean isHighPriority = highestFlag == TrapFlag.NEEDS_REPOSITIONING ||
                                        highestFlag == TrapFlag.READY_FOR_REMOVAL;
                int totalTraps = trapManager.getTotalCount();

                if (!isHighPriority && !script.isDrainingForBreak() && !trapManager.isCurrentlyLayingTrap() && totalTraps < maxTraps) {
                    WorldPosition currentPos = script.getWorldPosition();
                    if (currentPos != null) {
                        boolean positionValid = script.submitHumanTask(() -> {
                            Set<WorldPosition> existingTraps = new HashSet<>(trapManager.getLaidTrapPositions());
                            return placementStrategy.isValidPosition(currentPos, existingTraps);
                        }, RandomUtils.weightedRandom(300, 600));

                        if (positionValid) {
                            ScriptLogger.info(script, "Current position is valid for new trap (" + (totalTraps + 1) + "/" + maxTraps + "). Laying trap before handling flagged trap at " + trapToHandle.position());
                            layNewTrap();
                            return RandomUtils.weightedRandom(100, 200);
                        }
                    }
                }

                handleTrap(trapToHandle);
                return RandomUtils.uniformRandom(800, 1400);
            }
        }

        // Validate tile visibility before laying.
        boolean tilesVisible = validateAllTrapTilesVisible();

        if (!tilesVisible || trapManager.hasTrapsNeedingRepositioning()) {
            ScriptLogger.info(script, "Some trap tiles are not visible or occluded - repositioning for better view");

            WorldPosition rightEdgePos = getRightEdgePosition();
            if (rightEdgePos != null) {
                WorldPosition currentPos = script.getWorldPosition();
                if (currentPos != null && currentPos.distanceTo(rightEdgePos) > 2.0) {
                    ScriptLogger.navigation(script, "Moving to right edge at " + rightEdgePos + " to restore trap visibility");
                    script.getWalker().walkTo(rightEdgePos, walkConfigApprox);

                    boolean reached = script.submitTask(() -> {
                        WorldPosition newPos = script.getWorldPosition();
                        return newPos != null && newPos.distanceTo(rightEdgePos) <= 2.0;
                    }, 8000);

                    if (reached) {
                        trapManager.clearAllRepositioningFlags();
                        if (validateAllTrapTilesVisible()) {
                            ScriptLogger.info(script, "All trap tiles visible after repositioning to right edge");
                        } else {
                            ScriptLogger.warning(script, "Some trap tiles still not visible after repositioning");
                        }
                    }

                    return RandomUtils.uniformRandom(800, 1200);
                }
            }
        }

        // While draining, handle existing traps without placing new ones.
        if (script.isDrainingForBreak()) {
            int totalTraps = trapManager.getTotalCount();
            ScriptLogger.info(script, "Draining for break/hop. " + totalTraps + " traps remaining. Not laying new traps.");

            if (totalTraps > 0) {
                trapManager.verifyPhantomTraps();

                int newTotalTraps = trapManager.getTotalCount();
                if (newTotalTraps != totalTraps) {
                    ScriptLogger.info(script, "Phantom trap cleanup: " + totalTraps + " -> " + newTotalTraps + " traps");
                }

                if (newTotalTraps > 0) {
                    List<WorldPosition> remainingPositions = trapManager.getLaidTrapPositions();
                    ScriptLogger.debug(script, "Remaining trap positions during drain: " + remainingPositions);
                }
            }
        } else if (trapManager.isCurrentlyLayingTrap()) {
            ScriptLogger.debug(script, "Currently laying a trap. Waiting for completion.");
            return RandomUtils.weightedRandom(300, 500);
        } else {
            int totalTraps = trapManager.getTotalCount();
            if (totalTraps < maxTraps || skipTrapHandling) {
                if (skipTrapHandling) {
                    ScriptLogger.info(script, "Committed to laying trap at " + committedTrapPosition + ". Proceeding with trap laying.");
                } else {
                    ScriptLogger.info(script, "Trap limit not reached (" + totalTraps + "/" + maxTraps + "). Laying new trap.");
                }
                layNewTrap();
                return RandomUtils.weightedRandom(100, 200);
            }
        }

        int currentTrapCount = trapManager.getTotalCount();
        ScriptLogger.debug(script, "All " + currentTrapCount + "/" + maxTraps + " traps are set. Waiting...");
        return 2400;
    }



    private void handleTrap(TrapInfo trapInfo) {
        WorldPosition trapPos = trapInfo.position();
        TrapState trapState = trapInfo.state();

        ScriptLogger.info(script, "Handling " + trapState + " trap at " + trapPos);

        int initialTrapCount = getTrapCountInInventory();
        ScriptLogger.debug(script, "Initial trap count before handling: " + initialTrapCount);

        if (!interactionHandler.canInteract(trapPos)) {
            handleTrapRepositioning(trapPos);
            return;
        }

        InteractionResult result = interactionHandler.interact(trapInfo);

        switch (result.type()) {
            case TRAP_CHECKED, TRAP_RESET, TRAP_REMOVED -> {
                boolean inventoryChanged = script.submitHumanTask(() -> {
                    int currentCount = getTrapCountInInventory();
                    return currentCount != initialTrapCount;
                }, script.random(2500, 3500));

                if (inventoryChanged) {
                    int finalCount = getTrapCountInInventory();
                    ScriptLogger.actionSuccess(script, "Trap interaction confirmed - inventory changed from " +
                        initialTrapCount + " to " + finalCount);

                    if (result.type() == InteractionResult.InteractionType.TRAP_CHECKED) {
                        trapManager.removeTrap(trapPos);
                    } else if (result.type() == InteractionResult.InteractionType.TRAP_RESET) {
                        trapManager.removeTrap(trapPos);
                    }
                } else {
                    ScriptLogger.warning(script, "Interaction reported success but inventory unchanged");
                    trapManager.removeTrap(trapPos);
                }
            }

            case TRAP_LAID -> {
                ScriptLogger.info(script, "Laying collapsed trap at " + trapPos);

                if (!trapManager.startLayingTrap(trapPos)) {
                    ScriptLogger.warning(script, "Could not start laying trap - another trap is being laid");
                    return;
                }

                WorldPosition layStartPos = script.getWorldPosition();
                if (layStartPos != null) {
                    trapLayingStartPosition = layStartPos;
                    trapLayingStartTime = System.currentTimeMillis();

                    ScriptLogger.debug(script, "Starting trap lay on collapsed trap from position: " + layStartPos);
                }
            }

            case TRAP_RESET_INITIATED -> {
                ScriptLogger.info(script, "Reset action initiated on trap at " + trapPos);

                trapManager.startResettingTrap(trapPos);

                WorldPosition resetStartPos = script.getWorldPosition();
                if (resetStartPos != null) {
                    trapLayingStartPosition = resetStartPos;
                    trapLayingStartTime = System.currentTimeMillis();

                    ScriptLogger.debug(script, "Starting reset from position: " + resetStartPos);
                    script.submitTask(() -> {
                        WorldPosition currentPos = script.getWorldPosition();
                        return currentPos != null && currentPos.equals(trapPos);
                    }, 2000);

                    script.sleep(RandomUtils.weightedRandom(200, 400));
                }
            }

            case MOVEMENT_REQUIRED -> {
                ScriptLogger.navigation(script, "Movement required to interact with trap at " + trapPos);
                script.getWalker().walkTo(trapPos, walkConfigExact);
            }

            case VERIFICATION_NEEDED -> {
                ScriptLogger.debug(script, "Trap state uncertain, will re-scan next cycle");
            }

            case FAILED -> {
                ScriptLogger.warning(script, "Failed to interact with trap at " + trapPos + ": " + result.message());
                InteractionResult verifyResult = interactionHandler.verifyTrapState(trapPos);
                if (verifyResult.success()) {
                    ScriptLogger.info(script, "Blind tap verification succeeded - waiting for inventory change");

                    boolean inventoryChanged = script.submitHumanTask(() -> {
                        int currentCount = getTrapCountInInventory();
                        return currentCount != initialTrapCount;
                    }, script.random(2500, 3500));

                    if (inventoryChanged) {
                        int finalCount = getTrapCountInInventory();
                        ScriptLogger.actionSuccess(script, "Blind tap confirmed - inventory changed from " +
                            initialTrapCount + " to " + finalCount);
                        trapManager.removeTrap(trapPos);
                    } else {
                        ScriptLogger.warning(script, "Blind tap succeeded but inventory unchanged - keeping trap in tracking for retry");
                    }
                } else {
                    ScriptLogger.warning(script, "Blind tap verification failed - removing trap from tracking to avoid retry loop");
                    trapManager.removeTrap(trapPos);
                }
            }

            default -> {
                ScriptLogger.warning(script, "Unhandled interaction result type: " + result.type());
            }
        }
    }

    /**
     * Handle repositioning when a trap is occluded by UI.
     */
    private void handleTrapRepositioning(WorldPosition trapPos) {
        ScriptLogger.navigation(script, "Trap at " + trapPos + " is occluded. Finding better viewing position.");

        TrapVisibilityChecker visibilityChecker = interactionHandler.getVisibilityChecker();
        WorldPosition viewingPos = visibilityChecker.findBestViewingPosition(trapPos, huntingZones);

        if (viewingPos != null) {
            WorldPosition currentPos = script.getWorldPosition();
            if (currentPos != null && !currentPos.equals(viewingPos)) {
                double distance = currentPos.distanceTo(viewingPos);
                if (distance > 1) {
                    ScriptLogger.navigation(script, "Moving to viewing position: " + viewingPos);
                    script.getWalker().walkTo(viewingPos, walkConfigApprox);
                    script.submitHumanTask(() -> script.getPixelAnalyzer().isPlayerAnimating(0.4), 500);
                }
            }
        } else {
            WorldPosition currentPos = script.getWorldPosition();
            if (currentPos != null && currentPos.distanceTo(trapPos) > 1) {
                ScriptLogger.warning(script, "No optimal viewing position found. Walking to trap directly.");
                script.getWalker().walkTo(trapPos, walkConfigExact);
            }
        }
    }

    private void layNewTrap() {
        if (script.isDrainingForBreak()) {
            ScriptLogger.warning(script, "layNewTrap called during drain mode - refusing to lay trap");
            return;
        }

        if (trapType == TrapType.CHINCHOMPA) {
            ItemGroupResult inventoryResult = script.getWidgetManager().getInventory().search(Collections.emptySet());
            if (inventoryResult != null) {
                int freeSlots = inventoryResult.getFreeSlots();

                if (freeSlots == 0) {
                    Set<Integer> chinchompaIds = Set.of(ItemID.CHINCHOMPA, ItemID.RED_CHINCHOMPA);
                    ItemGroupResult chinResult = script.getItemManager().scanItemGroup(
                        script.getWidgetManager().getInventory(),
                        chinchompaIds
                    );

                    if (chinResult == null || chinResult.getAllOfItems(chinchompaIds).isEmpty()) {
                        ScriptLogger.error(script, "Inventory is full with no stackable chinchompas. Stopping script.");
                        script.stop();
                        return;
                    }
                    ScriptLogger.debug(script, "Inventory full but chinchompas can stack, continuing.");
                }
            }
        }

        WorldPosition initialPos = script.getWorldPosition();
        if (initialPos == null) {
            ScriptLogger.warning(script, "Could not read player position – skipping trap lay this cycle");
            return;
        }

        WorldPosition workingPos = initialPos;

        final WorldPosition checkPos = workingPos;
        if (huntingZones.stream().noneMatch(zone -> zone.contains(checkPos))) {
            Set<WorldPosition> existingTraps = new HashSet<>(trapManager.getLaidTrapPositions());
            WorldPosition targetPos = placementStrategy.findNextTrapPosition(initialPos, huntingZones, existingTraps);
            if (targetPos == null) {
                targetPos = findSafeRandomPosition(null);
            }
            if (targetPos == null) {
                ScriptLogger.warning(script, "Could not find safe position in hunting zone. Skipping trap laying this cycle.");
                return;
            }

            ScriptLogger.navigation(script, "Outside hunting zone. Walking to safe position " + targetPos);

            committedToLayingTrap = true;
            committedTrapPosition = targetPos;

            boolean reachedZone = navigation.simpleMoveTo(targetPos, RandomUtils.uniformRandom(5700, 6400), 0);

            if (!reachedZone) {
                WorldPosition currentPos = script.getWorldPosition();
                if (currentPos != null && huntingZones.stream().anyMatch(zone -> zone.contains(currentPos))) {
                    Set<WorldPosition> currentTraps = new HashSet<>(trapManager.getLaidTrapPositions());
                    if (placementStrategy.isValidPosition(currentPos, currentTraps)) {
                        ScriptLogger.navigation(script, "Missed target " + targetPos + " but current position " + currentPos + " is valid for trap laying. Continuing.");
                    } else {
                        ScriptLogger.warning(script, "Failed to reach hunting zone and current position not valid for trap laying. Skipping trap laying this cycle.");
                        committedToLayingTrap = false;
                        committedTrapPosition = null;
                        return;
                    }
                } else {
                    ScriptLogger.warning(script, "Failed to reach hunting zone. Skipping trap laying this cycle.");
                    committedToLayingTrap = false;
                    committedTrapPosition = null;
                    return;
                }
            } else {
                ScriptLogger.debug(script, "Successfully reached hunting zone. Continuing with trap laying.");
            }
        }

        if (!hasTrapSupplies()) {
            if (trapManager.hasPendingGracePeriods()) {
                int pendingCount = trapManager.getPendingGracePeriodsCount();
                ScriptLogger.info(script, "Out of " + trapType.getItemName() + " but " + pendingCount + " trap(s) have pending grace periods. Waiting for potential pickups...");
                return;
            }

            ScriptLogger.error(script, "Out of " + trapType.getItemName() + " and no pending grace periods. Stopping script.");
            script.stop();
            return;
        }

        Set<WorldPosition> existingTraps = new HashSet<>(trapManager.getLaidTrapPositions());

        if (!placementStrategy.isValidPosition(workingPos, existingTraps)) {
            ScriptLogger.info(script, "Current position " + workingPos + " violates " + placementStrategy.getStrategyName() + " strategy rules. Finding better position.");

            WorldPosition strategicPosition = placementStrategy.findNextTrapPosition(initialPos, huntingZones, existingTraps);

            if (strategicPosition != null) {
                WorldPosition currentPos = script.getWorldPosition();
                if (currentPos != null) {
                    double distance = currentPos.distanceTo(strategicPosition);
                    if (distance == 0 && placementStrategy.isValidPosition(currentPos, existingTraps)) {
                        ScriptLogger.debug(script, "Already at exact target position and position is valid. Skipping movement.");
                        workingPos = currentPos;
                    } else {
                        ScriptLogger.navigation(script, "Strategy suggests position: " + strategicPosition);

                        committedToLayingTrap = true;
                        committedTrapPosition = strategicPosition;

                        boolean moved = navigation.simpleMoveTo(strategicPosition, RandomUtils.uniformRandom(3600, 4800), 0);

                        if (!moved) {
                            currentPos = script.getWorldPosition();
                            if (currentPos != null) {
                                Set<WorldPosition> strategicTraps = new HashSet<>(trapManager.getLaidTrapPositions());
                                if (placementStrategy.isValidPosition(currentPos, strategicTraps)) {
                                    ScriptLogger.navigation(script, "Missed strategic target " + strategicPosition + " but current position " + currentPos + " is valid for trap laying. Continuing.");
                                    workingPos = currentPos;
                                } else {
                                    ScriptLogger.warning(script, "Failed to move to strategic position and current position not valid for trap laying. Skipping trap laying this cycle.");
                                    committedToLayingTrap = false;
                                    committedTrapPosition = null;
                                    return;
                                }
                            } else {
                                ScriptLogger.warning(script, "Failed to move to strategic position. Skipping trap laying this cycle.");
                                committedToLayingTrap = false;
                                committedTrapPosition = null;
                                return;
                            }
                        } else {
                            ScriptLogger.debug(script, "Successfully moved to strategic position. Continuing with trap laying.");
                            WorldPosition afterMovePos = script.getWorldPosition();
                            if (afterMovePos != null) {
                                workingPos = afterMovePos;
                            }
                        }
                    }
                }
            } else {
                ScriptLogger.warning(script, "Strategy could not find a valid position. Skipping trap laying this cycle.");
                return;
            }
        } else {
            ScriptLogger.debug(script, "Current position " + initialPos + " is valid according to " + placementStrategy.getStrategyName() + " strategy.");
        }

        // Ensure the final tile is not already occupied.
        WorldPosition finalPos = script.getWorldPosition();
        if (finalPos != null && existingTraps.contains(finalPos)) {
            ScriptLogger.warning(script, "Current position " + finalPos + " already has a trap after strategy validation. This should not happen.");
            return;
        }

        // Skip interaction while the player is still moving.
        if (script.getLastPositionChangeMillis() < RandomUtils.uniformRandom(350, 700)) {
            ScriptLogger.debug(script, "Still moving (last change: " + script.getLastPositionChangeMillis() + "ms ago). Waiting before laying trap.");
            return;
        }

        ScriptLogger.actionAttempt(script, "Laying trap " + (trapManager.getTotalCount() + 1) + "/" + maxTraps);

        final WorldPosition[] trapPosition = new WorldPosition[1];

        boolean submitted = script.submitTask(() -> {
            WorldPosition currentPos = script.getWorldPosition();
            if (currentPos == null) {
                ScriptLogger.warning(script, "Could not read player position during trap interaction");
                return false;
            }

            trapPosition[0] = new WorldPosition(currentPos.getX(), currentPos.getY(), currentPos.getPlane());
            ScriptLogger.debug(script, "Captured trap position during interaction: " + trapPosition[0]);

            ItemSearchResult trap = getTrapFromInventory();
            return trap != null && trap.interact(trapType.getInventoryActions()[0]);
        }, RandomUtils.uniformRandom(1000, 2000));

        if (submitted && trapPosition[0] != null) {
            committedToLayingTrap = false;
            committedTrapPosition = null;

            trapLayingStartPosition = script.getWorldPosition();
            trapLayingStartTime = System.currentTimeMillis();
            ScriptLogger.debug(script, "Starting trap laying from position: " + trapLayingStartPosition);

            if (!trapManager.startLayingTrap(trapPosition[0])) {
                ScriptLogger.warning(script, "Could not start laying trap - another trap is being laid");
                trapLayingStartPosition = null;
                trapLayingStartTime = 0;
                return;
            }

            ScriptLogger.info(script, "Trap laying initiated at " + trapPosition[0] + " - completion handled in main loop");
        } else {
            ScriptLogger.warning(script, "Failed to submit trap laying action or capture position");
        }
    }

    /**
     * Detects if a yellow respawn circle has appeared at the specified position.
     * This indicates that a trap has been successfully laid and is now active.
     * @param position The world position to check for respawn circle
     * @return true if a yellow respawn circle is detected at the position
     */
    private boolean detectRespawnCircleAtPosition(WorldPosition position) {
        if (position == null) {
            return false;
        }

        // Create a 0x0 RectangleArea at the trap position
        // Note: 0x0 actually searches the single tile, as 1x1 would be a 2x2 area
        RectangleArea searchArea = new RectangleArea(
            position.getX(), position.getY(), 0, 0, position.getPlane()
        );

        // Search for respawn circle using the trap type's active z-offset
        PixelAnalyzer.RespawnCircle circle = script.getPixelAnalyzer().getRespawnCircle(
            searchArea,
            PixelAnalyzer.RespawnCircleDrawType.CENTER, // Use center draw type for traps
            trapType.getActiveZOffset(), // Yellow circle z-offset from trap type
            6 // Distance tolerance in pixels
        );

        // Check if we found a yellow circle (indicates trap is active)
        if (circle != null && circle.getType() == PixelAnalyzer.RespawnCircle.Type.YELLOW) {
            ScriptLogger.debug(script, "Yellow respawn circle detected at " + position);
            return true;
        }

        return false;
    }

    /**
     * Validates that all trap tiles are visible on screen and not occluded by UI.
     * This is important for ALL trap states including collapsed traps.
     * Updates the TrapStateManager's repositioning flags for occluded traps.
     * @return true if all trap tiles are visible, false if any need repositioning
     */
    private boolean validateAllTrapTilesVisible() {
        // Don't check during trap laying
        if (trapManager.isCurrentlyLayingTrap()) {
            return true;
        }

        List<WorldPosition> allTrapPositions = trapManager.getLaidTrapPositions();
        if (allTrapPositions.isEmpty()) {
            return true; // No traps to check
        }

        boolean allVisible = true;
        int offScreenCount = 0;
        int occludedCount = 0;

        for (WorldPosition trapPos : allTrapPositions) {
            RSTile trapTile = script.getSceneManager().getTile(trapPos);

            if (trapTile == null) {
                // Tile is completely off-screen (not in the scene at all)
                trapManager.markTrapForRepositioning(trapPos);
                offScreenCount++;
                allVisible = false;
            } else {
                // Check if tile is visible on screen without UI occlusion
                // First check general visibility
                if (!trapTile.isOnGameScreen()) {
                    // Tile is occluded by UI or otherwise not visible
                    trapManager.markTrapForRepositioning(trapPos);
                    occludedCount++;
                    allVisible = false;
                }
            }
        }

        if (!allVisible) {
            ScriptLogger.info(script, "Trap visibility issues - Off-screen: " + offScreenCount +
                             ", UI-occluded: " + occludedCount + " of " + allTrapPositions.size() + " total traps");
        }

        return allVisible;
    }

}
