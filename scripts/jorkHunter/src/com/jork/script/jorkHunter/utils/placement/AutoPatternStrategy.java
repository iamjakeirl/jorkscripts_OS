package com.jork.script.jorkHunter.utils.placement;

import com.jork.script.jorkHunter.JorkHunter;
import com.jork.script.jorkHunter.tasks.TrapTask;
import com.jork.utils.ScriptLogger;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.location.area.impl.RectangleArea;

import java.util.List;
import java.util.Set;

/**
 * Auto pattern selection strategy.
 * Automatically selects the optimal trap placement pattern based on max trap count:
 * - 1-2 traps: Line pattern (random orientation)
 * - 3 traps: L-Pattern
 * - 4 traps: Cross pattern
 * - 5 traps: X-Pattern
 */
public class AutoPatternStrategy implements TrapPlacementStrategy {
    
    private final JorkHunter script;
    private final TrapTask huntTask;
    private final WorldPosition customAnchor;
    private final int maxTraps;
    private final TrapPlacementStrategy delegate;
    
    /**
     * Creates an auto pattern strategy that delegates to the optimal pattern.
     * 
     * @param script The script instance
     * @param huntTask The hunting task (for X-Pattern compatibility)
     * @param customAnchor The custom anchor position
     * @param maxTraps Maximum number of traps
     */
    public AutoPatternStrategy(JorkHunter script, TrapTask huntTask, WorldPosition customAnchor, int maxTraps) {
        this.script = script;
        this.huntTask = huntTask;
        this.customAnchor = customAnchor;
        this.maxTraps = maxTraps;
        
        // Select appropriate strategy based on trap count
        this.delegate = selectOptimalStrategy();
        
        ScriptLogger.info(script, "Auto pattern selected: " + delegate.getStrategyName() + 
                         " for " + maxTraps + " traps");
    }
    
    /**
     * Selects the optimal strategy based on max trap count.
     */
    private TrapPlacementStrategy selectOptimalStrategy() {
        switch (maxTraps) {
            case 1:
            case 2:
                // Line pattern with random orientation
                return new LinePatternStrategy(script, customAnchor, maxTraps, 
                                              LinePatternStrategy.Orientation.RANDOM);
            
            case 3:
                // L-Pattern is optimal for 3 traps
                return new LPatternStrategy(script, customAnchor, maxTraps);
            
            case 4:
                // Cross pattern for 4 traps (cardinals only)
                return new CrossPatternStrategy(script, customAnchor, maxTraps);
            
            case 5:
            default:
                // X-Pattern for 5 traps (maximum coverage)
                // X-Pattern constructor: (script, huntTask, maxCenterDistance, recenterOnEmpty, customAnchor)
                return new XPatternStrategy(script, huntTask, 0, false, customAnchor);
        }
    }
    
    @Override
    public WorldPosition findNextTrapPosition(WorldPosition playerPos, 
                                            List<RectangleArea> huntingZones,
                                            Set<WorldPosition> existingTraps) {
        return delegate.findNextTrapPosition(playerPos, huntingZones, existingTraps);
    }
    
    @Override
    public boolean isValidPosition(WorldPosition position, Set<WorldPosition> existingTraps) {
        return delegate.isValidPosition(position, existingTraps);
    }
    
    @Override
    public String getStrategyName() {
        return "Auto (" + delegate.getStrategyName() + ")";
    }
    
    @Override
    public String getDescription() {
        return "Automatically selected " + delegate.getStrategyName() + " pattern for " + 
               maxTraps + " traps. " + delegate.getDescription();
    }
}