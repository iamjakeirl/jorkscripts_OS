package com.jork.utils.teleport.handlers;

import com.jork.utils.teleport.TeleportDestination;
import com.osmb.api.item.ItemID;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.script.Script;
import com.osmb.api.ui.spellbook.StandardSpellbook;
import java.util.Set;

/**
 * Teleport handler for Trollheim Teleport spell (Standard Spellbook).
 *
 * <h2>Requirements:</h2>
 * <ul>
 *   <li>Magic Level: 61</li>
 *   <li>Runes: 2 Law, 2 Fire</li>
 *   <li>Spellbook: Standard</li>
 *   <li>Quest: Eadgar's Ruse required</li>
 * </ul>
 *
 * <h2>Destination:</h2>
 * Teleports to the top of Trollheim mountain, near the herb patch.
 * Close to God Wars Dungeon entrance and My Arm's Big Adventure herb patch.
 *
 * @author jork
 */
public class TrollheimTeleportHandler extends AbstractSpellTeleportHandler {

    // ═══════════════════════════════════════════════════════════════════════════
    // Trollheim Destination Configuration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Trollheim mountain top teleport landing area.
     * RectangleArea(x, y, width, height, plane)
     */
    private static final RectangleArea TROLLHEIM_AREA = new RectangleArea(
        2887, 3674,  // Starting position (SW corner)
        10, 10,      // Width and height
        0            // Ground plane
    );

    /** Trollheim region ID for fallback verification */
    private static final int TROLLHEIM_REGION = 11321;

    /** Walk target position near Trollheim herb patch */
    private static final WorldPosition TROLLHEIM_POS = new WorldPosition(2891, 3678, 0);

    /** Pre-built destination for Trollheim */
    private static final TeleportDestination TROLLHEIM_DESTINATION = new TeleportDestination(
        "Trollheim",
        TROLLHEIM_AREA,
        TROLLHEIM_REGION,
        TROLLHEIM_POS
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a Trollheim Teleport handler.
     *
     * @param script The script instance for API access
     */
    public TrollheimTeleportHandler(Script script) {
        super(script, "Trollheim Teleport", StandardSpellbook.TROLLHEIM_TELEPORT, TROLLHEIM_DESTINATION);
    }

    @Override
    public Set<Integer> getRequiredItemIds() {
        return Set.of(ItemID.LAW_RUNE, ItemID.FIRE_RUNE);
    }
}
