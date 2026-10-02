package com.lhy.jeict.client;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import com.lhy.jeict.compat.JustEnoughCharactersCompat;
import com.lhy.jeict.recipe_tree.RecipeTreeInputViewModel;
import com.lhy.jeict.recipe_tree.RecipeTreeInputViewModel.DisplayOption;

import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Draggable, resizable alternative-ingredient popover with pinyin-aware search. */
public final class AlternativePickerPanel {
    private static final int HEADER_HEIGHT = 18;
    private static final int SEARCH_HEIGHT = 16;
    private static final int ROW_HEIGHT = 18;
    private static final int PAD = 4;
    private static final int RESIZE_HANDLE = 10;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SCROLL_THUMB = 15;
    private static final int MIN_WIDTH = 168;
    private static final int MIN_HEIGHT = HEADER_HEIGHT + SEARCH_HEIGHT + PAD * 3 + ROW_HEIGHT * 3;
    private static final int DEFAULT_WIDTH = 196;
    private static int rememberedWidth = DEFAULT_WIDTH;
    private static int rememberedHeight = MIN_HEIGHT + ROW_HEIGHT * 5;

    private List<RecipeTreeInputViewModel> members = List.of();
    private List<DisplayOption> alternatives = List.of();
    private final List<Integer> filtered = new ArrayList<>();
    private int selectedIndex;
    private int x;
    private int y;
    private int width = rememberedWidth;
    private int height = rememberedHeight;
    private int scroll;
    private boolean open;
    private boolean dragging;
    private boolean resizing;
    private double dragOffsetX;
    private double dragOffsetY;
    private @Nullable EditBox searchBox;
    private @Nullable Font font;
    private final List<RowBounds> rowBounds = new ArrayList<>();

    public boolean isOpen() {
        return open;
    }

    public List<RecipeTreeInputViewModel> members() {
        return members;
    }

    public void open(Font font, List<RecipeTreeInputViewModel> members, List<DisplayOption> alternatives,
            int selectedIndex, int anchorX, int anchorY, int screenWidth, int screenHeight) {
        this.font = font;
        this.members = List.copyOf(members);
        this.alternatives = List.copyOf(alternatives);
        this.selectedIndex = selectedIndex;
        this.open = true;
        this.dragging = false;
        this.resizing = false;
        this.scroll = 0;
        this.width = clamp(rememberedWidth, MIN_WIDTH, Math.max(MIN_WIDTH, screenWidth - 12));
        this.height = clamp(rememberedHeight, MIN_HEIGHT, Math.max(MIN_HEIGHT, screenHeight - 12));
        this.x = clamp(anchorX + 12, 6, Math.max(6, screenWidth - width - 6));
        this.y = clamp(anchorY - 4, 6, Math.max(6, screenHeight - height - 6));
        this.searchBox = new EditBox(font, searchX(), searchY(), searchWidth(), SEARCH_HEIGHT,
                Component.translatable("gui.jeict.recipe_tree.alternative_search"));
        this.searchBox.setMaxLength(64);
        this.searchBox.setHint(Component.translatable("gui.jeict.recipe_tree.alternative_search_hint"));
        this.searchBox.setValue("");
        this.searchBox.setResponder(ignored -> {
            this.scroll = 0;
            rebuildFilter();
        });
        rebuildFilter();
        int selectedRow = filtered.indexOf(selectedIndex);
        if (selectedRow >= 0) {
            scroll = Math.max(0, selectedRow - 2);
        }
    }

    public void close() {
        open = false;
        dragging = false;
        resizing = false;
        members = List.of();
        alternatives = List.of();
        filtered.clear();
        rowBounds.clear();
        if (searchBox != null) {
            searchBox.setFocused(false);
        }
        searchBox = null;
        rememberedWidth = width;
        rememberedHeight = height;
        CursorHelper.picker(CursorHelper.Shape.ARROW);
    }

