package com.jork.utils.tilepicker;

import com.osmb.api.location.position.types.WorldPosition;
import java.util.Objects;

/**
 * Represents a selected tile with its associated category.
 * Used by EnhancedTilePickerPanel to return categorized selections.
 */
public class TileSelection {
    
    private final WorldPosition position;
    private final String categoryId;
    
    /**
     * Creates a new tile selection.
     * 
     * @param position The world position of the selected tile
     * @param categoryId The ID of the category this tile belongs to
     */
    public TileSelection(WorldPosition position, String categoryId) {
        this.position = Objects.requireNonNull(position, "Position cannot be null");
        this.categoryId = Objects.requireNonNull(categoryId, "Category ID cannot be null");
    }
    
    /**
     * Gets the world position of this selection.
     * @return The tile's world position
     */
    public WorldPosition getPosition() {
        return position;
    }
    
    /**
     * Gets the category ID for this selection.
     * @return The category identifier
     */
    public String getCategoryId() {
        return categoryId;
    }
    
    /**
     * Gets the X coordinate of the position.
     * @return X coordinate
     */
    public int getX() {
        return position.getX();
    }
    
    /**
     * Gets the Y coordinate of the position.
     * @return Y coordinate
     */
    public int getY() {
        return position.getY();
    }
    
    /**
     * Gets the plane/floor level of the position.
     * @return Plane level
     */
    public int getPlane() {
        return position.getPlane();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TileSelection that = (TileSelection) o;
        return position.equals(that.position) && categoryId.equals(that.categoryId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(position, categoryId);
    }
    
    @Override
    public String toString() {
        return String.format("TileSelection{position=[%d,%d,%d], category='%s'}", 
            position.getX(), position.getY(), position.getPlane(), categoryId);
    }
}