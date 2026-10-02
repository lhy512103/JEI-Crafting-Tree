package com.lhy.jeict.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.jetbrains.annotations.Nullable;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.runtime.IBookmarkOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.fml.ModList;

/**
 * JEI bookmark-bar chrome: follow {@code historyButton} (and ExtendedAE Plus) and draw
 * with JEI's own {@code Internal.getTextures()} sprites.
 */
public final class JeiStyleButtons {
    public static final int SIZE = 20;
    public static final int STEP = 22;

    private static @Nullable Method getTextures;
    private static @Nullable Method getButtonForState;
    private static @Nullable Method nineSliceDraw;
    private static @Nullable Method getCraftableFirst;
    private static @Nullable Method getRecipePlusSign;
    private static @Nullable Field historyButtonField;
    private static @Nullable Class<?> historyButtonOwner;
    private static @Nullable Method iconButtonGetArea;
    private static @Nullable Method rectX;
    private static @Nullable Method rectY;
    private static @Nullable Method rectW;
    private static @Nullable Method rectEmpty;
    private static boolean texturesResolved;

    private JeiStyleButtons() {
    }

    public static @Nullable Bounds bookmarkShortcutBounds(IBookmarkOverlay overlay) {
        Bounds history = historyButtonBounds(overlay);
        if (history == null) {
            return null;
        }
        int extra = ModList.get().isLoaded("extendedae_plus") ? STEP : 0;
        return new Bounds(history.x() + STEP + extra, history.y(), SIZE, SIZE);
    }

    public static void drawButton(GuiGraphics graphics, int x, int y, boolean hovered, boolean pressed) {
        Object textures = textures();
        if (textures != null) {
            try {
                if (getButtonForState == null) {
                    getButtonForState = textures.getClass().getMethod("getButtonForState",
                            boolean.class, boolean.class, boolean.class);
                }
                Object nine = getButtonForState.invoke(textures, pressed, true, hovered);
                if (nine != null) {
                    if (nineSliceDraw == null) {
                        nineSliceDraw = nine.getClass().getMethod("draw", GuiGraphics.class,
                                int.class, int.class, int.class, int.class);
                    }
                    nineSliceDraw.invoke(nine, graphics, x, y, SIZE, SIZE);
                    return;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        int border = hovered ? 0xFFC6C6C6 : 0xFF8B8B8B;
        int background = hovered ? 0xFF6B6B6B : 0xFF4A4A4A;
        graphics.fill(x, y, x + SIZE, y + SIZE, border);
        graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, background);
    }

    public static @Nullable IDrawable icon(boolean add) {
        return jeiIcon(add);
    }

    public static void drawIcon(GuiGraphics graphics, int x, int y, boolean add) {
        IDrawable icon = jeiIcon(add);
        if (icon != null) {
            int ix = x + Math.max(0, (SIZE - icon.getWidth()) / 2);
            int iy = y + Math.max(0, (SIZE - icon.getHeight()) / 2);
            icon.draw(graphics, ix, iy);
            return;
        }
        RecipeTreeButtonIcon.draw(graphics, x + 2, y + 2, 16, add);
    }

    public static void drawCraftIcon(GuiGraphics graphics, int x, int y) {
        IDrawable icon = jeiIcon(false);
        if (icon != null) {
            icon.draw(graphics, x, y);
            return;
        }
        RecipeTreeButtonIcon.draw(graphics, x, y, 16, false);
    }

    private static @Nullable IDrawable jeiIcon(boolean add) {
        Object textures = textures();
        if (textures == null) {
            return null;
        }
        try {
            if (add) {
                if (getRecipePlusSign == null) {
                    getRecipePlusSign = textures.getClass().getMethod("getRecipePlusSign");
                }
                Object icon = getRecipePlusSign.invoke(textures);
                return icon instanceof IDrawable drawable ? drawable : null;
            }
            if (getCraftableFirst == null) {
                getCraftableFirst = textures.getClass().getMethod("getCraftableFirst");
            }
            Object icon = getCraftableFirst.invoke(textures);
            return icon instanceof IDrawable drawable ? drawable : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static @Nullable Object textures() {
        if (texturesResolved && getTextures == null) {
            return null;
        }
        try {
            if (getTextures == null) {
                Class<?> internal = Class.forName("mezz.jei.common.Internal");
                getTextures = internal.getMethod("getTextures");
            }
            texturesResolved = true;
            return getTextures.invoke(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            texturesResolved = true;
            getTextures = null;
            return null;
        }
    }

    private static @Nullable Bounds historyButtonBounds(IBookmarkOverlay overlay) {
        if (overlay == null) {
            return null;
        }
        try {
            Class<?> owner = overlay.getClass();
            if (historyButtonField == null || historyButtonOwner != owner) {
                Field field = findField(owner, "historyButton");
                if (field == null) {
                    return null;
                }
                field.setAccessible(true);
                historyButtonField = field;
                historyButtonOwner = owner;
            }
            Object button = historyButtonField.get(overlay);
            if (button == null) {
                return null;
            }
            if (iconButtonGetArea == null) {
                iconButtonGetArea = button.getClass().getMethod("getArea");
            }
            Object area = iconButtonGetArea.invoke(button);
            if (area == null) {
                return null;
            }
            if (rectX == null) {
                Class<?> rect = area.getClass();
                rectX = rect.getMethod("x");
                rectY = rect.getMethod("y");
                rectW = rect.getMethod("width");
                rectEmpty = rect.getMethod("isEmpty");
            }
            if (Boolean.TRUE.equals(rectEmpty.invoke(area))) {
                return null;
            }
            int width = ((Number) rectW.invoke(area)).intValue();
            if (width <= 0) {
                return null;
            }
            return new Bounds(((Number) rectX.invoke(area)).intValue(),
                    ((Number) rectY.invoke(area)).intValue(), width, SIZE);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            historyButtonField = null;
            historyButtonOwner = null;
            return null;
        }
    }

    private static @Nullable Field findField(Class<?> type, String name) {
        Class<?> cursor = type;
        while (cursor != null && cursor != Object.class) {
            try {
                return cursor.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        return null;
    }

    public record Bounds(int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
