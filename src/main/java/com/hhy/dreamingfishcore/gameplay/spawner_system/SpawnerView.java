package com.hhy.dreamingfishcore.gameplay.spawner_system;

import java.util.ArrayList;
import java.util.List;

/**
 * 刷怪箱传给客户端的只读视图。
 *
 * <p>权限开关（能否编辑）与运行状态（是否在尸潮区域内、是否工作中）都由服务端算好再下发，
 * 客户端只负责按开关画控件 —— 与组织系统的做法一致，避免"界面能点、服务端拒绝"。</p>
 */
public record SpawnerView(String dimensionId, int x, int y, int z,
                          String entityId,
                          int detectionRadius, int spawnRadius, int spawnCount,
                          int cooldownTicks, int batches, int batchesSpawned, int aliveCount,
                          boolean redstoneControlled, boolean selfDestructWhenCleared,
                          boolean fixedClueEnabled, int clueId,
                          int rewardExperience, int rewardCoins,
                          List<RewardLine> rewardItems,
                          boolean canEdit, boolean inHordeArea, boolean active,
                          String areaHint) {

    /** 一条奖励物品。 */
    public record RewardLine(String itemId, int count) {
    }

    /** 从登记表条目装配一份视图。 */
    public static SpawnerView of(SpawnerEntry entry, boolean canEdit,
                                 boolean inHordeArea, boolean active, String areaHint) {
        List<RewardLine> items = new ArrayList<>();
        for (SpawnerEntry.RewardEntry reward : entry.rewardItems()) {
            items.add(new RewardLine(reward.itemId(), reward.count()));
        }
        return new SpawnerView(
                entry.dimensionId(), entry.x(), entry.y(), entry.z(), entry.entityId(),
                entry.detectionRadius(), entry.spawnRadius(), entry.spawnCount(),
                entry.cooldownTicks(), entry.batches(), entry.batchesSpawned(),
                entry.aliveEntityIds().size(),
                entry.redstoneControlled(), entry.selfDestructWhenCleared(),
                entry.fixedClueEnabled(), entry.clueId(),
                entry.rewardExperience(), entry.rewardCoins(), List.copyOf(items),
                canEdit, inHordeArea, active, areaHint == null ? "" : areaHint);
    }

    /** 冷却秒数，供界面显示（默认 CD 是 tick 数）。 */
    public int cooldownSeconds() {
        return Math.max(1, Math.round(cooldownTicks / 20.0F));
    }
}
