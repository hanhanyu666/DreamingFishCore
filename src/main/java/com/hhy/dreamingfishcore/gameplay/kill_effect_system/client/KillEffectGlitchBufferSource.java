package com.hhy.dreamingfishcore.gameplay.kill_effect_system.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits entity quads into horizontal bands immediately before they reach the GPU.
 * Each band can then be displaced or repeated as a colored channel without touching entity state.
 */
public final class KillEffectGlitchBufferSource implements MultiBufferSource {
    private static final int TARGET_BAND_COUNT = 12;
    private static final int MAX_BANDS_PER_QUAD = 18;
    private static final float EPSILON = 0.0001F;

    private final MultiBufferSource delegate;
    private final EffectParameters effect;

    private KillEffectGlitchBufferSource(MultiBufferSource delegate, EffectParameters effect) {
        this.delegate = delegate;
        this.effect = effect;
    }

    public static MultiBufferSource wrap(MultiBufferSource delegate,
                                         KillEffectClientState.Snapshot snapshot,
                                         float elapsedTicks,
                                         float originX,
                                         float originY,
                                         float originZ,
                                         float cameraLeftX,
                                         float cameraLeftZ) {
        float leftLength = (float) Math.sqrt(cameraLeftX * cameraLeftX + cameraLeftZ * cameraLeftZ);
        if (!Float.isFinite(leftLength) || leftLength < EPSILON) {
            cameraLeftX = 1.0F;
            cameraLeftZ = 0.0F;
        } else {
            cameraLeftX /= leftLength;
            cameraLeftZ /= leftLength;
        }

        EffectParameters parameters = new EffectParameters(
                snapshot.seed(),
                originX,
                originY,
                originZ,
                Math.max(0.05F, snapshot.width()),
                Math.max(0.05F, snapshot.height()),
                cameraLeftX,
                cameraLeftZ,
                elapsedTicks,
                snapshot.durationTicks());
        return new KillEffectGlitchBufferSource(delegate, parameters);
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        VertexConsumer original = delegate.getBuffer(renderType);
        if (renderType.mode != VertexFormat.Mode.QUADS
                || renderType.format != DefaultVertexFormat.NEW_ENTITY) {
            return original;
        }
        return new SlicedVertexConsumer(original, effect);
    }

    private static final class SlicedVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final EffectParameters effect;
        private final List<GlitchVertex> quad = new ArrayList<>(4);
        private GlitchVertex current;

