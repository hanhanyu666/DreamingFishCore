package com.hhy.dreamingfishcore.gameplay.spawner_system.network;

import com.hhy.dreamingfishcore.gameplay.spawner_system.SpawnerView;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * 刷怪箱的服务端下发入口集中封装。
 *
 * <p>统一走 {@code DreamingFishCore_NetworkManager.sendToClient}：它带认证门禁与异常兜底
 * （无头 gametest 的模拟连接没有协商通道，发不出去只会记警告，不影响服务端线程）。</p>
 */
public final class SpawnerSync {

    private SpawnerSync() {
    }

    public static void sendSnapshot(ServerPlayer player, SpawnerView view) {
        DreamingFishCore_NetworkManager.sendToClient(
                new Packet_SpawnerSnapshotResponse(view), player);
    }

    public static void sendResult(ServerPlayer player, boolean success, String message) {
        DreamingFishCore_NetworkManager.sendToClient(
                new Packet_SpawnerActionResult(success, message), player);
    }

    public static void openConfigScreen(ServerPlayer player, BlockPos pos) {
        DreamingFishCore_NetworkManager.sendToClient(
                new Packet_OpenSpawnerConfigGUI(pos), player);
    }
}
