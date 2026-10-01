package com.hhy.dreamingfishcore.gameplay.spawner_system.client;

/**
 * 刷怪箱配置界面的布局解算。
 *
 * <p>刻意不依赖任何 Minecraft 类：布局全是算术，而"在小面板上各行互相压住"这种事
 * 只能靠人眼在极端 GUI 缩放下发现 —— 抽成纯计算之后就能用单测把不重叠这条性质钉住。</p>
 *
 * <p>所有 Y 坐标都相对面板顶部，界面绘制时加上 {@code panelY}。</p>
 *
 * @param rowHeight         行高（面板越矮排得越紧）
 * @param showPresets       是否显示实体预设行（面板太矮时省掉，优先保证参数可见）
 * @param entityRowY        实体 id 输入行
 * @param rewardsTitleY     奖励物品标题行
 * @param rewardListTop     奖励列表第一行
 * @param rewardInputY      奖励输入行（输入框 + 添加按钮）
 * @param statusY           状态行
 * @param messageY          操作结果行
 * @param buttonsY          底部按钮行
 * @param rewardListMaxRows 奖励列表最多能画几行
 */
public record SpawnerConfigLayout(int rowHeight, boolean showPresets, int entityRowY,
                                 int rewardsTitleY, int rewardListTop, int rewardInputY,
                                 int statusY, int messageY, int buttonsY,
                                 int rewardListMaxRows) {

    public static final int PANEL_MAX_HEIGHT = 380;
    /**
     * 面板最少要多高。
     *
     * <p>这个下限是算出来的：标题 8 + 实体行 18 + 9 行参数（最紧 12px/行）+ 奖励标题 12 +
     * 至少 1 行奖励 + 底部固定区 70 ≈ 232。比这更矮就装不下所有控件了，
     * 所以界面宁可让面板比窗口略高一点，也不让各行互相压住。</p>
     */
    public static final int PANEL_MIN_HEIGHT = 232;
    /** 面板宽度的上下限，界面与布局共用。 */
    public static final int PANEL_MAX_WIDTH = 440;
    public static final int PANEL_MIN_WIDTH = 260;

    /** 实体行固定 18 高（要放下输入框）。 */
    private static final int ENTITY_ROW_HEIGHT = 18;
    /** 参数行数：检测范围、刷怪半径、每批数量、刷怪冷却、批次、三个开关、线索编号、奖励经验、奖励梦鱼币。 */
    private static final int PARAMETER_ROWS = 9;
    /** 底部固定区：奖励输入行 + 状态行 + 结果行 + 按钮行。 */
    private static final int BOTTOM_BLOCK = 70;
    private static final int TITLE_OFFSET = 8;

    public static SpawnerConfigLayout of(int panelHeight) {
        int height = Math.max(PANEL_MIN_HEIGHT, Math.min(PANEL_MAX_HEIGHT, panelHeight));
        int rowHeight = height >= 360 ? 18 : (height >= 300 ? 14 : 12);
        boolean showPresets = height >= 300;

        int entityRowY = TITLE_OFFSET + 16;
        int rewardsTitleY = entityRowY + ENTITY_ROW_HEIGHT
                + (showPresets ? rowHeight : 0)
                + PARAMETER_ROWS * rowHeight;
        int rewardListTop = rewardsTitleY + rowHeight;
        int rewardInputY = height - BOTTOM_BLOCK + 16;
        int statusY = height - 40;
        int messageY = statusY + 10;
        int buttonsY = height - 18;

        int listRowHeight = Math.max(10, rowHeight - 2);
        int rewardListMaxRows = Math.max(0, (rewardInputY - 4 - rewardListTop) / listRowHeight);

        return new SpawnerConfigLayout(rowHeight, showPresets, entityRowY, rewardsTitleY,
                rewardListTop, rewardInputY, statusY, messageY, buttonsY, rewardListMaxRows);
    }
}
