package com.hhy.dreamingfishcore.gameplay.spawner_system.client;

import com.hhy.dreamingfishcore.gameplay.spawner_system.SpawnerView;
import com.hhy.dreamingfishcore.gameplay.spawner_system.network.Packet_SpawnerConfigRequest;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 刷怪箱配置界面。
 *
 * <p>全部控件都是"发一次请求、等服务端回快照"：界面上显示的永远是最新快照，
 * 数值的合法性由服务端夹取后回传，客户端不自己改本地数字（否则界面会和服务端不一致）。</p>
 *
 * <p>布局用真实屏幕坐标并夹取到可用范围内，行高按面板高度自适应（18/14/12），
 * 底部固定为「奖励输入 → 状态 → 按钮」三块，奖励列表按剩余空间裁剪，
 * 所以在很小的 GUI 缩放下也不会互相压住。</p>
 */
public class Screen_SpawnerConfig extends Screen {

    private static final int SMALL_BUTTON_WIDTH = 16;
    private static final int LABEL_WIDTH = 80;

    private static final int COLOR_TEXT = 0xFFE8EDF2;
    private static final int COLOR_MUTED = 0xFFA7B2BE;
    private static final int COLOR_TITLE = 0xFFFFD479;
    private static final int COLOR_OK = 0xFF78D6A3;
    private static final int COLOR_FAIL = 0xFFFF8A8A;
    private static final int COLOR_PANEL = 0xF010161C;
    private static final int COLOR_BORDER = 0xFF3A4A56;
    private static final int COLOR_BUTTON = 0xFF243039;
    private static final int COLOR_BUTTON_HOVER = 0xFF32434F;

    private final BlockPos pos;
    private final List<Hit> hits = new ArrayList<>();
    private EditBox entityField;
    private EditBox rewardField;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int contentX;
    private int contentWidth;
    /** 布局解算结果：行高、底部锚点、奖励列表能画几行。 */
    private SpawnerConfigLayout layout = SpawnerConfigLayout.of(SpawnerConfigLayout.PANEL_MIN_HEIGHT);
    private int rowY;

