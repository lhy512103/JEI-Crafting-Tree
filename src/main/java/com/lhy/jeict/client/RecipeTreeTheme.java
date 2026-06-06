package com.lhy.jeict.client;

import net.minecraft.network.chat.Component;

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
            0xFF2489A6, 0xFFE19A32, 0xFF2F9B72, 0xFFD45A73, 0xFF7D6FD0, 0xFFB8793C
    };

    private static final Palette CLASSIC = new Palette(
            0xFF10161C, 0x16000000,
            0xFFFFFFFF, 0xFFDDE6EE, 0xFFEAF4FF, 0xFFD4DEE7,
            0xCC11161B, 0xFF273038, 0xFFFFFFFF, 0xFFC3CBD3, 0xFFE07A7A,
            0xB0000000, 0xFFFFFFFF,
            0xFFFFFFFF, 0xFF4D5962, 0xCC11161B,
            0xFF4D5962, 0xFF30373D, 0xFFFFFFFF, 0xFF7CC7FF, 0xFFFF8A80, 0xFFFFE082, 0xFF7CFF9B,
            0x3358A6FF, 0xFF263238,
            0xFF8C969D, 0xFFC9D2D8, 0xFF5C6770,
            0xFFFFFFFF, 0xFFBFC9D2, 0xFF2A3137,
            0x3310161C, 0x33273038, 0x40FFFFFF, 0x80FFFFFF, 0x40FFFFFF,
            0xFF00FF00, 0xFFFFFF00, 0xFFFF0000,
            CLASSIC_GROUP_COLORS);

    private static final Palette NEX = new Palette(
            0xFFF6F9FC, 0x00000000,
            0xFF172B4D, 0xFF526779, 0xFF234B5F, 0xFF445A6A,
            0xFFFFFFFF, 0xFFF7FBFC, 0xFFB7D5DD, 0xFFC8D6E0, 0xFFD86A6A,
            0x33FFFFFF, 0xFF8DB9C7,
            0xFF8DB9C7, 0xFFE4F3F6, 0xFFFFFFFF,
            0xFF8DB9C7, 0xFFEAF7F9, 0xFF173B4A, 0xFF1684A2, 0xFFD84A4A, 0xFFE2A32C, 0xFF2F9B72,
            0x2632A7C8, 0xFF263238,
            0xFFAEC6D1, 0xFFEAF3F6, 0xFFFFFFFF,
            0xFFAEC6D1, 0xFFEAF3F6, 0xFFFFFFFF,
            0xF7FFFFFF, 0xFFF2F8FA, 0x2632A7C8, 0x668DB9C7, 0x3032A7C8,
            0xFF238A5E, 0xFFC18A1B, 0xFFD84A4A,
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
            int[] groupColors) {
        public int groupColor(int index) {
            return groupColors[index % groupColors.length];
        }
    }
}