        private SlicedVertexConsumer(VertexConsumer delegate, EffectParameters effect) {
            this.delegate = delegate;
            this.effect = effect;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            finishCurrentVertex();
            current = new GlitchVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            if (current != null) {
                current.red = clampColor(red);
                current.green = clampColor(green);
                current.blue = clampColor(blue);
                current.alpha = clampColor(alpha);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (current != null) {
                current.u = u;
                current.v = v;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            if (current != null) {
                current.overlayU = u;
                current.overlayV = v;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            if (current != null) {
                current.lightU = u;
                current.lightV = v;
            }
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            if (current != null) {
                current.normalX = normalX;
                current.normalY = normalY;
                current.normalZ = normalZ;
            }
            finishCurrentVertex();
            return this;
        }

        private void finishCurrentVertex() {
            if (current == null) {
                return;
            }
            quad.add(current);
            current = null;
            if (quad.size() == 4) {
                emitSlicedQuad(quad);
                quad.clear();
            }
        }

        private void emitSlicedQuad(List<GlitchVertex> sourceQuad) {
            float minY = Float.POSITIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            for (GlitchVertex vertex : sourceQuad) {
                minY = Math.min(minY, vertex.y);
                maxY = Math.max(maxY, vertex.y);
            }

            float bandHeight = effect.bandHeight();
            int firstBand = (int) Math.floor((minY - effect.originY()) / bandHeight);
            int lastBand = maxY - minY < EPSILON
                    ? firstBand
                    : (int) Math.floor((maxY - effect.originY() - EPSILON) / bandHeight);
            firstBand = Math.max(-2, firstBand);
            lastBand = Math.min(TARGET_BAND_COUNT + 2, lastBand);
            if (lastBand < firstBand || lastBand - firstBand + 1 > MAX_BANDS_PER_QUAD) {
                emitPolygon(sourceQuad, effect.transformFor(firstBand));
                return;
            }

            for (int band = firstBand; band <= lastBand; band++) {
                float lower = effect.originY() + band * bandHeight;
                float upper = lower + bandHeight;
                List<GlitchVertex> clipped = clipAtY(sourceQuad, lower, true);
                clipped = clipAtY(clipped, upper, false);
                if (clipped.size() >= 3) {
                    emitPolygon(clipped, effect.transformFor(band));
                }
            }
        }

        private void emitPolygon(List<GlitchVertex> polygon, BandTransform transform) {
            if (!transform.mainVisible() && !transform.channelsVisible()) {
                return;
            }
            GlitchVertex first = polygon.getFirst();
            for (int index = 1; index < polygon.size() - 1; index++) {
                GlitchVertex second = polygon.get(index);
                GlitchVertex third = polygon.get(index + 1);
                if (transform.channelsVisible()) {
                    emitTriangle(first, second, third,
                            transform.magentaX(), transform.magentaY(), transform.magentaZ(),
                            transform.scale(), 1.0F, 0.035F, 0.72F, 0.78F, true);
                    emitTriangle(first, second, third,
                            transform.cyanX(), transform.cyanY(), transform.cyanZ(),
                            transform.scale(), 0.035F, 0.92F, 1.0F, 0.78F, true);
                }
                if (transform.mainVisible()) {
                    float coldTint = transform.scanFlash() ? 0.72F : 1.0F;
                    emitTriangle(first, second, third,
                            transform.mainX(), transform.mainY(), transform.mainZ(),
                            transform.scale(), coldTint, 1.0F, 1.0F, 1.0F, transform.scanFlash());
                }
            }
        }

        private void emitTriangle(GlitchVertex first, GlitchVertex second, GlitchVertex third,
                                   float offsetX, float offsetY, float offsetZ,
                                   float scale,
                                   float tintRed, float tintGreen, float tintBlue, float alpha,
                                   boolean fullBright) {
            emitVertex(first, offsetX, offsetY, offsetZ, scale,
                    tintRed, tintGreen, tintBlue, alpha, fullBright);
            emitVertex(second, offsetX, offsetY, offsetZ, scale,
                    tintRed, tintGreen, tintBlue, alpha, fullBright);
            emitVertex(third, offsetX, offsetY, offsetZ, scale,
                    tintRed, tintGreen, tintBlue, alpha, fullBright);
            emitVertex(third, offsetX, offsetY, offsetZ, scale,
                    tintRed, tintGreen, tintBlue, alpha, fullBright);
        }

        private void emitVertex(GlitchVertex vertex,
                                float offsetX, float offsetY, float offsetZ,
                                float scale,
                                float tintRed, float tintGreen, float tintBlue, float alpha,
                                boolean fullBright) {
            float scaledX = effect.originX() + (vertex.x - effect.originX()) * scale;
            float scaledZ = effect.originZ() + (vertex.z - effect.originZ()) * scale;
            delegate.addVertex(scaledX + offsetX, vertex.y + offsetY, scaledZ + offsetZ)
                    .setColor(
                            multiplyColor(vertex.red, tintRed),
                            multiplyColor(vertex.green, tintGreen),
                            multiplyColor(vertex.blue, tintBlue),
                            multiplyColor(vertex.alpha, alpha))
                    .setUv(vertex.u, vertex.v)
                    .setUv1(vertex.overlayU, vertex.overlayV)
                    .setUv2(fullBright ? 240 : vertex.lightU, fullBright ? 240 : vertex.lightV)
                    .setNormal(vertex.normalX, vertex.normalY, vertex.normalZ);
        }
    }

    private record EffectParameters(long seed, float originX, float originY, float originZ,
                                    float width, float height,
                                    float leftX, float leftZ, float elapsedTicks,
                                    int durationTicks) {
        private float bandHeight() {
            return Math.max(0.025F, height / TARGET_BAND_COUNT);
        }

        private BandTransform transformFor(int band) {
            float progress = clamp(elapsedTicks / Math.max(1.0F, durationTicks), 0.0F, 1.0F);
            int frame = Math.max(0, (int) Math.floor(elapsedTicks * 1.75F));
            float temporal = unitNoise(seed, frame, band, 101);
            float stable = unitNoise(seed, 0, band, 102);
            float signed = unitNoise(seed, frame, band, 103) * 2.0F - 1.0F;
            float pulse = 0.72F + unitNoise(seed, frame, -1, 104) * 0.92F;
            float lock = 1.0F - smoothstep(0.0F, 0.13F, progress);
            float slicing = smoothstep(0.08F, 0.38F, progress);
            float loss = smoothstep(0.30F, 0.78F, progress);
            float collapse = smoothstep(0.66F, 0.96F, progress);
            float endFade = 1.0F - smoothstep(0.97F, 1.0F, progress);
            float strength = endFade * (0.42F + slicing * 0.58F);
            float amplitude = width * (0.012F + 0.20F * slicing + 0.25F * loss) * pulse;
            float gate = temporal < (0.12F + loss * 0.16F)
                    ? 0.0F
                    : 0.18F + temporal * 0.82F;
            float mainHorizontal = signed * amplitude * gate;
            float channelSeparation = width * (0.035F + 0.13F * slicing + 0.12F * loss)
                    * (0.72F + unitNoise(seed, frame, band, 105) * 0.55F);
            float vertical = (unitNoise(seed, frame, band, 106) - 0.5F)
                    * height * (0.008F + 0.028F * slicing);
            float depth = (unitNoise(seed, frame, band, 107) - 0.5F)
                    * width * (0.018F + 0.045F * slicing);

            float normalizedBand = clamp((band + 0.5F) / TARGET_BAND_COUNT, 0.0F, 1.0F);
            float dissolveFront = smoothstep(0.30F, 0.78F, progress) * 1.16F - 0.10F;
            float raggedEdge = (stable - 0.5F) * 0.30F
                    + (unitNoise(seed, frame, band, 108) - 0.5F) * 0.10F;
            boolean transientDrop = progress > 0.16F && temporal > 0.91F;
            boolean mainVisible = normalizedBand + raggedEdge > dissolveFront && !transientDrop;
            boolean channelsVisible = strength > 0.02F
                    && temporal > 0.18F
                    && normalizedBand + raggedEdge * 0.7F > dissolveFront - 0.18F;
            boolean scanFlash = temporal > 0.86F || lock > 0.15F && temporal > 0.42F;
            float scale = 1.0F - collapse * 0.90F;

            float mainX = leftX * mainHorizontal;
            float mainZ = leftZ * mainHorizontal;
            float cyanHorizontal = mainHorizontal + channelSeparation;
            float magentaHorizontal = mainHorizontal - channelSeparation;
            return new BandTransform(
                    mainVisible,
                    channelsVisible,
                    scanFlash,
                    scale,
                    mainX,
                    vertical * 0.45F,
                    mainZ + depth * 0.30F,
                    leftX * cyanHorizontal,
                    vertical,
                    leftZ * cyanHorizontal + depth,
                    leftX * magentaHorizontal,
                    -vertical * 0.72F,
                    leftZ * magentaHorizontal - depth);
        }
    }

    private record BandTransform(boolean mainVisible, boolean channelsVisible, boolean scanFlash,
                                 float scale,
                                 float mainX, float mainY, float mainZ,
                                 float cyanX, float cyanY, float cyanZ,
                                 float magentaX, float magentaY, float magentaZ) {
    }

    private static final class GlitchVertex {
        private final float x;
        private final float y;
        private final float z;
        private int red = 255;
        private int green = 255;
        private int blue = 255;
        private int alpha = 255;
        private float u;
        private float v;
        private int overlayU;
        private int overlayV;
        private int lightU;
        private int lightV;
        private float normalX;
        private float normalY = 1.0F;
        private float normalZ;

        private GlitchVertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private GlitchVertex interpolate(GlitchVertex other, float amount) {
            float value = clamp(amount, 0.0F, 1.0F);
            GlitchVertex result = new GlitchVertex(
                    lerp(value, x, other.x),
                    lerp(value, y, other.y),
                    lerp(value, z, other.z));
            result.red = Math.round(lerp(value, red, other.red));
            result.green = Math.round(lerp(value, green, other.green));
            result.blue = Math.round(lerp(value, blue, other.blue));
            result.alpha = Math.round(lerp(value, alpha, other.alpha));
            result.u = lerp(value, u, other.u);
            result.v = lerp(value, v, other.v);
            result.overlayU = Math.round(lerp(value, overlayU, other.overlayU));
            result.overlayV = Math.round(lerp(value, overlayV, other.overlayV));
            result.lightU = Math.round(lerp(value, lightU, other.lightU));
            result.lightV = Math.round(lerp(value, lightV, other.lightV));
            result.normalX = lerp(value, normalX, other.normalX);
            result.normalY = lerp(value, normalY, other.normalY);
            result.normalZ = lerp(value, normalZ, other.normalZ);
            return result;
        }
    }

    private static List<GlitchVertex> clipAtY(List<GlitchVertex> input,
                                               float boundary,
                                               boolean keepAbove) {
        if (input.isEmpty()) {
            return List.of();
        }
        List<GlitchVertex> output = new ArrayList<>(input.size() + 2);
        GlitchVertex previous = input.getLast();
        boolean previousInside = isInside(previous.y, boundary, keepAbove);
        for (GlitchVertex current : input) {
            boolean currentInside = isInside(current.y, boundary, keepAbove);
            if (currentInside != previousInside) {
                float denominator = current.y - previous.y;
                float amount = Math.abs(denominator) < EPSILON
                        ? 0.0F
                        : (boundary - previous.y) / denominator;
                output.add(previous.interpolate(current, amount));
            }
            if (currentInside) {
                output.add(current);
            }
            previous = current;
            previousInside = currentInside;
        }
        return output;
    }

    private static boolean isInside(float y, float boundary, boolean keepAbove) {
        return keepAbove ? y >= boundary - EPSILON : y <= boundary + EPSILON;
    }

    private static int multiplyColor(int color, float multiplier) {
        return clampColor(Math.round(color * clamp(multiplier, 0.0F, 1.0F)));
    }

    private static int clampColor(int color) {
        return Math.max(0, Math.min(255, color));
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float normalized = clamp((value - edge0) / Math.max(EPSILON, edge1 - edge0), 0.0F, 1.0F);
        return normalized * normalized * (3.0F - 2.0F * normalized);
    }

    private static float unitNoise(long seed, int frame, int band, int salt) {
        long value = seed + 0x9E3779B97F4A7C15L * (long) (band + 17);
        value ^= 0xD1B54A32D192ED03L * (long) (frame + 31);
        value += 0x94D049BB133111EBL * (long) (salt + 1);
        value = mix64(value);
        return (float) ((value >>> 40) * (1.0D / 16_777_216.0D));
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static float lerp(float amount, float start, float end) {
        return start + amount * (end - start);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
