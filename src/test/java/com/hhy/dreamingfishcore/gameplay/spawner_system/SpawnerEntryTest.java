package com.hhy.dreamingfishcore.gameplay.spawner_system;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 刷怪箱数据层的纯逻辑：默认值、越界夹取、坏数据修复、批次调度、奖励记账。
 *
 * <p>涉及世界与实体的部分只能靠 gametest / 实机；这里锁的是配置语义与调度规则 ——
 * 它们最容易在后续改动里被悄悄改坏，而且都会直接改变玩家看到的刷怪节奏。</p>
 */
class SpawnerEntryTest {

    // ==================== 默认值与夹取 ====================

    @Test
    void freshSpawnerUsesDocumentedDefaults() {
        SpawnerEntry entry = new SpawnerEntry("minecraft:overworld", 10, 64, 20);

        assertEquals("minecraft:overworld|10|64|20", entry.key());
        assertEquals(SpawnerEntry.DEFAULT_ENTITY_ID, entry.entityId());
        assertEquals(32, entry.detectionRadius());
        assertEquals(8, entry.spawnRadius());
        assertEquals(3, entry.spawnCount());
        assertEquals(600, entry.cooldownTicks());
        assertEquals(3, entry.batches());
        assertFalse(entry.fixedClueEnabled(), "固定线索默认关闭");
        assertFalse(entry.redstoneControlled(), "红石控制默认关闭");
        assertFalse(entry.selfDestructWhenCleared(), "自毁默认关闭");
        assertEquals(0, entry.batchesSpawned());
        assertEquals(-1L, entry.nextBatchAtTick(), "还没开始计时");
        assertFalse(entry.completed());
    }

    @Test
    void settersClampOutOfRangeValuesAtBothEnds() {
        SpawnerEntry entry = new SpawnerEntry("minecraft:overworld", 0, 0, 0);

        entry.setDetectionRadius(9_999);
        assertEquals(SpawnerEntry.DETECTION_MAX, entry.detectionRadius());
        entry.setDetectionRadius(0);
        assertEquals(SpawnerEntry.DETECTION_MIN, entry.detectionRadius());

        entry.setSpawnRadius(0);
        assertEquals(SpawnerEntry.SPAWN_RADIUS_MIN, entry.spawnRadius());
        entry.setSpawnRadius(999);
        assertEquals(SpawnerEntry.SPAWN_RADIUS_MAX, entry.spawnRadius());

        entry.setSpawnCount(0);
        assertEquals(SpawnerEntry.SPAWN_COUNT_MIN, entry.spawnCount());
        entry.setSpawnCount(999);
        assertEquals(SpawnerEntry.SPAWN_COUNT_MAX, entry.spawnCount());

        entry.setCooldownTicks(1);
        assertEquals(SpawnerEntry.COOLDOWN_MIN, entry.cooldownTicks());
        entry.setCooldownTicks(999_999);
        assertEquals(SpawnerEntry.COOLDOWN_MAX, entry.cooldownTicks());

        entry.setBatches(0);
        assertEquals(SpawnerEntry.BATCHES_MIN, entry.batches());
        entry.setBatches(9_999);
        assertEquals(SpawnerEntry.BATCHES_MAX, entry.batches());

        entry.setRewardExperience(-5);
        assertEquals(0, entry.rewardExperience());
        entry.setRewardCoins(-5);
        assertEquals(0, entry.rewardCoins());
        entry.setClueId(-3);
        assertEquals(0, entry.clueId(), "线索编号 0 表示未指定");

        // 实体 id 留空必须退回默认值，否则运行时解析不出实体类型。
        entry.setEntityId("   ");
        assertEquals(SpawnerEntry.DEFAULT_ENTITY_ID, entry.entityId());
    }

    // ==================== 坏数据修复 ====================

