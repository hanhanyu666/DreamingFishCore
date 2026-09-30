package com.hhy.dreamingfishcore.client.ui.framework.node;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;

/** 系统鼠标指针。只在界面打开（鼠标未被游戏抓取）时生效。 */
final class UiCursors {
    private static final Map<Cursor, Long> HANDLES = new EnumMap<>(Cursor.class);

    private UiCursors() {
    }

    static void apply(Cursor cursor) {
        Minecraft minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().getWindow();
        if (cursor == Cursor.DEFAULT || minecraft.mouseHandler.isMouseGrabbed()) {
            GLFW.glfwSetCursor(window, 0L);
            return;
        }
        long handle = HANDLES.computeIfAbsent(cursor, UiCursors::create);
        GLFW.glfwSetCursor(window, handle);
    }

    private static long create(Cursor cursor) {
        int shape = switch (cursor) {
            case POINTER -> GLFW.GLFW_HAND_CURSOR;
            case TEXT -> GLFW.GLFW_IBEAM_CURSOR;
            case GRAB -> GLFW.GLFW_HAND_CURSOR;
            case RESIZE_H -> GLFW.GLFW_HRESIZE_CURSOR;
            default -> GLFW.GLFW_ARROW_CURSOR;
        };
        return GLFW.glfwCreateStandardCursor(shape);
    }
}
