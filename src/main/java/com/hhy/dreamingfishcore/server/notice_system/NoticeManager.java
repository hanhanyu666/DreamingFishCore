package com.hhy.dreamingfishcore.server.notice_system;

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
            List<NoticeData> previousNotices = new ArrayList<>(NOTICES);
            List<NoticeData> defaultNotices = new ArrayList<>();
            defaultNotices.addAll(
                    BuiltInNoticeCatalog.createMissingOpeningNotices(defaultNotices));
            if (writeNotices(defaultNotices)) {
                NOTICES.clear();
                NOTICES.addAll(defaultNotices);
                configWritable = true;
                DreamingFishCore.LOGGER.info("已创建默认公告配置文件并补齐内置开场公告");
            } else {
                NOTICES.clear();
                NOTICES.addAll(previousNotices);
                configWritable = false;
                DreamingFishCore.LOGGER.error("默认公告配置写入失败，已回滚内存公告列表");
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
            if (!ensureBuiltInNotices()) {
                DreamingFishCore.LOGGER.error("内置开场公告写入失败，已回滚本次内置公告补齐");
            }
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

    /**
     * Returns notices readable in the terminal for the supplied story state.
     * The tutorial flag remains in this overload for binary/source
     * compatibility with older callers, but terminal visibility deliberately
     * does not depend on it.
     */
    public static synchronized List<NoticeData> getVisibleNotices(
            String currentStageId, boolean tutorialCompleted) {
        return getVisibleNotices(currentStageId);
    }

    /** Returns notices readable in the terminal for the supplied story state. */
    public static synchronized List<NoticeData> getVisibleNotices(String currentStageId) {
        List<NoticeData> visibleNotices = new ArrayList<>(
                NoticeVisibilityPolicy.filterVisible(
                        NOTICES, currentStageId));
        visibleNotices.sort(Comparator.comparingLong(NoticeData::getPublishTime).reversed());
        return visibleNotices;
    }

    public static synchronized NoticeData getLatestNotice() {
        if (NOTICES.isEmpty()) {
            return null;
        }
        return getNotices().get(0);
    }

    public static synchronized int getMaxNoticeId() {
        return NOTICES.stream()
                .filter(notice -> notice != null)
                .mapToInt(NoticeData::getNoticeId)
                .max()
                .orElse(0);
    }

    public static synchronized NoticeData getNoticeById(int noticeId) {
        return NOTICES.stream()
                .filter(notice -> notice != null)
                .filter(notice -> notice.getNoticeId() == noticeId)
                .findFirst()
                .orElse(null);
    }

    public static synchronized NoticeData getNoticeByKey(String noticeKey) {
        if (noticeKey == null || noticeKey.isBlank()) {
            return null;
        }
        String normalizedKey = noticeKey.trim();
        return NOTICES.stream()
                .filter(notice -> notice != null)
                .filter(notice -> normalizedKey.equals(notice.getNoticeKey()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Idempotently appends the built-in opening guide notice and persists the
     * resulting list. The in-memory append is rolled back if persistence
     * fails; this method never emits a player notification.
     */
    public static synchronized boolean ensureBuiltInNotices() {
        if (!configWritable) {
            return false;
        }

        NoticeData desertTown = getNoticeByKey(BuiltInNoticeCatalog.DESERT_TOWN_KEY);
        String previousDesertTitle = desertTown == null ? "" : desertTown.getNoticeTitle();
        String previousDesertContent = desertTown == null ? "" : desertTown.getNoticeContent();
        boolean migratedAbydos = BuiltInNoticeCatalog.migrateAbydosTownName(NOTICES);
        List<NoticeData> additions = BuiltInNoticeCatalog.createMissingOpeningNotices(NOTICES);
        if (additions.isEmpty() && !migratedAbydos) {
            return true;
        }

        int originalSize = NOTICES.size();
        NOTICES.addAll(additions);
        if (writeNotices(NOTICES)) {
            if (migratedAbydos) {
                DreamingFishCore.LOGGER.info("已将内置阿拜多斯安置公告更新为最新文案");
            }
            if (!additions.isEmpty()) {
                DreamingFishCore.LOGGER.info("已补齐内置开场指引公告");
            }
            return true;
        }

        while (NOTICES.size() > originalSize) {
            NOTICES.remove(NOTICES.size() - 1);
        }
        if (migratedAbydos && desertTown != null) {
            desertTown.setNoticeTitle(previousDesertTitle);
            desertTown.setNoticeContent(previousDesertContent);
        }
        return false;
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
