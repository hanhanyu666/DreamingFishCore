package com.hhy.dreamingfishcore.server.title_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import net.neoforged.fml.loading.FMLPaths;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TitleConfig {
    private static final Path TITLE_CONFIG_PATH = FMLPaths.CONFIGDIR.get()
            .resolve(DreamingFishCore.MODID)
            .resolve("economy_titles.json");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();
    private static final Type TITLE_LIST_TYPE = new TypeToken<List<TitleData>>() {}.getType();

    private static final Map<Integer, Title> TITLE_MAP = new HashMap<>();
    private static final Map<String, Title> NAME_TO_TITLE_MAP = new HashMap<>();
    private static boolean configWritable;

    public static synchronized void loadConfig() {
        if (Files.notExists(TITLE_CONFIG_PATH)) {
            List<TitleData> defaults = createDefaultTitles();
            installTitles(defaults);
            configWritable = writeTitles(defaults);
            return;
        }

        try {
            if (Files.size(TITLE_CONFIG_PATH) == 0L) {
                configWritable = false;
                installTitles(createDefaultTitles());
                DreamingFishCore.LOGGER.error("称号配置为空，已使用内存默认值且拒绝覆盖文件：{}", TITLE_CONFIG_PATH);
                return;
            }

            List<TitleData> loadedTitles = JsonDataStore.read(
                    TITLE_CONFIG_PATH,
                    GSON,
                    TITLE_LIST_TYPE,
                    ArrayList::new);
            installTitles(loadedTitles);
            configWritable = true;
        } catch (Exception exception) {
            configWritable = false;
            installTitles(createDefaultTitles());
            DreamingFishCore.LOGGER.error(
                    "称号配置及备份读取失败，已使用内存默认值且拒绝覆盖原文件：{}",
                    TITLE_CONFIG_PATH,
                    exception);
        }
    }

    public static synchronized Title getTitleById(int titleId) {
        return TITLE_MAP.getOrDefault(titleId, TITLE_MAP.get(0));
    }

    public static synchronized boolean containsTitleId(int titleId) {
        return TITLE_MAP.containsKey(titleId);
    }

    public static synchronized Title getTitleByName(String titleName) {
        if (titleName == null || titleName.isEmpty()) {
            return TITLE_MAP.get(0);
        }
        return NAME_TO_TITLE_MAP.getOrDefault(titleName, TITLE_MAP.get(0));
    }

    /**
     * 删除并原子保存称号。写入失败时恢复内存映射，避免命令显示成功但磁盘未更新。
     */
    public static synchronized boolean removeTitleById(int titleId) {
        Title removedTitle = TITLE_MAP.remove(titleId);
        if (removedTitle == null) {
            return false;
        }
        NAME_TO_TITLE_MAP.remove(removedTitle.getTitleName());

        if (saveConfig()) {
            return true;
        }

        TITLE_MAP.put(titleId, removedTitle);
        NAME_TO_TITLE_MAP.put(removedTitle.getTitleName(), removedTitle);
        return false;
    }

    public static synchronized boolean saveConfig() {
        if (!configWritable) {
            DreamingFishCore.LOGGER.error("称号配置未安全加载，拒绝覆盖文件：{}", TITLE_CONFIG_PATH);
            return false;
        }

        List<TitleData> titleDataList = TITLE_MAP.values().stream()
                .sorted(Comparator.comparingInt(Title::getTitleID))
                .map(title -> new TitleData(title.getTitleID(), title.getTitleName(), title.getColor()))
                .toList();
        return writeTitles(titleDataList);
    }

    private static boolean writeTitles(List<TitleData> titles) {
        try {
            JsonDataStore.writeAtomic(TITLE_CONFIG_PATH, GSON, titles);
            return true;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("称号配置保存失败：{}", TITLE_CONFIG_PATH, exception);
            return false;
        }
    }

    private static void installTitles(List<TitleData> titleDataList) {
        TITLE_MAP.clear();
        NAME_TO_TITLE_MAP.clear();
        if (titleDataList == null) {
            return;
        }

        for (TitleData data : titleDataList) {
            if (data == null || data.titleName == null) {
                DreamingFishCore.LOGGER.warn("跳过无效称号配置项");
                continue;
            }
            if (TITLE_MAP.containsKey(data.titleId)) {
                DreamingFishCore.LOGGER.warn("重复的称号 ID：{}，已跳过", data.titleId);
                continue;
            }
            if (NAME_TO_TITLE_MAP.containsKey(data.titleName)) {
                DreamingFishCore.LOGGER.warn("重复的称号名称：{}，已跳过", data.titleName);
                continue;
            }

            Title title = new Title(data.titleId, data.titleName, data.color);
            TITLE_MAP.put(data.titleId, title);
            NAME_TO_TITLE_MAP.put(data.titleName, title);
        }
    }

    private static List<TitleData> createDefaultTitles() {
        return List.of(
                new TitleData(0, "萌新鱼友", 0xAAAAAA),
                new TitleData(1, "TEST", 0xFF5555),
                new TitleData(2, "TEST2", 0x55FFFF));
    }

    private static class TitleData {
        int titleId;
        String titleName;
        int color = 0xFFFFFFFF;

        public TitleData(int titleId, String titleName, int color) {
            this.titleId = titleId;
            this.titleName = titleName;
            this.color = color;
        }

        public TitleData() {
        }
    }
}
