package com.lhy.jeict.jei;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.JeiCraftingTreeMod;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class JeiCraftingTreePlugin implements IModPlugin {
    private static @Nullable IJeiRuntime jeiRuntime;

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JeiCraftingTreePlugin.jeiRuntime = jeiRuntime;
    }

    public static @Nullable IJeiRuntime getJeiRuntime() {
        return jeiRuntime;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(JeiCraftingTreeMod.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        registration.addRecipeButtonFactory(new OpenTreeButtonFactory());
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addUniversalRecipeTransferHandler(new RecipeTreeJeiTransferHandler(registration.getTransferHelper()));
    }
}
