package com.lhy.jeict.integration;

import com.lhy.jeict.Jeict;
import com.lhy.jeict.client.RecipeTreeButton;
import com.lhy.jeict.client.screen.RecipeTreeScreen;
import com.lhy.jeict.tree.RecipeGraphCache;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    /**
     * 注册一个全局 GUI 处理器：告诉 JEI "配方树按钮"占用了屏幕左下角的 20×20 区域，
     * 让 JEI 自动避让（不会盖到我们的按钮上面）。
     * <p>这是 JEI 1.20.1 公开 API，不需要 mixin 或反射。
     * 按钮实际绘制与点击接收由 Forge 的 {@code ScreenEvent} 处理（见 {@code ClientEvents}）。
     */
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGlobalGuiHandler(new IGlobalGuiHandler() {
            @Override
            public Collection<Rect2i> getGuiExtraAreas() {
                // 每帧 JEI layout 阶段调用：先刷新按钮位置/可见性，再返回占用区域。
                RecipeTreeButton.get().updateVisibility();
                Optional<Rect2i> area = RecipeTreeButton.get().currentArea();
                return area.map(List::of).orElse(List.of());
            }
        });
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
