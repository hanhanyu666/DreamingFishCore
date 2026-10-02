package com.hhy.dreamingfishcore.gameplay.research_system.client;

import com.hhy.dreamingfishcore.gameplay.research_system.ResearchTableMenu;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 研究桌界面布局的性质测试。
 *
 * <p>界面没法用无头测试验"好不好看"，但"会不会互相压住"是纯算术，必须钉住：
 * 这条一旦破了，只有在特定的窗口高度 + 课题数量下才会暴露（历史上就是"界面刚打开时快照还没到、
 * 面板按最小列表高度算过一遍，快照到了以后列表画到提交区块上"），非常难查。</p>
 *
 * <p>所以在每个支持的窗口尺寸下都断言：列表、提交区块、槽位、背包、按钮两两不重叠、
 * 顺序自上而下、列表永远留得住最小高度。854×480 是原版 GUI 的最小尺寸。</p>
 */
class ResearchTableLayoutTest {

    /** 原版最小 GUI 尺寸 + 两个常见分辨率。 */
    private static final int[][] SCREEN_SIZES = {{854, 480}, {1280, 720}, {1920, 1080}};
    /** 课题数量：0 = 快照还没到；15 = 服务端上限。 */
    private static final int[] OFFER_COUNTS = {0, 1, 2, 5, 10, 15};

    /** 互相之间绝对不能重叠的区块（槽位是提交区块的一部分，单独按"包含关系"验，见各用例）。 */
    private static Map<String, ResearchTableLayout.Rect> blocks(ResearchTableLayout layout) {
        Map<String, ResearchTableLayout.Rect> blocks = new LinkedHashMap<>();
        blocks.put("列表", layout.list());
        blocks.put("提交区块", layout.submit());
        blocks.put("玩家背包", layout.inventory());
        blocks.put("按钮行", layout.buttons());
        return blocks;
    }

    private static void assertBlocksApart(ResearchTableLayout layout, String where) {
        Map<String, ResearchTableLayout.Rect> blocks = blocks(layout);
        for (Map.Entry<String, ResearchTableLayout.Rect> left : blocks.entrySet()) {
            for (Map.Entry<String, ResearchTableLayout.Rect> right : blocks.entrySet()) {
                if (left == right) {
                    continue;
                }
                assertFalse(left.getValue().intersects(right.getValue()),
                        where + left.getKey() + " 与 " + right.getKey() + " 重叠："
                                + left.getValue() + " / " + right.getValue());
            }
        }
        // 槽位是提交区块自己的一部分（下面的 contains 断言），所以只验它与**其它**区块不相交。
        for (Map.Entry<String, ResearchTableLayout.Rect> other : blocks.entrySet()) {
            if ("提交区块".equals(other.getKey())) {
                continue;
            }
            assertFalse(layout.slot().intersects(other.getValue()),
                    where + "提交槽与" + other.getKey() + " 重叠："
                            + layout.slot() + " / " + other.getValue());
        }
        assertTrue(layout.submit().contains(layout.slot()),
                where + "提交槽必须画在提交区块里：" + layout.slot() + " / " + layout.submit());
    }

    /** 竖直顺序：头部 < 列表 < 提交 < 背包 < 按钮，且列表行数不能超出列表矩形。 */
    private static void assertVerticalOrder(ResearchTableLayout layout, String where) {
        assertTrue(layout.header().bottom() <= layout.list().y(),
                where + "列表标题行不能压住列表：" + layout.header() + " / " + layout.list());
        assertTrue(layout.list().bottom() <= layout.submit().y(),
                where + "提交区块不能画到列表上：" + layout.list() + " / " + layout.submit());
        assertTrue(layout.submit().bottom() <= layout.inventory().y(),
                where + "背包不能画到提交区块上：" + layout.submit() + " / " + layout.inventory());
        assertTrue(layout.inventory().bottom() <= layout.buttons().y(),
                where + "按钮不能画到背包上：" + layout.inventory() + " / " + layout.buttons());
        assertTrue(layout.list().height() >= ResearchTableLayout.LIST_MIN_HEIGHT,
                where + "列表高度不能小于最小值 " + ResearchTableLayout.LIST_MIN_HEIGHT
                        + "，实际 " + layout.list().height());
        assertTrue(layout.listRows() >= ResearchTableLayout.MIN_LIST_ROWS,
                where + "列表至少要能画 " + ResearchTableLayout.MIN_LIST_ROWS + " 行，实际 " + layout.listRows());
        assertTrue(layout.listRows() * ResearchTableLayout.ROW_HEIGHT <= layout.list().height(),
                where + "画出来的行不能超出列表矩形：行数 " + layout.listRows()
                        + " × " + ResearchTableLayout.ROW_HEIGHT + " > 高度 " + layout.list().height());
    }

