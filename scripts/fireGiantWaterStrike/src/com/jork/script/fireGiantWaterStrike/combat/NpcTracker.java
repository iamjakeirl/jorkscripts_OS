package com.jork.script.fireGiantWaterStrike.combat;

import com.osmb.api.location.position.types.WorldPosition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Velocity-projected NPC identity tracker with UUID persistence.
 *
 * Adapted from the CharacterTracker pattern for NPC tracking. Key differences:
 * <ul>
 *   <li>Tracks NPCs (yellow minimap dots) instead of players (white dots)</li>
 *   <li>Does NOT use frame listeners -- {@link #update} is called explicitly from poll()</li>
 *   <li>Filters input positions to {@link CombatConstants#FIRE_GIANT_AREA} before matching</li>
 *   <li>Accepts {@code List<WorldPosition>} (caller queries minimap and converts)</li>
 *   <li>No ToolkitScript dependency -- standalone single-threaded class</li>
 * </ul>
 *
 * Identity is preserved across ticks via velocity-projected Chebyshev matching:
 * each tracked NPC's position is projected forward using its smoothed velocity,
 * and the closest fresh position (within speed limits) is matched to preserve UUID.
 */
public class NpcTracker {

    private final Map<String, TrackedNpc> trackedNpcs = new HashMap<>();

    private long lastUpdateTime = 0;
    private long updateCount = 0;

    /**
     * Update tracker with fresh NPC positions from minimap.
     * Call ONCE per poll cycle. Positions should already be converted to a List.
     * This method handles area filtering, velocity-projected matching, cleanup,
     * and registration of new UUIDs for unmatched positions.
     *
     * @param allNpcPositions all NPC positions from getMinimap().getNPCPositions().asList().
     *                        Pass empty list (not null) if minimap unavailable.
     */
    public void update(List<WorldPosition> allNpcPositions) {
        long now = System.currentTimeMillis();
        updateCount++;

        // Filter to combat area
        List<WorldPosition> filtered = new ArrayList<>();
        for (WorldPosition pos : allNpcPositions) {
            if (CombatConstants.FIRE_GIANT_AREA.contains(pos)) {
                filtered.add(pos);
            }
        }

        double deltaTicks = (lastUpdateTime == 0) ? 1.0
            : (now - lastUpdateTime) / CombatConstants.TICK_MS;
        lastUpdateTime = now;

        List<WorldPosition> unmatchedNew = new ArrayList<>(filtered);

        // 1. MATCHING: Project each tracked NPC's position using velocity, match against actual positions
        for (TrackedNpc npc : trackedNpcs.values()) {
            WorldPosition bestMatch = null;
            double bestScore = Double.MAX_VALUE;

            double projectedX = npc.position.getPreciseX() + (npc.vx * deltaTicks);
            double projectedY = npc.position.getPreciseY() + (npc.vy * deltaTicks);

            for (WorldPosition pos : unmatchedNew) {
                // Score = distance from PROJECTED position (velocity-aware matching)
                double scoreDist = chebyshev(projectedX, projectedY,
                    pos.getPreciseX(), pos.getPreciseY());

                // Reject if actual move exceeds max speed
                double actualMoveDist = chebyshev(
                    npc.position.getPreciseX(), npc.position.getPreciseY(),
                    pos.getPreciseX(), pos.getPreciseY());
                if (actualMoveDist > CombatConstants.MAX_TILES_PER_TICK * deltaTicks) continue;

                if (scoreDist < bestScore) {
                    bestScore = scoreDist;
                    bestMatch = pos;
                }
            }

            if (bestMatch != null) {
                npc.update(bestMatch, deltaTicks, now, updateCount);
                unmatchedNew.remove(bestMatch);
            }
        }

        // 2. CLEANUP: Evict NPCs not seen within TTL
        trackedNpcs.values().removeIf(npc ->
            now - npc.lastSeenTimestamp > CombatConstants.NPC_TTL_MS);

        // 3. REGISTRATION: Unmatched positions get new UUIDs
        for (WorldPosition newPos : unmatchedNew) {
            String uuid = UUID.randomUUID().toString();
            trackedNpcs.put(uuid, new TrackedNpc(uuid, newPos, now, updateCount));
        }
    }

    /**
     * Check if a UUID is currently tracked (was matched in the most recent update).
     *
     * @param uuid the NPC's UUID
     * @return true if the UUID was matched this frame
     */
    public boolean isActive(String uuid) {
        TrackedNpc npc = trackedNpcs.get(uuid);
        return npc != null && npc.lastUpdateFrame == updateCount;
    }

    /**
     * Get the current position of a tracked NPC by UUID.
     *
     * @param uuid the NPC's UUID
     * @return the current position, or null if not active this frame
     */
    public WorldPosition getPosition(String uuid) {
        TrackedNpc npc = trackedNpcs.get(uuid);
        if (npc != null && npc.lastUpdateFrame == updateCount) {
            return npc.position;
        }
        return null;
    }

    /**
     * Get the last-known position of a tracked NPC (even if not matched this frame).
     *
     * @param uuid the NPC's UUID
     * @return the last-known position, or null if UUID is unknown
     */
    public WorldPosition getLastKnownPosition(String uuid) {
        TrackedNpc npc = trackedNpcs.get(uuid);
        return npc != null ? npc.position : null;
    }

    /**
     * Get all currently active NPC UUIDs and positions (matched this frame).
     *
     * @return map of UUID to position for all NPCs matched in the latest update
     */
    public Map<String, WorldPosition> getActiveNpcs() {
        Map<String, WorldPosition> result = new HashMap<>();
        for (Map.Entry<String, TrackedNpc> entry : trackedNpcs.entrySet()) {
            if (entry.getValue().lastUpdateFrame == updateCount) {
                result.put(entry.getKey(), entry.getValue().position);
            }
        }
        return result;
    }

    /**
     * Get time since a UUID was last seen.
     *
     * @param uuid the NPC's UUID
     * @return milliseconds since last seen, or Long.MAX_VALUE if UUID unknown
     */
    public long timeSinceLastSeen(String uuid) {
        TrackedNpc npc = trackedNpcs.get(uuid);
        if (npc == null) return Long.MAX_VALUE;
        return System.currentTimeMillis() - npc.lastSeenTimestamp;
    }

    /**
     * Clear all tracked NPCs. Call on logout/relog/recovery.
     */
    public void clear() {
        trackedNpcs.clear();
        lastUpdateTime = 0;
        updateCount = 0;
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Inner Class
    // ───────────────────────────────────────────────────────────────────────────

    private static class TrackedNpc {
        final String uuid;
        WorldPosition position;
        long lastSeenTimestamp;
        long lastUpdateFrame;
        double vx = 0;
        double vy = 0;
        int stillTicks = 0;

        TrackedNpc(String uuid, WorldPosition pos, long now, long frame) {
            this.uuid = uuid;
            this.position = pos;
            this.lastSeenTimestamp = now;
            this.lastUpdateFrame = frame;
        }

        void update(WorldPosition newPos, double deltaTicks, long now, long frame) {
            double dx = newPos.getPreciseX() - position.getPreciseX();
            double dy = newPos.getPreciseY() - position.getPreciseY();

            // Exponential smoothing of velocity
            this.vx = (dx / deltaTicks) * CombatConstants.VELOCITY_WEIGHT_NEW
                     + (vx * CombatConstants.VELOCITY_WEIGHT_OLD);
            this.vy = (dy / deltaTicks) * CombatConstants.VELOCITY_WEIGHT_NEW
                     + (vy * CombatConstants.VELOCITY_WEIGHT_OLD);

            if (Math.abs(dx) < CombatConstants.STILL_THRESHOLD
                    && Math.abs(dy) < CombatConstants.STILL_THRESHOLD) {
                stillTicks++;
            } else {
                stillTicks = 0;
            }

            this.position = newPos;
            this.lastSeenTimestamp = now;
            this.lastUpdateFrame = frame;
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Utility
    // ───────────────────────────────────────────────────────────────────────────

    private static double chebyshev(double x1, double y1, double x2, double y2) {
        return Math.max(Math.abs(x1 - x2), Math.abs(y1 - y2));
    }
}
