package com.hhy.dreamingfishcore.gameplay.raid_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.raid_system.extraction.ExtractionRunner;
import com.hhy.dreamingfishcore.gameplay.raid_system.extraction.ExtractionService;
import com.hhy.dreamingfishcore.gameplay.raid_system.loot.RaidLootService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 对局服务的生命周期与玩家进出。
 *
 * <p>按服主定的规则：**只有开局时在线的玩家算本局参与者**（中途进来不算，会收到明确提示）；
 * 掉线给 5 分钟宽限，超时按未撤离结算。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class RaidEvents {

    private RaidEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        RaidConfig.reload();     // config/dreamingfishcore/raid.json：读条半径/时长、出口、自动结束时间
        RaidService.load(event.getServer());
        RaidRoster.ensureForCurrentRaid(event.getServer());   // 同一局读回名单，换了局就重来
        ExtractionRunner.load(event.getServer());             // 读回本局限次撤离点的剩余次数
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RaidService.clear();
        RaidRoster.clear();
        RaidLootService.clear();
        ExtractionService.clear();
        ExtractionRunner.clear();
    }

    /**
     * 登录：宽限内回来则恢复为在图里；本局已开始但不是参与者的人会收到明确说明
     * （不然他会一直站进撤离点却什么都不发生）。
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (RaidRoster.handleLogin(player.getServer(), player)) {
            player.sendSystemMessage(Component.literal("[搜打撤] 欢迎回来，本局仍在进行，你还可以撤离"));
            return;
        }
        if (RaidService.current().isPresent() && !RaidRoster.roster().isParticipant(player.getUUID())) {
            player.sendSystemMessage(Component.literal(
                    "[搜打撤] 本局已经开始，你不是本局参与者（只能等下一局；撤离点对你无效）"));
        }
    }

    /** 掉线：进入 5 分钟宽限，背包与位置都保留（不没收）。 */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RaidRoster.markLeft(player.getServer(), player);
        }
    }

    /**
     * 对局内死亡：战利品随尸体带不出去（现有尸体流程本来就接管了背包），
     * 所以这里不改死亡逻辑，只记名单状态与事件日志。
     */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        RaidService.current().ifPresent(manifest -> {
            RaidRoster.mark(player.getServer(), player, RaidRoster.State.DEAD);
            RaidStatsLog.died(player.getServer(), manifest, player);
        });
    }

    /** 每 tick：撤离点粒子、撤离读条推进、掉线宽限到期检查。 */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ExtractionService.tickMarkers(event.getServer());
        ExtractionRunner.tick(event.getServer());
        RaidRoster.tickGrace(event.getServer());
    }
}
