package com.hhy.dreamingfishcore.server.notice_system;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure notice visibility and delivery-selection rules.
 *
 * <p>The server-facing delivery service supplies the current story snapshot
 * and tutorial state; this class deliberately has no Minecraft dependency so
 * the compatibility rules can be unit-tested in isolation.</p>
 */
public final class NoticeVisibilityPolicy {
    private NoticeVisibilityPolicy() {
    }

    /**
     * Returns whether a notice may be shown in the terminal right now.
     *
     * <p>Reading an announcement must not depend on the new-player tutorial:
     * a player may have skipped the tutorial, joined through an older client,
     * or simply want to read the story notices first.  Tutorial completion is
     * therefore deliberately not part of terminal visibility.  It is only a
     * gate for automatic left-corner delivery (see {@link #isDeliverable}).</p>
     */
    public static boolean isVisible(NoticeData notice, String currentStageId,
                                    boolean tutorialCompleted) {
        return isVisible(notice, currentStageId);
    }

    /** Returns whether a notice may be shown in the terminal right now. */
    public static boolean isVisible(NoticeData notice, String currentStageId) {
        if (notice == null) {
            return false;
        }
        if (notice.getCategory() == NoticeCategory.MAINTENANCE) {
            return true;
        }
        String noticeStageId = notice.getStoryStageId();
        return !noticeStageId.isEmpty()
                && currentStageId != null
                && !currentStageId.isBlank()
                && noticeStageId.equals(currentStageId.trim());
    }

    /**
     * Returns whether a notice may be pushed automatically to the player's
     * top-left notification area.  Story notices wait for tutorial completion
     * so the onboarding sequence remains orderly; terminal visibility does not
     * use this gate.
     */
    public static boolean isDeliverable(NoticeData notice, String currentStageId,
                                        boolean tutorialCompleted) {
        return isVisible(notice, currentStageId)
                && (!notice.isGameNotice() || tutorialCompleted);
    }

    public static List<NoticeData> filterVisible(List<NoticeData> notices,
                                                  String currentStageId,
                                                  boolean tutorialCompleted) {
        return filterVisible(notices, currentStageId);
    }

    /** Filters notices that are currently readable in the terminal. */
    public static List<NoticeData> filterVisible(List<NoticeData> notices,
                                                 String currentStageId) {
        if (notices == null || notices.isEmpty()) {
            return Collections.emptyList();
        }
        List<NoticeData> visible = new ArrayList<>();
        for (NoticeData notice : notices) {
            if (isVisible(notice, currentStageId)) {
                visible.add(notice);
            }
        }
        return visible;
    }

    /**
     * Selects notices that may be pushed now. The read set is intentionally
     * not an input: opening a notice and receiving a notice are independent
     * states. Callers pass only delivered IDs for de-duplication.
     */
    public static List<NoticeData> selectForDelivery(List<NoticeData> notices,
                                                      Set<Integer> deliveredNoticeIds,
                                                      String currentStageId,
                                                      boolean tutorialCompleted) {
        Set<Integer> delivered = deliveredNoticeIds == null
                ? Collections.emptySet()
                : new HashSet<>(deliveredNoticeIds);
        List<NoticeData> pending = new ArrayList<>();
        for (NoticeData notice : notices == null ? Collections.<NoticeData>emptyList() : notices) {
            if (notice != null
                    && !delivered.contains(notice.getNoticeId())
                    && isDeliverable(notice, currentStageId, tutorialCompleted)) {
                pending.add(notice);
            }
        }
        return sortForDelivery(pending);
    }

    /** Publish order is ascending; ID is the deterministic tie-breaker. */
    public static List<NoticeData> sortForDelivery(List<NoticeData> notices) {
        if (notices == null || notices.isEmpty()) {
            return Collections.emptyList();
        }
        List<NoticeData> sorted = new ArrayList<>(notices);
        sorted.sort(Comparator.comparingLong(NoticeData::getPublishTime)
                .thenComparingInt(NoticeData::getNoticeId));
        return sorted;
    }
}
