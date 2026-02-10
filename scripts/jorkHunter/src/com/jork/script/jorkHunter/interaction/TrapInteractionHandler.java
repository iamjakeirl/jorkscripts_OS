package com.jork.script.jorkHunter.interaction;

import com.jork.script.jorkHunter.JorkHunter;
import com.jork.script.jorkHunter.state.TrapInfo;
import com.jork.script.jorkHunter.state.TrapState;
import com.jork.script.jorkHunter.state.TrapFlag;
import com.jork.script.jorkHunter.trap.TrapType;
import com.jork.utils.ScriptLogger;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.visual.PixelCluster;
import com.osmb.api.visual.SearchablePixel;
import com.osmb.api.shape.Polygon;
import com.osmb.api.shape.Rectangle;
import com.osmb.api.scene.RSTile;
import com.osmb.api.visual.PixelCluster.ClusterSearchResult;
import com.osmb.api.utils.RandomUtils;
import com.osmb.api.input.MenuEntry;

import java.util.List;
import java.util.Optional;
import java.awt.Point;

/**
 * Handles all trap interaction logic including pixel detection, menu interaction,
 * and state verification. This class is responsible for the actual UI interaction
 * with traps, separated from state management concerns.
 */
public class TrapInteractionHandler implements InteractionHandler {
    private final JorkHunter script;
    private final TrapVisibilityChecker visibilityChecker;

    // Fields for polygon drawing visualization (chinchompas only)
    private volatile Polygon currentInteractionPolygon = null;
    private static final int POLYGON_COLOR = 0xFF00FF; // Magenta
    private static final double POLYGON_OPACITY = 0.8;

    // Chinchompa polygon transformation parameters
    private static final double CHINCHOMPA_X_SCALE = 0.25;  // 25% width
    private static final double CHINCHOMPA_Y_SCALE = 0.55;  // 55% height
    private static final PolygonAlignment CHINCHOMPA_ALIGNMENT = PolygonAlignment.CENTER_LEFT_EDGE; // Left edge at center

    /**
     * Alignment options for positioning scaled polygons within original bounds
     */
    public enum PolygonAlignment {
        CENTER,           // Center the scaled polygon
        LEFT,             // Align to left edge
        RIGHT,            // Align to right edge
        TOP,              // Align to top edge
        BOTTOM,           // Align to bottom edge
        TOP_LEFT,         // Align to top-left corner
        TOP_RIGHT,        // Align to top-right corner
        BOTTOM_LEFT,      // Align to bottom-left corner
        BOTTOM_RIGHT,     // Align to bottom-right corner
        CENTER_LEFT_EDGE  // Position left edge at center of original
    }

    public TrapInteractionHandler(JorkHunter script) {
        this.script = script;
        this.visibilityChecker = new TrapVisibilityChecker(script);
    }

    @Override
    public InteractionResult interact(TrapInfo trapInfo) {
        WorldPosition position = trapInfo.position();
        TrapState state = trapInfo.state();
        TrapType trapType = trapInfo.trapType();

        ScriptLogger.info(script, "Attempting interaction with " + state + " trap at " + position);

        // Check visibility first
        if (!canInteract(position)) {
            return InteractionResult.movementRequired(position);
        }

        // PRIMARY METHOD: Try TileCube approach first
        // Tile cube will be calculated fresh inside performTileCubeInteraction
        InteractionResult cubeResult = performTileCubeInteraction(trapInfo);
        if (cubeResult.success()) {
            return cubeResult;
        }

        // FALLBACK 1: Try pixel cluster detection if primary method fails
        ScriptLogger.debug(script, "Primary interaction failed, trying pixel cluster detection");
        RSTile trapTile = script.getSceneManager().getTile(position);
        Polygon trapArea = (trapTile != null) ? trapTile.getTilePoly() : null;

        if (trapArea == null) {
            ScriptLogger.debug(script, "Trap tile not on screen at " + position);
            return InteractionResult.movementRequired(position);
        }

        Optional<PixelCluster> trapCluster = findTrapPixels(trapArea, trapType, state);

        if (trapCluster.isPresent()) {
            Rectangle tapArea = createTapArea(trapCluster.get(), trapType);
            return performTapInteraction(tapArea, trapInfo);
        }

        // FALLBACK 2: Return the cube result if all methods failed
        ScriptLogger.debug(script, "All interaction methods failed for trap at " + position);
        return cubeResult;
    }

