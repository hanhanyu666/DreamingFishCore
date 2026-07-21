package com.hhy.dreamingfishcore.server.check_system.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class Packet_ChunkResponse {
    private static final Map<String, DownloadAccumulator> DOWNLOADS = new HashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "dreamingfishcore-chunk-save");
        thread.setDaemon(true);
        return thread;
    });

    private final String requestId;
    private final String targetName;
    private final String targetUuid;
    private final String actionType;
    private final String fileName;
    private final int chunkIndex;
    private final int totalChunks;
    private final String chunkData;

    public Packet_ChunkResponse(String requestId, String targetName, String targetUuid, String actionType,
                                String fileName, int chunkIndex, int totalChunks, String chunkData) {
        this.requestId = requestId;
        this.targetName = targetName;
        this.targetUuid = targetUuid;
        this.actionType = actionType;
        this.fileName = fileName;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.chunkData = chunkData;
    }

    public static void encode(Packet_ChunkResponse msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.requestId, FileInspectionSecurity.REQUEST_ID_LENGTH);
        buf.writeUtf(msg.targetName, 64);
        buf.writeUtf(msg.targetUuid, 36);
        buf.writeUtf(msg.actionType, FileInspectionSecurity.MAX_ACTION_TYPE_LENGTH);
        buf.writeUtf(msg.fileName, FileInspectionSecurity.MAX_FILE_NAME_LENGTH);
        buf.writeInt(msg.chunkIndex);
        buf.writeInt(msg.totalChunks);
        buf.writeUtf(msg.chunkData, FileInspectionSecurity.CHUNK_CHARS);
    }

    public static Packet_ChunkResponse decode(FriendlyByteBuf buf) {
        return new Packet_ChunkResponse(
                buf.readUtf(FileInspectionSecurity.REQUEST_ID_LENGTH),
                buf.readUtf(64),
                buf.readUtf(36),
                buf.readUtf(FileInspectionSecurity.MAX_ACTION_TYPE_LENGTH),
                buf.readUtf(FileInspectionSecurity.MAX_FILE_NAME_LENGTH),
                buf.readInt(),
                buf.readInt(),
                buf.readUtf(FileInspectionSecurity.CHUNK_CHARS)
        );
    }

    public static void handle(Packet_ChunkResponse msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> EXECUTOR.execute(() -> receiveChunk(msg)));
        context.setPacketHandled(true);
    }

    private static void receiveChunk(Packet_ChunkResponse msg) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isValidMetadata(msg)) {
            sendMessage(minecraft, "收到非法的文件分片，已拒绝");
            return;
        }

        synchronized (DOWNLOADS) {
            cleanupExpired();
            DownloadAccumulator accumulator = DOWNLOADS.get(msg.requestId);
            try {
                if (accumulator == null) {
                    if (DOWNLOADS.size() >= FileInspectionSecurity.MAX_CLIENT_TRANSFERS) {
                        sendMessage(minecraft, "同时接收的检查文件过多，已拒绝新传输");
                        return;
                    }
                    accumulator = new DownloadAccumulator(minecraft, msg);
                    DOWNLOADS.put(msg.requestId, accumulator);
                } else if (!accumulator.matches(msg)) {
                    accumulator.abort();
                    DOWNLOADS.remove(msg.requestId);
                    sendMessage(minecraft, "文件分片元数据不一致，已取消传输");
                    return;
                }

                if (accumulator.add(msg.chunkIndex, msg.chunkData)) {
                    Path output = accumulator.finish();
                    DOWNLOADS.remove(msg.requestId);
                    sendMessage(minecraft, "已安全保存玩家 " + msg.targetName + " 的文件到：" + output.toAbsolutePath());
                }
            } catch (IOException | IllegalArgumentException e) {
                if (accumulator != null) {
                    accumulator.abort();
                }
                DOWNLOADS.remove(msg.requestId);
                sendMessage(minecraft, "接收玩家 " + msg.targetName + " 的文件失败");
            }
        }
    }

    private static boolean isValidMetadata(Packet_ChunkResponse msg) {
        return FileInspectionSecurity.normalizeActionType(msg.actionType) != null
                && FileInspectionSecurity.isSafeFileName(msg.fileName)
                && msg.requestId != null
                && msg.requestId.matches("[0-9a-fA-F-]{36}")
                && msg.totalChunks >= 2
                && msg.totalChunks <= FileInspectionSecurity.MAX_CHUNKS
                && msg.chunkIndex >= 0
                && msg.chunkIndex < msg.totalChunks
                && msg.chunkData != null
                && msg.chunkData.length() <= FileInspectionSecurity.CHUNK_CHARS;
    }

    private static void cleanupExpired() {
        long cutoff = System.currentTimeMillis() - FileInspectionSecurity.CLIENT_TRANSFER_TIMEOUT_MILLIS;
        Iterator<Map.Entry<String, DownloadAccumulator>> iterator = DOWNLOADS.entrySet().iterator();
        while (iterator.hasNext()) {
            DownloadAccumulator accumulator = iterator.next().getValue();
            if (accumulator.lastActivity < cutoff) {
                accumulator.abort();
                iterator.remove();
            }
        }
    }

    private static void sendMessage(Minecraft minecraft, String message) {
        minecraft.execute(() -> {
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal(message));
            }
        });
    }

    private static final class DownloadAccumulator {
        private final String targetUuid;
        private final String actionType;
        private final String fileName;
        private final int totalChunks;
        private final BitSet received;
        private final Path temporary;
        private final Path output;
        private long lastActivity;

        private DownloadAccumulator(Minecraft minecraft, Packet_ChunkResponse msg) throws IOException {
            this.targetUuid = msg.targetUuid;
            this.actionType = msg.actionType;
            this.fileName = msg.fileName;
            this.totalChunks = msg.totalChunks;
            this.received = new BitSet(totalChunks);
            this.output = FileInspectionSecurity.resultFile(
                    minecraft.gameDirectory,
                    targetUuid,
                    actionType,
                    fileName
            );
            this.temporary = Files.createTempFile(output.getParent(), ".transfer-", ".part");
            this.lastActivity = System.currentTimeMillis();
        }

        private boolean matches(Packet_ChunkResponse msg) {
            return targetUuid.equals(msg.targetUuid)
                    && actionType.equals(msg.actionType)
                    && fileName.equals(msg.fileName)
                    && totalChunks == msg.totalChunks;
        }

        private boolean add(int index, String base64) throws IOException {
            if (received.get(index)) {
                return false;
            }
            byte[] bytes = Base64.getDecoder().decode(base64);
            boolean fullChunk = index < totalChunks - 1;
            if ((fullChunk && bytes.length != FileInspectionSecurity.RAW_BYTES_PER_FULL_CHUNK)
                    || (!fullChunk && bytes.length > FileInspectionSecurity.RAW_BYTES_PER_FULL_CHUNK)) {
                throw new IOException("非法分片长度");
            }
            long offset = (long) index * FileInspectionSecurity.RAW_BYTES_PER_FULL_CHUNK;
            if (offset + bytes.length > FileInspectionSecurity.MAX_FILE_BYTES) {
                throw new IOException("文件超过安全上限");
            }
            try (RandomAccessFile file = new RandomAccessFile(temporary.toFile(), "rw")) {
                file.seek(offset);
                file.write(bytes);
            }
            received.set(index);
            lastActivity = System.currentTimeMillis();
            return received.cardinality() == totalChunks;
        }

        private Path finish() throws IOException {
            if (Files.size(temporary) > FileInspectionSecurity.MAX_FILE_BYTES) {
                throw new IOException("文件超过安全上限");
            }
            FileInspectionSecurity.moveAtomically(temporary, output);
            return output;
        }

        private void abort() {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }
}
