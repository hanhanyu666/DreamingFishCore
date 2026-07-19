package com.hhy.dreamingfishcore.core.playerattributes_system.infection;

import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.network.packets.playerattribute_system.infection_system.Packet_SyncInfectionData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;

public class PlayerInfectionClientSync {
    public static void sendInfectionDataToClient(ServerPlayer player, float currentInfection) {
        Packet_SyncInfectionData packet = new Packet_SyncInfectionData(currentInfection);
        DreamingFishCore_NetworkManager.INSTANCE.sendTo(packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}
