package com.hhy.dreamingfishcore.gameplay.spawner_system.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 参数行的行序与输入解析。
 *
 * <p>这里锁的是"展开奖励分区后，奖励行必须从折叠标题下面开始"这条规则 ——
 * 它曾经写错过一次（奖励行直接接在开关行后面，两行叠在一起）。</p>
 */
class SpawnerConfigRowsTest {

    @Test
    void collapsedRowsOnlyContainBaseParameters() {
        List<SpawnerConfigRows.Row> rows = SpawnerConfigRows.visible(false);
        assertEquals(SpawnerConfigLayout.BASE_VALUE_ROWS, rows.size(),
                "折叠时只应有基础参数行：" + rows.stream().map(SpawnerConfigRows.Row::label).toList());
        assertTrue(rows.stream().noneMatch(SpawnerConfigRows.Row::rewardOnly));
    }

    @Test
    void expandedRowsAppendRewardParameters() {
        List<SpawnerConfigRows.Row> rows = SpawnerConfigRows.visible(true);
        assertEquals(SpawnerConfigLayout.BASE_VALUE_ROWS + SpawnerConfigLayout.REWARD_PARAM_ROWS,
                rows.size());
        assertTrue(rows.get(rows.size() - 1).rewardOnly(), "最后几行应当是奖励参数");
        assertEquals("线索编号", rows.get(SpawnerConfigLayout.BASE_VALUE_ROWS).label());
    }

    @Test
    void rewardRowsStartBelowTheFoldHeaderAndNeverOverlapBaseRows() {
        SpawnerConfigLayout layout = SpawnerConfigLayout.of(
                SpawnerConfigLayout.PANEL_MAX_HEIGHT, true);
        List<SpawnerConfigRows.Row> rows = SpawnerConfigRows.visible(true);
        int panelY = 0;

        int previousY = Integer.MIN_VALUE;
        for (int index = 0; index < rows.size(); index++) {
            int y = SpawnerConfigRows.yFor(rows, index, layout, panelY);
            assertTrue(y > previousY, "行必须严格往下排：index=" + index + " y=" + y);
            previousY = y;
        }

        // 第一条奖励行正好落在奖励参数区顶部，而不是紧跟在基础参数后面。
        int firstReward = SpawnerConfigLayout.BASE_VALUE_ROWS;
        assertEquals(layout.rewardRowsTop() + panelY,
                SpawnerConfigRows.yFor(rows, firstReward, layout, panelY),
                "第一条奖励行必须从折叠标题下面开始");
        // 最后一条数值行在开关行上面（开关行不在这份清单里，它占折叠标题前的那一行）。
        int lastValueRowY = SpawnerConfigRows.yFor(rows, firstReward - 1, layout, panelY);
        assertEquals(layout.paramRowsTop() + (SpawnerConfigLayout.BASE_VALUE_ROWS - 1)
                        * layout.rowHeight() + panelY, lastValueRowY,
                "最后一条数值行应当紧跟在前几行后面");
        assertTrue(lastValueRowY < layout.rewardSectionY() - layout.rowHeight() + panelY,
                "数值行不能压到开关行");
    }

    @Test
    void collapsedRowsLineUpWithTheBaseSection() {
        SpawnerConfigLayout layout = SpawnerConfigLayout.of(
                SpawnerConfigLayout.PANEL_MIN_HEIGHT_COLLAPSED, false);
        List<SpawnerConfigRows.Row> rows = SpawnerConfigRows.visible(false);
        int first = SpawnerConfigRows.yFor(rows, 0, layout, 0);
        int last = SpawnerConfigRows.yFor(rows, rows.size() - 1, layout, 0);
        assertEquals(layout.paramRowsTop(), first);
        assertEquals(layout.paramRowsTop()
                + (rows.size() - 1) * layout.rowHeight(), last);
        assertTrue(last < layout.rewardSectionY(), "基础行不能越过折叠标题");
    }

    @Test
    void amountParsingRejectsBlankAndGarbage() {
        assertEquals(Integer.valueOf(4), SpawnerConfigInput.parseAmount("4"));
        assertEquals(Integer.valueOf(32), SpawnerConfigInput.parseAmount(" 32 "));
        assertEquals(Integer.valueOf(-5), SpawnerConfigInput.parseAmount("-5"),
                "负数照原样提交，由服务端夹取到合法范围");
        assertNull(SpawnerConfigInput.parseAmount(""));
        assertNull(SpawnerConfigInput.parseAmount("   "));
        assertNull(SpawnerConfigInput.parseAmount("abc"));
        assertNull(SpawnerConfigInput.parseAmount(null));
        assertFalse(SpawnerConfigRows.ALL_ROWS.isEmpty());
    }
}
