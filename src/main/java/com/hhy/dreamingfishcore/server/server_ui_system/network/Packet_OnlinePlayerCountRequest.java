package com.hhy.dreamingfishcore.server.server_ui_system.network;

import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import com.hhy.dreamingfishcore.server.server_ui_system.ServerTpsCalculator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.WeakHashMap;


/**
 * 低频服务器状态请求包（在线人数和 TPS 共用一次刷新）。
 */
public class Packet_OnlinePlayerCountRequest implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    private static final long MIN_REQUEST_INTERVAL_NANOS = 1_000_000_000L;
    // 请求只在服务器主线程的 enqueueWork 中处理；弱键避免玩家离线后
    // 为了这个低频状态查询长期持有玩家对象。
    private static final Map<ServerPlayer, Long> LAST_REQUEST_NANOS = new WeakHashMap<>();

    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<Packet_OnlinePlayerCountRequest> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.hhy.dreamingfishcore.DreamingFishCore.MODID, "packet_online_player_count_request"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_OnlinePlayerCountRequest> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, packet) -> Packet_OnlinePlayerCountRequest.encode(packet, buf), Packet_OnlinePlayerCountRequest::decode);

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }
    // 无参构造（客户端发送请求时无需传参）
    public Packet_OnlinePlayerCountRequest() {}

    // 编码
    public static void encode(Packet_OnlinePlayerCountRequest msg, FriendlyByteBuf buf) {}

    // 解码
    public static Packet_OnlinePlayerCountRequest decode(FriendlyByteBuf buf) {
        return new Packet_OnlinePlayerCountRequest();
    }

    // 服务端处理逻辑（读取在线人数和已经维护的 TPS 聚合值并返回）
    public static void handle(Packet_OnlinePlayerCountRequest msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 获取请求的玩家和服务器
            ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            sendCurrentStatus(player);
        });
    }

    /**
     * Sends one server status sample.  This is also used after authentication
     * so the first HUD value does not wait for the next polling interval.
     */
    public static void sendCurrentStatus(ServerPlayer player) {
        if (player == null || player.getServer() == null
                || !AuthSessionGuard.isAuthenticated(player)
                || !isRefreshAllowed(player)) return;

        // 实时获取服务端所有在线玩家数量（核心！）
        int onlinePlayerCount = player.getServer().getPlayerList().getPlayers().size();

        // 在线人数和 TPS 共用一次低频请求，避免为显示 TPS 增加逐 tick
        // 计算或额外的服务器广播任务。
        float serverTps = ServerTpsCalculator.calculate(player.getServer());

        // 发送响应包给客户端
        DreamingFishCore_NetworkManager.sendToClient(
                player,
                new Packet_OnlinePlayerCountResponse(onlinePlayerCount, serverTps)
        );
    }

    private static boolean isRefreshAllowed(ServerPlayer player) {
        synchronized (LAST_REQUEST_NANOS) {
            long now = System.nanoTime();
            Long last = LAST_REQUEST_NANOS.get(player);
            if (last != null && now - last < MIN_REQUEST_INTERVAL_NANOS) {
                return false;
            }

            LAST_REQUEST_NANOS.put(player, now);
            return true;
        }
    }
}
