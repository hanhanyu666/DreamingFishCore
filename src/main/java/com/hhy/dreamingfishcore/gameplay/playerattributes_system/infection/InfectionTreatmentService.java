package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 感染身份变化的唯一服务端入口。
 *
 * <p>四状态（幸存者 → 不稳定感染者 → 稳定感染者 → 传播复发）之间的每一次转换都必须经过这里，
 * 这样"身份变化与治疗由服务端验证、重启后保留"这条要求只需要在一个地方保证：每个方法都会
 * 校验会话、写入玩家档案、推送客户端同步，并在需要时使用活动时钟记录期限。</p>
 *
 * <p>分层治疗（ADR 0005）：
 * <ul>
 *   <li>{@link #applySuppressant} 抑制剂 —— 只对尚未突变的幸存者降低感染值；</li>
 *   <li>{@link #applyEarlyReversal} 早期逆转 —— 不稳定感染者恢复为幸存者（成本较低）；</li>
 *   <li>{@link #applyStabilization} 稳定治疗 —— 不稳定感染者稳定下来成为稳定感染者，
 *       或让处于传播复发的稳定感染者结束复发；</li>
 *   <li>{@link #applyReconstruction} 高成本重构 —— 稳定感染者恢复为幸存者。</li>
 * </ul>
 * 所有正规疗程结果确定，不使用随机失败；代价（设施、资源、时间、冷却）由内容层提供，
 * 本里程碑只提供动作接口。</p>
 */
public final class InfectionTreatmentService {
    private InfectionTreatmentService() {
    }

    /** 一次治疗动作的结果，供调用方决定提示文案与是否消耗物品。 */
    public enum TreatmentOutcome {
        /** 已生效。 */
        APPLIED,
        /** 身份正确、但没有需要改变的内容（例如感染值已经是 0）。 */
        NOTHING_TO_DO,
        /** 身份不适用该疗程。 */
        WRONG_IDENTITY,
        /** 会话未通过认证。 */
        NOT_AUTHENTICATED,
        /** 玩家档案尚未加载或不存在。 */
        NOT_LOADED
    }

    // ==================== 分层治疗动作 ====================

    /** 抑制剂：降低幸存者的感染值；已经突变（不稳定/稳定/复发）的玩家不使用抑制剂。 */
    public static TreatmentOutcome applySuppressant(ServerPlayer player) {
        return suppressSurvivor(player, InfectionRules.SUPPRESSANT_INFECTION_REDUCTION);
    }

    /**
     * 足量抑制剂：把幸存者的感染值一次降到 0。
     *
     * <p>剧情发放的「基因复苏试剂」属于这一档：它给尚未突变的居民一次性清除感染读数，
     * 与 ADR 0005 的"抑制剂降低感染值"是同一条规则的不同剂量。</p>
     */
    public static TreatmentOutcome applyFullSuppressant(ServerPlayer player) {
        PlayerAttributesData data = resolve(player);
        if (data == null) {
            return notReady(player);
        }
        if (data.getInfectionIdentity() != InfectionIdentity.SURVIVOR) {
            return TreatmentOutcome.WRONG_IDENTITY;
        }
        if (data.getCurrentInfection() <= 0.0F) {
            return TreatmentOutcome.NOTHING_TO_DO;
        }
        data.setCurrentInfection(0.0F);
        commit(player, data, null);
        DreamingFishCore.LOGGER.info("足量抑制剂生效：玩家 {} 的感染值已清零", player.getScoreboardName());
        return TreatmentOutcome.APPLIED;
    }

    private static TreatmentOutcome suppressSurvivor(ServerPlayer player, float amount) {
        PlayerAttributesData data = resolve(player);
        if (data == null) {
            return notReady(player);
        }
        if (data.getInfectionIdentity() != InfectionIdentity.SURVIVOR) {
            return TreatmentOutcome.WRONG_IDENTITY;
        }
        if (data.getCurrentInfection() <= 0.0F) {
            return TreatmentOutcome.NOTHING_TO_DO;
        }
        // reduceInfection 已经负责下限钳制、警告档位回退与客户端同步。
        PlayerInfectionManager.reduceInfection(player, amount);
        DreamingFishCore.LOGGER.info("抑制剂生效：玩家 {} 的感染值降至 {}",
                player.getScoreboardName(), String.format("%.2f", data.getCurrentInfection()));
        return TreatmentOutcome.APPLIED;
    }

    /** 早期逆转：不稳定感染者恢复为幸存者。 */
    public static TreatmentOutcome applyEarlyReversal(ServerPlayer player) {
        PlayerAttributesData data = resolve(player);
        if (data == null) {
            return notReady(player);
        }
        if (data.getInfectionIdentity() != InfectionIdentity.UNSTABLE) {
            return TreatmentOutcome.WRONG_IDENTITY;
        }
        clearToSurvivor(data);
        commit(player, data, "§a早期逆转完成，你已经恢复为幸存者。");
        DreamingFishCore.LOGGER.info("早期逆转完成：玩家 {} 恢复为幸存者", player.getScoreboardName());
        return TreatmentOutcome.APPLIED;
    }

    /**
     * 稳定治疗：把不稳定感染者稳定为稳定感染者。
     *
     * <p>对处于传播复发的稳定感染者，稳定治疗的作用是结束这次复发（并进入复发冷却），
     * 这正是"传播复发可以被结束"的动作入口。</p>
     */
    public static TreatmentOutcome applyStabilization(ServerPlayer player) {
        return applyStabilization(player, "§e突变已经稳定下来：你现在是稳定感染者。");
    }

    /**
     * 稳定治疗的重载：由调用方决定成功提示。
     *
     * <p>治疗窗口到期（漏做治疗）与主动稳定治疗都会走到这里，但两件事的叙事分量不同，
     * 因此提示文案由调用方给出。</p>
     */
    public static TreatmentOutcome applyStabilization(ServerPlayer player, String successMessage) {
        PlayerAttributesData data = resolve(player);
        if (data == null) {
            return notReady(player);
        }
        InfectionIdentity identity = data.getInfectionIdentity();
        if (identity == InfectionIdentity.RELAPSE) {
            endRelapse(player, data, true);
            commit(player, data, successMessage);
            DreamingFishCore.LOGGER.info("稳定治疗结束传播复发：玩家 {}", player.getScoreboardName());
            return TreatmentOutcome.APPLIED;
        }
        if (identity != InfectionIdentity.UNSTABLE) {
            return TreatmentOutcome.WRONG_IDENTITY;
        }
        data.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_TWO);
        data.setCurrentInfection(PlayerInfectionManager.getInfectionMaximum(player, data));
        data.clearInfectionTreatmentDeadline();
        commit(player, data, successMessage);
        DreamingFishCore.LOGGER.info("稳定治疗完成：玩家 {} 成为稳定感染者", player.getScoreboardName());
        return TreatmentOutcome.APPLIED;
    }

    /** 高成本重构：稳定感染者（含传播复发）恢复为幸存者。 */
    public static TreatmentOutcome applyReconstruction(ServerPlayer player) {
        PlayerAttributesData data = resolve(player);
        if (data == null) {
            return notReady(player);
        }
        if (!data.getInfectionIdentity().isStabilized()) {
            return TreatmentOutcome.WRONG_IDENTITY;
        }
        clearToSurvivor(data);
        commit(player, data, "§a重构完成，你已经恢复为幸存者。");
        DreamingFishCore.LOGGER.info("重构疗程完成：玩家 {} 恢复为幸存者", player.getScoreboardName());
        return TreatmentOutcome.APPLIED;
    }

    // ==================== 身份推进（非治疗） ====================

    /**
     * 幸存者跨过阈值成为不稳定感染者。
     *
     * <p>面具阶段的新晋不稳定感染者同时获得个人治疗窗口；截止时间由
     * {@code PlayerInfectionManager.tickTreatmentWindows} 到期后转为稳定治疗。</p>
     *
     * @return 是否发生了身份变化
     */
    public static boolean becomeUnstable(ServerPlayer player, PlayerAttributesData data,
                                        boolean postMaskEra, long activeTick) {
        if (player == null || data == null || !AuthSessionGuard.isAuthenticated(player)) {
            return false;
        }
        if (data.isInfected()) {
            return false;
        }
        data.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_ONE);
        if (postMaskEra && activeTick >= 0L) {
            data.startInfectionTreatmentWindow(
                    activeTick + PlayerInfectionManager.NEW_LEVEL_ONE_TREATMENT_WINDOW_TICKS);
        }
        return true;
    }

    /**
     * 稳定感染者因重伤或高污染刺激进入传播复发。
     *
     * @return 是否真的开始了复发
     */
    public static boolean beginRelapse(ServerPlayer player) {
        PlayerAttributesData data = resolve(player);
        if (data == null) {
            return false;
        }
        long activeTick = currentActiveTick();
        if (activeTick < 0L) {
            // 活动时钟不可用时不要排定期限，否则复发可能永远不结束。
            return false;
        }
        if (!data.isStableInfected()) {
            return false;
        }
        if (data.isRelapseCoolingDown()) {
            return false;
        }
        if (!data.beginRelapse(activeTick + InfectionRules.RELAPSE_DURATION_TICKS)) {
            return false;
        }
        commit(player, data, "§4你的身体再次释放异常因子：进入传播复发。");
        DreamingFishCore.LOGGER.info("玩家 {} 进入传播复发（{} tick 后结束）",
                player.getScoreboardName(), InfectionRules.RELAPSE_DURATION_TICKS);
        return true;
    }

    /** 结束传播复发；{@code coolingDown} 决定是否进入冷却期。 */
    public static void endRelapse(ServerPlayer player, PlayerAttributesData data, boolean coolingDown) {
        if (player == null || data == null) {
            return;
        }
        data.endRelapse();
        if (coolingDown) {
            long activeTick = currentActiveTick();
            if (activeTick >= 0L) {
                data.setRelapseCooldownUntilActiveTick(
                        activeTick + InfectionRules.RELAPSE_COOLDOWN_TICKS);
            }
        }
    }

    /**
     * 每个服务器 tick 检查复发到期与冷却到期。
     *
     * <p>复发到点自动结束，玩家不会因为离线或忘记处理而永久保持传播能力。</p>
     */
    public static void tickRelapseWindows(MinecraftServer server) {
        if (server == null || !PlayerAttributesDataManager.isLoaded()) {
            return;
        }
        long activeTick = currentActiveTick();
        if (activeTick < 0L) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!AuthSessionGuard.isAuthenticated(player)) {
                continue;
            }
            PlayerAttributesData data = PlayerAttributesDataManager
                    .findStoredPlayerAttributesData(player.getUUID());
            if (data == null) {
                continue;
            }
            boolean changed = false;
            if (data.hasActiveRelapseWindow() && data.getRelapseUntilActiveTick() <= activeTick) {
                endRelapse(player, data, true);
                changed = true;
                player.displayClientMessage(
                        Component.literal("§e异常因子重新沉寂，传播复发已经结束。"), false);
                DreamingFishCore.LOGGER.info("玩家 {} 的传播复发已到期结束", player.getScoreboardName());
            }
            if (data.isRelapseCoolingDown() && data.getRelapseCooldownUntilActiveTick() <= activeTick) {
                data.setRelapseCooldownUntilActiveTick(-1L);
                changed = true;
            }
            if (changed) {
                commit(player, data, null);
            }
        }
    }

    // ==================== 内部工具 ====================

    private static void clearToSurvivor(PlayerAttributesData data) {
        data.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_NONE);
        data.setCurrentInfection(0.0F);
        data.clearInfectionTreatmentDeadline();
        data.clearRelapseState();
    }

    private static PlayerAttributesData resolve(ServerPlayer player) {
        if (player == null || !AuthSessionGuard.isAuthenticated(player)
                || !PlayerAttributesDataManager.isLoaded()) {
            return null;
        }
        return PlayerAttributesDataManager.findStoredPlayerAttributesData(player.getUUID());
    }

    private static TreatmentOutcome notReady(ServerPlayer player) {
        if (player != null && !AuthSessionGuard.isAuthenticated(player)) {
            return TreatmentOutcome.NOT_AUTHENTICATED;
        }
        return TreatmentOutcome.NOT_LOADED;
    }

    /** 写入档案、同步客户端，并按需给出提示。 */
    private static void commit(ServerPlayer player, PlayerAttributesData data, String message) {
        PlayerAttributesDataManager.updatePlayerAttributesData(player, data);
        syncIdentity(player, data);
        if (message != null) {
            player.displayClientMessage(Component.literal(message), false);
        }
    }

    /** 身份同步的唯一出口；复发窗口必须一起下发，否则客户端算不出"传播复发"。 */
    public static void syncIdentity(ServerPlayer player, PlayerAttributesData data) {
        if (player == null || data == null) {
            return;
        }
        PlayerInfectionClientSync.sendInfectionDataToClient(
                player,
                data.getCurrentInfection(),
                data.isInfected(),
                data.getInfectionLevel(),
                PlayerInfectionManager.getInfectionMaximum(player, data),
                data.hasActiveRelapseWindow());
    }

    /**
     * 当前活动 tick；故事运行时不可用时返回 -1，调用方必须据此放弃排期。
     *
     * <p>不复用"异常时按 0 处理"的旧写法：0 会让复发窗口立刻到期，比不排期更糟。</p>
     */
    public static long currentActiveTick() {
        try {
            return StoryManager.getSnapshot().activeTicks();
        } catch (RuntimeException exception) {
            return -1L;
        }
    }
}
