package com.hhy.dreamingfishcore.network;

import com.hhy.dreamingfishcore.gameplay.marker_system.network.*;
import com.hhy.dreamingfishcore.gameplay.npc_system.network.*;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.network.*;
import com.hhy.dreamingfishcore.gameplay.guidance_system.network.*;
import com.hhy.dreamingfishcore.gameplay.kill_effect_system.network.Packet_PlayKillEffect;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.courage.network.Packet_SyncCourageData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.network.*;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.network.Packet_SyncInfectionData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.limb_health_system.network.Packet_SyncLimbInjury;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.strength.network.*;
import com.hhy.dreamingfishcore.gameplay.playerlevel_system.network.*;
import com.hhy.dreamingfishcore.gameplay.storybook_system.network.*;
import com.hhy.dreamingfishcore.gameplay.story_system.network.*;
import com.hhy.dreamingfishcore.gameplay.task_system.network.*;
import com.hhy.dreamingfishcore.gameplay.task_location_system.network.Packet_SyncTaskLocationHud;
import com.hhy.dreamingfishcore.server.check_system.network.*;
import com.hhy.dreamingfishcore.server.economy_bridge.network.*;
import com.hhy.dreamingfishcore.server.login_system.network.*;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import com.hhy.dreamingfishcore.server.notice_system.network.*;
import com.hhy.dreamingfishcore.server.playerdata_system.network.*;
import com.hhy.dreamingfishcore.server.rank_system.network.Packet_EquipPlayerRank;
import com.hhy.dreamingfishcore.server.server_ui_system.network.*;
import com.hhy.dreamingfishcore.server.title_system.network.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Central registration and dispatch point for all client/server payloads. */
public final class DreamingFishCore_NetworkManager {
    // 1.0.4 接入新的数据驱动剧情流程与任务状态同步契约，旧客户端应在握手时明确拒绝连接。
    private static final String PROTOCOL_VERSION = "0.18.0";

