package com.jork.utils;

import com.osmb.api.location.area.Area;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.script.Script;
import com.osmb.api.walker.WalkConfig;
import com.osmb.api.shape.Polygon;
import com.osmb.api.scene.RSTile;
import com.osmb.api.utils.RandomUtils;

import java.awt.Point;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A utility class for handling generic navigation tasks within scripts.
 * This class abstracts the complexities of walking, pathfinding, and handling common obstacles.
 */
public class Navigation {

    private final Script script;

    /**
     * Constructs a new Navigation utility.
     * @param script The main script instance.
     */
    public Navigation(Script script) {
        this.script = script;
    }

    /**
     * Represents a special obstacle that may need to be handled during navigation.
     * Examples include doors, gates, ladders, or any other interactive object blocking a path.
     */
    public interface Obstacle {
        /**
         * The area where the obstacle is located.
         * @return A RectangleArea representing the obstacle's location.
         */
        Area getArea();

        /**
         * Checks if the obstacle currently needs to be handled (e.g., a door is closed).
         * @return true if handling is required, false otherwise.
         */
        boolean needsHandling();

        /**
         * Executes the logic to handle the obstacle (e.g., opens a door).
         * This method should also handle any necessary waiting after the interaction.
         * @return true if the obstacle was handled successfully, false otherwise.
         */
        boolean handle();
    }

    /**
     * The primary navigation method that uses a pre-built WalkConfig.
     * This offers the most customization for walking behavior.
     *
     * @param destination The target area to walk to.
     * @param config The fully configured WalkConfig to use for navigation.
     * @return true if the destination is reached, false otherwise.
     */
    public boolean navigateTo(Area destination, WalkConfig config) {
        ScriptLogger.navigation(script, "to " + destination.getCenter().toString());

        // Check if we are already at the destination before starting the walk.
        if (destination.contains(script.getWorldPosition())) {
            ScriptLogger.actionSuccess(script, "Already at destination.");
            return true;
        }

        Point targetPosition = destination.getCenter();
        ScriptLogger.actionAttempt(script, "Walking to " + targetPosition);
        
        // Execute the walk. The WalkConfig handles timeouts and break conditions.
        if (config != null) {
            script.getWalker().walkTo(targetPosition.x, targetPosition.y, config);
        } else {
            script.getWalker().walkTo(targetPosition.x, targetPosition.y);
        }

        // The final check is what matters. Did we arrive?
        WorldPosition currentPos = script.getWorldPosition();
        if (currentPos != null && destination.contains(currentPos)) {
            ScriptLogger.actionSuccess(script, "Successfully navigated to destination.");
            return true;
        }

        // Treat break distance as success if configured (useful for small destinations)
        if (currentPos != null && config != null) {
            int breakDistance = config.getBreakDistance();
            if (breakDistance > 0) {
                double distanceToCenter = currentPos.distanceTo(new WorldPosition(
                    targetPosition.x,
                    targetPosition.y,
                    currentPos.getPlane()
                ));
                if (distanceToCenter <= breakDistance) {
                    ScriptLogger.actionSuccess(script, "Reached destination within break distance.");
                    return true;
                }
            }
        }

        ScriptLogger.actionFailure(script, "Failed to navigate to destination. (Timed out or break condition met)", 1, 1);
        return false;
    }