    public boolean contains(double mouseX, double mouseY) {
        return open && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public boolean isSearchFocused() {
        return open && searchBox != null && searchBox.isFocused();
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, int screenWidth, int screenHeight,
            BiConsumer<GuiGraphics, SlotPaint> slotPainter) {
        if (!open) {
            CursorHelper.picker(CursorHelper.Shape.ARROW);
            return;
        }
        clampToScreen(screenWidth, screenHeight);
        layoutSearchBox();
        RecipeTreeTheme.Palette theme = RecipeTreeTheme.current();
        RecipeTreeTheme.drawFramedPanel(graphics, x, y, x + width, y + height);
        graphics.drawString(font, Component.translatable("gui.jeict.recipe_tree.alternative_panel_title"),
                x + PAD, y + 5, theme.titleText(), false);
        drawHeaderButton(graphics, closeLeft(), y + 3, "x", theme.danger());
        if (searchBox != null) {
            searchBox.render(graphics, mouseX, mouseY, 0.0F);
        }

        int listTop = listTop();
        int listBottom = listBottom();
        int visible = visibleRows();
        int maxScroll = maxScroll();
        scroll = clamp(scroll, 0, maxScroll);
        rowBounds.clear();
        graphics.enableScissor(x + 1, listTop, x + width - 1, listBottom);
        if (filtered.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.jeict.recipe_tree.alternative_empty"),
                    x + PAD, listTop + 4, theme.mutedText(), false);
        } else {
            int end = Math.min(filtered.size(), scroll + visible);
            for (int row = scroll; row < end; row++) {
                int optionIndex = filtered.get(row);
                DisplayOption option = alternatives.get(optionIndex);
                int rowY = listTop + (row - scroll) * ROW_HEIGHT;
                boolean selected = optionIndex == selectedIndex;
                boolean hovered = mouseX >= x + 2 && mouseX < x + width - 4
                        && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
                if (selected) {
                    graphics.fill(x + 3, rowY, x + width - 4, rowY + ROW_HEIGHT - 1, theme.selectedFill());
                } else if (hovered) {
                    graphics.fill(x + 3, rowY, x + width - 4, rowY + ROW_HEIGHT - 1, theme.hoverFill());
                }
                slotPainter.accept(graphics, new SlotPaint(x + PAD, rowY, option.typedIngredient()));
                graphics.drawString(font, font.plainSubstrByWidth(option.label(), Math.max(8, width - 36)),
                        x + PAD + 20, rowY + 5,
                        selected ? theme.onControlText() : theme.alternativeText(), false);
                rowBounds.add(new RowBounds(optionIndex, option.typedIngredient(), x + 3, rowY, width - 7, ROW_HEIGHT - 1));
            }
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            int trackX = x + width - PAD - SCROLLBAR_WIDTH;
            int trackH = Math.max(1, listBottom - listTop);
            graphics.fill(trackX, listTop, trackX + 1, listBottom, theme.scrollbarTrack());
            int thumbH = Math.min(SCROLL_THUMB, trackH);
            int travel = Math.max(0, trackH - thumbH);
            int thumbY = listTop + (travel <= 0 ? 0 : scroll * travel / maxScroll);
            graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbH, theme.scrollbarThumb());
        }
        drawResizeGrip(graphics, theme.mutedText());
        CursorHelper.picker(pointerCursor(mouseX, mouseY));
    }

    private CursorHelper.Shape pointerCursor(double mouseX, double mouseY) {
        if (resizing) {
            return CursorHelper.Shape.NWSE;
        }
        if (dragging) {
            return CursorHelper.Shape.HAND;
        }
        if (!contains(mouseX, mouseY)) {
            return CursorHelper.Shape.ARROW;
        }
        if (isOverResizeHandle(mouseX, mouseY)) {
            return CursorHelper.Shape.NWSE;
        }
        if (mouseY >= y && mouseY < y + HEADER_HEIGHT
                && !(mouseX >= closeLeft() && mouseX <= closeLeft() + 12 && mouseY >= y + 3 && mouseY <= y + 15)) {
            return CursorHelper.Shape.HAND;
        }
        return CursorHelper.Shape.ARROW;
    }

    public @Nullable ITypedIngredient<?> hoveredIngredient(double mouseX, double mouseY) {
        for (RowBounds row : rowBounds) {
            if (row.contains(mouseX, mouseY)) {
                return row.ingredient();
            }
        }
        return null;
    }

    /** @return selected original alternative index, or empty if the click was consumed without selecting */
    public OptionalInt mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) {
            return OptionalInt.empty();
        }
        if (button == 0 && mouseX >= closeLeft() && mouseX <= closeLeft() + 12
                && mouseY >= y + 3 && mouseY <= y + 15) {
            close();
            return OptionalInt.empty();
        }
        if (button == 0 && isOverResizeHandle(mouseX, mouseY)) {
            resizing = true;
            dragging = false;
            return OptionalInt.empty();
        }
        if (button == 0 && mouseY >= y && mouseY < y + HEADER_HEIGHT && contains(mouseX, mouseY)) {
            dragging = true;
            resizing = false;
            dragOffsetX = mouseX - x;
            dragOffsetY = mouseY - y;
            if (searchBox != null) {
                searchBox.setFocused(false);
            }
            return OptionalInt.empty();
        }
        if (searchBox != null && searchBox.isMouseOver(mouseX, mouseY)) {
            searchBox.setFocused(true);
            searchBox.mouseClicked(mouseX, mouseY, button);
            return OptionalInt.empty();
        }
        if (searchBox != null) {
            searchBox.setFocused(false);
        }
        for (RowBounds row : rowBounds) {
            if (row.contains(mouseX, mouseY)) {
                return OptionalInt.of(row.index());
            }
        }
        return OptionalInt.empty();
    }

    public boolean mouseDragged(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        if (!open) {
            return false;
        }
        if (resizing) {
            width = clamp((int) Math.round(mouseX - x), MIN_WIDTH, Math.max(MIN_WIDTH, screenWidth - x - 6));
            height = clamp((int) Math.round(mouseY - y), MIN_HEIGHT, Math.max(MIN_HEIGHT, screenHeight - y - 6));
            layoutSearchBox();
            return true;
        }
        if (dragging) {
            x = clamp((int) Math.round(mouseX - dragOffsetX), 6, Math.max(6, screenWidth - width - 6));
            y = clamp((int) Math.round(mouseY - dragOffsetY), 6, Math.max(6, screenHeight - height - 6));
            layoutSearchBox();
            return true;
        }
        return false;
    }

    public boolean mouseReleased() {
        boolean was = dragging || resizing;
        dragging = false;
        resizing = false;
        if (open) {
            rememberedWidth = width;
            rememberedHeight = height;
        }
        return was;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!open || !contains(mouseX, mouseY) || maxScroll() <= 0) {
            return false;
        }
        scroll = clamp(scroll - (int) Math.signum(delta), 0, maxScroll());
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return open && searchBox != null && searchBox.isFocused() && searchBox.charTyped(codePoint, modifiers);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) {
            return false;
        }
        if (keyCode == 256) {
            if (searchBox != null && !searchBox.getValue().isEmpty()) {
                searchBox.setValue("");
                return true;
            }
            close();
            return true;
        }
        return searchBox != null && searchBox.isFocused() && searchBox.keyPressed(keyCode, scanCode, modifiers);
    }

    public record SlotPaint(int x, int y, @Nullable ITypedIngredient<?> ingredient) {
    }

    private void rebuildFilter() {
        filtered.clear();
        String query = searchBox == null ? "" : searchBox.getValue().trim();
        for (int index = 0; index < alternatives.size(); index++) {
            if (matches(alternatives.get(index), query)) {
                filtered.add(index);
            }
        }
        scroll = clamp(scroll, 0, maxScroll());
    }

    private static boolean matches(DisplayOption option, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        if (JustEnoughCharactersCompat.contains(option.label(), query)) {
            return true;
        }
        ItemStack stack = option.itemStack();
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return false;
        }
        return JustEnoughCharactersCompat.contains(id.toString(), query)
                || JustEnoughCharactersCompat.contains(id.getPath(), query)
                || JustEnoughCharactersCompat.contains(id.getNamespace(), query);
    }

    private void layoutSearchBox() {
        if (searchBox == null) {
            return;
        }
        searchBox.setX(searchX());
        searchBox.setY(searchY());
        searchBox.setWidth(searchWidth());
    }

    private void clampToScreen(int screenWidth, int screenHeight) {
        width = clamp(width, MIN_WIDTH, Math.max(MIN_WIDTH, screenWidth - 12));
        height = clamp(height, MIN_HEIGHT, Math.max(MIN_HEIGHT, screenHeight - 12));
        x = clamp(x, 6, Math.max(6, screenWidth - width - 6));
        y = clamp(y, 6, Math.max(6, screenHeight - height - 6));
    }

    private int searchX() {
        return x + PAD;
    }

    private int searchY() {
        return y + HEADER_HEIGHT;
    }

    private int searchWidth() {
        return Math.max(20, width - PAD * 2);
    }

    private int listTop() {
        return y + HEADER_HEIGHT + SEARCH_HEIGHT + PAD;
    }

    private int listBottom() {
        return y + height - PAD;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW_HEIGHT);
    }

    private int maxScroll() {
        return Math.max(0, filtered.size() - visibleRows());
    }

    private int closeLeft() {
        return x + width - PAD - 12;
    }

    private boolean isOverResizeHandle(double mouseX, double mouseY) {
        return mouseX >= x + width - RESIZE_HANDLE && mouseX <= x + width
                && mouseY >= y + height - RESIZE_HANDLE && mouseY <= y + height;
    }

    private void drawHeaderButton(GuiGraphics graphics, int bx, int by, String text, int color) {
        RecipeTreeTheme.drawSmallControl(graphics, bx, by, 12, false);
        Font activeFont = font;
        if (activeFont != null) {
            graphics.drawCenteredString(activeFont, text, bx + 6, by + 2, color);
        }
    }

    private void drawResizeGrip(GuiGraphics graphics, int color) {
        for (int row = 0; row < 3; row++) {
            int dots = 3 - row;
            for (int col = 0; col < dots; col++) {
                int gx = x + width - 3 - col * 3;
                int gy = y + height - 3 - row * 3;
                graphics.fill(gx, gy, gx + 2, gy + 2, color);
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record RowBounds(int index, @Nullable ITypedIngredient<?> ingredient, int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }
}
