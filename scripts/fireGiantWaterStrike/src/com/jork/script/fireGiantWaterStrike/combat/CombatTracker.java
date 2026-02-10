package com.jork.script.fireGiantWaterStrike.combat;

import com.osmb.api.location.position.types.WorldPosition;

/**
 * Combat state wrapper around {@link NpcTracker}.
 *
 * Manages the current combat target by UUID, engagement state, idle tracking
 * for kill detection, and death position capture for loot targeting.
 *
 * CombatTracker never accesses the minimap directly. All position tracking
 * is delegated to NpcTracker. The main class calls
 * {@code combatTracker.getNpcTracker().update(positions)} once per poll cycle,
 * then uses CombatTracker's methods for combat decisions.
 */
public class CombatTracker {

    private final NpcTracker npcTracker;

    // Combat target identity
    private String targetUuid;
    private WorldPosition lastDeathCenterPos;

    // Engagement state
    private long engagementStartTime;
    private boolean engaged;

    // Kill detection: sustained idle tracking
    private long idleStartTime;

    // HealthOverlay HP tracking
    private Integer lastKnownHp;
    private long lastHpChangeTime;
    private long overlayMissingStartTime;

    public CombatTracker() {
        this.npcTracker = new NpcTracker();
    }

    // ───────────────────────────────────────────────────────────────────────────
    // NpcTracker Delegation
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Get the underlying NpcTracker.
     * Used by poll() to call {@link NpcTracker#update} once per cycle.
     *
     * @return the NpcTracker instance
     */
    public NpcTracker getNpcTracker() {
        return npcTracker;
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Target Management
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Set the current combat target by UUID.
     * Called by ACQUIRE_TARGET after selecting an NPC from the tracker.
     *
     * @param uuid the NPC's UUID from NpcTracker
     */
    public void setTarget(String uuid) {
        this.targetUuid = uuid;
        this.engaged = false;
        this.idleStartTime = 0;
    }

    /**
     * Get the target's current position from NpcTracker.
     *
     * @return the current position, or null if target not active this frame
     */
    public WorldPosition getTargetPosition() {
        if (targetUuid == null) return null;
        return npcTracker.getPosition(targetUuid);
    }

    /**
     * Get the target's last-known position (even if not matched this frame).
     *
     * @return the last-known position, or null if no target set
     */
    public WorldPosition getTargetLastKnownPosition() {
        if (targetUuid == null) return null;
        return npcTracker.getLastKnownPosition(targetUuid);
    }

    /**
     * Check if the target UUID is still actively tracked this frame.
     *
     * @return true if target was matched in the latest NpcTracker update
     */
    public boolean isTargetActive() {
        if (targetUuid == null) return false;
        return npcTracker.isActive(targetUuid);
    }

    /**
     * Get the target UUID.
     *
     * @return the current target's UUID, or null if no target set
     */
    public String getTargetUuid() {
        return targetUuid;
    }

    /**
     * Get last death center position (for loot targeting).
     *
     * @return the position where the last kill was detected, or null
     */
    public WorldPosition getLastDeathCenterPos() {
        return lastDeathCenterPos;
    }

    /**
     * Check if currently engaged in combat.
     *
     * @return true between engagement and kill/loss
     */
    public boolean isEngaged() {
        return engaged;
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Engagement
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Mark combat engagement started. Called when attack tap succeeds.
     */
    public void markEngaged() {
        this.engaged = true;
        this.engagementStartTime = System.currentTimeMillis();
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Kill Detection Helpers
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Time since target UUID was last seen by NpcTracker.
     *
     * @return milliseconds since last seen, or Long.MAX_VALUE if no target
     */
    public long timeSinceTargetSeen() {
        if (targetUuid == null) return Long.MAX_VALUE;
        return npcTracker.timeSinceLastSeen(targetUuid);
    }

    /**
     * Start or continue idle timer. Called when player is detected idle.
     */
    public void markIdleStart() {
        if (idleStartTime == 0) {
            idleStartTime = System.currentTimeMillis();
        }
    }

    /**
     * Clear idle timer. Called when player is detected animating.
     */
    public void clearIdleStart() {
        idleStartTime = 0;
    }

    /**
     * Get sustained idle duration in ms.
     *
     * @return milliseconds of sustained idle, or 0 if not idle
     */
    public long getSustainedIdleMs() {
        if (idleStartTime == 0) return 0;
        return System.currentTimeMillis() - idleStartTime;
    }

    // ───────────────────────────────────────────────────────────────────────────
    // HealthOverlay HP Tracking
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Update tracked HP from HealthOverlay reading.
     * Only records a change (and resets the stale timer) when HP actually differs.
     *
     * @param currentHp current hitpoints from HealthOverlay, or null if unavailable
     */
    public void updateHp(Integer currentHp) {
        if (currentHp == null && lastKnownHp == null) return;
        if (currentHp != null && currentHp.equals(lastKnownHp)) return;

        lastKnownHp = currentHp;
        lastHpChangeTime = System.currentTimeMillis();
    }

    /**
     * Check if the last known HP is zero (definitive kill signal).
     *
     * @return true if HP was read as 0
     */
    public boolean isHpDead() {
        return lastKnownHp != null && lastKnownHp == 0;
    }

    /**
     * Get the time since HP last changed value.
     *
     * @return milliseconds since last HP change, or 0 if no HP has been recorded
     */
    public long getHpStaleMs() {
        if (lastHpChangeTime == 0) return 0;
        return System.currentTimeMillis() - lastHpChangeTime;
    }

    /**
     * Get the last known HP value from HealthOverlay.
     *
     * @return the last HP reading, or null if never read
     */
    public Integer getLastKnownHp() {
        return lastKnownHp;
    }

    /**
     * Marks that HealthOverlay is currently visible.
     * Clears any in-progress "overlay missing" timer.
     */
    public void markOverlayVisible() {
        overlayMissingStartTime = 0;
    }

    /**
     * Starts/continues a timer for how long HealthOverlay has been missing.
     * Only used when a target was previously engaged and had known HP.
     */
    public void markOverlayMissing() {
        if (overlayMissingStartTime == 0) {
            overlayMissingStartTime = System.currentTimeMillis();
        }
    }

    /**
     * Returns how long HealthOverlay has been continuously missing.
     *
     * @return milliseconds missing, or 0 if not currently missing
     */
    public long getOverlayMissingMs() {
        if (overlayMissingStartTime == 0) return 0;
        return System.currentTimeMillis() - overlayMissingStartTime;
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Death Capture
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Capture death position from NpcTracker's last-known position for the target UUID.
     * Uses {@link NpcTracker#getLastKnownPosition} (not getPosition) because the target
     * may already be evicted from the active set but still has a last-known position stored.
     */
    public void captureDeathPosition() {
        if (targetUuid != null) {
            WorldPosition pos = npcTracker.getLastKnownPosition(targetUuid);
            if (pos != null) {
                lastDeathCenterPos = pos;
            }
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Reset
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Reset combat state for a new combat cycle.
     * Clears target UUID, engagement, idle tracking.
     * Does NOT clear lastDeathCenterPos (needed by POST_KILL for loot).
     * Does NOT clear NpcTracker (other NPCs still being tracked).
     */
    public void reset() {
        targetUuid = null;
        engaged = false;
        idleStartTime = 0;
        lastKnownHp = null;
        lastHpChangeTime = 0;
        overlayMissingStartTime = 0;
    }

    /**
     * Full reset including death position and NpcTracker.
     * Called on logout, relog, or recovery.
     */
    public void fullReset() {
        reset();
        lastDeathCenterPos = null;
        npcTracker.clear();
    }
}
