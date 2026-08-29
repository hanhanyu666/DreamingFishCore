package com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud;

import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Draws a compact Minecraft-style player silhouette for the HUD.
 *
 * <p>The cuboids are projected to a small raster mask first. Only the outside boundary of the combined body is
 * rendered, preventing the many overlapping box edges from turning into an unreadable wire cage.</p>
 */
final class HudPlayerWireframeRenderer {
    private static final double MODEL_SCALE = 2.05D;
    private static final double MODEL_YAW = Math.toRadians(-24.0D);
    private static final double MODEL_PITCH = Math.toRadians(-6.0D);
    private static final long GEOMETRY_CACHE_STEP_MS = 33L;
    private static final int SURFACE_ALPHA = 0x42;
    private static final int OUTLINE_ALPHA = 0xEC;
    private static final int STRUCTURE_ALPHA = 0x76;
    private static final int LEG_SEAM_ALPHA = 0x5C;
    private static final int CONTRAST_HALO_COLOR = 0xA6000000;

    // With the fixed camera angle, these three faces cover the visible projection of a cuboid.
    private static final int[][] VISIBLE_FACES = {
            {0, 4, 6, 2},
            {2, 6, 7, 3},
            {4, 5, 7, 6}
    };

    private static CachedGeometry cachedGeometry;

    private HudPlayerWireframeRenderer() {
    }

    static void render(GuiGraphics graphics, Player player, int centerX, int footY,
                       int healthColor, boolean flashing) {
        double horizontalSpeed = player.getDeltaMovement().horizontalDistance();
        double movementStrength = Math.min(0.55D, horizontalSpeed * (player.isSprinting() ? 15.0D : 11.0D));
        long now = System.currentTimeMillis();
        long animationFrame = now / GEOMETRY_CACHE_STEP_MS;
        int movementKey = (int) Math.round(movementStrength * 1000.0D);
        CachedGeometry geometry = cachedGeometry;
        if (geometry == null
                || geometry.centerX() != centerX
                || geometry.footY() != footY
                || geometry.animationFrame() != animationFrame
                || geometry.movementKey() != movementKey) {
            geometry = buildGeometry(centerX, footY, movementKey / 1000.0D,
                    animationFrame * (double) GEOMETRY_CACHE_STEP_MS);
            cachedGeometry = geometry;
        }

        int activeColor = flashing ? blendColor(healthColor, 0xFFFFFFFF, 0.38D) : healthColor;
        int surfaceColor = withAlpha(darken(activeColor, 0.30D), SURFACE_ALPHA);
        int outlineColor = withAlpha(activeColor, OUTLINE_ALPHA);
        int structureColor = withAlpha(activeColor, STRUCTURE_ALPHA);
        int legSeamColor = withAlpha(activeColor, LEG_SEAM_ALPHA);
        geometry.mask().render(graphics, surfaceColor, CONTRAST_HALO_COLOR, outlineColor);

        // A couple of subdued seams retain the Minecraft model identity without rebuilding the wire cage.
        drawLine(graphics, geometry.head()[4], geometry.head()[5], structureColor);
        drawLine(graphics, geometry.body()[4], geometry.body()[5], structureColor);
        drawLine(graphics, geometry.seamTop(), geometry.seamBottom(), legSeamColor);
    }

