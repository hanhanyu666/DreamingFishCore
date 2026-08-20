package com.hhy.dreamingfishcore.client.ui.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client-side persisted layout for the immersive chat window. */
final class ImmersiveChatConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get()
            .resolve(DreamingFishCore.MODID)
            .resolve("immersive_chat.json");
    private static final Path HISTORY_DIR = FMLPaths.CONFIGDIR.get()
            .resolve(DreamingFishCore.MODID)
            .resolve("chat_history");

    private static LayoutData data = load();

    private ImmersiveChatConfig() {
    }

    static synchronized Layout resolve(int screenWidth, int screenHeight) {
        if (data.width <= 0 || data.height <= 0) {
            int width = Math.min(360, Math.max(250, (int) (screenWidth * 0.42f)));
            int height = Math.min(185, Math.max(120, (int) (screenHeight * 0.34f)));
            data.x = 7;
            data.y = Math.max(20, screenHeight - height - 96);
            data.width = width;
            data.height = height;
        }
        clamp(screenWidth, screenHeight);
        return new Layout(data.x, data.y, data.width, data.height);
    }

    static synchronized void set(int x, int y, int width, int height, int screenWidth, int screenHeight,
                                 boolean persist) {
        data.x = x;
        data.y = y;
        data.width = width;
        data.height = height;
        clamp(screenWidth, screenHeight);
        if (persist) {
            save();
        }
    }

    static synchronized void saveNow() {
        save();
    }

    static Path historyDirectory() {
        return HISTORY_DIR;
    }

    private static void clamp(int screenWidth, int screenHeight) {
        int maxWidth = Math.max(220, screenWidth - 12);
        int maxHeight = Math.max(90, screenHeight - 54);
        data.width = Math.max(220, Math.min(data.width, maxWidth));
        data.height = Math.max(90, Math.min(data.height, maxHeight));
        data.x = Math.max(2, Math.min(data.x, Math.max(2, screenWidth - data.width - 2)));
        // The input is fixed at the bottom of the screen, so leave a little breathing room above it.
        data.y = Math.max(10, Math.min(data.y, Math.max(10, screenHeight - data.height - 28)));
    }

    private static LayoutData load() {
        if (!Files.isRegularFile(CONFIG_PATH)) {
            return new LayoutData();
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            LayoutData loaded = GSON.fromJson(reader, LayoutData.class);
            return loaded == null ? new LayoutData() : loaded;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.warn("无法读取沉浸式聊天布局配置，将使用默认布局", exception);
            return new LayoutData();
        }
    }

    private static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException exception) {
            DreamingFishCore.LOGGER.warn("无法保存沉浸式聊天布局配置", exception);
        }
    }

    static record Layout(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }
    }

    private static final class LayoutData {
        int x = -1;
        int y = -1;
        int width = -1;
        int height = -1;
    }
}
