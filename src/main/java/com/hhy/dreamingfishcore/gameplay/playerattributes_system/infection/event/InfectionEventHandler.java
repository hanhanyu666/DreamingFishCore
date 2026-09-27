package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.PlayerInfectionManager;
import com.hhy.dreamingfishcore.item.items.ProtectiveMaskItem;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 感染值事件处理
 * 处理玩家实际受伤和附近感染者传播导致的感染值变化。
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public class InfectionEventHandler {

    private static final int PROXIMITY_CHECK_INTERVAL = 400; // 20秒检查一次附近感染者（每次检查加1点）
    private static final double PROXIMITY_RADIUS = 32.0; // 检测范围：32格

    // 记录玩家在感染者附近的检查次数（用于每分钟提示一次）
    private static final Map<UUID, Integer> NEARBY_INFECTED_CHECK_COUNT = new ConcurrentHashMap<>();

    /**
     * 玩家 tick 事件只负责定时检查附近感染者。
     *
     * <p>感染值不再通过比较前后两次血量来推断，避免登录、重生、最大生命属性刷新
     * 或其他系统校正血量时被误判为受伤。实际受伤由 {@link LivingDamageEvent.Post}
     * 处理。</p>
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        
        if (event.getEntity().level().isClientSide() || !event.getEntity().isAlive()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !AuthSessionGuard.isAuthenticated(player)) {
            return;
        }
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) {
            return;
        }

        if (player.tickCount % PROXIMITY_CHECK_INTERVAL != 0) {
            return;
        }

        UUID playerUUID = player.getUUID();
        PlayerAttributesData attributesData = PlayerAttributesDataManager.getPlayerAttributesData(playerUUID);
        if (attributesData == null) {
            return;
        }

        // 检查附近是否有感染者，如果有则增加感染值；每20秒检查一次。
        checkNearbyInfectedPlayers(player, attributesData);
    }

    /**
     * 只在实体实际损失生命值后增加感染值。
     * LivingDamageEvent.Post 的 newDamage 是经过护甲、抗性和吸收结算后真正扣除的血量，
     * 不会把登录、退出、重生或最大生命值同步误认为伤害。
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
        if (attributesData == null || attributesData.isInfected()) {
            return;
        }

        float currentInfection = attributesData.getCurrentInfection();
        float infectionIncrease = healthLoss / 5.0F;
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
     * 检查附近是否有感染者，如果有则增加幸存者的感染值
     * @param player 幸存者玩家
     * @param attributesData 玩家属性数据
     */
    private static void checkNearbyInfectedPlayers(ServerPlayer player, PlayerAttributesData attributesData) {
        // 如果已经是感染者，不受影响
        if (attributesData.isInfected()) {
            return;
        }

        // 获取所有在线玩家
        var serverPlayers = player.server.getPlayerList().getPlayers();
        boolean hasNearbyInfected = false;
        boolean protectedByMask = ProtectiveMaskItem.isEquipped(player);

        for (ServerPlayer otherPlayer : serverPlayers) {
            // 跳过自己
            if (otherPlayer.getUUID().equals(player.getUUID())) {
                continue;
            }

            // 不同维度的坐标没有可比性，也不应产生感染接触。
            if (otherPlayer.level() != player.level()) {
                continue;
            }

            // 检查对方是否是感染者
            PlayerAttributesData otherAttributes = PlayerAttributesDataManager.getPlayerAttributesData(otherPlayer.getUUID());
            if (otherAttributes == null || !otherAttributes.isInfected()) {
                continue;
            }

            // 防护面具只阻断一级感染者的近距离传播；二级感染者仍然可以传播，
            // 因此不能在发现面具后直接结束整次扫描。
            // 拿到一件面具不等于全服已经进入面具阶段；只有首件面具实际发放后
            // 写入的世界事实才开启传播拦截。这样管理员提前测试/掉落的面具不会
            // 悄悄改变第一阶段的感染规则。
            if (PlayerInfectionManager.isPostMaskEraEnabled()
                    && isBlockedByProtectiveMask(otherAttributes, protectedByMask)) {
                continue;
            }

            // 检查距离
            double distance = player.position().distanceTo(otherPlayer.position());
            if (distance <= PROXIMITY_RADIUS) {
                hasNearbyInfected = true;
                break;
            }
        }

        // 如果附近有感染者，增加1点感染值
        if (hasNearbyInfected) {
            float currentInfection = attributesData.getCurrentInfection();
            PlayerInfectionManager.addInfection(player, 1.0F);
            float newInfection = attributesData.getCurrentInfection();

            if (newInfection > currentInfection) {

                // 增加检查计数
                UUID playerUUID = player.getUUID();
                int checkCount = NEARBY_INFECTED_CHECK_COUNT.getOrDefault(playerUUID, 0) + 1;
                NEARBY_INFECTED_CHECK_COUNT.put(playerUUID, checkCount);

                // 每2次检查（1分钟）发送一次提示
                if (checkCount % 2 == 0) {
                    player.displayClientMessage(
                            Component.literal("§c您附近有感染者，感染值在逐渐增加..."),
                            true
                    );
                }

                // addInfection 已经完成阈值转换和同步；达到阈值后清除附近提示计数。
                if (attributesData.isInfected()) {
                    NEARBY_INFECTED_CHECK_COUNT.remove(playerUUID);
                }
            }
        } else {
            // 附近没有感染者，清除计数
            NEARBY_INFECTED_CHECK_COUNT.remove(player.getUUID());
        }
    }

    /**
     * 面具传播策略的单一入口：只拦截一级感染者，绝不把二级感染者误判为安全来源。
     * 保持为无实体依赖的纯判定，方便在服务端扫描前进行测试。
     */
    static boolean isBlockedByProtectiveMask(PlayerAttributesData sourceAttributes,
                                              boolean protectedByMask) {
        return protectedByMask
                && sourceAttributes != null
                && sourceAttributes.isLevelOneInfected();
    }

}
