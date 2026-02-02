package com.jork.utils.teleport.handlers;

import com.jork.utils.teleport.TeleportDestination;
import com.osmb.api.item.ItemID;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.script.Script;
import com.osmb.api.ui.spellbook.StandardSpellbook;
import java.util.Set;

/**
 * Teleport handler for Kourend Castle Teleport spell (Standard Spellbook).
 *
 * <h2>Requirements:</h2>
 * <ul>
 *   <li>Magic Level: 48</li>
 *   <li>Runes: 2 Law, 2 Fire, 5 Water, 2 Soul</li>
 *   <li>Spellbook: Standard</li>
 *   <li>Quest/Favour: Architectural Alliance (all houses at 100% favour) OR purchased from Arceuus Library</li>
 * </ul>
 *
 * <h2>Destination:</h2>
 * Teleports to the Kourend Castle courtyard in Great Kourend.
 * Located north of the main castle entrance, central to all Zeah houses.
 *
 * @author jork
 */
public class KourendCastleTeleportHandler extends AbstractSpellTeleportHandler {

    // ═══════════════════════════════════════════════════════════════════════════
    // Kourend Castle Destination Configuration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Kourend Castle courtyard teleport landing area.
     * RectangleArea(x, y, width, height, plane)
     */
    private static final RectangleArea KOUREND_CASTLE_AREA = new RectangleArea(
        1639, 3670,  // Starting position (SW corner)
        10, 10,      // Width and height
        0            // Ground plane
    );

    /** Kourend Castle region ID for fallback verification */
    private static final int KOUREND_REGION = 6457;

    /** Walk target position in Kourend Castle courtyard */
    private static final WorldPosition KOUREND_WALK_TARGET = new WorldPosition(1643, 3673, 0);

    /** Pre-built destination for Kourend Castle */
    private static final TeleportDestination KOUREND_DESTINATION = new TeleportDestination(
        "Kourend Castle",
        KOUREND_CASTLE_AREA,
        KOUREND_REGION,
        KOUREND_WALK_TARGET
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a Kourend Castle Teleport handler.
     *
     * @param script The script instance for API access
     */
    public KourendCastleTeleportHandler(Script script) {
        super(script, "Kourend Castle Teleport", StandardSpellbook.KOUREND_TELEPORT, KOUREND_DESTINATION);
    }

    @Override
    public Set<Integer> getRequiredItemIds() {
        return Set.of(ItemID.LAW_RUNE, ItemID.FIRE_RUNE, ItemID.WATER_RUNE, ItemID.SOUL_RUNE);
    }
}
