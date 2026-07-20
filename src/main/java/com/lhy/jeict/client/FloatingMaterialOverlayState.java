package com.lhy.jeict.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.jei.JeiCraftingTreePlugin;
import com.lhy.jeict.recipe_tree.RecipeTreeRootContext;
import com.mojang.blaze3d.platform.InputConstants;

import mezz.jei.api.ingredients.ITypedIngredient;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

public final class FloatingMaterialOverlayState {
    private static final int BASE_WIDTH = 168;
    private static final int HEADER_HEIGHT = 20;
    private static final int CONTROL_SIZE = 12;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int MAX_CONTENT_HEIGHT = 240;
    private static final int ITEM_SIZE = 16;
    private static final int ITEM_H_GAP = 14;
    private static final int ITEMS_PER_ROW = 5;
    private static final int ROW_HEIGHT = 22;
    private static final int GROUP_HEADER_HEIGHT = 14;
    private static final int GROUP_PADDING = 4;
    private static final int SCROLL_STEP = 14;
    private static final float JEI_BOOKMARK_Z = 200.0F;
    private static final float OVERLAY_Z = JEI_BOOKMARK_Z + 1.0F;
    private static final float TOOLTIP_Z = OVERLAY_Z + 400.0F;

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
    private static final Set<String> collapsedGroupKeys = new HashSet<>();
    private static int hoveredGroupIndex = -1;
    private static int hoveredEntryIndex = -1;
    private static long lastTitleClickTime;

    private FloatingMaterialOverlayState() {
    }

    public static void set(Snapshot nextSnapshot) {
        snapshot = nextSnapshot;
        scrollOffset = 0;
        maxScrollOffset = 0;
        collapsedGroupKeys.clear();
        hoveredGroupIndex = -1;
        hoveredEntryIndex = -1;
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
        collapsedGroupKeys.clear();
        hoveredGroupIndex = -1;
        hoveredEntryIndex = -1;
    }

