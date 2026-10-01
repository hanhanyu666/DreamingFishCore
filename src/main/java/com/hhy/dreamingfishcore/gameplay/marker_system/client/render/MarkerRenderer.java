package com.hhy.dreamingfishcore.gameplay.marker_system.client.render;

import com.hhy.dreamingfishcore.gameplay.marker_system.MarkerManager;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.marker_system.MarkerData;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.hhy.dreamingfishcore.client.ui.framework.hud.HudFrame;
import com.hhy.dreamingfishcore.client.ui.framework.hud.HudLayer;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Collection;

@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public class MarkerRenderer {
    private static final int[] PLAYER_COLORS = {
            0xFFEFB64A,
            0xFF65C8FF,
            0xFFFF6B88,
            0xFF72E08A,
            0xFFC58BFF,
            0xFFFF8A4C,
            0xFF4FE0C7,
            0xFFE4D65A
    };
    private static final float TEXT_SCALE = 0.67F;
    /** 下边缘指示器让开快捷栏、行动条与体征读数。 */
    private static final int BOTTOM_CLEARANCE = 58;
    private static Matrix4f lastModelViewMatrix;
    private static Matrix4f lastProjectionMatrix;
    private static Vec3 lastCameraPosition;

    /** 标记的屏幕投影在统一 HUD 画布中最后绘制，压在其他 HUD 之上。 */
    public static final HudLayer LAYER = new HudLayer() {
        @Override
        public int order() {
            return 70;
        }

        @Override
        public boolean visible(Minecraft mc) {
            return mc.player != null && mc.level != null && !mc.options.hideGui && !mc.getDebugOverlay().showDebugScreen()
                    && lastModelViewMatrix != null && lastProjectionMatrix != null && lastCameraPosition != null
                    && !MarkerManager.getActiveMarkers().isEmpty();
        }

        @Override
        public void paint(HudFrame frame) {
            MarkerRenderer.paint(frame);
        }
    };

    private MarkerRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }

        // Do not copy the two camera matrices when there is no marker to draw.
        // This stage runs every world frame, while markers are an occasional HUD.
        if (MarkerManager.getActiveMarkers().isEmpty()) {
            lastModelViewMatrix = null;
            lastProjectionMatrix = null;
            lastCameraPosition = null;
            return;
        }

        // AFTER_ENTITIES uses a temporary PoseStack that has already been restored to identity.
        // The event's model-view matrix is the one that still contains the camera rotation needed
        // to project a camera-relative world position onto the GUI correctly.
        lastModelViewMatrix = new Matrix4f(event.getModelViewMatrix());
        lastProjectionMatrix = new Matrix4f(event.getProjectionMatrix());
        lastCameraPosition = event.getCamera().getPosition();
    }

    private static void paint(HudFrame frame) {
        Minecraft mc = frame.minecraft();
        UiCanvas canvas = frame.canvas();
        Font font = frame.font();
        long now = Util.getMillis();
        for (MarkerData marker : MarkerManager.getActiveMarkers()) {
            float fade = marker.getFade(now);
            if (fade <= 0.01F) {
                continue;
            }
            ScreenPoint point = projectToScreen(mc, marker.getPosition());
            if (point == null) {
                continue;
            }
            double distanceSqr = marker.getPosition().distanceToSqr(mc.player.getEyePosition());
            int distance = Math.max(0, Math.round((float) Math.sqrt(distanceSqr)));
            int color = colorForMarker(marker);
            canvas.pushAlpha(fade);
            if (point.edge != ScreenEdge.NONE) {
                drawEdgeIndicator(canvas, font, point.x, point.y, marker.getOwnerName(), distance, color, point.edge);
            } else {
                drawPin(canvas, font, point.x, point.y, marker.getOwnerName() + " · " + distance + "m", color);
            }
            canvas.popAlpha();
        }
    }

    /** 屏幕内的标记：定位针 + 下方的名字与距离标签。 */
    private static void drawPin(UiCanvas canvas, Font font, float x, float y, String label, int color) {
        canvas.circle(x, y - 8.0F, 7.5F, 0x50000000);
        Icon.paint(canvas, Icons.PIN, x - 6.0F, y - 15.0F, 12.0F, 2.6F, 0xB0000000);
        Icon.paint(canvas, Icons.PIN, x - 6.0F, y - 15.0F, 12.0F, color);
        drawLabel(canvas, font, label, x, y + 1.0F, color);
    }

    /** 屏幕外的标记：边缘的圆形指向箭头，下方是名字与距离。 */
    private static void drawEdgeIndicator(UiCanvas canvas, Font font, float centerX, float centerY,
                                          String ownerName, int distance, int color, ScreenEdge edge) {
        Icons arrow = switch (edge) {
            case LEFT -> Icons.CHEVRON_LEFT;
            case RIGHT -> Icons.CHEVRON_RIGHT;
            case TOP -> Icons.CHEVRON_UP;
            default -> Icons.CHEVRON_DOWN;
        };
        canvas.shape(centerX - 6.5F, centerY - 6.5F, 13.0F, 13.0F).radius(6.5F).fill(0x88000000)
                .border(1.0F, UiColor.withAlpha(color, 0.75F)).draw();
        Icon.paint(canvas, arrow, centerX - 4.5F, centerY - 4.5F, 9.0F, color);
        float textY = edge == ScreenEdge.TOP ? centerY + 8.0F : centerY + 9.0F;
        drawLabel(canvas, font, ownerName + " · " + distance + "m", centerX, textY, color);
    }

    private static void drawLabel(UiCanvas canvas, Font font, String text, float centerX, float top, int color) {
        float textWidth = font.width(text) * TEXT_SCALE;
        float width = textWidth + 8.0F;
        canvas.shape(centerX - width / 2.0F, top, width, 9.0F).radius(4.5F).fill(0x8C000000)
                .border(1.0F, UiColor.withAlpha(color, 0.35F)).draw();
        canvas.text(text, centerX - textWidth / 2.0F, top + 1.8F, color, TEXT_SCALE, false);
    }

    private static ScreenPoint projectToScreen(Minecraft mc, Vec3 worldPosition) {
        Vec3 relative = worldPosition.subtract(lastCameraPosition);
        Vector4f view = new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1.0F);
        lastModelViewMatrix.transform(view);
        Vector4f clip = new Vector4f(view);
        lastProjectionMatrix.transform(clip);

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int margin = 18;

        if (clip.w() <= 0.0F) {
            float absoluteW = Math.max(0.0001F, Math.abs(clip.w()));
            float directionX = clip.x() / absoluteW;
            float directionY = clip.y() / absoluteW;
            return projectToEdge(screenWidth, screenHeight, margin, directionX, directionY);
        }

        float ndcX = clip.x() / clip.w();
        float ndcY = clip.y() / clip.w();
        float ndcZ = clip.z() / clip.w();
        if (ndcZ < -1.0F || ndcZ > 1.0F) {
            return null;
        }

        if (ndcX < -1.0F || ndcX > 1.0F || ndcY < -1.0F || ndcY > 1.0F) {
            return projectToEdge(screenWidth, screenHeight, margin, ndcX, ndcY);
        }

        float x = (ndcX * 0.5F + 0.5F) * screenWidth;
        float y = (0.5F - ndcY * 0.5F) * screenHeight;
        return new ScreenPoint(x, y, ScreenEdge.NONE);
    }

    static ScreenPoint projectToEdge(int screenWidth, int screenHeight, int margin, float ndcX, float ndcY) {
        float scale = Math.max(Math.abs(ndcX), Math.abs(ndcY));
        if (scale < 0.0001F) {
            return new ScreenPoint(screenWidth / 2.0F, screenHeight - BOTTOM_CLEARANCE, ScreenEdge.BOTTOM);
        }

        float edgeX = ndcX / scale;
        float edgeY = ndcY / scale;
        float x = (edgeX * 0.5F + 0.5F) * screenWidth;
        float y = (0.5F - edgeY * 0.5F) * screenHeight;
        x = clamp(x, margin, screenWidth - margin);
        y = clamp(y, margin, screenHeight - BOTTOM_CLEARANCE);

        if (Math.abs(edgeX) >= Math.abs(edgeY)) {
            return new ScreenPoint(edgeX < 0.0F ? margin : screenWidth - margin, y,
                    edgeX < 0.0F ? ScreenEdge.LEFT : ScreenEdge.RIGHT);
        }

        return new ScreenPoint(x, edgeY > 0.0F ? margin : screenHeight - BOTTOM_CLEARANCE,
                edgeY > 0.0F ? ScreenEdge.TOP : ScreenEdge.BOTTOM);
    }

    private static int colorForMarker(MarkerData marker) {
        int index = Math.floorMod(marker.getOwnerId().hashCode(), PLAYER_COLORS.length);
        return PLAYER_COLORS[index];
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    enum ScreenEdge {
        NONE,
        LEFT,
        RIGHT,
        TOP,
        BOTTOM
    }

    record ScreenPoint(float x, float y, ScreenEdge edge) {
    }
}
