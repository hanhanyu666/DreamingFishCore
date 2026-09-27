package com.hhy.dreamingfishcore.client.ui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Retained two-layer player face for HUD positions that do not move per frame. */
public final class RetainedPlayerFace implements AutoCloseable {
    private final ResourceLocation skin;
    private final VertexBuffer vertexBuffer;

    private RetainedPlayerFace(ResourceLocation skin, VertexBuffer vertexBuffer) {
        this.skin = skin;
        this.vertexBuffer = vertexBuffer;
    }

    public static RetainedPlayerFace create(ResourceLocation skin, Matrix4f pose,
                                            int x, int y, int size) {
        BufferBuilder builder = GuiQuadBatchRenderer.begin();
        PlayerFaceBatchRenderer.addFace(builder, pose, x, y, size, 1.0F);
        MeshData mesh = builder.build();
        if (mesh == null) {
            throw new IllegalStateException("Player face produced no retained geometry");
        }

        VertexBuffer vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        try {
            vertexBuffer.bind();
            vertexBuffer.upload(mesh);
            return new RetainedPlayerFace(skin, vertexBuffer);
        } catch (Throwable throwable) {
            vertexBuffer.close();
            throw throwable;
        } finally {
            VertexBuffer.unbind();
        }
    }

    public void draw() {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, skin);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        try {
            ShaderInstance shader = RenderSystem.getShader();
            if (shader == null) {
                throw new IllegalStateException("Player-face shader is unavailable");
            }
            vertexBuffer.bind();
            vertexBuffer.drawWithShader(
                    RenderSystem.getModelViewMatrix(),
                    RenderSystem.getProjectionMatrix(), shader);
        } finally {
            VertexBuffer.unbind();
            RenderSystem.disableBlend();
        }
    }

    @Override
    public void close() {
        vertexBuffer.close();
    }
}
