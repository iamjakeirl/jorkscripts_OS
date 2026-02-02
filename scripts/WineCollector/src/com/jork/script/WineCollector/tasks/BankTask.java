package com.jork.script.WineCollector.tasks;

import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.scene.RSObject;
import com.osmb.api.ui.bank.Bank;
import com.osmb.api.item.ItemGroupResult;
import com.osmb.api.utils.RandomUtils;
import java.util.Set;
import com.jork.script.WineCollector.WineCollector;
import com.jork.script.WineCollector.config.WineConfig;

public class BankTask implements Task {

    private final WineCollector script;

    public BankTask(WineCollector script) {
        this.script = script;
    }

    @Override
    public boolean canExecute() {
        // Check persistent flag set by CollectTask
        // In the future, this will be used by NavigateTask instead
        return script.shouldBank();
    }

    @Override
    public int execute() {
        // Assumes NavigateTask has already positioned us at the bank on ground floor

        WorldPosition currentPos = script.getWorldPosition();
        if (currentPos == null) {
            return WineConfig.POLL_DELAY_LONG;
        }

        Bank bank = script.getWidgetManager().getBank();
        if (bank == null) {
            return WineConfig.POLL_DELAY_LONG;
        }

        if (!bank.isVisible()) {
            RSObject bankChest = script.getObjectManager().getClosestObject(currentPos, WineConfig.BANK_CHEST_NAME);

            if (bankChest == null) {
                return WineConfig.POLL_DELAY_LONG;
            }

            boolean interacted = bankChest.interact(WineConfig.BANK_USE_ACTION);
            if (!interacted) {
                return WineConfig.POLL_DELAY_LONG;
            }

            int openTimeout = RandomUtils.weightedRandom(
                WineConfig.BANK_OPEN_TIMEOUT - 100,
                WineConfig.BANK_OPEN_TIMEOUT + 100
            );
            boolean opened = script.pollFramesHuman(() -> {
                Bank b = script.getWidgetManager().getBank();
                return b != null && b.isVisible();
            }, openTimeout);

            if (!opened) {
                return WineConfig.POLL_DELAY_LONG;
            }

            return WineConfig.POLL_DELAY_MEDIUM;
        }
        
        boolean deposited = bank.depositAll(Set.of());

        if (deposited) {
            int depositTimeout = RandomUtils.weightedRandom(2900, 3100);
            script.pollFramesHuman(() -> {
                ItemGroupResult checkResult = script.getWidgetManager().getInventory().search(Set.of(WineConfig.WINE_ID));
                int currentWines = checkResult != null ? checkResult.getAmount(WineConfig.WINE_ID) : 0;
                return currentWines == 0;
            }, depositTimeout);

            bank.close();

            int closeTimeout = RandomUtils.weightedRandom(1900, 2100);
            script.pollFramesHuman(() -> {
                Bank b = script.getWidgetManager().getBank();
                return b == null || !b.isVisible();
            }, closeTimeout);

            // Clear banking flag - ready to collect again
            script.setShouldBank(false);
        }

        return WineConfig.POLL_DELAY_LONG;
    }
}