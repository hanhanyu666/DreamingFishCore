package com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retains the baked GPU geometry for the stable hotbar item layer.
 *
 * <p>Count and durability geometry is retained, but their driving values are
 * checked every frame and rebuild the cache immediately when they change.
 * Cooldown masks and third-party decorators remain live. Full component maps
 * and item-property overrides are checked at tick cadence, while custom item
 * renderers remain immediate so animations and side effects keep their normal
 * per-frame behaviour.</p>
 */
final class HotbarItemRenderCache {
    private static final long DYNAMIC_MODEL_CHECK_NANOS = 50_000_000L;
    private static final long STACK_COMPONENT_CHECK_NANOS = 50_000_000L;
    private static final int CAPTURE_BUFFER_INITIAL_BYTES = 4096;

    private final ItemStack[] cachedStacks;
    private final BakedModel[] cachedModels;
    private final BakedModel[] immediateModels;
    private final boolean[] dynamicModels;
    private final int[] cachedX;
    private final int[] cachedY;

    private List<CachedRenderBuffer> flatBuffers = List.of();
    private List<CachedRenderBuffer> blockLitBuffers = List.of();
    private List<CachedRenderBuffer> decorationBuffers = List.of();
    private int cachedCount = -1;
    private float cachedScale = Float.NaN;
    private boolean cachedShaderTransparency;
    private Object cachedLevel;
    private final Matrix4f cachedGuiPose = new Matrix4f();
    private boolean hasCachedGuiPose;
    private long nextDynamicModelCheckNanos = Long.MAX_VALUE;
    private long nextStackComponentCheckNanos = Long.MIN_VALUE;
    private boolean dirty = true;

    HotbarItemRenderCache(int capacity) {
        this.cachedStacks = new ItemStack[capacity];
        this.cachedModels = new BakedModel[capacity];
        this.immediateModels = new BakedModel[capacity];
        this.dynamicModels = new boolean[capacity];
        this.cachedX = new int[capacity];
        this.cachedY = new int[capacity];
    }

    void invalidate() {
        // Model-bake callbacks are not guaranteed to be the render point at
        // which an existing VBO should be deleted. Defer disposal until the
        // next hotbar render, which always runs on the render thread.
        dirty = true;
    }

    boolean renderModels(GuiGraphics graphics, Minecraft minecraft, Player player,
                         ItemStack[] stacks, int[] itemX, int[] itemY, int count,
                         float scale, boolean cacheableScale) {
        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        try {
            if (!cacheableScale) {
                resolveModels(itemRenderer, minecraft, player, stacks, count, immediateModels);
                renderImmediate(graphics, itemRenderer, stacks, itemX, itemY,
                        immediateModels, count, false);
                return false;
            }

            long now = System.nanoTime();
            if (!isValid(graphics, itemRenderer, minecraft, player, stacks, itemX, itemY,
                    count, scale, now)) {
                rebuild(graphics, itemRenderer, minecraft, player,
                        stacks, itemX, itemY, count, scale, now);
            }

            if (!flatBuffers.isEmpty() || !blockLitBuffers.isEmpty()
                    || !decorationBuffers.isEmpty()) {
                // Submit the panel behind the items before switching to the
                // retained VBOs. Only the GUI render type is pending here;
                // avoid walking every fixed BufferSource type at 1000+ FPS.
                flushGuiLayer(graphics);
                if (!flatBuffers.isEmpty() || !blockLitBuffers.isEmpty()) {
                    drawCachedModelBuffers();
                }
            }

            // A BlockEntityWithoutLevelRenderer can perform arbitrary dynamic
            // work, so those entries are intentionally not recorded in VBOs.
            renderImmediate(graphics, itemRenderer, stacks, itemX, itemY,
                    cachedModels, count, true);
            if (!decorationBuffers.isEmpty()) {
                drawBuffersWithDepthDisabled(decorationBuffers);
            }
            return true;
        } finally {
            Lighting.setupFor3DItems();
        }
    }

