package com.jork.script.jorkHunter.utils.placement;

import com.jork.script.jorkHunter.JorkHunter;
import com.jork.utils.ScriptLogger;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.utils.RandomUtils;

import java.util.*;

/**
 * Custom tile picker trap placement strategy.
 * Allows users to select specific trap positions using the enhanced tile picker.
 * Places traps at user-selected positions with humanized nearest-position logic.
 * Creates a bounding rectangle area from the selected positions for the hunting zone.
 */
public class CustomTilePickerStrategy implements TrapPlacementStrategy {
    
    private final JorkHunter script;
    private final List<WorldPosition> selectedPositions;
    private final RectangleArea boundingArea;
    
    /**
     * Creates a custom tile picker strategy with user-selected positions.
     * 
     * @param script The script instance
     * @param selectedPositions List of positions selected by the user via tile picker
     */
    public CustomTilePickerStrategy(JorkHunter script, List<WorldPosition> selectedPositions) {
        this.script = script;
        this.selectedPositions = new ArrayList<>(selectedPositions);
        this.boundingArea = createBoundingArea(selectedPositions);
        
        ScriptLogger.info(script, "Custom tile picker strategy initialized with " + 
                         selectedPositions.size() + " positions");
        if (boundingArea != null) {
            ScriptLogger.debug(script, "Bounding area: " + boundingArea.getX() + "," + 
                             boundingArea.getY() + " size " + boundingArea.getWidth() + "x" + 
                             boundingArea.getHeight());
        }
    }
    
    @Override
    public WorldPosition findNextTrapPosition(WorldPosition playerPos, 
                                            List<RectangleArea> huntingZones,
                                            Set<WorldPosition> existingTraps) {
        if (selectedPositions.isEmpty()) {
            ScriptLogger.warning(script, "No custom positions available");
            return null;
        }
        
        // Find nearest unoccupied position from selected positions (humanized behavior)
        WorldPosition nearestPosition = null;
        double nearestDistance = Double.MAX_VALUE;
        List<WorldPosition> equalDistancePositions = new ArrayList<>();
        
        for (WorldPosition position : selectedPositions) {
            // Skip if position is already occupied
            if (existingTraps != null && existingTraps.contains(position)) {
                continue;
            }
            
            if (playerPos == null) {
                // No player position, return first available
                ScriptLogger.debug(script, "No player position, using first available custom position: " + position);
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
        
        // If multiple positions at same distance, pick randomly for human-like variation
        if (equalDistancePositions.size() > 1) {
            int randomIndex = RandomUtils.uniformRandom(0, equalDistancePositions.size() - 1);
            nearestPosition = equalDistancePositions.get(randomIndex);
            ScriptLogger.debug(script, "Multiple custom positions at distance " + nearestDistance + 
                             ", randomly selected: " + nearestPosition);
        } else if (nearestPosition != null) {
            ScriptLogger.debug(script, "Next custom placement (nearest): " + nearestPosition + 
                             " at distance " + nearestDistance);
        }
        
        if (nearestPosition == null) {
            ScriptLogger.debug(script, "All custom positions occupied");
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
        
        // Check if position is one of the user-selected positions
        return selectedPositions.contains(position);
    }
    
    @Override
    public String getStrategyName() {
        return "Custom Tile Picker";
    }
    
    @Override
    public String getDescription() {
        return "Places traps at " + selectedPositions.size() + 
               " user-selected positions. Traps are placed at the nearest available position for human-like behavior.";
    }
    
    /**
     * Creates a bounding rectangle area that encompasses all selected positions.
     * 
     * @param positions The list of selected positions
     * @return A RectangleArea containing all positions, or null if positions is empty
     */
    private RectangleArea createBoundingArea(List<WorldPosition> positions) {
        if (positions == null || positions.isEmpty()) {
            return null;
        }
        
        // Find min/max coordinates
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int plane = positions.get(0).getPlane(); // Assume all on same plane
        
        for (WorldPosition pos : positions) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
        }
        
        // Create rectangle area with 1 tile padding on each side
        int padding = 1;
        return new RectangleArea(
            minX - padding,
            minY - padding,
            (maxX - minX) + 1 + (padding * 2),
            (maxY - minY) + 1 + (padding * 2),
            plane
        );
    }
    
    /**
     * Gets the bounding area created from the selected positions.
     * 
     * @return The bounding rectangle area
     */
    public RectangleArea getBoundingArea() {
        return boundingArea;
    }
    
    /**
     * Gets the list of user-selected positions.
     * 
     * @return List of selected positions
     */
    public List<WorldPosition> getSelectedPositions() {
        return new ArrayList<>(selectedPositions);
    }
}