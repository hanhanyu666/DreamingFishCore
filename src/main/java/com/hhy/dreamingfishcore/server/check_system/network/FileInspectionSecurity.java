package com.hhy.dreamingfishcore.server.check_system.network;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

public final class FileInspectionSecurity {
    public static final int REQUEST_ID_LENGTH = 36;
    public static final int MAX_ACTION_TYPE_LENGTH = 32;
    public static final int MAX_FILE_NAME_LENGTH = 255;
    public static final int MAX_STATUS_MESSAGE_LENGTH = 256;
    public static final int MAX_MANIFEST_CHARS = 1_000_000;
    public static final int MAX_MANIFEST_FILES = 2_048;
    public static final int CHUNK_CHARS = 30_000;
    public static final int RAW_BYTES_PER_FULL_CHUNK = CHUNK_CHARS * 3 / 4;
    public static final long MAX_FILE_BYTES = 32L * 1024L * 1024L;
    public static final int MAX_CHUNKS = (int) ((MAX_FILE_BYTES + RAW_BYTES_PER_FULL_CHUNK - 1) / RAW_BYTES_PER_FULL_CHUNK);
    public static final long CLIENT_TRANSFER_TIMEOUT_MILLIS = 120_000L;
    public static final int MAX_CLIENT_TRANSFERS = 8;

    private static final Set<String> ALLOWED_ACTION_TYPES = Set.of("mods", "shaderpacks", "resourcepacks");

    private FileInspectionSecurity() {
    }

    public static String normalizeActionType(String actionType) {
        if (actionType == null) {
            return null;
        }
        String normalized = actionType.toLowerCase(Locale.ROOT);
        return ALLOWED_ACTION_TYPES.contains(normalized) ? normalized : null;
    }

    public static boolean isSafeFileName(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.length() > MAX_FILE_NAME_LENGTH) {
            return false;
        }
        if (fileName.equals(".") || fileName.equals("..") || fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0) {
            return false;
        }
        if (fileName.endsWith(".") || fileName.endsWith(" ") || fileName.matches(".*[<>:\"|?*].*")) {
            return false;
        }
        for (int i = 0; i < fileName.length(); i++) {
            if (Character.isISOControl(fileName.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static Path inspectionFolder(File gameDirectory, String actionType) throws IOException {
        String normalizedType = normalizeActionType(actionType);
        if (normalizedType == null) {
            throw new IOException("不支持的检查类型");
        }
        return gameDirectory.toPath().toAbsolutePath().normalize().resolve(normalizedType).normalize();
    }

    public static Path resolveReadableFile(File gameDirectory, String actionType, String fileName) throws IOException {
        if (!isSafeFileName(fileName)) {
            throw new IOException("非法文件名");
        }
        Path folder = inspectionFolder(gameDirectory, actionType);
        if (!Files.isDirectory(folder)) {
            throw new IOException("目标目录不存在");
        }
        Path realFolder = folder.toRealPath();
        Path candidate = folder.resolve(fileName).normalize();
        if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("文件不存在或不是普通文件");
        }
        Path realCandidate = candidate.toRealPath();
        if (!realCandidate.getParent().equals(realFolder)) {
            throw new IOException("文件超出允许目录");
        }
        return realCandidate;
    }

    public static Path resultDirectory(File gameDirectory, String targetUuid, String actionType) throws IOException {
        String normalizedType = normalizeActionType(actionType);
        if (normalizedType == null || targetUuid == null || !targetUuid.matches("[0-9a-fA-F-]{36}")) {
            throw new IOException("非法检查结果路径");
        }
        Path root = gameDirectory.toPath().toAbsolutePath().normalize()
                .resolve("dreamingfishcore")
                .resolve("inspection-results")
                .resolve(targetUuid.toLowerCase(Locale.ROOT))
                .resolve(normalizedType)
                .normalize();
        Files.createDirectories(root);
        return root;
    }

    public static Path resultFile(File gameDirectory, String targetUuid, String actionType, String fileName) throws IOException {
        if (!isSafeFileName(fileName)) {
            throw new IOException("非法结果文件名");
        }
        Path directory = resultDirectory(gameDirectory, targetUuid, actionType);
        Path output = directory.resolve(fileName).normalize();
        if (!output.getParent().equals(directory)) {
            throw new IOException("结果文件超出隔离目录");
        }
        return output;
    }

    public static void writeAtomically(Path destination, byte[] data) throws IOException {
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), ".inspection-", ".part");
        boolean moved = false;
        try {
            Files.write(temporary, data);
            moveAtomically(temporary, destination);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    public static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
