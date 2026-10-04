package com.hhy.dreamingfishcore.gameplay.raid_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * 对局服务的生命周期：开局读回进行中的对局，关服清缓存。
 *
 * <p>读回而不是重新随机，是设计稿 §14.4 的硬要求：重启后撤离点/资源点不能变，
 * 否则玩家的举报与 bug 都无法复现。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class RaidEvents {

    private RaidEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        RaidService.load(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RaidService.clear();
        com.hhy.dreamingfishcore.gameplay.raid_system.loot.RaidLootService.clear();
        com.hhy.dreamingfishcore.gameplay.raid_system.extraction.ExtractionService.clear();
    }

    /** 给本局开放的撤离点喷粒子标识（服务端粒子，不动协议）。 */
    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        com.hhy.dreamingfishcore.gameplay.raid_system.extraction.ExtractionService
                .tickMarkers(event.getServer());
    }
}
