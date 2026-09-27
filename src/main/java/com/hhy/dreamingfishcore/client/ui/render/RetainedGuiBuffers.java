package com.hhy.dreamingfishcore.client.ui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Retains static GUI/font geometry in GPU vertex buffers. */
public final class RetainedGuiBuffers implements AutoCloseable {
    private static final int INITIAL_BUFFER_BYTES = 4096;

    private final List<Entry> entries;

    private RetainedGuiBuffers(List<Entry> entries) {
        this.entries = entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void draw() {
        try {
            for (Entry entry : entries) {
                RenderType renderType = entry.renderType();
                renderType.setupRenderState();
                try {
                    ShaderInstance shader = RenderSystem.getShader();
                    if (shader == null) {
                        throw new IllegalStateException(
                                "Render type did not select a shader: " + renderType);
                    }
                    entry.vertexBuffer().bind();
                    entry.vertexBuffer().drawWithShader(
                            RenderSystem.getModelViewMatrix(),
                            RenderSystem.getProjectionMatrix(), shader);
                } finally {
                    renderType.clearRenderState();
                }
            }
        } finally {
            VertexBuffer.unbind();
        }
    }

    @Override
    public void close() {
        for (Entry entry : entries) {
            entry.vertexBuffer().close();
        }
    }

    private record Entry(RenderType renderType, VertexBuffer vertexBuffer) {
    }

    public static final class Capture implements MultiBufferSource, AutoCloseable {
        private final Map<RenderType, CaptureBuilder> builders = new LinkedHashMap<>();
        private boolean uploaded;

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            if (uploaded) {
                throw new IllegalStateException("GUI capture was already uploaded");
            }
            return builders.computeIfAbsent(renderType, CaptureBuilder::new).builder;
        }

        public RetainedGuiBuffers upload() {
            if (uploaded) {
                throw new IllegalStateException("GUI capture was already uploaded");
            }
            uploaded = true;
            List<Entry> result = new ArrayList<>(builders.size());
            try {
                for (Map.Entry<RenderType, CaptureBuilder> builderEntry : builders.entrySet()) {
                    RenderType renderType = builderEntry.getKey();
                    CaptureBuilder capture = builderEntry.getValue();
                    MeshData mesh = capture.builder.build();
                    if (mesh == null) {
                        continue;
                    }
                    if (renderType.sortOnUpload()) {
                        mesh.sortQuads(capture.storage, RenderSystem.getVertexSorting());
                    }

                    VertexBuffer vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    try {
                        vertexBuffer.bind();
                        vertexBuffer.upload(mesh);
                    } catch (Throwable throwable) {
                        vertexBuffer.close();
                        throw throwable;
                    } finally {
                        VertexBuffer.unbind();
                    }
                    result.add(new Entry(renderType, vertexBuffer));
                }
                return new RetainedGuiBuffers(List.copyOf(result));
            } catch (Throwable throwable) {
                for (Entry entry : result) {
                    entry.vertexBuffer().close();
                }
                throw throwable;
            }
        }

        @Override
        public void close() {
            for (CaptureBuilder builder : builders.values()) {
                builder.storage.close();
            }
            builders.clear();
        }
    }

    private static final class CaptureBuilder {
        private final ByteBufferBuilder storage;
        private final BufferBuilder builder;

        private CaptureBuilder(RenderType renderType) {
            storage = new ByteBufferBuilder(Math.max(INITIAL_BUFFER_BYTES,
                    Math.min(65_536, renderType.bufferSize())));
            builder = new BufferBuilder(storage, renderType.mode(), renderType.format());
        }
    }
}
