package com.hhy.dreamingfishcore.gameplay.raid_system.map;

import com.hhy.dreamingfishcore.gameplay.raid_system.RaidRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 部分随机地图：变体选择与连通性校验（设计稿 §6）。
 *
 * <p>设计稿的核心是"**不要实时扫描几百万方块**"，而是维护一张**简化的区域连通图**，
 * 地图变化控制的只是"边是否开放"。所以这一层完全是图论与规则，不碰世界，可以充分单测。</p>
 *
 * <p>三个要点：</p>
 * <ol>
 *   <li><b>每组选一个状态</b>（也可以选多个），按权重抽，同种子同结果；</li>
 *   <li><b>随机完必须校验</b>：每个出生区至少能到一个撤离区、不能把区域完全封死、
 *       指定"必须可达"的高价值区仍可达；不合格就重新抽（有次数上限），
 *       到上限仍不行就**退回无变体**并在结果里说明（设计稿的 applyFallbackVariants）；</li>
 *   <li><b>边名规范</b>：{@code "区域A-区域B"} 形式的字符串，**区域名里不要用连字符**；
 *       内部统一成排序后的 {@code "A|B"} 键，保证顺序无关。</li>
 * </ol>
 */
public final class RaidMapVariants {

    /** 一个局部状态：它开放/关闭哪些边。 */
    public record Variant(String id, int weight, Set<String> disabledEdges, Set<String> enabledEdges) {

        public Variant {
            id = id == null ? "" : id;
            disabledEdges = normalize(disabledEdges);
            enabledEdges = normalize(enabledEdges);
        }

        public static Variant of(String id, int weight) {
            return new Variant(id, weight, Set.of(), Set.of());
        }
    }

    /**
     * 一个变体组：{@code choose} 通常为 1。
     *
     * @param id       组名（例如 factory_north_entrance）
     * @param choose   本组选几个（≤ 0 视为 1）
     * @param variants 候选状态
     */
    public record Group(String id, int choose, List<Variant> variants) {

        public Group {
            id = id == null ? "" : id;
            choose = choose <= 0 ? 1 : choose;
            List<Variant> ordered = new ArrayList<>(variants == null ? List.of() : variants);
            ordered.sort(Comparator.comparing(Variant::id));
            variants = List.copyOf(ordered);
        }
    }

    /** 简化连通图：节点是区域，边是通道。 */
    public record Graph(Map<String, Set<String>> adjacency) {

        public Graph {
            Map<String, Set<String>> copy = new TreeMap<>();
            if (adjacency != null) {
                adjacency.forEach((node, neighbors) ->
                        copy.put(node, Set.copyOf(new TreeSet<>(neighbors == null ? Set.of() : neighbors))));
            }
            adjacency = Map.copyOf(copy);
        }

        /** 用 {@code "A-B"} 形式的边构造（区域名里不要用连字符）。 */
        public static Graph of(String... edges) {
            Map<String, Set<String>> adjacency = new TreeMap<>();
            for (String edge : edges == null ? new String[0] : edges) {
                String[] parts = split(edge);
                if (parts == null) {
                    continue;
                }
                adjacency.computeIfAbsent(parts[0], key -> new TreeSet<>()).add(parts[1]);
                adjacency.computeIfAbsent(parts[1], key -> new TreeSet<>()).add(parts[0]);
            }
            return new Graph(adjacency);
        }

        public Set<String> nodes() {
            return adjacency.keySet();
        }

        public boolean hasAnyEdge(String node) {
            Set<String> neighbors = adjacency.get(node);
            return neighbors != null && !neighbors.isEmpty();
        }

        /**
         * 当前开放的邻居。
         *
         * <p>关键：{@code disabledEdges} 存的是**边键**（{@code "A|B"}），不是邻居名，
         * 所以这里必须先把 {@code (自己, 邻居)} 拼成同一个键再比较——
         * 直接拿键去和邻居名比是匹配不上的（这个 bug 一开始就踩了，被测试抓住）。</p>
         *
         * <p>变体还能**开启基础图上没有的通道**（{@code enabledEdges}）；两者冲突时以禁用为准（更保守）。</p>
         */
        public Set<String> openNeighbors(String node, Selection selection) {
            Set<String> open = new TreeSet<>();
            for (String neighbor : adjacency.getOrDefault(node, Set.of())) {
                if (!selection.disabledEdges().contains(keyOf(node, neighbor))) {
                    open.add(neighbor);
                }
            }
            for (String key : selection.enabledEdges()) {
                String other = otherEnd(key, node);
                if (other != null) {
                    open.add(other);
                }
            }
            for (String key : selection.disabledEdges()) {
                String other = otherEnd(key, node);
                if (other != null) {
                    open.remove(other);
                }
            }
            return open;
        }

        /** 两个区域之间的边键（与 {@link #edgeKey} 的归一结果一致）。 */
        static String keyOf(String left, String right) {
            return left.compareTo(right) <= 0 ? left + "|" + right : right + "|" + left;
        }

