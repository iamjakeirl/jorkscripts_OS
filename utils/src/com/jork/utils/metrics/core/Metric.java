package com.jork.utils.metrics.core;

import java.util.function.Supplier;

/**
 * Represents a single trackable metric with its label, value provider, and formatting.
 * This class encapsulates all the data needed to track and display a metric.
 */
public class Metric {
    private final String label;
    private final Supplier<Object> valueSupplier;
    private final MetricType type;
    private final String customFormat;
    
    // Caching for performance
    private Object cachedValue;
    private Object cachedFormattedValue;
    private long lastUpdateTime;
    private long updateInterval;
    
    // For rate calculations
    private Object initialValue;
    private long startTime;
    
    /**
     * Creates a new metric with default formatting
     */
    public Metric(String label, Supplier<Object> valueSupplier, MetricType type) {
        this(label, valueSupplier, type, null, 1000); // Default 1 second update interval
    }
    
    /**
     * Creates a new metric with custom format
     */
    public Metric(String label, Supplier<Object> valueSupplier, MetricType type, String customFormat) {
        this(label, valueSupplier, type, customFormat, 1000);
    }
    
    /**
     * Full constructor with all options
     */
    public Metric(String label, Supplier<Object> valueSupplier, MetricType type, 
                  String customFormat, long updateInterval) {
        this.label = label;
        this.valueSupplier = valueSupplier;
        this.type = type;
        this.customFormat = customFormat;
        this.updateInterval = updateInterval;
        this.startTime = System.currentTimeMillis();
        this.lastUpdateTime = 0;
        
        // Capture initial value for rate calculations
        if (type.isRateBased()) {
            this.initialValue = valueSupplier.get();
        }
    }
    
    /**
     * Updates the cached value if the update interval has passed
     */
    public void update() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= updateInterval) {
            cachedValue = valueSupplier.get();
            lastUpdateTime = currentTime;
            
            // Calculate formatted value
            if (type.isRateBased() && initialValue != null) {
                cachedFormattedValue = calculateRate();
            } else {
                cachedFormattedValue = formatValue(cachedValue);
            }
        }
    }
    
    /**
     * Calculates the per-hour rate for rate-based metrics
     */
    private Object calculateRate() {
        if (cachedValue instanceof Number && initialValue instanceof Number) {
            double current = ((Number) cachedValue).doubleValue();
            double initial = ((Number) initialValue).doubleValue();
            double gained = current - initial;
            
            long elapsedMs = System.currentTimeMillis() - startTime;
            double hoursElapsed = elapsedMs / 3600000.0;
            
            if (hoursElapsed > 0) {
                return (int) (gained / hoursElapsed);
            }
        }
        return 0;
    }
    
    /**
     * Formats a value based on the metric type and format string
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "N/A";
        }
        
        String format = customFormat != null ? customFormat : type.getDefaultFormat();
        
        // Special handling for TIME type
        if (type == MetricType.TIME) {
            if (value instanceof Long) {
                return formatTime((Long) value);
            } else if (value instanceof String) {
                return (String) value; // Already formatted
            }
        }
        
        // Handle numeric formatting
        if (value instanceof Number) {
            Number num = (Number) value;
            if (format.contains("d")) {
                return String.format(format, num.intValue());
            } else if (format.contains("f")) {
                return String.format(format, num.doubleValue());
            }
        }
        
        // Default to string formatting
        return String.format(format.isEmpty() ? "%s" : format, value);
    }
    
    /**
     * Formats milliseconds as HH:MM:SS
     */
    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }
    
    // Getters
    
    public String getLabel() {
        return label;
    }
    
    public Object getValue() {
        update();
        return cachedValue;
    }
    
    public String getFormattedValue() {
        update();
        if (cachedFormattedValue != null) {
            return cachedFormattedValue.toString();
        }
        return formatValue(cachedValue);
    }
    
    public MetricType getType() {
        return type;
    }
    
    public void setUpdateInterval(long interval) {
        this.updateInterval = interval;
    }
    
    /**
     * Resets the metric's initial values (useful for session resets)
     */
    public void reset() {
        this.startTime = System.currentTimeMillis();
        this.lastUpdateTime = 0;
        this.cachedValue = null;
        this.cachedFormattedValue = null;
        
        if (type.isRateBased()) {
            this.initialValue = valueSupplier.get();
        }
    }
}