package com.hhy.dreamingfishcore.gameplay.creeper_system.ender.client;

import com.hhy.dreamingfishcore.gameplay.creeper_system.ender.EnderCreeperEntity;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.resources.ResourceLocation;

/**
 * 末影人苦力怕渲染器：原版苦力怕模型与贴图。
 *
 * <p>刻意不加自定义皮肤。参考图里它本来就是一张普通苦力怕脸，靠传送粒子识别——这也是它吓人的地方：
 * 不闪的时候你分不出它是哪一只。真要给它单独一张皮，把 {@link #TEXTURE} 换掉即可。</p>
 */
public final class EnderCreeperRenderer
        extends MobRenderer<EnderCreeperEntity, CreeperModel<EnderCreeperEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "textures/entity/ender_creeper.png");

    public EnderCreeperRenderer(EntityRendererProvider.Context context) {
        super(context, new CreeperModel<>(context.bakeLayer(ModelLayers.CREEPER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(EnderCreeperEntity entity) {
        return TEXTURE;
    }
}
