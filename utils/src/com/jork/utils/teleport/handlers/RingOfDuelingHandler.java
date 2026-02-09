package com.jork.utils.teleport.handlers;

import com.jork.utils.ScriptLogger;
import com.jork.utils.teleport.AbstractTeleportHandler;
import com.jork.utils.teleport.TeleportDestination;
import com.jork.utils.teleport.TeleportResult;
import com.osmb.api.input.MenuEntry;
import com.osmb.api.input.MenuHook;
import com.osmb.api.item.ItemSearchResult;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.script.Script;
import com.osmb.api.ui.tabs.Equipment;

import java.util.List;
import java.util.Set;

/**
 * Teleport handler for Ring of Dueling to Castle Wars bank.
 * Handles multi-option menu selection using MenuHook to select "Castle Wars" destination.
 *
 * <h2>Ring Charges:</h2>
 * Ring of Dueling has 8 charges. Each teleport uses 1 charge.
 * Item IDs range from 2552 (8 charges) to 2566 (1 charge).
 *
 * @author jork
 */
public class RingOfDuelingHandler extends AbstractTeleportHandler {

    // ═══════════════════════════════════════════════════════════════════════════
    // Ring of Dueling Item IDs (charges 8 to 1)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Ring of dueling(8) - full charges */
    private static final int RING_8 = 2552;
    /** Ring of dueling(7) */
    private static final int RING_7 = 2554;
    /** Ring of dueling(6) */
    private static final int RING_6 = 2556;
    /** Ring of dueling(5) */
    private static final int RING_5 = 2558;
    /** Ring of dueling(4) */
    private static final int RING_4 = 2560;
    /** Ring of dueling(3) */
    private static final int RING_3 = 2562;
    /** Ring of dueling(2) */
    private static final int RING_2 = 2564;
    /** Ring of dueling(1) - last charge */
    private static final int RING_1 = 2566;

