package com.jork.utils.metrics.providers;

import com.osmb.api.script.Script;
import com.osmb.api.shape.Rectangle;
import com.osmb.api.trackers.experience.XPTracker;
import com.osmb.api.ui.component.ComponentSearchResult;
import com.osmb.api.ui.component.minimap.xpcounter.XPDropsComponent;
import com.osmb.api.ui.component.tabs.skill.SkillType;
import com.osmb.api.utils.RandomUtils;
import com.osmb.api.visual.color.ColorModel;
import com.osmb.api.visual.color.tolerance.impl.SingleThresholdComparator;
import com.osmb.api.visual.image.SearchableImage;
import com.jork.utils.ExceptionUtils;
import com.jork.utils.ScriptLogger;

/**
 * OCR-based XP metric provider that reads XP values from the game display.
 * Works around the unimplemented getSkillExperience() API by using visual detection.
 */
public class XPMetricProvider {
    private Script script;
    private SkillType skillType;
    private int spriteId;
    private SearchableImage skillSprite;
    private XPDropsComponent xpDropsComponent;
    private XPTracker tracker;
    private Integer lastKnownXP;
    private int startingXP;
    
    // Pausable timer implementation
    private long accumulatedTimeMillis = 0;  // Time accumulated while logged in
    private long sessionStartTime = 0;        // When current session started  
    private boolean isPaused = false;         // Whether timer is paused
    private boolean pauseDuringLogoutEnabled = true;  // Config option
    
    /**
     * Initializes the XP tracker for a specific skill using OCR
     * @param script The script instance
     * @param skillType The skill to track
     * @param spriteId The sprite ID for the skill icon (e.g., 220 for Hunter)
     */
    public void initialize(Script script, SkillType skillType, int spriteId) {
        this.script = script;
        this.skillType = skillType;
        this.spriteId = spriteId;
        
        // Get XP drops component
        this.xpDropsComponent = (XPDropsComponent) script.getWidgetManager().getComponent(XPDropsComponent.class);
        
        // Initialize skill sprite for detection
        try {
            // Script extends ScriptCore, so we can pass script directly
            SearchableImage fullSprite = new SearchableImage(spriteId, script, 
                new SingleThresholdComparator(15), ColorModel.RGB);
            // Use right half of sprite like the example
            this.skillSprite = fullSprite.subImage(fullSprite.width / 2, 0, 
                fullSprite.width / 2, fullSprite.height);
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.warning(script, "Failed to load sprite " + spriteId + ": " + e.getMessage());
        }
        
        // Ensure XP counter is visible
        ensureXPCounterVisible();
        
        // Initialize the pausable timer
        this.sessionStartTime = System.currentTimeMillis();
        this.accumulatedTimeMillis = 0;
        this.isPaused = false;
        
        // Read initial XP
        Integer initialXP = readCurrentXP();
        if (initialXP != null) {
            this.startingXP = initialXP;
            this.lastKnownXP = initialXP;
            // Initialize tracker with 0 to track only gains, not total XP
            this.tracker = new XPTracker(script, 0);
            ScriptLogger.info(script, skillType + " XP Tracker initialized. Starting XP: " + initialXP);
        } else {
            // Initialize with 0 if we can't read XP
            this.startingXP = 0;
            this.lastKnownXP = 0;
            this.tracker = new XPTracker(script, 0);
            ScriptLogger.warning(script, "Could not read initial XP for " + skillType + ", starting at 0");
        }
    }
    
    /**
     * Ensures the XP counter display is active and visible
     */
    private boolean ensureXPCounterVisible() {
        if (xpDropsComponent == null) {
            ScriptLogger.warning(script, "XPDropsComponent is null");
            return false;
        }
        
        Rectangle bounds = xpDropsComponent.getBounds();
        if (bounds == null) {
            ScriptLogger.warning(script, "Failed to get XP drops component bounds");
            return false;
        }
        
        ComponentSearchResult<Integer> result = xpDropsComponent.getResult();
        if (result == null || result.getComponentImage().getGameFrameStatusType() != 1) {
            ScriptLogger.info(script, "XP drops component is not open, attempting to open it");
            
            // Tap to open the XP drops component
            script.getFinger().tap(bounds);
            
            // Wait for it to open
            boolean succeeded = script.pollFramesHuman(() -> {
                ComponentSearchResult<Integer> res = xpDropsComponent.getResult();
                return res != null && res.getComponentImage().getGameFrameStatusType() == 1;
            }, RandomUtils.uniformRandom(1500, 3000));
            
            if (succeeded) {
                ScriptLogger.info(script, "XP drops component opened successfully");
            } else {
                ScriptLogger.warning(script, "Failed to open XP drops component");
            }
            
            return succeeded;
        }
        
        return true;
    }
    
    /**
     * Reads the current XP value using OCR
     */
    private Integer readCurrentXP() {
        Rectangle bounds = getXPDropsBounds();
        if (bounds == null) {
            return null;
        }
        
        // Check if our skill icon is present
        if (skillSprite != null) {
            boolean isCorrectSkill = script.getImageAnalyzer().findLocation(bounds, skillSprite) != null;
            if (!isCorrectSkill) {
                // Not showing our skill, return last known value
                return lastKnownXP;
            }
        }
        
        // Use OCR to read the XP value
        String xpText = script.getOCR().getText(com.osmb.api.visual.ocr.fonts.Font.SMALL_FONT, bounds, -1)
            .replaceAll("[^0-9]", "");
        
        if (xpText.isEmpty()) {
            return null;
        }
        
        try {
            return Integer.parseInt(xpText);
        } catch (NumberFormatException e) {
            ScriptLogger.warning(script, "Failed to parse XP text: " + xpText);
            return null;
        }
    }
    
