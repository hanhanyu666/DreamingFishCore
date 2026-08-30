package com.hhy.dreamingfishcore.gameplay.story_system.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 内容包中的一个“事实触发 → 状态校验 → 效果 → 下一节点”节点。 */
public final class StoryFlowNode {
    private static final Pattern NODE_ID = Pattern.compile("[a-z0-9][a-z0-9._/-]{0,95}");
    /** 效果是服务端注册的扩展点，不把未来效果硬编码进当前模组版本。 */
    private static final Pattern EFFECT_ID = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern CONDITION_KEY = Pattern.compile("[a-zA-Z][a-zA-Z0-9_.-]{0,63}");
    private static final Set<String> BUILT_IN_CONDITIONS = Set.of(
            "cursor", "membership", "playerFlag", "worldFlag");

    private String id = "";
    private StoryEventType event = StoryEventType.PLAYER_AUTHENTICATED;
    private String subjectId = "";
    private String secondaryId = "";
    private String locationId = "";
    private StoryFlowScope scope = StoryFlowScope.PLAYER;
    private Map<String, String> conditions = new LinkedHashMap<>();
    private List<StoryFlowEffect> effects = new ArrayList<>();
    private String nextNodeId = "";
    /** 无下一节点的节点默认只执行一次；修复/重发节点可声明 repeatable。 */
    private boolean repeatable;
    /** 非空时，玩家在该流程状态下打开对应 NPC 会看到这里的台词。 */
    private int dialogueNpcId;
    private List<String> dialogueLines = new ArrayList<>();

    public StoryFlowNode() {
    }

    public StoryFlowNode(
            String id,
            StoryEventType event,
            String subjectId,
            String secondaryId,
            String locationId,
            StoryFlowScope scope,
            List<StoryFlowEffect> effects) {
        this.id = id;
        this.event = event;
        this.subjectId = subjectId;
        this.secondaryId = secondaryId;
        this.locationId = locationId;
        this.scope = scope;
        this.effects = effects == null ? new ArrayList<>() : new ArrayList<>(effects);
    }

    public String getId() {
        return id == null ? "" : id;
    }

    public StoryEventType getEvent() {
        return event == null ? StoryEventType.PLAYER_AUTHENTICATED : event;
    }

    public String getSubjectId() {
        return subjectId == null ? "" : subjectId.trim();
    }

    public String getSecondaryId() {
        return secondaryId == null ? "" : secondaryId.trim();
    }

    public String getLocationId() {
        return locationId == null ? "" : locationId.trim();
    }

    public StoryFlowScope getScope() {
        return scope == null ? StoryFlowScope.PLAYER : scope;
    }

    public Map<String, String> getConditions() {
        if (conditions == null) {
            conditions = new LinkedHashMap<>();
        }
        return Collections.unmodifiableMap(conditions);
    }

    public String getCondition(String key) {
        if (key == null || conditions == null) {
            return "";
        }
        String value = conditions.get(key);
        return value == null ? "" : value.trim();
    }

    public List<StoryFlowEffect> getEffects() {
        if (effects == null) {
            effects = new ArrayList<>();
        }
        return Collections.unmodifiableList(effects);
    }

    public String getNextNodeId() {
        return nextNodeId == null ? "" : nextNodeId.trim();
    }

    public boolean isRepeatable() {
        return repeatable;
    }

    public int getDialogueNpcId() {
        return dialogueNpcId;
    }

    public List<String> getDialogueLines() {
        if (dialogueLines == null) {
            dialogueLines = new ArrayList<>();
        }
        return Collections.unmodifiableList(dialogueLines);
    }

    public StoryFlowNode withCondition(String key, String value) {
        if (conditions == null) {
            conditions = new LinkedHashMap<>();
        }
        conditions.put(key, value == null ? "" : value);
        return this;
    }

    public StoryFlowNode withNextNode(String next) {
        nextNodeId = next == null ? "" : next;
        return this;
    }

    public StoryFlowNode withLocation(String location) {
        locationId = location == null ? "" : location;
        return this;
    }

    public StoryFlowNode repeatable(boolean value) {
        repeatable = value;
        return this;
    }

