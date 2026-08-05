package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.network;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.PlayerInfectionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public class Packet_SyncInfectionData implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<Packet_SyncInfectionData> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.hhy.dreamingfishcore.DreamingFishCore.MODID, "playerattribute_system/infection_system/packet_sync_infection_data"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_SyncInfectionData> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, packet) -> Packet_SyncInfectionData.encode(packet, buf), Packet_SyncInfectionData::decode);

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }
    private final float currentInfection;
    private final boolean infected;

    public Packet_SyncInfectionData(float currentInfection, boolean infected) {
        this.currentInfection = currentInfection;
        this.infected = infected;
    }

    public static void encode(Packet_SyncInfectionData packet, FriendlyByteBuf buf) {
        buf.writeFloat(packet.currentInfection);
        buf.writeBoolean(packet.infected);
    }

    public static Packet_SyncInfectionData decode(FriendlyByteBuf buf) {
        float current = buf.readFloat();
        boolean infected = buf.readBoolean();
        return new Packet_SyncInfectionData(current, infected);
    }

    public static void handle(Packet_SyncInfectionData packet, IPayloadContext context) {
        final float safeCurrentInfection = packet.currentInfection;
        final boolean safeInfected = packet.infected;

        context.enqueueWork(() -> processOnMainThread(safeCurrentInfection, safeInfected));
    }

    private static void processOnMainThread(float currentInfection, boolean infected) {
        new ClientRunnable(currentInfection, infected).run();
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientRunnable implements Runnable {
        private final float currentInfection;
        private final boolean infected;

        public ClientRunnable(float currentInfection, boolean infected) {
            this.currentInfection = currentInfection;
            this.infected = infected;
        }

        @Override
        public void run() {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            Player player = minecraft.player;
            if (player == null) return;

            PlayerInfectionManager.setInfectionDataClient(player, this.currentInfection, this.infected);
        }
    }

    public float getCurrentInfection() {
        return currentInfection;
    }

    public boolean isInfected() {
        return infected;
    }
}
