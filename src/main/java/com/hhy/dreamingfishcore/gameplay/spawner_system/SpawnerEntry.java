package com.hhy.dreamingfishcore.gameplay.spawner_system;

import com.hhy.dreamingfishcore.DreamingFishCore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一台刷怪箱的全部数据：服主配置 + 运行状态。
 *
 * <p>为什么不用方块实体：与聚居地过滤装置同一个理由（见 {@code SettlementFilterRegistry} 的类注释）——
 * 服务端要能一次性遍历全部刷怪箱做周期判定，而区块里的方块实体只能靠扫描已加载区块枚举。
 * 这里的世界存档登记表是唯一真相，方块本身只保存"外观"这类纯展示状态。</p>
 *
 * <p>配置字段全部有上下限，读写两侧都走 {@link #normalize()}：客户端提交的任何值都当作不可信输入。</p>
 */
public final class SpawnerEntry {

    // ==================== 配置上下限（UI 与命令共用） ====================

    /** 检测范围：判定"附近有没有合格玩家"的水平半径。 */
    public static final int DETECTION_MIN = 4;
    public static final int DETECTION_MAX = 128;
    public static final int DETECTION_DEFAULT = 32;

    /** 刷怪范围：以刷怪箱为中心随机落点的水平半径。 */
    public static final int SPAWN_RADIUS_MIN = 1;
    public static final int SPAWN_RADIUS_MAX = 64;
    public static final int SPAWN_RADIUS_DEFAULT = 8;

    /** 每批刷怪量。 */
    public static final int SPAWN_COUNT_MIN = 1;
    public static final int SPAWN_COUNT_MAX = 32;
    public static final int SPAWN_COUNT_DEFAULT = 3;

    /** 每批之间的冷却（从上一批全部死亡开始计时）。 */
    public static final int COOLDOWN_MIN = 20;
    public static final int COOLDOWN_MAX = 24_000;
    public static final int COOLDOWN_DEFAULT = 600;

    /** 批次总数。 */
    public static final int BATCHES_MIN = 1;
    public static final int BATCHES_MAX = 64;
    public static final int BATCHES_DEFAULT = 3;

    /** 单条奖励物品的数量上限。 */
    public static final int REWARD_ITEM_COUNT_MAX = 64;
    /** 奖励条目数量上限，避免一条配置把包撑爆。 */
    public static final int REWARD_ITEM_ENTRIES_MAX = 16;
    /** 奖励经验与梦鱼币上限。 */
    public static final long REWARD_EXPERIENCE_MAX = 100_000L;
    public static final int REWARD_COINS_MAX = 1_000_000;
    /** 奖励列表保留几条就够（每台设备记过的玩家）；超出按最旧的丢。 */
    public static final int REWARDED_PLAYERS_MAX = 512;
    /** 同时在场的生成物追踪上限。 */
    public static final int ALIVE_TRACKING_MAX = 512;

    /** 默认刷的怪：模组自己的尸潮怪。 */
    public static final String DEFAULT_ENTITY_ID = DreamingFishCore.MODID + ":siege_zombie";

    // ==================== 位置（登记表的索引来源） ====================

    /** 维度 ID，例如 minecraft:overworld。 */
    private String dimensionId = "";
    private int x;
    private int y;
    private int z;

    // ==================== 配置 ====================

    private String entityId = DEFAULT_ENTITY_ID;
    private int detectionRadius = DETECTION_DEFAULT;
    private int spawnRadius = SPAWN_RADIUS_DEFAULT;
    private int spawnCount = SPAWN_COUNT_DEFAULT;
    private int cooldownTicks = COOLDOWN_DEFAULT;
    private int batches = BATCHES_DEFAULT;
    /** 剿灭结算时是否给合格玩家发一条固定线索。 */
    private boolean fixedClueEnabled;
    /** 固定线索的编号（fragment_data.json 的整数 id）；0 表示未指定。 */
    private int clueId;
    /** 勾选后必须持续有红石信号才工作（ADR：可红石控制的刷怪箱）。 */
    private boolean redstoneControlled;
    /** 全部批次刷完且场上的怪清空后自毁。 */
    private boolean selfDestructWhenCleared;
    private List<RewardEntry> rewardItems = new ArrayList<>();
    private int rewardExperience;
    private int rewardCoins;

    // ==================== 运行状态 ====================

    /** 已经刷出去的批次数。 */
    private int batchesSpawned;
    /** 下一批最早可以刷的游戏刻；-1 表示"还没开始计时"。 */
    private long nextBatchAtTick = -1L;
    /** 本台刷怪箱刷出、目前仍在追踪的实体 UUID。 */
    private List<String> aliveEntityIds = new ArrayList<>();
    /** 已经领过剿灭奖励的玩家 UUID（每台设备每人只发一次）。 */
    private List<String> rewardedPlayerIds = new ArrayList<>();
    /** 本轮是否已经结算过。 */
    private boolean completed;

    /** Gson 需要无参构造。 */
    public SpawnerEntry() {
    }

    public SpawnerEntry(String dimensionId, int x, int y, int z) {
        this.dimensionId = dimensionId == null ? "" : dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String dimensionId() {
        return dimensionId == null ? "" : dimensionId;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    /** 登记表索引键：维度|X|Y|Z。 */
    public String key() {
        return keyOf(dimensionId(), x, y, z);
    }

    public static String keyOf(String dimensionId, int x, int y, int z) {
        return (dimensionId == null ? "" : dimensionId) + "|" + x + "|" + y + "|" + z;
    }

    /** 记录里没有维度就没法回到世界，加载时直接跳过。 */
    boolean valid() {
        return dimensionId != null && !dimensionId.isBlank();
    }

    // ==================== 归一 ====================

    /**
     * 把越界值夹回合法范围，丢掉坏条目。返回是否发生了修改。
     *
     * <p>报告"是否修改"而不是直接静默修正，是为了让加载路径能在修过东西时标脏写回，
     * 避免坏数据每 tick 都被重新夹一遍。</p>
     */
    public boolean normalize() {
        boolean changed = false;
        if (entityId == null || entityId.isBlank()) {
            entityId = DEFAULT_ENTITY_ID;
            changed = true;
        }

        int oldDetection = detectionRadius;
        int oldSpawnRadius = spawnRadius;
        int oldCount = spawnCount;
        int oldCooldown = cooldownTicks;
        int oldBatches = batches;
        int oldClue = clueId;
        int oldExperience = rewardExperience;
        int oldCoins = rewardCoins;
        int oldSpawned = batchesSpawned;
        long oldNextBatch = nextBatchAtTick;

        detectionRadius = clamp(detectionRadius, DETECTION_MIN, DETECTION_MAX);
        spawnRadius = clamp(spawnRadius, SPAWN_RADIUS_MIN, SPAWN_RADIUS_MAX);
        spawnCount = clamp(spawnCount, SPAWN_COUNT_MIN, SPAWN_COUNT_MAX);
        cooldownTicks = clamp(cooldownTicks, COOLDOWN_MIN, COOLDOWN_MAX);
        batches = clamp(batches, BATCHES_MIN, BATCHES_MAX);
        clueId = Math.max(0, clueId);
        rewardExperience = (int) clamp((long) rewardExperience, 0L, REWARD_EXPERIENCE_MAX);
        rewardCoins = clamp(rewardCoins, 0, REWARD_COINS_MAX);
        // 状态与配置打架时以配置为准：批次总数改小了，已刷批次也要跟着夹住。
        batchesSpawned = clamp(batchesSpawned, 0, batches);
        nextBatchAtTick = Math.max(-1L, nextBatchAtTick);

        changed |= oldDetection != detectionRadius
                || oldSpawnRadius != spawnRadius
                || oldCount != spawnCount
                || oldCooldown != cooldownTicks
                || oldBatches != batches
                || oldClue != clueId
                || oldExperience != rewardExperience
                || oldCoins != rewardCoins
                || oldSpawned != batchesSpawned
                || oldNextBatch != nextBatchAtTick;

        if (rewardItems == null) {
            rewardItems = new ArrayList<>();
            changed = true;
        }
        if (aliveEntityIds == null) {
            aliveEntityIds = new ArrayList<>();
            changed = true;
        }
        if (rewardedPlayerIds == null) {
            rewardedPlayerIds = new ArrayList<>();
            changed = true;
        }

        List<RewardEntry> cleaned = new ArrayList<>();
        for (RewardEntry entry : rewardItems) {
            if (entry == null || !entry.valid()) {
                changed = true;
                continue;
            }
            changed |= entry.normalize();
            if (cleaned.size() < REWARD_ITEM_ENTRIES_MAX) {
                cleaned.add(entry);
            } else {
                changed = true;
            }
        }
        if (cleaned.size() != rewardItems.size()) {
            changed = true;
        }
        rewardItems = cleaned;

        changed |= trimList(aliveEntityIds, ALIVE_TRACKING_MAX);
        changed |= trimList(rewardedPlayerIds, REWARDED_PLAYERS_MAX);
        return changed;
    }

    private static boolean trimList(List<String> list, int max) {
        if (list.size() <= max) {
            return false;
        }
        list.subList(0, list.size() - max).clear();
        return true;
    }

    public static int clamp(int value, int lower, int upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    public static long clamp(long value, long lower, long upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    // ==================== 批次调度（纯函数，便于单测） ====================

    /**
     * 现在该不该刷下一批。
     *
     * <p>规则：全新的一台刷怪箱在启用时立刻刷；之后的每一批都必须等**上一批全部死亡**
     * 才开始算 CD，CD 到了才刷。这样节奏由玩家清怪决定，而不是固定间隔连刷。</p>
     */
    static boolean nextBatchDue(int batchesSpawned, int batches, boolean fieldCleared,
                               long nextBatchAtTick, long now) {
        if (batchesSpawned >= batches) {
            return false;
        }
        if (batchesSpawned == 0) {
            // 全新：立刻开刷（nextBatchAtTick 还没排过）；刚结算完上一轮的：等轮次 CD。
            return nextBatchAtTick < 0L || now >= nextBatchAtTick;
        }
        if (!fieldCleared) {
            return false;
        }
        return nextBatchAtTick >= 0L && now >= nextBatchAtTick;
    }

    /** 上一批刚被打光，需要把 CD 起点定在"现在"。 */
    static boolean needsCooldownAnchor(int batchesSpawned, int batches, boolean fieldCleared,
                                       long nextBatchAtTick) {
        return batchesSpawned > 0 && batchesSpawned < batches && fieldCleared && nextBatchAtTick < 0L;
    }

    /** 全部批次刷完且场上清空 —— 剿灭达成，可以结算。 */
    static boolean isCleared(int batchesSpawned, int batches, int aliveCount) {
        return batchesSpawned >= batches && aliveCount <= 0;
    }

    // ==================== 访问器 ====================

    public String entityId() {
        return entityId == null || entityId.isBlank() ? DEFAULT_ENTITY_ID : entityId;
    }

    public void setEntityId(String value) {
        this.entityId = value == null ? "" : value.trim();
        normalize();
    }

    public int detectionRadius() {
        return detectionRadius;
    }

    public void setDetectionRadius(int value) {
        this.detectionRadius = clamp(value, DETECTION_MIN, DETECTION_MAX);
    }

    public int spawnRadius() {
        return spawnRadius;
    }

    public void setSpawnRadius(int value) {
        this.spawnRadius = clamp(value, SPAWN_RADIUS_MIN, SPAWN_RADIUS_MAX);
    }

    public int spawnCount() {
        return spawnCount;
    }

    public void setSpawnCount(int value) {
        this.spawnCount = clamp(value, SPAWN_COUNT_MIN, SPAWN_COUNT_MAX);
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public void setCooldownTicks(int value) {
        this.cooldownTicks = clamp(value, COOLDOWN_MIN, COOLDOWN_MAX);
    }

    public int batches() {
        return batches;
    }

    public void setBatches(int value) {
        this.batches = clamp(value, BATCHES_MIN, BATCHES_MAX);
        this.batchesSpawned = clamp(this.batchesSpawned, 0, this.batches);
    }

    public boolean fixedClueEnabled() {
        return fixedClueEnabled;
    }

    public void setFixedClueEnabled(boolean value) {
        this.fixedClueEnabled = value;
    }

    public int clueId() {
        return clueId;
    }

    public void setClueId(int value) {
        this.clueId = Math.max(0, value);
    }

    public boolean redstoneControlled() {
        return redstoneControlled;
    }

    public void setRedstoneControlled(boolean value) {
        this.redstoneControlled = value;
    }

    public boolean selfDestructWhenCleared() {
        return selfDestructWhenCleared;
    }

    public void setSelfDestructWhenCleared(boolean value) {
        this.selfDestructWhenCleared = value;
    }

    public List<RewardEntry> rewardItems() {
        if (rewardItems == null) {
            rewardItems = new ArrayList<>();
        }
        return rewardItems;
    }

    public int rewardExperience() {
        return rewardExperience;
    }

    public void setRewardExperience(int value) {
        this.rewardExperience = (int) clamp((long) value, 0L, REWARD_EXPERIENCE_MAX);
    }

    public int rewardCoins() {
        return rewardCoins;
    }

    public void setRewardCoins(int value) {
        this.rewardCoins = clamp(value, 0, REWARD_COINS_MAX);
    }

    public int batchesSpawned() {
        return batchesSpawned;
    }

    public long nextBatchAtTick() {
        return nextBatchAtTick;
    }

    public void setNextBatchAtTick(long value) {
        this.nextBatchAtTick = Math.max(-1L, value);
    }

    public List<String> aliveEntityIds() {
        if (aliveEntityIds == null) {
            aliveEntityIds = new ArrayList<>();
        }
        return aliveEntityIds;
    }

    public List<String> rewardedPlayerIds() {
        if (rewardedPlayerIds == null) {
            rewardedPlayerIds = new ArrayList<>();
        }
        return rewardedPlayerIds;
    }

    public boolean completed() {
        return completed;
    }

    // ==================== 状态变更 ====================

    /** 记下一批已经刷出去。 */
    public void markBatchSpawned() {
        batchesSpawned++;
        nextBatchAtTick = -1L;
    }

    /** 记下 CD 起点（上一批刚被打光）。 */
    public void anchorCooldown(long now) {
        nextBatchAtTick = now + cooldownTicks;
    }

    public void trackSpawned(UUID entityId) {
        if (entityId == null) {
            return;
        }
        List<String> ids = aliveEntityIds();
        ids.add(entityId.toString());
        trimList(ids, ALIVE_TRACKING_MAX);
    }

    public void markCompleted() {
        completed = true;
        nextBatchAtTick = -1L;
    }

    public boolean hasRewarded(UUID playerId) {
        return playerId != null && rewardedPlayerIds().contains(playerId.toString());
    }

    public void markRewarded(UUID playerId) {
        if (playerId == null) {
            return;
        }
        List<String> ids = rewardedPlayerIds();
        String key = playerId.toString();
        if (!ids.contains(key)) {
            ids.add(key);
        }
        trimList(ids, REWARDED_PLAYERS_MAX);
    }

    /**
     * 重置为一轮新的尸潮（已经结算过、且不自毁时使用）。
     *
     * <p>刻意保留 {@code rewardedPlayerIds}：每台刷怪箱对每个玩家只发一次奖励，
     * 否则玩家可以反复刷同一台箱子领奖励。轮次之间仍然要等一个 CD，
     * 不然玩家一清完就会立刻接上下一轮。</p>
     */
    public void resetRound(long now) {
        batchesSpawned = 0;
        nextBatchAtTick = now + cooldownTicks;
        aliveEntityIds().clear();
        completed = false;
    }

    /** 一条剿灭奖励物品。 */
    public static final class RewardEntry {
        private String itemId = "";
        private int count = 1;

        public RewardEntry() {
        }

        public RewardEntry(String itemId, int count) {
            this.itemId = itemId == null ? "" : itemId.trim();
            this.count = clamp(count, 1, REWARD_ITEM_COUNT_MAX);
        }

        public String itemId() {
            return itemId == null ? "" : itemId;
        }

        public int count() {
            return clamp(count, 1, REWARD_ITEM_COUNT_MAX);
        }

        boolean valid() {
            return itemId != null && !itemId.isBlank();
        }

        boolean normalize() {
            int clamped = clamp(count, 1, REWARD_ITEM_COUNT_MAX);
            if (clamped == count) {
                return false;
            }
            count = clamped;
            return true;
        }
    }
}
