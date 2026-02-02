package com.jork.script.WineCollector.tasks;

import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.item.ItemGroupResult;
import java.util.Set;
import com.osmb.api.profile.ProfileManager;
import com.osmb.api.utils.RandomUtils;
import com.jork.utils.ScriptLogger;
import com.jork.script.WineCollector.WineCollector;
import com.jork.script.WineCollector.config.WineConfig;

import com.osmb.api.shape.Polygon;
import com.osmb.api.visual.SearchablePixel;
import com.osmb.api.visual.color.ColorModel;
import com.osmb.api.visual.color.tolerance.impl.SingleThresholdComparator;
import com.osmb.api.input.MenuEntry;

import java.awt.Point;

public class CollectTask implements Task {

    private final WineCollector script;

    public CollectTask(WineCollector script) {
        this.script = script;
    }

    /**
     * Detects if wine is present at the spawn position using color detection.
     * @param winePos The world position to check for wine
     * @return true if wine color pixels are found, false otherwise
     */
    private boolean detectWine(WorldPosition winePos) {
        // Get tile cube for wine position
        Polygon wineCube = script.getSceneProjector().getTileCube(winePos, WineConfig.WINE_CUBE_HEIGHT);
        if (wineCube == null) {
            ScriptLogger.debug(script, "Wine cube not on screen");
            return false;
        }

        // Resize cube to fit wine bottle model better
        Polygon resizedCube = wineCube.getResized(WineConfig.WINE_CUBE_RESIZE_FACTOR);

        // Define wine pixel color with tolerance
        SearchablePixel winePixel = new SearchablePixel(
            WineConfig.WINE_BOTTLE_COLOR,
            new SingleThresholdComparator(WineConfig.WINE_COLOR_TOLERANCE),
            ColorModel.HSL  // HSL for better lighting tolerance
        );

        // Search for wine color in the cube
        Point foundPixel = script.getPixelAnalyzer().findPixel(resizedCube, winePixel);

        boolean detected = foundPixel != null;
        ScriptLogger.debug(script, "Wine detection result: " + (detected ? "FOUND" : "NOT FOUND"));

        return detected;
    }

    /**
     * Attempts to pick up wine using menu hook interaction.
     * @param winePos The world position of the wine
     * @return true if wine was successfully picked up, false otherwise
     */
    private boolean pickupWine(WorldPosition winePos) {
        // Get tile cube
        Polygon wineCube = script.getSceneProjector().getTileCube(winePos, WineConfig.WINE_CUBE_HEIGHT);
        if (wineCube == null) {
            ScriptLogger.warning(script, "Wine cube not visible for pickup");
            return false;
        }

        // Resize for better hit detection
        Polygon resizedCube = wineCube.getResized(WineConfig.WINE_CUBE_RESIZE_FACTOR);

        // Track if we found the correct menu entry
        boolean[] wineEntryFound = {false};

        // Tap with menu hook
        boolean tapped = script.submitHumanTask(() ->
            script.getFinger().tapGameScreen(resizedCube, (menuEntries) -> {
                // Search for "Take" action on "Eclipse red"
                for (MenuEntry entry : menuEntries) {
                    String action = entry.getAction();
                    String rawText = entry.getRawText();

                    if (action != null && rawText != null) {
                        String actionLower = action.toLowerCase();
                        String rawLower = rawText.toLowerCase();

                        if (actionLower.contains("take") && rawLower.contains("eclipse red")) {
                            wineEntryFound[0] = true;
                            ScriptLogger.actionAttempt(script, "Taking " + WineConfig.WINE_NAME);
                            return entry;  // Select this menu entry
                        }
                    }
                }

                // No wine found in menu
                ScriptLogger.warning(script, "Wine menu entry not found - will force hop");
                return null;  // Cancel menu
            }), 3000);

        if (!tapped) {
            ScriptLogger.warning(script, "Failed to tap wine cube");
            return false;
        }

        return wineEntryFound[0];
    }

