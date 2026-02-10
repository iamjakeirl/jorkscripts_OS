package com.jork.script.fireGiantWaterStrike.config;

/**
 * Immutable configuration record for the Fire Giant Water Strike script.
 * Built by ScriptOptions UI and consumed by the main script class.
 */
public record CombatConfig(
    LootMode lootMode,
    FoodType foodType,
    boolean xpFailsafeEnabled,
    int xpFailsafeTimeoutMinutes,
    boolean xpFailsafePauseDuringLogout,
    boolean debugLogging
) {

    /**
     * Returns a default configuration suitable for typical usage.
     *
     * @return default CombatConfig with TELEGRAB loot, LOBSTER food, failsafe on (5 min), pause on, debug off
     */
    public static CombatConfig getDefault() {
        return new CombatConfig(LootMode.TELEGRAB, FoodType.LOBSTER, true, 5, true, false);
    }
}
