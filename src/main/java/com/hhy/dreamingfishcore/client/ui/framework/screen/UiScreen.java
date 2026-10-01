package com.hhy.dreamingfishcore.client.ui.framework.screen;

import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiRoot;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Modal;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * 框架界面的基类。子类实现 {@link #build()} 返回界面内容；输入转交给节点树。
 *
 * <p>背景默认使用终端主题的遮罩（不叠加 Modern UI 的模糊与淡入）；
 * 需要沿用客户端统一的模糊背景时用 {@link Background#VANILLA}。</p>
 */
public abstract class UiScreen extends Screen {
    public enum Background {
        /** 不绘制背景。 */
        NONE,
        /** 主题遮罩。 */
        SCRIM,
        /** 原版背景（装有 Modern UI 时为其模糊背景）。 */
        VANILLA
    }

    protected final UiRoot ui = new UiRoot();
    private boolean built;
    private Background background = Background.SCRIM;

    protected UiScreen(Component title) {
        super(title);
    }

    /** 构建界面内容；只在首次打开时调用一次（窗口尺寸变化只重新布局）。 */
    protected abstract UiNode<?> build();

    /** 界面首次构建完成后调用。 */
    protected void onOpened() {
    }

    protected void setBackground(Background value) {
        background = value;
    }

    public UiRoot ui() {
        return ui;
    }

    @Override
    protected void init() {
        super.init();
        if (!built) {
            built = true;
            ui.setContent(build());
            onOpened();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        ui.render(graphics, preciseMouseX(mouseX), preciseMouseY(mouseY), width, height);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        switch (background) {
            case SCRIM -> graphics.fill(0, 0, width, height, Theme.terminal().color(ColorRole.SCRIM));
            case VANILLA -> super.renderBackground(graphics, mouseX, mouseY, partialTick);
            default -> {
            }
        }
    }

    private double preciseMouseX(int fallback) {
        if (minecraft == null) {
            return fallback;
        }
        return minecraft.mouseHandler.xpos() * width / Math.max(1, minecraft.getWindow().getScreenWidth());
    }

    private double preciseMouseY(int fallback) {
        if (minecraft == null) {
            return fallback;
        }
        return minecraft.mouseHandler.ypos() * height / Math.max(1, minecraft.getWindow().getScreenHeight());
    }

    // ==================== 输入 ====================

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        ui.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return ui.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return ui.mouseReleased(mouseX, mouseY, button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return ui.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return ui.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
                || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (ui.topOverlay() instanceof Modal modal) {
                if (modal.isDismissible()) {
                    modal.dismiss();
                }
                return true;
            }
            if (ui.focusedNode() != null && ui.focusedNode() instanceof com.hhy.dreamingfishcore.client.ui.framework.widget.TextField) {
                ui.focus(null);
                return true;
            }
            if (onEscape()) {
                return true;
            }
        }
        if (ui.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Esc 处理；返回 true 表示已处理（例如页面返回），否则关闭界面。 */
    protected boolean onEscape() {
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return ui.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }

    @Override
    public void removed() {
        ui.close();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
