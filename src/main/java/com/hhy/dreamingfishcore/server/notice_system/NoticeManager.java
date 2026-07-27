package com.hhy.dreamingfishcore.server.notice_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import net.minecraftforge.fml.loading.FMLPaths;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 公告管理器，配置改动通过原子替换写入。 */
public class NoticeManager {
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get()
            .resolve(DreamingFishCore.MODID)
            .resolve("notices.json");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final Type NOTICE_LIST_TYPE = new TypeToken<List<NoticeData>>() {}.getType();
    private static final List<NoticeData> NOTICES = new ArrayList<>();

    private static boolean configWritable;

    public static synchronized void loadFromConfig() {
        if (Files.notExists(CONFIG_PATH)) {
            NOTICES.clear();
            configWritable = writeNotices(NOTICES);
            if (configWritable) {
                DreamingFishCore.LOGGER.info("已创建默认公告配置文件");
            }
            return;
        }

        try {
            if (Files.size(CONFIG_PATH) == 0L) {
                configWritable = false;
                DreamingFishCore.LOGGER.error("公告配置为空，拒绝覆盖文件：{}", CONFIG_PATH);
                return;
            }

            List<NoticeData> loadedNotices = JsonDataStore.read(
                    CONFIG_PATH,
                    GSON,
                    NOTICE_LIST_TYPE,
                    ArrayList::new);
            NOTICES.clear();
            NOTICES.addAll(loadedNotices);
            configWritable = true;
            DreamingFishCore.LOGGER.info("已加载 {} 条公告", NOTICES.size());
        } catch (Exception exception) {
            configWritable = false;
            DreamingFishCore.LOGGER.error(
                    "公告配置及备份读取失败，保留当前内存数据并拒绝覆盖文件：{}",
                    CONFIG_PATH,
                    exception);
        }
    }

    public static synchronized List<NoticeData> getNotices() {
        List<NoticeData> sortedNotices = new ArrayList<>(NOTICES);
        sortedNotices.sort(Comparator.comparingLong(NoticeData::getPublishTime).reversed());
        return sortedNotices;
    }

    public static synchronized NoticeData getLatestNotice() {
        if (NOTICES.isEmpty()) {
            return null;
        }
        return getNotices().get(0);
    }

    public static synchronized int getMaxNoticeId() {
        return NOTICES.stream()
                .mapToInt(NoticeData::getNoticeId)
                .max()
                .orElse(0);
    }

    public static synchronized NoticeData getNoticeById(int noticeId) {
        return NOTICES.stream()
                .filter(notice -> notice.getNoticeId() == noticeId)
                .findFirst()
                .orElse(null);
    }

    public static synchronized boolean addNotice(NoticeData notice) {
        if (notice == null || !configWritable) {
            return false;
        }

        NOTICES.add(notice);
        if (writeNotices(NOTICES)) {
            DreamingFishCore.LOGGER.info("已保存 {} 条公告到配置文件", NOTICES.size());
            return true;
        }

        NOTICES.remove(NOTICES.size() - 1);
        return false;
    }

    public static synchronized boolean deleteNotice(int noticeId) {
        if (!configWritable) {
            return false;
        }

        int index = -1;
        for (int current = 0; current < NOTICES.size(); current++) {
            if (NOTICES.get(current).getNoticeId() == noticeId) {
                index = current;
                break;
            }
        }
        if (index < 0) {
            return false;
        }

        NoticeData removed = NOTICES.remove(index);
        if (writeNotices(NOTICES)) {
            DreamingFishCore.LOGGER.info("已保存 {} 条公告到配置文件", NOTICES.size());
            return true;
        }

        NOTICES.add(index, removed);
        return false;
    }

    private static boolean writeNotices(List<NoticeData> notices) {
        try {
            JsonDataStore.writeAtomic(CONFIG_PATH, GSON, notices);
            return true;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("保存公告配置文件失败：{}", CONFIG_PATH, exception);
            return false;
        }
    }
}
