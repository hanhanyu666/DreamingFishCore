package com.hhy.dreamingfishcore.network;

import com.hhy.dreamingfishcore.gameplay.playerlevel_system.network.*;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.server_ui_system.network.Packet_OnlinePlayerCountRequest;
import com.hhy.dreamingfishcore.server.server_ui_system.network.Packet_OnlinePlayerCountResponse;
import com.hhy.dreamingfishcore.server.server_ui_system.network.Packet_ServerPlayerListRequest;
import com.hhy.dreamingfishcore.server.server_ui_system.network.Packet_ServerPlayerListResponse;
import com.hhy.dreamingfishcore.server.server_ui_system.network.Packet_SystemMessage;
import com.hhy.dreamingfishcore.server.check_system.network.*;
import com.hhy.dreamingfishcore.server.login_system.network.*;
import com.hhy.dreamingfishcore.gameplay.marker_system.network.*;
import com.hhy.dreamingfishcore.server.notice_system.network.*;
import com.hhy.dreamingfishcore.gameplay.npc_system.network.*;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.courage.network.Packet_SyncCourageData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.network.*;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.network.Packet_SyncInfectionData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.limb_health_system.network.Packet_SyncLimbInjury;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.strength.network.*;
import com.hhy.dreamingfishcore.server.playerdata_system.network.*;
import com.hhy.dreamingfishcore.gameplay.storybook_system.network.*;
import com.hhy.dreamingfishcore.gameplay.task_system.network.*;
import com.hhy.dreamingfishcore.server.notice_system.network.Packet_SendTipToClient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class DreamingFishCore_NetworkManager {
    // Protocol 4 changes the full task sync layout for shared story progress.
    private static final String PROTOCOL_VERSION = "4";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DreamingFishCore.MODID, "network"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private DreamingFishCore_NetworkManager() {
    }

    public static void register() {
        int id = 0;

        INSTANCE.registerMessage(id++, Packet_Check.class, Packet_Check::encode, Packet_Check::decode, Packet_Check::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_CheckResultRequest.class, Packet_CheckResultRequest::encode, Packet_CheckResultRequest::decode, Packet_CheckResultRequest::handle, serverbound());
        INSTANCE.registerMessage(id++, Packet_CheckResultResponse.class, Packet_CheckResultResponse::encode, Packet_CheckResultResponse::decode, Packet_CheckResultResponse::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_Get.class, Packet_Get::encode, Packet_Get::decode, Packet_Get::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_GetResultRequest.class, Packet_GetResultRequest::encode, Packet_GetResultRequest::decode, Packet_GetResultRequest::handle, serverbound());
        INSTANCE.registerMessage(id++, Packet_GetResultResponse.class, Packet_GetResultResponse::encode, Packet_GetResultResponse::decode, Packet_GetResultResponse::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_Chunk.class, Packet_Chunk::encode, Packet_Chunk::decode, Packet_Chunk::handle, serverbound());
        INSTANCE.registerMessage(id++, Packet_ChunkResponse.class, Packet_ChunkResponse::encode, Packet_ChunkResponse::decode, Packet_ChunkResponse::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_ServerPlayerListRequest.class, Packet_ServerPlayerListRequest::encode, Packet_ServerPlayerListRequest::decode, Packet_ServerPlayerListRequest::handle, serverbound());
        INSTANCE.registerMessage(id++, Packet_ServerPlayerListResponse.class, Packet_ServerPlayerListResponse::encode, Packet_ServerPlayerListResponse::decode, Packet_ServerPlayerListResponse::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_SystemMessage.class, Packet_SystemMessage::encode, Packet_SystemMessage::decode, Packet_SystemMessage::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_OnlinePlayerCountRequest.class, Packet_OnlinePlayerCountRequest::encode, Packet_OnlinePlayerCountRequest::decode, Packet_OnlinePlayerCountRequest::handle, serverbound());
        INSTANCE.registerMessage(id++, Packet_OnlinePlayerCountResponse.class, Packet_OnlinePlayerCountResponse::encode, Packet_OnlinePlayerCountResponse::decode, Packet_OnlinePlayerCountResponse::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_SyncPlayerData.class, Packet_SyncPlayerData::encode, Packet_SyncPlayerData::decode, Packet_SyncPlayerData::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_SyncFullTaskData.class, Packet_SyncFullTaskData::encode, Packet_SyncFullTaskData::decode, Packet_SyncFullTaskData::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_SyncCompleteTask.class, Packet_SyncCompleteTask::encode, Packet_SyncCompleteTask::decode, Packet_SyncCompleteTask::handle, serverbound());
        INSTANCE.registerMessage(id++, Packet_CantRun.class, Packet_CantRun::encode, Packet_CantRun::decode, Packet_CantRun::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_LevelUpNotify.class, Packet_LevelUpNotify::encode, Packet_LevelUpNotify::decode, Packet_LevelUpNotify::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_BiomeDiscoveryNotify.class, Packet_BiomeDiscoveryNotify::encode, Packet_BiomeDiscoveryNotify::decode, Packet_BiomeDiscoveryNotify::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_VanillaAdvancementNotify.class, Packet_VanillaAdvancementNotify::encode, Packet_VanillaAdvancementNotify::decode, Packet_VanillaAdvancementNotify::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_SyncStrengthData.class, Packet_SyncStrengthData::encode, Packet_SyncStrengthData::decode, Packet_SyncStrengthData::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_SendTipToClient.class, Packet_SendTipToClient::encode, Packet_SendTipToClient::decode, Packet_SendTipToClient::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_SyncCourageData.class, Packet_SyncCourageData::encode, Packet_SyncCourageData::decode, Packet_SyncCourageData::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_SyncInfectionData.class, Packet_SyncInfectionData::encode, Packet_SyncInfectionData::decode, Packet_SyncInfectionData::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_SyncRespawnPointData.class, Packet_SyncRespawnPointData::encode, Packet_SyncRespawnPointData::decode, Packet_SyncRespawnPointData::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_KeepInventoryRequest.class, Packet_KeepInventoryRequest::encode, Packet_KeepInventoryRequest::decode, Packet_KeepInventoryRequest::handle, serverbound());
        INSTANCE.registerMessage(id++, Packet_KeepInventoryResponse.class, Packet_KeepInventoryResponse::encode, Packet_KeepInventoryResponse::decode, Packet_KeepInventoryResponse::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_NormalRespawnRequest.class, Packet_NormalRespawnRequest::encode, Packet_NormalRespawnRequest::decode, Packet_NormalRespawnRequest::handle, serverbound());
        INSTANCE.registerMessage(id++, Packet_NormalRespawnResponse.class, Packet_NormalRespawnResponse::encode, Packet_NormalRespawnResponse::decode, Packet_NormalRespawnResponse::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_DeathScreenData.class, Packet_DeathScreenData::encode, Packet_DeathScreenData::decode, Packet_DeathScreenData::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_RequestAllPlayerData.class, Packet_RequestAllPlayerData::encode, Packet_RequestAllPlayerData::decode, Packet_RequestAllPlayerData::handle, serverbound());
        INSTANCE.registerMessage(id++, Packet_RequestPlayerStats.class, Packet_RequestPlayerStats::encode, Packet_RequestPlayerStats::decode, Packet_RequestPlayerStats::handle, serverbound());
        INSTANCE.registerMessage(id++, Packet_SyncPlayerStats.class, Packet_SyncPlayerStats::encode, Packet_SyncPlayerStats::decode, Packet_SyncPlayerStats::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_PlayerLoginRequest.class, Packet_PlayerLoginRequest::encode, Packet_PlayerLoginRequest::decode, Packet_PlayerLoginRequest::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_PlayerLoginResponse.class, Packet_PlayerLoginResponse::encode, Packet_PlayerLoginResponse::decode, Packet_PlayerLoginResponse::handle, serverbound());
        INSTANCE.registerMessage(id++, Packet_PlayerLoginResult.class, Packet_PlayerLoginResult::encode, Packet_PlayerLoginResult::decode, Packet_PlayerLoginResult::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_NoticeCheckResponse.class, Packet_NoticeCheckResponse::encode, Packet_NoticeCheckResponse::decode, Packet_NoticeCheckResponse::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_NoticeListRequest.class, Packet_NoticeListRequest::encode, Packet_NoticeListRequest::decode, Packet_NoticeListRequest::handle, serverbound());
        INSTANCE.registerMessage(id++, Packet_NoticeListResponse.class, Packet_NoticeListResponse::encode, Packet_NoticeListResponse::decode, Packet_NoticeListResponse::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_MarkNoticeReadRequest.class, Packet_MarkNoticeReadRequest::encode, Packet_MarkNoticeReadRequest::decode, Packet_MarkNoticeReadRequest::handle, serverbound());
        INSTANCE.registerMessage(id++, Packet_SyncLimbInjury.class, Packet_SyncLimbInjury::encode, Packet_SyncLimbInjury::decode, Packet_SyncLimbInjury::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_OpenRevivalCharmGUI.class, Packet_OpenRevivalCharmGUI::encode, Packet_OpenRevivalCharmGUI::decode, Packet_OpenRevivalCharmGUI::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_RevivalRequest.class, Packet_RevivalRequest::encode, Packet_RevivalRequest::decode, Packet_RevivalRequest::handle, serverbound());
        INSTANCE.registerMessage(id++, Packet_OpenStoryBookGUI.class, Packet_OpenStoryBookGUI::encode, Packet_OpenStoryBookGUI::decode, Packet_OpenStoryBookGUI::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_OpenStoryFragmentGUI.class, Packet_OpenStoryFragmentGUI::encode, Packet_OpenStoryFragmentGUI::decode, Packet_OpenStoryFragmentGUI::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_UpdateStoryBookOrder.class, Packet_UpdateStoryBookOrder::encode, Packet_UpdateStoryBookOrder::decode, Packet_UpdateStoryBookOrder::handle, serverbound());
        INSTANCE.registerMessage(id++, Packet_OpenNpcDialogueGUI.class, Packet_OpenNpcDialogueGUI::encode, Packet_OpenNpcDialogueGUI::decode, Packet_OpenNpcDialogueGUI::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_NpcInteractionRequest.class, Packet_NpcInteractionRequest::encode, Packet_NpcInteractionRequest::decode, Packet_NpcInteractionRequest::handle, serverbound());
        INSTANCE.registerMessage(id++, Packet_SyncUpdateTask.class, Packet_SyncUpdateTask::encode, Packet_SyncUpdateTask::decode, Packet_SyncUpdateTask::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_RequestMarker.class, Packet_RequestMarker::encode, Packet_RequestMarker::decode, Packet_RequestMarker::handle, serverbound());
        INSTANCE.registerMessage(id++, Packet_ShowMarker.class, Packet_ShowMarker::encode, Packet_ShowMarker::decode, Packet_ShowMarker::handle, clientbound());
        INSTANCE.registerMessage(id++, Packet_MarkerRejected.class, Packet_MarkerRejected::encode, Packet_MarkerRejected::decode, Packet_MarkerRejected::handle, clientbound());
    }

    private static Optional<NetworkDirection> clientbound() {
        return Optional.of(NetworkDirection.PLAY_TO_CLIENT);
    }

    private static Optional<NetworkDirection> serverbound() {
        return Optional.of(NetworkDirection.PLAY_TO_SERVER);
    }

    public static void sendToClient(Object packet, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToClient(ServerPlayer player, Object packet) {
        sendToClient(packet, player);
    }

    public static void sendToServer(Object packet) {
        INSTANCE.sendToServer(packet);
    }
}
