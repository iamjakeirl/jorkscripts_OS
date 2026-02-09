package com.jork.utils;

import com.osmb.api.item.ItemGroupResult;
import com.osmb.api.item.ItemSearchResult;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.scene.RSObject;
import com.osmb.api.script.Script;
import com.osmb.api.ui.bank.Bank;
import com.osmb.api.ui.WidgetManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * A comprehensive banking utility that abstracts OSMB's Bank API into clean, reusable methods.
 * Handles bank detection, opening, deposits, withdrawals, and queries.
 *
 * <h2>Usage:</h2>
 * <pre>
 *     JorkBank bank = new JorkBank(script);
 *
 *     if (bank.open() == OpenResult.SUCCESS) {
 *         bank.depositAllExcept(ECTOPHIAL, TELEPORT_ITEM);
 *         bank.withdraw(BONES, 8);
 *         bank.close();
 *     }
 * </pre>
 *
 * <h2>IMPORTANT: OSMB Bank Tab System</h2>
 *
 * OSMB has an internal "selected tab" preference system that affects ALL bank operations.
 * Understanding this is critical to avoid unexpected behavior.
 *
 * <h3>How OSMB's Tab System Works:</h3>
 * <ul>
 *   <li>OSMB maintains an internal "selected tab" preference</li>
 *   <li>When {@code promptBankTabDialogue()} returns {@code true} in your Script, OSMB shows
 *       a dialogue when the bank first opens, asking the user to pick their preferred tab</li>
 *   <li>This selection is stored as the internal "selected tab" preference</li>
 *   <li><b>ALL bank operations</b> (search, withdraw, deposit, contains, etc.) will
 *       automatically switch to this "selected tab" before executing</li>
 * </ul>
 *
 * <h3>Critical Gotcha - getSelectedTabIndex() Has Side Effects:</h3>
 * <p>
 * The OSMB method {@code bank.getSelectedTabIndex()} is <b>NOT</b> a passive getter!
 * Calling it triggers OSMB to switch the bank UI back to the saved "selected tab" preference.
 * This means you cannot use it to verify a tab switch - the verification itself will undo
 * the switch you just made.
 * </p>
 *
 * <h3>Recommended Approach - Work WITH OSMB's System:</h3>
 * <ol>
 *   <li>Override {@code promptBankTabDialogue()} in your Script to return {@code true}</li>
 *   <li>Let the user select their preferred bank tab via OSMB's dialogue</li>
 *   <li>Do NOT manually switch tabs - OSMB will handle it automatically for all operations</li>
 *   <li>Remove any bank tab configuration from your script's UI - it's not needed</li>
 * </ol>
 *
 * <h3>Example Script Setup:</h3>
 * <pre>
 *     public class MyScript extends Script {
 *
 *         &#64;Override
 *         public boolean promptBankTabDialogue() {
 *             // Enable OSMB's bank tab dialogue - user selects tab, OSMB handles switching
 *             return true;
 *         }
 *
 *         // ... rest of script
 *     }
 * </pre>
 *
 * <h3>Note on Tab Methods:</h3>
 * <p>
 * JorkBank intentionally does not expose tab management methods. Let OSMB handle tab
 * switching automatically via the {@code promptBankTabDialogue()} system described above.
 * </p>
 *
 * @author jork
 * @see com.osmb.api.script.configuration.ScriptOptions#promptBankTabDialogue()
 */
public class JorkBank {

    // ═══════════════════════════════════════════════════════════════════════════
    // Constants
    // ═══════════════════════════════════════════════════════════════════════════

    /** Common bank object names for detection */
    private static final Set<String> BANK_OBJECT_NAMES = Set.of(
        "Bank booth",
        "Bank chest",
        "Grand Exchange booth",
        "Bank",
        "Chest"  // Some areas use generic "Chest" for banking
    );

    /** Bank interaction actions */
    private static final Set<String> BANK_ACTIONS = Set.of(
        "Bank",
        "Use",
        "Open"
    );