    public static void render(GuiGraphics graphics) {
        if (!isInWorld()) {
            clear();
            return;
        }
        if (snapshot == null || snapshot.groups().isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) {
            return;
        }
        Font font = minecraft.font;
        List<DisplayGroup> displayGroups = buildDisplayGroups(snapshot.groups());
        if (displayGroups.isEmpty()) {
            return;
        }

        int totalContentHeight = computeTotalContentHeight(displayGroups);
        int visibleContentHeight = MAX_CONTENT_HEIGHT;
        int contentHeight = HEADER_HEIGHT + 4 + Math.min(totalContentHeight, visibleContentHeight) + 4;
        int height = Math.min(260, contentHeight);
        int actualVisibleContentHeight = height - HEADER_HEIGHT - 8;
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

        hoveredGroupIndex = -1;
        hoveredEntryIndex = -1;
        ItemStack hoveredStack = ItemStack.EMPTY;

        int contentTop = HEADER_HEIGHT + 4;
        int currentY = contentTop - scrollOffset;

        for (int groupIndex = 0; groupIndex < displayGroups.size(); groupIndex++) {
            DisplayGroup group = displayGroups.get(groupIndex);
            boolean collapsed = collapsedGroupKeys.contains(group.title().getString());
            int groupHeight = collapsed ? GROUP_HEADER_HEIGHT : groupHeight(group.entries().size());
            int groupBottom = currentY + groupHeight;

            if (groupBottom < contentTop) {
                currentY += groupHeight + GROUP_PADDING;
                continue;
            }
            if (currentY > height - 4) {
                graphics.drawString(font, "...", 8, height - 16, theme.mutedText(), false);
                break;
            }

            int color = theme.groupColor(groupIndex);
            int clippedTop = Math.max(currentY, contentTop);
            int clippedBottom = Math.min(groupBottom, height - 4);
            if (clippedTop < clippedBottom) {
                graphics.fill(4, clippedTop, BASE_WIDTH - 4, clippedBottom, theme.overlayGroupFill());
                drawBorder(graphics, 4, clippedTop, BASE_WIDTH - 4, clippedBottom, color);
            }

            if (currentY >= contentTop && currentY < height - 4) {
                String collapseIcon = collapsed ? "\u25B6" : "\u25BC";
                graphics.drawString(font, collapseIcon, 6, currentY + 3, color, false);
                String titleText = font.substrByWidth(group.title(), BASE_WIDTH - 24).getString();
                graphics.drawString(font, titleText, 16, currentY + 3, theme.metricText(), false);
            }

            if (!collapsed) {
                int itemStartY = currentY + GROUP_HEADER_HEIGHT;
                int entryIndex = 0;
                int itemX = 8;
                int itemY = itemStartY;
                for (DisplayEntry entry : group.entries()) {
                    if (itemY + ITEM_SIZE <= contentTop) {
                        entryIndex++;
                        itemX += ITEM_SIZE + ITEM_H_GAP;
                        if (entryIndex % ITEMS_PER_ROW == 0) {
                            itemX = 8;
                            itemY += ROW_HEIGHT;
                        }
                        continue;
                    }
                    if (itemY >= height - 4) {
                        break;
                    }

                    boolean isHovered = localMouseX >= itemX && localMouseX < itemX + ITEM_SIZE
                            && localMouseY >= itemY && localMouseY < itemY + ITEM_SIZE
                            && localMouseY >= contentTop && localMouseY < height - 4;

                    if (isHovered) {
                        hoveredGroupIndex = groupIndex;
                        hoveredEntryIndex = entryIndex;
                        hoveredStack = entry.stack().copy();
                    }

                    ItemStack displayStack = entry.stack().copyWithCount(Math.max(1, entry.remaining() > 0 ? entry.remaining() : 1));
                    RecipeTreeTheme.drawSlot(graphics, itemX - 1, itemY - 1);
                    graphics.renderItem(displayStack, itemX, itemY);
                    if (isHovered) {
                        RecipeTreeTheme.drawBorder(graphics, itemX - 2, itemY - 2, itemX + ITEM_SIZE + 2, itemY + ITEM_SIZE + 2, theme.accent());
                    }

                    if (showAll && entry.remaining() <= 0) {
                        graphics.drawString(font, "\u2713", itemX + 10, itemY + 10, theme.enough(), true);
                    } else {
                        String label = entry.remaining() > 1 ? formatCompactCount(entry.remaining()) : "1";
                        graphics.drawString(font, label, itemX + 17, itemY + 10, entry.color(), true);
                    }

                    entryIndex++;
                    itemX += ITEM_SIZE + ITEM_H_GAP;
                    if (entryIndex % ITEMS_PER_ROW == 0) {
                        itemX = 8;
                        itemY += ROW_HEIGHT;
                    }
                }
            }

            currentY += groupHeight + GROUP_PADDING;
        }

        if (maxScrollOffset > 0) {
            int trackX = BASE_WIDTH - SCROLLBAR_WIDTH - 2;
            int trackTop = contentTop;
            int trackBottom = height - 4;
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

        graphics.pose().popPose();

        if (!hoveredStack.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, TOOLTIP_Z);
            List<Component> tooltipLines = Screen.getTooltipFromItem(minecraft, hoveredStack);
            tooltipLines.add(Component.translatable("gui.jeict.recipe_tree.floating_left_click_hint")
                    .withStyle(s -> s.withColor(0xFFAAAAAA).withItalic(true)));
            tooltipLines.add(Component.translatable("gui.jeict.recipe_tree.floating_right_click_hint")
                    .withStyle(s -> s.withColor(0xFFAAAAAA).withItalic(true)));
            graphics.renderTooltip(font, tooltipLines, hoveredStack.getTooltipImage(),
                    hoveredStack, mouseX, mouseY);
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

        if (localY >= HEADER_HEIGHT && localY < lastHeight) {
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

    private static boolean handleContentClick(double localX, double localY, int button) {
        List<DisplayGroup> displayGroups = buildDisplayGroups(snapshot.groups());
        if (displayGroups.isEmpty()) {
            return false;
        }

        int contentTop = HEADER_HEIGHT + 4;
        int adjustedY = (int) localY + scrollOffset - contentTop;

        int currentY = 0;
        for (int groupIndex = 0; groupIndex < displayGroups.size(); groupIndex++) {
            DisplayGroup group = displayGroups.get(groupIndex);
            boolean collapsed = collapsedGroupKeys.contains(group.title().getString());
            int groupHeight = collapsed ? GROUP_HEADER_HEIGHT : groupHeight(group.entries().size());

            if (adjustedY >= currentY && adjustedY < currentY + GROUP_HEADER_HEIGHT) {
                if (button == 0) {
                    String key = group.title().getString();
                    if (collapsedGroupKeys.contains(key)) {
                        collapsedGroupKeys.remove(key);
                    } else {
                        collapsedGroupKeys.add(key);
                    }
                }
                return true;
            }

            if (!collapsed && adjustedY >= currentY + GROUP_HEADER_HEIGHT && adjustedY < currentY + groupHeight) {
                int itemAreaY = adjustedY - currentY - GROUP_HEADER_HEIGHT;
                int row = itemAreaY / ROW_HEIGHT;
                int col = (int) (localX - 8) / (ITEM_SIZE + ITEM_H_GAP);
                if (col >= 0 && col < ITEMS_PER_ROW && localX >= 8 && localX < BASE_WIDTH - 8) {
                    int entryIndex = row * ITEMS_PER_ROW + col;
                    if (entryIndex >= 0 && entryIndex < group.entries().size()) {
                        DisplayEntry entry = group.entries().get(entryIndex);
                        openJeiForItem(entry.stack(), button);
                        return true;
                    }
                }
            }

            currentY += groupHeight + GROUP_PADDING;
        }
        return false;
    }

    private static void openJeiForItem(ItemStack stack, int button) {
        IJeiRuntime runtime = JeiCraftingTreePlugin.getJeiRuntime();
        if (runtime == null) {
            return;
        }
        IIngredientManager ingredientManager = runtime.getIngredientManager();
        ITypedIngredient<?> ingredient = ingredientManager.createTypedIngredient(stack.copyWithCount(1), true)
                .orElse(null);
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

    private static int groupHeight(int entryCount) {
        int rows = (Math.max(0, entryCount - 1) / ITEMS_PER_ROW) + 1;
        return GROUP_HEADER_HEIGHT + rows * ROW_HEIGHT;
    }

    private static int computeTotalContentHeight(List<DisplayGroup> displayGroups) {
        int total = 0;
        for (DisplayGroup group : displayGroups) {
            boolean collapsed = collapsedGroupKeys.contains(group.title().getString());
            total += (collapsed ? GROUP_HEADER_HEIGHT : groupHeight(group.entries().size())) + GROUP_PADDING;
        }
        return total;
    }

    private static List<DisplayGroup> buildDisplayGroups(List<Group> groups) {
        List<DisplayGroup> displayGroups = new ArrayList<>();
        for (Group group : groups) {
            Map<String, DisplayEntryAccumulator> merged = new LinkedHashMap<>();
            for (Entry entry : group.entries()) {
                if (entry.stack().isEmpty()) {
                    continue;
                }
                String key = stackKey(entry.stack());
                merged.computeIfAbsent(key, ignored -> new DisplayEntryAccumulator(entry.stack())).add(entry.count());
            }

            List<DisplayEntry> entries = new ArrayList<>();
            for (DisplayEntryAccumulator accumulator : merged.values()) {
                int available = getItemCountInInventory(accumulator.stack);
                int remaining = Math.max(0, accumulator.count - available);
                if (!showAll && remaining <= 0) {
                    continue;
                }
                int color;
                if (remaining <= 0) {
                    color = RecipeTreeTheme.current().enough();
                } else if (available <= 0) {
                    color = RecipeTreeTheme.current().missing();
                } else {
                    color = RecipeTreeTheme.current().partial();
                }
                ItemStack displayStack = accumulator.stack.copyWithCount(1);
                entries.add(new DisplayEntry(displayStack, remaining, accumulator.count, color));
            }

            if (!entries.isEmpty()) {
                displayGroups.add(new DisplayGroup(group.title(), List.copyOf(entries)));
            }
        }
        return List.copyOf(displayGroups);
    }

    private static void drawControl(GuiGraphics graphics, int x, int y, String text, int color) {
        RecipeTreeTheme.drawSmallControl(graphics, x, y, CONTROL_SIZE, false);
        graphics.drawString(Minecraft.getInstance().font, text, x + 3, y + 2, color, false);
    }

    private static void drawBorder(GuiGraphics graphics, int left, int top, int right, int bottom, int color) {
        RecipeTreeTheme.drawBorder(graphics, left, top, right, bottom, color);
    }


    private static String formatCompactCount(int count) {
        if (count < 1000) {
            return Integer.toString(count);
        }
        double value = count;
        String[] suffixes = { "K", "M", "B" };
        int suffixIndex = -1;
        while (value >= 1000.0D && suffixIndex + 1 < suffixes.length) {
            value /= 1000.0D;
            suffixIndex++;
        }
        if (value >= 100.0D || Math.abs(value - Math.round(value)) < 0.05D) {
            return ((int) Math.round(value)) + suffixes[suffixIndex];
        }
        return String.format(java.util.Locale.ROOT, "%.1f%s", value, suffixes[suffixIndex]);
    }

    private static int getItemCountInInventory(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return 0;
        }
        Inventory inventory = minecraft.player.getInventory();
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack invStack = inventory.getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, invStack)) {
                count += invStack.getCount();
            }
        }
        return count;
    }

