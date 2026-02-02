package com.jork.utils.metrics.display;

import com.osmb.api.visual.drawing.Canvas;
import com.jork.utils.metrics.core.MetricType;
import com.jork.utils.ExceptionUtils;
import com.jork.utils.ScriptLogger;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Renders the metrics display panel on the game canvas.
 * Handles layout, styling, and drawing of all metrics.
 */
public class MetricsDisplay {
    private final MetricsPanelConfig config;
    private long creationTime;
    private int lastPanelWidth = 0;
    private int lastPanelHeight = 0;
    
    // Cached images
    private BufferedImage backgroundImage = null;
    private BufferedImage logoImage = null;
    private boolean imagesLoaded = false;
    private String lastBackgroundPath = null;
    private String lastLogoPath = null;
    
    public MetricsDisplay(MetricsPanelConfig config) {
        this.config = config;
        this.creationTime = System.currentTimeMillis();
    }
    
    /**
     * Data class for metric display information
     */
    public static class MetricDisplayData {
        public final String label;
        public final String value;
        public final MetricType type;
        
        public MetricDisplayData(String label, String value, MetricType type) {
            this.label = label;
            this.value = value;
            this.type = type;
        }
    }
    
    /**
     * Renders the metrics panel with all metrics
     */
    public void render(Canvas canvas, List<MetricDisplayData> metrics) {
        if (canvas == null || metrics == null || metrics.isEmpty()) {
            return;
        }
        
        // Ensure images are loaded if configured
        ensureImagesLoaded(config);
        
        // Calculate panel dimensions
        Dimension panelSize = calculatePanelSize(canvas, metrics);
        int panelWidth = panelSize.width;
        int panelHeight = panelSize.height;
        
        // Store for external access
        lastPanelWidth = panelWidth;
        lastPanelHeight = panelHeight;
        
        // Calculate panel position
        Point panelPos = calculatePanelPosition(canvas, panelWidth, panelHeight);
        int x = panelPos.x;
        int y = panelPos.y;
        
        // Apply fade-in effect if enabled
        float opacity = 1.0f;
        if (config.isFadeIn()) {
            long elapsed = System.currentTimeMillis() - creationTime;
            if (elapsed < config.getFadeInDuration()) {
                opacity = (float) elapsed / config.getFadeInDuration();
            }
        }
        
        // Draw background (image or solid)
        if (config.isShowBackground()) {
            if (config.isUsingImageBackground() && backgroundImage != null) {
                // Reload background if panel size changed significantly
                if (Math.abs(panelWidth - backgroundImage.getWidth()) > 10 || 
                    Math.abs(panelHeight - backgroundImage.getHeight()) > 10) {
                    backgroundImage = MetricsImageLoader.loadAndResize(
                        config.getBackgroundImagePath(),
                        panelWidth,
                        panelHeight,
                        config.getBackgroundScaleMode()
                    );
                }
                
                if (backgroundImage != null) {
                    drawBackgroundImage(canvas, backgroundImage, x, y, 
                                      config.getBackgroundImageOpacity() * opacity);
                    
                    // Add text overlay for readability
                    if (config.isUseTextOverlay()) {
                        Color overlayColor = config.getTextOverlayColor();
                        canvas.fillRect(x, y, panelWidth, panelHeight, 
                                      overlayColor.getRGB(), overlayColor.getAlpha() / 255.0 * opacity);
                    }
                }
            } else {
                drawBackground(canvas, x, y, panelWidth, panelHeight, opacity);
            }
        }
        
        // Draw border
        if (config.isShowBorder()) {
            drawBorder(canvas, x, y, panelWidth, panelHeight, opacity);
        }
        
        // Calculate metrics start position (after logo if present)
        int metricsStartY = y;
        
        // Draw logo at top if configured
        if (logoImage != null) {
            // Ensure logo fits within panel with 10px padding on each side
            int maxLogoWidth = panelWidth - 20; // 10px padding on each side
            
            // Check if logo needs to be resized to fit
            if (logoImage.getWidth() > maxLogoWidth) {
                // Recalculate logo size to fit within bounds
                float scale = (float) maxLogoWidth / logoImage.getWidth();
                int newHeight = Math.round(logoImage.getHeight() * scale);
                
                // Reload logo with constrained size
                logoImage = MetricsImageLoader.loadAndResize(
                    config.getLogoImagePath(),
                    maxLogoWidth,
                    newHeight,
                    config.getLogoScaleMode()
                );
                
                if (logoImage == null) {
                    metricsStartY = y + config.getPadding();
                } else {
                    int logoX = x + (panelWidth - logoImage.getWidth()) / 2;
                    int logoY = y + 10; // 10px from top
                    drawLogo(canvas, logoImage, logoX, logoY, config.getLogoOpacity() * opacity);
                    metricsStartY = logoY + logoImage.getHeight() + 10; // 10px gap after logo
                }
            } else {
                // Logo fits, center it normally
                int logoX = x + (panelWidth - logoImage.getWidth()) / 2;
                int logoY = y + 10; // 10px from top
                drawLogo(canvas, logoImage, logoX, logoY, config.getLogoOpacity() * opacity);
                metricsStartY = logoY + logoImage.getHeight() + 10; // 10px gap after logo
            }
        }
        
        // Draw metrics
        drawMetrics(canvas, metrics, x, metricsStartY, panelWidth, opacity);
    }
    
