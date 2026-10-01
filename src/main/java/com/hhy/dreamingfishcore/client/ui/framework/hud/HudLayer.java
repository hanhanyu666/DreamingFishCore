package com.hhy.dreamingfishcore.client.ui.framework.hud;

import net.minecraft.client.Minecraft;

/**
 * 一块 HUD 区域。所有可见区域每帧共用一个 {@link com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas}，
 * 形状合批提交，文字与物品按层穿插，避免各自开一遍 GUI 批次。
 */
public interface HudLayer {
    /** 绘制时机。 */
    enum Pass {
        /** 原版 HUD 绘制完成之后（RenderGuiEvent.Post）。 */
        MAIN,
        /** 最后一刻，盖在其他模组的 HUD 之上（例如小地图）。 */
        OVERLAY
    }

    default Pass pass() {
        return Pass.MAIN;
    }

    /** 同一时机内的绘制顺序，小的先画、在下面。 */
    default int order() {
        return 0;
    }

    boolean visible(Minecraft minecraft);

    void paint(HudFrame frame);
}
