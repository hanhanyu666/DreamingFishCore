package com.hhy.dreamingfishcore.gameplay.clue_system;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 线索掉落的纯逻辑：从一个线索池里挑出该玩家还没有发现的线索。
 *
 * <p>这里不接触事件、世界或网络，只做集合与随机数处理，便于单元测试。
 * “同一残页全服共享内容、个人发现状态独立”的规则因此只落在一处。</p>
 *
 * <p>里程碑 2 收尾后线索池与玩家状态都用稳定 ID：池来自 {@link ClueCatalog}，
 * 玩家状态来自随记本的永久发现记录（不再是旧的整数编号集合）。</p>
 */
public final class ClueDropSelector {

    private ClueDropSelector() {
    }

    /**
     * 返回候选线索 ID：排除玩家已经发现的线索，并跳过内容缺失的条目。
     *
     * @param pool              当前生效的线索池
     * @param discoveredClueIds 该玩家随记本里已经发现的线索 ID
     */
    public static List<String> candidates(Collection<ClueDefinition> pool,
                                          Set<String> discoveredClueIds) {
        List<String> candidates = new ArrayList<>();
        if (pool == null || pool.isEmpty()) {
            return candidates;
        }

        for (ClueDefinition definition : pool) {
            if (definition == null || definition.id().isBlank()) {
                continue;
            }
            if (discoveredClueIds != null && discoveredClueIds.contains(definition.id())) {
                continue;
            }
            candidates.add(definition.id());
        }
        return candidates;
    }

    /**
     * 从候选列表里随机取一条；没有候选时返回 {@code null}，表示这次不掉落而不是掉落空内容。
     */
    public static String pick(List<String> candidates, RandomSource random) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (random == null) {
            return candidates.get(0);
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * 按百分比判定这次击杀是否掉落，支持小数概率（例如 {@code 0.01} 表示 0.01%）。
     * {@code percent <= 0} 表示不掉，{@code percent >= 100} 表示必掉。
     */
    public static boolean rollDrop(double percent, RandomSource random) {
        if (percent <= 0.0D) {
            return false;
        }
        if (percent >= 100.0D) {
            return true;
        }
        if (random == null) {
            return false;
        }
        return random.nextDouble() * 100.0D < percent;
    }
}