    // ─────────────────────────────────────────────────────────────────────────────
    // Timeout Configuration (base values - actual timeouts are randomized ±15-25%)
    // ─────────────────────────────────────────────────────────────────────────────

    /** Base timeout for bank open operations */
    private static final int BASE_OPEN_TIMEOUT = 5000;

    /** Base timeout for transaction verifications */
    private static final int BASE_TRANSACTION_TIMEOUT = 3000;

    /** Minimum variance percentage for timeout randomization */
    private static final double MIN_VARIANCE = 0.15;  // 15%

    /** Maximum variance percentage for timeout randomization */
    private static final double MAX_VARIANCE = 0.25;  // 25%

    // ═══════════════════════════════════════════════════════════════════════════
    // Result Enums
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Result of attempting to open the bank.
     */
    public enum OpenResult {
        /** Bank is now open */
        SUCCESS,
        /** Bank was already open when open() was called */
        ALREADY_OPEN,
        /** No bank object or NPC found nearby */
        NO_BANK_FOUND,
        /** Found bank object but interact() call failed */
        INTERACTION_FAILED,
        /** Interacted with bank but interface didn't open in time */
        TIMEOUT,
        /** WidgetManager or Bank instance not available */
        API_UNAVAILABLE
    }

    /**
     * Result of deposit/withdraw operations.
     */
    public enum TransactionResult {
        /** Operation completed successfully */
        SUCCESS,
        /** Bank is not open */
        BANK_NOT_OPEN,
        /** Item not found in source (inventory for deposit, bank for withdraw) */
        ITEM_NOT_FOUND,
        /** The operation call returned false */
        OPERATION_FAILED,
        /** Operation started but verification timed out */
        VERIFICATION_TIMEOUT,
        /** API components not available */
        API_UNAVAILABLE
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Instance Fields
    // ═══════════════════════════════════════════════════════════════════════════

