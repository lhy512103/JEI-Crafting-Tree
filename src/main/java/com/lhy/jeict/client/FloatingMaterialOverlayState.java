package com.lhy.jeict.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.config.RecipeTreeConfig;
import com.lhy.jeict.jei.JeiCraftingTreePlugin;
import com.lhy.jeict.network.CreativeRefillRequestPayload;
import com.lhy.jeict.recipe_tree.RecipeTreeRootContext;
import com.lhy.jeict.planning.MaterialKey;
import com.lhy.jeict.util.GenericIngredientUtil;
import com.lhy.jeict.util.IngredientIdentityUtil;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IRecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FloatingMaterialOverlayState {
    private static final int HEADER_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 16;
    private static final int CONTROL_SIZE = 12;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SCROLL_THUMB_HEIGHT = 15;
    private static final int CONTENT_PADDING = 6;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_ICON_PAD = 1;
    private static final int ICON_SIZE = 16;
    private static final int DEFAULT_COLUMNS = 6;
    private static final int MIN_COLUMNS = 3;
    private static final int MAX_COLUMNS = 16;
    private static final int CHAIN_INSET = 2;
    private static final int RESIZE_HANDLE = 10;
    private static final int DEFAULT_MAX_CONTENT_HEIGHT = 14 * SLOT_SIZE + CHAIN_INSET * 2;
    private static final int CHAIN_COLOR = 0xFF4FA3FF;
    private static final int MULTIPLIER_COLOR = 0xFFADADAD;
    private static final int CATALYST_MARKER_COLOR = 0xFFFFFF55;
    private static final int SCROLL_STEP = 18;
    private static final float OVERLAY_Z = 500.0F;
    private static final float TOOLTIP_Z = OVERLAY_Z + 400.0F;
    private static final int MAX_MISSING_TOOLTIP_LINES = 8;
    private static final long STOP_STATUS_MILLIS = 6000L;
    private static final float SHORTAGE_TINT_Z = 200.0F;
    private static final int MISSING_TINT = 0x44FF0000;
    private static final int PARTIAL_TINT = 0x44FFAA00;
    private static final int CRAFTABLE_TINT = 0x4455AAFF;
    private static final ResourceLocation MICRO_AMOUNT_FONT = ResourceLocation.withDefaultNamespace("uniform");

    private static Snapshot snapshot;
    private static int x = -1;
    private static int y = 8;
    private static int lastWidth;
    private static int lastHeight;
    private static int userWidth;
    private static int userHeight;
    private static float scale = 1.0F;
    private static boolean pinned;
    private static boolean dragging;
    private static boolean resizing;
    private static boolean leftMouseDown;
    private static double dragOffsetX;
    private static double dragOffsetY;

    private static int scrollOffset;
    private static int maxScrollOffset;
    private static boolean showAll;
    private static long lastTitleClickTime;
    private static List<DisplayGroup> cachedDisplayGroups = List.of();
    private static List<PlacedSlot> cachedSlots = List.of();
    private static int cachedRows;
    private static int cachedLayoutColumns = -1;
    private static long cachedInventoryVersion = Long.MIN_VALUE;
    private static boolean cachedShowAll;
    private static boolean displayEntriesDirty = true;
    private static List<DisplayEntry> cachedMissingRaw = List.of();

    private FloatingMaterialOverlayState() {
    }

    public static void set(Snapshot nextSnapshot) {
        snapshot = nextSnapshot;
        scrollOffset = 0;
        maxScrollOffset = 0;
        displayEntriesDirty = true;
        Minecraft minecraft = Minecraft.getInstance();
        if (x < 0 && minecraft.getWindow() != null) {
            x = Math.max(6, minecraft.getWindow().getGuiScaledWidth() - Math.round(widthForColumns(DEFAULT_COLUMNS) * scale) - 8);
            y = 8;
        }
    }

    public static void clear() {
        snapshot = null;
        dragging = false;
        resizing = false;
        leftMouseDown = false;
        scrollOffset = 0;
        maxScrollOffset = 0;
        cachedDisplayGroups = List.of();
        cachedSlots = List.of();
        cachedRows = 0;
        cachedLayoutColumns = -1;
        cachedMissingRaw = List.of();
        cachedInventoryVersion = Long.MIN_VALUE;
        displayEntriesDirty = true;
        CursorHelper.overlay(CursorHelper.Shape.ARROW);
    }

    public static void render(GuiGraphics graphics) {
        if (!RecipeTreeConfig.SHOW_FLOATING_MATERIALS.get()) {
            CursorHelper.overlay(CursorHelper.Shape.ARROW);
            return;
        }
        if (!isInWorld()) {
            clear();
            return;
        }
        if (snapshot == null || (snapshot.entries().isEmpty() && snapshot.tasks().isEmpty())) {
            CursorHelper.overlay(CursorHelper.Shape.ARROW);
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) {
            CursorHelper.overlay(CursorHelper.Shape.ARROW);
            return;
        }
        Font font = minecraft.font;
        refreshPanelSize();

        int totalContentHeight = chainContentHeight();
        int actualVisibleContentHeight = Math.max(0, lastHeight - HEADER_HEIGHT - 8 - FOOTER_HEIGHT);
        maxScrollOffset = Math.max(0, totalContentHeight - actualVisibleContentHeight);
        scrollOffset = Math.min(Math.max(0, scrollOffset), maxScrollOffset);
        clampToScreen();
        int height = lastHeight;

        int mouseX = (int) Math.round(scaledMouseX());
        int mouseY = (int) Math.round(scaledMouseY());
        double localMouseX = (mouseX - x) / scale;
        double localMouseY = (mouseY - y) / scale;

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, OVERLAY_Z);
        graphics.pose().scale(scale, scale, 1.0F);

        RecipeTreeTheme.Palette theme = RecipeTreeTheme.current();
        RecipeTreeTheme.drawFramedPanel(graphics, 0, 0, lastWidth, height);

        graphics.drawString(font, Component.translatable("gui.jeict.recipe_tree.floating_materials_title"), 22, 6,
                theme.metricText(), false);
        drawControl(graphics, 6, 5, pinned ? "P" : "p", pinned ? theme.pinned() : theme.controlText());
        drawControl(graphics, lastWidth - 44, 5, showAll ? "A" : "a", showAll ? theme.success() : theme.controlText());
        if (snapshot.context() != null) {
            drawControl(graphics, lastWidth - 31, 5, "\u2190", theme.accent());
        }
        drawControl(graphics, lastWidth - 18, 5, "x", theme.danger());

        DisplayEntry hoveredEntry = null;
        Component hoveredMachineName = null;

        int contentTop = HEADER_HEIGHT + 4;
        int contentBottom = height - 4 - FOOTER_HEIGHT;
        int chainX = CONTENT_PADDING + CHAIN_INSET;
        int chainY = contentTop - scrollOffset + CHAIN_INSET;
        PlacedSlot hoveredSlot = slotAt(localMouseX, localMouseY, chainX, chainY);
        if (hoveredSlot != null && (localMouseY < contentTop || localMouseY >= contentBottom
                || localMouseX < CONTENT_PADDING || localMouseX >= lastWidth - SCROLLBAR_WIDTH - 2)) {
            hoveredSlot = null;
        }
        if (hoveredSlot != null) {
            if (hoveredSlot.kind() == SlotKind.MACHINE) {
                hoveredMachineName = hoveredSlot.machineName();
            } else {
                hoveredEntry = hoveredSlot.entry();
            }
        }

        applyContentScissor(contentTop, contentBottom);
        drawChainBorder(graphics, chainX, chainY);
        for (PlacedSlot slot : cachedSlots) {
            int sx = chainX + slot.col() * SLOT_SIZE;
            int sy = chainY + slot.row() * SLOT_SIZE;
            if (sy + SLOT_SIZE <= contentTop || sy >= contentBottom) {
                continue;
            }
            if (slot == hoveredSlot) {
                graphics.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, theme.hoverFill());
            }
            drawPlacedSlot(graphics, font, slot, sx, sy);
        }
        if (hoveredSlot != null) {
            int hsx = chainX + hoveredSlot.col() * SLOT_SIZE;
            int hsy = chainY + hoveredSlot.row() * SLOT_SIZE;
            RecipeTreeTheme.drawBorder(graphics, hsx, hsy, hsx + SLOT_SIZE, hsy + SLOT_SIZE, theme.accent());
        }
        RenderSystem.disableScissor();

        if (maxScrollOffset > 0) {
            int trackX = lastWidth - SCROLLBAR_WIDTH - 2;
            int trackTop = contentTop;
            int trackBottom = contentBottom;
            int trackHeight = trackBottom - trackTop;
            if (trackHeight > 0) {
                graphics.fill(trackX, trackTop, trackX + 1, trackBottom, theme.scrollbarTrack());
                int thumbHeight = Math.min(SCROLL_THUMB_HEIGHT, trackHeight);
                int travel = Math.max(0, trackHeight - thumbHeight);
                int thumbY = trackTop + (maxScrollOffset > 0 && travel > 0
                        ? (int) ((long) scrollOffset * travel / maxScrollOffset)
                        : 0);
                graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, theme.scrollbarThumb());
            }
        }

        int craftY = autoCraftButtonTop(height);
        boolean creativeRefillVisible = canCreativeRefill();
        int craftLeft = actionCraftLeft();
        int refillLeft = actionRefillLeft();
        boolean creativeRefillHovered = creativeRefillVisible
                && localMouseX >= refillLeft && localMouseX < refillLeft + CONTROL_SIZE
                && localMouseY >= craftY && localMouseY < craftY + CONTROL_SIZE;
        if (creativeRefillVisible) {
            drawControl(graphics, refillLeft, craftY, "", theme.controlText());
            drawRefillIcon(graphics, refillLeft, craftY,
                    creativeRefillHovered ? theme.controlHoverText() : theme.success());
        }
        boolean craftHovered = localMouseX >= craftLeft && localMouseX < craftLeft + CONTROL_SIZE
                && localMouseY >= craftY && localMouseY < craftY + CONTROL_SIZE;
        boolean autoCraftRunning = RecipeTreeAutoCraftSession.status().running();
        drawControl(graphics, craftLeft, craftY, "", theme.controlText());
        drawCraftIcon(graphics, craftLeft, craftY, autoCraftRunning,
                craftHovered ? theme.controlHoverText() : (autoCraftRunning ? theme.danger() : theme.accent()));

        int statusLeft = CONTENT_PADDING;
        int statusRight = (creativeRefillVisible ? refillLeft : craftLeft) - 4;
        StatusLine status = statusLine(theme);
        int craftTextY = craftY + Math.max(0, (CONTROL_SIZE - font.lineHeight) / 2);
        graphics.drawString(font, font.plainSubstrByWidth(status.text().getString(), Math.max(0, statusRight - statusLeft)),
                statusLeft, craftTextY, status.color(), false);
        boolean statusHovered = localMouseX >= statusLeft && localMouseX < statusRight
                && localMouseY >= craftY && localMouseY < craftY + CONTROL_SIZE;
        List<Component> statusTooltip = statusHovered ? status.tooltip() : null;
        drawResizeGrip(graphics, lastWidth, height, theme.mutedText());

        graphics.pose().popPose();

        List<Component> controlTooltip = controlTooltipAt(localMouseX, localMouseY);
        if (controlTooltip != null || hoveredEntry != null || hoveredMachineName != null || statusTooltip != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, TOOLTIP_Z);
            if (controlTooltip != null) {
                graphics.renderTooltip(font, controlTooltip, java.util.Optional.empty(), mouseX, mouseY);
            } else if (hoveredMachineName != null) {
                graphics.renderTooltip(font, List.of(hoveredMachineName),
                        java.util.Optional.empty(), mouseX, mouseY);
            } else if (hoveredEntry == null) {
                graphics.renderTooltip(font, statusTooltip, java.util.Optional.empty(), mouseX, mouseY);
            } else {
                List<Component> tooltipLines = ingredientTooltipLines(hoveredEntry.source());
                if (!hoveredEntry.raw()) {
                    DisplayEntry hovered = hoveredEntry;
                    tooltipLines.add(Component.translatable(switch (hovered.state()) {
                        case ENOUGH -> "gui.jeict.recipe_tree.floating_state_enough";
                        case CRAFTABLE -> "gui.jeict.recipe_tree.floating_state_craftable";
                        default -> "gui.jeict.recipe_tree.floating_state_blocked";
                    }).withStyle(s -> s.withColor(stateColor(hovered.state(), theme))));
                }
                tooltipLines.add(Component.translatable("gui.jeict.recipe_tree.floating_available_amount",
                        formatDetailedAmount(hoveredEntry.source(), hoveredEntry.available()))
                        .withStyle(s -> s.withColor(0xFFAAAAAA)));
                tooltipLines.add(Component.translatable("gui.jeict.recipe_tree.floating_required_amount",
                        formatDetailedAmount(hoveredEntry.source(), hoveredEntry.need()))
                        .withStyle(s -> s.withColor(0xFFAAAAAA)));
                int missingAmountColor = hoveredEntry.remaining() > 0 ? 0xFFFF5555 : 0xFF55FF55;
                tooltipLines.add(Component.translatable("gui.jeict.recipe_tree.floating_missing_amount",
                        formatDetailedAmount(hoveredEntry.source(), hoveredEntry.remaining()))
                        .withStyle(s -> s.withColor(missingAmountColor)));
                tooltipLines.add(Component.translatable("gui.jeict.recipe_tree.floating_left_click_hint")
                        .withStyle(s -> s.withColor(0xFFAAAAAA).withItalic(true)));
                tooltipLines.add(Component.translatable("gui.jeict.recipe_tree.floating_right_click_hint")
                        .withStyle(s -> s.withColor(0xFFAAAAAA).withItalic(true)));
                graphics.renderTooltip(font, tooltipLines, java.util.Optional.empty(), mouseX, mouseY);
            }
            graphics.pose().popPose();
        }
        CursorHelper.overlay(pointerCursor(localMouseX, localMouseY, mouseX, mouseY));
    }

    public static boolean handleScreenMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!isInWorld()) {
            return false;
        }
        return handleMouseButton(event.getMouseX(), event.getMouseY(), event.getButton(), true, event);
    }

    public static boolean handleScreenMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!isInWorld()) {
            return false;
        }
        return handleMouseButton(event.getMouseX(), event.getMouseY(), event.getButton(), false, event);
    }

    public static boolean handleScreenMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (!isInWorld()) {
            return false;
        }
        if (handleDrag(event.getMouseX(), event.getMouseY(), event.getMouseButton())) {
            event.setCanceled(true);
            return true;
        }
        return false;
    }

    public static boolean handleMouseButton(InputEvent.MouseButton.Pre event) {
        if (!canHandleGlobalHudInput()) {
            return false;
        }
        double mouseX = scaledMouseX();
        double mouseY = scaledMouseY();
        boolean press = event.getAction() == InputConstants.PRESS;
        return handleMouseButton(mouseX, mouseY, event.getButton(), press, event);
    }

    public static boolean handleScreenMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (!isInWorld()) {
            return false;
        }
        if (handleScroll(event.getMouseX(), event.getMouseY(), event.getScrollDeltaY(), Screen.hasControlDown())) {
            event.setCanceled(true);
            return true;
        }
        return false;
    }

    public static boolean handleMouseScrolled(InputEvent.MouseScrollingEvent event) {
        if (!canHandleGlobalHudInput()) {
            return false;
        }
        if (handleScroll(event.getMouseX(), event.getMouseY(), event.getScrollDeltaY(), Screen.hasControlDown())) {
            event.setCanceled(true);
            return true;
        }
        return false;
    }

    public static void updateDrag() {
        if (!isInWorld()) {
            dragging = false;
            leftMouseDown = false;
            return;
        }
        if ((!dragging && !resizing) || snapshot == null) {
            return;
        }
        if (!leftMouseDown) {
            dragging = false;
            resizing = false;
            return;
        }
        if (resizing) {
            userWidth = clamp((int) Math.round((scaledMouseX() - x) / scale), minPanelWidth(), maxPanelWidth());
            userHeight = clamp((int) Math.round((scaledMouseY() - y) / scale), minPanelHeight(), maxPanelHeight());
            lastWidth = userWidth;
            lastHeight = userHeight;
            cachedLayoutColumns = -1;
            clampToScreen();
            return;
        }
        x = (int) Math.round(scaledMouseX() - dragOffsetX);
        y = (int) Math.round(scaledMouseY() - dragOffsetY);
        clampToScreen();
    }

    private static boolean handleMouseButton(double mouseX, double mouseY, int button, boolean press, Object event) {
        if (snapshot == null) {
            if (!press) {
                dragging = false;
                resizing = false;
                leftMouseDown = false;
            }
            return false;
        }
        if (!press) {
            dragging = false;
            resizing = false;
            leftMouseDown = false;
            return false;
        }

        if (button == 0) {
            leftMouseDown = true;
        }

        if (!contains(mouseX, mouseY)) {
            return false;
        }

        double localX = (mouseX - x) / scale;
        double localY = (mouseY - y) / scale;

        if (button == 0 && isOverResizeHandle(localX, localY)) {
            resizing = true;
            dragging = false;
            cancel(event);
            return true;
        }

        if (button == 0 && localX >= lastWidth - 18 && localX <= lastWidth - 6 && localY >= 5 && localY <= 17) {
            clear();
            cancel(event);
            return true;
        }

        if (button == 0 && localX >= lastWidth - 44 && localX <= lastWidth - 32 && localY >= 5 && localY <= 17) {
            showAll = !showAll;
            displayEntriesDirty = true;
            scrollOffset = 0;
            cancel(event);
            return true;
        }

        if (button == 0 && snapshot.context() != null
                && localX >= lastWidth - 31 && localX <= lastWidth - 19 && localY >= 5 && localY <= 17) {
            openRecipeTree();
            cancel(event);
            return true;
        }

        if (button == 0 && localX >= 6 && localX <= 18 && localY >= 5 && localY <= 17) {
            pinned = !pinned;
            cancel(event);
            return true;
        }

        if (button == 0 && isOverAutoCraftButton(localX, localY)) {
            runAutoCraft();
            cancel(event);
            return true;
        }

        if (button == 0 && isOverCreativeRefillButton(localX, localY)) {
            runCreativeRefill();
            cancel(event);
            return true;
        }

        if (localY >= HEADER_HEIGHT && localY < lastHeight - FOOTER_HEIGHT) {
            if (handleContentClick(localX, localY, button)) {
                cancel(event);
                return true;
            }
        }

        if (button == 0 && localY <= HEADER_HEIGHT) {
            long now = System.currentTimeMillis();
            if (now - lastTitleClickTime < 400) {
                scale = 1.0F;
                userWidth = 0;
                userHeight = 0;
                cachedLayoutColumns = -1;
                x = Math.max(6, Minecraft.getInstance().getWindow().getGuiScaledWidth()
                        - Math.round(widthForColumns(DEFAULT_COLUMNS) * scale) - 8);
                y = 8;
                clampToScreen();
                lastTitleClickTime = 0;
            } else {
                lastTitleClickTime = now;
            }
            dragging = true;
            dragOffsetX = mouseX - x;
            dragOffsetY = mouseY - y;
            cancel(event);
            return true;
        }

        cancel(event);
        return true;
    }

    public static boolean isAutoCraftButtonAt(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        return isOverAutoCraftButton((mouseX - x) / scale, (mouseY - y) / scale);
    }

    private static int autoCraftButtonTop(int height) {
        return height - FOOTER_HEIGHT - 1;
    }

    private static boolean isOverAutoCraftButton(double localX, double localY) {
        int craftY = autoCraftButtonTop(lastHeight);
        int left = actionCraftLeft();
        return localX >= left && localX < left + CONTROL_SIZE
                && localY >= craftY && localY < craftY + CONTROL_SIZE;
    }

    private static boolean isOverCreativeRefillButton(double localX, double localY) {
        int refillY = autoCraftButtonTop(lastHeight);
        int left = actionRefillLeft();
        return canCreativeRefill()
                && localX >= left && localX < left + CONTROL_SIZE
                && localY >= refillY && localY < refillY + CONTROL_SIZE;
    }

    private static boolean isOverResizeHandle(double localX, double localY) {
        return localX >= lastWidth - RESIZE_HANDLE && localX < lastWidth
                && localY >= lastHeight - RESIZE_HANDLE && localY < lastHeight;
    }

    private static CursorHelper.Shape pointerCursor(double localX, double localY, double mouseX, double mouseY) {
        if (resizing) {
            return CursorHelper.Shape.NWSE;
        }
        if (dragging) {
            return CursorHelper.Shape.HAND;
        }
        if (!contains(mouseX, mouseY)) {
            return CursorHelper.Shape.ARROW;
        }
        if (isOverResizeHandle(localX, localY)) {
            return CursorHelper.Shape.NWSE;
        }
        if (localY >= 0 && localY <= HEADER_HEIGHT && !isOverHeaderControl(localX, localY)) {
            return CursorHelper.Shape.HAND;
        }
        return CursorHelper.Shape.ARROW;
    }

    private static boolean isOverHeaderControl(double localX, double localY) {
        if (localY < 5 || localY > 17) {
            return false;
        }
        return (localX >= 6 && localX <= 18)
                || (localX >= lastWidth - 44 && localX <= lastWidth - 32)
                || (snapshot != null && snapshot.context() != null
                && localX >= lastWidth - 31 && localX <= lastWidth - 19)
                || (localX >= lastWidth - 18 && localX <= lastWidth - 6);
    }

    private static int actionCraftLeft() {
        return lastWidth - CONTENT_PADDING - RESIZE_HANDLE - CONTROL_SIZE;
    }

    private static int actionRefillLeft() {
        return actionCraftLeft() - 4 - CONTROL_SIZE;
    }

    private static void runAutoCraft() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        boolean wasRunning = RecipeTreeAutoCraftSession.status().running();
        boolean singleBatch = !wasRunning && Screen.hasShiftDown();
        boolean running = RecipeTreeAutoCraftSession.toggle(snapshot == null ? null : snapshot.context(),
                singleBatch ? 1 : Integer.MAX_VALUE);
        Component message = running
                ? Component.translatable(singleBatch
                        ? "message.jeict.auto_craft_started_once"
                        : "message.jeict.auto_craft_started")
                : wasRunning
                        ? Component.translatable("message.jeict.auto_craft_cancelled")
                        : Component.translatable("message.jeict.auto_craft_unavailable");
        minecraft.player.displayClientMessage(message, true);
    }

    private static void runCreativeRefill() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !canCreativeRefill()) return;
        var player = minecraft.player;
        var menu = player.containerMenu;
        List<net.minecraft.world.inventory.Slot> playerSlots = new ArrayList<>();
        List<net.minecraft.world.inventory.Slot> containerSlots = new ArrayList<>();
        for (var slot : menu.slots) {
            if (slot.container != player.getInventory()) {
                containerSlots.add(slot);
            } else if (CreativeRefillRequestPayload.isRefillTarget(player, slot)) {
                playerSlots.add(slot);
            }
        }
        List<List<net.minecraft.world.inventory.Slot>> tiers = Screen.hasShiftDown()
                ? List.of(containerSlots)
                : List.of(playerSlots, containerSlots);

        Map<Integer, ItemStack> planned = new LinkedHashMap<>();
        Map<Integer, Integer> beforeCounts = new LinkedHashMap<>();
        Map<Integer, MaterialKey> slotKeys = new LinkedHashMap<>();
        boolean shortOnSpace = false;
        Map<String, Long> byMaterial = missingByMaterial();
        Map<String, DisplayEntry> firstByMaterial = new LinkedHashMap<>();
        for (DisplayEntry entry : missingRaw()) firstByMaterial.putIfAbsent(entry.key(), entry);
        for (Map.Entry<String, Long> item : byMaterial.entrySet()) {
            DisplayEntry entry = firstByMaterial.get(item.getKey());
            if (entry == null) continue;
            ItemStack template = itemStack(entry.source());
            if (template.isEmpty()) continue;
            MaterialKey materialKey = materialKey(entry.source());
            long remaining = item.getValue();
            for (var tier : tiers) {
                remaining = planRefill(tier, template, remaining, planned, beforeCounts, slotKeys, materialKey, true);
                remaining = planRefill(tier, template, remaining, planned, beforeCounts, slotKeys, materialKey, false);
            }
            shortOnSpace |= remaining > 0L;
        }

        List<CreativeRefillRequestPayload.Fill> fills = new ArrayList<>();
        Map<MaterialKey, Long> expected = new LinkedHashMap<>();
        planned.forEach((slot, stack) -> {
            fills.add(new CreativeRefillRequestPayload.Fill(slot, stack));
            int added = stack.getCount() - beforeCounts.getOrDefault(slot, 0);
            MaterialKey key = slotKeys.get(slot);
            if (key != null && added > 0) {
                expected.merge(key, (long) added, FloatingMaterialOverlayState::saturatedAddLong);
            }
        });
        for (int from = 0; from < fills.size(); from += CreativeRefillRequestPayload.MAX_FILLS_PER_PACKET) {
            int to = Math.min(fills.size(), from + CreativeRefillRequestPayload.MAX_FILLS_PER_PACKET);
            PacketDistributor.sendToServer(new CreativeRefillRequestPayload(menu.containerId,
                    fills.subList(from, to)));
        }
        if (!expected.isEmpty()) {
            ClientInventorySnapshotCache.applyExpectedChanges(expected);
            displayEntriesDirty = true;
        }
        player.displayClientMessage(Component.translatable(fills.isEmpty()
                ? "message.jeict.creative_refill_no_space"
                : shortOnSpace ? "message.jeict.creative_refill_partial" : "message.jeict.creative_refill_sent",
                fills.size()), true);
    }

    /**
     * Adds up to {@code remaining} of {@code template} to the given slots, either topping up stacks that already hold
     * it or using empty slots, and returns what did not fit. Results accumulate in {@code planned} as final stacks.
     */
    private static long planRefill(List<net.minecraft.world.inventory.Slot> slots, ItemStack template,
            long remaining, Map<Integer, ItemStack> planned, Map<Integer, Integer> beforeCounts,
            Map<Integer, MaterialKey> slotKeys, @Nullable MaterialKey materialKey, boolean topUpOnly) {
        for (var slot : slots) {
            if (remaining <= 0L) break;
            ItemStack current = planned.getOrDefault(slot.index, slot.getItem());
            if (current.isEmpty() == topUpOnly || !slot.mayPlace(template)) continue;
            if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, template)) continue;
            int max = slot.getMaxStackSize(template);
            int space = max - current.getCount();
            if (space <= 0) continue;
            beforeCounts.putIfAbsent(slot.index, current.getCount());
            if (materialKey != null) slotKeys.putIfAbsent(slot.index, materialKey);
            int added = (int) Math.min(remaining, space);
            planned.put(slot.index, template.copyWithCount(current.getCount() + added));
            remaining -= added;
        }
        return remaining;
    }

    private static boolean canCreativeRefill() {
        Minecraft minecraft = Minecraft.getInstance();
        var connection = minecraft.getConnection();
        if (minecraft.player == null || !minecraft.player.getAbilities().instabuild
                || connection == null || !connection.hasChannel(CreativeRefillRequestPayload.TYPE)) return false;
        for (DisplayEntry entry : missingRaw()) {
            if (!itemStack(entry.source()).isEmpty()) return true;
        }
        return false;
    }

    private static ItemStack itemStack(Entry entry) {
        if (!entry.stack().isEmpty()) return entry.stack().copyWithCount(1);
        return entry.ingredient() == null ? ItemStack.EMPTY
                : entry.ingredient().getIngredient(VanillaTypes.ITEM_STACK)
                        .map(stack -> stack.copyWithCount(1)).orElse(ItemStack.EMPTY);
    }

    private static boolean handleContentClick(double localX, double localY, int button) {
        displayGroups();
        if (cachedSlots.isEmpty()) {
            return false;
        }
        int contentTop = HEADER_HEIGHT + 4;
        int chainX = CONTENT_PADDING + CHAIN_INSET;
        int chainY = contentTop - scrollOffset + CHAIN_INSET;
        PlacedSlot hit = slotAt(localX, localY, chainX, chainY);
        if (hit == null) {
            return false;
        }
        if (hit.kind() != SlotKind.MACHINE && hit.entry() != null) {
            openJeiForEntry(hit.entry().source(), button);
        }
        return true;
    }

    private static @Nullable PlacedSlot slotAt(double localX, double localY, int chainX, int chainY) {
        int gx = (int) Math.floor(localX - chainX);
        int gy = (int) Math.floor(localY - chainY);
        if (gx < 0 || gy < 0) {
            return null;
        }
        int col = gx / SLOT_SIZE;
        int row = gy / SLOT_SIZE;
        if (col >= currentColumns()) {
            return null;
        }
        for (PlacedSlot slot : cachedSlots) {
            if (slot.col() == col && slot.row() == row) {
                return slot;
            }
        }
        return null;
    }

    private static void openJeiForEntry(Entry entry, int button) {
        IJeiRuntime runtime = JeiCraftingTreePlugin.getJeiRuntime();
        if (runtime == null) {
            return;
        }
        ITypedIngredient<?> ingredient = entry.ingredient();
        if (ingredient == null && !entry.stack().isEmpty()) {
            ingredient = runtime.getIngredientManager().createTypedIngredient(entry.stack().copyWithCount(1), true)
                    .orElse(null);
        }
        if (ingredient == null) {
            return;
        }
        IFocusFactory focusFactory = runtime.getJeiHelpers().getFocusFactory();
        RecipeIngredientRole role = (button == 1) ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.INPUT;
        IFocus<?> focus = createFocus(focusFactory, ingredient, role);
        IRecipesGui recipesGui = runtime.getRecipesGui();
        if (recipesGui != null) {
            recipesGui.show(focus);
        }
    }

    private static <T> IFocus<T> createFocus(IFocusFactory focusFactory, ITypedIngredient<?> ingredient, RecipeIngredientRole role) {
        @SuppressWarnings("unchecked")
        ITypedIngredient<T> typed = (ITypedIngredient<T>) ingredient;
        return focusFactory.createFocus(role, typed);
    }

    private static void openRecipeTree() {
        RecipeTreeRootContext context = snapshot.context();
        if (context == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new RecipeTreeOverviewScreen(context, null));
    }

    private static boolean handleDrag(double mouseX, double mouseY, int button) {
        if (button != 0 || snapshot == null) {
            return false;
        }
        if (resizing) {
            userWidth = clamp((int) Math.round((mouseX - x) / scale), minPanelWidth(), maxPanelWidth());
            userHeight = clamp((int) Math.round((mouseY - y) / scale), minPanelHeight(), maxPanelHeight());
            lastWidth = userWidth;
            lastHeight = userHeight;
            cachedLayoutColumns = -1;
            clampToScreen();
            return true;
        }
        if (!dragging) {
            return false;
        }
        x = (int) Math.round(mouseX - dragOffsetX);
        y = (int) Math.round(mouseY - dragOffsetY);
        clampToScreen();
        return true;
    }

    private static boolean handleScroll(double mouseX, double mouseY, double delta, boolean ctrlDown) {
        if (snapshot == null || !contains(mouseX, mouseY)) {
            return false;
        }
        if (ctrlDown) {
            float oldScale = scale;
            scale = Math.max(0.5F, Math.min(2.5F, scale + (float) delta * 0.08F));
            double localX = (mouseX - x) / oldScale;
            double localY = (mouseY - y) / oldScale;
            x = (int) Math.round(mouseX - localX * scale);
            y = (int) Math.round(mouseY - localY * scale);
            clampToScreen();
        } else {
            scrollOffset -= (int) (delta * SCROLL_STEP);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
        }
        return true;
    }

    private static void cancel(Object event) {
        if (event instanceof ScreenEvent.MouseButtonPressed.Pre pre) {
            pre.setCanceled(true);
        } else if (event instanceof InputEvent.MouseButton.Pre pre) {
            pre.setCanceled(true);
        }
    }

    private static boolean contains(double mouseX, double mouseY) {
        return snapshot != null
                && mouseX >= x && mouseX <= x + lastWidth * scale
                && mouseY >= y && mouseY <= y + lastHeight * scale;
    }

    private static boolean isInWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.player != null && minecraft.level != null;
    }

    private static boolean canHandleGlobalHudInput() {
        Minecraft minecraft = Minecraft.getInstance();
        return isInWorld() && (minecraft.screen == null || minecraft.screen instanceof ChatScreen);
    }

    private static void clampToScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int scaledWidth = Math.round(lastWidth * scale);
        int scaledHeight = Math.round(Math.max(HEADER_HEIGHT + 8, lastHeight) * scale);
        x = Math.max(0, Math.min(x, Math.max(0, screenWidth - scaledWidth)));
        y = Math.max(0, Math.min(y, Math.max(0, screenHeight - scaledHeight)));
    }

    private static double scaledMouseX() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
    }

    private static double scaledMouseY() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
    }

    private static int chainContentHeight() {
        return cachedRows <= 0 ? 0 : cachedRows * SLOT_SIZE + CHAIN_INSET * 2;
    }

    private static void refreshPanelSize() {
        lastWidth = clamp(userWidth > 0 ? userWidth : widthForColumns(DEFAULT_COLUMNS),
                minPanelWidth(), maxPanelWidth());
        displayGroups();
        layoutCurrentColumns();
        int autoHeight = HEADER_HEIGHT + 4
                + Math.min(chainContentHeight(), DEFAULT_MAX_CONTENT_HEIGHT)
                + 4 + FOOTER_HEIGHT;
        lastHeight = clamp(userHeight > 0 ? userHeight : autoHeight, minPanelHeight(), maxPanelHeight());
    }

    private static int currentColumns() {
        int width = lastWidth > 0 ? lastWidth : widthForColumns(DEFAULT_COLUMNS);
        int inner = width - CONTENT_PADDING * 2 - CHAIN_INSET * 2 - SCROLLBAR_WIDTH - 2;
        return clamp(inner / SLOT_SIZE, MIN_COLUMNS, MAX_COLUMNS);
    }

    private static int widthForColumns(int columns) {
        return CONTENT_PADDING * 2 + CHAIN_INSET * 2 + columns * SLOT_SIZE + SCROLLBAR_WIDTH + 2;
    }

    private static int minPanelWidth() {
        return Math.max(widthForColumns(MIN_COLUMNS), 6 + CONTROL_SIZE + 4 + 48 + 44);
    }

    private static int minPanelHeight() {
        return HEADER_HEIGHT + 4 + SLOT_SIZE * 2 + CHAIN_INSET * 2 + 4 + FOOTER_HEIGHT;
    }

    private static int maxPanelWidth() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() == null) {
            return widthForColumns(MAX_COLUMNS);
        }
        return Math.max(minPanelWidth(), (int) (minecraft.getWindow().getGuiScaledWidth() / scale));
    }

    private static int maxPanelHeight() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() == null) {
            return minPanelHeight() + DEFAULT_MAX_CONTENT_HEIGHT;
        }
        return Math.max(minPanelHeight(), (int) (minecraft.getWindow().getGuiScaledHeight() / scale));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Window-pixel scissor so JEI's leftover GuiGraphics scissor cannot hide the chain. */
    private static void applyContentScissor(int contentTop, int contentBottom) {
        Minecraft minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        double scaleX = (double) window.getWidth() / window.getGuiScaledWidth();
        double scaleY = (double) window.getHeight() / window.getGuiScaledHeight();
        int x1 = (int) Math.floor((x + CONTENT_PADDING * scale) * scaleX);
        int y1 = (int) Math.floor((y + contentTop * scale) * scaleY);
        int x2 = (int) Math.ceil((x + (lastWidth - SCROLLBAR_WIDTH - 2) * scale) * scaleX);
        int y2 = (int) Math.ceil((y + contentBottom * scale) * scaleY);
        int width = Math.max(0, x2 - x1);
        int height = Math.max(0, y2 - y1);
        RenderSystem.enableScissor(x1, window.getHeight() - y2, width, height);
    }

    private static List<DisplayGroup> displayGroups() {
        long inventoryVersion = ClientInventorySnapshotCache.version();
        if (!displayEntriesDirty && inventoryVersion == cachedInventoryVersion && showAll == cachedShowAll) {
            return cachedDisplayGroups;
        }
        var inventory = ClientInventorySnapshotCache.get();
        List<DisplayGroup> groups = snapshot.tasks().isEmpty() ? flatGroups(inventory) : chainGroups(inventory);
        List<DisplayEntry> missing = new ArrayList<>();
        for (DisplayGroup group : groups) {
            for (DisplayEntry entry : group.entries()) {
                if (entry.raw() && entry.remaining() > 0L) missing.add(entry);
            }
        }
        cachedDisplayGroups = List.copyOf(groups);
        cachedLayoutColumns = -1;
        layoutCurrentColumns();
        cachedMissingRaw = List.copyOf(missing);
        cachedInventoryVersion = inventoryVersion;
        cachedShowAll = showAll;
        displayEntriesDirty = false;
        return cachedDisplayGroups;
    }

    /** Raw materials that are still short, across every step of the chain. */
    private static List<DisplayEntry> missingRaw() {
        displayGroups();
        return cachedMissingRaw;
    }

    /** Fallback for snapshots without a task tree: one step-less group per machine. */
    private static List<DisplayGroup> flatGroups(com.lhy.jeict.planning.InventorySnapshot inventory) {
        Map<String, List<DisplayEntry>> grouped = new LinkedHashMap<>();
        Map<String, Entry> first = new LinkedHashMap<>();
        int index = 0;
        for (Entry entry : snapshot.entries()) {
            long available = availableAmount(inventory, entry);
            long remaining = Math.max(0L, (long) entry.count() - available);
            if (!showAll && remaining <= 0L) continue;
            State state = remaining <= 0L ? State.ENOUGH : available > 0L ? State.PARTIAL : State.MISSING;
            grouped.computeIfAbsent(entry.machineKey(), ignored -> new ArrayList<>())
                    .add(displayEntry(entry, "flat" + index++, entry.count(), available, state, true));
            first.putIfAbsent(entry.machineKey(), entry);
        }
        List<DisplayGroup> groups = new ArrayList<>(grouped.size());
        grouped.forEach((key, entries) -> {
            State state = State.ENOUGH;
            for (DisplayEntry entry : entries) state = worse(state, entry.state());
            Entry source = first.get(key);
            groups.add(new DisplayGroup(source.machineIcon(), source.machineName(), null, List.copyOf(entries),
                    0L, state));
        });
        return groups;
    }

    private static List<DisplayGroup> chainGroups(com.lhy.jeict.planning.InventorySnapshot inventory) {
        Map<MaterialKey, Long> available = new LinkedHashMap<>(inventory.amounts());
        Map<String, StepBuilder> steps = new LinkedHashMap<>();
        Set<Task> path = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Task task : snapshot.tasks()) {
            evaluate(task, task.requiredAmount(), available, path, steps);
        }
        List<DisplayGroup> groups = new ArrayList<>(steps.size());
        for (StepBuilder step : steps.values()) groups.add(step.freeze());
        return groups;
    }

    /**
     * Allocates stock to the task and, when more has to be crafted, to its inputs, recording every crafting
     * step in dependency order (inputs before the step that consumes them).
     */
    private static Result evaluate(Task task, long required, Map<MaterialKey, Long> available, Set<Task> path,
            Map<String, StepBuilder> steps) {
        long owned = Math.max(0L, available.getOrDefault(task.outputKey(), 0L));
        long used = Math.min(owned, required);
        if (used > 0L) available.put(task.outputKey(), owned - used);
        long missing = required - used;
        boolean leaf = task.inputs().isEmpty();
        if (missing <= 0L) {
            if (!leaf && showAll) {
                long crafts = ceilDiv(required, task.outputPerCraft());
                StepBuilder done = new StepBuilder(task, required, used, State.ENOUGH);
                for (Task input : task.inputs()) {
                    long need = input.consumed() ? saturatedMultiply(input.requiredAmount(), crafts)
                            : input.requiredAmount();
                    done.addInput(input, need, need, State.ENOUGH, input.inputs().isEmpty());
                }
                addStep(steps, done);
            }
            return new Result(State.ENOUGH, used, false);
        }
        State own = used > 0L ? State.PARTIAL : State.MISSING;
        if (leaf || !path.add(task)) return new Result(own, used, true);

        long crafts = ceilDiv(missing, task.outputPerCraft());
        boolean ready = true;
        List<InputResult> inputResults = new ArrayList<>();
        for (Task input : task.inputs()) {
            long need = input.consumed() ? saturatedMultiply(input.requiredAmount(), crafts)
                    : input.requiredAmount();
            Result result = evaluate(input, need, available, path, steps);
            ready &= result.state() == State.ENOUGH;
            inputResults.add(new InputResult(input, need, result));
        }
        path.remove(task);
        long produced = saturatedMultiply(crafts, task.outputPerCraft());
        if (produced > missing) {
            available.merge(task.outputKey(), produced - missing, FloatingMaterialOverlayState::saturatedAddLong);
        }
        State state = ready ? State.CRAFTABLE : own;
        StepBuilder step = new StepBuilder(task, required, used, state);
        for (InputResult item : inputResults) {
            step.addInput(item.task(), item.need(), item.result().used(), item.result().state(),
                    item.result().leaf());
        }
        addStep(steps, step);
        return new Result(state, used, false);
    }

    private static void addStep(Map<String, StepBuilder> steps, StepBuilder step) {
        String key = step.task.identity().isEmpty() ? "#" + steps.size() : step.task.identity();
        StepBuilder existing = steps.get(key);
        if (existing == null) steps.put(key, step);
        else existing.merge(step);
    }

    private static State worse(State left, State right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }

    private static int stateColor(State state, RecipeTreeTheme.Palette theme) {
        return switch (state) {
            case ENOUGH -> theme.enough();
            case CRAFTABLE -> theme.accent();
            case PARTIAL -> theme.partial();
            case MISSING -> theme.missing();
        };
    }

    private static DisplayEntry displayEntry(Entry template, String key, long need, long have, State state,
            boolean raw) {
        long safeNeed = Math.max(1L, need);
        long safeHave = Math.max(0L, Math.min(have, safeNeed));
        long remaining = safeNeed - safeHave;
        Entry source = template.withCount(safeNeed);
        long shown = state == State.ENOUGH ? safeNeed : remaining;
        Component badge = Component.literal(formatEntryAmount(source, shown).replace(" ", ""))
                .withStyle(style -> style.withFont(MICRO_AMOUNT_FONT));
        return new DisplayEntry(source, key, safeNeed, safeHave, remaining, state, raw, badge);
    }

    private static final class StepBuilder {
        private final Task task;
        private long required;
        private long used;
        private State state;
        private final Map<String, InputBuilder> inputs = new LinkedHashMap<>();

        private StepBuilder(Task task, long required, long used, State state) {
            this.task = task;
            this.required = required;
            this.used = used;
            this.state = state;
        }

        private void addInput(Task input, long need, long have, State inputState, boolean raw) {
            String key = input.outputKey().encoded() + (raw ? "#raw" : "#step");
            InputBuilder builder = inputs.computeIfAbsent(key, ignored -> new InputBuilder(input, raw));
            builder.need = saturatedAddLong(builder.need, need);
            builder.have = saturatedAddLong(builder.have, have);
            builder.state = worse(builder.state, inputState);
        }

        private void merge(StepBuilder other) {
            required = saturatedAddLong(required, other.required);
            used = saturatedAddLong(used, other.used);
            state = worse(state, other.state);
            for (Map.Entry<String, InputBuilder> entry : other.inputs.entrySet()) {
                InputBuilder source = entry.getValue();
                InputBuilder builder = inputs.computeIfAbsent(entry.getKey(),
                        ignored -> new InputBuilder(source.task, source.raw));
                builder.need = saturatedAddLong(builder.need, source.need);
                builder.have = saturatedAddLong(builder.have, source.have);
                builder.state = worse(builder.state, source.state);
            }
        }

        private DisplayGroup freeze() {
            long missing = Math.max(0L, required - used);
            long crafts = state == State.ENOUGH
                    ? ceilDiv(required, task.outputPerCraft())
                    : ceilDiv(missing, task.outputPerCraft());
            List<DisplayEntry> entries = new ArrayList<>(inputs.size());
            for (Map.Entry<String, InputBuilder> entry : inputs.entrySet()) {
                InputBuilder builder = entry.getValue();
                long need = builder.task.consumed()
                        ? saturatedMultiply(builder.task.requiredAmount(), Math.max(1L, crafts))
                        : builder.task.requiredAmount();
                if (need <= 0L) need = builder.need;
                long have = Math.min(builder.have, need);
                entries.add(displayEntry(builder.task.output(), entry.getKey(), need, have,
                        builder.state, builder.raw));
            }
            Entry output = task.output();
            return new DisplayGroup(output.machineIcon(), output.machineName(),
                    displayEntry(output, task.identity() + "#out", required, used, state, false),
                    List.copyOf(entries), crafts, state);
        }
    }

    private static final class InputBuilder {
        private final Task task;
        private final boolean raw;
        private long need;
        private long have;
        private State state = State.ENOUGH;

        private InputBuilder(Task task, boolean raw) {
            this.task = task;
            this.raw = raw;
        }
    }

    private record Result(State state, long used, boolean leaf) {
    }

    private record InputResult(Task task, long need, Result result) {
    }

    private static long saturatedAddLong(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static long ceilDiv(long numerator, long denominator) {
        if (numerator <= 0L) return 0L;
        long safeDenominator = Math.max(1L, denominator);
        return 1L + (numerator - 1L) / safeDenominator;
    }

    private static long saturatedMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static long availableAmount(com.lhy.jeict.planning.InventorySnapshot inventory, Entry entry) {
        MaterialKey key = materialKey(entry);
        return key == null ? 0L : inventory.amount(key);
    }

    private static @Nullable MaterialKey materialKey(Entry entry) {
        IJeiRuntime runtime = JeiCraftingTreePlugin.getJeiRuntime();
        if (runtime == null) return null;
        ITypedIngredient<?> ingredient = entry.ingredient();
        if (ingredient == null && !entry.stack().isEmpty()) {
            ingredient = runtime.getIngredientManager().createTypedIngredient(entry.stack().copyWithCount(1), true)
                    .orElse(null);
        }
        if (ingredient == null) return null;
        return IngredientIdentityUtil.keyOf(runtime.getIngredientManager(), ingredient);
    }

    private static void layoutCurrentColumns() {
        int columns = currentColumns();
        if (columns == cachedLayoutColumns) {
            return;
        }
        cachedSlots = layoutSlots(cachedDisplayGroups, columns);
        cachedRows = countRows(cachedSlots);
        cachedLayoutColumns = columns;
    }

    private static List<PlacedSlot> layoutSlots(List<DisplayGroup> groups, int columns) {
        List<PlacedSlot> slots = new ArrayList<>();
        int col = 0;
        int row = 0;
        boolean rowOccupied = false;
        for (DisplayGroup group : groups) {
            if (rowOccupied) {
                row++;
                col = 0;
                rowOccupied = false;
            }
            boolean firstInStep = true;
            for (DisplayEntry entry : group.entries()) {
                int[] pos = nextCell(col, row, firstInStep, columns);
                col = pos[0];
                row = pos[1];
                firstInStep = false;
                slots.add(new PlacedSlot(SlotKind.INPUT, entry, col, row, group.crafts(), null, null));
                col++;
                rowOccupied = true;
            }
            if (group.output() != null) {
                int[] pos = nextCell(col, row, firstInStep, columns);
                col = pos[0];
                row = pos[1];
                firstInStep = false;
                slots.add(new PlacedSlot(SlotKind.OUTPUT, group.output(), col, row, group.crafts(), null, null));
                col++;
                rowOccupied = true;
            }
            if (group.machineIcon() != null) {
                int[] pos = nextCell(col, row, firstInStep, columns);
                col = pos[0];
                row = pos[1];
                slots.add(new PlacedSlot(SlotKind.MACHINE, null, col, row, group.crafts(),
                        group.machineIcon(), group.machineName()));
                col++;
                rowOccupied = true;
            }
        }
        return List.copyOf(slots);
    }

    /** Continues a step in the current cell, wrapping with a one-column indent. */
    private static int[] nextCell(int col, int row, boolean firstInStep, int columns) {
        if (!firstInStep && col >= columns) {
            return new int[] { columns > 1 ? 1 : 0, row + 1 };
        }
        return new int[] { col, row };
    }

    private static int countRows(List<PlacedSlot> slots) {
        int rows = 0;
        for (PlacedSlot slot : slots) {
            rows = Math.max(rows, slot.row() + 1);
        }
        return rows;
    }

    private static void drawChainBorder(GuiGraphics graphics, int chainX, int chainY) {
        if (cachedSlots.isEmpty()) {
            return;
        }
        Set<Long> occupied = new HashSet<>();
        for (PlacedSlot slot : cachedSlots) {
            occupied.add(cellKey(slot.col(), slot.row()));
        }
        for (PlacedSlot slot : cachedSlots) {
            int sx = chainX + slot.col() * SLOT_SIZE;
            int sy = chainY + slot.row() * SLOT_SIZE;
            if (!occupied.contains(cellKey(slot.col() - 1, slot.row()))) {
                graphics.fill(sx, sy, sx + 1, sy + SLOT_SIZE, CHAIN_COLOR);
            }
            if (!occupied.contains(cellKey(slot.col() + 1, slot.row()))) {
                graphics.fill(sx + SLOT_SIZE - 1, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, CHAIN_COLOR);
            }
            if (!occupied.contains(cellKey(slot.col(), slot.row() - 1))) {
                graphics.fill(sx, sy, sx + SLOT_SIZE, sy + 1, CHAIN_COLOR);
            }
            if (!occupied.contains(cellKey(slot.col(), slot.row() + 1))) {
                graphics.fill(sx, sy + SLOT_SIZE - 1, sx + SLOT_SIZE, sy + SLOT_SIZE, CHAIN_COLOR);
            }
        }
    }

    private static long cellKey(int col, int row) {
        return ((long) row << 32) ^ (col & 0xFFFFFFFFL);
    }

    private static void drawPlacedSlot(GuiGraphics graphics, Font font, PlacedSlot slot, int sx, int sy) {
        int iconX = sx + SLOT_ICON_PAD;
        int iconY = sy + SLOT_ICON_PAD;
        if (slot.kind() == SlotKind.MACHINE) {
            renderMachineSlot(graphics, font, slot.machineIcon(), iconX, iconY);
            return;
        }
        if (slot.entry() == null) {
            return;
        }
        drawEntry(graphics, font, slot.entry(), iconX, iconY);
        if (slot.kind() == SlotKind.OUTPUT && slot.crafts() > 0L) {
            renderCornerLabel(graphics, font, "x" + formatCompactCount(slot.crafts()),
                    sx + 1, sy + 1, MULTIPLIER_COLOR);
        }
    }

    private static void renderMachineSlot(GuiGraphics graphics, Font font, @Nullable IDrawable icon, int x, int y) {
        if (icon != null) {
            graphics.pose().pushPose();
            float iconScale = ICON_SIZE / (float) Math.max(1, Math.max(icon.getWidth(), icon.getHeight()));
            graphics.pose().translate(x, y, 0.0F);
            graphics.pose().scale(iconScale, iconScale, 1.0F);
            icon.draw(graphics, 0, 0);
            graphics.pose().popPose();
        }
        renderCornerLabel(graphics, font, "C", x, y, CATALYST_MARKER_COLOR);
    }

    private static void renderCornerLabel(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        float textScale = 0.75F;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 300.0F);
        graphics.pose().scale(textScale, textScale, 1.0F);
        graphics.drawString(font, text, 1, 1, 0xFF000000, false);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static void drawControl(GuiGraphics graphics, int x, int y, String text, int color) {
        RecipeTreeTheme.drawSmallControl(graphics, x, y, CONTROL_SIZE, false);
        Font font = Minecraft.getInstance().font;
        int textY = y + Math.max(0, (CONTROL_SIZE - font.lineHeight) / 2);
        graphics.drawCenteredString(font, text, x + CONTROL_SIZE / 2, textY, color);
    }

    private static @Nullable List<Component> controlTooltipAt(double localMouseX, double localMouseY) {
        if (isOverResizeHandle(localMouseX, localMouseY)) {
            return List.of(Component.translatable("gui.jeict.recipe_tree.floating_resize_tooltip"));
        }
        if (isOverAutoCraftButton(localMouseX, localMouseY)) {
            if (RecipeTreeAutoCraftSession.status().running()) {
                return List.of(Component.translatable("gui.jeict.recipe_tree.floating_auto_craft_stop_tooltip"));
            }
            return List.of(
                    Component.translatable("gui.jeict.recipe_tree.floating_auto_craft_tooltip"),
                    Component.translatable("gui.jeict.recipe_tree.floating_auto_craft_tooltip_shift")
                            .withStyle(s -> s.withColor(0xFFAAAAAA)));
        }
        if (isOverCreativeRefillButton(localMouseX, localMouseY)) {
            return List.of(
                    Component.translatable("gui.jeict.recipe_tree.floating_creative_refill_tooltip"),
                    Component.translatable("gui.jeict.recipe_tree.floating_creative_refill_tooltip_shift")
                            .withStyle(s -> s.withColor(0xFFAAAAAA)));
        }
        if (localMouseY < 5 || localMouseY > 17) {
            return null;
        }
        if (localMouseX >= 6 && localMouseX <= 18) {
            return List.of(Component.translatable(pinned
                    ? "gui.jeict.recipe_tree.floating_unpin_tooltip"
                    : "gui.jeict.recipe_tree.floating_pin_tooltip"));
        }
        if (localMouseX >= 22 && localMouseX < lastWidth - 44) {
            return List.of(Component.translatable("gui.jeict.recipe_tree.floating_scale_tooltip",
                    Math.round(scale * 100.0F)));
        }
        if (localMouseX >= lastWidth - 44 && localMouseX <= lastWidth - 32) {
            return List.of(Component.translatable(showAll
                    ? "gui.jeict.recipe_tree.floating_missing_only_tooltip"
                    : "gui.jeict.recipe_tree.floating_show_all_tooltip"));
        }
        if (snapshot.context() != null
                && localMouseX >= lastWidth - 31 && localMouseX <= lastWidth - 19) {
            return List.of(Component.translatable("gui.jeict.recipe_tree.floating_back_tooltip"));
        }
        if (localMouseX >= lastWidth - 18 && localMouseX <= lastWidth - 6) {
            return List.of(Component.translatable("gui.jeict.recipe_tree.floating_close_tooltip"));
        }
        return null;
    }

    private static void drawCraftIcon(GuiGraphics graphics, int x, int y, boolean stop, int color) {
        if (stop) {
            graphics.fill(x + 3, y + 3, x + 9, y + 9, color);
            return;
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int px = x + 2 + col * 3;
                int py = y + 2 + row * 3;
                graphics.fill(px, py, px + 2, py + 2, color);
            }
        }
    }

    private static void drawRefillIcon(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x + 5, y + 2, x + 7, y + 10, color);
        graphics.fill(x + 2, y + 5, x + 10, y + 7, color);
    }

    private static void drawResizeGrip(GuiGraphics graphics, int width, int height, int color) {
        for (int row = 0; row < 3; row++) {
            int dots = 3 - row;
            for (int col = 0; col < dots; col++) {
                int gx = width - 3 - col * 3;
                int gy = height - 3 - row * 3;
                graphics.fill(gx, gy, gx + 2, gy + 2, color);
            }
        }
    }

    private static void renderEntryIngredient(GuiGraphics graphics, Entry entry, int x, int y) {
        if (!entry.stack().isEmpty()) {
            graphics.renderItem(entry.stack().copyWithCount(1), x, y);
            return;
        }
        ITypedIngredient<?> ingredient = entry.ingredient();
        IJeiRuntime runtime = JeiCraftingTreePlugin.getJeiRuntime();
        if (ingredient == null || runtime == null) {
            return;
        }
        IIngredientManager ingredientManager = runtime.getIngredientManager();
        FluidStack renderFluid = entry.renderFluid();
        if (renderFluid != null && !renderFluid.isEmpty()) {
            IIngredientRenderer<FluidStack> renderer = ingredientManager.getIngredientRenderer(NeoForgeTypes.FLUID_STACK);
            renderer.render(graphics, renderFluid, x, y);
            return;
        }
        renderTypedIngredient(graphics, ingredientManager, ingredient, x, y);
    }

    private static void drawEntry(GuiGraphics graphics, Font font, DisplayEntry entry, int x, int y) {
        renderEntryIngredient(graphics, entry.source(), x, y);
        renderEntryTint(graphics, entry, x, y);
        renderEntryAmount(graphics, font, entry, x, y);
    }

    private static void renderEntryTint(GuiGraphics graphics, DisplayEntry entry, int x, int y) {
        int tint = switch (entry.state()) {
            case ENOUGH -> 0;
            case CRAFTABLE -> CRAFTABLE_TINT;
            case PARTIAL -> PARTIAL_TINT;
            case MISSING -> MISSING_TINT;
        };
        if (tint == 0) return;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, SHORTAGE_TINT_Z);
        graphics.fill(x, y, x + ICON_SIZE, y + ICON_SIZE, tint);
        graphics.pose().popPose();
    }



    private static StatusLine statusLine(RecipeTreeTheme.Palette theme) {
        RecipeTreeAutoCraftSession.Status status = RecipeTreeAutoCraftSession.status();
        if (status.running()) {
            return new StatusLine(
                    Component.translatable("gui.jeict.recipe_tree.floating_status_running", status.operations()),
                    theme.accent(), List.of(Component.translatable("message.jeict.auto_craft_started")));
        }
        RecipeTreeAutoCraftSession.StopReason reason = status.stopReason();
        if (reason != null && System.currentTimeMillis() - status.stoppedAtMillis() < STOP_STATUS_MILLIS) {
            int color = RecipeTreeAutoCraftSession.isSuccess(reason) ? theme.success()
                    : reason == RecipeTreeAutoCraftSession.StopReason.CANCELLED ? theme.mutedText() : theme.missing();
            return new StatusLine(
                    Component.translatable("gui.jeict.recipe_tree.floating_status_stop."
                            + reason.name().toLowerCase(java.util.Locale.ROOT)),
                    color, List.of(RecipeTreeAutoCraftSession.stopMessage(reason)));
        }
        int kinds = missingByMaterial().size();
        if (kinds == 0) {
            return new StatusLine(Component.translatable("gui.jeict.recipe_tree.floating_status_ready"),
                    theme.success(), null);
        }
        return new StatusLine(Component.translatable("gui.jeict.recipe_tree.floating_status_missing", kinds),
                theme.missing(), missingTooltip(kinds));
    }

    /** Still-missing raw materials summed per material across steps. */
    private static Map<String, Long> missingByMaterial() {
        Map<String, Long> missing = new LinkedHashMap<>();
        for (DisplayEntry entry : missingRaw()) {
            missing.merge(entry.key(), entry.remaining(), FloatingMaterialOverlayState::saturatedAddLong);
        }
        return missing;
    }

    private static List<Component> missingTooltip(int missingKinds) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.jeict.recipe_tree.floating_missing_header", missingKinds)
                .withStyle(s -> s.withColor(0xFFFF5555)));
        Map<String, DisplayEntry> firstByMaterial = new LinkedHashMap<>();
        for (DisplayEntry entry : missingRaw()) firstByMaterial.putIfAbsent(entry.key(), entry);
        Map<String, Long> remaining = missingByMaterial();
        int listed = 0;
        for (Map.Entry<String, DisplayEntry> item : firstByMaterial.entrySet()) {
            if (listed >= MAX_MISSING_TOOLTIP_LINES) break;
            DisplayEntry entry = item.getValue();
            List<Component> name = ingredientTooltipLines(entry.source());
            int color = entry.state() == State.PARTIAL ? 0xFFFFAA00 : 0xFFFF5555;
            lines.add(Component.literal("- ")
                    .append(name.isEmpty() ? Component.literal("?") : name.get(0))
                    .append(" \u00d7" + formatDetailedAmount(entry.source(), remaining.get(item.getKey())))
                    .withStyle(s -> s.withColor(color)));
            listed++;
        }
        if (missingKinds > listed) {
            lines.add(Component.translatable("gui.jeict.recipe_tree.floating_missing_more", missingKinds - listed)
                    .withStyle(s -> s.withColor(0xFFAAAAAA).withItalic(true)));
        }
        return lines;
    }

    private record StatusLine(Component text, int color, @Nullable List<Component> tooltip) {
    }

    private static void renderEntryAmount(GuiGraphics graphics, Font font, DisplayEntry entry, int x, int y) {
        Component label = entry.badgeText();
        float textScale = 0.75F;
        int textWidth = font.width(label);
        float textX = x + 17 - textWidth * textScale;
        float textY = y + 17 - font.lineHeight * textScale;
        graphics.pose().pushPose();
        graphics.pose().translate(textX, textY, 300.0F);
        graphics.pose().scale(textScale, textScale, 1.0F);
        RecipeTreeTheme.Palette theme = RecipeTreeTheme.current();
        graphics.drawString(font, label, 1, 1, stateColor(entry.state(), theme), false);
        graphics.drawString(font, label, 0, 0, theme.slotOverlayText(), false);
        graphics.pose().popPose();
    }

    private static String formatDetailedAmount(Entry entry, long amount) {
        String compact = formatEntryAmount(entry, amount);
        if (usesMilliBucketUnits(entry) || amount < 1_000L) return compact;
        return compact + " (" + String.format(java.util.Locale.ROOT, "%,d", Math.max(0L, amount)) + ")";
    }

    private static String formatEntryAmount(Entry entry, long amount) {
        long safeAmount = Math.max(0L, amount);
        if (usesMilliBucketUnits(entry)) {
            if (safeAmount < 1000) {
                return safeAmount + " mB";
            }
            return java.math.BigDecimal.valueOf(safeAmount, 3).stripTrailingZeros().toPlainString() + " B";
        }
        return formatCompactCount(safeAmount);
    }

    private static boolean usesMilliBucketUnits(Entry entry) {
        ITypedIngredient<?> ingredient = entry.ingredient();
        if (ingredient == null) {
            return false;
        }
        if (ingredient.getIngredient(NeoForgeTypes.FLUID_STACK).filter(stack -> !stack.isEmpty()).isPresent()) {
            return true;
        }
        return GenericIngredientUtil.tryGetMekanismChemicalAmount(ingredient.getIngredient()) > 0L;
    }

    private static @Nullable FluidStack createRenderFluid(@Nullable ITypedIngredient<?> ingredient) {
        if (ingredient == null) {
            return null;
        }
        FluidStack fluid = ingredient.getIngredient(NeoForgeTypes.FLUID_STACK).orElse(null);
        if (fluid == null || fluid.isEmpty()) {
            return null;
        }
        FluidStack renderFluid = fluid.copy();
        renderFluid.setAmount(Math.max(1000, renderFluid.getAmount()));
        return renderFluid;
    }

    private static String formatCompactCount(long count) {
        if (count < 1000) {
            return Long.toString(count);
        }
        double value = count;
        String[] suffixes = { "K", "M", "B" };
        int suffixIndex = -1;
        while (value >= 1000.0D && suffixIndex + 1 < suffixes.length) {
            value /= 1000.0D;
            suffixIndex++;
        }
        if (value >= 100.0D || Math.abs(value - Math.round(value)) < 0.05D) {
            return Math.round(value) + suffixes[suffixIndex];
        }
        return String.format(java.util.Locale.ROOT, "%.1f%s", value, suffixes[suffixIndex]);
    }

    private static List<Component> ingredientTooltipLines(Entry entry) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!entry.stack().isEmpty()) {
            return new ArrayList<>(Screen.getTooltipFromItem(minecraft, entry.stack()));
        }
        ITypedIngredient<?> ingredient = entry.ingredient();
        IJeiRuntime runtime = JeiCraftingTreePlugin.getJeiRuntime();
        if (ingredient == null || runtime == null) {
            return new ArrayList<>();
        }
        return typedIngredientTooltip(runtime.getIngredientManager(), ingredient);
    }

    private static <T> void renderTypedIngredient(GuiGraphics graphics, IIngredientManager ingredientManager,
            ITypedIngredient<?> ingredient, int x, int y) {
        @SuppressWarnings("unchecked")
        ITypedIngredient<T> typed = (ITypedIngredient<T>) ingredient;
        IIngredientRenderer<T> renderer = ingredientManager.getIngredientRenderer(typed.getType());
        renderer.render(graphics, typed.getIngredient(), x, y);
    }

    private static <T> List<Component> typedIngredientTooltip(IIngredientManager ingredientManager,
            ITypedIngredient<?> ingredient) {
        @SuppressWarnings("unchecked")
        ITypedIngredient<T> typed = (ITypedIngredient<T>) ingredient;
        IIngredientRenderer<T> renderer = ingredientManager.getIngredientRenderer(typed.getType());
        return new ArrayList<>(renderer.getTooltip(typed.getIngredient(), TooltipFlag.Default.NORMAL));
    }

    public record Snapshot(List<Entry> entries, List<Task> tasks, @Nullable RecipeTreeRootContext context) {
        public Snapshot(List<Entry> entries, @Nullable RecipeTreeRootContext context) {
            this(entries, List.of(), context);
        }

        public Snapshot {
            entries = List.copyOf(entries);
            tasks = tasks == null ? List.of() : List.copyOf(tasks);
        }
    }

    public record Task(String identity, Entry output, MaterialKey outputKey, long requiredAmount,
            long outputPerCraft, boolean consumed, List<Task> inputs) {
        public Task {
            identity = identity == null ? "" : identity;
            requiredAmount = Math.max(1L, requiredAmount);
            outputPerCraft = Math.max(1L, outputPerCraft);
            inputs = inputs == null ? List.of() : List.copyOf(inputs);
        }
    }

    private enum State {
        /** Stock covers it. */
        ENOUGH,
        /** Not in stock but every input is, so it can be crafted right now. */
        CRAFTABLE,
        /** Some in stock, the rest still depends on missing materials. */
        PARTIAL,
        /** None in stock and it depends on missing materials. */
        MISSING
    }

    private record DisplayEntry(Entry source, String key, long need, long available, long remaining, State state,
            boolean raw, Component badgeText) {
    }

    private enum SlotKind {
        INPUT,
        OUTPUT,
        MACHINE
    }

    private record PlacedSlot(SlotKind kind, @Nullable DisplayEntry entry, int col, int row, long crafts,
            @Nullable IDrawable machineIcon, @Nullable Component machineName) {
    }

    private record DisplayGroup(@Nullable IDrawable machineIcon, @Nullable Component machineName,
            @Nullable DisplayEntry output, List<DisplayEntry> entries, long crafts, State state) {
    }

    public record Entry(ItemStack stack, @Nullable ITypedIngredient<?> ingredient, int count, String amountLabel,
            @Nullable IDrawable machineIcon, @Nullable Component machineName, String machineKey,
            @Nullable FluidStack renderFluid) {
        public Entry(ItemStack stack, @Nullable ITypedIngredient<?> ingredient, int count, String amountLabel,
                @Nullable IDrawable machineIcon, @Nullable Component machineName, String machineKey) {
            this(stack, ingredient, count, amountLabel, machineIcon, machineName, machineKey,
                    createRenderFluid(ingredient));
        }

        public Entry {
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
            count = Math.max(1, count);
            amountLabel = amountLabel == null ? "" : amountLabel;
            machineKey = machineKey == null ? "" : machineKey;
            renderFluid = renderFluid == null ? null : renderFluid.copy();
        }

        private Entry withCount(long nextCount) {
            int safeCount = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, nextCount));
            return new Entry(stack, ingredient, safeCount, amountLabel, machineIcon, machineName, machineKey, renderFluid);
        }
    }
}