    private boolean isValid(GuiGraphics graphics, ItemRenderer itemRenderer,
                            Minecraft minecraft, Player player,
                            ItemStack[] stacks, int[] itemX, int[] itemY,
                            int count, float scale, long now) {
        if (dirty
                || cachedCount != count
                || Float.floatToIntBits(cachedScale) != Float.floatToIntBits(scale)
                || cachedShaderTransparency != Minecraft.useShaderTransparency()
                || cachedLevel != minecraft.level
                || !hasCachedGuiPose
                || !cachedGuiPose.equals(graphics.pose().last().pose())) {
            return false;
        }

        for (int index = 0; index < count; index++) {
            if (cachedX[index] != itemX[index]
                    || cachedY[index] != itemY[index]) {
                return false;
            }

            ItemStack cachedStack = cachedStacks[index];
            ItemStack currentStack = stacks[index];
            // These values drive the visible count and durability overlay, so
            // keep them frame-accurate without paying for a full component-map
            // comparison for every occupied slot at 1000+ FPS.
            if (cachedStack == null
                    || cachedStack.getItem() != currentStack.getItem()
                    || cachedStack.getCount() != currentStack.getCount()
                    || cachedStack.getDamageValue() != currentStack.getDamageValue()) {
                return false;
            }
        }

        if (now >= nextStackComponentCheckNanos) {
            nextStackComponentCheckNanos = now + STACK_COMPONENT_CHECK_NANOS;
            for (int index = 0; index < count; index++) {
                if (!ItemStack.matches(cachedStacks[index], stacks[index])) {
                    return false;
                }
            }
        }

        if (now < nextDynamicModelCheckNanos) {
            return true;
        }

        nextDynamicModelCheckNanos = now + DYNAMIC_MODEL_CHECK_NANOS;
        for (int index = 0; index < count; index++) {
            if (dynamicModels[index]
                    && itemRenderer.getModel(stacks[index], minecraft.level, player, 0)
                    != cachedModels[index]) {
                return false;
            }
        }
        return true;
    }

    private void rebuild(GuiGraphics graphics, ItemRenderer itemRenderer,
                         Minecraft minecraft, Player player,
                         ItemStack[] stacks, int[] itemX, int[] itemY,
                         int count, float scale, long now) {
        CapturingBufferSource flatCapture = new CapturingBufferSource();
        CapturingBufferSource blockLitCapture = new CapturingBufferSource();
        CapturingBufferSource decorationCapture = new CapturingBufferSource();
        List<CachedRenderBuffer> newFlatBuffers = List.of();
        List<CachedRenderBuffer> newBlockLitBuffers = List.of();
        List<CachedRenderBuffer> newDecorationBuffers = List.of();
        boolean hasDynamicModels = false;

        try {
            resolveModels(itemRenderer, minecraft, player, stacks, count, cachedModels);
            for (int index = 0; index < count; index++) {
                ItemStack stack = stacks[index];
                BakedModel model = cachedModels[index];
                BakedModel baseModel = itemRenderer.getItemModelShaper().getItemModel(stack);
                ItemOverrides overrides = baseModel.getOverrides();
                boolean dynamic = model.isCustomRenderer() || !overrides.getOverrides().isEmpty();
                dynamicModels[index] = dynamic;
                hasDynamicModels |= dynamic;

                if (model.isCustomRenderer()) {
                    captureStaticDecorations(minecraft.font, stack, itemX[index], itemY[index],
                            graphics.pose().last().pose(), decorationCapture);
                    continue;
                }

                PoseStack itemPose = new PoseStack();
                itemPose.mulPose(graphics.pose().last().pose());
                itemPose.translate(itemX[index] + 8.0F, itemY[index] + 8.0F, 150.0F);
                itemPose.scale(16.0F, -16.0F, 16.0F);
                MultiBufferSource target = model.usesBlockLight()
                        ? blockLitCapture
                        : flatCapture;
                itemRenderer.render(stack, ItemDisplayContext.GUI, false,
                        itemPose, target, 15728880, OverlayTexture.NO_OVERLAY,
                        HotbarOutlineFilteredModel.forHotbar(model));
                captureStaticDecorations(minecraft.font, stack, itemX[index], itemY[index],
                        graphics.pose().last().pose(), decorationCapture);
            }

            newFlatBuffers = flatCapture.upload();
            newBlockLitBuffers = blockLitCapture.upload();
            newDecorationBuffers = decorationCapture.upload();
        } catch (Throwable throwable) {
            closeBuffers(newFlatBuffers);
            closeBuffers(newBlockLitBuffers);
            closeBuffers(newDecorationBuffers);
            throw throwable;
        } finally {
            flatCapture.close();
            blockLitCapture.close();
            decorationCapture.close();
        }

        closeBuffers(flatBuffers);
        closeBuffers(blockLitBuffers);
        closeBuffers(decorationBuffers);
        flatBuffers = newFlatBuffers;
        blockLitBuffers = newBlockLitBuffers;
        decorationBuffers = newDecorationBuffers;

        for (int index = 0; index < count; index++) {
            cachedStacks[index] = stacks[index].copy();
            cachedX[index] = itemX[index];
            cachedY[index] = itemY[index];
        }
        for (int index = count; index < cachedCount; index++) {
            cachedStacks[index] = null;
            cachedModels[index] = null;
            dynamicModels[index] = false;
        }

        cachedCount = count;
        cachedScale = scale;
        cachedShaderTransparency = Minecraft.useShaderTransparency();
        cachedLevel = minecraft.level;
        cachedGuiPose.set(graphics.pose().last().pose());
        hasCachedGuiPose = true;
        nextDynamicModelCheckNanos = hasDynamicModels
                ? now + DYNAMIC_MODEL_CHECK_NANOS
                : Long.MAX_VALUE;
        nextStackComponentCheckNanos = now + STACK_COMPONENT_CHECK_NANOS;
        dirty = false;
    }

