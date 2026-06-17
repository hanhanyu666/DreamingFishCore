package com.hhy.dreamingfishcore.network;

import com.hhy.dreamingfishcore.network.packets.Packet_OnlinePlayerCountRequest;
import com.hhy.dreamingfishcore.network.packets.Packet_OnlinePlayerCountResponse;
import com.hhy.dreamingfishcore.network.packets.Packet_ServerPlayerListRequest;
import com.hhy.dreamingfishcore.network.packets.Packet_ServerPlayerListResponse;
import com.hhy.dreamingfishcore.network.packets.Packet_SystemMessage;
import com.hhy.dreamingfishcore.network.packets.check_system.Packet_Check;
import com.hhy.dreamingfishcore.network.packets.check_system.Packet_CheckResultRequest;
import com.hhy.dreamingfishcore.network.packets.check_system.Packet_CheckResultResponse;
import com.hhy.dreamingfishcore.network.packets.check_system.Packet_Chunk;
import com.hhy.dreamingfishcore.network.packets.check_system.Packet_ChunkResponse;
import com.hhy.dreamingfishcore.network.packets.check_system.Packet_Get;
import com.hhy.dreamingfishcore.network.packets.check_system.Packet_GetResultRequest;
import com.hhy.dreamingfishcore.network.packets.check_system.Packet_GetResultResponse;
import com.hhy.dreamingfishcore.network.packets.login_system.Packet_PlayerLoginRequest;
import com.hhy.dreamingfishcore.network.packets.login_system.Packet_PlayerLoginResponse;
import com.hhy.dreamingfishcore.network.packets.login_system.Packet_PlayerLoginResult;
import com.hhy.dreamingfishcore.network.packets.notice_system.Packet_MarkNoticeReadRequest;
import com.hhy.dreamingfishcore.network.packets.notice_system.Packet_NoticeCheckResponse;
import com.hhy.dreamingfishcore.network.packets.notice_system.Packet_NoticeListRequest;
import com.hhy.dreamingfishcore.network.packets.notice_system.Packet_NoticeListResponse;
import com.hhy.dreamingfishcore.network.packets.npc_system.Packet_NpcInteractionRequest;
import com.hhy.dreamingfishcore.network.packets.npc_system.Packet_OpenNpcDialogueGUI;
import com.hhy.dreamingfishcore.network.packets.playerattribute_system.courage_system.Packet_SyncCourageData;
import com.hhy.dreamingfishcore.network.packets.playerattribute_system.death_system.Packet_DeathScreenData;
import com.hhy.dreamingfishcore.network.packets.playerattribute_system.death_system.Packet_KeepInventoryRequest;
import com.hhy.dreamingfishcore.network.packets.playerattribute_system.death_system.Packet_KeepInventoryResponse;
import com.hhy.dreamingfishcore.network.packets.playerattribute_system.death_system.Packet_NormalRespawnRequest;
import com.hhy.dreamingfishcore.network.packets.playerattribute_system.death_system.Packet_NormalRespawnResponse;
import com.hhy.dreamingfishcore.network.packets.playerattribute_system.death_system.Packet_OpenRevivalCharmGUI;
import com.hhy.dreamingfishcore.network.packets.playerattribute_system.death_system.Packet_RevivalRequest;
import com.hhy.dreamingfishcore.network.packets.playerattribute_system.death_system.Packet_SyncRespawnPointData;
import com.hhy.dreamingfishcore.network.packets.playerattribute_system.infection_system.Packet_SyncInfectionData;
import com.hhy.dreamingfishcore.network.packets.playerattribute_system.limb_system.Packet_SyncLimbInjury;
import com.hhy.dreamingfishcore.network.packets.playerattribute_system.strength_system.Packet_CantRun;
import com.hhy.dreamingfishcore.network.packets.playerattribute_system.strength_system.Packet_SyncStrengthData;
import com.hhy.dreamingfishcore.network.packets.playerdata_system.Packet_BiomeDiscoveryNotify;
import com.hhy.dreamingfishcore.network.packets.playerdata_system.Packet_LevelUpNotify;
import com.hhy.dreamingfishcore.network.packets.playerdata_system.Packet_RequestAllPlayerData;
import com.hhy.dreamingfishcore.network.packets.playerdata_system.Packet_RequestPlayerStats;
import com.hhy.dreamingfishcore.network.packets.playerdata_system.Packet_SyncPlayerData;
import com.hhy.dreamingfishcore.network.packets.playerdata_system.Packet_SyncPlayerStats;
import com.hhy.dreamingfishcore.network.packets.playerdata_system.Packet_VanillaAdvancementNotify;
import com.hhy.dreamingfishcore.network.packets.storybook_system.Packet_OpenStoryBookGUI;
import com.hhy.dreamingfishcore.network.packets.storybook_system.Packet_OpenStoryFragmentGUI;
import com.hhy.dreamingfishcore.network.packets.storybook_system.Packet_UpdateStoryBookOrder;
import com.hhy.dreamingfishcore.network.packets.task_system.Packet_SyncCompleteTask;
import com.hhy.dreamingfishcore.network.packets.task_system.Packet_SyncFullTaskData;
import com.hhy.dreamingfishcore.network.packets.task_system.Packet_SyncUpdateTask;
import com.hhy.dreamingfishcore.network.packets.tip_system.Packet_SendTipToClient;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class DreamingFishCore_NetworkManager {
    private static final String PROTOCOL_VERSION = "1";

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
