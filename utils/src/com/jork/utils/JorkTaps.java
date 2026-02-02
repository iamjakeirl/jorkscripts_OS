package com.jork.utils;

import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.location.position.types.LocalPosition;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.script.Script;
import com.osmb.api.shape.Polygon;
import com.osmb.api.shape.Shape;

import java.util.Random;
import java.util.function.BooleanSupplier;

/**
 * Utility class for tap interactions with the OSMB Finger API.
 * Provides spam tap functionality with configurable delays and completion conditions.
 *
 * <p>Usage examples:</p>
 * <pre>{@code
 * // Spam tap a screen shape until condition is met
 * boolean success = JorkTaps.spamTap(script, polygon, 100, 300, 20,
 *     () -> getEmptyPotCount() == baseline);
 *
 * // Spam tap a world-space RectangleArea with human delay at end
 * boolean success = JorkTaps.spamTapArea(script, rectangleArea, 100, 150, 400, 15,
 *     () -> inventory.isFull(), true);
 *
 * // Spam tap a single tile position
 * boolean success = JorkTaps.spamTapPosition(script, worldPos, 100, 150, 400, 15,
 *     () -> itemCollected(), true);
 * }</pre>
 */
public final class JorkTaps {

    private static final Random random = new Random();

    private JorkTaps() {
        // Utility class - no instantiation
    }

    /**
     * Spam tap a screen-space shape until a condition is met or max taps reached.
     * Uses pollFramesUntil (no human delay at end).
     *
     * @param script     The script instance
     * @param shape      The screen-space shape to tap (Rectangle, Polygon)
     * @param minDelayMs Minimum delay between taps in milliseconds
     * @param maxDelayMs Maximum delay between taps in milliseconds
     * @param maxTaps    Maximum number of taps before giving up
     * @param condition  Completion condition - stops when this returns true. If null, taps maxTaps times.
     * @return true if condition was met, false if max taps reached without condition met
     */
    public static boolean spamTap(Script script, Shape shape, int minDelayMs, int maxDelayMs,
                                   int maxTaps, BooleanSupplier condition) {
        return spamTap(script, shape, minDelayMs, maxDelayMs, maxTaps, condition, false);
    }

    /**
     * Spam tap a screen-space shape until a condition is met or max taps reached.
     *
     * @param script     The script instance
     * @param shape      The screen-space shape to tap (Rectangle, Polygon)
     * @param minDelayMs Minimum delay between taps in milliseconds
     * @param maxDelayMs Maximum delay between taps in milliseconds
     * @param maxTaps    Maximum number of taps before giving up
     * @param condition  Completion condition - stops when this returns true. If null, taps maxTaps times.
     * @param human      If true, uses pollFramesHuman (adds human delay after completion)
     * @return true if condition was met, false if max taps reached without condition met
     */
    public static boolean spamTap(Script script, Shape shape, int minDelayMs, int maxDelayMs,
                                   int maxTaps, BooleanSupplier condition, boolean human) {
        if (script == null) {
            ScriptLogger.warning(null, "JorkTaps.spamTap: script is null");
            return false;
        }

        if (shape == null) {
            ScriptLogger.warning(script, "JorkTaps.spamTap: shape is null");
            return false;
        }

        if (maxTaps <= 0) {
            ScriptLogger.warning(script, "JorkTaps.spamTap: maxTaps must be positive");
            return false;
        }

        // Ensure min <= max
        final int effectiveMinDelay = Math.min(minDelayMs, maxDelayMs);
        final int effectiveMaxDelay = Math.max(minDelayMs, maxDelayMs);
        final int delayRange = Math.max(1, effectiveMaxDelay - effectiveMinDelay + 1);

        final int[] tapCount = {0};
        final boolean[] conditionMet = {false};
        final long[] lastTapTime = {0};
        final int[] currentDelay = {0};

        BooleanSupplier pollCondition = () -> {
            // Check completion condition first
            if (condition != null && condition.getAsBoolean()) {
                conditionMet[0] = true;
                return true;
            }

            // Check if we've reached max taps
            if (tapCount[0] >= maxTaps) {
                return true;
            }

            long now = System.currentTimeMillis();

            // First tap or delay has passed since last tap
            if (lastTapTime[0] == 0 || (now - lastTapTime[0]) >= currentDelay[0]) {
                // Perform tap on game screen (avoids UI elements)
                boolean tapped = script.getFinger().tapGameScreen(shape);

                if (tapped) {
                    tapCount[0]++;
                    lastTapTime[0] = now;

                    // Set random delay for next tap
                    currentDelay[0] = effectiveMinDelay + random.nextInt(delayRange);

                    // Check condition immediately after tap
                    if (condition != null && condition.getAsBoolean()) {
                        conditionMet[0] = true;
                        return true;
                    }
                }
            }

            return false;
        };

        // Calculate total timeout: max taps * max delay + buffer
        int totalTimeout = maxTaps * effectiveMaxDelay + 5000;

        try {
            if (human) {
                script.pollFramesHuman(pollCondition, totalTimeout);
            } else {
                script.pollFramesUntil(pollCondition, totalTimeout);
            }
        } catch (Exception e) {
            ScriptLogger.debug(script, "JorkTaps.spamTap: Interrupted after " + tapCount[0] + " taps");
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            return conditionMet[0];
        }

        ScriptLogger.debug(script, "JorkTaps.spamTap: " +
            (conditionMet[0] ? "Condition met" : "Max taps reached") +
            " after " + tapCount[0] + " taps");

        return conditionMet[0];
    }

