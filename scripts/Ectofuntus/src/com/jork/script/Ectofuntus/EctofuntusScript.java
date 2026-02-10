package com.jork.script.Ectofuntus;

import com.jork.script.Ectofuntus.config.EctoConfig;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.scene.ObjectManager;
import com.osmb.api.script.Script;
import com.osmb.api.ui.WidgetManager;

import java.util.function.BooleanSupplier;

/**
 * Interface used by Ectofuntus tasks to access shared script behavior.
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
    // Baseline Tracking
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Gets the supply baseline after banking.
     */
    int getSupplyBaseline();

    /**
     * Sets the supply baseline after banking.
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
     * Polls until a condition is met or timeout expires.
     * @param condition The condition to check
     * @param timeout Maximum time to wait in milliseconds
     * @param ignoreTasks When true, suppresses break/hop/afk interruptions
     * @return true if condition was met, false if timeout
     */
    boolean pollFramesUntil(BooleanSupplier condition, int timeout, boolean ignoreTasks);

    /**
     * Polls with human-like delays after condition is met.
     * @param condition The condition to check
     * @param timeout Maximum time to wait in milliseconds
     * @return true if condition was met, false if timeout
     */
    boolean pollFramesHuman(BooleanSupplier condition, int timeout);

    /**
     * Polls with human-like delays after condition is met.
     * @param condition The condition to check
     * @param timeout Maximum time to wait in milliseconds
     * @param ignoreTasks When true, suppresses break/hop/afk interruptions
     * @return true if condition was met, false if timeout
     */
    boolean pollFramesHuman(BooleanSupplier condition, int timeout, boolean ignoreTasks);

    // ───────────────────────────────────────────────────────────────────────────
    // Script Control
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Stops the script execution.
     */
    void stop();

    /**
     * Increments the bones processed counter by a specified amount (for metrics).
     * @param count The number of bones to add to the counter
     */
    void incrementBonesProcessed(int count);

    /**
     * Increments the ecto tokens gained counter (for metrics).
     * @param count The number of tokens to add to the counter
     */
    void incrementEctoTokens(int count);

    /**
     * Resets task state machines to their initial states.
     */
    void resetCycleTaskStates();

    /**
     * Returns the underlying Script instance.
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

    // ───────────────────────────────────────────────────────────────────────────
    // Ecto Token Collection
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Checks if tokens should be collected (set after worship completes).
     */
    boolean shouldCollectTokens();

    /**
     * Sets the token collection flag.
     * @param shouldCollect true after worship, false after successful collection
     */
    void setShouldCollectTokens(boolean shouldCollect);

    /**
     * Gets the number of ecto tokens gained since the last collection.
     */
    int getTokensSinceLastCollection();

    /**
     * Gets the current token collection threshold.
     */
    int getTokenCollectionThreshold();

    /**
     * Marks tokens as collected - resets the "since last collection" counter
     * and picks a new randomized threshold.
     */
    void markTokensCollected();
}
