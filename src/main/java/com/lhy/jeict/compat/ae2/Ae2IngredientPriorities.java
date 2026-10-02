package com.lhy.jeict.compat.ae2;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.MEStorageMenu;

import com.lhy.jeict.recipe_tree.RecipeTreeInputViewModel.DisplayOption;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * AE2 pattern-encoding ingredient ranking from {@link EncodingHelper}:
 * craftable (has a pattern) first, then undamaged, then stored amount.
 */
public final class Ae2IngredientPriorities {
    private static final Comparator<GridInventoryEntry> ENTRY_COMPARATOR = Comparator
            .comparing(GridInventoryEntry::isCraftable)
            .thenComparing(Ae2IngredientPriorities::isUndamaged)
            .thenComparing(GridInventoryEntry::getStoredAmount);

    private Ae2IngredientPriorities() {
    }

    /**
     * @return best alternative index, or {@code -1} when no ME terminal repo is available
     */
    public static int bestIndex(List<DisplayOption> options) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !(minecraft.player.containerMenu instanceof MEStorageMenu menu)) {
            return -1;
        }
        if (menu.getClientRepo() == null) {
            return -1;
        }
        Map<AEKey, Integer> priorities = EncodingHelper.getIngredientPriorities(menu, ENTRY_COMPARATOR);
        int bestIndex = 0;
        int bestScore = Integer.MIN_VALUE;
        for (int index = 0; index < options.size(); index++) {
            AEKey key = toAeKey(options.get(index));
            int score = key == null ? Integer.MIN_VALUE : priorities.getOrDefault(key, Integer.MIN_VALUE);
            if (score > bestScore) {
                bestScore = score;
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private static Boolean isUndamaged(GridInventoryEntry entry) {
        AEKey what = entry.getWhat();
        return !(what instanceof AEItemKey itemKey) || !itemKey.isDamaged();
    }

    private static @Nullable AEKey toAeKey(DisplayOption option) {
        ItemStack stack = option.itemStack();
        return stack.isEmpty() ? null : AEItemKey.of(stack);
    }
}
