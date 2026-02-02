package com.jork.script.Ectofuntus.utils;

import com.jork.script.Ectofuntus.EctofuntusConstants;
import com.jork.script.Ectofuntus.EctofuntusScript;
import com.jork.script.Ectofuntus.config.BoneType;
import com.jork.script.Ectofuntus.config.EctoConfig;
import com.jork.utils.ExceptionUtils;
import com.jork.utils.ScriptLogger;
import com.jork.utils.teleport.TeleportHandler;
import com.jork.utils.teleport.TeleportHandlerFactory;
import com.osmb.api.item.ItemGroupResult;
import com.osmb.api.location.position.types.WorldPosition;

import java.util.Set;

/**
 * Centralized state detection utility for the Ectofuntus script.
 * Answers "where am I and what do I have?" by examining player location and inventory holistically.
 *
 * Purpose: Enable context-aware recovery decisions. A centralized utility allows consistent state
 * detection across the script, supporting smart recovery from edge cases (logout, partial inventory,
 * unexpected location).
 *
 * Key insight from CONTEXT.md: "bone count + bone dust count together represent 'bones processed'"
 *
 * @author jork
 */
public class StateDetector {

    private final EctofuntusScript script;

    // Cached teleport handler for bank location detection
    private TeleportHandler cachedBankHandler;