    private static CachedGeometry buildGeometry(int centerX, int footY,
                                                double movementStrength, double animationTime) {
        List<ScreenPoint[]> faces = new ArrayList<>(18);
        double walkSwing = Math.sin(animationTime / 125.0D) * movementStrength;
        double idleSwing = Math.sin(animationTime / 760.0D) * 0.025D;

        ScreenPoint[] head = addCuboid(faces, centerX, footY, 0.0D, 23.0D, 0.0D,
                -4.0D, 0.0D, -4.0D, 4.0D, 8.0D, 4.0D, 0.0D, 0.0D);
        ScreenPoint[] body = addCuboid(faces, centerX, footY, 0.0D, 12.0D, 0.0D,
                -4.0D, 0.0D, -2.0D, 4.0D, 12.0D, 2.0D, 0.0D, 0.0D);
        addCuboid(faces, centerX, footY, -6.1D, 23.7D, 0.0D,
                -2.0D, -11.7D, -2.0D, 2.0D, 0.0D, 2.0D,
                walkSwing + idleSwing, -0.065D);
        addCuboid(faces, centerX, footY, 6.1D, 23.7D, 0.0D,
                -2.0D, -11.7D, -2.0D, 2.0D, 0.0D, 2.0D,
                -walkSwing - idleSwing, 0.065D);
        ScreenPoint[] leftLeg = addCuboid(faces, centerX, footY, -2.35D, 12.0D, 0.0D,
                -1.85D, -12.0D, -2.0D, 1.85D, 0.0D, 2.0D,
                -walkSwing, -0.022D);
        ScreenPoint[] rightLeg = addCuboid(faces, centerX, footY, 2.35D, 12.0D, 0.0D,
                -1.85D, -12.0D, -2.0D, 1.85D, 0.0D, 2.0D,
                walkSwing, 0.022D);

        SilhouetteMask mask = SilhouetteMask.from(faces);
        return new CachedGeometry(
                centerX,
                footY,
                (int) Math.round(movementStrength * 1000.0D),
                Math.round(animationTime / GEOMETRY_CACHE_STEP_MS),
                head,
                body,
                midpoint(leftLeg[7], rightLeg[6]),
                midpoint(leftLeg[5], rightLeg[4]),
                mask
        );
    }

    private static ScreenPoint midpoint(ScreenPoint a, ScreenPoint b) {
        return new ScreenPoint((a.x() + b.x()) * 0.5D, (a.y() + b.y()) * 0.5D);
    }

    private static int withAlpha(int color, int alpha) {
        return Math.max(0, Math.min(255, alpha)) << 24 | color & 0x00FFFFFF;
    }

    private static int darken(int color, double factor) {
        double clamped = Math.max(0.0D, Math.min(1.0D, factor));
        int red = (int) Math.round((color >> 16 & 0xFF) * clamped);
        int green = (int) Math.round((color >> 8 & 0xFF) * clamped);
        int blue = (int) Math.round((color & 0xFF) * clamped);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int blendColor(int from, int to, double amount) {
        double t = Math.max(0.0D, Math.min(1.0D, amount));
        int red = (int) Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
        int green = (int) Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
        int blue = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static ScreenPoint[] addCuboid(List<ScreenPoint[]> faces, int centerX, int footY,
                                            double pivotX, double pivotY, double pivotZ,
                                            double minX, double minY, double minZ,
                                            double maxX, double maxY, double maxZ,
                                            double rotationX, double rotationZ) {
        Point3[] local = {
                new Point3(minX, minY, minZ), new Point3(maxX, minY, minZ),
                new Point3(minX, maxY, minZ), new Point3(maxX, maxY, minZ),
                new Point3(minX, minY, maxZ), new Point3(maxX, minY, maxZ),
                new Point3(minX, maxY, maxZ), new Point3(maxX, maxY, maxZ)
        };
        ScreenPoint[] projected = new ScreenPoint[local.length];
        double sinX = Math.sin(rotationX);
        double cosX = Math.cos(rotationX);
        double sinZ = Math.sin(rotationZ);
        double cosZ = Math.cos(rotationZ);

        for (int i = 0; i < local.length; i++) {
            Point3 point = local[i];
            double rotatedY = point.y() * cosX - point.z() * sinX;
            double rotatedZ = point.y() * sinX + point.z() * cosX;
            double rotatedX = point.x() * cosZ - rotatedY * sinZ;
            rotatedY = point.x() * sinZ + rotatedY * cosZ;
            projected[i] = project(centerX, footY,
                    pivotX + rotatedX, pivotY + rotatedY, pivotZ + rotatedZ);
        }

        for (int[] indices : VISIBLE_FACES) {
            ScreenPoint[] face = new ScreenPoint[indices.length];
            for (int i = 0; i < indices.length; i++) {
                face[i] = projected[indices[i]];
            }
            faces.add(face);
        }
        return projected;
    }

    private static ScreenPoint project(int centerX, int footY, double x, double y, double z) {
        double yawCos = Math.cos(MODEL_YAW);
        double yawSin = Math.sin(MODEL_YAW);
        double yawX = x * yawCos - z * yawSin;
        double yawZ = x * yawSin + z * yawCos;

        double pitchCos = Math.cos(MODEL_PITCH);
        double pitchSin = Math.sin(MODEL_PITCH);
        double pitchY = y * pitchCos - yawZ * pitchSin;
        return new ScreenPoint(centerX + yawX * MODEL_SCALE, footY - pitchY * MODEL_SCALE);
    }

    private static void drawLine(GuiGraphics graphics, ScreenPoint a, ScreenPoint b, int color) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 0.35D) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(a.x(), a.y(), 210.0D);
        graphics.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        graphics.fill(0, 0, Math.max(1, (int) Math.ceil(length)), 1, color);
        graphics.pose().popPose();
    }