    /**
     * The most flexible navigation method that intelligently combines custom settings with automated logic.
     *
     * @param destination The target area to walk to.
     * @param obstacles A list of potential obstacles on the path.
     * @param breakCondition A custom condition to terminate walking early.
     * @param timeout The maximum time in milliseconds for the navigation attempt.
     * @param builder An optional, pre-configured WalkConfig.Builder with custom settings (e.g., disabled run).
     * @return true if the destination is reached, false otherwise.
     */
    public boolean navigateTo(Area destination, List<Obstacle> obstacles, BooleanSupplier breakCondition, int timeout, WalkConfig.Builder builder) {
        // Use the provided builder or create a new one if null.
        WalkConfig.Builder finalBuilder = (builder != null) ? builder : new WalkConfig.Builder();

        // Master break condition: stop if we arrive, or if the custom condition is met.
        BooleanSupplier masterBreakCondition = () -> {
            if (destination.contains(script.getWorldPosition())) {
                return true;
            }
            return breakCondition != null && breakCondition.getAsBoolean();
        };

        // Apply core navigation logic to the builder
        finalBuilder.breakCondition(masterBreakCondition);
        finalBuilder.timeout(timeout);
        
        // Layer on the obstacle handling logic
        if (obstacles != null && !obstacles.isEmpty()) {
            finalBuilder.doWhileWalking(() -> {
                for (Obstacle obstacle : obstacles) {
                    if (obstacle.getArea().contains(script.getWorldPosition()) && obstacle.needsHandling()) {
                        ScriptLogger.info(script, "Obstacle detected, attempting to handle...");
                        obstacle.handle();
                    }
                }
                return null; // Continue with the current walk config
            });
        }
        
        // Build the final config and call the primary navigation method.
        return navigateTo(destination, finalBuilder.build());
    }

    /**
     * Convenience overload for navigateTo.
     */
    public boolean navigateTo(Area destination, List<Obstacle> obstacles, BooleanSupplier breakCondition, int timeout) {
        return navigateTo(destination, obstacles, breakCondition, timeout, null);
    }

    /**
     * Convenience overload for navigateTo.
     */
    public boolean navigateTo(Area destination, List<Obstacle> obstacles) {
        return navigateTo(destination, obstacles, null, 20000, null);
    }

    /**
     * Convenience overload for navigateTo.
     */
    public boolean navigateTo(Area destination) {
        return navigateTo(destination, null, null, 20000, null);
    }

