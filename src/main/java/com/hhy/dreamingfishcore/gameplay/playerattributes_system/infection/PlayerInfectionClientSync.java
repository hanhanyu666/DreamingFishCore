package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection;

import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.network.Packet_SyncInfectionData;
import net.minecraft.server.level.ServerPlayer;

public class PlayerInfectionClientSync {
    public static void sendInfectionDataToClient(ServerPlayer player, float currentInfection, boolean infected) {
        Packet_SyncInfectionData packet = new Packet_SyncInfectionData(currentInfection, infected);
        DreamingFishCore_NetworkManager.sendToClient(packet, player);
    }
}
