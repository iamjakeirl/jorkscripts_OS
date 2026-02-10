package com.jork.utils.metrics.providers;

import com.osmb.api.script.Script;
import com.osmb.api.trackers.experience.XPTracker;
import com.osmb.api.ui.component.tabs.skill.SkillType;
import com.jork.utils.ExceptionUtils;
import com.jork.utils.ScriptLogger;

import java.util.Map;

/**
 * XP metric provider that delegates to OSMB's native XPTracker system.
 * The native tracker handles all XP reading via XPDropsListener internally.
 * This provider adds a pausable "time since last XP gain" timer for failsafe support.
 */
public class XPMetricProvider {
    private Script script;
    private SkillType skillType;
    private XPTracker tracker;
    private double lastKnownXP;
    private double startingXP;
    private boolean trackerWarningLogged = false;

    // Pausable timer implementation
    private long accumulatedTimeMillis = 0;
    private long sessionStartTime = 0;
    private boolean isPaused = false;
    private boolean pauseDuringLogoutEnabled = true;

    /**
     * Initializes the XP tracker for a specific skill using OSMB's native tracking.
     * @param script The script instance
     * @param skillType The skill to track
     */
    public void initialize(Script script, SkillType skillType) {
        this.script = script;
        this.skillType = skillType;

        // Start the pausable timer
        this.sessionStartTime = System.currentTimeMillis();
        this.accumulatedTimeMillis = 0;
        this.isPaused = false;

        // Attempt to acquire native tracker
        acquireNativeTracker();

        if (tracker != null) {
            this.startingXP = tracker.getXp();
            this.lastKnownXP = startingXP;
            ScriptLogger.info(script, skillType + " XP Tracker initialized (native). Starting XP: " + (int) startingXP);
        } else {
            this.startingXP = 0;
            this.lastKnownXP = 0;
            ScriptLogger.warning(script, "Native XP tracker not yet available for " + skillType + " - will retry on update");
        }
    }

    /**
     * Attempts to acquire the native XPTracker from OSMB's tracker map.
     */
    private void acquireNativeTracker() {
        if (tracker != null) return;

        try {
            Map<SkillType, XPTracker> trackers = script.getXPTrackers();
            if (trackers != null) {
                this.tracker = trackers.get(skillType);
            }
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
        }

        if (tracker != null && trackerWarningLogged) {
            ScriptLogger.info(script, "Native XP tracker acquired for " + skillType);
            trackerWarningLogged = false;
        }
    }

    /**
     * Updates by polling the native tracker for XP changes.
     * Resets the pausable timer when XP gain is detected.
     */
    public void update() {
        if (script == null || skillType == null) {
            return;
        }

        // Retry acquiring native tracker if we don't have one yet
        if (tracker == null) {
            acquireNativeTracker();
            if (tracker == null) {
                if (!trackerWarningLogged) {
                    ScriptLogger.warning(script, "Native XP tracker still unavailable for " + skillType);
                    trackerWarningLogged = true;
                }
                return;
            }
            // Just acquired -- capture starting XP
            startingXP = tracker.getXp();
            lastKnownXP = startingXP;
            ScriptLogger.info(script, skillType + " XP tracking started at: " + (int) startingXP);
            return;
        }

        double currentXP = tracker.getXp();

        if (currentXP > lastKnownXP) {
            // XP gained -- reset the pausable timer (only if not paused)
            if (!isPaused) {
                accumulatedTimeMillis = 0;
                sessionStartTime = System.currentTimeMillis();
            }

            ScriptLogger.debug(script, skillType + " XP gained: " + (int)(currentXP - lastKnownXP) +
                " | Total gained: " + (int)(currentXP - startingXP) + " | Current XP: " + (int) currentXP);
        }
        lastKnownXP = currentXP;
    }

    /**
     * Gets the total XP gained since initialization
     */
    public int getXPGained() {
        if (tracker != null) {
            return (int) tracker.getXpGained();
        }
        return 0;
    }

