package com.jork.utils.teleport;

import com.jork.utils.teleport.handlers.CamelotTeleportHandler;
import com.jork.utils.teleport.handlers.EctophialHandler;
import com.jork.utils.teleport.handlers.FaladorTeleportHandler;
import com.jork.utils.teleport.handlers.RingOfDuelingHandler;
import com.jork.utils.teleport.handlers.VarrockTeleportHandler;
import com.jork.utils.teleport.handlers.WalkingHandler;

// Standard Spellbook handlers (Plan 01)
import com.jork.utils.teleport.handlers.LumbridgeTeleportHandler;
import com.jork.utils.teleport.handlers.ArdougneTeleportHandler;
import com.jork.utils.teleport.handlers.TrollheimTeleportHandler;

// Standard Spellbook handlers (Plan 02)
import com.jork.utils.teleport.handlers.KourendCastleTeleportHandler;
import com.jork.utils.teleport.handlers.WatchtowerTeleportHandler;
import com.jork.utils.teleport.handlers.ApeAtollTeleportHandler;

// Specialty item handlers (Plan 03)
import com.jork.utils.teleport.handlers.DrakansMedallionHandler;
import com.jork.utils.teleport.handlers.DrakansMedallionHandler.MedallionDestination;
import com.jork.utils.teleport.handlers.AmuletOfTheEyeHandler;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.script.Script;

/**
 * Factory for creating TeleportHandler instances.
 * Provides easy integration with script configuration enums.
 *
 * <h2>Usage:</h2>
 * <pre>
 *     // Direct factory method
 *     TeleportHandler handler = TeleportHandlerFactory.createRingOfDuelingHandler(script);
 *
 *     // From BankLocation enum display name
 *     TeleportHandler handler = TeleportHandlerFactory.fromBankLocationName(
 *         script,
 *         config.getBankLocation().getDisplayName()
 *     );
 * </pre>
 *
 * @author jork
 */
public class TeleportHandlerFactory {

    // ═══════════════════════════════════════════════════════════════════════════
    // Pre-defined Destinations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Port Phasmatys bank area.
     * RectangleArea(x, y, width, height, plane)
     */
    private static final RectangleArea PORT_PHASMATYS_BANK_AREA = new RectangleArea(
        3687, 3466,  // Starting position
        4, 3,        // Width and height
        0            // Ground plane
    );

    /** Walk target position inside Port Phasmatys bank */
    private static final WorldPosition PORT_PHASMATYS_BANK_POS = new WorldPosition(3688, 3467, 0);

