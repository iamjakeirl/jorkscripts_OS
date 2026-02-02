package com.jork.utils.teleport;

import com.osmb.api.location.position.types.WorldPosition;
import java.util.Collections;
import java.util.Set;

/**
 * Interface for teleportation handlers.
 * Implementations provide specific teleport methods (items, spells, walking).
 *
 * <h2>Usage:</h2>
 * <pre>
 *     TeleportHandler handler = TeleportHandlerFactory.createRingOfDuelingHandler(script);
 *
 *     if (handler.canTeleport()) {
 *         TeleportResult result = handler.teleport();
 *         if (result.isSuccess()) {
 *             // Navigate to specific location within destination
 *             WorldPosition target = handler.getDestination().getWalkTarget();
 *             getWalker().walkTo(target);
 *         }
 *     }
 * </pre>
 *
 * @author jork
 */
public interface TeleportHandler {

    /**
     * Gets the display name of this teleport method.
     * @return The handler name (e.g., "Ring of Dueling (Castle Wars)")
     */
    String getName();

    /**
     * Gets the destination this handler teleports to.
     * @return The destination with area/region verification and walk target
     */
    TeleportDestination getDestination();

    /**
     * Checks if this handler uses an item (vs walking/spells).
     * @return true if teleportation requires an inventory item
     */
    boolean requiresItem();

    /**
     * Gets the item ID required for this teleport.
     * @return The item ID, or -1 if no item required
     */
    int getItemId();

    /**
     * Checks if the handler can currently execute.
     * Verifies item availability, charges, position, etc.
     *
     * @return true if teleport can be attempted
     */
    boolean canTeleport();

    /**
     * Checks if player is already at the destination.
     *
     * @param currentPosition Player's current position
     * @return true if already at destination
     */
    default boolean isAtDestination(WorldPosition currentPosition) {
        return getDestination().isAtDestination(currentPosition);
    }

    /**
     * Executes the teleport operation.
     * Handles item interaction, menu selection, and arrival verification.
     * Includes built-in retry logic.
     *
     * @return TeleportResult indicating outcome
     */
    TeleportResult teleport();

    /**
     * Gets the current charge count for chargeable items.
     *
     * @return Number of charges remaining, or -1 if not applicable/unknown
     */
    default int getCharges() {
        return -1;
    }

    /**
     * Checks if the teleport item is wearable (allows extra inventory space).
     * @return true if the teleport item can be equipped
     */
    default boolean isWearable() {
        return false;
    }

    /**
     * Checks if this handler uses a spell (vs items or walking).
     * Spell-based handlers require casting from spellbook, not item interaction.
     * Used by BankTask to distinguish spell teleports from walking handlers.
     *
     * @return true if teleportation uses a spell from the spellbook
     */
    default boolean isSpellBased() {
        return false;
    }

    /**
     * Gets item IDs required to use this teleport (e.g., runes for spell teleports).
     * Used by banking logic to avoid depositing required items.
     *
     * @return set of required item IDs, empty if none
     */
    default Set<Integer> getRequiredItemIds() {
        return Collections.emptySet();
    }
}
