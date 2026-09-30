package com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

/**
 * 把 {@link HudBodyPainter} 输出的矩形提交到共享 GUI 批次，并缓存人形几何。
 *
 * <p>静止时几何不变、不会重建；只有行走摆臂时才按约 30 帧每秒的步长重新栅格化。</p>
 */
final class HudBodyRenderer {
    private static final long SWING_STEP_MS = 33L;
    private static HudBodyGeometry cachedGeometry;
    private static int cachedCenterX = Integer.MIN_VALUE;
    private static int cachedFootY = Integer.MIN_VALUE;
    private static int cachedSwingKey = Integer.MIN_VALUE;

    private HudBodyRenderer() {
    }

    static HudBodyGeometry geometry(Player player, int centerX, int footY, long now) {
        double horizontalSpeed = player.getDeltaMovement().horizontalDistance();
        double movement = Math.min(0.55D, horizontalSpeed * (player.isSprinting() ? 15.0D : 11.0D));
        long frame = now / SWING_STEP_MS;
        double swing = Math.sin(frame * SWING_STEP_MS / 125.0D) * movement;
        int swingKey = (int) Math.round(swing * 200.0D);
        if (cachedGeometry == null || cachedCenterX != centerX || cachedFootY != footY
                || cachedSwingKey != swingKey) {
            cachedGeometry = HudBodyGeometry.build(centerX, footY, swingKey / 200.0D);
            cachedCenterX = centerX;
            cachedFootY = footY;
            cachedSwingKey = swingKey;
        }
        return cachedGeometry;
    }

    static void render(GuiGraphics graphics, HudBodyGeometry body, HudBodyPainter.Visual visual) {
        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f pose = graphics.pose().last().pose();
        // 与 GuiGraphics#fill 相同的顶点顺序与渲染类型，但复用同一个 consumer 和 pose。
        HudBodyPainter.paint(body, visual, (left, top, right, bottom, color) -> {
            consumer.addVertex(pose, left, top, 0.0F).setColor(color);
            consumer.addVertex(pose, left, bottom, 0.0F).setColor(color);
            consumer.addVertex(pose, right, bottom, 0.0F).setColor(color);
            consumer.addVertex(pose, right, top, 0.0F).setColor(color);
        });
    }
}