    /**
     * Spam tap a world-space RectangleArea until a condition is met or max taps reached.
     * Converts the area to a screen-space Polygon via SceneProjector.
     * Uses pollFramesUntil (no human delay at end).
     *
     * @param script     The script instance
     * @param area       The world-space RectangleArea to tap
     * @param cubeHeight The height of the 3D projection (e.g., 100 for standard objects)
     * @param minDelayMs Minimum delay between taps in milliseconds
     * @param maxDelayMs Maximum delay between taps in milliseconds
     * @param maxTaps    Maximum number of taps before giving up
     * @param condition  Completion condition - stops when this returns true. If null, taps maxTaps times.
     * @return true if condition was met, false if max taps reached or area not visible
     */
    public static boolean spamTapArea(Script script, RectangleArea area, int cubeHeight,
                                       int minDelayMs, int maxDelayMs, int maxTaps,
                                       BooleanSupplier condition) {
        return spamTapArea(script, area, cubeHeight, minDelayMs, maxDelayMs, maxTaps, condition, false);
    }

    /**
     * Spam tap a world-space RectangleArea until a condition is met or max taps reached.
     * Converts the area to a screen-space Polygon via SceneProjector.
     *
     * @param script     The script instance
     * @param area       The world-space RectangleArea to tap
     * @param cubeHeight The height of the 3D projection (e.g., 100 for standard objects)
     * @param minDelayMs Minimum delay between taps in milliseconds
     * @param maxDelayMs Maximum delay between taps in milliseconds
     * @param maxTaps    Maximum number of taps before giving up
     * @param condition  Completion condition - stops when this returns true. If null, taps maxTaps times.
     * @param human      If true, uses pollFramesHuman (adds human delay after completion)
     * @return true if condition was met, false if max taps reached or area not visible
     */
    public static boolean spamTapArea(Script script, RectangleArea area, int cubeHeight,
                                       int minDelayMs, int maxDelayMs, int maxTaps,
                                       BooleanSupplier condition, boolean human) {
        if (script == null) {
            ScriptLogger.warning(null, "JorkTaps.spamTapArea: script is null");
            return false;
        }

        if (area == null) {
            ScriptLogger.warning(script, "JorkTaps.spamTapArea: area is null");
            return false;
        }

        // Convert RectangleArea to screen-space Polygon
        Polygon areaCube = convertAreaToPolygon(script, area, cubeHeight);
        if (areaCube == null) {
            ScriptLogger.warning(script, "JorkTaps.spamTapArea: Could not project RectangleArea to screen - area may not be visible");
            return false;
        }

        return spamTap(script, areaCube, minDelayMs, maxDelayMs, maxTaps, condition, human);
    }

