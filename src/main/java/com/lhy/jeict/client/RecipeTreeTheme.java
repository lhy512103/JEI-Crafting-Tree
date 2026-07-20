package com.lhy.jeict.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Visual styles for the recipe-tree UI. {@link Style#NEX} mirrors the AE2
 * terminal chrome used by MEST: cool grey panels, restrained one-pixel bevels,
 * dark controls and a pale cyan selection accent.
 */
public final class RecipeTreeTheme {
    public enum Style {
        NEX,
        CLASSIC
    }

    private static Style style = Style.NEX;

    private static final int[] CLASSIC_GROUP_COLORS = {
            0xFF7CC7FF, 0xFFFFC857, 0xFF7CFF9B, 0xFFFF7CA8, 0xFFB47CFF, 0xFFFF9B5F
    };
    private static final int[] NEX_GROUP_COLORS = {
            0xFF5E7190, 0xFF8A6F52, 0xFF4C8874, 0xFF9A5D70, 0xFF766AA2, 0xFF7F7A52
    };

    private static final Palette CLASSIC = new Palette(
            0xFF10161C, 0x16000000,
            0xFFFFFFFF, 0xFFDDE6EE, 0xFFEAF4FF, 0xFFD4DEE7,
            0xCC11161B, 0xFF273038, 0xFFFFFFFF, 0xFFC3CBD3, 0xFFE07A7A,
            0xB0000000, 0xFFFFFFFF,
            0xFFFFFFFF, 0xFF4D5962, 0xCC11161C,
            0xFF4D5962, 0xFF30373D, 0xFFFFFFFF, 0xFF7CC7FF, 0xFFFF8A80, 0xFFFFE082, 0xFF7CFF9B,
            0x3358A6FF, 0xFF263238,
            0xFF8C969D, 0xFFC9D2D8, 0xFF5C6770,
            0xFFFFFFFF, 0xFFBFC9D2, 0xFF2A3137,
            0x3310161C, 0x33273038, 0x40FFFFFF, 0x80FFFFFF, 0x40FFFFFF,
            0xFF00FF00, 0xFFFFFF00, 0xFFFF0000,
            0x241E2A31, 0x38384650, 0xD010161C, 0xFF4D5962, 0xFF66737B, 0xCC05080A, 0xFF303E47,
            0xFFFFFFFF, 0xFF1A2228, 0xFF3A454D,
            CLASSIC_GROUP_COLORS);

    /**
     * MEST uses AE2's generated terminal background. These values match the
     * shared panel constants in MEST's ModulePanel and its screen palette.
     */
    private static final Palette NEX = new Palette(
            // workspace well (sunken) / faint overlay
            0xFFDEDFE3, 0xB0000000,
            // title / hint / metric / muted text
            0xFF413F54, 0xFF878FA5, 0xFF5D6174, 0xFF878FA5,
            // node / material fill + borders
            0xFFC8CAD2, 0xFFBEC1CB, 0xFF777B8C, 0xFF878FA5, 0xFFCE2401,
            // edges
            0x60777B8C, 0xFF777B8C,
            // nested panels
            0xFF777B8C, 0xFFC8CAD2, 0xFFD9DBE1,
            // controls
            0xFF413F54, 0xFF9A9FB4, 0xFFF2F2F2, 0xFFACE9FF, 0xFFCE2401, 0xFFACE9FF, 0xFF4C9A72,
            // selection + alternative text
            0x88ACE9FF, 0xFF413F54,
            // item slots (sunken dark well)
            0xFF55586A, 0xFF777B8C, 0xFF676A7A,
            // machine slots
            0xFF777B8C, 0xFFB8BBC5, 0xFFD9DBE1,
            // overlay
            0xF4C8CAD2, 0xE8BEC1CB, 0x55ACE9FF, 0xFF777B8C, 0xFFB8BBC5,
            // stock colors
            0xFF3B9B6A, 0xFFC08A35, 0xFFCE2401,
            // grid + chrome
            0x18777B8C, 0x30777B8C, 0xFFCBCCD4, 0xFF413F54, 0xFFF2F2F2, 0xFF878FA5, 0xFF9CD3FF,
            // bevel helpers
            0xFF517497, 0xFF696D7E, 0xFFE5E7ED,
            NEX_GROUP_COLORS);

    private RecipeTreeTheme() {
    }

    public static Palette current() {
        return style == Style.NEX ? NEX : CLASSIC;
    }

    public static boolean isNexStyle() {
        return style == Style.NEX;
    }

    public static void toggle() {
        style = style == Style.NEX ? Style.CLASSIC : Style.NEX;
    }

    public static Component styleButtonMessage() {
        return Component.translatable(style == Style.NEX
                ? "gui.jeict.recipe_tree.overview_style_nex"
                : "gui.jeict.recipe_tree.overview_style_classic");
    }

    /** Outer frame + raised body used by windows, toolbars and popovers. */
    public static void drawRaisedPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        Palette theme = current();
        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) {
            return;
        }
        graphics.fill(left, top, right, bottom, theme.chromeBorder());
        if (width <= 2 || height <= 2) {
            return;
        }
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, theme.chromeFill());
        // Same edge order as AE2's textures/guis/background.png.
        graphics.fill(left + 1, top + 1, right - 1, top + 2, theme.raisedHighlight());
        graphics.fill(left + 1, top + 1, left + 2, bottom - 1, theme.raisedHighlight());
        graphics.fill(left + 1, bottom - 2, right - 1, bottom - 1, theme.raisedShadow());
        graphics.fill(right - 2, top + 1, right - 1, bottom - 1, theme.raisedShadow());
    }

    /** Nested content well: darker sunken surface inside a raised frame. */
    public static void drawSunkenPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        Palette theme = current();
        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) {
            return;
        }
        graphics.fill(left, top, right, bottom, theme.chromeBorder());
        if (width <= 2 || height <= 2) {
            return;
        }
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, theme.background());
        graphics.fill(left + 1, top + 1, right - 1, top + 2, theme.raisedShadow());
        graphics.fill(left + 1, top + 1, left + 2, bottom - 1, theme.raisedShadow());
        graphics.fill(left + 1, bottom - 2, right - 1, bottom - 1, theme.raisedHighlight());
        graphics.fill(right - 2, top + 1, right - 1, bottom - 1, theme.raisedHighlight());
    }

    /** Compact raised card used by graph nodes and material chips. */
    public static void drawNodePanel(GuiGraphics graphics, int left, int top, int right, int bottom,
            int fill, int border) {
        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) {
            return;
        }
        Palette theme = current();
        graphics.fill(left, top, right, bottom, border);
        if (width <= 2 || height <= 2) {
            return;
        }
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, fill);
        graphics.fill(left + 1, top + 1, right - 1, top + 2, theme.raisedHighlight());
        graphics.fill(left + 1, top + 1, left + 2, bottom - 1, theme.raisedHighlight());
        graphics.fill(left + 1, bottom - 2, right - 1, bottom - 1, theme.bevelDark());
        graphics.fill(right - 2, top + 1, right - 1, bottom - 1, theme.bevelDark());
    }

    /** White slot card with the same raised border language as terminal buttons. */
    public static void drawMarkdownNode(GuiGraphics graphics, int left, int top, int right, int bottom,
            int accent) {
        if (right - left <= 2 || bottom - top <= 2) {
            return;
        }
        Palette theme = current();
        int fill = isNexStyle() ? 0xFFF7F7F5 : 0xFFFFFFFF;
        graphics.fill(left, top, right, bottom, theme.nodeBorder());
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, fill);
    }

    /** Triple-border framed panel used by popovers and the top-materials strip. */
    public static void drawFramedPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        Palette theme = current();
        graphics.fill(left, top, right, bottom, theme.panelBorder());
        if (right - left <= 4 || bottom - top <= 4) {
            return;
        }
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, theme.panelMiddle());
        graphics.fill(left + 1, top + 1, right - 1, top + 2, theme.raisedHighlight());
        graphics.fill(left + 1, top + 1, left + 2, bottom - 1, theme.raisedHighlight());
        graphics.fill(left + 1, bottom - 2, right - 1, bottom - 1, theme.raisedShadow());
        graphics.fill(right - 2, top + 1, right - 1, bottom - 1, theme.raisedShadow());
        if (right - left > 6 && bottom - top > 6) {
            graphics.fill(left + 3, top + 3, right - 3, bottom - 3, theme.panelInner());
        }
    }

    /** AE2-style sunken 18×18 item slot. */
    public static void drawSlot(GuiGraphics graphics, int x, int y) {
        Palette theme = current();
        graphics.fill(x, y, x + 18, y + 18, theme.slotBorder());
        graphics.fill(x + 1, y + 1, x + 17, y + 17, theme.slotInner());
        // sunken bevel
        graphics.fill(x + 1, y + 1, x + 17, y + 2, theme.raisedShadow());
        graphics.fill(x + 1, y + 1, x + 2, y + 17, theme.raisedShadow());
        graphics.fill(x + 1, y + 16, x + 17, y + 17, theme.bevelLight());
        graphics.fill(x + 16, y + 1, x + 17, y + 17, theme.bevelLight());
    }

    /** Squared terminal button with optional hover/disabled states. */
    public static void drawButton(GuiGraphics graphics, int x, int y, int width, int height,
            boolean hovered, boolean active) {
        Palette theme = current();
        if (isNexStyle()) {
            int fill = !active ? 0xFF696D88 : (hovered ? 0xFF9CD3FF : 0xFF9A9FB4);
            int highlight = !active ? 0xFF878FA5 : (hovered ? 0xFFDAFFFF : 0xFFADB0C4);
            int shadow = hovered ? 0xFF708CBA : 0xFF696D88;
            graphics.fill(x, y, x + width, y + height, 0xFF413F54);
            if (width > 2 && height > 2) {
                graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
                graphics.fill(x + 1, y + 1, x + width - 1, y + 2, highlight);
                graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, shadow);
            }
            return;
        }
        int border = !active ? theme.nodeBorder() : (hovered ? theme.accent() : theme.controlBorder());
        int fill = !active ? theme.panelInner() : (hovered ? theme.controlHoverFill() : theme.controlFill());
        graphics.fill(x, y, x + width, y + height, border);
        if (width <= 2 || height <= 2) {
            return;
        }
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        if (active && !hovered) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + 2, theme.raisedHighlight());
            graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, theme.raisedShadow());
        }
    }

    public static void drawSmallControl(GuiGraphics graphics, int x, int y, int size, boolean hovered) {
        drawButton(graphics, x, y, size, size, hovered, true);
    }

    public static void drawBorder(GuiGraphics graphics, int left, int top, int right, int bottom, int color) {
        graphics.fill(left, top, right, top + 1, color);
        graphics.fill(left, bottom - 1, right, bottom, color);
        graphics.fill(left, top, left + 1, bottom, color);
        graphics.fill(right - 1, top, right, bottom, color);
    }

    public record Palette(
            int background,
            int backgroundOverlay,
            int titleText,
            int hintText,
            int metricText,
            int mutedText,
            int nodeFill,
            int materialFill,
            int nodeBorder,
            int leafBorder,
            int patternHintBorder,
            int edgeShadow,
            int edge,
            int panelBorder,
            int panelMiddle,
            int panelInner,
            int controlBorder,
            int controlFill,
            int controlText,
            int accent,
            int danger,
            int pinned,
            int success,
            int selectedFill,
            int alternativeText,
            int slotBorder,
            int slotMiddle,
            int slotInner,
            int machineSlotBorder,
            int machineSlotMiddle,
            int machineSlotInner,
            int overlayFill,
            int overlayGroupFill,
            int hoverFill,
            int scrollbarThumb,
            int scrollbarTrack,
            int enough,
            int partial,
            int missing,
            int gridLine,
            int gridMajorLine,
            int chromeFill,
            int chromeBorder,
            int raisedHighlight,
            int raisedShadow,
            int controlHoverFill,
            int controlHoverText,
            int bevelDark,
            int bevelLight,
            int[] groupColors) {
        public int groupColor(int index) {
            return groupColors[index % groupColors.length];
        }
    }
}