    private record Point3(double x, double y, double z) {
    }

    private record ScreenPoint(double x, double y) {
    }

    private record CachedGeometry(int centerX, int footY, int movementKey, long animationFrame,
                                  ScreenPoint[] head, ScreenPoint[] body,
                                  ScreenPoint seamTop, ScreenPoint seamBottom,
                                  SilhouetteMask mask) {
    }

    private static final class SilhouetteMask {
        private final int originX;
        private final int originY;
        private final int width;
        private final int height;
        private final boolean[] pixels;
        private Span[] haloSpans = new Span[0];
        private Span[] surfaceSpans = new Span[0];
        private Span[] boundarySpans = new Span[0];

        private SilhouetteMask(int originX, int originY, int width, int height) {
            this.originX = originX;
            this.originY = originY;
            this.width = width;
            this.height = height;
            this.pixels = new boolean[width * height];
        }

        private static SilhouetteMask from(List<ScreenPoint[]> faces) {
            double minX = faces.stream().flatMap(Arrays::stream).mapToDouble(ScreenPoint::x).min().orElse(0.0D);
            double maxX = faces.stream().flatMap(Arrays::stream).mapToDouble(ScreenPoint::x).max().orElse(0.0D);
            double minY = faces.stream().flatMap(Arrays::stream).mapToDouble(ScreenPoint::y).min().orElse(0.0D);
            double maxY = faces.stream().flatMap(Arrays::stream).mapToDouble(ScreenPoint::y).max().orElse(0.0D);
            int originX = (int) Math.floor(minX) - 1;
            int originY = (int) Math.floor(minY) - 1;
            SilhouetteMask mask = new SilhouetteMask(
                    originX,
                    originY,
                    Math.max(1, (int) Math.ceil(maxX) - originX + 2),
                    Math.max(1, (int) Math.ceil(maxY) - originY + 2)
            );
            for (ScreenPoint[] face : faces) {
                mask.rasterize(face);
            }
            mask.buildSpans();
            return mask;
        }

        private void rasterize(ScreenPoint[] polygon) {
            int startY = Math.max(0, (int) Math.floor(
                    Arrays.stream(polygon).mapToDouble(ScreenPoint::y).min().orElse(originY)) - originY);
            int endY = Math.min(height - 1, (int) Math.ceil(
                    Arrays.stream(polygon).mapToDouble(ScreenPoint::y).max().orElse(originY)) - originY);
            double[] intersections = new double[polygon.length];

            for (int localY = startY; localY <= endY; localY++) {
                double scanY = originY + localY + 0.5D;
                int count = 0;
                for (int i = 0; i < polygon.length; i++) {
                    ScreenPoint a = polygon[i];
                    ScreenPoint b = polygon[(i + 1) % polygon.length];
                    if ((a.y() <= scanY && b.y() > scanY) || (b.y() <= scanY && a.y() > scanY)) {
                        double progress = (scanY - a.y()) / (b.y() - a.y());
                        intersections[count++] = a.x() + (b.x() - a.x()) * progress;
                    }
                }
                Arrays.sort(intersections, 0, count);
                for (int i = 0; i + 1 < count; i += 2) {
                    int startX = Math.max(0, (int) Math.floor(intersections[i]) - originX);
                    int endX = Math.min(width - 1, (int) Math.ceil(intersections[i + 1]) - originX);
                    for (int localX = startX; localX <= endX; localX++) {
                        pixels[localY * width + localX] = true;
                    }
                }
            }
        }

