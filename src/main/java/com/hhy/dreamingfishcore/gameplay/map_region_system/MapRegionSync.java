package com.hhy.dreamingfishcore.gameplay.map_region_system;

import com.hhy.dreamingfishcore.gameplay.map_region_system.network.Packet_MapRegionSnapshot;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 服务端：把区域快照下发给玩家。
 *
 * <p>时机：**登录时**发一次（覆盖绝大多数情况），另外客户端可以主动请求一次
 * （{@code Packet_MapRegionRequest}，用于重开地图或重连后刷新）。</p>
 */
@EventBusSubscriber(modid = com.hhy.dreamingfishcore.DreamingFishCore.MODID)
public final class MapRegionSync {

    private MapRegionSync() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendTo(player);
        }
    }

    /** 汇总并发送给一个玩家。 */
    public static void sendTo(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        List<MapRegion> regions = MapRegionCollector.collect(player.getServer());
        PacketDistributor.sendToPlayer(player, new Packet_MapRegionSnapshot(regions));
    }

    /** 汇总并发送给所有在线玩家（区域数据变化时用）。 */
    public static void sendToAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTo(player);
        }
    }
}
