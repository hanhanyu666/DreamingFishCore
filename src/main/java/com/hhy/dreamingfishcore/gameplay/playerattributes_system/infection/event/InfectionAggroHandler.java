package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.AggroPreferenceRules;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionIdentity;
import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieEntity;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 感染身份对丧尸仇恨的影响（ADR 0016）。
 *
 * <p>为什么挂在 {@link LivingChangeTargetEvent} 上：这是原版设置攻击目标的统一入口，改在这里
 * 就不需要替换任何 AI 目标，也不会和尸潮广播、声音锁定等既有目标来源打架。
 * 决策本身全是纯规则，放在 {@link AggroPreferenceRules} 里；这里只负责筛实体、算距离和落地结果。</p>
 *
 * <p>只处理稳定感染者这一种身份：幸存者照常被仇恨，这是与稳定感染者相对而言的"更容易吸引"，
 * 不稳定感染者和传播复发同样不做减免。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public class InfectionAggroHandler {

    /**
     * 丧尸把玩家选为目标时的仇恨软规则。
     *
     * <p>判断顺序按代价从低到高排：非丧尸 / 客户端世界 → 广播目标例外 → 目标不是合格玩家 →
     * 身份不是稳定感染者 → 才扫描在线玩家列表。绝大多数目标切换在前几步就退出了。</p>
     */
    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Zombie zombie) || zombie.level().isClientSide()) {
            return;
        }
        // 尸潮丧尸的声音/广播目标是显式下达的指令，比仇恨偏好更强；干预它会让攻城的
        // 合围目标被附近路过的幸存者悄悄改道。
        if (zombie instanceof SiegeZombieEntity siegeZombie && siegeZombie.hasActiveAlertTarget()) {
            return;
        }
        if (!(event.getNewAboutToBeSetTarget() instanceof ServerPlayer target)
                || !isCombatEligible(target)) {
            return;
        }

        PlayerAttributesData targetData = PlayerAttributesDataManager
                .findStoredPlayerAttributesData(target.getUUID());
        if (targetData == null) {
            return;
        }
        InfectionIdentity targetIdentity = targetData.getInfectionIdentity();
        if (targetIdentity != InfectionIdentity.STABLE) {
            return;
        }

        SurvivorCandidate candidate = findNearestSurvivor(zombie, target);
        double targetDistance = zombie.distanceTo(target);
        AggroPreferenceRules.Decision decision = AggroPreferenceRules.decide(
                targetIdentity,
                targetDistance,
                nearestSurvivorDistance(candidate),
                zombie.getRandom().nextFloat() < AggroPreferenceRules.DROP_CHANCE);

        if (decision == AggroPreferenceRules.Decision.REDIRECT && candidate != null) {
            event.setNewAboutToBeSetTarget(candidate.player());
            DreamingFishCore.LOGGER.debug("丧尸 {} 放弃稳定感染者 {}，转向更近的幸存者 {}（目标距离 {}，幸存者距离 {}）",
                    zombie.getUUID(), target.getScoreboardName(), candidate.player().getScoreboardName(),
                    formatDistance(targetDistance), formatDistance(candidate.distance()));
            return;
        }
        if (decision == AggroPreferenceRules.Decision.DROP) {
            event.setCanceled(true);
            DreamingFishCore.LOGGER.debug("丧尸 {} 放弃稳定感染者 {}（目标距离 {}，附近没有可转移的幸存者）",
                    zombie.getUUID(), target.getScoreboardName(), formatDistance(targetDistance));
        }
    }

    /**
     * 在线的、符合条件的幸存者里离丧尸最近的一个。
     *
     * <p>只统计与当前目标同维度的玩家：跨维度的坐标没有可比性，也不该把另一个世界的玩家
     * 卷进这场仇恨。返回 {@code null} 表示没有可转移对象。</p>
     */
    @Nullable
    private static SurvivorCandidate findNearestSurvivor(Zombie zombie, ServerPlayer target) {
        // 玩家列表从目标玩家身上取：能走到这里的目标一定已经在服务端世界里，不需要再判空维度。
        MinecraftServer server = target.server;
        SurvivorCandidate nearest = null;
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other.level() != target.level()
                    || other.getUUID().equals(target.getUUID())
                    || !isCombatEligible(other)) {
                continue;
            }
            PlayerAttributesData otherData = PlayerAttributesDataManager
                    .findStoredPlayerAttributesData(other.getUUID());
            if (otherData == null || !AggroPreferenceRules.isEligibleSurvivor(otherData.getInfectionIdentity())) {
                continue;
            }
            double distance = zombie.distanceTo(other);
            // 超出转移范围的距离在这里就丢弃：规则层不会接受它，留下只会污染"最近"的判断。
            if (!Double.isFinite(distance) || distance > AggroPreferenceRules.REDIRECT_RANGE) {
                continue;
            }
            if (nearest == null || distance < nearest.distance()) {
                nearest = new SurvivorCandidate(other, distance);
            }
        }
        return nearest;
    }

    /** 把"没有候选者"翻译成规则层的距离约定；规则层因此不需要可空的距离参数。 */
    private static double nearestSurvivorDistance(@Nullable SurvivorCandidate candidate) {
        return candidate == null ? AggroPreferenceRules.NO_SURVIVOR_DISTANCE : candidate.distance();
    }

    /**
     * 仇恨规则共同的玩家门槛：未认证、已死亡和创造/旁观模式都不参与仇恨结算。
     * 未认证玩家在登录流程里可能还处于旁观，放过他们会让登录界面变成安全屋。
     */
    private static boolean isCombatEligible(ServerPlayer player) {
        if (player == null || !player.isAlive() || !AuthSessionGuard.isAuthenticated(player)) {
            return false;
        }
        GameType gameType = player.gameMode.getGameModeForPlayer();
        return gameType != GameType.CREATIVE && gameType != GameType.SPECTATOR;
    }

    /** 距离保留一位小数，日志读起来稳定；只在真正改目标/放弃目标的分支里调用。 */
    private static String formatDistance(double distance) {
        return String.format("%.1f", distance);
    }

    /** 最近幸存者的引用和距离必须一起带出来，否则改目标时还得再扫一遍玩家列表。 */
    private record SurvivorCandidate(ServerPlayer player, double distance) {
    }
}