    @Test
    void normalizeRepairsBrokenJsonEntriesAndIsIdempotent() {
        // 用手改坏的 JSON 构造条目：Gson 会绕过 setter，这正是读档时的情况。
        SpawnerEntry broken = new Gson().fromJson("""
                {"dimensionId":"minecraft:overworld","x":1,"y":2,"z":3,
                 "entityId":"  ","detectionRadius":9999,"spawnRadius":0,"spawnCount":-4,
                 "cooldownTicks":1,"batches":0,"clueId":-7,"rewardExperience":-5,"rewardCoins":-1,
                 "batchesSpawned":9,
                 "rewardItems":[{"itemId":"","count":5},{"itemId":"minecraft:apple","count":0}]}
                """, SpawnerEntry.class);

        assertTrue(broken.normalize(), "坏数据应报告发生了修复");
        assertEquals(SpawnerEntry.DEFAULT_ENTITY_ID, broken.entityId());
        assertEquals(SpawnerEntry.DETECTION_MAX, broken.detectionRadius());
        assertEquals(SpawnerEntry.SPAWN_RADIUS_MIN, broken.spawnRadius());
        assertEquals(SpawnerEntry.SPAWN_COUNT_MIN, broken.spawnCount());
        assertEquals(SpawnerEntry.COOLDOWN_MIN, broken.cooldownTicks());
        assertEquals(SpawnerEntry.BATCHES_MIN, broken.batches());
        assertEquals(0, broken.clueId());
        assertEquals(0, broken.rewardExperience());
        assertEquals(0, broken.rewardCoins());
        assertEquals(SpawnerEntry.BATCHES_MIN, broken.batchesSpawned(),
                "已刷批次不能超过总批次，否则永远无法结算");
        assertEquals(1, broken.rewardItems().size(), "空 id 的奖励条目应被丢掉");
        assertEquals("minecraft:apple", broken.rewardItems().get(0).itemId());
        assertEquals(1, broken.rewardItems().get(0).count(), "数量下限为 1");
        assertFalse(broken.normalize(), "修复之后应幂等");
    }

    @Test
    void normalizeKeepsSpawnedBatchCountWithinTheConfiguredBatchTotal() {
        SpawnerEntry entry = new SpawnerEntry("minecraft:overworld", 0, 0, 0);
        entry.setBatches(2);
        entry.markBatchSpawned();
        entry.markBatchSpawned();
        assertEquals(2, entry.batchesSpawned());

        // 服主把批次从 2 改回 1：已经刷出去的记录必须跟着夹住，否则永远无法结算。
        entry.setBatches(1);
        assertEquals(1, entry.batchesSpawned());
        assertTrue(SpawnerEntry.isCleared(entry.batchesSpawned(), entry.batches(), 0));
    }

    // ==================== 批次调度 ====================

    @Test
    void firstBatchSpawnsImmediatelyAndLaterBatchesWaitForClearThenCooldown() {
        // 全新的一台：只要还没刷过就立刻刷，不管 CD（nextBatchAtTick 还是 -1）。
        assertTrue(SpawnerEntry.nextBatchDue(0, 3, false, -1L, 0L));
        assertTrue(SpawnerEntry.nextBatchDue(0, 3, true, -1L, 0L));

        // 上一轮刚结算完（批次归零 + 已排轮次 CD）：要等 CD 到，否则会无缝接下一轮。
        assertFalse(SpawnerEntry.nextBatchDue(0, 3, true, 5_000L, 4_999L));
        assertTrue(SpawnerEntry.nextBatchDue(0, 3, true, 5_000L, 5_000L));

        // 第二批：场上还有怪就不刷（CD 从"上一批全死"开始算）。
        assertFalse(SpawnerEntry.nextBatchDue(1, 3, false, -1L, 10_000L));
        // 刚清空、还没定 CD 起点时也不刷。
        assertFalse(SpawnerEntry.nextBatchDue(1, 3, true, -1L, 10_000L));
        // CD 没到不刷，到了才刷。
        assertFalse(SpawnerEntry.nextBatchDue(1, 3, true, 1_000L, 999L));
        assertTrue(SpawnerEntry.nextBatchDue(1, 3, true, 1_000L, 1_000L));

        // 批次刷满之后永远不再刷。
        assertFalse(SpawnerEntry.nextBatchDue(3, 3, true, 0L, 99_999L));
    }

