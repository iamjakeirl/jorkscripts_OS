package com.jork.script.jorkHunter.utils.placement;

import com.jork.script.jorkHunter.JorkHunter;
import com.jork.script.jorkHunter.tasks.TrapTask;
import com.jork.utils.ScriptLogger;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.utils.RandomUtils;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * X-shaped trap placement strategy for higher hunter levels.
 * Places traps in a diagonal X pattern with a dynamically selected center.
 * The center can be recalculated when placing the first trap (0 current traps).
 * Traps are placed at the nearest available position from the player's current location
 * for more human-like behavior, with random selection when multiple positions are equidistant.
 */
public class XPatternStrategy implements TrapPlacementStrategy {
    
    private final JorkHunter script;
    private final TrapTask huntTask;
    private final int maxCenterDistance;
    private final boolean recenterOnEmpty;
    private final WorldPosition customAnchor; // Optional fixed anchor position
    private WorldPosition currentCenter;
    private List<WorldPosition> currentPattern;
    
    /**
     * Creates a new X-pattern placement strategy with optional custom anchor.
     * 
     * @param script The main script instance
     * @param huntTask The hunt task for accessing trap state
     * @param maxCenterDistance Maximum distance from hunting area center for X center (default 3)
     * @param recenterOnEmpty Whether to pick new center when no traps are placed (default true)
     * @param customAnchor Optional fixed anchor position (overrides dynamic center selection)
     */
    public XPatternStrategy(JorkHunter script, TrapTask huntTask, int maxCenterDistance, 
                           boolean recenterOnEmpty, WorldPosition customAnchor) {
        this.script = script;
        this.huntTask = huntTask;
        this.maxCenterDistance = maxCenterDistance;
        this.recenterOnEmpty = recenterOnEmpty;
        this.customAnchor = customAnchor;
        this.currentCenter = customAnchor; // Use custom anchor as initial center if provided
        this.currentPattern = new ArrayList<>();
        
        if (customAnchor != null) {
            ScriptLogger.info(script, "X-Pattern using custom anchor at: " + customAnchor);
        }
    }
    
    /**
     * Creates a new X-pattern placement strategy.
     * 
     * @param script The main script instance
     * @param huntTask The hunt task for accessing trap state
     * @param maxCenterDistance Maximum distance from hunting area center for X center (default 3)
     * @param recenterOnEmpty Whether to pick new center when no traps are placed (default true)
     */
    public XPatternStrategy(JorkHunter script, TrapTask huntTask, int maxCenterDistance, boolean recenterOnEmpty) {
        this(script, huntTask, maxCenterDistance, recenterOnEmpty, null);
    }
    
    /**
     * Creates a new X-pattern strategy with default settings.
     * 
     * @param script The main script instance
     * @param huntTask The hunt task for accessing trap state
     */
    public XPatternStrategy(JorkHunter script, TrapTask huntTask) {
        this(script, huntTask, 3, true, null);
    }
    
