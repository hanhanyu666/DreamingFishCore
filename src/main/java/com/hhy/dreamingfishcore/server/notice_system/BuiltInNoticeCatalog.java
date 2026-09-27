package com.hhy.dreamingfishcore.server.notice_system;

import com.hhy.dreamingfishcore.gameplay.story_system.StoryStageCatalog;

import java.util.Collections;
import java.util.List;

/** Definition and deterministic backfilling rule for the single opening guide notice. */
public final class BuiltInNoticeCatalog {
    public static final String OPENING_STAGE_ID = StoryStageCatalog.DREAM_BEGINNING_ID;
    public static final String OPENING_STORY_DATE = "危机第1日";
    public static final String DESERT_TOWN_KEY = "opening.desert_town";

    static final String ABYDOS_TOWN_TITLE = "阿拜多斯 · 临时安置通知";
    static final String ABYDOS_TOWN_CONTENT =
            "各位抵达者：\n\n请尽快前往登记处（坐标 X:9890，Y:151，Z:1771 附近）完成登记。现场已备有照明、床位及首批补给物资。\n\n医院即日起接收伤员，受伤者请优先前往。\n\n登记仅用于统计需求，不作为准入审查。来自外缘带的居民可保留原有预登记信息，我们正在逐条核对。\n\n有意参与救援、建设或公共事务者，请留意终端私信。是否参与，自愿决定。\n\n此前在建筑服中完成的建筑已统一安置于新岸社区（坐标 X:10580，Z:1200 附近），可作为各位抵达后的住所。\n\n特此通知。\n\n阿拜多斯安置点管理处";

    private BuiltInNoticeCatalog() {
    }

    /**
     * Creates only the missing opening settlement notice. The input list is never mutated;
     * the caller owns persistence and can roll the returned additions back if
     * the write fails.
     */
    public static List<NoticeData> createMissingOpeningNotices(List<NoticeData> existing) {
        List<NoticeData> notices = existing == null ? Collections.emptyList() : existing;
        NoticeData desertTown = findByKey(notices, DESERT_TOWN_KEY);
        if (desertTown != null) {
            return Collections.emptyList();
        }

        int nextId = notices.stream()
                .filter(notice -> notice != null)
                .mapToInt(NoticeData::getNoticeId)
                .max()
                .orElse(0) + 1;
        return List.of(new NoticeData(
                nextId,
                ABYDOS_TOWN_TITLE,
                ABYDOS_TOWN_CONTENT,
                System.currentTimeMillis(),
                NoticeCategory.GAME,
                OPENING_STAGE_ID,
                OPENING_STORY_DATE,
                DESERT_TOWN_KEY));
    }

    private static NoticeData findByKey(List<NoticeData> notices, String key) {
        for (NoticeData notice : notices) {
            if (notice != null && key.equals(notice.getNoticeKey())) {
                return notice;
            }
        }
        return null;
    }
}
