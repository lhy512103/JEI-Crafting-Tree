package com.lhy.jeict.jei;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.client.RecipeTreeJeiTransferTarget;
import com.lhy.jeict.client.RecipeTreeTransferMenu;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

public class RecipeTreeJeiTransferHandler implements IUniversalRecipeTransferHandler<RecipeTreeTransferMenu> {
    private final IRecipeTransferHandlerHelper transferHelper;

    public RecipeTreeJeiTransferHandler(IRecipeTransferHandlerHelper transferHelper) {
        this.transferHelper = transferHelper;
    }

    @Override
    public Class<? extends RecipeTreeTransferMenu> getContainerClass() {
        return RecipeTreeTransferMenu.class;
    }

    @Override
    public Optional<MenuType<RecipeTreeTransferMenu>> getMenuType() {
        return Optional.empty();
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(RecipeTreeTransferMenu container, Object recipe, IRecipeSlotsView recipeSlots,
            Player player, boolean maxTransfer, boolean doTransfer) {
        RecipeTreeJeiTransferTarget target = container.jeict$getTarget();
        if (target == null) {
            return transferHelper.createUserErrorWithTooltip(Component.translatable("message.jeict.recipe_tree_select_target_first"));
        }

        if (doTransfer) {
            target.jeict$applyJeiRecipe(recipe, recipeSlots);
            var clientPlayer = Minecraft.getInstance().player;
            if (clientPlayer != null) {
                clientPlayer.displayClientMessage(Component.translatable("message.jeict.recipe_tree_recipe_applied"), true);
            }
        }

        return null;
    }
}
