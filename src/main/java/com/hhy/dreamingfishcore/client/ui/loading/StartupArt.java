package com.hhy.dreamingfishcore.client.ui.loading;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 首次启动画面的灯塔插画与光效贴图（由 {@code tools/generate_startup_lighthouse.py} 生成）。
 *
 * <p>启动阶段界面着色器还没加载，这里直接用原版贴图绘制，从游戏窗口出现起就画得出来；
 * 贴图都带 .mcmeta 打开线性过滤，任意缩放都不会出锯齿。光束与光晕用叠加混合，像真正的光。</p>
 */
final class StartupArt {
    static final Texture LIGHTHOUSE = new Texture("lighthouse", 128, 256);
    static final Texture GLOW = new Texture("lighthouse_glow", 128, 128);
    static final Texture BEAM = new Texture("lighthouse_beam", 512, 128);
    static final Texture FLARE = new Texture("lighthouse_flare", 256, 64);

    /** 灯室中心在灯塔贴图中的相对位置。 */
    static final float LAMP_U = 0.5F;
    static final float LAMP_V = 59.0F / 256.0F;
    /** 海平线穿过灯塔贴图的相对高度（礁石中部）。 */
    static final float HORIZON_V = 226.0F / 256.0F;

    private StartupArt() {
    }

    record Texture(ResourceLocation location, int width, int height) {
        Texture(String name, int width, int height) {
            this(ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "textures/gui/loading/" + name + ".png"),
                    width, height);
        }
    }

    /** 普通混合绘制。 */
    static void draw(GuiGraphics graphics, Texture texture, float x, float y, float width, float height, float alpha) {
        blit(graphics, texture, x, y, width, height, false, alpha, false);
    }

    /** 叠加混合绘制（光束、光晕、星芒）；{@code flipX} 时左右翻转。 */
    static void light(GuiGraphics graphics, Texture texture, float x, float y, float width, float height,
                      boolean flipX, float alpha) {
        blit(graphics, texture, x, y, width, height, flipX, alpha, true);
    }

    private static void blit(GuiGraphics graphics, Texture texture, float x, float y, float width, float height,
                             boolean flipX, float alpha, boolean additive) {
        if (alpha <= 0.004F || width <= 0.0F || height <= 0.0F) {
            return;
        }
        // setColor 会先提交 GuiGraphics 里攒着的原版矩形，提交后混合会被关掉，所以混合要在它之后开
        graphics.setColor(1.0F, 1.0F, 1.0F, Math.min(1.0F, alpha));
        RenderSystem.enableBlend();
        if (additive) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(width / texture.width(), height / texture.height(), 1.0F);
        int w = texture.width();
        int h = texture.height();
        graphics.blit(texture.location(), 0, 0, w, h, flipX ? w : 0, 0, flipX ? -w : w, h, w, h);
        pose.popPose();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
    }
}
