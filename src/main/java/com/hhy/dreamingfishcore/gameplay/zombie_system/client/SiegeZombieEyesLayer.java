package com.hhy.dreamingfishcore.gameplay.zombie_system.client;

import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Full-bright overlay using the six supplied eye colors; the body remains normally lit. */
public final class SiegeZombieEyesLayer
        extends RenderLayer<SiegeZombieEntity, ZombieModel<SiegeZombieEntity>> {
    private static final RenderType[] EYE_LIGHTS = {
            eyeLight("eyelight_blue.png"),
            eyeLight("eyelight_green.png"),
            eyeLight("eyelight_pink.png"),
            eyeLight("eyelight_red2.png"),
            eyeLight("eyelight_white.png"),
            eyeLight("eyelight_yellow.png")
    };

    public SiegeZombieEyesLayer(RenderLayerParent<SiegeZombieEntity, ZombieModel<SiegeZombieEntity>> renderer) {
        super(renderer);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            SiegeZombieEntity zombie,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        RenderType renderType = EYE_LIGHTS[Math.floorMod(
                zombie.getEyeLightVariantIndex(),
                EYE_LIGHTS.length)];
        VertexConsumer vertexConsumer = buffer.getBuffer(renderType);
        // RenderType.eyes plus maximum packed light is the same full-bright
        // path used by vanilla spider/enderman eyes. Transparent pixels keep
        // every body UV invisible, leaving only the four supplied eye pixels.
        this.getParentModel().renderToBuffer(
                poseStack,
                vertexConsumer,
                15728640,
                OverlayTexture.NO_OVERLAY);
    }

    private static RenderType eyeLight(String fileName) {
        return RenderType.eyes(ResourceLocation.fromNamespaceAndPath(
                "dreamingfishcore",
                "textures/entity/siege_zombie/" + fileName));
    }
}
