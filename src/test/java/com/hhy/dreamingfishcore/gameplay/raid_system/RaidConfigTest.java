package com.hhy.dreamingfishcore.gameplay.raid_system;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 对局自动结束的判定（纯函数）。
 *
 * <p>这条判定会在服务端每 tick 被调用，判错就会"每局刚开始就被结束"或"永远不结束"，
 * 所以单独钉住边界。</p>
 */
class RaidConfigTest {

    private static final long START = 1_700_000_000_000L;

    @Test
    void autoEndTriggersOnlyAfterTheConfiguredTime() {
        assertFalse(RaidConfig.shouldAutoEnd(START, START, 1800), "刚开始时不该结束");
        assertFalse(RaidConfig.shouldAutoEnd(START, START + 1799_000L, 1800), "差 1 秒还不该结束");
        assertTrue(RaidConfig.shouldAutoEnd(START, START + 1800_000L, 1800), "刚好到点应结束");
        assertTrue(RaidConfig.shouldAutoEnd(START, START + 9999_000L, 1800), "超时当然结束");
    }

    @Test
    void zeroOrNegativeDisablesAutoEnd() {
        assertFalse(RaidConfig.shouldAutoEnd(START, START + 999_999_000L, 0),
                "配置成 0 表示关闭自动结束");
        assertFalse(RaidConfig.shouldAutoEnd(START, START + 999_999_000L, -5),
                "负数同样按关闭处理");
    }

    @Test
    void missingStartTimeNeverTriggers() {
        assertFalse(RaidConfig.shouldAutoEnd(0L, START, 1800), "没有开局时间就不该自动结束");
        assertFalse(RaidConfig.shouldAutoEnd(-1L, START, 1800));
    }

    @Test
    void defaultsAreSane() {
        assertTrue(RaidConfig.DEFAULT_EXTRACTION_RADIUS > 0.0D);
        assertTrue(RaidConfig.DEFAULT_EXTRACTION_SECONDS > 0);
        assertTrue(RaidConfig.DEFAULT_AUTO_END_SECONDS > 0);
    }
}
