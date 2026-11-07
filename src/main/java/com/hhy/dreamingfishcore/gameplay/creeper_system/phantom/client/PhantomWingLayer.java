package com.hhy.dreamingfishcore.gameplay.creeper_system.phantom.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.creeper_system.phantom.PhantomCreeperEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 给幻翼苦力怕挂一对翼膜（渲染层）。
 *
 * <p>几何来自 {@link PhantomWingModel}（专用翅膀）。早先这里借的是 {@code ElytraModel}，
 * 但鞘翅的 UV 是按 64×32 的鞘翅贴图排的，套 64×64 的幻翼贴图只会错位；
 * 而且鞘翅与苦力怕模型的部件名不一致，{@code copyPropertiesTo} 拷不到东西，翅膀不会动。</p>
 *
 * <p>挂点：苦力怕模型原点在脚底，所以往上 {@link #WING_Y_OFFSET} 格到躯干、
 * 再往后 {@link #WING_Z_OFFSET} 格，避免翅膀插进身体。</p>
 */
public final class PhantomWingLayer
        extends RenderLayer<PhantomCreeperEntity, CreeperModel<PhantomCreeperEntity>> {

    /** 翅膀相对模型原点的上移量（格）。 */
    private static final float WING_Y_OFFSET = 0.75F;
    /** 翅膀相对模型原点的后移量（格）。 */
    private static final float WING_Z_OFFSET = 0.15F;

    private final PhantomWingModel wings;

    public PhantomWingLayer(
            RenderLayerParent<PhantomCreeperEntity, CreeperModel<PhantomCreeperEntity>> parent,
            EntityModelSet modelSet) {
        super(parent);
        this.wings = new PhantomWingModel(modelSet.bakeLayer(PhantomWingModel.LAYER_LOCATION));
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            PhantomCreeperEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        poseStack.pushPose();
        poseStack.translate(0.0F, WING_Y_OFFSET, WING_Z_OFFSET);
        this.wings.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        var consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(PhantomWingModel.TEXTURE));
        this.wings.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    /** 模型层注册（MOD 事件总线）。 */
    @EventBusSubscriber(modid = DreamingFishCore.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class LayerRegistration {
        private LayerRegistration() {
        }

        @SubscribeEvent
        public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(
                    PhantomWingModel.LAYER_LOCATION, PhantomWingModel::createBodyLayer);
        }
    }
}
