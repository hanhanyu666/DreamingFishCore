package com.hhy.dreamingfishcore.gameplay.spawner_system.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置界面布局的性质测试。
 *
 * <p>界面没法用无头测试验"好不好看"，但"会不会互相压住"是纯算术，必须钉住：
 * 这条一旦破了，只有在特定的 GUI 缩放 + 奖励物品够多时才会暴露，非常难查。</p>
 */
class SpawnerConfigLayoutTest {

    @Test
    void everySupportedPanelHeightKeepsSectionsInOrder() {
        for (boolean expanded : new boolean[]{false, true}) {
            int min = SpawnerConfigLayout.minHeightFor(expanded);
            for (int height = min; height <= 600; height++) {
                SpawnerConfigLayout layout = SpawnerConfigLayout.of(height, expanded);
                int actual = Math.max(min, Math.min(SpawnerConfigLayout.PANEL_MAX_HEIGHT, height));
                String where = "expanded=" + expanded + " height=" + height + " ";

                assertTrue(layout.entityRowY() > 0, where + "实体行要在标题下面");
                assertTrue(layout.paramRowsTop() > layout.entityRowY(),
                        where + "参数行要在实体行下面");
                assertTrue(layout.rewardSectionY() >= layout.paramRowsTop()
                                + SpawnerConfigLayout.BASE_PARAM_ROWS * layout.rowHeight(),
                        where + "折叠标题要在基础参数行下面");
                assertTrue(layout.rewardRowsTop() > layout.rewardSectionY(),
                        where + "奖励参数行要在折叠标题下面");
                assertTrue(layout.rewardListTop() > layout.rewardRowsTop(),
                        where + "奖励列表要在奖励参数行下面");
                assertTrue(layout.rewardInputY() > layout.rewardListTop(),
                        where + "奖励输入行要在列表下面");
                assertTrue(layout.statusY() > layout.rewardInputY(),
                        where + "状态行要在输入行下面");
                assertTrue(layout.messageY() > layout.statusY(), where + "结果行要在状态行下面");
                assertTrue(layout.buttonsY() > layout.messageY(), where + "按钮行要在结果行下面");
                assertTrue(layout.buttonsY() + 16 <= actual,
                        where + "底部按钮不能超出面板，buttonsY=" + layout.buttonsY());
            }
        }
    }

    @Test
    void rewardListAlwaysHasRoomOrIsExplicitlyHidden() {
        for (boolean expanded : new boolean[]{false, true}) {
            for (int height = 1; height <= 600; height++) {
                SpawnerConfigLayout layout = SpawnerConfigLayout.of(height, expanded);
                assertTrue(layout.rewardListMaxRows() >= 0,
                        "行数不能为负：expanded=" + expanded + " height=" + height);
                int listRowHeight = Math.max(10, layout.rowHeight() - 2);
                int bottom = layout.rewardListTop()
                        + layout.rewardListMaxRows() * listRowHeight;
                assertTrue(bottom <= layout.rewardInputY(),
                        "奖励列表不能压到输入行：expanded=" + expanded + " height=" + height);
            }
        }
    }

    @Test
    void collapsedSectionShowsNoRewardRows() {
        SpawnerConfigLayout collapsed = SpawnerConfigLayout.of(
                SpawnerConfigLayout.PANEL_MAX_HEIGHT, false);
        assertEquals(0, collapsed.rewardListMaxRows(), "折叠时不应画出任何奖励列表行");
        // 折叠时奖励参数行与折叠标题重合，等于没有占用额外高度。
        assertEquals(collapsed.rewardSectionY() + collapsed.rowHeight(), collapsed.rewardRowsTop());
    }

    @Test
    void expandedSectionShowsSeveralRewardRowsOnAStandardPanel() {
        SpawnerConfigLayout expanded = SpawnerConfigLayout.of(
                SpawnerConfigLayout.PANEL_MAX_HEIGHT, true);
        assertTrue(expanded.rewardListMaxRows() >= 3,
                "展开后标准面板至少要能看到 3 项奖励物品，实际 " + expanded.rewardListMaxRows());
        SpawnerConfigLayout minimum = SpawnerConfigLayout.of(
                SpawnerConfigLayout.PANEL_MIN_HEIGHT_EXPANDED, true);
        assertTrue(minimum.rewardListMaxRows() >= 1,
                "展开状态的最低高度也要能看见 1 项奖励物品，否则玩家删不掉已有奖励");
    }

    @Test
    void shortPanelsTightenRowsAndDropThePresetLine() {
        SpawnerConfigLayout tall = SpawnerConfigLayout.of(SpawnerConfigLayout.PANEL_MAX_HEIGHT, true);
        SpawnerConfigLayout medium = SpawnerConfigLayout.of(320, true);
        SpawnerConfigLayout tiny = SpawnerConfigLayout.of(
                SpawnerConfigLayout.PANEL_MIN_HEIGHT_COLLAPSED, false);

        assertTrue(tall.rowHeight() >= medium.rowHeight(), "面板越矮行高不应更大");
        assertTrue(medium.rowHeight() >= tiny.rowHeight());
        assertTrue(tall.showPresets(), "高面板应当显示常用实体预设");
        assertFalse(tiny.showPresets(), "矮面板应省掉预设行，优先保证参数可见");
    }

    @Test
    void belowTheSupportedMinimumThePanelStopsShrinking() {
        assertTrue(SpawnerConfigLayout.of(10, false).equals(
                SpawnerConfigLayout.of(SpawnerConfigLayout.PANEL_MIN_HEIGHT_COLLAPSED, false)));
        assertTrue(SpawnerConfigLayout.of(150, true).equals(
                SpawnerConfigLayout.of(SpawnerConfigLayout.PANEL_MIN_HEIGHT_EXPANDED, true)));
        assertTrue(SpawnerConfigLayout.of(5000, true).equals(
                SpawnerConfigLayout.of(SpawnerConfigLayout.PANEL_MAX_HEIGHT, true)));
    }
}