    /**
     * Simple screen-based movement for short distances with configurable tolerance.
     * If the target is on screen, taps directly on the tile with one retry attempt.
     * Otherwise falls back to A* walker.
     *
     * Poll patterns:
     * - pollFramesUntil for movement completion -- CORRECT, pure position/state verification
     * - pollFramesHuman(() -> true) after arrival -- CORRECT, human recognizes arrival (reaction delay)
     * - pollFramesHuman(() -> true) after failed tap -- CORRECT, human reaction to failed interaction
     * - pollFramesHuman(() -> true) after movement stopped -- CORRECT, human reaction to stopped movement
     *
     * @param targetPosition The target WorldPosition to move to
     * @param timeout Maximum time to wait for movement completion in milliseconds
     * @param tolerance Distance tolerance in tiles (0 for exact position, 1 for adjacent, etc.)
     * @return true if movement was successful, false otherwise
     */
    public boolean simpleMoveTo(WorldPosition targetPosition, int timeout, int tolerance) {
        if (targetPosition == null) {
            ScriptLogger.warning(script, "Cannot move to null position");
            return false;
        }

        WorldPosition currentPos = script.getWorldPosition();
        if (currentPos == null) {
            ScriptLogger.warning(script, "Cannot read current player position");
            return false;
        }

        // Check if already within tolerance of target
        if (currentPos.distanceTo(targetPosition) <= tolerance) {
            return true;
        }

        // Try simple movement up to 2 times before falling back to walker
        for (int attempt = 1; attempt <= 2; attempt++) {
            // Recalculate tile polygon on each attempt to account for player movement
            RSTile targetTile = script.getSceneManager().getTile(targetPosition);
            if (targetTile == null) {
                // Off-screen, use walker fallback
                ScriptLogger.debug(script, "Target off-screen, using walker");
                return script.getWalker().walkTo(targetPosition);
            }

            // Get tile polygon for screen tap
            Polygon tilePoly = targetTile.getTilePoly();
            if (tilePoly == null) {
                ScriptLogger.warning(script, "Could not get tile polygon");
                return script.getWalker().walkTo(targetPosition);
            }

            // Scale the polygon to 83% to focus taps away from tile edges
            Polygon scaledTilePoly = tilePoly.getResized(0.83);
            
            ScriptLogger.debug(script, "Screen tap attempt " + attempt + "/2 to " + targetPosition);
            
            // Perform tap with "Walk here" context menu directly
            boolean tapped = script.getFinger().tapGameScreen(scaledTilePoly, "Walk here");
            
            if (!tapped) {
                ScriptLogger.debug(script, "Failed to tap tile on attempt " + attempt);
                if (attempt == 2) {
                    ScriptLogger.warning(script, "Both tap attempts failed, using walker fallback");
                    return script.getWalker().walkTo(targetPosition);
                }
                
                // Human reaction to failed tap - would take time to notice and retry
                script.pollFramesHuman(() -> true, RandomUtils.weightedRandom(600, 1200));
                continue;
            }

            // SMART MOVEMENT DETECTION
            // Use submitTask (no delay) to detect movement completion
            long movementStartTime = System.currentTimeMillis();
            boolean movementComplete = script.pollFramesUntil(() -> {
                WorldPosition nowPos = script.getWorldPosition();
                
                // First check: Have we arrived?
                if (nowPos != null && nowPos.distanceTo(targetPosition) <= tolerance) {
                    return true; // Success - exit immediately
                }
                
                // Give movement time to start (first 800ms)
                if (System.currentTimeMillis() - movementStartTime < 800) {
                    return false; // Too early to determine failure
                }
                
                // After initial period, check if we've stopped moving
                long timeSinceLastMove = script.getLastPositionChangeMillis();
                
                // 400ms is enough to determine we've stopped
                if (timeSinceLastMove >= 400) {
                    // We've stopped but not at target - movement failed
                    return true; // Exit early for retry logic
                }
                
                return false; // Still moving, keep waiting
            }, timeout);

            // Now check why we exited the polling loop
            WorldPosition finalPos = script.getWorldPosition();
            
            if (finalPos != null && finalPos.distanceTo(targetPosition) <= tolerance) {
                // SUCCESS! Use submitHumanTask for human-like arrival recognition
                ScriptLogger.debug(script, "Simple movement completed on attempt " + attempt);
                
                // Human would take a moment to recognize arrival
                script.pollFramesHuman(() -> true, RandomUtils.weightedRandom(200, 400));
                return true;
            }
            
            // Movement stopped but we didn't arrive
            if (movementComplete && attempt == 1) {
                long stopTime = script.getLastPositionChangeMillis();
                ScriptLogger.debug(script, "Movement stopped after " + stopTime + "ms at " + 
                                 finalPos + " (target: " + targetPosition + ")");
                
                // Human reaction to recognizing we stopped at wrong place
                script.pollFramesHuman(() -> true, RandomUtils.weightedRandom(400, 800));
                continue; // Retry
            }
            
            // Timeout or second attempt failed
            if (!movementComplete) {
                ScriptLogger.debug(script, "Movement timed out on attempt " + attempt);
            }
        }
        
        // Both attempts failed, fall back to walker
        ScriptLogger.warning(script, "Simple movement failed after 2 attempts, using walker fallback");
        return script.getWalker().walkTo(targetPosition);
    }

    /**
     * Simple screen-based movement with default 1-tile tolerance.
     * 
     * @param targetPosition The target WorldPosition to move to
     * @param timeout Maximum time to wait for movement completion in milliseconds
     * @return true if movement was successful, false otherwise
     */
    public boolean simpleMoveTo(WorldPosition targetPosition, int timeout) {
        return simpleMoveTo(targetPosition, timeout, 1);
    }
    
    /**
     * Convenience overload with 5 second timeout and 1-tile tolerance.
     */
    public boolean simpleMoveTo(WorldPosition targetPosition) {
        return simpleMoveTo(targetPosition, 5000, 1);
    }
} 
