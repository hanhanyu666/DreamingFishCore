package com.hhy.dreamingfishcore.gameplay.storybook_system;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryBookDataTest {
    @Test
    void chapterZeroIsNotARealChapterAndFirstPageCanUnlockItsChapter() {
        StoryBookData data = new StoryBookData();

        assertFalse(data.unlockChapter(0));
        assertTrue(data.unlockChapter(1));
        assertTrue(data.hasUnlockedChapter(1));
    }

    @Test
    void obtainedOrderOnlyContainsOwnedFragmentsAndPreservesOwnership() {
        StoryBookData data = new StoryBookData();
        data.setUnlockedFragmentIds(new LinkedHashSet<>(List.of(1, 2, 3)));

        data.setObtainedOrder(List.of(3, 3, 99, 1));

        assertEquals(List.of(3, 1, 2), data.getSortedFragmentIds());
        assertTrue(data.hasUnlockedFragment(2));
    }
}
