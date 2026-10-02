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
 */
public class Screen_ResearchTable extends Screen implements MenuAccess<ResearchTableMenu> {

    private static final int PANEL_MAX_WIDTH = 360;
    private static final int PANEL_MARGIN = 12;
    /** 标题 + 状态一句话 + 列表标题，三行。 */
    private static final int HEADER_HEIGHT = 42;
    private static final int FOOTER_HEIGHT = 30;
    /** 提交区：一行说明、一行"槽位 + 需要多少个"、一行服务端给的原因。 */
    private static final int SUBMIT_HEIGHT = 50;
    private static final int ROW_HEIGHT = 16;
    private static final int ICON_SIZE = 16;
    private static final int SLOT_SIZE = 18;
    /** 玩家背包：主背包 3 行 + 快捷栏 1 行，中间留 4 像素。 */
    private static final int INVENTORY_ROWS = 4;
    private static final int INVENTORY_HEIGHT = INVENTORY_ROWS * SLOT_SIZE + 4;
    private static final int INVENTORY_BLOCK_HEIGHT = INVENTORY_HEIGHT + 4;
    /** 没有课题时也要留一点高度，免得面板缩成一条线。 */
    private static final int MIN_LIST_ROWS = 2;
    private static final int LIST_PADDING = 4;

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

    private static final int BUTTON_MAX_WIDTH = 96;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 8;
    /** 内容区的滚动量（像素）。 */
    private static final int SCROLL_STEP = 16;

