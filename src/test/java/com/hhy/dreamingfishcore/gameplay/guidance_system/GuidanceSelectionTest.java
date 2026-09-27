package com.hhy.dreamingfishcore.gameplay.guidance_system;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GuidanceSelectionTest {
    @Test
    void newActionsDoNotStealTrackingAndCyclingWrapsBothWays() {
        GuidanceSelection selection = new GuidanceSelection();
        GuidanceEntry first = entry("test:first", "test:medical", 1);
        GuidanceEntry second = entry("test:second", "test:build", 2);
        selection.update(List.of(GuidanceViewData.fromEntry(first)));
        selection.update(List.of(GuidanceViewData.fromEntry(second), GuidanceViewData.fromEntry(first)));
        assertEquals("test:first", selection.selected().definitionId());
        selection.cycle(-1);
        assertEquals("test:second", selection.selected().definitionId());
        selection.cycle(1);
        assertEquals("test:first", selection.selected().definitionId());
        assertFalse(selection.select("test:unknown"));
        assertEquals(2, selection.active().size());
        assertEquals(GuidanceEntry.Status.ACTIVE, second.getStatus());
    }

    @Test
    void completedOrArchivedActionsCannotBeTrackedAndDisconnectClearsSelection() {
        GuidanceSelection selection = new GuidanceSelection();
        GuidanceEntry first = entry("test:first", "test:medical", 1);
        GuidanceEntry second = entry("test:second", "test:build", 2);
        first.resolve(3);
        selection.update(List.of(GuidanceViewData.fromEntry(first), GuidanceViewData.fromEntry(second)));
        assertEquals("test:second", selection.selected().definitionId());
        assertFalse(selection.select("test:first"));
        second.archive(4);
        selection.update(List.of(GuidanceViewData.fromEntry(second)));
        assertNull(selection.selected());
        selection.cycle(-1);
        selection.clear();
        assertTrue(selection.active().isEmpty());
    }

    @Test
    void selectionUsesTheCurrentSnapshotAfterContentUpdatesAndClearsOnDisconnect() {
        GuidanceSelection selection = new GuidanceSelection();
        GuidanceViewData first = GuidanceViewData.fromEntry(entry("test:first", "test:medical", 1));
        GuidanceViewData second = GuidanceViewData.fromEntry(entry("test:second", "test:build", 2));
        selection.update(List.of(first, second));
        assertSame(first, selection.selected());
        assertTrue(selection.select(second.definitionId()));
        for (int frame = 0; frame < 1_000; frame++) {
            assertSame(second, selection.selected());
        }

        GuidanceViewData refreshedSecond = GuidanceViewData.fromEntry(entry("test:second", "test:build", 2));
        selection.update(List.of(first, refreshedSecond));
        assertSame(refreshedSecond, selection.selected());
        assertFalse(selection.select("test:missing"));
        assertSame(refreshedSecond, selection.selected());
        selection.update(List.of(first));
        assertSame(first, selection.selected());
        selection.clear();
        assertNull(selection.selected());
        selection.update(List.of(second));
        assertSame(second, selection.selected());
        selection.update(List.of());
        assertNull(selection.selected());
    }

    @Test
    void onlyTheSameStoryLineReplacesItsPreviousStep() {
        GuidanceEntry medical = entry("test:first", "test:medical", 1);
        GuidanceSeed nextMedical = new GuidanceSeed("test:next", "复核", "去复核")
                .withStoryStage("test:stage").withStoryLine("test:medical");
        GuidanceSeed build = new GuidanceSeed("test:build", "建设", "去建设")
                .withStoryStage("test:stage").withStoryLine("test:build");
        assertTrue(medical.isReplacedBy(nextMedical));
        assertFalse(medical.isReplacedBy(build));
        assertEquals("test:build", GuidanceSeed.copyOf(build).getStoryLineId());
        medical.archive(2);
        assertEquals(GuidanceEntry.Status.ARCHIVED, medical.getStatus());
        assertFalse(medical.resolve(3));
    }

    private static GuidanceEntry entry(String id, String line, long time) {
        return GuidanceEntry.fromMessage(new GuidanceSeed(id, "行动", "说明")
                .withStoryStage("test:stage").withStoryLine(line), "source:" + id, 1, "NPC", "原文", time);
    }
}
