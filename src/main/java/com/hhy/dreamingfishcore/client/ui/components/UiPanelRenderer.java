package com.hhy.dreamingfishcore.client.ui.components;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/** Shared primitives for the small panels used by HUD and notification UI. */
public final class UiPanelRenderer {
    private static final int SMOOTH_CORNER_SEGMENTS = 6;
    private static final int SMOOTH_CIRCLE_SEGMENTS = SMOOTH_CORNER_SEGMENTS * 4;
    private static final float[] SMOOTH_CIRCLE_X = new float[SMOOTH_CIRCLE_SEGMENTS + 1];
    private static final float[] SMOOTH_CIRCLE_Y = new float[SMOOTH_CIRCLE_SEGMENTS + 1];
    private static final float SMOOTH_EDGE_WIDTH = 1.0F;
    private static final float CRISP_CORNER_EDGE_WIDTH = 0.35F;

    static {
        for (int index = 0; index <= SMOOTH_CIRCLE_SEGMENTS; index++) {
            double angle = Math.PI * 2.0D * index / SMOOTH_CIRCLE_SEGMENTS;
            SMOOTH_CIRCLE_X[index] = (float) Math.cos(angle);
            SMOOTH_CIRCLE_Y[index] = (float) Math.sin(angle);
        }
    }

    private UiPanelRenderer() {
    }

    /**
     * Draws a rounded panel as one tessellated GUI mesh with a one-pixel feathered edge.
     * This avoids both the stair-stepped corners and repeated buffer flushes caused by
     * building translucent curves from many {@link GuiGraphics#fill} calls.
     */
    public static void smoothRoundedRect(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                          int radius, int fillColor, int borderColor) {
        smoothRoundedRectInternal(guiGraphics, x, y, width, height, radius,
                fillColor, borderColor, true);
    }

    /**
     * Draws a smooth rounded rectangle without ending the GUI batch.
     *
     * <p>Call this from inside {@link GuiGraphics#drawManaged(Runnable)} (or flush the
     * graphics explicitly after a group of shapes) so multiple HUD primitives can share
     * one buffer submission.</p>
     */
    public static void smoothRoundedRectBatched(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                                int radius, int fillColor, int borderColor) {
        smoothRoundedRectInternal(guiGraphics, x, y, width, height, radius,
                fillColor, borderColor, false);
    }

    private static void smoothRoundedRectInternal(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                                  int radius, int fillColor, int borderColor, boolean flush) {
        if (width <= 0 || height <= 0
                || (fillColor >>> 24) == 0 && (borderColor >>> 24) == 0) {
            return;
        }

        float roundedRadius = Math.max(0.0F,
                Math.min(radius, Math.min(width / 2.0F, height / 2.0F)));
        VertexConsumer consumer = guiGraphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f pose = guiGraphics.pose().last().pose();

        if (roundedRadius < 1.0F || width < 3 || height < 3) {
            drawSmoothSquare(consumer, pose, x, y, width, height, fillColor, borderColor);
            if (flush) {
                guiGraphics.flush();
            }
            return;
        }

        float edgeWidth = Math.min(SMOOTH_EDGE_WIDTH,
                Math.min(roundedRadius - 0.25F, Math.min(width, height) / 2.0F - 0.25F));
        edgeWidth = Math.max(0.5F, edgeWidth);
        int edgeColor = (borderColor >>> 24) != 0
                ? compositeOver(borderColor, fillColor)
                : fillColor;

        drawSmoothSolid(consumer, pose, x, y, width, height,
                roundedRadius, edgeWidth, fillColor);
        drawSmoothEdgeBand(consumer, pose, x, y, width, height,
                roundedRadius, edgeWidth, edgeColor & 0x00FFFFFF, edgeColor);
        if (flush) {
            guiGraphics.flush();
        }
    }

    public static void roundedRect(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                   int radius, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) {
            return;
        }

        int right = x + width;
        int bottom = y + height;
        int cornerDepth = pixelCornerDepth(width, height, radius);

        if (cornerDepth == 0) {
            guiGraphics.fill(x, y, right, bottom, color);
            return;
        }