    public StoryFlowNode withDialogue(int npcId, List<String> lines) {
        dialogueNpcId = npcId;
        dialogueLines = lines == null ? new ArrayList<>() : new ArrayList<>(lines);
        return this;
    }

    static boolean isValidEffectId(String effectId) {
        return effectId != null && EFFECT_ID.matcher(effectId).matches();
    }

    boolean matches(StoryEvent actual) {
        if (actual == null || getEvent() != actual.type()) {
            return false;
        }
        return matchesOptional(getSubjectId(), actual.subjectId())
                && matchesOptional(getSecondaryId(), actual.secondaryId())
                && matchesOptional(getLocationId(), actual.locationId());
    }

    boolean hasDialogueFor(int npcId) {
        return dialogueNpcId == npcId && npcId > 0 && !getDialogueLines().isEmpty();
    }

    void validate(String flowId, Set<String> nodeIds) {
        if (!NODE_ID.matcher(getId()).matches()) {
            throw new IllegalStateException("故事流程节点 ID 非法：" + flowId + "/" + getId());
        }
        if (event == null || scope == null) {
            throw new IllegalStateException("故事流程节点缺少事件或范围：" + flowId + "/" + getId());
        }
        if (getSubjectId().length() > 160 || getSecondaryId().length() > 160
                || getLocationId().length() > 160) {
            throw new IllegalStateException("故事流程节点匹配字段过长：" + flowId + "/" + getId());
        }
        if (conditions == null) {
            conditions = new LinkedHashMap<>();
        }
        if (conditions.size() > 32) {
            throw new IllegalStateException("故事流程节点条件过多：" + flowId + "/" + getId());
        }
        for (Map.Entry<String, String> condition : conditions.entrySet()) {
            if (condition.getKey() == null
                    || !CONDITION_KEY.matcher(condition.getKey()).matches()
                    || condition.getValue() == null
                    || condition.getValue().length() > 256) {
                throw new IllegalStateException(
                        "故事流程节点条件非法：" + flowId + "/" + getId());
            }
            if (!BUILT_IN_CONDITIONS.contains(condition.getKey())) {
                throw new IllegalStateException(
                        "故事流程节点使用了未注册的条件：" + flowId + "/" + getId()
                                + "/" + condition.getKey());
            }
        }
        if (effects == null) {
            effects = new ArrayList<>();
        }
        if (effects.size() > 64) {
            throw new IllegalStateException("故事流程节点效果过多：" + flowId + "/" + getId());
        }
        Set<String> effectIds = new java.util.HashSet<>();
        for (int index = 0; index < effects.size(); index++) {
            StoryFlowEffect effect = effects.get(index);
            if (effect == null) {
                throw new IllegalStateException("故事流程节点包含空效果：" + flowId + "/" + getId());
            }
            effect.validate(flowId, getId(), index);
            if (!effectIds.add(effect.getId())) {
                throw new IllegalStateException(
                        "故事流程节点效果 ID 重复：" + flowId + "/" + getId()
                                + "/" + effect.getId());
            }
        }
        if (!getNextNodeId().isBlank() && !nodeIds.contains(getNextNodeId())) {
            throw new IllegalStateException(
                    "故事流程节点指向不存在的 nextNodeId：" + flowId + "/" + getId());
        }
        if (dialogueNpcId < 0 || dialogueNpcId > 2_000_000_000
                || getDialogueLines().size() > 64) {
            throw new IllegalStateException("故事流程节点对话字段非法：" + flowId + "/" + getId());
        }
        for (String line : getDialogueLines()) {
            if (line == null || line.isBlank() || line.length() > 8192) {
                throw new IllegalStateException("故事流程节点包含非法台词：" + flowId + "/" + getId());
            }
        }
        String membership = getCondition("membership");
        if (!membership.isBlank()
                && !membership.equalsIgnoreCase("ANY")
                && !membership.equalsIgnoreCase("MEMBER")
                && !membership.equalsIgnoreCase("NON_MEMBER")) {
            throw new IllegalStateException("故事流程节点成员条件非法：" + flowId + "/" + getId());
        }
    }

    private static boolean matchesOptional(String expected, String actual) {
        return expected.isBlank() || expected.equals(actual == null ? "" : actual);
    }
}
