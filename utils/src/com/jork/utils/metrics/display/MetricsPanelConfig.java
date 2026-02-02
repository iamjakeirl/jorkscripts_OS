package com.jork.utils.metrics.display;

import java.awt.Color;
import java.awt.Font;

/**
 * Configuration class for the metrics display panel.
 * Allows customization of position, colors, fonts, and layout.
 */
public class MetricsPanelConfig {
    
    public enum Position {
        TOP_LEFT(10, 10),
        TOP_RIGHT(-10, 10),
        BOTTOM_LEFT(10, -10),
        BOTTOM_RIGHT(-10, -10),
        CUSTOM(0, 0);
        
        private int x, y;
        
        Position(int x, int y) {
            this.x = x;
            this.y = y;
        }
        
        public int getX() { return x; }
        public int getY() { return y; }
    }
    
    // Position settings
    private Position position = Position.TOP_LEFT;
    private int customX = 10;
    private int customY = 10;
    
    // Display settings
    private boolean enabled = true;
    private boolean showBackground = true;
    private boolean showBorder = true;
    private boolean compactMode = false;
    
    // Colors
    private Color backgroundColor = new Color(30, 30, 30, 200); // Dark semi-transparent
    private Color borderColor = new Color(100, 100, 100, 255);
    private Color labelColor = new Color(200, 200, 200, 255);
    private Color valueColor = new Color(255, 255, 255, 255);
    private Color headerColor = new Color(255, 215, 0, 255); // Gold
    
    // Fonts
    private Font labelFont = new Font("Arial", Font.PLAIN, 11);
    private Font valueFont = new Font("Arial", Font.BOLD, 11);
    private Font headerFont = new Font("Arial", Font.BOLD, 12);
    
    // Layout
    private int padding = 8;
    private int lineSpacing = 2;
    private int minWidth = 150;
    private int maxWidth = 250;
    
    // Animation
    private boolean fadeIn = true;
    private long fadeInDuration = 500; // milliseconds
    
    // Image configuration
    private String backgroundImagePath = null;
    private String logoImagePath = null;
    private MetricsImageLoader.ScaleMode backgroundScaleMode = MetricsImageLoader.ScaleMode.STRETCH;
    private MetricsImageLoader.ScaleMode logoScaleMode = MetricsImageLoader.ScaleMode.FIT;
    private int logoHeight = 30;  // Target height for logo
    private boolean useImageBackground = false;
    private double backgroundImageOpacity = 0.8;
    private double logoOpacity = 1.0;
    
    // Text overlay for readability over images
    private boolean useTextOverlay = true;
    private Color textOverlayColor = new Color(0, 0, 0, 100); // 40% black
    
    // Getters and setters
    
    public Position getPosition() {
        return position;
    }
    
    public void setPosition(Position position) {
        this.position = position;
    }
    
    public void setCustomPosition(int x, int y) {
        this.position = Position.CUSTOM;
        this.customX = x;
        this.customY = y;
    }
    
    public int getX() {
        if (position == Position.CUSTOM) {
            return customX;
        }
        return position.getX();
    }
    
    public int getY() {
        if (position == Position.CUSTOM) {
            return customY;
        }
        return position.getY();
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isShowBackground() {
        return showBackground;
    }
    
    public void setShowBackground(boolean showBackground) {
        this.showBackground = showBackground;
    }
    
    public boolean isShowBorder() {
        return showBorder;
    }
    
    public void setShowBorder(boolean showBorder) {
        this.showBorder = showBorder;
    }
    
    public boolean isCompactMode() {
        return compactMode;
    }
    
    public void setCompactMode(boolean compactMode) {
        this.compactMode = compactMode;
        if (compactMode) {
            this.padding = 4;
            this.lineSpacing = 1;
            this.labelFont = new Font("Arial", Font.PLAIN, 10);
            this.valueFont = new Font("Arial", Font.BOLD, 10);
        }
    }
    
    public Color getBackgroundColor() {
        return backgroundColor;
    }
    
    public void setBackgroundColor(Color backgroundColor) {
        this.backgroundColor = backgroundColor;
    }
    
    public Color getBorderColor() {
        return borderColor;
    }
    
    public void setBorderColor(Color borderColor) {
        this.borderColor = borderColor;
    }
    
    public Color getLabelColor() {
        return labelColor;
    }
    
    public void setLabelColor(Color labelColor) {
        this.labelColor = labelColor;
    }
    
    public Color getValueColor() {
        return valueColor;
    }
    
    public void setValueColor(Color valueColor) {
        this.valueColor = valueColor;
    }
    
    public Color getHeaderColor() {
        return headerColor;
    }
    
    public void setHeaderColor(Color headerColor) {
        this.headerColor = headerColor;
    }
    
    public Font getLabelFont() {
        return labelFont;
    }
    
    public void setLabelFont(Font labelFont) {
        this.labelFont = labelFont;
    }
    
    public Font getValueFont() {
        return valueFont;
    }
    
    public void setValueFont(Font valueFont) {
        this.valueFont = valueFont;
    }
    
    public Font getHeaderFont() {
        return headerFont;
    }
    
    public void setHeaderFont(Font headerFont) {
        this.headerFont = headerFont;
    }
    
    public int getPadding() {
        return padding;
    }
    
    public void setPadding(int padding) {
        this.padding = padding;
    }
    
    public int getLineSpacing() {
        return lineSpacing;
    }
    
    public void setLineSpacing(int lineSpacing) {
        this.lineSpacing = lineSpacing;
    }
    
    public int getMinWidth() {
        return minWidth;
    }
    
    public void setMinWidth(int minWidth) {
        this.minWidth = minWidth;
    }
    
    public int getMaxWidth() {
        return maxWidth;
    }
    
    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
    }
    
