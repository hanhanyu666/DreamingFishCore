package com.hhy.dreamingfishcore.gameplay.zombie_system.charred.client;

import com.hhy.dreamingfishcore.gameplay.zombie_system.charred.CharredZombieEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 焦尸的余烬眼睛：全亮叠加层，只画贴图里不透明的那两个像素。
 *
 * <p>与 {@code SiegeZombieEyesLayer} 同一套做法：{@code RenderType.eyes} 加满亮度，其余像素靠
 * 透明通道隐藏。躯干继续吃正常光照，所以白天看起来只是一具炭黑的尸体，只有眼睛在发光——
 * 这正好也是「它还活着」的唯一视觉线索。</p>
 */
public final class CharredZombieEyesLayer
        extends RenderLayer<CharredZombieEntity, ZombieModel<CharredZombieEntity>> {
    private static final RenderType EMBER_EYES = RenderType.eyes(ResourceLocation.fromNamespaceAndPath(
            "dreamingfishcore", "textures/entity/charred_zombie/charred_zombie_eyes.png"));

    public CharredZombieEyesLayer(RenderLayerParent<CharredZombieEntity, ZombieModel<CharredZombieEntity>> renderer) {
        super(renderer);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            CharredZombieEntity zombie,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        VertexConsumer vertexConsumer = buffer.getBuffer(EMBER_EYES);
        // 15728640 = 满亮度（0xF000F0），与原版蜘蛛/末影人的眼睛走同一条全亮路径。
        this.getParentModel().renderToBuffer(
                poseStack,
                vertexConsumer,
                15728640,
                OverlayTexture.NO_OVERLAY);
    }
}
