package com.hhy.dreamingfishcore.gameplay.playerattributes_system.strength;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.strength.network.Packet_SyncStrengthData;
import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端体力同步工具类（主动发送同步包给客户端）
 */
public class StrengthSyncManager {
    /**
     * 服务端向指定玩家发送体力同步包（核心方法）
     */
    public static void syncStrengthToClient(ServerPlayer serverPlayer) {
        if (serverPlayer == null) return;

        // 1. 获取服务端真实体力数据（从PlayerData中读取，这是体力的唯一真实来源）
        PlayerAttributesData playerData = PlayerAttributesDataManager.getPlayerAttributesData(serverPlayer.getUUID());
        if (playerData == null) return;
        int currentStrength = playerData.getCurrentStrength();
        int maxStrength = playerData.getMaxStrength();

        // 2. 判断是否可以疾跑（体力是否≥20）
        boolean canSprint = currentStrength >= PlayerStrengthManager.MIN_RESPRINT_STRENGTH;

        // 3. 发送同步包（服务端→客户端）
        DreamingFishCore_NetworkManager.sendToClient(
                new Packet_SyncStrengthData(currentStrength, maxStrength, canSprint),
                serverPlayer
        );

        // 可选：打印日志，确认包已发送
        // System.out.println("服务端发送体力同步包：" + currentStrength + "/" + maxStrength);
    }
}