        /** 边键里"另一端"的区域名；该键与此区域无关时返回 null。 */
        static String otherEnd(String key, String node) {
            if (key == null || node == null) {
                return null;
            }
            int index = key.indexOf('|');
            if (index <= 0 || index >= key.length() - 1) {
                return null;
            }
            String left = key.substring(0, index);
            String right = key.substring(index + 1);
            if (left.equals(node)) {
                return right;
            }
            return right.equals(node) ? left : null;
        }

        /** 从 {@code from} 出发能否走到 {@code targets} 里的任意一个（广度优先，纯图论）。 */
        public boolean canReach(String from, Set<String> targets, Selection selection) {
            if (from == null || targets == null || targets.isEmpty()) {
                return false;
            }
            if (targets.contains(from)) {
                return true;
            }
            Set<String> visited = new LinkedHashSet<>();
            List<String> queue = new ArrayList<>();
            queue.add(from);
            visited.add(from);
            while (!queue.isEmpty()) {
                String node = queue.remove(0);
                for (String neighbor : openNeighbors(node, selection)) {
                    if (!visited.add(neighbor)) {
                        continue;
                    }
                    if (targets.contains(neighbor)) {
                        return true;
                    }
                    queue.add(neighbor);
                }
            }
            return false;
        }
    }

    /** 本局选中的变体与它对边的影响。 */
    public record Selection(Map<String, String> chosenByGroup, Set<String> disabledEdges,
                            Set<String> enabledEdges, List<String> notes) {

        public Selection {
            chosenByGroup = Map.copyOf(new TreeMap<>(chosenByGroup == null ? Map.of() : chosenByGroup));
            disabledEdges = normalize(disabledEdges);
            enabledEdges = normalize(enabledEdges);
            notes = notes == null ? List.of() : List.copyOf(notes);
        }

        public static Selection empty() {
            return new Selection(Map.of(), Set.of(), Set.of(), List.of());
        }

        public boolean isEmpty() {
            return chosenByGroup.isEmpty();
        }
    }

    /** 校验结果。 */
    public record Validation(boolean valid, List<String> problems) {

        public Validation {
            problems = problems == null ? List.of() : List.copyOf(problems);
        }

        public static Validation ok() {
            return new Validation(true, List.of());
        }
    }

    /** 选择 + 校验的最终结果。 */
    public record Outcome(Selection selection, Validation validation, int attempts, boolean usedFallback) {
    }

    private RaidMapVariants() {
    }

    /** 按权重为每组抽一个（或 choose 个）状态，顺序确定。 */
    public static Selection select(List<Group> groups, RaidRandom random) {
        Map<String, String> chosen = new TreeMap<>();
        Set<String> disabled = new TreeSet<>();
        Set<String> enabled = new TreeSet<>();
        List<String> notes = new ArrayList<>();
        if (groups == null || groups.isEmpty()) {
            return Selection.empty();
        }
        List<Group> ordered = new ArrayList<>(groups);
        ordered.sort(Comparator.comparing(Group::id));

        for (Group group : ordered) {
            List<Variant> pool = new ArrayList<>();
            for (Variant variant : group.variants()) {
                if (variant.weight() > 0) {
                    pool.add(variant);
                }
            }
            int want = Math.min(group.choose(), pool.size());
            for (int index = 0; index < want; index++) {
                var pick = random.pickWeighted(pool, Variant::weight);
                if (pick.isEmpty()) {
                    break;
                }
                Variant variant = pick.get();
                pool.remove(variant);
                chosen.put(group.id(), variant.id());
                disabled.addAll(variant.disabledEdges());
                enabled.addAll(variant.enabledEdges());
                notes.add("已选 " + group.id() + " → " + variant.id());
            }
            if (pool.isEmpty() && want == 0) {
                notes.add(group.id() + " 没有可用状态（权重都 ≤ 0 或列表为空）");
            }
        }
        return new Selection(chosen, disabled, enabled, notes);
    }

    /**
     * 随机完地图状态后必须做的校验（设计稿 §6.5）。
     *
     * @param spawnNodes        出生区
     * @param extractionNodes   撤离区
     * @param mustStayReachable 必须保持可达的高价值区（可为空）
     */
    public static Validation validate(Graph graph, Selection selection, Set<String> spawnNodes,
                                      Set<String> extractionNodes, Set<String> mustStayReachable) {
        List<String> problems = new ArrayList<>();
        if (graph == null) {
            return new Validation(false, List.of("没有连通图"));
        }
        Selection effective = selection == null ? Selection.empty() : selection;

        Set<String> spawns = spawnNodes == null ? Set.of() : spawnNodes;
        Set<String> extractions = extractionNodes == null ? Set.of() : extractionNodes;

        if (spawns.isEmpty() || extractions.isEmpty()) {
            problems.add("NO_SPAWN_OR_EXTRACTION：没配出生区或撤离区，无法校验连通性");
        }
        for (String spawn : new TreeSet<>(spawns)) {
            if (!graph.canReach(spawn, extractions, effective)) {
                problems.add("SPAWN_NO_ROUTE：" + spawn + " 到不了任何撤离点");
            }
        }
        for (String node : new TreeSet<>(graph.nodes())) {
            if (graph.hasAnyEdge(node) && graph.openNeighbors(node, effective).isEmpty()) {
                problems.add("ISOLATED_ZONE：" + node + " 被完全封死（一条通道都不剩）");
            }
        }
        for (String node : new TreeSet<>(mustStayReachable == null ? Set.of() : mustStayReachable)) {
            boolean reachable = false;
            for (String spawn : spawns) {
                if (graph.canReach(spawn, Set.of(node), effective)) {
                    reachable = true;
                    break;
                }
            }
            if (!reachable) {
                problems.add("SEALED_OFF：" + node + " 从任何出生区都到不了");
            }
        }
        return new Validation(problems.isEmpty(), problems);
    }

