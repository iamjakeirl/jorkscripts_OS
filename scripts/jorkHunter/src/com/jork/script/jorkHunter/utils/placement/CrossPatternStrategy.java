package com.jork.script.jorkHunter.utils.placement;

import com.jork.script.jorkHunter.JorkHunter;
import com.jork.utils.ScriptLogger;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.utils.RandomUtils;

import java.util.*;

/**
 * Cross pattern trap placement strategy.
 * Places traps in a cross/plus shape with custom anchor at center.
 * For 4 traps: places on cardinals only (center not used).
 * For 5 traps: places on cardinals plus center (center placed last).
 */
public class CrossPatternStrategy implements TrapPlacementStrategy {
    
    private final JorkHunter script;
    private final WorldPosition customAnchor;
    private final int maxTraps;
    private List<WorldPosition> currentPattern;
    
    /**
     * Creates a cross pattern strategy.
     * 
     * @param script The script instance
     * @param customAnchor The custom anchor position (center of the cross)
     * @param maxTraps Maximum number of traps (4-5)
     */
    public CrossPatternStrategy(JorkHunter script, WorldPosition customAnchor, int maxTraps) {
        this.script = script;
        this.customAnchor = customAnchor;
        this.maxTraps = Math.min(5, Math.max(4, maxTraps)); // Clamp between 4 and 5
        this.currentPattern = new ArrayList<>();
        
        ScriptLogger.info(script, "Cross pattern strategy initialized with anchor at: " + customAnchor + 
                         ", max traps: " + this.maxTraps);
    }
    
    @Override
    public WorldPosition findNextTrapPosition(WorldPosition playerPos, 
                                            List<RectangleArea> huntingZones,
                                            Set<WorldPosition> existingTraps) {
        // Generate pattern if not yet generated
        if (currentPattern.isEmpty()) {
            generateCrossPattern();
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
            generateCrossPattern();
        }
        
        if (!currentPattern.contains(position)) {
            return false;
        }
        
        // Check if already occupied
        return existingTraps == null || !existingTraps.contains(position);
    }
    
    /**
     * Generates the cross pattern positions.
     * 4 traps: cardinals only
     * 5 traps: cardinals + center (center last)
     */
    private void generateCrossPattern() {
        currentPattern.clear();
        
        if (customAnchor == null) {
            ScriptLogger.warning(script, "No custom anchor set for Cross pattern");
            return;
        }
        
        int x = customAnchor.getX();
        int y = customAnchor.getY();
        int plane = customAnchor.getPlane();
        
        // Create cardinal positions
        List<WorldPosition> cardinals = new ArrayList<>();
        cardinals.add(new WorldPosition(x, y + 1, plane));  // North
        cardinals.add(new WorldPosition(x, y - 1, plane));  // South
        cardinals.add(new WorldPosition(x + 1, y, plane));  // East
        cardinals.add(new WorldPosition(x - 1, y, plane));  // West
        
        // Shuffle cardinals for random placement order
        Collections.shuffle(cardinals);
        
        // Add cardinals to pattern
        currentPattern.addAll(cardinals);
        
        // For 5 traps, add center last (lowest priority)
        if (maxTraps == 5) {
            currentPattern.add(customAnchor);
        }
        
        ScriptLogger.debug(script, "Generated cross pattern with " + currentPattern.size() + " positions" +
                          (maxTraps == 4 ? " (center excluded)" : " (center included last)"));
    }
    
    @Override
    public String getStrategyName() {
        return "Cross";
    }
    
    @Override
    public String getDescription() {
        if (maxTraps == 4) {
            return "Places traps on cardinal directions only (N, S, E, W). Center is not used.";
        } else {
            return "Places traps on cardinal directions plus center. Center is placed last.";
        }
    }
}