package com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.ui.render.GuiQuadBatchRenderer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * 体征 HUD 的图标队列。
 *
 * <p>所有图标都是 GUI 图集中的精灵：帧内先排队，最后在一次显式边界后合并提交，
 * 避免每个图标各自构建一次网格。</p>
 */
final class HudIconBatch {
    enum Icon {
        HEALTH(ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "dreamingfish_hud/health_v2")),
        FOOD(ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "dreamingfish_hud/food_v2")),
        ARMOR(ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "dreamingfish_hud/armor_v2")),
        INFECTION(ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "dreamingfish_hud/infection_v2")),
        STAMINA(ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "dreamingfish_hud/stamina_v2")),
        COURAGE(ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "dreamingfish_hud/courage_v2")),
        AIR(ResourceLocation.withDefaultNamespace("hud/air"));

        private final ResourceLocation sprite;

        Icon(ResourceLocation sprite) {
            this.sprite = sprite;
        }
    }

    private static final int MAX_QUEUED = 16;
    private static final Icon[] ICONS = Icon.values();
    private static final TextureAtlasSprite[] SPRITES = new TextureAtlasSprite[ICONS.length];
    private static final Icon[] QUEUED_ICON = new Icon[MAX_QUEUED];
    private static final float[] QUEUED_X = new float[MAX_QUEUED];
    private static final float[] QUEUED_Y = new float[MAX_QUEUED];
    private static final int[] QUEUED_SIZE = new int[MAX_QUEUED];
    private static final float[] QUEUED_ALPHA = new float[MAX_QUEUED];
    private static ResourceLocation atlas;
    private static int queued;

    private HudIconBatch() {
    }

    static void queue(Icon icon, float x, float y, int size, float alpha) {
        if (alpha <= 0.02F || queued >= MAX_QUEUED) {
            return;
        }
        QUEUED_ICON[queued] = icon;
        QUEUED_X[queued] = x;
        QUEUED_Y[queued] = y;
        QUEUED_SIZE[queued] = size;
        QUEUED_ALPHA[queued] = Math.min(1.0F, alpha);
        queued++;
    }

    /** 先提交之前排队的面板与文字，再一次性绘制全部图标，使图标位于最上层。 */
    static void flush(GuiGraphics graphics) {
        if (queued == 0) {
            return;
        }
        graphics.flush();
        ensureSprites();
        Matrix4f pose = graphics.pose().last().pose();
        BufferBuilder buffer = GuiQuadBatchRenderer.begin();
        try {
            for (int index = 0; index < queued; index++) {
                GuiQuadBatchRenderer.addSprite(buffer, pose, SPRITES[QUEUED_ICON[index].ordinal()],
                        QUEUED_X[index], QUEUED_Y[index], QUEUED_SIZE[index], QUEUED_SIZE[index],
                        QUEUED_ALPHA[index]);
            }
            GuiQuadBatchRenderer.draw(buffer, atlas);
        } finally {
            queued = 0;
        }
    }

    static void invalidate() {
        java.util.Arrays.fill(SPRITES, null);
        atlas = null;
    }

    private static void ensureSprites() {
        if (SPRITES[0] != null) {
            return;
        }
        for (Icon icon : ICONS) {
            SPRITES[icon.ordinal()] = Minecraft.getInstance().getGuiSprites().getSprite(icon.sprite);
        }
        atlas = SPRITES[0].atlasLocation();
    }
}
