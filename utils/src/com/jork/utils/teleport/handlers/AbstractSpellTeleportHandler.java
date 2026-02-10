package com.jork.utils.teleport.handlers;

import com.jork.utils.ExceptionUtils;
import com.jork.utils.ScriptLogger;
import com.jork.utils.teleport.TeleportDestination;
import com.jork.utils.teleport.TeleportHandler;
import com.jork.utils.teleport.TeleportResult;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.script.Script;
import com.osmb.api.ui.spellbook.InvalidSpellbookTypeException;
import com.osmb.api.ui.tabs.Spellbook;
import com.osmb.api.ui.spellbook.Spell;
import com.osmb.api.ui.spellbook.SpellNotFoundException;
import com.osmb.api.utils.RandomUtils;

/**
 * Base class for spell-based teleport handlers.
 * Handles spellbook interaction using the Standard Spellbook API.
 *
 * <h2>Usage:</h2>
 * Subclasses only need to provide the spell constant and destination.
 * <pre>
 * public class VarrockTeleportHandler extends AbstractSpellTeleportHandler {
 *     public VarrockTeleportHandler(Script script) {
 *         super(script, "Varrock Teleport", StandardSpellbook.VARROCK_TELEPORT, DESTINATION);
 *     }
 * }
 * </pre>
 *
 * <h2>How It Works:</h2>
 * <ol>
 *   <li>Opens spellbook if not already open</li>
 *   <li>Selects the teleport spell</li>
 *   <li>Waits for tab change (indicating teleport started)</li>
 *   <li>Verifies arrival at destination</li>
 * </ol>
 *
 * @author jork
 */
public abstract class AbstractSpellTeleportHandler implements TeleportHandler {

    // ═══════════════════════════════════════════════════════════════════════════
    // Configuration Constants
    // ═══════════════════════════════════════════════════════════════════════════

    /** Maximum retry attempts before giving up */
    protected static final int MAX_TELEPORT_RETRIES = 3;

    // ═══════════════════════════════════════════════════════════════════════════
    // Instance Fields
    // ═══════════════════════════════════════════════════════════════════════════

    protected final Script script;
    protected final TeleportDestination destination;
    protected final Spell spell;
    protected final String name;

