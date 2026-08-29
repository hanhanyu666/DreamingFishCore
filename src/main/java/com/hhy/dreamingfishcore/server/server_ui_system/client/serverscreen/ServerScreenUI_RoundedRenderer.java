package com.hhy.dreamingfishcore.server.server_ui_system.client.serverscreen;

import com.hhy.dreamingfishcore.client.ui.components.UiPanelRenderer;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 终端统一圆角矩形渲染器。
 *
 * <p>圆角由一个小型四边形网格一次性提交。不要在这里按扫描线调用
 * {@link GuiGraphics#fill}：Screen 默认处于非 managed 绘制模式，每次 fill 都会刷新
 * GUI buffer，大尺寸面板会因此在一帧内触发数百次 GPU 提交。</p>
 *
 * <p>外沿使用一条带顶点透明度插值的 1px 羽化带。这样既能保留 Minecraft 原生
 * GUI RenderType 的混合顺序，也能在终端的非整数缩放下获得平滑圆角。</p>
 */
final class ServerScreenUI_RoundedRenderer {
    private ServerScreenUI_RoundedRenderer() {
    }

    static boolean draw(GuiGraphics graphics, int x, int y, int width, int height,
                        int radius, int fillColor, int borderColor) {
        int smoothRadius = radius <= 0 ? 0 : radius * 2;
        UiPanelRenderer.smoothRoundedRect(graphics, x, y, width, height,
                smoothRadius, fillColor, borderColor);
        return true;
    }
}
