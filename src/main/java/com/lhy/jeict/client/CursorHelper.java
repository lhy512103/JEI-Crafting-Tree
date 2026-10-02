package com.lhy.jeict.client;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;

/** Standard GLFW cursors for floating-panel move/resize, matching MEST. */
public final class CursorHelper {
    public enum Shape {
        ARROW(0),
        HAND(GLFW.GLFW_HAND_CURSOR),
        EW(GLFW.GLFW_HRESIZE_CURSOR),
        NS(GLFW.GLFW_VRESIZE_CURSOR),
        /** GLFW 3.4 {@code GLFW_RESIZE_NWSE_CURSOR}; LWJGL 3.3 bindings omit the name. */
        NWSE(0x00036007);

        private final int glfwShape;

        Shape(int glfwShape) {
            this.glfwShape = glfwShape;
        }
    }

    private static final long[] HANDLES = new long[Shape.values().length];
    private static Shape applied = Shape.ARROW;
    private static Shape overlay = Shape.ARROW;
    private static Shape picker = Shape.ARROW;

    private CursorHelper() {
    }

    public static void overlay(Shape shape) {
        overlay = shape == null ? Shape.ARROW : shape;
        publish();
    }

    public static void picker(Shape shape) {
        picker = shape == null ? Shape.ARROW : shape;
        publish();
    }

    public static void apply(Shape shape) {
        if (shape == null || shape == Shape.ARROW) {
            restoreArrow();
            return;
        }
        long cursor = handle(shape);
        long window = windowHandle();
        if (cursor == 0L || window == 0L) {
            restoreArrow();
            return;
        }
        GLFW.glfwSetCursor(window, cursor);
        applied = shape;
    }

    public static void resetCursor() {
        overlay = Shape.ARROW;
        picker = Shape.ARROW;
        restoreArrow();
    }

    private static void publish() {
        apply(overlay != Shape.ARROW ? overlay : picker);
    }

    private static void restoreArrow() {
        if (applied == Shape.ARROW) {
            return;
        }
        long window = windowHandle();
        if (window != 0L) {
            GLFW.glfwSetCursor(window, 0L);
        }
        applied = Shape.ARROW;
    }

    private static long handle(Shape shape) {
        int index = shape.ordinal();
        if (HANDLES[index] == 0L && shape.glfwShape != 0) {
            HANDLES[index] = GLFW.glfwCreateStandardCursor(shape.glfwShape);
        }
        return HANDLES[index];
    }

    private static long windowHandle() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return 0L;
        }
        return minecraft.getWindow().getWindow();
    }
}
