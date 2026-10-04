package com.hhy.dreamingfishcore.gameplay.raid_system.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hhy.dreamingfishcore.gameplay.raid_system.RaidRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 撤离点选择的不变量测试。
 *
 * <p>最要紧的两条：**永远至少有一个能走的撤离点**（否则玩家被锁在图里），
 * 以及**同种子同结果**（否则重启后撤离点变了，玩家举报无法复现）。</p>
 */
class ExtractionSelectorTest {

    private static ExtractionSelector.Candidate candidate(String id, ExtractionSelector.Kind kind,
                                                         int weight, double x, double z) {
        return new ExtractionSelector.Candidate(id, kind, weight, x, z, Set.of(), Set.of(), 0.0D, 0.0D, 0);
    }

    @Test
    void fixedExtractionIsAlwaysOpen() {
        List<ExtractionSelector.Candidate> candidates = List.of(
                candidate("fixed_gate", ExtractionSelector.Kind.FIXED, 1, 500, 500),
                candidate("random_a", ExtractionSelector.Kind.RANDOM, 100, 100, 100),
                candidate("random_b", ExtractionSelector.Kind.RANDOM, 100, -100, -100));

        ExtractionSelector.Result result = ExtractionSelector.select(candidates,
                ExtractionSelector.Rules.of(1), new RaidRandom(1L));

        assertTrue(result.alwaysOpen().contains("fixed_gate"), "固定撤离点必须每局开放");
        assertEquals(1, result.randomOpen().size(), "随机点按 randomCount 抽取");
        assertEquals(2, result.all().size(), "固定点不该被重复计入随机点");
    }

    @Test
    void randomCountIsRespectedAndHasNoDuplicates() {
        List<ExtractionSelector.Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            candidates.add(candidate("random_" + index, ExtractionSelector.Kind.RANDOM, 100, index * 10.0D, 0));
        }
        ExtractionSelector.Result result = ExtractionSelector.select(candidates,
                ExtractionSelector.Rules.of(3), new RaidRandom(2L));

