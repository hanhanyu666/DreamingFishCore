package com.hhy.dreamingfishcore.network.packets;

import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.utils.Util_Player;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public class Packet_ServerPlayerListRequest {
    public Packet_ServerPlayerListRequest() {}

    public static void encode(Packet_ServerPlayerListRequest msg, FriendlyByteBuf buf) {
        // 无需数据
    }

    public static Packet_ServerPlayerListRequest decode(FriendlyByteBuf buf) {
        return new Packet_ServerPlayerListRequest();
    }

    public static void handle(Packet_ServerPlayerListRequest msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                List<Map.Entry<UUID, String>> accounts = Util_Player.getOnlinePlayerNames(player.server);
                DreamingFishCore_NetworkManager.sendToClient(player, new Packet_ServerPlayerListResponse(accounts));
            }
        });
        context.setPacketHandled(true);
    }
}