    /**
     * Calculates the required panel size based on metrics
     */
    private Dimension calculatePanelSize(Canvas canvas, List<MetricDisplayData> metrics) {
        FontMetrics labelMetrics = canvas.getFontMetrics(config.getLabelFont());
        FontMetrics valueMetrics = canvas.getFontMetrics(config.getValueFont());
        
        int maxWidth = config.getMinWidth();
        int totalHeight = config.getPadding() * 2;
        
        // Add logo height if configured
        if (config.getLogoImagePath() != null) {
            // Logo height + top padding (10px) + bottom gap (10px)
            totalHeight += config.getLogoHeight() + 20;
        }
        
        for (MetricDisplayData metric : metrics) {
            // Calculate width for this metric
            int labelWidth = labelMetrics.stringWidth(metric.label + ": ");
            int valueWidth = valueMetrics.stringWidth(metric.value);
            int metricWidth = labelWidth + valueWidth + config.getPadding() * 2;
            
            maxWidth = Math.max(maxWidth, metricWidth);
            
            // Add height for this metric
            int metricHeight = Math.max(labelMetrics.getHeight(), valueMetrics.getHeight());
            totalHeight += metricHeight + config.getLineSpacing();
        }
        
        // Cap at max width
        maxWidth = Math.min(maxWidth, config.getMaxWidth());
        
        return new Dimension(maxWidth, totalHeight);
    }
    
    /**
     * Calculates the panel position based on configuration
     */
    private Point calculatePanelPosition(Canvas canvas, int panelWidth, int panelHeight) {
        int x = config.getX();
        int y = config.getY();
        
        // Handle right-aligned positions
        if (config.getPosition() == MetricsPanelConfig.Position.TOP_RIGHT || 
            config.getPosition() == MetricsPanelConfig.Position.BOTTOM_RIGHT) {
            x = canvas.canvasWidth + x - panelWidth; // x is negative for right positions
        }
        
        // Handle bottom-aligned positions
        if (config.getPosition() == MetricsPanelConfig.Position.BOTTOM_LEFT ||
            config.getPosition() == MetricsPanelConfig.Position.BOTTOM_RIGHT) {
            y = canvas.canvasHeight + y - panelHeight; // y is negative for bottom positions
        }
        
        return new Point(x, y);
    }
    
