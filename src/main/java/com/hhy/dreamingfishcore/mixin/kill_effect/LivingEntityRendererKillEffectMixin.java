package com.hhy.dreamingfishcore.mixin.kill_effect;

import com.hhy.dreamingfishcore.gameplay.kill_effect_system.client.KillEffectClientState;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererKillEffectMixin {
    @WrapMethod(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void dreamingfishcore$renderKillEffect(LivingEntity entity,
                                                   float entityYaw,
                                                   float partialTicks,
                                                   PoseStack poseStack,
                                                   MultiBufferSource bufferSource,
                                                   int packedLight,
                                                   Operation<Void> original) {
        KillEffectClientState.Snapshot snapshot = KillEffectClientState.getSnapshot(entity);
        if (snapshot == null) {
            original.call(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
            return;
        }

        float elapsed = KillEffectClientState.elapsedTicks(snapshot, partialTicks);
        if (!Float.isFinite(elapsed) || elapsed < 0.0F || elapsed >= snapshot.durationTicks()) {
            original.call(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
            return;
        }

        // Let the opening lock-on show the real textured model, then remove the vanilla model
        // once the sliced projection has taken over. The entity itself still follows vanilla
        // death/cleanup rules; only this client-side draw is suppressed.
        if (KillEffectClientState.shouldHide(entity, partialTicks)) {
            return;
        }

        // Let the ground hole form first, then accelerate the upright model straight downward as
        // if the supporting block had opened beneath it. A mild late horizontal squeeze keeps
        // wider models inside the small opening without replacing the fall with a shrink effect.
        float progress = KillEffectClientState.progress(snapshot, partialTicks);
        float fall = KillEffectClientState.fallAmount(progress);
        float squeeze = smoothstep(0.28F, 0.52F, progress);
        float horizontalScale = 1.0F - squeeze * 0.14F;
        float dropDistance = KillEffectClientState.fallDistance(snapshot.height());
        poseStack.pushPose();
        try {
            if (fall > 0.001F) {
                poseStack.translate(0.0F, -dropDistance * fall, 0.0F);
            }
            if (squeeze > 0.001F) {
                poseStack.scale(horizontalScale, 1.0F, horizontalScale);
            }
            original.call(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
        } finally {
            poseStack.popPose();
        }
    }

    @Inject(method = "setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void dreamingfishcore$keepKilledEntityUpright(LivingEntity entity,
                                                            PoseStack poseStack,
                                                            float bob,
                                                            float bodyRotation,
                                                            float partialTick,
                                                            float scale,
                                                            CallbackInfo callbackInfo) {
        if (entity.deathTime <= 0 || KillEffectClientState.getSnapshot(entity) == null) {
            return;
        }

        // LivingEntityRenderer normally applies the death Z rotation here. Preserve only the
        // ordinary body yaw so the target remains upright while the black hole consumes it.
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyRotation));
        callbackInfo.cancel();
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float normalized = Math.max(0.0F, Math.min(1.0F,
                (value - edge0) / Math.max(0.0001F, edge1 - edge0)));
        return normalized * normalized * (3.0F - 2.0F * normalized);
    }
}
