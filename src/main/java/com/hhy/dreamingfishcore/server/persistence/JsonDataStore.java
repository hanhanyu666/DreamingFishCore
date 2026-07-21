package com.hhy.dreamingfishcore.server.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.hhy.dreamingfishcore.DreamingFishCore;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Shared JSON persistence with atomic replacement and a last-known-good backup.
 */
public final class JsonDataStore {
    private static final Set<Path> WRITE_BLOCKED = new HashSet<>();
    private static final Set<Path> RECOVERED_FROM_BACKUP = new HashSet<>();

    private JsonDataStore() {
    }

    public static synchronized void resetSession() {
        WRITE_BLOCKED.clear();
        RECOVERED_FROM_BACKUP.clear();
    }

    public static <T> T read(Path path, Gson gson, Type type, Supplier<T> emptyValue) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.notExists(normalized) || Files.size(normalized) == 0L) {
            return emptyValue.get();
        }

        try {
            return readExisting(normalized, gson, type, emptyValue);
        } catch (IOException | JsonParseException | IllegalStateException primaryFailure) {
            Path backup = backupPath(normalized);
            if (Files.exists(backup) && Files.size(backup) > 0L) {
                try {
                    T recovered = readExisting(backup, gson, type, emptyValue);
                    synchronized (JsonDataStore.class) {
                        RECOVERED_FROM_BACKUP.add(normalized);
                    }
                    DreamingFishCore.LOGGER.error("JSON 主文件损坏，已从备份恢复到内存：{}", normalized, primaryFailure);
                    return recovered;
                } catch (IOException | JsonParseException | IllegalStateException backupFailure) {
                    primaryFailure.addSuppressed(backupFailure);
                }
            }

            synchronized (JsonDataStore.class) {
                WRITE_BLOCKED.add(normalized);
            }
            throw new IOException("JSON 主文件和备份均无法读取，已禁止本次服务器会话覆盖该文件：" + normalized,
                    primaryFailure);
        }
    }

    private static <T> T readExisting(Path path, Gson gson, Type type, Supplier<T> emptyValue) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            T value = gson.fromJson(reader, type);
            return value == null ? emptyValue.get() : value;
        }
    }

    public static void writeAtomic(Path path, Gson gson, Object value) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        synchronized (JsonDataStore.class) {
            if (WRITE_BLOCKED.contains(normalized)) {
                throw new IOException("为保护损坏数据，拒绝覆盖文件：" + normalized);
            }
        }

        Path parent = normalized.getParent();
        if (parent == null) {
            throw new IOException("JSON 文件缺少父目录：" + normalized);
        }
        Files.createDirectories(parent);

        Path temporary = Files.createTempFile(parent, "." + normalized.getFileName() + ".", ".tmp");
        boolean replaceSucceeded = false;
        try {
            try (Writer writer = Files.newBufferedWriter(
                    temporary,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                gson.toJson(value, writer);
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }

            boolean recovered;
            synchronized (JsonDataStore.class) {
                recovered = RECOVERED_FROM_BACKUP.contains(normalized);
            }
            if (!recovered && Files.exists(normalized) && Files.size(normalized) > 0L) {
                Files.copy(normalized, backupPath(normalized),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
            }

            try {
                Files.move(temporary, normalized,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING);
            }
            replaceSucceeded = true;
            synchronized (JsonDataStore.class) {
                RECOVERED_FROM_BACKUP.remove(normalized);
            }
        } finally {
            if (!replaceSucceeded) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static Path backupPath(Path path) {
        return path.resolveSibling(path.getFileName() + ".bak");
    }
}