    /**
     * Gets the XP per hour rate
     */
    public int getXPPerHour() {
        if (tracker != null) {
            return tracker.getXpPerHour();
        }
        return 0;
    }

    /**
     * Gets the formatted time to next level
     */
    public String getTimeToLevel() {
        if (tracker != null) {
            String ttl = tracker.timeToNextLevelString();
            return (ttl != null && !ttl.isEmpty()) ? ttl : "N/A";
        }
        return "N/A";
    }

    /**
     * Gets the time to next level with custom XP/hour rate
     */
    public String getTimeToLevel(double xpPerHour) {
        if (tracker != null && xpPerHour > 0) {
            String ttl = tracker.timeToNextLevelString(xpPerHour);
            return (ttl != null && !ttl.isEmpty()) ? ttl : "N/A";
        }
        return "N/A";
    }

    /**
     * Gets the level progress percentage
     */
    public int getLevelProgress() {
        if (tracker != null) {
            return tracker.getLevelProgressPercentage();
        }
        return 0;
    }

    /**
     * Gets the current level
     */
    public int getCurrentLevel() {
        if (tracker != null) {
            return tracker.getLevel();
        }
        return 1;
    }

    /**
     * Gets XP needed for next level
     */
    public double getXPForNextLevel() {
        if (tracker != null) {
            return tracker.getXpForNextLevel();
        }
        return 0;
    }

    /**
     * Resets the tracker for new sessions
     */
    public void reset() {
        if (tracker != null) {
            lastKnownXP = tracker.getXp();
            startingXP = lastKnownXP;
            ScriptLogger.info(script, skillType + " XP Tracker reset. New starting XP: " + (int) startingXP);
        }
        accumulatedTimeMillis = 0;
        sessionStartTime = System.currentTimeMillis();
        isPaused = false;
    }

    /**
     * Gets the raw XP tracker for advanced usage
     */
    public XPTracker getTracker() {
        return tracker;
    }

    /**
     * Gets the last known XP value
     */
    public double getLastKnownXP() {
        return lastKnownXP;
    }

    /**
     * Gets the starting XP value
     */
    public double getStartingXP() {
        return startingXP;
    }

    // ── Pausable timer (for XP failsafe) ────────────────────────────────

    /**
     * Sets whether the timer should pause during logout
     */
    public void setPauseDuringLogout(boolean enabled) {
        this.pauseDuringLogoutEnabled = enabled;
    }

    /**
     * Pauses the timer (for breaks/hops)
     */
    public void pauseTimer() {
        if (!pauseDuringLogoutEnabled || isPaused) return;

        if (sessionStartTime > 0) {
            accumulatedTimeMillis += System.currentTimeMillis() - sessionStartTime;
            isPaused = true;
            sessionStartTime = 0;
        }
    }

    /**
     * Resumes the timer after pause
     */
    public void resumeTimer() {
        if (!pauseDuringLogoutEnabled || !isPaused) return;

        sessionStartTime = System.currentTimeMillis();
        isPaused = false;
    }

    /**
     * Gets the time in milliseconds since the last XP gain
     */
    public long getTimeSinceLastXPGain() {
        if (isPaused) {
            return accumulatedTimeMillis;
        }
        if (sessionStartTime > 0) {
            return accumulatedTimeMillis + (System.currentTimeMillis() - sessionStartTime);
        }
        return 0;
    }

    /**
     * Gets the formatted time since the last XP gain
     */
    public String getTimeSinceLastXPGainFormatted() {
        long timeElapsed = getTimeSinceLastXPGain();

        long hours = timeElapsed / 3600000;
        long minutes = (timeElapsed % 3600000) / 60000;
        long seconds = (timeElapsed % 60000) / 1000;
        long millis = timeElapsed % 1000;

        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis);
    }
}
