package com.hhy.dreamingfishcore.gameplay.marker_system.client.handler;

import com.hhy.dreamingfishcore.gameplay.marker_system.MarkerManager;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.input.KeybindHandler;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.gameplay.marker_system.network.Packet_RequestMarker;
import com.hhy.dreamingfishcore.server.notice_system.client.NotificationClientDisplay;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public class MarkerClientHandler {
    private static final int FAILURE_TIP_DURATION_MS = 1600;

    private MarkerClientHandler() {
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != InputConstants.PRESS
                || !KeybindHandler.FPS_MARKER_KEY.matchesMouse(event.getButton())) {
            return;
        }

        if (createMarker(Minecraft.getInstance())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != InputConstants.PRESS
                || !KeybindHandler.FPS_MARKER_KEY.matches(event.getKey(), event.getScanCode())) {
            return;
        }

        // Forge 的 InputEvent.Key 不可取消；处理键位即可，调用 setCanceled 会直接使客户端崩溃。
        createMarker(Minecraft.getInstance());
    }

    private static boolean createMarker(Minecraft mc) {
        if (!canCreateMarker(mc)) {
            return false;
        }

        Vec3 markerPosition = getMarkerPosition(mc);
        if (markerPosition == null) {
            showFailure("标点失败：准星没有命中目标");
            return true;
        }

        if (!isWithinRange(mc, markerPosition)) {
            showFailure("标点失败：目标超过渲染距离");
            return true;
        }

        if (!isClientChunkLoaded(mc, markerPosition)) {
            showFailure("标点失败：目标区块尚未加载");
            return true;
        }

        DreamingFishCore_NetworkManager.sendToServer(new Packet_RequestMarker(markerPosition.x, markerPosition.y, markerPosition.z));
        return true;
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        MarkerManager.clear();
    }

    private static boolean canCreateMarker(Minecraft mc) {
        return mc.player != null
                && mc.level != null
                && mc.screen == null
                && mc.getOverlay() == null
                && mc.isWindowActive();
    }

    private static Vec3 getMarkerPosition(Minecraft mc) {
        HitResult hitResult = pickTarget(mc);
        if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
            return null;
        }

        if (hitResult instanceof EntityHitResult entityHitResult) {
            Entity entity = entityHitResult.getEntity();
            return entity.position().add(0.0D, Math.min(entity.getBbHeight() + 0.45D, 2.8D), 0.0D);
        }

        if (hitResult instanceof BlockHitResult blockHitResult) {
            BlockPos pos = blockHitResult.getBlockPos();
            return Vec3.atCenterOf(pos).add(0.0D, 0.18D, 0.0D);
        }

        return hitResult.getLocation();
    }

    private static HitResult pickTarget(Minecraft mc) {
        Entity cameraEntity = mc.getCameraEntity();
        if (cameraEntity == null) {
            return null;
        }

        double maxDistance = getMaxMarkerDistance(mc);
        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 eyePosition = cameraEntity.getEyePosition(partialTick);
        Vec3 viewVector = cameraEntity.getViewVector(partialTick);
        Vec3 endPosition = eyePosition.add(viewVector.scale(maxDistance));
        HitResult blockHit = cameraEntity.pick(maxDistance, partialTick, false);
        double nearestDistanceSqr = blockHit.getType() == HitResult.Type.MISS
                ? maxDistance * maxDistance
                : blockHit.getLocation().distanceToSqr(eyePosition);

        AABB searchBox = cameraEntity.getBoundingBox()
                .expandTowards(viewVector.scale(maxDistance))
                .inflate(1.0D, 1.0D, 1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                cameraEntity,
                eyePosition,
                endPosition,
                searchBox,
                entity -> !entity.isSpectator() && entity.isPickable(),
                nearestDistanceSqr
        );

        return entityHit != null ? entityHit : blockHit;
    }

    private static boolean isWithinRange(Minecraft mc, Vec3 markerPosition) {
        Vec3 eyePosition = mc.player.getEyePosition();
        double maxDistance = getMaxMarkerDistance(mc);
        return markerPosition.distanceToSqr(eyePosition) <= maxDistance * maxDistance;
    }

    private static double getMaxMarkerDistance(Minecraft mc) {
        return mc.options.getEffectiveRenderDistance() * 16.0D;
    }

    private static boolean isClientChunkLoaded(Minecraft mc, Vec3 markerPosition) {
        BlockPos pos = BlockPos.containing(markerPosition);
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        return mc.level.getChunkSource().hasChunk(chunkX, chunkZ);
    }

    private static void showFailure(String message) {
        NotificationClientDisplay.showTopLeft(message, FAILURE_TIP_DURATION_MS);
    }
}
