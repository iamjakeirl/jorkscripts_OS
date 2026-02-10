package com.jork.script.Ectofuntus.utils;

import com.jork.script.Ectofuntus.EctofuntusConstants;
import com.jork.script.Ectofuntus.EctofuntusScript;
import com.jork.script.Ectofuntus.config.BoneType;
import com.jork.script.Ectofuntus.config.EctoConfig;
import com.jork.utils.ScriptLogger;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.scene.RSObject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects current location and inventory state for recovery and task routing.
 */
public class StateDetector {

    private static final Set<String> BANK_OBJECT_NAMES = Set.of(
        "Bank booth", "Bank chest", "Grand Exchange booth", "Bank", "Chest"
    );
    private static final Set<String> BANK_ACTIONS = Set.of("Bank", "Use", "Open");

    private final EctofuntusScript script;

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

        Set<Integer> boneIdsForCount = config.isUseAllBonesInTab()
            ? BoneType.getAllItemIds()
            : Set.of(config.getBoneType().getItemId());
        Set<Integer> inventoryItemIds = new HashSet<>(boneIdsForCount);
        inventoryItemIds.addAll(EctofuntusConstants.BONEMEAL_IDS);
        inventoryItemIds.add(EctofuntusConstants.BUCKET_OF_SLIME);
        inventoryItemIds.add(EctofuntusConstants.EMPTY_POT);
        inventoryItemIds.add(EctofuntusConstants.EMPTY_BUCKET);

        var inventorySnapshot = InventoryQueries.snapshot(
            script,
            inventoryItemIds,
            "StateDetector: Error taking inventory snapshot: "
        );

        int bones = InventoryQueries.amount(inventorySnapshot, boneIdsForCount);
        int boneDust = InventoryQueries.amount(inventorySnapshot, EctofuntusConstants.BONEMEAL_IDS);
        int slime = InventoryQueries.amount(inventorySnapshot, EctofuntusConstants.BUCKET_OF_SLIME);
        int emptyPots = InventoryQueries.amount(inventorySnapshot, EctofuntusConstants.EMPTY_POT);
        int emptyBuckets = InventoryQueries.amount(inventorySnapshot, EctofuntusConstants.EMPTY_BUCKET);

        // Total progress includes both raw bones and bonemeal.
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
     * Checks if a reachable bank object exists in the loaded scene.
     *
     * @param pos The player's current position (unused, kept for signature compat)
     * @return true if a reachable bank object is nearby
     */
    private boolean isNearBank(WorldPosition pos) {
        if (script.getScript().getObjectManager() == null) {
            return false;
        }

        List<RSObject> bankObjects = script.getScript().getObjectManager().getObjects(obj -> {
            if (obj == null || obj.getName() == null) {
                return false;
            }
            String name = obj.getName();
            boolean isBankObject = BANK_OBJECT_NAMES.stream()
                .anyMatch(bankName -> name.equalsIgnoreCase(bankName));
            if (!isBankObject) {
                return false;
            }
            String[] actions = obj.getActions();
            if (actions == null) {
                return false;
            }
            boolean hasAction = Arrays.stream(actions)
                .filter(a -> a != null)
                .anyMatch(a -> BANK_ACTIONS.stream()
                    .anyMatch(bankAction -> a.equalsIgnoreCase(bankAction)));
            return hasAction && obj.canReach();
        });

        return bankObjects != null && !bankObjects.isEmpty();
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

}
