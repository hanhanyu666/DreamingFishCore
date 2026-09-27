package com.hhy.dreamingfishcore.gameplay.story_system;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 与真实个人完成记录分开的前情发布、接入及归档事实，随 story_state 一起保存。 */
public final class StoryOperationsState {
    private Map<String, Recap> recaps = new LinkedHashMap<>();
    private Map<String, PlayerAccess> players = new LinkedHashMap<>();
    private Set<String> archivedTasks = new LinkedHashSet<>();

    public void validate() {
        if (recaps == null || players == null || archivedTasks == null
                || recaps.size() > 128 || players.size() > 16384 || archivedTasks.size() > 16384) {
            throw new IllegalStateException("剧情运营状态容器非法或超过容量限制");
        }
        Set<Integer> noticeIds = new LinkedHashSet<>();
        recaps.forEach((key, recap) -> {
            if (recap == null || !key.equals(recap.id()) || !noticeIds.add(recap.noticeId())) {
                throw new IllegalStateException("前情发布记录非法：" + key);
            }
            recap.validate();
        });
        archivedTasks.forEach(id -> StoryWorldState.requireValidId(id, "归档任务"));
        players.forEach((id, access) -> {
            UUID.fromString(id);
            if (access == null || access.reachedOrder < 0 || access.receipts == null
                    || access.waivedTasks == null || access.receipts.size() > 128
                    || access.waivedTasks.size() > 16384) {
                throw new IllegalStateException("玩家前情接入记录非法：" + id);
            }
            access.receipts.forEach((recapId, read) -> {
                if (!recaps.containsKey(recapId) || read == null) {
                    throw new IllegalStateException("前情收件记录没有对应发布：" + recapId);
                }
            });
            access.waivedTasks.forEach(key -> StoryWorldState.requireValidId(key, "跳过任务"));
        });
    }

    public boolean publish(String id, String title, String content, String checkpointId, long now) {
        if (recaps.containsKey(id)) {
            return false;
        }
        if (recaps.size() >= 128) {
            throw new IllegalStateException("前情总结已达 128 份上限");
        }
        // 普通公告使用正整数；负数专用于世界存档里的前情投影，不写入 notices.json。
        Recap recap = new Recap(id, title, content, checkpointId, -1000 - recaps.size(), now);
        recap.validate();
        recaps.put(id, recap);
        return true;
    }

    public boolean deliver(String recapId, UUID playerId, int actualOrder, StoryCheckpoint checkpoint,
                           Set<String> actuallyCompletedTasks) {
        Recap recap = recaps.get(recapId);
        if (recap == null || !recap.checkpointId().equals(checkpoint.id())
                || Math.max(actualOrder, reachedOrder(playerId)) >= checkpoint.order()) {
            return false;
        }
        PlayerAccess access = access(playerId);
        if (access.receipts.containsKey(recapId)) {
            return false;
        }
        access.receipts.put(recapId, false);
        access.reachedOrder = Math.max(access.reachedOrder, checkpoint.order());
        for (String task : checkpoint.skippedTasks()) {
            if (!actuallyCompletedTasks.contains(task)) {
                access.waivedTasks.add(task);
            }
        }
        return true;
    }

    public boolean reach(UUID playerId, int order) {
        if (order <= reachedOrder(playerId)) {
            return false;
        }
        access(playerId).reachedOrder = order;
        return true;
    }

    public int reachedOrder(UUID playerId) {
        PlayerAccess access = playerId == null ? null : players.get(playerId.toString());
        return access == null ? 0 : access.reachedOrder;
    }

    public boolean hasReceived(UUID playerId, String recapId) {
        PlayerAccess access = playerId == null ? null : players.get(playerId.toString());
        return access != null && access.receipts.containsKey(recapId);
    }

    public boolean hasRead(UUID playerId, String recapId) {
        PlayerAccess access = playerId == null ? null : players.get(playerId.toString());
        return access != null && Boolean.TRUE.equals(access.receipts.get(recapId));
    }

    public boolean markRead(UUID playerId, String recapId) {
        if (!hasReceived(playerId, recapId) || hasRead(playerId, recapId)) {
            return false;
        }
        access(playerId).receipts.put(recapId, true);
        return true;
    }

    public boolean isWaived(UUID playerId, String taskKey) {
        PlayerAccess access = playerId == null ? null : players.get(playerId.toString());
        return access != null && access.waivedTasks.contains(taskKey);
    }

    public boolean archive(String taskKey) {
        StoryWorldState.requireValidId(taskKey, "归档任务");
        return archivedTasks.add(taskKey);
    }

    public boolean isArchived(String taskKey) {
        return archivedTasks.contains(taskKey);
    }

    public Map<String, Recap> recaps() {
        return Collections.unmodifiableMap(recaps);
    }

    private PlayerAccess access(UUID playerId) {
        if (!players.containsKey(playerId.toString()) && players.size() >= 16384) {
            throw new IllegalStateException("剧情接入玩家数量超过限制");
        }
        return players.computeIfAbsent(playerId.toString(), ignored -> new PlayerAccess());
    }

    public record Recap(String id, String title, String content, String checkpointId,
                        int noticeId, long publishedAt) {
        void validate() {
            StoryWorldState.requireValidId(id, "前情总结");
            StoryCheckpoint.require(checkpointId);
            if (title == null || title.isBlank() || title.length() > 256
                    || content == null || content.isBlank() || content.length() > 4096
                    || noticeId > -1000 || noticeId < -1127 || publishedAt < 0) {
                throw new IllegalStateException("前情总结正文或编号非法：" + id);
            }
        }
    }

    private static final class PlayerAccess {
        private int reachedOrder;
        private Map<String, Boolean> receipts = new LinkedHashMap<>();
        private Set<String> waivedTasks = new LinkedHashSet<>();
    }
}
