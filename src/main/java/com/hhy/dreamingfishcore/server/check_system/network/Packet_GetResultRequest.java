package com.hhy.dreamingfishcore.server.check_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.check_system.FileInspectionSessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Base64;

public class Packet_GetResultRequest implements CustomPacketPayload {
    public static final Type<Packet_GetResultRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "check_system/packet_get_result_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_GetResultRequest> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> encode(packet, buf), Packet_GetResultRequest::decode);

    private final String requestId;
    private final boolean success;
    private final String payload;

    public Packet_GetResultRequest(String requestId, boolean success, String payload) {
        this.requestId = requestId;
        this.success = success;
        this.payload = payload;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(Packet_GetResultRequest msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.requestId, FileInspectionSecurity.REQUEST_ID_LENGTH);
        buf.writeBoolean(msg.success);
        buf.writeUtf(msg.payload, FileInspectionSecurity.CHUNK_CHARS);
    }

    public static Packet_GetResultRequest decode(FriendlyByteBuf buf) {
        return new Packet_GetResultRequest(
                buf.readUtf(FileInspectionSecurity.REQUEST_ID_LENGTH),
                buf.readBoolean(),
                buf.readUtf(FileInspectionSecurity.CHUNK_CHARS)
        );
    }

    public static void handle(Packet_GetResultRequest msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer responder = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            FileInspectionSessionManager.Session session = FileInspectionSessionManager.consumeSingleResult(
                    msg.requestId,
                    responder,
                    FileInspectionSessionManager.Operation.GET
            );
            if (session == null || responder == null) {
                return;
            }
            ServerPlayer requester = responder.server.getPlayerList().getPlayer(session.requesterUuid());
            if (requester == null || !requester.hasPermissions(2)) {
                return;
            }

            boolean validSuccess = msg.success && isValidSmallPayload(msg.payload);
            String payload = validSuccess
                    ? msg.payload
                    : limitMessage(msg.success ? "文件内容编码无效" : msg.payload);
            DreamingFishCore_NetworkManager.sendToClient(
                    new Packet_GetResultResponse(
                            session.targetName(),
                            session.targetUuid(),
                            session.actionType(),
                            session.fileName(),
                            validSuccess,
                            payload
                    ), requester
            );
        });
    }

    private static boolean isValidSmallPayload(String payload) {
        if (payload == null || payload.length() > FileInspectionSecurity.CHUNK_CHARS) {
            return false;
        }
        try {
            return Base64.getDecoder().decode(payload).length <= FileInspectionSecurity.MAX_FILE_BYTES;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String limitMessage(String message) {
        if (message == null || message.isBlank()) {
            return "文件读取失败";
        }
        return message.substring(0, Math.min(message.length(), FileInspectionSecurity.MAX_STATUS_MESSAGE_LENGTH));
    }
}
