package com.jork.utils.teleport;

import com.jork.utils.ExceptionUtils;
import com.jork.utils.ScriptLogger;
import com.osmb.api.item.ItemGroupResult;
import com.osmb.api.item.ItemSearchResult;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.script.Script;
import com.osmb.api.ui.WidgetManager;
import com.osmb.api.ui.tabs.Equipment;
import com.osmb.api.utils.RandomUtils;
import com.osmb.api.utils.UIResult;

import java.util.Set;

/**
 * Base class for item-based teleport handlers with common retry and verification logic.
 * Follows patterns from JorkBank utility for timeout randomization and error handling.
 *
 * <h2>Subclass Implementation:</h2>
 * <pre>
 * public class MyTeleportHandler extends AbstractTeleportHandler {
 *     public MyTeleportHandler(Script script) {
 *         super(script, "My Teleport", ITEM_ID, DESTINATION);
 *     }
 *
 *     &#64;Override
 *     protected TeleportResult executeTeleport() {
 *         // Implement item-specific interaction
 *     }
 *
 *     &#64;Override
 *     protected Collection&lt;Integer&gt; getAllValidItemIds() {
 *         return Set.of(ITEM_ID);
 *     }
 * }
 * </pre>
 *
 * @author jork
 */
public abstract class AbstractTeleportHandler implements TeleportHandler {

    // ═══════════════════════════════════════════════════════════════════════════
    // Configuration Constants
    // ═══════════════════════════════════════════════════════════════════════════

    /** Maximum retry attempts before giving up */
    protected static final int MAX_TELEPORT_RETRIES = 3;

    // ═══════════════════════════════════════════════════════════════════════════
    // Instance Fields
    // ═══════════════════════════════════════════════════════════════════════════

    protected final Script script;
    protected final TeleportDestination destination;
    protected final int primaryItemId;
    protected final String name;

    protected int currentRetryCount = 0;

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a new teleport handler.
     *
     * @param script The script instance for API access
     * @param name Display name for this teleport method
     * @param primaryItemId The primary item ID (used for getItemId())
     * @param destination The teleport destination
     */
    protected AbstractTeleportHandler(Script script, String name,
                                       int primaryItemId, TeleportDestination destination) {
        this.script = script;
        this.name = name;
        this.primaryItemId = primaryItemId;
        this.destination = destination;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TeleportHandler Interface Implementation
    // ═══════════════════════════════════════════════════════════════════════════

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
        return primaryItemId > 0;
    }

    @Override
    public int getItemId() {
        return primaryItemId;
    }

    @Override
    public boolean canTeleport() {
        // Check if already at destination
        WorldPosition pos = script.getWorldPosition();
        if (pos != null && isAtDestination(pos)) {
            return false;  // No need to teleport
        }

        // Check item availability (inventory or equipped)
        if (requiresItem()) {
            ItemSearchResult invItem = findTeleportItemInInventory();
            if (invItem != null) {
                return true;
            }

            // Check equipment if wearable
            if (isWearable()) {
                ItemSearchResult equipItem = findTeleportItemInEquipment();
                return equipItem != null;
            }

            return false;
        }

        return true;
    }

