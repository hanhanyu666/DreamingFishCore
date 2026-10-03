package com.hhy.dreamingfishcore.gameplay.water_gun_system.client;

import com.hhy.dreamingfishcore.gameplay.water_gun_system.WaterJetEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 水柱的渲染器：一张朝向镜头的半透明方片。
 *
 * <p>用公告板（billboard）而不是骨刺那种交叉四边形：水柱本身没有明确的朝向，玩家看到的是
 * 「一坨水在飞」，而公告板正好永远正对镜头、任何角度都不会变细消失。真正的观感主要靠
 * {@link WaterJetEntity#tick()} 里洒的水花粒子，这张贴图只是给水柱一个实体。</p>
 */
public final class WaterJetRenderer extends EntityRenderer<WaterJetEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "dreamingfishcore", "textures/entity/projectile/water_jet.png");
    /** 方片的半边长（格）。0.22 大约是一个拳头大的水团。 */
    private static final float HALF_SIZE = 0.22F;

    public WaterJetRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(WaterJetEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(
            WaterJetEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        poseStack.pushPose();
        // 公告板：先对齐相机朝向，再翻 180° 让贴图正面朝着镜头（原版掉落物就是这个套路）。
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        vertex(consumer, pose, packedLight, -HALF_SIZE, -HALF_SIZE, 0.0F, 0.0F, 1.0F);
        vertex(consumer, pose, packedLight, HALF_SIZE, -HALF_SIZE, 0.0F, 1.0F, 1.0F);
        vertex(consumer, pose, packedLight, HALF_SIZE, HALF_SIZE, 0.0F, 1.0F, 0.0F);
        vertex(consumer, pose, packedLight, -HALF_SIZE, HALF_SIZE, 0.0F, 0.0F, 0.0F);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static void vertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            int packedLight,
            float x,
            float y,
            float z,
            float u,
            float v) {
        consumer.addVertex(pose, x, y, z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