    /** All valid Ring of Dueling item IDs */
    private static final Set<Integer> ALL_RING_IDS = Set.of(
        RING_8, RING_7, RING_6, RING_5, RING_4, RING_3, RING_2, RING_1
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Castle Wars Destination Configuration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Castle Wars bank area.
     * RectangleArea(x, y, width, height, plane)
     * Covers the bank chest area at Castle Wars.
     */
    private static final RectangleArea CASTLE_WARS_AREA = new RectangleArea(
        2438, 3083,  // Starting position (SW corner)
        8, 14,       // Width and height
        0            // Ground plane
    );

    /** Castle Wars region ID for fallback verification */
    private static final int CASTLE_WARS_REGION = 9776;

    /** Walk target position near bank chest */
    private static final WorldPosition CASTLE_WARS_BANK_POS = new WorldPosition(2443, 3083, 0);

    /** Pre-built destination for Castle Wars */
    private static final TeleportDestination CASTLE_WARS_DESTINATION = new TeleportDestination(
        "Castle Wars Bank",
        CASTLE_WARS_AREA,
        CASTLE_WARS_REGION,
        CASTLE_WARS_BANK_POS
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a Ring of Dueling handler for Castle Wars teleportation.
     *
     * @param script The script instance for API access
     */
    public RingOfDuelingHandler(Script script) {
        super(script, "Ring of Dueling (Castle Wars)", RING_8, CASTLE_WARS_DESTINATION);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TeleportHandler Overrides
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public boolean isWearable() {
        return true;  // Ring can be worn for +1 inventory space
    }

    @Override
    public int getCharges() {
        ItemSearchResult item = findTeleportItem();
        if (item == null) {
            return 0;
        }

        int itemId = item.getId();

        // Calculate charges from item ID
        // RING_8 (2552) = 8 charges, RING_1 (2566) = 1 charge
        // Pattern: IDs increase by 2 as charges decrease by 1
        if (itemId == RING_8) return 8;
        if (itemId == RING_7) return 7;
        if (itemId == RING_6) return 6;
        if (itemId == RING_5) return 5;
        if (itemId == RING_4) return 4;
        if (itemId == RING_3) return 3;
        if (itemId == RING_2) return 2;
        if (itemId == RING_1) return 1;

        return 0;  // Unknown variant
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AbstractTeleportHandler Implementation
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    protected Set<Integer> getAllValidItemIds() {
        return ALL_RING_IDS;
    }

    @Override
    protected TeleportResult executeTeleport() {
        // Check charges first
        int charges = getCharges();
        if (charges <= 0) {
            ScriptLogger.warning(script, "Ring of Dueling has no charges");
            return TeleportResult.NO_CHARGES;
        }

        // Determine if ring is equipped or in inventory
        boolean equipped = isItemEquipped();
        ScriptLogger.debug(script, "Ring of Dueling found " +
            (equipped ? "equipped" : "in inventory") + " with " + charges + " charge(s)");

        boolean interacted;

        if (equipped) {
            // Ring is equipped - use Equipment.interact()
            interacted = interactWithEquippedRing();
        } else {
            // Ring is in inventory - use ItemSearchResult.interact(MenuHook)
            interacted = interactWithInventoryRing();
        }

        if (!interacted) {
            ScriptLogger.warning(script, "Failed to interact with Ring of Dueling");
            return TeleportResult.INTERACTION_FAILED;
        }

        // Verify arrival at Castle Wars
        if (verifyArrival()) {
            return TeleportResult.SUCCESS;
        }

        return TeleportResult.ARRIVAL_TIMEOUT;
    }

    /**
     * Interacts with equipped Ring of Dueling to teleport to Castle Wars.
     *
     * @return true if interaction was initiated successfully
     */
    private boolean interactWithEquippedRing() {
        try {
            Equipment equipment = script.getWidgetManager().getEquipment();
            if (equipment == null) {
                ScriptLogger.warning(script, "Equipment interface not available");
                return false;
            }

            // Find which ring ID is currently equipped
            ItemSearchResult equippedRing = findTeleportItemInEquipment();
            if (equippedRing == null) {
                ScriptLogger.warning(script, "Could not find equipped ring");
                return false;
            }

            int ringId = equippedRing.getId();
            ScriptLogger.debug(script, "Interacting with equipped ring (ID: " + ringId + ")");

            // Use Equipment.interact with the Castle Wars menu option
            return equipment.interact(ringId, "Castle Wars");
        } catch (Exception e) {
            ScriptLogger.debug(script, "Error interacting with equipped ring: " + e.getMessage());
            return false;
        }
    }

    /**
     * Interacts with Ring of Dueling in inventory using MenuHook for Castle Wars selection.
     *
     * @return true if interaction was initiated successfully
     */
    private boolean interactWithInventoryRing() {
        ItemSearchResult ring = findTeleportItemInInventory();
        if (ring == null) {
            ScriptLogger.warning(script, "Ring of Dueling not found in inventory");
            return false;
        }

        // Create MenuHook to select "Castle Wars" option
        MenuHook castleWarsSelector = createCastleWarsMenuHook();

        // Interact with ring using the menu hook
        return ring.interact(castleWarsSelector);
    }

    /**
     * Creates a MenuHook that selects the "Castle Wars" teleport option.
     *
     * The Ring of Dueling menu typically shows options like:
     * - Rub (default/first option)
     * - Castle Wars
     * - Ferox Enclave
     * - Clan Wars / PvP Arena
     *
     * We search for any entry containing "Castle Wars" in the raw text.
     *
     * @return MenuHook that selects Castle Wars option
     */
    private MenuHook createCastleWarsMenuHook() {
        return (List<MenuEntry> menuEntries) -> {
            if (menuEntries == null || menuEntries.isEmpty()) {
                ScriptLogger.debug(script, "No menu entries available for Ring of Dueling");
                return null;
            }

            // Debug: Log available options
            ScriptLogger.debug(script, "Ring of Dueling menu has " + menuEntries.size() + " entries:");
            for (MenuEntry entry : menuEntries) {
                if (entry != null) {
                    ScriptLogger.debug(script, "  - Raw: '" + entry.getRawText() +
                        "' | Action: '" + entry.getAction() + "'");
                }
            }

            // Find Castle Wars option
            for (MenuEntry entry : menuEntries) {
                if (entry == null) continue;

                String rawText = entry.getRawText();
                String action = entry.getAction();

                // Check raw text for "Castle Wars" (case-insensitive)
                if (rawText != null && rawText.toLowerCase().contains("castle wars")) {
                    ScriptLogger.debug(script, "Selected menu entry: " + rawText);
                    return entry;
                }

                // Fallback: Check action text
                if (action != null && action.toLowerCase().contains("castle wars")) {
                    ScriptLogger.debug(script, "Selected menu entry by action: " + action);
                    return entry;
                }
            }

            ScriptLogger.warning(script, "Could not find 'Castle Wars' option in Ring of Dueling menu");
            return null;
        };
    }
}
