package com.jork.utils.teleport.handlers;

import com.jork.utils.teleport.TeleportDestination;
import com.osmb.api.item.ItemID;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.script.Script;
import com.osmb.api.ui.spellbook.StandardSpellbook;
import java.util.Set;

/**
 * Teleport handler for Watchtower Teleport spell (Standard Spellbook).
 *
 * <h2>Requirements:</h2>
 * <ul>
 *   <li>Magic Level: 58</li>
 *   <li>Runes: 2 Law, 2 Earth</li>
 *   <li>Spellbook: Standard</li>
 *   <li>Quest: Watchtower required</li>
 * </ul>
 *
 * <h2>Destination:</h2>
 * Teleports to the top of the Watchtower near Yanille (plane 2).
 * Useful for accessing the Yanille area and nearby activities.
 *
 * <p><strong>Note:</strong> The teleport destination is on plane 2 (top of tower),
 * not ground level. Players will need to climb down to reach ground level.</p>
 *
 * @author jork
 */
public class WatchtowerTeleportHandler extends AbstractSpellTeleportHandler {

    // ═══════════════════════════════════════════════════════════════════════════
    // Watchtower Destination Configuration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Watchtower teleport landing area.
     * RectangleArea(x, y, width, height, plane)
     * IMPORTANT: Plane 2 - top of the Watchtower
     */
    private static final RectangleArea WATCHTOWER_AREA = new RectangleArea(
        2544, 3110,  // Starting position (SW corner)
        10, 10,      // Width and height
        2            // Plane 2 - top of tower
    );

    /** Watchtower region ID for fallback verification */
    private static final int WATCHTOWER_REGION = 10033;

    /** Walk target position on top of Watchtower (plane 2) */
    private static final WorldPosition WATCHTOWER_WALK_TARGET = new WorldPosition(2549, 3114, 2);

    /** Pre-built destination for Watchtower */
    private static final TeleportDestination WATCHTOWER_DESTINATION = new TeleportDestination(
        "Watchtower",
        WATCHTOWER_AREA,
        WATCHTOWER_REGION,
        WATCHTOWER_WALK_TARGET
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a Watchtower Teleport handler.
     *
     * @param script The script instance for API access
     */
    public WatchtowerTeleportHandler(Script script) {
        super(script, "Watchtower Teleport", StandardSpellbook.WATCHTOWER_TELEPORT, WATCHTOWER_DESTINATION);
    }

    @Override
    public Set<Integer> getRequiredItemIds() {
        return Set.of(ItemID.LAW_RUNE, ItemID.EARTH_RUNE);
    }
}
