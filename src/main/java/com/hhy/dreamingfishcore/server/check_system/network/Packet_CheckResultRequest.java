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

public class Packet_CheckResultRequest implements CustomPacketPayload {
    public static final Type<Packet_CheckResultRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "check_system/packet_check_result_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_CheckResultRequest> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> encode(packet, buf), Packet_CheckResultRequest::decode);

    private final String requestId;
    private final boolean success;
    private final String payload;

    public Packet_CheckResultRequest(String requestId, boolean success, String payload) {
        this.requestId = requestId;
        this.success = success;
        this.payload = payload;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(Packet_CheckResultRequest msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.requestId, FileInspectionSecurity.REQUEST_ID_LENGTH);
        buf.writeBoolean(msg.success);
        buf.writeUtf(msg.payload, FileInspectionSecurity.MAX_MANIFEST_CHARS);
    }

    public static Packet_CheckResultRequest decode(FriendlyByteBuf buf) {
        return new Packet_CheckResultRequest(
                buf.readUtf(FileInspectionSecurity.REQUEST_ID_LENGTH),
                buf.readBoolean(),
                buf.readUtf(FileInspectionSecurity.MAX_MANIFEST_CHARS)
        );
    }

    public static void handle(Packet_CheckResultRequest msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer responder = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            FileInspectionSessionManager.Session session = FileInspectionSessionManager.consumeSingleResult(
                    msg.requestId,
                    responder,
                    FileInspectionSessionManager.Operation.CHECK
            );
            if (session == null || responder == null) {
                return;
            }
            ServerPlayer requester = responder.server.getPlayerList().getPlayer(session.requesterUuid());
            if (requester == null || !requester.hasPermissions(2)) {
                return;
            }
            String payload = msg.success
                    ? msg.payload
                    : limitMessage(msg.payload);
            DreamingFishCore_NetworkManager.sendToClient(
                    new Packet_CheckResultResponse(
                            session.targetName(),
                            session.targetUuid(),
                            session.actionType(),
                            msg.success,
                            payload
                    ), requester
            );
        });
    }

    private static String limitMessage(String message) {
        if (message == null) {
            return "检查失败";
        }
        return message.substring(0, Math.min(message.length(), FileInspectionSecurity.MAX_STATUS_MESSAGE_LENGTH));
    }
}
