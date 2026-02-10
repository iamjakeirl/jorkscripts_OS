package com.jork.utils.metrics.core;

import com.osmb.api.script.Script;
import com.osmb.api.visual.drawing.Canvas;
import com.jork.utils.ExceptionUtils;
import com.jork.utils.metrics.display.MetricsDisplay;
import com.jork.utils.metrics.display.MetricsPanelConfig;
import com.jork.utils.metrics.providers.RuntimeMetricProvider;
import com.jork.utils.metrics.providers.XPMetricProvider;
import com.osmb.api.ui.component.tabs.skill.SkillType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Core metrics tracking engine that manages all metrics for a script.
 * Thread-safe and performance-optimized with caching.
 */
public class MetricsTracker {
    private final Script script;
    private final Map<String, Metric> metrics;
    private final MetricsDisplay display;
    private final MetricsPanelConfig displayConfig;
    
    // Providers for built-in metrics
    private RuntimeMetricProvider runtimeProvider;
    private XPMetricProvider xpProvider;
    
    // Update control
    private long lastUpdateTime;
    private static final long UPDATE_INTERVAL = 500; // Update metrics every 500ms
    
    public MetricsTracker(Script script) {
        this(script, new MetricsPanelConfig());
    }
    
    public MetricsTracker(Script script, MetricsPanelConfig config) {
        this.script = script;
        this.metrics = new LinkedHashMap<>(); // Preserve insertion order
        this.displayConfig = config;
        this.display = new MetricsDisplay(config);
        this.lastUpdateTime = 0;
    }
    
    /**
     * Registers a new metric to track
     */
    public void register(String label, Supplier<Object> valueSupplier, MetricType type) {
        register(label, valueSupplier, type, null);
    }
    
    /**
     * Registers a new metric with custom format
     */
    public void register(String label, Supplier<Object> valueSupplier, MetricType type, String customFormat) {
        Metric metric = new Metric(label, valueSupplier, type, customFormat);
        metrics.put(label, metric);
    }
    
    /**
     * Registers a metric object directly
     */
    public void register(Metric metric) {
        metrics.put(metric.getLabel(), metric);
    }
    
    /**
     * Removes a metric from tracking
     */
    public void unregister(String label) {
        metrics.remove(label);
    }
    
    /**
     * Registers runtime tracking metrics (automatic)
     */
    public void registerRuntimeMetrics() {
        if (runtimeProvider == null) {
            runtimeProvider = new RuntimeMetricProvider();
            runtimeProvider.initialize();
            
            // Register runtime metric
            register("Runtime", 
                    () -> runtimeProvider.getFormattedRuntime(),
                    MetricType.TEXT);
        }
    }
    
    /**
     * Registers XP tracking for a specific skill using OSMB's native XP tracker.
     * @param skill The skill type to track
     */
    public void registerXPTracking(SkillType skill) {
        if (xpProvider == null) {
            xpProvider = new XPMetricProvider();
        }

        try {
            xpProvider.initialize(script, skill);
            
            // Register XP metrics - use NUMBER type to show total instead of rate
            register("XP Gained", 
                    () -> xpProvider.getXPGained(),
                    MetricType.NUMBER);
            
            register("XP/Hour",
                    () -> xpProvider.getXPPerHour(),
                    MetricType.NUMBER,
                    "%,d/hr");
            
            register("TTL",
                    () -> xpProvider.getTimeToLevel(),
                    MetricType.TEXT);
            
            register("Level Progress",
                    () -> xpProvider.getLevelProgress(),
                    MetricType.PERCENTAGE);
            
            register("Current Level",
                    () -> xpProvider.getCurrentLevel(),
                    MetricType.NUMBER);
                    
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            script.log("Failed to initialize XP tracking: " + e.getMessage());
        }
    }
    
    /**
     * Updates all metrics if the update interval has passed
     */
    private void updateMetrics() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL) {
            // Update providers first
            if (runtimeProvider != null) {
                runtimeProvider.update();
            }
            if (xpProvider != null) {
                xpProvider.update();
            }
            
            // Update all metrics
            for (Metric metric : metrics.values()) {
                metric.update();
            }
            
            lastUpdateTime = currentTime;
        }
    }
    
    /**
     * Renders the metrics display on the canvas
     */
    public void render(Canvas canvas) {
        if (canvas == null || !displayConfig.isEnabled()) {
            return;
        }
        
        // Update metrics before rendering
        updateMetrics();
        
        // Prepare display data
        List<MetricsDisplay.MetricDisplayData> displayData = new ArrayList<>();
        for (Metric metric : metrics.values()) {
            displayData.add(new MetricsDisplay.MetricDisplayData(
                metric.getLabel(),
                metric.getFormattedValue(),
                metric.getType()
            ));
        }
        
        // Render the display
        display.render(canvas, displayData);
    }
    
    /**
     * Gets a specific metric by label
     */
    public Metric getMetric(String label) {
        return metrics.get(label);
    }
    
    /**
     * Gets the current value of a metric
     */
    public Object getMetricValue(String label) {
        Metric metric = metrics.get(label);
        return metric != null ? metric.getValue() : null;
    }
    
    /**
     * Gets the formatted value of a metric
     */
    public String getMetricFormattedValue(String label) {
        Metric metric = metrics.get(label);
        return metric != null ? metric.getFormattedValue() : "N/A";
    }
    
    /**
     * Resets all metrics (useful for session resets)
     */
    public void resetAll() {
        for (Metric metric : metrics.values()) {
            metric.reset();
        }
        
        if (runtimeProvider != null) {
            runtimeProvider.reset();
        }
        if (xpProvider != null) {
            xpProvider.reset();
        }
    }
    
    /**
     * Gets the display configuration for customization
     */
    public MetricsPanelConfig getDisplayConfig() {
        return displayConfig;
    }
    
    /**
     * Gets all registered metric labels
     */
    public List<String> getMetricLabels() {
        return new ArrayList<>(metrics.keySet());
    }
    
    /**
     * Clears all metrics
     */
    public void clear() {
        metrics.clear();
        runtimeProvider = null;
        xpProvider = null;
    }
    
    /**
     * Gets the XP provider for direct access
     */
    public XPMetricProvider getXPProvider() {
        return xpProvider;
    }
    
    /**
     * Pauses the XP timer (for breaks/hops)
     */
    public void pauseXPTimer() {
        if (xpProvider != null) {
            xpProvider.pauseTimer();
        }
    }
    
    /**
     * Resumes the XP timer after pause
     */
    public void resumeXPTimer() {
        if (xpProvider != null) {
            xpProvider.resumeTimer();
        }
    }
    
    /**
     * Configures whether the XP timer should pause during logout
     * @param pauseDuringLogout Whether to pause during logout
     */
    public void configureXPTimerPause(boolean pauseDuringLogout) {
        if (xpProvider != null) {
            xpProvider.setPauseDuringLogout(pauseDuringLogout);
        }
    }
}
