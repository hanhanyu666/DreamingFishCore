package com.hhy.dreamingfishcore.client.debug;

import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextLayout;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 渲染核心自检界面：覆盖圆角、描边、渐变、阴影、圆弧、裁剪、文字、物品与头像。 */
final class UiCanvasTestScreen extends Screen {
    private final UiCanvas canvas = new UiCanvas();

    UiCanvasTestScreen() {
        super(Component.literal("UiCanvas Test"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Theme theme = Theme.terminal();
        canvas.begin(graphics);
        canvas.fill(0, 0, width, height, 0xFF0B1016);

        float x = 12;
        float y = 12;
        canvas.shape(x, y, 90, 50).radius(8).fill(theme.color(ColorRole.SURFACE_RAISED))
                .shadow(Theme.Elevation.LEVEL3).draw();
        canvas.shape(x + 100, y, 90, 50).radius(12, 2, 12, 2).fill(0xFF203040)
                .border(1.5F, theme.color(ColorRole.ACCENT)).draw();
        canvas.shape(x + 200, y, 90, 50).radius(6).verticalGradient(0xFF5CCFE6, 0xFF1B3A66).draw();
        canvas.shape(x + 300, y, 90, 50).radius(25).radial(0xFFFFC857, 0x00FFC857, 45, 25, 45).draw();
        canvas.shape(x + 400, y, 90, 50).radius(8).fill(0x66FFFFFF)
                .shadow(new Theme.Shadow(0, 6, 16, 0, 0xAA000000)).border(1, 0x88FFFFFF).draw();

        y += 64;
        canvas.arc(x + 25, y + 25, 18, 5, (float) (-Math.PI / 2), (float) (Math.PI * 1.4), 0xFF5BD69A, 0xFF5CCFE6);
        canvas.arc(x + 80, y + 25, 18, 3, 0, (float) (Math.PI * 2), 0x33FFFFFF, 0x33FFFFFF);
        canvas.arc(x + 80, y + 25, 18, 3, (float) (-Math.PI / 2), (float) (Math.PI * 0.6), 0xFFF06A5F, 0xFFF06A5F);
        canvas.circle(x + 130, y + 25, 6, 0xFF5BD69A);
        canvas.line(x + 150, y + 5, x + 210, y + 45, 2, 0xFFE6EDF3, true);
        canvas.line(x + 150, y + 45, x + 210, y + 5, 1, 0xFF6C7B89, false);
        canvas.shape(x + 230, y, 90, 50).radius(8).fill(0xFF141D26)
                .innerShadow(new Theme.Shadow(0, 2, 8, 0, 0xCC000000)).draw();

        y += 64;
        canvas.pushClip(x, y, 200, 60, 10);
        canvas.fill(x - 20, y - 20, 240, 100, 0xFF2A3947);
        for (int i = 0; i < 6; i++) {
            canvas.shape(x + i * 36 - 8, y + 10, 30, 40).radius(4).fill(0xFF5CCFE6 - i * 0x00101000).draw();
            canvas.text("剪" + i, x + i * 36 - 4, y + 26, 0xFF04121A, 1.0F, false);
        }
        canvas.popClip();

        TextLayout layout = TextLayout.build(Component.literal(
                        "梦屿终端：重生系统会记录你的身体模板。这是一段较长的正文，用于验证自动换行、行距与省略号是否正常工作。"),
                TextStyle.BODY, 200, 3, true);
        canvas.shape(x + 220, y, 216, layout.height() + 16).radius(6).fill(0xFF141D26).draw();
        canvas.text(layout, x + 228, y + 8, 200, 0.0F, theme.color(ColorRole.TEXT), false);

        y += 80;
        canvas.text("DISPLAY 标题", x, y, theme.color(ColorRole.TEXT), 2.0F, false);
        canvas.text("Headline 1.5", x + 150, y + 4, theme.color(ColorRole.ACCENT), 1.5F, false);
        canvas.text("Caption 0.75 说明文字", x + 260, y + 8, theme.color(ColorRole.TEXT_MUTED), 0.75F, false);
        y += 26;
        canvas.shape(x, y, 120, 28).radius(6).fill(theme.color(ColorRole.ACCENT)).draw();
        canvas.text("主要按钮", x + 36, y + 10, theme.color(ColorRole.TEXT_ON_ACCENT), 1.0F, false);
        canvas.shape(x + 130, y, 120, 28).radius(6).fill(theme.color(ColorRole.ACCENT_SOFT))
                .border(1, 0x665CCFE6).draw();
        canvas.text("次要按钮", x + 166, y + 10, theme.color(ColorRole.ACCENT), 1.0F, false);
        canvas.item(new ItemStack(Items.DIAMOND_SWORD), x + 270, y + 2, 24, false);
        canvas.item(new ItemStack(Items.GOLDEN_APPLE, 12), x + 300, y + 6, 16, true);
        if (minecraft != null && minecraft.player != null) {
            canvas.playerFace(minecraft.player.getSkin().texture(), x + 330, y, 28, 6, 0xFFFFFFFF);
        }
        // 文字之上的形状必须自动开新层
        canvas.shape(x + 36, y + 6, 20, 16).radius(3).fill(0xCCF06A5F).draw();

        canvas.end();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