    @Override
    public boolean canInteract(WorldPosition position) {
        // Check if trap is visible (not occluded by UI)
        if (!visibilityChecker.isTrapVisible(position)) {
            ScriptLogger.debug(script, "Trap at " + position + " is occluded by UI");
            return false;
        }

        // Check if trap is on screen
        RSTile trapTile = script.getSceneManager().getTile(position);
        if (trapTile == null || trapTile.getTilePoly() == null) {
            ScriptLogger.debug(script, "Trap at " + position + " is not on screen");
            return false;
        }

        return true;
    }

    /**
     * Creates an asymmetrically scaled and positioned polygon from a base polygon
     * with custom offset percentages for precise positioning.
     *
     * @param basePoly The original polygon to transform
     * @param xScale Scale factor for X axis (0.0 to 1.0)
     * @param yScale Scale factor for Y axis (0.0 to 1.0)
     * @param xOffsetPercent Where to position left edge (0.0 = left, 0.5 = center, 1.0 = right)
     * @param yOffsetPercent Where to position top edge (0.0 = top, 0.5 = center, 1.0 = bottom)
     * @return A new polygon with asymmetric scaling and custom positioning
     */
    private Polygon getAsymmetricScaledPolygon(Polygon basePoly, double xScale, double yScale,
                                               double xOffsetPercent, double yOffsetPercent) {
        if (basePoly == null) {
            return null;
        }

        // Get the original vertices
        int[] xPoints = basePoly.getXPoints();
        int[] yPoints = basePoly.getYPoints();

        if (xPoints == null || yPoints == null || xPoints.length != yPoints.length) {
            return null;
        }

        // Calculate bounding box of original polygon
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int i = 0; i < xPoints.length; i++) {
            minX = Math.min(minX, xPoints[i]);
            maxX = Math.max(maxX, xPoints[i]);
            minY = Math.min(minY, yPoints[i]);
            maxY = Math.max(maxY, yPoints[i]);
        }

        // Calculate original dimensions
        int originalWidth = maxX - minX;
        int originalHeight = maxY - minY;

        // Calculate scaled dimensions
        int scaledWidth = (int)(originalWidth * xScale);
        int scaledHeight = (int)(originalHeight * yScale);

        // Calculate offset based on percentage
        // xOffsetPercent determines where the LEFT edge of scaled poly goes
        // 0.0 = aligned with left edge, 0.5 = left edge at center, 1.0 = left edge at right (poly off screen)
        int maxXOffset = originalWidth - scaledWidth;
        int maxYOffset = originalHeight - scaledHeight;

        int xOffset = (int)(xOffsetPercent * originalWidth);
        int yOffset = (int)(yOffsetPercent * originalHeight);

        // Clamp offsets to prevent polygon from going completely outside bounds
        xOffset = Math.min(xOffset, maxXOffset);
        yOffset = Math.min(yOffset, maxYOffset);

        // Create new polygon with transformed vertices
        Polygon transformedPoly = new Polygon();

        for (int i = 0; i < xPoints.length; i++) {
            // Get relative position within bounding box (0.0 to 1.0)
            double relX = (double)(xPoints[i] - minX) / originalWidth;
            double relY = (double)(yPoints[i] - minY) / originalHeight;

            // Apply scaling and offset
            int newX = minX + xOffset + (int)(relX * scaledWidth);
            int newY = minY + yOffset + (int)(relY * scaledHeight);

            transformedPoly.addVertex(newX, newY);
        }

