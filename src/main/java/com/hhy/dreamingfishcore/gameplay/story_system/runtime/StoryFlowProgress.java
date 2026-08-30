package com.hhy.dreamingfishcore.gameplay.story_system.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 一个玩家（或共享作用域）在单条故事流程中的持久状态。
 *
 * <p>cursor 表示下一次允许处理的节点；completedNodes 和 appliedEffects（节点 ID/效果 ID）用于保证
 * 重连、重复点击以及服务器重启不会重复执行不可逆效果。它不保存任何 Minecraft
 * 实体引用，因而可以安全地写入世界 JSON。</p>
 */
public final class StoryFlowProgress {
    private static final int MAX_COMPLETED_NODES = 4096;
    private static final int MAX_APPLIED_EFFECTS = 16_384;
    private static final int MAX_FLAGS = 4096;

    private String cursor = "";
    private Set<String> completedNodes = new LinkedHashSet<>();
    private Set<String> appliedEffects = new LinkedHashSet<>();
    private Map<String, String> flags = new LinkedHashMap<>();
    private long updatedAtEpochMillis;

    public StoryFlowProgress() {
    }

    public StoryFlowProgress(String initialNodeId) {
        cursor = initialNodeId == null ? "" : initialNodeId.trim();
    }

    public String getCursor() {
        return cursor == null ? "" : cursor;
    }

    public Set<String> getCompletedNodes() {
        if (completedNodes == null) {
            completedNodes = new LinkedHashSet<>();
        }
        return Collections.unmodifiableSet(completedNodes);
    }

    public Set<String> getAppliedEffects() {
        if (appliedEffects == null) {
            appliedEffects = new LinkedHashSet<>();
        }
        return Collections.unmodifiableSet(appliedEffects);
    }

    public Map<String, String> getFlags() {
        if (flags == null) {
            flags = new LinkedHashMap<>();
        }
        return Collections.unmodifiableMap(flags);
    }

    public long getUpdatedAtEpochMillis() {
        return updatedAtEpochMillis;
    }

    boolean hasCompletedNode(String nodeId) {
        return nodeId != null && completedNodes != null && completedNodes.contains(nodeId);
    }

    boolean hasAppliedEffect(String effectKey) {
        return effectKey != null && appliedEffects != null && appliedEffects.contains(effectKey);
    }

    boolean hasFlag(String flagId) {
        return flagId != null && flags != null && "true".equalsIgnoreCase(flags.get(flagId));
    }

    void setCursor(String nextNodeId, long now) {
        cursor = nextNodeId == null ? "" : nextNodeId.trim();
        touch(now);
    }

    void markNodeCompleted(String nodeId, long now) {
        if (completedNodes == null) {
            completedNodes = new LinkedHashSet<>();
        }
        if (nodeId != null && !nodeId.isBlank()) {
            if (completedNodes.size() >= MAX_COMPLETED_NODES && !completedNodes.contains(nodeId)) {
                throw new IllegalStateException("故事流程已完成节点数量超过上限");
            }
            completedNodes.add(nodeId);
        }
        touch(now);
    }

    void markEffectApplied(String effectKey, long now) {
        if (appliedEffects == null) {
            appliedEffects = new LinkedHashSet<>();
        }
        if (effectKey != null && !effectKey.isBlank()) {
            if (appliedEffects.size() >= MAX_APPLIED_EFFECTS && !appliedEffects.contains(effectKey)) {
                throw new IllegalStateException("故事流程已执行效果数量超过上限");
            }
            appliedEffects.add(effectKey);
        }
        touch(now);
    }

    void setFlag(String flagId, boolean enabled, long now) {
        if (flags == null) {
            flags = new LinkedHashMap<>();
        }
        if (flagId == null || flagId.isBlank()) {
            throw new IllegalArgumentException("玩家故事旗标不能为空");
        }
        if (enabled) {
            if (flags.size() >= MAX_FLAGS && !flags.containsKey(flagId)) {
                throw new IllegalStateException("玩家故事旗标数量超过上限");
            }
            flags.put(flagId, "true");
        } else {
            flags.remove(flagId);
        }
        touch(now);
    }

    boolean repair() {
        boolean changed = false;
        if (cursor == null) {
            cursor = "";
            changed = true;
        }
        if (completedNodes == null) {
            completedNodes = new LinkedHashSet<>();
            changed = true;
        }
        if (appliedEffects == null) {
            appliedEffects = new LinkedHashSet<>();
            changed = true;
        }
        if (flags == null) {
            flags = new LinkedHashMap<>();
            changed = true;
        }
        if (updatedAtEpochMillis < 0L) {
            updatedAtEpochMillis = 0L;
            changed = true;
        }
        return changed;
    }

    void validate(String ownerKey, String flowId, StoryFlowDefinition flow) {
        if (flow == null) {
            throw new IllegalStateException("故事流程状态引用不存在的流程：" + flowId);
        }
        if (cursor == null || cursor.isBlank()) {
            throw new IllegalStateException("故事流程状态缺少 cursor：" + ownerKey + "/" + flowId);
        }
        if (flow.getNodes().stream().noneMatch(node -> node.getId().equals(cursor))) {
            throw new IllegalStateException("故事流程状态 cursor 不存在：" + ownerKey + "/" + flowId);
        }
        if (completedNodes == null || completedNodes.size() > MAX_COMPLETED_NODES
                || appliedEffects == null || appliedEffects.size() > MAX_APPLIED_EFFECTS
                || flags == null || flags.size() > MAX_FLAGS) {
            throw new IllegalStateException("故事流程状态集合超过上限：" + ownerKey + "/" + flowId);
        }
        for (String nodeId : completedNodes) {
            if (nodeId == null || nodeId.isBlank()) {
                throw new IllegalStateException("故事流程完成节点 ID 为空：" + ownerKey + "/" + flowId);
            }
        }
        for (String effectId : appliedEffects) {
            if (effectId == null || effectId.isBlank() || effectId.length() > 256) {
                throw new IllegalStateException("故事流程效果记录非法：" + ownerKey + "/" + flowId);
            }
        }
        for (Map.Entry<String, String> flag : flags.entrySet()) {
            if (flag.getKey() == null || flag.getKey().isBlank()
                    || flag.getValue() == null || flag.getValue().length() > 32) {
                throw new IllegalStateException("故事流程玩家旗标非法：" + ownerKey + "/" + flowId);
            }
        }
        if (updatedAtEpochMillis < 0L) {
            throw new IllegalStateException("故事流程更新时间不能为负数：" + ownerKey + "/" + flowId);
        }
    }

    private void touch(long now) {
        updatedAtEpochMillis = Math.max(0L, now);
    }
}
