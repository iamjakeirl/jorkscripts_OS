package com.jork.script.Ectofuntus.tasks;

import com.jork.script.Ectofuntus.EctofuntusConstants;
import com.jork.script.Ectofuntus.EctofuntusScript;
import com.jork.script.Ectofuntus.config.BoneType;
import com.jork.script.Ectofuntus.config.BankLocation;
import com.jork.script.Ectofuntus.config.EctoConfig;
import com.jork.utils.ExceptionUtils;
import com.jork.utils.JorkBank;
import com.jork.utils.JorkBank.OpenResult;
import com.jork.utils.JorkBank.TransactionResult;
import com.jork.utils.Navigation;
import com.jork.utils.ScriptLogger;
import com.jork.utils.teleport.TeleportHandler;
import com.jork.utils.teleport.TeleportHandlerFactory;
import com.jork.utils.teleport.TeleportResult;
import com.osmb.api.item.ItemGroupResult;
import com.osmb.api.utils.RandomUtils;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.scene.RSObject;
import com.osmb.api.walker.WalkConfig;

import java.awt.Point;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles banking operations for the Ectofuntus script.
 * Uses JorkBank utility for clean, reusable banking logic.
 * Priority: 1 (Highest)
 *
 * @author jork
 */
public class BankTask {

    // ───────────────────────────────────────────────────────────────────────────
    // Item ID Constants
    // ───────────────────────────────────────────────────────────────────────────
    private static final int ECTOPHIAL = 4251;
    private static final int EMPTY_POT = 1931;
    private static final int EMPTY_BUCKET = 1925;
    private static final int RUNE_POUCH = EctofuntusConstants.RUNE_POUCH;
    private static final int DIVINE_RUNE_POUCH = EctofuntusConstants.DIVINE_RUNE_POUCH;
    private static final Set<Integer> RUNE_POUCH_IDS = EctofuntusConstants.RUNE_POUCH_IDS;

    // ───────────────────────────────────────────────────────────────────────────
    // State Machine
    // ───────────────────────────────────────────────────────────────────────────
    private enum BankingState {
        TELEPORTING_TO_BANK,  // First: ensure we're at the bank
        OPENING_BANK,
        DEPOSITING_UNWANTED,
        CHECKING_SUPPLIES,
        WITHDRAWING_BONES,          // Custom amount (8) first — sets X quantity
        WITHDRAWING_POTS,           // Custom amount (8) — X cached, skips dialogue
        WITHDRAWING_BUCKETS,        // Custom amount (8) — X cached, skips dialogue
        WITHDRAWING_ECTOPHIAL,      // Standard amount (1) — after X items
        WITHDRAWING_TELEPORT,       // Standard amount (1)
        WITHDRAWING_TELEPORT_RUNES, // All
        VERIFYING_INVENTORY,
        COMPLETE
    }

    /** Maximum retries for any withdraw operation before fallback */
    private static final int MAX_WITHDRAW_RETRIES = 3;

    /** Maximum teleport failures before stopping script */
    private static final int MAX_TELEPORT_FAILURES = 3;
    /** How close we need to be to the walk target before stopping the walker */
    private static final int BANK_WALK_BREAK_DISTANCE = 3;
    /** Randomization radius for bank walking */
    private static final int BANK_WALK_RANDOM_RADIUS = 3;
    /** Distance threshold for "nearby bank" detection (tiles) */
    private static final int NEARBY_BANK_THRESHOLD = 10;
    /** Port Phasmatys gate area (Energy Barrier) */
    private static final RectangleArea PORT_PHASMATYS_BARRIER_AREA = new RectangleArea(3658, 3509, 3, 2, 0);
    /** Port Phasmatys bank area (inside the gate) */
    private static final RectangleArea PORT_PHASMATYS_BANK_AREA = new RectangleArea(3687, 3466, 4, 3, 0);
    private static final String ENERGY_BARRIER_NAME = "Energy Barrier";
    private static final String ENERGY_BARRIER_ACTION = "Pass";

    // ───────────────────────────────────────────────────────────────────────────
    // Instance Fields
    // ───────────────────────────────────────────────────────────────────────────
    private final EctofuntusScript script;
    private final JorkBank bank;
    private BankingState currentState = BankingState.TELEPORTING_TO_BANK;

    // Teleportation handling
    private TeleportHandler teleportHandler;
    private BankLocation bankLocation;
    private int teleportFailCount = 0;

    // Retry counters for fallback logic
    private int withdrawRetryCount = 0;

    public BankTask(EctofuntusScript script) {
        this.script = script;
        this.bank = new JorkBank(script.getScript());
    }

    /**
     * Check if this task can execute
     */
    public boolean canExecute() {
        return script.shouldBank();
    }

