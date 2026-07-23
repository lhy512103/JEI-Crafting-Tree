package com.lhy.jeict.jei;

import java.util.concurrent.atomic.AtomicLong;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.lhy.jeict.client.RecipeTreeOverviewScreen;
import com.lhy.jeict.recipe_tree.RecipeTreeNodeViewModel;
import com.lhy.jeict.recipe_tree.RecipeTreeRootContext;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;

public final class RecipeTreeOpenHelper {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicLong REQUEST_SEQUENCE = new AtomicLong();
    private static @Nullable PendingOpen pendingOpen;
    private static @Nullable RecipeTreeOverviewScreen observedTreeScreen;
    private static long observedRequestId;
    private static int observedTicks;

    private RecipeTreeOpenHelper() {
    }

    public static void openFromLayout(IRecipeLayoutDrawable<?> recipeLayout, Screen returnScreen) {
        open(recipeLayout.getRecipe(), recipeLayout.getRecipeSlotsView(), returnScreen);
    }

    public static void open(Object recipe, IRecipeSlotsView recipeSlots, Screen returnScreen) {
        long requestId = REQUEST_SEQUENCE.incrementAndGet();
        var rootRecipe = RecipeTreeJeiLookup.createRootSnapshot(recipe, recipeSlots);
        RecipeTreeOverviewScreen screen = new RecipeTreeOverviewScreen(
                new RecipeTreeRootContext(new RecipeTreeNodeViewModel(rootRecipe, null), returnScreen),
                returnScreen);
        pendingOpen = new PendingOpen(requestId, screen, returnScreen);
        LOGGER.info("[JEICT-SCREEN] queued request={} current={} return={} recipe={} thread={}",
                requestId, screenName(Minecraft.getInstance().screen), screenName(returnScreen),
                rootRecipe.stableIdentity(), Thread.currentThread().getName());
    }

    public static void onClientTickPost() {
        PendingOpen request = pendingOpen;
        if (request != null) {
            pendingOpen = null;
            Minecraft minecraft = Minecraft.getInstance();
            LOGGER.info("[JEICT-SCREEN] applying request={} current={} target={} return={}",
                    request.id(), screenName(minecraft.screen), screenName(request.screen()),
                    screenName(request.returnScreen()));
            observedTreeScreen = request.screen();
            observedRequestId = request.id();
            observedTicks = 0;
            minecraft.setScreen(request.screen());
            LOGGER.info("[JEICT-SCREEN] applied request={} resultingScreen={}",
                    request.id(), screenName(minecraft.screen));
        }

        RecipeTreeOverviewScreen observed = observedTreeScreen;
        if (observed == null) return;
        Screen current = Minecraft.getInstance().screen;
        if (current == observed) {
            observedTicks++;
            if (observedTicks == 40) {
                LOGGER.info("[JEICT-SCREEN] request={} remained active for 40 ticks; stopping transition tracing",
                        observedRequestId);
                observedTreeScreen = null;
            }
        } else {
            LOGGER.warn("[JEICT-SCREEN] request={} tree no longer active after {} ticks; current={}",
                    observedRequestId, observedTicks, screenName(current));
            observedTreeScreen = null;
        }
    }

    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen current = event.getCurrentScreen();
        Screen next = event.getNewScreen();
        boolean relevant = current instanceof RecipeTreeOverviewScreen
                || next instanceof RecipeTreeOverviewScreen
                || pendingOpen != null;
        if (!relevant) return;

        LOGGER.info("[JEICT-SCREEN] ScreenEvent.Opening current={} next={} pendingRequest={}",
                screenName(current), screenName(next), pendingOpen == null ? "none" : pendingOpen.id());
        if (current instanceof RecipeTreeOverviewScreen && !(next instanceof RecipeTreeOverviewScreen)) {
            LOGGER.warn("[JEICT-SCREEN] recipe tree is being replaced by {}; caller trace follows",
                    screenName(next), new IllegalStateException("JEICT temporary screen replacement trace"));
        }
    }

    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (!(event.getScreen() instanceof RecipeTreeOverviewScreen)) return;
        LOGGER.warn("[JEICT-SCREEN] ScreenEvent.Closing tree={} current={}; caller trace follows",
                screenName(event.getScreen()), screenName(Minecraft.getInstance().screen),
                new IllegalStateException("JEICT temporary screen closing trace"));
    }

    private static String screenName(@Nullable Screen screen) {
        return screen == null ? "<null>" : screen.getClass().getName();
    }

    private record PendingOpen(long id, RecipeTreeOverviewScreen screen, @Nullable Screen returnScreen) {
    }
}
