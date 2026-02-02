package com.jork.script.jorkHunter;

import com.osmb.api.location.area.impl.RectangleArea;
import com.jork.script.jorkHunter.utils.placement.TrapPlacementStrategy;
import com.jork.script.jorkHunter.utils.placement.NoCardinalStrategy;
import java.util.List;

/**
 * Represents a specific hunting location, including its name, region ID, the valid zones within it,
 * and an optional preferred trap placement strategy.
 *
 * @param name               The display name of the location (e.g., "Verdant Valley").
 * @param regionId           The main region ID for this location.
 * @param huntingZones       A list of {@link RectangleArea}s where hunting is permitted.
 * @param preferredStrategy  The preferred trap placement strategy for this location (optional).
 */
public record HuntingLocation(String name, int regionId, List<RectangleArea> huntingZones, TrapPlacementStrategy preferredStrategy) {
    
    /**
     * Convenience constructor that uses the default NoCardinal strategy.
     */
    public HuntingLocation(String name, int regionId, List<RectangleArea> huntingZones) {
        this(name, regionId, huntingZones, new NoCardinalStrategy());
    }
    
    /**
     * Gets the preferred strategy, falling back to NoCardinal if none specified.
     * @return The preferred trap placement strategy
     */
    public TrapPlacementStrategy getPreferredStrategy() {
        return preferredStrategy != null ? preferredStrategy : new NoCardinalStrategy();
    }
} 