package com.jork.script.jorkHunter.tasks.base;

import com.jork.script.jorkHunter.JorkHunter;
import com.jork.script.jorkHunter.trap.TrapType;
import com.jork.script.jorkHunter.utils.tasks.Task;
import com.jork.script.jorkHunter.utils.placement.TrapPlacementStrategy;
import com.jork.script.jorkHunter.interaction.TrapInteractionHandler;
import com.jork.script.jorkHunter.state.TrapStateManager;
import com.jork.script.jorkHunter.state.TrapInfo;
import com.jork.utils.ScriptLogger;
import com.jork.utils.Navigation;

import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.item.ItemGroupResult;
import com.osmb.api.item.ItemSearchResult;
import com.osmb.api.walker.WalkConfig;
import com.osmb.api.utils.RandomUtils;

import java.util.List;
import java.util.Set;

/**
 * Abstract base class for hunting tasks.
 * Provides common functionality for trap-based hunting scripts.
 */
public abstract class AbstractHuntingTask implements Task {
    
    protected final JorkHunter script;
    protected final TrapType trapType;
    protected final int maxTraps;
    protected final List<RectangleArea> huntingZones;
    protected final TrapStateManager trapManager;
    protected final TrapPlacementStrategy placementStrategy;
    protected final TrapInteractionHandler interactionHandler;
    protected final Navigation navigation;
    protected final WalkConfig walkConfigExact;
    protected final WalkConfig walkConfigApprox;
    
    public AbstractHuntingTask(JorkHunter script, TrapType trapType, int maxTraps, 
                               List<RectangleArea> huntingZones, TrapPlacementStrategy placementStrategy) {
        this.script = script;
        this.trapType = trapType;
        this.maxTraps = maxTraps;
        this.huntingZones = huntingZones;
        this.placementStrategy = placementStrategy;
        this.trapManager = new TrapStateManager(script, trapType);
        this.interactionHandler = new TrapInteractionHandler(script);
        this.navigation = new Navigation(script);
        
        // Configure walker with exact positioning for trap placement
        this.walkConfigExact = new WalkConfig.Builder()
            .breakDistance(0)
            .timeout(10000)
            .build();
        
        // Configure walker with 2-tile tolerance for visibility and general movement
        this.walkConfigApprox = new WalkConfig.Builder()
            .breakDistance(2)
            .timeout(10000)
            .build();
        
        // Ensure a clean state every time the task is created
        trapManager.clearAllTraps();
        
        ScriptLogger.info(script, getTaskName() + " initialized for " + trapType.name() + 
                         " with max traps: " + maxTraps + " using " + 
                         placementStrategy.getStrategyName() + " strategy.");
    }
    
    /**
     * Get the name of this task for logging.
     */
    protected abstract String getTaskName();
    
    /**
     * Check if there are trap supplies in inventory.
     */
    protected boolean hasTrapSupplies() {
        return getTrapFromInventory() != null;
    }
    
    /**
     * Get a trap item from inventory.
     */
    protected ItemSearchResult getTrapFromInventory() {
        ItemGroupResult inventoryResult = script.getItemManager().scanItemGroup(
            script.getWidgetManager().getInventory(), 
            Set.of(trapType.getItemId())
        );
        if (inventoryResult == null) {
            return null;
        }
        return inventoryResult.getItem(trapType.getItemId());
    }
    
    /**
     * Get the count of traps in inventory.
     */
    protected int getTrapCountInInventory() {
        ItemGroupResult inventoryResult = script.getItemManager().scanItemGroup(
            script.getWidgetManager().getInventory(), 
            Set.of(trapType.getItemId())
        );
        if (inventoryResult == null) {
            ScriptLogger.debug(script, "Could not scan inventory for trap count");
            return 0;
        }
        return inventoryResult.getAmount(trapType.getItemId());
    }
    
    /**
     * Find a safe random position within hunting zones.
     */
    protected WorldPosition findSafeRandomPosition(Set<WorldPosition> existingTraps) {
        // Add safety check for empty hunting zones
        if (huntingZones == null || huntingZones.isEmpty()) {
            ScriptLogger.error(script, "CRITICAL: No hunting zones available for trap placement!");
            ScriptLogger.error(script, "This usually means the custom anchor selection was cancelled or failed.");
            ScriptLogger.error(script, "Please restart the script and select a valid hunting location.");
            script.stop();  // Stop the script
            return null;
        }
        
        for (int attempt = 0; attempt < 10; attempt++) {
            RectangleArea randomZone = huntingZones.get(RandomUtils.uniformRandom(0, huntingZones.size() - 1));
            WorldPosition candidate = randomZone.getRandomPosition();
            
            if (candidate != null && !existingTraps.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }
    
    /**
     * Get the center of the hunting area.
     */
    protected WorldPosition getHuntingAreaCenter() {
        if (huntingZones == null || huntingZones.isEmpty()) {
            return null;
        }
        
        RectangleArea firstZone = huntingZones.get(0);
        if (firstZone == null) {
            return null;
        }
        
        int centerX = firstZone.getX() + (firstZone.getWidth() / 2);
        int centerY = firstZone.getY() + (firstZone.getHeight() / 2);
        
        return new WorldPosition(centerX, centerY, firstZone.getPlane());
    }
    
    /**
     * Get the right edge position of the hunting area.
     */
    protected WorldPosition getRightEdgePosition() {
        if (huntingZones == null || huntingZones.isEmpty()) {
            return null;
        }
        
        RectangleArea firstZone = huntingZones.get(0);
        if (firstZone == null) {
            return null;
        }
        
        WorldPosition currentPos = script.getWorldPosition();
        if (currentPos == null) {
            int rightX = firstZone.getX() + firstZone.getWidth() - 1;
            int centerY = firstZone.getY() + (firstZone.getHeight() / 2);
            return new WorldPosition(rightX, centerY, firstZone.getPlane());
        }
        
        int rightX = firstZone.getX() + firstZone.getWidth() - 1;
        int targetY = currentPos.getY();
        
        // Clamp Y to zone bounds
        int minY = firstZone.getY();
        int maxY = firstZone.getY() + firstZone.getHeight() - 1;
        targetY = Math.max(minY, Math.min(maxY, targetY));
        
        return new WorldPosition(rightX, targetY, firstZone.getPlane());
    }
    
    /**
     * Provides access to the TrapStateManager.
     */
    public TrapStateManager getTrapStateManager() {
        return trapManager;
    }
    
    /**
     * Gets the trap type being used.
     */
    public TrapType getTrapType() {
        return trapType;
    }
    
    /**
     * Gets the placement strategy being used.
     */
    public TrapPlacementStrategy getPlacementStrategy() {
        return placementStrategy;
    }
    
    /**
     * Gets the interaction handler used for trap interactions.
     */
    public TrapInteractionHandler getInteractionHandler() {
        return interactionHandler;
    }
    
    @Override
    public boolean canExecute() {
        return true;
    }
}