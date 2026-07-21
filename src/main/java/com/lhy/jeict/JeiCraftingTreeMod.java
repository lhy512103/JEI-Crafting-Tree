package com.lhy.jeict;

import com.lhy.jeict.client.ClientEvents;
import com.lhy.jeict.client.ClientInventorySources;
import com.lhy.jeict.config.RecipeTreeConfig;
import com.lhy.jeict.config.RecipeTreeKeyMappings;

import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.bus.api.IEventBus;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(JeiCraftingTreeMod.MOD_ID)
public final class JeiCraftingTreeMod {
    public static final String MOD_ID = "jeict";

    public JeiCraftingTreeMod(IEventBus modBus) {
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, RecipeTreeConfig.SPEC);
        modBus.addListener(RecipeTreeKeyMappings::register);
        if (FMLEnvironment.dist.isClient()) {
            ClientInventorySources.registerBuiltIns();
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, RenderGuiEvent.Post.class, ClientEvents::onRenderGuiPost);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ScreenEvent.MouseButtonPressed.Pre.class, ClientEvents::onScreenMousePressed);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ScreenEvent.MouseButtonReleased.Pre.class, ClientEvents::onScreenMouseReleased);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ScreenEvent.MouseDragged.Pre.class, ClientEvents::onScreenMouseDragged);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ScreenEvent.MouseScrolled.Pre.class, ClientEvents::onScreenMouseScrolled);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, InputEvent.Key.class, ClientEvents::onKeyInput);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, InputEvent.MouseButton.Pre.class, ClientEvents::onMouseButton);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, InputEvent.MouseScrollingEvent.class, ClientEvents::onMouseScrolled);
        }
    }
}
