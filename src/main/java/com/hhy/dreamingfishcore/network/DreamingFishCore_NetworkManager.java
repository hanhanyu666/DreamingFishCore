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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Optional;

public final class DreamingFishCore_NetworkManager {
    // Protocol 4 changes the full task sync layout for shared story progress.
    private static final String PROTOCOL_VERSION = "4";

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(DreamingFishCore_NetworkManager::registerPayloadHandlers);
    }

    private static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(Packet_Check.TYPE, Packet_Check.STREAM_CODEC, Packet_Check::handle);
        registrar.playToServer(Packet_CheckResultRequest.TYPE, Packet_CheckResultRequest.STREAM_CODEC, Packet_CheckResultRequest::handle);
        registrar.playToClient(Packet_CheckResultResponse.TYPE, Packet_CheckResultResponse.STREAM_CODEC, Packet_CheckResultResponse::handle);
        registrar.playToClient(Packet_Get.TYPE, Packet_Get.STREAM_CODEC, Packet_Get::handle);
        registrar.playToServer(Packet_GetResultRequest.TYPE, Packet_GetResultRequest.STREAM_CODEC, Packet_GetResultRequest::handle);
        registrar.playToClient(Packet_GetResultResponse.TYPE, Packet_GetResultResponse.STREAM_CODEC, Packet_GetResultResponse::handle);
        registrar.playToServer(Packet_Chunk.TYPE, Packet_Chunk.STREAM_CODEC, Packet_Chunk::handle);
        registrar.playToClient(Packet_ChunkResponse.TYPE, Packet_ChunkResponse.STREAM_CODEC, Packet_ChunkResponse::handle);
        registrar.playToServer(Packet_ServerPlayerListRequest.TYPE, Packet_ServerPlayerListRequest.STREAM_CODEC, Packet_ServerPlayerListRequest::handle);
        registrar.playToClient(Packet_ServerPlayerListResponse.TYPE, Packet_ServerPlayerListResponse.STREAM_CODEC, Packet_ServerPlayerListResponse::handle);
        registrar.playToClient(Packet_SystemMessage.TYPE, Packet_SystemMessage.STREAM_CODEC, Packet_SystemMessage::handle);
        registrar.playToServer(Packet_OnlinePlayerCountRequest.TYPE, Packet_OnlinePlayerCountRequest.STREAM_CODEC, Packet_OnlinePlayerCountRequest::handle);
        registrar.playToClient(Packet_OnlinePlayerCountResponse.TYPE, Packet_OnlinePlayerCountResponse.STREAM_CODEC, Packet_OnlinePlayerCountResponse::handle);
        registrar.playToClient(Packet_SyncPlayerData.TYPE, Packet_SyncPlayerData.STREAM_CODEC, Packet_SyncPlayerData::handle);
        registrar.playToClient(Packet_SyncFullTaskData.TYPE, Packet_SyncFullTaskData.STREAM_CODEC, Packet_SyncFullTaskData::handle);
        registrar.playToServer(Packet_SyncCompleteTask.TYPE, Packet_SyncCompleteTask.STREAM_CODEC, Packet_SyncCompleteTask::handle);
        registrar.playToClient(Packet_CantRun.TYPE, Packet_CantRun.STREAM_CODEC, Packet_CantRun::handle);
        registrar.playToClient(Packet_LevelUpNotify.TYPE, Packet_LevelUpNotify.STREAM_CODEC, Packet_LevelUpNotify::handle);
        registrar.playToClient(Packet_BiomeDiscoveryNotify.TYPE, Packet_BiomeDiscoveryNotify.STREAM_CODEC, Packet_BiomeDiscoveryNotify::handle);
        registrar.playToClient(Packet_VanillaAdvancementNotify.TYPE, Packet_VanillaAdvancementNotify.STREAM_CODEC, Packet_VanillaAdvancementNotify::handle);
        registrar.playToClient(Packet_SyncStrengthData.TYPE, Packet_SyncStrengthData.STREAM_CODEC, Packet_SyncStrengthData::handle);
        registrar.playToClient(Packet_SendTipToClient.TYPE, Packet_SendTipToClient.STREAM_CODEC, Packet_SendTipToClient::handle);
        registrar.playToClient(Packet_SyncCourageData.TYPE, Packet_SyncCourageData.STREAM_CODEC, Packet_SyncCourageData::handle);
        registrar.playToClient(Packet_SyncInfectionData.TYPE, Packet_SyncInfectionData.STREAM_CODEC, Packet_SyncInfectionData::handle);
        registrar.playToClient(Packet_SyncRespawnPointData.TYPE, Packet_SyncRespawnPointData.STREAM_CODEC, Packet_SyncRespawnPointData::handle);
        registrar.playToServer(Packet_KeepInventoryRequest.TYPE, Packet_KeepInventoryRequest.STREAM_CODEC, Packet_KeepInventoryRequest::handle);
        registrar.playToClient(Packet_KeepInventoryResponse.TYPE, Packet_KeepInventoryResponse.STREAM_CODEC, Packet_KeepInventoryResponse::handle);
        registrar.playToServer(Packet_NormalRespawnRequest.TYPE, Packet_NormalRespawnRequest.STREAM_CODEC, Packet_NormalRespawnRequest::handle);
        registrar.playToClient(Packet_NormalRespawnResponse.TYPE, Packet_NormalRespawnResponse.STREAM_CODEC, Packet_NormalRespawnResponse::handle);
        registrar.playToClient(Packet_DeathScreenData.TYPE, Packet_DeathScreenData.STREAM_CODEC, Packet_DeathScreenData::handle);
        registrar.playToServer(Packet_RequestAllPlayerData.TYPE, Packet_RequestAllPlayerData.STREAM_CODEC, Packet_RequestAllPlayerData::handle);
        registrar.playToServer(Packet_RequestPlayerStats.TYPE, Packet_RequestPlayerStats.STREAM_CODEC, Packet_RequestPlayerStats::handle);
        registrar.playToClient(Packet_SyncPlayerStats.TYPE, Packet_SyncPlayerStats.STREAM_CODEC, Packet_SyncPlayerStats::handle);
        registrar.playToClient(Packet_PlayerLoginRequest.TYPE, Packet_PlayerLoginRequest.STREAM_CODEC, Packet_PlayerLoginRequest::handle);
        registrar.playToServer(Packet_PlayerLoginResponse.TYPE, Packet_PlayerLoginResponse.STREAM_CODEC, Packet_PlayerLoginResponse::handle);
        registrar.playToClient(Packet_PlayerLoginResult.TYPE, Packet_PlayerLoginResult.STREAM_CODEC, Packet_PlayerLoginResult::handle);
        registrar.playToClient(Packet_NoticeCheckResponse.TYPE, Packet_NoticeCheckResponse.STREAM_CODEC, Packet_NoticeCheckResponse::handle);
        registrar.playToServer(Packet_NoticeListRequest.TYPE, Packet_NoticeListRequest.STREAM_CODEC, Packet_NoticeListRequest::handle);
        registrar.playToClient(Packet_NoticeListResponse.TYPE, Packet_NoticeListResponse.STREAM_CODEC, Packet_NoticeListResponse::handle);
        registrar.playToServer(Packet_MarkNoticeReadRequest.TYPE, Packet_MarkNoticeReadRequest.STREAM_CODEC, Packet_MarkNoticeReadRequest::handle);
        registrar.playToClient(Packet_SyncLimbInjury.TYPE, Packet_SyncLimbInjury.STREAM_CODEC, Packet_SyncLimbInjury::handle);
        registrar.playToClient(Packet_OpenRevivalCharmGUI.TYPE, Packet_OpenRevivalCharmGUI.STREAM_CODEC, Packet_OpenRevivalCharmGUI::handle);
        registrar.playToServer(Packet_RevivalRequest.TYPE, Packet_RevivalRequest.STREAM_CODEC, Packet_RevivalRequest::handle);
        registrar.playToClient(Packet_OpenStoryBookGUI.TYPE, Packet_OpenStoryBookGUI.STREAM_CODEC, Packet_OpenStoryBookGUI::handle);
        registrar.playToClient(Packet_OpenStoryFragmentGUI.TYPE, Packet_OpenStoryFragmentGUI.STREAM_CODEC, Packet_OpenStoryFragmentGUI::handle);
        registrar.playToServer(Packet_UpdateStoryBookOrder.TYPE, Packet_UpdateStoryBookOrder.STREAM_CODEC, Packet_UpdateStoryBookOrder::handle);
        registrar.playToClient(Packet_OpenNpcDialogueGUI.TYPE, Packet_OpenNpcDialogueGUI.STREAM_CODEC, Packet_OpenNpcDialogueGUI::handle);
        registrar.playToServer(Packet_NpcInteractionRequest.TYPE, Packet_NpcInteractionRequest.STREAM_CODEC, Packet_NpcInteractionRequest::handle);
        registrar.playToClient(Packet_SyncUpdateTask.TYPE, Packet_SyncUpdateTask.STREAM_CODEC, Packet_SyncUpdateTask::handle);
        registrar.playToServer(Packet_RequestMarker.TYPE, Packet_RequestMarker.STREAM_CODEC, Packet_RequestMarker::handle);
        registrar.playToClient(Packet_ShowMarker.TYPE, Packet_ShowMarker.STREAM_CODEC, Packet_ShowMarker::handle);
        registrar.playToClient(Packet_MarkerRejected.TYPE, Packet_MarkerRejected.STREAM_CODEC, Packet_MarkerRejected::handle);
    }

    public static void sendToClient(CustomPacketPayload packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToClient(ServerPlayer player, CustomPacketPayload packet) {
        sendToClient(packet, player);
    }

    public static void sendToServer(CustomPacketPayload packet) {
        PacketDistributor.sendToServer(packet);
    }
}
