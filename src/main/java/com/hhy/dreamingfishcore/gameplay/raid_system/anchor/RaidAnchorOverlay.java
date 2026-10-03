package com.hhy.dreamingfishcore.gameplay.raid_system.anchor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 锚点的世界层覆盖（服主在游戏内的微调）。
 *
 * <p>设计上锚点分两层：</p>
 * <ul>
 *   <li><b>定义层</b>：随地图分发、可版本控制、可审核；</li>
 *   <li><b>世界层</b>（本类）：只记录"差异"——新增/覆盖、禁用、删除，随存档走。</li>
 * </ul>
 *
 * <p>只记差异的好处：地图重新分发时，服主本地的微调不会丢，也不会把整张图复制一份。</p>
 *
 * <p>内部一律用有序容器（{@link TreeMap} / {@link LinkedHashSet}），保证同一份覆盖层
 * 每次加载得到完全相同的顺序——"同 seed 同结果"的前提之一是遍历顺序确定。</p>
 *
 * @param patches  id → 锚点（新增或整体覆盖定义层的那一个）
 * @param disabled 被服主临时关掉的锚点 id
 * @param removed  被服主删掉的锚点 id（定义层里即使有也不再生成）
 */
public record RaidAnchorOverlay(Map<String, RaidAnchor> patches,
                                Set<String> disabled,
                                Set<String> removed) {

    public RaidAnchorOverlay {
        patches = patches == null ? Map.of() : new TreeMap<>(patches);
        disabled = disabled == null ? Set.of() : new LinkedHashSet<>(disabled);
        removed = removed == null ? Set.of() : new LinkedHashSet<>(removed);
    }

    public static RaidAnchorOverlay empty() {
        return new RaidAnchorOverlay(Map.of(), Set.of(), Set.of());
    }

    public boolean isEmpty() {
        return patches.isEmpty() && disabled.isEmpty() && removed.isEmpty();
    }

    /** 新增或整体覆盖一个锚点（命令 place 用）。 */
    public RaidAnchorOverlay withPatch(RaidAnchor anchor) {
        Map<String, RaidAnchor> next = new TreeMap<>(patches);
        next.put(anchor.id(), anchor);
        return new RaidAnchorOverlay(next, disabled, removed);
    }

    /** 删除一个锚点（命令 remove 用）：从 patches 里移除，并加入 removed 集合。 */
    public RaidAnchorOverlay withRemoved(String id) {
        Map<String, RaidAnchor> next = new TreeMap<>(patches);
        next.remove(id);
        Set<String> nextRemoved = new LinkedHashSet<>(removed);
        nextRemoved.add(id);
        Set<String> nextDisabled = new LinkedHashSet<>(disabled);
        nextDisabled.remove(id);
        return new RaidAnchorOverlay(next, nextDisabled, nextRemoved);
    }

    /** 临时开关（命令 enable / disable 用）。 */
    public RaidAnchorOverlay withDisabled(String id, boolean value) {
        Set<String> nextDisabled = new LinkedHashSet<>(disabled);
        if (value) {
            nextDisabled.add(id);
        } else {
            nextDisabled.remove(id);
        }
        return new RaidAnchorOverlay(patches, nextDisabled, removed);
    }

    public static RaidAnchorOverlay fromJson(JsonObject json, List<String> problems) {
        if (json == null) {
            return empty();
        }
        Map<String, RaidAnchor> patches = new TreeMap<>();
        JsonElement anchorsJson = json.get("anchors");
        if (anchorsJson != null && anchorsJson.isJsonArray()) {
            for (JsonElement element : anchorsJson.getAsJsonArray()) {
                if (element == null || !element.isJsonObject()) {
                    problems.add("覆盖层 anchors 里有非对象项，已忽略");
                    continue;
                }
                RaidAnchor.ParseResult result = RaidAnchor.fromJson(element.getAsJsonObject(),
                        RaidAnchor.Source.OVERLAY);
                if (result.ok()) {
                    patches.put(result.anchor().id(), result.anchor());
                } else {
                    problems.add("覆盖层锚点解析失败：" + String.join("；", result.problems()));
                }
            }
        }
        Set<String> disabled = readStringSet(json, "disabled", problems);
        Set<String> removed = readStringSet(json, "removed", problems);
        return new RaidAnchorOverlay(patches, disabled, removed);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        JsonArray anchors = new JsonArray();
        patches.values().forEach(anchor -> anchors.add(anchor.toJson()));
        json.add("anchors", anchors);

        JsonArray disabledArray = new JsonArray();
        disabled.forEach(disabledArray::add);
        json.add("disabled", disabledArray);

        JsonArray removedArray = new JsonArray();
        removed.forEach(removedArray::add);
        json.add("removed", removedArray);
        return json;
    }

    private static Set<String> readStringSet(JsonObject json, String key, List<String> problems) {
        Set<String> values = new LinkedHashSet<>();
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return values;
        }
        if (!element.isJsonArray()) {
            problems.add(key + " 必须是字符串数组，已忽略");
            return values;
        }
        for (JsonElement item : element.getAsJsonArray()) {
            if (item == null || !item.isJsonPrimitive()) {
                problems.add(key + " 里有非字符串项，已忽略");
                continue;
            }
            String value = item.getAsString().trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    /** 供调试命令打印。 */
    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        patches.values().forEach(anchor -> lines.add("覆盖/新增 " + anchor.describe()));
        disabled.forEach(id -> lines.add("已禁用 " + id));
        removed.forEach(id -> lines.add("已删除 " + id));
        return lines;
    }
}
