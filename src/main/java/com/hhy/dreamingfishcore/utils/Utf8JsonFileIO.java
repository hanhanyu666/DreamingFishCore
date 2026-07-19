package com.hhy.dreamingfishcore.utils;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.hhy.dreamingfishcore.DreamingFishCore;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Opens JSON files with a stable UTF-8 encoding while remaining compatible
 * with files written by older Windows/JDK 17 builds using GBK.
 */
public final class Utf8JsonFileIO {
    private static final Charset LEGACY_WINDOWS_CHARSET = Charset.forName("GB18030");

    private Utf8JsonFileIO() {
    }

    public static Reader openReader(File file) throws IOException {
        return openReader(file.toPath());
    }

    public static Reader openReader(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        String text;
        boolean legacyEncoded = false;

        try {
            text = decodeStrict(bytes, StandardCharsets.UTF_8);
        } catch (CharacterCodingException utf8Failure) {
            try {
                text = decodeStrict(bytes, LEGACY_WINDOWS_CHARSET);
            } catch (CharacterCodingException legacyFailure) {
                legacyFailure.addSuppressed(utf8Failure);
                throw new IOException("JSON file is neither valid UTF-8 nor GB18030: " + path, legacyFailure);
            }

            validateJson(path, text);
            legacyEncoded = true;
        }

        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }

        if (legacyEncoded) {
            migrateLegacyFile(path, text);
        }
        return new StringReader(text);
    }

    public static Writer openWriter(File file) throws IOException {
        return openWriter(file.toPath());
    }

    public static Writer openWriter(Path path) throws IOException {
        Path absolutePath = path.toAbsolutePath();
        Path parent = absolutePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return Files.newBufferedWriter(
                absolutePath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
    }

    private static String decodeStrict(byte[] bytes, Charset charset) throws CharacterCodingException {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return decoder.decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static void validateJson(Path path, String text) throws IOException {
        try {
            JsonParser.parseString(text);
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IOException("Legacy-encoded file is not valid JSON: " + path, exception);
        }
    }

    private static void migrateLegacyFile(Path path, String text) {
        Path absolutePath = path.toAbsolutePath();
        Path parent = absolutePath.getParent();
        if (parent == null) {
            return;
        }

        Path backup = absolutePath.resolveSibling(absolutePath.getFileName() + ".gbk.bak");
        Path temporary = null;
        try {
            if (Files.notExists(backup)) {
                Files.copy(absolutePath, backup, StandardCopyOption.COPY_ATTRIBUTES);
            }

            temporary = Files.createTempFile(parent, "." + absolutePath.getFileName() + ".", ".utf8.tmp");
            Files.writeString(
                    temporary,
                    text,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );

            try {
                Files.move(temporary, absolutePath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, absolutePath, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
            DreamingFishCore.LOGGER.info("已将旧版 GBK JSON 迁移为 UTF-8：{}（备份：{}）", absolutePath, backup);
        } catch (IOException exception) {
            DreamingFishCore.LOGGER.warn("读取到了旧版 GBK JSON，但自动迁移 UTF-8 失败：{}", absolutePath, exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }
}
