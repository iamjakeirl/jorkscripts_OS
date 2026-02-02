package com.jork.utils.metrics.providers;

import com.osmb.api.utils.timing.Timer;

/**
 * Provider that wraps OSMB's Timer for runtime tracking.
 * Provides formatted runtime and elapsed time calculations.
 */
public class RuntimeMetricProvider {
    private Timer timer;
    private long pausedTime;
    private boolean isPaused;
    
    /**
     * Initializes the runtime timer
     */
    public void initialize() {
        timer = new Timer();
        pausedTime = 0;
        isPaused = false;
    }
    
    /**
     * Updates the timer (placeholder for future pause functionality)
     */
    public void update() {
        // Timer auto-updates, nothing needed here unless implementing pause
    }
    
    /**
     * Gets the elapsed time in milliseconds
     */
    public long getElapsedMillis() {
        if (timer != null) {
            return timer.timeElapsed() - pausedTime;
        }
        return 0;
    }
    
    /**
     * Gets the formatted runtime as HH:MM:SS
     */
    public String getFormattedRuntime() {
        if (timer != null) {
            // Timer provides HH:mm:ss.SSS format, we'll trim the milliseconds
            String fullFormat = timer.getTimeElapsedFormatted();
            if (fullFormat != null && fullFormat.contains(".")) {
                return fullFormat.substring(0, fullFormat.lastIndexOf("."));
            }
            return fullFormat;
        }
        return "00:00:00";
    }
    
    /**
     * Gets the raw formatted runtime with milliseconds (HH:MM:SS.SSS)
     */
    public String getFormattedRuntimeWithMillis() {
        if (timer != null) {
            return timer.getTimeElapsedFormatted();
        }
        return "00:00:00.000";
    }
    
    /**
     * Pauses the timer (for break tracking)
     */
    public void pause() {
        if (!isPaused && timer != null) {
            pausedTime = timer.timeElapsed();
            isPaused = true;
        }
    }
    
    /**
     * Resumes the timer after pause
     */
    public void resume() {
        if (isPaused && timer != null) {
            long currentTime = System.currentTimeMillis();
            timer = new Timer(currentTime - pausedTime);
            isPaused = false;
        }
    }
    
    /**
     * Resets the timer to start from zero
     */
    public void reset() {
        timer = new Timer();
        pausedTime = 0;
        isPaused = false;
    }
    
    /**
     * Gets the hours elapsed as a decimal
     */
    public double getHoursElapsed() {
        return getElapsedMillis() / 3600000.0;
    }
    
    /**
     * Checks if the timer is currently paused
     */
    public boolean isPaused() {
        return isPaused;
    }
    
    /**
     * Gets the raw Timer object for advanced usage
     */
    public Timer getTimer() {
        return timer;
    }
}