    /**
     * Spam tap a world-space WorldPosition until a condition is met or max taps reached.
     * Converts the position to a screen-space Polygon via SceneProjector.
     *
     * @param script     The script instance
     * @param position   The world-space position to tap
     * @param cubeHeight The height of the 3D projection (e.g., 100 for standard objects)
     * @param minDelayMs Minimum delay between taps in milliseconds
     * @param maxDelayMs Maximum delay between taps in milliseconds
     * @param maxTaps    Maximum number of taps before giving up
     * @param condition  Completion condition - stops when this returns true. If null, taps maxTaps times.
     * @param human      If true, uses pollFramesHuman (adds human delay after completion)
     * @return true if condition was met, false if max taps reached or position not visible
     */
    public static boolean spamTapPosition(Script script, WorldPosition position, int cubeHeight,
                                           int minDelayMs, int maxDelayMs, int maxTaps,
                                           BooleanSupplier condition, boolean human) {
        if (script == null) {
            ScriptLogger.warning(null, "JorkTaps.spamTapPosition: script is null");
            return false;
        }

        if (position == null) {
            ScriptLogger.warning(script, "JorkTaps.spamTapPosition: position is null");
            return false;
        }

        // Convert WorldPosition to screen-space Polygon
        Polygon tileCube = script.getSceneProjector().getTileCube(position, cubeHeight);
        if (tileCube == null) {
            ScriptLogger.warning(script, "JorkTaps.spamTapPosition: Could not project position to screen - tile may not be visible");
            return false;
        }

        return spamTap(script, tileCube, minDelayMs, maxDelayMs, maxTaps, condition, human);
    }

    /**
     * Spam tap a world-space WorldPosition until a condition is met or max taps reached.
     * Uses pollFramesUntil (no human delay at end).
     *
     * @param script     The script instance
     * @param position   The world-space position to tap
     * @param cubeHeight The height of the 3D projection (e.g., 100 for standard objects)
     * @param minDelayMs Minimum delay between taps in milliseconds
     * @param maxDelayMs Maximum delay between taps in milliseconds
     * @param maxTaps    Maximum number of taps before giving up
     * @param condition  Completion condition - stops when this returns true. If null, taps maxTaps times.
     * @return true if condition was met, false if max taps reached or position not visible
     */
    public static boolean spamTapPosition(Script script, WorldPosition position, int cubeHeight,
                                           int minDelayMs, int maxDelayMs, int maxTaps,
                                           BooleanSupplier condition) {
        return spamTapPosition(script, position, cubeHeight, minDelayMs, maxDelayMs, maxTaps, condition, false);
    }

    /**
     * Converts a world-space RectangleArea to a screen-space Polygon.
     *
     * @param script     The script instance
     * @param area       The world-space RectangleArea
     * @param cubeHeight The height of the 3D projection
     * @return The screen-space Polygon, or null if the area is not visible
     */
    public static Polygon convertAreaToPolygon(Script script, RectangleArea area, int cubeHeight) {
        if (script == null || area == null) {
            return null;
        }

        WorldPosition basePos = area.getBasePosition();
        if (basePos == null) {
            return null;
        }

        // Convert world position to local position for scene projection
        LocalPosition localPos = basePos.toLocalPosition(script);
        if (localPos == null) {
            return null;
        }

        return script.getSceneProjector().getTileCube(
            localPos.getPreciseX(),
            localPos.getPreciseY(),
            area.getPlane(),
            0,                  // baseHeight (ground level)
            cubeHeight,
            area.getWidth(),
            area.getHeight(),
            false               // fullyOnScreen
        );
    }

    /**
     * Converts a world-space WorldPosition to a screen-space Polygon (single tile cube).
     *
     * @param script     The script instance
     * @param position   The world-space position
     * @param cubeHeight The height of the 3D projection
     * @return The screen-space Polygon, or null if the position is not visible
     */
    public static Polygon convertPositionToPolygon(Script script, WorldPosition position, int cubeHeight) {
        if (script == null || position == null) {
            return null;
        }

        return script.getSceneProjector().getTileCube(position, cubeHeight);
    }
}
