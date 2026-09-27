package com.hhy.dreamingfishcore.client.ui.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.Arrays;

/** Draws the base head and hat layer in one submission per distinct skin. */
public final class PlayerFaceBatchRenderer {
    private static final float HEAD_MIN_U = 8.0F / 64.0F;
    private static final float HEAD_MIN_V = 8.0F / 64.0F;
    private static final float HEAD_MAX_U = 16.0F / 64.0F;
    private static final float HEAD_MAX_V = 16.0F / 64.0F;
    private static final float HAT_MIN_U = 40.0F / 64.0F;
    private static final float HAT_MAX_U = 48.0F / 64.0F;

    private static boolean[] rendered = new boolean[16];

    private PlayerFaceBatchRenderer() {
    }

    public static void drawOne(GuiGraphics graphics, ResourceLocation skin,
                               int x, int y, int size, float alpha) {
        BufferBuilder builder = GuiQuadBatchRenderer.begin();
        addFace(builder, graphics.pose().last().pose(), x, y, size, alpha);
        GuiQuadBatchRenderer.draw(builder, skin);
    }

    /**
     * Batches repeated messages from the same player together. The arrays are
     * caller-owned reusable storage and are read only up to {@code count}.
     */
    public static void drawMany(GuiGraphics graphics,
                                ResourceLocation[] skins,
                                int[] x, int[] y, int[] size, float[] alpha,
                                int count) {
        if (count <= 0) {
            return;
        }
        if (rendered.length < count) {
            rendered = new boolean[Math.max(count, rendered.length * 2)];
        } else {
            Arrays.fill(rendered, 0, count, false);
        }

        Matrix4f pose = graphics.pose().last().pose();
        for (int first = 0; first < count; first++) {
            if (rendered[first] || skins[first] == null) {
                continue;
            }

            ResourceLocation skin = skins[first];
            BufferBuilder builder = GuiQuadBatchRenderer.begin();
            for (int index = first; index < count; index++) {
                if (!rendered[index] && skin.equals(skins[index])) {
                    addFace(builder, pose, x[index], y[index], size[index], alpha[index]);
                    rendered[index] = true;
                }
            }
            GuiQuadBatchRenderer.draw(builder, skin);
        }
    }

    static void addFace(BufferBuilder builder, Matrix4f pose,
                        int x, int y, int size, float alpha) {
        GuiQuadBatchRenderer.add(builder, pose, x, y, size, size,
                HEAD_MIN_U, HEAD_MIN_V, HEAD_MAX_U, HEAD_MAX_V, alpha);
        GuiQuadBatchRenderer.add(builder, pose, x, y, size, size,
                HAT_MIN_U, HEAD_MIN_V, HAT_MAX_U, HEAD_MAX_V, alpha);
    }
}