    /**
     * Gets the bounds of the XP drops area
     */
    private Rectangle getXPDropsBounds() {
        if (xpDropsComponent == null) {
            return null;
        }
        
        Rectangle bounds = xpDropsComponent.getBounds();
        if (bounds == null) {
            return null;
        }
        
        ComponentSearchResult<Integer> result = xpDropsComponent.getResult();
        if (result == null || result.getComponentImage().getGameFrameStatusType() != 1) {
            return null;
        }
        
        // Return the area where XP text appears (to the left of the XP drops button)
        return new Rectangle(bounds.x - 140, bounds.y - 1, 119, 38);
    }
    
    /**
     * Updates the current XP from the game display
     */
    public void update() {
        if (script == null || skillType == null || tracker == null) {
            return;
        }
        
        Integer currentXP = readCurrentXP();
        if (currentXP != null && currentXP > 0) {
            // First time seeing XP? Update starting point
            if (startingXP == 0 && lastKnownXP == 0) {
                startingXP = currentXP;
                lastKnownXP = currentXP;
                ScriptLogger.info(script, skillType + " XP tracking started at: " + currentXP);
                return;
            }
            
            // Only update if we got a valid reading and XP increased
            if (currentXP > lastKnownXP) {
                // Only reset timer if not currently paused (e.g., not during break/hop preparation)
                if (!isPaused) {
                    accumulatedTimeMillis = 0;
                    sessionStartTime = System.currentTimeMillis();
                }
                // Note: Don't change isPaused state here - only resumeTimer() should unpause
                
                double gained = currentXP - lastKnownXP;
                tracker.incrementXp(gained);
                int totalGained = currentXP - startingXP;
                ScriptLogger.debug(script, skillType + " XP gained this tick: " + gained + 
                                 " | Total gained: " + totalGained + " | Current XP: " + currentXP);
            }
            lastKnownXP = currentXP;
        }
    }
    
    /**
     * Gets the total XP gained since initialization
     */
    public int getXPGained() {
        if (tracker != null) {
            return (int) tracker.getXpGained();
        }
        return (lastKnownXP != null ? lastKnownXP : 0) - startingXP;
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
     * Gets the formatted time to next level using accurate OSRS XP data
     */
    public String getTimeToLevel() {
        if (lastKnownXP == null || lastKnownXP == 0) {
            return "N/A";
        }
        
        // Get XP per hour from tracker (it calculates this correctly)
        int xpPerHour = tracker != null ? tracker.getXpPerHour() : 0;
        if (xpPerHour <= 0) {
            return "N/A";
        }
        
        // Calculate time to level using our accurate XP data
        long timeMillis = XPLevelData.calculateTimeToLevel(lastKnownXP, xpPerHour);
        if (timeMillis <= 0) {
            return "N/A";
        }
        
        return XPLevelData.formatTime(timeMillis);
    }
    
    /**
     * Gets the time to next level with custom XP/hour rate using accurate OSRS XP data
     */
    public String getTimeToLevel(double xpPerHour) {
        if (lastKnownXP == null || lastKnownXP == 0 || xpPerHour <= 0) {
            return "N/A";
        }
        
        // Calculate time to level using our accurate XP data
        long timeMillis = XPLevelData.calculateTimeToLevel(lastKnownXP, (int) xpPerHour);
        if (timeMillis <= 0) {
            return "N/A";
        }
        
        return XPLevelData.formatTime(timeMillis);
    }
    
    /**
     * Gets the level progress percentage using accurate OSRS XP data
     */
    public int getLevelProgress() {
        if (lastKnownXP == null || lastKnownXP == 0) {
            return 0;
        }
        
        // Calculate progress using our accurate XP data
        double progress = XPLevelData.getLevelProgress(lastKnownXP);
        return (int) Math.round(progress);
    }
    
    /**
     * Gets the current level using accurate OSRS XP data
     */
    public int getCurrentLevel() {
        if (lastKnownXP == null || lastKnownXP == 0) {
            return 1;
        }
        
        // Get level using our accurate XP data
        return XPLevelData.getLevelForXP(lastKnownXP);
    }
    
    /**
     * Gets XP needed for next level using accurate OSRS XP data
     */
    public double getXPForNextLevel() {
        if (lastKnownXP == null || lastKnownXP == 0) {
            return 0;
        }
        
        // Get XP needed using our accurate XP data
        return XPLevelData.getXPToNextLevel(lastKnownXP);
    }
    
    /**
     * Resets the tracker (for new sessions)
     */
    public void reset() {
        if (tracker != null && script != null) {
            update(); // Get latest XP
            startingXP = lastKnownXP != null ? lastKnownXP : 0;
            // Reset tracker to 0 gains, not current XP total
            tracker = new XPTracker(script, 0);
            ScriptLogger.info(script, skillType + " XP Tracker reset. New starting XP: " + startingXP);
        }
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
    public Integer getLastKnownXP() {
        return lastKnownXP;
    }
    
    /**
     * Gets the starting XP value
     */
    public int getStartingXP() {
        return startingXP;
    }
    
    /**
     * Sets whether the timer should pause during logout
     * @param enabled Whether pause during logout is enabled
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
     * @return Time elapsed since last XP gain in milliseconds
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
     * @return Formatted time string (HH:mm:ss.SSS)
     */
    public String getTimeSinceLastXPGainFormatted() {
        long timeElapsed = getTimeSinceLastXPGain();
        
        // Format as HH:mm:ss.SSS
        long hours = timeElapsed / 3600000;
        long minutes = (timeElapsed % 3600000) / 60000;
        long seconds = (timeElapsed % 60000) / 1000;
        long millis = timeElapsed % 1000;
        
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis);
    }
}
