package com.jork.script.Ectofuntus.config;

/**
 * Configuration class for Ectofuntus script settings.
 * Holds user-selected options from the UI.
 *
 * @author jork
 */
public class EctoConfig {

    private final BoneType boneType;
    private final BankLocation bankLocation;
    private final boolean xpFailsafeEnabled;
    private final int xpFailsafeTimeoutMinutes;
    private final boolean xpFailsafePauseDuringLogout;
    private final boolean debugLogging;
    private final boolean useAllBonesInTab;
    private final boolean runePouchModeEnabled;

    public EctoConfig(
        BoneType boneType,
        BankLocation bankLocation,
        boolean xpFailsafeEnabled,
        int xpFailsafeTimeoutMinutes,
        boolean xpFailsafePauseDuringLogout,
        boolean debugLogging,
        boolean useAllBonesInTab,
        boolean runePouchModeEnabled
    ) {
        this.boneType = boneType;
        this.bankLocation = bankLocation;
        this.xpFailsafeEnabled = xpFailsafeEnabled;
        this.xpFailsafeTimeoutMinutes = xpFailsafeTimeoutMinutes;
        this.xpFailsafePauseDuringLogout = xpFailsafePauseDuringLogout;
        this.debugLogging = debugLogging;
        this.useAllBonesInTab = useAllBonesInTab;
        this.runePouchModeEnabled = runePouchModeEnabled;
    }

    /**
     * Returns a default configuration for use when window is closed without confirming.
     */
    public static EctoConfig getDefault() {
        return new EctoConfig(
            BoneType.DRAGON_BONES,
            BankLocation.VARROCK,
            true,   // XP failsafe enabled
            5,      // 5 minute timeout
            true,   // Pause during logout
            false,  // Debug logging off
            false,  // Use all bones in tab off (single bone mode default)
            false   // Rune pouch mode disabled by default
        );
    }

    public BoneType getBoneType() {
        return boneType;
    }

    public BankLocation getBankLocation() {
        return bankLocation;
    }

    public boolean isXpFailsafeEnabled() {
        return xpFailsafeEnabled;
    }

    public int getXpFailsafeTimeoutMinutes() {
        return xpFailsafeTimeoutMinutes;
    }

    public boolean isXpFailsafePauseDuringLogout() {
        return xpFailsafePauseDuringLogout;
    }

    public boolean isDebugLogging() {
        return debugLogging;
    }

    /**
     * Returns true if script should use all bone types found in the bank tab,
     * rather than just the configured bone type.
     */
    public boolean isUseAllBonesInTab() {
        return useAllBonesInTab;
    }

    /**
     * Returns true if teleport runes are expected to come from rune pouch.
     */
    public boolean isRunePouchModeEnabled() {
        return runePouchModeEnabled;
    }

    @Override
    public String toString() {
        return "EctoConfig{" +
            "boneType=" + boneType +
            ", bankLocation=" + bankLocation +
            ", xpFailsafe=" + xpFailsafeEnabled +
            " (" + xpFailsafeTimeoutMinutes + "m)" +
            ", debugLog=" + debugLogging +
            ", useAllBones=" + useAllBonesInTab +
            ", runePouchMode=" + runePouchModeEnabled +
            '}';
    }
}
