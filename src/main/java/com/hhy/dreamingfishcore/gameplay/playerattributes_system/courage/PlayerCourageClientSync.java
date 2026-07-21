package com.hhy.dreamingfishcore.gameplay.playerattributes_system.courage;

import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.courage.network.Packet_SyncCourageData;
import net.minecraft.server.level.ServerPlayer;

public class PlayerCourageClientSync {
    public static void sendCourageDataToClient(ServerPlayer player, float currentCourage, float maxCourage) {
        // 构建勇气值同步数据包
        Packet_SyncCourageData packet = new Packet_SyncCourageData(currentCourage, maxCourage);
        // 发送数据包到指定玩家
        DreamingFishCore_NetworkManager.sendToClient(packet, player);
    }
}