    private DreamingFishCore_NetworkManager() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(DreamingFishCore_NetworkManager::registerPayloadHandlers);
    }

    private static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToClient(Packet_Check.TYPE, Packet_Check.STREAM_CODEC, Packet_Check::handle);
        registrar.playToServer(Packet_CheckResultRequest.TYPE, Packet_CheckResultRequest.STREAM_CODEC, authenticated(Packet_CheckResultRequest::handle));
        registrar.playToClient(Packet_CheckResultResponse.TYPE, Packet_CheckResultResponse.STREAM_CODEC, Packet_CheckResultResponse::handle);
        registrar.playToClient(Packet_Get.TYPE, Packet_Get.STREAM_CODEC, Packet_Get::handle);
        registrar.playToServer(Packet_GetResultRequest.TYPE, Packet_GetResultRequest.STREAM_CODEC, authenticated(Packet_GetResultRequest::handle));
        registrar.playToClient(Packet_GetResultResponse.TYPE, Packet_GetResultResponse.STREAM_CODEC, Packet_GetResultResponse::handle);
        registrar.playToServer(Packet_Chunk.TYPE, Packet_Chunk.STREAM_CODEC, authenticated(Packet_Chunk::handle));
        registrar.playToClient(Packet_ChunkResponse.TYPE, Packet_ChunkResponse.STREAM_CODEC, Packet_ChunkResponse::handle);

        registrar.playToServer(Packet_ServerPlayerListRequest.TYPE, Packet_ServerPlayerListRequest.STREAM_CODEC, authenticated(Packet_ServerPlayerListRequest::handle));
        registrar.playToClient(Packet_ServerPlayerListResponse.TYPE, Packet_ServerPlayerListResponse.STREAM_CODEC, Packet_ServerPlayerListResponse::handle);
        registrar.playToClient(Packet_SystemMessage.TYPE, Packet_SystemMessage.STREAM_CODEC, Packet_SystemMessage::handle);
        registrar.playToClient(Packet_RichChatMessage.TYPE, Packet_RichChatMessage.STREAM_CODEC, Packet_RichChatMessage::handle);
        registrar.playToServer(Packet_QuotedChatMessage.TYPE, Packet_QuotedChatMessage.STREAM_CODEC, authenticated(Packet_QuotedChatMessage::handle));
        registrar.playToServer(Packet_OnlinePlayerCountRequest.TYPE, Packet_OnlinePlayerCountRequest.STREAM_CODEC, authenticated(Packet_OnlinePlayerCountRequest::handle));
        registrar.playToClient(Packet_OnlinePlayerCountResponse.TYPE, Packet_OnlinePlayerCountResponse.STREAM_CODEC, Packet_OnlinePlayerCountResponse::handle);
        registrar.playToServer(Packet_EconomyTerminalRequest.TYPE, Packet_EconomyTerminalRequest.STREAM_CODEC, authenticated(Packet_EconomyTerminalRequest::handle));
        registrar.playToClient(Packet_EconomyTerminalResponse.TYPE, Packet_EconomyTerminalResponse.STREAM_CODEC, Packet_EconomyTerminalResponse::handle);

        registrar.playToClient(Packet_SyncPlayerData.TYPE, Packet_SyncPlayerData.STREAM_CODEC, Packet_SyncPlayerData::handle);
        registrar.playToServer(Packet_RequestAllPlayerData.TYPE, Packet_RequestAllPlayerData.STREAM_CODEC, authenticated(Packet_RequestAllPlayerData::handle));
        registrar.playToServer(Packet_RequestPlayerStats.TYPE, Packet_RequestPlayerStats.STREAM_CODEC, authenticated(Packet_RequestPlayerStats::handle));
        registrar.playToClient(Packet_SyncPlayerStats.TYPE, Packet_SyncPlayerStats.STREAM_CODEC, Packet_SyncPlayerStats::handle);
        registrar.playToClient(Packet_VanillaAdvancementNotify.TYPE, Packet_VanillaAdvancementNotify.STREAM_CODEC, Packet_VanillaAdvancementNotify::handle);
        registrar.playToClient(Packet_LevelUpNotify.TYPE, Packet_LevelUpNotify.STREAM_CODEC, Packet_LevelUpNotify::handle);
        registrar.playToClient(Packet_BiomeDiscoveryNotify.TYPE, Packet_BiomeDiscoveryNotify.STREAM_CODEC, Packet_BiomeDiscoveryNotify::handle);
        registrar.playToServer(Packet_EquipPlayerRank.TYPE, Packet_EquipPlayerRank.STREAM_CODEC, authenticated(Packet_EquipPlayerRank::handle));

        registrar.playToClient(Packet_SyncFullTaskData.TYPE, Packet_SyncFullTaskData.STREAM_CODEC, Packet_SyncFullTaskData::handle);
        registrar.playToServer(Packet_SyncCompleteTask.TYPE, Packet_SyncCompleteTask.STREAM_CODEC, authenticated(Packet_SyncCompleteTask::handle));
        registrar.playToClient(Packet_SyncUpdateTask.TYPE, Packet_SyncUpdateTask.STREAM_CODEC, Packet_SyncUpdateTask::handle);
        registrar.playToClient(Packet_SyncTaskLocationHud.TYPE, Packet_SyncTaskLocationHud.STREAM_CODEC, Packet_SyncTaskLocationHud::handle);

        registrar.playToClient(Packet_CantRun.TYPE, Packet_CantRun.STREAM_CODEC, Packet_CantRun::handle);
        registrar.playToClient(Packet_SyncStrengthData.TYPE, Packet_SyncStrengthData.STREAM_CODEC, Packet_SyncStrengthData::handle);
        registrar.playToClient(Packet_SyncCourageData.TYPE, Packet_SyncCourageData.STREAM_CODEC, Packet_SyncCourageData::handle);
        registrar.playToClient(Packet_SyncInfectionData.TYPE, Packet_SyncInfectionData.STREAM_CODEC, Packet_SyncInfectionData::handle);
        registrar.playToClient(Packet_SyncRespawnPointData.TYPE, Packet_SyncRespawnPointData.STREAM_CODEC, Packet_SyncRespawnPointData::handle);
        registrar.playToClient(Packet_SyncLimbInjury.TYPE, Packet_SyncLimbInjury.STREAM_CODEC, Packet_SyncLimbInjury::handle);
        registrar.playToClient(Packet_DeathScreenData.TYPE, Packet_DeathScreenData.STREAM_CODEC, Packet_DeathScreenData::handle);
        registrar.playToServer(Packet_KeepInventoryRequest.TYPE, Packet_KeepInventoryRequest.STREAM_CODEC, authenticated(Packet_KeepInventoryRequest::handle));
        registrar.playToClient(Packet_KeepInventoryResponse.TYPE, Packet_KeepInventoryResponse.STREAM_CODEC, Packet_KeepInventoryResponse::handle);
        registrar.playToServer(Packet_NormalRespawnRequest.TYPE, Packet_NormalRespawnRequest.STREAM_CODEC, authenticated(Packet_NormalRespawnRequest::handle));
        registrar.playToClient(Packet_NormalRespawnResponse.TYPE, Packet_NormalRespawnResponse.STREAM_CODEC, Packet_NormalRespawnResponse::handle);
        registrar.playToClient(Packet_OpenRevivalCharmGUI.TYPE, Packet_OpenRevivalCharmGUI.STREAM_CODEC, Packet_OpenRevivalCharmGUI::handle);
        registrar.playToServer(Packet_RevivalRequest.TYPE, Packet_RevivalRequest.STREAM_CODEC, authenticated(Packet_RevivalRequest::handle));

        registrar.playToClient(Packet_PlayerLoginRequest.TYPE, Packet_PlayerLoginRequest.STREAM_CODEC, Packet_PlayerLoginRequest::handle);
        registrar.playToServer(Packet_PlayerLoginResponse.TYPE, Packet_PlayerLoginResponse.STREAM_CODEC, Packet_PlayerLoginResponse::handle);
        registrar.playToClient(Packet_PlayerLoginResult.TYPE, Packet_PlayerLoginResult.STREAM_CODEC, Packet_PlayerLoginResult::handle);

        registrar.playToClient(Packet_NoticeCheckResponse.TYPE, Packet_NoticeCheckResponse.STREAM_CODEC, Packet_NoticeCheckResponse::handle);
        registrar.playToServer(Packet_NoticeListRequest.TYPE, Packet_NoticeListRequest.STREAM_CODEC, authenticated(Packet_NoticeListRequest::handle));
        registrar.playToClient(Packet_NoticeListResponse.TYPE, Packet_NoticeListResponse.STREAM_CODEC, Packet_NoticeListResponse::handle);
        registrar.playToServer(Packet_MarkNoticeReadRequest.TYPE, Packet_MarkNoticeReadRequest.STREAM_CODEC, authenticated(Packet_MarkNoticeReadRequest::handle));
        registrar.playToClient(Packet_SendNotificationToClient.TYPE, Packet_SendNotificationToClient.STREAM_CODEC, Packet_SendNotificationToClient::handle);
        registrar.playToServer(Packet_NewPlayerGuideViewed.TYPE, Packet_NewPlayerGuideViewed.STREAM_CODEC, authenticated(Packet_NewPlayerGuideViewed::handle));
        registrar.playToClient(Packet_NewPlayerGuideCompleted.TYPE, Packet_NewPlayerGuideCompleted.STREAM_CODEC, Packet_NewPlayerGuideCompleted::handle);

        registrar.playToClient(Packet_OpenStoryBookGUI.TYPE, Packet_OpenStoryBookGUI.STREAM_CODEC, Packet_OpenStoryBookGUI::handle);
        registrar.playToClient(Packet_OpenStoryFragmentGUI.TYPE, Packet_OpenStoryFragmentGUI.STREAM_CODEC, Packet_OpenStoryFragmentGUI::handle);
        registrar.playToServer(Packet_UpdateStoryBookOrder.TYPE, Packet_UpdateStoryBookOrder.STREAM_CODEC, authenticated(Packet_UpdateStoryBookOrder::handle));
        registrar.playToServer(Packet_WorldHistoryRequest.TYPE, Packet_WorldHistoryRequest.STREAM_CODEC, authenticated(Packet_WorldHistoryRequest::handle));
        registrar.playToClient(Packet_WorldHistoryResponse.TYPE, Packet_WorldHistoryResponse.STREAM_CODEC, Packet_WorldHistoryResponse::handle);
        registrar.playToClient(Packet_OpenNpcDialogueGUI.TYPE, Packet_OpenNpcDialogueGUI.STREAM_CODEC, Packet_OpenNpcDialogueGUI::handle);
        registrar.playToServer(Packet_NpcInteractionRequest.TYPE, Packet_NpcInteractionRequest.STREAM_CODEC, authenticated(Packet_NpcInteractionRequest::handle));
        registrar.playToServer(Packet_NpcMessageSnapshotRequest.TYPE, Packet_NpcMessageSnapshotRequest.STREAM_CODEC, authenticated(Packet_NpcMessageSnapshotRequest::handle));
        registrar.playToClient(Packet_NpcMessageSnapshotResponse.TYPE, Packet_NpcMessageSnapshotResponse.STREAM_CODEC, Packet_NpcMessageSnapshotResponse::handle);
        registrar.playToServer(Packet_NpcMessageReplyRequest.TYPE, Packet_NpcMessageReplyRequest.STREAM_CODEC, authenticated(Packet_NpcMessageReplyRequest::handle));
        registrar.playToServer(Packet_NpcMessageReadRequest.TYPE, Packet_NpcMessageReadRequest.STREAM_CODEC, authenticated(Packet_NpcMessageReadRequest::handle));
        registrar.playToServer(Packet_GuidanceSnapshotRequest.TYPE, Packet_GuidanceSnapshotRequest.STREAM_CODEC, authenticated(Packet_GuidanceSnapshotRequest::handle));
        registrar.playToClient(Packet_GuidanceSnapshotResponse.TYPE, Packet_GuidanceSnapshotResponse.STREAM_CODEC, Packet_GuidanceSnapshotResponse::handle);
        registrar.playToClient(Packet_PlayKillEffect.TYPE, Packet_PlayKillEffect.STREAM_CODEC, Packet_PlayKillEffect::handle);

        registrar.playToServer(Packet_RequestMarker.TYPE, Packet_RequestMarker.STREAM_CODEC, authenticated(Packet_RequestMarker::handle));
        registrar.playToClient(Packet_ShowMarker.TYPE, Packet_ShowMarker.STREAM_CODEC, Packet_ShowMarker::handle);
        registrar.playToClient(Packet_MarkerRejected.TYPE, Packet_MarkerRejected.STREAM_CODEC, Packet_MarkerRejected::handle);
    }

    public static void sendToClient(CustomPacketPayload packet, ServerPlayer player) {
        if (packet == null || player == null) {
            return;
        }

        // 登录握手是唯一允许在认证前下发的自定义协议；其余同步/界面包统一由
        // 认证门禁拦截，避免某个新模块忘记在调用点加 isAuthenticated 检查。
        if (!AuthSessionGuard.isAuthenticated(player)
                && !(packet instanceof Packet_PlayerLoginRequest)
                && !(packet instanceof Packet_PlayerLoginResult)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToClient(ServerPlayer player, CustomPacketPayload packet) {
        sendToClient(packet, player);
    }

    public static void sendToServer(CustomPacketPayload packet) {
        PacketDistributor.sendToServer(packet);
    }

    /** 统一拒绝尚未完成登录验证的客户端 C2S 请求。登录响应包本身不经过此包装器。 */
    private static <T extends CustomPacketPayload> IPayloadHandler<T> authenticated(IPayloadHandler<T> handler) {
        return (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !AuthSessionGuard.isAuthenticated(player)) {
                return;
            }

            // 先切到服务器主线程，再次确认实体和连接仍然是当前已认证会话。
            // 这样处理器内部常见的 enqueueWork 不会在玩家断线/重生后继续执行旧请求。
            context.enqueueWork(() -> {
                if (AuthSessionGuard.isCurrentAuthenticated(player)) {
                    handler.handle(payload, context);
                }
            });
        };
    }
}
