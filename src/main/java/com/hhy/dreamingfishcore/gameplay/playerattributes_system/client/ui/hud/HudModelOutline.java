package com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;

/**
 * 左下角的玩家线稿：把玩家实体（含护甲与手持物品）渲染到离屏目标，
 * 再由着色器从深度中提取轮廓线与棱线，并叠加生命、感染和部位状态。
 *
 * <p>着色器不可用或渲染出错时返回 {@code false}，由调用方改用程序化人形。
 * 游泳、滑翔等横躺姿态不重新渲染模型，沿用上一帧站立的线稿。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
final class HudModelOutline {
    /** 线稿朝向屏幕中央：身体偏转角与轻微俯视角。 */
    private static final float BODY_YAW = 180.0F - 32.0F;
    private static final float VIEW_TILT_DEGREES = 8.0F;
    private static final float MAX_HEAD_TURN = 50.0F;
    private static final float MAX_HEAD_PITCH = 40.0F;
    /** 深度投影范围；线稿阈值按这个范围换算成归一化深度。 */
    private static final float DEPTH_RANGE = 2000.0F;
    private static final float STEP_THRESHOLD_LOW = 0.18F;
    private static final float STEP_THRESHOLD_HIGH = 0.4F;
    private static final float CREASE_THRESHOLD_LOW = 0.25F;
    private static final float CREASE_THRESHOLD_HIGH = 0.6F;
    private static final float HEAD_ALLOWANCE_BLOCKS = 0.12F;
    /**
     * 模型离屏重绘的最短间隔（约 40 次每秒）。高帧率下每帧重绘整个玩家实体代价远大于收益；
     * 生命、闪白、扫描等叠加效果仍在合成时逐帧更新。
     */
    private static final long MODEL_FRAME_INTERVAL_MS = 25L;

    private static ShaderInstance shader;
    private static TextureTarget target;
    private static MultiBufferSource.BufferSource buffers;
    private static boolean failed;
    private static boolean hasFrame;
    private static long lastModelFrameAt;

    private HudModelOutline() {
    }

    @SubscribeEvent
    public static void registerShader(RegisterShadersEvent event) {
        shader = null;
        failed = false;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "hud_body_outline"),
                    DefaultVertexFormat.POSITION_TEX), loaded -> shader = loaded);
        } catch (IOException exception) {
            DreamingFishCore.LOGGER.error("体征线稿着色器加载失败，将使用程序化人形", exception);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (RenderSystem.isOnRenderThread()) {
            release();
        } else {
            RenderSystem.recordRenderCall(HudModelOutline::release);
        }
    }

    /** 线稿布局：GUI 坐标下的绘制区域、脚底锚点与人形高度。 */
    record Layout(int left, int top, int width, int height, float anchorX, float footY, float figureHeight) {
    }

    /**
     * 渲染一帧线稿。需要重绘模型时会先提交共享 GUI 批次，
     * 因为离屏渲染期间可能有物品渲染器直接结束共享缓冲。
     */
    static boolean render(GuiGraphics graphics, Minecraft minecraft, Player player, Layout layout,
                          HudBodyPainter.Visual visual, float partialTick) {
        if (shader == null || failed) {
            return false;
        }
        double guiScale = minecraft.getWindow().getGuiScale();
        int textureWidth = (int) Math.ceil(layout.width() * guiScale);
        int textureHeight = (int) Math.ceil(layout.height() * guiScale);
        if (textureWidth < 4 || textureHeight < 4) {
            return false;
        }

        try {
            if (target == null || target.width != textureWidth || target.height != textureHeight) {
                release();
                target = new TextureTarget(textureWidth, textureHeight, true, Minecraft.ON_OSX);
                target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                hasFrame = false;
            }
            long now = visual.timeMillis();
            boolean due = now - lastModelFrameAt >= MODEL_FRAME_INTERVAL_MS || now < lastModelFrameAt;
            if (!hasFrame || due && !isLyingDown(player)) {
                graphics.flush();
                renderModel(minecraft, player, layout, (float) guiScale, partialTick);
                hasFrame = true;
                lastModelFrameAt = now;
            }
            composite(graphics, layout, visual, (float) guiScale);
            return true;
        } catch (RuntimeException exception) {
            failed = true;
            DreamingFishCore.LOGGER.error("体征线稿渲染失败，已改用程序化人形", exception);
            return false;
        }
    }

    static void release() {
        if (target != null) {
            int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int deleted = target.frameBufferId;
            target.destroyBuffers();
            target = null;
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read == deleted ? 0 : read);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw == deleted ? 0 : draw);
        }
        hasFrame = false;
    }

    private static boolean isLyingDown(Player player) {
        return player.isVisuallySwimming() || player.isFallFlying() || player.isAutoSpinAttack()
                || player.isSleeping();
    }

    private static void renderModel(Minecraft minecraft, Player player, Layout layout, float guiScale,
                                    float partialTick) {
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        ShaderInstance previousShader = RenderSystem.getShader();

        // 真实插值后的头部朝向：线稿的头部会跟随玩家实际转头与俯仰。
        float bodyRot = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float headRot = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
        float headTurn = Mth.clamp(Mth.wrapDegrees(headRot - bodyRot), -MAX_HEAD_TURN, MAX_HEAD_TURN);
        float pitch = Mth.clamp(Mth.lerp(partialTick, player.xRotO, player.getXRot()),
                -MAX_HEAD_PITCH, MAX_HEAD_PITCH);

        float savedBodyRot = player.yBodyRot;
        float savedBodyRotO = player.yBodyRotO;
        float savedYRot = player.getYRot();
        float savedYRotO = player.yRotO;
        float savedXRot = player.getXRot();
        float savedXRotO = player.xRotO;
        float savedHeadRot = player.yHeadRot;
        float savedHeadRotO = player.yHeadRotO;

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        RenderSystem.backupProjectionMatrix();
        modelView.pushMatrix();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        try {
            RenderSystem.depthMask(true);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);

            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0F, target.width, target.height, 0.0F,
                    -DEPTH_RANGE / 2.0F, DEPTH_RANGE / 2.0F), VertexSorting.ORTHOGRAPHIC_Z);
            modelView.identity();
            RenderSystem.applyModelViewMatrix();

            float modelScale = player.getScale() > 0.0F ? player.getScale() : 1.0F;
            float pixelsPerBlock = layout.figureHeight() / 1.8F * guiScale / modelScale;
            PoseStack pose = new PoseStack();
            pose.translate((layout.anchorX() - layout.left()) * guiScale,
                    (layout.footY() - layout.top()) * guiScale, 0.0F);
            pose.scale(pixelsPerBlock, pixelsPerBlock, -pixelsPerBlock);
            Quaternionf tilt = new Quaternionf().rotateX(VIEW_TILT_DEGREES * Mth.DEG_TO_RAD);
            pose.mulPose(new Quaternionf().rotateZ(Mth.PI).mul(tilt));

            player.yBodyRot = BODY_YAW;
            player.yBodyRotO = BODY_YAW;
            player.setYRot(BODY_YAW + headTurn);
            player.yRotO = BODY_YAW + headTurn;
            player.yHeadRot = BODY_YAW + headTurn;
            player.yHeadRotO = BODY_YAW + headTurn;
            player.setXRot(pitch);
            player.xRotO = pitch;

            if (buffers == null) {
                buffers = MultiBufferSource.immediate(new ByteBufferBuilder(262_144));
            }
            Lighting.setupForEntityInInventory();
            dispatcher.overrideCameraOrientation(tilt.conjugate(new Quaternionf()).rotateY(Mth.PI));
            dispatcher.setRenderShadow(false);
            RenderSystem.runAsFancy(() -> dispatcher.render(player, 0.0D, 0.0D, 0.0D, 0.0F, partialTick,
                    pose, buffers, 15728880));
            buffers.endBatch();
            // 个别物品渲染器会直接写入共享缓冲；调用前已清空它，这里的内容只可能来自本次渲染。
            minecraft.renderBuffers().bufferSource().endBatch();
        } finally {
            dispatcher.setRenderShadow(true);
            Lighting.setupFor3DItems();
            player.yBodyRot = savedBodyRot;
            player.yBodyRotO = savedBodyRotO;
            player.setYRot(savedYRot);
            player.yRotO = savedYRotO;
            player.setXRot(savedXRot);
            player.xRotO = savedXRotO;
            player.yHeadRot = savedHeadRot;
            player.yHeadRotO = savedHeadRotO;

            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.setShader(() -> previousShader);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            restore(depthTest, blend, cull, depthWrite);
        }
    }

    private static void composite(GuiGraphics graphics, HudModelOutline.Layout layout,
                                  HudBodyPainter.Visual visual, float guiScale) {
        float health = HudPalette.clamp01(visual.healthRatio());
        float lag = Math.max(health, HudPalette.clamp01(visual.lagRatio()));
        boolean critical = health < 0.15F;
        float criticalPulse = critical ? HudPalette.pulse(visual.timeMillis(), 900L) : 0.0F;
        float danger = HudPalette.clamp01((0.5F - health) / 0.35F);
        int healthColor = HudPalette.bodyColor(health);

        float footV = 1.0F - (layout.footY() - layout.top()) / layout.height();
        float topV = 1.0F - (layout.footY() - layout.figureHeight() * (1.0F + HEAD_ALLOWANCE_BLOCKS / 1.8F)
                - layout.top()) / layout.height();
        float span = topV - footV;
        float scan = visual.scanProgress() >= 0.0F && visual.scanProgress() <= 1.0F
                ? topV - visual.scanProgress() * span
                : -1.0F;
        float modelPixel = layout.figureHeight() / 1.8F * guiScale / 16.0F;

        shader.setSampler("FigureDepth", target.getDepthTextureId());
        shader.safeGetUniform("TexelSize").set(1.0F / target.width, 1.0F / target.height);
        shader.safeGetUniform("Thresholds").set(
                STEP_THRESHOLD_LOW * modelPixel / DEPTH_RANGE,
                STEP_THRESHOLD_HIGH * modelPixel / DEPTH_RANGE,
                CREASE_THRESHOLD_LOW / DEPTH_RANGE,
                CREASE_THRESHOLD_HIGH / DEPTH_RANGE);
        setColor("LineColor", healthColor, critical ? 0.6F + 0.4F * criticalPulse : 0.94F);
        setColor("SpentLineColor", HudPalette.BONE_DIM, 0.36F);
        setColor("FillColor", healthColor, 0.16F + 0.16F * danger + 0.1F * criticalPulse);
        setColor("LagColor", HudPalette.BONE, 0.3F);
        setColor("InfectionColor", HudPalette.INFECTION_ROT, 0.62F);
        shader.safeGetUniform("Levels").set(footV, topV, footV + health * span, footV + lag * span);
        shader.safeGetUniform("InfectionParams").set(visual.infectionRatio(), 4.0F * modelPixel,
                guiScale >= 3.0F ? 1.0F : 0.0F, 0.34F);
        setBand("BandHead", visual, HudBodyGeometry.REGION_HEAD);
        setBand("BandChest", visual, HudBodyGeometry.REGION_CHEST);
        setBand("BandLegs", visual, HudBodyGeometry.REGION_LEGS);
        setBand("BandFeet", visual, HudBodyGeometry.REGION_FEET);
        shader.safeGetUniform("Effects").set(visual.flash(), scan, 0.16F, 0.0F);

        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        ShaderInstance previousShader = RenderSystem.getShader();
        try {
            RenderSystem.setShader(() -> shader);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            Matrix4f pose = graphics.pose().last().pose();
            float left = layout.left();
            float top = layout.top();
            float right = left + layout.width();
            float bottom = top + layout.height();
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION_TEX);
            buffer.addVertex(pose, left, top, 0.0F).setUv(0.0F, 1.0F);
            buffer.addVertex(pose, left, bottom, 0.0F).setUv(0.0F, 0.0F);
            buffer.addVertex(pose, right, bottom, 0.0F).setUv(1.0F, 0.0F);
            buffer.addVertex(pose, right, top, 0.0F).setUv(1.0F, 1.0F);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            RenderSystem.setShader(() -> previousShader);
            if (depthTest) {
                RenderSystem.enableDepthTest();
            }
            if (!blend) {
                RenderSystem.disableBlend();
            }
        }
    }

    /** 部位警示：受击时泛红；穿戴装备耐久过低时闪烁琥珀或红色。真实护甲本身已画在线稿中。 */
    private static void setBand(String uniform, HudBodyPainter.Visual visual, int region) {
        float hit = HudPalette.clamp01(visual.regionHit()[region]);
        if (hit > 0.0F) {
            setColor(uniform, HudPalette.RED, hit);
            return;
        }
        float durability = visual.regionDurability()[region];
        if (visual.regionPlated()[region] && durability <= 0.25F) {
            boolean severe = durability <= 0.1F;
            float pulse = HudPalette.pulse(visual.timeMillis(), severe ? 520L : 1100L);
            setColor(uniform, severe ? HudPalette.RED : HudPalette.AMBER, 0.35F + 0.4F * pulse);
            return;
        }
        shader.safeGetUniform(uniform).set(0.0F, 0.0F, 0.0F, 0.0F);
    }

    private static void setColor(String uniform, int rgb, float alpha) {
        shader.safeGetUniform(uniform).set((rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F, HudPalette.clamp01(alpha));
    }

    private static void restore(boolean depthTest, boolean blend, boolean cull, boolean depthWrite) {
        if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        RenderSystem.depthMask(depthWrite);
    }
}
