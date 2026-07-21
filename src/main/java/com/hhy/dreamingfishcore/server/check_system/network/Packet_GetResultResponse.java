package com.hhy.dreamingfishcore.server.check_system.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class Packet_GetResultResponse {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "dreamingfishcore-file-save");
        thread.setDaemon(true);
        return thread;
    });

    private final String targetName;
    private final String targetUuid;
    private final String actionType;
    private final String fileName;
    private final boolean success;
    private final String payload;

    public Packet_GetResultResponse(String targetName, String targetUuid, String actionType,
                                    String fileName, boolean success, String payload) {
        this.targetName = targetName;
        this.targetUuid = targetUuid;
        this.actionType = actionType;
        this.fileName = fileName;
        this.success = success;
        this.payload = payload;
    }

    public static void encode(Packet_GetResultResponse msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.targetName, 64);
        buf.writeUtf(msg.targetUuid, 36);
        buf.writeUtf(msg.actionType, FileInspectionSecurity.MAX_ACTION_TYPE_LENGTH);
        buf.writeUtf(msg.fileName, FileInspectionSecurity.MAX_FILE_NAME_LENGTH);
        buf.writeBoolean(msg.success);
        buf.writeUtf(msg.payload, FileInspectionSecurity.CHUNK_CHARS);
    }

    public static Packet_GetResultResponse decode(FriendlyByteBuf buf) {
        return new Packet_GetResultResponse(
                buf.readUtf(64),
                buf.readUtf(36),
                buf.readUtf(FileInspectionSecurity.MAX_ACTION_TYPE_LENGTH),
                buf.readUtf(FileInspectionSecurity.MAX_FILE_NAME_LENGTH),
                buf.readBoolean(),
                buf.readUtf(FileInspectionSecurity.CHUNK_CHARS)
        );
    }

    public static void handle(Packet_GetResultResponse msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> EXECUTOR.execute(() -> saveResult(msg)));
        context.setPacketHandled(true);
    }

    private static void saveResult(Packet_GetResultResponse msg) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!msg.success) {
            sendMessage(minecraft, "玩家 " + msg.targetName + " 的文件获取失败：" + msg.payload);
            return;
        }

        try {
            byte[] data = Base64.getDecoder().decode(msg.payload);
            if (data.length > FileInspectionSecurity.MAX_FILE_BYTES) {
                throw new IOException("文件超过安全上限");
            }
            Path output = FileInspectionSecurity.resultFile(
                    minecraft.gameDirectory,
                    msg.targetUuid,
                    msg.actionType,
                    msg.fileName
            );
            FileInspectionSecurity.writeAtomically(output, data);
            sendMessage(minecraft, "已安全保存玩家 " + msg.targetName + " 的文件到：" + output.toAbsolutePath());
        } catch (IOException | IllegalArgumentException e) {
            sendMessage(minecraft, "保存玩家 " + msg.targetName + " 的文件失败");
        }
    }

    private static void sendMessage(Minecraft minecraft, String message) {
        minecraft.execute(() -> {
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal(message));
            }
        });
    }
}
