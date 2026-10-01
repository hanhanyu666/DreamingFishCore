package com.hhy.dreamingfishcore.server.notice_system.client.cache;

import com.hhy.dreamingfishcore.server.notice_system.NoticeData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 当前客户端玩家可见公告的轻量未读快照。
 *
 * <p>公告终端的完整列表由 {@code TerminalData} 保存并在梦屿终端中渲染；这个缓存保存未读
 * ID 以及标题轻量视图，让 HUD 可以直接告诉玩家“哪一条公告”尚未阅读。</p>
 */
public final class NoticeClientCache {
    private static Set<Integer> unreadNoticeIds = Set.of();
    /** Immutable title-only view used by the in-world reminder card. */
    private static List<UnreadNotice> unreadNotices = List.of();
    private static boolean loaded;

    private NoticeClientCache() {
    }

    /** Replaces the snapshot sent by the server and recalculates unread IDs. */
    public static synchronized void set(List<NoticeData> notices, Set<Integer> readNoticeIds) {
        Set<Integer> safeReadIds = readNoticeIds == null ? Set.of() : readNoticeIds;
        Set<Integer> unread = new HashSet<>();
        ArrayList<UnreadNotice> unreadWithTitles = new ArrayList<>();
        if (notices != null) {
            for (NoticeData notice : notices) {
                if (notice != null && !safeReadIds.contains(notice.getNoticeId())) {
                    unread.add(notice.getNoticeId());
                    unreadWithTitles.add(new UnreadNotice(
                            notice.getNoticeId(),
                            normalizeTitle(notice.getNoticeTitle(), notice.getNoticeId())));
                }
            }
        }
        unreadNoticeIds = Set.copyOf(unread);
        unreadNotices = List.copyOf(unreadWithTitles);
        loaded = true;
    }

    /**
     * Applies the legacy lightweight check response when a full list has not arrived yet.
     * A full snapshot always wins, so a stale check cannot overwrite an exact count.
     */
    public static synchronized void setUnreadHint(
            boolean hasUnread, int latestNoticeId, String latestNoticeTitle) {
        if (loaded) {
            return;
        }
        if (hasUnread && latestNoticeId >= 0) {
            unreadNoticeIds = Set.of(latestNoticeId);
            unreadNotices = List.of(new UnreadNotice(
                    latestNoticeId, normalizeTitle(latestNoticeTitle, latestNoticeId)));
        } else {
            unreadNoticeIds = Set.of();
            unreadNotices = List.of();
        }
    }

    /** Keeps callers that only have the old lightweight ID hint source-compatible. */
    public static synchronized void setUnreadHint(boolean hasUnread, int latestNoticeId) {
        setUnreadHint(hasUnread, latestNoticeId, "");
    }

    public static synchronized int getUnreadCount() {
        return unreadNoticeIds.size();
    }

    public static synchronized boolean hasUnread() {
        return !unreadNoticeIds.isEmpty();
    }

    /** Returns every unread notice and its title in the server-provided order. */
    public static synchronized List<UnreadNotice> getUnreadNotices() {
        return unreadNotices;
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
        unreadNotices = unreadNotices.stream()
                .filter(notice -> notice.noticeId() != noticeId)
                .toList();
    }

    public static synchronized void clear() {
        unreadNoticeIds = Set.of();
        unreadNotices = List.of();
        loaded = false;
    }

    private static String normalizeTitle(String title, int noticeId) {
        if (title != null) {
            String normalized = title.trim();
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "公告 #" + noticeId;
    }

    /** Minimal immutable data needed by the HUD; the full body stays in the terminal cache. */
    public record UnreadNotice(int noticeId, String title) {
        public UnreadNotice {
            title = title == null || title.isBlank() ? "未命名公告" : title;
        }
    }
}
