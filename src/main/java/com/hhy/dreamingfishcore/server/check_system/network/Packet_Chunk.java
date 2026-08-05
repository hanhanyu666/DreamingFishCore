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

public class Packet_Chunk implements CustomPacketPayload {
    public static final Type<Packet_Chunk> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "check_system/packet_chunk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_Chunk> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> encode(packet, buf), Packet_Chunk::decode);

    private final String requestId;
    private final int chunkIndex;
    private final int totalChunks;
    private final String chunkData;

    public Packet_Chunk(String requestId, int chunkIndex, int totalChunks, String chunkData) {
        this.requestId = requestId;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.chunkData = chunkData;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(Packet_Chunk msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.requestId, FileInspectionSecurity.REQUEST_ID_LENGTH);
        buf.writeInt(msg.chunkIndex);
        buf.writeInt(msg.totalChunks);
        buf.writeUtf(msg.chunkData, FileInspectionSecurity.CHUNK_CHARS);
    }

    public static Packet_Chunk decode(FriendlyByteBuf buf) {
        return new Packet_Chunk(
                buf.readUtf(FileInspectionSecurity.REQUEST_ID_LENGTH),
                buf.readInt(),
                buf.readInt(),
                buf.readUtf(FileInspectionSecurity.CHUNK_CHARS)
        );
    }

    public static void handle(Packet_Chunk msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer responder = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            if (responder == null || !isValidChunk(msg)) {
                return;
            }
            FileInspectionSessionManager.Session session = FileInspectionSessionManager.acceptChunk(
                    msg.requestId,
                    responder,
                    msg.chunkIndex,
                    msg.totalChunks
            );
            if (session == null) {
                return;
            }
            ServerPlayer requester = responder.server.getPlayerList().getPlayer(session.requesterUuid());
            if (requester == null || !requester.hasPermissions(2)) {
                return;
            }
            DreamingFishCore_NetworkManager.sendToClient(
                    new Packet_ChunkResponse(
                            session.requestId(),
                            session.targetName(),
                            session.targetUuid(),
                            session.actionType(),
                            session.fileName(),
                            msg.chunkIndex,
                            msg.totalChunks,
                            msg.chunkData
                    ), requester
            );
        });
    }

    private static boolean isValidChunk(Packet_Chunk msg) {
        if (msg.chunkData == null || msg.chunkData.length() > FileInspectionSecurity.CHUNK_CHARS
                || msg.totalChunks < 2 || msg.totalChunks > FileInspectionSecurity.MAX_CHUNKS
                || msg.chunkIndex < 0 || msg.chunkIndex >= msg.totalChunks) {
            return false;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(msg.chunkData);
            long offset = (long) msg.chunkIndex * FileInspectionSecurity.RAW_BYTES_PER_FULL_CHUNK;
            boolean validLength = msg.chunkIndex < msg.totalChunks - 1
                    ? decoded.length == FileInspectionSecurity.RAW_BYTES_PER_FULL_CHUNK
                    : decoded.length <= FileInspectionSecurity.RAW_BYTES_PER_FULL_CHUNK;
            return validLength
                    && offset + decoded.length <= FileInspectionSecurity.MAX_FILE_BYTES;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
