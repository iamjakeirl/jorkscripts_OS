package com.jork.script.jorkHunter.utils.placement;

import com.jork.script.jorkHunter.JorkHunter;
import com.jork.utils.ScriptLogger;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.utils.RandomUtils;

import java.util.*;

/**
 * L-Pattern trap placement strategy.
 * Places traps in a fixed L shape with the custom anchor at the corner.
 * The vertical leg extends north and the horizontal leg extends east.
 * Scales from 3 to 5 traps.
 */
public class LPatternStrategy implements TrapPlacementStrategy {
    
    private final JorkHunter script;
    private final WorldPosition customAnchor;
    private final int maxTraps;
    private List<WorldPosition> currentPattern;
    
    /**
     * Creates an L-pattern strategy with the given anchor and max trap count.
     * 
     * @param script The script instance
     * @param customAnchor The custom anchor position (corner of the L)
     * @param maxTraps Maximum number of traps (3-5)
     */
    public LPatternStrategy(JorkHunter script, WorldPosition customAnchor, int maxTraps) {
        this.script = script;
        this.customAnchor = customAnchor;
        this.maxTraps = Math.min(5, Math.max(3, maxTraps)); // Clamp between 3 and 5
        this.currentPattern = new ArrayList<>();
        
        ScriptLogger.info(script, "L-Pattern strategy initialized with anchor at: " + customAnchor + 
                         ", max traps: " + this.maxTraps);
    }
    
    @Override
    public WorldPosition findNextTrapPosition(WorldPosition playerPos, 
                                            List<RectangleArea> huntingZones,
                                            Set<WorldPosition> existingTraps) {
        // Generate pattern if not yet generated
        if (currentPattern.isEmpty()) {
            generateLPattern();
        }
        
        // Find nearest unoccupied position
        WorldPosition nearestPosition = null;
        double nearestDistance = Double.MAX_VALUE;
        List<WorldPosition> equalDistancePositions = new ArrayList<>();
        
        for (WorldPosition position : currentPattern) {
            if (existingTraps == null || !existingTraps.contains(position)) {
                if (playerPos == null) {
                    // No player position, return first available
                    return position;
                }
                
                double distance = playerPos.distanceTo(position);
                
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestPosition = position;
                    equalDistancePositions.clear();
                    equalDistancePositions.add(position);
                } else if (Math.abs(distance - nearestDistance) < 0.001) {
                    equalDistancePositions.add(position);
                }
            }
        }
        
        // If multiple positions at same distance, pick randomly
        if (equalDistancePositions.size() > 1) {
            int randomIndex = RandomUtils.uniformRandom(0, equalDistancePositions.size() - 1);
            nearestPosition = equalDistancePositions.get(randomIndex);
        }
        
        return nearestPosition;
    }
    
    @Override
    public boolean isValidPosition(WorldPosition position, Set<WorldPosition> existingTraps) {
        if (position == null) return false;
        
        // Check if position is in our pattern
        if (currentPattern.isEmpty()) {
            generateLPattern();
        }
        
        if (!currentPattern.contains(position)) {
            return false;
        }
        
        // Check if already occupied
        return existingTraps == null || !existingTraps.contains(position);
    }
    
    /**
     * Generates the L pattern positions.
     * Fixed orientation: vertical leg north, horizontal leg east.
     */
    private void generateLPattern() {
        currentPattern.clear();
        
        if (customAnchor == null) {
            ScriptLogger.warning(script, "No custom anchor set for L-Pattern");
            return;
        }
        
        int x = customAnchor.getX();
        int y = customAnchor.getY();
        int plane = customAnchor.getPlane();
        
        // Always add the corner (anchor)
        currentPattern.add(customAnchor);
        
        if (maxTraps == 3) {
            // Basic L: corner + 1 north + 1 east
            currentPattern.add(new WorldPosition(x, y + 1, plane));     // North
            currentPattern.add(new WorldPosition(x + 1, y, plane));     // East
        } else if (maxTraps == 4) {
            // Extended L: randomly choose to extend north or east more
            if (RandomUtils.uniformRandom(0, 1) == 0) {
                // Extend north leg more
                currentPattern.add(new WorldPosition(x, y + 1, plane));     // North 1
                currentPattern.add(new WorldPosition(x, y + 2, plane));     // North 2
                currentPattern.add(new WorldPosition(x + 1, y, plane));     // East 1
            } else {
                // Extend east leg more
                currentPattern.add(new WorldPosition(x, y + 1, plane));     // North 1
                currentPattern.add(new WorldPosition(x + 1, y, plane));     // East 1
                currentPattern.add(new WorldPosition(x + 2, y, plane));     // East 2
            }
        } else { // maxTraps == 5
            // Full L: corner + 2 north + 2 east
            currentPattern.add(new WorldPosition(x, y + 1, plane));     // North 1
            currentPattern.add(new WorldPosition(x, y + 2, plane));     // North 2
            currentPattern.add(new WorldPosition(x + 1, y, plane));     // East 1
            currentPattern.add(new WorldPosition(x + 2, y, plane));     // East 2
        }
        
        // Randomize the order (except anchor stays first for priority)
        if (currentPattern.size() > 1) {
            List<WorldPosition> nonAnchor = new ArrayList<>(currentPattern.subList(1, currentPattern.size()));
            Collections.shuffle(nonAnchor);
            currentPattern.clear();
            currentPattern.add(customAnchor);
            currentPattern.addAll(nonAnchor);
        }
        
        ScriptLogger.debug(script, "Generated L-Pattern with " + currentPattern.size() + " positions");
    }
    
    @Override
    public String getStrategyName() {
        return "L-Pattern";
    }
    
    @Override
    public String getDescription() {
        return "Places traps in a fixed L shape with anchor at corner. " +
               "Vertical leg extends north, horizontal leg extends east.";
    }
}