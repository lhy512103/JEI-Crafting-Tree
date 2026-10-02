package com.lhy.jeict.client;

import net.minecraft.client.gui.GuiGraphics;

/** Compact tree glyph used on JEI-style icon buttons. */
public final class RecipeTreeButtonIcon {
    private RecipeTreeButtonIcon() {
    }

    public static void draw(GuiGraphics graphics, int x, int y, int size, boolean add) {
        int leaf = 0xFF3D8B3D;
        int leafHi = 0xFF5CB85C;
        int trunk = 0xFF8D6E3F;
        int cx = x + size / 2;
        int top = y + Math.max(1, size / 8);
        int mid = y + size / 2;
        int bottom = y + size - 2;
        graphics.fill(cx - size / 2 + 2, top + 2, cx + size / 2 - 2, mid + 1, leaf);
        graphics.fill(cx - size / 3, top, cx + size / 3, mid - 1, leafHi);
        graphics.fill(cx - 1, mid, cx + 1, bottom, trunk);
        if (add) {
            int plus = 0xFFFFFFFF;
            int px = x + size - 5;
            int py = y + 2;
            graphics.fill(px + 1, py, px + 2, py + 5, plus);
            graphics.fill(px, py + 2, px + 3, py + 3, plus);
        }
    }
}
