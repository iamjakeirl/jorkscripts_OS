package com.jork.utils.teleport.handlers;

import com.jork.utils.teleport.TeleportDestination;
import com.osmb.api.item.ItemID;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.script.Script;
import com.osmb.api.ui.spellbook.StandardSpellbook;
import java.util.Set;

/**
 * Teleport handler for Falador Teleport spell (Standard Spellbook).
 *
 * <h2>Requirements:</h2>
 * <ul>
 *   <li>Magic Level: 37</li>
 *   <li>Runes: 1 Law, 3 Air, 1 Water</li>
 *   <li>Spellbook: Standard</li>
 * </ul>
 *
 * <h2>Destination:</h2>
 * Teleports to Falador town center, near the White Knight's Castle.
 * Close to Falador West Bank and Falador East Bank.
 *
 * @author jork
 */
public class FaladorTeleportHandler extends AbstractSpellTeleportHandler {

    // ═══════════════════════════════════════════════════════════════════════════
    // Falador Destination Configuration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Falador teleport landing area (verified).
     * RectangleArea(x, y, width, height, plane)
     */
    private static final RectangleArea FALADOR_AREA = new RectangleArea(
        2960, 3376,  // Starting position (SW corner)
        10, 8,       // Width and height
        0            // Ground plane
    );

    /** Falador region ID for fallback verification (verified) */
    private static final int FALADOR_REGION = 11828;

    /** Walk target position in Falador center (verified) */
    private static final WorldPosition FALADOR_WALK_TARGET = new WorldPosition(2946, 3368, 0);

    /** Pre-built destination for Falador */
    private static final TeleportDestination FALADOR_DESTINATION = new TeleportDestination(
        "Falador",
        FALADOR_AREA,
        FALADOR_REGION,
        FALADOR_WALK_TARGET
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a Falador Teleport handler.
     *
     * @param script The script instance for API access
     */
    public FaladorTeleportHandler(Script script) {
        super(script, "Falador Teleport", StandardSpellbook.FALADOR_TELEPORT, FALADOR_DESTINATION);
    }

    @Override
    public Set<Integer> getRequiredItemIds() {
        return Set.of(ItemID.LAW_RUNE, ItemID.AIR_RUNE, ItemID.WATER_RUNE);
    }
}
