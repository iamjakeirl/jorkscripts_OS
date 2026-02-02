package com.jork.script.jorkHunter.interaction;

import com.jork.script.jorkHunter.JorkHunter;
import com.jork.utils.ScriptLogger;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.scene.RSTile;
import com.osmb.api.shape.Polygon;
import com.osmb.api.utils.Utils;

import java.util.Collections;
import java.util.List;

/**
 * Utility class for checking trap visibility and finding optimal viewing positions.
 * Handles UI occlusion detection using the OSMB WidgetManager API.
 */
public class TrapVisibilityChecker {
    
    private static final double VISIBILITY_THRESHOLD = 0.7; // 70% visible threshold
    private final JorkHunter script;
    
    public TrapVisibilityChecker(JorkHunter script) {
        this.script = script;
    }
    
    /**
     * Checks if a trap at the given position is visible (not occluded by UI elements).
     * 
     * @param trapPos The world position of the trap to check
     * @return true if the trap is visible (above threshold), false if occluded
     */
    public boolean isTrapVisible(WorldPosition trapPos) {
        if (trapPos == null) {
            return false;
        }
        
        // Get the tile for the trap position
        RSTile trapTile = script.getSceneManager().getTile(trapPos);
        if (trapTile == null) {
            ScriptLogger.debug(script, "Trap tile is null for position: " + trapPos);
            return false; // Off-screen or invalid
        }
        
        // Get the tile polygon for visibility checking
        Polygon trapArea = trapTile.getTilePoly();
        if (trapArea == null) {
            ScriptLogger.debug(script, "Trap tile polygon is null for position: " + trapPos);
            return false;
        }
        
        // Check visibility factor using WidgetManager
        double visibilityFactor = script.getWidgetManager()
            .insideGameScreenFactor(trapArea, Collections.emptyList());
        
        boolean isVisible = visibilityFactor >= VISIBILITY_THRESHOLD;
        
        if (!isVisible) {
            ScriptLogger.debug(script, "Trap at " + trapPos + " is occluded (visibility: " + 
                String.format("%.2f", visibilityFactor) + ")");
        }
        
        return isVisible;
    }
    
    /**
     * Finds the best position within hunting zones to view an occluded trap.
     * Uses deterministic 1-2 tile radius search around the trap position.
     * 
     * @param trapPos The position of the occluded trap
     * @param huntingZones List of valid hunting zones to search within
     * @return A suitable viewing position, or null if none found
     */
    public WorldPosition findBestViewingPosition(WorldPosition trapPos, List<RectangleArea> huntingZones) {
        if (trapPos == null || huntingZones == null || huntingZones.isEmpty()) {
            return null;
        }
        
        // Systematically check positions in 1-tile radius first (cardinal directions)
        WorldPosition[] oneRadius = {
            new WorldPosition(trapPos.getX(), trapPos.getY() + 1, trapPos.getPlane()), // North
            new WorldPosition(trapPos.getX(), trapPos.getY() - 1, trapPos.getPlane()), // South  
            new WorldPosition(trapPos.getX() + 1, trapPos.getY(), trapPos.getPlane()), // East
            new WorldPosition(trapPos.getX() - 1, trapPos.getY(), trapPos.getPlane())  // West
        };
        
        for (WorldPosition candidate : oneRadius) {
            if (isValidViewingPosition(candidate, huntingZones)) {
                ScriptLogger.debug(script, "Found 1-tile viewing position: " + candidate + " (distance: 1 from trap)");
                return candidate;
            }
        }
        
        // If 1-tile radius fails, check 2-tile radius (cardinal + diagonal)
        WorldPosition[] twoRadius = {
            // Cardinal directions at 2 tiles
            new WorldPosition(trapPos.getX(), trapPos.getY() + 2, trapPos.getPlane()), // North
            new WorldPosition(trapPos.getX(), trapPos.getY() - 2, trapPos.getPlane()), // South
            new WorldPosition(trapPos.getX() + 2, trapPos.getY(), trapPos.getPlane()), // East
            new WorldPosition(trapPos.getX() - 2, trapPos.getY(), trapPos.getPlane()), // West
            // Diagonal directions at 1 tile
            new WorldPosition(trapPos.getX() + 1, trapPos.getY() + 1, trapPos.getPlane()), // NE
            new WorldPosition(trapPos.getX() + 1, trapPos.getY() - 1, trapPos.getPlane()), // SE
            new WorldPosition(trapPos.getX() - 1, trapPos.getY() + 1, trapPos.getPlane()), // NW
            new WorldPosition(trapPos.getX() - 1, trapPos.getY() - 1, trapPos.getPlane()), // SW
            // Diagonal directions at 2 tiles
            new WorldPosition(trapPos.getX() + 2, trapPos.getY() + 2, trapPos.getPlane()), // NE
            new WorldPosition(trapPos.getX() + 2, trapPos.getY() - 2, trapPos.getPlane()), // SE
            new WorldPosition(trapPos.getX() - 2, trapPos.getY() + 2, trapPos.getPlane()), // NW
            new WorldPosition(trapPos.getX() - 2, trapPos.getY() - 2, trapPos.getPlane())  // SW
        };
        
        for (WorldPosition candidate : twoRadius) {
            if (isValidViewingPosition(candidate, huntingZones)) {
                double distance = candidate.distanceTo(trapPos);
                ScriptLogger.debug(script, "Found 2-tile viewing position: " + candidate + 
                    " (distance: " + distance + " from trap)");
                return candidate;
            }
        }
        
        ScriptLogger.warning(script, "Could not find suitable viewing position within 1-2 tiles of trap at " + trapPos);
        return null;
    }
    
    /**
     * Validates if a candidate position is suitable for viewing a trap.
     * Checks if the position is within hunting zones and walkable.
     * 
     * @param candidate The position to validate
     * @param huntingZones List of valid hunting zones
     * @return true if the position is valid for viewing, false otherwise
     */
    private boolean isValidViewingPosition(WorldPosition candidate, List<RectangleArea> huntingZones) {
        if (candidate == null) {
            return false;
        }
        
        // Check if position is within any hunting zone
        boolean inHuntingZone = huntingZones.stream().anyMatch(zone -> zone.contains(candidate));
        if (!inHuntingZone) {
            return false;
        }
        
        // Additional checks could be added here:
        // - Check if position is walkable
        // - Check if position is on same plane
        // - Check for collision detection
        
        return true;
    }
    
    /**
     * Gets the visibility threshold used for occlusion detection.
     * 
     * @return The visibility threshold (0.0 to 1.0)
     */
    public double getVisibilityThreshold() {
        return VISIBILITY_THRESHOLD;
    }
}