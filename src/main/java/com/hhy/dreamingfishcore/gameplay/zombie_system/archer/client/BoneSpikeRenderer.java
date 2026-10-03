package com.hhy.dreamingfishcore.gameplay.zombie_system.archer.client;

import com.hhy.dreamingfishcore.gameplay.zombie_system.archer.BoneSpikeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 骨刺的渲染器。
 *
 * <p>骨刺不是 {@code AbstractArrow}，所以不能直接复用 {@code ArrowRenderer}（那个类是绑死在
 * {@code AbstractArrow#shakeTime} 上的）。这里用两片沿飞行方向交叉的四边形拼出一根细刺——
 * 是最省事、也最不容易出渲染毛病的做法：任何观察角度都至少有一片正对着镜头，不会出现
 * 「侧看变一条线」的消失问题。</p>
 *
 * <p>局部坐标沿用原版箭的约定：模型沿 <b>+X</b> 前伸，先绕 Y 轴对齐偏航、再绕 Z 轴对齐俯仰，
 * 就能与实体的飞行方向一致。</p>
 */
public final class BoneSpikeRenderer extends EntityRenderer<BoneSpikeEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "dreamingfishcore",
            "textures/entity/projectile/bone_spike.png");
    /** 整体缩放：模型单位约 15 长，乘完大约 0.9 格。 */
    private static final float MODEL_SCALE = 0.06F;
    /** 刺的尾部与尖端的局部 X 坐标（+X 为前方，所以尖端在前）。 */
    private static final float TAIL_X = -7.0F;
    private static final float TIP_X = 8.0F;
    /** 刺的半厚。 */
    private static final float HALF_THICKNESS = 0.9F;

    public BoneSpikeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(BoneSpikeEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(
            BoneSpikeEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(
                Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        // XY 面（法线朝 Z）
        addQuad(
                pose, consumer, packedLight, 0.0F, 0.0F, 1.0F,
                TAIL_X, -HALF_THICKNESS, 0.0F,
                TIP_X, -HALF_THICKNESS, 0.0F,
                TIP_X, HALF_THICKNESS, 0.0F,
                TAIL_X, HALF_THICKNESS, 0.0F);
        // XZ 面（法线朝 Y）
        addQuad(
                pose, consumer, packedLight, 0.0F, 1.0F, 0.0F,
                TAIL_X, 0.0F, -HALF_THICKNESS,
                TIP_X, 0.0F, -HALF_THICKNESS,
                TIP_X, 0.0F, HALF_THICKNESS,
                TAIL_X, 0.0F, HALF_THICKNESS);

        poseStack.popPose();
    }

    private static void addQuad(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int packedLight,
            float normalX,
            float normalY,
            float normalZ,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3) {
        vertex(pose, consumer, packedLight, normalX, normalY, normalZ, x0, y0, z0, 0.0F, 1.0F);
        vertex(pose, consumer, packedLight, normalX, normalY, normalZ, x1, y1, z1, 1.0F, 1.0F);
        vertex(pose, consumer, packedLight, normalX, normalY, normalZ, x2, y2, z2, 1.0F, 0.0F);
        vertex(pose, consumer, packedLight, normalX, normalY, normalZ, x3, y3, z3, 0.0F, 0.0F);
    }

    private static void vertex(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int packedLight,
            float normalX,
            float normalY,
            float normalZ,
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
                .setNormal(pose, normalX, normalY, normalZ);
    }
}
