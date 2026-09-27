package com.hhy.dreamingfishcore.gameplay.kill_effect_system.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** One scene copy and one bounded pass, after world composition and before hand/HUD rendering. */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class BlackHoleLensRenderer {
    private static final int MAX_LENSES = 4;
    private static ShaderInstance shader;
    private static TextureTarget scene;
    private static boolean failed;

    private BlackHoleLensRenderer() { }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (RenderSystem.isOnRenderThread()) release();
        else RenderSystem.recordRenderCall(BlackHoleLensRenderer::release);
    }

    @SubscribeEvent
    public static void registerShader(RegisterShadersEvent event) {
        shader = null;
        failed = false;
        release();
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "black_hole_lens"),
                    DefaultVertexFormat.POSITION), loaded -> shader = loaded);
        } catch (IOException exception) {
            // A broken resource-pack override must not prevent the client from starting.
            DreamingFishCore.LOGGER.error("黑洞透镜着色器加载失败，将保留几何特效", exception);
        }
    }

    public static void release() {
        if (scene != null) {
            int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int deleted = scene.frameBufferId;
            scene.destroyBuffers();
            scene = null;
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read == deleted ? 0 : read);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw == deleted ? 0 : draw);
        }
    }

    public static void render(RenderLevelStageEvent event, List<KillEffectClientState.Snapshot> snapshots) {
        if (shader == null || failed) return;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (main.width < 1 || main.height < 1 || !main.useDepth || mc.player == null) return;
        // Sampling a shader pack's unfinished target would overwrite its deferred attachments.
        if (GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != main.frameBufferId) return;
        float aspect = (float) main.width / main.height;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 camera = event.getCamera().getPosition();
        List<BlackHoleLensProjection.Lens> lenses = new ArrayList<>();
        for (KillEffectClientState.Snapshot snapshot : snapshots) {
            if (mc.level == null || !snapshot.dimension().equals(mc.level.dimension())) continue;
            Vec3 position = KillEffectClientState.trackedPosition(snapshot, partial);
            if (mc.player.position().distanceToSqr(position) > KillEffectClientState.RECEIVE_RANGE_SQUARED) continue;
            float elapsed = KillEffectClientState.elapsedTicks(snapshot, partial);
            if (!Float.isFinite(elapsed) || elapsed < 0 || elapsed >= snapshot.durationTicks()) continue;
            var frame = BlackHoleGeometry.sample(snapshot.width(), snapshot.height(), elapsed,
                    snapshot.durationTicks(), snapshot.height(), snapshot.seed(), false);
            var lens = BlackHoleLensProjection.project(event.getModelViewMatrix(), event.getProjectionMatrix(),
                    (float) (position.x - camera.x), (float) (snapshot.y() + 0.028 + frame.center().y() - camera.y),
                    (float) (position.z - camera.z), frame, aspect, mc.options.screenEffectScale().get().floatValue());
            if (lens != null && lens.strength() > 0.001F) lenses.add(lens);
        }
        if (lenses.isEmpty()) return;
        // The largest apparent sources dominate. Crowds never allocate a render target per kill.
        lenses.sort(Comparator.comparingDouble(BlackHoleLensProjection.Lens::radius).reversed());
        if (lenses.size() > MAX_LENSES) lenses.subList(MAX_LENSES, lenses.size()).clear();

        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        ShaderInstance previousShader = RenderSystem.getShader();
        try {
            if (scene != null && (scene.width != main.width || scene.height != main.height
                    || scene.isStencilEnabled() != main.isStencilEnabled())) release();
            if (scene == null) {
                scene = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
                if (main.isStencilEnabled()) scene.enableStencil();
                scene.setFilterMode(GL11.GL_LINEAR);
            }
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, main.width, main.height,
                    GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
            shader.setSampler("SceneSampler", scene.getColorTextureId());
            shader.setSampler("DepthSampler", scene.getDepthTextureId());
            shader.safeGetUniform("Viewport").set((float) main.width, (float) main.height);
            shader.safeGetUniform("LensCount").set(lenses.size());
            float left = 1, bottom = 1, right = 0, top = 0;
            for (int i = 0; i < MAX_LENSES; i++) {
                if (i >= lenses.size()) {
                    shader.safeGetUniform("Hole" + i).set(0F, 0F, 0F, 0F);
                    shader.safeGetUniform("Shape" + i).set(0F, 0F, 0F, 0F);
                    continue;
                }
                var lens = lenses.get(i);
                shader.safeGetUniform("Hole" + i).set(lens.x(), lens.y(), lens.radius(), lens.strength());
                shader.safeGetUniform("Shape" + i).set(lens.coreScale(), lens.progress(), lens.nearDepth(), 0);
                float reach = lens.radius() * 6.2F;
                left = Math.min(left, lens.x() - reach / aspect);
                right = Math.max(right, lens.x() + reach / aspect);
                bottom = Math.min(bottom, lens.y() - reach);
                top = Math.max(top, lens.y() + reach);
            }
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            shader.apply();
            BufferBuilder buffer = RenderSystem.renderThreadTesselator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            buffer.addVertex(ndc(left), ndc(bottom), 0);
            buffer.addVertex(ndc(right), ndc(bottom), 0);
            buffer.addVertex(ndc(right), ndc(top), 0);
            buffer.addVertex(ndc(left), ndc(top), 0);
            BufferUploader.draw(buffer.buildOrThrow());
        } catch (RuntimeException exception) {
            failed = true;
            DreamingFishCore.LOGGER.error("黑洞场景透镜已停用，几何特效继续显示", exception);
        } finally {
            shader.clear();
            RenderSystem.setShader(() -> previousShader);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.depthMask(depthWrite);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        }
    }

    private static float ndc(float uv) { return Math.max(0, Math.min(1, uv)) * 2 - 1; }
}
