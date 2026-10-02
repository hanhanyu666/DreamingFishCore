package com.hhy.dreamingfishcore.gameplay.blueprint_system;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 掉落抽取的纯逻辑：「按玩家只看已学」的去重口径。 */
class PlayerBlueprintSelectionTest {

    private static final List<String> POOL = List.of(
            "minecraft:iron_sword",
            "minecraft:iron_pickaxe",
            "minecraft:anvil");

    @Test
    void learnedBlueprintsAreExcluded() {
        List<String> candidates = PlayerBlueprintData.excludeLearned(
                POOL, Set.of("minecraft:iron_sword"));

        assertEquals(List.of("minecraft:iron_pickaxe", "minecraft:anvil"), candidates);
    }

    @Test
    void nothingLearnedKeepsTheWholePool() {
        assertEquals(POOL, PlayerBlueprintData.excludeLearned(POOL, Set.of()));
    }

    @Test
    void everythingLearnedLeavesNothingToDrop() {
        assertTrue(PlayerBlueprintData.excludeLearned(POOL, Set.copyOf(POOL)).isEmpty(),
                "全学完之后不再掉落，掉落方拿到空列表就不会生成物品");
    }

    @Test
    void learnedItemsOutsideThePoolDoNotAffectCandidates() {
        List<String> candidates = PlayerBlueprintData.excludeLearned(
                POOL, Set.of("minecraft:diamond_sword"));

        assertEquals(POOL, candidates, "学过不在池里的物品不该把池里的东西挤掉");
    }

    @Test
    void candidatesKeepPoolOrder() {
        List<String> candidates = PlayerBlueprintData.excludeLearned(
                POOL, Set.of("minecraft:iron_pickaxe"));

        assertEquals(List.of("minecraft:iron_sword", "minecraft:anvil"), candidates);
    }
}
