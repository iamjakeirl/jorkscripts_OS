package com.jork.utils.metrics.core;

/**
 * Enum defining the different types of metrics that can be tracked and displayed.
 * Each type has specific formatting and calculation behaviors.
 */
public enum MetricType {
    /**
     * Simple numeric value display (e.g., "5", "123")
     */
    NUMBER("%,d"),
    
    /**
     * Rate calculation with per-hour suffix (e.g., "250/hr")
     */
    RATE("%,d/hr"),
    
    /**
     * Percentage display (e.g., "85.5%")
     */
    PERCENTAGE("%.1f%%"),
    
    /**
     * Time duration display (e.g., "01:23:45")
     */
    TIME(""),
    
    /**
     * Experience points with automatic per-hour calculation
     */
    XP("%,d"),
    
    /**
     * Simple counter that increments
     */
    COUNTER("%,d"),
    
    /**
     * Text-based status display
     */
    TEXT("%s");
    
    private final String defaultFormat;
    
    MetricType(String defaultFormat) {
        this.defaultFormat = defaultFormat;
    }
    
    public String getDefaultFormat() {
        return defaultFormat;
    }
    
    /**
     * Determines if this metric type should calculate per-hour rates
     */
    public boolean isRateBased() {
        return this == RATE || this == XP;
    }
    
    /**
     * Determines if this metric type requires special formatting
     */
    public boolean requiresSpecialFormatting() {
        return this == TIME || this == PERCENTAGE;
    }
}