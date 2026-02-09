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
 * Teleport handler for Drakan's Medallion with multi-destination support.
 * Handles menu-based destination selection using MenuHook.
 *
 * <h2>Item Details:</h2>
 * <ul>
 *   <li>Item ID: 22400 (single ID, no charge variants)</li>
 *   <li>Charges: Unlimited teleportation</li>
 *   <li>Slot: Neck (wearable)</li>
 * </ul>
 *
 * <h2>Quest Requirements:</h2>
 * <ul>
 *   <li>Base item: "A Taste of Hope" quest</li>
 * </ul>
 *
 * <h2>Destinations:</h2>
 * <ul>
 *   <li>Ver Sinhaza - Theatre of Blood entrance, has bank nearby</li>
 * </ul>
 *
 * <h2>Restrictions:</h2>
 * Cannot teleport past level 20 Wilderness (enforced by game engine).
 *
 * @author jork
 */
public class DrakansMedallionHandler extends AbstractTeleportHandler {

    // ═══════════════════════════════════════════════════════════════════════════
    // Drakan's Medallion Item ID
    // ═══════════════════════════════════════════════════════════════════════════

    /** Drakan's Medallion - single item ID, unlimited charges */
    private static final int DRAKANS_MEDALLION = 22400;

    /** All valid medallion item IDs (just the one) */
    private static final Set<Integer> ALL_MEDALLION_IDS = Set.of(DRAKANS_MEDALLION);

    /** Ver Sinhaza region ID (verified) */
    private static final int VER_SINHAZA_REGION = 14642;

    // ═══════════════════════════════════════════════════════════════════════════
    // Destination Enum
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Available destinations for Drakan's Medallion teleportation.
     * Each destination has a menu option text and arrival position.
     */
    public enum MedallionDestination {
        /**
         * Ver Sinhaza - Theatre of Blood entrance area.
         * Available immediately upon receiving the medallion.
         * Has bank nearby, commonly used for ToB access.
         */
        VER_SINHAZA("Ver Sinhaza", new WorldPosition(3649, 3230, 0));
        // TODO: Add Darkmeyer destination after verifying teleport landing tile.
        // TODO: Add Slepe destination after verifying teleport landing tile.

        private final String menuOption;
        private final WorldPosition position;

        MedallionDestination(String menuOption, WorldPosition position) {
            this.menuOption = menuOption;
            this.position = position;
        }

        /**
         * Gets the menu option text for this destination.
         * @return The exact menu text to search for
         */
        public String getMenuOption() {
            return menuOption;
        }