    protected int currentRetryCount = 0;

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a new spell teleport handler.
     *
     * @param script The script instance for API access
     * @param name Display name for this teleport method
     * @param spell The spell enum constant (e.g., StandardSpellbook.VARROCK_TELEPORT)
     * @param destination The teleport destination
     */
    protected AbstractSpellTeleportHandler(Script script, String name,
                                            Spell spell, TeleportDestination destination) {
        this.script = script;
        this.name = name;
        this.spell = spell;
        this.destination = destination;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TeleportHandler Interface Implementation
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public String getName() {
        return name;
    }

    @Override
    public TeleportDestination getDestination() {
        return destination;
    }

    @Override
    public boolean requiresItem() {
        return false;  // Spells don't require an item (runes handled by game)
    }

    @Override
    public int getItemId() {
        return -1;  // No item for spells
    }

    @Override
    public boolean canTeleport() {
        // Check if already at destination
        WorldPosition pos = script.getWorldPosition();
        if (pos != null && isAtDestination(pos)) {
            return false;  // No need to teleport
        }

        // Spell teleports are always "available" from our perspective
        // The game will handle rune checking and magic level requirements
        // We could add rune checking here, but it's complex and the game handles it
        return true;
    }

    /**
     * Executes the spell teleport with retry logic.
     * Delegates to executeTeleport() which calls verifyArrival() for arrival confirmation.
     */
    @Override
    public TeleportResult teleport() {
        // Pre-checks
        WorldPosition startPos = script.getWorldPosition();
        if (startPos == null) {
            ScriptLogger.warning(script, "Cannot get current position");
            return TeleportResult.API_UNAVAILABLE;
        }

        // Check if already at destination
        if (isAtDestination(startPos)) {
            ScriptLogger.debug(script, "Already at " + destination.getName());
            return TeleportResult.ALREADY_AT_DESTINATION;
        }

        // Retry loop
        currentRetryCount = 0;
        while (currentRetryCount < MAX_TELEPORT_RETRIES) {
            // Re-check location before each attempt in case a prior cast succeeded
            WorldPosition preAttemptPos = script.getWorldPosition();
            if (preAttemptPos != null && isAtDestination(preAttemptPos)) {
                ScriptLogger.debug(script, "Arrived at " + destination.getName() +
                    " before attempt " + (currentRetryCount + 1));
                return TeleportResult.ALREADY_AT_DESTINATION;
            }

            currentRetryCount++;
            ScriptLogger.actionAttempt(script, "Teleport attempt " + currentRetryCount +
                "/" + MAX_TELEPORT_RETRIES + " via " + name);

            TeleportResult result = executeTeleport();

            if (result.isSuccess()) {
                ScriptLogger.actionSuccess(script, "Teleport successful to " + destination.getName());
                return result;
            }

            // If selection/verification failed, still check if we actually arrived
            WorldPosition postAttemptPos = script.getWorldPosition();
            if (postAttemptPos != null && isAtDestination(postAttemptPos)) {
                ScriptLogger.debug(script, "Arrived at " + destination.getName() +
                    " after failed attempt " + currentRetryCount + " (" + result + ")");
                return TeleportResult.SUCCESS;
            }

            // Handle fatal vs retriable failures
            if (result.isFatal()) {
                ScriptLogger.error(script, "Fatal teleport failure: " + result);
                return result;
            }

            ScriptLogger.warning(script, "Teleport attempt failed: " + result + " - retrying...");
        }

        ScriptLogger.error(script, "Teleport failed after " + MAX_TELEPORT_RETRIES + " attempts");
        return TeleportResult.UNKNOWN_FAILURE;
    }

    @Override
    public boolean isWearable() {
        return false;  // Spells are not items
    }

    @Override
    public int getCharges() {
        return -1;  // Not applicable to spells
    }

    @Override
    public boolean isSpellBased() {
        return true;  // Spell handlers cast from spellbook
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Protected Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Executes the actual teleport via spellbook interaction.
     *
     * @return TeleportResult indicating the outcome of this single attempt
     */
    protected TeleportResult executeTeleport() {
        // Get spellbook
        Spellbook spellbook = script.getWidgetManager().getSpellbook();
        if (spellbook == null) {
            ScriptLogger.warning(script, "Spellbook not available");
            return TeleportResult.API_UNAVAILABLE;
        }

        ScriptLogger.debug(script, "Selecting spell: " + spell.getName());

        try {
            // null ResultType = fire-and-forget click; verifyArrival() confirms landing
            String menuOption = getSpellMenuOption();
            boolean selected = menuOption == null
                ? spellbook.selectSpell(spell, null)
                : spellbook.selectSpell(spell, menuOption, null);

            if (!selected) {
                if (menuOption == null) {
                    ScriptLogger.warning(script, "Failed to select spell: " + spell.getName());
                } else {
                    ScriptLogger.warning(script, "Failed to select spell option '" + menuOption +
                        "' for: " + spell.getName());
                }
                return TeleportResult.INTERACTION_FAILED;
            }
        } catch (SpellNotFoundException e) {
            ScriptLogger.warning(script, "Spell not found in spellbook: " + spell.getName() +
                " - may be wrong spellbook or insufficient runes/level");
            return TeleportResult.ITEM_NOT_FOUND;  // Reusing this for "spell not available"
        } catch (InvalidSpellbookTypeException e) {
            ScriptLogger.warning(script, "Invalid spellbook/menu option for spell: " + spell.getName());
            return TeleportResult.ITEM_NOT_FOUND;
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.warning(script, "Error selecting spell: " + e.getMessage());
            return TeleportResult.INTERACTION_FAILED;
        }

        // Verify arrival at destination
        if (verifyArrival()) {
            return TeleportResult.SUCCESS;
        }

        return TeleportResult.ARRIVAL_TIMEOUT;
    }

    /**
     * Verifies arrival at destination with polling.
     * Uses pollFramesHuman so a human reaction delay is added after arrival is detected.
     * On timeout, the wait duration itself serves as the delay before retry.
     *
     * @return true if arrived at destination within timeout
     */
    protected boolean verifyArrival() {
        try {
            return script.pollFramesHuman(() -> {
                WorldPosition pos = script.getWorldPosition();
                return pos != null && isAtDestination(pos);
            }, RandomUtils.uniformRandom(4000, 7000));
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            return false;
        }
    }

    /**
     * Gets the Script instance for subclasses that need it.
     *
     * @return The Script instance
     */
    protected Script getScript() {
        return script;
    }

    /**
     * Gets the spell used by this handler.
     *
     * @return The spell enum constant
     */
    protected Spell getSpell() {
        return spell;
    }

    /**
     * Optional spell menu option for spell variants (e.g. Varrock -> Grand Exchange).
     * Return null to use default left-click behavior.
     */
    protected String getSpellMenuOption() {
        return null;
    }
}
