package com.jork.utils.teleport;

import com.osmb.api.location.area.Area;
import com.osmb.api.location.position.types.WorldPosition;

/**
 * Represents a teleport destination with verification methods.
 * Supports both Area-based (more precise) and Region ID-based (fallback) verification.
 *
 * @author jork
 */
public class TeleportDestination {

    private final String name;
    private final Area destinationArea;           // Primary - area-based check
    private final int destinationRegionId;        // Fallback - region-based check
    private final WorldPosition walkTarget;       // Target position for walkers

    /**
     * Creates a destination with Area-based verification only.
     *
     * @param name Display name for logging
     * @param area The destination area
     * @param walkTarget Target position for navigation after teleport
     */
    public TeleportDestination(String name, Area area, WorldPosition walkTarget) {
        this.name = name;
        this.destinationArea = area;
        this.destinationRegionId = -1;
        this.walkTarget = walkTarget;
    }

    /**
     * Creates a destination with Region ID-based verification only.
     *
     * @param name Display name for logging
     * @param regionId The destination region ID
     * @param walkTarget Target position for navigation after teleport
     */
    public TeleportDestination(String name, int regionId, WorldPosition walkTarget) {
        this.name = name;
        this.destinationArea = null;
        this.destinationRegionId = regionId;
        this.walkTarget = walkTarget;
    }

    /**
     * Creates a destination with both Area and Region ID verification.
     * Area check is preferred, Region ID is fallback.
     *
     * @param name Display name for logging
     * @param area The destination area (primary check)
     * @param regionId The destination region ID (fallback check)
     * @param walkTarget Target position for navigation after teleport
     */
    public TeleportDestination(String name, Area area, int regionId, WorldPosition walkTarget) {
        this.name = name;
        this.destinationArea = area;
        this.destinationRegionId = regionId;
        this.walkTarget = walkTarget;
    }

    /**
     * Checks if the given position is at/near the destination.
     * Uses area check if available, falls back to region check.
     *
     * @param position The position to check
     * @return true if position is at the destination
     */
    public boolean isAtDestination(WorldPosition position) {
        if (position == null) {
            return false;
        }

        // Prefer area check if available
        if (destinationArea != null && destinationArea.contains(position)) {
            return true;
        }

        // Fallback to region check
        if (destinationRegionId > 0 && position.getRegionID() == destinationRegionId) {
            return true;
        }

        return false;
    }

    /**
     * Gets the display name of this destination.
     * @return The destination name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the destination area for precise position checking.
     * @return The area, or null if only region-based checking is used
     */
    public Area getArea() {
        return destinationArea;
    }

    /**
     * Gets the destination region ID for fallback checking.
     * @return The region ID, or -1 if only area-based checking is used
     */
    public int getRegionId() {
        return destinationRegionId;
    }

    /**
     * Gets the walk target position for navigation after teleport.
     * This is the position the script should walk to (e.g., bank booth location).
     * @return The walk target position
     */
    public WorldPosition getWalkTarget() {
        return walkTarget;
    }

    /**
     * Gets the region ID for the walk target position.
     * @return The walk target region ID, or -1 if no walk target is set
     */
    public int getWalkTargetRegionId() {
        if (walkTarget == null) {
            return -1;
        }

        return walkTarget.getRegionID();
    }

    /**
     * Checks if this destination has an area defined.
     * @return true if area-based verification is available
     */
    public boolean hasArea() {
        return destinationArea != null;
    }

    /**
     * Checks if this destination has a region ID defined.
     * @return true if region-based verification is available
     */
    public boolean hasRegionId() {
        return destinationRegionId > 0;
    }

    @Override
    public String toString() {
        return "TeleportDestination{" +
            "name='" + name + '\'' +
            ", hasArea=" + hasArea() +
            ", regionId=" + destinationRegionId +
            ", walkTarget=" + walkTarget +
            '}';
    }
}
