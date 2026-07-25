package com.lhy.jeict.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Visual styles for the recipe-tree UI. {@link Style#NEX} mirrors the AE2
 * terminal chrome used by MEST: cool grey panels, restrained one-pixel bevels,
 * dark controls and a pale cyan selection accent. {@link Style#CLASSIC} is a
 * blueprint-paper look: cool white grid canvas with navy ink. {@link Style#DARK}
 * is the original high-contrast dark terminal.
 */
public final class RecipeTreeTheme {
    public enum Style {
        NEX,
        CLASSIC,
        DARK
    }

    private static final int GRID_LOGICAL_STEP = 8;
    private static final int GRID_MAJOR_EVERY = 5;

    private static Style style = Style.NEX;

    private static final int[] DARK_GROUP_COLORS = {
            0xFF7CC7FF, 0xFFFFC857, 0xFF7CFF9B, 0xFFFF7CA8, 0xFFB47CFF, 0xFFFF9B5F
    };
    private static final int[] NEX_GROUP_COLORS = {
            0xFF5E7190, 0xFF8A6F52, 0xFF4C8874, 0xFF9A5D70, 0xFF766AA2, 0xFF7F7A52
    };
    private static final int[] CLASSIC_GROUP_COLORS = {
            0xFF4A86C0, 0xFF2E7A50, 0xFF9A5040, 0xFF7A6AAA, 0xFF3A8A9A, 0xFF8A7A30
    };

    private static final Palette DARK = new Palette(
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
            // modified / focus / link / on-control / notice backdrop
            0xFFFF9800, 0xFFFFB74D, 0xFF76B9E6, 0xFFFFFFFF, 0xFFFFFFFF, 0xE010141A,
            DARK_GROUP_COLORS);

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
            // modified / focus / link / on-control / notice backdrop
            0xFFC07A18, 0xFFB4700F, 0xFF3B6FA8, 0xFFF2F2F2, 0xFFFFFFFF, 0xF0413F54,
            NEX_GROUP_COLORS);

    /**
     * Drafting-paper look: warm paper canvas ruled with a blue grid, deep navy
     * ink for every border, and no bevels anywhere. Selection is a solid ink
     * fill with paper-coloured text rather than a translucent tint.
     */
    private static final Palette CLASSIC = new Palette(
            // paper canvas / faint overlay
            0xFFF7F4EC, 0x30000000,
            // title / hint / metric / muted text
            0xFF16346B, 0xFF5C6E8C, 0xFF1E4A7A, 0xFF93A2B8,
            // node / material fill + borders
            0xFFFCFAF4, 0xFFF0EDE3, 0xFF16346B, 0xFF8FA6C4, 0xFFCE3A2E,
            // edges
            0x5516346B, 0xFF16346B,
            // nested panels
            0xFF16346B, 0xFF8FA6C4, 0xFFFCFAF4,
            // controls
            0xFF16346B, 0xFFFCFAF4, 0xFF16346B, 0xFF1E6CB5, 0xFFCE3A2E, 0xFF1E6CB5, 0xFF1F7A4C,
            // selection + alternative text
            0xFF16346B, 0xFF16346B,
            // item slots
            0xFF16346B, 0xFF8FA6C4, 0xFFF2EFE6,
            // machine slots
            0xFF16346B, 0xFF8FA6C4, 0xFFFCFAF4,
            // overlay
            0xF7F7F4EC, 0xFFF0EDE3, 0xFFDCE7F5, 0xFF16346B, 0xFFD8D3C6,
            // stock colors
            0xFF1F7A4C, 0xFF9A6A10, 0xFFCE3A2E,
            // grid + chrome
            0x1C2F5C96, 0x382F5C96, 0xFFFCFAF4, 0xFF16346B, 0xFFDCE7F5, 0xFF8FA6C4, 0xFFDCE7F5,
            // bevel helpers
            0xFF16346B, 0xFF8FA6C4, 0xFFDCE7F5,
            // modified / focus / link / on-control / notice backdrop
            0xFFC2620C, 0xFFC2620C, 0xFF1E6CB5, 0xFFFCFAF4, 0xFFFFFFFF, 0xF2FCFAF4,
            CLASSIC_GROUP_COLORS);

    private RecipeTreeTheme() {
    }

    public static Palette current() {
        return switch (style) {
            case NEX -> NEX;
            case CLASSIC -> CLASSIC;
            case DARK -> DARK;
        };
    }

    public static boolean isNexStyle() {
        return style == Style.NEX;
    }

    public static boolean isClassicStyle() {
        return style == Style.CLASSIC;
    }

    /** Cycles AE2 -> classic blueprint -> dark terminal. */
    public static void toggle() {
        style = switch (style) {
            case NEX -> Style.CLASSIC;
            case CLASSIC -> Style.DARK;
            case DARK -> Style.NEX;
        };
    }

    public static Component styleButtonMessage() {
        return Component.translatable(switch (style) {
            case NEX -> "gui.jeict.recipe_tree.overview_style_nex";
            case CLASSIC -> "gui.jeict.recipe_tree.overview_style_classic";
            case DARK -> "gui.jeict.recipe_tree.overview_style_dark";
        });
    }

    /** Single-pixel ink outline around a solid fill; the classic style's only frame. */
    public static void drawFlatPanel(GuiGraphics graphics, int left, int top, int right, int bottom,
            int fill, int border) {
        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) {
            return;
        }
        graphics.fill(left, top, right, bottom, border);
        if (width > 2 && height > 2) {
            graphics.fill(left + 1, top + 1, right - 1, bottom - 1, fill);
        }
    }

    /** Thin ink rule used to divide sections inside a classic panel. */
    public static void drawHairline(GuiGraphics graphics, int left, int right, int y) {
        graphics.fill(left, y, right, y + 1, current().bevelLight());
    }

    /** Outer frame + raised body used by windows, toolbars and popovers. */
    public static void drawRaisedPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        Palette theme = current();
        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) {
            return;
        }
        if (isClassicStyle()) {
            drawFlatPanel(graphics, left, top, right, bottom, theme.chromeFill(), theme.chromeBorder());
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
        if (isClassicStyle()) {
            drawFlatPanel(graphics, left, top, right, bottom, theme.background(), theme.chromeBorder());
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
        if (isClassicStyle()) {
            drawFlatPanel(graphics, left, top, right, bottom, fill, border);
            return;
        }
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
        if (isClassicStyle()) {
            // Ink outline stays uniform; state reads from a stripe so the canvas keeps a
            // drafted look instead of a grid of saturated outlines.
            drawFlatPanel(graphics, left, top, right, bottom, theme.nodeFill(), theme.nodeBorder());
            if (right - left > 5) {
                graphics.fill(left + 1, top + 1, left + 3, bottom - 1, accent);
            }
            return;
        }
        int fill = isNexStyle() ? 0xFFF7F7F5 : theme.nodeFill();
        graphics.fill(left, top, right, bottom, accent);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, fill);
    }

    /** Triple-border framed panel used by popovers and the top-materials strip. */
    public static void drawFramedPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        Palette theme = current();
        if (isClassicStyle()) {
            drawFlatPanel(graphics, left, top, right, bottom, theme.panelInner(), theme.panelBorder());
            return;
        }
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
        if (isClassicStyle()) {
            drawFlatPanel(graphics, x, y, x + 18, y + 18, theme.slotInner(), theme.slotBorder());
            return;
        }
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
        if (isClassicStyle()) {
            int classicBorder = !active ? theme.mutedText() : (hovered ? theme.accent() : theme.controlBorder());
            int classicFill = !active ? theme.chromeFill() : (hovered ? theme.controlHoverFill() : theme.controlFill());
            drawFlatPanel(graphics, x, y, x + width, y + height, classicFill, classicBorder);
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

    /**
     * Ruled grid paper behind the graph. Spacing follows the logical grid through
     * the current pan/zoom so the paper travels with the tree, and thin lines drop
     * out once they would alias into each other.
     */
    public static void drawBlueprintGrid(GuiGraphics graphics, int left, int top, int right, int bottom,
            double panX, double panY, double zoom) {
        if (right - left <= 0 || bottom - top <= 0 || zoom <= 0.0D) {
            return;
        }
        Palette theme = current();
        double step = GRID_LOGICAL_STEP * zoom;
        if (step <= 0.0D) {
            return;
        }
        boolean thinLines = step >= 3.0D;
        double majorStep = step * GRID_MAJOR_EVERY;
        drawGridAxis(graphics, left, top, right, bottom, panX, majorStep, true, theme.gridMajorLine());
        drawGridAxis(graphics, left, top, right, bottom, panY, majorStep, false, theme.gridMajorLine());
        if (!thinLines) {
            return;
        }
        drawGridAxis(graphics, left, top, right, bottom, panX, step, true, theme.gridLine());
        drawGridAxis(graphics, left, top, right, bottom, panY, step, false, theme.gridLine());
    }

    private static void drawGridAxis(GuiGraphics graphics, int left, int top, int right, int bottom,
            double pan, double step, boolean vertical, int color) {
        double axisStart = vertical ? left : top;
        double axisEnd = vertical ? right : bottom;
        // First gridline at or after the viewport edge, expressed in screen space.
        double firstIndex = Math.ceil((axisStart - pan) / step);
        for (double position = pan + firstIndex * step; position < axisEnd; position += step) {
            int line = (int) Math.round(position);
            if (line < axisStart || line >= axisEnd) {
                continue;
            }
            if (vertical) {
                graphics.fill(line, top, line + 1, bottom, color);
            } else {
                graphics.fill(left, line, right, line + 1, color);
            }
        }
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
            int modifiedAccent,
            int focusHighlight,
            int linkText,
            int onControlText,
            int slotOverlayText,
            int noticeBackground,
            int[] groupColors) {
        public int groupColor(int index) {
            return groupColors[index % groupColors.length];
        }
    }
}
