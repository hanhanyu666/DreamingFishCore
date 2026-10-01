package com.hhy.dreamingfishcore.client.ui.framework.screen;

import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiRoot;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Modal;
import com.hhy.dreamingfishcore.client.ui.framework.widget.TextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;

/**
 * 把框架节点树挂到任意 {@link Screen} 上（主要给原版界面的 Mixin 用）。
 *
 * <p>原版界面保留自身的控件与逻辑以兼容其他模组，绘制与输入优先交给节点树：
 * 渲染时调用 {@link #render}，各输入方法返回 true 表示已被节点树消费。
 * 内容在第一次渲染时才构建，方便等待原版界面的淡入或数据就绪。</p>
 */
public final class ScreenHost {
    private final UiRoot ui = new UiRoot();
    private final Supplier<UiNode<?>> builder;
    private boolean built;

    public ScreenHost(Supplier<UiNode<?>> builder) {
        this.builder = builder;
    }

    public UiRoot ui() {
        return ui;
    }

    public boolean isBuilt() {
        return built;
    }

    /** 立即构建（通常不需要，首次渲染时会自动构建）。 */
    public void ensureBuilt() {
        if (!built) {
            built = true;
            ui.setContent(builder.get());
        }
    }

    /** 丢弃当前内容，下次渲染时重新构建。 */
    public void rebuild() {
        built = false;
    }

    public void render(Screen screen, GuiGraphics graphics) {
        ensureBuilt();
        ui.render(graphics, preciseMouseX(screen), preciseMouseY(screen), screen.width, screen.height);
    }

    public static double preciseMouseX(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.mouseHandler.xpos() * screen.width / Math.max(1, minecraft.getWindow().getScreenWidth());
    }

    public static double preciseMouseY(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.mouseHandler.ypos() * screen.height / Math.max(1, minecraft.getWindow().getScreenHeight());
    }

    public boolean mouseMoved(double mouseX, double mouseY) {
        return built && ui.mouseMoved(mouseX, mouseY);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return built && ui.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return built && ui.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return built && ui.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return built && ui.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Esc 先关闭弹窗、再取消输入框焦点；都没有时返回 false 交还原版处理。 */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!built) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (ui.topOverlay() instanceof Modal modal) {
                if (modal.isDismissible()) {
                    modal.dismiss();
                }
                return true;
            }
            if (ui.focusedNode() instanceof TextField) {
                ui.focus(null);
                return true;
            }
            return false;
        }
        return ui.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char character, int modifiers) {
        return built && ui.charTyped(character, modifiers);
    }

    public void close() {
        ui.close();
    }
}
