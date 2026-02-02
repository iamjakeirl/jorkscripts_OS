package com.jork.utils.teleport.handlers;

import com.jork.utils.teleport.TeleportDestination;
import com.osmb.api.item.ItemID;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.script.Script;
import com.osmb.api.ui.spellbook.StandardSpellbook;
import java.util.Set;

/**
 * Teleport handler for Camelot Teleport spell (Standard Spellbook).
 *
 * <h2>Requirements:</h2>
 * <ul>
 *   <li>Magic Level: 45</li>
 *   <li>Runes: 1 Law, 5 Air</li>
 *   <li>Spellbook: Standard</li>
 * </ul>
 *
 * <h2>Destination:</h2>
 * Teleports to Camelot Castle courtyard in Seers' Village.
 * Close to Camelot Bank (after Kandarin Hard Diary, can teleport directly to bank).
 *
 * @author jork
 */
public class CamelotTeleportHandler extends AbstractSpellTeleportHandler {

    // ═══════════════════════════════════════════════════════════════════════════
    // Camelot Destination Configuration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Camelot teleport landing area (verified).
     * RectangleArea(x, y, width, height, plane)
     */
    private static final RectangleArea CAMELOT_AREA = new RectangleArea(
        2753, 3473,  // Starting position (SW corner)
        8, 8,        // Width and height
        0            // Ground plane
    );

    /** Camelot region ID for fallback verification (verified) */
    private static final int CAMELOT_REGION = 11062;

    /** Walk target position in Camelot courtyard (verified) */
    private static final WorldPosition CAMELOT_WALK_TARGET = new WorldPosition(2725, 3491, 0);

    /** Pre-built destination for Camelot */
    private static final TeleportDestination CAMELOT_DESTINATION = new TeleportDestination(
        "Camelot",
        CAMELOT_AREA,
        CAMELOT_REGION,
        CAMELOT_WALK_TARGET
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a Camelot Teleport handler.
     *
     * @param script The script instance for API access
     */
    public CamelotTeleportHandler(Script script) {
        super(script, "Camelot Teleport", StandardSpellbook.CAMELOT_TELEPORT, CAMELOT_DESTINATION);
    }

    @Override
    public Set<Integer> getRequiredItemIds() {
        return Set.of(ItemID.LAW_RUNE, ItemID.AIR_RUNE);
    }
}