    private final ResearchTableMenu menu;
    private final BlockPos tablePos;
    private final List<Hit> hits = new ArrayList<>();

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int listTop;
    private int listBottom;
    private int submitTop;
    private int inventoryTop;
    private int inventoryX;
    private int footerTop;
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
        computeLayout();
        // 要一份最新快照：快照里的"当前经验"是服务端算的，玩家可能在打开界面之前刚补过经验。
        // 这个请求不会重掷课题（服务端只在旧课题失效时才换一批）。
        DreamingFishCore_NetworkManager.sendToServer(new Packet_ResearchTableOpenRequest(this.tablePos));
    }

    /**
     * 版面一次算好，渲染与点击命中都用这一份，避免"画在这儿、点在那儿"。
     *
     * <p>自下而上排：页脚按钮 → 玩家背包 → 提交区 → 剩下的都给课题列表。
     * 窗口太矮时先压缩的也是列表（它本来就能滚），其它区块保持完整。</p>
     */
    private void computeLayout() {
        int rows = Math.max(MIN_LIST_ROWS, Math.min(maxRows(), offer().size()));
        panelWidth = Math.min(PANEL_MAX_WIDTH, this.width - PANEL_MARGIN * 2);
        int desiredHeight = HEADER_HEIGHT + rows * ROW_HEIGHT
                + SUBMIT_HEIGHT + INVENTORY_BLOCK_HEIGHT + FOOTER_HEIGHT;
        panelHeight = Math.min(desiredHeight, this.height - PANEL_MARGIN * 2);
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;

        footerTop = panelY + panelHeight - FOOTER_HEIGHT;
        inventoryTop = footerTop - INVENTORY_BLOCK_HEIGHT;
        submitTop = inventoryTop - SUBMIT_HEIGHT;
        listTop = panelY + HEADER_HEIGHT;
        listBottom = Math.max(listTop + ROW_HEIGHT, submitTop);
        inventoryX = panelX + Math.max(LIST_PADDING, (panelWidth - ResearchTableMenu.INVENTORY_COLUMNS * SLOT_SIZE) / 2);
    }

    private int maxRows() {
        return Math.max(MIN_LIST_ROWS,
                (this.height - PANEL_MARGIN * 2 - HEADER_HEIGHT - SUBMIT_HEIGHT
                        - INVENTORY_BLOCK_HEIGHT - FOOTER_HEIGHT) / ROW_HEIGHT);
    }

    private int submitSlotX() {
        return panelX + 12;
    }

    private int submitSlotY() {
        return submitTop + 14;
    }

    private int inventoryGridY() {
        return inventoryTop + 4;
    }

    private int hotbarY() {
        return inventoryGridY() + 3 * SLOT_SIZE + 4;
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
        hits.clear();

        drawPanel(guiGraphics);

        if (snapshot == null) {
            guiGraphics.drawString(this.font, "§7正在读取研究桌状态…", panelX + 12, listTop, COLOR_MUTED, false);
            drawFooter(guiGraphics, null, mouseX, mouseY);
            drawCarried(guiGraphics, mouseX, mouseY);
            return;
        }

        drawHeader(guiGraphics, snapshot);
        drawOfferList(guiGraphics, snapshot, mouseX, mouseY);
        drawSubmitArea(guiGraphics, snapshot, mouseX, mouseY);
        drawInventory(guiGraphics, mouseX, mouseY);
        drawFooter(guiGraphics, snapshot, mouseX, mouseY);
        drawSlotTooltip(guiGraphics, mouseX, mouseY);
        drawCarried(guiGraphics, mouseX, mouseY);
    }

    private void drawPanel(GuiGraphics guiGraphics) {
        guiGraphics.fill(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, COLOR_BORDER);
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, COLOR_PANEL);
    }

    private void drawHeader(GuiGraphics guiGraphics, ResearchTableClientCache.Snapshot snapshot) {
        int x = panelX + 12;
        guiGraphics.drawString(this.font, "研究桌", x, panelY + 6, COLOR_TITLE, false);

        String cost = "消耗 " + snapshot.cost() + " 点经验";
        String have = "当前 " + snapshot.playerExperience() + " 点";
        guiGraphics.drawString(this.font, cost,
                panelX + panelWidth - 12 - this.font.width(have) - this.font.width("  ") - this.font.width(cost),
                panelY + 6, snapshot.available() ? COLOR_TEXT : COLOR_MUTED, false);
        guiGraphics.drawString(this.font, have, panelX + panelWidth - 12 - this.font.width(have),
                panelY + 6, snapshot.playerExperience() >= snapshot.cost() ? COLOR_TEXT : 0xFFFF8A8A, false);

        if (!snapshot.message().isEmpty()) {
            guiGraphics.drawString(this.font, snapshot.message(), x, panelY + 18, COLOR_MUTED, false);
        }
        String hint = snapshot.learned().isEmpty()
                ? "本次课题（" + snapshot.offer().size() + " 个）"
                : "本次学会 " + snapshot.learned().size() + " 个";
        guiGraphics.drawString(this.font, hint, x, panelY + 30, COLOR_MUTED, false);

        // 列表底衬，让"这是一块可滚动区域"看起来像回事。
        guiGraphics.fill(panelX + 8, listTop - 2, panelX + panelWidth - 8, listBottom + 1, 0x40000000);
    }

    private void drawOfferList(GuiGraphics guiGraphics, ResearchTableClientCache.Snapshot snapshot,
                               int mouseX, int mouseY) {
        List<String> rows = snapshot.offer();
        if (rows.isEmpty()) {
            guiGraphics.drawString(this.font, "§7没有可以研究的配方了", panelX + 14, listTop + 2, COLOR_MUTED, false);
            return;
        }

        int visible = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        int maxScroll = Math.max(0, (rows.size() - visible) * ROW_HEIGHT);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int first = scroll / ROW_HEIGHT;

        for (int index = first; index < rows.size() && index < first + visible + 1; index++) {
            int rowY = listTop + (index * ROW_HEIGHT) - scroll;
            if (rowY + ROW_HEIGHT < listTop || rowY > listBottom) {
                continue;
            }
            if (mouseY >= rowY && mouseY < rowY + ROW_HEIGHT && mouseX >= panelX + 8 && mouseX <= panelX + panelWidth - 8) {
                guiGraphics.fill(panelX + 8, rowY, panelX + panelWidth - 8, rowY + ROW_HEIGHT, COLOR_ROW);
            }
            ItemStack stack = resolve(rows.get(index));
            if (!stack.isEmpty()) {
                guiGraphics.renderItem(stack, panelX + 14, rowY);
            }
            String name = stack.isEmpty() ? rows.get(index) : stack.getHoverName().getString();
            guiGraphics.drawString(this.font, name, panelX + 14 + ICON_SIZE + 6, rowY + 4, COLOR_TEXT, false);
        }

        if (maxScroll > 0) {
            String more = (scroll > 0 ? "▲ " : "") + (scroll < maxScroll ? "▼ 滚轮翻页" : "");
            if (!more.isBlank()) {
                guiGraphics.drawString(this.font, more, panelX + panelWidth - 12 - this.font.width(more),
                        listBottom - 10, COLOR_MUTED, false);
            }
        }
    }

    /**
     * 提交区：真实的槽位 + "需要多少个" + "背包里有多少" + 服务端给的原因。
     *
     * <p>"需要多少个"用服务端下发的除数当场算，所以放下物品的一瞬间就能看到数字；
     * 能不能提交仍然只认服务端那一份结论（按钮的可用状态）。</p>
     */
    private void drawSubmitArea(GuiGraphics guiGraphics, ResearchTableClientCache.Snapshot snapshot,
                                int mouseX, int mouseY) {
        int x = panelX + 12;
        ItemStack stack = this.menu.getSubmitStack();
        int hovered = slotIndexAt(mouseX, mouseY);

        guiGraphics.fill(panelX + 8, submitTop - 2, panelX + panelWidth - 8, submitTop + SUBMIT_HEIGHT - 2,
                0x40000000);
        guiGraphics.drawString(this.font, "提交物品解锁配方", x, submitTop + 2, COLOR_TEXT, false);

        String amount = stack.isEmpty()
                ? "槽位 0 ｜ 背包 0"
                : "槽位 " + stack.getCount() + " ｜ 背包 " + inventoryCountOf(stack);
        guiGraphics.drawString(this.font, amount, panelX + panelWidth - 12 - this.font.width(amount),
                submitTop + 2, COLOR_MUTED, false);

        drawSlot(guiGraphics, submitSlotX(), submitSlotY(), stack,
                hovered == ResearchTableMenu.SUBMIT_SLOT);
        guiGraphics.drawString(this.font, requiredLabel(snapshot, stack), submitSlotX() + SLOT_SIZE + 8,
                submitSlotY() + 5, stack.isEmpty() ? COLOR_MUTED : COLOR_TEXT, false);

        guiGraphics.drawString(this.font, snapshot.submitStatus(), x, submitTop + 36,
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

    private void drawInventory(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int hovered = slotIndexAt(mouseX, mouseY);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < ResearchTableMenu.INVENTORY_COLUMNS; column++) {
                int index = ResearchTableMenu.INVENTORY_SLOT_START + row * ResearchTableMenu.INVENTORY_COLUMNS + column;
                drawSlot(guiGraphics, inventoryX + column * SLOT_SIZE, inventoryGridY() + row * SLOT_SIZE,
                        this.menu.getSlot(index).getItem(), hovered == index);
            }
        }
        for (int column = 0; column < ResearchTableMenu.INVENTORY_COLUMNS; column++) {
            int index = ResearchTableMenu.INVENTORY_SLOT_START + ResearchTableMenu.MAIN_INVENTORY_SIZE + column;
            drawSlot(guiGraphics, inventoryX + column * SLOT_SIZE, hotbarY(),
                    this.menu.getSlot(index).getItem(), hovered == index);
        }
    }

    private void drawSlot(GuiGraphics guiGraphics, int x, int y, ItemStack stack, boolean hovered) {
        guiGraphics.fill(x - 1, y - 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, COLOR_BORDER);
        guiGraphics.fill(x, y, x + ICON_SIZE, y + ICON_SIZE, hovered ? COLOR_SLOT_HOVER : COLOR_SLOT);
        if (!stack.isEmpty()) {
            guiGraphics.renderItem(stack, x, y);
            guiGraphics.renderItemDecorations(this.font, stack, x, y);
        }
    }

    /** 悬停在有东西的槽位上时显示原版物品提示（正拿着东西时不显示，免得挡住鼠标）。 */
    private void drawSlotTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
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

    private void drawFooter(GuiGraphics guiGraphics, ResearchTableClientCache.Snapshot snapshot,
                            int mouseX, int mouseY) {
        boolean ready = snapshot != null;
        int width = Math.min(BUTTON_MAX_WIDTH, (panelWidth - PANEL_MARGIN * 2 - BUTTON_GAP * 2) / 3);
        int y = footerTop + 5;
        int closeX = panelX + 12;
        int researchX = closeX + width + BUTTON_GAP;
        int submitX = researchX + width + BUTTON_GAP;

        drawButton(guiGraphics, closeX, y, width, BUTTON_HEIGHT, "关闭", true, mouseX, mouseY,
                this::onClose);
        drawButton(guiGraphics, researchX, y, width, BUTTON_HEIGHT, "开始研究",
                ready && snapshot.available(), mouseX, mouseY,
                () -> DreamingFishCore_NetworkManager.sendToServer(new Packet_ResearchConfirmRequest(tablePos)));
        drawButton(guiGraphics, submitX, y, width, BUTTON_HEIGHT, "解锁这个配方",
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

    /** 鼠标下的菜单槽位下标；不在任何槽位上时返回 -1。 */
    private int slotIndexAt(double mouseX, double mouseY) {
        if (inside(mouseX, mouseY, submitSlotX(), submitSlotY())) {
            return ResearchTableMenu.SUBMIT_SLOT;
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < ResearchTableMenu.INVENTORY_COLUMNS; column++) {
                if (inside(mouseX, mouseY, inventoryX + column * SLOT_SIZE, inventoryGridY() + row * SLOT_SIZE)) {
                    return ResearchTableMenu.INVENTORY_SLOT_START
                            + row * ResearchTableMenu.INVENTORY_COLUMNS + column;
                }
            }
        }
        for (int column = 0; column < ResearchTableMenu.INVENTORY_COLUMNS; column++) {
            if (inside(mouseX, mouseY, inventoryX + column * SLOT_SIZE, hotbarY())) {
                return ResearchTableMenu.INVENTORY_SLOT_START
                        + ResearchTableMenu.MAIN_INVENTORY_SIZE + column;
            }
        }
        return -1;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D && mouseX >= panelX + 8 && mouseX <= panelX + panelWidth - 8
                && mouseY >= listTop && mouseY <= listBottom) {
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
