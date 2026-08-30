package com.hhy.dreamingfishcore.gameplay.story_system.runtime;

import com.hhy.dreamingfishcore.gameplay.story_system.StoryWorldState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 可由服主编辑的故事流程定义。 */
public final class StoryFlowDefinition {
    private String id = "";
    private String stageId = "";
    private StoryFlowScope scope = StoryFlowScope.PLAYER;
    private boolean enabled = true;
    /** 新玩家在该流程中等待的第一个节点。 */
    private String initialNodeId = "";
    private List<StoryFlowNode> nodes = new ArrayList<>();

    public StoryFlowDefinition() {
    }

    public StoryFlowDefinition(
            String id,
            String stageId,
            StoryFlowScope scope,
            String initialNodeId,
            List<StoryFlowNode> nodes) {
        this.id = id;
        this.stageId = stageId;
        this.scope = scope;
        this.initialNodeId = initialNodeId;
        this.nodes = nodes == null ? new ArrayList<>() : new ArrayList<>(nodes);
    }

    public String getId() {
        return id == null ? "" : id;
    }

    public String getStageId() {
        return stageId == null ? "" : stageId;
    }

    public StoryFlowScope getScope() {
        return scope == null ? StoryFlowScope.PLAYER : scope;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getInitialNodeId() {
        return initialNodeId == null ? "" : initialNodeId.trim();
    }

    public List<StoryFlowNode> getNodes() {
        if (nodes == null) {
            nodes = new ArrayList<>();
        }
        return Collections.unmodifiableList(nodes);
    }

    List<StoryFlowNode> matchingNodes(StoryEvent event) {
        if (!enabled || event == null) {
            return List.of();
        }
        return getNodes().stream()
                .filter(node -> node != null && node.matches(event))
                .toList();
    }

    void validate(Set<String> stageIds) {
        StoryWorldState.requireValidId(getId(), "故事流程");
        StoryWorldState.requireValidId(getStageId(), "故事流程阶段");
        if (!stageIds.contains(getStageId())) {
            throw new IllegalStateException(
                    "故事流程引用不存在的阶段：" + getId() + " -> " + getStageId());
        }
        if (scope == null || getNodes().isEmpty()) {
            throw new IllegalStateException("故事流程必须有范围和至少一个节点：" + getId());
        }
        if (!getInitialNodeId().isBlank() && !NODE_ID_SET(getNodes()).contains(getInitialNodeId())) {
            throw new IllegalStateException("故事流程初始节点不存在：" + getId());
        }
        Set<String> nodeIds = new HashSet<>();
        for (StoryFlowNode node : getNodes()) {
            if (node == null || !nodeIds.add(node.getId())) {
                throw new IllegalStateException("故事流程节点 ID 重复或为空：" + getId());
            }
        }
        if (getInitialNodeId().isBlank()) {
            throw new IllegalStateException("故事流程必须声明 initialNodeId：" + getId());
        }
        for (StoryFlowNode node : getNodes()) {
            if (node.getScope() != getScope()) {
                throw new IllegalStateException(
                        "故事流程节点范围必须与流程一致：" + getId() + "/" + node.getId());
            }
            node.validate(getId(), nodeIds);
        }
    }

    private static Set<String> NODE_ID_SET(List<StoryFlowNode> nodes) {
        Set<String> ids = new HashSet<>();
        for (StoryFlowNode node : nodes) {
            if (node != null) {
                ids.add(node.getId());
            }
        }
        return ids;
    }
}