    /**
     * Draws the panel background
     */
    private void drawBackground(Canvas canvas, int x, int y, int width, int height, float opacity) {
        Color bgColor = config.getBackgroundColor();
        int alpha = (int) (bgColor.getAlpha() * opacity);
        Color colorWithOpacity = new Color(
            bgColor.getRed(), 
            bgColor.getGreen(), 
            bgColor.getBlue(), 
            Math.min(255, alpha)
        );
        
        // Draw rectangle background (Canvas doesn't support rounded rectangles)
        canvas.fillRect(x, y, width, height, colorWithOpacity.getRGB(), opacity);
    }
    
    /**
     * Draws the panel border
     */
    private void drawBorder(Canvas canvas, int x, int y, int width, int height, float opacity) {
        Color borderColor = config.getBorderColor();
        int alpha = (int) (borderColor.getAlpha() * opacity);
        Color colorWithOpacity = new Color(
            borderColor.getRed(),
            borderColor.getGreen(),
            borderColor.getBlue(),
            Math.min(255, alpha)
        );
        
        canvas.drawRect(x, y, width, height, colorWithOpacity.getRGB(), opacity);
    }
    
    /**
     * Draws all metrics within the panel
     */
    private void drawMetrics(Canvas canvas, List<MetricDisplayData> metrics, 
                            int panelX, int panelY, int panelWidth, float opacity) {
        int x = panelX + config.getPadding();
        int y = panelY + config.getPadding();
        
        FontMetrics labelMetrics = canvas.getFontMetrics(config.getLabelFont());
        FontMetrics valueMetrics = canvas.getFontMetrics(config.getValueFont());
        int lineHeight = Math.max(labelMetrics.getHeight(), valueMetrics.getHeight());
        
        for (MetricDisplayData metric : metrics) {
            // Calculate colors with opacity
            Color labelColor = applyOpacity(config.getLabelColor(), opacity);
            Color valueColor = applyOpacity(getColorForType(metric.type), opacity);
            
            // Draw label
            String labelText = metric.label + ": ";
            int labelWidth = labelMetrics.stringWidth(labelText);
            y += labelMetrics.getAscent();
            canvas.drawText(labelText, x, y, labelColor.getRGB(), config.getLabelFont());
            
            // Draw value (right-aligned or next to label)
            if (config.isCompactMode()) {
                // Compact mode: value next to label
                canvas.drawText(metric.value, x + labelWidth, y, valueColor.getRGB(), config.getValueFont());
            } else {
                // Normal mode: value right-aligned
                int valueWidth = valueMetrics.stringWidth(metric.value);
                int valueX = panelX + panelWidth - config.getPadding() - valueWidth;
                canvas.drawText(metric.value, valueX, y, valueColor.getRGB(), config.getValueFont());
            }
            
            // Move to next line
            y += config.getLineSpacing() + (lineHeight - labelMetrics.getAscent());
        }
    }
    
    /**
     * Gets the appropriate color for a metric type
     */
    private Color getColorForType(MetricType type) {
        switch (type) {
            case XP:
                return new Color(255, 215, 0); // Gold for XP
            case PERCENTAGE:
                return new Color(0, 255, 127); // Spring green for percentages
            case RATE:
                return new Color(135, 206, 250); // Light sky blue for rates
            case TIME:
                return new Color(255, 182, 193); // Light pink for time
            default:
                return config.getValueColor();
        }
    }
    
    /**
     * Applies opacity to a color
     */
    private Color applyOpacity(Color color, float opacity) {
        int alpha = (int) (color.getAlpha() * opacity);
        return new Color(
            color.getRed(),
            color.getGreen(),
            color.getBlue(),
            Math.min(255, alpha)
        );
    }
    
    /**
     * Gets the last calculated panel width
     */
    public int getLastPanelWidth() {
        return lastPanelWidth;
    }
    
    /**
     * Gets the last calculated panel height
     */
    public int getLastPanelHeight() {
        return lastPanelHeight;
    }
    
