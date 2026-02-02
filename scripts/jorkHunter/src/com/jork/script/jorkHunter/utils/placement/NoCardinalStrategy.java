package com.jork.script.jorkHunter.utils.placement;

import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.utils.RandomUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Trap placement strategy that avoids placing traps in cardinal directions from existing traps.
 * Allows diagonal placement and keeps traps within 4 tiles of existing traps for clustering.
 */
public class NoCardinalStrategy implements TrapPlacementStrategy {
    
    private static final int MAX_DISTANCE_FROM_EXISTING = 4;
    private static final int MAX_PLACEMENT_ATTEMPTS = 25;
    
    @Override
    public WorldPosition findNextTrapPosition(WorldPosition playerPos, 
                                            List<RectangleArea> huntingZones,
                                            Set<WorldPosition> existingTraps) {
        
        if (huntingZones == null || huntingZones.isEmpty()) {
            return null;
        }
        
        // If no existing traps, place anywhere in the zones
        if (existingTraps == null || existingTraps.isEmpty()) {
            return getRandomPositionInZones(huntingZones);
        }
        
        // Try to find a position that follows our rules, preferring positions closer to player
        List<WorldPosition> candidates = new ArrayList<>();
        
        // Generate multiple candidates and pick the best one
        for (int attempt = 0; attempt < MAX_PLACEMENT_ATTEMPTS; attempt++) {
            WorldPosition candidate = getRandomPositionInZones(huntingZones);
            
            if (candidate == null) {
                continue;
            }
            
            // Check if this position violates our rules
            if (isValidPlacement(candidate, existingTraps)) {
                candidates.add(candidate);
                
                // If we have enough candidates, pick the closest to player
                if (candidates.size() >= 5) {
                    break;
                }
            }
        }
        
        // Pick the candidate closest to the player position
        if (!candidates.isEmpty() && playerPos != null) {
            WorldPosition bestCandidate = candidates.get(0);
            double bestDistance = playerPos.distanceTo(bestCandidate);
            
            for (WorldPosition candidate : candidates) {
                double distance = playerPos.distanceTo(candidate);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestCandidate = candidate;
                }
            }
            return bestCandidate;
        } else if (!candidates.isEmpty()) {
            // No player position provided, return first valid candidate
            return candidates.get(0);
        }
        
        // If we couldn't find a position following strict rules, fall back to basic distance check
        for (int attempt = 0; attempt < MAX_PLACEMENT_ATTEMPTS; attempt++) {
            WorldPosition candidate = getRandomPositionInZones(huntingZones);
            
            if (candidate == null) {
                continue;
            }
            
            // Just ensure we're not on top of an existing trap
            if (!existingTraps.contains(candidate)) {
                return candidate;
            }
        }
        
        return null;
    }
    
    @Override
    public boolean isValidPosition(WorldPosition position, Set<WorldPosition> existingTraps) {
        return isValidPlacement(position, existingTraps);
    }
    
    /**
     * Checks if a candidate position is valid according to NoCardinal rules.
     * 
     * @param candidate The position to check
     * @param existingTraps Set of existing trap positions
     * @return true if the position is valid, false otherwise
     */
    private boolean isValidPlacement(WorldPosition candidate, Set<WorldPosition> existingTraps) {
        for (WorldPosition existing : existingTraps) {
            // Check distance (must be within 4 tiles)
            double distance = candidate.distanceTo(existing);
            if (distance > MAX_DISTANCE_FROM_EXISTING) {
                continue; // This existing trap is too far away to matter
            }
            
            // Check if candidate is in a cardinal direction from existing trap
            if (isCardinalDirection(candidate, existing)) {
                return false; // Invalid: in cardinal direction
            }
            
            // Check if candidate is on the same tile (shouldn't happen, but safety check)
            if (distance == 0) {
                return false; // Invalid: same position
            }
        }
        
        return true; // Valid position
    }
    
    /**
     * Checks if two positions are in cardinal directions from each other.
     * Cardinal directions are North, South, East, West (not diagonal).
     * 
     * @param pos1 First position
     * @param pos2 Second position
     * @return true if positions are in cardinal directions
     */
    private boolean isCardinalDirection(WorldPosition pos1, WorldPosition pos2) {
        int deltaX = pos1.getX() - pos2.getX();
        int deltaY = pos1.getY() - pos2.getY();
        
        // Cardinal directions have either deltaX = 0 OR deltaY = 0, but not both
        // (both being 0 means same position, both being non-zero means diagonal)
        return (deltaX == 0 && deltaY != 0) || (deltaY == 0 && deltaX != 0);
    }
    
    /**
     * Gets a random position within any of the hunting zones.
     * 
     * @param huntingZones List of available hunting zones
     * @return A random WorldPosition within the zones, or null if none available
     */
    private WorldPosition getRandomPositionInZones(List<RectangleArea> huntingZones) {
        if (huntingZones.isEmpty()) {
            return null;
        }
        
        // Pick a random zone
        RectangleArea randomZone = huntingZones.get(RandomUtils.uniformRandom(0, huntingZones.size() - 1));
        return randomZone.getRandomPosition();
    }
    
    @Override
    public String getStrategyName() {
        return "No Cardinal";
    }
    
    @Override
    public String getDescription() {
        return "Avoids placing traps in cardinal directions (N/S/E/W) from existing traps. " +
               "Allows diagonal placement and keeps traps within " + MAX_DISTANCE_FROM_EXISTING + " tiles.";
    }
}