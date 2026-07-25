package com.lhy.jeict.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.jei.JeiCraftingTreePlugin;
import com.lhy.jeict.jei.RecipeTreeJeiLookup;
import com.lhy.jeict.recipe_tree.RecipeTreeRecipeViewModel;
import com.lhy.jeict.recipe_tree.RecipeTreeRootContext;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Fills the container the player currently has open using JEI's registered recipe transfer
 * handlers, so any machine or terminal that already supports JEI's "+" button is supported
 * without mod-specific code. Everything here runs on click only; nothing touches the frame
 * or tick loop.
 */
public final class RecipeTreeAutoCraftService {
    /** Upper bound on candidate recipes examined per click, so a huge tree cannot stall the click. */
    private static final int MAX_CANDIDATES = 64;

    private static final Map<ResourceLocation, IRecipeSlotsView> SLOT_VIEW_CACHE = new HashMap<>();
    private static @Nullable IJeiRuntime slotViewCacheRuntime;

    private RecipeTreeAutoCraftService() {
    }

    public enum Outcome {
        TRANSFERRED,
        NO_CONTAINER,
        NO_HANDLER,
        MISSING_ITEMS,
        REJECTED,
        FAILED,
        UNAVAILABLE
    }

    public record Result(Outcome outcome, @Nullable Component recipeTitle) {
    }

    /**
     * Walks the selected tree order and transfers one batch of the first recipe the open
     * container accepts. Continuous orchestration deliberately lives outside this gateway.
     */
    public static Result craftFirstAvailable(@Nullable RecipeTreeRootContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (context == null || player == null) {
            return new Result(Outcome.UNAVAILABLE, null);
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) {
            return new Result(Outcome.NO_CONTAINER, null);
        }
        IJeiRuntime runtime = JeiCraftingTreePlugin.getJeiRuntime();
        if (runtime == null) {
            return new Result(Outcome.UNAVAILABLE, null);
        }
        if (slotViewCacheRuntime != runtime) {
            SLOT_VIEW_CACHE.clear();
            slotViewCacheRuntime = runtime;
        }

        List<RecipeTreeRecipeViewModel> candidates = context.collectSelectedRecipes();
        boolean sawHandler = false;
        boolean sawMissingItems = false;
        boolean sawFailure = false;
        int examined = 0;
        for (RecipeTreeRecipeViewModel view : candidates) {
            if (examined >= MAX_CANDIDATES) break;
            RecipeTreeJeiLookup.RecipeHandle handle = RecipeTreeJeiLookup.findRecipeHandle(view).orElse(null);
            if (handle == null) continue;
            IRecipeTransferHandler<?, ?> transferHandler =
                    findTransferHandler(runtime, menu, handle.category()).orElse(null);
            if (transferHandler == null) continue;
            sawHandler = true;
            examined++;
            IRecipeSlotsView slotsView = slotsViewFor(runtime, view, handle);
            if (slotsView == null) continue;

            IRecipeTransferError probe;
            try {
                probe = transfer(transferHandler, menu, handle.recipe(), slotsView, player, false);
            } catch (RuntimeException | LinkageError problem) {
                sawFailure = true;
                continue;
            }
            if (probe != null && !probe.getType().allowsTransfer) {
                sawMissingItems = true;
                continue;
            }
            try {
                IRecipeTransferError execution = transfer(transferHandler, menu, handle.recipe(), slotsView, player, true);
                if (execution == null || execution.getType().allowsTransfer) {
                    return new Result(Outcome.TRANSFERRED, view.title());
                }
                sawMissingItems = true;
            } catch (RuntimeException | LinkageError problem) {
                sawFailure = true;
            }
        }
        if (sawFailure) {
            return new Result(Outcome.FAILED, null);
        }
        if (sawMissingItems) {
            return new Result(Outcome.MISSING_ITEMS, null);
        }
        return new Result(sawHandler ? Outcome.REJECTED : Outcome.NO_HANDLER, null);
    }

    /** Drops cached slot views; call after a JEI reload so stale layouts are not reused. */
    public static void clearCaches() {
        SLOT_VIEW_CACHE.clear();
        slotViewCacheRuntime = null;
    }

    private static @Nullable IRecipeSlotsView slotsViewFor(IJeiRuntime runtime, RecipeTreeRecipeViewModel view,
            RecipeTreeJeiLookup.RecipeHandle handle) {
        ResourceLocation recipeId = view.recipeId();
        if (recipeId != null) {
            IRecipeSlotsView cached = SLOT_VIEW_CACHE.get(recipeId);
            if (cached != null) {
                return cached;
            }
        }
        IRecipeSlotsView built = buildSlotsView(runtime, handle);
        if (built != null && recipeId != null) {
            SLOT_VIEW_CACHE.put(recipeId, built);
        }
        return built;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static @Nullable IRecipeSlotsView buildSlotsView(IJeiRuntime runtime,
            RecipeTreeJeiLookup.RecipeHandle handle) {
        var focusGroup = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        Optional<IRecipeLayoutDrawable<?>> layout = (Optional) runtime.getRecipeManager()
                .createRecipeLayoutDrawable((IRecipeCategory) handle.category(), handle.recipe(), focusGroup);
        return layout.map(IRecipeLayoutDrawable::getRecipeSlotsView).orElse(null);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Optional<IRecipeTransferHandler<?, ?>> findTransferHandler(IJeiRuntime runtime,
            AbstractContainerMenu menu, IRecipeCategory<?> category) {
        return (Optional) runtime.getRecipeTransferManager()
                .getRecipeTransferHandler(menu, (IRecipeCategory) category);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static @Nullable IRecipeTransferError transfer(IRecipeTransferHandler<?, ?> handler,
            AbstractContainerMenu menu, Object recipe, IRecipeSlotsView slotsView, Player player, boolean doTransfer) {
        return ((IRecipeTransferHandler) handler).transferRecipe(menu, recipe, slotsView, player, true, doTransfer);
    }

}