        return transformedPoly;
    }

    /**
     * Creates an asymmetrically scaled and positioned polygon from a base polygon.
     * This allows for different scaling factors on X and Y axes, and positioning
     * within the original polygon bounds based on alignment.
     *
     * @param basePoly The original polygon to transform
     * @param xScale Scale factor for X axis (0.0 to 1.0)
     * @param yScale Scale factor for Y axis (0.0 to 1.0)
     * @param alignment How to position the scaled polygon within original bounds
     * @return A new polygon with asymmetric scaling and positioning
     */
    private Polygon getAsymmetricScaledPolygon(Polygon basePoly, double xScale, double yScale, PolygonAlignment alignment) {
        // Convert alignment to offset percentages
        double xOffsetPercent, yOffsetPercent;

        switch (alignment) {
            case CENTER:
                xOffsetPercent = (1.0 - xScale) / 2.0;  // Center horizontally
                yOffsetPercent = (1.0 - yScale) / 2.0;  // Center vertically
                break;
            case LEFT:
                xOffsetPercent = 0.0;
                yOffsetPercent = (1.0 - yScale) / 2.0;
                break;
            case RIGHT:
                xOffsetPercent = 1.0 - xScale;
                yOffsetPercent = (1.0 - yScale) / 2.0;
                break;
            case TOP:
                xOffsetPercent = (1.0 - xScale) / 2.0;
                yOffsetPercent = 0.0;
                break;
            case BOTTOM:
                xOffsetPercent = (1.0 - xScale) / 2.0;
                yOffsetPercent = 1.0 - yScale;
                break;
            case TOP_LEFT:
                xOffsetPercent = 0.0;
                yOffsetPercent = 0.0;
                break;
            case TOP_RIGHT:
                xOffsetPercent = 1.0 - xScale;
                yOffsetPercent = 0.0;
                break;
            case BOTTOM_LEFT:
                xOffsetPercent = 0.0;
                yOffsetPercent = 1.0 - yScale;
                break;
            case BOTTOM_RIGHT:
                xOffsetPercent = 1.0 - xScale;
                yOffsetPercent = 1.0 - yScale;
                break;
            case CENTER_LEFT_EDGE:
                xOffsetPercent = 0.5;  // Left edge at center
                yOffsetPercent = (1.0 - yScale) / 2.0;  // Center vertically
                break;
            default:
                xOffsetPercent = 0.0;
                yOffsetPercent = 0.0;
        }

        // Delegate to the custom offset method
        return getAsymmetricScaledPolygon(basePoly, xScale, yScale, xOffsetPercent, yOffsetPercent);
    }

    @Override
    public InteractionResult verifyTrapState(WorldPosition position) {
        ScriptLogger.debug(script, "Performing blind tap verification at " + position);

        // Use TileCube approach for consistency
        Polygon trapCube = script.getSceneProjector().getTileCube(position, 100);
        if (trapCube == null) {
            ScriptLogger.debug(script, "Trap cube not on screen for verification at " + position);
            return InteractionResult.movementRequired(position);
        }

        // Resize the cube to better fit the trap model (40% of original size)
        final Polygon resizedCube = trapCube.getResized(0.4);

        // Attempt a blind tap to see what menu options are available
        boolean[] trapFound = {false};
        boolean tapped = script.submitHumanTask(() ->
            script.getFinger().tapGameScreen(resizedCube, (menuEntries) -> {
                for (MenuEntry entry : menuEntries) {
                    String action = entry.getAction();
                    if (action != null) {
                        String actionLower = action.toLowerCase();
                        if (actionLower.contains("check") ||
                            actionLower.contains("dismantle") ||
                            actionLower.contains("lay")) {
                            trapFound[0] = true;
                            ScriptLogger.debug(script, "Blind tap found action: " + action);
                            return entry; // Select the trap action
                        }
                    }
                }
                return null; // Cancel if no trap actions found
            }), 2000);

        if (tapped && trapFound[0]) {
            return InteractionResult.success(
                InteractionResult.InteractionType.VERIFICATION_NEEDED,
                "Trap interaction detected at position via TileCube",
                position
            );
        }

        return InteractionResult.verificationNeeded(position);
    }

    /**
     * Find trap pixels within the given area.
     */
    private Optional<PixelCluster> findTrapPixels(Polygon area, TrapType trapType, TrapState state) {
        // Select pixel clusters based on trap state.
        SearchablePixel[][] clustersToTry = (state == TrapState.COLLAPSED)
            ? trapType.getCollapsedPixelClusters()
            : trapType.getStandingPixelClusters();

        for (int i = 0; i < clustersToTry.length; i++) {
            if (clustersToTry[i] == null || clustersToTry[i].length == 0) continue;

            ScriptLogger.debug(script, "Trying pixel cluster #" + (i + 1) + " for " + state + " trap");

            PixelCluster.ClusterQuery query = new PixelCluster.ClusterQuery(
                (int) trapType.getClusterDistance(),
                trapType.getMinClusterSize(),
                clustersToTry[i]
            );

            ClusterSearchResult result = script.getPixelAnalyzer().findClusters(area, query);

            if (result != null && result.getClusters() != null && !result.getClusters().isEmpty()) {
                ScriptLogger.debug(script, "Found trap pixels using cluster #" + (i + 1));
                return Optional.of(getLargestCluster(result.getClusters()));
            }
        }

        return Optional.empty();
    }

    /**
     * Get the largest cluster from a list of clusters.
     * Since we don't have access to pixel count, just return the first one.
     */
    private PixelCluster getLargestCluster(List<PixelCluster> clusters) {
        // In practice, there's usually only one cluster per trap anyway
        return clusters.isEmpty() ? null : clusters.get(0);
    }

    /**
     * Create a tap area from pixel cluster bounds.
     */
    private Rectangle createTapArea(PixelCluster cluster, TrapType trapType) {
        Rectangle bounds = cluster.getBounds();

        // Ensure minimum dimensions
        int width = Math.max(bounds.width, 2);
        int height = Math.max(bounds.height, 2);

        // Apply Y-scaling from trap type
        double yScale = trapType.getTapAreaYScale();
        int scaledHeight = Math.max((int)(height * yScale), 2);
        int yOffset = (scaledHeight - height) / 2;

        return new Rectangle(
            bounds.x,
            bounds.y - yOffset,
            width,
            scaledHeight
        );
    }

    /**
     * Performs menu-based interaction using a tile polygon.
     */
    private InteractionResult performTileCubeInteraction(TrapInfo trapInfo) {
        TrapState state = trapInfo.state();
        TrapType trapType = trapInfo.trapType();
        WorldPosition position = trapInfo.position();

        // Calculate dimensions (chinchompas now use tilePoly method instead)
        int height = 100;  // Default for non-chinchompa traps
        double resizeRatio = 0.4;  // Default for non-chinchompa traps

        // Special handling for collapsed bird snares
        if (state == TrapState.COLLAPSED) {
            height = 40;
            resizeRatio = 0.6;
        }

        String[] acceptableActions = trapType.getActionsForTrapState(state);
        String[] selectedAction = new String[1]; // Track which action was selected
        boolean cancelSelected = false;

        // Retry once with a recalculated polygon if needed.
        for (int attempt = 1; attempt <= 2; attempt++) {
            // Recalculate TileCube/TilePoly on each attempt for fresh positioning
            Polygon trapCube;

            // For chinchompas, use tilePoly with asymmetric scaling
            if (trapType == TrapType.CHINCHOMPA) {
                // Get tilePoly for chinchompas
                RSTile trapTile = script.getSceneManager().getTile(position);
                if (trapTile == null || trapTile.getTilePoly() == null) {
                    ScriptLogger.debug(script, "Trap tile not available at " + position);
                    return InteractionResult.movementRequired(position);
                }

                // Use asymmetric scaling for chinchompas
                trapCube = getAsymmetricScaledPolygon(
                    trapTile.getTilePoly(),
                    CHINCHOMPA_X_SCALE,
                    CHINCHOMPA_Y_SCALE,
                    CHINCHOMPA_ALIGNMENT
                );

                if (trapCube == null) {
                    ScriptLogger.warning(script, "Failed to create asymmetric polygon, falling back to standard scaling");
                    trapCube = trapTile.getTilePoly().getResized(0.3);
                }
            } else {
                // Standard tileCube approach for bird snares
                trapCube = script.getSceneProjector().getTileCube(position, height);
                if (trapCube != null) {
                    trapCube = trapCube.getResized(resizeRatio);
                }
            }

            if (trapCube == null) {
                ScriptLogger.debug(script, "Trap interaction polygon not available at " + position);
                return InteractionResult.movementRequired(position);
            }

            final Polygon tapCube = trapCube;

            // Store polygon for chinchompa visualization
            if (trapType == TrapType.CHINCHOMPA) {
                currentInteractionPolygon = tapCube;
            }

            // Log info for debugging
            String method;
            if (trapType == TrapType.CHINCHOMPA) {
                method = String.format("AsymmetricPoly (x=%.1f, y=%.1f, align=%s)",
                    CHINCHOMPA_X_SCALE, CHINCHOMPA_Y_SCALE, CHINCHOMPA_ALIGNMENT);
            } else {
                method = "TileCube (resize=" + resizeRatio + ")";
            }
            ScriptLogger.debug(script, "Attempt " + attempt + "/2: Using " + method + " for " + trapType + " trap");

            final int currentAttempt = attempt;
            cancelSelected = false; // Reset flag for this attempt

            boolean interacted = script.submitHumanTask(() ->
                script.getFinger().tapGameScreen(tapCube, (menuEntries) -> {
                    // Draw polygon for chinchompas while menu is open
                    if (trapType == TrapType.CHINCHOMPA && currentInteractionPolygon != null) {
                        script.getScreen().queueCanvasDrawable("trapPoly", canvas -> {
                            canvas.drawPolygon(currentInteractionPolygon, POLYGON_COLOR, POLYGON_OPACITY);
                        });
                    }

                    // Prefer collection actions when expedite mode is set.
                    if (trapInfo.flags() != null && trapInfo.flags().hasFlag(TrapFlag.EXPEDITE_COLLECTION)) {
                        // For expedited collection, prioritize dismantling active traps
                        if (state == TrapState.ACTIVE) {
                            // Look for dismantle action on active traps
                            for (MenuEntry entry : menuEntries) {
                                String action = entry.getAction();
                                if (action != null && action.toLowerCase().contains("dismantle")) {
                                    ScriptLogger.debug(script, "EXPEDITE: Selected DISMANTLE for active trap");
                                    selectedAction[0] = action;
                                    return entry;
                                }
                            }
                        }
                        // For any other state during expedite, prefer take/check/dismantle
                        for (MenuEntry entry : menuEntries) {
                            String action = entry.getAction();
                            if (action != null) {
                                String actionLower = action.toLowerCase();
                                if (actionLower.contains("take") || actionLower.contains("check") ||
                                    actionLower.contains("dismantle")) {
                                    ScriptLogger.debug(script, "EXPEDITE: Selected " + action + " for " + state + " trap");
                                    selectedAction[0] = action;
                                    return entry;
                                }
                            }
                        }
                    }

                    // For collapsed traps, prefer take while draining.
                    if (state == TrapState.COLLAPSED) {
                        if (script.isDrainingForBreak()) {
                            for (MenuEntry entry : menuEntries) {
                                String action = entry.getAction();
                                if (action != null && action.toLowerCase().contains("take")) {
                                    ScriptLogger.debug(script, "Selected TAKE for collapsed trap during drain mode");
                                    selectedAction[0] = action;
                                    return entry;
                                }
                            }
                            // If no "take" option, fall through to try other actions
                        } else {
                            // In normal mode, prefer "lay" most of the time.
                            boolean preferLay = RandomUtils.uniformRandom(1, 100) <= 85;
                            String preferredAction = preferLay ? "lay" : "take";

                            // First try to find the preferred action
                            for (MenuEntry entry : menuEntries) {
                                String action = entry.getAction();
                                if (action != null && action.toLowerCase().contains(preferredAction)) {
                                    ScriptLogger.debug(script, "Selected " + preferredAction.toUpperCase() +
                                                     " for collapsed trap");
                                    selectedAction[0] = action;
                                    return entry;
                                }
                            }
                        }
                    }

                    // First pass: prioritize reset actions for chinchompas with finished states
                    // BUT SKIP reset during drain mode to allow trap removal
                    if (!script.isDrainingForBreak() && trapType.supportsReset() &&
                        (state == TrapState.FINISHED || state == TrapState.FINISHED_SUCCESS ||
                         state == TrapState.FINISHED_FAILED)) {
                        for (MenuEntry entry : menuEntries) {
                            String action = entry.getAction();
                            if (action != null) {
                                for (String resetAction : trapType.getResetActions()) {
                                    if (action.toLowerCase().contains(resetAction.toLowerCase())) {
                                        ScriptLogger.debug(script, "Selected RESET action via TileCube: " + action);
                                        selectedAction[0] = action;
                                        return entry;
                                    }
                                }
                            }
                        }
                    }

                    // Second pass: fallback to regular actions
                    for (MenuEntry entry : menuEntries) {
                        String action = entry.getAction();
                        if (action != null) {
                            for (String acceptableAction : acceptableActions) {
                                if (action.toLowerCase().contains(acceptableAction.toLowerCase())) {
                                    ScriptLogger.debug(script, "Selected action via TileCube: " + action);
                                    selectedAction[0] = action;
                                    return entry;
                                }
                            }
                        }
                    }

                    // No matching actions found - return cancel to prevent API retry
                    ScriptLogger.debug(script, "No matching actions found in TileCube tap (attempt " + currentAttempt + ")");

                    // Look for cancel option to exit menu gracefully
                    for (MenuEntry entry : menuEntries) {
                        String action = entry.getAction();
                        if (action != null && action.toLowerCase().equals("cancel")) {
                            if (currentAttempt == 1) {
                                ScriptLogger.debug(script, "Selecting cancel - will retry with fresh TileCube");
                            } else {
                                ScriptLogger.debug(script, "Selecting cancel - no more retries");
                            }
                            selectedAction[0] = "cancel";
                            return entry;
                        }
                    }

                    // If no cancel found (shouldn't happen), return first entry to prevent API retry
                    if (!menuEntries.isEmpty()) {
                        ScriptLogger.warning(script, "No cancel option found, selecting first menu entry to prevent API retry");
                        selectedAction[0] = menuEntries.get(0).getAction();
                        return menuEntries.get(0);
                    }

                    // Only return null if menu is completely empty (very rare)
                    return null;
                }), RandomUtils.uniformRandom(2900, 3400));

            // Check if we selected cancel (meaning we missed the trap)
            cancelSelected = selectedAction[0] != null && selectedAction[0].equals("cancel");

            // If we successfully interacted with a trap action (not cancel)
            if (interacted && selectedAction[0] != null && !cancelSelected) {
                // Clear polygon drawing for chinchompas
                if (trapType == TrapType.CHINCHOMPA) {
                    clearInteractionPolygon();
                }

                InteractionResult.InteractionType type = determineInteractionType(state, selectedAction[0]);
                String methodUsed = (trapType == TrapType.CHINCHOMPA) ? "TilePoly" : "TileCube";
                return InteractionResult.success(type, "Successfully interacted with trap via " + methodUsed + " (attempt " + attempt + ")", position);
            }

            // If this was the first attempt and we selected cancel, retry with fresh TileCube
            if (attempt == 1 && cancelSelected) {
                ScriptLogger.info(script, "First tap missed trap at " + position + ", recalculating TileCube for retry...");
                script.sleep(RandomUtils.weightedRandom(200, 400));
                continue;
            }

            // If second attempt also failed or other failure
            if (attempt == 2) {
                break; // Exit loop, will return failure below
            }
        }

        // Clear polygon if we failed
        if (trapType == TrapType.CHINCHOMPA) {
            clearInteractionPolygon();
        }

        String method = (trapType == TrapType.CHINCHOMPA) ? "TilePoly" : "TileCube";
        return InteractionResult.failure(method + " tap failed after 2 attempts", position);
    }

    /**
     * Perform tap interaction with menu matching.
     */
    private InteractionResult performTapInteraction(Rectangle tapArea, TrapInfo trapInfo) {
        TrapState state = trapInfo.state();
        TrapType trapType = trapInfo.trapType();
        WorldPosition position = trapInfo.position();

        String[] acceptableActions = trapType.getActionsForTrapState(state);
        String[] selectedAction = new String[1]; // Track which action was selected

        boolean interacted = script.submitHumanTask(() ->
            script.getFinger().tapGameScreen(tapArea, (menuEntries) -> {
                // HIGHEST PRIORITY: Check for EXPEDITE_COLLECTION flag
                if (trapInfo.flags() != null && trapInfo.flags().hasFlag(TrapFlag.EXPEDITE_COLLECTION)) {
                    // For expedited collection, prioritize dismantling active traps
                    if (state == TrapState.ACTIVE) {
                        // Look for dismantle action on active traps
                        for (MenuEntry entry : menuEntries) {
                            String action = entry.getAction();
                            if (action != null && action.toLowerCase().contains("dismantle")) {
                                ScriptLogger.debug(script, "EXPEDITE: Selected DISMANTLE for active trap");
                                selectedAction[0] = action;
                                return entry;
                            }
                        }
                    }
                    // For any other state during expedite, prefer take/check/dismantle
                    for (MenuEntry entry : menuEntries) {
                        String action = entry.getAction();
                        if (action != null) {
                            String actionLower = action.toLowerCase();
                            if (actionLower.contains("take") || actionLower.contains("check") ||
                                actionLower.contains("dismantle")) {
                                ScriptLogger.debug(script, "EXPEDITE: Selected " + action + " for " + state + " trap");
                                selectedAction[0] = action;
                                return entry;
                            }
                        }
                    }
                }

                // Special handling for COLLAPSED state
                if (state == TrapState.COLLAPSED) {
                    // For collapsed traps, prefer take while draining.
                    if (script.isDrainingForBreak()) {
                        for (MenuEntry entry : menuEntries) {
                            String action = entry.getAction();
                            if (action != null && action.toLowerCase().contains("take")) {
                                ScriptLogger.debug(script, "Selected TAKE for collapsed trap during drain mode");
                                selectedAction[0] = action;
                                return entry;
                            }
                        }
                        // If no "take" option, fall through to try other actions
                    } else {
                        // In normal mode, prefer "lay" most of the time.
                        boolean preferLay = RandomUtils.uniformRandom(1, 100) <= 85;
                        String preferredAction = preferLay ? "lay" : "take";

                        // First try to find the preferred action
                        for (MenuEntry entry : menuEntries) {
                            String action = entry.getAction();
                            if (action != null && action.toLowerCase().contains(preferredAction)) {
                                ScriptLogger.debug(script, "Selected " + preferredAction.toUpperCase() +
                                                 " for collapsed trap");
                                selectedAction[0] = action;
                                return entry;
                            }
                        }
                    }
                }

                // First pass: prioritize reset actions for chinchompas with finished states
                // BUT SKIP reset during drain mode to allow trap removal
                if (!script.isDrainingForBreak() && trapType.supportsReset() &&
                    (state == TrapState.FINISHED || state == TrapState.FINISHED_SUCCESS ||
                     state == TrapState.FINISHED_FAILED)) {
                    for (MenuEntry entry : menuEntries) {
                        String action = entry.getAction();
                        if (action != null) {
                            for (String resetAction : trapType.getResetActions()) {
                                if (action.toLowerCase().contains(resetAction.toLowerCase())) {
                                    ScriptLogger.debug(script, "Selected RESET action: " + action);
                                    selectedAction[0] = action;
                                    return entry;
                                }
                            }
                        }
                    }
                }

                // Second pass: fallback to regular actions
                for (MenuEntry entry : menuEntries) {
                    String action = entry.getAction();
                    if (action != null) {
                        for (String acceptableAction : acceptableActions) {
                            if (action.toLowerCase().contains(acceptableAction.toLowerCase())) {
                                ScriptLogger.debug(script, "Selected action: " + action);
                                selectedAction[0] = action;
                                return entry;
                            }
                        }
                    }
                }
                return null;
            }), RandomUtils.uniformRandom(2900, 3400));

        if (interacted) {
            InteractionResult.InteractionType type = determineInteractionType(state, selectedAction[0]);
            return InteractionResult.success(type, "Successfully interacted with trap", position);
        }

        return InteractionResult.failure("Failed to interact with trap", position);
    }

    /**
     * Clears the current interaction polygon and removes it from the canvas.
     */
    private void clearInteractionPolygon() {
        currentInteractionPolygon = null;
        script.getScreen().removeCanvasDrawable("trapPoly");
    }

    /**
     * Determine interaction type based on trap state and selected action.
     */
    private InteractionResult.InteractionType determineInteractionType(TrapState state, String selectedAction) {
        // Check if reset action was selected
        if (selectedAction != null && selectedAction.toLowerCase().contains("reset")) {
            return InteractionResult.InteractionType.TRAP_RESET_INITIATED;
        }

        // Check if lay action was selected on a collapsed trap
        if (selectedAction != null && selectedAction.toLowerCase().contains("lay") && state == TrapState.COLLAPSED) {
            return InteractionResult.InteractionType.TRAP_LAID; // Laying a collapsed trap in place
        }

        return switch (state) {
            case FINISHED, FINISHED_SUCCESS, FINISHED_FAILED -> InteractionResult.InteractionType.TRAP_CHECKED;
            case COLLAPSED -> InteractionResult.InteractionType.TRAP_RESET; // For take actions
            case LAYING -> InteractionResult.InteractionType.TRAP_LAID;
            default -> InteractionResult.InteractionType.FAILED;
        };
    }

    public TrapVisibilityChecker getVisibilityChecker() {
        return visibilityChecker;
    }
}
