package com.hhy.dreamingfishcore.gameplay.raid_system.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 撤离读条的核心规则测试（纯逻辑）。
 *
 * <p>这几条规则直接决定玩家能不能撤出去、以及"走开再回来"会不会被白刷进度，
 * 所以放在单测里钉死，而不是靠手感验证。</p>
 */
class ExtractionRunnerTest {

    @Test
    void progressesWhileInRangeAndCompletesAtDuration() {
        ExtractionRunner.Progress progress = new ExtractionRunner.Progress(5);

        for (int tick = 1; tick <= 4; tick++) {
            assertEquals(ExtractionRunner.Progress.Step.PROGRESSING, progress.advance(true),
                    "第 " + tick + " tick 应仍在读条");
        }
        assertEquals(ExtractionRunner.Progress.Step.COMPLETED, progress.advance(true),
                "第 5 tick 应完成");
        assertEquals(5, progress.ticks());
        assertEquals(1.0D, progress.ratio(), 1.0E-9);
    }

    @Test
    void leavingRangeResetsProgress() {
        ExtractionRunner.Progress progress = new ExtractionRunner.Progress(10);
        progress.advance(true);
        progress.advance(true);
        progress.advance(true);
        assertEquals(3, progress.ticks());

        assertEquals(ExtractionRunner.Progress.Step.INTERRUPTED, progress.advance(false),
                "离开范围应报告中断");
        assertEquals(0, progress.ticks(), "中断后进度必须清零（不能走开再回来接着读）");
        assertEquals(ExtractionRunner.Progress.Step.IDLE, progress.advance(false),
                "再离开一次就没进度可打断了");
    }

    @Test
    void stayingOutDoesNotAccumulateAnything() {
        ExtractionRunner.Progress progress = new ExtractionRunner.Progress(3);
        for (int tick = 0; tick < 50; tick++) {
            progress.advance(false);
        }
        assertEquals(0, progress.ticks(), "范围外不管待多久都不该有进度");
        assertEquals(0.0D, progress.ratio(), 1.0E-9);
    }

    @Test
    void ratioIsCappedAndResetWorks() {
        ExtractionRunner.Progress progress = new ExtractionRunner.Progress(4);
        progress.advance(true);
        assertEquals(0.25D, progress.ratio(), 1.0E-9);
        progress.advance(true);
        progress.advance(true);
        progress.advance(true);
        assertEquals(1.0D, progress.ratio(), 1.0E-9);

        progress.reset();
        assertEquals(0, progress.ticks(), "重置后应回到起点（撤离成功后立刻重置，避免连续触发）");
        assertEquals(ExtractionRunner.Progress.Step.PROGRESSING, progress.advance(true));
    }

    @Test
    void durationIsAtLeastOneTick() {
        ExtractionRunner.Progress instant = new ExtractionRunner.Progress(0);
        assertEquals(1, instant.required(), "时长至少 1 tick，避免配置成 0 时瞬间撤离");

        ExtractionRunner.Progress negative = new ExtractionRunner.Progress(-5);
        assertEquals(1, negative.required());
        assertEquals(ExtractionRunner.Progress.Step.COMPLETED, negative.advance(true));
    }

    @Test
    void usesLeftStartsEmptyAndClearResets() {
        // 没跑过 tick 时不该有任何撤离点的余量记录
        assertEquals(null, ExtractionRunner.usesLeft("nothing_here"));
        ExtractionRunner.clear();
        assertEquals(null, ExtractionRunner.usesLeft("nothing_here"));
        assertTrue(ExtractionRunner.DEFAULT_RADIUS > 0.0D);
        assertTrue(ExtractionRunner.DEFAULT_DURATION_TICKS > 0);
    }
}
