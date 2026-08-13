package com.lhy.jeict.client;

import com.lhy.jeict.config.RecipeTreeConfig;
import com.lhy.jeict.config.RecipeTreeKeyMappings;
import com.lhy.jeict.network.BatchCraftResultDispatch;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Registers code that must never be loaded by a dedicated server. */
public final class JeictClientBootstrap {
    private JeictClientBootstrap() {
    }

    public static void initialize(IEventBus modBus) {
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, RecipeTreeConfig.SPEC);
        modBus.addListener(RecipeTreeKeyMappings::register);
        BatchCraftResultDispatch.registerClientHandler(RecipeTreeAutoCraftSession::handleBatchResult);
        ClientInventorySources.registerBuiltIns();
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ScreenEvent.Opening.class, ClientEvents::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ScreenEvent.Closing.class, ClientEvents::onScreenClosing);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, RenderGuiEvent.Post.class, ClientEvents::onRenderGuiPost);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, ScreenEvent.Render.Post.class, ClientEvents::onScreenRenderPost);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ScreenEvent.MouseButtonPressed.Pre.class, ClientEvents::onScreenMousePressed);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ScreenEvent.MouseButtonReleased.Pre.class, ClientEvents::onScreenMouseReleased);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ScreenEvent.MouseDragged.Pre.class, ClientEvents::onScreenMouseDragged);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ScreenEvent.MouseScrolled.Pre.class, ClientEvents::onScreenMouseScrolled);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, InputEvent.Key.class, ClientEvents::onKeyInput);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, InputEvent.MouseButton.Pre.class, ClientEvents::onMouseButton);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, InputEvent.MouseScrollingEvent.class, ClientEvents::onMouseScrolled);
    }
}