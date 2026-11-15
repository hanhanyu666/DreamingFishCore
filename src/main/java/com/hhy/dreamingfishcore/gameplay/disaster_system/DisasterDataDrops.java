package com.hhy.dreamingfishcore.gameplay.disaster_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule.EventType;
import com.hhy.dreamingfishcore.item.DisasterDataItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * 「天灾分析数据」的来源。
 *
 * <p>两条来源对应两种玩法：</p>
 * <ul>
 *   <li><b>刷怪小概率</b>（{@link #MOB_KILL_CHANCE}）：日常打怪顺手摸到，类型随机；</li>
 *   <li><b>经历天灾大概率</b>（{@link #SURVIVED_CHANCE}）：天灾那天熬过去，第二天睁眼就有一份
 *       当天那种天灾的数据 —— 越是硬扛过的天灾，越有"下次可以清掉它"的回报。</li>
 * </ul>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class DisasterDataDrops {

    /** 击杀生物掉数据的概率。 */
    private static final double MOB_KILL_CHANCE = 0.03D;
    /** 经历一场天灾后拿到该天灾数据的概率。 */
    private static final double SURVIVED_CHANCE = 0.7D;

    private DisasterDataDrops() {
    }

    /** 玩家击杀生物时小概率掉一份随机类型的数据。 */
    @SubscribeEvent
    public static void onMobKilled(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || killer.isSpectator()) {
            return;
        }
        if (mob.getRandom().nextDouble() >= MOB_KILL_CHANCE) {
            return;
        }
        mob.spawnAtLocation(DisasterDataItem.create(randomType(mob.getRandom())), 0.4F);
    }

    /**
     * 天灾结束后，给"经历过"的在线玩家发该天灾的数据。
     *
     * <p>由 {@link DisasterService} 在跨天时调用一次（传进来刚结束的那一天的计划）。</p>
     */
    public static void rewardSurvivors(MinecraftServer server, DisasterSchedule.DayPlan finishedDay) {
        if (server == null || finishedDay == null || finishedDay.events().isEmpty()) {
            return;
        }
        for (EventType type : finishedDay.events()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.isSpectator() || player.getRandom().nextDouble() >= SURVIVED_CHANCE) {
                    continue;
                }
                ItemStack stack = DisasterDataItem.create(type);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§d[天灾] 你熬过了「" + type.displayName() + "」，捡到一份它的分析数据。"), true);
            }
        }
    }

    private static EventType randomType(net.minecraft.util.RandomSource random) {
        EventType[] values = EventType.values();
        return values[random.nextInt(values.length)];
    }
}
