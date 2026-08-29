package com.hhy.dreamingfishcore.server.notice_system;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoticeVisibilityPolicyTest {
    private static final String STAGE_ID = "dreamingfishcore:dream_beginning";

    @Test
    void legacyJsonDefaultsToMaintenance() {
        NoticeData legacy = new Gson().fromJson(
                "{\"noticeId\":7,\"noticeTitle\":\"旧公告\","
                        + "\"noticeContent\":\"内容\",\"publishTime\":10}",
                NoticeData.class);

        assertEquals(NoticeCategory.MAINTENANCE, legacy.getCategory());
        assertEquals("", legacy.getStoryStageId());
        assertEquals("", legacy.getStoryDate());
        assertEquals("", legacy.getNoticeKey());
    }

    @Test
    void terminalVisibilityDoesNotRequireTutorialButStillRequiresCurrentStage() {
        NoticeData maintenance = new NoticeData(1, "维护", "内容", 10);
        NoticeData game = new NoticeData(
                2, "剧情", "内容", 20, NoticeCategory.GAME,
                STAGE_ID, "危机第1日", "opening.test");

        assertTrue(NoticeVisibilityPolicy.isVisible(maintenance, "", false));
        assertTrue(NoticeVisibilityPolicy.isVisible(game, STAGE_ID, false));
        assertTrue(NoticeVisibilityPolicy.isVisible(game, STAGE_ID, true));
        assertFalse(NoticeVisibilityPolicy.isVisible(game, "dreamingfishcore:other", true));
    }

    @Test
    void automaticDeliveryKeepsTutorialGateSeparateFromTerminalVisibility() {
        NoticeData maintenance = new NoticeData(1, "维护", "内容", 10);
        NoticeData game = new NoticeData(
                2, "剧情", "内容", 20, NoticeCategory.GAME,
                STAGE_ID, "危机第1日", "opening.test");

        assertTrue(NoticeVisibilityPolicy.isDeliverable(maintenance, STAGE_ID, false));
        assertTrue(NoticeVisibilityPolicy.isVisible(game, STAGE_ID, false));
        assertFalse(NoticeVisibilityPolicy.isDeliverable(game, STAGE_ID, false));
        assertTrue(NoticeVisibilityPolicy.isDeliverable(game, STAGE_ID, true));

        List<NoticeData> beforeTutorial = NoticeVisibilityPolicy.selectForDelivery(
                List.of(maintenance, game), Set.of(), STAGE_ID, false);
        assertEquals(List.of(maintenance), beforeTutorial);
    }

    @Test
    void deliveryUsesPublishOrderAndDeliveredIdsNotReadIds() {
        NoticeData olderGame = new NoticeData(
                2, "早", "内容", 10, NoticeCategory.GAME, STAGE_ID, "", "early");
        NoticeData newerMaintenance = new NoticeData(1, "维护", "内容", 30);
        NoticeData deliveredGame = new NoticeData(
                3, "已投递", "内容", 20, NoticeCategory.GAME, STAGE_ID, "", "delivered");

        List<NoticeData> pending = NoticeVisibilityPolicy.selectForDelivery(
                List.of(newerMaintenance, deliveredGame, olderGame),
                Set.of(deliveredGame.getNoticeId()),
                STAGE_ID,
                true);

        assertEquals(List.of(olderGame, newerMaintenance), pending);
    }

    @Test
    void settersNormalizeNullAndBlankStoryFields() {
        NoticeData notice = new NoticeData(
                1, null, null, 0, null, null, "   ", null);

        assertEquals(NoticeCategory.MAINTENANCE, notice.getCategory());
        assertEquals("", notice.getStoryStageId());
        assertEquals("", notice.getStoryDate());
        assertEquals("", notice.getNoticeKey());

        notice.setCategory(null);
        notice.setStoryStageId(null);
        notice.setStoryDate(null);
        notice.setNoticeKey(null);
        assertEquals(NoticeCategory.MAINTENANCE, notice.getCategory());
        assertEquals("", notice.getStoryStageId());
        assertEquals("", notice.getStoryDate());
        assertEquals("", notice.getNoticeKey());
    }
}
