package com.hhy.dreamingfishcore.gameplay.task_location_system.client;

import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceEntry;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceViewData;
import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskLocationReminderHudRendererTest {
    private final CountingFont font = new CountingFont();

    @BeforeEach
    void resetCaches() {
        TaskLocationReminderHudRenderer.invalidateLayoutCache();
    }

    @Test
    void unchangedFramesReuseLayoutWithoutAnyTextMeasurement() {
        GuidanceViewData selected = entry("first", "第一条", "正文内容".repeat(40));
        List<GuidanceViewData> active = List.of(selected);
        var first = TaskLocationReminderHudRenderer.getPreparedGuidanceLayout(
                font, active, selected, 160, false, "");
        int measurements = font.measurements;
        assertTrue(measurements > 0);

        for (int frame = 0; frame < 1_000; frame++) {
            assertSame(first, TaskLocationReminderHudRenderer.getPreparedGuidanceLayout(
                    font, active, selected, 160, false, ""));
        }
        assertEquals(measurements, font.measurements);
        assertEquals(3, first.description().size());
        assertTrue(first.description().getLast().endsWith("…"));
        assertTrue(first.width() <= 160);
        assertThrows(UnsupportedOperationException.class, () -> first.description().add("unexpected"));
    }

    @Test
    void collapsedAndExpandedLayoutsStayCachedIndependently() {
        GuidanceViewData first = entry("first", "第一条", "正文");
        List<GuidanceViewData> active = List.of(first,
                entry("second", "第二条", ""), entry("third", "第三条", ""), entry("fourth", "第四条", ""));
        var collapsed = TaskLocationReminderHudRenderer.getPreparedGuidanceLayout(
                font, active, first, 160, false, "");
        var expanded = TaskLocationReminderHudRenderer.getPreparedGuidanceLayout(
                font, active, first, 160, true, "Tab + 滚轮切换");
        int measurements = font.measurements;

        assertEquals("追踪 · 第一条 1/4", collapsed.title());
        assertTrue(collapsed.compactCards().isEmpty());
        assertEquals("", collapsed.hint());
        assertEquals("Tab + 滚轮切换", expanded.hint());
        assertEquals(List.of("第二条", "第三条"),
                expanded.compactCards().stream().map(card -> card.title()).toList());
        assertEquals(23, collapsed.mainHeight());
        assertEquals(30, expanded.mainHeight());
        assertEquals(14, expanded.compactHeight());
        for (int frame = 0; frame < 100; frame++) {
            assertSame(collapsed, TaskLocationReminderHudRenderer.getPreparedGuidanceLayout(
                    font, active, first, 160, false, ""));
            assertSame(expanded, TaskLocationReminderHudRenderer.getPreparedGuidanceLayout(
                    font, active, first, 160, true, "Tab + 滚轮切换"));
        }
        assertEquals(measurements, font.measurements);
    }

    @Test
    void selectionAndNewSnapshotsRefreshTitlesAndOtherCardsImmediately() {
        GuidanceViewData first = entry("first", "第一条", "正文");
        GuidanceViewData second = entry("second", "第二条", "正文");
        List<GuidanceViewData> active = List.of(first, second);
        var original = TaskLocationReminderHudRenderer.getPreparedGuidanceLayout(
                font, active, first, 160, true, "Tab + 滚轮切换");
        var switched = TaskLocationReminderHudRenderer.getPreparedGuidanceLayout(
                font, active, second, 160, true, "Tab + 滚轮切换");
        assertNotSame(original, switched);
        assertEquals("追踪 · 第二条 2/2", switched.title());
        assertEquals("第一条", switched.compactCards().getFirst().title());

        // The selected entry is unchanged, but another card's content changed.
        GuidanceViewData updatedFirst = entry("first", "第一条已更新", "新正文");
        var updated = TaskLocationReminderHudRenderer.getPreparedGuidanceLayout(
                font, List.of(updatedFirst, second), second, 160, true, "Tab + 滚轮切换");
        assertNotSame(switched, updated);
        assertEquals("第一条已更新", updated.compactCards().getFirst().title());

        var updatedSelected = TaskLocationReminderHudRenderer.getPreparedGuidanceLayout(
                font, List.of(updatedFirst), updatedFirst, 160, false, "");
        assertEquals("追踪 · 第一条已更新", updatedSelected.title());
        assertEquals(List.of("新正文"), updatedSelected.description());
    }

    @Test
    void widthFontAndReboundKeyInvalidateTheAffectedLayout() {
        GuidanceViewData selected = entry("first", "很长的任务标题".repeat(4), "很长的正文".repeat(12));
        List<GuidanceViewData> active = List.of(selected, entry("second", "其他", ""));
        var wide = TaskLocationReminderHudRenderer.getPreparedGuidanceLayout(
                font, active, selected, 160, true, "Tab + 滚轮切换");
        var narrow = TaskLocationReminderHudRenderer.getPreparedGuidanceLayout(
                font, active, selected, 80, true, "Tab + 滚轮切换");
        assertNotSame(wide, narrow);
        assertTrue(narrow.width() <= 80);
        assertNotEquals(wide.description(), narrow.description());

        var rebound = TaskLocationReminderHudRenderer.getPreparedGuidanceLayout(
                font, active, selected, 80, true, "G + 滚轮切换");
        assertNotSame(narrow, rebound);
        assertEquals("G + 滚轮切换", rebound.hint());

        CountingFont replacementFont = new CountingFont();
        var reloaded = TaskLocationReminderHudRenderer.getPreparedGuidanceLayout(
                replacementFont, active, selected, 80, true, "G + 滚轮切换");
        assertNotSame(rebound, reloaded);
        assertTrue(replacementFont.measurements > 0);
    }

    @Test
    void resourceReloadClearsBothLayoutsEvenWithTheSameFontInstance() {
        GuidanceViewData selected = entry("first", "第一条", "正文");
        List<GuidanceViewData> active = List.of(selected, entry("second", "第二条", ""));
        var collapsed = TaskLocationReminderHudRenderer.getPreparedGuidanceLayout(
                font, active, selected, 160, false, "");
        var expanded = TaskLocationReminderHudRenderer.getPreparedGuidanceLayout(
                font, active, selected, 160, true, "Tab + 滚轮切换");
        int measurements = font.measurements;

        TaskLocationReminderHudRenderer.invalidateLayoutCache();

        assertNotSame(collapsed, TaskLocationReminderHudRenderer.getPreparedGuidanceLayout(
                font, active, selected, 160, false, ""));
        assertNotSame(expanded, TaskLocationReminderHudRenderer.getPreparedGuidanceLayout(
                font, active, selected, 160, true, "Tab + 滚轮切换"));
        assertTrue(font.measurements > measurements);
    }

    @Test
    void blankAndFormattedDescriptionsKeepTheirExistingLayoutRules() {
        GuidanceViewData blank = entry("blank", "空正文", " \n\t ");
        var empty = TaskLocationReminderHudRenderer.getPreparedGuidanceLayout(
                font, List.of(blank), blank, 160, false, "");
        assertTrue(empty.description().isEmpty());
        assertEquals(16, empty.mainHeight());

        GuidanceViewData formatted = entry("formatted", "格式正文", "  §a第一行\n   第二行  ");
        var normalized = TaskLocationReminderHudRenderer.getPreparedGuidanceLayout(
                font, List.of(formatted), formatted, 160, false, "");
        assertEquals(List.of("第一行 第二行"), normalized.description());
    }

    private static GuidanceViewData entry(String id, String title, String content) {
        return new GuidanceViewData(id, id, 0, "", title, content,
                "", "", "", "", false, 0, 0, 0, GuidanceEntry.Status.ACTIVE, 0, 0);
    }

    /** Deterministic glyph metrics without a game window, texture uploads or GL. */
    private static final class CountingFont extends Font {
        private int measurements;

        private CountingFont() {
            super(ignored -> {
                throw new AssertionError("The layout test must not load a font texture");
            }, false);
        }

        @Override
        public int width(String text) {
            measurements++;
            return text.codePointCount(0, text.length()) * 6;
        }

        @Override
        public String plainSubstrByWidth(String text, int maxWidth) {
            measurements++;
            int codePoints = Math.min(text.codePointCount(0, text.length()), Math.max(0, maxWidth / 6));
            return text.substring(0, text.offsetByCodePoints(0, codePoints));
        }
    }
}