    /**
     * Execute the banking task
     * @return poll delay in milliseconds
     */
    public int execute() {
        switch (currentState) {
            case TELEPORTING_TO_BANK:
                return handleTeleportToBank();
            case OPENING_BANK:
                return handleOpenBank();
            case DEPOSITING_UNWANTED:
                return handleDepositUnwanted();
            case CHECKING_SUPPLIES:
                return handleCheckSupplies();
            case WITHDRAWING_ECTOPHIAL:
                return handleWithdrawEctophial();
            case WITHDRAWING_TELEPORT:
                return handleWithdrawTeleport();
            case WITHDRAWING_TELEPORT_RUNES:
                return handleWithdrawTeleportRunes();
            case WITHDRAWING_BONES:
                return handleWithdrawBones();
            case WITHDRAWING_POTS:
                return handleWithdrawPots();
            case WITHDRAWING_BUCKETS:
                return handleWithdrawBuckets();
            case VERIFYING_INVENTORY:
                return handleVerifyInventory();
            case COMPLETE:
                return handleComplete();
            default:
                ScriptLogger.error(script.getScript(), "Unknown banking state: " + currentState);
                currentState = BankingState.TELEPORTING_TO_BANK;
                return 0;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // State Handlers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Handles teleportation to the bank location.
     * Uses the teleport framework to get to the bank before opening it.
     */
    private int handleTeleportToBank() {
        // Initialize handler if needed
        if (teleportHandler == null) {
            EctoConfig config = script.getConfig();
            if (config == null) {
                ScriptLogger.error(script.getScript(), "Config not available for teleport handler");
                script.stop();
                return 0;
            }

            teleportHandler = TeleportHandlerFactory.fromBankLocationName(
                script.getScript(),
                config.getBankLocation().getDisplayName()
            );
            bankLocation = config.getBankLocation();

            if (teleportHandler == null) {
                ScriptLogger.error(script.getScript(), "Could not create teleport handler for: " +
                    config.getBankLocation().getDisplayName());
                script.stop();
                return 0;
            }

            ScriptLogger.info(script.getScript(), "Teleport handler initialized: " + teleportHandler.getName());
        }

        // Check if already at destination
        WorldPosition pos = script.getWorldPosition();
        if (pos != null && teleportHandler.isAtDestination(pos)) {
            ScriptLogger.info(script.getScript(), "Already at bank location - proceeding to open bank");
            teleportFailCount = 0;
            currentState = BankingState.OPENING_BANK;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(100, 500, 200, 60));
            return 0;
        }

        // Route based on handler type:
        // 1. Spell-based teleports (Varrock, Falador, Camelot) - cast spell, walk to area, JorkBank finds bank
        // 2. Walking handlers - just walk (with nearby bank fallback)

        if (teleportHandler.requiresItem()) {
            // Item-based teleport
            ScriptLogger.info(script.getScript(), "Teleporting to bank via " + teleportHandler.getName());
            TeleportResult result = teleportHandler.teleport();

            switch (result) {
                case SUCCESS:
                case ALREADY_AT_DESTINATION:
                    return walkToBankTarget("Teleport successful - walking to bank");

                case ITEM_NOT_FOUND:
                case NO_CHARGES:
                    if (isNearAnyBank()) {
                        ScriptLogger.info(script.getScript(), "Near a bank - opening nearby bank");
                        teleportFailCount = 0;
                        currentState = BankingState.OPENING_BANK;
                        script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(100, 500, 200, 60));
                        return 0;
                    }
                    ScriptLogger.error(script.getScript(), "Teleport item unavailable: " + result + " - stopping");
                    script.stop();
                    return 0;

                default:
                    teleportFailCount++;
                    if (teleportFailCount >= MAX_TELEPORT_FAILURES) {
                        ScriptLogger.error(script.getScript(), "Teleport failed " +
                            MAX_TELEPORT_FAILURES + " times - stopping");
                        script.stop();
                        return 0;
                    }
                    ScriptLogger.warning(script.getScript(), "Teleport failed (" + result +
                        "), attempt " + teleportFailCount + "/" + MAX_TELEPORT_FAILURES);
                    script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(400, 1500, 800, 200));
                    return 0;
            }
        } else if (teleportHandler.isSpellBased()) {
            // Spell-based teleport (Varrock, Falador, Camelot)
            // Flow: cast spell -> walk to walk target -> OPENING_BANK uses JorkBank.open() to find bank
            ScriptLogger.info(script.getScript(), "Casting " + teleportHandler.getName());
            TeleportResult result = teleportHandler.teleport();

            switch (result) {
                case SUCCESS:
                case ALREADY_AT_DESTINATION:
                    // Walk to the walk target area, then OPENING_BANK state will use
                    // JorkBank.open() which calls findBankObject() to locate the actual bank
                    return walkToBankTarget("Spell cast successful - walking to bank area");

                case ITEM_NOT_FOUND:
                    // For spells, this means spell not available (wrong spellbook or no runes)
                    boolean nearby = isNearAnyBank();
                    ScriptLogger.debug(script.getScript(),
                        "Spell teleport unavailable - nearby bank check: " + nearby);
                    if (nearby) {
                        ScriptLogger.info(script.getScript(), "Near a bank - opening nearby bank");
                        teleportFailCount = 0;
                        currentState = BankingState.OPENING_BANK;
                        script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(100, 500, 200, 60));
                        return 0;
                    }
                    ScriptLogger.error(script.getScript(), "Spell unavailable (wrong spellbook or no runes) - stopping");
                    script.stop();
                    return 0;

                default:
                    teleportFailCount++;
                    if (teleportFailCount >= MAX_TELEPORT_FAILURES) {
                        ScriptLogger.error(script.getScript(), "Spell cast failed " +
                            MAX_TELEPORT_FAILURES + " times - stopping");
                        script.stop();
                        return 0;
                    }
                    ScriptLogger.warning(script.getScript(), "Spell cast failed (" + result +
                        "), attempt " + teleportFailCount + "/" + MAX_TELEPORT_FAILURES);
                    script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(400, 1500, 800, 200));
                    return 0;
            }
        } else {
            // Walking handler - check for nearby bank fallback first
            if (isNearAnyBank()) {
                ScriptLogger.info(script.getScript(), "Near a bank - using nearby bank instead of walking to configured location");
                teleportFailCount = 0;
                currentState = BankingState.OPENING_BANK;
                script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(100, 500, 200, 60));
                return 0;
            }

            // No nearby bank, must walk to configured destination
            return walkToBankTarget("Walking to " + teleportHandler.getDestination().getName());
        }
    }

    private int walkToBankTarget(String logMessage) {
        if (bankLocation == BankLocation.WALK_PORT_PHASMATYS) {
            return walkToPortPhasmatysBank(logMessage);
        }

        WorldPosition target = teleportHandler.getDestination().getWalkTarget();
        if (target == null) {
            ScriptLogger.warning(script.getScript(), "No walk target configured for bank destination");
            currentState = BankingState.OPENING_BANK;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(200, 700, 350, 80));
            return 0;
        }

        ScriptLogger.navigation(script.getScript(), logMessage);

        WalkConfig walkConfig = new WalkConfig.Builder()
            .tileRandomisationRadius(BANK_WALK_RANDOM_RADIUS)
            .breakDistance(BANK_WALK_BREAK_DISTANCE)
            .build();

        boolean arrived = script.getScript().getWalker().walkTo(target, walkConfig);

        // Re-check if we arrived (or are still within destination area)
        WorldPosition newPos = script.getWorldPosition();
        if (arrived || (newPos != null && teleportHandler.isAtDestination(newPos))) {
            ScriptLogger.info(script.getScript(), "Arrived at bank location");
            teleportFailCount = 0;
            currentState = BankingState.OPENING_BANK;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(200, 700, 350, 80));
            return 0;
        }

        // Walking failed - retry with limit
        teleportFailCount++;
        if (teleportFailCount >= MAX_TELEPORT_FAILURES) {
            ScriptLogger.error(script.getScript(), "Failed to walk to bank after " +
                MAX_TELEPORT_FAILURES + " attempts - stopping");
            script.stop();
            return 0;
        }

        ScriptLogger.warning(script.getScript(), "Walk attempt " + teleportFailCount +
            "/" + MAX_TELEPORT_FAILURES + " incomplete - retrying");
        script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(300, 1200, 600, 150));
        return 0;
    }

    /**
     * Walks to Port Phasmatys bank, handling the Energy Barrier obstacle.
     *
     * Poll patterns:
     * - pollFramesUntil for position in bank area after barrier pass -- CORRECT, pure position verification
     * - pollFramesUntil for position in bank area via obstacle handler -- CORRECT, pure position verification
     * All banking interactions are delegated to JorkBank utility (which handles its own polls correctly).
     */
    private int walkToPortPhasmatysBank(String logMessage) {
        ScriptLogger.navigation(script.getScript(), logMessage);

        WalkConfig.Builder configBuilder = new WalkConfig.Builder()
            .tileRandomisationRadius(BANK_WALK_RANDOM_RADIUS)
            .breakDistance(BANK_WALK_BREAK_DISTANCE);

        Navigation nav = new Navigation(script.getScript());
        // Step 1: walk to the gate area first
        boolean atGate = nav.navigateTo(PORT_PHASMATYS_BARRIER_AREA, configBuilder.build());
        if (atGate || PORT_PHASMATYS_BARRIER_AREA.contains(script.getWorldPosition())) {
            RSObject barrier = findEnergyBarrier();
            if (barrier != null) {
                ScriptLogger.info(script.getScript(), "Passing Port Phasmatys Energy Barrier");
                boolean interacted = barrier.interact(ENERGY_BARRIER_ACTION);
                if (interacted) {
                    script.pollFramesUntil(
                        () -> PORT_PHASMATYS_BANK_AREA.contains(script.getWorldPosition()),
                        RandomUtils.uniformRandom(2500, 4000)
                    );
                }
            }
        }

        // Step 2: walk from gate to bank area
        boolean arrived = nav.navigateTo(
            PORT_PHASMATYS_BANK_AREA,
            List.of(new Navigation.Obstacle() {
                @Override
                public RectangleArea getArea() {
                    return PORT_PHASMATYS_BARRIER_AREA;
                }

                @Override
                public boolean needsHandling() {
                    return findEnergyBarrier() != null;
                }

                @Override
                public boolean handle() {
                    RSObject barrier = findEnergyBarrier();
                    if (barrier == null) {
                        return false;
                    }

                    ScriptLogger.info(script.getScript(), "Passing Port Phasmatys Energy Barrier");
                    boolean interacted = barrier.interact(ENERGY_BARRIER_ACTION);
                    if (!interacted) {
                        return false;
                    }

                    return script.pollFramesUntil(
                        () -> PORT_PHASMATYS_BANK_AREA.contains(script.getWorldPosition()),
                        RandomUtils.uniformRandom(2500, 4000)
                    );
                }
            }),
            null,
            RandomUtils.uniformRandom(18000, 28000),
            configBuilder
        );

        if (arrived || PORT_PHASMATYS_BANK_AREA.contains(script.getWorldPosition())) {
            ScriptLogger.info(script.getScript(), "Arrived at Port Phasmatys bank");
            teleportFailCount = 0;
            currentState = BankingState.OPENING_BANK;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(200, 700, 350, 80));
            return 0;
        }

        teleportFailCount++;
        if (teleportFailCount >= MAX_TELEPORT_FAILURES) {
            ScriptLogger.error(script.getScript(), "Failed to walk to bank after " +
                MAX_TELEPORT_FAILURES + " attempts - stopping");
            script.stop();
            return 0;
        }

        ScriptLogger.warning(script.getScript(), "Walk attempt " + teleportFailCount +
            "/" + MAX_TELEPORT_FAILURES + " incomplete - retrying");
        script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(300, 1200, 600, 150));
        return 0;
    }

    private RSObject findEnergyBarrier() {
        if (script.getObjectManager() == null) {
            return null;
        }

        // Prefer barrier closest to the gate area center (area is just outside the barrier)
        Point gateCenter = PORT_PHASMATYS_BARRIER_AREA.getCenter();
        RSObject inGateArea = script.getObjectManager().getRSObject(obj -> {
            if (obj.getName() == null || !obj.getName().equalsIgnoreCase(ENERGY_BARRIER_NAME)) {
                return false;
            }
            if (!obj.canReach()) {
                return false;
            }
            if (!hasAction(obj, ENERGY_BARRIER_ACTION)) {
                return false;
            }
            WorldPosition objPos = obj.getWorldPosition();
            if (objPos == null) {
                return false;
            }
            WorldPosition gateCenterPos = new WorldPosition(gateCenter.x, gateCenter.y, objPos.getPlane());
            return objPos.distanceTo(gateCenterPos) <= 3;
        });
        if (inGateArea != null) {
            return inGateArea;
        }

        return script.getObjectManager().getRSObject(obj ->
            obj.getName() != null &&
                obj.getName().equalsIgnoreCase(ENERGY_BARRIER_NAME) &&
                obj.canReach() &&
                hasAction(obj, ENERGY_BARRIER_ACTION)
        );
    }

    private boolean isNearAnyBank() {
        return bank.isBankNearby(NEARBY_BANK_THRESHOLD);
    }

    private boolean hasAction(RSObject obj, String action) {
        String[] actions = obj.getActions();
        if (actions == null) {
            return false;
        }

        for (String objAction : actions) {
            if (objAction != null && objAction.equalsIgnoreCase(action)) {
                return true;
            }
        }

        return false;
    }

    private int handleOpenBank() {
        OpenResult result = bank.open();

        switch (result) {
            case SUCCESS:
            case ALREADY_OPEN:
                ScriptLogger.info(script.getScript(), "Bank is open - depositing unwanted items");
                currentState = BankingState.DEPOSITING_UNWANTED;
                script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(100, 500, 200, 60));
                return 0;

            case NO_BANK_FOUND:
                ScriptLogger.warning(script.getScript(), "No bank found nearby - re-attempting teleport");
                currentState = BankingState.TELEPORTING_TO_BANK;
                script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(300, 1200, 600, 150));
                return 0;

            case INTERACTION_FAILED:
                ScriptLogger.warning(script.getScript(), "Bank interaction failed - retrying");
                script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(300, 1200, 600, 150));
                return 0;

            case TIMEOUT:
                ScriptLogger.warning(script.getScript(), "Bank open timeout - retrying");
                script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(400, 1500, 800, 200));
                return 0;

            default:
                ScriptLogger.error(script.getScript(), "Unexpected bank open result: " + result);
                return 0;
        }
    }

    private int handleDepositUnwanted() {
        if (!bank.isOpen()) {
            ScriptLogger.warning(script.getScript(), "Bank closed unexpectedly");
            currentState = BankingState.OPENING_BANK;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(300, 1200, 600, 150));
            return 0;
        }

        EctoConfig config = script.getConfig();
        if (config == null) {
            ScriptLogger.error(script.getScript(), "Config not available");
            script.stop();
            return 0;
        }

        boolean skipSpellRuneChecks = config.isRunePouchModeEnabled() && isSpellBankTeleport(config);
        Set<Integer> requiredItems = (!skipSpellRuneChecks && teleportHandler != null)
            ? teleportHandler.getRequiredItemIds()
            : Collections.emptySet();

        // Build set of items to keep
        int[] keepItems;
        int baseItemCount = 3;  // ECTOPHIAL, EMPTY_POT, EMPTY_BUCKET

        if (config.isUseAllBonesInTab()) {
            // Keep all bone types when mixed mode enabled
            java.util.Set<Integer> allBoneIds = BoneType.getAllItemIds();
            int totalItems = baseItemCount + allBoneIds.size() + requiredItems.size();
            if (config.getBankLocation().requiresItem()) {
                totalItems++;
            }
            if (config.isRunePouchModeEnabled()) {
                totalItems += RUNE_POUCH_IDS.size();
            }

            keepItems = new int[totalItems];
            int idx = 0;
            keepItems[idx++] = ECTOPHIAL;
            keepItems[idx++] = EMPTY_POT;
            keepItems[idx++] = EMPTY_BUCKET;
            if (config.getBankLocation().requiresItem()) {
                keepItems[idx++] = config.getBankLocation().getItemId();
            }
            for (Integer itemId : requiredItems) {
                keepItems[idx++] = itemId;
            }
            if (config.isRunePouchModeEnabled()) {
                keepItems[idx++] = RUNE_POUCH;
                keepItems[idx++] = DIVINE_RUNE_POUCH;
            }
            for (Integer boneId : allBoneIds) {
                keepItems[idx++] = boneId;
            }
        } else {
            // Keep only configured bone type
            int boneId = config.getBoneType().getItemId();
            int totalItems = baseItemCount + 1 + requiredItems.size();
            if (config.getBankLocation().requiresItem()) {
                totalItems++;
            }
            if (config.isRunePouchModeEnabled()) {
                totalItems += RUNE_POUCH_IDS.size();
            }
            keepItems = new int[totalItems];
            int idx = 0;
            keepItems[idx++] = ECTOPHIAL;
            keepItems[idx++] = EMPTY_POT;
            keepItems[idx++] = EMPTY_BUCKET;
            if (config.getBankLocation().requiresItem()) {
                keepItems[idx++] = config.getBankLocation().getItemId();
            }
            for (Integer itemId : requiredItems) {
                keepItems[idx++] = itemId;
            }
            if (config.isRunePouchModeEnabled()) {
                keepItems[idx++] = RUNE_POUCH;
                keepItems[idx++] = DIVINE_RUNE_POUCH;
            }
            keepItems[idx++] = boneId;
        }

        ScriptLogger.info(script.getScript(), "Depositing unwanted items...");
        TransactionResult result = bank.depositAllExcept(keepItems);

        if (result == TransactionResult.SUCCESS) {
            ScriptLogger.info(script.getScript(), "Deposit complete");
            currentState = BankingState.CHECKING_SUPPLIES;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(150, 600, 250, 70));
            return 0;
        } else {
            ScriptLogger.warning(script.getScript(), "Deposit result: " + result + " - continuing anyway");
            currentState = BankingState.CHECKING_SUPPLIES;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(200, 700, 350, 80));
            return 0;
        }
    }

    private int handleCheckSupplies() {
        if (!bank.isOpen()) {
            ScriptLogger.warning(script.getScript(), "Bank closed unexpectedly");
            currentState = BankingState.OPENING_BANK;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(300, 1200, 600, 150));
            return 0;
        }

        EctoConfig config = script.getConfig();
        if (config == null) {
            ScriptLogger.error(script.getScript(), "Config not available");
            script.stop();
            return 0;
        }

        if (teleportHandler == null) {
            teleportHandler = TeleportHandlerFactory.fromBankLocationName(
                script.getScript(),
                config.getBankLocation().getDisplayName()
            );
        }

        boolean skipSpellRuneChecks = config.isRunePouchModeEnabled() && isSpellBankTeleport(config);
        int teleportItemId = -1;
        Set<Integer> requiredTeleportItems = Collections.emptySet();
        Set<Integer> inventoryItemIds = new HashSet<>();
        inventoryItemIds.add(ECTOPHIAL);
        inventoryItemIds.add(EMPTY_POT);
        inventoryItemIds.add(EMPTY_BUCKET);
        if (config.isRunePouchModeEnabled()) {
            inventoryItemIds.addAll(RUNE_POUCH_IDS);
        }

        if (config.getBankLocation().requiresItem()) {
            teleportItemId = config.getBankLocation().getItemId();
            inventoryItemIds.add(teleportItemId);
        }

        if (teleportHandler != null) {
            Set<Integer> handlerRequiredItems = teleportHandler.getRequiredItemIds();
            if (!skipSpellRuneChecks && handlerRequiredItems != null) {
                requiredTeleportItems = handlerRequiredItems;
                inventoryItemIds.addAll(requiredTeleportItems);
            }
        }

        if (config.isUseAllBonesInTab()) {
            inventoryItemIds.addAll(BoneType.getAllItemIds());
        } else {
            inventoryItemIds.add(config.getBoneType().getItemId());
        }

        ItemGroupResult inventorySnapshot = getInventorySnapshot(inventoryItemIds);

        // Check all required items (inventory OR bank - items may already be in inventory)
        if (getSnapshotAmount(inventorySnapshot, ECTOPHIAL) == 0 && !bank.contains(ECTOPHIAL)) {
            ScriptLogger.error(script.getScript(), "Ectophial not found in inventory or bank - stopping");
            script.stop();
            return 0;
        }

        if (teleportItemId > 0 && getSnapshotAmount(inventorySnapshot, teleportItemId) == 0 && !bank.contains(teleportItemId)) {
            ScriptLogger.error(script.getScript(), "Teleport item not found in inventory or bank - stopping");
            script.stop();
            return 0;
        }

        if (config.isRunePouchModeEnabled() && !hasRunePouchInSnapshot(inventorySnapshot)) {
            ScriptLogger.error(script.getScript(),
                "Rune pouch mode enabled but no rune pouch in inventory - stopping");
            script.stop();
            return 0;
        }

        for (Integer itemId : requiredTeleportItems) {
            if (getSnapshotAmount(inventorySnapshot, itemId) == 0 && !bank.contains(itemId)) {
                ScriptLogger.error(script.getScript(),
                    "Required teleport item not found in inventory or bank (ID: " + itemId + ") - stopping");
                script.stop();
                return 0;
            }
        }

        // Check bones - use mixed mode helper (check inventory + bank)
        boolean hasBonesAvailable;
        if (config.isUseAllBonesInTab()) {
            int inventoryBones = getSnapshotAmount(inventorySnapshot, BoneType.getAllItemIds());
            boolean bankHasBones = false;
            for (Integer boneId : BoneType.getAllItemIds()) {
                if (bank.contains(boneId)) {
                    bankHasBones = true;
                    break;
                }
            }
            hasBonesAvailable = inventoryBones > 0 || bankHasBones;
        } else {
            int configuredBoneId = config.getBoneType().getItemId();
            hasBonesAvailable = getSnapshotAmount(inventorySnapshot, configuredBoneId) > 0 || bank.contains(configuredBoneId);
        }

        if (!hasBonesAvailable) {
            if (config.isUseAllBonesInTab()) {
                ScriptLogger.error(script.getScript(), "No bone types found in inventory or bank - stopping");
            } else {
                ScriptLogger.error(script.getScript(), "No " + config.getBoneType().getDisplayName() + " found in inventory or bank - stopping");
            }
            script.stop();
            return 0;
        }

        if (getSnapshotAmount(inventorySnapshot, EMPTY_POT) == 0 && !bank.contains(EMPTY_POT)) {
            ScriptLogger.error(script.getScript(), "Empty pots not found in inventory or bank - stopping");
            script.stop();
            return 0;
        }

        if (getSnapshotAmount(inventorySnapshot, EMPTY_BUCKET) == 0 && !bank.contains(EMPTY_BUCKET)) {
            ScriptLogger.error(script.getScript(), "Empty buckets not found in inventory or bank - stopping");
            script.stop();
            return 0;
        }

        ScriptLogger.info(script.getScript(), "Supply check passed - withdrawing items");
        currentState = BankingState.WITHDRAWING_BONES;
        script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(100, 500, 200, 60));
        return 0;
    }

    private int handleWithdrawEctophial() {
        if (!bank.isOpen()) {
            currentState = BankingState.OPENING_BANK;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(300, 1200, 600, 150));
            return 0;
        }

        // Already have it - skip immediately
        if (getInventoryCount(ECTOPHIAL) > 0) {
            resetRetryCount();
            currentState = BankingState.WITHDRAWING_TELEPORT;
            return 0;
        }

        TransactionResult result = bank.withdraw(ECTOPHIAL, 1);

        if (result == TransactionResult.SUCCESS) {
            resetRetryCount();
            currentState = BankingState.WITHDRAWING_TELEPORT;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(100, 500, 200, 60));
            return 0;
        } else if (result == TransactionResult.ITEM_NOT_FOUND) {
            ScriptLogger.error(script.getScript(), "Ectophial not found in bank - stopping");
            script.stop();
            return 0;
        } else {
            return handleWithdrawRetry("Ectophial", ECTOPHIAL);
        }
    }

    private int handleWithdrawTeleport() {
        EctoConfig config = script.getConfig();
        if (config == null) {
            ScriptLogger.error(script.getScript(), "Config not available");
            script.stop();
            return 0;
        }

        // Skip if teleport not required for this banking method
        if (!config.getBankLocation().requiresItem()) {
            resetRetryCount();
            currentState = BankingState.WITHDRAWING_TELEPORT_RUNES;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(100, 500, 200, 60));
            return 0;
        }

        if (!bank.isOpen()) {
            currentState = BankingState.OPENING_BANK;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(300, 1200, 600, 150));
            return 0;
        }

        int teleportId = config.getBankLocation().getItemId();

        // Already have it - skip immediately
        if (getInventoryCount(teleportId) > 0) {
            resetRetryCount();
            currentState = BankingState.WITHDRAWING_TELEPORT_RUNES;
            return 0;
        }

        TransactionResult result = bank.withdraw(teleportId, 1);

        if (result == TransactionResult.SUCCESS) {
            resetRetryCount();
            currentState = BankingState.WITHDRAWING_TELEPORT_RUNES;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(100, 500, 200, 60));
            return 0;
        } else if (result == TransactionResult.ITEM_NOT_FOUND) {
            ScriptLogger.error(script.getScript(), "Teleport item (" + config.getBankLocation().getDisplayName() + ") not found - stopping");
            script.stop();
            return 0;
        } else {
            return handleWithdrawRetry("Teleport item", teleportId);
        }
    }

    private int handleWithdrawTeleportRunes() {
        if (!bank.isOpen()) {
            currentState = BankingState.OPENING_BANK;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(300, 1200, 600, 150));
            return 0;
        }

        EctoConfig config = script.getConfig();
        if (config == null) {
            ScriptLogger.error(script.getScript(), "Config not available");
            script.stop();
            return 0;
        }

        if (teleportHandler == null) {
            teleportHandler = TeleportHandlerFactory.fromBankLocationName(
                script.getScript(),
                config.getBankLocation().getDisplayName()
            );
        }

        if (teleportHandler == null) {
            ScriptLogger.error(script.getScript(), "Teleport handler not available for rune withdraw");
            script.stop();
            return 0;
        }

        if (config.isRunePouchModeEnabled() && isSpellBankTeleport(config)) {
            if (!hasRunePouchInInventory()) {
                ScriptLogger.error(script.getScript(),
                    "Rune pouch mode enabled but no rune pouch in inventory - stopping");
                script.stop();
                return 0;
            }
            ScriptLogger.info(script.getScript(), "Rune pouch mode enabled - skipping teleport rune withdrawal");
            resetRetryCount();
            currentState = BankingState.VERIFYING_INVENTORY;
            return 0;
        }

        Set<Integer> requiredItems = teleportHandler.getRequiredItemIds();
        if (requiredItems.isEmpty()) {
            resetRetryCount();
            currentState = BankingState.VERIFYING_INVENTORY;
            return 0;
        }

        for (Integer itemId : requiredItems) {
            // Already have this rune - skip to next
            if (getInventoryCount(itemId) > 0) {
                continue;
            }

            TransactionResult result = bank.withdrawAll(itemId);
            if (result == TransactionResult.SUCCESS) {
                resetRetryCount();
                script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(100, 500, 200, 60));
                return 0;  // Continue withdrawing other runes next tick
            } else if (result == TransactionResult.ITEM_NOT_FOUND) {
                ScriptLogger.error(script.getScript(), "Required teleport item not found in bank (ID: " + itemId + ") - stopping");
                script.stop();
                return 0;
            } else {
                return handleWithdrawRetry("Teleport item", itemId);
            }
        }

        // All runes already in inventory - skip immediately
        resetRetryCount();
        currentState = BankingState.VERIFYING_INVENTORY;
        return 0;
    }

    private int handleWithdrawBones() {
        if (!bank.isOpen()) {
            currentState = BankingState.OPENING_BANK;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(300, 1200, 600, 150));
            return 0;
        }

        EctoConfig config = script.getConfig();
        if (config == null) {
            ScriptLogger.error(script.getScript(), "Config not available");
            script.stop();
            return 0;
        }

        int targetCount = getSuppliesPerType();
        int currentCount = getTotalBoneInventoryCount();

        if (currentCount >= targetCount) {
            resetRetryCount();
            currentState = BankingState.WITHDRAWING_POTS;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(100, 500, 200, 60));
            return 0;
        }

        // Find available bone type
        int boneId = findAvailableBoneId();
        if (boneId == -1) {
            ScriptLogger.error(script.getScript(), "No bones available in bank - stopping");
            script.stop();
            return 0;
        }

        int needed = targetCount - currentCount;
        ScriptLogger.debug(script.getScript(), "Withdrawing " + needed + " bones (ID: " + boneId + ")");

        TransactionResult result = bank.withdraw(boneId, needed);

        if (result == TransactionResult.SUCCESS) {
            // Check if we got enough - if not, try another bone type
            int newCount = getTotalBoneInventoryCount();
            if (newCount < targetCount && config.isUseAllBonesInTab()) {
                // Keep withdrawing from other bone types
                ScriptLogger.debug(script.getScript(), "Got " + newCount + "/" + targetCount + " bones, trying more types");
                script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(100, 500, 200, 60));
                return 0;  // Loop back for more
            }
            resetRetryCount();
            currentState = BankingState.WITHDRAWING_POTS;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(100, 500, 200, 60));
            return 0;
        } else if (result == TransactionResult.ITEM_NOT_FOUND) {
            if (config.isUseAllBonesInTab()) {
                // In mixed mode, try the next bone type
                ScriptLogger.debug(script.getScript(), "Bone type " + boneId + " not found, trying next");
                script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(100, 500, 200, 60));
                return 0;  // Will find next bone type on retry
            }
            ScriptLogger.error(script.getScript(), "Bones (" + config.getBoneType().getDisplayName() + ") not found in bank - stopping");
            script.stop();
            return 0;
        } else {
            return handleWithdrawRetry("Bones", boneId);
        }
    }

    private int handleWithdrawPots() {
        if (!bank.isOpen()) {
            currentState = BankingState.OPENING_BANK;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(300, 1200, 600, 150));
            return 0;
        }

        int targetCount = getSuppliesPerType();
        int currentCount = getInventoryCount(EMPTY_POT);

        if (currentCount >= targetCount) {
            resetRetryCount();
            currentState = BankingState.WITHDRAWING_BUCKETS;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(100, 500, 200, 60));
            return 0;
        }

        int needed = targetCount - currentCount;
        ScriptLogger.debug(script.getScript(), "Withdrawing " + needed + " empty pots");

        TransactionResult result = bank.withdraw(EMPTY_POT, needed);

        if (result == TransactionResult.SUCCESS) {
            resetRetryCount();
            currentState = BankingState.WITHDRAWING_BUCKETS;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(100, 500, 200, 60));
            return 0;
        } else if (result == TransactionResult.ITEM_NOT_FOUND) {
            ScriptLogger.error(script.getScript(), "Empty pots not found in bank - stopping");
            script.stop();
            return 0;
        } else {
            return handleWithdrawRetry("Empty pots", EMPTY_POT);
        }
    }

    private int handleWithdrawBuckets() {
        if (!bank.isOpen()) {
            currentState = BankingState.OPENING_BANK;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(300, 1200, 600, 150));
            return 0;
        }

        int targetCount = getSuppliesPerType();
        int currentCount = getInventoryCount(EMPTY_BUCKET);

        if (currentCount >= targetCount) {
            resetRetryCount();
            currentState = BankingState.WITHDRAWING_ECTOPHIAL;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(100, 500, 200, 60));
            return 0;
        }

        int needed = targetCount - currentCount;
        ScriptLogger.debug(script.getScript(), "Withdrawing " + needed + " empty buckets");

        TransactionResult result = bank.withdraw(EMPTY_BUCKET, needed);

        if (result == TransactionResult.SUCCESS) {
            resetRetryCount();
            currentState = BankingState.WITHDRAWING_ECTOPHIAL;
            script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(100, 500, 200, 60));
            return 0;
        } else if (result == TransactionResult.ITEM_NOT_FOUND) {
            ScriptLogger.error(script.getScript(), "Empty buckets not found in bank - stopping");
            script.stop();
            return 0;
        } else {
            return handleWithdrawRetry("Empty buckets", EMPTY_BUCKET);
        }
    }

    private int handleVerifyInventory() {
        EctoConfig config = script.getConfig();
        if (config == null) {
            ScriptLogger.error(script.getScript(), "Config not available");
            script.stop();
            return 0;
        }

        int targetCount = getSuppliesPerType();

        // Check final counts - use mixed mode helper for bones
        int finalBones = getTotalBoneInventoryCount();
        int finalPots = getInventoryCount(EMPTY_POT);
        int finalBuckets = getInventoryCount(EMPTY_BUCKET);
        int ectophialCount = getInventoryCount(ECTOPHIAL);
        int runePouchCount = getInventoryCount(RUNE_POUCH) + getInventoryCount(DIVINE_RUNE_POUCH);

        ScriptLogger.info(script.getScript(), String.format(
            "Inventory: Bones=%d/%d, Pots=%d/%d, Buckets=%d/%d, Ectophial=%d, RunePouch=%d",
            finalBones, targetCount, finalPots, targetCount, finalBuckets, targetCount, ectophialCount, runePouchCount));

        if (config.isRunePouchModeEnabled() && runePouchCount == 0) {
            ScriptLogger.error(script.getScript(),
                "Rune pouch mode enabled but no rune pouch in inventory - stopping");
            script.stop();
            return 0;
        }

        boolean verified = (finalBones >= targetCount &&
                           finalPots >= targetCount &&
                           finalBuckets >= targetCount &&
                           ectophialCount >= 1);

        if (!verified) {
            ScriptLogger.error(script.getScript(), "Inventory verification failed - stopping");
            script.stop();
            return 0;
        }

        ScriptLogger.info(script.getScript(), "Inventory verification successful");
        currentState = BankingState.COMPLETE;
        script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(100, 500, 200, 60));
        return 0;
    }

    private int handleComplete() {
        bank.close();
        ScriptLogger.info(script.getScript(), "Bank closed");

        // Set supply baseline for completion detection
        int baseline = getSuppliesPerType();
        script.setSupplyBaseline(baseline);
        ScriptLogger.debug(script.getScript(), "Supply baseline set to " + baseline);

        // Reset script state
        script.setShouldBank(false);
        script.setHasSlime(false);
        script.setHasBoneMeal(false);

        // Reset task state for next cycle
        currentState = BankingState.TELEPORTING_TO_BANK;
        teleportFailCount = 0;

        ScriptLogger.info(script.getScript(), "Banking complete - ready for Ectofuntus");
        script.pollFramesUntil(() -> false, RandomUtils.exponentialRandom(300, 500, 1500));
        return 0;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean isSpellBankTeleport(EctoConfig config) {
        if (teleportHandler != null) {
            return teleportHandler.isSpellBased();
        }
        if (config == null) {
            return false;
        }
        return config.getBankLocation() == BankLocation.VARROCK ||
            config.getBankLocation() == BankLocation.FALADOR ||
            config.getBankLocation() == BankLocation.CAMELOT;
    }

    private boolean hasRunePouchInInventory() {
        return getInventoryCount(RUNE_POUCH) > 0 || getInventoryCount(DIVINE_RUNE_POUCH) > 0;
    }

    private boolean hasRunePouchInSnapshot(ItemGroupResult snapshot) {
        return getSnapshotAmount(snapshot, RUNE_POUCH) > 0 ||
            getSnapshotAmount(snapshot, DIVINE_RUNE_POUCH) > 0;
    }

    /**
     * Gets total bone count in inventory based on config mode.
     * If useAllBonesInTab is enabled, counts all bone types.
     * Otherwise counts only the configured bone type.
     */
    private int getTotalBoneInventoryCount() {
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
            return getInventoryCount(config.getBoneType().getItemId());
        }
    }

    /**
     * Checks if bank contains any bone type based on config mode.
     * If useAllBonesInTab is enabled, checks for any bone type.
     * Otherwise checks only the configured bone type.
     */
    private boolean bankContainsAnyBones() {
        EctoConfig config = script.getConfig();
        if (config == null) return false;

        if (config.isUseAllBonesInTab()) {
            for (Integer boneId : BoneType.getAllItemIds()) {
                if (bank.contains(boneId)) {
                    return true;
                }
            }
            return false;
        } else {
            return bank.contains(config.getBoneType().getItemId());
        }
    }

    /**
     * Checks if any bones are available in inventory OR bank based on config mode.
     * If useAllBonesInTab is enabled, checks for any bone type.
     * Otherwise checks only the configured bone type.
     */
    private boolean hasAnyBonesAvailable() {
        EctoConfig config = script.getConfig();
        if (config == null) return false;

        if (config.isUseAllBonesInTab()) {
            for (Integer boneId : BoneType.getAllItemIds()) {
                if (getInventoryCount(boneId) > 0 || bank.contains(boneId)) {
                    return true;
                }
            }
            return false;
        } else {
            int boneId = config.getBoneType().getItemId();
            return getInventoryCount(boneId) > 0 || bank.contains(boneId);
        }
    }

    /**
     * Finds the first available bone type in bank for withdrawal.
     * If useAllBonesInTab is enabled, returns first bone type found.
     * Otherwise returns the configured bone type ID.
     * @return bone item ID, or -1 if no bones available
     */
    private int findAvailableBoneId() {
        EctoConfig config = script.getConfig();
        if (config == null) return -1;

        if (config.isUseAllBonesInTab()) {
            for (Integer boneId : BoneType.getAllItemIds()) {
                if (bank.contains(boneId)) {
                    return boneId;
                }
            }
            return -1;
        } else {
            int boneId = config.getBoneType().getItemId();
            return bank.contains(boneId) ? boneId : -1;
        }
    }

    /**
     * Calculates how many of each supply type to withdraw.
     * Ectofuntus always uses 8 supplies per type.
     */
    private int getSuppliesPerType() {
        return 8;
    }

    /**
     * Gets the count of an item in the inventory.
     * Uses script's WidgetManager directly for speed.
     */
    private int getInventoryCount(int itemId) {
        try {
            var wm = script.getWidgetManager();
            if (wm == null || wm.getInventory() == null) {
                return 0;
            }
            var search = wm.getInventory().search(Set.of(itemId));
            return search != null ? search.getAmount(itemId) : 0;
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.debug(script.getScript(), "Error counting item " + itemId + ": " + e.getMessage());
            return 0;
        }
    }

    private ItemGroupResult getInventorySnapshot(Set<Integer> itemIds) {
        try {
            var wm = script.getWidgetManager();
            if (wm == null || wm.getInventory() == null || itemIds == null || itemIds.isEmpty()) {
                return null;
            }
            return wm.getInventory().search(itemIds);
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.debug(script.getScript(), "Error taking inventory snapshot: " + e.getMessage());
            return null;
        }
    }

    private int getSnapshotAmount(ItemGroupResult snapshot, int itemId) {
        return snapshot != null ? snapshot.getAmount(itemId) : 0;
    }

    private int getSnapshotAmount(ItemGroupResult snapshot, Set<Integer> itemIds) {
        return snapshot != null ? snapshot.getAmount(itemIds) : 0;
    }

    /**
     * Handles retry with escalating log levels.
     * - First failure: WARNING (something went wrong)
     * - Middle retries: DEBUG (only if debug enabled)
     * - Final attempt: WARNING (about to give up)
     * - Exhausted: ERROR (stopping script)
     *
     * @param operation Description of what failed
     * @return true if retries exhausted (should stop), false if can continue
     */
    private boolean handleRetryWithEscalation(String operation) {
        withdrawRetryCount++;

        if (withdrawRetryCount == 1) {
            // First failure - warn user something went wrong
            ScriptLogger.warning(script.getScript(),
                operation + " failed, retrying (1/" + MAX_WITHDRAW_RETRIES + ")");
        } else if (withdrawRetryCount == MAX_WITHDRAW_RETRIES) {
            // Final attempt - warn user we're about to give up
            ScriptLogger.warning(script.getScript(),
                operation + " failed " + (MAX_WITHDRAW_RETRIES - 1) + " times, final attempt...");
        } else {
            // Middle retries - debug only
            ScriptLogger.debug(script.getScript(),
                operation + " failed, retrying (" + withdrawRetryCount + "/" + MAX_WITHDRAW_RETRIES + ")");
        }

        if (withdrawRetryCount >= MAX_WITHDRAW_RETRIES) {
            ScriptLogger.error(script.getScript(),
                operation + " failed after " + MAX_WITHDRAW_RETRIES + " attempts");
            return true; // Exhausted
        }

        return false; // Can continue
    }

    /**
     * Handles withdraw retry logic with fallback.
     * After MAX_WITHDRAW_RETRIES, checks if bank is open and item exists.
     *
     * @param itemName Display name for logging
     * @param itemId Item ID being withdrawn
     * @return Poll delay in milliseconds
     */
    private int handleWithdrawRetry(String itemName, int itemId) {
        if (handleRetryWithEscalation(itemName + " withdraw")) {
            // Max retries reached - perform fallback checks
            // Check 1: Is bank still open?
            if (!bank.isOpen()) {
                ScriptLogger.info(script.getScript(), "Bank closed during retries - reopening");
                resetRetryCount();
                currentState = BankingState.OPENING_BANK;
                script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(300, 1200, 600, 150));
                return 0;
            }

            // Check 2: Does the item still exist in bank?
            if (!bank.contains(itemId)) {
                ScriptLogger.error(script.getScript(), itemName + " (ID: " + itemId + ") not found in bank after retries - stopping");
                script.stop();
                return 0;
            }

            // Bank is open and item exists - something else is wrong
            ScriptLogger.error(script.getScript(), itemName + " withdraw keeps failing despite bank open and item present - stopping");
            script.stop();
            return 0;
        }

        script.pollFramesUntil(() -> false, RandomUtils.gaussianRandom(300, 1200, 600, 150));
        return 0;
    }

    /**
     * Resets the withdraw retry counter.
     * Should be called after successful withdraw or state transition.
     */
    private void resetRetryCount() {
        withdrawRetryCount = 0;
    }
}