    private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
    }

    public Screen_SpawnerConfig(BlockPos pos) {
        super(Component.literal("刷怪箱配置"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        super.init();
        panelWidth = Math.min(SpawnerConfigLayout.PANEL_MAX_WIDTH,
                Math.max(SpawnerConfigLayout.PANEL_MIN_WIDTH, this.width - 20));
        panelHeight = Math.min(SpawnerConfigLayout.PANEL_MAX_HEIGHT,
                Math.max(SpawnerConfigLayout.PANEL_MIN_HEIGHT, this.height - 20));
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;
        contentX = panelX + 10;
        contentWidth = panelWidth - 20;
        layout = SpawnerConfigLayout.of(panelHeight);

        SpawnerView view = SpawnerConfigClientCache.get();
        // 实体 id 输入框：内容按快照预填，回车或点「应用」提交。
        entityField = new EditBox(this.font, contentX + LABEL_WIDTH,
                panelY + layout.entityRowY(),
                contentWidth - LABEL_WIDTH - 44, 16, Component.literal("实体 id"));
        entityField.setMaxLength(128);
        entityField.setValue(view == null ? "" : view.entityId());
        entityField.setHint(Component.literal("例如 minecraft:zombie"));
        addRenderableWidget(entityField);

        // 奖励物品输入框与「添加」按钮同排，避免"输入框在下面、按钮在上面"的割裂感。
        rewardField = new EditBox(this.font, contentX + LABEL_WIDTH,
                panelY + layout.rewardInputY(),
                contentWidth - LABEL_WIDTH - 48, 16, Component.literal("奖励物品"));
        rewardField.setMaxLength(128);
        rewardField.setHint(Component.literal("minecraft:apple x8"));
        addRenderableWidget(rewardField);
    }

    // ==================== 渲染 ====================

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 只在这里铺一次背景；结尾不能调 super.render，否则它会再铺一层把面板压暗。
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        hits.clear();

        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, COLOR_PANEL);
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, COLOR_BORDER);
        guiGraphics.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth,
                panelY + panelHeight, COLOR_BORDER);

        SpawnerView view = SpawnerConfigClientCache.get();
        if (view == null) {
            guiGraphics.drawString(this.font, "等待服务端下发刷怪箱数据…",
                    contentX, panelY + 30, COLOR_MUTED, false);
            renderWidgets(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        guiGraphics.drawString(this.font,
                "刷怪箱配置 · " + view.x() + ", " + view.y() + ", " + view.z(),
                contentX, panelY + 8, COLOR_TITLE, false);

        rowY = panelY + layout.entityRowY();
        drawEntityRow(guiGraphics, mouseX, mouseY, view);
        drawNumberRow(guiGraphics, mouseX, mouseY, "检测范围", view.detectionRadius(), 4, 32,
                Packet_SpawnerConfigRequest.Action.SET_DETECTION_RADIUS, " 格");
        drawNumberRow(guiGraphics, mouseX, mouseY, "刷怪半径", view.spawnRadius(), 1, 8,
                Packet_SpawnerConfigRequest.Action.SET_SPAWN_RADIUS, " 格");
        drawNumberRow(guiGraphics, mouseX, mouseY, "每批数量", view.spawnCount(), 1, 3,
                Packet_SpawnerConfigRequest.Action.SET_SPAWN_COUNT, " 只");
        drawNumberRow(guiGraphics, mouseX, mouseY, "刷怪冷却", view.cooldownTicks(), 100, 600,
                Packet_SpawnerConfigRequest.Action.SET_COOLDOWN,
                " tick（" + view.cooldownSeconds() + " 秒）");
        drawNumberRow(guiGraphics, mouseX, mouseY, "批次", view.batches(), 1, 3,
                Packet_SpawnerConfigRequest.Action.SET_BATCHES, " 批");
        drawToggleRow(guiGraphics, mouseX, mouseY, view);
        drawNumberRow(guiGraphics, mouseX, mouseY, "线索编号", view.clueId(), 1, 1,
                Packet_SpawnerConfigRequest.Action.SET_CLUE_ID, "");
        drawNumberRow(guiGraphics, mouseX, mouseY, "奖励经验", view.rewardExperience(), 50, 0,
                Packet_SpawnerConfigRequest.Action.SET_REWARD_EXPERIENCE, "");
        drawNumberRow(guiGraphics, mouseX, mouseY, "奖励梦鱼币", view.rewardCoins(), 100, 0,
                Packet_SpawnerConfigRequest.Action.SET_REWARD_COINS, " 梦鱼币");

        drawRewards(guiGraphics, mouseX, mouseY, view);
        drawRewardInputRow(guiGraphics, mouseX, mouseY);
        drawStatus(guiGraphics, view, mouseX, mouseY);

        renderWidgets(guiGraphics, mouseX, mouseY, partialTick);
    }

    /** 只渲染控件本身（背景已在上面铺过）。 */
    private void renderWidgets(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        for (Renderable renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private void drawEntityRow(GuiGraphics guiGraphics, int mouseX, int mouseY, SpawnerView view) {
        guiGraphics.drawString(this.font, "刷的怪", contentX, rowY + 3, COLOR_TEXT, false);
        int buttonX = contentX + contentWidth - 40;
        button(guiGraphics, mouseX, mouseY, buttonX, rowY, 40, 16, "应用",
                () -> send(Packet_SpawnerConfigRequest.Action.SET_ENTITY, 0, false,
                        entityField.getValue()));
        rowY += Math.max(18, layout.rowHeight());

        if (!layout.showPresets()) {
            return;
        }
        // 常用预设：省得每次手打 id。面板太矮时这一行直接不画，优先保证其余参数可见。
        int presetX = contentX + LABEL_WIDTH;
        presetX = preset(guiGraphics, mouseX, mouseY, presetX, "攻城丧尸",
                "dreamingfishcore:siege_zombie");
        presetX = preset(guiGraphics, mouseX, mouseY, presetX, "僵尸", "minecraft:zombie");
        presetX = preset(guiGraphics, mouseX, mouseY, presetX, "尸壳", "minecraft:husk");
        preset(guiGraphics, mouseX, mouseY, presetX, "骷髅", "minecraft:skeleton");
        rowY += layout.rowHeight();
    }

    private int preset(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, String label,
                       String entityId) {
        int width = this.font.width(label) + 10;
        button(guiGraphics, mouseX, mouseY, x, rowY, width, 14, label, () -> {
            entityField.setValue(entityId);
            send(Packet_SpawnerConfigRequest.Action.SET_ENTITY, 0, false, entityId);
        });
        return x + width + 4;
    }

    private void drawNumberRow(GuiGraphics guiGraphics, int mouseX, int mouseY, String label,
                               int current, int step, int defaultValue,
                               Packet_SpawnerConfigRequest.Action action, String suffix) {
        guiGraphics.drawString(this.font, label, contentX, rowY + 3, COLOR_TEXT, false);
        int minusX = contentX + LABEL_WIDTH;
        int buttonHeight = Math.min(16, layout.rowHeight() - 2);
        button(guiGraphics, mouseX, mouseY, minusX, rowY, SMALL_BUTTON_WIDTH, buttonHeight, "-",
                () -> send(action, current - step, false, ""));
        int valueX = minusX + SMALL_BUTTON_WIDTH + 4;
        guiGraphics.drawString(this.font, current + suffix, valueX, rowY + 3, COLOR_TEXT, false);
        int plusX = contentX + contentWidth - SMALL_BUTTON_WIDTH * 2 - 4;
        button(guiGraphics, mouseX, mouseY, plusX, rowY, SMALL_BUTTON_WIDTH, buttonHeight, "+",
                () -> send(action, current + step, false, ""));
        if (defaultValue >= 0) {
            button(guiGraphics, mouseX, mouseY, plusX + SMALL_BUTTON_WIDTH + 4, rowY,
                    SMALL_BUTTON_WIDTH, buttonHeight, "R", () -> send(action, defaultValue, false, ""));
        }
        rowY += layout.rowHeight();
    }

    private void drawToggleRow(GuiGraphics guiGraphics, int mouseX, int mouseY, SpawnerView view) {
        int x = contentX;
        x = toggle(guiGraphics, mouseX, mouseY, x, "红石控制", view.redstoneControlled(),
                Packet_SpawnerConfigRequest.Action.SET_REDSTONE, !view.redstoneControlled());
        x = toggle(guiGraphics, mouseX, mouseY, x, "剿灭自毁", view.selfDestructWhenCleared(),
                Packet_SpawnerConfigRequest.Action.SET_SELF_DESTRUCT,
                !view.selfDestructWhenCleared());
        toggle(guiGraphics, mouseX, mouseY, x, "固定线索", view.fixedClueEnabled(),
                Packet_SpawnerConfigRequest.Action.SET_CLUE_ENABLED, !view.fixedClueEnabled());
        rowY += layout.rowHeight();
    }

    private int toggle(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, String label,
                       boolean on, Packet_SpawnerConfigRequest.Action action, boolean next) {
        String text = label + "：" + (on ? "开" : "关");
        int width = this.font.width(text) + 10;
        button(guiGraphics, mouseX, mouseY, x, rowY, width, Math.min(14, layout.rowHeight() - 2), text,
                () -> send(action, 0, next, ""));
        return x + width + 4;
    }

    /**
     * 奖励物品列表。
     *
     * <p>只画"到输入行为止"还剩得下的行数，放不下的用一行提示，避免压住下面的状态与输入框。</p>
     */
    private void drawRewards(GuiGraphics guiGraphics, int mouseX, int mouseY, SpawnerView view) {
        guiGraphics.drawString(this.font,
                "奖励物品（" + view.rewardItems().size() + "）", contentX, rowY + 3, COLOR_TEXT, false);
        rowY += layout.rowHeight();

        int listRowHeight = Math.max(10, layout.rowHeight() - 2);
        int available = Math.min(layout.rewardListMaxRows(), (panelY + layout.rewardInputY() - 4 - rowY) / listRowHeight);
        if (available <= 0) {
            return;
        }
        if (view.rewardItems().isEmpty()) {
            guiGraphics.drawString(this.font, "（还没有奖励物品，可在下面输入 id 后点添加）",
                    contentX + LABEL_WIDTH, rowY + 2, COLOR_MUTED, false);
            return;
        }
        int shown = Math.min(available, view.rewardItems().size());
        for (int index = 0; index < shown; index++) {
            SpawnerView.RewardLine line = view.rewardItems().get(index);
            guiGraphics.drawString(this.font, "· " + line.itemId() + " ×" + line.count(),
                    contentX + LABEL_WIDTH, rowY + 2, COLOR_TEXT, false);
            int finalIndex = index;
            button(guiGraphics, mouseX, mouseY, contentX + contentWidth - 40, rowY, 40,
                    listRowHeight, "删除",
                    () -> send(Packet_SpawnerConfigRequest.Action.REMOVE_REWARD_ITEM,
                            finalIndex, false, ""));
            rowY += listRowHeight;
        }
        if (view.rewardItems().size() > shown) {
            guiGraphics.drawString(this.font,
                    "（还有 " + (view.rewardItems().size() - shown) + " 条未显示，删除上面的即可看到）",
                    contentX + LABEL_WIDTH, rowY + 2, COLOR_MUTED, false);
        }
    }

    /** 奖励输入行：输入框 + 添加按钮。 */
    private void drawRewardInputRow(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int buttonX = contentX + contentWidth - 44;
        button(guiGraphics, mouseX, mouseY, buttonX, panelY + layout.rewardInputY(), 44, 16, "添加",
                () -> send(Packet_SpawnerConfigRequest.Action.ADD_REWARD_ITEM, 0, false,
                        rewardField.getValue()));
    }

    private void drawStatus(GuiGraphics guiGraphics, SpawnerView view, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font,
                (view.inHordeArea() ? "尸潮区域内" : "不在尸潮区域内") + " · "
                        + (view.active() ? "工作中" : "停机") + " · 批次 "
                        + view.batchesSpawned() + "/" + view.batches() + " · 场上 "
                        + view.aliveCount() + " 只",
                contentX, panelY + layout.statusY(), view.inHordeArea() ? COLOR_OK : COLOR_FAIL, false);

        String message = SpawnerConfigClientCache.message();
        if (!message.isBlank()) {
            guiGraphics.drawString(this.font, message, contentX, panelY + layout.messageY(),
                    SpawnerConfigClientCache.lastSuccess() ? COLOR_OK : COLOR_FAIL, false);
        }

        button(guiGraphics, mouseX, mouseY, contentX, panelY + layout.buttonsY(), 60, 14, "重置本轮",
                () -> send(Packet_SpawnerConfigRequest.Action.RESET_ROUND, 0, false, ""));
        button(guiGraphics, mouseX, mouseY, contentX + 66, panelY + layout.buttonsY(), 40, 14, "关闭",
                this::onClose);
    }

    /** 画一个按钮并登记点击区域。 */
    private void button(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y,
                        int width, int height, String label, Runnable action) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        guiGraphics.fill(x, y, x + width, y + height, hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
        guiGraphics.fill(x, y, x + width, y + 1, COLOR_BORDER);
        String text = this.font.width(label) > width - 4
                ? this.font.plainSubstrByWidth(label, width - 6) : label;
        guiGraphics.drawString(this.font, text,
                x + (width - this.font.width(text)) / 2, y + (height - 8) / 2, COLOR_TEXT, false);
        hits.add(new Hit(x, y, x + width, y + height, action));
    }

    // ==================== 输入 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        for (int index = hits.size() - 1; index >= 0; index--) {
            Hit hit = hits.get(index);
            if (mouseX >= hit.x1() && mouseX <= hit.x2()
                    && mouseY >= hit.y1() && mouseY <= hit.y2()) {
                hit.action().run();
                return true;
            }
        }
        return false;
    }

    private void send(Packet_SpawnerConfigRequest.Action action, int value, boolean flag,
                      String text) {
        if (pos == null) {
            return;
        }
        DreamingFishCore_NetworkManager.sendToServer(
                new Packet_SpawnerConfigRequest(pos, action, value, flag, text));
    }

    @Override
    public boolean isPauseScreen() {
        // 单机下不暂停：刷怪箱的判定依赖服务端 tick，暂停会让"测试刷怪"变得莫名其妙。
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        SpawnerConfigClientCache.setMessage(true, "");
    }
}
