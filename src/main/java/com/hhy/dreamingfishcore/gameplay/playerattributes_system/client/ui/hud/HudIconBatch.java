package com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * 体征 HUD 的图标。全部是 GUI 图集里的精灵，直接作为贴图形状画进共享画布，
 * 与面板一起合批提交。
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

    private static final Icon[] ICONS = Icon.values();
    private static final TextureAtlasSprite[] SPRITES = new TextureAtlasSprite[ICONS.length];

    private HudIconBatch() {
    }

    static void draw(UiCanvas canvas, Icon icon, float x, float y, int size, float alpha) {
        if (alpha <= 0.02F) {
            return;
        }
        ensureSprites();
        TextureAtlasSprite sprite = SPRITES[icon.ordinal()];
        int tint = HudPalette.withAlpha(0xFFFFFF, Math.round(255 * Math.min(1.0F, alpha)));
        canvas.image(sprite.atlasLocation(), x, y, size, size, sprite.getU0(), sprite.getV0(), sprite.getU1(),
                sprite.getV1(), 0.0F, tint);
    }

    static void invalidate() {
        java.util.Arrays.fill(SPRITES, null);
    }

    private static void ensureSprites() {
        if (SPRITES[0] != null) {
            return;
        }
        for (Icon icon : ICONS) {
            SPRITES[icon.ordinal()] = Minecraft.getInstance().getGuiSprites().getSprite(icon.sprite);
        }
    }
}
