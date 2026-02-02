package com.jork.utils;

import com.osmb.api.script.Script;
import java.awt.Color;
import java.awt.Point;

/**
 * A utility class for drawing debug visuals on the screen.
 */
public class DebugDrawer {

    /**
     * Draws a visible 3x3 square at a projected tile point to help calibrate z-offsets.
     * To use, temporarily add `DebugDrawer.drawZOffsetMarker(this, Z_OFFSET_TO_TEST);`
     * to your script's main poll() loop.
     *
     * @param script    The script instance, used to access game state (player position, screen).
     * @param zOffset   The vertical height offset to test.
     */
    public static void drawZOffsetMarker(Script script, int zOffset) {
        com.osmb.api.location.position.types.WorldPosition playerPos = script.getWorldPosition();
        if (playerPos == null) {
            // No need to log here, as this will be called every poll.
            // A script without a known position is a common state.
            return;
        }

        com.osmb.api.location.position.types.LocalPosition localPos = playerPos.toLocalPosition(script);
        if (localPos == null) {
            return;
        }

        Point tilePoint = script.getSceneProjector().getTilePoint(localPos.getX(), localPos.getY(), localPos.getPlane(), null, zOffset);
        if (tilePoint == null) {
            return; // Point is likely off-screen, no need to draw.
        }

        // Draw a 3x3 square for better visibility
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                script.getScreen().getDrawableCanvas().setRGB(tilePoint.x + dx, tilePoint.y + dy, Color.RED.getRGB(), 1.0);
            }
        }
    }
} 