package com.hhy.dreamingfishcore.gameplay.spawner_system.client;

import org.junit.jupiter.api.Test;

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
        for (int height = SpawnerConfigLayout.PANEL_MIN_HEIGHT; height <= 600; height++) {
            SpawnerConfigLayout layout = SpawnerConfigLayout.of(height);
            int actual = Math.max(SpawnerConfigLayout.PANEL_MIN_HEIGHT,
                    Math.min(SpawnerConfigLayout.PANEL_MAX_HEIGHT, height));

            assertTrue(layout.entityRowY() > 0,
                    "实体行要在标题下面：height=" + height);
            assertTrue(layout.rewardsTitleY() > layout.entityRowY(),
                    "奖励标题要在实体行下面：height=" + height);
            assertTrue(layout.rewardListTop() > layout.rewardsTitleY(),
                    "奖励列表要在标题下面：height=" + height);
            assertTrue(layout.rewardInputY() > layout.rewardListTop(),
                    "奖励输入行要在列表下面：height=" + height);
            assertTrue(layout.statusY() > layout.rewardInputY(),
                    "状态行要在输入行下面：height=" + height);
            assertTrue(layout.messageY() > layout.statusY(),
                    "结果行要在状态行下面：height=" + height);
            assertTrue(layout.buttonsY() > layout.messageY(),
                    "按钮行要在结果行下面：height=" + height);
            assertTrue(layout.buttonsY() + 14 <= actual,
                    "底部按钮不能超出面板：height=" + height + " buttonsY=" + layout.buttonsY());
        }
    }

    @Test
    void rewardListAlwaysHasRoomOrIsExplicitlyEmpty() {
        for (int height = 1; height <= 600; height++) {
            SpawnerConfigLayout layout = SpawnerConfigLayout.of(height);
            assertTrue(layout.rewardListMaxRows() >= 0,
                    "行数不能为负：height=" + height);
            int listRowHeight = Math.max(10, layout.rowHeight() - 2);
            int bottom = layout.rewardListTop()
                    + layout.rewardListMaxRows() * listRowHeight;
            assertTrue(bottom <= layout.rewardInputY(),
                    "奖励列表不能压到输入行：height=" + height + " bottom=" + bottom
                            + " input=" + layout.rewardInputY());
        }
    }

    @Test
    void minimumPanelHeightStillShowsOneRewardRow() {
        SpawnerConfigLayout layout = SpawnerConfigLayout.of(SpawnerConfigLayout.PANEL_MIN_HEIGHT);
        assertTrue(layout.rewardListMaxRows() >= 1,
                "最低支持高度也必须能看见 1 条奖励物品，否则玩家删不掉已有奖励");
    }

    @Test
    void belowTheSupportedMinimumThePanelStopsShrinking() {
        // 比最低高度还矮时不再继续压缩：宁可面板比窗口高一点，也不让各行互相压住。
        SpawnerConfigLayout minimum = SpawnerConfigLayout.of(SpawnerConfigLayout.PANEL_MIN_HEIGHT);
        assertTrue(SpawnerConfigLayout.of(10).equals(minimum));
        assertTrue(SpawnerConfigLayout.of(150).equals(minimum));
    }

    @Test
    void shortPanelsTightenRowsAndDropThePresetLine() {
        SpawnerConfigLayout tall = SpawnerConfigLayout.of(SpawnerConfigLayout.PANEL_MAX_HEIGHT);
        SpawnerConfigLayout medium = SpawnerConfigLayout.of(320);
        SpawnerConfigLayout tiny = SpawnerConfigLayout.of(200);

        assertTrue(tall.rowHeight() >= medium.rowHeight(), "面板越矮行高不应更大");
        assertTrue(medium.rowHeight() >= tiny.rowHeight());
        assertTrue(tall.showPresets(), "高面板应当显示常用实体预设");
        assertFalse(tiny.showPresets(), "矮面板应省掉预设行，优先保证参数可见");

        // 面板高被夹取到上下限：传再离谱的值也不能算出越界布局。
        assertTrue(SpawnerConfigLayout.of(10).equals(SpawnerConfigLayout.of(200)));
        assertTrue(SpawnerConfigLayout.of(5000).equals(
                SpawnerConfigLayout.of(SpawnerConfigLayout.PANEL_MAX_HEIGHT)));
    }

    @Test
    void tallPanelsShowSeveralRewardRows() {
        assertTrue(SpawnerConfigLayout.of(SpawnerConfigLayout.PANEL_MAX_HEIGHT)
                .rewardListMaxRows() >= 3, "标准面板至少要能看到 3 条奖励物品");
    }
}
