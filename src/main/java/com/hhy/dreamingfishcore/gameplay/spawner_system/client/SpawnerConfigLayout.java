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
 * @param paramRowsTop      第一批参数行的顶部
 * @param rewardSectionY    「剿灭奖励」折叠标题行
 * @param rewardRowsTop     展开后奖励参数的第一行（折叠时等于 {@code rewardSectionY}）
 * @param rewardListTop     奖励列表第一行
 * @param rewardInputY      奖励输入行
 * @param statusY           状态行
 * @param messageY          操作结果 / 诊断说明行
 * @param buttonsY          底部按钮行
 * @param rewardListMaxRows 奖励列表最多能画几行（折叠时为 0）
 */
public record SpawnerConfigLayout(int rowHeight, boolean showPresets, int entityRowY,
                                 int paramRowsTop, int rewardSectionY, int rewardRowsTop,
                                 int rewardListTop, int rewardInputY,
                                 int statusY, int messageY, int buttonsY,
                                 int rewardListMaxRows) {

    public static final int PANEL_MAX_HEIGHT = 380;
    /** 面板宽度的上下限，界面与布局共用。 */
    public static final int PANEL_MAX_WIDTH = 440;
    public static final int PANEL_MIN_WIDTH = 260;

    /**
     * 折叠奖励分区时的面板最少高度：标题 8 + 实体行 18 + 6 行参数（最紧 12px）+ 折叠标题 12
     * + 底部固定区 70 ≈ 220，留一点余量取 232。
     */
    public static final int PANEL_MIN_HEIGHT_COLLAPSED = 232;
    /** 展开奖励分区时还要多放下 3 行奖励参数 + 奖励标题 + 至少 1 行奖励 + 输入行。 */
    public static final int PANEL_MIN_HEIGHT_EXPANDED = 320;

    /** 实体行固定 18 高（要放下输入框）。 */
    private static final int ENTITY_ROW_HEIGHT = 18;
    /** 数值参数行（对应 SpawnerConfigRows 里的基础行）：检测范围、刷怪半径、每批数量、刷怪冷却、批次。 */
    public static final int BASE_VALUE_ROWS = 5;
    /** 开关行：红石控制 / 剿灭自毁 / 固定线索，三个开关挤在一行。 */
    public static final int TOGGLE_ROWS = 1;
    /** 折叠标题之前的总行数（数值行 + 开关行）。 */
    public static final int BASE_PARAM_ROWS = BASE_VALUE_ROWS + TOGGLE_ROWS;
    /** 奖励参数行：线索编号、奖励经验、奖励梦鱼币。 */
    public static final int REWARD_PARAM_ROWS = 3;
    /** 底部固定区：奖励输入行 + 状态行 + 结果行 + 按钮行。 */
    private static final int BOTTOM_BLOCK = 70;
    private static final int TITLE_OFFSET = 8;

    /** 某个折叠状态下的面板最少高度。 */
    public static int minHeightFor(boolean rewardsExpanded) {
        return rewardsExpanded ? PANEL_MIN_HEIGHT_EXPANDED : PANEL_MIN_HEIGHT_COLLAPSED;
    }

    public static SpawnerConfigLayout of(int panelHeight, boolean rewardsExpanded) {
        int height = Math.max(minHeightFor(rewardsExpanded),
                Math.min(PANEL_MAX_HEIGHT, panelHeight));
        int rowHeight = height >= 360 ? 18 : (height >= 300 ? 14 : 12);
        boolean showPresets = height >= 300;

        int entityRowY = TITLE_OFFSET + 16;
        int paramRowsTop = entityRowY + ENTITY_ROW_HEIGHT + (showPresets ? rowHeight : 0);
        int rewardSectionY = paramRowsTop + BASE_PARAM_ROWS * rowHeight;
        int rewardRowsTop = rewardSectionY + rowHeight;
        int rewardTitleY = rewardRowsTop + (rewardsExpanded ? REWARD_PARAM_ROWS * rowHeight : 0);
        int rewardListTop = rewardTitleY + rowHeight;
        int rewardInputY = height - BOTTOM_BLOCK + 16;
        int statusY = height - 40;
        int messageY = statusY + 10;
        int buttonsY = height - 18;

        int listRowHeight = Math.max(10, rowHeight - 2);
        int rewardListMaxRows = rewardsExpanded
                ? Math.max(0, (rewardInputY - 4 - rewardListTop) / listRowHeight)
                : 0;

        return new SpawnerConfigLayout(rowHeight, showPresets, entityRowY, paramRowsTop,
                rewardSectionY, rewardRowsTop, rewardListTop, rewardInputY,
                statusY, messageY, buttonsY, rewardListMaxRows);
    }
}
