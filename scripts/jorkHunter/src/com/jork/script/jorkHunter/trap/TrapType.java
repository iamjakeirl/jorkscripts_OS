package com.jork.script.jorkHunter.trap;

import com.jork.script.jorkHunter.state.TrapState;
import com.osmb.api.item.ItemID;
import com.osmb.api.visual.SearchablePixel;
import com.osmb.api.visual.color.ColorModel;
import com.osmb.api.visual.color.tolerance.impl.SingleThresholdComparator;

/**
 * Enum defining all trap types with their specific configurations.
 * Each trap type contains all the data needed for TrapStateManager to function generically.
 */
public enum TrapType {
    BIRD_SNARE(
        ItemID.BIRD_SNARE,
        "bird snare",
        // Standing trap pixel clusters (multiple sets for robustness)
        new SearchablePixel[][] {
            // Primary cluster (original - top/middle of trap)
            {
                new SearchablePixel(-12699078, new SingleThresholdComparator(2), ColorModel.HSL),
                new SearchablePixel(-4477010, new SingleThresholdComparator(2), ColorModel.HSL),
                new SearchablePixel(-5990506, new SingleThresholdComparator(2), ColorModel.HSL),
                new SearchablePixel(-12308192, new SingleThresholdComparator(2), ColorModel.HSL),
                new SearchablePixel(-2511756, new SingleThresholdComparator(2), ColorModel.HSL),
                new SearchablePixel(-5735094, new SingleThresholdComparator(2), ColorModel.HSL),
                new SearchablePixel(-11321305, new SingleThresholdComparator(2), ColorModel.HSL),
            },
            // Secondary cluster (to be added - different part of trap)
            // TODO: Add secondary pixel cluster here
            
            // Tertiary cluster (to be added - another distinct part)
            // TODO: Add tertiary pixel cluster here
        },
        // Collapsed trap pixel clusters (multiple sets for robustness)
        new SearchablePixel[][] {
            // Primary cluster (original)
            {
                new SearchablePixel(-5201246, new SingleThresholdComparator(2), ColorModel.HSL),
                new SearchablePixel(-12569314, new SingleThresholdComparator(2), ColorModel.HSL),
                new SearchablePixel(-7705268, new SingleThresholdComparator(2), ColorModel.HSL),
                new SearchablePixel(-9153995, new SingleThresholdComparator(2), ColorModel.HSL),
            },
            // Secondary cluster (to be added)
            // TODO: Add secondary collapsed cluster here
        },
        70,    // Yellow circle Z-offset
        -102,  // Finished circle Z-offset (Green/Red)
        1000,  // Collapse grace period in ms
        5,     // Min cluster size
        5.0,   // Cluster distance
        new String[] { "lay" },  // Inventory action
        new String[] { "check", "retrieve", "pick-up", "take", "dismantle" }, // Success trap actions (defensive)
        new String[] { "dismantle", "check", "pick-up", "take", "retrieve" }, // Failed trap actions (defensive)
        new String[] { "lay", "pick-up", "take", "check", "dismantle", "retrieve" }, // Collapsed trap actions (lay first for efficiency)
        new String[] {}, // Reset actions (bird snares don't support reset)
        new int[] { ItemID.BONES, ItemID.RAW_BIRD_MEAT }, // Drop items
        1.5, // Tap area Y-scale factor
        TrapStateHandlingMode.BINARY // Use binary mode for bird snares (more robust)
    ),
    
    // Chinchompa hunting with box traps
    CHINCHOMPA(
        ItemID.BOX_TRAP,
        "box trap",
        // Standing trap pixel clusters (to be determined through testing)
        new SearchablePixel[][] {
            // Primary cluster - placeholder, needs real pixel data
            {
                // TODO: Capture actual box trap standing pixels
                new SearchablePixel(-1, new SingleThresholdComparator(2), ColorModel.HSL),
            }
        },
        // Collapsed/shaking trap pixel clusters
        new SearchablePixel[][] {
            // Primary cluster - placeholder, needs real pixel data
            {
                // TODO: Capture actual box trap collapsed/shaking pixels
                new SearchablePixel(-1, new SingleThresholdComparator(2), ColorModel.HSL),
            }
        },
        70,    // Yellow circle Z-offset (may need adjustment)
        -102,  // Finished circle Z-offset (may need adjustment)
        1500,  // Collapse grace period in ms (box traps may be different)
        5,     // Min cluster size
        5.0,   // Cluster distance
        new String[] { "lay" },  // Inventory action
        new String[] { "check", "reset", "take", "dismantle" }, // Success trap actions
        new String[] { "reset", "dismantle", "check", "take" }, // Failed trap actions (dismantle for drain mode)
        new String[] { "lay", "take", "pick-up" }, // Collapsed trap actions (lay first for efficiency)
        new String[] { "reset" }, // Reset actions (chinchompas support reset for finished traps)
        new int[] { 
            ItemID.FERRET  // Drop ferrets when inventory is nearly full
        }, // Drop items (ferrets only - chinchompas and jerboas are kept)
        1.5, // Tap area Y-scale factor
        TrapStateHandlingMode.TERNARY // Use ternary mode for chinchompas to distinguish success/failed
    );
    
