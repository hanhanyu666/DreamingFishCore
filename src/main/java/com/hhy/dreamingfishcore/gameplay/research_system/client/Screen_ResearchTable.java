package com.hhy.dreamingfishcore.gameplay.research_system.client;

import com.hhy.dreamingfishcore.gameplay.research_system.network.Packet_ResearchConfirmRequest;
import com.hhy.dreamingfishcore.gameplay.research_system.network.Packet_ResearchTableOpenRequest;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 研究桌界面。
 *
 * <p>几处取舍：</p>
 * <ul>
 *   <li><b>先看清再花钱</b>：本次课题逐条列出（带物品图标），点「开始研究」才扣经验。
 *       课题是服务端固定下来的，关掉再开还是同一批——所以不存在"反复开关刷结果"。</li>
 *   <li><b>状态全部来自服务端</b>：界面不自己算经验、也不自己判断能不能研究，
 *       每次渲染都读 {@link ResearchTableClientCache}，服务端推一份新的就自动刷新。</li>
 *   <li><b>列表超过一屏可滚动</b>：课题最多 15 条，小窗口下会超出，用滚轮翻。</li>
 * </ul>
 */
public class Screen_ResearchTable extends Screen {

    private static final int PANEL_MAX_WIDTH = 360;
    private static final int PANEL_MARGIN = 12;
    private static final int HEADER_HEIGHT = 56;
    private static final int FOOTER_HEIGHT = 34;
    private static final int ROW_HEIGHT = 16;
    private static final int ICON_SIZE = 16;
    /** 没有课题时也要留一点高度，免得面板缩成一条线。 */
    private static final int MIN_LIST_ROWS = 3;

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
    private static final int COLOR_ROW = 0xFF161E25;

    private static final int BUTTON_WIDTH = 96;
    private static final int BUTTON_HEIGHT = 20;
    /** 内容区的滚动量（像素）。 */
    private static final int SCROLL_STEP = 16;

    private final BlockPos tablePos;
    private final List<Hit> hits = new ArrayList<>();

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int listTop;
    private int listBottom;
    private int scroll;

    private record Hit(int x1, int y1, int x2, int y2, boolean enabled, Runnable action) {
    }

    public Screen_ResearchTable(BlockPos tablePos) {
        super(Component.literal("研究桌"));
        this.tablePos = tablePos;
    }

    @Override
    public boolean isPauseScreen() {
        // 与方块界面一致：不暂停世界，多人服务器上也不该暂停。
        return false;
    }

    @Override
    protected void init() {
        super.init();
        int rows = Math.max(MIN_LIST_ROWS, Math.min(maxRows(), offer().size()));
        panelWidth = Math.min(PANEL_MAX_WIDTH, this.width - PANEL_MARGIN * 2);
        panelHeight = Math.min(
                Math.max(HEADER_HEIGHT + FOOTER_HEIGHT + rows * ROW_HEIGHT, 140),
                this.height - PANEL_MARGIN * 2);
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;
        listTop = panelY + HEADER_HEIGHT;
        listBottom = panelY + panelHeight - FOOTER_HEIGHT;
        // 要一份最新快照：快照里的"当前经验"是服务端算的，玩家可能在打开界面之前刚补过经验。
        // 这个请求不会重掷课题（服务端只在旧课题失效时才换一批）。
        DreamingFishCore_NetworkManager.sendToServer(new Packet_ResearchTableOpenRequest(tablePos));
    }

    private int maxRows() {
        return Math.max(MIN_LIST_ROWS, (this.height - PANEL_MARGIN * 2 - HEADER_HEIGHT - FOOTER_HEIGHT) / ROW_HEIGHT);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        ResearchTableClientCache.Snapshot snapshot = ResearchTableClientCache.get();
        hits.clear();

        drawPanel(guiGraphics);

        if (snapshot == null) {
            guiGraphics.drawString(this.font, "§7正在读取研究桌状态…", panelX + 12, listTop, COLOR_MUTED, false);
            drawCloseButton(guiGraphics, mouseX, mouseY);
            return;
        }

        drawHeader(guiGraphics, snapshot);
        drawOfferList(guiGraphics, snapshot, mouseX, mouseY);
        drawFooter(guiGraphics, snapshot, mouseX, mouseY);
    }

    private void drawPanel(GuiGraphics guiGraphics) {
        guiGraphics.fill(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, COLOR_BORDER);
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, COLOR_PANEL);
    }

    private void drawHeader(GuiGraphics guiGraphics, ResearchTableClientCache.Snapshot snapshot) {
        int x = panelX + 12;
        int y = panelY + 10;
        guiGraphics.drawString(this.font, "研究桌", x, y, COLOR_TITLE, false);

        String cost = "消耗 " + snapshot.cost() + " 点经验";
        String have = "当前 " + snapshot.playerExperience() + " 点";
        int costWidth = this.font.width(cost);
        guiGraphics.drawString(this.font, cost, panelX + panelWidth - 12 - costWidth - this.font.width("  ") - this.font.width(have),
                y, snapshot.available() ? COLOR_TEXT : COLOR_MUTED, false);
        guiGraphics.drawString(this.font, have, panelX + panelWidth - 12 - this.font.width(have), y,
                snapshot.playerExperience() >= snapshot.cost() ? COLOR_TEXT : 0xFFFF8A8A, false);

        if (!snapshot.message().isEmpty()) {
            guiGraphics.drawString(this.font, snapshot.message(), x, y + 14, COLOR_MUTED, false);
        }
        String hint = snapshot.learned().isEmpty()
                ? "本次课题（" + snapshot.offer().size() + " 个）"
                : "本次学会 " + snapshot.learned().size() + " 个";
        guiGraphics.drawString(this.font, hint, x, y + 28, COLOR_MUTED, false);

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

    private void drawFooter(GuiGraphics guiGraphics, ResearchTableClientCache.Snapshot snapshot,
                            int mouseX, int mouseY) {
        int y = panelY + panelHeight - FOOTER_HEIGHT + 6;
        int researchX = panelX + panelWidth - 12 - BUTTON_WIDTH;
        drawButton(guiGraphics, researchX, y, BUTTON_WIDTH, BUTTON_HEIGHT, "开始研究",
                snapshot.available(), mouseX, mouseY,
                () -> DreamingFishCore_NetworkManager.sendToServer(new Packet_ResearchConfirmRequest(tablePos)));
        drawCloseButton(guiGraphics, mouseX, mouseY);
    }

    private void drawCloseButton(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int y = panelY + panelHeight - FOOTER_HEIGHT + 6;
        drawButton(guiGraphics, panelX + 12, y, BUTTON_WIDTH, BUTTON_HEIGHT, "关闭", true, mouseX, mouseY,
                this::onClose);
    }

    private void drawButton(GuiGraphics guiGraphics, int x, int y, int width, int height, String label,
                            boolean enabled, int mouseX, int mouseY, Runnable action) {
        boolean hovered = enabled && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        int background;
        if (!enabled) {
            background = COLOR_BUTTON_OFF;
        } else if (isPrimary(label)) {
            background = hovered ? COLOR_PRIMARY_HOVER : COLOR_PRIMARY;
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

    private boolean isPrimary(String label) {
        return "开始研究".equals(label);
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
        return super.mouseClicked(mouseX, mouseY, button);
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

    private List<String> offer() {
        ResearchTableClientCache.Snapshot snapshot = ResearchTableClientCache.get();
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