        /**
         * Gets the destination world position.
         * @return The position where player arrives after teleport
         */
        public WorldPosition getPosition() {
            return position;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Instance Field
    // ═══════════════════════════════════════════════════════════════════════════

    /** The selected destination for this handler instance */
    private final MedallionDestination destination;

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a Drakan's Medallion handler for the specified destination.
     *
     * @param script The script instance for API access
     * @param destination The medallion destination to teleport to
     */
    public DrakansMedallionHandler(Script script, MedallionDestination destination) {
        super(script, "Drakan's Medallion (" + destination.name() + ")",
              DRAKANS_MEDALLION, createDestinationFor(destination));
        this.destination = destination;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TeleportHandler Overrides
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public boolean isWearable() {
        return true;  // Medallion can be equipped in neck slot
    }

    @Override
    public int getCharges() {
        // Drakan's Medallion has unlimited charges
        return Integer.MAX_VALUE;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AbstractTeleportHandler Implementation
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    protected Set<Integer> getAllValidItemIds() {
        return ALL_MEDALLION_IDS;
    }

    @Override
    protected TeleportResult executeTeleport() {
        // Find medallion (inventory or equipped)
        ItemSearchResult medallion = findTeleportItem();
        if (medallion == null) {
            ScriptLogger.warning(script, "Drakan's Medallion not found");
            return TeleportResult.ITEM_NOT_FOUND;
        }

        // Determine if medallion is equipped or in inventory
        boolean equipped = isItemEquipped();
        ScriptLogger.debug(script, "Drakan's Medallion found " +
            (equipped ? "equipped" : "in inventory") + " - teleporting to " + destination.name());

        boolean interacted;

        if (equipped) {
            // Medallion is equipped - use Equipment.interact()
            interacted = interactWithEquippedMedallion();
        } else {
            // Medallion is in inventory - use ItemSearchResult.interact(MenuHook)
            interacted = interactWithInventoryMedallion();
        }

        if (!interacted) {
            ScriptLogger.warning(script, "Failed to interact with Drakan's Medallion");
            return TeleportResult.INTERACTION_FAILED;
        }

        // Verify arrival at destination
        if (verifyArrival()) {
            return TeleportResult.SUCCESS;
        }

        return TeleportResult.ARRIVAL_TIMEOUT;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Private Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Interacts with equipped Drakan's Medallion to teleport to the selected destination.
     *
     * @return true if interaction was initiated successfully
     */
    private boolean interactWithEquippedMedallion() {
        try {
            Equipment equipment = script.getWidgetManager().getEquipment();
            if (equipment == null) {
                ScriptLogger.warning(script, "Equipment interface not available");
                return false;
            }

            // Find the equipped medallion
            ItemSearchResult equippedMedallion = findTeleportItemInEquipment();
            if (equippedMedallion == null) {
                ScriptLogger.warning(script, "Could not find equipped medallion");
                return false;
            }

            int medallionId = equippedMedallion.getId();
            ScriptLogger.debug(script, "Interacting with equipped medallion (ID: " + medallionId +
                ") for destination: " + destination.getMenuOption());

            // Use Equipment.interact with the destination menu option
            return equipment.interact(medallionId, destination.getMenuOption());
        } catch (Exception e) {
            ScriptLogger.debug(script, "Error interacting with equipped medallion: " + e.getMessage());
            return false;
        }
    }

    /**
     * Interacts with Drakan's Medallion in inventory using MenuHook for destination selection.
     *
     * @return true if interaction was initiated successfully
     */
    private boolean interactWithInventoryMedallion() {
        ItemSearchResult medallion = findTeleportItemInInventory();
        if (medallion == null) {
            ScriptLogger.warning(script, "Drakan's Medallion not found in inventory");
            return false;
        }

        // Create MenuHook to select the destination option
        MenuHook destinationSelector = createDestinationMenuHook();

        // Interact with medallion using the menu hook
        return medallion.interact(destinationSelector);
    }

    /**
     * Creates a MenuHook that selects the appropriate destination option.
     *
     * The Drakan's Medallion inventory menu typically shows:
     * - Wear
     * - Ver Sinhaza
     * - Drop
     *
     * We search for the destination name in the raw text (case-insensitive).
     *
     * @return MenuHook that selects the destination option
     */
    private MenuHook createDestinationMenuHook() {
        return (List<MenuEntry> menuEntries) -> {
            if (menuEntries == null || menuEntries.isEmpty()) {
                ScriptLogger.debug(script, "No menu entries available for Drakan's Medallion");
                return null;
            }

            // Debug: Log available options
            ScriptLogger.debug(script, "Drakan's Medallion menu has " + menuEntries.size() + " entries:");
            for (MenuEntry entry : menuEntries) {
                if (entry != null) {
                    ScriptLogger.debug(script, "  - Raw: '" + entry.getRawText() +
                        "' | Action: '" + entry.getAction() + "'");
                }
            }

            // Find the destination option
            String targetOption = destination.getMenuOption().toLowerCase();
            for (MenuEntry entry : menuEntries) {
                if (entry == null) continue;

                String rawText = entry.getRawText();
                String action = entry.getAction();

                // Check raw text for destination name (case-insensitive)
                if (rawText != null && rawText.toLowerCase().contains(targetOption)) {
                    ScriptLogger.debug(script, "Selected menu entry: " + rawText);
                    return entry;
                }

                // Fallback: Check action text
                if (action != null && action.toLowerCase().contains(targetOption)) {
                    ScriptLogger.debug(script, "Selected menu entry by action: " + action);
                    return entry;
                }
            }

            ScriptLogger.warning(script, "Could not find '" + destination.getMenuOption() +
                "' option in Drakan's Medallion menu");
            return null;
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Static Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a TeleportDestination for the specified medallion destination.
     * Builds a 10x10 RectangleArea centered on the destination position.
     *
     * @param dest The medallion destination enum value
     * @return TeleportDestination configured for arrival verification
     */
    private static TeleportDestination createDestinationFor(MedallionDestination dest) {
        WorldPosition pos = dest.getPosition();

        // Create 10x10 area centered on position (offset by -5 to center)
        RectangleArea area = new RectangleArea(
            pos.getX() - 5,
            pos.getY() - 5,
            10,
            10,
            pos.getPlane()
        );

        int region;
        if (dest == MedallionDestination.VER_SINHAZA) {
            region = VER_SINHAZA_REGION;
        } else {
            // Calculate region ID from position coordinates
            // Region ID formula: (regionX << 8) | regionY
            // where regionX = x >> 6 and regionY = y >> 6
            int regionX = pos.getX() >> 6;
            int regionY = pos.getY() >> 6;
            region = (regionX << 8) | regionY;
        }

        return new TeleportDestination(dest.name(), area, region, pos);
    }
}
