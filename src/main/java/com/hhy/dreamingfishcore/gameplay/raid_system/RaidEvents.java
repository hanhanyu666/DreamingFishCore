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
        RaidConfig.reload();     // config/dreamingfishcore/raid.json：读条半径/时长、出口、自动结束时间
        RaidService.load(event.getServer());
        com.hhy.dreamingfishcore.gameplay.raid_system.extraction.ExtractionRunner
                .load(event.getServer());   // 读回本局限次撤离点的剩余次数
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RaidService.clear();
        com.hhy.dreamingfishcore.gameplay.raid_system.loot.RaidLootService.clear();
        com.hhy.dreamingfishcore.gameplay.raid_system.extraction.ExtractionService.clear();
    }

    /**
     * 对局内死亡：按设计，战利品随尸体带不出去（现有尸体流程本来就接管了背包），
     * 所以这里不需要改死亡逻辑，只把事件记进统计，便于运营对账。
     */
    @SubscribeEvent
    public static void onPlayerDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        com.hhy.dreamingfishcore.gameplay.raid_system.RaidService.current()
                .ifPresent(manifest -> com.hhy.dreamingfishcore.gameplay.raid_system.RaidStatsLog
                        .died(player.getServer(), manifest, player));
    }

    /** 给本局开放的撤离点喷粒子标识（服务端粒子，不动协议）。 */
    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        com.hhy.dreamingfishcore.gameplay.raid_system.extraction.ExtractionService
                .tickMarkers(event.getServer());
        // 撤离读条：站在撤离点范围内累计进度，满了就传送并结算
        com.hhy.dreamingfishcore.gameplay.raid_system.extraction.ExtractionRunner
                .tick(event.getServer());
    }
}
