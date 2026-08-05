package com.hhy.dreamingfishcore.server.check_system.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class Packet_Check implements CustomPacketPayload {
    public static final Type<Packet_Check> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "check_system/packet_check"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_Check> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> encode(packet, buf), Packet_Check::decode);

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "dreamingfishcore-file-check");
        thread.setDaemon(true);
        return thread;
    });

    private final String requestId;
    private final String actionType;

    public Packet_Check(String requestId, String actionType) {
        this.requestId = requestId;
        this.actionType = actionType;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(Packet_Check msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.requestId, FileInspectionSecurity.REQUEST_ID_LENGTH);
        buf.writeUtf(msg.actionType, FileInspectionSecurity.MAX_ACTION_TYPE_LENGTH);
    }

    public static Packet_Check decode(FriendlyByteBuf buf) {
        return new Packet_Check(
                buf.readUtf(FileInspectionSecurity.REQUEST_ID_LENGTH),
                buf.readUtf(FileInspectionSecurity.MAX_ACTION_TYPE_LENGTH)
        );
    }

    public static void handle(Packet_Check msg, IPayloadContext context) {
        context.enqueueWork(() -> EXECUTOR.execute(() -> inspectFiles(msg)));
    }

    private static void inspectFiles(Packet_Check msg) {
        String normalizedType = FileInspectionSecurity.normalizeActionType(msg.actionType);
        if (normalizedType == null) {
            sendFailure(msg.requestId, "不支持的检查类型");
            return;
        }

        try {
            Path folder = FileInspectionSecurity.inspectionFolder(Minecraft.getInstance().gameDirectory, normalizedType);
            Map<String, String> fileHashes = new LinkedHashMap<>();
            if (Files.isDirectory(folder)) {
                try (Stream<Path> paths = Files.list(folder)) {
                    Path[] files = paths
                            .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                            .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                            .limit(FileInspectionSecurity.MAX_MANIFEST_FILES + 1L)
                            .toArray(Path[]::new);
                    if (files.length > FileInspectionSecurity.MAX_MANIFEST_FILES) {
                        sendFailure(msg.requestId, "目录内文件数量超过安全上限");
                        return;
                    }
                    for (Path file : files) {
                        String fileName = file.getFileName().toString();
                        if (FileInspectionSecurity.isSafeFileName(fileName)) {
                            fileHashes.put(fileName, computeSHA256(file));
                        }
                    }
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put(normalizedType, fileHashes);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(result);
            if (json.length() > FileInspectionSecurity.MAX_MANIFEST_CHARS) {
                sendFailure(msg.requestId, "检查结果超过安全上限");
                return;
            }
            DreamingFishCore_NetworkManager.sendToServer(
                    new Packet_CheckResultRequest(msg.requestId, true, json)
            );
        } catch (IOException | NoSuchAlgorithmException e) {
            sendFailure(msg.requestId, "读取或计算文件哈希失败");
        }
    }

    private static void sendFailure(String requestId, String message) {
        DreamingFishCore_NetworkManager.sendToServer(
                new Packet_CheckResultRequest(requestId, false, message)
        );
    }

    private static String computeSHA256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        try (InputStream input = Files.newInputStream(path)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}
