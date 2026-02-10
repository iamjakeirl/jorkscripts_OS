package com.jork.utils.teleport.handlers;

import com.jork.utils.teleport.TeleportDestination;
import com.osmb.api.item.ItemID;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.script.Script;
import com.osmb.api.ui.spellbook.StandardSpellbook;

import java.util.Set;

/**
 * Teleport handler for Grand Exchange teleport variant (Varrock Teleport with Varrock diary).
 *
 * <h2>Requirements:</h2>
 * <ul>
 *   <li>Magic Level: 25</li>
 *   <li>Runes: 1 Law, 3 Air, 1 Fire (same as Varrock Teleport)</li>
 *   <li>Spellbook: Standard</li>
 *   <li>Varrock medium diary completed (for GE landing variant)</li>
 * </ul>
 *
 * @author jork
 */
public class GrandExchangeTeleportHandler extends AbstractSpellTeleportHandler {
    private static final String GE_SPELL_MENU_OPTION = "Grand Exchange";

    /**
     * Grand Exchange teleport landing strip from validated utility reference.
     * RectangleArea(x, y, width, height, plane)
     */
    private static final RectangleArea GRAND_EXCHANGE_AREA = new RectangleArea(
        3162, 3489,
        5, 1,
        0
    );

    /** GE landing region ID fallback */
    private static final int GRAND_EXCHANGE_REGION = 12598;

    /** Walk target in front of GE booths */
    private static final WorldPosition GRAND_EXCHANGE_WALK_TARGET = new WorldPosition(3164, 3489, 0);

    private static final TeleportDestination GRAND_EXCHANGE_DESTINATION = new TeleportDestination(
        "Grand Exchange",
        GRAND_EXCHANGE_AREA,
        GRAND_EXCHANGE_REGION,
        GRAND_EXCHANGE_WALK_TARGET
    );

    /**
     * Creates a Grand Exchange Teleport handler.
     *
     * @param script script instance
     */
    public GrandExchangeTeleportHandler(Script script) {
        super(
            script,
            "Grand Exchange Teleport",
            StandardSpellbook.VARROCK_TELEPORT,
            GRAND_EXCHANGE_DESTINATION
        );
    }

    @Override
    public Set<Integer> getRequiredItemIds() {
        return Set.of(ItemID.LAW_RUNE, ItemID.AIR_RUNE, ItemID.FIRE_RUNE);
    }

    @Override
    protected String getSpellMenuOption() {
        return GE_SPELL_MENU_OPTION;
    }
}
