package com.jork.utils.teleport.handlers;

import com.jork.utils.teleport.TeleportDestination;
import com.osmb.api.item.ItemID;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.script.Script;
import com.osmb.api.ui.spellbook.StandardSpellbook;
import java.util.Set;

/**
 * Teleport handler for Lumbridge Teleport spell (Standard Spellbook).
 *
 * <h2>Requirements:</h2>
 * <ul>
 *   <li>Magic Level: 31</li>
 *   <li>Runes: 1 Law, 3 Air, 1 Earth</li>
 *   <li>Spellbook: Standard</li>
 * </ul>
 *
 * <h2>Destination:</h2>
 * Teleports to Lumbridge Castle courtyard, near the castle entrance.
 * Close to Lumbridge bank (top floor), general store, and Cook's kitchen.
 *
 * @author jork
 */
public class LumbridgeTeleportHandler extends AbstractSpellTeleportHandler {

    // ═══════════════════════════════════════════════════════════════════════════
    // Lumbridge Destination Configuration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Lumbridge Castle courtyard teleport landing area (verified).
     * RectangleArea(x, y, width, height, plane)
     */
    private static final RectangleArea LUMBRIDGE_AREA = new RectangleArea(
        3217, 3215,  // Starting position (SW corner)
        8, 8,        // Width and height
        0            // Ground plane
    );

    /** Lumbridge region ID for fallback verification (verified) */
    private static final int LUMBRIDGE_REGION = 12850;

    /** Walk target position in Lumbridge Castle courtyard (verified) */
    private static final WorldPosition LUMBRIDGE_POS = new WorldPosition(3208, 3212, 0);

    /** Pre-built destination for Lumbridge */
    private static final TeleportDestination LUMBRIDGE_DESTINATION = new TeleportDestination(
        "Lumbridge Castle",
        LUMBRIDGE_AREA,
        LUMBRIDGE_REGION,
        LUMBRIDGE_POS
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a Lumbridge Teleport handler.
     *
     * @param script The script instance for API access
     */
    public LumbridgeTeleportHandler(Script script) {
        super(script, "Lumbridge Teleport", StandardSpellbook.LUMBRIDGE_TELEPORT, LUMBRIDGE_DESTINATION);
    }

    @Override
    public Set<Integer> getRequiredItemIds() {
        return Set.of(ItemID.LAW_RUNE, ItemID.AIR_RUNE, ItemID.EARTH_RUNE);
    }
}
