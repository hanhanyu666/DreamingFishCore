package com.hhy.dreamingfishcore.client.ui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Small immediate batch used at the boundaries between buffered GUI geometry
 * and textured HUD layers.
 *
 * <p>Minecraft 1.21.1's {@code GuiGraphics#blit} path builds and submits one
 * mesh per call. HUDs that draw several sprites from the same atlas can use
 * this helper to submit all of their quads together.</p>
 */
public final class GuiQuadBatchRenderer {
    private GuiQuadBatchRenderer() {
    }

    public static BufferBuilder begin() {
        return Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    public static void addSprite(BufferBuilder builder, Matrix4f pose,
                                 TextureAtlasSprite sprite,
                                 float x, float y, float width, float height,
                                 float alpha) {
        add(builder, pose, x, y, width, height,
                sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), alpha);
    }

    public static void add(BufferBuilder builder, Matrix4f pose,
                           float x, float y, float width, float height,
                           float minU, float minV, float maxU, float maxV,
                           float alpha) {
        float clampedAlpha = Math.max(0.0F, Math.min(1.0F, alpha));
        float right = x + width;
        float bottom = y + height;
        builder.addVertex(pose, x, y, 0.0F).setUv(minU, minV)
                .setColor(1.0F, 1.0F, 1.0F, clampedAlpha);
        builder.addVertex(pose, x, bottom, 0.0F).setUv(minU, maxV)
                .setColor(1.0F, 1.0F, 1.0F, clampedAlpha);
        builder.addVertex(pose, right, bottom, 0.0F).setUv(maxU, maxV)
                .setColor(1.0F, 1.0F, 1.0F, clampedAlpha);
        builder.addVertex(pose, right, y, 0.0F).setUv(maxU, minV)
                .setColor(1.0F, 1.0F, 1.0F, clampedAlpha);
    }

    public static void draw(BufferBuilder builder, ResourceLocation texture) {
        MeshData mesh = builder.build();
        if (mesh == null) {
            return;
        }

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        try {
            BufferUploader.drawWithShader(mesh);
        } finally {
            RenderSystem.disableBlend();
        }
    }
}
