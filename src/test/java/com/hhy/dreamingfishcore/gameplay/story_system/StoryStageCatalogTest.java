package com.hhy.dreamingfishcore.gameplay.story_system;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryStageCatalogTest {
    @Test
    void exposesTheTwoCurrentStagesInStableOrder() {
        var stages = StoryStageCatalog.seeds();

        assertEquals(2, stages.size());
        assertEquals("梦的开始", stages.get(0).name());
        assertEquals("余梦期", stages.get(1).name());
        assertEquals(2, new HashSet<>(stages.stream().map(StoryStageCatalog.StageSeed::id).toList()).size());
        assertTrue(stages.stream().allMatch(seed -> seed.number() > 0));
    }
}