    @Test
    void everySupportedWindowSizeKeepsTheBlocksApart() {
        for (int[] size : SCREEN_SIZES) {
            for (int offers : OFFER_COUNTS) {
                ResearchTableLayout layout = ResearchTableLayout.of(size[0], size[1], offers);
                String where = size[0] + "×" + size[1] + " / " + offers + " 个课题：";
                assertBlocksApart(layout, where);
                assertVerticalOrder(layout, where);

                // 窗口足够大：面板完整落在窗口里，不能越界。
                assertTrue(layout.panel().x() >= 0 && layout.panel().y() >= 0,
                        where + "面板左上角越界：" + layout.panel());
                assertTrue(layout.panel().right() <= size[0] && layout.panel().bottom() <= size[1],
                        where + "面板右下角越界：" + layout.panel());

                // 背包 9 列必须完整落在背包区块里（否则最后一列会画到面板外）。
                assertTrue(layout.inventoryX() >= layout.inventory().x()
                                && layout.inventoryX()
                                + ResearchTableLayout.INVENTORY_COLUMNS * ResearchTableLayout.SLOT_SIZE
                                <= layout.inventory().right(),
                        where + "背包列超出区块：x=" + layout.inventoryX() + " 区块=" + layout.inventory());
                assertTrue(layout.hotbarY() + ResearchTableLayout.SLOT_SIZE <= layout.inventory().bottom(),
                        where + "快捷栏超出背包区块：hotbarY=" + layout.hotbarY()
                                + " 区块=" + layout.inventory());

                // 按钮行要在面板内、且三个按钮都放得下。
                assertTrue(layout.buttons().right() <= layout.panel().right(),
                        where + "按钮行超出面板：" + layout.buttons() + " / " + layout.panel());
                assertTrue(layout.buttons().bottom() <= layout.panel().bottom(),
                        where + "按钮行超出面板底部：" + layout.buttons() + " / " + layout.panel());
                assertTrue(layout.buttonWidth() > 0, where + "按钮宽度必须为正");
            }
        }
    }

    @Test
    void noWindowHeightCanMakeTheBlocksOverlap() {
        // 逐像素扫高度：这是"窗口再矮也不会压住"的核心性质。
        // 宽度取原版最小 854，课题取服务端上限 15（列表最需要空间的情况）。
        for (int height = 1; height <= 1400; height++) {
            ResearchTableLayout layout = ResearchTableLayout.of(854, height, 15);
            String where = "854×" + height + "：";
            assertBlocksApart(layout, where);
            assertVerticalOrder(layout, where);
        }
        // 同样的扫描换成"只有几条课题"，确认列表少的时候也不会撞上。
        for (int height = 1; height <= 1400; height++) {
            ResearchTableLayout layout = ResearchTableLayout.of(854, height, 3);
            assertBlocksApart(layout, "854×" + height + " / 3 个课题：");
            assertVerticalOrder(layout, "854×" + height + " / 3 个课题：");
        }
    }

    @Test
    void shortWindowSqueezesTheListInsteadOfOverlappingIt() {
        // 窗口 400 高：固定区块（头部 42 + 提交 46 + 背包 80 + 页脚 30 = 198）+ 上下边距 24 之后
        // 只剩 178 给列表 —— 15 条课题放不下，列表被压到 11 行（178 ÷ 16），必须能滚、且不越界。
        ResearchTableLayout squeezed = ResearchTableLayout.of(854, 400, 15);
        assertEquals(376, squeezed.panel().height(), "400 - 2 × 12 = 376");
        assertEquals(178, squeezed.list().height(), "376 - 198 = 178");
        assertEquals(11, squeezed.listRows(), "178 ÷ 16 = 11 行");
        assertTrue(squeezed.listRows() < 15, "15 条课题在 400 高的窗口里必须退化成「显示不全、可滚动」");
        assertBlocksApart(squeezed, "854×400 / 15 个课题：");
        assertVerticalOrder(squeezed, "854×400 / 15 个课题：");

        // 临界高度 269：剩余空间 245 - 198 = 47 只够 2 行（47 ÷ 16 = 2），列表正好退化到最小值，
        // 面板还完整落在窗口里。
        ResearchTableLayout boundary = ResearchTableLayout.of(854, 269, 15);
        assertEquals(245, boundary.panel().height(), "269 - 2 × 12 = 245");
        assertEquals(47, boundary.list().height(), "245 - 198 = 47");
        assertEquals(ResearchTableLayout.MIN_LIST_ROWS, boundary.listRows(), "47 ÷ 16 = 2，正好最小值");
        assertBlocksApart(boundary, "854×269 / 15 个课题：");
        assertVerticalOrder(boundary, "854×269 / 15 个课题：");

        // 再矮：剩余空间连最小列表高度都不够，列表**钉在最小值**上，
        // 面板停在不重叠所需的最小高度（宁可越出窗口一点，也不让区块互相压住）。
        for (int height : new int[]{253, 240, 200, 100}) {
            ResearchTableLayout layout = ResearchTableLayout.of(854, height, 15);
            String where = "854×" + height + "：";
            assertEquals(ResearchTableLayout.MIN_LIST_ROWS, layout.listRows(),
                    where + "列表必须退化到最小行数");
            assertEquals(ResearchTableLayout.LIST_MIN_HEIGHT, layout.list().height(),
                    where + "列表必须退化到最小高度");
            assertEquals(ResearchTableLayout.panelMinHeight(), layout.panel().height(),
                    where + "面板必须停在不重叠所需的最小高度");
            assertBlocksApart(layout, where);
            assertVerticalOrder(layout, where);
        }
    }