    /**
     * 抽到合格为止（有次数上限），上限内都不合格就退回无变体。
     *
     * <p>退回无变体是设计稿的 {@code applyFallbackVariants}：**宁可这局地图没有任何变化，
     * 也不能给玩家一张走不通的图**。</p>
     */
    public static Outcome selectValid(List<Group> groups, Graph graph, Set<String> spawnNodes,
                                      Set<String> extractionNodes, Set<String> mustStayReachable,
                                      RaidRandom random, int maxAttempts) {
        int attempts = Math.max(1, maxAttempts);
        for (int index = 0; index < attempts; index++) {
            Selection selection = select(groups, random);
            Validation validation = validate(graph, selection, spawnNodes, extractionNodes, mustStayReachable);
            if (validation.valid()) {
                return new Outcome(selection, validation, index + 1, false);
            }
        }
        // 退回无变体：基础图应当是通的（不通说明图本身配错了，如实报告）
        Selection fallback = Selection.empty();
        Validation validation = validate(graph, fallback, spawnNodes, extractionNodes, mustStayReachable);
        List<String> notes = new ArrayList<>(fallback.notes());
        notes.add("抽了 " + attempts + " 次都不合格，已退回无变体");
        return new Outcome(new Selection(fallback.chosenByGroup(), fallback.disabledEdges(),
                fallback.enabledEdges(), notes), validation, attempts, true);
    }

    /** 供命令与日志打印。 */
    public static List<String> describe(Outcome outcome) {
        List<String> lines = new ArrayList<>();
        lines.add("地图变体：选了 " + outcome.selection().chosenByGroup().size() + " 组"
                + "（尝试 " + outcome.attempts() + " 次"
                + (outcome.usedFallback() ? "，已退回无变体" : "") + "）"
                + "，校验" + (outcome.validation().valid() ? "通过" : "不通过"));
        outcome.selection().chosenByGroup().forEach((group, variant) ->
                lines.add("  " + group + " → " + variant));
        if (!outcome.selection().disabledEdges().isEmpty()) {
            lines.add("  关闭的通道：" + outcome.selection().disabledEdges());
        }
        outcome.validation().problems().forEach(problem -> lines.add("  问题 " + problem));
        outcome.selection().notes().forEach(note -> lines.add("  " + note));
        return lines;
    }

    // ---------------------------------------------------------------- 工具

    /** 把 {@code "A-B"} 归一成排序后的 {@code "A|B"} 键；格式不对返回 null。 */
    static String edgeKey(String edge) {
        String[] parts = split(edge);
        if (parts == null) {
            return null;
        }
        return parts[0].compareTo(parts[1]) <= 0 ? parts[0] + "|" + parts[1] : parts[1] + "|" + parts[0];
    }

    private static String[] split(String edge) {
        if (edge == null) {
            return null;
        }
        int index = edge.indexOf('-');
        if (index <= 0 || index >= edge.length() - 1) {
            return null;
        }
        String left = edge.substring(0, index).trim();
        String right = edge.substring(index + 1).trim();
        if (left.isEmpty() || right.isEmpty()) {
            return null;
        }
        return new String[]{left, right};
    }

    /**
     * 边集合归一化。
     *
     * <p>必须**同时接受两种写法**：{@code "A-B"}（人写的）与 {@code "A|B"}（已经归一过的键）。
     * 一开始只处理前一种，于是 {@code Selection} 对 {@code Variant} 已经归一好的键再归一一次时，
     * 按"第一个连字符"去拆 {@code "factory|road"} 找不到连字符 → 返回 null → **合法键被丢掉、
     * 关边失效**。这个 bug 被连通性测试抓出来。</p>
     */
    private static Set<String> normalize(Set<String> edges) {
        if (edges == null || edges.isEmpty()) {
            return Set.of();
        }
        Set<String> keys = new TreeSet<>();
        for (String edge : edges) {
            if (edge == null) {
                continue;
            }
            String trimmed = edge.trim();
            String key = trimmed.indexOf('|') >= 0 ? trimmed : edgeKey(trimmed);
            if (key == null) {
                continue;
            }
            int index = key.indexOf('|');
            if (index > 0 && index < key.length() - 1) {
                keys.add(key);
            }
        }
        return Set.copyOf(keys);
    }
}
