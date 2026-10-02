package com.lhy.jeict.client;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.jei.RecipeTreeOpenHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Bookmark-bar placement and click routing for the JEI-hosted recipe-tree button. */
public final class JeiRecipeTreeShortcutOverlay {
    private static @Nullable HitTest hitTest;

    private JeiRecipeTreeShortcutOverlay() {
    }

    public interface HitTest {
        boolean isMouseOver(double mouseX, double mouseY);
    }

    public static void bind(@Nullable HitTest hitTest) {
        JeiRecipeTreeShortcutOverlay.hitTest = hitTest;
    }

    /** Pixels to the right of the history button; skips ExtendedAE Plus when that mod is loaded. */
    public static int offsetFromHistoryButton() {
        return JeiStyleButtons.STEP + (ModList.get().isLoaded("extendedae_plus") ? JeiStyleButtons.STEP : 0);
    }

    public static boolean handleMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0 || hitTest == null
                || !hitTest.isMouseOver(event.getMouseX(), event.getMouseY())) {
            return false;
        }
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        if (RecipeTreeOpenHelper.hasLastWorkspace()) {
            RecipeTreeOpenHelper.openLastWorkspace(event.getScreen());
        } else {
            RecipeTreeOpenHelper.openJeiForNewTree(event.getScreen());
        }
        event.setCanceled(true);
        return true;
    }
}
