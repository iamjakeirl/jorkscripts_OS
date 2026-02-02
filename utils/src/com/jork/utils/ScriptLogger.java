package com.jork.utils;

import com.osmb.api.script.Script;

/**
 * Utility class providing standardized logging functionality for all scripts.
 * This class provides consistent logging methods with proper formatting and categorization.
 * 
 * Usage:
 * ScriptLogger.info(this, "Your message");
 * ScriptLogger.error(this, "Error message");
 * 
 * @author jork
 */
public class ScriptLogger {

    /**
     * Log levels for global filtering.
     */
    public enum Level {
        ERROR(3),
        WARNING(2),
        INFO(1),
        DEBUG(0);
        final int priority;
        Level(int p) { this.priority = p; }
    }

    /**
     * Global minimum level to log. Defaults to INFO (DEBUG suppressed).
     */
    private static volatile Level minLevel = Level.INFO;

    /**
     * Set the global minimum level. Messages below this level are suppressed.
     */
    public static void setMinLevel(Level level) {
        if (level != null) {
            minLevel = level;
        }
    }

    /**
     * Convenience toggle for debug logging.
     */
    public static void setDebugEnabled(boolean enabled) {
        minLevel = enabled ? Level.DEBUG : Level.INFO;
    }

    private static boolean shouldLog(Level level) {
        return level.priority >= minLevel.priority;
    }
    
    /**
     * Private constructor to prevent instantiation of utility class
     */
    private ScriptLogger() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Log an informational message with class name prefix
     * @param script The script instance calling this method
     * @param message The message to log
     */
    public static void info(Script script, String message) {
        if (shouldLog(Level.INFO)) {
            script.log(script.getClass().getSimpleName(), "[INFO] " + message);
        }
    }
    
    /**
     * Log a warning message with class name prefix
     * @param script The script instance calling this method
     * @param message The warning message to log
     */
    public static void warning(Script script, String message) {
        if (shouldLog(Level.WARNING)) {
            script.log(script.getClass().getSimpleName(), "[WARNING] " + message);
        }
    }
    
    /**
     * Log an error message with class name prefix
     * @param script The script instance calling this method
     * @param message The error message to log
     */
    public static void error(Script script, String message) {
        if (shouldLog(Level.ERROR)) {
            script.log(script.getClass().getSimpleName(), "[ERROR] " + message);
        }
    }
    
    /**
     * Log a debug message with class name prefix
     * @param script The script instance calling this method
     * @param message The debug message to log
     */
    public static void debug(Script script, String message) {
        if (shouldLog(Level.DEBUG)) {
            script.log(script.getClass().getSimpleName(), "[DEBUG] " + message);
        }
    }
    
    /**
     * Log a state change with detailed information
     * @param script The script instance calling this method
     * @param oldState The previous state
     * @param newState The new state
     * @param reason The reason for the state change
     */
    public static void stateChange(Script script, Object oldState, Object newState, String reason) {
        info(script, "State change: " + oldState + " -> " + newState + " (" + reason + ")");
    }
    
    /**
     * Log inventory status for debugging
     * @param script The script instance calling this method
     * @param itemCounts Variable number of item count pairs (name, count, name, count, ...)
     */
    public static void inventoryStatus(Script script, Object... itemCounts) {
        if (itemCounts.length % 2 != 0) {
            error(script, "Invalid inventory status parameters - must be pairs of (name, count)");
            return;
        }
        
        StringBuilder sb = new StringBuilder("Inventory - ");
        for (int i = 0; i < itemCounts.length; i += 2) {
            if (i > 0) sb.append(", ");
            sb.append(itemCounts[i]).append(": ").append(itemCounts[i + 1]);
        }
        
        debug(script, sb.toString());
    }
    
    /**
     * Log action attempt with timing
     * @param script The script instance calling this method
     * @param action The action being attempted
     */
    public static void actionAttempt(Script script, String action) {
        info(script, "Attempting: " + action);
    }
    
    /**
     * Log action success
     * @param script The script instance calling this method
     * @param action The action that succeeded
     */
    public static void actionSuccess(Script script, String action) {
        info(script, "Success: " + action);
    }
    
    /**
     * Log action failure with retry information
     * @param script The script instance calling this method
     * @param action The action that failed
     * @param retryCount Current retry count
     * @param maxRetries Maximum retries allowed
     */
    public static void actionFailure(Script script, String action, int retryCount, int maxRetries) {
        warning(script, "Failed: " + action + " (Retry " + retryCount + "/" + maxRetries + ")");
    }
    
    /**
     * Log navigation attempt
     * @param script The script instance calling this method
     * @param destination The destination being navigated to
     */
    public static void navigation(Script script, String destination) {
        info(script, "Navigating to: " + destination);
    }
    
    /**
     * Log script startup information
     * @param script The script instance calling this method
     * @param version The script version
     * @param author The script author
     * @param target The target activity/skill
     */
    public static void startup(Script script, String version, String author, String target) {
        String scriptName = script.getClass().getSimpleName();
        info(script, "=== " + scriptName + " Script Starting ===");
        info(script, "Version: " + version);
        info(script, "Author: " + author);
        info(script, "Target: " + target);
    }
    
    /**
     * Log script shutdown information
     * @param script The script instance calling this method
     * @param reason The reason for shutdown
     */
    public static void shutdown(Script script, String reason) {
        String scriptName = script.getClass().getSimpleName();
        info(script, "=== " + scriptName + " Script Stopping ===");
        info(script, "Reason: " + reason);
    }
    
    /**
     * Log with custom level and formatting
     * @param script The script instance calling this method
     * @param level The log level (e.g., "CUSTOM", "TRACE")
     * @param message The message to log
     */
    public static void custom(Script script, String level, String message) {
        // Map known levels to gating; default to INFO
        String upper = level == null ? "INFO" : level.toUpperCase();
        Level mapped = switch (upper) {
            case "ERROR" -> Level.ERROR;
            case "WARN", "WARNING" -> Level.WARNING;
            case "INFO" -> Level.INFO;
            case "DEBUG" -> Level.DEBUG;
            default -> Level.INFO;
        };
        if (shouldLog(mapped)) {
            script.log(script.getClass().getSimpleName(), "[" + upper + "] " + message);
        }
    }
    
    /**
     * Log exception with stack trace
     * @param script The script instance calling this method
     * @param action The action that caused the exception
     * @param exception The exception that occurred
     */
    public static void exception(Script script, String action, Exception exception) {
        error(script, "Exception during " + action + ": " + exception.getMessage());
        // Optionally log stack trace for debugging
        debug(script, "Stack trace: " + getStackTraceString(exception));
    }
    
    /**
     * Convert exception stack trace to string for logging
     * @param exception The exception
     * @return Stack trace as string
     */
    private static String getStackTraceString(Exception exception) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        exception.printStackTrace(pw);
        return sw.toString();
    }
} 