    @Test
    void theSubmitSlotIsTheVanillaEighteenPixelSquare() {
        for (int[] size : SCREEN_SIZES) {
            ResearchTableLayout layout = ResearchTableLayout.of(size[0], size[1], 10);
            String where = size[0] + "×" + size[1] + "：";
            assertEquals(ResearchTableLayout.SLOT_SIZE, layout.slot().width(), where + "槽位宽度应是原版 18");
            assertEquals(ResearchTableLayout.SLOT_SIZE, layout.slot().height(), where + "槽位高度应是原版 18");
            assertEquals(18, layout.slot().width(), where + "原版槽位就是 18×18");
            assertEquals(16, ResearchTableLayout.SLOT_INNER, "内底 16×16，物品画在 +1 的位置");
            // 槽位整块在提交区块内部：标题行下面、状态行上面。
            assertTrue(layout.slot().y() >= layout.submit().y() + ResearchTableLayout.SUBMIT_TITLE_OFFSET,
                    where + "槽位要在「提交物品解锁配方」标题下面");
            assertTrue(layout.slot().bottom() <= layout.submit().y() + ResearchTableLayout.SUBMIT_STATUS_OFFSET,
                    where + "槽位不能压到状态行");
        }
    }

    @Test
    void listTakesEveryPixelBetweenTheHeaderAndTheSubmitBlock() {
        for (int offers : OFFER_COUNTS) {
            ResearchTableLayout layout = ResearchTableLayout.of(1280, 720, offers);
            String where = "1280×720 / " + offers + " 个课题：";
            assertEquals(layout.panel().y() + ResearchTableLayout.HEADER_HEIGHT, layout.list().y(),
                    where + "列表顶 = 面板顶 + 头部高度");
            assertEquals(layout.submit().y(), layout.list().bottom(),
                    where + "列表底 = 提交区块顶（中间不留空、也不重叠）");
            assertEquals(layout.panel().height() - ResearchTableLayout.fixedHeight(), layout.list().height(),
                    where + "列表高度 = 面板高度 - 固定区块高度");
        }
    }

    @Test
    void eightHundredFiftyFourByFourHundredEightyNumbersAreAsComputed() {
        // 854×480（原版最小 GUI）逐个数一遍，方便回归时一眼看出哪个数变了。
        ResearchTableLayout layout = ResearchTableLayout.of(854, 480, 15);
        assertEquals(360, layout.panel().width(), "面板宽封顶 360");
        assertEquals(247, layout.panel().x(), "(854 - 360) ÷ 2");
        assertEquals(438, layout.panel().height(), "198（固定）+ 15 × 16（列表）");
        assertEquals(21, layout.panel().y(), "(480 - 438) ÷ 2");
        assertEquals(21 + 42, layout.list().y(), "列表顶 = 面板顶 + 42");
        assertEquals(303, layout.submit().y(), "列表底 = 提交区块顶");
        assertEquals(240, layout.list().height(), "15 × 16，15 条课题全放得下");
        assertEquals(15, layout.listRows());
        assertEquals(349, layout.inventory().y(), "页脚顶 429 - 背包 80");
        assertEquals(429, layout.footerTop(), "面板底 459 - 页脚 30");
        assertEquals(346, layout.inventoryX(), "背包 9 列居中：(360 - 162) ÷ 2 = 99");
        assertEquals(96, layout.buttonWidth(), "按钮宽封顶 96");
        assertEquals(304, layout.buttons().width(), "3 × 96 + 2 × 8");
    }

