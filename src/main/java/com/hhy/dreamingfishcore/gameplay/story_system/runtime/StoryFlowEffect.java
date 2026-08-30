package com.hhy.dreamingfishcore.gameplay.story_system.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * 一个由内容包声明的故事效果。
 *
 * <p>效果只描述参数，不直接持有 Minecraft 对象。服务端运行时根据 type 找到
 * {@link StoryEffectExecutor}，这样文案和流程顺序可以在 JSON 中调整，而效果实现仍由
 * 服务端掌握。{@code id} 是节点内稳定的效果实例 ID，供一次性日志使用；parameters
 * 使用字符串是刻意的：它让配置文件稳定、易读，也避免把
 * Gson 的浮点数语义泄露到剧情数据中。</p>
 */
public final class StoryFlowEffect {
    private static final Pattern EFFECT_INSTANCE_ID =
            Pattern.compile("[a-z0-9][a-z0-9._/-]{0,95}");

    /** 节点内稳定的效果 ID；一次性效果日志依赖它，而不是效果数组下标。 */
    private String id = "";
    private String type = "";
    private Map<String, String> parameters = new LinkedHashMap<>();
    private List<StoryFlowItemGrant> itemGrants = new ArrayList<>();
    /** 一次性效果在成功执行后记入玩家流程存档；可重复效果每次事件都重新执行。 */
    private boolean once = true;

    public StoryFlowEffect() {
    }

    public StoryFlowEffect(String type) {
        this.type = type;
        this.id = defaultId(type);
    }

    public StoryFlowEffect(String type, Map<String, String> parameters) {
        this(type);
        if (parameters != null) {
            this.parameters = new LinkedHashMap<>(parameters);
        }
    }

    public StoryFlowEffect(String id, String type, Map<String, String> parameters) {
        this.id = id;
        this.type = type;
        if (parameters != null) {
            this.parameters = new LinkedHashMap<>(parameters);
        }
    }

    public String getId() {
        return id == null ? "" : id.trim();
    }

    public String getType() {
        return type == null ? "" : type;
    }

    public Map<String, String> getParameters() {
        if (parameters == null) {
            parameters = new LinkedHashMap<>();
        }
        return Collections.unmodifiableMap(parameters);
    }

    public String getParameter(String key) {
        if (key == null || parameters == null) {
            return "";
        }
        String value = parameters.get(key);
        return value == null ? "" : value.trim();
    }

    public List<StoryFlowItemGrant> getItemGrants() {
        if (itemGrants == null) {
            itemGrants = new ArrayList<>();
        }
        return Collections.unmodifiableList(itemGrants);
    }

    public boolean isOnce() {
        return once;
    }

    public StoryFlowEffect once(boolean value) {
        this.once = value;
        return this;
    }

    public StoryFlowEffect withId(String effectId) {
        id = effectId == null ? "" : effectId;
        return this;
    }

    public StoryFlowEffect withParameter(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("故事效果参数名不能为空");
        }
        if (parameters == null) {
            parameters = new LinkedHashMap<>();
        }
        parameters.put(key, value == null ? "" : value);
        return this;
    }

    public StoryFlowEffect withItemGrants(List<StoryFlowItemGrant> grants) {
        itemGrants = grants == null ? new ArrayList<>() : new ArrayList<>(grants);
        return this;
    }

    void validate(String flowId, String nodeId, int index) {
        if (!EFFECT_INSTANCE_ID.matcher(getId()).matches()) {
            throw new IllegalStateException(
                    "故事效果实例 ID 非法：" + flowId + "/" + nodeId + "/" + getId());
        }
        String normalizedType = getType().trim().toUpperCase(java.util.Locale.ROOT);
        if (!StoryFlowNode.isValidEffectId(normalizedType)) {
            throw new IllegalStateException(
                    "故事效果 ID 非法：" + flowId + "/" + nodeId + "/" + getType());
        }
        if (parameters == null) {
            parameters = new LinkedHashMap<>();
        }
        if (parameters.size() > 64) {
            throw new IllegalStateException(
                    "故事效果参数过多：" + flowId + "/" + nodeId + "/" + index);
        }
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getKey().length() > 96
                    || entry.getValue() == null || entry.getValue().length() > 8192) {
                throw new IllegalStateException(
                        "故事效果参数非法：" + flowId + "/" + nodeId + "/" + entry.getKey());
            }
        }
        if (itemGrants == null) {
            itemGrants = new ArrayList<>();
        }
        if (itemGrants.size() > 64) {
            throw new IllegalStateException(
                    "故事效果物品奖励过多：" + flowId + "/" + nodeId);
        }
        for (StoryFlowItemGrant grant : itemGrants) {
            if (grant == null) {
                throw new IllegalStateException(
                        "故事效果包含空物品奖励：" + flowId + "/" + nodeId);
            }
            grant.validate(flowId, nodeId);
        }
    }

    private static String defaultId(String effectType) {
        if (effectType == null || effectType.isBlank()) {
            return "";
        }
        return effectType.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