    private static void resolveModels(ItemRenderer itemRenderer, Minecraft minecraft,
                                      Player player, ItemStack[] stacks, int count,
                                      BakedModel[] destination) {
        for (int index = 0; index < count; index++) {
            destination[index] = itemRenderer.getModel(
                    stacks[index], minecraft.level, player, 0);
        }
    }

    private static void captureStaticDecorations(Font font, ItemStack stack, int x, int y,
                                                 Matrix4f guiPose,
                                                 CapturingBufferSource decorationCapture) {
        boolean hasCount = stack.getCount() != 1;
        boolean hasBar = stack.isBarVisible();
        if (!hasCount && !hasBar) {
            return;
        }

        Matrix4f pose = new Matrix4f(guiPose);
        if (hasCount) {
            String countText = Integer.toString(stack.getCount());
            pose.translate(0.0F, 0.0F, 200.0F);
            font.drawInBatch(countText,
                    x + 17 - font.width(countText), y + 9,
                    0xFFFFFFFF, true, pose, decorationCapture,
                    Font.DisplayMode.NORMAL, 0, 15728880);
        }

        if (hasBar) {
            int barX = x + 2;
            int barY = y + 13;
            addGuiQuad(decorationCapture, pose,
                    barX, barY, barX + 13, barY + 2, 0xFF000000);
            addGuiQuad(decorationCapture, pose,
                    barX, barY, barX + stack.getBarWidth(), barY + 1,
                    stack.getBarColor() | 0xFF000000);
        }
    }

    private static void addGuiQuad(MultiBufferSource target, Matrix4f pose,
                                   int minX, int minY, int maxX, int maxY, int color) {
        VertexConsumer consumer = target.getBuffer(RenderType.guiOverlay());
        // Keep GuiGraphics.fill()'s winding: guiOverlay retains face culling.
        consumer.addVertex(pose, maxX, maxY, 0.0F).setColor(color);
        consumer.addVertex(pose, maxX, minY, 0.0F).setColor(color);
        consumer.addVertex(pose, minX, minY, 0.0F).setColor(color);
        consumer.addVertex(pose, minX, maxY, 0.0F).setColor(color);
    }

    private static void renderImmediate(GuiGraphics graphics, ItemRenderer itemRenderer,
                                        ItemStack[] stacks, int[] itemX, int[] itemY,
                                        BakedModel[] models, int count, boolean customOnly) {
        renderImmediatePass(graphics, itemRenderer, stacks, itemX, itemY,
                models, count, true, customOnly);
        renderImmediatePass(graphics, itemRenderer, stacks, itemX, itemY,
                models, count, false, customOnly);
    }

    private static void renderImmediatePass(GuiGraphics graphics, ItemRenderer itemRenderer,
                                            ItemStack[] stacks, int[] itemX, int[] itemY,
                                            BakedModel[] models, int count,
                                            boolean flatLit, boolean customOnly) {
        boolean hasItems = false;
        for (int index = 0; index < count; index++) {
            BakedModel model = models[index];
            if (customOnly && !model.isCustomRenderer()) {
                continue;
            }
            if ((!model.usesBlockLight()) != flatLit) {
                continue;
            }

            if (!hasItems) {
                if (flatLit) {
                    Lighting.setupForFlatItems();
                } else {
                    Lighting.setupFor3DItems();
                }
                hasItems = true;
            }

            graphics.pose().pushPose();
            try {
                graphics.pose().translate(itemX[index] + 8.0F,
                        itemY[index] + 8.0F, 150.0F);
                graphics.pose().scale(16.0F, -16.0F, 16.0F);
                itemRenderer.render(stacks[index], ItemDisplayContext.GUI, false,
                        graphics.pose(), graphics.bufferSource(), 15728880,
                        OverlayTexture.NO_OVERLAY, HotbarOutlineFilteredModel.forHotbar(model));
            } finally {
                graphics.pose().popPose();
            }
        }

        if (hasItems) {
            graphics.flush();
        }
    }

