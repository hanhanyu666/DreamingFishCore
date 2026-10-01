package com.hhy.dreamingfishcore.client.ui.framework.render;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * SDF 图形的 GPU 提交器。
 *
 * <p>顶点数据写入自己的 VAO / VBO，着色器程序借用原版 {@link ShaderInstance} 管理（统一变量、
 * 程序缓存和资源重载都走原版流程）。这样不需要注册自定义 {@code VertexFormatElement}，
 * 不会与其他模组的顶点元素 ID 冲突。提交结束后解绑 VAO 并让 {@link BufferUploader} 失效，
 * 原版下一次绘制会重新绑定自己的缓冲。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class SdfRenderer {
    static final int STRIDE = 120;

    private static final Attribute[] ATTRIBUTES = {
            new Attribute("Position", 3, GL11.GL_FLOAT, false, 0),
            new Attribute("Local", 2, GL11.GL_FLOAT, false, 12),
            new Attribute("Half", 2, GL11.GL_FLOAT, false, 20),
            new Attribute("Radii", 4, GL11.GL_FLOAT, false, 28),
            new Attribute("Style", 4, GL11.GL_FLOAT, false, 44),
            new Attribute("Grad", 4, GL11.GL_FLOAT, false, 60),
            new Attribute("Clip", 4, GL11.GL_FLOAT, false, 76),
            new Attribute("Extra", 4, GL11.GL_FLOAT, false, 92),
            new Attribute("Fill0", 4, GL11.GL_UNSIGNED_BYTE, true, 108),
            new Attribute("Fill1", 4, GL11.GL_UNSIGNED_BYTE, true, 112),
            new Attribute("Border", 4, GL11.GL_UNSIGNED_BYTE, true, 116),
    };

    private static ShaderInstance solidShader;
    private static ShaderInstance textureShader;
    private static int vbo;
    private static int solidVao;
    private static int textureVao;
    private static int solidVaoProgram = -1;
    private static int textureVaoProgram = -1;
    private static boolean failed;
    private static volatile boolean forceFallback;

    private SdfRenderer() {
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        solidShader = null;
        textureShader = null;
        failed = false;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "ui_sdf"),
                    DefaultVertexFormat.POSITION), loaded -> solidShader = loaded);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "ui_sdf_tex"),
                    DefaultVertexFormat.POSITION), loaded -> textureShader = loaded);
        } catch (IOException exception) {
            DreamingFishCore.LOGGER.error("UI SDF 着色器加载失败，界面将退回原版绘制", exception);
        }
    }

    /** 着色器已就绪；首次资源加载完成前返回 false，此时由调用方退回原版绘制。 */
    public static boolean available() {
        return !forceFallback && !failed && solidShader != null && textureShader != null;
    }

    /** 调试用：强制按“着色器未就绪”绘制，预览首次启动阶段的样子。 */
    public static void forceFallback(boolean value) {
        forceFallback = value;
    }

    /**
     * 提交一层图形。{@code data} 的 position 为 0、limit 为有效字节数。
     *
     * @param segments 每段连续的四边形共用同一个着色器和贴图
     */
    static void draw(ByteBuffer data, int quadCount, ShapeBuffer.Segment[] segments, int segmentCount) {
        if (quadCount <= 0 || !available()) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        try {
            drawInternal(data, quadCount, segments, segmentCount);
        } catch (RuntimeException exception) {
            failed = true;
            DreamingFishCore.LOGGER.error("UI SDF 绘制失败，界面将退回原版绘制", exception);
        } finally {
            GlStateManager._glBindVertexArray(0);
            BufferUploader.invalidate();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
        }
    }

    private static void drawInternal(ByteBuffer data, int quadCount, ShapeBuffer.Segment[] segments, int segmentCount) {
        Window window = Minecraft.getInstance().getWindow();
        BufferUploader.invalidate();
        if (vbo == 0) {
            vbo = GlStateManager._glGenBuffers();
        }
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GlStateManager._glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();

        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        for (int i = 0; i < segmentCount; i++) {
            ShapeBuffer.Segment segment = segments[i];
            boolean textured = segment.textureId >= 0;
            ShaderInstance shader = textured ? textureShader : solidShader;
            if (textured) {
                RenderSystem.setShaderTexture(0, segment.textureId);
            }
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS,
                    RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), window);
            shader.apply();
            GlStateManager._glBindVertexArray(vaoFor(shader, textured));
            indices.bind(quadCount * 6);
            VertexFormat.IndexType type = indices.type();
            GlStateManager._drawElements(GL11.GL_TRIANGLES, segment.quadCount * 6, type.asGLType,
                    (long) segment.firstQuad * 6L * type.bytes);
            shader.clear();
        }
    }

    private static int vaoFor(ShaderInstance shader, boolean textured) {
        int program = shader.getId();
        if (textured) {
            if (textureVao == 0 || textureVaoProgram != program) {
                textureVao = rebuildVao(textureVao, program);
                textureVaoProgram = program;
            }
            return textureVao;
        }
        if (solidVao == 0 || solidVaoProgram != program) {
            solidVao = rebuildVao(solidVao, program);
            solidVaoProgram = program;
        }
        return solidVao;
    }

    private static int rebuildVao(int previous, int program) {
        if (previous != 0) {
            GlStateManager._glDeleteVertexArrays(previous);
        }
        int vao = GlStateManager._glGenVertexArrays();
        GlStateManager._glBindVertexArray(vao);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        for (Attribute attribute : ATTRIBUTES) {
            int location = GL20.glGetAttribLocation(program, attribute.name);
            if (location < 0) {
                continue;
            }
            GlStateManager._enableVertexAttribArray(location);
            GlStateManager._vertexAttribPointer(location, attribute.size, attribute.type,
                    attribute.normalized, STRIDE, attribute.offset);
        }
        return vao;
    }

    private record Attribute(String name, int size, int type, boolean normalized, long offset) {
    }
}