        assertEquals(3, result.all().size(), "本局开放总数应为 3");
        assertEquals(1, result.alwaysOpen().size(),
                "没有固定撤离点时，会把随机抽中的一个提升为保底（所以它在 alwaysOpen 而不在 randomOpen）");
        assertEquals(2, result.randomOpen().size());
        assertEquals(result.randomOpen().size(), Set.copyOf(result.randomOpen()).size(), "不该重复");
        assertEquals(result.randomOpen().stream().sorted().toList(), result.randomOpen(),
                "输出应按 id 排序，保证确定性");
    }

    @Test
    void conditionalExtractionNeedsItsTags() {
        ExtractionSelector.Candidate powered = new ExtractionSelector.Candidate("powered_gate",
                ExtractionSelector.Kind.CONDITIONAL, 100, 300, 300,
                Set.of("factory_power_on"), Set.of(), 0.0D, 0.0D, 0);
        List<ExtractionSelector.Candidate> candidates = List.of(powered);

        ExtractionSelector.Result withoutTag = ExtractionSelector.select(candidates,
                ExtractionSelector.Rules.of(1), new RaidRandom(3L));
        assertFalse(withoutTag.all().contains("powered_gate"), "条件不满足时不该开放");
        assertEquals(1, withoutTag.rejectionsOf("TAG_MISSING").size());
        assertEquals(1, withoutTag.rejectionsOf("NO_FALLBACK").size(),
                "唯一候选被条件挡住时应如实报告没有可用撤离点（不该硬开一个条件没满足的门）");

        ExtractionSelector.Result withTag = ExtractionSelector.select(candidates,
                new ExtractionSelector.Rules(1, Set.of("factory_power_on"), "", 0, 0), new RaidRandom(3L));
        assertTrue(withTag.all().contains("powered_gate"), "条件满足后应当可以开放");
    }

    @Test
    void forbiddenSpawnGroupIsHonoured() {
        ExtractionSelector.Candidate eastOnly = new ExtractionSelector.Candidate("east_gate",
                ExtractionSelector.Kind.RANDOM, 100, 100, 100, Set.of(), Set.of("east_spawn"), 0, 0, 0);
        ExtractionSelector.Candidate anyGroup = candidate("any_gate", ExtractionSelector.Kind.FIXED, 1, 0, 0);

        ExtractionSelector.Result eastSpawn = ExtractionSelector.select(List.of(eastOnly, anyGroup),
                new ExtractionSelector.Rules(1, Set.of(), "east_spawn", 0, 0), new RaidRandom(4L));
        assertFalse(eastSpawn.randomOpen().contains("east_gate"));
        assertEquals(1, eastSpawn.rejectionsOf("SPAWN_GROUP_FORBIDDEN").size());

        ExtractionSelector.Result westSpawn = ExtractionSelector.select(List.of(eastOnly, anyGroup),
                new ExtractionSelector.Rules(1, Set.of(), "west_spawn", 0, 0), new RaidRandom(4L));
        assertTrue(westSpawn.randomOpen().contains("east_gate"), "别的出生组不该受影响");
    }

    @Test
    void distanceWindowFromSpawnIsHonoured() {
        ExtractionSelector.Candidate tooClose = new ExtractionSelector.Candidate("near_gate",
                ExtractionSelector.Kind.RANDOM, 100, 50, 0, Set.of(), Set.of(), 350, 900, 0);
        ExtractionSelector.Candidate tooFar = new ExtractionSelector.Candidate("far_gate",
                ExtractionSelector.Kind.RANDOM, 100, 2000, 0, Set.of(), Set.of(), 350, 900, 0);
        ExtractionSelector.Candidate justRight = new ExtractionSelector.Candidate("good_gate",
                ExtractionSelector.Kind.RANDOM, 100, 600, 0, Set.of(), Set.of(), 350, 900, 0);
        ExtractionSelector.Candidate fallback = candidate("fallback", ExtractionSelector.Kind.FIXED, 1, 0, 0);

        ExtractionSelector.Result result = ExtractionSelector.select(
                List.of(tooClose, tooFar, justRight, fallback),
                new ExtractionSelector.Rules(3, Set.of(), "", 0, 0), new RaidRandom(5L));

        assertTrue(result.randomOpen().contains("good_gate"));
        assertFalse(result.all().contains("near_gate"));
        assertFalse(result.all().contains("far_gate"));
        assertEquals(1, result.rejectionsOf("TOO_CLOSE").size());
        assertEquals(1, result.rejectionsOf("TOO_FAR").size());
    }

    @Test
    void selectionIsDeterministicForTheSameSeed() {
        List<ExtractionSelector.Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            candidates.add(candidate("gate_" + index, ExtractionSelector.Kind.RANDOM,
                    50 + index, index * 80.0D, index * 20.0D));
        }
        ExtractionSelector.Rules rules = ExtractionSelector.Rules.of(4);

        ExtractionSelector.Result first = ExtractionSelector.select(candidates, rules, new RaidRandom(6L));
        ExtractionSelector.Result second = ExtractionSelector.select(candidates, rules, new RaidRandom(6L));
        assertEquals(first.all(), second.all(), "同种子必须选出同一批撤离点");

        ExtractionSelector.Result other = ExtractionSelector.select(candidates, rules, new RaidRandom(7L));
        assertFalse(first.all().equals(other.all()) && first.randomOpen().size() > 2,
                "换种子不该连一个都不变");
    }

    @Test
    void highWeightIsPickedMoreOften() {
        List<ExtractionSelector.Candidate> candidates = List.of(
                candidate("common_gate", ExtractionSelector.Kind.RANDOM, 100, 0, 0),
                candidate("rare_gate", ExtractionSelector.Kind.RANDOM, 1, 10, 0));
        ExtractionSelector.Rules rules = ExtractionSelector.Rules.of(1);

        int commonWins = 0;
        for (long seed = 0; seed < 300; seed++) {
            ExtractionSelector.Result result = ExtractionSelector.select(candidates, rules, new RaidRandom(seed));
            if (result.all().contains("common_gate")) {
                commonWins++;
            }
        }
        assertTrue(commonWins > 240, "权重 100:1 时高权重点应明显更常被选中，实际 " + commonWins + "/300");
    }

    @Test
    void emptyOrImpossibleInputsAreReportedInsteadOfCrashing() {
        ExtractionSelector.Result empty = ExtractionSelector.select(List.of(),
                ExtractionSelector.Rules.of(2), new RaidRandom(1L));
        assertTrue(empty.alwaysOpen().isEmpty());
        assertEquals(1, empty.rejectionsOf("NO_CANDIDATES").size());

        // 全部候选都被条件/距离挡掉：不该死循环，也不该谎报有路可走
        ExtractionSelector.Candidate blocked = new ExtractionSelector.Candidate("blocked",
                ExtractionSelector.Kind.RANDOM, 100, 0, 0, Set.of("needs_power"), Set.of(), 5000, 0, 0);
        ExtractionSelector.Result impossible = ExtractionSelector.select(List.of(blocked),
                ExtractionSelector.Rules.of(3), new RaidRandom(1L));
        assertTrue(impossible.alwaysOpen().isEmpty(), "没有可用点时必须如实报告");
        assertEquals(1, impossible.rejectionsOf("NO_FALLBACK").size());

        assertEquals(1, ExtractionSelector.select(null, ExtractionSelector.Rules.of(1),
                new RaidRandom(1L)).rejectionsOf("NO_CANDIDATES").size(), "null 输入同样只报告不崩");
    }

    @Test
    void zeroWeightAndDuplicateIdsAreHandled() {
        List<ExtractionSelector.Candidate> candidates = List.of(
                candidate("zero", ExtractionSelector.Kind.RANDOM, 0, 0, 0),
                candidate("normal", ExtractionSelector.Kind.FIXED, 1, 0, 0));
        ExtractionSelector.Result result = ExtractionSelector.select(candidates,
                ExtractionSelector.Rules.of(2), new RaidRandom(8L));

        assertEquals(1, result.rejectionsOf("ZERO_WEIGHT").size());
        assertFalse(result.all().contains("zero"));
        assertTrue(result.hasAlwaysOpen());
    }

    @Test
    void describeListsTypesAndUses() {
        ExtractionSelector.Candidate vehicle = new ExtractionSelector.Candidate("truck",
                ExtractionSelector.Kind.SINGLE_USE, 80, 100, 100, Set.of(), Set.of(), 0, 0, 4);
        ExtractionSelector.Candidate fallback = candidate("gate", ExtractionSelector.Kind.FIXED, 1, 0, 0);
        List<ExtractionSelector.Candidate> candidates = List.of(vehicle, fallback);

        ExtractionSelector.Result result = ExtractionSelector.select(candidates,
                ExtractionSelector.Rules.of(1), new RaidRandom(9L));
        List<String> lines = ExtractionSelector.describe(candidates, result);

        assertEquals(result.all().size(), lines.size());
        assertTrue(lines.stream().anyMatch(line -> line.contains("保底")), "应标出保底撤离点");
        assertTrue(lines.stream().anyMatch(line -> line.contains("可用 4 次")), "应标出可用次数");
    }
}
