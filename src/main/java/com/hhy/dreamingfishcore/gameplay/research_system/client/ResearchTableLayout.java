package com.hhy.dreamingfishcore.gameplay.research_system.client;

/**
 * 研究桌界面的布局解算。
 *
 * <p>刻意不依赖任何 Minecraft 类：布局全是算术，而"课题列表被下面的提交区块压住"这种事
 * 只会在特定的窗口高度下暴露（界面刚打开时服务端快照还没到、课题数为 0，面板按最小列表高度算过一遍；
 * 快照到了以后课题变成十几条，列表就画到提交区块上去了）。抽成纯计算之后，
 * 就能用单测把"任何窗口尺寸下各区块两两不重叠"这条性质钉住。</p>
 *
 * <p>所有坐标都是**屏幕绝对坐标**（原点在窗口左上角），界面拿到矩形直接画，
 * 不再自己加减 {@code panelX/panelY}，免得出现"画在这儿、点在那儿"。</p>
 *
 * <p>竖直方向自上而下固定为七段，顺序与高度都写死在 {@link #of} 里：</p>
 * <ol>
 *   <li>标题行（研究桌 / 消耗与当前经验）</li>
 *   <li>警告行（例如"经验不足…"）</li>
 *   <li>列表标题（本次课题（N 个））</li>
 *   <li>可滚动的课题列表</li>
 *   <li>提交物品解锁配方（标题 + 18×18 槽位 + 提示 + 状态）</li>
 *   <li>玩家背包（主背包 3 行 + 快捷栏 1 行）</li>
 *   <li>底部按钮行（关闭 / 开始研究 / 解锁这个配方）</li>
 * </ol>
 *
 * <p>唯一会伸缩的是课题列表：它拿走剩下的全部竖直空间，但**永远保留
 * {@link #LIST_MIN_HEIGHT} 的最小高度**。窗口再矮也只是列表变矮（它本来就能滚），
 * 不会让后面的区块往上挤——最坏情况是面板比窗口还高一点（宁可越出窗口，也不重叠）。</p>
 *
 * @param panel           面板整体矩形
 * @param header          标题 + 警告 + 列表标题这三行所占的区块
 * @param titleY          标题行文字的基线顶（第一行）
 * @param messageY        警告行文字的基线顶（第二行）
 * @param listTitleY      列表标题行文字的基线顶（第三行）
 * @param list            可滚动的课题列表矩形（内容区，不含内边距）
 * @param listRows        列表里能完整画下的行数（≥ {@link #MIN_LIST_ROWS}，滚动按它算）
 * @param submit          提交区块矩形
 * @param slot            提交槽矩形（原版 18×18）
 * @param inventory       玩家背包区块矩形
 * @param inventoryX      背包第一列槽位的左边界
 * @param inventoryGridY  主背包第一行槽位的上边界
 * @param hotbarY         快捷栏槽位的上边界
 * @param footerTop       页脚（按钮行）区块的上边界
 * @param buttons         三个按钮合起来占的矩形
 * @param buttonWidth     单个按钮的宽度
 */
