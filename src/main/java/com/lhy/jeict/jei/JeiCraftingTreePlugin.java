package com.lhy.jeict.jei;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.JeiCraftingTreeMod;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class JeiCraftingTreePlugin implements IModPlugin {
    private static @Nullable IJeiRuntime jeiRuntime;

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        RecipeTreeJeiLookup.clearCaches();
        JeiCraftingTreePlugin.jeiRuntime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        jeiRuntime = null;
        RecipeTreeJeiLookup.clearCaches();
    }

    public static @Nullable IJeiRuntime getJeiRuntime() {
        return jeiRuntime;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(JeiCraftingTreeMod.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addUniversalRecipeTransferHandler(new RecipeTreeJeiTransferHandler(registration.getTransferHelper()));
    }
}
