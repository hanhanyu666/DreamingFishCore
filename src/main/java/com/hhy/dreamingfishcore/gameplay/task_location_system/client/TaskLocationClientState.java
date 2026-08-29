package com.hhy.dreamingfishcore.gameplay.task_location_system.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationMode;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Client-side snapshot backing the compact mode label and brief boundary effect. */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class TaskLocationClientState {
    static final long BOUNDARY_DURATION_MS = 5_000L;
    private static volatile Snapshot current;

    private TaskLocationClientState() {
    }

    public static Snapshot get() {
        return current;
    }

    public static void show(String locationName, TaskLocationMode mode,
                            BlockPos min, BlockPos max) {
        String safeName = locationName == null || locationName.isBlank()
                ? "未命名地点"
                : locationName.trim();
        current = new Snapshot(safeName,
                mode == null ? TaskLocationMode.PROTECTED : mode,
                min == null ? BlockPos.ZERO : min.immutable(),
                max == null ? BlockPos.ZERO : max.immutable(),
                System.currentTimeMillis());
    }

    public static void clear() {
        current = null;
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public record Snapshot(String locationName, TaskLocationMode mode,
                           BlockPos min, BlockPos max, long enteredAtMs) {
    }
}
