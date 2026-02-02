package com.jork.script.Ectofuntus;

import com.jork.script.Ectofuntus.config.EctoConfig;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.scene.ObjectManager;
import com.osmb.api.script.Script;
import com.osmb.api.ui.WidgetManager;

import java.util.function.BooleanSupplier;

/**
 * Interface defining the contract for Ectofuntus script implementations.
 * Both the main Ectofuntus script and EctofuntusTest implement this interface,
 * allowing tasks to work with either implementation.
 *
 * @author jork
 */
public interface EctofuntusScript {

    // ───────────────────────────────────────────────────────────────────────────
    // Configuration
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Gets the script configuration.
     * @return The EctoConfig containing bone type, bank location, etc.
     */
    EctoConfig getConfig();

    // ───────────────────────────────────────────────────────────────────────────
    // State Flags
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Checks if banking is needed.
     * @return true if the script should bank
     */
    boolean shouldBank();

    /**
     * Sets the banking flag.
     * @param shouldBank true if banking is needed
     */
    void setShouldBank(boolean shouldBank);

    /**
     * Checks if slime has been collected.
     * @return true if slime is in inventory
     */
    boolean hasSlime();

    /**
     * Sets the slime flag.
     * @param hasSlime true if slime has been collected
     */
    void setHasSlime(boolean hasSlime);

    /**
     * Checks if bonemeal has been ground.
     * @return true if bonemeal is in inventory
     */
    boolean hasBoneMeal();

    /**
     * Sets the bonemeal flag.
     * @param hasBoneMeal true if bonemeal has been ground
     */
    void setHasBoneMeal(boolean hasBoneMeal);

    // ───────────────────────────────────────────────────────────────────────────
    // Baseline Tracking (for container-based completion detection)
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Gets the supply baseline (empty pot/bucket count after banking).
     * Used by WorshipTask to detect completion when empties return to baseline.
     * @return baseline count (8 default, 9 if wearable teleport)
     */
    int getSupplyBaseline();

    /**
     * Sets the supply baseline after banking.
     * Called by BankTask after successful withdraw.
     * @param baseline the empty pot/bucket count target
     */
    void setSupplyBaseline(int baseline);

    // ───────────────────────────────────────────────────────────────────────────
    // Game API Delegations
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Gets the widget manager for UI interactions.
     * @return The WidgetManager instance
     */
    WidgetManager getWidgetManager();

    /**
     * Gets the object manager for game object interactions.
     * @return The ObjectManager instance
     */
    ObjectManager getObjectManager();

    /**
     * Gets the player's current world position.
     * @return The current WorldPosition
     */
    WorldPosition getWorldPosition();

    // ───────────────────────────────────────────────────────────────────────────
    // Polling Methods
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Polls until a condition is met or timeout expires.
     * @param condition The condition to check
     * @param timeout Maximum time to wait in milliseconds
     * @return true if condition was met, false if timeout
     */
    boolean pollFramesUntil(BooleanSupplier condition, int timeout);

    /**
     * Polls with human-like delays after condition is met.
     * @param condition The condition to check
     * @param timeout Maximum time to wait in milliseconds
     * @return true if condition was met, false if timeout
     */
    boolean pollFramesHuman(BooleanSupplier condition, int timeout);

    // ───────────────────────────────────────────────────────────────────────────
    // Script Control
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Stops the script execution.
     */
    void stop();

    /**
     * Increments the bones processed counter by 1 (for metrics).
     */
    void incrementBonesProcessed();

    /**
     * Increments the bones processed counter by a specified amount (for metrics).
     * @param count The number of bones to add to the counter
     */
    void incrementBonesProcessed(int count);

    /**
     * Returns the underlying Script instance for utilities that require it.
     * Used by JorkBank and other Script-dependent utilities.
     * @return The Script instance
     */
    Script getScript();

    // ───────────────────────────────────────────────────────────────────────────
    // Player Stats
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Gets the cached player agility level.
     * @return agility level (1 if detection failed, -1 if not yet detected)
     */
    int getPlayerAgilityLevel();

    /**
     * Checks if player can use the agility shortcut (level 58+).
     * @return true if shortcut is available
     */
    boolean canUseAgilityShortcut();
}