    @Override
    public WorldPosition findNextTrapPosition(WorldPosition playerPos, 
                                             List<RectangleArea> huntingZones,
                                             Set<WorldPosition> existingTraps) {
        if (huntingZones == null || huntingZones.isEmpty()) {
            return null;
        }
        
        // Use first hunting zone as primary area
        RectangleArea primaryZone = huntingZones.get(0);
        
        // Check if we should recalculate center
        // If custom anchor is set, only generate pattern once; never recenter
        if (customAnchor != null) {
            if (currentPattern.isEmpty()) {
                // First time - generate pattern around custom anchor
                generateXPattern(primaryZone);
            }
        } else {
            // Dynamic center logic
            boolean shouldRecenter = currentCenter == null || 
                (recenterOnEmpty && (existingTraps == null || existingTraps.isEmpty()));
            
            if (shouldRecenter) {
                selectNewCenter(primaryZone);
                generateXPattern(primaryZone);
            }
        }
        
        // Find nearest unoccupied position in pattern (human-like behavior)
        WorldPosition nearestPosition = null;
        double nearestDistance = Double.MAX_VALUE;
        List<WorldPosition> equalDistancePositions = new ArrayList<>();
        
        // Calculate distances to all unoccupied positions
        for (WorldPosition position : currentPattern) {
            if (existingTraps == null || !existingTraps.contains(position)) {
                if (playerPos == null) {
                    // If no player position, fallback to first available
                    ScriptLogger.debug(script, "No player position available, using first available pattern position: " + position);
                    return position;
                }
                
                double distance = playerPos.distanceTo(position);
                
                if (distance < nearestDistance) {
                    // Found a closer position
                    nearestDistance = distance;
                    nearestPosition = position;
                    equalDistancePositions.clear();
                    equalDistancePositions.add(position);
                } else if (Math.abs(distance - nearestDistance) < 0.001) {
                    // Equal distance (using small epsilon for double comparison)
                    equalDistancePositions.add(position);
                }
            }
        }
        
        // If multiple positions at same distance, pick randomly for human-like variation
        if (equalDistancePositions.size() > 1) {
            int randomIndex = RandomUtils.uniformRandom(0, equalDistancePositions.size() - 1);
            nearestPosition = equalDistancePositions.get(randomIndex);
            ScriptLogger.debug(script, "Multiple positions at distance " + nearestDistance + 
                             ", randomly selected: " + nearestPosition);
        } else if (nearestPosition != null) {
            ScriptLogger.debug(script, "Next X-pattern placement (nearest): " + nearestPosition + 
                             " at distance " + nearestDistance);
        }
        
        if (nearestPosition == null) {
            ScriptLogger.warning(script, "All X-pattern positions occupied");
        }
        
        return nearestPosition;
    }
    
    @Override
    public boolean isValidPosition(WorldPosition position, Set<WorldPosition> existingTraps) {
        if (position == null) {
            return false;
        }
        
        // Check if position is already occupied
        if (existingTraps != null && existingTraps.contains(position)) {
            return false;
        }
        
        // Check if position is part of current pattern
        if (currentPattern != null && currentPattern.contains(position)) {
            return true;
        }
        
        return false;
    }
    
    @Override
    public String getStrategyName() {
        return "X-Pattern";
    }
    
    @Override
    public String getDescription() {
        return "Places traps in an X-shaped diagonal pattern around custom anchor at " + customAnchor + ". " +
               "Traps are placed at the nearest available position for human-like behavior.";
    }
    
    /**
     * Selects a new center point for the X pattern within the hunting area.
     * 
     * @param huntingArea The hunting area to select center within
     */
    private void selectNewCenter(RectangleArea huntingArea) {
        Point areaCenterPoint = huntingArea.getCenter();
        WorldPosition areaCenter = new WorldPosition(areaCenterPoint.x, areaCenterPoint.y, huntingArea.getPlane());
        
        // If maxCenterDistance is 0, use exact center
        if (maxCenterDistance == 0) {
            currentCenter = areaCenter;
            ScriptLogger.debug(script, "X-pattern center set to exact area center: " + currentCenter);
            return;
        }
        
        // Pick random offset within maxCenterDistance
        int xOffset = RandomUtils.uniformRandom(-maxCenterDistance, maxCenterDistance);
        int yOffset = RandomUtils.uniformRandom(-maxCenterDistance, maxCenterDistance);
        
        int centerX = areaCenter.getX() + xOffset;
        int centerY = areaCenter.getY() + yOffset;
        
        // Ensure center is within hunting area bounds
        centerX = Math.max(huntingArea.getX(), 
                  Math.min(centerX, huntingArea.getX() + huntingArea.getWidth() - 1));
        centerY = Math.max(huntingArea.getY(), 
                  Math.min(centerY, huntingArea.getY() + huntingArea.getHeight() - 1));
        
        currentCenter = new WorldPosition(centerX, centerY, huntingArea.getPlane());
        ScriptLogger.info(script, "Selected new X-pattern center: " + currentCenter + 
            " (offset: " + xOffset + ", " + yOffset + " from area center)");
    }
    