    public boolean isFadeIn() {
        return fadeIn;
    }
    
    public void setFadeIn(boolean fadeIn) {
        this.fadeIn = fadeIn;
    }
    
    public long getFadeInDuration() {
        return fadeInDuration;
    }
    
    public void setFadeInDuration(long fadeInDuration) {
        this.fadeInDuration = fadeInDuration;
    }
    
    // Image-related getters and setters
    
    public String getBackgroundImagePath() {
        return backgroundImagePath;
    }
    
    public void setBackgroundImage(String path) {
        this.backgroundImagePath = path;
        this.useImageBackground = (path != null && !path.isEmpty());
    }
    
    public void setBackgroundImage(String path, MetricsImageLoader.ScaleMode scaleMode) {
        this.backgroundImagePath = path;
        this.backgroundScaleMode = scaleMode;
        this.useImageBackground = (path != null && !path.isEmpty());
    }
    
    public MetricsImageLoader.ScaleMode getBackgroundScaleMode() {
        return backgroundScaleMode;
    }
    
    public void setBackgroundScaleMode(MetricsImageLoader.ScaleMode mode) {
        this.backgroundScaleMode = mode;
    }
    
    public String getLogoImagePath() {
        return logoImagePath;
    }
    
    public void setLogoImage(String path) {
        this.logoImagePath = path;
    }
    
    public void setLogoImage(String path, int targetHeight) {
        this.logoImagePath = path;
        this.logoHeight = targetHeight;
    }
    
    public MetricsImageLoader.ScaleMode getLogoScaleMode() {
        return logoScaleMode;
    }
    
    public void setLogoScaleMode(MetricsImageLoader.ScaleMode mode) {
        this.logoScaleMode = mode;
    }
    
    public int getLogoHeight() {
        return logoHeight;
    }
    
    public void setLogoHeight(int height) {
        this.logoHeight = height;
    }
    
    public boolean isUsingImageBackground() {
        return useImageBackground && backgroundImagePath != null;
    }
    
    public void useColorBackground() {
        this.useImageBackground = false;
    }
    
    public double getBackgroundImageOpacity() {
        return backgroundImageOpacity;
    }
    
    public void setBackgroundImageOpacity(double opacity) {
        this.backgroundImageOpacity = Math.max(0.0, Math.min(1.0, opacity));
    }
    
    public double getLogoOpacity() {
        return logoOpacity;
    }
    
    public void setLogoOpacity(double opacity) {
        this.logoOpacity = Math.max(0.0, Math.min(1.0, opacity));
    }
    
    public boolean isUseTextOverlay() {
        return useTextOverlay;
    }
    
    public void setUseTextOverlay(boolean useOverlay) {
        this.useTextOverlay = useOverlay;
    }
    
    public Color getTextOverlayColor() {
        return textOverlayColor;
    }
    
    public void setTextOverlayColor(Color color) {
        this.textOverlayColor = color;
    }
    
    /**
     * Creates a preset configuration for minimal display
     */
    public static MetricsPanelConfig minimal() {
        MetricsPanelConfig config = new MetricsPanelConfig();
        config.setShowBackground(false);
        config.setShowBorder(false);
        config.setCompactMode(true);
        return config;
    }
    
    /**
     * Creates a preset configuration for dark theme
     */
    public static MetricsPanelConfig darkTheme() {
        MetricsPanelConfig config = new MetricsPanelConfig();
        config.setBackgroundColor(new Color(20, 20, 20, 220));
        config.setBorderColor(new Color(60, 60, 60, 255));
        config.setLabelColor(new Color(180, 180, 180, 255));
        config.setValueColor(new Color(255, 255, 255, 255));
        return config;
    }
    
    /**
     * Creates a preset configuration for light theme
     */
    public static MetricsPanelConfig lightTheme() {
        MetricsPanelConfig config = new MetricsPanelConfig();
        config.setBackgroundColor(new Color(245, 245, 245, 200));
        config.setBorderColor(new Color(200, 200, 200, 255));
        config.setLabelColor(new Color(60, 60, 60, 255));
        config.setValueColor(new Color(0, 0, 0, 255));
        config.setHeaderColor(new Color(0, 100, 200, 255));
        return config;
    }
}