    /**
     * Pre-built destination for Port Phasmatys bank.
     * NOTE: Uses area-only constructor (no region ID fallback) because Port Phasmatys bank
     * shares region 14646 with the Ectofuntus. Region-based fallback would cause false positives
     * when standing at Ectofuntus.
     */
    private static final TeleportDestination PORT_PHASMATYS_DESTINATION = new TeleportDestination(
        "Port Phasmatys Bank",
        PORT_PHASMATYS_BANK_AREA,
        PORT_PHASMATYS_BANK_POS
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Factory Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a handler for Ring of Dueling (Castle Wars teleport).
     *
     * @param script The script instance
     * @return RingOfDuelingHandler instance
     */
    public static TeleportHandler createRingOfDuelingHandler(Script script) {
        return new RingOfDuelingHandler(script);
    }

    /**
     * Creates a walking handler for Port Phasmatys bank.
     *
     * @param script The script instance
     * @return WalkingHandler for Port Phasmatys
     */
    public static TeleportHandler createPortPhasmatysWalkHandler(Script script) {
        return new WalkingHandler(script, "Walk to Port Phasmatys", PORT_PHASMATYS_DESTINATION);
    }

    /**
     * Creates a handler based on the Ectofuntus BankLocation enum display name.
     * This method integrates with the existing configuration system.
     *
     * @param script The script instance
     * @param bankLocationDisplayName The display name from BankLocation enum
     * @return Appropriate TeleportHandler, or null if not recognized
     */
    public static TeleportHandler fromBankLocationName(Script script, String bankLocationDisplayName) {
        if (bankLocationDisplayName == null) {
            return null;
        }

        String lowerName = bankLocationDisplayName.toLowerCase();

        // Ring of Dueling variants
        if (lowerName.contains("ring of dueling") || lowerName.contains("castle wars")) {
            return createRingOfDuelingHandler(script);
        }

        // Walking to Port Phasmatys
        if (lowerName.contains("port phasmatys") || lowerName.contains("walk")) {
            return createPortPhasmatysWalkHandler(script);
        }

        // Ectophial
        if (lowerName.contains("ectophial") || lowerName.contains("ectofuntus")) {
            return createEctophialHandler(script);
        }

        // Spell teleports (original)
        if (lowerName.contains("varrock")) {
            return createVarrockTeleportHandler(script);
        }
        if (lowerName.contains("falador")) {
            return createFaladorTeleportHandler(script);
        }
        if (lowerName.contains("camelot")) {
            return createCamelotTeleportHandler(script);
        }

        // Standard spellbook - extended
        if (lowerName.contains("lumbridge")) {
            return createLumbridgeTeleportHandler(script);
        }
        if (lowerName.contains("ardougne")) {
            return createArdougneTeleportHandler(script);
        }
        if (lowerName.contains("trollheim")) {
            return createTrollheimTeleportHandler(script);
        }
        if (lowerName.contains("kourend")) {
            return createKourendCastleTeleportHandler(script);
        }
        if (lowerName.contains("watchtower") || lowerName.contains("yanille")) {
            return createWatchtowerTeleportHandler(script);
        }
        if (lowerName.contains("ape atoll") || lowerName.contains("marimbo")) {
            return createApeAtollTeleportHandler(script);
        }

        // Specialty items
        if (lowerName.contains("drakan") || lowerName.contains("ver sinhaza")) {
            return createDrakansVerSinhazaHandler(script);
        }
        if (lowerName.contains("amulet of the eye") || lowerName.contains("temple of the eye") || lowerName.contains("gotr")) {
            return createAmuletOfTheEyeHandler(script);
        }

        return null;
    }

    /**
     * Creates a custom walking handler for any destination.
     *
     * @param script The script instance
     * @param name Display name for the handler
     * @param destination The destination to walk to
     * @return WalkingHandler for the specified destination
     */
    public static TeleportHandler createWalkingHandler(Script script, String name,
                                                        TeleportDestination destination) {
        return new WalkingHandler(script, name, destination);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Ectophial Handler
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a handler for Ectophial teleportation to the Ectofuntus.
     *
     * @param script The script instance
     * @return EctophialHandler instance
     */
    public static TeleportHandler createEctophialHandler(Script script) {
        return new EctophialHandler(script);
    }

    /**
     * Creates an Ectophial handler and returns the typed version.
     * Useful when you need access to Ectophial-specific methods like hasEmptyEctophial().
     *
     * @param script The script instance
     * @return EctophialHandler instance (typed)
     */
    public static EctophialHandler createEctophialHandlerTyped(Script script) {
        return new EctophialHandler(script);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Spell Teleport Handlers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a handler for Varrock Teleport spell.
     *
     * @param script The script instance
     * @return VarrockTeleportHandler instance
     */
    public static TeleportHandler createVarrockTeleportHandler(Script script) {
        return new VarrockTeleportHandler(script);
    }

    /**
     * Creates a handler for Falador Teleport spell.
     *
     * @param script The script instance
     * @return FaladorTeleportHandler instance
     */
    public static TeleportHandler createFaladorTeleportHandler(Script script) {
        return new FaladorTeleportHandler(script);
    }

    /**
     * Creates a handler for Camelot Teleport spell.
     *
     * @param script The script instance
     * @return CamelotTeleportHandler instance
     */
    public static TeleportHandler createCamelotTeleportHandler(Script script) {
        return new CamelotTeleportHandler(script);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Standard Spellbook Teleport Handlers (Extended)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a handler for Lumbridge Teleport spell.
     *
     * @param script The script instance
     * @return LumbridgeTeleportHandler instance
     */
    public static TeleportHandler createLumbridgeTeleportHandler(Script script) {
        return new LumbridgeTeleportHandler(script);
    }

    /**
     * Creates a handler for Ardougne Teleport spell.
     *
     * @param script The script instance
     * @return ArdougneTeleportHandler instance
     */
    public static TeleportHandler createArdougneTeleportHandler(Script script) {
        return new ArdougneTeleportHandler(script);
    }

    /**
     * Creates a handler for Trollheim Teleport spell.
     *
     * @param script The script instance
     * @return TrollheimTeleportHandler instance
     */
    public static TeleportHandler createTrollheimTeleportHandler(Script script) {
        return new TrollheimTeleportHandler(script);
    }

    /**
     * Creates a handler for Kourend Castle Teleport spell.
     *
     * @param script The script instance
     * @return KourendCastleTeleportHandler instance
     */
    public static TeleportHandler createKourendCastleTeleportHandler(Script script) {
        return new KourendCastleTeleportHandler(script);
    }

    /**
     * Creates a handler for Watchtower Teleport spell.
     *
     * @param script The script instance
     * @return WatchtowerTeleportHandler instance
     */
    public static TeleportHandler createWatchtowerTeleportHandler(Script script) {
        return new WatchtowerTeleportHandler(script);
    }

    /**
     * Creates a handler for Ape Atoll Teleport spell.
     *
     * @param script The script instance
     * @return ApeAtollTeleportHandler instance
     */
    public static TeleportHandler createApeAtollTeleportHandler(Script script) {
        return new ApeAtollTeleportHandler(script);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Specialty Item Teleport Handlers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a handler for Drakan's Medallion teleport to Ver Sinhaza.
     *
     * @param script The script instance
     * @return DrakansMedallionHandler for Ver Sinhaza
     */
    public static TeleportHandler createDrakansVerSinhazaHandler(Script script) {
        return new DrakansMedallionHandler(script, MedallionDestination.VER_SINHAZA);
    }

    // TODO: Add Darkmeyer/Slepe factory methods after verifying landing tiles.

    /**
     * Creates a handler for Amulet of the Eye teleport to Temple of the Eye.
     *
     * @param script The script instance
     * @return AmuletOfTheEyeHandler instance
     */
    public static TeleportHandler createAmuletOfTheEyeHandler(Script script) {
        return new AmuletOfTheEyeHandler(script);
    }

    // Private constructor to prevent instantiation
    private TeleportHandlerFactory() {
    }
}
