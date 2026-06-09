package com.lhy.jeict.integration;

import com.lhy.jeict.Jeict;
import com.lhy.jeict.client.screen.RecipeTreeScreen;
import com.lhy.jeict.tree.RecipeGraphCache;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@JeiPlugin
public class JeiTreePlugin implements IModPlugin {
    private static final ResourceLocation UID = new ResourceLocation(Jeict.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addUniversalRecipeTransferHandler(new RecipeTreeTransferHandler());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JeiRuntimeAccess.set(jeiRuntime);
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiRuntimeAccess.clear();
        RecipeGraphCache.clear();
    }

    private static final class RecipeTreeTransferHandler implements IUniversalRecipeTransferHandler<RecipeTreeScreen.ContainerTransfer> {
        @Override
        public Class<? extends RecipeTreeScreen.ContainerTransfer> getContainerClass() {
            return RecipeTreeScreen.ContainerTransfer.class;
        }

        @Override
        public Optional<MenuType<RecipeTreeScreen.ContainerTransfer>> getMenuType() {
            return Optional.empty();
        }

        @Override
        public @Nullable IRecipeTransferError transferRecipe(RecipeTreeScreen.ContainerTransfer container, Object recipe, IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
            if (doTransfer && container.getScreen() != null) {
                container.getScreen().transferRecipeFromJei(recipe, recipeSlots);
            }
            return null;
        }
    }
}
