package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.ContactExposureTracker;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionIdentity;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionRules;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionTreatmentService;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.PlayerInfectionManager;
import com.hhy.dreamingfishcore.item.items.ProtectiveMaskItem;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 感染值事件处理：野外接触暴露、受伤导致的感染增长，以及重伤触发的传播复发。
 *
 * <p>野外传播在里程碑 1 从"靠近即加感染"改为 {@link InfectionRules 接触暴露}：
 * 幸存者在未受保护的区域持续靠近<b>具有传播能力</b>的感染者（不稳定感染者与传播复发者）时，
 * 先累积一段带警告的暴露量，攒满后才转化为实际感染增长，离开范围后逐渐衰减
 * （CONTEXT.md「接触暴露」，ADR 0003、ADR 0017）。</p>
 *
 * <p>稳定感染者在正常状态下不产生暴露：这是"稳定感染者不会持续感染队友"这条设定的落点。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public class InfectionEventHandler {

    /**
     * 玩家 tick：只负责按固定间隔推进一次接触暴露判定。
     *
     * <p>感染值不再通过比较前后两次血量来推断，避免登录、重生、最大生命属性刷新
     * 或其他系统校正血量时被误判为受伤。实际受伤由 {@link LivingDamageEvent.Post} 处理。</p>
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide() || !event.getEntity().isAlive()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !AuthSessionGuard.isAuthenticated(player)) {
            return;
        }
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) {
            // 创造模式不参与暴露累积；暴露量按"离开范围"处理自然衰减。
            ContactExposureTracker.advance(player.getUUID(), false);
            return;
        }
        if (player.tickCount % InfectionRules.EXPOSURE_INTERVAL_TICKS != 0) {
            return;
        }

        PlayerAttributesData attributesData = PlayerAttributesDataManager
                .getPlayerAttributesData(player.getUUID());
        if (attributesData == null) {
            return;
        }
        advanceContactExposure(player, attributesData);
    }

    /**
     * 只在实体实际损失生命值后处理感染。
     *
     * <p>幸存者按实际损失累积感染值；感染者不再累积感染值，但<b>重伤</b>可能让稳定感染者
     * 进入传播复发（ADR 0003、ADR 0016）。</p>
     */
    @SubscribeEvent
    public static void onPlayerDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity().level().isClientSide()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !AuthSessionGuard.isAuthenticated(player)
                || player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) {
            return;
        }

        float healthLoss = event.getNewDamage();
        if (!(healthLoss > 0.0F) || Float.isNaN(healthLoss) || Float.isInfinite(healthLoss)) {
            return;
        }

        PlayerAttributesData attributesData = PlayerAttributesDataManager
                .getPlayerAttributesData(player.getUUID());
        if (attributesData == null) {
            return;
        }

        if (attributesData.isInfected()) {
            maybeTriggerRelapse(player, attributesData, healthLoss);
            return;
        }

        float currentInfection = attributesData.getCurrentInfection();
        float infectionIncrease = infectionInputFor(healthLoss, player.getMaxHealth()) / 5.0F;
        PlayerInfectionManager.addInfection(player, infectionIncrease);
        float newInfection = attributesData.getCurrentInfection();
        if (Math.abs(newInfection - currentInfection) > 0.01F) {
            DreamingFishCore.LOGGER.info("感染值增加: 玩家{}, 实际生命损失:{}, 增加感染:{}, {}->{}",
                    player.getScoreboardName(), String.format("%.1f", healthLoss),
                    String.format("%.2f", infectionIncrease), String.format("%.2f", currentInfection),
                    String.format("%.2f", newInfection));
        }
    }

    /**
     * 把一次实际伤害折算成可用于感染累积的输入。
     *
     * <p>上限是玩家当前最大生命：单次伤害不可能"代表"超过一整条血条的感染输入。
     * 这不是理论上的洁癖——{@code /kill} 一类来源会用 {@link Float#MAX_VALUE} 结算伤害，
     * 若直接按它折算，幸存者会在一次自杀里瞬间跨过感染阈值（实测日志出现过
     * {@code 实际生命损失:3.4e38 → 0.00->100.00}），感染来源就此变得不可解释。</p>
     */
    static float infectionInputFor(float healthLoss, double maxHealth) {
        if (!(healthLoss > 0.0F) || Float.isNaN(healthLoss) || Float.isInfinite(healthLoss)) {
            return 0.0F;
        }
        if (!Double.isFinite(maxHealth) || maxHealth <= 0.0D) {
            return healthLoss;
        }
        return Math.min(healthLoss, (float) maxHealth);
    }

    /** 登出时清空暴露量：暴露描述的是现场风险，不该跨会话保留。 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ContactExposureTracker.clear(event.getEntity().getUUID());
    }

    /** 跨维度时坐标不再可比，暴露量随之作废（验收要求：不同维度不会错误累积）。 */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        ContactExposureTracker.clear(event.getEntity().getUUID());
    }

    /** 死亡与重生同样清空暴露量，避免复活后带着上一次的暴露继续计数。 */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        ContactExposureTracker.clear(event.getEntity().getUUID());
    }

    /** 停服时清空，防止同一进程内切换世界后残留旧记录。 */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ContactExposureTracker.clearAll();
    }

    /**
     * 推进一次接触暴露：先判断是否处在传播范围内，再累积/衰减，最后按档位警告或转化。
     */
    private static void advanceContactExposure(ServerPlayer player, PlayerAttributesData attributesData) {
        InfectionIdentity identity = attributesData.getInfectionIdentity();
        // 感染身份已经适应异常因子，不再累积暴露；这里仍然调用一次以让旧暴露量自然衰减。
        boolean exposed = identity == InfectionIdentity.SURVIVOR
                && hasSpreadingSourceNearby(player);
        applyExposureStep(player, attributesData, exposed);
    }

    /**
     * 执行一次暴露判定，并处理警告与转化。
     *
     * <p>{@code exposed} 由调用方给出，是为了让调试命令也能走这条完全相同的代码路径：
     * 验证警告阶梯与转化不需要真的站到另一名感染者旁边。</p>
     */
    static void applyExposureStep(ServerPlayer player, PlayerAttributesData attributesData, boolean exposed) {
        if (player == null || attributesData == null) {
            return;
        }
        ContactExposureTracker.Step step = ContactExposureTracker.advance(player.getUUID(), exposed);

        if (step.converts()) {
            PlayerInfectionManager.addInfection(player, InfectionRules.EXPOSURE_CONVERSION_INFECTION);
            player.displayClientMessage(
                    Component.literal("§4接触暴露已经转化为感染：你的感染值上升了。"), true);
            DreamingFishCore.LOGGER.info("玩家 {} 的接触暴露转化为感染，当前感染值 {}",
                    player.getScoreboardName(),
                    String.format("%.2f", attributesData.getCurrentInfection()));
            return;
        }
        if (step.escalated()) {
            player.displayClientMessage(Component.literal(exposureWarningText(step.warningStep())), true);
        }
    }

    /**
     * 调试命令入口：按指定次数强制推进暴露判定（视为始终处在传播范围内）。
     *
     * @return 推进后的暴露量
     */
    public static float debugAdvanceExposure(ServerPlayer player, int times) {
        if (player == null || times <= 0) {
            return 0.0F;
        }
        PlayerAttributesData data = PlayerAttributesDataManager
                .findStoredPlayerAttributesData(player.getUUID());
        if (data == null) {
            return 0.0F;
        }
        for (int i = 0; i < times; i++) {
            applyExposureStep(player, data, true);
        }
        return ContactExposureTracker.chargeOf(player.getUUID());
    }

    /** 调试命令入口：清除暴露量。 */
    public static void debugClearExposure(ServerPlayer player) {
        if (player != null) {
            ContactExposureTracker.clear(player.getUUID());
        }
    }

    private static String exposureWarningText(int warningStep) {
        return switch (warningStep) {
            case 1 -> "§e你正在接触异常因子，身体的异样正在累积...";
            case 2 -> "§c接触暴露持续累积，尽快离开具有传播能力的感染者。";
            case 3 -> "§4接触暴露已接近临界，再停留就会转化为感染！";
            default -> "";
        };
    }

    /**
     * 附近是否存在"具有传播能力"的感染者（不稳定感染者，或正在传播复发的稳定感染者）。
     *
     * <p>稳定感染者在正常状态下不产生暴露，因此"和稳定感染者正常相处"不会持续感染队友。</p>
     *
     * <p>待接入：ADR 0017 的聚居地抑制设备（领地空气过滤/异常因子抑制）尚未实装，
     * 它落地后应当在这里返回 true 以跳过设备覆盖范围内的来源，而不是改动上面的规则。</p>
     *
     * <p>包内可见是为了让 gametest 能直接用两名真实服务端玩家验证"稳定感染者不产生暴露、
     * 不稳定感染者产生暴露"这条共存规则，而不是只测一个谓词。</p>
     */
    static boolean hasSpreadingSourceNearby(ServerPlayer player) {
        // ADR 0017：领地内工作中的聚居地过滤装置会阻断被动接触传播。
        // 判定放在最前面：设备覆盖范围内直接不产生暴露，也就不必再扫玩家列表。
        if (com.hhy.dreamingfishcore.gameplay.organization_system.SettlementFilterService
                .isSuppressed(player)) {
            return false;
        }
        boolean protectedByMask = ProtectiveMaskItem.isEquipped(player);
        for (ServerPlayer otherPlayer : player.server.getPlayerList().getPlayers()) {
            if (otherPlayer.getUUID().equals(player.getUUID())) {
                continue;
            }
            // 不同维度的坐标没有可比性，也不应产生感染接触。
            if (otherPlayer.level() != player.level()) {
                continue;
            }
            PlayerAttributesData otherAttributes = PlayerAttributesDataManager
                    .findStoredPlayerAttributesData(otherPlayer.getUUID());
            if (otherAttributes == null) {
                continue;
            }
            // 只有不稳定感染者与传播复发者会释放异常因子。
            if (!otherAttributes.getInfectionIdentity().canSpread()) {
                continue;
            }
            // 防护面具只阻断传播来源的近距离影响；拿到一件面具不等于全服已经进入面具阶段，
            // 只有首件面具实际发放后写入的世界事实才开启拦截，避免管理员提前测试用的面具
            // 悄悄改变第一阶段的感染规则。
            if (PlayerInfectionManager.isPostMaskEraEnabled()
                    && isBlockedByProtectiveMask(otherAttributes, protectedByMask)) {
                continue;
            }
            if (player.position().distanceTo(otherPlayer.position()) <= InfectionRules.EXPOSURE_RADIUS) {
                return true;
            }
        }
        return false;
    }

    /**
     * 面具传播策略的单一入口：只拦截"当前具有传播能力"的来源。
     *
     * <p>稳定感染者在正常状态下不产生暴露，因此既不会被面具"放过"，也不会造成传播；
     * 传播复发的稳定感染者则和不稳定感染者一样会被面具拦下。</p>
     */
    static boolean isBlockedByProtectiveMask(PlayerAttributesData sourceAttributes,
                                             boolean protectedByMask) {
        return protectedByMask
                && sourceAttributes != null
                && sourceAttributes.getInfectionIdentity().canSpread();
    }

    /**
     * 重伤触发传播复发。
     *
     * <p>v1 只实现"单次实际损失达到阈值"这一条触发条件；设定里的"高污染刺激"需要等到
     * 污染区域实装后接入（见 {@link InfectionRules} 的说明），届时在这里追加判定即可。</p>
     */
    private static void maybeTriggerRelapse(ServerPlayer player, PlayerAttributesData attributesData,
                                            float healthLoss) {
        if (!attributesData.getInfectionIdentity().isStabilized()) {
            return;
        }
        if (!InfectionRules.shouldTriggerRelapse(
                healthLoss, attributesData.isRelapsing(), attributesData.isRelapseCoolingDown())) {
            return;
        }
        if (InfectionTreatmentService.beginRelapse(player)) {
            DreamingFishCore.LOGGER.info("玩家 {} 因单次损失 {} 点生命进入传播复发",
                    player.getScoreboardName(), String.format("%.1f", healthLoss));
        }
    }
}