public record ResearchTableLayout(Rect panel,
                                  Rect header,
                                  int titleY,
                                  int messageY,
                                  int listTitleY,
                                  Rect list,
                                  int listRows,
                                  Rect submit,
                                  Rect slot,
                                  Rect inventory,
                                  int inventoryX,
                                  int inventoryGridY,
                                  int hotbarY,
                                  int footerTop,
                                  Rect buttons,
                                  int buttonWidth) {

    /** 面板与窗口边缘的最小间距（上下左右一样）。 */
    public static final int PANEL_MARGIN = 12;
    /** 面板宽度上限：再宽也就这样，太宽反而不好读。 */
    public static final int PANEL_MAX_WIDTH = 360;
    /** 面板宽度下限：至少要放得下 9 列背包槽（162）加两边内衬，否则内容会溢出面板。 */
    public static final int PANEL_MIN_WIDTH = 200;
    /** 区块左右内衬：列表 / 提交 / 背包的底衬都从这里缩进。 */
    public static final int CONTENT_PADDING = 8;

    /** 头部三行：标题行、警告行、列表标题行。行距 12，留到 42 给列表标题行下沿一点余量。 */
    public static final int HEADER_HEIGHT = 42;
    /** 头部三行文字的偏移（相对面板顶部）。 */
    public static final int TITLE_OFFSET = 6;
    public static final int MESSAGE_OFFSET = 18;
    public static final int LIST_TITLE_OFFSET = 30;

    /** 原版槽位尺寸：18×18，其中内底 16×16 向内缩 1 像素。 */
    public static final int SLOT_SIZE = 18;
    public static final int SLOT_INNER = 16;
    /** 背包列数，与 {@code ResearchTableMenu.INVENTORY_COLUMNS} 必须一致（那边是 Minecraft 类，这里不能引用）。 */
    public static final int INVENTORY_COLUMNS = 9;
    /** 主背包行数（快捷栏另算一行）。 */
    public static final int INVENTORY_GRID_ROWS = 3;
    /** 主背包与快捷栏之间的空隙。 */
    public static final int INVENTORY_GAP = 4;
    /** 背包区块顶部内衬。 */
    public static final int INVENTORY_TOP_PADDING = 4;
    /** 背包区块总高：4（内衬）+ 3×18（主背包）+ 4（空隙）+ 18（快捷栏）= 80。 */
    public static final int INVENTORY_BLOCK_HEIGHT = INVENTORY_TOP_PADDING
            + INVENTORY_GRID_ROWS * SLOT_SIZE + INVENTORY_GAP + SLOT_SIZE;

    /**
     * 提交区块高度：标题行（+2，9 像素高）+ 槽位（+16，18 像素高）+ 槽位下方的状态行（+36）。
     * 46 正好让状态行（+36..+45）落在区块内。
     */
    public static final int SUBMIT_HEIGHT = 46;
    /** 提交标题相对区块顶部的偏移。 */
    public static final int SUBMIT_TITLE_OFFSET = 2;
    /** 槽位相对区块顶部的偏移：标题行下面一点，且 16+18=34 ≤ 46，槽位不会压到状态行。 */
    public static final int SUBMIT_SLOT_OFFSET = 16;
    /** 槽位左边的内衬。 */
    public static final int SUBMIT_SLOT_PADDING = 12;
    /** 状态行相对区块顶部的偏移。 */
    public static final int SUBMIT_STATUS_OFFSET = 36;

    /** 页脚高度：按钮 20 高，上下各留 5。 */
    public static final int FOOTER_HEIGHT = 30;
    /** 按钮相对页脚顶部的偏移。 */
    public static final int FOOTER_BUTTON_OFFSET = 5;
    public static final int BUTTON_HEIGHT = 20;
    public static final int BUTTON_GAP = 8;
    public static final int BUTTON_MAX_WIDTH = 96;
    /** 按钮行距离面板左右边缘的内衬。 */
    public static final int BUTTON_SIDE_PADDING = 12;

    /** 课题列表的行高（一行 = 16 像素图标 + 上下各一点余量）。 */
    public static final int ROW_HEIGHT = 16;
    /** 列表至少要能显示的行数：窗口再矮也要看得见两行，否则玩家不知道自己在滚什么。 */
    public static final int MIN_LIST_ROWS = 2;
    /** 列表最小高度。 */
    public static final int LIST_MIN_HEIGHT = MIN_LIST_ROWS * ROW_HEIGHT;
    /** 课题最多 15 条（服务端上限），列表高度按它封顶，免得面板无意义地高。 */
    public static final int MAX_LIST_ROWS = 15;

    /**
     * 半开区间矩形：{@code [x, x+width) × [y, y+height)}。
     *
     * <p>用半开区间是为了让"列表下沿正好贴着提交区块上沿"算作不重叠——
     * 这正是我们要的排布（相邻但不互相压住）。</p>
     */
    public record Rect(int x, int y, int width, int height) {

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        /** 是否与另一个矩形真正相交（仅仅贴边不算）。 */
        public boolean intersects(Rect other) {
            return x < other.right() && other.x < right()
                    && y < other.bottom() && other.y < bottom();
        }

        /** 是否完整包含另一个矩形。 */
        public boolean contains(Rect other) {
            return other.x >= x && other.right() <= right()
                    && other.y >= y && other.bottom() <= bottom();
        }

        /** 是否完整落在另一个矩形里（{@link #contains} 的另一种读法）。 */
        public boolean isInside(Rect outer) {
            return outer.contains(this);
        }
    }

    /** 头部 + 提交 + 背包 + 页脚：不随课题数量变化的那部分面板高度。 */
    public static int fixedHeight() {
        return HEADER_HEIGHT + SUBMIT_HEIGHT + INVENTORY_BLOCK_HEIGHT + FOOTER_HEIGHT;
    }

    /** 面板最小高度：固定区块 + 列表最小高度。窗口比它还矮时面板就不再缩了。 */
    public static int panelMinHeight() {
        return fixedHeight() + LIST_MIN_HEIGHT;
    }

    /** 给定课题数量时"理想"的面板高度（窗口够高时就用它，面板不会白白留空）。 */
    public static int desiredPanelHeight(int offerCount) {
        return fixedHeight() + desiredListRows(offerCount) * ROW_HEIGHT;
    }

    /** 理想的列表行数：受课题数量与 {@link #MAX_LIST_ROWS} 限制，但不低于 {@link #MIN_LIST_ROWS}。 */
    public static int desiredListRows(int offerCount) {
        int offers = Math.max(0, Math.min(MAX_LIST_ROWS, offerCount));
        return Math.max(MIN_LIST_ROWS, offers);
    }

    /**
     * 解算一整套布局。
     *
     * <p>竖直方向的算法（面板自身坐标，最后统一加上 {@code panelY} 变成屏幕坐标）：</p>
     * <pre>
     * wall        = 窗口高度 - 2 × PANEL_MARGIN                 // 面板能用的最大高度
     * panelHeight = wall ≥ 理想高度 ? 理想高度 : max(面板最小高度, wall)
     * listTop     = HEADER_HEIGHT
     * footerTop   = panelHeight - FOOTER_HEIGHT
     * 背包顶      = footerTop - INVENTORY_BLOCK_HEIGHT
     * 提交顶      = 背包顶 - SUBMIT_HEIGHT
     * 列表底      = 提交顶                                      // 列表拿走中间剩下的一切
     * </pre>
     *
     * <p>于是列表高度恒等于 {@code panelHeight - fixedHeight()}，
     * 且因为 {@code panelHeight ≥ panelMinHeight()}，它恒 ≥ {@link #LIST_MIN_HEIGHT}。</p>
     *
     * @param screenWidth  窗口宽度（GUI 坐标）
     * @param screenHeight 窗口高度（GUI 坐标）
     * @param offerCount   本次课题数量（快照还没到时传 0，布局照样成立）
     */
    public static ResearchTableLayout of(int screenWidth, int screenHeight, int offerCount) {
        // 面板宽度：能用多宽就用多宽（封顶 360），窗口太窄时至少给 PANEL_MIN_WIDTH，
        // 但不允许超过窗口本身，免得 panelX 变成负数。
        int availableWidth = screenWidth - 2 * PANEL_MARGIN;
        int panelWidth = availableWidth >= PANEL_MIN_WIDTH
                ? Math.min(PANEL_MAX_WIDTH, availableWidth)
                : Math.max(1, Math.min(PANEL_MIN_WIDTH, screenWidth));
        int panelX = Math.max(0, (screenWidth - panelWidth) / 2);

        int desiredRows = desiredListRows(offerCount);
        int wallHeight = screenHeight - 2 * PANEL_MARGIN;
        int desired = desiredPanelHeight(offerCount);
        // 关键：窗口矮到放不下"固定区块 + 列表最小高度"时，面板不再继续缩，
        // 而是宁可越出窗口——越出窗口只是看不见，重叠是直接画错。
        int panelHeight = desired <= wallHeight ? desired : Math.max(panelMinHeight(), wallHeight);
        int panelY = Math.max(0, (screenHeight - panelHeight) / 2);

        int titleY = panelY + TITLE_OFFSET;
        int messageY = panelY + MESSAGE_OFFSET;
        int listTitleY = panelY + LIST_TITLE_OFFSET;

        int listTop = panelY + HEADER_HEIGHT;
        int footerTop = panelY + panelHeight - FOOTER_HEIGHT;
        int inventoryTop = footerTop - INVENTORY_BLOCK_HEIGHT;
        int submitTop = inventoryTop - SUBMIT_HEIGHT;
        int listBottom = submitTop;
        int listHeight = listBottom - listTop;

        int contentWidth = panelWidth - 2 * CONTENT_PADDING;
        Rect panel = new Rect(panelX, panelY, panelWidth, panelHeight);
        Rect header = new Rect(panelX + CONTENT_PADDING, panelY, contentWidth, HEADER_HEIGHT);
        Rect list = new Rect(panelX + CONTENT_PADDING, listTop, contentWidth, listHeight);
        Rect submit = new Rect(panelX + CONTENT_PADDING, submitTop, contentWidth, SUBMIT_HEIGHT);
        Rect slot = new Rect(panelX + SUBMIT_SLOT_PADDING, submitTop + SUBMIT_SLOT_OFFSET,
                SLOT_SIZE, SLOT_SIZE);
        Rect inventory = new Rect(panelX + CONTENT_PADDING, inventoryTop, contentWidth,
                INVENTORY_BLOCK_HEIGHT);

        // 列表行数：既要放得下理想行数，也不能超过实际高度。
        // listHeight ≥ LIST_MIN_HEIGHT 恒成立，所以这里不会小于 MIN_LIST_ROWS。
        int listRows = Math.max(MIN_LIST_ROWS, Math.min(desiredRows, listHeight / ROW_HEIGHT));

        // 背包横向居中（内衬不够时靠左，绝不越出面板内衬）。
        int inventoryX = panelX + Math.max(CONTENT_PADDING,
                (panelWidth - INVENTORY_COLUMNS * SLOT_SIZE) / 2);
        int inventoryGridY = inventoryTop + INVENTORY_TOP_PADDING;
        int hotbarY = inventoryGridY + INVENTORY_GRID_ROWS * SLOT_SIZE + INVENTORY_GAP;

        int buttonWidth = Math.max(16, Math.min(BUTTON_MAX_WIDTH,
                (panelWidth - 2 * BUTTON_SIDE_PADDING - 2 * BUTTON_GAP) / 3));
        Rect buttons = new Rect(panelX + BUTTON_SIDE_PADDING, footerTop + FOOTER_BUTTON_OFFSET,
                3 * buttonWidth + 2 * BUTTON_GAP, BUTTON_HEIGHT);

        return new ResearchTableLayout(panel, header, titleY, messageY, listTitleY,
                list, listRows, submit, slot, inventory, inventoryX, inventoryGridY, hotbarY,
                footerTop, buttons, buttonWidth);
    }
}
