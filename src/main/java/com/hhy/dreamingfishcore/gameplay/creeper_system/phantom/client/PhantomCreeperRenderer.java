package com.hhy.dreamingfishcore.gameplay.creeper_system.phantom.client;

import com.hhy.dreamingfishcore.gameplay.creeper_system.phantom.PhantomCreeperEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.resources.ResourceLocation;

/**
 * 幻翼苦力怕渲染器：原版苦力怕模型 + 一张幻翼翅膀层 + 趴平姿态。
 *
 * <p><b>趴平是怎么做的</b>：不动模型本身，而是在 {@code setupRotations} 里把整个姿态栈绕 X 轴
 * 俯转 {@link #FLY_TILT_DEGREES} 度。旋转前先把轴心抬到躯体中心
 * （{@link #PRONE_PIVOT_Y}），否则会以脚底为轴，整只苦力怕会甩到前面去。</p>
 *
 * <p>这里只影响渲染，命中箱仍然是直立的 0.6 × 1.7（见 {@code PhantomCreeperEntities}）——
 * 判定与视觉解耦，玩家打它、它炸玩家都按原版苦力怕的体积算。</p>
 */
public final class PhantomCreeperRenderer
        extends MobRenderer<PhantomCreeperEntity, CreeperModel<PhantomCreeperEntity>> {
    /** 贴图沿用原版苦力怕——它本来就是个苦力怕，辨识度靠翅膀与姿势，不需要另画一张皮。 */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "textures/entity/phantom_creeper.png");

    /** 趴平旋转的轴心高度（格）。 */
    private static final float PRONE_PIVOT_Y = 0.85F;
    /**
     * 飞行时的前倾角。
     *
     * <p>早先用的是 90°「趴平」，但苦力怕模型的头在上方、脸朝前，绕 X 轴转 90° 会把脸转到**朝下**，
     * 于是玩家完全看不出它在看谁（实测反馈"不会面向玩家"）。改成 22 度前倾：像在飞，脸还朝着前方/目标。</p>
     */
    private static final float FLY_TILT_DEGREES = 22.0F;

    public PhantomCreeperRenderer(EntityRendererProvider.Context context) {
        super(context, new CreeperModel<>(context.bakeLayer(ModelLayers.CREEPER)), 0.5F);
        this.addLayer(new PhantomWingLayer(this, context.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(PhantomCreeperEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void setupRotations(
            PhantomCreeperEntity entity,
            PoseStack poseStack,
            float bob,
            float yBodyRot,
            float partialTick,
            float scale) {
        super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
        if (!entity.isFlyingPose()) {
            return;
        }
        poseStack.translate(0.0D, PRONE_PIVOT_Y, 0.0D);
        poseStack.mulPose(Axis.XP.rotationDegrees(FLY_TILT_DEGREES));
        poseStack.translate(0.0D, -PRONE_PIVOT_Y, 0.0D);
    }
}
