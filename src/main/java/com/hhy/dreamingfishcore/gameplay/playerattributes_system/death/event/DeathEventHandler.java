package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.PendingDeathData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.DeathCorpseManager;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.network.Packet_DeathScreenData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.UUID;

//幸存者死亡，可以花费额外点数让死亡不掉落
//感染身份按 ADR 0016 分档：幸存者 5 / 稳定感染者 10 / 不稳定感染者 20，保留物品栏额外 +30
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public class DeathEventHandler {

    //死亡消耗的唯一来源是 InfectionRules（见下方 getNormalCost 的说明），这里不再保留第二份常量。

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        // 未认证玩家虽然暂时处于旁观模式，但不能借助死亡事件修改复活点、
        // 创建尸体或触发死亡封禁。
        if (!AuthSessionGuard.isAuthenticated(serverPlayer)) {
            return;
        }

        // NeoForge 的玩家死亡链可能重复派发 LivingDeathEvent；同一次捕获只结算一次。
        if (DeathCorpseManager.isDeathConfigured(serverPlayer)) {
            return;
        }

        // 自定义尸体 Mixin 会在本次死亡中接管原版掉落；复活物品由
        // CustomRespawnInventoryManager 按玩家一次性复制，不再修改全局 keepInventory。

        UUID deathPlayerUUID = serverPlayer.getUUID();
        UserBanList banList = serverPlayer.server.getPlayerList().getBans();

        // 缺失属性档案时不能使用临时默认值创建一条没有结算记录的尸体；
        // 让原版掉落规则接管，并保留后续人工修复数据的机会。
        PlayerAttributesData deathPlayerAttributesData =
                PlayerAttributesDataManager.findStoredPlayerAttributesData(deathPlayerUUID);
        if (deathPlayerAttributesData == null) {
            DeathCorpseManager.finishCapture(serverPlayer);
            DreamingFishCore.LOGGER.error("玩家 {} 死亡时缺少属性数据，未创建待处理死亡记录",
                    serverPlayer.getScoreboardName());
            return;
        }

        boolean isInfected = deathPlayerAttributesData.isInfected();
        float currentRespawnPoint = deathPlayerAttributesData.getRespawnPoint();

        // 计算消耗（按感染身份分档）
        float respawnCost = getNormalCost(deathPlayerAttributesData.getInfectionIdentity());
        boolean awaitingChoice = com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.TemplateReconstructionRules.canReconstruct(currentRespawnPoint);
        UUID corpseId = DeathCorpseManager.configureCapture(serverPlayer, awaitingChoice);

        // 余量大于零就可以完成最后一次标准重建；耗尽之后的下一次死亡才停止重建。
        if (!awaitingChoice) {
            PendingDeathData.DeathLocation corpseLocation =
                    DeathCorpseManager.getPlannedCorpseLocation(serverPlayer);
            String banReason = buildRespawnExhaustedReason(corpseLocation);

            // 复活玩家（不扣除点数），避免重连时显示死亡界面
            deathPlayerAttributesData.syncMaxHealthToPlayer(serverPlayer);
            float maxHealth = serverPlayer.getMaxHealth();
            serverPlayer.setHealth(maxHealth);
            serverPlayer.deathTime = 0;

            // 传送到复活点
            teleportToRespawnPosition(serverPlayer);

            // 清除死亡待处理标记（物品已掉落，不需要再显示死亡界面）
            clearDeathState(serverPlayer);

            UserBanListEntry banEntry = new UserBanListEntry(
                    serverPlayer.getGameProfile(),
                    null,
                    "DeathSystem",
                    null,
                    banReason
            );
            banList.add(banEntry);

            BlockPos corpsePos = BlockPos.containing(
                    corpseLocation.x(), corpseLocation.y(), corpseLocation.z());
            DreamingFishCore.LOGGER.info(
                    "玩家 {} 复活点数不足({})，物品已转入 {} 的尸体 {} {} {}，已被封禁",
                    serverPlayer.getScoreboardName(), currentRespawnPoint,
                    corpseLocation.dimension(), corpsePos.getX(), corpsePos.getY(), corpsePos.getZ());

            // 立即踢出玩家
            serverPlayer.connection.disconnect(Component.literal(banReason));
            return;
        }

        // 复活点足够，发送死亡屏幕数据包
        Component deathMessage = serverPlayer.getCombatTracker().getDeathMessage();

        // 获取死亡位置
        double deathX = serverPlayer.getX();
        double deathY = serverPlayer.getY();
        double deathZ = serverPlayer.getZ();
        String dimension = serverPlayer.level().dimension().location().toString();

        // 玩家 NBT 只保存结算状态和尸体引用；物品由死亡位置的尸体实体持久化。
        float keepInventoryCost = getKeepInventoryCost(deathPlayerAttributesData.getInfectionIdentity());
        UUID deathId = PendingDeathData.begin(
                serverPlayer,
                currentRespawnPoint,
                respawnCost,
                keepInventoryCost,
                isInfected,
                deathMessage,
                corpseId);

        Packet_DeathScreenData packet = new Packet_DeathScreenData(
                currentRespawnPoint,
                respawnCost,
                keepInventoryCost,
                isInfected,
                deathMessage,
                deathX,
                deathY,
                deathZ,
                dimension,
                deathId
        );
        DreamingFishCore_NetworkManager.sendToClient(packet, serverPlayer);

//        DreamingFishCore.LOGGER.info("玩家 {} 死亡状态已持久化，位置: {} {} {}",
//                serverPlayer.getScoreboardName(), dimension, (int)deathX, (int)deathY, (int)deathZ);
    }

    /**
     * 清除玩家的死亡状态
     */
    public static void clearDeathState(ServerPlayer player) {
        PendingDeathData.clear(player);
        DreamingFishCore.LOGGER.info("玩家 {} 的死亡状态已清除", player.getScoreboardName());
    }

    /**
     * 检查玩家是否有未处理的死亡状态
     */
    public static boolean hasDeathState(ServerPlayer player) {
        return PendingDeathData.hasPending(player);
    }

    /**
     * 恢复玩家的死亡状态，发送死亡数据包
     * 注意：使用玩家当前的实际复活点数，而不是存储的旧值
     */
    public static void restoreDeathState(ServerPlayer player) {
        if (!hasDeathState(player)) {
            return;
        }
        PendingDeathData.recoverInterruptedResolution(player);

        sendDeathScreenData(player);

        DreamingFishCore.LOGGER.info("玩家 {} 的死亡状态已恢复，当前复活点: {}",
                player.getScoreboardName(),
                PlayerAttributesDataManager.getPlayerAttributesData(player.getUUID()) == null
                        ? "未知"
                        : PlayerAttributesDataManager.getPlayerAttributesData(player.getUUID()).getRespawnPoint());
    }

    /** 尸体实体生成或被安全迁移后，刷新死亡界面中的实际尸体位置。 */
    public static void refreshDeathScreenData(ServerPlayer player) {
        if (!hasDeathState(player)) {
            return;
        }
        sendDeathScreenData(player);
    }

    private static void sendDeathScreenData(ServerPlayer player) {

        // 获取玩家当前的实际属性数据
        PlayerAttributesData data = PlayerAttributesDataManager.getPlayerAttributesData(player.getUUID());
        if (data == null) {
            DreamingFishCore.LOGGER.warn("玩家 {} 重连时无法获取属性数据", player.getScoreboardName());
            return;
        }

        // 使用当前复活点数和感染身份
        float currentRespawnPoint = data.getRespawnPoint();
        boolean isInfected = data.isInfected();

        // 根据当前状态重新计算消耗
        float normalCost = getNormalCost(data.getInfectionIdentity());
        float keepInventoryCost = getKeepInventoryCost(data.getInfectionIdentity());

        PendingDeathData.DeathLocation corpseLocation = PendingDeathData.getCorpseLocation(player);
        double deathX = corpseLocation.x();
        double deathY = corpseLocation.y();
        double deathZ = corpseLocation.z();
        String dimension = corpseLocation.dimension();
        Component deathMessage = PendingDeathData.getDeathMessage(player);
        UUID deathId = PendingDeathData.getDeathId(player);

        // 发送死亡界面数据包（使用当前数据）
        Packet_DeathScreenData packet = new Packet_DeathScreenData(
                currentRespawnPoint,  // 使用当前复活点数
                normalCost,            // 重新计算的消耗
                keepInventoryCost,     // 重新计算的消耗
                isInfected,            // 当前感染状态
                deathMessage,
                deathX,
                deathY,
                deathZ,
                dimension,
                deathId
        );
        DreamingFishCore_NetworkManager.sendToClient(packet, player);
    }

    //获取正常复活消耗：数值唯一来源是 InfectionRules，界面预览与服务端扣费共用
    public static float getNormalCost(
            com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionIdentity identity) {
        return com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionRules
                .respawnCost(identity);
    }

    //获取保留物品复活消耗
    public static float getKeepInventoryCost(
            com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionIdentity identity) {
        return com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionRules
                .keepInventoryCost(identity);
    }

    private static String buildRespawnExhaustedReason(PendingDeathData.DeathLocation corpseLocation) {
        BlockPos position = BlockPos.containing(
                corpseLocation.x(), corpseLocation.y(), corpseLocation.z());
        String dimension = switch (corpseLocation.dimension()) {
            case "minecraft:overworld" -> "主世界";
            case "minecraft:the_nether" -> "下界";
            case "minecraft:the_end" -> "末地";
            default -> corpseLocation.dimension();
        };
        return "§c您的模板重建余量已耗尽，请等待其他居民协助救援"
                + "\n§7尸体位置：" + dimension
                + " X:" + position.getX()
                + " Y:" + position.getY()
                + " Z:" + position.getZ();
    }

    /**
     * 将玩家传送到其复活点（床/重生锚/世界出生点）
     */
    private static void teleportToRespawnPosition(ServerPlayer player) {
        // 获取玩家的复活点设置
        BlockPos respawnPos = player.getRespawnPosition();
        ResourceKey<Level> respawnDim = player.getRespawnDimension();
        float respawnAngle = player.getRespawnAngle();

        ServerLevel targetLevel;
        Vec3 targetPos;

        // 尝试使用玩家设置的复活点（床/重生锚）
        if (respawnPos != null && respawnDim != null) {
            targetLevel = player.server.getLevel(respawnDim);
            if (targetLevel != null) {
                DimensionTransition transition = player.findRespawnPositionAndUseSpawnBlock(
                        false, DimensionTransition.DO_NOTHING);
                if (!transition.missingRespawnBlock()) {
                    targetLevel = transition.newLevel();
                    targetPos = transition.pos();
                    player.teleportTo(targetLevel, targetPos.x, targetPos.y, targetPos.z, respawnAngle, 0);
                    DreamingFishCore.LOGGER.info("玩家 {} 已传送到复活点: {} {} {}",
                            player.getScoreboardName(), (int)targetPos.x, (int)targetPos.y, (int)targetPos.z);
                    return;
                }
            }
        }

        // 没有有效复活点，传送到世界出生点
        targetLevel = player.server.overworld();
        BlockPos spawnPos = targetLevel.getSharedSpawnPos();
        targetPos = Vec3.atBottomCenterOf(spawnPos);
        player.teleportTo(targetLevel, targetPos.x, targetPos.y, targetPos.z, 0, 0);
        DreamingFishCore.LOGGER.info("玩家 {} 已传送到世界出生点: {} {} {}",
                player.getScoreboardName(), spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
    }

}
