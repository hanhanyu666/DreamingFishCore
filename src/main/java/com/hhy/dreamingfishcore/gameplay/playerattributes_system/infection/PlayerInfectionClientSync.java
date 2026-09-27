package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection;

import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.network.Packet_SyncInfectionData;
import net.minecraft.server.level.ServerPlayer;

public class PlayerInfectionClientSync {
    public static void sendInfectionDataToClient(ServerPlayer player, float currentInfection, boolean infected) {
        sendInfectionDataToClient(player, currentInfection, infected, infected ? 1 : 0);
    }

    public static void sendInfectionDataToClient(
            ServerPlayer player, float currentInfection, boolean infected, int infectionLevel) {
        sendInfectionDataToClient(
                player,
                currentInfection,
                infected,
                infectionLevel,
                PlayerInfectionManager.getInfectionMaximum(player));
    }

    public static void sendInfectionDataToClient(
            ServerPlayer player, float currentInfection, boolean infected,
            int infectionLevel, int infectionMaximum) {
        Packet_SyncInfectionData packet = new Packet_SyncInfectionData(
                currentInfection, infected, infectionLevel, infectionMaximum);
        DreamingFishCore_NetworkManager.sendToClient(packet, player);
    }
}