    @Test
    void cooldownAnchorIsSetOnlyOncePerClearedBatch() {
        // 上一批刚被打光：需要定 CD 起点。
        assertTrue(SpawnerEntry.needsCooldownAnchor(1, 3, true, -1L));
        // 已经定过了就不要重复推后。
        assertFalse(SpawnerEntry.needsCooldownAnchor(1, 3, true, 500L));
        // 场上还有怪 / 还没刷过 / 批次已满：都不需要。
        assertFalse(SpawnerEntry.needsCooldownAnchor(1, 3, false, -1L));
        assertFalse(SpawnerEntry.needsCooldownAnchor(0, 3, true, -1L));
        assertFalse(SpawnerEntry.needsCooldownAnchor(3, 3, true, -1L));
    }

    @Test
    void clearedRequiresEveryBatchSpawnedAndNoSurvivors() {
        assertFalse(SpawnerEntry.isCleared(2, 3, 0), "还有批次没刷完不算剿灭");
        assertFalse(SpawnerEntry.isCleared(3, 3, 1), "还有怪活着不算剿灭");
        assertTrue(SpawnerEntry.isCleared(3, 3, 0));
    }

    // ==================== 奖励记账 ====================

    @Test
    void eachPlayerIsRewardedOncePerSpawnerEvenAcrossRounds() {
        SpawnerEntry entry = new SpawnerEntry("minecraft:overworld", 0, 0, 0);
        UUID player = UUID.randomUUID();

        assertFalse(entry.hasRewarded(player));
        entry.markRewarded(player);
        assertTrue(entry.hasRewarded(player));
        entry.markRewarded(player);
        assertEquals(1, entry.rewardedPlayerIds().size(), "重复记账只保留一条");

        // 重置一轮时保留已领名单：否则同一台箱子可以被反复刷奖励。
        entry.markBatchSpawned();
        entry.markCompleted();
        entry.resetRound(1_000L);
        assertTrue(entry.hasRewarded(player), "重置一轮不应清空已领名单");
        assertEquals(0, entry.batchesSpawned());
        assertFalse(entry.completed());
        assertTrue(entry.aliveEntityIds().isEmpty());
        // 轮次之间仍要等 CD：重置后立刻应该"还没到点"。
        assertFalse(SpawnerEntry.nextBatchDue(0, entry.batches(), true,
                entry.nextBatchAtTick(), 1_000L));
    }

    @Test
    void spawnTrackingAppendsAndResetsWithTheRound() {
        SpawnerEntry entry = new SpawnerEntry("minecraft:overworld", 0, 0, 0);
        UUID spawned = UUID.randomUUID();
        entry.trackSpawned(spawned);
        entry.trackSpawned(null);
        assertEquals(1, entry.aliveEntityIds().size(), "null 不该被记进去");
        assertEquals(spawned.toString(), entry.aliveEntityIds().get(0));

        entry.resetRound(0L);
        assertTrue(entry.aliveEntityIds().isEmpty());
    }

    @Test
    void entriesWithoutDimensionAreRejectedOnLoad() {
        SpawnerEntry entry = new SpawnerEntry();
        assertFalse(entry.valid(), "没有维度的记录无法回到世界，加载时应跳过");
        assertTrue(new SpawnerEntry("minecraft:the_nether", 0, 0, 0).valid());
        assertFalse(new SpawnerEntry("   ", 0, 0, 0).valid(), "空白维度同样无效");
        // 合法条目不应被报告为"需要修复"。
        assertFalse(new SpawnerEntry("minecraft:overworld", 1, 2, 3).normalize());
    }
}
