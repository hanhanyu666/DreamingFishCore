package com.hhy.dreamingfishcore.server.notice_system;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoticeDeliveryServiceTest {
    @Test
    void gameAndServerNoticeUseDifferentPlayerFacingLabels() {
        NoticeData game = notice(1, NoticeCategory.GAME, "道路观察");
        NoticeData server = notice(2, NoticeCategory.MAINTENANCE, "维护安排");

        String gameSummary = NoticeDeliveryService.formatSummary(game);
        String serverSummary = NoticeDeliveryService.formatSummary(server);

        assertTrue(gameSummary.startsWith("§6道路观察\n"));
        assertTrue(serverSummary.startsWith("§b服务器公告§f "));
        assertFalse(gameSummary.contains("梦屿广播§f"));
        assertFalse(gameSummary.contains("【"));
        assertFalse(gameSummary.contains("】"));
        assertFalse(serverSummary.contains("【"));
        assertFalse(serverSummary.contains("】"));
        assertTrue(serverSummary.contains("“梦屿广播”"));
    }

    @Test
    void onlyMoreThanTwoGameNoticesAreAggregated() {
        NoticeData gameOne = notice(1, NoticeCategory.GAME, "一");
        NoticeData gameTwo = notice(2, NoticeCategory.GAME, "二");
        NoticeData gameThree = notice(3, NoticeCategory.GAME, "三");
        NoticeData server = notice(4, NoticeCategory.MAINTENANCE, "维护");

        assertFalse(NoticeDeliveryService.shouldAggregateGameNotices(List.of(gameOne, server)));
        assertFalse(NoticeDeliveryService.shouldAggregateGameNotices(List.of(gameOne, gameTwo, server)));
        assertTrue(NoticeDeliveryService.shouldAggregateGameNotices(
                List.of(gameOne, gameTwo, gameThree, server)));
    }

    private static NoticeData notice(int id, NoticeCategory category, String title) {
        return new NoticeData(id, title, "正文", 1L, category, "", "", "test:" + id);
    }
}
