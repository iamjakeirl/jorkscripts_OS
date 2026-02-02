package com.jork.utils.tilepicker;

import java.awt.Color;
import java.util.Objects;

/**
 * Represents a category for tile selection in the EnhancedTilePickerPanel.
 * Each category has an ID, customizable display name, color, and optional selection limit.
 */
public class TileCategory {
    
    private final String id;
    private final String displayName;
    private final Color color;
    private final int maxSelections;
    
    /**
     * Creates a new tile category with unlimited selections.
     * 
     * @param id Unique identifier for this category
     * @param displayName Human-readable name shown in UI
     * @param color Visual color for this category
     */
    public TileCategory(String id, String displayName, Color color) {
        this(id, displayName, color, -1);
    }
    
    /**
     * Creates a new tile category with selection limit.
     * 
     * @param id Unique identifier for this category
     * @param displayName Human-readable name shown in UI
     * @param color Visual color for this category
     * @param maxSelections Maximum tiles allowed in this category (-1 for unlimited)
     */
    public TileCategory(String id, String displayName, Color color, int maxSelections) {
        this.id = Objects.requireNonNull(id, "Category ID cannot be null");
        this.displayName = Objects.requireNonNull(displayName, "Display name cannot be null");
        this.color = Objects.requireNonNull(color, "Color cannot be null");
        this.maxSelections = maxSelections;
    }
    
    /**
     * Gets the unique identifier for this category.
     * @return The category ID
     */
    public String getId() {
        return id;
    }
    
    /**
     * Gets the display name shown in the UI.
     * @return The human-readable display name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Gets the visual color for this category.
     * @return The category color
     */
    public Color getColor() {
        return color;
    }
    
    /**
     * Gets the maximum number of selections allowed for this category.
     * @return Max selections (-1 for unlimited)
     */
    public int getMaxSelections() {
        return maxSelections;
    }
    
    /**
     * Checks if this category has a selection limit.
     * @return true if limited, false if unlimited
     */
    public boolean hasLimit() {
        return maxSelections > 0;
    }
    
    /**
     * Creates a copy of this category with a different display name.
     * @param newDisplayName The new display name
     * @return A new TileCategory instance with the updated name
     */
    public TileCategory withDisplayName(String newDisplayName) {
        return new TileCategory(id, newDisplayName, color, maxSelections);
    }
    
    /**
     * Creates a copy of this category with a different color.
     * @param newColor The new color
     * @return A new TileCategory instance with the updated color
     */
    public TileCategory withColor(Color newColor) {
        return new TileCategory(id, displayName, newColor, maxSelections);
    }
    
    /**
     * Creates a copy of this category with a different selection limit.
     * @param newMaxSelections The new maximum selections
     * @return A new TileCategory instance with the updated limit
     */
    public TileCategory withMaxSelections(int newMaxSelections) {
        return new TileCategory(id, displayName, color, newMaxSelections);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TileCategory that = (TileCategory) o;
        return id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return String.format("TileCategory{id='%s', displayName='%s', maxSelections=%d}", 
            id, displayName, maxSelections);
    }
}