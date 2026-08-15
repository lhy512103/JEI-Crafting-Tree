package com.lhy.jeict.client;

import com.lhy.jeict.config.RecipeTreeConfig;
import com.lhy.jeict.config.RecipeTreeKeyMappings;
import com.lhy.jeict.network.BatchCraftResultDispatch;

import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;

/** Registers code that must never be loaded by a dedicated server. */
public final class JeictClientBootstrap {
    private JeictClientBootstrap() {
    }

    public static void initialize(IEventBus modBus) {
        modBus.addListener(RecipeTreeKeyMappings::register);
        BatchCraftResultDispatch.registerClientHandler(RecipeTreeAutoCraftSession::handleBatchResult);
        ClientInventorySources.registerBuiltIns();
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onClientTickPost);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ScreenEvent.Opening.class, ClientEvents::onScreenOpening);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ScreenEvent.Closing.class, ClientEvents::onScreenClosing);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, RenderGuiEvent.Post.class, ClientEvents::onRenderGuiPost);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, ScreenEvent.Render.Post.class, ClientEvents::onScreenRenderPost);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ScreenEvent.MouseButtonPressed.Pre.class, ClientEvents::onScreenMousePressed);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ScreenEvent.MouseButtonReleased.Pre.class, ClientEvents::onScreenMouseReleased);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ScreenEvent.MouseDragged.Pre.class, ClientEvents::onScreenMouseDragged);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ScreenEvent.MouseScrolled.Pre.class, ClientEvents::onScreenMouseScrolled);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, InputEvent.Key.class, ClientEvents::onKeyInput);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, InputEvent.MouseButton.Pre.class, ClientEvents::onMouseButton);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, InputEvent.MouseScrollingEvent.class, ClientEvents::onMouseScrolled);
    }
}
