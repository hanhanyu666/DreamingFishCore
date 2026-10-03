package com.hhy.dreamingfishcore.client.ui.render;

import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import net.minecraft.client.gui.Font;

/**
 * Rank 小标签：不描边，底色在文字起头处最浓、向右淡出，文字是提亮的 Rank 色。
 * 聊天栏名字后面与右上角系统消息卡片共用，保证两处长得一样。
 */
public final class RankBadge {
    public static final float HEIGHT = 5.8F;
    private static final float TEXT_SCALE = 0.5F;
    private static final float PADDING_LEFT = 2.0F;
    /** 文字后面留出的渐隐长度。 */
    private static final float TAIL = 6.0F;

    private RankBadge() {
    }

    public static float width(Font font, String rank) {
        return font.width(rank) * TEXT_SCALE + PADDING_LEFT + TAIL;
    }

    /**
     * 画在 {@code (x, top)}，{@code rgb} 为 Rank 色，{@code alpha} 为 0～1 的整体透明度。返回宽度。
     */
    public static float draw(UiCanvas canvas, Font font, String rank, int rgb, float x, float top, float alpha) {
        float width = width(font, rank);
        int color = UiColor.lerp(0xFF000000 | rgb, 0xFFFFFFFF, 0.2F);
        canvas.shape(x, top, width, HEIGHT).radius(1.5F, 0.0F, 0.0F, 1.5F)
                .horizontalGradient(UiColor.withAlpha(color, 0.51F * alpha), UiColor.withAlpha(color, 0)).draw();
        canvas.text(rank, x + PADDING_LEFT, top + 0.8F,
                UiColor.withAlpha(UiColor.lerp(color, 0xFFFFFFFF, 0.55F), alpha), TEXT_SCALE, false);
        return width;
    }
}
