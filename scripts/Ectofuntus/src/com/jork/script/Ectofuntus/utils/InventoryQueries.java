package com.jork.script.Ectofuntus.utils;

import com.jork.script.Ectofuntus.EctofuntusScript;
import com.jork.utils.ExceptionUtils;
import com.jork.utils.ScriptLogger;
import com.osmb.api.item.ItemGroupResult;
import com.osmb.api.item.ItemSearchResult;

import java.util.Set;

/**
 * Shared inventory query helpers for Ectofuntus tasks.
 */
public final class InventoryQueries {

    private InventoryQueries() {
    }

    public static int countItem(EctofuntusScript script, int itemId, String errorPrefix) {
        try {
            var wm = script.getWidgetManager();
            if (wm == null || wm.getInventory() == null) {
                return 0;
            }

            ItemGroupResult search = wm.getInventory().search(Set.of(itemId));
            return search != null ? search.getAmount(itemId) : 0;
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.debug(script.getScript(), errorPrefix + e.getMessage());
            return 0;
        }
    }

    public static int countItems(EctofuntusScript script, Set<Integer> itemIds, String errorPrefix) {
        try {
            var wm = script.getWidgetManager();
            if (wm == null || wm.getInventory() == null || itemIds == null || itemIds.isEmpty()) {
                return 0;
            }

            ItemGroupResult search = wm.getInventory().search(itemIds);
            return search != null ? search.getAmount(itemIds) : 0;
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.debug(script.getScript(), errorPrefix + e.getMessage());
            return 0;
        }
    }

    public static ItemSearchResult findItem(EctofuntusScript script, int itemId, String errorPrefix) {
        try {
            var wm = script.getWidgetManager();
            if (wm == null || wm.getInventory() == null) {
                return null;
            }

            ItemGroupResult search = wm.getInventory().search(Set.of(itemId));
            return search != null ? search.getItem(itemId) : null;
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.debug(script.getScript(), errorPrefix + e.getMessage());
            return null;
        }
    }

    public static ItemGroupResult snapshot(EctofuntusScript script, Set<Integer> itemIds, String errorPrefix) {
        try {
            var wm = script.getWidgetManager();
            if (wm == null || wm.getInventory() == null || itemIds == null || itemIds.isEmpty()) {
                return null;
            }

            return wm.getInventory().search(itemIds);
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.debug(script.getScript(), errorPrefix + e.getMessage());
            return null;
        }
    }

    public static int amount(ItemGroupResult snapshot, int itemId) {
        return snapshot != null ? snapshot.getAmount(itemId) : 0;
    }

    public static int amount(ItemGroupResult snapshot, Set<Integer> itemIds) {
        return snapshot != null ? snapshot.getAmount(itemIds) : 0;
    }
}
