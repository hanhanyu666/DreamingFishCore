package com.hhy.dreamingfishcore.gameplay.marker_system.client.render;

import com.hhy.dreamingfishcore.gameplay.marker_system.MarkerManager;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.marker_system.MarkerData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
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
    private static Matrix4f lastModelViewMatrix;
    private static Matrix4f lastProjectionMatrix;
    private static Vec3 lastCameraPosition;

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

        lastModelViewMatrix = new Matrix4f(event.getPoseStack().last().pose());
        lastProjectionMatrix = new Matrix4f(event.getProjectionMatrix());
        lastCameraPosition = event.getCamera().getPosition();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()
                || lastModelViewMatrix == null || lastProjectionMatrix == null || lastCameraPosition == null) {
            return;
        }

        Collection<MarkerData> markers = MarkerManager.getActiveMarkers();
        if (markers.isEmpty()) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = mc.font;
        long now = System.currentTimeMillis();

        for (MarkerData marker : markers) {
            double distanceSqr = marker.getPosition().distanceToSqr(mc.player.getEyePosition());
            float fade = marker.getFade(now);
            if (fade <= 0.01F) {
                continue;
            }

            ScreenPoint point = projectToScreen(mc, marker.getPosition().add(0.0D, 1.65D, 0.0D));
            if (point == null) {
                continue;
            }

            int distance = Math.max(0, Math.round((float) Math.sqrt(distanceSqr)));
            int alpha = Math.max(0, Math.min(255, Math.round(255.0F * fade)));
            int markerColor = withAlpha(colorForMarker(marker), alpha);
            int textColor = markerColor;

            if (point.edge != ScreenEdge.NONE) {
                drawEdgeIndicator(guiGraphics, font, Math.round(point.x), Math.round(point.y),
                        marker.getOwnerName(), distance, markerColor, textColor, point.edge);
                continue;
            }

            Component text = Component.literal(marker.getOwnerName() + " · " + distance + "m");
            int textWidth = Math.round(font.width(text) * TEXT_SCALE);
            int iconX = Math.round(point.x);
            int iconY = Math.round(point.y - 8.0F);
            int textX = Math.round(point.x - textWidth / 2.0F);
            int textY = Math.round(point.y + 1.0F);
            drawMarkerIcon(guiGraphics, iconX, iconY, markerColor);
            drawScaledString(guiGraphics, font, text, textX, textY, TEXT_SCALE, textColor);
        }
    }

    private static void drawMarkerIcon(GuiGraphics guiGraphics, int centerX, int centerY, int color) {
        guiGraphics.fill(centerX - 1, centerY - 3, centerX + 1, centerY - 1, color);
        guiGraphics.fill(centerX - 2, centerY - 2, centerX + 3, centerY + 3, color);
        guiGraphics.fill(centerX - 1, centerY - 3, centerX + 2, centerY + 4, color);
        guiGraphics.fill(centerX, centerY + 3, centerX + 1, centerY + 8, color);
    }

    private static void drawEdgeIndicator(GuiGraphics guiGraphics, Font font, int centerX, int centerY,
                                          String ownerName, int distance, int markerColor, int textColor, ScreenEdge edge) {
        switch (edge) {
            case LEFT -> drawLeftArrow(guiGraphics, centerX, centerY, markerColor);
            case RIGHT -> drawRightArrow(guiGraphics, centerX, centerY, markerColor);
            case TOP -> drawUpArrow(guiGraphics, centerX, centerY, markerColor);
            case BOTTOM -> drawDownArrow(guiGraphics, centerX, centerY, markerColor);
            default -> {
            }
        }

        int textY = edge == ScreenEdge.TOP ? centerY + 7 : centerY + 8;
        Component nameText = Component.literal(ownerName);
        Component distanceText = Component.literal(distance + "m");
        int nameWidth = Math.round(font.width(nameText) * TEXT_SCALE);
        int textWidth = Math.round(font.width(distanceText) * TEXT_SCALE);
        drawScaledString(guiGraphics, font, nameText, centerX - nameWidth / 2, textY, TEXT_SCALE, textColor);
        drawScaledString(guiGraphics, font, distanceText, centerX - textWidth / 2, textY + 7, TEXT_SCALE, textColor);
    }

    private static void drawDownArrow(GuiGraphics guiGraphics, int centerX, int centerY, int color) {
        guiGraphics.fill(centerX - 4, centerY, centerX + 5, centerY + 1, color);
        guiGraphics.fill(centerX - 3, centerY + 1, centerX + 4, centerY + 2, color);
        guiGraphics.fill(centerX - 2, centerY + 2, centerX + 3, centerY + 3, color);
        guiGraphics.fill(centerX - 1, centerY + 3, centerX + 2, centerY + 4, color);
        guiGraphics.fill(centerX, centerY + 4, centerX + 1, centerY + 6, color);
    }

    private static void drawUpArrow(GuiGraphics guiGraphics, int centerX, int centerY, int color) {
        guiGraphics.fill(centerX, centerY - 6, centerX + 1, centerY - 4, color);
        guiGraphics.fill(centerX - 1, centerY - 4, centerX + 2, centerY - 3, color);
        guiGraphics.fill(centerX - 2, centerY - 3, centerX + 3, centerY - 2, color);
        guiGraphics.fill(centerX - 3, centerY - 2, centerX + 4, centerY - 1, color);
        guiGraphics.fill(centerX - 4, centerY - 1, centerX + 5, centerY, color);
    }

    private static void drawLeftArrow(GuiGraphics guiGraphics, int centerX, int centerY, int color) {
        guiGraphics.fill(centerX - 6, centerY, centerX - 4, centerY + 1, color);
        guiGraphics.fill(centerX - 4, centerY - 1, centerX - 3, centerY + 2, color);
        guiGraphics.fill(centerX - 3, centerY - 2, centerX - 2, centerY + 3, color);
        guiGraphics.fill(centerX - 2, centerY - 3, centerX - 1, centerY + 4, color);
        guiGraphics.fill(centerX - 1, centerY - 4, centerX, centerY + 5, color);
    }

    private static void drawRightArrow(GuiGraphics guiGraphics, int centerX, int centerY, int color) {
        guiGraphics.fill(centerX + 4, centerY, centerX + 6, centerY + 1, color);
        guiGraphics.fill(centerX + 3, centerY - 1, centerX + 4, centerY + 2, color);
        guiGraphics.fill(centerX + 2, centerY - 2, centerX + 3, centerY + 3, color);
        guiGraphics.fill(centerX + 1, centerY - 3, centerX + 2, centerY + 4, color);
        guiGraphics.fill(centerX, centerY - 4, centerX + 1, centerY + 5, color);
    }

    private static void drawScaledString(GuiGraphics guiGraphics, Font font, Component text,
                                         int x, int y, float scale, int color) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.drawString(font, text, Math.round(x / scale), Math.round(y / scale), color, true);
        guiGraphics.pose().popPose();
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
            float horizontal = clamp(view.x() / Math.max(1.0F, Math.abs(view.z())), -0.92F, 0.92F);
            float x = (horizontal * 0.5F + 0.5F) * screenWidth;
            return new ScreenPoint(clamp(x, margin, screenWidth - margin), screenHeight - 24.0F, ScreenEdge.BOTTOM);
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

    private static ScreenPoint projectToEdge(int screenWidth, int screenHeight, int margin, float ndcX, float ndcY) {
        float scale = Math.max(Math.abs(ndcX), Math.abs(ndcY));
        if (scale < 0.0001F) {
            return new ScreenPoint(screenWidth / 2.0F, screenHeight - margin, ScreenEdge.BOTTOM);
        }

        float edgeX = ndcX / scale;
        float edgeY = ndcY / scale;
        float x = (edgeX * 0.5F + 0.5F) * screenWidth;
        float y = (0.5F - edgeY * 0.5F) * screenHeight;
        x = clamp(x, margin, screenWidth - margin);
        y = clamp(y, margin, screenHeight - margin - 12);

        if (Math.abs(edgeX) >= Math.abs(edgeY)) {
            return new ScreenPoint(edgeX < 0.0F ? margin : screenWidth - margin, y,
                    edgeX < 0.0F ? ScreenEdge.LEFT : ScreenEdge.RIGHT);
        }

        return new ScreenPoint(x, edgeY > 0.0F ? margin : screenHeight - margin - 12,
                edgeY > 0.0F ? ScreenEdge.TOP : ScreenEdge.BOTTOM);
    }

    private static int colorForMarker(MarkerData marker) {
        int index = Math.floorMod(marker.getOwnerId().hashCode(), PLAYER_COLORS.length);
        return PLAYER_COLORS[index];
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private enum ScreenEdge {
        NONE,
        LEFT,
        RIGHT,
        TOP,
        BOTTOM
    }

    private record ScreenPoint(float x, float y, ScreenEdge edge) {
    }
}
