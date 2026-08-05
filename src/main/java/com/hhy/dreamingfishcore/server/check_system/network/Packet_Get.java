package com.hhy.dreamingfishcore.server.check_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Packet_Get implements CustomPacketPayload {
    public static final Type<Packet_Get> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "check_system/packet_get"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_Get> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> encode(packet, buf), Packet_Get::decode);

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "dreamingfishcore-file-read");
        thread.setDaemon(true);
        return thread;
    });

    private final String requestId;
    private final String actionType;
    private final String fileName;

    public Packet_Get(String requestId, String actionType, String fileName) {
        this.requestId = requestId;
        this.actionType = actionType;
        this.fileName = fileName;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(Packet_Get msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.requestId, FileInspectionSecurity.REQUEST_ID_LENGTH);
        buf.writeUtf(msg.actionType, FileInspectionSecurity.MAX_ACTION_TYPE_LENGTH);
        buf.writeUtf(msg.fileName, FileInspectionSecurity.MAX_FILE_NAME_LENGTH);
    }

    public static Packet_Get decode(FriendlyByteBuf buf) {
        return new Packet_Get(
                buf.readUtf(FileInspectionSecurity.REQUEST_ID_LENGTH),
                buf.readUtf(FileInspectionSecurity.MAX_ACTION_TYPE_LENGTH),
                buf.readUtf(FileInspectionSecurity.MAX_FILE_NAME_LENGTH)
        );
    }

    public static void handle(Packet_Get msg, IPayloadContext context) {
        context.enqueueWork(() -> EXECUTOR.execute(() -> readAndSend(msg)));
    }

    private static void readAndSend(Packet_Get msg) {
        try {
            Path file = FileInspectionSecurity.resolveReadableFile(
                    Minecraft.getInstance().gameDirectory,
                    msg.actionType,
                    msg.fileName
            );
            long fileSize = Files.size(file);
            if (fileSize > FileInspectionSecurity.MAX_FILE_BYTES) {
                sendFailure(msg.requestId, "文件超过 32 MiB 安全上限");
                return;
            }

            byte[] fileBytes = Files.readAllBytes(file);
            String base64 = Base64.getEncoder().encodeToString(fileBytes);
            if (base64.length() <= FileInspectionSecurity.CHUNK_CHARS) {
                DreamingFishCore_NetworkManager.sendToServer(
                        new Packet_GetResultRequest(msg.requestId, true, base64)
                );
                return;
            }

            int totalChunks = (base64.length() + FileInspectionSecurity.CHUNK_CHARS - 1)
                    / FileInspectionSecurity.CHUNK_CHARS;
            if (totalChunks > FileInspectionSecurity.MAX_CHUNKS) {
                sendFailure(msg.requestId, "文件分片数量超过安全上限");
                return;
            }
            for (int i = 0; i < totalChunks; i++) {
                int start = i * FileInspectionSecurity.CHUNK_CHARS;
                int end = Math.min(start + FileInspectionSecurity.CHUNK_CHARS, base64.length());
                DreamingFishCore_NetworkManager.sendToServer(
                        new Packet_Chunk(msg.requestId, i, totalChunks, base64.substring(start, end))
                );
            }
        } catch (IOException | RuntimeException e) {
            sendFailure(msg.requestId, "文件不存在或无法读取");
        }
    }

    private static void sendFailure(String requestId, String message) {
        DreamingFishCore_NetworkManager.sendToServer(
                new Packet_GetResultRequest(requestId, false, message)
        );
    }
}
