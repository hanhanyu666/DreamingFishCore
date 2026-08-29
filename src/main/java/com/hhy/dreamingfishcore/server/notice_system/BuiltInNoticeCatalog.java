package com.hhy.dreamingfishcore.server.notice_system;

import java.util.Collections;
import java.util.List;

/** Definition and deterministic backfilling rule for the single opening guide notice. */
public final class BuiltInNoticeCatalog {
    public static final String OPENING_STAGE_ID = "dreamingfishcore:dream_beginning";
    public static final String OPENING_STORY_DATE = "危机第1日";
    public static final String DESERT_TOWN_KEY = "opening.desert_town";

    static final String LEGACY_DESERT_TOWN_TITLE = "沙海灯火 · 临时安置通知";
    static final String LEGACY_DESERT_TOWN_CONTENT =
            "梦屿应急联络网确认：沙漠边缘的临时小镇现已开放接纳。若你刚刚抵达，请前往坐标 X: [待定]，Y: [待定]，Z: [待定] 完成登记。那里仍有灯光、床位，以及为后来者保留的第一批补给。";
    static final String ABYDOS_TOWN_TITLE = "阿拜多斯 · 临时安置通知";
    static final String PREVIOUS_ABYDOS_TOWN_CONTENT =
            "梦屿应急联络网确认：沙漠小镇阿拜多斯现已开放接纳。若你刚刚抵达，请前往坐标 X: [待定]，Y: [待定]，Z: [待定] 完成登记。那里仍有灯光、床位，以及为后来者保留的第一批补给。";
    static final String PREVIOUS_ABYDOS_TOWN_CONTENT_WITHOUT_NEW_SHORE =
            "若你刚刚抵达：这里是沙漠小镇阿拜多斯，梦屿边缘最早开放的临时安置点之一。\n\n请前往登记处（X: 9890，Y: 151，Z: 1771 附近）完成登记。那里仍有灯光、床位和第一批补给；医院正在接收伤员，受伤者请优先前往。\n\n登记只为了解谁需要什么，不是准入审查。来自外缘带的居民请保留原有预登记信息，我们正在逐条核对。\n\n愿意参与救援、建设或承担公共工作的人，请留意终端私信——参与与否，由你自己决定。";
    static final String LEGACY_ABYDOS_TOWN_CONTENT =
            PREVIOUS_ABYDOS_TOWN_CONTENT_WITHOUT_NEW_SHORE
                    + "\n\n大家此前在建筑服完成的建筑已安置于新岸社区，可作为各位抵达后的住所。社区位于 X: 10580，Z: 1200 附近。";
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

    /** 升级仍使用内置旧文案的开场公告，不覆盖服主自定义内容。 */
    public static boolean migrateAbydosTownName(List<NoticeData> notices) {
        if (notices == null) {
            return false;
        }
        NoticeData desertTown = findByKey(notices, DESERT_TOWN_KEY);
        if (desertTown == null) {
            return false;
        }
        boolean changed = false;
        if (LEGACY_DESERT_TOWN_TITLE.equals(desertTown.getNoticeTitle())) {
            desertTown.setNoticeTitle(ABYDOS_TOWN_TITLE);
            changed = true;
        }
        if (LEGACY_DESERT_TOWN_CONTENT.equals(desertTown.getNoticeContent())
                || PREVIOUS_ABYDOS_TOWN_CONTENT.equals(desertTown.getNoticeContent())
                || PREVIOUS_ABYDOS_TOWN_CONTENT_WITHOUT_NEW_SHORE.equals(
                        desertTown.getNoticeContent())
                || LEGACY_ABYDOS_TOWN_CONTENT.equals(
                        desertTown.getNoticeContent())) {
            desertTown.setNoticeContent(ABYDOS_TOWN_CONTENT);
            changed = true;
        }
        return changed;
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
