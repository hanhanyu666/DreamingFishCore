package com.hhy.dreamingfishcore.gameplay.archive_system;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 资料库的纯逻辑：一条配方收不收、哪些是新增、哪些还没学过。
 *
 * <p>刻意不碰 {@code Player} / {@code Level} / 任何注册表——「去重规则」与「待学习集合」
 * 这类判定抽成纯函数就能直接单测，这也是项目里既有的做法（数值与判定与游戏状态解耦）。</p>
 */
public final class ArchiveRules {

    /** 单个资料库最多记多少条。池子再大也不会超过需要蓝图的物品总数，这条只是防存档被撑爆。 */
    public static final int MAX_RECIPES_PER_LIBRARY = 4096;

    private ArchiveRules() {
    }

    /**
     * 规范化一批配方 ID：去掉首尾空白、丢掉空串与不像资源位置的串、按原顺序去重。
     *
     * <p>为什么要校验格式：内容会直接写进存档，坏数据一旦落盘，下次读档就要靠 normalize 兜底；
     * 在这里挡掉比在读取侧兜底便宜。</p>
     */
    public static List<String> normalize(Collection<String> ids) {
        List<String> out = new ArrayList<>();
        if (ids == null) {
            return out;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : ids) {
            if (raw == null) {
                continue;
            }
            String id = raw.trim();
            // 只收 "命名空间:路径" 这种形状，且两端都不为空。
            int colon = id.indexOf(':');
            if (colon <= 0 || colon == id.length() - 1) {
                continue;
            }
            if (seen.add(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** {@code incoming} 里 {@code existing} 还没有的那些（保持 incoming 的顺序）。 */
    public static List<String> newIds(Collection<String> existing, Collection<String> incoming) {
        Set<String> have = new LinkedHashSet<>(normalize(existing));
        List<String> out = new ArrayList<>();
        for (String id : normalize(incoming)) {
            if (!have.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** 库里 {@code stored} 中玩家还没学会的那些（保持 stored 的顺序）。 */
    public static List<String> unlearned(Collection<String> stored, Collection<String> alreadyLearned) {
        Set<String> known = new LinkedHashSet<>(normalize(alreadyLearned));
        List<String> out = new ArrayList<>();
        for (String id : normalize(stored)) {
            if (!known.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** 合并之后是否会超过单个资料库的容量上限。 */
    public static boolean exceedsCapacity(Collection<String> existing, Collection<String> incoming) {
        long merged = (long) normalize(existing).size() + newIds(existing, incoming).size();
        return merged > MAX_RECIPES_PER_LIBRARY;
    }
}
