package com.hhy.dreamingfishcore.gameplay.task_location_system.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.OptionalDouble;

/** Briefly outlines a task location when the player crosses into it. */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class TaskLocationBoundaryRenderer {
    private static final long FADE_IN_MS = 180L;
    private static final long FADE_OUT_MS = 800L;
    private static final double EDGE_OFFSET = 0.002D;
    private static final double EYE_BAND_HALF_HEIGHT = 0.012D;
    private static final int BUILDABLE_CORE = 0xFFFF4FDC;
    private static final int BUILDABLE_HALO = 0xFF300023;
    private static final int PROTECTED_CORE = 0xFFFF9A3D;
    private static final int PROTECTED_HALO = 0xFF3B1600;

    /** No depth test: both passes stay visible through terrain without filling the region. */
    private static final RenderType THROUGH_TERRAIN_HALO_LINES = createLineType(
            "dreamingfish_task_location_boundary_halo", 4.0D);
    private static final RenderType THROUGH_TERRAIN_CORE_LINES = createLineType(
            "dreamingfish_task_location_boundary_core", 2.0D);

    private static RenderType createLineType(String name, double width) {
        return RenderType.create(
                name,
                DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES,
                256,
                false,
                false,
                RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(width)))
                    .setLayeringState(RenderStateShard.NO_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));
    }

    private TaskLocationBoundaryRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || minecraft.options.hideGui
                || minecraft.getDebugOverlay().showDebugScreen()
                || minecraft.screen != null) {
            return;
        }

        TaskLocationClientState.Snapshot snapshot = TaskLocationClientState.get();
        if (snapshot == null) {
            return;
        }

        float visibility = visibility(snapshot.enteredAtMs(), System.currentTimeMillis());
        if (visibility <= 0.001F) {
            return;
        }

        renderBoundary(event, minecraft, snapshot, visibility);
    }

    private static void renderBoundary(RenderLevelStageEvent event, Minecraft minecraft,
                                       TaskLocationClientState.Snapshot snapshot,
                                       float visibility) {
        BlockPos min = snapshot.min();
        BlockPos max = snapshot.max();
        Vec3 camera = event.getCamera().getPosition();
        double minX = min.getX() - camera.x - EDGE_OFFSET;
        double minY = min.getY() - camera.y - EDGE_OFFSET;
        double minZ = min.getZ() - camera.z - EDGE_OFFSET;
        double maxX = max.getX() + 1.0D - camera.x + EDGE_OFFSET;
        double maxY = max.getY() + 1.0D - camera.y + EDGE_OFFSET;
        double maxZ = max.getZ() + 1.0D - camera.z + EDGE_OFFSET;
        /*
         * Keep the additional horizontal guide attached to the location itself.  Using the
         * player's eye Y here makes the guide (and, for the usual one-block-high locations, the
         * whole visible frame) appear to jump along with the player.  The boundary is world data,
         * so its guide must use a fixed point halfway between the configured Y limits.
         */
        double guideWorldY = min.getY() + (max.getY() + 1.0D - min.getY()) * 0.5D;
        double guideBandY = guideWorldY - camera.y;

        int core = snapshot.mode() == TaskLocationMode.BUILDABLE
                ? BUILDABLE_CORE : PROTECTED_CORE;
        int halo = snapshot.mode() == TaskLocationMode.BUILDABLE
                ? BUILDABLE_HALO : PROTECTED_HALO;
        float pulse = 0.88F + 0.12F
                * (0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 145.0D));
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        /*
         * RenderStateShard.NO_DEPTH_TEST deliberately performs no setup for GL_ALWAYS. When this
         * standalone batch follows terrain rendering, the previous GL depth state can therefore
         * remain enabled. Force it off around the actual buffer flush so terrain can never hide the
         * boundary, then restore the state expected by the rest of the level renderer.
         */
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
            renderBoundaryPass(poseStack, buffers.getBuffer(THROUGH_TERRAIN_HALO_LINES),
                    minX, minY, minZ, maxX, maxY, maxZ, guideBandY,
                    halo, visibility * 0.82F);
            buffers.endBatch(THROUGH_TERRAIN_HALO_LINES);

            renderBoundaryPass(poseStack, buffers.getBuffer(THROUGH_TERRAIN_CORE_LINES),
                    minX, minY, minZ, maxX, maxY, maxZ, guideBandY,
                    core, visibility * pulse);
            buffers.endBatch(THROUGH_TERRAIN_CORE_LINES);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    private static void renderBoundaryPass(PoseStack poseStack, VertexConsumer lines,
                                           double minX, double minY, double minZ,
                                           double maxX, double maxY, double maxZ,
                                            double guideBandY, int color, float alpha) {
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        LevelRenderer.renderLineBox(poseStack, lines,
                minX, minY, minZ, maxX, maxY, maxZ,
                red, green, blue, alpha);
        LevelRenderer.renderLineBox(poseStack, lines,
                 minX, guideBandY - EYE_BAND_HALF_HEIGHT, minZ,
                 maxX, guideBandY + EYE_BAND_HALF_HEIGHT, maxZ,
                red, green, blue, alpha);
    }

    static float visibility(long enteredAtMs, long nowMs) {
        long age = Math.max(0L, nowMs - enteredAtMs);
        if (age >= TaskLocationClientState.BOUNDARY_DURATION_MS) {
            return 0.0F;
        }

        float intro = Math.min(1.0F, age / (float) FADE_IN_MS);
        long fadeOutStart = TaskLocationClientState.BOUNDARY_DURATION_MS - FADE_OUT_MS;
        float outro = age <= fadeOutStart
                ? 1.0F
                : 1.0F - (age - fadeOutStart) / (float) FADE_OUT_MS;
        return Math.max(0.0F, Math.min(1.0F, intro * outro));
    }
}
