package com.jork.utils.teleport.handlers;

import com.jork.utils.ScriptLogger;
import com.jork.utils.teleport.AbstractTeleportHandler;
import com.jork.utils.teleport.TeleportDestination;
import com.jork.utils.teleport.TeleportResult;
import com.osmb.api.item.ItemSearchResult;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.script.Script;
import com.osmb.api.ui.tabs.Equipment;

import java.util.Set;

/**
 * Teleport handler for Amulet of the Eye to Temple of the Eye.
 * Simple single-action teleport with unlimited charges and multiple color variants.
 *
 * <h2>Item Details:</h2>
 * <ul>
 *   <li>Item IDs:
 *     <ul>
 *       <li>26914 - Base amulet (regular/quest reward)</li>
 *       <li>26990 - Red variant (recolored with red abyssal dye)</li>
 *       <li>26992 - Green variant (recolored with green abyssal dye)</li>
 *       <li>26994 - Blue variant (recolored with blue abyssal dye)</li>
 *     </ul>
 *   </li>
 *   <li>Charges: Unlimited teleportation</li>
 *   <li>Slot: Neck (wearable)</li>
 * </ul>
 *
 * <h2>Quest Requirements:</h2>
 * "Temple of the Eye" quest required to obtain and use the amulet.
 *
 * <h2>Destination:</h2>
 * Temple of the Eye - Guardians of the Rift minigame entrance area.
 * Used for Runecraft training via the Guardians of the Rift minigame.
 *
 * <h2>Special Features:</h2>
 * <ul>
 *   <li>Color variants are cosmetic only (no functional difference)</li>
 *   <li>Cannot be lost on death (kept on death always)</li>
 *   <li>Cannot be alchemized or traded</li>
 *   <li>Lost amulets can be reclaimed from the Lumbridge Guide</li>
 * </ul>
 *
 * @author jork
 */
public class AmuletOfTheEyeHandler extends AbstractTeleportHandler {

    // ═══════════════════════════════════════════════════════════════════════════
    // Amulet of the Eye Item IDs
    // ═══════════════════════════════════════════════════════════════════════════

    /** Amulet of the Eye - base version (quest reward) */
    private static final int AMULET_BASE = 26914;

    /** Amulet of the Eye (red) - recolored with red abyssal dye */
    private static final int AMULET_RED = 26990;

    /** Amulet of the Eye (green) - recolored with green abyssal dye */
    private static final int AMULET_GREEN = 26992;

    /** Amulet of the Eye (blue) - recolored with blue abyssal dye */
    private static final int AMULET_BLUE = 26994;

    /** All valid Amulet of the Eye item IDs (base + 3 color variants) */
    private static final Set<Integer> ALL_AMULET_IDS = Set.of(
        AMULET_BASE, AMULET_RED, AMULET_GREEN, AMULET_BLUE
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Temple of the Eye Destination Configuration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Temple of the Eye area (verified).
     * RectangleArea(x, y, width, height, plane)
     * Note: y coordinate in 9000+ range indicates underground area.
     */
    private static final RectangleArea TEMPLE_AREA = new RectangleArea(
        3610, 9455,  // Starting position (SW corner of arrival area)
        10, 10,      // Width and height
        0            // Ground plane (underground areas use plane 0)
    );

    /**
     * Temple of the Eye region ID (verified).
     * Calculated from coordinates: regionX = 3615 >> 6 = 56, regionY = 9460 >> 6 = 147
     * Region ID = (56 << 8) | 147 = 14483
     */
    private static final int TEMPLE_REGION = 14483;

    /** Walk target position in Temple of the Eye (verified) */
    private static final WorldPosition TEMPLE_POS = new WorldPosition(3618, 9473, 0);

    /** Pre-built destination for Temple of the Eye */
    private static final TeleportDestination TEMPLE_DESTINATION = new TeleportDestination(
        "Temple of the Eye",
        TEMPLE_AREA,
        TEMPLE_REGION,
        TEMPLE_POS
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates an Amulet of the Eye handler for Temple of the Eye teleportation.
     *
     * @param script The script instance for API access
     */
    public AmuletOfTheEyeHandler(Script script) {
        super(script, "Amulet of the Eye", AMULET_BASE, TEMPLE_DESTINATION);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TeleportHandler Overrides
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public boolean isWearable() {
        return true;  // Amulet can be equipped in neck slot
    }

    @Override
    public int getCharges() {
        // Amulet of the Eye has unlimited charges
        return Integer.MAX_VALUE;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AbstractTeleportHandler Implementation
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    protected Set<Integer> getAllValidItemIds() {
        return ALL_AMULET_IDS;
    }

    @Override
    protected TeleportResult executeTeleport() {
        // Find amulet (any color variant, in inventory or equipped)
        ItemSearchResult amulet = findTeleportItem();
        if (amulet == null) {
            ScriptLogger.warning(script, "Amulet of the Eye not found");
            return TeleportResult.ITEM_NOT_FOUND;
        }

        // Determine if amulet is equipped or in inventory
        boolean equipped = isItemEquipped();
        ScriptLogger.debug(script, "Amulet of the Eye (ID: " + amulet.getId() + ") found " +
            (equipped ? "equipped" : "in inventory"));

        boolean interacted;

        if (equipped) {
            // Amulet is equipped - use Equipment.interact()
            interacted = interactWithEquippedAmulet();
        } else {
            // Amulet is in inventory - simple "Teleport" interaction
            interacted = interactWithInventoryAmulet(amulet);
        }

        if (!interacted) {
            ScriptLogger.warning(script, "Failed to interact with Amulet of the Eye");
            return TeleportResult.INTERACTION_FAILED;
        }

        // Wait for teleport animation
        waitForTeleportAnimation();

        // Verify arrival at Temple of the Eye
        if (verifyArrival()) {
            return TeleportResult.SUCCESS;
        }

        return TeleportResult.ARRIVAL_TIMEOUT;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Private Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Interacts with equipped Amulet of the Eye to teleport.
     * Uses simple "Teleport" action since there's only one destination.
     *
     * @return true if interaction was initiated successfully
     */
    private boolean interactWithEquippedAmulet() {
        try {
            Equipment equipment = script.getWidgetManager().getEquipment();
            if (equipment == null) {
                ScriptLogger.warning(script, "Equipment interface not available");
                return false;
            }

            // Find which amulet variant is currently equipped
            ItemSearchResult equippedAmulet = findTeleportItemInEquipment();
            if (equippedAmulet == null) {
                ScriptLogger.warning(script, "Could not find equipped amulet");
                return false;
            }

            int amuletId = equippedAmulet.getId();
            ScriptLogger.debug(script, "Interacting with equipped amulet (ID: " + amuletId + ")");

            // Use Equipment.interact with the "Teleport" action
            return equipment.interact(amuletId, "Teleport");
        } catch (Exception e) {
            ScriptLogger.debug(script, "Error interacting with equipped amulet: " + e.getMessage());
            return false;
        }
    }

    /**
     * Interacts with Amulet of the Eye in inventory to teleport.
     * Simple "Teleport" action - no menu selection required.
     *
     * @param amulet The amulet ItemSearchResult from inventory
     * @return true if interaction was initiated successfully
     */
    private boolean interactWithInventoryAmulet(ItemSearchResult amulet) {
        try {
            ScriptLogger.debug(script, "Interacting with inventory amulet (ID: " + amulet.getId() + ")");

            // Simple "Teleport" interaction - single destination, no menu
            return amulet.interact("Teleport");
        } catch (Exception e) {
            ScriptLogger.debug(script, "Error interacting with inventory amulet: " + e.getMessage());
            return false;
        }
    }
}
