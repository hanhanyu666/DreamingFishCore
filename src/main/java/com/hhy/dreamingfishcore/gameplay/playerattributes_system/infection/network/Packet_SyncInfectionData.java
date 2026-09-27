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
    private final int infectionLevel;
    private final int infectionMaximum;

    public Packet_SyncInfectionData(float currentInfection, boolean infected) {
        this(currentInfection, infected, infected ? 1 : 0, 100);
    }

    public Packet_SyncInfectionData(float currentInfection, boolean infected, int infectionLevel) {
        this(currentInfection, infected, infectionLevel, infectionLevel == 2 ? 200 : 100);
    }

    public Packet_SyncInfectionData(float currentInfection, boolean infected,
                                    int infectionLevel, int infectionMaximum) {
        this.currentInfection = currentInfection;
        this.infected = infected;
        this.infectionLevel = Math.max(0, Math.min(infectionLevel, 2));
        this.infectionMaximum = infectionMaximum >= 200 ? 200 : 100;
    }

    public static void encode(Packet_SyncInfectionData packet, FriendlyByteBuf buf) {
        buf.writeFloat(packet.currentInfection);
        buf.writeBoolean(packet.infected);
        buf.writeVarInt(packet.infectionLevel);
        buf.writeVarInt(packet.infectionMaximum);
    }

    public static Packet_SyncInfectionData decode(FriendlyByteBuf buf) {
        float current = buf.readFloat();
        boolean infected = buf.readBoolean();
        int infectionLevel = buf.readVarInt();
        int infectionMaximum = buf.readVarInt();
        return new Packet_SyncInfectionData(current, infected, infectionLevel, infectionMaximum);
    }

    public static void handle(Packet_SyncInfectionData packet, IPayloadContext context) {
        final float safeCurrentInfection = packet.currentInfection;
        final boolean safeInfected = packet.infected;
        final int safeInfectionLevel = packet.infectionLevel;
        final int safeInfectionMaximum = packet.infectionMaximum;

        context.enqueueWork(() -> processOnMainThread(
                safeCurrentInfection, safeInfected, safeInfectionLevel, safeInfectionMaximum));
    }

    private static void processOnMainThread(
            float currentInfection, boolean infected, int infectionLevel, int infectionMaximum) {
        new ClientRunnable(currentInfection, infected, infectionLevel, infectionMaximum).run();
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientRunnable implements Runnable {
        private final float currentInfection;
        private final boolean infected;
        private final int infectionLevel;
        private final int infectionMaximum;

        public ClientRunnable(float currentInfection, boolean infected,
                              int infectionLevel, int infectionMaximum) {
            this.currentInfection = currentInfection;
            this.infected = infected;
            this.infectionLevel = infectionLevel;
            this.infectionMaximum = infectionMaximum;
        }

        @Override
        public void run() {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            Player player = minecraft.player;
            if (player == null) return;

            PlayerInfectionManager.setInfectionDataClient(
                    player, this.currentInfection, this.infected,
                    this.infectionLevel, this.infectionMaximum);
        }
    }

    public float getCurrentInfection() {
        return currentInfection;
    }

    public boolean isInfected() {
        return infected;
    }

    public int getInfectionLevel() {
        return infectionLevel;
    }

    public int getInfectionMaximum() {
        return infectionMaximum;
    }
}
