package com.jork.utils.teleport.handlers;

import com.jork.utils.teleport.TeleportDestination;
import com.osmb.api.item.ItemID;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.script.Script;
import com.osmb.api.ui.spellbook.StandardSpellbook;
import java.util.Set;

/**
 * Teleport handler for Ape Atoll Teleport spell (Standard Spellbook).
 *
 * <h2>Requirements:</h2>
 * <ul>
 *   <li>Magic Level: 64</li>
 *   <li>Runes: 2 Law, 2 Fire, 2 Water, 1 Banana</li>
 *   <li>Spellbook: Standard</li>
 *   <li>Quest: Recipe for Disaster (freeing King Awowogei subquest) OR Monkey Madness II</li>
 * </ul>
 *
 * <h2>Destination:</h2>
 * Teleports to the Temple of Marimbo on Ape Atoll.
 * Used for accessing maniacal monkey training and Ape Atoll activities.
 *
 * <p><strong>Note:</strong> Player may need a greegree equipped to avoid being
 * attacked by nearby monkeys. Without a greegree, the Temple guards will attack.</p>
 *
 * @author jork
 */
public class ApeAtollTeleportHandler extends AbstractSpellTeleportHandler {

    // ═══════════════════════════════════════════════════════════════════════════
    // Ape Atoll Destination Configuration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Temple of Marimbo teleport landing area.
     * RectangleArea(x, y, width, height, plane)
     */
    private static final RectangleArea APE_ATOLL_AREA = new RectangleArea(
        2755, 2780,  // Starting position (SW corner)
        12, 12,      // Width and height (slightly larger area)
        0            // Ground plane
    );

    /** Ape Atoll region ID for fallback verification */
    private static final int APE_ATOLL_REGION = 10794;

    /** Walk target position at Temple of Marimbo */
    private static final WorldPosition APE_ATOLL_WALK_TARGET = new WorldPosition(2764, 2784, 0);

    /** Pre-built destination for Ape Atoll */
    private static final TeleportDestination APE_ATOLL_DESTINATION = new TeleportDestination(
        "Temple of Marimbo",
        APE_ATOLL_AREA,
        APE_ATOLL_REGION,
        APE_ATOLL_WALK_TARGET
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates an Ape Atoll Teleport handler.
     *
     * @param script The script instance for API access
     */
    public ApeAtollTeleportHandler(Script script) {
        super(script, "Ape Atoll Teleport", StandardSpellbook.APE_ATOLL_TELEPORT, APE_ATOLL_DESTINATION);
    }

    @Override
    public Set<Integer> getRequiredItemIds() {
        return Set.of(ItemID.LAW_RUNE, ItemID.FIRE_RUNE, ItemID.WATER_RUNE, ItemID.BANANA);
    }
}