    @Test
    void emptyOfferListStillReservesRoomForThePlaceholder() {
        ResearchTableLayout layout = ResearchTableLayout.of(1280, 720, 0);
        // 快照还没到时也要有一块地方写「正在读取研究桌状态…」/「没有可以研究的配方了」。
        assertEquals(ResearchTableLayout.MIN_LIST_ROWS, layout.listRows());
        assertTrue(layout.list().height() >= ResearchTableLayout.LIST_MIN_HEIGHT,
                "空列表也要留住最小高度：" + layout.list().height());
        assertEquals(ResearchTableLayout.panelMinHeight(), layout.panel().height(),
                "没有课题时面板收缩到最小高度，不白白占屏幕");
    }

    @Test
    void desiredListRowsFollowsTheOfferCountWithinLimits() {
        assertEquals(ResearchTableLayout.MIN_LIST_ROWS, ResearchTableLayout.desiredListRows(0));
        assertEquals(ResearchTableLayout.MIN_LIST_ROWS, ResearchTableLayout.desiredListRows(2));
        assertEquals(3, ResearchTableLayout.desiredListRows(3));
        assertEquals(ResearchTableLayout.MAX_LIST_ROWS, ResearchTableLayout.desiredListRows(15));
        assertEquals(ResearchTableLayout.MAX_LIST_ROWS, ResearchTableLayout.desiredListRows(999),
                "课题再多也不许把面板撑得更高（服务端上限 15）");
        assertEquals(ResearchTableLayout.fixedHeight() + 15 * ResearchTableLayout.ROW_HEIGHT,
                ResearchTableLayout.desiredPanelHeight(15));
    }

    @Test
    void layoutMirrorsTheMenuSlotGrid() {
        // 布局类刻意不引用任何 Minecraft 类（菜单是 AbstractContainerMenu 的子类），
        // 所以背包的列数 / 主背包行数在这里各写了一份常量；这条断言保证两份不会走散。
        assertEquals(ResearchTableMenu.INVENTORY_COLUMNS, ResearchTableLayout.INVENTORY_COLUMNS,
                "背包列数必须和菜单一致");
        assertEquals(ResearchTableMenu.MAIN_INVENTORY_ROWS, ResearchTableLayout.INVENTORY_GRID_ROWS,
                "主背包行数必须和菜单一致");
    }

    @Test
    void theReportedWindowShowsTwoListRowsAboveTheSubmitBlock() {
        // 用户截图那次的实际 GUI 尺寸：1471×937 的窗口在 GUI 缩放 4 下就是 367×234。
        // 旧版在这里会把"多画的那一行"从列表底沿（也就是提交区块顶）往下画出去，
        // 于是「提交物品解锁配方」标题和 18×18 槽位正好压在那一行上。
        ResearchTableLayout layout = ResearchTableLayout.of(367, 234, 10);
        assertEquals(230, layout.panel().height(), "固定 198 + 列表最小 32");
        assertEquals(2, layout.listRows(), "只剩两行可画（最小行数）");
        assertEquals(44, layout.list().y(), "面板顶 2 + 头部 42");
        assertEquals(76, layout.submit().y(), "44 + 2 × 16 = 列表底，正好是提交区块顶");
        assertEquals(layout.list().bottom(), layout.submit().y(), "列表底沿贴着提交区块，不越界");
        assertFalse(layout.slot().intersects(layout.list()), "槽位绝不能压到列表上");
        assertTrue(layout.slot().y() >= layout.submit().y() + ResearchTableLayout.SUBMIT_SLOT_OFFSET,
                "槽位在提交区块内部：" + layout.slot() + " / " + layout.submit());
        assertBlocksApart(layout, "367×234 / 10 个课题：");
        assertVerticalOrder(layout, "367×234 / 10 个课题：");
    }

    @Test
    void tallWindowsAreCenteredAndDoNotGrowPastTheContentHeight() {
        ResearchTableLayout tall = ResearchTableLayout.of(1920, 1080, 15);
        assertEquals(ResearchTableLayout.desiredPanelHeight(15), tall.panel().height(),
                "窗口够高时面板按内容收缩，不留大片空白");
        assertEquals((1920 - tall.panel().width()) / 2, tall.panel().x(), "水平居中");
        assertEquals((1080 - tall.panel().height()) / 2, tall.panel().y(), "竖直居中");
        // 最矮的可滚动窗口：刚好放下理想高度时不应触发压缩。
        int ideal = ResearchTableLayout.desiredPanelHeight(15);
        ResearchTableLayout exact = ResearchTableLayout.of(854, ideal + 24, 15);
        assertEquals(ideal, exact.panel().height());
        assertEquals(15, exact.listRows());
    }
}
