package com.lhy.jeict.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.jei.JeiCraftingTreePlugin;
import com.lhy.jeict.jei.RecipeTreeJeiLookup;
import com.lhy.jeict.compat.sophisticated.SophisticatedCraftingClient;
import com.lhy.jeict.debug.AutoCraftDebug;
import com.lhy.jeict.planning.InventorySnapshot;
import com.lhy.jeict.planning.MaterialKey;
import com.lhy.jeict.planning.RecipePlanSolver;
import com.lhy.jeict.recipe_tree.RecipeTreeInputViewModel;
import com.lhy.jeict.recipe_tree.RecipeTreeNodeViewModel;
import com.lhy.jeict.recipe_tree.RecipeTreeRecipeViewModel;
import com.lhy.jeict.util.IngredientIdentityUtil;
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
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

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
        COMPLETED,
        UNAVAILABLE
    }

    public record Result(Outcome outcome, @Nullable Component recipeTitle, ItemStack expectedOutput,
            @Nullable RecipeTreeRecipeViewModel recipe) {
        public Result(Outcome outcome, @Nullable Component recipeTitle) {
            this(outcome, recipeTitle, ItemStack.EMPTY, null);
        }

        public Result(Outcome outcome, @Nullable Component recipeTitle, ItemStack expectedOutput,
                @Nullable RecipeTreeRecipeViewModel recipe) {
            this.outcome = outcome;
            this.recipeTitle = recipeTitle;
            this.expectedOutput = expectedOutput == null ? ItemStack.EMPTY : expectedOutput.copy();
            this.recipe = recipe;
        }
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

        PlannedCandidates planned = plannedCandidates(context);
        AutoCraftDebug.log("menu={} id={} planned={} completed={}", menu.getClass().getName(), menu.containerId,
                planned.recipes().size(), planned.completed());
        if (planned.completed()) {
            return new Result(Outcome.COMPLETED, context.title());
        }
        List<RecipeTreeRecipeViewModel> candidates = planned.recipes();
        boolean sawHandler = false;
        boolean sawMissingItems = false;
        boolean sawFailure = false;
        int examined = 0;
        for (RecipeTreeRecipeViewModel view : candidates) {
            if (examined >= MAX_CANDIDATES) break;
            RecipeTreeJeiLookup.RecipeHandle handle = RecipeTreeJeiLookup.findRecipeHandle(view).orElse(null);
            if (handle == null) {
                AutoCraftDebug.log("skip recipe={} because JEI handle is missing", view.recipeId());
                continue;
            }
            IRecipeTransferHandler<?, ?> transferHandler =
                    findTransferHandler(runtime, menu, handle.category()).orElse(null);
            IRecipeSlotsView slotsView = slotsViewFor(runtime, view, handle);
            if (slotsView == null) {
                AutoCraftDebug.log("skip recipe={} because JEI slots view is missing", view.recipeId());
                continue;
            }
            if (ModList.get().isLoaded("sophisticatedcore") && SophisticatedCraftingClient.supports(menu)) {
                AutoCraftDebug.log("try sophisticated recipe={} inputs={}", view.recipeId(),
                        slotsView.getSlotViews(mezz.jei.api.recipe.RecipeIngredientRole.INPUT).size());
                if (SophisticatedCraftingClient.transfer(menu, player, slotsView,
                        runtime.getJeiHelpers().getStackHelper())) {
                    AutoCraftDebug.log("sophisticated click plan sent recipe={}", view.recipeId());
                    return new Result(Outcome.TRANSFERRED, view.title(), view.primaryOutput(), view);
                }
                AutoCraftDebug.log("sophisticated click plan rejected recipe={}", view.recipeId());
                sawMissingItems = true;
                continue;
            }
            if (transferHandler == null) continue;
            sawHandler = true;
            examined++;

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
                    return new Result(Outcome.TRANSFERRED, view.title(), view.primaryOutput(), view);
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

    private static PlannedCandidates plannedCandidates(RecipeTreeRootContext context) {
        InventorySnapshot snapshot = ClientInventorySnapshotCache.get();
        Map<MaterialKey, Long> available = new LinkedHashMap<>(snapshot.amounts());
        Set<RecipeTreeNodeViewModel> path = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        long targetAmount = context.projects().amounts().getOrDefault(context.projects().activeProject(), 1L);
        Selection selection = selectFrontier(context.root(), targetAmount, available, path, 0);
        if (!selection.completed() && selection.recipe() == null) {
            RecipeTreeRecipeViewModel rootRecipe = context.root().recipe();
            MaterialKey rootKey = materialKey(rootRecipe.primaryOutputIngredient(), rootRecipe.primaryOutput());
            long incrementalTarget = RecipePlanSolver.saturatedAdd(
                    snapshot.amount(rootKey), Math.max(1L, rootRecipe.primaryOutputAmount()));
            selection = selectFrontier(context.root(), incrementalTarget,
                    new LinkedHashMap<>(snapshot.amounts()),
                    java.util.Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        }
        if (!selection.completed() && selection.recipe() == null) {
            List<RecipeTreeRecipeViewModel> selected = context.collectSelectedRecipes();
            for (int index = selected.size() - 1; index >= 0; index--) {
                RecipeTreeRecipeViewModel candidate = selected.get(index);
                if (canCraftOnce(candidate, snapshot)) {
                    selection = new Selection(candidate, false);
                    AutoCraftDebug.log("incremental fallback selected recipe={}", candidate.recipeId());
                    break;
                }
            }
        }
        return selection.completed()
                ? new PlannedCandidates(List.of(), true)
                : new PlannedCandidates(selection.recipe() == null ? List.of() : List.of(selection.recipe()), false);
    }

    private static boolean canCraftOnce(RecipeTreeRecipeViewModel recipe, InventorySnapshot snapshot) {
        if (recipe.inputs().isEmpty()) return false;
        Map<MaterialKey, Long> required = new LinkedHashMap<>();
        for (RecipeTreeInputViewModel input : recipe.inputs()) {
            MaterialKey key = materialKey(input.displayIngredient(), input.displayStack());
            long amount = Math.max(1L, input.longAmount());
            required.merge(key, amount, RecipePlanSolver::saturatedAdd);
        }
        for (Map.Entry<MaterialKey, Long> entry : required.entrySet()) {
            if (snapshot.amount(entry.getKey()) < entry.getValue()) return false;
        }
        return true;
    }

    private static Selection selectFrontier(RecipeTreeNodeViewModel node, long requiredOutput,
            Map<MaterialKey, Long> available, Set<RecipeTreeNodeViewModel> path, int depth) {
        RecipeTreeRecipeViewModel recipe = node.recipe();
        MaterialKey outputKey = materialKey(recipe.primaryOutputIngredient(), recipe.primaryOutput());
        long owned = available.getOrDefault(outputKey, 0L);
        long used = Math.min(owned, requiredOutput);
        if (used > 0L) available.put(outputKey, owned - used);
        long missing = requiredOutput - used;
        if (missing <= 0L) return Selection.COMPLETE;
        if (depth >= 128 || !path.add(node)) return new Selection(null, false);

        long crafts = ceilDiv(missing, recipe.primaryOutputAmount());
        Map<InputKey, InputDemand> demands = new LinkedHashMap<>();
        for (RecipeTreeInputViewModel input : recipe.inputs()) {
            RecipeTreeNodeViewModel child = input.child();
            MaterialKey key = child == null
                    ? materialKey(input.displayIngredient(), input.displayStack())
                    : materialKey(child.recipe().primaryOutputIngredient(), child.recipe().primaryOutput());
            InputKey demandKey = new InputKey(key, child, input.consumed());
            demands.computeIfAbsent(demandKey, ignored -> new InputDemand(input, child))
                    .add(input.longAmount());
        }

        boolean inputsReady = true;
        for (InputDemand demand : demands.values()) {
            long required = demand.input().consumed()
                    ? saturatedMultiply(demand.amount(), crafts)
                    : demand.amount();
            RecipeTreeNodeViewModel child = demand.child();
            if (child != null) {
                Selection childSelection = selectFrontier(child, required, available, path, depth + 1);
                if (childSelection.recipe() != null) {
                    path.remove(node);
                    return childSelection;
                }
                inputsReady &= childSelection.completed();
                continue;
            }
            MaterialKey key = materialKey(demand.input().displayIngredient(), demand.input().displayStack());
            long rawOwned = available.getOrDefault(key, 0L);
            long rawUsed = Math.min(rawOwned, required);
            if (rawUsed > 0L) available.put(key, rawOwned - rawUsed);
            inputsReady &= rawUsed >= required;
        }
        path.remove(node);
        return inputsReady ? new Selection(recipe, false) : new Selection(null, false);
    }

    static MaterialKey materialKey(@Nullable mezz.jei.api.ingredients.ITypedIngredient<?> ingredient,
            ItemStack stack) {
        IJeiRuntime runtime = JeiCraftingTreePlugin.getJeiRuntime();
        if (runtime == null) return MaterialKey.of(IngredientIdentityUtil.fallbackSignature(ingredient, stack));
        var manager = runtime.getIngredientManager();
        var resolved = ingredient;
        if (resolved == null && stack != null && !stack.isEmpty()) {
            resolved = manager.createTypedIngredient(stack.copyWithCount(1), true).orElse(null);
        }
        return resolved == null
                ? MaterialKey.of(IngredientIdentityUtil.fallbackSignature(ingredient, stack))
                : IngredientIdentityUtil.keyOf(manager, resolved);
    }

    private static long ceilDiv(long numerator, long denominator) {
        if (numerator <= 0L) return 0L;
        long safeDenominator = Math.max(1L, denominator);
        return 1L + (numerator - 1L) / safeDenominator;
    }

    private static long saturatedMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private record InputKey(MaterialKey material, @Nullable RecipeTreeNodeViewModel child, boolean consumed) {
        @Override
        public boolean equals(Object other) {
            return other instanceof InputKey key && material.equals(key.material) && child == key.child
                    && consumed == key.consumed;
        }

        @Override
        public int hashCode() {
            return 31 * (31 * material.hashCode() + System.identityHashCode(child)) + Boolean.hashCode(consumed);
        }
    }

    private static final class InputDemand {
        private final RecipeTreeInputViewModel input;
        private final @Nullable RecipeTreeNodeViewModel child;
        private long amount;

        private InputDemand(RecipeTreeInputViewModel input, @Nullable RecipeTreeNodeViewModel child) {
            this.input = input;
            this.child = child;
        }

        private void add(long added) {
            amount = RecipePlanSolver.saturatedAdd(amount, added);
        }

        private RecipeTreeInputViewModel input() { return input; }
        private @Nullable RecipeTreeNodeViewModel child() { return child; }
        private long amount() { return amount; }
    }

    private record Selection(@Nullable RecipeTreeRecipeViewModel recipe, boolean completed) {
        private static final Selection COMPLETE = new Selection(null, true);
    }

    private record PlannedCandidates(List<RecipeTreeRecipeViewModel> recipes, boolean completed) {
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
