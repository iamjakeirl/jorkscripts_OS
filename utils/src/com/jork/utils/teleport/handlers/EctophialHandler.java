package com.jork.utils.teleport.handlers;

import com.jork.utils.ExceptionUtils;
import com.jork.utils.ScriptLogger;
import com.jork.utils.teleport.AbstractTeleportHandler;
import com.jork.utils.teleport.TeleportDestination;
import com.jork.utils.teleport.TeleportResult;
import com.osmb.api.item.ItemGroupResult;
import com.osmb.api.item.ItemID;
import com.osmb.api.item.ItemSearchResult;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.script.Script;
import com.osmb.api.ui.WidgetManager;

import java.util.Set;

/**
 * Teleport handler for the Ectophial - teleports to the Ectofuntus.
 *
 * The Ectophial is a reward from the Ghosts Ahoy quest. When emptied,
 * it teleports the player to the Ectofuntus and becomes empty. The empty
 * ectophial can be refilled at the Pool of Slime.
 *
 * <h2>Item States:</h2>
 * <ul>
 *   <li>ECTOPHIAL (4251) - Full, can be used to teleport</li>
 *   <li>ECTOPHIAL_4252 (4252) - Empty, must be refilled at Pool of Slime</li>
 * </ul>
 *
 * @author jork
 */
public class EctophialHandler extends AbstractTeleportHandler {

    // ═══════════════════════════════════════════════════════════════════════════
    // Ectophial Item IDs
    // ═══════════════════════════════════════════════════════════════════════════

    /** Full ectophial - can be used to teleport */
    private static final int ECTOPHIAL_FULL = ItemID.ECTOPHIAL;

    /** Empty ectophial - needs to be refilled */
    private static final int ECTOPHIAL_EMPTY = ItemID.ECTOPHIAL_4252;

    /** Only the full ectophial can teleport */
    private static final Set<Integer> VALID_TELEPORT_IDS = Set.of(ECTOPHIAL_FULL);

    /** All ectophial variants (for checking if player has any) */
    private static final Set<Integer> ALL_ECTOPHIAL_IDS = Set.of(ECTOPHIAL_FULL, ECTOPHIAL_EMPTY);

    // ═══════════════════════════════════════════════════════════════════════════
    // Ectofuntus Destination Configuration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Ectofuntus area - covers the main Ectofuntus building and surroundings.
     * The ectophial teleports players to just outside the Ectofuntus.
     * RectangleArea(x, y, width, height, plane)
     */
    private static final RectangleArea ECTOFUNTUS_AREA = new RectangleArea(
        3653, 3514,  // Starting position (SW corner)
        16, 12,      // Width and height
        0            // Ground plane
    );

    /** Walk target position near the Ectofuntus altar */
    private static final WorldPosition ECTOFUNTUS_POS = new WorldPosition(3659, 3518, 0);

    /**
     * Pre-built destination for Ectofuntus.
     * NOTE: Uses area-only constructor (no region ID fallback) because region 14646
     * is shared with Port Phasmatys bank. Region-based fallback would cause false positives
     * when standing at Port Phasmatys, preventing ectophial teleport.
     */
    private static final TeleportDestination ECTOFUNTUS_DESTINATION = new TeleportDestination(
        "Ectofuntus",
        ECTOFUNTUS_AREA,
        ECTOFUNTUS_POS
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates an Ectophial handler for Ectofuntus teleportation.
     *
     * @param script The script instance for API access
     */
    public EctophialHandler(Script script) {
        super(script, "Ectophial (Ectofuntus)", ECTOPHIAL_FULL, ECTOFUNTUS_DESTINATION);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TeleportHandler Overrides
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public boolean isWearable() {
        return false;  // Ectophial cannot be equipped
    }

    @Override
    public int getCharges() {
        // Ectophial has 1 charge when full, 0 when empty
        ItemSearchResult item = findTeleportItemInInventory();
        if (item != null && item.getId() == ECTOPHIAL_FULL) {
            return 1;
        }
        return 0;
    }

    @Override
    public boolean canTeleport() {
        // Only can teleport if we have a FULL ectophial
        // Override to specifically check for full version only
        WorldPosition pos = script.getWorldPosition();
        if (pos != null && isAtDestination(pos)) {
            return false;  // Already at Ectofuntus
        }

        return findFullEctophialInInventory() != null;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AbstractTeleportHandler Implementation
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    protected Set<Integer> getAllValidItemIds() {
        return VALID_TELEPORT_IDS;
    }

    @Override
    protected TeleportResult executeTeleport() {
        // Find the full ectophial in inventory
        ItemSearchResult ectophial = findFullEctophialInInventory();
        if (ectophial == null) {
            ScriptLogger.warning(script, "Full Ectophial not found in inventory");
            return TeleportResult.ITEM_NOT_FOUND;
        }

        ScriptLogger.debug(script, "Found Ectophial (ID: " + ectophial.getId() + ") - emptying...");

        // Interact with ectophial using "Empty" action
        boolean interacted = ectophial.interact("Empty");

        if (!interacted) {
            ScriptLogger.warning(script, "Failed to interact with Ectophial");
            return TeleportResult.INTERACTION_FAILED;
        }

        // Verify arrival at Ectofuntus
        if (verifyArrival()) {
            ScriptLogger.debug(script, "Successfully teleported to Ectofuntus");
            return TeleportResult.SUCCESS;
        }

        return TeleportResult.ARRIVAL_TIMEOUT;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Finds the full ectophial in inventory.
     * Only returns the full version (4251), not the empty one (4252).
     *
     * @return The full Ectophial if found, null otherwise
     */
    private ItemSearchResult findFullEctophialInInventory() {
        try {
            WidgetManager wm = script.getWidgetManager();
            if (wm == null || wm.getInventory() == null) {
                return null;
            }

            ItemGroupResult search = wm.getInventory().search(Set.of(ECTOPHIAL_FULL));
            if (search == null) {
                return null;
            }

            return search.getItem(ECTOPHIAL_FULL);
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.debug(script, "Error finding Ectophial: " + e.getMessage());
            return null;
        }
    }

    /**
     * Checks if the player has any ectophial (full or empty).
     * Useful for determining if player needs to obtain one.
     *
     * @return true if player has any ectophial variant
     */
    public boolean hasAnyEctophial() {
        try {
            WidgetManager wm = script.getWidgetManager();
            if (wm == null || wm.getInventory() == null) {
                return false;
            }

            ItemGroupResult search = wm.getInventory().search(ALL_ECTOPHIAL_IDS);
            return search != null && search.getItem(ALL_ECTOPHIAL_IDS) != null;
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            return false;
        }
    }

    /**
     * Checks if the player has an empty ectophial that needs refilling.
     *
     * @return true if player has an empty ectophial
     */
    public boolean hasEmptyEctophial() {
        try {
            WidgetManager wm = script.getWidgetManager();
            if (wm == null || wm.getInventory() == null) {
                return false;
            }

            ItemGroupResult search = wm.getInventory().search(Set.of(ECTOPHIAL_EMPTY));
            return search != null && search.getItem(ECTOPHIAL_EMPTY) != null;
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            return false;
        }
    }
}
