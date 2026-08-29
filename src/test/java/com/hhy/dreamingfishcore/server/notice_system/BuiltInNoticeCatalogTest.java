package com.hhy.dreamingfishcore.server.notice_system;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInNoticeCatalogTest {
    @Test
    void createsOnlyTheMissingOpeningSettlementNoticeFromCurrentMaximumId() {
        List<NoticeData> existing = new ArrayList<>(List.of(
                new NoticeData(41, "旧公告", "保留", 1)));

        List<NoticeData> additions = BuiltInNoticeCatalog.createMissingOpeningNotices(existing);

        assertEquals(1, additions.size());
        assertEquals(42, additions.get(0).getNoticeId());
        assertEquals(BuiltInNoticeCatalog.DESERT_TOWN_KEY,
                additions.get(0).getNoticeKey());
        assertEquals(NoticeCategory.GAME, additions.get(0).getCategory());
        assertEquals(BuiltInNoticeCatalog.OPENING_STAGE_ID,
                additions.get(0).getStoryStageId());
        assertEquals(BuiltInNoticeCatalog.OPENING_STORY_DATE,
                additions.get(0).getStoryDate());
        assertEquals("阿拜多斯 · 临时安置通知", additions.get(0).getNoticeTitle());
        assertEquals(
                "各位抵达者：\n\n请尽快前往登记处（坐标 X:9890，Y:151，Z:1771 附近）完成登记。现场已备有照明、床位及首批补给物资。\n\n医院即日起接收伤员，受伤者请优先前往。\n\n登记仅用于统计需求，不作为准入审查。来自外缘带的居民可保留原有预登记信息，我们正在逐条核对。\n\n有意参与救援、建设或公共事务者，请留意终端私信。是否参与，自愿决定。\n\n此前在建筑服中完成的建筑已统一安置于新岸社区（坐标 X:10580，Z:1200 附近），可作为各位抵达后的住所。\n\n特此通知。\n\n阿拜多斯安置点管理处",
                additions.get(0).getNoticeContent());
    }

    @Test
    void isIdempotentAndRetainsExistingNotice() {
        List<NoticeData> existing = new ArrayList<>(List.of(
                new NoticeData(9, "旧公告", "保留", 1),
                new NoticeData(12, "已有开场", "保留", 100,
                        NoticeCategory.GAME,
                        BuiltInNoticeCatalog.OPENING_STAGE_ID,
                        BuiltInNoticeCatalog.OPENING_STORY_DATE,
                        BuiltInNoticeCatalog.DESERT_TOWN_KEY)));

        List<NoticeData> additions = BuiltInNoticeCatalog.createMissingOpeningNotices(existing);
        existing.addAll(additions);
        List<NoticeData> secondPass = BuiltInNoticeCatalog.createMissingOpeningNotices(existing);

        assertEquals(2, existing.size());
        assertEquals("旧公告", existing.get(0).getNoticeTitle());
        assertEquals(0, secondPass.size());
        assertEquals(0, additions.size());
    }

    @Test
    void catalogPreparationDoesNotMutateInputBeforePersistence() {
        List<NoticeData> existing = new ArrayList<>(List.of(
                new NoticeData(4, "旧公告", "保留", 1)));

        List<NoticeData> additions = BuiltInNoticeCatalog.createMissingOpeningNotices(existing);

        assertEquals(1, existing.size());
        assertEquals(4, existing.get(0).getNoticeId());
        assertEquals(1, additions.size());
    }

    @Test
    void migratesOnlyTheUnmodifiedDesertTownCopyToAbydos() {
        NoticeData legacy = new NoticeData(
                7,
                BuiltInNoticeCatalog.LEGACY_DESERT_TOWN_TITLE,
                BuiltInNoticeCatalog.LEGACY_DESERT_TOWN_CONTENT,
                1L,
                NoticeCategory.GAME,
                BuiltInNoticeCatalog.OPENING_STAGE_ID,
                BuiltInNoticeCatalog.OPENING_STORY_DATE,
                BuiltInNoticeCatalog.DESERT_TOWN_KEY);

        assertTrue(BuiltInNoticeCatalog.migrateAbydosTownName(List.of(legacy)));
        assertEquals(BuiltInNoticeCatalog.ABYDOS_TOWN_TITLE, legacy.getNoticeTitle());
        assertEquals(BuiltInNoticeCatalog.ABYDOS_TOWN_CONTENT, legacy.getNoticeContent());
        assertFalse(BuiltInNoticeCatalog.migrateAbydosTownName(List.of(legacy)));

        NoticeData customized = new NoticeData(
                8, "自定义标题", "自定义正文", 2L,
                NoticeCategory.GAME,
                BuiltInNoticeCatalog.OPENING_STAGE_ID,
                BuiltInNoticeCatalog.OPENING_STORY_DATE,
                BuiltInNoticeCatalog.DESERT_TOWN_KEY);
        assertFalse(BuiltInNoticeCatalog.migrateAbydosTownName(List.of(customized)));
        assertEquals("自定义标题", customized.getNoticeTitle());
        assertEquals("自定义正文", customized.getNoticeContent());
    }

    @Test
    void fillsCoordinatesInThePreviousUnmodifiedAbydosNotice() {
        NoticeData previous = new NoticeData(
                9,
                BuiltInNoticeCatalog.ABYDOS_TOWN_TITLE,
                BuiltInNoticeCatalog.PREVIOUS_ABYDOS_TOWN_CONTENT,
                3L,
                NoticeCategory.GAME,
                BuiltInNoticeCatalog.OPENING_STAGE_ID,
                BuiltInNoticeCatalog.OPENING_STORY_DATE,
                BuiltInNoticeCatalog.DESERT_TOWN_KEY);

        assertTrue(BuiltInNoticeCatalog.migrateAbydosTownName(List.of(previous)));
        assertEquals(BuiltInNoticeCatalog.ABYDOS_TOWN_TITLE, previous.getNoticeTitle());
        assertEquals(BuiltInNoticeCatalog.ABYDOS_TOWN_CONTENT, previous.getNoticeContent());
        assertFalse(BuiltInNoticeCatalog.migrateAbydosTownName(List.of(previous)));
    }

    @Test
    void addsNewShoreCommunityToThePreviousUnmodifiedOpeningNotice() {
        NoticeData previous = new NoticeData(
                10,
                BuiltInNoticeCatalog.ABYDOS_TOWN_TITLE,
                BuiltInNoticeCatalog.PREVIOUS_ABYDOS_TOWN_CONTENT_WITHOUT_NEW_SHORE,
                4L,
                NoticeCategory.GAME,
                BuiltInNoticeCatalog.OPENING_STAGE_ID,
                BuiltInNoticeCatalog.OPENING_STORY_DATE,
                BuiltInNoticeCatalog.DESERT_TOWN_KEY);

        assertTrue(BuiltInNoticeCatalog.migrateAbydosTownName(List.of(previous)));
        assertEquals(BuiltInNoticeCatalog.ABYDOS_TOWN_CONTENT, previous.getNoticeContent());
        assertFalse(BuiltInNoticeCatalog.migrateAbydosTownName(List.of(previous)));
    }

    @Test
    void migratesThePreviouslyPublishedFullOpeningNoticeToTheOfficialNotice() {
        NoticeData previous = new NoticeData(
                11,
                BuiltInNoticeCatalog.ABYDOS_TOWN_TITLE,
                BuiltInNoticeCatalog.LEGACY_ABYDOS_TOWN_CONTENT,
                5L,
                NoticeCategory.GAME,
                BuiltInNoticeCatalog.OPENING_STAGE_ID,
                BuiltInNoticeCatalog.OPENING_STORY_DATE,
                BuiltInNoticeCatalog.DESERT_TOWN_KEY);

        assertTrue(BuiltInNoticeCatalog.migrateAbydosTownName(List.of(previous)));
        assertEquals(BuiltInNoticeCatalog.ABYDOS_TOWN_CONTENT, previous.getNoticeContent());
        assertFalse(BuiltInNoticeCatalog.migrateAbydosTownName(List.of(previous)));
    }

}