    /**
     * Gets the current count of wines in inventory.
     * @return Number of wines, or 0 if none
     */
    private int getWineCountInInventory() {
        ItemGroupResult result = script.getWidgetManager().getInventory().search(Set.of(WineConfig.WINE_ID));
        return (result != null) ? result.getAmount(WineConfig.WINE_ID) : 0;
    }

    @Override
    public boolean canExecute() {
        // Always allow CollectTask to run - inventory check happens after wine detection
        return true;
    }

    @Override
    public int execute() {
        // Assumes NavigateTask has already positioned us on the top floor

        // PRIORITY CHECK: Chat hop trigger overrides all other logic
        if (script.isChatHopTriggered()) {
            ScriptLogger.warning(script, "Chat-based hop triggered - forcing world hop");
            script.clearChatHopFlag();

            ProfileManager profileManager = script.getProfileManager();
            if (profileManager != null && profileManager.hasHopProfile()) {
                boolean hopInitiated = profileManager.forceHop();
                if (hopInitiated) {
                    ScriptLogger.info(script, "World hop initiated successfully");
                } else {
                    ScriptLogger.warning(script, "Failed to initiate world hop");
                }
            } else {
                ScriptLogger.warning(script, "No hop profile available - cannot hop");
            }

            return WineConfig.POLL_DELAY_WORLD_HOP;
        }

        // Wine detection and collection
        if (detectWine(WineConfig.WINE_SPAWN_POSITION)) {
            ScriptLogger.info(script, "Wine detected at spawn position");

            // Human delay to simulate checking inventory visually (randomized timeout)
            int inventoryCheckDelay = RandomUtils.weightedRandom(
                WineConfig.INVENTORY_CHECK_DELAY - 100,
                WineConfig.INVENTORY_CHECK_DELAY + 100
            );
            script.pollFramesHuman(() -> true, inventoryCheckDelay);

            // Check inventory BEFORE pickup attempt
            ItemGroupResult result = script.getWidgetManager().getInventory().search(Set.of());
            if (result != null && result.getOccupiedSlotCount() >= WineConfig.INVENTORY_SIZE) {
                ScriptLogger.info(script, "Inventory full - will bank wines and continue");
                script.setShouldBank(true);
                return WineConfig.POLL_DELAY_MEDIUM;
            }

            // Get wine count BEFORE pickup
            int winesBefore = getWineCountInInventory();
            ScriptLogger.debug(script, "Wine count before pickup: " + winesBefore);

            // Human pause before interacting with the wine
            script.pollFramesHuman(() -> true, RandomUtils.uniformRandom(300, 500));

            // Attempt pickup
            boolean menuFound = pickupWine(WineConfig.WINE_SPAWN_POSITION);

            if (menuFound) {
                // Wait for wine to appear in inventory (verify count increased)
                boolean wineCollected = script.submitTask(() -> {
                    int winesNow = getWineCountInInventory();
                    return winesNow > winesBefore;
                }, WineConfig.PICKUP_TIMEOUT);

                if (wineCollected) {
                    script.incrementWineCount();
                    ScriptLogger.info(script, "Wine collected successfully (inventory increased)");
                    return WineConfig.POLL_DELAY_SHORT;
                } else {
                    // Menu was found but inventory didn't increase
                    ScriptLogger.warning(script, "Wine pickup failed - inventory didn't increase (likely taken by another player)");
                    // Fall through to world hop logic below
                }
            } else {
                // Menu entry not found
                ScriptLogger.warning(script, "Failed to pick up wine - menu entry not found");
                // Fall through to world hop logic below
            }
        }

        // No wine found OR pickup failed - hop worlds
        ProfileManager profileManager = script.getProfileManager();
        if (profileManager != null && profileManager.hasHopProfile()) {
            profileManager.forceHop();
        }

        return WineConfig.POLL_DELAY_WORLD_HOP;
    }
}
