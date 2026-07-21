package com.hhy.dreamingfishcore.gameplay.task_system.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class Packet_SyncUpdateTask {
    public static void encode(Packet_SyncUpdateTask packet, FriendlyByteBuf buf) {
    }

    public static Packet_SyncUpdateTask decode(FriendlyByteBuf buf) {
        return new Packet_SyncUpdateTask();
    }

    public static void handle(Packet_SyncUpdateTask packet, Supplier<NetworkEvent.Context> contextSupplier) {
        contextSupplier.get().setPacketHandled(true);
    }
}