        private void render(GuiGraphics graphics, int surfaceColor, int haloColor, int outlineColor) {
            renderSpans(graphics, haloSpans, haloColor);
            renderSpans(graphics, surfaceSpans, surfaceColor);
            renderSpans(graphics, boundarySpans, outlineColor);
        }

        private void buildSpans() {
            List<Span> halo = new ArrayList<>();
            for (int y = -1; y <= height; y++) {
                collectRowSpans(halo, y, -1, width, true, false);
            }
            this.haloSpans = mergeVerticalSpans(halo);

            List<Span> surface = new ArrayList<>();
            for (int y = 0; y < height; y++) {
                collectRowSpans(surface, y, 0, width - 1, false, false);
            }
            this.surfaceSpans = mergeVerticalSpans(surface);

            List<Span> boundary = new ArrayList<>();
            for (int y = 0; y < height; y++) {
                collectRowSpans(boundary, y, 0, width - 1, false, true);
            }
            this.boundarySpans = mergeVerticalSpans(boundary);
        }

        /**
         * Rows in the cached raster often have the same horizontal span for many
         * consecutive pixels.  Merge those rows into one rectangle before
         * rendering.  The covered pixel set is identical, but the number of GUI
         * quads submitted per frame is much smaller.
         */
        private static Span[] mergeVerticalSpans(List<Span> source) {
            if (source.isEmpty()) {
                return new Span[0];
            }

            List<Span> merged = new ArrayList<>(source.size());
            for (Span span : source) {
                int last = merged.size() - 1;
                if (last >= 0) {
                    Span previous = merged.get(last);
                    if (previous.y() + previous.height() == span.y()
                            && previous.startX() == span.startX()
                            && previous.endXExclusive() == span.endXExclusive()) {
                        merged.set(last, new Span(previous.y(), previous.startX(),
                                previous.endXExclusive(), previous.height() + span.height()));
                        continue;
                    }
                }
                merged.add(span);
            }
            return merged.toArray(Span[]::new);
        }

        private void collectRowSpans(List<Span> spans, int y, int minX, int maxX,
                                     boolean halo, boolean boundary) {
            int runStart = -1;
            for (int x = minX; x <= maxX; x++) {
                boolean active = halo ? isHalo(x, y) : boundary ? isBoundary(x, y) : isFilled(x, y);
                if (active) {
                    if (runStart < 0) {
                        runStart = x;
                    }
                } else if (runStart >= 0) {
                    spans.add(new Span(y, runStart, x, 1));
                    runStart = -1;
                }
            }
            if (runStart >= 0) {
                spans.add(new Span(y, runStart, maxX + 1, 1));
            }
        }

        private void renderSpans(GuiGraphics graphics, Span[] spans, int color) {
            for (Span span : spans) {
                graphics.fill(originX + span.startX(), originY + span.y(),
                        originX + span.endXExclusive(),
                        originY + span.y() + span.height(), color);
            }
        }

        private boolean isBoundary(int x, int y) {
            if (!isFilled(x, y)) {
                return false;
            }
            for (int offsetY = -1; offsetY <= 1; offsetY++) {
                for (int offsetX = -1; offsetX <= 1; offsetX++) {
                    if (offsetX == 0 && offsetY == 0) {
                        continue;
                    }
                    if (!isFilled(x + offsetX, y + offsetY)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isHalo(int x, int y) {
            if (isFilled(x, y)) {
                return false;
            }
            for (int offsetY = -1; offsetY <= 1; offsetY++) {
                for (int offsetX = -1; offsetX <= 1; offsetX++) {
                    if (isFilled(x + offsetX, y + offsetY)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isFilled(int x, int y) {
            return x >= 0 && x < width && y >= 0 && y < height && pixels[y * width + x];
        }

        private record Span(int y, int startX, int endXExclusive, int height) {
        }
    }
}
