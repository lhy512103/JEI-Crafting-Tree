package com.lhy.jeict.client;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.api.CraftingTreeBackend;
import com.lhy.jeict.api.CraftingTreeBackends;
import com.lhy.jeict.jei.JeiCraftingTreePlugin;
import com.lhy.jeict.planning.InventorySnapshot;
import com.lhy.jeict.planning.MaterialKey;
import com.lhy.jeict.recipe_tree.RecipeTreeInputViewModel.DisplayOption;
import com.lhy.jeict.util.IngredientIdentityUtil;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Picks the default ingredient alternative the way AE2 encoding does:
 * already-craftable (has a pattern) first, then undamaged, then highest stock.
 */
public final class IngredientCandidateRanker {
    private static final long CRAFTABLE_SCORE = 4_000_000_000_000L;
    private static final long UNDAMAGED_SCORE = 2_000_000_000_000L;

    private IngredientCandidateRanker() {
    }

    public static int bestIndex(List<DisplayOption> options) {
        if (options == null || options.size() <= 1) {
            return 0;
        }
        if (ModList.get().isLoaded("ae2")) {
            int encoded = com.lhy.jeict.compat.ae2.Ae2IngredientPriorities.bestIndex(options);
            if (encoded >= 0) {
                return encoded;
            }
        }
        return fallbackIndex(options);
    }

    private static int fallbackIndex(List<DisplayOption> options) {
        CraftingTreeBackend backend = CraftingTreeBackends.get();
        InventorySnapshot inventory = ClientInventorySnapshotCache.get();
        IJeiRuntime runtime = JeiCraftingTreePlugin.getJeiRuntime();
        IIngredientManager manager = runtime == null ? null : runtime.getIngredientManager();
        int bestIndex = 0;
        long bestScore = Long.MIN_VALUE;
        for (int index = 0; index < options.size(); index++) {
            long score = score(options.get(index), backend, inventory, manager);
            if (score > bestScore) {
                bestScore = score;
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private static long score(DisplayOption option, @Nullable CraftingTreeBackend backend,
            InventorySnapshot inventory, @Nullable IIngredientManager manager) {
        ITypedIngredient<?> typed = option.typedIngredient();
        ItemStack stack = option.itemStack();
        Object raw = typed == null ? null : typed.getIngredient();
        boolean craftable = backend != null && raw != null && backend.isCraftable(raw);
        boolean damaged = !stack.isEmpty() && stack.isDamaged();
        long stored = 0L;
        if (manager != null && typed != null) {
            MaterialKey key = IngredientIdentityUtil.keyOf(manager, typed);
            stored = inventory.amount(key);
        } else if (!stack.isEmpty()) {
            MaterialKey key = MaterialKey.of(IngredientIdentityUtil.fallbackSignature(typed, stack));
            stored = inventory.amount(key);
        }
        return (craftable ? CRAFTABLE_SCORE : 0L)
                + (damaged ? 0L : UNDAMAGED_SCORE)
                + Math.min(stored, UNDAMAGED_SCORE - 1L);
    }
}