    private static void flushGuiLayer(GuiGraphics graphics) {
        RenderSystem.disableDepthTest();
        try {
            graphics.bufferSource().endBatch(RenderType.gui());
        } finally {
            RenderSystem.enableDepthTest();
        }
    }

    private void drawCachedModelBuffers() {
        RenderSystem.disableDepthTest();
        try {
            if (!flatBuffers.isEmpty()) {
                Lighting.setupForFlatItems();
                drawBuffers(flatBuffers);
            }
            if (!blockLitBuffers.isEmpty()) {
                Lighting.setupFor3DItems();
                drawBuffers(blockLitBuffers);
            }
        } finally {
            VertexBuffer.unbind();
            RenderSystem.enableDepthTest();
        }
    }

    private static void drawBuffersWithDepthDisabled(List<CachedRenderBuffer> buffers) {
        RenderSystem.disableDepthTest();
        try {
            drawBuffers(buffers);
        } finally {
            VertexBuffer.unbind();
            RenderSystem.enableDepthTest();
        }
    }

    private static void drawBuffers(List<CachedRenderBuffer> buffers) {
        for (CachedRenderBuffer buffer : buffers) {
            RenderType renderType = buffer.renderType();
            renderType.setupRenderState();
            try {
                ShaderInstance shader = RenderSystem.getShader();
                if (shader == null) {
                    throw new IllegalStateException("Render type did not select a shader: " + renderType);
                }
                buffer.vertexBuffer().bind();
                buffer.vertexBuffer().drawWithShader(
                        RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
            } finally {
                renderType.clearRenderState();
            }
        }
    }

    private static void closeBuffers(List<CachedRenderBuffer> buffers) {
        for (CachedRenderBuffer buffer : buffers) {
            buffer.vertexBuffer().close();
        }
    }

    private static boolean isGlint(RenderType renderType) {
        return renderType.equals(RenderType.armorEntityGlint())
                || renderType.equals(RenderType.glint())
                || renderType.equals(RenderType.glintTranslucent())
                || renderType.equals(RenderType.entityGlint())
                || renderType.equals(RenderType.entityGlintDirect());
    }

    private record CachedRenderBuffer(RenderType renderType, VertexBuffer vertexBuffer) {
    }

    /** Collects ItemRenderer output without issuing a draw call. */
    private static final class CapturingBufferSource implements MultiBufferSource, AutoCloseable {
        private final Map<RenderType, CaptureBuilder> builders = new LinkedHashMap<>();

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return builders.computeIfAbsent(renderType, CaptureBuilder::new).builder();
        }

        List<CachedRenderBuffer> upload() {
            List<CachedRenderBuffer> result = new ArrayList<>(builders.size());
            try {
                for (Map.Entry<RenderType, CaptureBuilder> entry : builders.entrySet()) {
                    RenderType renderType = entry.getKey();
                    CaptureBuilder capture = entry.getValue();
                    MeshData mesh = capture.builder().build();
                    if (mesh == null) {
                        continue;
                    }
                    if (renderType.sortOnUpload()) {
                        mesh.sortQuads(capture.storage(), RenderSystem.getVertexSorting());
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
                    result.add(new CachedRenderBuffer(renderType, vertexBuffer));
                }
            } catch (Throwable throwable) {
                closeBuffers(result);
                throw throwable;
            }

            // Match the normal GUI BufferSource: ordinary/sheet item layers are
            // submitted before its fixed glint buffers.
            result.sort(Comparator.comparingInt(buffer -> isGlint(buffer.renderType()) ? 1 : 0));
            return List.copyOf(result);
        }

        @Override
        public void close() {
            for (CaptureBuilder builder : builders.values()) {
                builder.storage().close();
            }
            builders.clear();
        }
    }

    private static final class CaptureBuilder {
        private final ByteBufferBuilder storage;
        private final BufferBuilder builder;

        private CaptureBuilder(RenderType renderType) {
            storage = new ByteBufferBuilder(Math.max(CAPTURE_BUFFER_INITIAL_BYTES,
                    Math.min(16_384, renderType.bufferSize())));
            // A compact native buffer is sufficient for ten GUI items and can
            // still grow automatically for a modded multi-pass model.
            builder = new BufferBuilder(storage, renderType.mode(), renderType.format());
        }

        private ByteBufferBuilder storage() {
            return storage;
        }

        private BufferBuilder builder() {
            return builder;
        }
    }
}
