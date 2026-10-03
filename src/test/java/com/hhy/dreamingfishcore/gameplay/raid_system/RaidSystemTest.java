package com.hhy.dreamingfishcore.gameplay.raid_system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 对局随机与 Manifest 的纯逻辑测试。
 *
 * <p>重点不是"随机得像不像"，而是**可复现**：同 seed 同结果、子系统互不影响、
 * 顺序确定。这三条是设计稿里"服务器重启后可恢复、玩家举报可复现"的前提。</p>
 */
class RaidSystemTest {

    private record Entry(String id, int weight) {
    }

    // ---------------------------------------------------------------- 种子

    @Test
    void sameSeedGivesSameSequence() {
        long[] left = new RaidRandom(12345L).peek(16);
        long[] right = new RaidRandom(12345L).peek(16);
        assertEquals(List.of(box(left)), List.of(box(right)), "同种子必须给出完全相同的序列");
    }

    @Test
    void differentSeedGivesDifferentSequence() {
        long[] left = new RaidRandom(12345L).peek(8);
        long[] right = new RaidRandom(12346L).peek(8);
        assertNotEquals(List.of(box(left)), List.of(box(right)));
    }

    @Test
    void raidSeedIsStableAndSensitiveToEveryInput() {
        long base = RaidRandom.raidSeed(111L, "factory", 7L);
        assertEquals(base, RaidRandom.raidSeed(111L, "factory", 7L), "同样输入必须同样结果");
        assertNotEquals(base, RaidRandom.raidSeed(112L, "factory", 7L), "换服务器种子应换结果");
        assertNotEquals(base, RaidRandom.raidSeed(111L, "warehouse", 7L), "换地图应换结果");
        assertNotEquals(base, RaidRandom.raidSeed(111L, "factory", 8L), "换对局号应换结果");
    }

    @Test
    void raidSeedSurvivesNullMapId() {
        long seed = RaidRandom.raidSeed(1L, null, 1L);
        assertEquals(seed, RaidRandom.raidSeed(1L, null, 1L));
    }

    @Test
    void subsystemsAreIndependent() {
        RaidManifest manifest = manifest(42L);

        RaidRandom loot = manifest.randomFor("loot");
        RaidRandom mobs = manifest.randomFor("mobs");
        assertNotEquals(loot.peek(8)[0], mobs.peek(8)[0], "不同子系统的序列不应相同");

        // 关键性质：怪物抽了多少次，不影响战利品的抽取结果
        RaidRandom lootOnly = manifest.randomFor("loot");
        List<Integer> expected = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            expected.add(lootOnly.nextInt(1000));
        }

