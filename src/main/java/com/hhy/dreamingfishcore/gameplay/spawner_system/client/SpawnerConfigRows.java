package com.hhy.dreamingfishcore.gameplay.spawner_system.client;

import com.hhy.dreamingfishcore.gameplay.spawner_system.network.Packet_SpawnerConfigRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置界面的参数行清单与行序计算。
 *
 * <p>抽成纯逻辑是因为"第几行用哪个 Y"最容易写错：奖励分区折叠时行数会变，
 * 一旦把奖励行接到了开关行的位置上，界面就会出现两行叠在一起 —— 而这种错在无头测试里
 * 只有把行序也算成纯函数才测得到。</p>
 */
public final class SpawnerConfigRows {

    /** 一行数值参数：标签 + 输入框 + 加减按钮 + 复位。 */
    public record Row(String label, Packet_SpawnerConfigRequest.Action action,
                      int step, int defaultValue, String suffix, boolean rewardOnly) {
    }

    private static final List<Row> ALL = List.of(
            new Row("检测范围", Packet_SpawnerConfigRequest.Action.SET_DETECTION_RADIUS,
                    4, 32, " 格", false),
            new Row("刷怪半径", Packet_SpawnerConfigRequest.Action.SET_SPAWN_RADIUS,
                    1, 8, " 格", false),
            new Row("每批数量", Packet_SpawnerConfigRequest.Action.SET_SPAWN_COUNT,
                    1, 3, " 只", false),
            new Row("刷怪冷却", Packet_SpawnerConfigRequest.Action.SET_COOLDOWN,
                    100, 600, " tick", false),
            new Row("批次", Packet_SpawnerConfigRequest.Action.SET_BATCHES,
                    1, 3, " 批", false),
            new Row("线索编号", Packet_SpawnerConfigRequest.Action.SET_CLUE_ID,
                    1, 1, "（0 = 不发放）", true),
            new Row("奖励经验", Packet_SpawnerConfigRequest.Action.SET_REWARD_EXPERIENCE,
                    50, 0, "", true),
            new Row("奖励梦鱼币", Packet_SpawnerConfigRequest.Action.SET_REWARD_COINS,
                    100, 0, " 梦鱼币", true));

    public static final List<Row> ALL_ROWS = ALL;

    private SpawnerConfigRows() {
    }

    /** 当前折叠状态下可见的行（折叠时不含奖励参数行）。 */
    public static List<Row> visible(boolean rewardsExpanded) {
        List<Row> rows = new ArrayList<>();
        for (Row row : ALL) {
            if (!row.rewardOnly() || rewardsExpanded) {
                rows.add(row);
            }
        }
        return List.copyOf(rows);
    }

    /**
     * 第 {@code index} 个可见行的绝对 Y。
     *
     * <p>基础行从 {@code paramRowsTop} 开始往下排；**第一条奖励行**起跳到 {@code rewardRowsTop}
     * （奖励参数行在折叠标题下面），之后的奖励行继续按行高往下排。</p>
     */
    public static int yFor(List<Row> visibleRows, int index, SpawnerConfigLayout layout,
                           int panelY) {
        int y = panelY + layout.paramRowsTop();
        boolean switchedToRewards = false;
        for (int i = 0; i <= index && i < visibleRows.size(); i++) {
            Row row = visibleRows.get(i);
            if (!switchedToRewards && row.rewardOnly()) {
                y = panelY + layout.rewardRowsTop();
                switchedToRewards = true;
            }
            if (i < index) {
                y += layout.rowHeight();
            }
        }
        return y;
    }
}
