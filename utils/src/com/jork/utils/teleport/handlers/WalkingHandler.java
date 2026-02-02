package com.jork.utils.teleport.handlers;

import com.jork.utils.ScriptLogger;
import com.jork.utils.teleport.TeleportDestination;
import com.jork.utils.teleport.TeleportHandler;
import com.jork.utils.teleport.TeleportResult;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.script.Script;

/**
 * A "teleport" handler that represents walking to a destination (no actual teleport).
 * Used for locations without item teleports (e.g., Port Phasmatys bank).
 *
 * <h2>Design Note:</h2>
 * This handler does NOT perform the walking itself - it returns {@link TeleportResult#NOT_APPLICABLE}
 * from {@link #teleport()} and expects the caller (e.g., BankTask) to handle navigation using
 * {@link TeleportDestination#getWalkTarget()}.
 *
 * This design allows scripts to use their own walking configuration and navigation preferences.
 *
 * <h2>Usage:</h2>
 * <pre>
 *     TeleportHandler handler = new WalkingHandler(script, "Port Phasmatys", destination);
 *
 *     if (!handler.requiresItem()) {
 *         // Handler is walking-based, use walker directly
 *         WorldPosition target = handler.getDestination().getWalkTarget();
 *         getWalker().walkTo(target);
 *     }
 * </pre>
 *
 * @author jork
 */
public class WalkingHandler implements TeleportHandler {

    private final Script script;
    private final TeleportDestination destination;
    private final String name;

    /**
     * Creates a walking-based handler for the specified destination.
     *
     * @param script The script instance for API access
     * @param name Display name for this handler
     * @param destination The destination to walk to
     */
    public WalkingHandler(Script script, String name, TeleportDestination destination) {
        this.script = script;
        this.name = name;
        this.destination = destination;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public TeleportDestination getDestination() {
        return destination;
    }

    @Override
    public boolean requiresItem() {
        return false;  // No teleport item needed
    }

    @Override
    public int getItemId() {
        return -1;  // No item
    }

    @Override
    public boolean canTeleport() {
        // Walking is always "possible" if not already at destination
        WorldPosition pos = script.getWorldPosition();
        return pos != null && !isAtDestination(pos);
    }

    @Override
    public TeleportResult teleport() {
        // Check if already at destination
        WorldPosition pos = script.getWorldPosition();
        if (pos != null && isAtDestination(pos)) {
            return TeleportResult.ALREADY_AT_DESTINATION;
        }

        // Return NOT_APPLICABLE - caller should use getDestination().getWalkTarget()
        // and handle walking themselves
        ScriptLogger.debug(script, "WalkingHandler: No teleport available. " +
            "Use getDestination().getWalkTarget() for navigation to " + destination.getName());
        return TeleportResult.NOT_APPLICABLE;
    }

    @Override
    public boolean isWearable() {
        return false;  // No item to wear
    }

    @Override
    public int getCharges() {
        return -1;  // Not applicable
    }
}
