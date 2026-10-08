package com.hhy.dreamingfishcore.gameplay.archive_system;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 资料库的纯逻辑测试。
 *
 * <p>刻意不引导注册表/世界：{@link ArchiveRules} 里的判定全是纯函数，所以直接喂字符串就能测
 * （项目里既有的做法——把数值与判定抽出来单独测）。</p>
 */
class ArchiveRulesTest {

    @Test
    void normalizeTrimsAndDropsMalformedIds() {
        assertEquals(
                List.of("minecraft:stick", "dreamingfishcore:water_gun"),
                ArchiveRules.normalize(List.of(
                        "  minecraft:stick  ",   // 首尾空白要去掉
                        "minecraft:stick",       // 重复只留一条
                        "",                      // 空串丢掉
                        "   ",
                        "no_namespace",          // 没有冒号
                        ":no_namespace",         // 冒号在开头
                        "trailing:",             // 冒号在结尾
                        "dreamingfishcore:water_gun")));
    }

    @Test
    void normalizeKeepsFirstOccurrenceOrder() {
        assertEquals(List.of("a:b", "c:d", "e:f"),
                ArchiveRules.normalize(List.of("a:b", "c:d", "a:b", "e:f", "c:d")));
    }

    @Test
    void normalizeOfNullIsEmpty() {
        assertTrue(ArchiveRules.normalize(null).isEmpty());
    }

    @Test
    void newIdsOnlyReturnsWhatIsMissing() {
        assertEquals(
                List.of("c:new1", "d:new2"),
                ArchiveRules.newIds(
                        List.of("a:old", "b:old"),
                        List.of("a:old", "c:new1", "b:old", "d:new2", "c:new1")));
        assertTrue(ArchiveRules.newIds(List.of("a:x"), List.of("a:x")).isEmpty());
    }

    @Test
    void newIdsTreatsDuplicatesInsideIncomingAsOneEntry() {
        // 同一个 ID 在来料里出现两次，也只能算一条新增——否则库里会出现重复条目。
        assertEquals(List.of("z:only"), ArchiveRules.newIds(List.of(), List.of("z:only", "z:only")));
    }

    @Test
    void unlearnedSkipsWhatThePlayerAlreadyKnows() {
        assertEquals(
                List.of("lib:third"),
                ArchiveRules.unlearned(
                        List.of("lib:first", "lib:second", "lib:third"),
                        List.of("lib:first", "lib:second")));
        assertTrue(ArchiveRules.unlearned(List.of("lib:a"), List.of("lib:a")).isEmpty(),
                "库里全都会了就该返回空——调用方据此提示「都已经学会了」");
    }

    @Test
    void capacityBoundaryIsInclusive() {
        int max = ArchiveRules.MAX_RECIPES_PER_LIBRARY;
        List<String> full = java.util.stream.IntStream.range(0, max)
                .mapToObj(i -> "pool:item" + i)
                .toList();

        assertFalse(ArchiveRules.exceedsCapacity(full, List.of()), "刚好装满不算超");
        assertFalse(ArchiveRules.exceedsCapacity(full, List.of("pool:item0")),
                "重复条目不占新位置，不该判超");
        assertTrue(ArchiveRules.exceedsCapacity(full, List.of("pool:another")),
                "再多一条就该判超");
    }
}
