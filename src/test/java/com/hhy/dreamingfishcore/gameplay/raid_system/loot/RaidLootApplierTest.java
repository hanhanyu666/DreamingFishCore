package com.hhy.dreamingfishcore.gameplay.raid_system.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 填充记账与清理安全规则的测试。
 *
 * <p>清理是唯一会**删除玩家世界里物品**的操作，所以它的准入条件必须单独守住：
 * 只有容器里装的仍是"当初我们放进去的那批"才允许清空。</p>
 */
class RaidLootApplierTest {

    private static RaidLootApplier.AppliedStack stack(String itemId, int count) {
        return new RaidLootApplier.AppliedStack(itemId, count);
    }

    @Test
    void clearingIsAllowedOnlyWhenContentsMatchExactly() {
        List<RaidLootApplier.AppliedStack> expected = List.of(stack("minecraft:coal", 3),
                stack("minecraft:iron_ingot", 1));

        assertTrue(RaidLootApplier.canClear(expected, List.of(stack("minecraft:iron_ingot", 1),
                stack("minecraft:coal", 3))), "顺序不同但内容一致应当允许清理");
        assertTrue(RaidLootApplier.canClear(expected,
                List.of(stack("minecraft:coal", 2), stack("minecraft:coal", 1),
                        stack("minecraft:iron_ingot", 1))), "同一物品分了多摞也应当允许");

        assertFalse(RaidLootApplier.canClear(expected, List.of(stack("minecraft:coal", 3))),
                "少了东西（玩家拿走了）不许清");
        assertFalse(RaidLootApplier.canClear(expected, List.of(stack("minecraft:coal", 3),
                stack("minecraft:iron_ingot", 1), stack("minecraft:gold_ingot", 1))),
                "玩家塞了别的东西不许清");
        assertFalse(RaidLootApplier.canClear(expected, List.of()),
                "空容器不等于内容一致（可能已被搬空），仍按不一致处理");
        assertFalse(RaidLootApplier.canClear(expected, List.of(stack("minecraft:coal", 0))),
                "数量为 0 的条目不该参与比较");
    }

    @Test
    void appliedRecordJsonRoundTrip() {
        RaidLootApplier.AppliedContainer original = new RaidLootApplier.AppliedContainer(
                "factory_office_container_loot_01", "minecraft:overworld", 9810, -59, 1815,
                List.of(stack("minecraft:coal", 4), stack("minecraft:gold_ingot", 2)));

        RaidLootApplier.AppliedContainer reparsed =
                RaidLootApplier.AppliedContainer.fromJson(original.toJson());

        assertNotNull(reparsed);
        assertEquals(original.anchorId(), reparsed.anchorId());
        assertEquals(original.dimension(), reparsed.dimension());
        assertEquals(9810, reparsed.x());
        assertEquals(-59, reparsed.y());
        assertEquals(1815, reparsed.z());
        assertEquals(original.items(), reparsed.items());
        assertEquals(original.pos(), reparsed.pos(), "坐标应能还原成 BlockPos");

        assertEquals(null, RaidLootApplier.AppliedContainer.fromJson(null), "坏数据应返回 null 而不是抛异常");
    }

    @Test
    void emptyRecordMeansNothingToClear() {
        assertTrue(RaidLootApplier.canClear(List.of(), List.of()), "没填过东西时清理是空操作");
        assertFalse(RaidLootApplier.canClear(List.of(), List.of(stack("minecraft:coal", 1))),
                "没记过账却要求清理时不应误清玩家容器");
    }
}
