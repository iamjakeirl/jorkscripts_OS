package com.jork.utils.teleport.handlers;

import com.jork.utils.teleport.TeleportDestination;
import com.osmb.api.item.ItemID;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.script.Script;
import com.osmb.api.ui.spellbook.StandardSpellbook;
import java.util.Set;

/**
 * Teleport handler for Ardougne Teleport spell (Standard Spellbook).
 *
 * <h2>Requirements:</h2>
 * <ul>
 *   <li>Magic Level: 51</li>
 *   <li>Runes: 2 Law, 2 Water</li>
 *   <li>Spellbook: Standard</li>
 *   <li>Quest: Plague City required</li>
 * </ul>
 *
 * <h2>Destination:</h2>
 * Teleports to East Ardougne market square, near the central plaza.
 * Close to Ardougne bank, market stalls, and Ardougne cloak teleport area.
 *
 * @author jork
 */
public class ArdougneTeleportHandler extends AbstractSpellTeleportHandler {

    // ═══════════════════════════════════════════════════════════════════════════
    // Ardougne Destination Configuration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * East Ardougne market square teleport landing area.
     * RectangleArea(x, y, width, height, plane)
     */
    private static final RectangleArea ARDOUGNE_AREA = new RectangleArea(
        2655, 3302,  // Starting position (SW corner)
        10, 10,      // Width and height
        0            // Ground plane
    );

    /** Ardougne region ID for fallback verification */
    private static final int ARDOUGNE_REGION = 10290;

    /** Walk target position in East Ardougne market square */
    private static final WorldPosition ARDOUGNE_POS = new WorldPosition(2662, 3307, 0);

    /** Pre-built destination for Ardougne */
    private static final TeleportDestination ARDOUGNE_DESTINATION = new TeleportDestination(
        "Ardougne Market",
        ARDOUGNE_AREA,
        ARDOUGNE_REGION,
        ARDOUGNE_POS
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates an Ardougne Teleport handler.
     *
     * @param script The script instance for API access
     */
    public ArdougneTeleportHandler(Script script) {
        super(script, "Ardougne Teleport", StandardSpellbook.ARDOUGNE_TELEPORT, ARDOUGNE_DESTINATION);
    }

    @Override
    public Set<Integer> getRequiredItemIds() {
        return Set.of(ItemID.LAW_RUNE, ItemID.WATER_RUNE);
    }
}
