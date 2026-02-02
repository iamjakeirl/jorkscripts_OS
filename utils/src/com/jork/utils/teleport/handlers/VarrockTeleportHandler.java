package com.jork.utils.teleport.handlers;

import com.jork.utils.teleport.TeleportDestination;
import com.osmb.api.item.ItemID;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.script.Script;
import com.osmb.api.ui.spellbook.StandardSpellbook;
import java.util.Set;

/**
 * Teleport handler for Varrock Teleport spell (Standard Spellbook).
 *
 * <h2>Requirements:</h2>
 * <ul>
 *   <li>Magic Level: 25</li>
 *   <li>Runes: 1 Law, 3 Air, 1 Fire</li>
 *   <li>Spellbook: Standard</li>
 * </ul>
 *
 * <h2>Destination:</h2>
 * Teleports to Varrock Square, near the central fountain and statue.
 * Close to Varrock West Bank and Grand Exchange.
 *
 * @author jork
 */
public class VarrockTeleportHandler extends AbstractSpellTeleportHandler {

    // ═══════════════════════════════════════════════════════════════════════════
    // Varrock Destination Configuration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Varrock Square teleport landing area (verified).
     * RectangleArea(x, y, width, height, plane)
     */
    private static final RectangleArea VARROCK_SQUARE_AREA = new RectangleArea(
        3207, 3421,  // Starting position (SW corner)
        11, 6,       // Width and height
        0            // Ground plane
    );

    /** Varrock region ID for fallback verification (verified) */
    private static final int VARROCK_REGION = 12853;

    /** Walk target position in Varrock Square (verified) */
    private static final WorldPosition VARROCK_WALK_TARGET = new WorldPosition(3185, 3436, 0);

    /** Pre-built destination for Varrock */
    private static final TeleportDestination VARROCK_DESTINATION = new TeleportDestination(
        "Varrock Square",
        VARROCK_SQUARE_AREA,
        VARROCK_REGION,
        VARROCK_WALK_TARGET
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a Varrock Teleport handler.
     *
     * @param script The script instance for API access
     */
    public VarrockTeleportHandler(Script script) {
        super(script, "Varrock Teleport", StandardSpellbook.VARROCK_TELEPORT, VARROCK_DESTINATION);
    }

    @Override
    public Set<Integer> getRequiredItemIds() {
        return Set.of(ItemID.LAW_RUNE, ItemID.AIR_RUNE, ItemID.FIRE_RUNE);
    }
}