        // Matches vanilla's 24x24 HUD effect background: 2px, 1px, then a square edge.
        if (cornerDepth == 2) {
            guiGraphics.fill(x + 2, y, right - 2, y + 1, color);
            guiGraphics.fill(x + 1, y + 1, right - 1, y + 2, color);
            guiGraphics.fill(x, y + 2, right, bottom - 2, color);
            guiGraphics.fill(x + 1, bottom - 2, right - 1, bottom - 1, color);
            guiGraphics.fill(x + 2, bottom - 1, right - 2, bottom, color);
        } else {
            guiGraphics.fill(x + 1, y, right - 1, y + 1, color);
            guiGraphics.fill(x, y + 1, right, bottom - 1, color);
            guiGraphics.fill(x + 1, bottom - 1, right - 1, bottom, color);
        }
    }

    /**
     * Draws a small rounded rectangle with solid straight edges and feathering only on the corner arcs.
     * Thin HUD bars stay sharp while their curved ends still avoid stair stepping.
     */
    public static void crispRoundedRect(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                         int radius, int color) {
        crispRoundedRectInternal(guiGraphics, x, y, width, height, radius, color, true);
    }

    /**
     * Draws a crisp rounded rectangle without ending the GUI batch.
     * Intended for groups of thin HUD bars rendered inside a managed GUI batch.
     */
    public static void crispRoundedRectBatched(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                               int radius, int color) {
        crispRoundedRectInternal(guiGraphics, x, y, width, height, radius, color, false);
    }

    private static void crispRoundedRectInternal(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                                 int radius, int color, boolean flush) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) {
            return;
        }

        /*
         * The crisp primitive is used by the HUD's 5-6 pixel progress bars.  A
         * tessellated anti-aliased arc is disproportionately expensive at that
         * size (over two hundred vertices for a single bar), while the intended
         * visual is the vanilla/pixel corner.  Use three non-overlapping quads
         * for thin bars; this keeps the same one-pixel cut corner and avoids
         * translucent overdraw and per-frame mesh work.  Larger callers retain
         * the smooth path below so full-size panels keep their existing finish.
         */
        if (height <= 6 || width <= 6) {
            drawPixelRoundedRect(guiGraphics, x, y, width, height, color);
            if (flush) {
                guiGraphics.flush();
            }
            return;
        }

        float roundedRadius = Math.max(0.0F,
                Math.min(radius, Math.min(width / 2.0F, height / 2.0F)));
        VertexConsumer consumer = guiGraphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f pose = guiGraphics.pose().last().pose();
        if (roundedRadius < 1.0F || width < 3 || height < 3) {
            addSmoothRect(consumer, pose, x, y, x + width, y + height, color);
            if (flush) {
                guiGraphics.flush();
            }
            return;
        }

        float edgeWidth = Math.min(CRISP_CORNER_EDGE_WIDTH, roundedRadius - 0.1F);
        drawCrispRoundedSolid(consumer, pose, x, y, width, height,
                roundedRadius, Math.max(0.2F, edgeWidth), color);
        if (flush) {
            guiGraphics.flush();
        }
    }

    /**
     * Draws the one-pixel pixel-art corner used by vanilla HUD bars.  The
     * regions deliberately do not overlap so translucent colors are composited
     * exactly once in the interior.
     */
    private static void drawPixelRoundedRect(GuiGraphics guiGraphics, int x, int y,
                                             int width, int height, int color) {
        int right = x + width;
        int bottom = y + height;
        if (width <= 2 || height <= 2) {
            guiGraphics.fill(x, y, right, bottom, color);
            return;
        }

        guiGraphics.fill(x + 1, y, right - 1, y + 1, color);
        guiGraphics.fill(x, y + 1, right, bottom - 1, color);
        guiGraphics.fill(x + 1, bottom - 1, right - 1, bottom, color);
    }

    public static void roundedBorder(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                     int radius, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) {
            return;
        }

        int right = x + width;
        int bottom = y + height;
        int cornerDepth = pixelCornerDepth(width, height, radius);

        if (cornerDepth == 0) {
            guiGraphics.fill(x, y, right, y + 1, color);
            guiGraphics.fill(x, bottom - 1, right, bottom, color);
            guiGraphics.fill(x, y + 1, x + 1, bottom - 1, color);
            guiGraphics.fill(right - 1, y + 1, right, bottom - 1, color);
            return;
        }

        if (cornerDepth == 2) {
            guiGraphics.fill(x + 2, y, right - 2, y + 1, color);
            guiGraphics.fill(x + 1, y + 1, x + 2, y + 2, color);
            guiGraphics.fill(right - 2, y + 1, right - 1, y + 2, color);
            guiGraphics.fill(x, y + 2, x + 1, bottom - 2, color);
            guiGraphics.fill(right - 1, y + 2, right, bottom - 2, color);
            guiGraphics.fill(x + 1, bottom - 2, x + 2, bottom - 1, color);
            guiGraphics.fill(right - 2, bottom - 2, right - 1, bottom - 1, color);
            guiGraphics.fill(x + 2, bottom - 1, right - 2, bottom, color);
        } else {
            guiGraphics.fill(x + 1, y, right - 1, y + 1, color);
            guiGraphics.fill(x, y + 1, x + 1, bottom - 1, color);
            guiGraphics.fill(right - 1, y + 1, right, bottom - 1, color);
            guiGraphics.fill(x + 1, bottom - 1, right - 1, bottom, color);
        }
    }

    private static int pixelCornerDepth(int width, int height, int radius) {
        if (radius <= 0) {
            return 0;
        }
        return Math.min(2, Math.min(radius,
                Math.min((width - 1) / 2, (height - 1) / 2)));
    }

    public static int withAlpha(int color, int alpha) {
        int clampedAlpha = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (clampedAlpha << 24);
    }

    private static void drawSmoothSquare(VertexConsumer consumer, Matrix4f pose,
                                         float x, float y, float width, float height,
                                         int fillColor, int borderColor) {
        float right = x + width;
        float bottom = y + height;
        addSmoothRect(consumer, pose, x, y, right, bottom, fillColor);

        if ((borderColor >>> 24) == 0) {
            return;
        }

        int edgeColor = compositeOver(borderColor, fillColor);
        addSmoothRect(consumer, pose, x, y, right, Math.min(bottom, y + 1.0F), edgeColor);
        addSmoothRect(consumer, pose, x, Math.max(y, bottom - 1.0F), right, bottom, edgeColor);
        addSmoothRect(consumer, pose, x, y + 1.0F, Math.min(right, x + 1.0F), bottom - 1.0F, edgeColor);
        addSmoothRect(consumer, pose, Math.max(x, right - 1.0F), y + 1.0F, right, bottom - 1.0F, edgeColor);
    }

    private static void drawSmoothSolid(VertexConsumer consumer, Matrix4f pose,
                                        float x, float y, float width, float height,
                                        float radius, float inset, int color) {
        float right = x + width;
        float bottom = y + height;
        float leftInner = x + inset;
        float topInner = y + inset;
        float rightInner = right - inset;
        float bottomInner = bottom - inset;
        float arcRadius = radius - inset;

        if (arcRadius <= 0.0F) {
            addSmoothRect(consumer, pose, leftInner, topInner, rightInner, bottomInner, color);
            return;
        }

        float leftCenter = x + radius;
        float rightCenter = right - radius;
        float topCenter = y + radius;
        float bottomCenter = bottom - radius;

        addSmoothRect(consumer, pose, leftCenter, topInner, rightCenter, bottomInner, color);
        addSmoothRect(consumer, pose, leftInner, topCenter, leftCenter, bottomCenter, color);
        addSmoothRect(consumer, pose, rightCenter, topCenter, rightInner, bottomCenter, color);

        drawSmoothCornerFan(consumer, pose, rightCenter, bottomCenter, arcRadius, 0, color);
        drawSmoothCornerFan(consumer, pose, leftCenter, bottomCenter, arcRadius,
                SMOOTH_CORNER_SEGMENTS, color);
        drawSmoothCornerFan(consumer, pose, leftCenter, topCenter, arcRadius,
                SMOOTH_CORNER_SEGMENTS * 2, color);
        drawSmoothCornerFan(consumer, pose, rightCenter, topCenter, arcRadius,
                SMOOTH_CORNER_SEGMENTS * 3, color);
    }

    private static void drawCrispRoundedSolid(VertexConsumer consumer, Matrix4f pose,
                                              float x, float y, float width, float height,
                                              float radius, float edgeWidth, int color) {
        float right = x + width;
        float bottom = y + height;
        float leftCenter = x + radius;
        float rightCenter = right - radius;
        float topCenter = y + radius;
        float bottomCenter = bottom - radius;

        // These three rectangles do not overlap, so translucent progress colors keep their exact alpha.
        addSmoothRect(consumer, pose, leftCenter, y, rightCenter, bottom, color);
        addSmoothRect(consumer, pose, x, topCenter, leftCenter, bottomCenter, color);
        addSmoothRect(consumer, pose, rightCenter, topCenter, right, bottomCenter, color);

        float innerRadius = Math.max(0.0F, radius - edgeWidth);
        drawSmoothCornerFan(consumer, pose, rightCenter, bottomCenter, innerRadius, 0, color);
        drawSmoothCornerFan(consumer, pose, leftCenter, bottomCenter, innerRadius,
                SMOOTH_CORNER_SEGMENTS, color);
        drawSmoothCornerFan(consumer, pose, leftCenter, topCenter, innerRadius,
                SMOOTH_CORNER_SEGMENTS * 2, color);
        drawSmoothCornerFan(consumer, pose, rightCenter, topCenter, innerRadius,
                SMOOTH_CORNER_SEGMENTS * 3, color);

        int transparentColor = color & 0x00FFFFFF;
        drawSmoothCornerBand(consumer, pose, rightCenter, bottomCenter,
                radius, innerRadius, 0, transparentColor, color);
        drawSmoothCornerBand(consumer, pose, leftCenter, bottomCenter,
                radius, innerRadius, SMOOTH_CORNER_SEGMENTS, transparentColor, color);
        drawSmoothCornerBand(consumer, pose, leftCenter, topCenter,
                radius, innerRadius, SMOOTH_CORNER_SEGMENTS * 2, transparentColor, color);
        drawSmoothCornerBand(consumer, pose, rightCenter, topCenter,
                radius, innerRadius, SMOOTH_CORNER_SEGMENTS * 3, transparentColor, color);
    }

    private static void drawSmoothEdgeBand(VertexConsumer consumer, Matrix4f pose,
                                           float x, float y, float width, float height,
                                           float radius, float inset,
                                           int outerColor, int innerColor) {
        float right = x + width;
        float bottom = y + height;
        float leftCenter = x + radius;
        float rightCenter = right - radius;
        float topCenter = y + radius;
        float bottomCenter = bottom - radius;

        addSmoothQuad(consumer, pose,
                rightCenter, y + inset, innerColor,
                rightCenter, y, outerColor,
                leftCenter, y, outerColor,
                leftCenter, y + inset, innerColor);
        addSmoothQuad(consumer, pose,
                rightCenter, bottom, outerColor,
                rightCenter, bottom - inset, innerColor,
                leftCenter, bottom - inset, innerColor,
                leftCenter, bottom, outerColor);
        addSmoothQuad(consumer, pose,
                x + inset, bottomCenter, innerColor,
                x + inset, topCenter, innerColor,
                x, topCenter, outerColor,
                x, bottomCenter, outerColor);
        addSmoothQuad(consumer, pose,
                right, bottomCenter, outerColor,
                right, topCenter, outerColor,
                right - inset, topCenter, innerColor,
                right - inset, bottomCenter, innerColor);

        float innerRadius = Math.max(0.0F, radius - inset);
        drawSmoothCornerBand(consumer, pose, rightCenter, bottomCenter,
                radius, innerRadius, 0, outerColor, innerColor);
        drawSmoothCornerBand(consumer, pose, leftCenter, bottomCenter,
                radius, innerRadius, SMOOTH_CORNER_SEGMENTS, outerColor, innerColor);
        drawSmoothCornerBand(consumer, pose, leftCenter, topCenter,
                radius, innerRadius, SMOOTH_CORNER_SEGMENTS * 2, outerColor, innerColor);
        drawSmoothCornerBand(consumer, pose, rightCenter, topCenter,
                radius, innerRadius, SMOOTH_CORNER_SEGMENTS * 3, outerColor, innerColor);
    }

    private static void drawSmoothCornerFan(VertexConsumer consumer, Matrix4f pose,
                                            float centerX, float centerY, float radius,
                                            int startIndex, int color) {
        for (int segment = 0; segment < SMOOTH_CORNER_SEGMENTS; segment++) {
            int low = startIndex + segment;
            int high = low + 1;
            addSmoothQuad(consumer, pose,
                    centerX, centerY, color,
                    centerX + SMOOTH_CIRCLE_X[high] * radius,
                    centerY + SMOOTH_CIRCLE_Y[high] * radius, color,
                    centerX + SMOOTH_CIRCLE_X[low] * radius,
                    centerY + SMOOTH_CIRCLE_Y[low] * radius, color,
                    centerX, centerY, color);
        }
    }

    private static void drawSmoothCornerBand(VertexConsumer consumer, Matrix4f pose,
                                             float centerX, float centerY,
                                             float outerRadius, float innerRadius,
                                             int startIndex, int outerColor, int innerColor) {
        for (int segment = 0; segment < SMOOTH_CORNER_SEGMENTS; segment++) {
            int low = startIndex + segment;
            int high = low + 1;
            addSmoothQuad(consumer, pose,
                    centerX + SMOOTH_CIRCLE_X[high] * outerRadius,
                    centerY + SMOOTH_CIRCLE_Y[high] * outerRadius, outerColor,
                    centerX + SMOOTH_CIRCLE_X[low] * outerRadius,
                    centerY + SMOOTH_CIRCLE_Y[low] * outerRadius, outerColor,
                    centerX + SMOOTH_CIRCLE_X[low] * innerRadius,
                    centerY + SMOOTH_CIRCLE_Y[low] * innerRadius, innerColor,
                    centerX + SMOOTH_CIRCLE_X[high] * innerRadius,
                    centerY + SMOOTH_CIRCLE_Y[high] * innerRadius, innerColor);
        }
    }

    private static void addSmoothRect(VertexConsumer consumer, Matrix4f pose,
                                      float left, float top, float right, float bottom, int color) {
        if (right <= left || bottom <= top || (color >>> 24) == 0) {
            return;
        }
        addSmoothQuad(consumer, pose,
                right, bottom, color,
                right, top, color,
                left, top, color,
                left, bottom, color);
    }

    private static void addSmoothQuad(VertexConsumer consumer, Matrix4f pose,
                                      float x1, float y1, int color1,
                                      float x2, float y2, int color2,
                                      float x3, float y3, int color3,
                                      float x4, float y4, int color4) {
        consumer.addVertex(pose, x1, y1, 0.0F).setColor(color1);
        consumer.addVertex(pose, x2, y2, 0.0F).setColor(color2);
        consumer.addVertex(pose, x3, y3, 0.0F).setColor(color3);
        consumer.addVertex(pose, x4, y4, 0.0F).setColor(color4);
    }

    private static int compositeOver(int foreground, int background) {
        int foregroundAlpha = (foreground >>> 24) & 0xFF;
        if (foregroundAlpha == 0) {
            return background;
        }
        if (foregroundAlpha == 0xFF) {
            return foreground;
        }

        int backgroundAlpha = (background >>> 24) & 0xFF;
        int inverseForeground = 0xFF - foregroundAlpha;
        int outputAlpha = foregroundAlpha + (backgroundAlpha * inverseForeground + 127) / 255;
        if (outputAlpha == 0) {
            return 0;
        }

        int red = compositeChannel((foreground >>> 16) & 0xFF, foregroundAlpha,
                (background >>> 16) & 0xFF, backgroundAlpha, inverseForeground, outputAlpha);
        int green = compositeChannel((foreground >>> 8) & 0xFF, foregroundAlpha,
                (background >>> 8) & 0xFF, backgroundAlpha, inverseForeground, outputAlpha);
        int blue = compositeChannel(foreground & 0xFF, foregroundAlpha,
                background & 0xFF, backgroundAlpha, inverseForeground, outputAlpha);
        return (outputAlpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int compositeChannel(int foreground, int foregroundAlpha,
                                        int background, int backgroundAlpha,
                                        int inverseForeground, int outputAlpha) {
        int premultiplied = foreground * foregroundAlpha
                + (background * backgroundAlpha * inverseForeground + 127) / 255;
        return Math.max(0, Math.min(255,
                (premultiplied + outputAlpha / 2) / outputAlpha));
    }
}
