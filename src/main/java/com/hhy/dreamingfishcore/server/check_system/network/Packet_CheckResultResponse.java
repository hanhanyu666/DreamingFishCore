package com.hhy.dreamingfishcore.server.check_system.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class Packet_CheckResultResponse implements CustomPacketPayload {
    public static final Type<Packet_CheckResultResponse> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "check_system/packet_check_result_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_CheckResultResponse> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> encode(packet, buf), Packet_CheckResultResponse::decode);

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "dreamingfishcore-check-result");
        thread.setDaemon(true);
        return thread;
    });

    private final String targetName;
    private final String targetUuid;
    private final String actionType;
    private final boolean success;
    private final String payload;

    public Packet_CheckResultResponse(String targetName, String targetUuid, String actionType,
                                      boolean success, String payload) {
        this.targetName = targetName;
        this.targetUuid = targetUuid;
        this.actionType = actionType;
        this.success = success;
        this.payload = payload;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(Packet_CheckResultResponse msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.targetName, 64);
        buf.writeUtf(msg.targetUuid, 36);
        buf.writeUtf(msg.actionType, FileInspectionSecurity.MAX_ACTION_TYPE_LENGTH);
        buf.writeBoolean(msg.success);
        buf.writeUtf(msg.payload, FileInspectionSecurity.MAX_MANIFEST_CHARS);
    }

    public static Packet_CheckResultResponse decode(FriendlyByteBuf buf) {
        return new Packet_CheckResultResponse(
                buf.readUtf(64),
                buf.readUtf(36),
                buf.readUtf(FileInspectionSecurity.MAX_ACTION_TYPE_LENGTH),
                buf.readBoolean(),
                buf.readUtf(FileInspectionSecurity.MAX_MANIFEST_CHARS)
        );
    }

    public static void handle(Packet_CheckResultResponse msg, IPayloadContext context) {
        context.enqueueWork(() -> EXECUTOR.execute(() -> processResult(msg)));
    }

    private static void processResult(Packet_CheckResultResponse msg) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!msg.success) {
            sendMessage(minecraft, Component.literal("玩家 " + msg.targetName + " 的检查失败：" + msg.payload));
            return;
        }

        String normalizedType = FileInspectionSecurity.normalizeActionType(msg.actionType);
        if (normalizedType == null) {
            sendMessage(minecraft, Component.literal("收到非法的检查结果类型"));
            return;
        }

        try {
            Map<String, String> remoteHashes = parseHashes(msg.payload, normalizedType);
            Map<String, String> localHashes = hashLocalFiles(minecraft, normalizedType);
            Path manifest = FileInspectionSecurity.resultFile(
                    minecraft.gameDirectory,
                    msg.targetUuid,
                    normalizedType,
                    "manifest.json"
            );
            FileInspectionSecurity.writeAtomically(manifest, msg.payload.getBytes(StandardCharsets.UTF_8));

            List<Component> messages = new ArrayList<>();
            messages.add(Component.literal("收到来自玩家 " + msg.targetName + " 的检查结果"));
            messages.add(Component.literal("结果已保存到：" + manifest.toAbsolutePath()));

            Set<String> allFiles = new HashSet<>();
            allFiles.addAll(remoteHashes.keySet());
            allFiles.addAll(localHashes.keySet());
            allFiles.stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(fileName -> {
                String remoteHash = remoteHashes.get(fileName);
                String localHash = localHashes.get(fileName);
                if (remoteHash == null && localHash != null) {
                    messages.add(diffMessage("[+]", fileName, msg.targetName, normalizedType));
                } else if (remoteHash != null && localHash == null) {
                    messages.add(diffMessage("[-]", fileName, msg.targetName, normalizedType));
                } else if (!Objects.equals(remoteHash, localHash)) {
                    messages.add(diffMessage("[!]", fileName, msg.targetName, normalizedType));
                }
            });
            if (messages.size() == 2) {
                messages.add(Component.literal("与 " + msg.targetName + " 的文件对比：无差异"));
            }
            minecraft.execute(() -> {
                LocalPlayer player = minecraft.player;
                if (player != null) {
                    messages.forEach(player::sendSystemMessage);
                }
            });
        } catch (IOException | NoSuchAlgorithmException | RuntimeException e) {
            sendMessage(minecraft, Component.literal("处理玩家 " + msg.targetName + " 的检查结果失败"));
        }
    }

    private static Map<String, String> parseHashes(String json, String actionType) throws IOException {
        JsonElement rootElement = JsonParser.parseString(json);
        if (!rootElement.isJsonObject()) {
            throw new IOException("检查结果不是 JSON 对象");
        }
        JsonElement filesElement = rootElement.getAsJsonObject().get(actionType);
        if (filesElement == null || !filesElement.isJsonObject()) {
            throw new IOException("检查结果缺少文件列表");
        }
        JsonObject files = filesElement.getAsJsonObject();
        if (files.size() > FileInspectionSecurity.MAX_MANIFEST_FILES) {
            throw new IOException("检查结果文件数量超限");
        }

        Map<String, String> hashes = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : files.entrySet()) {
            if (!FileInspectionSecurity.isSafeFileName(entry.getKey()) || !entry.getValue().isJsonPrimitive()) {
                throw new IOException("检查结果包含非法条目");
            }
            String hash = entry.getValue().getAsString();
            if (!hash.matches("[0-9a-fA-F]{64}")) {
                throw new IOException("检查结果包含非法哈希");
            }
            hashes.put(entry.getKey(), hash.toLowerCase());
        }
        return hashes;
    }

    private static Map<String, String> hashLocalFiles(Minecraft minecraft, String actionType)
            throws IOException, NoSuchAlgorithmException {
        Path folder = FileInspectionSecurity.inspectionFolder(minecraft.gameDirectory, actionType);
        Map<String, String> hashes = new HashMap<>();
        if (!Files.isDirectory(folder)) {
            return hashes;
        }
        try (Stream<Path> paths = Files.list(folder)) {
            Path[] files = paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .limit(FileInspectionSecurity.MAX_MANIFEST_FILES)
                    .toArray(Path[]::new);
            for (Path file : files) {
                String fileName = file.getFileName().toString();
                if (FileInspectionSecurity.isSafeFileName(fileName)) {
                    hashes.put(fileName, computeSHA256(file));
                }
            }
        }
        return hashes;
    }

    private static Component diffMessage(String type, String fileName, String targetName, String actionType) {
        if (!FileInspectionSecurity.isSafeFileName(fileName)) {
            return Component.literal(type + " 非法文件名");
        }
        String command = "/get " + targetName + " \"" + fileName + "\" " + actionType;
        Component button = Component.literal("[获取]").withStyle(style -> style
                .withColor(0x55FF55)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("点击获取该文件"))));
        return Component.literal(type + " " + fileName + " ").append(button);
    }

    private static void sendMessage(Minecraft minecraft, Component message) {
        minecraft.execute(() -> {
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(message);
            }
        });
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
