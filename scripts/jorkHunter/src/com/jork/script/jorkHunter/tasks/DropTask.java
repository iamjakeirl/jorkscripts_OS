package com.jork.script.jorkHunter.tasks;

import com.jork.script.jorkHunter.HuntingLocation;
import com.jork.script.jorkHunter.JorkHunter;
import com.jork.script.jorkHunter.utils.tasks.Task;
import com.osmb.api.script.Script;
import com.osmb.api.utils.RandomUtils;
import com.osmb.api.item.ItemGroupResult;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.location.area.impl.RectangleArea;
import com.jork.utils.ScriptLogger;
import com.jork.utils.Navigation;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DropTask implements Task {

    private final Script script;
    private final int[] itemsToDrop;
    private final HuntingLocation huntingLocation;
    private final Navigation navigation;

    // Movement state
    private WorldPosition preMovePosition;
    private int dropTargetX;
    private int dropTargetY;
    private boolean movingForDrop;

    // Tunables
    private static final int ARRIVAL_TOLERANCE_TILES = 1;
    private static final int AWAY_MIN_TILES = 2;
    private static final int NULL_POS_BACKOFF_MS = 800;
    private static final int ANIM_POLL_MIN_MS = 200;
    private static final int ANIM_POLL_MAX_MS = 400;
    private static final int DROP_DISTANCE_MIN = 1;
    private static final int DROP_DISTANCE_MAX = 3;

    public DropTask(Script script, int[] itemsToDrop, HuntingLocation huntingLocation) {
        this.script = script;
        this.itemsToDrop = itemsToDrop;
        this.huntingLocation = huntingLocation;
        this.navigation = new Navigation(script);
    }

    @Override
    public boolean canExecute() {
        // If no items to drop (e.g., chinchompa hunting), never execute
        if (itemsToDrop == null || itemsToDrop.length == 0) {
            return false;
        }
        
        // Trigger when we have 2-4 free inventory slots remaining (to account for bird traps returning 2 items).
        // This creates more natural variation in when we drop items.
        ItemGroupResult inventoryResult = script.getWidgetManager().getInventory().search(Collections.emptySet());
        if (inventoryResult == null) return false;
        
        int freeSlots = inventoryResult.getFreeSlots();
        return freeSlots >= 0 && freeSlots <= 4; // Trigger when 2-4 slots are free
    }

    @Override
    public int execute() {
        // Allow drops during drain mode - we need to keep inventory clear to pick up traps
        if (script instanceof JorkHunter && ((JorkHunter) script).isDrainingForBreak()) {
            ScriptLogger.info(script, "Drain mode active. Proceeding with drop task to keep inventory clear.");
        }
        
        ScriptLogger.info(script, "Inventory has few free slots. Checking for items to drop...");

        // Convert our droppable items array to a Set for scanning.
        Set<Integer> itemIdsToLookFor = Arrays.stream(itemsToDrop)
                                     .boxed()
                                     .collect(Collectors.toSet());
        
        // Scan the inventory to see if any of our designated items are present.
        ItemGroupResult invResult = script.getItemManager().scanItemGroup(
            script.getWidgetManager().getInventory(),
            itemIdsToLookFor
        );

        // If the scan found no droppable items, we are in an unrecoverable state.
        if (invResult == null || invResult.getAllOfItems(itemIdsToLookFor).isEmpty()) {
            ScriptLogger.error(script, "Inventory has few free slots, but no droppable items were found. Stopping script.");
            script.stop();
            return -1; // Stop polling
        }

        // If we are in the middle of moving for drop, drive the non-blocking completion flow
        if (movingForDrop) {
            WorldPosition current = script.getWorldPosition();
            if (current == null) {
                ScriptLogger.warning(script, "World position is null during drop movement; backing off");
                return NULL_POS_BACKOFF_MS;
            }

            // Animation-first: if animating, keep polling quickly
            boolean animating = script.getPixelAnalyzer().isPlayerAnimating(0.4);
            if (animating) {
                return RandomUtils.weightedRandom(ANIM_POLL_MIN_MS, ANIM_POLL_MAX_MS);
            }

            // Not animating: check arrival or away-from-area
            boolean arrived = hasArrived(current, dropTargetX, dropTargetY, ARRIVAL_TOLERANCE_TILES);
            boolean outsideHuntingArea = isAtLeastTwoTilesOutsideHuntingArea(current);

            if (arrived || outsideHuntingArea) {
                int distToTarget = Math.max(Math.abs(current.getX() - dropTargetX), Math.abs(current.getY() - dropTargetY));
                double distFromPreMove = (preMovePosition != null) ? preMovePosition.distanceTo(current) : -1;
                ScriptLogger.info(script, "Ready to drop. distToTarget=" + distToTarget + ", distFromPreMove=" + distFromPreMove);

                script.getWidgetManager().getInventory().dropItems(itemsToDrop);
                ScriptLogger.debug(script, "Disabling 'Tap to drop' mode as a cleanup step.");
                script.getWidgetManager().getHotkeys().setTapToDropEnabled(false);

                // Reset state
                movingForDrop = false;
                preMovePosition = null;
                return RandomUtils.weightedRandom(300, 600);
            }

            // Still not there: re-issue movement using simpleMoveTo for retry
            // This will attempt screen tap first, then walker fallback
            WorldPosition dropTarget = new WorldPosition(dropTargetX, dropTargetY, current.getPlane());
            boolean retrySuccess = navigation.simpleMoveTo(dropTarget, RandomUtils.uniformRandom(2000, 3000), 0);
            
            if (!retrySuccess) {
                ScriptLogger.warning(script, "Movement retry failed, may be stuck");
            }
            return RandomUtils.weightedRandom(250, 450);
        }

        // We are not moving yet: initiate movement away for drop
        boolean movementStarted = moveAwayForDrop();
        if (movementStarted) {
            // Let the poll loop drive completion
            return RandomUtils.weightedRandom(250, 450);
        }

        // Movement could not be started; drop at current position as a fallback
        ScriptLogger.info(script, "Failed to initiate movement, dropping at current position...");
        script.getWidgetManager().getInventory().dropItems(itemsToDrop);
        ScriptLogger.debug(script, "Disabling 'Tap to drop' mode as a cleanup step.");
        script.getWidgetManager().getHotkeys().setTapToDropEnabled(false);
        return RandomUtils.weightedRandom(300, 600);
    }
    
    /**
     * Moves away from current position for anti-detection before dropping items.
     * Intelligently moves 1-3 tiles beyond the nearest hunting zone edge.
     * @return true if movement was successful, false otherwise
     */
    private boolean moveAwayForDrop() {
        WorldPosition currentPosition = script.getWorldPosition();
        if (currentPosition == null) {
            ScriptLogger.warning(script, "Cannot read current position for drop movement");
            return false;
        }
        
        // Calculate smart drop position based on nearest edge
        WorldPosition dropPosition = calculateSmartDropPosition(currentPosition);
        
        if (dropPosition == null) {
            // Fallback to conservative random movement if smart calculation fails
            ScriptLogger.warning(script, "Smart drop position calculation failed, using fallback");
            int xOffset = RandomUtils.uniformRandom(2, 3) * (RandomUtils.uniformRandom(0, 1) == 0 ? -1 : 1);
            int yOffset = RandomUtils.uniformRandom(0, 1) * (RandomUtils.uniformRandom(0, 1) == 0 ? -1 : 1);
            dropPosition = new WorldPosition(
                currentPosition.getX() + xOffset,
                currentPosition.getY() + yOffset,
                currentPosition.getPlane()
            );
        }
        
        // Record movement state
        preMovePosition = currentPosition;
        dropTargetX = dropPosition.getX();
        dropTargetY = dropPosition.getY();
        
        ScriptLogger.info(script, "Moving to drop position: " + dropPosition + " (from " + currentPosition + ")");
        
        // Use simpleMoveTo with exact precision (0 tolerance) to ensure we actually move
        // This will try screen tapping first, then fall back to walker if needed
        boolean walkResult = navigation.simpleMoveTo(dropPosition, RandomUtils.uniformRandom(3000, 4000), 0);
        
        if (!walkResult) {
            ScriptLogger.warning(script, "Failed to initiate movement to drop position");
            return false;
        }
        
        // Mark as moving - simpleMoveTo already handles waiting for movement
        movingForDrop = true;
        return true;
    }
    
    private boolean hasArrived(WorldPosition current, int targetX, int targetY, int toleranceTiles) {
        int dx = Math.abs(current.getX() - targetX);
        int dy = Math.abs(current.getY() - targetY);
        int chebyshev = Math.max(dx, dy);
        return chebyshev <= toleranceTiles;
    }

    private boolean isSufficientlyAway(WorldPosition current, WorldPosition reference, int minTiles) {
        if (current == null || reference == null) return false;
        return reference.distanceTo(current) >= minTiles;
    }

    /**
     * Calculates an intelligent drop position based on the nearest hunting zone edge.
     * Moves 1-3 tiles beyond the nearest edge for minimal travel distance.
     * @param currentPos The player's current position
     * @return The calculated drop position, or null if calculation fails
     */
    private WorldPosition calculateSmartDropPosition(WorldPosition currentPos) {
        if (currentPos == null) {
            return null;
        }
        
        // Handle case where we're using TilePicker and don't have predefined zones
        if (huntingLocation == null) {
            // Create a simple drop position 2-3 tiles away
            int distance = RandomUtils.uniformRandom(DROP_DISTANCE_MIN, DROP_DISTANCE_MAX);
            int angle = RandomUtils.uniformRandom(0, 360);
            int dropX = currentPos.getX() + (int)(distance * Math.cos(Math.toRadians(angle)));
            int dropY = currentPos.getY() + (int)(distance * Math.sin(Math.toRadians(angle)));
            return new WorldPosition(dropX, dropY, currentPos.getPlane());
        }
        
        List<RectangleArea> zones = huntingLocation.huntingZones();
        if (zones == null || zones.isEmpty()) {
            return null;
        }
        
        // Find the nearest edge across all hunting zones
        EdgeInfo nearestEdge = findNearestEdge(currentPos, zones);
        if (nearestEdge == null) {
            return null;
        }
        
        // Calculate position 1-3 tiles beyond the edge
        int dropDistance = RandomUtils.uniformRandom(DROP_DISTANCE_MIN, DROP_DISTANCE_MAX);
        int parallelOffset = RandomUtils.uniformRandom(-1, 1); // Slight randomization along the edge
        
        int dropX = currentPos.getX();
        int dropY = currentPos.getY();
        
        switch (nearestEdge.direction) {
            case NORTH -> {
                dropY = nearestEdge.edgeCoordinate + dropDistance;
                dropX += parallelOffset;
            }
            case SOUTH -> {
                dropY = nearestEdge.edgeCoordinate - dropDistance;
                dropX += parallelOffset;
            }
            case EAST -> {
                dropX = nearestEdge.edgeCoordinate + dropDistance;
                dropY += parallelOffset;
            }
            case WEST -> {
                dropX = nearestEdge.edgeCoordinate - dropDistance;
                dropY += parallelOffset;
            }
        }
        
        return new WorldPosition(dropX, dropY, currentPos.getPlane());
    }
    
    /**
     * Finds the nearest edge of the hunting zones to the current position.
     * @param currentPos The player's current position
     * @param zones The list of hunting zones
     * @return Information about the nearest edge, or null if none found
     */
    private EdgeInfo findNearestEdge(WorldPosition currentPos, List<RectangleArea> zones) {
        if (currentPos == null || zones == null || zones.isEmpty()) {
            return null;
        }
        
        EdgeInfo nearestEdge = null;
        double minDistance = Double.MAX_VALUE;
        
        for (RectangleArea zone : zones) {
            if (zone == null) continue;
            
            // Calculate distance to each edge
            // RectangleArea uses (x, y) as bottom-left corner with width and height
            int northEdge = zone.getY() + zone.getHeight() - 1;  // Max Y
            int southEdge = zone.getY();                          // Min Y
            int eastEdge = zone.getX() + zone.getWidth() - 1;    // Max X
            int westEdge = zone.getX();                           // Min X
            
            // North edge distance
            double northDist = Math.abs(currentPos.getY() - northEdge);
            if (northDist < minDistance) {
                minDistance = northDist;
                nearestEdge = new EdgeInfo(EdgeDirection.NORTH, northEdge, northDist);
            }
            
            // South edge distance  
            double southDist = Math.abs(currentPos.getY() - southEdge);
            if (southDist < minDistance) {
                minDistance = southDist;
                nearestEdge = new EdgeInfo(EdgeDirection.SOUTH, southEdge, southDist);
            }
            
            // East edge distance
            double eastDist = Math.abs(currentPos.getX() - eastEdge);
            if (eastDist < minDistance) {
                minDistance = eastDist;
                nearestEdge = new EdgeInfo(EdgeDirection.EAST, eastEdge, eastDist);
            }
            
            // West edge distance
            double westDist = Math.abs(currentPos.getX() - westEdge);
            if (westDist < minDistance) {
                minDistance = westDist;
                nearestEdge = new EdgeInfo(EdgeDirection.WEST, westEdge, westDist);
            }
        }
        
        return nearestEdge;
    }
    
    /**
     * Helper class to store edge information
     */
    private static class EdgeInfo {
        final EdgeDirection direction;
        final int edgeCoordinate;
        final double distance;
        
        EdgeInfo(EdgeDirection direction, int edgeCoordinate, double distance) {
            this.direction = direction;
            this.edgeCoordinate = edgeCoordinate;
            this.distance = distance;
        }
    }
    
    /**
     * Enum for edge directions
     */
    private enum EdgeDirection {
        NORTH, SOUTH, EAST, WEST
    }
    
    private boolean isAtLeastTwoTilesOutsideHuntingArea(WorldPosition pos) {
        if (pos == null) return false;

        // If we have a configured hunting location, compute distance from its zones
        if (huntingLocation != null) {
            List<RectangleArea> zones = huntingLocation.huntingZones();
            if (zones != null && !zones.isEmpty()) {
                // Inside any zone: not outside
                for (RectangleArea zone : zones) {
                    if (zone != null && zone.contains(pos)) {
                        return false;
                    }
                }
                // Compute min distance to the union of zones
                double minDistance = Double.MAX_VALUE;
                for (RectangleArea zone : zones) {
                    if (zone == null) continue;
                    double d = zone.distanceTo(pos);
                    if (d < minDistance) {
                        minDistance = d;
                    }
                }
                return minDistance >= AWAY_MIN_TILES;
            }
        }

        // Fallback to distance from pre-move position
        return isSufficientlyAway(pos, preMovePosition, AWAY_MIN_TILES);
    }
} 