    /**
     * Ensures images are loaded if paths have been configured
     */
    private void ensureImagesLoaded(MetricsPanelConfig config) {
        // Check if paths have changed
        boolean pathsChanged = false;
        if (config.getBackgroundImagePath() != null && !config.getBackgroundImagePath().equals(lastBackgroundPath)) {
            pathsChanged = true;
            lastBackgroundPath = config.getBackgroundImagePath();
        }
        if (config.getLogoImagePath() != null && !config.getLogoImagePath().equals(lastLogoPath)) {
            pathsChanged = true;
            lastLogoPath = config.getLogoImagePath();
        }
        
        if (imagesLoaded && !pathsChanged) {
            return;
        }
        
        // Load background image if configured
        if (config.getBackgroundImagePath() != null) {
            try {
                backgroundImage = MetricsImageLoader.loadAndResize(
                    config.getBackgroundImagePath(),
                    config.getMinWidth(),
                    300, // Estimated initial height
                    config.getBackgroundScaleMode()
                );
            } catch (Exception e) {
                ExceptionUtils.rethrowIfTaskInterrupted(e);
                ScriptLogger.warning(null, "Failed to load background image: " + e.getMessage());
                backgroundImage = null;
            }
        }
        
        // Load logo image if configured
        if (config.getLogoImagePath() != null) {
            System.out.println("[MetricsDisplay] Logo path configured: " + config.getLogoImagePath());
            try {
                logoImage = MetricsImageLoader.loadAndResize(
                    config.getLogoImagePath(),
                    -1, // Calculate width from aspect ratio
                    config.getLogoHeight(),
                    config.getLogoScaleMode()
                );
                if (logoImage != null) {
                    System.out.println("[MetricsDisplay] Logo loaded successfully: " + logoImage.getWidth() + "x" + logoImage.getHeight());
                }
            } catch (Exception e) {
                ExceptionUtils.rethrowIfTaskInterrupted(e);
                System.out.println("[MetricsDisplay] Failed to load logo: " + e.getMessage());
                e.printStackTrace();
                ScriptLogger.warning(null, "Failed to load logo image: " + e.getMessage());
                logoImage = null;
            }
        }
        
        imagesLoaded = true;
    }
    
    /**
     * Draws a background image on the canvas
     */
    private void drawBackgroundImage(Canvas canvas, BufferedImage image, int x, int y, double opacity) {
        if (image == null) {
            return;
        }
        
        int[] pixels = MetricsImageLoader.toPixelArray(image);
        if (pixels == null) {
            return;
        }
        
        // Apply opacity to pixels if needed
        if (opacity < 1.0) {
            for (int i = 0; i < pixels.length; i++) {
                int pixel = pixels[i];
                int alpha = (pixel >> 24) & 0xFF;
                alpha = (int) (alpha * opacity);
                pixels[i] = (alpha << 24) | (pixel & 0x00FFFFFF);
            }
        }
        
        // Draw the image pixels
        canvas.drawPixels(pixels, x, y, image.getWidth(), image.getHeight());
    }
    
    /**
     * Draws a logo image on the canvas
     */
    private void drawLogo(Canvas canvas, BufferedImage image, int x, int y, double opacity) {
        if (image == null) {
            return;
        }
        
        int[] pixels = MetricsImageLoader.toPixelArray(image);
        if (pixels == null) {
            return;
        }
        
        // Simple opacity application - no artifact filtering needed since we use black background
        if (opacity < 1.0) {
            for (int i = 0; i < pixels.length; i++) {
                int pixel = pixels[i];
                int alpha = (pixel >> 24) & 0xFF;
                alpha = (int) (alpha * opacity);
                pixels[i] = (alpha << 24) | (pixel & 0x00FFFFFF);
            }
        }
        
        // Draw the logo pixels
        canvas.drawPixels(pixels, x, y, image.getWidth(), image.getHeight());
    }
}
