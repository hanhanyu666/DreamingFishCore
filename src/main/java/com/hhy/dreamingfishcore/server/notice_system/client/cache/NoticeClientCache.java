package com.hhy.dreamingfishcore.server.notice_system.client.cache;

import com.hhy.dreamingfishcore.server.notice_system.NoticeData;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 当前客户端玩家可见公告的轻量未读快照。
 *
 * <p>公告终端的完整列表仍由 {@code ServerScreenUI_Screen} 渲染；这个缓存只保存
 * HUD 提醒卡需要的未读 ID，避免 HUD 每帧依赖终端界面状态。</p>
 */
public final class NoticeClientCache {
    private static Set<Integer> unreadNoticeIds = Set.of();
    private static boolean loaded;

    private NoticeClientCache() {
    }

    /** Replaces the snapshot sent by the server and recalculates unread IDs. */
    public static synchronized void set(List<NoticeData> notices, Set<Integer> readNoticeIds) {
        Set<Integer> safeReadIds = readNoticeIds == null ? Set.of() : readNoticeIds;
        Set<Integer> unread = new HashSet<>();
        if (notices != null) {
            for (NoticeData notice : notices) {
                if (notice != null && !safeReadIds.contains(notice.getNoticeId())) {
                    unread.add(notice.getNoticeId());
                }
            }
        }
        unreadNoticeIds = Set.copyOf(unread);
        loaded = true;
    }

    /**
     * Applies the legacy lightweight check response when a full list has not arrived yet.
     * A full snapshot always wins, so a stale check cannot overwrite an exact count.
     */
    public static synchronized void setUnreadHint(boolean hasUnread, int latestNoticeId) {
        if (loaded) {
            return;
        }
        if (hasUnread && latestNoticeId >= 0) {
            unreadNoticeIds = Set.of(latestNoticeId);
        } else {
            unreadNoticeIds = Set.of();
        }
    }

    public static synchronized int getUnreadCount() {
        return unreadNoticeIds.size();
    }

    public static synchronized boolean hasUnread() {
        return !unreadNoticeIds.isEmpty();
    }

    public static synchronized boolean isLoaded() {
        return loaded;
    }

    /** Optimistically removes a notice after the player opens its detail page. */
    public static synchronized void markRead(int noticeId) {
        if (!unreadNoticeIds.contains(noticeId)) {
            return;
        }
        Set<Integer> remaining = new HashSet<>(unreadNoticeIds);
        remaining.remove(noticeId);
        unreadNoticeIds = Set.copyOf(remaining);
    }

    public static synchronized void clear() {
        unreadNoticeIds = Set.of();
        loaded = false;
    }
}