    /**
     * Creates a state detector for the given script.
     *
     * @param script The Ectofuntus script instance
     */
    public StateDetector(EctofuntusScript script) {
        this.script = script;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Main Detection Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Detects the player's current location state.
     * Uses coordinate-based detection with plane checks for robust location determination.
     *
     * @return The current location state
     */
    public LocationState detectCurrentLocation() {
        WorldPosition pos = script.getWorldPosition();
        if (pos == null) {
            ScriptLogger.debug(script.getScript(), "StateDetector: Position unavailable - UNKNOWN location");
            return LocationState.UNKNOWN;
        }

        // Check bank locations first (cheapest - just distance/region)
        if (isNearBank(pos)) {
            ScriptLogger.debug(script.getScript(), "StateDetector: AT_BANK");
            return LocationState.AT_BANK;
        }

        // Check ectofuntus locations (region + distance)
        int plane = pos.getPlane();

        // Check altar (region-based)
        if (EctofuntusConstants.isNearAltar(pos)) {
            ScriptLogger.info(script.getScript(),
                "StateDetector: Near altar check passed - pos=" + pos +
                    ", plane=" + pos.getPlane() +
                    ", region=" + pos.getRegionID() +
                    ", altarPos=" + EctofuntusConstants.ECTOFUNTUS_ALTAR_POS +
                    ", distance=" + pos.distanceTo(EctofuntusConstants.ECTOFUNTUS_ALTAR_POS));
            ScriptLogger.debug(script.getScript(), "StateDetector: AT_ALTAR");
            return LocationState.AT_ALTAR;
        }

        // Check basement (slime dungeon region)
        if (EctofuntusConstants.isInSlimeDungeon(pos)) {
            ScriptLogger.debug(script.getScript(), "StateDetector: AT_BASEMENT");
            return LocationState.AT_BASEMENT;
        }

        // Check grinder (plane 1)
        if (plane == EctofuntusConstants.GRINDER_PLANE && isNearGrinder(pos)) {
            ScriptLogger.debug(script.getScript(), "StateDetector: AT_GRINDER");
            return LocationState.AT_GRINDER;
        }

        ScriptLogger.debug(script.getScript(), "StateDetector: UNKNOWN location (plane=" + plane + ", pos=" + pos + ")");
        return LocationState.UNKNOWN;
    }

    /**
     * Detects the player's current inventory state.
     * Treats bones + bone_dust together as "progress toward worship", not separate counters.
     *
     * @return The current inventory state
     */
    public InventoryState detectInventoryState() {
        EctoConfig config = script.getConfig();
        if (config == null) {
            ScriptLogger.debug(script.getScript(), "StateDetector: Config unavailable - UNKNOWN inventory");
            return InventoryState.UNKNOWN;
        }

        // Get inventory counts
        int bones = getTotalBoneCount();
        int boneDust = getBonemealCount();
        int slime = getInventoryCount(EctofuntusConstants.BUCKET_OF_SLIME);
        int emptyPots = getInventoryCount(EctofuntusConstants.EMPTY_POT);
        int emptyBuckets = getInventoryCount(EctofuntusConstants.EMPTY_BUCKET);

        // Key insight: totalProgress = bones in any form (raw or processed)
        int totalProgress = bones + boneDust;
        int canWorship = Math.min(boneDust, slime);  // Limited by whichever is lower

        ScriptLogger.debug(script.getScript(), "StateDetector: Inventory - bones=" + bones +
            (config.isUseAllBonesInTab() ? " (mixed mode)" : "") +
            ", boneDust=" + boneDust + ", slime=" + slime +
            ", emptyPots=" + emptyPots + ", emptyBuckets=" + emptyBuckets);

        int baseline = script.getSupplyBaseline();
        if (baseline <= 0) {
            baseline = 8;
        }

        // Full inventory ready to worship (all processed, no raw bones left)
        if (canWorship >= baseline && bones == 0) {
            ScriptLogger.debug(script.getScript(), "StateDetector: READY_TO_WORSHIP");
            return InventoryState.READY_TO_WORSHIP;
        }

        // Partial worship progress (some dust + slime, but not full)
        if (boneDust > 0 && slime > 0) {
            ScriptLogger.debug(script.getScript(), "StateDetector: PARTIAL_WORSHIP_READY");
            return InventoryState.PARTIAL_WORSHIP_READY;
        }

        // Have unprocessed bones and empty containers
        if (bones > 0 && emptyPots > 0 && emptyBuckets > 0) {
            ScriptLogger.debug(script.getScript(), "StateDetector: NEED_PROCESSING");
            return InventoryState.NEED_PROCESSING;
        }

        // Have bones but missing containers
        if (bones > 0 && (emptyPots == 0 || emptyBuckets == 0)) {
            ScriptLogger.debug(script.getScript(), "StateDetector: NEED_RESTOCK");
            return InventoryState.NEED_RESTOCK;
        }

        // Have dust but no slime (and have empty buckets to fill)
        if (boneDust > 0 && slime == 0 && emptyBuckets > 0) {
            ScriptLogger.debug(script.getScript(), "StateDetector: NEED_SLIME_ONLY");
            return InventoryState.NEED_SLIME_ONLY;
        }

        // Have slime but no dust (and have bones + empty pots to process)
        if (slime > 0 && boneDust == 0 && bones > 0 && emptyPots > 0) {
            ScriptLogger.debug(script.getScript(), "StateDetector: NEED_DUST_ONLY");
            return InventoryState.NEED_DUST_ONLY;
        }

        // Empty or depleted state
        if (totalProgress == 0 && slime == 0) {
            ScriptLogger.debug(script.getScript(), "StateDetector: EMPTY_NEED_BANK");
            return InventoryState.EMPTY_NEED_BANK;
        }

        ScriptLogger.debug(script.getScript(), "StateDetector: UNKNOWN inventory state");
        return InventoryState.UNKNOWN;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Checks if the player is near a bank location.
     * Uses the configured bank teleport handler's destination area.
     *
     * @param pos The position to check
     * @return true if near bank
     */
    private boolean isNearBank(WorldPosition pos) {
        // Lazy-initialize bank handler for location checking
        if (cachedBankHandler == null) {
            EctoConfig config = script.getConfig();
            if (config == null) {
                return false;
            }

            cachedBankHandler = TeleportHandlerFactory.fromBankLocationName(
                script.getScript(),
                config.getBankLocation().getDisplayName()
            );

            if (cachedBankHandler == null) {
                ScriptLogger.debug(script.getScript(), "StateDetector: Could not create bank handler for location check");
                return false;
            }
        }

        return cachedBankHandler.isAtDestination(pos);
    }

    /**
     * Checks if the player is near the bone grinder.
     *
     * @param pos The position to check
     * @return true if near grinder
     */
    private boolean isNearGrinder(WorldPosition pos) {
        if (pos.getPlane() != EctofuntusConstants.GRINDER_PLANE) {
            return false;
        }
        return pos.distanceTo(EctofuntusConstants.BONE_HOPPER_POS) <= 10;
    }

    /**
     * Checks if the player is near the Pool of Slime.
     *
     * @param pos The position to check
     * @return true if near pool
     */
    private boolean isNearPool(WorldPosition pos) {
        return pos.distanceTo(EctofuntusConstants.POOL_OF_SLIME_POS) <= 10;
    }

    /**
     * Gets total bone count based on config mode.
     * If useAllBonesInTab is enabled, counts all bone types.
     * Otherwise counts only the configured bone type.
     */
    private int getTotalBoneCount() {
        EctoConfig config = script.getConfig();
        if (config == null) return 0;

        if (config.isUseAllBonesInTab()) {
            // Single search for all bone types to avoid double-counting
            try {
                var wm = script.getWidgetManager();
                if (wm == null || wm.getInventory() == null) return 0;
                java.util.Set<Integer> allBoneIds = BoneType.getAllItemIds();
                var search = wm.getInventory().search(allBoneIds);
                return search != null ? search.getAmount(allBoneIds) : 0;
            } catch (Exception e) {
                ExceptionUtils.rethrowIfTaskInterrupted(e);
                ScriptLogger.debug(script.getScript(), "Error counting bones: " + e.getMessage());
                return 0;
            }
        } else {
            // Count only configured bone type
            return getInventoryCount(config.getBoneType().getItemId());
        }
    }

    /**
     * Gets the count of bonemeal in inventory (all variants).
     * Uses a single search call to avoid double-counting visually identical items.
     */
    private int getBonemealCount() {
        try {
            var wm = script.getWidgetManager();
            if (wm == null || wm.getInventory() == null) {
                return 0;
            }
            var search = wm.getInventory().search(EctofuntusConstants.BONEMEAL_IDS);
            return search != null ? search.getAmount(EctofuntusConstants.BONEMEAL_IDS) : 0;
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.debug(script.getScript(), "StateDetector: Error counting bonemeal: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Gets the count of an item in inventory.
     * Handles exceptions gracefully and rethrows TaskInterruptedException.
     *
     * @param itemId The item ID to count
     * @return The count of the item, or 0 if unavailable
     */
    private int getInventoryCount(int itemId) {
        try {
            var wm = script.getWidgetManager();
            if (wm == null || wm.getInventory() == null) {
                return 0;
            }
            ItemGroupResult search = wm.getInventory().search(Set.of(itemId));
            return search != null ? search.getAmount(itemId) : 0;
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.debug(script.getScript(), "StateDetector: Error counting item " + itemId + ": " + e.getMessage());
            return 0;
        }
    }

}
