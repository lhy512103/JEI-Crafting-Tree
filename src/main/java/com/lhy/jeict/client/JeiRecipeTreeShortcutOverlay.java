package com.lhy.jeict.client;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.jei.JeiCraftingTreePlugin;
import com.lhy.jeict.jei.RecipeTreeOpenHelper;

import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.client.event.ScreenEvent;

/** JEI-side shortcut rendered at the lower-left edge of the bookmark area. */
public final class JeiRecipeTreeShortcutOverlay {
    private static final int HEIGHT = 20;
    private static final int MARGIN = 6;
    private static final int JEI_NATIVE_BUTTONS_WIDTH = 44;
    private static final int MAX_WIDTH = 112;
    private static @Nullable Bounds lastBounds;
    private static @Nullable Screen lastScreen;
    private static @Nullable Class<?> cachedBookmarkOverlayClass;
    private static @Nullable Method cachedBookmarkVisibilityMethod;

    private JeiRecipeTreeShortcutOverlay() {
    }

    public static void render(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (screen instanceof RecipeTreeOverviewScreen) {
            clearBounds();
            return;
        }
        IJeiRuntime runtime = JeiCraftingTreePlugin.getJeiRuntime();
        if (runtime == null || !isJeiOverlayVisible(runtime)) {
            clearBounds();
            return;
        }

        boolean hasTree = RecipeTreeOpenHelper.hasLastWorkspace();
        Component label = Component.translatable(hasTree
                ? "gui.jeict.recipe_tree.jei_shortcut_open"
                : "gui.jeict.recipe_tree.jei_shortcut_add");
        Minecraft minecraft = Minecraft.getInstance();
        int x = MARGIN + JEI_NATIVE_BUTTONS_WIDTH;
        int availableWidth = Math.max(20, screen.width - x - MARGIN);
        int width = Math.min(MAX_WIDTH, Math.min(availableWidth, Math.max(62, minecraft.font.width(label) + 12)));
        String visibleText = minecraft.font.plainSubstrByWidth(label.getString(), Math.max(1, width - 12));
        Component visibleLabel = Component.literal(visibleText);
        int y = Math.max(MARGIN, screen.height - HEIGHT - MARGIN);
        Bounds bounds = new Bounds(x, y, width, HEIGHT);
        lastBounds = bounds;
        lastScreen = screen;

        GuiGraphics graphics = event.getGuiGraphics();
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        boolean hovered = bounds.contains(mouseX, mouseY);
        int border = hovered ? 0xFFE0B04B : 0xFF727272;
        int background = hovered ? 0xE83A3020 : 0xD8202020;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 450.0F);
        graphics.fill(x, y, x + width, y + HEIGHT, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + HEIGHT - 1, background);
        graphics.drawCenteredString(minecraft.font, visibleLabel, x + width / 2, y + 6, 0xFFFFFFFF);
        if (hovered) {
            graphics.renderTooltip(minecraft.font,
                    List.of(Component.translatable(hasTree
                            ? "gui.jeict.recipe_tree.jei_shortcut_open_tooltip"
                            : "gui.jeict.recipe_tree.jei_shortcut_add_tooltip")),
                    Optional.empty(), (int) mouseX, (int) mouseY);
        }
        graphics.pose().popPose();
    }

    public static boolean handleMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0 || lastBounds == null || lastScreen != event.getScreen()
                || !lastBounds.contains(event.getMouseX(), event.getMouseY())) {
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

    private static boolean isJeiOverlayVisible(IJeiRuntime runtime) {
        if (runtime.getIngredientListOverlay().isListDisplayed()) return true;
        IBookmarkOverlay bookmarkOverlay = runtime.getBookmarkOverlay();
        try {
            Class<?> overlayClass = bookmarkOverlay.getClass();
            Method method = cachedBookmarkVisibilityMethod;
            if (method == null || cachedBookmarkOverlayClass != overlayClass) {
                method = overlayClass.getMethod("isListDisplayed");
                cachedBookmarkOverlayClass = overlayClass;
                cachedBookmarkVisibilityMethod = method;
            }
            Object result = method.invoke(bookmarkOverlay);
            return result instanceof Boolean displayed && displayed;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            cachedBookmarkOverlayClass = null;
            cachedBookmarkVisibilityMethod = null;
            return false;
        }
    }

    private static void clearBounds() {
        lastBounds = null;
        lastScreen = null;
    }

    private record Bounds(int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}