    private final Script script;


    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a new JorkBank utility instance.
     *
     * @param script The script instance for API access
     */
    public JorkBank(Script script) {
        this.script = script;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Core Operations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Opens the nearest bank (booth, chest, or banker NPC).
     * Does NOT handle navigation - caller must already be near a bank.
     *
     * @return OpenResult indicating success or specific failure reason
     */
    public OpenResult open() {
        return open(null);
    }

    /**
     * Opens a bank matching the custom filter.
     * Useful when you need to interact with a specific bank object.
     *
     * Poll patterns:
     * - pollFramesUntil for bank open -- verification only, caller handles humanization
     *
     * @param customFilter Optional predicate to filter bank objects (null for default)
     * @return OpenResult indicating success or specific failure reason
     */
    public OpenResult open(Predicate<RSObject> customFilter) {
        // Check if already open
        if (isOpen()) {
            ScriptLogger.debug(script, "Bank already open");
            return OpenResult.ALREADY_OPEN;
        }

        // Find bank object
        RSObject bankObject = findBankObject(customFilter);
        if (bankObject == null) {
            ScriptLogger.warning(script, "No bank object found nearby");
            return OpenResult.NO_BANK_FOUND;
        }

        ScriptLogger.actionAttempt(script, "Opening bank: " + bankObject.getName());

        // Determine the correct action for this bank object
        String action = determineBankAction(bankObject);
        if (action == null) {
            ScriptLogger.warning(script, "Could not determine bank action for: " + bankObject.getName());
            return OpenResult.INTERACTION_FAILED;
        }

        // Interact with the bank
        boolean interacted = bankObject.interact(action);
        if (!interacted) {
            ScriptLogger.actionFailure(script, "Bank interaction failed", 1, 1);
            return OpenResult.INTERACTION_FAILED;
        }

        // Wait for bank to open (caller handles humanization)
        try {
            boolean opened = script.pollFramesUntil(() -> isOpen(), getOpenTimeout());

            if (opened) {
                ScriptLogger.actionSuccess(script, "Bank opened successfully");
                return OpenResult.SUCCESS;
            } else {
                ScriptLogger.warning(script, "Bank open timeout - interface didn't appear");
                return OpenResult.TIMEOUT;
            }
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.exception(script, "Error waiting for bank to open", e);
            return OpenResult.TIMEOUT;
        }
    }

    /**
     * Closes the bank interface if it's open.
     *
     * Poll patterns:
     * - pollFramesUntil for bank close -- verification only, caller handles humanization
     *
     * @return true if bank is now closed (or was already closed)
     */
    public boolean close() {
        if (!isOpen()) {
            return true;
        }

        Bank bank = getBankSafely();
        if (bank == null) {
            return true;  // Can't be open if we can't get reference
        }

        ScriptLogger.debug(script, "Closing bank");
        boolean closed = bank.close();

        if (closed) {
            // Wait for interface to actually close (caller handles humanization)
            try {
                script.pollFramesUntil(() -> !isOpen(), getTransactionTimeout());
            } catch (Exception e) {
                ExceptionUtils.rethrowIfTaskInterrupted(e);
            }
        }

        return !isOpen();
    }

    /**
     * Checks if the bank interface is currently open and visible.
     *
     * @return true if bank is open
     */
    public boolean isOpen() {
        Bank bank = getBankSafely();
        return bank != null && bank.isVisible();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Deposit Operations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Deposits all of a specific item from inventory to bank.
     *
     * @param itemId The item ID to deposit
     * @return TransactionResult indicating success or failure
     */
    public TransactionResult deposit(int itemId) {
        return deposit(itemId, -1);  // -1 = all
    }

    /**
     * Deposits a specific amount of an item from inventory to bank.
     *
     * Poll patterns:
     * - pollFramesUntil for inventory count decrease -- CORRECT, background verification
     *   (deposit is instant, checking count is not a visible UI event)
     *
     * @param itemId The item ID to deposit
     * @param amount The amount to deposit (use -1 for all)
     * @return TransactionResult indicating success or failure
     */
    public TransactionResult deposit(int itemId, int amount) {
        Bank bank = getBankSafely();
        if (bank == null || !bank.isVisible()) {
            return TransactionResult.BANK_NOT_OPEN;
        }

        // Check if item exists in inventory
        int currentCount = getInventoryCount(itemId);
        if (currentCount <= 0) {
            ScriptLogger.debug(script, "Item " + itemId + " not found in inventory for deposit");
            return TransactionResult.ITEM_NOT_FOUND;
        }

        int depositAmount = (amount == -1) ? currentCount : Math.min(amount, currentCount);
        ScriptLogger.debug(script, "Depositing " + depositAmount + " of item " + itemId);

        boolean success = bank.deposit(itemId, depositAmount);
        if (!success) {
            ScriptLogger.warning(script, "Deposit operation returned false");
            return TransactionResult.OPERATION_FAILED;
        }

        // Verify deposit completed
        try {
            int expectedCount = currentCount - depositAmount;
            boolean verified = script.pollFramesUntil(() ->
                getInventoryCount(itemId) <= expectedCount, getTransactionTimeout());

            return verified ? TransactionResult.SUCCESS : TransactionResult.VERIFICATION_TIMEOUT;
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            return TransactionResult.VERIFICATION_TIMEOUT;
        }
    }

    /**
     * Deposits the entire inventory into the bank.
     *
     * @return TransactionResult indicating success or failure
     */
    public TransactionResult depositAll() {
        return depositAllExcept(Set.of());
    }

    /**
     * Deposits all items except those specified.
     *
     * @param keepItemIds Item IDs to keep in inventory
     * @return TransactionResult indicating success or failure
     */
    public TransactionResult depositAllExcept(int... keepItemIds) {
        Set<Integer> keepSet = new HashSet<>();
        for (int id : keepItemIds) {
            keepSet.add(id);
        }
        return depositAllExcept(keepSet);
    }

    /**
     * Deposits all items except those in the specified set.
     *
     * Poll patterns:
     * - pollFramesUntil for only kept items remaining -- CORRECT, background verification
     *   (checking remaining item count is not a visible UI event)
     *
     * @param keepItemIds Set of item IDs to keep in inventory
     * @return TransactionResult indicating success or failure
     */
    public TransactionResult depositAllExcept(Set<Integer> keepItemIds) {
        Bank bank = getBankSafely();
        if (bank == null || !bank.isVisible()) {
            return TransactionResult.BANK_NOT_OPEN;
        }

        ScriptLogger.debug(script, "Depositing all except " + keepItemIds.size() + " item types");

        boolean success = bank.depositAll(keepItemIds);
        if (!success) {
            ScriptLogger.warning(script, "DepositAll operation returned false");
            return TransactionResult.OPERATION_FAILED;
        }

        // Verify deposit - inventory should only contain kept items
        try {
            boolean verified = script.pollFramesUntil(() -> {
                int totalCount = getInventoryOccupiedSlots();
                int keptCount = 0;
                for (int itemId : keepItemIds) {
                    keptCount += getInventoryCount(itemId);
                }
                // All remaining items should be from the keep list
                return totalCount <= keptCount;
            }, getTransactionTimeout());

            return verified ? TransactionResult.SUCCESS : TransactionResult.VERIFICATION_TIMEOUT;
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            return TransactionResult.VERIFICATION_TIMEOUT;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Withdraw Operations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Withdraws 1 of a specific item from bank to inventory.
     *
     * @param itemId The item ID to withdraw
     * @return TransactionResult indicating success or failure
     */
    public TransactionResult withdraw(int itemId) {
        return withdraw(itemId, 1);
    }

    /**
     * Withdraws a specific amount of an item from bank to inventory.
     *
     * Optimizes X-quantity withdrawals: if OSMB's cached X value already matches
     * the desired amount, bypasses the X dialogue by interacting with the item
     * directly via its right-click menu. Falls back to standard withdraw if the
     * direct interaction fails.
     *
     * Poll patterns:
     * - pollFramesUntil for inventory count increase -- verification only, caller handles humanization
     *
     * @param itemId The item ID to withdraw
     * @param amount The amount to withdraw
     * @return TransactionResult indicating success or failure
     */
    public TransactionResult withdraw(int itemId, int amount) {
        Bank bank = getBankSafely();
        if (bank == null || !bank.isVisible()) {
            return TransactionResult.BANK_NOT_OPEN;
        }

        // Check if item exists in bank
        if (!bankContains(itemId)) {
            ScriptLogger.debug(script, "Item " + itemId + " not found in bank for withdrawal");
            return TransactionResult.ITEM_NOT_FOUND;
        }

        int startCount = getInventoryCount(itemId);
        ScriptLogger.debug(script, "Withdrawing " + amount + " of item " + itemId);

        boolean success;

        success = bank.withdraw(itemId, amount);

        if (!success) {
            ScriptLogger.warning(script, "Withdraw operation returned false");
            return TransactionResult.OPERATION_FAILED;
        }

        // Verify withdrawal completed
        try {
            boolean verified = script.pollFramesUntil(() ->
                getInventoryCount(itemId) > startCount, getTransactionTimeout());

            return verified ? TransactionResult.SUCCESS : TransactionResult.VERIFICATION_TIMEOUT;
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            return TransactionResult.VERIFICATION_TIMEOUT;
        }
    }

    /**
     * Withdraws all of a specific item from bank to inventory.
     *
     * @param itemId The item ID to withdraw
     * @return TransactionResult indicating success or failure
     */
    public TransactionResult withdrawAll(int itemId) {
        // OSMB's withdraw with a very large number effectively withdraws all
        return withdraw(itemId, Integer.MAX_VALUE);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Query Operations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Checks if the bank contains a specific item.
     * Bank must be open.
     *
     * @param itemId The item ID to check
     * @return true if bank contains at least 1 of the item
     */
    public boolean contains(int itemId) {
        return bankContains(itemId);
    }

    /**
     * Checks if the bank contains at least a minimum amount of an item.
     * Bank must be open.
     *
     * @param itemId The item ID to check
     * @param minAmount Minimum required amount
     * @return true if bank contains at least minAmount of the item
     */
    public boolean contains(int itemId, int minAmount) {
        return getBankCount(itemId) >= minAmount;
    }

    /**
     * Gets the count of a specific item in the bank.
     * Bank must be open.
     *
     * @param itemId The item ID to count
     * @return Count of item in bank, or 0 if not found/bank not open
     */
    public int getCount(int itemId) {
        return getBankCount(itemId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Safely gets the Bank interface reference with null checks.
     */
    private Bank getBankSafely() {
        WidgetManager wm = script.getWidgetManager();
        if (wm == null) {
            return null;
        }
        return wm.getBank();
    }

    /**
     * Checks if a reachable bank object is nearby within the specified distance.
     *
     * @param maxDistance Maximum distance in tiles to consider "nearby"
     * @return true if a reachable bank object is within range, false otherwise
     */
    public boolean isBankNearby(int maxDistance) {
        WorldPosition playerPos = script.getWorldPosition();
        if (playerPos == null) {
            return false;
        }

        // Use RSObject.distance() method for distance comparison
        RSObject bankObject = findBankObject(obj -> obj.distance(playerPos) <= maxDistance);

        return bankObject != null;
    }

    /**
     * Finds a bank object near the player.
     *
     * @param customFilter Optional custom filter (null for default bank detection)
     * @return The closest reachable bank object, or null if none found
     */
    private RSObject findBankObject(Predicate<RSObject> customFilter) {
        WorldPosition playerPos = script.getWorldPosition();
        if (playerPos == null) {
            return null;
        }

        if (script.getObjectManager() == null) {
            return null;
        }

        // Build the filter
        Predicate<RSObject> filter = obj -> {
            if (obj == null || obj.getName() == null) {
                return false;
            }

            // Check if it's a known bank object
            String name = obj.getName();
            boolean isBankObject = BANK_OBJECT_NAMES.stream()
                .anyMatch(bankName -> name.equalsIgnoreCase(bankName));

            if (!isBankObject) {
                return false;
            }

            // Must have a valid bank action
            String[] actions = obj.getActions();
            if (actions == null) {
                return false;
            }

            boolean hasAction = Arrays.stream(actions)
                .filter(a -> a != null)
                .anyMatch(a -> BANK_ACTIONS.stream()
                    .anyMatch(bankAction -> a.equalsIgnoreCase(bankAction)));

            return hasAction;
        };

        // Apply custom filter if provided
        Predicate<RSObject> finalFilter = customFilter != null
            ? filter.and(customFilter)
            : filter;

        // Find all matching objects
        List<RSObject> bankObjects = script.getObjectManager().getObjects(finalFilter);

        if (bankObjects == null || bankObjects.isEmpty()) {
            return null;
        }

        // Return closest one (sorted by distance)
        return bankObjects.stream()
            .filter(obj -> obj.canReach())  // Must be reachable
            .min((a, b) -> Double.compare(a.distance(playerPos), b.distance(playerPos)))
            .orElse(null);
    }

    /**
     * Determines the correct interaction action for a bank object.
     */
    private String determineBankAction(RSObject bankObject) {
        String[] actions = bankObject.getActions();
        if (actions == null) {
            return null;
        }

        // Prefer "Bank" action, then "Use", then "Open"
        for (String preferred : new String[]{"Bank", "Use", "Open"}) {
            for (String action : actions) {
                if (action != null && action.equalsIgnoreCase(preferred)) {
                    return action;
                }
            }
        }

        return null;
    }

    /**
     * Checks if the bank contains an item.
     */
    private boolean bankContains(int itemId) {
        Bank bank = getBankSafely();
        if (bank == null || !bank.isVisible()) {
            return false;
        }

        try {
            ItemGroupResult search = bank.search(Set.of(itemId));
            return search != null && search.getAmount(itemId) > 0;
        } catch (Exception e) {
            ScriptLogger.debug(script, "Error checking bank contents: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the count of an item in the bank.
     */
    private int getBankCount(int itemId) {
        Bank bank = getBankSafely();
        if (bank == null || !bank.isVisible()) {
            return 0;
        }

        try {
            ItemGroupResult search = bank.search(Set.of(itemId));
            return search != null ? search.getAmount(itemId) : 0;
        } catch (Exception e) {
            ScriptLogger.debug(script, "Error getting bank count: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Gets the count of an item in the inventory.
     */
    private int getInventoryCount(int itemId) {
        try {
            WidgetManager wm = script.getWidgetManager();
            if (wm == null || wm.getInventory() == null) {
                return 0;
            }

            ItemGroupResult search = wm.getInventory().search(Set.of(itemId));
            return search != null ? search.getAmount(itemId) : 0;
        } catch (Exception e) {
            ScriptLogger.debug(script, "Error getting inventory count: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Checks if an amount requires the X (custom) quantity button.
     * Standard amounts (1, 5, 10) have dedicated buttons and don't trigger X dialogue.
     *
     * <p>Currently unused — OSMB's default withdraw handles X-quantity correctly.
     * Preserved for future use (e.g., single-tap optimization).</p>
     */
    private boolean isCustomAmount(int amount) {
        return amount != 1 && amount != 5 && amount != 10;
    }

    /**
     * Attempts to withdraw by directly interacting with the bank item's right-click menu,
     * bypassing OSMB's withdraw flow (which always enters the X dialogue).
     * Only works when X quantity is already cached to the desired amount.
     *
     * <p>Currently unused — OSMB's default withdraw handles X-quantity correctly.
     * Preserved for future use (e.g., single-tap optimization).</p>
     *
     * @return true if the interaction was sent successfully
     */
    private boolean withdrawViaDirectInteract(Bank bank, int itemId, int amount) {
        try {
            ItemGroupResult search = bank.search(Set.of(itemId));
            if (search == null) {
                return false;
            }

            ItemSearchResult item = search.getItem(itemId);
            if (item == null) {
                return false;
            }

            // Try"Withdraw-<amount>"
            return item.interact("Withdraw-" + amount);
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.debug(script, "Direct withdraw interact failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the number of occupied slots in the inventory.
     */
    private int getInventoryOccupiedSlots() {
        try {
            WidgetManager wm = script.getWidgetManager();
            if (wm == null || wm.getInventory() == null) {
                return 0;
            }

            // Search with empty set to scan all slots
            ItemGroupResult search = wm.getInventory().search(Set.of());
            return search != null ? search.getOccupiedSlotCount() : 0;
        } catch (Exception e) {
            ScriptLogger.debug(script, "Error getting inventory slot count: " + e.getMessage());
            return 0;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Timeout Randomization
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generates a randomized timeout for bank open operations.
     * Applies ±15-25% variance to avoid detection from consistent timing.
     *
     * @return Randomized timeout in milliseconds
     */
    private int getOpenTimeout() {
        return randomizeTimeout(BASE_OPEN_TIMEOUT);
    }

    /**
     * Generates a randomized timeout for transaction verifications.
     * Applies ±15-25% variance to avoid detection from consistent timing.
     *
     * @return Randomized timeout in milliseconds
     */
    private int getTransactionTimeout() {
        return randomizeTimeout(BASE_TRANSACTION_TIMEOUT);
    }

    /**
     * Applies random variance to a base timeout value.
     * The variance is randomized between MIN_VARIANCE and MAX_VARIANCE,
     * then applied as either positive or negative adjustment.
     *
     * Example: base=3000, variance=20% → returns between 2400-3600ms
     *
     * @param baseTimeout The base timeout value in milliseconds
     * @return Randomized timeout with variance applied
     */
    private int randomizeTimeout(int baseTimeout) {
        // Random variance between MIN_VARIANCE and MAX_VARIANCE
        double variancePercent = MIN_VARIANCE +
            (ThreadLocalRandom.current().nextDouble() * (MAX_VARIANCE - MIN_VARIANCE));

        // Randomly apply as positive or negative
        int variance = (int) (baseTimeout * variancePercent);
        int adjustment = ThreadLocalRandom.current().nextBoolean() ? variance : -variance;

        return baseTimeout + adjustment;
    }
}
