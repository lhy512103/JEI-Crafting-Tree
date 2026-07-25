package com.lhy.jeict.client;

import com.lhy.jeict.jei.RecipeTreeOpenHelper;

import net.neoforged.neoforge.client.event.InputEvent;
import com.lhy.jeict.config.RecipeTreeKeyMappings;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.minecraft.client.Minecraft;

public final class ClientEvents {
    public static void onClientTickPost(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        RecipeTreeOpenHelper.onClientTickPost();
    }

    public static void onScreenOpening(ScreenEvent.Opening event) {
        RecipeTreeOpenHelper.onScreenOpening(event);
    }

    public static void onScreenClosing(ScreenEvent.Closing event) {
        RecipeTreeOpenHelper.onScreenClosing(event);
    }

    private ClientEvents() {
    }

    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() == 1 && Minecraft.getInstance().screen != null) {
            RecipeTreeKeyMappings.handle(Minecraft.getInstance().screen);
        }
    }

    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen == null) {
            FloatingMaterialOverlayState.updateDrag();
            FloatingMaterialOverlayState.render(event.getGuiGraphics());
        }
    }

    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        FloatingMaterialOverlayState.updateDrag();
        FloatingMaterialOverlayState.render(event.getGuiGraphics());
        JeiRecipeTreeShortcutOverlay.render(event);
    }

    public static void onScreenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (JeiRecipeTreeShortcutOverlay.handleMousePressed(event)) return;
        FloatingMaterialOverlayState.handleScreenMouseClicked(event);
    }

    public static void onScreenMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        FloatingMaterialOverlayState.handleScreenMouseReleased(event);
    }

    public static void onScreenMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        FloatingMaterialOverlayState.handleScreenMouseDragged(event);
    }

    public static void onScreenMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        FloatingMaterialOverlayState.handleScreenMouseScrolled(event);
    }

    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        FloatingMaterialOverlayState.handleMouseButton(event);
    }

    public static void onMouseScrolled(InputEvent.MouseScrollingEvent event) {
        FloatingMaterialOverlayState.handleMouseScrolled(event);
    }
}
