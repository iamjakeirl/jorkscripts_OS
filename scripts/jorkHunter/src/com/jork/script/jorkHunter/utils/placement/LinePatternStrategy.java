package com.jork.script.jorkHunter.utils.placement;

import com.jork.script.jorkHunter.JorkHunter;
import com.jork.utils.ScriptLogger;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.utils.RandomUtils;

import java.util.*;

/**
 * Line pattern trap placement strategy.
 * Places traps in a straight line with the custom anchor at the center.
 * Can be horizontal or vertical based on configuration.
 * Scales from 1 to 5 traps.
 */
public class LinePatternStrategy implements TrapPlacementStrategy {
    
    public enum Orientation {
        HORIZONTAL,
        VERTICAL,
        RANDOM
    }
    
    private final JorkHunter script;
    private final WorldPosition customAnchor;
    private final int maxTraps;
    private final Orientation orientation;
    private List<WorldPosition> currentPattern;
    
    /**
     * Creates a line pattern strategy.
     * 
     * @param script The script instance
     * @param customAnchor The custom anchor position (center of the line)
     * @param maxTraps Maximum number of traps (1-5)
     * @param orientation The line orientation (HORIZONTAL, VERTICAL, or RANDOM)
     */
    public LinePatternStrategy(JorkHunter script, WorldPosition customAnchor, int maxTraps, Orientation orientation) {
        this.script = script;
        this.customAnchor = customAnchor;
        this.maxTraps = Math.min(5, Math.max(1, maxTraps)); // Clamp between 1 and 5
        
        // If orientation is RANDOM, pick one randomly
        if (orientation == Orientation.RANDOM) {
            this.orientation = RandomUtils.uniformRandom(0, 1) == 0 ? Orientation.HORIZONTAL : Orientation.VERTICAL;
            ScriptLogger.debug(script, "Line pattern randomly selected orientation: " + this.orientation);
        } else {
            this.orientation = orientation;
        }
        
        this.currentPattern = new ArrayList<>();
        
        ScriptLogger.info(script, "Line pattern strategy initialized with anchor at: " + customAnchor + 
                         ", max traps: " + this.maxTraps + ", orientation: " + this.orientation);
    }
    
    @Override
    public WorldPosition findNextTrapPosition(WorldPosition playerPos, 
                                            List<RectangleArea> huntingZones,
                                            Set<WorldPosition> existingTraps) {
        // Generate pattern if not yet generated
        if (currentPattern.isEmpty()) {
            generateLinePattern();
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
            generateLinePattern();
        }
        
        if (!currentPattern.contains(position)) {
            return false;
        }
        
        // Check if already occupied
        return existingTraps == null || !existingTraps.contains(position);
    }
    
    /**
     * Generates the line pattern positions based on orientation.
     */
    private void generateLinePattern() {
        currentPattern.clear();
        
        if (customAnchor == null) {
            ScriptLogger.warning(script, "No custom anchor set for Line pattern");
            return;
        }
        
        int x = customAnchor.getX();
        int y = customAnchor.getY();
        int plane = customAnchor.getPlane();
        
        // Always add the center (anchor)
        currentPattern.add(customAnchor);
        
        if (maxTraps == 1) {
            // Just the anchor
            return;
        }
        
        // Calculate how to distribute traps
        int trapsPerSide = (maxTraps - 1) / 2;
        int extraTrap = (maxTraps - 1) % 2;
        
        // Add positions based on orientation
        if (orientation == Orientation.HORIZONTAL) {
            // Add west side traps
            for (int i = 1; i <= trapsPerSide; i++) {
                currentPattern.add(new WorldPosition(x - i, y, plane));
            }
            // Add east side traps
            for (int i = 1; i <= trapsPerSide + extraTrap; i++) {
                currentPattern.add(new WorldPosition(x + i, y, plane));
            }
        } else { // VERTICAL
            // Add south side traps
            for (int i = 1; i <= trapsPerSide; i++) {
                currentPattern.add(new WorldPosition(x, y - i, plane));
            }
            // Add north side traps
            for (int i = 1; i <= trapsPerSide + extraTrap; i++) {
                currentPattern.add(new WorldPosition(x, y + i, plane));
            }
        }
        
        // Randomize placement order (but keep center first for priority)
        if (currentPattern.size() > 1) {
            List<WorldPosition> nonCenter = new ArrayList<>(currentPattern.subList(1, currentPattern.size()));
            Collections.shuffle(nonCenter);
            currentPattern.clear();
            currentPattern.add(customAnchor);
            currentPattern.addAll(nonCenter);
        }
        
        ScriptLogger.debug(script, "Generated " + orientation + " line pattern with " + currentPattern.size() + " positions");
    }
    
    @Override
    public String getStrategyName() {
        return "Line";
    }
    
    @Override
    public String getDescription() {
        return "Places traps in a " + orientation.toString().toLowerCase() + 
               " line with anchor at center. Scales from 1 to " + maxTraps + " traps.";
    }
}