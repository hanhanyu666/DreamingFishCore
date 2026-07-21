package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.network;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.PlayerInfectionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class Packet_SyncInfectionData {
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

    public static void handle(Packet_SyncInfectionData packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        final float safeCurrentInfection = packet.currentInfection;
        final boolean safeInfected = packet.infected;

        context.enqueueWork(() -> processOnMainThread(safeCurrentInfection, safeInfected));
        context.setPacketHandled(true);
    }

    private static void processOnMainThread(float currentInfection, boolean infected) {
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> new ClientRunnable(currentInfection, infected));
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientRunnable implements DistExecutor.SafeRunnable {
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