    /**
     * Executes the teleport with retry logic.
     * Delegates to executeTeleport() which calls verifyArrival() for arrival confirmation.
     */
    @Override
    public TeleportResult teleport() {
        // Pre-checks
        WorldPosition startPos = script.getWorldPosition();
        if (startPos == null) {
            ScriptLogger.warning(script, "Cannot get current position");
            return TeleportResult.API_UNAVAILABLE;
        }

        // Check if already at destination
        if (isAtDestination(startPos)) {
            ScriptLogger.debug(script, "Already at " + destination.getName());
            return TeleportResult.ALREADY_AT_DESTINATION;
        }

        // Retry loop
        currentRetryCount = 0;
        while (currentRetryCount < MAX_TELEPORT_RETRIES) {
            currentRetryCount++;
            ScriptLogger.actionAttempt(script, "Teleport attempt " + currentRetryCount +
                "/" + MAX_TELEPORT_RETRIES + " via " + name);

            TeleportResult result = executeTeleport();

            if (result.isSuccess()) {
                ScriptLogger.actionSuccess(script, "Teleport successful to " + destination.getName());
                return result;
            }

            // Handle fatal vs retriable failures
            if (result.isFatal()) {
                ScriptLogger.error(script, "Fatal teleport failure: " + result);
                return result;
            }

            ScriptLogger.warning(script, "Teleport attempt failed: " + result + " - retrying...");
        }

        ScriptLogger.error(script, "Teleport failed after " + MAX_TELEPORT_RETRIES + " attempts");
        return TeleportResult.UNKNOWN_FAILURE;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Abstract Methods - Implemented by Subclasses
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Execute the actual teleport interaction.
     * Subclasses implement item-specific logic (simple click vs menu selection).
     *
     * @return TeleportResult indicating the outcome of this single attempt
     */
    protected abstract TeleportResult executeTeleport();

    /**
     * Returns all valid item IDs for this teleport type.
     * For chargeable items, this includes all charge variants.
     * Example: Ring of Dueling (8) through Ring of Dueling (1)
     *
     * @return Set of valid item IDs
     */
    protected abstract Set<Integer> getAllValidItemIds();

    // ═══════════════════════════════════════════════════════════════════════════
    // Protected Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Finds the teleport item in inventory OR equipment.
     * Checks inventory first, then equipment if wearable.
     *
     * @return The ItemSearchResult if found, null otherwise
     */
    protected ItemSearchResult findTeleportItem() {
        // Check inventory first
        ItemSearchResult invItem = findTeleportItemInInventory();
        if (invItem != null) {
            return invItem;
        }

        // Check equipment if item is wearable
        if (isWearable()) {
            return findTeleportItemInEquipment();
        }

        return null;
    }

    /**
     * Finds the teleport item in inventory only.
     *
     * @return The ItemSearchResult if found in inventory, null otherwise
     */
    protected ItemSearchResult findTeleportItemInInventory() {
        try {
            WidgetManager wm = script.getWidgetManager();
            if (wm == null || wm.getInventory() == null) {
                return null;
            }

            ItemGroupResult search = wm.getInventory().search(getAllValidItemIds());
            if (search == null) {
                return null;
            }

            return search.getItem(getAllValidItemIds());
        } catch (Exception e) {
            ScriptLogger.debug(script, "Error finding teleport item in inventory: " + e.getMessage());
            return null;
        }
    }

    /**
     * Finds the teleport item in equipment.
     *
     * @return The ItemSearchResult if equipped, null otherwise
     */
    protected ItemSearchResult findTeleportItemInEquipment() {
        try {
            WidgetManager wm = script.getWidgetManager();
            if (wm == null) {
                return null;
            }

            Equipment equipment = wm.getEquipment();
            if (equipment == null) {
                return null;
            }

            // Convert Set to array for varargs
            int[] itemIds = getAllValidItemIds().stream().mapToInt(Integer::intValue).toArray();

            UIResult<ItemSearchResult> result = equipment.findItem(itemIds);
            if (result != null && result.isFound()) {
                return result.get();
            }

            return null;
        } catch (Exception e) {
            ScriptLogger.debug(script, "Error finding teleport item in equipment: " + e.getMessage());
            return null;
        }
    }

    /**
     * Checks if the teleport item is currently equipped.
     *
     * @return true if item is equipped, false otherwise
     */
    protected boolean isItemEquipped() {
        try {
            WidgetManager wm = script.getWidgetManager();
            if (wm == null) {
                return false;
            }

            Equipment equipment = wm.getEquipment();
            if (equipment == null) {
                return false;
            }

            int[] itemIds = getAllValidItemIds().stream().mapToInt(Integer::intValue).toArray();

            UIResult<Boolean> result = equipment.isEquipped(itemIds);
            return result != null && result.isFound() && Boolean.TRUE.equals(result.get());
        } catch (Exception e) {
            ScriptLogger.debug(script, "Error checking if teleport item equipped: " + e.getMessage());
            return false;
        }
    }

    /**
     * Verifies arrival at destination with polling.
     * Uses pollFramesHuman so a human reaction delay is added after arrival is detected.
     * On timeout, the wait duration itself serves as the delay before retry.
     *
     * @return true if arrived at destination within timeout
     */
    protected boolean verifyArrival() {
        try {
            return script.pollFramesHuman(() -> {
                WorldPosition pos = script.getWorldPosition();
                return pos != null && isAtDestination(pos);
            }, RandomUtils.uniformRandom(4000, 7000));
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            return false;
        }
    }

    /**
     * Gets the Script instance for subclasses that need it.
     *
     * @return The Script instance
     */
    protected Script getScript() {
        return script;
    }

}
