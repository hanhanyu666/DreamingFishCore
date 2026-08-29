package com.hhy.dreamingfishcore.gameplay.kill_effect_system.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
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
import java.util.List;

/** Client-side presentation for the short-lived kill effect. */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class KillEffectClientRenderer {
    private static final float MIN_WIDTH = 0.05F;
    private static final float MAX_WIDTH = 16.0F;
    private static final float MIN_HEIGHT = 0.05F;
    private static final float MAX_HEIGHT = 32.0F;
    private static final float TWO_PI = (float) (Math.PI * 2.0D);
    private static final double SURFACE_LIFT = 0.028D;
    private static final Vec3 GROUND_AXIS_X = new Vec3(1.0D, 0.0D, 0.0D);
    private static final Vec3 GROUND_AXIS_Z = new Vec3(0.0D, 0.0D, 1.0D);

    /* Color-only translucent geometry: it tests depth, but never writes depth. */
    private static final RenderType KILL_EFFECT_RENDER_TYPE = RenderType.create(
            "dreamingfish_kill_effect",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTextureState(RenderStateShard.NO_TEXTURE)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .setLayeringState(RenderStateShard.NO_LAYERING)
                    .createCompositeState(false)
    );

    /* Additive pass for luminous disk strands and stars. The dark core stays in the normal pass. */
    private static final RenderType KILL_EFFECT_GLOW_RENDER_TYPE = RenderType.create(
            "dreamingfish_kill_effect_glow",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTextureState(RenderStateShard.NO_TEXTURE)
                    .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .setLayeringState(RenderStateShard.NO_LAYERING)
                    .createCompositeState(false)
    );

    private KillEffectClientRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        List<KillEffectClientState.Snapshot> snapshots = KillEffectClientState.snapshots();
        if (snapshots.isEmpty()) {
            return;
        }

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        Vec3 cameraLeft = new Vec3(camera.getLeftVector());
        Vec3 cameraUp = new Vec3(camera.getUpVector());
        Frustum frustum = event.getFrustum();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
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
                    renderEffect(poseStack, bufferSource, snapshot, elapsed, trackedPosition,
                            cameraPosition, cameraLeft, cameraUp);
                } finally {
                    poseStack.popPose();
                }
                renderedEffects++;
            }
        } finally {
            bufferSource.endBatch(KILL_EFFECT_RENDER_TYPE);
            bufferSource.endBatch(KILL_EFFECT_GLOW_RENDER_TYPE);
        }
    }

    private static void renderEffect(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                     KillEffectClientState.Snapshot snapshot, float elapsed,
                                     Vec3 trackedPosition, Vec3 cameraPosition,
                                     Vec3 cameraLeft, Vec3 cameraUp) {
        float width = clamp(snapshot.width(), MIN_WIDTH, MAX_WIDTH);
        float height = clamp(snapshot.height(), MIN_HEIGHT, MAX_HEIGHT);
        float halfWidth = Math.max(0.025F, width * 0.5F);
        float fade = fadeAtEnd(elapsed, snapshot.durationTicks());
        float intro = clamp(elapsed / 0.35F, 0.0F, 1.0F);
        float strength = fade * (0.72F + intro * 0.28F);
        if (strength <= 0.001F) {
            return;
        }

        double centerX = trackedPosition.x - cameraPosition.x;
        // target.getY() is the creature's foot position, which is normally the top face of the
        // supporting block. Lock this axis to the kill snapshot: knockback may move the creature
        // vertically, but the ground hole must follow only its X/Z movement.
        double centerY = snapshot.y() + SURFACE_LIFT - cameraPosition.y;
        double centerZ = trackedPosition.z - cameraPosition.z;
        int discreteTick = Math.max(0, (int) Math.floor(elapsed));
        Vec3 holeCenter = new Vec3(centerX, centerY, centerZ);

        float coreRadius = renderBlackHole(poseStack, bufferSource, snapshot.seed(),
                holeCenter, GROUND_AXIS_X, GROUND_AXIS_Z,
                halfWidth, height, elapsed, snapshot.durationTicks(), discreteTick, strength);
        VertexConsumer glowVertices = bufferSource.getBuffer(KILL_EFFECT_GLOW_RENDER_TYPE);
        renderBodyInfallStreams(poseStack, glowVertices, snapshot.seed(), holeCenter,
                cameraLeft, cameraUp, halfWidth, height, coreRadius,
                (float) (trackedPosition.y - snapshot.y()),
                elapsed, snapshot.durationTicks(), strength);
    }

    /** A restrained stellar surround gives the black hole scale without turning into a particle cloud. */
    private static void renderCaptureField(PoseStack poseStack, VertexConsumer vertices, long seed,
                                           Vec3 center, Vec3 cameraLeft, Vec3 cameraUp,
                                           Vec3 cameraLook, float halfWidth, float height,
                                           float elapsed, int duration, int discreteTick,
                                           float strength) {
        float progress = clamp(elapsed / Math.max(1.0F, duration), 0.0F, 1.0F);
        float appear = smoothstep(0.05F, 0.18F, progress);
        float release = 1.0F - smoothstep(0.58F, 0.88F, progress);
        float fieldAlpha = strength * appear * release;
        if (fieldAlpha <= 0.001F) {
            return;
        }

        Vec3 fieldCenter = center.add(cameraLook.scale(-Math.max(0.035F, halfWidth * 0.25F)));
        float fieldHalfWidth = Math.max(halfWidth * 1.24F, height * 0.18F);
        float fieldHalfHeight = Math.max(0.12F, height * 0.42F);
        Vec3 holeCenter = center.add(cameraLook.scale(-Math.max(0.08F, halfWidth * 0.72F)));

        // Star points orbit the target, then interpolate toward the hole with short luminous tails.
        float starReveal = smoothstep(0.10F, 0.24F, progress)
                * (1.0F - smoothstep(0.66F, 0.90F, progress));
        float pull = smoothstep(0.24F, 0.76F, progress);
        for (int star = 0; star < 7; star++) {
            float stable = unitNoise(seed, 0, star, 155);
            float angle = stable * TWO_PI + elapsed * (0.18F + stable * 0.42F);
            float radiusX = fieldHalfWidth * (1.05F + stable * 1.05F);
            float radiusY = fieldHalfHeight * (0.62F + stable * 0.68F);
            Vec3 outer = ringPoint(fieldCenter, cameraLeft, cameraUp, radiusX, radiusY, angle);
            float localPull = clamp(pull * (0.52F + stable * 0.44F), 0.0F, 0.92F);
            Vec3 target = ringPoint(holeCenter, cameraLeft, cameraUp,
                    fieldHalfWidth * (0.14F + stable * 0.16F),
                    fieldHalfHeight * (0.10F + stable * 0.16F), angle + 0.45F);
            Vec3 point = outer.scale(1.0D - localPull).add(target.scale(localPull));
            float twinkle = 0.54F + 0.46F * (float) Math.sin(elapsed * (2.0F + stable * 2.8F)
                    + stable * TWO_PI);
            float size = clamp(height * (0.0032F + stable * 0.0055F) * (0.82F + twinkle * 0.40F),
                    0.010F, 0.060F);
            float cool = clamp(0.38F + stable * 0.56F, 0.0F, 1.0F);
            float starRed = 0.62F + cool * 0.34F;
            float starGreen = 0.70F + cool * 0.28F;
            float starBlue = 0.92F + cool * 0.08F;
            float starAlpha = fieldAlpha * starReveal * (0.18F + stable * 0.38F) * twinkle;
            emitStar(poseStack, vertices, point, cameraLeft, cameraUp, size,
                    starRed, starGreen, starBlue, starAlpha);
            if (localPull > 0.20F) {
                emitBillboardSegment(poseStack, vertices, outer, point, cameraLeft, cameraUp,
                        Math.max(0.004F, size * 0.34F), starRed, starGreen, starBlue,
                        starAlpha * 0.24F);
            }
        }
    }

    private static void emitStar(PoseStack poseStack, VertexConsumer vertices, Vec3 center,
                                 Vec3 left, Vec3 up, float size,
                                 float red, float green, float blue, float alpha) {
        if (alpha <= 0.001F || size <= 0.001F) {
            return;
        }
        emitBillboardSegment(poseStack, vertices, center.subtract(left.scale(size)),
                center.add(left.scale(size)), left, up, Math.max(0.003F, size * 0.18F),
                red, green, blue, alpha);
        emitBillboardSegment(poseStack, vertices, center.subtract(up.scale(size * 0.82F)),
                center.add(up.scale(size * 0.82F)), left, up, Math.max(0.003F, size * 0.18F),
                red, green, blue, alpha * 0.88F);
        emitBillboardSegment(poseStack, vertices, center.subtract(left.scale(size * 0.46F))
                        .subtract(up.scale(size * 0.46F)),
                center.add(left.scale(size * 0.46F)).add(up.scale(size * 0.46F)),
                left, up, Math.max(0.002F, size * 0.11F), red, green, blue, alpha * 0.50F);
    }

    /** Renders a horizontal black hole on the supporting block's top face. */
    private static float renderBlackHole(PoseStack poseStack,
                                         MultiBufferSource.BufferSource bufferSource,
                                         long seed,
                                         Vec3 center, Vec3 planeX, Vec3 planeZ,
                                         float halfWidth, float height, float elapsed, int duration,
                                         int discreteTick, float strength) {
        float progress = clamp(elapsed / Math.max(1.0F, duration), 0.0F, 1.0F);
        float grow = smoothstep(0.0F, 0.15F, progress);
        float collapse = smoothstep(0.70F, 0.97F, progress);
        float tailFade = 1.0F - smoothstep(0.96F, 1.0F, progress);
        float pulse = 0.97F + 0.03F * (float) Math.sin(elapsed * 2.35F
                + unitNoise(seed, 0, 0, 140) * TWO_PI);

        // The soft outer ring reaches about 1.477 core radii. A 1.62 core multiplier therefore
        // makes the completed main silhouette approximately 2.4 times the creature's full width.
        // The small floor prevents tiny mobs from producing a nearly invisible hole.
        float fullCoreRadius = clamp(Math.max(0.24F, halfWidth * 1.62F), 0.24F, 4.00F);
        float coreRadius = fullCoreRadius * (0.30F + 0.70F * grow) * pulse;
        float ringOuter = coreRadius * (1.40F - collapse * 0.06F);
        float ringInner = coreRadius * (1.015F + collapse * 0.04F);
        Vec3 holeCenter = center;
        float activeAlpha = strength * tailFade;
        VertexConsumer baseVertices = bufferSource.getBuffer(KILL_EFFECT_RENDER_TYPE);

        // These axes lie in the world XZ plane, so perspective naturally turns the circle into a
        // ground-plane ellipse instead of a camera-facing disc.
        emitDisc(poseStack, baseVertices, holeCenter, planeX, planeZ,
                coreRadius * 1.30F, coreRadius * 1.30F, 64,
                0.001F, 0.001F, 0.006F, activeAlpha * 0.56F);
        emitDisc(poseStack, baseVertices, holeCenter, planeX, planeZ,
                coreRadius * 1.20F, coreRadius * 1.20F, 64,
                0.001F, 0.001F, 0.004F, activeAlpha * 0.94F);
        emitDisc(poseStack, baseVertices, holeCenter, planeX, planeZ,
                coreRadius * 0.90F, coreRadius * 0.90F, 64,
                0.0F, 0.0F, 0.001F, activeAlpha);

        VertexConsumer glowVertices = bufferSource.getBuffer(KILL_EFFECT_GLOW_RENDER_TYPE);
        renderAccretionDisk(poseStack, glowVertices, holeCenter, planeX, planeZ,
                ringOuter, ringInner, elapsed, seed, activeAlpha);
        renderInnerGoldReflection(poseStack, glowVertices, holeCenter, planeX, planeZ,
                coreRadius, elapsed, seed, activeAlpha);

        float flash = smoothstep(0.78F, 0.88F, progress)
                * (1.0F - smoothstep(0.89F, 0.98F, progress));
        if (flash > 0.001F) {
            emitDisc(poseStack, glowVertices, holeCenter, planeX, planeZ,
                    coreRadius * (1.10F + flash * 0.24F), coreRadius * (1.10F + flash * 0.24F), 64,
                    1.0F, 0.80F, 0.34F, strength * flash * 0.12F);
        }
        return coreRadius;
    }

    /** Sparse vertical ribbons make the body feel pulled into the ground hole without a new halo. */
    private static void renderBodyInfallStreams(PoseStack poseStack, VertexConsumer vertices,
                                                long seed, Vec3 holeCenter,
                                                Vec3 cameraLeft, Vec3 cameraUp,
                                                float halfWidth, float height, float coreRadius,
                                                float bodyBaseOffsetY,
                                                float elapsed, int duration, float strength) {
        float progress = clamp(elapsed / Math.max(1.0F, duration), 0.0F, 1.0F);
        float reveal = smoothstep(0.07F, 0.18F, progress);
        float vanish = 1.0F - smoothstep(0.46F, 0.60F, progress);
        float fieldAlpha = strength * reveal * vanish;
        if (fieldAlpha <= 0.001F) {
            return;
        }

        float fall = KillEffectClientState.fallAmount(progress);
        float dropDistance = KillEffectClientState.fallDistance(height);
        float visualDrop = dropDistance * fall;
        // Keep these strands beside the model even though the ground hole itself is much larger.
        float bodyRadius = Math.max(0.14F, halfWidth * 0.95F);
        Vec3 sideAxis = new Vec3(cameraLeft.x, 0.0D, cameraLeft.z);
        if (sideAxis.lengthSqr() < 0.000001D) {
            sideAxis = GROUND_AXIS_X;
        } else {
            sideAxis = sideAxis.normalize();
        }
        Vec3 depthAxis = new Vec3(-sideAxis.z, 0.0D, sideAxis.x);

        // Alternating indices guarantee exactly 32 pull streams on each visible side.
        for (int stream = 0; stream < 64; stream++) {
            float stable = unitNoise(seed, 0, stream, 183);
            float phase = unitNoise(seed, 0, stream, 184);
            int side = (stream & 1) == 0 ? -1 : 1;
            float sway = (float) Math.sin(elapsed * (0.13F + stable * 0.08F)
                    + phase * TWO_PI) * bodyRadius * 0.075F;
            float lateral = side * bodyRadius * (0.90F + stable * 0.52F) + sway;
            float depthJitter = bodyRadius * (phase - 0.5F) * 0.58F;
            float startHeight = bodyBaseOffsetY
                    + height * (0.18F + phase * 0.70F) - visualDrop;
            Vec3 start = holeCenter
                    .add(sideAxis.scale(lateral))
                    .add(depthAxis.scale(depthJitter))
                    .add(0.0D, startHeight, 0.0D);

            Vec3 end = holeCenter
                    .add(sideAxis.scale(side * coreRadius * (0.055F + stable * 0.095F)))
                    .add(depthAxis.scale((phase - 0.5F) * coreRadius * 0.12F))
                    .add(0.0D, 0.012D, 0.0D);

            Vec3 curve = depthAxis.scale(side * bodyRadius * (0.08F + stable * 0.10F));
            Vec3 middle = start.scale(0.43D).add(end.scale(0.57D)).add(curve);
            float warm = (float) Math.pow(stable, 1.35D);
            float red = 0.48F + warm * 0.40F;
            float green = 0.56F + warm * 0.26F;
            float blue = 0.82F - warm * 0.38F;
            float ribbonAlpha = fieldAlpha * (0.10F + stable * 0.15F);
            float thickness = Math.max(0.004F,
                    Math.min(0.018F, height * (0.0030F + stable * 0.0032F)));

            emitTaperedBillboardSegment(poseStack, vertices, start, middle,
                    cameraLeft, cameraUp, thickness,
                    red, green, blue, ribbonAlpha * 0.08F, ribbonAlpha * 0.48F);
            emitTaperedBillboardSegment(poseStack, vertices, middle, end,
                    cameraLeft, cameraUp, thickness * 0.72F,
                    red, green, blue, ribbonAlpha * 0.44F, ribbonAlpha * 0.82F);

            if (stable > 0.56F) {
                float motePhase = fract(elapsed * (0.085F + stable * 0.055F) + phase);
                Vec3 mote = start.scale(1.0D - motePhase).add(end.scale(motePhase));
                float moteSize = Math.max(0.008F,
                        Math.min(0.026F, height * (0.004F + stable * 0.004F)));
                emitStar(poseStack, vertices, mote, cameraLeft, cameraUp, moteSize,
                        red, green, blue, ribbonAlpha * 0.72F);
            }
        }
    }

    /** Small broken glints give the event horizon depth without making a yellow blob. */
    private static void renderInnerGoldReflection(PoseStack poseStack, VertexConsumer vertices,
                                                   Vec3 center, Vec3 left, Vec3 up,
                                                   float coreRadius, float elapsed, long seed,
                                                   float alpha) {
        float pulse = 0.78F + 0.22F * (float) Math.sin(elapsed * 1.35F
                + unitNoise(seed, 0, 0, 174) * TWO_PI);
        float arcStart = 2.08F + unitNoise(seed, 0, 1, 174) * 0.10F;
        float arcEnd = 2.64F - unitNoise(seed, 0, 2, 174) * 0.10F;

        // Broken radial streaks sit inside the upper-right rim. Their low alpha and tapered
        // ends keep the black center dominant while still catching the eye for a frame.
        for (int streak = 0; streak < 7; streak++) {
            float stable = unitNoise(seed, 0, streak, 175);
            float angle = arcStart + (arcEnd - arcStart) * ((streak + 0.35F) / 7.0F)
                    + (stable - 0.5F) * 0.045F + elapsed * 0.018F;
            float outerRadius = coreRadius * (0.925F + stable * 0.055F);
            float innerRadius = coreRadius * (0.735F + stable * 0.085F);
            Vec3 start = ringPoint(center, left, up, outerRadius, outerRadius, angle - 0.025F);
            Vec3 end = ringPoint(center, left, up, innerRadius, innerRadius, angle + 0.055F);
            float falloff = (float) Math.pow(Math.sin(Math.PI * (streak + 0.5F) / 7.0F), 0.9D);
            float streakAlpha = alpha * pulse * falloff * (0.028F + stable * 0.045F);
            float thickness = Math.max(0.003F, coreRadius * (0.008F + stable * 0.006F));
            emitTaperedBillboardSegment(poseStack, vertices, start, end, left, up,
                    thickness, 1.0F, 0.78F, 0.42F,
                    streakAlpha * 0.12F, streakAlpha * 0.62F);

            // A few glints are nearly white, matching the hot edge of the reference image.
            if (stable > 0.42F) {
                Vec3 glintStart = start.scale(0.62D).add(end.scale(0.38D));
                emitTaperedBillboardSegment(poseStack, vertices, glintStart, end, left, up,
                        thickness * 0.48F, 1.0F, 0.92F, 0.72F,
                        streakAlpha * 0.16F, streakAlpha * 0.46F);
            }
        }
    }

    private static void renderAccretionDisk(PoseStack poseStack, VertexConsumer vertices,
                                             Vec3 center, Vec3 left, Vec3 up,
                                             float ringOuter, float ringInner,
                                             float elapsed, long seed, float alpha) {
        final int segments = 96;
        float rotation = elapsed * 0.34F + unitNoise(seed, 0, 0, 160) * TWO_PI;
        float hotAngle = rotation + 0.55F;
        float ringWidth = Math.max(0.012F, ringOuter - ringInner);

        // Three concentric passes make one clean ring with a soft halo and a bright inner edge.
        float[][] layers = {
                {1.20F, 1.09F, 0.050F},
                {1.11F, 1.035F, 0.150F},
                {1.045F, 1.005F, 0.420F}
        };
        for (int layer = 0; layer < layers.length; layer++) {
            float outer = ringInner + ringWidth * layers[layer][0];
            float inner = ringInner + ringWidth * layers[layer][1];
            float layerAlpha = alpha * layers[layer][2];
            for (int segment = 0; segment < segments; segment++) {
                float angle0 = rotation + segment * TWO_PI / segments;
                float angle1 = rotation + (segment + 1.0F) * TWO_PI / segments;
                float mid = (angle0 + angle1) * 0.5F;
                float facing = clamp(0.5F + 0.5F * (float) Math.cos(mid - hotAngle), 0.0F, 1.0F);
                float hot = (float) Math.pow(facing, 3.0D);
                float cool = 1.0F - facing;
                float shimmer = 0.97F + 0.03F * (float) Math.sin(mid * 4.0F + elapsed * 1.3F);
                // Warm side is pale gold; the opposite side retains a restrained blue-white tint.
                float red = 0.52F + hot * 0.44F;
                float green = 0.34F + hot * 0.48F;
                float blue = 0.13F + hot * 0.34F + cool * (layer == 0 ? 0.40F : 0.16F);
                float segmentAlpha = layerAlpha * shimmer * (0.10F + hot * 0.90F);
                emitAnnulusSegment(poseStack, vertices, center, left, up,
                        outer, outer, inner, inner, angle0, angle1,
                        red, green, blue, segmentAlpha);
            }
        }

        renderOrbitingParticles(poseStack, vertices, center, left, up, ringOuter, ringInner,
                elapsed, seed, alpha, hotAngle);
    }

    /** Fine tangent particles complete the reference silhouette without adding another ring. */
    private static void renderOrbitingParticles(PoseStack poseStack, VertexConsumer vertices,
                                                Vec3 center, Vec3 left, Vec3 up,
                                                float ringOuter, float ringInner,
                                                float elapsed, long seed, float alpha,
                                                float hotAngle) {
        float radiusSpan = Math.max(0.02F, ringOuter - ringInner);
        for (int index = 0; index < 60; index++) {
            float stable = unitNoise(seed, 0, index, 166);
            float angle = stable * TWO_PI + elapsed * (0.26F + stable * 0.20F);
            float radius = ringInner + radiusSpan * (0.16F + stable * 1.56F);
            float trailLength = 0.07F + stable * 0.16F;
            float trailAngle = angle - trailLength;
            float trailRadius = radius * (1.015F + stable * 0.045F);
            Vec3 point = ringPoint(center, left, up, radius, radius, angle);
            Vec3 trail = ringPoint(center, left, up, trailRadius, trailRadius, trailAngle);
            float facing = clamp(0.5F + 0.5F * (float) Math.cos(angle - hotAngle), 0.0F, 1.0F);
            float gold = (float) Math.pow(facing, 2.2D);
            float red = 0.48F + gold * 0.48F;
            float green = 0.36F + gold * 0.48F;
            float blue = 0.20F + gold * 0.28F + (1.0F - gold) * 0.38F;
            float particleAlpha = alpha * (0.09F + stable * 0.22F) * (0.34F + gold * 0.66F);
            float size = Math.max(0.005F, radiusSpan * (0.014F + stable * 0.030F));

            // The long pass is soft and tapered; the short pass keeps a crisp moving highlight.
            emitTaperedBillboardSegment(poseStack, vertices, trail, point, left, up,
                    size * 0.70F, red, green, blue,
                    particleAlpha * 0.12F, particleAlpha * 0.48F);
            Vec3 brightStart = trail.scale(0.46D).add(point.scale(0.54D));
            emitTaperedBillboardSegment(poseStack, vertices, brightStart, point, left, up,
                    size * 0.34F, red, green, blue,
                    particleAlpha * 0.25F, particleAlpha * 0.78F);
            if (stable > 0.64F) {
                emitStar(poseStack, vertices, point, left, up, size * 0.65F,
                        red, green, blue, particleAlpha * 0.42F);
            }
        }

        // A second, quieter layer sits beyond the ring. It supplies the reference's sense of
        // motion and scale without becoming a second hard-edged circle.
        for (int index = 0; index < 20; index++) {
            float stable = unitNoise(seed, 0, index, 178);
            float angle = stable * TWO_PI + elapsed * (0.18F + stable * 0.24F);
            float radius = ringOuter + radiusSpan * (0.18F + stable * 1.18F);
            float trailLength = 0.10F + stable * 0.18F;
            float trailAngle = angle - trailLength;
            float trailRadius = radius * (1.012F + stable * 0.055F);
            Vec3 point = ringPoint(center, left, up, radius, radius, angle);
            Vec3 trail = ringPoint(center, left, up, trailRadius, trailRadius, trailAngle);
            float facing = clamp(0.5F + 0.5F * (float) Math.cos(angle - hotAngle), 0.0F, 1.0F);
            float gold = (float) Math.pow(facing, 1.8D);
            float red = 0.46F + gold * 0.48F;
            float green = 0.42F + gold * 0.40F;
            float blue = 0.54F + gold * 0.20F;
            float streamerAlpha = alpha * (0.035F + stable * 0.10F) * (0.45F + gold * 0.55F);
            float size = Math.max(0.004F, radiusSpan * (0.010F + stable * 0.018F));
            emitTaperedBillboardSegment(poseStack, vertices, trail, point, left, up,
                    size * 0.58F, red, green, blue,
                    streamerAlpha * 0.08F, streamerAlpha * 0.38F);
            if (stable > 0.56F) {
                Vec3 shortStart = trail.scale(0.48D).add(point.scale(0.52D));
                emitTaperedBillboardSegment(poseStack, vertices, shortStart, point, left, up,
                        size * 0.28F, red, green, blue,
                        streamerAlpha * 0.18F, streamerAlpha * 0.58F);
            }
        }
    }

    private static void renderGravityJets(PoseStack poseStack, VertexConsumer vertices,
                                          Vec3 center, Vec3 left, Vec3 up,
                                          float diskRadius, float diskHeight, float elapsed,
                                          long seed, float alpha, float grow) {
        float jetAlpha = alpha * smoothstep(0.10F, 0.42F, grow) * 0.075F;
        if (jetAlpha <= 0.001F) {
            return;
        }
        for (int side : new int[]{-1, 1}) {
            float sway = (float) Math.sin(elapsed * 0.75F + side * 1.4F
                    + unitNoise(seed, 0, side + 2, 161) * TWO_PI) * diskRadius * 0.08F;
            Vec3 base = center.add(up.scale(side * diskHeight * 0.62F)).add(left.scale(sway));
            Vec3 tip = center.add(up.scale(side * diskRadius * 0.88F)).add(left.scale(sway * 1.25F));
            emitBillboardSegment(poseStack, vertices, base, tip, left, up,
                    Math.max(0.010F, diskRadius * 0.028F),
                    0.16F, 0.24F, 0.70F, jetAlpha * 0.25F);
            emitBillboardSegment(poseStack, vertices, base, tip, left, up,
                    Math.max(0.005F, diskRadius * 0.010F),
                    0.52F, 0.70F, 0.94F, jetAlpha * 0.48F);
        }
    }

    private static void renderShockwaves(PoseStack poseStack, VertexConsumer vertices,
                                         Vec3 center, Vec3 left, Vec3 up,
                                         float coreRadius, float diskHeight, float progress,
                                         float alpha) {
        float opening = smoothstep(0.02F, 0.14F, progress)
                * (1.0F - smoothstep(0.16F, 0.34F, progress));
        float closing = smoothstep(0.70F, 0.82F, progress)
                * (1.0F - smoothstep(0.84F, 0.98F, progress));
        renderShockwaveRing(poseStack, vertices, center, left, up, coreRadius, diskHeight,
                1.06F + opening * 1.45F, opening * alpha * 0.18F);
        renderShockwaveRing(poseStack, vertices, center, left, up, coreRadius, diskHeight,
                1.08F + closing * 1.70F, closing * alpha * 0.24F);
    }

    private static void renderShockwaveRing(PoseStack poseStack, VertexConsumer vertices,
                                            Vec3 center, Vec3 left, Vec3 up,
                                            float coreRadius, float diskHeight, float scale,
                                            float alpha) {
        if (alpha <= 0.001F) {
            return;
        }
        float radiusX = coreRadius * scale;
        float radiusY = Math.max(0.02F, diskHeight * scale * 0.72F);
        float thickness = Math.max(0.006F, coreRadius * 0.026F);
        final int segments = 80;
        for (int segment = 0; segment < segments; segment++) {
            float angle0 = segment * TWO_PI / segments;
            float angle1 = (segment + 1.0F) * TWO_PI / segments;
            float fade = 0.55F + 0.45F * (float) Math.sin(angle0 * 2.0F + 0.8F);
            emitAnnulusSegment(poseStack, vertices, center, left, up,
                    radiusX + thickness, radiusY + thickness * 0.6F,
                    Math.max(0.001F, radiusX - thickness), Math.max(0.001F, radiusY - thickness * 0.6F),
                    angle0, angle1, 0.46F, 0.68F, 1.0F, alpha * fade);
        }
    }

    private static void emitAnnulusSegment(PoseStack poseStack, VertexConsumer vertices,
                                           Vec3 center, Vec3 left, Vec3 up,
                                           float outerRadiusX, float outerRadiusY,
                                           float innerRadiusX, float innerRadiusY,
                                           float angle0, float angle1,
                                           float red, float green, float blue, float alpha) {
        if (alpha <= 0.001F || outerRadiusX <= innerRadiusX || outerRadiusY <= innerRadiusY) {
            return;
        }
        Vec3 outer0 = ringPoint(center, left, up, outerRadiusX, outerRadiusY, angle0);
        Vec3 outer1 = ringPoint(center, left, up, outerRadiusX, outerRadiusY, angle1);
        Vec3 inner1 = ringPoint(center, left, up, innerRadiusX, innerRadiusY, angle1);
        Vec3 inner0 = ringPoint(center, left, up, innerRadiusX, innerRadiusY, angle0);
        emitQuad(poseStack, vertices, outer0, outer1, inner1, inner0,
                red, green, blue, alpha);
    }

    private static Vec3 ringPoint(Vec3 center, Vec3 left, Vec3 up,
                                  float radiusX, float radiusY, float angle) {
        return center.add(left.scale(Math.cos(angle) * radiusX))
                .add(up.scale(Math.sin(angle) * radiusY));
    }

    private static void emitDisc(PoseStack poseStack, VertexConsumer vertices, Vec3 center,
                                 Vec3 left, Vec3 up, float radiusX, float radiusY, int segments,
                                 float red, float green, float blue, float alpha) {
        if (alpha <= 0.001F || radiusX <= 0.001F || radiusY <= 0.001F) {
            return;
        }
        Vec3 previous = ringPoint(center, left, up, radiusX, radiusY, 0.0F);
        for (int segment = 1; segment <= segments; segment++) {
            float angle = segment * TWO_PI / segments;
            Vec3 current = ringPoint(center, left, up, radiusX, radiusY, angle);
            emitQuad(poseStack, vertices, center, previous, current, current,
                    red, green, blue, alpha);
            previous = current;
        }
    }

    private static void emitBillboardSegment(PoseStack poseStack, VertexConsumer vertices,
                                             Vec3 start, Vec3 end, Vec3 left, Vec3 up,
                                             float halfThickness, float red, float green,
                                             float blue, float alpha) {
        Vec3 delta = end.subtract(start);
        float dx = (float) delta.dot(left);
        float dy = (float) delta.dot(up);
        Vec3 perpendicular = left.scale(-dy).add(up.scale(dx));
        if (perpendicular.lengthSqr() < 0.000001D) {
            perpendicular = left;
        } else {
            perpendicular = perpendicular.normalize();
        }
        Vec3 offset = perpendicular.scale(halfThickness);
        emitQuad(poseStack, vertices,
                start.subtract(offset), start.add(offset), end.add(offset), end.subtract(offset),
                red, green, blue, alpha);
    }

    private static void emitTaperedBillboardSegment(PoseStack poseStack, VertexConsumer vertices,
                                                    Vec3 start, Vec3 end, Vec3 left, Vec3 up,
                                                    float halfThickness, float red, float green,
                                                    float blue, float startAlpha, float endAlpha) {
        if (halfThickness <= 0.001F || (startAlpha <= 0.001F && endAlpha <= 0.001F)) {
            return;
        }
        Vec3 delta = end.subtract(start);
        float dx = (float) delta.dot(left);
        float dy = (float) delta.dot(up);
        Vec3 perpendicular = left.scale(-dy).add(up.scale(dx));
        if (perpendicular.lengthSqr() < 0.000001D) {
            perpendicular = left;
        } else {
            perpendicular = perpendicular.normalize();
        }
        Vec3 offset = perpendicular.scale(halfThickness);
        PoseStack.Pose pose = poseStack.last();
        int r = color(red);
        int g = color(green);
        int b = color(blue);
        vertices.addVertex(pose, (float) (start.x - offset.x), (float) (start.y - offset.y),
                (float) (start.z - offset.z)).setColor(r, g, b, color(startAlpha));
        vertices.addVertex(pose, (float) (start.x + offset.x), (float) (start.y + offset.y),
                (float) (start.z + offset.z)).setColor(r, g, b, color(startAlpha));
        vertices.addVertex(pose, (float) (end.x + offset.x), (float) (end.y + offset.y),
                (float) (end.z + offset.z)).setColor(r, g, b, color(endAlpha));
        vertices.addVertex(pose, (float) (end.x - offset.x), (float) (end.y - offset.y),
                (float) (end.z - offset.z)).setColor(r, g, b, color(endAlpha));
    }

    private static void emitQuad(PoseStack poseStack, VertexConsumer vertices,
                                 Vec3 first, Vec3 second, Vec3 third, Vec3 fourth,
                                 float red, float green, float blue, float alpha) {
        if (alpha <= 0.001F) {
            return;
        }
        PoseStack.Pose pose = poseStack.last();
        int r = color(red);
        int g = color(green);
        int b = color(blue);
        int a = color(alpha);
        vertices.addVertex(pose, (float) first.x, (float) first.y, (float) first.z).setColor(r, g, b, a);
        vertices.addVertex(pose, (float) second.x, (float) second.y, (float) second.z).setColor(r, g, b, a);
        vertices.addVertex(pose, (float) third.x, (float) third.y, (float) third.z).setColor(r, g, b, a);
        vertices.addVertex(pose, (float) fourth.x, (float) fourth.y, (float) fourth.z).setColor(r, g, b, a);
    }

    private static int color(float value) {
        return Math.round(clamp(value, 0.0F, 1.0F) * 255.0F);
    }

    private static void renderScanStrips(PoseStack poseStack, VertexConsumer vertices, long seed,
                                         double centerX, double footY, double centerZ,
                                         float halfWidth, float height, float elapsed, float strength) {
        final int pairs = 7;
        for (int pair = 0; pair < pairs; pair++) {
            float pairPhase = unitNoise(seed, 0, pair, 11);
            float phase = fract(elapsed * (0.68F + pair * 0.041F) + pairPhase);
            float reversePhase = 1.0F - phase;
            float span = halfWidth * (0.18F + unitNoise(seed, 0, pair, 12) * 0.66F);
            float depth = Math.max(0.014F,
                    halfWidth * (0.08F + unitNoise(seed, 0, pair, 13) * 0.18F));
            float thickness = Math.max(0.009F,
                    height * (0.0035F + unitNoise(seed, 0, pair, 14) * 0.006F));
            float offset = (pair - 3.0F) * height * 0.012F;
            float hiddenOffset = clamp((elapsed - KillEffectClientState.HIDE_AFTER_TICKS) / 3.0F, 0.0F, 1.0F);
            float horizontalOffset = halfWidth * (0.24F + hiddenOffset * 0.64F)
                    * (float) Math.sin((elapsed * 1.15F + pairPhase + pair * 0.17F) * TWO_PI);
            float segmentCenter = (unitNoise(seed, (int) elapsed, pair, 15) - 0.5F) * halfWidth * 1.15F;
            float cyanY = (float) footY + height * (0.06F + phase * 0.86F) + offset;
            float magentaY = (float) footY + height * (0.08F + reversePhase * 0.84F) - offset;
            float cyanAlpha = strength * (0.27F + 0.31F * hiddenBoost(seed, pair));
            float magentaAlpha = strength * (0.24F + 0.34F * hiddenBoost(seed, pair + 5));

            filledBox(poseStack, vertices,
                    centerX + segmentCenter + horizontalOffset - span, cyanY, centerZ - depth,
                    centerX + segmentCenter + horizontalOffset + span, cyanY + thickness, centerZ + depth,
                    0.04F, 0.88F, 1.0F, cyanAlpha);
            filledBox(poseStack, vertices,
                    centerX - segmentCenter - horizontalOffset - span, magentaY, centerZ - depth,
                    centerX - segmentCenter - horizontalOffset + span, magentaY + thickness, centerZ + depth,
                    1.0F, 0.08F, 0.84F, magentaAlpha);
        }
    }

    private static void renderWhiteScanLine(PoseStack poseStack, VertexConsumer vertices, long seed,
                                            double centerX, double footY, double centerZ,
                                            float halfWidth, float height, float elapsed, float strength) {
        float phase = fract(elapsed * 2.05F + unitNoise(seed, 0, 0, 31));
        float y = (float) footY + height * (0.035F + phase * 0.92F);
        float span = halfWidth * 1.28F;
        float depth = Math.max(0.018F, halfWidth * 0.13F);
        float thickness = Math.max(0.012F, height * 0.007F);
        filledBox(poseStack, vertices,
                centerX - span, y, centerZ - depth,
                centerX + span, y + thickness, centerZ + depth,
                0.95F, 1.0F, 1.0F, strength * 0.88F);
    }

    private static void renderDataColumns(PoseStack poseStack, VertexConsumer vertices, long seed,
                                           double centerX, double footY, double centerZ,
                                           float halfWidth, float height, float elapsed,
                                           int discreteTick, float strength) {
        final int columns = 11;
        for (int index = 0; index < columns; index++) {
            if (unitNoise(seed, discreteTick, index, 71) < 0.28F) {
                continue;
            }
            float angle = unitNoise(seed, 0, index, 72) * TWO_PI;
            float radius = halfWidth * (0.30F + unitNoise(seed, discreteTick, index, 73) * 1.10F);
            float drift = (unitNoise(seed, 0, index, 74) - 0.5F) * height * elapsed * 0.018F;
            float phase = fract(unitNoise(seed, 0, index, 75) + elapsed * (0.08F + index * 0.004F));
            float x = (float) centerX + (float) Math.cos(angle) * radius;
            float z = (float) centerZ + (float) Math.sin(angle) * radius;
            float y = (float) footY + height * phase + drift;
            float width = Math.max(0.012F, halfWidth * (0.025F + unitNoise(seed, 0, index, 76) * 0.045F));
            float columnHeight = height * (0.035F + unitNoise(seed, discreteTick, index, 77) * 0.14F);
            float alpha = strength * (0.25F + unitNoise(seed, discreteTick, index, 78) * 0.48F);
            boolean cyan = (index & 1) == 0;
            filledBox(poseStack, vertices,
                    x - width, y - columnHeight * 0.5F, z - width,
                    x + width, y + columnHeight * 0.5F, z + width,
                    cyan ? 0.03F : 1.0F,
                    cyan ? 0.92F : 0.06F,
                    cyan ? 1.0F : 0.78F,
                    alpha);
        }
    }

    /**
     * Turns the last visible slices into a deliberate compression core. The core gives the
     * effect a readable final beat instead of letting the model simply fade into particles.
     */
    private static void renderCompressionCore(PoseStack poseStack, VertexConsumer vertices, long seed,
                                              double centerX, double footY, double centerZ,
                                              float halfWidth, float height, float elapsed,
                                              int duration, int discreteTick, float strength) {
        float progress = clamp(elapsed / Math.max(1.0F, duration), 0.0F, 1.0F);
        float reveal = smoothstep(0.54F, 0.72F, progress);
        float collapse = smoothstep(0.62F, 0.94F, progress);
        float tail = 1.0F - smoothstep(0.95F, 1.0F, progress);
        float frameFlicker = unitNoise(seed, discreteTick, 0, 91);
        float flicker = frameFlicker < 0.13F ? 0.28F : 1.0F;
        float alpha = strength * reveal * tail * flicker;
        if (alpha <= 0.001F) {
            return;
        }

        float lineHalfWidth = Math.max(0.007F, halfWidth * (0.038F - collapse * 0.022F));
        float lineDepth = Math.max(0.006F, halfWidth * (0.034F - collapse * 0.020F));
        float lineSeparation = halfWidth * (0.12F - collapse * 0.095F);
        float jitter = (unitNoise(seed, discreteTick, 0, 92) - 0.5F)
                * halfWidth * (0.025F + collapse * 0.06F);
        float bottom = (float) footY + height * (0.19F - collapse * 0.12F);
        float top = (float) footY + height * (0.81F + collapse * 0.15F);

        float cyanAlpha = alpha * (0.58F + unitNoise(seed, discreteTick, 1, 93) * 0.28F);
        float magentaAlpha = alpha * (0.56F + unitNoise(seed, discreteTick, 2, 94) * 0.30F);
        filledBox(poseStack, vertices,
                centerX - lineSeparation - lineHalfWidth + jitter, bottom, centerZ - lineDepth,
                centerX - lineSeparation + lineHalfWidth + jitter, top, centerZ + lineDepth,
                0.02F, 0.90F, 1.0F, cyanAlpha);
        filledBox(poseStack, vertices,
                centerX + lineSeparation - lineHalfWidth - jitter, bottom, centerZ - lineDepth,
                centerX + lineSeparation + lineHalfWidth - jitter, top, centerZ + lineDepth,
                1.0F, 0.06F, 0.82F, magentaAlpha);

        float whitePulse = 0.5F + 0.5F * (float) Math.sin(elapsed * 2.7F + unitNoise(seed, 0, 0, 95) * TWO_PI);
        float whiteAlpha = alpha * smoothstep(0.70F, 0.90F, progress)
                * (0.20F + whitePulse * 0.62F);
        if (frameFlicker > 0.20F) {
            float whiteWidth = Math.max(0.004F, lineHalfWidth * (0.52F + whitePulse * 0.42F));
            filledBox(poseStack, vertices,
                    centerX - whiteWidth, bottom + height * 0.02F, centerZ - lineDepth * 0.72F,
                    centerX + whiteWidth, top - height * 0.02F, centerZ + lineDepth * 0.72F,
                    0.92F, 1.0F, 1.0F, whiteAlpha);
        }

        // Small horizontal memory reads keep the core from looking like a static beam.
        for (int index = 0; index < 9; index++) {
            if (unitNoise(seed, discreteTick, index, 96) < 0.24F) {
                continue;
            }
            float phase = fract(unitNoise(seed, 0, index, 97) + elapsed
                    * (0.10F + unitNoise(seed, 0, index, 98) * 0.06F));
            float y = bottom + (top - bottom) * phase;
            float span = halfWidth * (0.08F + unitNoise(seed, 0, index, 99) * 0.34F)
                    * (0.46F + collapse * 0.54F);
            float thickness = Math.max(0.006F, height * (0.0024F + unitNoise(seed, 0, index, 100) * 0.0048F));
            float barAlpha = alpha * (0.22F + unitNoise(seed, discreteTick, index, 101) * 0.42F);
            boolean cyan = (index & 1) == 0;
            filledBox(poseStack, vertices,
                    centerX - span, y - thickness, centerZ - lineDepth * 0.68F,
                    centerX + span, y + thickness, centerZ + lineDepth * 0.68F,
                    cyan ? 0.10F : 1.0F,
                    cyan ? 0.88F : 0.12F,
                    cyan ? 1.0F : 0.86F,
                    barAlpha);
        }
    }

    private static void renderFragments(PoseStack poseStack, VertexConsumer vertices, long seed,
                                        double centerX, double footY, double centerZ,
                                        float halfWidth, float height, float elapsed,
                                        int discreteTick, float strength) {
        final int fragments = 28;
        for (int index = 0; index < fragments; index++) {
            if (unitNoise(seed, discreteTick, index, 40) < 0.18F) {
                continue;
            }
            float angle = unitNoise(seed, 0, index, 41) * TWO_PI;
            float radialStart = halfWidth * (0.24F + unitNoise(seed, 0, index, 42) * 0.82F);
            float radialSpeed = Math.max(0.018F, halfWidth * (0.024F + unitNoise(seed, 0, index, 43) * 0.055F));
            float radius = radialStart + radialSpeed * elapsed;
            float verticalStart = height * (0.05F + unitNoise(seed, 0, index, 44) * 0.86F);
            float verticalSpeed = height * (-0.015F + unitNoise(seed, 0, index, 45) * 0.036F);
            float jitterX = (unitNoise(seed, discreteTick, index, 46) - 0.5F) * halfWidth * 0.14F;
            float jitterZ = (unitNoise(seed, discreteTick, index, 47) - 0.5F) * halfWidth * 0.14F;
            double fragmentX = centerX + Math.cos(angle) * radius + jitterX;
            double fragmentZ = centerZ + Math.sin(angle) * radius + jitterZ;
            float fragmentY = (float) footY + verticalStart + verticalSpeed * elapsed;
            float sizeX = clamp(halfWidth * (0.025F + unitNoise(seed, 0, index, 48) * 0.11F),
                    0.018F, 0.20F);
            float sizeY = clamp(height * (0.004F + unitNoise(seed, 0, index, 50) * 0.018F),
                    0.012F, 0.13F);
            float sizeZ = clamp(halfWidth * (0.012F + unitNoise(seed, 0, index, 52) * 0.050F),
                    0.008F, 0.10F);
            float alpha = strength * (0.34F + unitNoise(seed, discreteTick, index, 49) * 0.48F);
            boolean cyan = (index & 1) == 0;
            filledBox(poseStack, vertices,
                    fragmentX - sizeX, fragmentY - sizeY, fragmentZ - sizeZ,
                    fragmentX + sizeX, fragmentY + sizeY, fragmentZ + sizeZ,
                    cyan ? 0.05F : 0.96F,
                    cyan ? 0.90F : 0.10F,
                    cyan ? 1.0F : 0.86F,
                    alpha);
        }
    }

    private static void filledBox(PoseStack poseStack, VertexConsumer vertices,
                                  double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ,
                                  float red, float green, float blue, float alpha) {
        if (alpha <= 0.001F) {
            return;
        }
        LevelRenderer.addChainedFilledBoxVertices(poseStack, vertices,
                minX, minY, minZ, maxX, maxY, maxZ,
                red, green, blue, clamp(alpha, 0.0F, 1.0F));
    }

    private static boolean withinReceiveRange(LivingEntity player, Vec3 trackedPosition) {
        double dx = trackedPosition.x - player.getX();
        double dy = trackedPosition.y - player.getY();
        double dz = trackedPosition.z - player.getZ();
        return dx * dx + dy * dy + dz * dz <= KillEffectClientState.RECEIVE_RANGE_SQUARED;
    }

    private static AABB effectBounds(KillEffectClientState.Snapshot snapshot, Vec3 trackedPosition) {
        double reach = Math.max(1.5D, Math.max(snapshot.width() * 2.6D, snapshot.height() * 1.35D));
        double minY = Math.min(snapshot.y(), trackedPosition.y) - 1.0D;
        double maxY = Math.max(snapshot.y(), trackedPosition.y) + snapshot.height() + 2.0D;
        return new AABB(trackedPosition.x - reach, minY,
                trackedPosition.z - reach,
                trackedPosition.x + reach, maxY,
                trackedPosition.z + reach);
    }

    private static float fadeAtEnd(float elapsed, int duration) {
        float tail = Math.max(1.5F, duration * 0.22F);
        return clamp((duration - elapsed) / tail, 0.0F, 1.0F);
    }

    private static float hiddenBoost(long seed, int index) {
        return 0.75F + unitNoise(seed, 0, index, 51) * 0.25F;
    }

    private static float unitNoise(long seed, int tick, int index, int salt) {
        long value = seed + 0x9E3779B97F4A7C15L * (long) (index + 1);
        value ^= 0xD1B54A32D192ED03L * (long) (tick + 1);
        value += 0x94D049BB133111EBL * (long) (salt + 1);
        value = mix64(value);
        return (float) ((value >>> 40) * (1.0D / 16_777_216.0D));
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float normalized = clamp((value - edge0) / Math.max(0.0001F, edge1 - edge0), 0.0F, 1.0F);
        return normalized * normalized * (3.0F - 2.0F * normalized);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
