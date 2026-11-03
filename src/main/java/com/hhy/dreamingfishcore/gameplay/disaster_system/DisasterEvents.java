package com.hhy.dreamingfishcore.gameplay.disaster_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 天灾日程的生命周期：开服读回、每 tick 推进（内部只在跨天时动手）、关服清缓存。
 *
 * <p>与搜打撤的 {@code RaidEvents} 同一套写法：一个事件类负责接线，具体逻辑全在
 * {@link DisasterService} 里，方便单测与其它系统调用。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class DisasterEvents {

    private DisasterEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        DisasterService.ensureLoaded(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DisasterService.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DisasterService.tick(event.getServer());
    }
}
