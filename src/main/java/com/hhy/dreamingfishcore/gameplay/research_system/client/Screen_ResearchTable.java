package com.hhy.dreamingfishcore.gameplay.research_system.client;

import com.hhy.dreamingfishcore.gameplay.research_system.ResearchMath;
import com.hhy.dreamingfishcore.gameplay.research_system.ResearchTableMenu;
import com.hhy.dreamingfishcore.gameplay.research_system.network.Packet_ResearchConfirmRequest;
import com.hhy.dreamingfishcore.gameplay.research_system.network.Packet_ResearchSubmitItemRequest;
import com.hhy.dreamingfishcore.gameplay.research_system.network.Packet_ResearchTableOpenRequest;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 研究桌界面：**自绘的容器界面**。
 *
 * <p>为什么不是 {@code AbstractContainerScreen}：研究桌原本就有一套自己的面板与课题列表
 * （滚动的课题、经验进度、按钮命中区），改成 {@code AbstractContainerScreen} 等于把这一整套
 * 推平重写。这里走的是另一条等价路线：实现原版的
 * {@link MenuAccess}（客户端就是靠它把 {@code containerMenu} 指向我们的菜单），
 * 自己画槽位、自己把鼠标点击交给
 * {@code MultiPlayerGameMode#handleInventoryMouseClick}（它会转发给菜单并同步给服务端）。
 * 于是槽位仍然是**真正的容器槽位**：内容在服务端，靠原版容器同步下发，客户端只是把它画出来。</p>
 *
 * <p>支持的交互（与"至少要能左键放入 / 取出整叠"对齐）：左键拿起/放下整叠、右键拿一半/放一个、
 * Shift + 左键在背包与提交槽之间快速移动。不支持原版的拖拽分发、双击聚拢、界面外丢出——
 * 这些都不影响"把物品放进去提交"这条主路径，且少了它们反而不会误丢物品。</p>
 *
 * <p>几处取舍：</p>
 * <ul>
 *   <li><b>先看清再花钱</b>：本次课题逐条列出（带物品图标），点「开始研究」才扣经验。
 *       课题是服务端固定下来的，关掉再开还是同一批——所以不存在"反复开关刷结果"。</li>
 *   <li><b>状态全部来自服务端</b>：经验、能不能研究、能不能提交、为什么不能提交，都是
 *       {@link ResearchTableClientCache} 里的那一份快照；服务端推一份新的就自动刷新。
 *       界面只用服务端下发的除数把"需要多少个"即时算出来显示，不参与任何结算。</li>
 *   <li><b>列表超过一屏可滚动</b>：课题最多 15 条，小窗口下会超出，用滚轮翻。</li>
 * </ul>
 *
 * <p><b>版面完全交给 {@link ResearchTableLayout}</b>：这个类只按算出来的矩形画，
 * 自己不做任何"剩下多少高度"的算术。以前这里是自己上上下下推算的，结果界面刚打开时快照还没到
 * （课题数 0，面板按最小列表高度算），快照到了以后课题变成十几条，列表就画到提交区块上去了。
 * 现在布局（含课题数）每次渲染前都会按当前的窗口尺寸与课题数量重算，这种时序问题不再存在。</p>
 */
public class Screen_ResearchTable extends Screen implements MenuAccess<ResearchTableMenu> {

    private static final int COLOR_TEXT = 0xFFE8EDF2;
    private static final int COLOR_MUTED = 0xFFA7B2BE;
    private static final int COLOR_TITLE = 0xFFFFD479;
    private static final int COLOR_PANEL = 0xF010161C;
    private static final int COLOR_BORDER = 0xFF3A4A56;
    private static final int COLOR_BUTTON = 0xFF243039;
    private static final int COLOR_BUTTON_HOVER = 0xFF32434F;
    private static final int COLOR_BUTTON_OFF = 0xFF1A2229;
    private static final int COLOR_PRIMARY = 0xFF27506B;
    private static final int COLOR_PRIMARY_HOVER = 0xFF35708F;
    private static final int COLOR_SUBMIT = 0xFF2F5B3A;
    private static final int COLOR_SUBMIT_HOVER = 0xFF3F7A4E;
    private static final int COLOR_ROW = 0xFF161E25;
    private static final int COLOR_SLOT = 0xFF0B1014;
    private static final int COLOR_SLOT_HOVER = 0xFF2A3A46;
    /** 列表 / 提交区块的底衬（半透明黑，让"这是一块区域"看得出来）。 */
    private static final int COLOR_BLOCK_BACKDROP = 0x40000000;

    /** 内容区的滚动量（像素）。 */
    private static final int SCROLL_STEP = 16;
    /** 区块内文字相对区块左边缘的内衬。 */
    private static final int TEXT_PADDING = 12;

    private final ResearchTableMenu menu;
    private final BlockPos tablePos;
    private final List<Hit> hits = new ArrayList<>();

    /**
     * 当前版面。按"窗口宽、窗口高、课题数"缓存：这三个只要没变就不必重算，
     * 一变（窗口缩放、服务端快照到达）立刻重算——这正是保证不重叠的关键。
     */
    private ResearchTableLayout cachedLayout;
    private int layoutScreenWidth = -1;
    private int layoutScreenHeight = -1;
    private int layoutOfferCount = -1;
    private int scroll;

    private record Hit(int x1, int y1, int x2, int y2, boolean enabled, Runnable action) {
    }

    public Screen_ResearchTable(ResearchTableMenu menu, Inventory inventory, Component title) {
        super(title);
        this.menu = menu;
        this.tablePos = menu.getPos();
    }

    @Override
    public ResearchTableMenu getMenu() {
        return this.menu;
    }

    @Override
    public boolean isPauseScreen() {
        // 与方块界面一致：不暂停世界，多人服务器上也不该暂停。
        return false;
    }

    @Override
    protected void init() {
        super.init();
        // 窗口尺寸可能变了（缩放 / 全屏），旧版面作废，下面第一次用到时重算。
        this.cachedLayout = null;
        // 要一份最新快照：快照里的"当前经验"是服务端算的，玩家可能在打开界面之前刚补过经验。
        // 这个请求不会重掷课题（服务端只在旧课题失效时才换一批）。
        DreamingFishCore_NetworkManager.sendToServer(new Packet_ResearchTableOpenRequest(this.tablePos));
    }

    /**
     * 版面一次算好，渲染与点击命中都用这一份，避免"画在这儿、点在那儿"。
     *
     * <p>课题数量是版面的一部分（列表要给多少高度取决于它），而快照是异步到的：
     * 界面 init 时可能还是 0 条、下一帧就变成 15 条。所以这里每次都拿当前的课题数比对，
     * 对不上就重算，绝不沿用"打开界面那一刻"的旧版面。</p>
     */
    private ResearchTableLayout layout() {
        int offers = offer().size();
        if (this.cachedLayout == null || this.layoutScreenWidth != this.width
                || this.layoutScreenHeight != this.height || this.layoutOfferCount != offers) {
            this.cachedLayout = ResearchTableLayout.of(this.width, this.height, offers);
            this.layoutScreenWidth = this.width;
            this.layoutScreenHeight = this.height;
            this.layoutOfferCount = offers;
        }
        return this.cachedLayout;
    }

    private int inventoryCountOf(ItemStack sample) {
        if (sample == null || sample.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int index = ResearchTableMenu.INVENTORY_SLOT_START; index < this.menu.slots.size(); index++) {
            ItemStack stack = this.menu.getSlot(index).getItem();
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, sample)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        ResearchTableClientCache.Snapshot snapshot = snapshot();
        ResearchTableLayout layout = layout();
        hits.clear();

        drawPanel(guiGraphics, layout);

        if (snapshot == null) {
            guiGraphics.drawString(this.font, "§7正在读取研究桌状态…",
                    layout.list().x() + 6, layout.list().y() + 2, COLOR_MUTED, false);
            drawFooter(guiGraphics, layout, null, mouseX, mouseY);
            drawCarried(guiGraphics, mouseX, mouseY);
            return;
        }

        drawHeader(guiGraphics, layout, snapshot);
        drawOfferList(guiGraphics, layout, snapshot, mouseX, mouseY);
        drawSubmitArea(guiGraphics, layout, snapshot, mouseX, mouseY);
        drawInventory(guiGraphics, layout, mouseX, mouseY);
        drawFooter(guiGraphics, layout, snapshot, mouseX, mouseY);
        drawSlotTooltip(guiGraphics, layout, mouseX, mouseY);
        drawCarried(guiGraphics, mouseX, mouseY);
    }

    private void drawPanel(GuiGraphics guiGraphics, ResearchTableLayout layout) {
        ResearchTableLayout.Rect panel = layout.panel();
        guiGraphics.fill(panel.x() - 1, panel.y() - 1, panel.right() + 1, panel.bottom() + 1, COLOR_BORDER);
        guiGraphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), COLOR_PANEL);
    }

    private void drawHeader(GuiGraphics guiGraphics, ResearchTableLayout layout,
                            ResearchTableClientCache.Snapshot snapshot) {
        int textX = layout.panel().x() + TEXT_PADDING;
        int rightEdge = layout.panel().right() - TEXT_PADDING;
        guiGraphics.drawString(this.font, "研究桌", textX, layout.titleY(), COLOR_TITLE, false);

        String cost = "消耗 " + snapshot.cost() + " 点经验";
        String have = "当前 " + snapshot.playerExperience() + " 点";
        guiGraphics.drawString(this.font, cost,
                rightEdge - this.font.width(have) - this.font.width("  ") - this.font.width(cost),
                layout.titleY(), snapshot.available() ? COLOR_TEXT : COLOR_MUTED, false);
        guiGraphics.drawString(this.font, have, rightEdge - this.font.width(have),
                layout.titleY(), snapshot.playerExperience() >= snapshot.cost() ? COLOR_TEXT : 0xFFFF8A8A, false);

        if (!snapshot.message().isEmpty()) {
            guiGraphics.drawString(this.font, snapshot.message(), textX, layout.messageY(), COLOR_MUTED, false);
        }
        String hint = snapshot.learned().isEmpty()
                ? "本次课题（" + snapshot.offer().size() + " 个）"
                : "本次学会 " + snapshot.learned().size() + " 个";
        guiGraphics.drawString(this.font, hint, textX, layout.listTitleY(), COLOR_MUTED, false);
    }

    /**
     * 课题列表：只画完整落在列表矩形里的行。
     *
     * <p>以前这里会多画一行（为了滚动时露出半行），但边界判断允许"整行起点正好落在列表下沿"，
     * 于是多出来的那一行稳稳地压在提交区块上——列表越短、课题越多越明显。
     * 现在行数由 {@link ResearchTableLayout#listRows()} 给定，且必须整行在矩形内才画。</p>
     */
    private void drawOfferList(GuiGraphics guiGraphics, ResearchTableLayout layout,
                               ResearchTableClientCache.Snapshot snapshot, int mouseX, int mouseY) {
        ResearchTableLayout.Rect list = layout.list();
        guiGraphics.fill(list.x(), list.y(), list.right(), list.bottom(), COLOR_BLOCK_BACKDROP);

        List<String> rows = snapshot.offer();
        if (rows.isEmpty()) {
            guiGraphics.drawString(this.font, "§7没有可以研究的配方了",
                    list.x() + 6, list.y() + 2, COLOR_MUTED, false);
            return;
        }

        int visible = Math.max(1, layout.listRows());
        int maxScroll = Math.max(0, (rows.size() - visible) * ResearchTableLayout.ROW_HEIGHT);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int first = scroll / ResearchTableLayout.ROW_HEIGHT;

        for (int index = first; index < rows.size() && index < first + visible; index++) {
            int rowY = list.y() + (index * ResearchTableLayout.ROW_HEIGHT) - scroll;
            // 滚动到一半时首行会露在半空：整行放不下就不画，免得画到列表外面去。
            if (rowY < list.y() || rowY + ResearchTableLayout.ROW_HEIGHT > list.bottom()) {
                continue;
            }
            if (mouseY >= rowY && mouseY < rowY + ResearchTableLayout.ROW_HEIGHT
                    && mouseX >= list.x() && mouseX <= list.right()) {
                guiGraphics.fill(list.x(), rowY, list.right(), rowY + ResearchTableLayout.ROW_HEIGHT, COLOR_ROW);
            }
            ItemStack stack = resolve(rows.get(index));
            if (!stack.isEmpty()) {
                guiGraphics.renderItem(stack, list.x() + 6, rowY);
            }
            String name = stack.isEmpty() ? rows.get(index) : stack.getHoverName().getString();
            guiGraphics.drawString(this.font, name,
                    list.x() + 6 + ResearchTableLayout.SLOT_INNER + 6, rowY + 4, COLOR_TEXT, false);
        }

        if (maxScroll > 0) {
            String more = (scroll > 0 ? "▲ " : "") + (scroll < maxScroll ? "▼ 滚轮翻页" : "");
            if (!more.isBlank()) {
                guiGraphics.drawString(this.font, more,
                        list.right() - 6 - this.font.width(more), list.bottom() - 10, COLOR_MUTED, false);
            }
        }
    }

    /**
     * 提交区：真实的槽位 + "需要多少个" + "背包里有多少" + 服务端给的原因。
     *
     * <p>"需要多少个"用服务端下发的除数当场算，所以放下物品的一瞬间就能看到数字；
     * 能不能提交仍然只认服务端那一份结论（按钮的可用状态）。</p>
     */
    private void drawSubmitArea(GuiGraphics guiGraphics, ResearchTableLayout layout,
                                ResearchTableClientCache.Snapshot snapshot, int mouseX, int mouseY) {
        ResearchTableLayout.Rect submit = layout.submit();
        ResearchTableLayout.Rect slot = layout.slot();
        int textX = layout.panel().x() + TEXT_PADDING;
        int rightEdge = layout.panel().right() - TEXT_PADDING;
        ItemStack stack = this.menu.getSubmitStack();
        int hovered = slotIndexAt(mouseX, mouseY);

        guiGraphics.fill(submit.x(), submit.y(), submit.right(), submit.bottom(), COLOR_BLOCK_BACKDROP);
        guiGraphics.drawString(this.font, "提交物品解锁配方", textX,
                submit.y() + ResearchTableLayout.SUBMIT_TITLE_OFFSET, COLOR_TEXT, false);

        String amount = stack.isEmpty()
                ? "槽位 0 ｜ 背包 0"
                : "槽位 " + stack.getCount() + " ｜ 背包 " + inventoryCountOf(stack);
        guiGraphics.drawString(this.font, amount, rightEdge - this.font.width(amount),
                submit.y() + ResearchTableLayout.SUBMIT_TITLE_OFFSET, COLOR_MUTED, false);

        drawSlot(guiGraphics, slot.x(), slot.y(), stack, hovered == ResearchTableMenu.SUBMIT_SLOT);
        guiGraphics.drawString(this.font, requiredLabel(snapshot, stack),
                slot.x() + ResearchTableLayout.SLOT_SIZE + 8, slot.y() + 5,
                stack.isEmpty() ? COLOR_MUTED : COLOR_TEXT, false);

        guiGraphics.drawString(this.font, snapshot.submitStatus(), textX,
                submit.y() + ResearchTableLayout.SUBMIT_STATUS_OFFSET,
                snapshot.canSubmit() ? 0xFF9BE8A8 : COLOR_MUTED, false);
    }

    /** "需要 16 个铁锭（堆叠上限 64 ÷ 4）"；空槽时给一句怎么用。 */
    private String requiredLabel(ResearchTableClientCache.Snapshot snapshot, ItemStack stack) {
        if (stack.isEmpty()) {
            return "把物品放进来（左键整叠，Shift 快速移动）";
        }
        int required = ResearchMath.requiredSubmitCount(stack.getMaxStackSize(), snapshot.submitDivisor());
        return "需要 " + required + " 个" + stack.getHoverName().getString()
                + "（堆叠上限 " + stack.getMaxStackSize() + " ÷ " + snapshot.submitDivisor() + "）";
    }

    private void drawInventory(GuiGraphics guiGraphics, ResearchTableLayout layout,
                              int mouseX, int mouseY) {
        int hovered = slotIndexAt(mouseX, mouseY);
        for (int row = 0; row < ResearchTableLayout.INVENTORY_GRID_ROWS; row++) {
            for (int column = 0; column < ResearchTableLayout.INVENTORY_COLUMNS; column++) {
                int index = ResearchTableMenu.INVENTORY_SLOT_START
                        + row * ResearchTableLayout.INVENTORY_COLUMNS + column;
                drawSlot(guiGraphics,
                        layout.inventoryX() + column * ResearchTableLayout.SLOT_SIZE,
                        layout.inventoryGridY() + row * ResearchTableLayout.SLOT_SIZE,
                        this.menu.getSlot(index).getItem(), hovered == index);
            }
        }
        for (int column = 0; column < ResearchTableLayout.INVENTORY_COLUMNS; column++) {
            int index = ResearchTableMenu.INVENTORY_SLOT_START
                    + ResearchTableMenu.MAIN_INVENTORY_SIZE + column;
            drawSlot(guiGraphics,
                    layout.inventoryX() + column * ResearchTableLayout.SLOT_SIZE, layout.hotbarY(),
                    this.menu.getSlot(index).getItem(), hovered == index);
        }
    }

    /**
     * 原版槽位画法：18×18 的边框底 + 向内缩 1 像素的 16×16 内底，物品（含数量角标）画在内底上。
     * 尺寸全部取自 {@link ResearchTableLayout}，界面里不再有第二个槽位尺寸。
     */
    private void drawSlot(GuiGraphics guiGraphics, int x, int y, ItemStack stack, boolean hovered) {
        guiGraphics.fill(x, y, x + ResearchTableLayout.SLOT_SIZE, y + ResearchTableLayout.SLOT_SIZE,
                COLOR_BORDER);
        guiGraphics.fill(x + 1, y + 1, x + 1 + ResearchTableLayout.SLOT_INNER,
                y + 1 + ResearchTableLayout.SLOT_INNER, hovered ? COLOR_SLOT_HOVER : COLOR_SLOT);
        if (!stack.isEmpty()) {
            guiGraphics.renderItem(stack, x + 1, y + 1);
            guiGraphics.renderItemDecorations(this.font, stack, x + 1, y + 1);
        }
    }

    /** 悬停在有东西的槽位上时显示原版物品提示（正拿着东西时不显示，免得挡住鼠标）。 */
    private void drawSlotTooltip(GuiGraphics guiGraphics, ResearchTableLayout layout,
                                int mouseX, int mouseY) {
        if (!this.menu.getCarried().isEmpty()) {
            return;
        }
        int index = slotIndexAt(mouseX, mouseY);
        if (index < 0) {
            return;
        }
        ItemStack stack = this.menu.getSlot(index).getItem();
        if (!stack.isEmpty()) {
            guiGraphics.renderTooltip(this.font, stack, mouseX, mouseY);
        }
    }

    private void drawCarried(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        ItemStack carried = this.menu.getCarried();
        if (carried.isEmpty()) {
            return;
        }
        guiGraphics.renderItem(carried, mouseX - 8, mouseY - 8);
        guiGraphics.renderItemDecorations(this.font, carried, mouseX - 8, mouseY - 8);
    }

    private void drawFooter(GuiGraphics guiGraphics, ResearchTableLayout layout,
                            ResearchTableClientCache.Snapshot snapshot, int mouseX, int mouseY) {
        boolean ready = snapshot != null;
        int width = layout.buttonWidth();
        int y = layout.buttons().y();
        int closeX = layout.buttons().x();
        int researchX = closeX + width + ResearchTableLayout.BUTTON_GAP;
        int submitX = researchX + width + ResearchTableLayout.BUTTON_GAP;

        drawButton(guiGraphics, closeX, y, width, ResearchTableLayout.BUTTON_HEIGHT, "关闭", true,
                mouseX, mouseY, this::onClose);
        drawButton(guiGraphics, researchX, y, width, ResearchTableLayout.BUTTON_HEIGHT, "开始研究",
                ready && snapshot.available(), mouseX, mouseY,
                () -> DreamingFishCore_NetworkManager.sendToServer(new Packet_ResearchConfirmRequest(tablePos)));
        drawButton(guiGraphics, submitX, y, width, ResearchTableLayout.BUTTON_HEIGHT, "解锁这个配方",
                ready && snapshot.canSubmit(), mouseX, mouseY, this::submitItem);
    }

    private void submitItem() {
        // 只发"我点了提交"：槽里是什么、够不够、要不要解锁，全部由服务端重新判定。
        DreamingFishCore_NetworkManager.sendToServer(new Packet_ResearchSubmitItemRequest(this.tablePos));
    }

    private void drawButton(GuiGraphics guiGraphics, int x, int y, int width, int height, String label,
                            boolean enabled, int mouseX, int mouseY, Runnable action) {
        boolean hovered = enabled && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        int background;
        if (!enabled) {
            background = COLOR_BUTTON_OFF;
        } else if (isResearch(label)) {
            background = hovered ? COLOR_PRIMARY_HOVER : COLOR_PRIMARY;
        } else if (isSubmit(label)) {
            background = hovered ? COLOR_SUBMIT_HOVER : COLOR_SUBMIT;
        } else {
            background = hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON;
        }
        guiGraphics.fill(x, y, x + width, y + height, background);
        guiGraphics.fill(x, y, x + width, y + 1, COLOR_BORDER);
        int textColor = enabled ? COLOR_TEXT : COLOR_MUTED;
        guiGraphics.drawString(this.font, label,
                x + (width - this.font.width(label)) / 2, y + (height - 8) / 2, textColor, false);
        if (enabled) {
            hits.add(new Hit(x, y, x + width, y + height, true, action));
        }
    }

    private boolean isResearch(String label) {
        return "开始研究".equals(label);
    }

    private boolean isSubmit(String label) {
        return "解锁这个配方".equals(label);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int index = hits.size() - 1; index >= 0; index--) {
            Hit hit = hits.get(index);
            if (hit.enabled() && mouseX >= hit.x1() && mouseX < hit.x2()
                    && mouseY >= hit.y1() && mouseY < hit.y2()) {
                hit.action().run();
                return true;
            }
        }

        // 槽位点击：左键整叠、右键一半/一个、Shift 快速移动。
        //
        // 必须走 MultiPlayerGameMode#handleInventoryMouseClick，而不是直接 menu.clicked：
        // 前者除了转发给 menu.clicked 之外，还会把 ServerboundContainerClickPacket 发给服务端。
        // 只调 menu.clicked 的话客户端会"看起来动了"，服务端却完全不知道，物品会在下一次同步时弹回去。
        int slot = slotIndexAt(mouseX, mouseY);
        if (slot >= 0 && (button == 0 || button == 1) && this.minecraft != null && this.minecraft.player != null) {
            ClickType clickType = hasShiftDown() ? ClickType.QUICK_MOVE : ClickType.PICKUP;
            this.minecraft.gameMode.handleInventoryMouseClick(
                    this.menu.containerId, slot, button, clickType, this.minecraft.player);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 鼠标下的菜单槽位下标；不在任何槽位上时返回 -1。命中区就是布局给的那几个矩形。 */
    private int slotIndexAt(double mouseX, double mouseY) {
        ResearchTableLayout layout = layout();
        if (inside(mouseX, mouseY, layout.slot())) {
            return ResearchTableMenu.SUBMIT_SLOT;
        }
        for (int row = 0; row < ResearchTableLayout.INVENTORY_GRID_ROWS; row++) {
            for (int column = 0; column < ResearchTableLayout.INVENTORY_COLUMNS; column++) {
                if (inside(mouseX, mouseY, slotAt(layout, column, row))) {
                    return ResearchTableMenu.INVENTORY_SLOT_START
                            + row * ResearchTableLayout.INVENTORY_COLUMNS + column;
                }
            }
        }
        for (int column = 0; column < ResearchTableLayout.INVENTORY_COLUMNS; column++) {
            if (inside(mouseX, mouseY, new ResearchTableLayout.Rect(
                    layout.inventoryX() + column * ResearchTableLayout.SLOT_SIZE, layout.hotbarY(),
                    ResearchTableLayout.SLOT_SIZE, ResearchTableLayout.SLOT_SIZE))) {
                return ResearchTableMenu.INVENTORY_SLOT_START
                        + ResearchTableMenu.MAIN_INVENTORY_SIZE + column;
            }
        }
        return -1;
    }

    private ResearchTableLayout.Rect slotAt(ResearchTableLayout layout, int column, int row) {
        return new ResearchTableLayout.Rect(
                layout.inventoryX() + column * ResearchTableLayout.SLOT_SIZE,
                layout.inventoryGridY() + row * ResearchTableLayout.SLOT_SIZE,
                ResearchTableLayout.SLOT_SIZE, ResearchTableLayout.SLOT_SIZE);
    }

    private static boolean inside(double mouseX, double mouseY, ResearchTableLayout.Rect rect) {
        return mouseX >= rect.x() && mouseX < rect.right() && mouseY >= rect.y() && mouseY < rect.bottom();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ResearchTableLayout.Rect list = layout().list();
        if (scrollY != 0.0D && mouseX >= list.x() && mouseX <= list.right()
                && mouseY >= list.y() && mouseY <= list.bottom()) {
            scroll -= (int) (scrollY * SCROLL_STEP);
            scroll = Math.max(0, scroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void tick() {
        super.tick();
        // 服务端已经把这个容器关掉了（走远了 / 死亡 / 被别人顶掉）：直接收掉界面。
        // 这时服务端已经按 removed() 把槽里的物品退回背包，不需要（也不能）再发关闭请求。
        if (this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.containerMenu != this.menu) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public void onClose() {
        closeContainerIfNeeded();
        super.onClose();
    }

    @Override
    public void removed() {
        // 界面被别的地方顶掉（切换界面 / 断线）时也要把容器关掉：
        // 否则服务端还以为菜单开着，槽里的物品就悬在那儿了。
        closeContainerIfNeeded();
        super.removed();
    }

    private void closeContainerIfNeeded() {
        if (this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.containerMenu == this.menu) {
            // 原版容器界面的关法：发关闭包让服务端 removed() 把槽里剩下的物品退回背包。
            this.minecraft.player.closeContainer();
        }
    }

    private ResearchTableClientCache.Snapshot snapshot() {
        ResearchTableClientCache.Snapshot snapshot = ResearchTableClientCache.get();
        // 全局只留一份快照：如果不是这张桌子的（刚看过另一张），当作还没读到。
        if (snapshot == null || !this.tablePos.equals(snapshot.pos())) {
            return null;
        }
        return snapshot;
    }

    private List<String> offer() {
        ResearchTableClientCache.Snapshot snapshot = snapshot();
        return snapshot == null ? List.of() : snapshot.offer();
    }

    private static ItemStack resolve(String itemId) {
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }
}
