package com.hhy.dreamingfishcore.gameplay.task_system.network;


import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;


//增量更新包
public class Packet_SyncUpdateTask implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<Packet_SyncUpdateTask> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.hhy.dreamingfishcore.DreamingFishCore.MODID, "task_system/packet_sync_update_task"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_SyncUpdateTask> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, packet) -> Packet_SyncUpdateTask.encode(packet, buf), Packet_SyncUpdateTask::decode);

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(Packet_SyncUpdateTask packet, FriendlyByteBuf buf) {
    }

    public static Packet_SyncUpdateTask decode(FriendlyByteBuf buf) {
        return new Packet_SyncUpdateTask();
    }

    public static void handle(Packet_SyncUpdateTask packet, IPayloadContext context) {
    }
}
