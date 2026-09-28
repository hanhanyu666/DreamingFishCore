package com.hhy.dreamingfishcore.gameplay.task_location_system;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 把“剧情地点”按**角色**解析，而不是死认一个 ID。
 *
 * <p>剧情代码原本直接比较固定 ID（例如 {@code dreamingfishcore:location_d41fd2b0…}）。问题是服主用
 * {@code /dreamingfish task_location select <名称>} 建出来的地点 ID 是随机生成的，于是同一个地方
 * “建了也不认”——能对话、不推进、还不报错。现在改为两种都算命中：</p>
 *
 * <ol>
 *   <li>地点 ID 与角色的固定 ID **精确相等**（兼容已经按 ID 建好地点的服务器与存档）；</li>
 *   <li>地点**显示名称包含**角色的任一关键词（服主按习惯命名即可）。</li>
 * </ol>
 *
 * <p>关键词是宽匹配，同一角色可能命中多个地点，因此 {@link #find(Role)} 的优先级是：固定 ID 命中 →
 * 启用中的 → 名称与关键词完全相等的 → 按 ID 排序取第一个，保证结果稳定可预测。</p>
 */
public final class StoryLocationResolver {

    /** 剧情地点的角色。固定 ID 是历史写法，保留用于兼容。 */
    public enum Role {
        /** 阿拜多斯：开局安置、与白芷见面的区域。 */
        ABYDOS("dreamingfishcore:location_d105866ccdc84c4da7b017a7f13ec7d3", List.of("阿拜多斯")),
        /** 逐光会区域；它在余梦期同时承担医疗接待点。 */
        ZHUIGUANG("dreamingfishcore:location_d41fd2b0cc77479c9e2017ae727fd117",
                List.of("逐光会", "医疗接待"));

        private final String fixedId;
        private final List<String> nameKeywords;

        Role(String fixedId, List<String> nameKeywords) {
            this.fixedId = fixedId;
            this.nameKeywords = List.copyOf(nameKeywords);
        }

        /** 历史上写死在代码里的地点 ID，现在只作为兜底与兼容值。 */
        public String fixedId() {
            return fixedId;
        }

        /** 名称里出现任意一个即视为该角色。 */
        public List<String> nameKeywords() {
            return nameKeywords;
        }
    }

    private StoryLocationResolver() {
    }

    /** 该地点是否属于这个角色：固定 ID 命中，或名称包含任一关键词。 */
    public static boolean matches(Role role, TaskLocationDefinition location) {
        if (role == null || location == null) {
            return false;
        }
        if (role.fixedId().equals(location.getId())) {
            return true;
        }
        String name = normalize(location.getName());
        return !name.isEmpty() && role.nameKeywords().stream()
                .anyMatch(keyword -> name.contains(normalize(keyword)));
    }

    /** 按地点 ID 判定；ID 不存在、地点系统尚未加载时返回 false，不会抛异常。 */
    public static boolean matchesId(Role role, String locationId) {
        if (role == null || locationId == null || locationId.isBlank()) {
            return false;
        }
        if (role.fixedId().equals(locationId.trim())) {
            return true;
        }
        return findById(locationId).map(location -> matches(role, location)).orElse(false);
    }

    /** 解析该角色当前实际使用的地点：先按固定 ID，再按名称关键词。 */
    public static Optional<TaskLocationDefinition> find(Role role) {
        if (role == null) {
            return Optional.empty();
        }
        Optional<TaskLocationDefinition> byId = findById(role.fixedId());
        if (byId.isPresent()) {
            return byId;
        }
        try {
            return TaskLocationManager.getAllLocations().stream()
                    .filter(location -> matches(role, location))
                    .sorted(Comparator
                            .comparing((TaskLocationDefinition location) -> !location.isEnabled())
                            .thenComparing(location -> !isExactNameMatch(role, location))
                            .thenComparing(TaskLocationDefinition::getId))
                    .findFirst();
        } catch (RuntimeException | LinkageError exception) {
            // 地点系统不可用（未加载、配置非法、类初始化失败）时按“没有地点”处理。
            return Optional.empty();
        }
    }

    /**
     * 该角色应当写进任务/引导引用的地点 ID：优先实际存在的地点，找不到时退回固定 ID
     * （与旧存档、旧配置保持一致，不会把引用变成空字符串）。
     */
    public static String referenceId(Role role) {
        if (role == null) {
            return "";
        }
        return find(role).map(TaskLocationDefinition::getId).orElse(role.fixedId());
    }

    private static Optional<TaskLocationDefinition> findById(String locationId) {
        try {
            return TaskLocationManager.getLocation(locationId);
        } catch (RuntimeException | LinkageError exception) {
            // 判定类调用只应返回 false，不能因为地点系统不可用而打断剧情线程；
            // LinkageError 覆盖 FMLPaths 未初始化导致的类初始化失败（例如单测环境）。
            return Optional.empty();
        }
    }

    private static boolean isExactNameMatch(Role role, TaskLocationDefinition location) {
        String name = normalize(location.getName());
        return !name.isEmpty() && role.nameKeywords().stream()
                .anyMatch(keyword -> name.equals(normalize(keyword)));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
