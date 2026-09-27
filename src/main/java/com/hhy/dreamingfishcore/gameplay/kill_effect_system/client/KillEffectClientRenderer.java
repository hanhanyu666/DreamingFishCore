package com.hhy.dreamingfishcore.gameplay.kill_effect_system.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Client-side presentation for the short-lived kill effect. */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class KillEffectClientRenderer {
    private static final double SURFACE_LIFT = 0.028D;

    /* World composition has finished; write directly to the main target before the hand/HUD. */
    private static final RenderType KILL_EFFECT_RENDER_TYPE = createKillEffectRenderType(
            "dreamingfish_kill_effect", RenderStateShard.TRANSLUCENT_TRANSPARENCY);
    /* SRC_ALPHA + ONE preserves the wide plasma gradients and animated fade. */
    private static final RenderType KILL_EFFECT_GLOW_RENDER_TYPE = createKillEffectRenderType(
            "dreamingfish_kill_effect_glow", RenderStateShard.LIGHTNING_TRANSPARENCY);

    private static RenderType createKillEffectRenderType(
            String name,
            RenderStateShard.TransparencyStateShard transparency) {
        return RenderType.create(
                name,
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS,
                1536,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                        .setTextureState(RenderStateShard.NO_TEXTURE)
                        .setTransparencyState(transparency)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setOutputState(RenderStateShard.MAIN_TARGET)
                        .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                        .setOverlayState(RenderStateShard.NO_OVERLAY)
                        .setLayeringState(RenderStateShard.NO_LAYERING)
                        .createCompositeState(false));
    }

    /** Private immediate buffer: shader-loader delayed world buffers must not retain these passes. */
    private static final class DirectBuffers {
        private static final ByteBufferBuilder STORAGE = new ByteBufferBuilder(1_048_576);
        private static final MultiBufferSource.BufferSource SOURCE = MultiBufferSource.immediate(STORAGE);
    }

    private KillEffectClientRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            BlackHoleLensRenderer.release();
            return;
        }

        List<KillEffectClientState.Snapshot> snapshots = new ArrayList<>(KillEffectClientState.snapshots());
        if (snapshots.isEmpty()) {
            return;
        }

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        Vec3 cameraLeft = new Vec3(camera.getLeftVector());
        Vec3 cameraUp = new Vec3(camera.getUpVector());
        // Whole effects are composed back to front; each core masks its own rear plasma.
        snapshots.sort(Comparator.comparingDouble((KillEffectClientState.Snapshot snapshot) ->
                snapshot.position().distanceToSqr(cameraPosition)).reversed());
        Frustum frustum = event.getFrustum();
        PoseStack poseStack = event.getPoseStack();
        // Refract the composed world before painting the horizon and its plasma.
        BlackHoleLensRenderer.render(event, snapshots);
        MultiBufferSource.BufferSource bufferSource = DirectBuffers.SOURCE;
        RenderType baseRenderType = KILL_EFFECT_RENDER_TYPE;
        RenderType glowRenderType = KILL_EFFECT_GLOW_RENDER_TYPE;
        // AFTER_LEVEL supplies an empty pose stack after vanilla popped its world rotation.
        // Put the event's rotation in the pose, keeping the global matrix at identity once.
        RenderSystem.getModelViewStack().pushMatrix().identity();
        RenderSystem.applyModelViewMatrix();
        poseStack.pushPose();
        poseStack.mulPose(event.getModelViewMatrix());
        try {
            int renderedEffects = 0;
            for (KillEffectClientState.Snapshot snapshot : snapshots) {
                if (renderedEffects >= KillEffectClientState.MAX_EFFECTS) {
                    break;
                }
                if (!snapshot.dimension().equals(mc.level.dimension())) {
                    continue;
                }

                Vec3 trackedPosition = KillEffectClientState.trackedPosition(snapshot, partial);
                if (!withinReceiveRange(mc.player, trackedPosition)) {
                    continue;
                }

                float elapsed = KillEffectClientState.elapsedTicks(snapshot, partial);
                if (!Float.isFinite(elapsed) || elapsed < 0.0F
                        || elapsed >= snapshot.durationTicks()) {
                    continue;
                }

                if (frustum != null && !frustum.isVisible(effectBounds(snapshot, trackedPosition))) {
                    continue;
                }

                poseStack.pushPose();
                try {
                    renderEffect(poseStack, bufferSource, baseRenderType, glowRenderType,
                            snapshot, elapsed, trackedPosition,
                            cameraPosition, cameraLeft, cameraUp, snapshots.size() > 6);
                } finally {
                    poseStack.popPose();
                }
                renderedEffects++;
            }
        } finally {
            try {
                bufferSource.endBatch(baseRenderType);
                bufferSource.endBatch(glowRenderType);
            } finally {
                poseStack.popPose();
                RenderSystem.getModelViewStack().popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
        }
    }

    private static void renderEffect(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                     RenderType baseRenderType, RenderType glowRenderType,
                                     KillEffectClientState.Snapshot snapshot, float elapsed,
                                     Vec3 trackedPosition, Vec3 cameraPosition,
                                     Vec3 cameraLeft, Vec3 cameraUp, boolean crowded) {
        float progress = elapsed / Math.max(1, snapshot.durationTicks());
        float bodyTop = (float) (trackedPosition.y - snapshot.y()) + snapshot.height()
                - KillEffectClientState.fallDistance(snapshot.height()) * KillEffectClientState.fallAmount(progress);
        boolean reducedDetail = crowded || trackedPosition.distanceToSqr(cameraPosition) > 24 * 24;
        BlackHoleGeometry.Frame frame = BlackHoleGeometry.sample(snapshot.width(), snapshot.height(),
                elapsed, snapshot.durationTicks(), bodyTop, snapshot.seed(), reducedDetail);
        Vec3 center = new Vec3(trackedPosition.x, snapshot.y() + SURFACE_LIFT + frame.center().y(),
                trackedPosition.z);
        Vec3 towardEye = cameraPosition.subtract(center);
        towardEye = towardEye.lengthSqr() < 0.000001D
                ? cameraLeft.cross(cameraUp).normalize() : towardEye.normalize();
        Vec3 right = cameraUp.cross(towardEye);
        right = right.lengthSqr() < 0.000001D ? cameraLeft : right.normalize();
        Vec3 up = towardEye.cross(right).normalize();
        BlackHoleGeometry.View view = new BlackHoleGeometry.View(point(right), point(up), point(towardEye));

        // Preserve the fixed floor anchor while following the victim's horizontal knockback.
        poseStack.translate(trackedPosition.x - cameraPosition.x,
                snapshot.y() + SURFACE_LIFT - cameraPosition.y, trackedPosition.z - cameraPosition.z);
        PoseStack.Pose pose = poseStack.last();
        for (BlackHoleGeometry.Pass pass : BlackHoleGeometry.Pass.values()) {
            boolean luminous = pass == BlackHoleGeometry.Pass.REAR_LIGHT
                    || pass == BlackHoleGeometry.Pass.FRONT_LIGHT;
            RenderType type = luminous ? glowRenderType : baseRenderType;
            VertexConsumer vertices = bufferSource.getBuffer(type);
            BlackHoleGeometry.render(pass, frame, view, (x, y, z, red, green, blue, alpha) ->
                    vertices.addVertex(pose, x, y, z).setColor(color(red), color(green), color(blue), color(alpha)));
            // Explicit boundaries are essential: the black hemisphere must cover only rear light.
            bufferSource.endBatch(type);
        }
    }

    private static BlackHoleGeometry.Point point(Vec3 vector) {
        return new BlackHoleGeometry.Point((float) vector.x, (float) vector.y, (float) vector.z);
    }

    private static int color(float value) {
        return Math.round(Math.max(0, Math.min(1, value)) * 255);
    }

    private static boolean withinReceiveRange(LivingEntity player, Vec3 trackedPosition) {
        return player.position().distanceToSqr(trackedPosition) <= KillEffectClientState.RECEIVE_RANGE_SQUARED;
    }

    private static AABB effectBounds(KillEffectClientState.Snapshot snapshot, Vec3 trackedPosition) {
        double reach = BlackHoleGeometry.reachFor(snapshot.width(), snapshot.height());
        double minY = Math.min(snapshot.y(), trackedPosition.y) - reach;
        double maxY = Math.max(snapshot.y(), trackedPosition.y) + snapshot.height() + reach;
        return new AABB(trackedPosition.x - reach, minY, trackedPosition.z - reach,
                trackedPosition.x + reach, maxY, trackedPosition.z + reach);
    }
}
