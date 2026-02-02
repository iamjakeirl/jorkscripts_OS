package com.jork.script.jorkHunter.utils.placement;

import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.location.area.impl.RectangleArea;

import java.util.List;
import java.util.Set;

/**
 * Interface for trap placement strategies that determine where new traps should be placed.
 * Strategies can implement different patterns and rules for trap positioning.
 */
public interface TrapPlacementStrategy {
    
    /**
     * Finds the next suitable position for placing a trap based on the strategy's rules.
     * 
     * @param playerPos Current player position
     * @param huntingZones List of valid hunting zones where traps can be placed
     * @param existingTraps Set of positions where traps are already placed
     * @return A suitable WorldPosition for the next trap, or null if no suitable position found
     */
    WorldPosition findNextTrapPosition(WorldPosition playerPos, 
                                     List<RectangleArea> huntingZones,
                                     Set<WorldPosition> existingTraps);
    
    /**
     * Validates if a position is suitable for trap placement according to this strategy's rules.
     * 
     * @param position The position to validate
     * @param existingTraps Set of positions where traps are already placed
     * @return true if the position is valid for trap placement, false otherwise
     */
    boolean isValidPosition(WorldPosition position, Set<WorldPosition> existingTraps);
    
    /**
     * Gets the display name of this strategy for UI purposes.
     * 
     * @return The strategy name
     */
    String getStrategyName();
    
    /**
     * Gets a description of what this strategy does.
     * 
     * @return A brief description of the strategy's behavior
     */
    default String getDescription() {
        return "Trap placement strategy: " + getStrategyName();
    }
}