        RaidRandom lootAgain = manifest.randomFor("loot");
        RaidRandom mobRandom = manifest.randomFor("mobs");
        List<Integer> actual = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            mobRandom.nextInt(50);                 // 中途大量使用怪物子系统
            actual.add(lootAgain.nextInt(1000));
        }
        assertEquals(expected, actual, "别的子系统抽多少次都不该影响战利品结果");
    }

    @Test
    void peekDoesNotAdvanceTheSequence() {
        RaidRandom random = new RaidRandom(777L);
        long[] first = random.peek(4);
        long[] second = random.peek(4);
        assertEquals(List.of(box(first)), List.of(box(second)), "peek 不应改变内部状态");
        assertEquals(first[0], random.nextLong(), "peek 之后取到的第一个数应与 peek 首项一致");
    }

    // ---------------------------------------------------------------- 取值

    @Test
    void nextIntStaysInRangeAndIsRoughlyUniform() {
        RaidRandom random = new RaidRandom(2024L);
        int buckets = 10;
        int[] counts = new int[buckets];
        for (int index = 0; index < 20_000; index++) {
            int value = random.nextInt(buckets);
            assertTrue(value >= 0 && value < buckets, "必须落在 [0, bound) 内：" + value);
            counts[value]++;
        }
        for (int index = 0; index < buckets; index++) {
            assertTrue(counts[index] > 1500 && counts[index] < 2500,
                    "第 " + index + " 桶数量异常：" + counts[index]);
        }
        assertEquals(0, random.nextInt(0), "bound ≤ 0 时返回 0 而不是抛异常");
    }

    @Test
    void nextIntInclusiveRangeAndDoubles() {
        RaidRandom random = new RaidRandom(5L);
        Set<Integer> seen = new HashSet<>();
        for (int index = 0; index < 500; index++) {
            int value = random.nextInt(3, 5);
            assertTrue(value >= 3 && value <= 5, "应落在闭区间 [3,5]：" + value);
            seen.add(value);
        }
        assertEquals(Set.of(3, 4, 5), seen, "闭区间两端都应能取到");

        for (int index = 0; index < 500; index++) {
            double unit = random.nextDouble();
            assertTrue(unit >= 0.0D && unit < 1.0D, "[0,1) 越界：" + unit);
            double ranged = random.nextDouble(2.0D, 4.0D);
            assertTrue(ranged >= 2.0D && ranged < 4.0D, "[2,4) 越界：" + ranged);
        }
        assertEquals(2.0D, random.nextDouble(2.0D, 2.0D), "区间为空时返回下界");
    }

    @Test
    void pickWeightedRespectsWeightsAndSkipsNonPositive() {
        List<Entry> entries = List.of(new Entry("common", 100), new Entry("rare", 50),
                new Entry("never", 0), new Entry("negative", -10));
        RaidRandom random = new RaidRandom(99L);

        int common = 0;
        int rare = 0;
        for (int index = 0; index < 30_000; index++) {
            Entry picked = random.pickWeighted(entries, Entry::weight).orElseThrow();
            assertNotEquals("never", picked.id(), "权重 0 的条目不该被抽中");
            assertNotEquals("negative", picked.id(), "负权重的条目不该被抽中");
            if (picked.id().equals("common")) {
                common++;
            } else {
                rare++;
            }
        }
        // 期望 2:1，给足松紧度（种子固定，这个断言是确定的）
        double ratio = (double) common / rare;
        assertTrue(ratio > 1.6D && ratio < 2.4D, "权重比例不对：common=" + common + " rare=" + rare);
    }

    @Test
    void pickWeightedOnEmptyPoolReturnsEmptyInsteadOfThrowing() {
        RaidRandom random = new RaidRandom(1L);
        assertTrue(random.pickWeighted(List.of(), Entry::weight).isEmpty());
        assertTrue(random.pickWeighted(null, Entry::weight).isEmpty());
        assertEquals(Optional.empty(),
                random.pickWeighted(List.of(new Entry("zero", 0)), Entry::weight),
                "总权重为 0 时应返回空，由调用方走兜底");
    }

    // ---------------------------------------------------------------- Manifest

    private static RaidManifest manifest(long seed) {
        return new RaidManifest(3L, seed, "abandoned_factory", 1_700_000_000_000L, 8, 2,
                Set.of("east_spawn", "west_spawn"), Set.of("factory_north_locked"),
                Set.of("factory_truck_extract"), Map.of("laboratory_keycard", 1),
                List.of("本局测试数据"));
    }

    @Test
    void manifestJsonRoundTripKeepsEveryField() {
        RaidManifest original = manifest(RaidRandom.raidSeed(1L, "abandoned_factory", 3L));
        RaidManifest reparsed = RaidManifest.fromJson(original.toJson());

        assertNotNull(reparsed);
        assertEquals(original.raidId(), reparsed.raidId());
        assertEquals(original.raidSeed(), reparsed.raidSeed());
        assertEquals(original.mapId(), reparsed.mapId());
        assertEquals(original.startedAtEpochMillis(), reparsed.startedAtEpochMillis());
        assertEquals(original.playerCount(), reparsed.playerCount());
        assertEquals(original.difficultyTier(), reparsed.difficultyTier());
        assertEquals(original.activeSpawnGroups(), reparsed.activeSpawnGroups());
        assertEquals(original.activeVariants(), reparsed.activeVariants());
        assertEquals(original.activeExtractions(), reparsed.activeExtractions());
        assertEquals(original.allocatedRareItems(), reparsed.allocatedRareItems());
        assertEquals(original.notes(), reparsed.notes());
    }

    @Test
    void manifestFromBrokenJsonReturnsNullInsteadOfThrowing() {
        assertNull(RaidManifest.fromJson(null));
        assertNull(RaidManifest.fromJson(JsonParser.parseString("{\"map_id\":\"x\"}").getAsJsonObject()),
                "缺关键字段时应返回 null，由调用方按没有进行中的对局处理");
    }

    @Test
    void manifestIterationOrderIsDeterministic() {
        // 用 HashSet 打乱输入顺序，输出必须是稳定排序后的结果
        Set<String> spawnGroups = new HashSet<>(List.of("west_spawn", "east_spawn", "south_spawn"));
        RaidManifest manifest = new RaidManifest(1L, 2L, "map", 0L, 1, 0, spawnGroups,
                Set.of(), Set.of(), Map.of(), List.of());
        assertEquals(List.of("east_spawn", "south_spawn", "west_spawn"),
                List.copyOf(manifest.activeSpawnGroups()), "集合应按字典序稳定输出");

        RaidManifest reparsed = RaidManifest.fromJson(manifest.toJson());
        assertNotNull(reparsed);
        assertEquals(List.copyOf(manifest.activeSpawnGroups()), List.copyOf(reparsed.activeSpawnGroups()));
    }

    @Test
    void manifestHelpersBehave() {
        RaidManifest manifest = manifest(123L);
        RaidManifest withNote = manifest.withNote("追加一条");
        assertEquals(manifest.notes().size() + 1, withNote.notes().size());
        assertEquals(manifest.raidSeed(), withNote.raidSeed(), "追加备注不该改变对局身份");

        RaidRandom direct = new RaidRandom(123L).forSystem("loot");
        RaidRandom viaManifest = manifest.randomFor("loot");
        assertEquals(List.of(box(direct.peek(4))), List.of(box(viaManifest.peek(4))),
                "manifest.randomFor 应等价于用主种子派生子系统");

        assertTrue(manifest.describe().stream().anyMatch(line -> line.contains("abandoned_factory")));
        assertFalse(manifest.describe().isEmpty());
    }

    private static String box(long[] values) {
        StringBuilder builder = new StringBuilder();
        for (long value : values) {
            builder.append(value).append(',');
        }
        return builder.toString();
    }
}
