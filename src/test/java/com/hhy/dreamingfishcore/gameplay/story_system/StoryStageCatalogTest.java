package com.hhy.dreamingfishcore.gameplay.story_system;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryStageCatalogTest {
    @Test
    void exposesTheFiveManualStagesInStableOrder() {
        var stages = StoryStageCatalog.seeds();

        assertEquals(5, stages.size());
        assertEquals("梦的开始", stages.get(0).name());
        assertEquals("余梦期", stages.get(1).name());
        assertEquals("管制期", stages.get(2).name());
        assertEquals("疑光期", stages.get(3).name());
        assertEquals("破晓期", stages.get(4).name());
        assertEquals(5, new HashSet<>(stages.stream().map(StoryStageCatalog.StageSeed::id).toList()).size());
        assertTrue(stages.stream().allMatch(seed -> seed.number() > 0));
    }
}
