package com.lhy.jeict.client;

import java.util.ArrayList;
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
    private static final int BASE_WIDTH = 168;
    private static final int HEADER_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 16;
    private static final int CONTROL_SIZE = 12;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int MAX_CONTENT_HEIGHT = 240;
    private static final int CONTENT_PADDING = 6;
    private static final int GROUP_WIDTH = BASE_WIDTH - CONTENT_PADDING * 2;
    private static final int GROUP_PADDING = 4;
    private static final int GROUP_GAP = 4;
    private static final int ICON_SIZE = 16;
    private static final int MACHINE_SLOT_SIZE = 18;
    private static final int ICON_GAP = 2;
    private static final int MATERIAL_CELL_SIZE = ICON_SIZE + ICON_GAP;
    private static final int MATERIAL_START_X = GROUP_PADDING + MACHINE_SLOT_SIZE + 4;
    private static final int MATERIALS_PER_ROW = 7;
    private static final int SCROLL_STEP = 14;
    private static final float JEI_BOOKMARK_Z = 200.0F;
    private static final float OVERLAY_Z = JEI_BOOKMARK_Z + 1.0F;
    private static final float TOOLTIP_Z = OVERLAY_Z + 400.0F;
    private static final int AUTO_CRAFT_WIDTH = 46;
    private static final int AUTO_CRAFT_LEFT = BASE_WIDTH - CONTENT_PADDING - AUTO_CRAFT_WIDTH;
    private static final int CREATIVE_REFILL_WIDTH = 46;
    private static final int CREATIVE_REFILL_LEFT = AUTO_CRAFT_LEFT - 4 - CREATIVE_REFILL_WIDTH;
    private static final int MAX_CREATIVE_REFILL_SLOTS = 64;
    private static final Component AUTO_CRAFT_LABEL =
            Component.translatable("gui.jeict.recipe_tree.floating_auto_craft_label");
    private static final Component CREATIVE_REFILL_LABEL =
            Component.translatable("gui.jeict.recipe_tree.floating_creative_refill_label");
    private static final ResourceLocation MICRO_AMOUNT_FONT = ResourceLocation.withDefaultNamespace("uniform");

    private static Snapshot snapshot;
    private static int x = -1;
    private static int y = 8;
    private static int lastWidth = BASE_WIDTH;
    private static int lastHeight = 0;
    private static float scale = 1.0F;
    private static boolean pinned;
    private static boolean dragging;
    private static boolean leftMouseDown;
    private static double dragOffsetX;
    private static double dragOffsetY;

    private static int scrollOffset;
    private static int maxScrollOffset;
    private static boolean showAll;
    private static long lastTitleClickTime;
    private static List<DisplayGroup> cachedDisplayGroups = List.of();
    private static long cachedInventoryVersion = Long.MIN_VALUE;
    private static boolean cachedShowAll;
    private static boolean displayEntriesDirty = true;

    private FloatingMaterialOverlayState() {
    }

    public static void set(Snapshot nextSnapshot) {
        snapshot = nextSnapshot;
        scrollOffset = 0;
        maxScrollOffset = 0;
        displayEntriesDirty = true;
        Minecraft minecraft = Minecraft.getInstance();
        if (x < 0 && minecraft.getWindow() != null) {
            x = Math.max(6, minecraft.getWindow().getGuiScaledWidth() - Math.round(BASE_WIDTH * scale) - 8);
            y = 8;
        }
    }

    public static void clear() {
        snapshot = null;
        dragging = false;
        leftMouseDown = false;
        scrollOffset = 0;
        maxScrollOffset = 0;
        cachedDisplayGroups = List.of();
        cachedInventoryVersion = Long.MIN_VALUE;
        displayEntriesDirty = true;
    }

    public static void render(GuiGraphics graphics) {
        if (!RecipeTreeConfig.SHOW_FLOATING_MATERIALS.get()) return;
        if (!isInWorld()) {
            clear();
            return;
        }
        if (snapshot == null || (snapshot.entries().isEmpty() && snapshot.tasks().isEmpty())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) {
            return;
        }
        Font font = minecraft.font;
        List<DisplayGroup> displayGroups = displayGroups();

        int totalContentHeight = computeTotalContentHeight(displayGroups);
        int visibleContentHeight = MAX_CONTENT_HEIGHT;
        int contentHeight = HEADER_HEIGHT + 4 + Math.min(totalContentHeight, visibleContentHeight) + 4 + FOOTER_HEIGHT;
        int height = Math.min(260 + FOOTER_HEIGHT, contentHeight);
        int actualVisibleContentHeight = height - HEADER_HEIGHT - 8 - FOOTER_HEIGHT;
        maxScrollOffset = Math.max(0, totalContentHeight - actualVisibleContentHeight);
        scrollOffset = Math.min(scrollOffset, maxScrollOffset);
        scrollOffset = Math.max(0, scrollOffset);

        lastWidth = BASE_WIDTH;
        lastHeight = height;
        clampToScreen();

        int mouseX = (int) Math.round(scaledMouseX());
        int mouseY = (int) Math.round(scaledMouseY());
        double localMouseX = (mouseX - x) / scale;
        double localMouseY = (mouseY - y) / scale;

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, OVERLAY_Z);
        graphics.pose().scale(scale, scale, 1.0F);

        RecipeTreeTheme.Palette theme = RecipeTreeTheme.current();
        RecipeTreeTheme.drawFramedPanel(graphics, 0, 0, BASE_WIDTH, height);

        graphics.drawString(font, Component.translatable("gui.jeict.recipe_tree.floating_materials_title"), 22, 6,
                theme.metricText(), false);
        drawControl(graphics, 6, 5, pinned ? "P" : "p", pinned ? theme.pinned() : theme.controlText());
        drawControl(graphics, BASE_WIDTH - 44, 5, showAll ? "A" : "a", showAll ? theme.success() : theme.controlText());
        if (snapshot.context() != null) {
            drawControl(graphics, BASE_WIDTH - 31, 5, "\u2190", theme.accent());
        }
        drawControl(graphics, BASE_WIDTH - 18, 5, "x", theme.danger());

        DisplayEntry hoveredEntry = null;
        Component hoveredMachineName = null;

        int contentTop = HEADER_HEIGHT + 4;
        int contentBottom = height - 4 - FOOTER_HEIGHT;
        int groupX = CONTENT_PADDING;
        int groupY = contentTop - scrollOffset;
        for (DisplayGroup group : displayGroups) {
            int groupBottom = groupY + group.height();
            if (groupBottom > contentTop && groupY < contentBottom) {
                boolean groupHovered = localMouseX >= groupX && localMouseX < groupX + GROUP_WIDTH
                        && localMouseY >= groupY && localMouseY < groupBottom
                        && localMouseY >= contentTop && localMouseY < contentBottom;
                RecipeTreeTheme.drawMarkdownNode(graphics, groupX, groupY, groupX + GROUP_WIDTH, groupBottom,
                        theme.accent());
                if (group.machineIcon() != null) {
                    int machineX = groupX + GROUP_PADDING;
                    int machineY = groupY + GROUP_PADDING;
                    RecipeTreeTheme.drawSlot(graphics, machineX, machineY);
                    group.machineIcon().draw(graphics, machineX + 1, machineY + 1);
                    if (groupHovered
                            && localMouseX >= machineX
                            && localMouseX < machineX + MACHINE_SLOT_SIZE
                            && localMouseY >= machineY
                            && localMouseY < machineY + MACHINE_SLOT_SIZE) {
                        hoveredMachineName = group.machineName();
                    }
                }
                for (int entryIndex = 0; entryIndex < group.entries().size(); entryIndex++) {
                    DisplayEntry entry = group.entries().get(entryIndex);
                    int itemX = groupX + MATERIAL_START_X
                            + entryIndex % MATERIALS_PER_ROW * MATERIAL_CELL_SIZE;
                    int itemY = groupY + GROUP_PADDING
                            + entryIndex / MATERIALS_PER_ROW * MATERIAL_CELL_SIZE;
                    renderEntryIngredient(graphics, entry.source(), itemX, itemY);
                    renderEntryAmount(graphics, font, entry, itemX, itemY);
                    if (groupHovered
                            && localMouseX >= itemX && localMouseX < itemX + ICON_SIZE
                            && localMouseY >= itemY && localMouseY < itemY + ICON_SIZE) {
                        hoveredEntry = entry;
                    }
                }
                if (groupHovered) {
                    RecipeTreeTheme.drawBorder(graphics, groupX - 1, groupY - 1,
                            groupX + GROUP_WIDTH + 1, groupBottom + 1, theme.accent());
                }
            }
            groupY = groupBottom + GROUP_GAP;
        }

        if (maxScrollOffset > 0) {
            int trackX = BASE_WIDTH - SCROLLBAR_WIDTH - 2;
            int trackTop = contentTop;
            int trackBottom = contentBottom;
            int trackHeight = trackBottom - trackTop;
            if (trackHeight > 0) {
                graphics.fill(trackX, trackTop, trackX + 2, trackBottom, theme.scrollbarTrack());
                int thumbHeight = Math.max(12, trackHeight * trackHeight / (trackHeight + maxScrollOffset));
                int thumbY = trackTop + (maxScrollOffset > 0
                        ? (int) ((long) scrollOffset * (trackHeight - thumbHeight) / maxScrollOffset)
                        : 0);
                graphics.fill(trackX - 1, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, theme.scrollbarThumb());
            }
        }

        int craftY = autoCraftButtonTop(height);
        boolean creativeRefillVisible = canCreativeRefill(displayGroups);
        boolean creativeRefillHovered = creativeRefillVisible
                && localMouseX >= CREATIVE_REFILL_LEFT
                && localMouseX < CREATIVE_REFILL_LEFT + CREATIVE_REFILL_WIDTH
                && localMouseY >= craftY && localMouseY < craftY + CONTROL_SIZE;
        if (creativeRefillVisible) {
            RecipeTreeTheme.drawButton(graphics, CREATIVE_REFILL_LEFT, craftY, CREATIVE_REFILL_WIDTH, CONTROL_SIZE,
                    creativeRefillHovered, true);
            int refillTextY = craftY + Math.max(0, (CONTROL_SIZE - font.lineHeight) / 2);
            graphics.drawCenteredString(font, CREATIVE_REFILL_LABEL,
                    CREATIVE_REFILL_LEFT + CREATIVE_REFILL_WIDTH / 2, refillTextY,
                    creativeRefillHovered ? theme.controlHoverText() : theme.controlText());
        }
        boolean craftHovered = localMouseX >= AUTO_CRAFT_LEFT && localMouseX < AUTO_CRAFT_LEFT + AUTO_CRAFT_WIDTH
                && localMouseY >= craftY && localMouseY < craftY + CONTROL_SIZE;
        boolean autoCraftRunning = RecipeTreeAutoCraftSession.status().running();
        RecipeTreeTheme.drawButton(graphics, AUTO_CRAFT_LEFT, craftY, AUTO_CRAFT_WIDTH, CONTROL_SIZE,
                craftHovered, true);
        Component autoCraftLabel = autoCraftRunning
                ? Component.translatable("gui.jeict.recipe_tree.floating_auto_craft_stop_label")
                : AUTO_CRAFT_LABEL;
        int craftTextY = craftY + Math.max(0, (CONTROL_SIZE - font.lineHeight) / 2);
        graphics.drawCenteredString(font, autoCraftLabel, AUTO_CRAFT_LEFT + AUTO_CRAFT_WIDTH / 2, craftTextY,
                craftHovered ? theme.controlHoverText() : theme.controlText());

        graphics.pose().popPose();

        Component controlTooltip = controlTooltipAt(localMouseX, localMouseY);
        if (controlTooltip != null || hoveredEntry != null || hoveredMachineName != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, TOOLTIP_Z);
            if (controlTooltip != null) {
                graphics.renderTooltip(font, List.of(controlTooltip), java.util.Optional.empty(), mouseX, mouseY);
            } else if (hoveredMachineName != null) {
                graphics.renderTooltip(font, List.of(hoveredMachineName),
                        java.util.Optional.empty(), mouseX, mouseY);
            } else {
                List<Component> tooltipLines = ingredientTooltipLines(hoveredEntry.source());
                tooltipLines.add(Component.translatable("gui.jeict.recipe_tree.floating_available_amount",
                        formatDetailedAmount(hoveredEntry.source(), hoveredEntry.available()))
                        .withStyle(s -> s.withColor(0xFFAAAAAA)));
                tooltipLines.add(Component.translatable("gui.jeict.recipe_tree.floating_required_amount",
                        formatDetailedAmount(hoveredEntry.source(), hoveredEntry.source().count()))
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
        if (!dragging || snapshot == null) {
            return;
        }
        if (!leftMouseDown) {
            dragging = false;
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
                leftMouseDown = false;
            }
            return false;
        }
        if (!press) {
            dragging = false;
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

        if (button == 0 && localX >= BASE_WIDTH - 18 && localX <= BASE_WIDTH - 6 && localY >= 5 && localY <= 17) {
            clear();
            cancel(event);
            return true;
        }

        if (button == 0 && localX >= BASE_WIDTH - 44 && localX <= BASE_WIDTH - 32 && localY >= 5 && localY <= 17) {
            showAll = !showAll;
            displayEntriesDirty = true;
            scrollOffset = 0;
            cancel(event);
            return true;
        }

        if (button == 0 && snapshot.context() != null
                && localX >= BASE_WIDTH - 31 && localX <= BASE_WIDTH - 19 && localY >= 5 && localY <= 17) {
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
                x = Math.max(6, Minecraft.getInstance().getWindow().getGuiScaledWidth() - Math.round(BASE_WIDTH * scale) - 8);
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
        return localX >= AUTO_CRAFT_LEFT && localX < AUTO_CRAFT_LEFT + AUTO_CRAFT_WIDTH
                && localY >= craftY && localY < craftY + CONTROL_SIZE;
    }

    private static boolean isOverCreativeRefillButton(double localX, double localY) {
        int refillY = autoCraftButtonTop(lastHeight);
        return canCreativeRefill(displayGroups())
                && localX >= CREATIVE_REFILL_LEFT && localX < CREATIVE_REFILL_LEFT + CREATIVE_REFILL_WIDTH
                && localY >= refillY && localY < refillY + CONTROL_SIZE;
    }

    private static void runAutoCraft() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        boolean wasRunning = RecipeTreeAutoCraftSession.status().running();
        boolean running = RecipeTreeAutoCraftSession.toggle(snapshot == null ? null : snapshot.context());
        Component message = running
                ? Component.translatable("message.jeict.auto_craft_started")
                : wasRunning
                        ? Component.translatable("message.jeict.auto_craft_cancelled")
                        : Component.translatable("message.jeict.auto_craft_unavailable");
        minecraft.player.displayClientMessage(message, true);
    }

    private static void runCreativeRefill() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !canCreativeRefill(displayGroups())) return;
        Map<Integer, ItemStack> plannedSlots = new LinkedHashMap<>();
        int changedSlots = 0;
        for (DisplayGroup group : displayGroups()) {
            for (DisplayEntry entry : group.entries()) {
                if (entry.remaining() <= 0L) continue;
                ItemStack template = itemStack(entry.source());
                if (template.isEmpty()) continue;
                long remaining = entry.remaining();
                for (var slot : minecraft.player.containerMenu.slots) {
                    if (changedSlots >= MAX_CREATIVE_REFILL_SLOTS || remaining <= 0L) break;
                    if (slot.container == minecraft.player.getInventory() || !slot.mayPlace(template)) continue;
                    ItemStack current = plannedSlots.getOrDefault(slot.index, slot.getItem()).copy();
                    if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, template)) continue;
                    int max = Math.min(slot.getMaxStackSize(), template.getMaxStackSize());
                    int space = current.isEmpty() ? max : max - current.getCount();
                    if (space <= 0) continue;
                    int added = (int) Math.min(remaining, space);
                    ItemStack filled = template.copyWithCount((current.isEmpty() ? 0 : current.getCount()) + added);
                    plannedSlots.put(slot.index, filled.copy());
                    remaining -= added;
                    changedSlots++;
                }
                if (changedSlots >= MAX_CREATIVE_REFILL_SLOTS) break;
            }
            if (changedSlots >= MAX_CREATIVE_REFILL_SLOTS) break;
        }
        plannedSlots.forEach((slot, stack) -> PacketDistributor.sendToServer(new CreativeRefillRequestPayload(
                minecraft.player.containerMenu.containerId, slot, stack)));
        if (changedSlots > 0) ClientInventorySnapshotCache.invalidate();
        minecraft.player.displayClientMessage(Component.translatable(changedSlots > 0
                ? "message.jeict.creative_refill_sent"
                : "message.jeict.creative_refill_no_space"), true);
    }

    private static boolean canCreativeRefill(List<DisplayGroup> groups) {
        Minecraft minecraft = Minecraft.getInstance();
        var connection = minecraft.getConnection();
        if (minecraft.player == null || !minecraft.player.getAbilities().instabuild
                || connection == null || !connection.hasChannel(CreativeRefillRequestPayload.TYPE)) return false;
        for (DisplayGroup group : groups) {
            for (DisplayEntry entry : group.entries()) {
                if (entry.remaining() > 0L && !itemStack(entry.source()).isEmpty()) return true;
            }
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
        List<DisplayGroup> displayGroups = displayGroups();
        if (displayGroups.isEmpty()) {
            return false;
        }

        int contentTop = HEADER_HEIGHT + 4;
        int adjustedY = (int) localY + scrollOffset - contentTop;
        int adjustedX = (int) localX - CONTENT_PADDING;
        if (adjustedX < 0 || adjustedX >= GROUP_WIDTH || adjustedY < 0) {
            return false;
        }
        int groupTop = 0;
        for (DisplayGroup group : displayGroups) {
            if (adjustedY >= groupTop && adjustedY < groupTop + group.height()) {
                int materialX = adjustedX - MATERIAL_START_X;
                int materialY = adjustedY - groupTop - GROUP_PADDING;
                if (materialX >= 0 && materialY >= 0) {
                    int col = materialX / MATERIAL_CELL_SIZE;
                    int row = materialY / MATERIAL_CELL_SIZE;
                    if (col < MATERIALS_PER_ROW
                            && materialX % MATERIAL_CELL_SIZE < ICON_SIZE
                            && materialY % MATERIAL_CELL_SIZE < ICON_SIZE) {
                        int entryIndex = row * MATERIALS_PER_ROW + col;
                        if (entryIndex < group.entries().size()) {
                            openJeiForEntry(group.entries().get(entryIndex).source(), button);
                        }
                    }
                }
                return true;
            }
            groupTop += group.height() + GROUP_GAP;
        }
        return false;
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
        if (button != 0 || !dragging || snapshot == null) {
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

    private static int computeTotalContentHeight(List<DisplayGroup> groups) {
        if (groups.isEmpty()) {
            return 0;
        }
        int height = -GROUP_GAP;
        for (DisplayGroup group : groups) {
            height += group.height() + GROUP_GAP;
        }
        return height;
    }

    private static List<DisplayGroup> displayGroups() {
        long inventoryVersion = ClientInventorySnapshotCache.version();
        if (!displayEntriesDirty && inventoryVersion == cachedInventoryVersion && showAll == cachedShowAll) {
            return cachedDisplayGroups;
        }
        var inventory = ClientInventorySnapshotCache.get();
        Map<String, MutableDisplayGroup> grouped = new LinkedHashMap<>();
        for (Entry entry : projectedEntries(inventory)) {
            long available = availableAmount(inventory, entry);
            long remaining = Math.max(0L, (long) entry.count() - available);
            if (!showAll && remaining <= 0L) {
                continue;
            }
            Component badgeText = Component.literal(formatEntryAmount(entry, remaining).replace(" ", ""))
                    .withStyle(style -> style.withFont(MICRO_AMOUNT_FONT));
            grouped.computeIfAbsent(entry.machineKey(), ignored -> new MutableDisplayGroup(entry))
                    .entries.add(new DisplayEntry(entry, Math.min(available, entry.count()), remaining, badgeText));
        }
        List<DisplayGroup> groups = new ArrayList<>(grouped.size());
        for (MutableDisplayGroup group : grouped.values()) {
            groups.add(group.freeze());
        }
        cachedDisplayGroups = List.copyOf(groups);
        cachedInventoryVersion = inventoryVersion;
        cachedShowAll = showAll;
        displayEntriesDirty = false;
        return cachedDisplayGroups;
    }

    private static List<Entry> projectedEntries(com.lhy.jeict.planning.InventorySnapshot inventory) {
        if (snapshot.tasks().isEmpty()) {
            return snapshot.entries();
        }
        Map<MaterialKey, Long> available = new LinkedHashMap<>(inventory.amounts());
        List<Entry> projected = new ArrayList<>();
        Set<Task> path = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Task task : snapshot.tasks()) {
            collectTaskFrontier(task, task.requiredAmount(), available, projected, path);
        }
        return mergeProjectedEntries(projected);
    }

    private static boolean collectTaskFrontier(Task task, long requiredAmount, Map<MaterialKey, Long> available,
            List<Entry> projected, Set<Task> path) {
        long owned = Math.max(0L, available.getOrDefault(task.outputKey(), 0L));
        long used = Math.min(owned, requiredAmount);
        if (used > 0L) {
            available.put(task.outputKey(), owned - used);
        }
        long missing = requiredAmount - used;
        if (missing <= 0L) {
            if (showAll) projected.add(task.output().withCount(requiredAmount));
            return true;
        }
        if (task.inputs().isEmpty() || !path.add(task)) {
            projected.add(task.output().withCount(requiredAmount));
            return false;
        }

        long crafts = ceilDiv(missing, task.outputPerCraft());
        List<Entry> childFrontier = new ArrayList<>();
        boolean inputsReady = true;
        for (Task input : task.inputs()) {
            long required = input.consumed()
                    ? saturatedMultiply(input.requiredAmount(), crafts)
                    : input.requiredAmount();
            inputsReady &= collectTaskFrontier(input, required, available, childFrontier, path);
        }
        path.remove(task);
        if (inputsReady) {
            projected.add(task.output().withCount(requiredAmount));
        } else {
            projected.addAll(childFrontier);
        }
        return false;
    }

    private static List<Entry> mergeProjectedEntries(List<Entry> entries) {
        IJeiRuntime runtime = JeiCraftingTreePlugin.getJeiRuntime();
        if (runtime == null) return List.copyOf(entries);
        IIngredientManager manager = runtime.getIngredientManager();
        Map<String, Entry> merged = new LinkedHashMap<>();
        for (Entry entry : entries) {
            ITypedIngredient<?> ingredient = typedIngredient(manager, entry);
            String material = ingredient == null
                    ? IngredientIdentityUtil.fallbackSignature(entry.ingredient(), entry.stack())
                    : IngredientIdentityUtil.keyOf(manager, ingredient).encoded();
            String key = entry.machineKey() + '\u0000' + material;
            merged.merge(key, entry, (left, right) -> left.withCount(saturatedAdd(left.count(), right.count())));
        }
        return List.copyOf(merged.values());
    }

    private static @Nullable ITypedIngredient<?> typedIngredient(IIngredientManager manager, Entry entry) {
        if (entry.ingredient() != null) return entry.ingredient();
        if (entry.stack().isEmpty()) return null;
        return manager.createTypedIngredient(entry.stack().copyWithCount(1), true).orElse(null);
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

    private static int saturatedAdd(int left, int right) {
        return (int) Math.min(Integer.MAX_VALUE, (long) left + right);
    }

    private static long availableAmount(com.lhy.jeict.planning.InventorySnapshot inventory, Entry entry) {
        IJeiRuntime runtime = JeiCraftingTreePlugin.getJeiRuntime();
        if (runtime == null) return 0L;
        ITypedIngredient<?> ingredient = entry.ingredient();
        if (ingredient == null && !entry.stack().isEmpty()) {
            ingredient = runtime.getIngredientManager().createTypedIngredient(entry.stack().copyWithCount(1), true)
                    .orElse(null);
        }
        if (ingredient == null) return 0L;
        MaterialKey key = IngredientIdentityUtil.keyOf(runtime.getIngredientManager(), ingredient);
        return inventory.amount(key);
    }

    private static int groupHeight(int entryCount) {
        int rows = Math.max(1, (entryCount + MATERIALS_PER_ROW - 1) / MATERIALS_PER_ROW);
        int materialHeight = rows * MATERIAL_CELL_SIZE - ICON_GAP;
        return GROUP_PADDING * 2 + Math.max(MACHINE_SLOT_SIZE, materialHeight);
    }

    private static void drawControl(GuiGraphics graphics, int x, int y, String text, int color) {
        RecipeTreeTheme.drawSmallControl(graphics, x, y, CONTROL_SIZE, false);
        Font font = Minecraft.getInstance().font;
        int textY = y + Math.max(0, (CONTROL_SIZE - font.lineHeight) / 2);
        graphics.drawCenteredString(font, text, x + CONTROL_SIZE / 2, textY, color);
    }

    private static @Nullable Component controlTooltipAt(double localMouseX, double localMouseY) {
        if (isOverAutoCraftButton(localMouseX, localMouseY)) {
            return Component.translatable("gui.jeict.recipe_tree.floating_auto_craft_tooltip");
        }
        if (isOverCreativeRefillButton(localMouseX, localMouseY)) {
            return Component.translatable("gui.jeict.recipe_tree.floating_creative_refill_tooltip");
        }
        if (localMouseY < 5 || localMouseY > 17) {
            return null;
        }
        if (localMouseX >= 6 && localMouseX <= 18) {
            return Component.translatable(pinned
                    ? "gui.jeict.recipe_tree.floating_unpin_tooltip"
                    : "gui.jeict.recipe_tree.floating_pin_tooltip");
        }
        if (localMouseX >= 22 && localMouseX < BASE_WIDTH - 44) {
            return Component.translatable("gui.jeict.recipe_tree.floating_scale_tooltip",
                    Math.round(scale * 100.0F));
        }
        if (localMouseX >= BASE_WIDTH - 44 && localMouseX <= BASE_WIDTH - 32) {
            return Component.translatable(showAll
                    ? "gui.jeict.recipe_tree.floating_missing_only_tooltip"
                    : "gui.jeict.recipe_tree.floating_show_all_tooltip");
        }
        if (snapshot.context() != null
                && localMouseX >= BASE_WIDTH - 31 && localMouseX <= BASE_WIDTH - 19) {
            return Component.translatable("gui.jeict.recipe_tree.floating_back_tooltip");
        }
        if (localMouseX >= BASE_WIDTH - 18 && localMouseX <= BASE_WIDTH - 6) {
            return Component.translatable("gui.jeict.recipe_tree.floating_close_tooltip");
        }
        return null;
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
        graphics.drawString(font, label, 1, 1, entry.remaining() <= 0 ? theme.enough() : theme.missing(), false);
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
        ITypedIngredient<?> ingredient = entry.ingredient();
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

    private record DisplayEntry(Entry source, long available, long remaining, Component badgeText) {
    }

    private record DisplayGroup(@Nullable IDrawable machineIcon, @Nullable Component machineName,
            List<DisplayEntry> entries, int height) {
    }

    private static final class MutableDisplayGroup {
        private final @Nullable IDrawable machineIcon;
        private final @Nullable Component machineName;
        private final List<DisplayEntry> entries = new ArrayList<>();

        private MutableDisplayGroup(Entry firstEntry) {
            machineIcon = firstEntry.machineIcon();
            machineName = firstEntry.machineName();
        }

        private DisplayGroup freeze() {
            return new DisplayGroup(machineIcon, machineName, List.copyOf(entries), groupHeight(entries.size()));
        }
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