    /**
     * Generates the X pattern positions based on current center.
     * Pattern includes center plus 4 diagonal positions with randomized ordering.
     * For custom anchors, creates a 5x5 area centered on the anchor.
     * 
     * @param huntingArea The hunting area to constrain positions within (ignored for custom anchors)
     */
    private void generateXPattern(RectangleArea huntingArea) {
        currentPattern.clear();
        
        // If using custom anchor, create a 5x5 area centered on it
        if (customAnchor != null) {
            int areaSize = 5;
            huntingArea = new RectangleArea(
                customAnchor.getX() - areaSize / 2,
                customAnchor.getY() - areaSize / 2,
                areaSize,
                areaSize,
                customAnchor.getPlane()
            );
            ScriptLogger.debug(script, "Created 5x5 custom hunting area centered at " + customAnchor);
        }
        
        // Always add center position first
        currentPattern.add(currentCenter);
        
        // Define diagonal positions with names for clarity
        int[][] corners = {
            {1, 1},   // 0: NE
            {-1, 1},  // 1: NW
            {-1, -1}, // 2: SW
            {1, -1}   // 3: SE
        };
        
        String[] cornerNames = {"NE", "NW", "SW", "SE"};
        
        // Define which corners are adjacent to each corner
        // NE(0) is adjacent to NW(1) and SE(3)
        // NW(1) is adjacent to NE(0) and SW(2)
        // SW(2) is adjacent to NW(1) and SE(3)
        // SE(3) is adjacent to NE(0) and SW(2)
        int[][] adjacentCorners = {
            {1, 3}, // NE is adjacent to NW and SE
            {0, 2}, // NW is adjacent to NE and SW
            {1, 3}, // SW is adjacent to NW and SE
            {0, 2}  // SE is adjacent to NE and SW
        };
        
        // Randomly select starting corner
        int firstCorner = RandomUtils.uniformRandom(0, 3);
        
        // Select an adjacent corner as second
        int[] adjacentOptions = adjacentCorners[firstCorner];
        int secondCorner = adjacentOptions[RandomUtils.uniformRandom(0, adjacentOptions.length - 1)];
        
        // Determine remaining corners
        List<Integer> cornerOrder = new ArrayList<>();
        cornerOrder.add(firstCorner);
        cornerOrder.add(secondCorner);
        
        // Add remaining corners
        for (int i = 0; i < 4; i++) {
            if (!cornerOrder.contains(i)) {
                cornerOrder.add(i);
            }
        }
        
        // Build pattern in the determined order
        StringBuilder orderLog = new StringBuilder("Corner order: ");
        for (int cornerIndex : cornerOrder) {
            int newX = currentCenter.getX() + corners[cornerIndex][0];
            int newY = currentCenter.getY() + corners[cornerIndex][1];
            WorldPosition diagonal = new WorldPosition(newX, newY, currentCenter.getPlane());
            
            // Only add if within hunting area
            if (huntingArea.contains(diagonal)) {
                currentPattern.add(diagonal);
                orderLog.append(cornerNames[cornerIndex]).append(" ");
            } else {
                ScriptLogger.debug(script, "X-pattern " + cornerNames[cornerIndex] + 
                    " position " + diagonal + " is outside hunting area, skipping");
            }
        }
        
        ScriptLogger.info(script, "Generated X-pattern with " + currentPattern.size() + 
            " positions centered at " + currentCenter + ". " + orderLog.toString());
    }
    
    /**
     * Gets the current center position of the X pattern.
     * 
     * @return The current center position, or null if not yet selected
     */
    public WorldPosition getCurrentCenter() {
        return currentCenter;
    }
    
    /**
     * Forces selection of a new center on next placement.
     * Has no effect if using custom anchor.
     */
    public void forceRecenter() {
        if (customAnchor != null) {
            ScriptLogger.debug(script, "Cannot force recenter when using custom anchor");
            return;
        }
        currentCenter = null;
        currentPattern.clear();
        ScriptLogger.debug(script, "X-pattern center reset, will recalculate on next placement");
    }
}