    private final int itemId;
    private final String itemName;
    private final SearchablePixel[][] standingPixelClusters;
    private final SearchablePixel[][] collapsedPixelClusters;
    private final int activeZOffset;
    private final int finishedZOffset;
    private final long collapseGracePeriod;
    private final int minClusterSize;
    private final double clusterDistance;
    private final String[] inventoryActions;
    private final String[] successActions;
    private final String[] failedActions;
    private final String[] collapsedActions;
    private final String[] resetActions;
    private final int[] dropItems;
    private final double tapAreaYScale;
    private final TrapStateHandlingMode stateHandlingMode;
    
    TrapType(int itemId, String itemName, SearchablePixel[][] standingPixelClusters, 
             SearchablePixel[][] collapsedPixelClusters, int activeZOffset, int finishedZOffset,
             long collapseGracePeriod, int minClusterSize, double clusterDistance,
             String[] inventoryActions, String[] successActions, String[] failedActions, 
             String[] collapsedActions, String[] resetActions, int[] dropItems, double tapAreaYScale,
             TrapStateHandlingMode stateHandlingMode) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.standingPixelClusters = standingPixelClusters;
        this.collapsedPixelClusters = collapsedPixelClusters;
        this.activeZOffset = activeZOffset;
        this.finishedZOffset = finishedZOffset;
        this.collapseGracePeriod = collapseGracePeriod;
        this.minClusterSize = minClusterSize;
        this.clusterDistance = clusterDistance;
        this.inventoryActions = inventoryActions;
        this.successActions = successActions;
        this.failedActions = failedActions;
        this.collapsedActions = collapsedActions;
        this.resetActions = resetActions;
        this.dropItems = dropItems;
        this.tapAreaYScale = tapAreaYScale;
        this.stateHandlingMode = stateHandlingMode;
    }
    
    // Getters
    public int getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    
    // Get all standing pixel clusters for trying multiple detection methods
    public SearchablePixel[][] getStandingPixelClusters() { return standingPixelClusters; }
    
    // Get all collapsed pixel clusters for trying multiple detection methods
    public SearchablePixel[][] getCollapsedPixelClusters() { return collapsedPixelClusters; }
    
    // Backward compatibility - get primary cluster
    public SearchablePixel[] getStandingPixelCluster() { 
        return standingPixelClusters != null && standingPixelClusters.length > 0 
            ? standingPixelClusters[0] : new SearchablePixel[0];
    }
    
    // Backward compatibility - get primary cluster
    public SearchablePixel[] getCollapsedPixelCluster() { 
        return collapsedPixelClusters != null && collapsedPixelClusters.length > 0
            ? collapsedPixelClusters[0] : new SearchablePixel[0];
    }
    public int getActiveZOffset() { return activeZOffset; }
    public int getFinishedZOffset() { return finishedZOffset; }
    public long getCollapseGracePeriod() { return collapseGracePeriod; }
    public int getMinClusterSize() { return minClusterSize; }
    public double getClusterDistance() { return clusterDistance; }
    public String[] getInventoryActions() { return inventoryActions; }
    public String[] getSuccessActions() { return successActions; }
    public String[] getFailedActions() { return failedActions; }
    public String[] getCollapsedActions() { return collapsedActions; }
    public int[] getDropItems() { return dropItems; }
    public double getTapAreaYScale() { return tapAreaYScale; }
    public TrapStateHandlingMode getStateHandlingMode() { return stateHandlingMode; }
    public String[] getResetActions() { return resetActions; }
    
    /**
     * Checks if this trap type supports the reset action.
     * @return true if reset is supported (has reset actions defined)
     */
    public boolean supportsReset() {
        return resetActions != null && resetActions.length > 0;
    }
    
    /**
     * Gets the appropriate actions for interacting with a trap based on its state.
     * For chinchompas with finished states, prioritizes reset actions if available.
     * @param state The current state of the trap
     * @return Array of action strings that are acceptable for this trap state
     */
    public String[] getActionsForTrapState(TrapState state) {
        return switch (state) {
            case FINISHED -> {
                // Binary mode - if reset is supported, prioritize it
                if (supportsReset()) {
                    yield combineActions(resetActions, successActions, failedActions);
                } else {
                    yield combineActions(successActions, failedActions);
                }
            }
            case FINISHED_SUCCESS -> {
                // Ternary mode - prioritize reset for success states
                if (supportsReset()) {
                    yield combineActions(resetActions, successActions);
                } else {
                    yield successActions;
                }
            }
            case FINISHED_FAILED -> {
                // Ternary mode - prioritize reset for failed states too
                if (supportsReset()) {
                    yield combineActions(resetActions, failedActions);
                } else {
                    yield failedActions;
                }
            }
            case COLLAPSED -> collapsedActions;
            default -> combineActions(successActions, failedActions, collapsedActions);
        };
    }
    
    private String[] combineActions(String[]... arrays) {
        int totalLength = 0;
        for (String[] array : arrays) {
            totalLength += array.length;
        }
        
        String[] result = new String[totalLength];
        int index = 0;
        for (String[] array : arrays) {
            System.arraycopy(array, 0, result, index, array.length);
            index += array.length;
        }
        return result;
    }
}