    public record Snapshot(List<Group> groups, @Nullable RecipeTreeRootContext context) {
        public Snapshot {
            groups = List.copyOf(groups);
        }
    }

    private record DisplayGroup(Component title, List<DisplayEntry> entries) {
    }

    private record DisplayEntry(ItemStack stack, int remaining, int totalNeeded, int color) {
    }

    public record Group(Component title, List<Entry> entries) {
        public Group {
            title = title == null ? Component.empty() : title.copy();
            entries = mergeEntries(entries);
        }
    }

    public record Entry(ItemStack stack, int count) {
        public Entry {
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
            count = Math.max(1, count);
        }
    }

    private static List<Entry> mergeEntries(List<Entry> entries) {
        Map<String, EntryAccumulator> merged = new LinkedHashMap<>();
        for (Entry entry : entries) {
            if (entry == null || entry.stack().isEmpty()) {
                continue;
            }
            String key = stackKey(entry.stack());
            merged.computeIfAbsent(key, ignored -> new EntryAccumulator(entry.stack())).add(entry.count());
        }
        List<Entry> result = new ArrayList<>(merged.size());
        for (EntryAccumulator accumulator : merged.values()) {
            result.add(new Entry(accumulator.stack, accumulator.count));
        }
        return List.copyOf(result);
    }

    private static String stackKey(ItemStack stack) {
        return stack.getItem() + "#" + stack.getComponents();
    }

    private static final class DisplayEntryAccumulator {
        private final ItemStack stack;
        private int count;

        private DisplayEntryAccumulator(ItemStack stack) {
            this.stack = stack.copyWithCount(1);
        }

        private void add(int amount) {
            long value = (long) count + Math.max(1, amount);
            count = (int) Math.min(Integer.MAX_VALUE, value);
        }
    }

    private static final class EntryAccumulator {
        private final ItemStack stack;
        private int count;

        private EntryAccumulator(ItemStack stack) {
            this.stack = stack.copyWithCount(1);
        }

        private void add(int amount) {
            long value = (long) count + Math.max(1, amount);
            count = (int) Math.min(Integer.MAX_VALUE, value);
        }
    }
}
