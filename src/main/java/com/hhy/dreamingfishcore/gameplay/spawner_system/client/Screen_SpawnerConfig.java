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
 * <p>几处刻意的取舍：</p>
 * <ul>
 *   <li><b>数值可以直接输入</b>：数字本身就是一个输入框，点进去打"4"回车即可，不必把 32 点 7 次减号。
 *       回车提交、点「确认」也会提交当前正在编辑的那一格。</li>
 *   <li><b>奖励项收进折叠分区</b>：常用参数（刷什么、检测、半径、数量、CD、批次、开关）一屏直接可见，
 *       线索与奖励点开「剿灭奖励」才显示。</li>
 *   <li><b>服务端权威</b>：界面不保存数字，每次提交后都用服务端回的快照刷新输入框
 *       （正在编辑的那一格不覆盖，避免打字被抢）。</li>
 * </ul>
 */
public class Screen_SpawnerConfig extends Screen {

    private static final int LABEL_WIDTH = 80;
    private static final int STEP_BUTTON_WIDTH = 16;
    private static final int VALUE_WIDTH = 74;

    private static final int COLOR_TEXT = 0xFFE8EDF2;
    private static final int COLOR_MUTED = 0xFFA7B2BE;
    private static final int COLOR_TITLE = 0xFFFFD479;
    private static final int COLOR_OK = 0xFF78D6A3;
    private static final int COLOR_FAIL = 0xFFFF8A8A;
    private static final int COLOR_PANEL = 0xF010161C;
    private static final int COLOR_BORDER = 0xFF3A4A56;
    private static final int COLOR_BUTTON = 0xFF243039;
    private static final int COLOR_BUTTON_HOVER = 0xFF32434F;
    private static final int COLOR_PRIMARY = 0xFF27506B;
    private static final int COLOR_PRIMARY_HOVER = 0xFF35708F;

    private final BlockPos pos;
    private final List<Hit> hits = new ArrayList<>();
    /** 与 ROWS 对应的输入框（只创建当前可见的那些）。 */
    private final List<EditBox> valueFields = new ArrayList<>();
    private final List<SpawnerConfigRows.Row> visibleRows = new ArrayList<>();
    private EditBox entityField;
    private EditBox rewardField;
    /** 折叠状态：默认收起奖励分区，先让常用参数占满视野。 */
    private boolean rewardsExpanded;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int contentX;
    private int contentWidth;
    private SpawnerConfigLayout layout =
            SpawnerConfigLayout.of(SpawnerConfigLayout.PANEL_MIN_HEIGHT_COLLAPSED, false);
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
                Math.max(SpawnerConfigLayout.minHeightFor(rewardsExpanded), this.height - 20));
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;
        contentX = panelX + 10;
        contentWidth = panelWidth - 20;
        layout = SpawnerConfigLayout.of(panelHeight, rewardsExpanded);

        SpawnerView view = SpawnerConfigClientCache.get();
        entityField = new EditBox(this.font, contentX + LABEL_WIDTH,
                panelY + layout.entityRowY(),
                contentWidth - LABEL_WIDTH - 44, 16, Component.literal("实体 id"));
        entityField.setMaxLength(128);
        entityField.setValue(view == null ? "" : view.entityId());
        entityField.setHint(Component.literal("例如 minecraft:zombie"));
        addRenderableWidget(entityField);

        // 数值输入框：行序与 render 完全一致（SpawnerConfigRows.yFor 同一条规则）。
        visibleRows.clear();
        valueFields.clear();
        visibleRows.addAll(SpawnerConfigRows.visible(rewardsExpanded));
        for (int index = 0; index < visibleRows.size(); index++) {
            SpawnerConfigRows.Row spec = visibleRows.get(index);
            EditBox box = new EditBox(this.font, contentX + LABEL_WIDTH,
                    SpawnerConfigRows.yFor(visibleRows, index, layout, panelY),
                    VALUE_WIDTH, 16, Component.literal(spec.label()));
            box.setMaxLength(12);
            box.setValue(Integer.toString(currentValue(view, spec.action())));
            addRenderableWidget(box);
            valueFields.add(box);
        }

        if (rewardsExpanded) {
            rewardField = new EditBox(this.font, contentX + LABEL_WIDTH,
                    panelY + layout.rewardInputY(),
                    contentWidth - LABEL_WIDTH - 48, 16, Component.literal("奖励物品"));
            rewardField.setMaxLength(128);
            rewardField.setHint(Component.literal("minecraft:apple x8"));
            addRenderableWidget(rewardField);
        } else {
            rewardField = null;
        }
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

        drawEntityRow(guiGraphics, mouseX, mouseY, view);
        drawValueRows(guiGraphics, mouseX, mouseY, view);
        drawToggleRow(guiGraphics, mouseX, mouseY, view);
        drawRewardSection(guiGraphics, mouseX, mouseY, view);
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
        rowY = panelY + layout.entityRowY();
        guiGraphics.drawString(this.font, "刷的怪", contentX, rowY + 3, COLOR_TEXT, false);
        int buttonX = contentX + contentWidth - 40;
        button(guiGraphics, mouseX, mouseY, buttonX, rowY, 40, 16, "应用", false,
                () -> send(Packet_SpawnerConfigRequest.Action.SET_ENTITY, 0, false,
                        entityField.getValue()));
        rowY += 18;

        if (!layout.showPresets()) {
            return;
        }
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
        button(guiGraphics, mouseX, mouseY, x, rowY, width, 14, label, false, () -> {
            entityField.setValue(entityId);
            send(Packet_SpawnerConfigRequest.Action.SET_ENTITY, 0, false, entityId);
        });
        return x + width + 4;
    }

    /**
     * 数值行：标签 + 输入框（点进去直接打数字）+ 加减 + 复位。
     *
     * <p>输入框位置在 {@link #init()} 里排好，这里只画按钮并把服务端的最新值同步进输入框
     * （正在编辑的那一格不覆盖，否则打字会被抢掉）。</p>
     */
    private void drawValueRows(GuiGraphics guiGraphics, int mouseX, int mouseY, SpawnerView view) {
        for (int index = 0; index < visibleRows.size(); index++) {
            SpawnerConfigRows.Row spec = visibleRows.get(index);
            int y = SpawnerConfigRows.yFor(visibleRows, index, layout, panelY);
            int value = currentValue(view, spec.action());

            guiGraphics.drawString(this.font, spec.label(), contentX, y + 3, COLOR_TEXT, false);
            EditBox box = valueFields.get(index);
            if (!box.isFocused() && !box.getValue().equals(Integer.toString(value))) {
                box.setValue(Integer.toString(value));
            }
            int suffixX = contentX + LABEL_WIDTH + VALUE_WIDTH + 4;
            if (!spec.suffix().isBlank()) {
                guiGraphics.drawString(this.font, spec.suffix(), suffixX, y + 3, COLOR_MUTED, false);
            }

            int minusX = contentX + contentWidth - STEP_BUTTON_WIDTH * 3 - 8;
            button(guiGraphics, mouseX, mouseY, minusX, y, STEP_BUTTON_WIDTH, 16, "-", false,
                    () -> send(spec.action(), value - spec.step(), false, ""));
            button(guiGraphics, mouseX, mouseY, minusX + STEP_BUTTON_WIDTH + 2, y,
                    STEP_BUTTON_WIDTH, 16, "+", false,
                    () -> send(spec.action(), value + spec.step(), false, ""));
            button(guiGraphics, mouseX, mouseY, minusX + (STEP_BUTTON_WIDTH + 2) * 2, y,
                    STEP_BUTTON_WIDTH, 16, "R", false,
                    () -> send(spec.action(), spec.defaultValue(), false, ""));
            y += layout.rowHeight();
        }
    }

    private void drawToggleRow(GuiGraphics guiGraphics, int mouseX, int mouseY, SpawnerView view) {
        int x = contentX;
        rowY = panelY + layout.rewardSectionY() - layout.rowHeight();
        x = toggle(guiGraphics, mouseX, mouseY, x, "红石控制", view.redstoneControlled(),
                Packet_SpawnerConfigRequest.Action.SET_REDSTONE, !view.redstoneControlled());
        x = toggle(guiGraphics, mouseX, mouseY, x, "剿灭自毁", view.selfDestructWhenCleared(),
                Packet_SpawnerConfigRequest.Action.SET_SELF_DESTRUCT,
                !view.selfDestructWhenCleared());
        toggle(guiGraphics, mouseX, mouseY, x, "固定线索", view.fixedClueEnabled(),
                Packet_SpawnerConfigRequest.Action.SET_CLUE_ENABLED, !view.fixedClueEnabled());
    }

    private int toggle(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, String label,
                       boolean on, Packet_SpawnerConfigRequest.Action action, boolean next) {
        String text = label + "：" + (on ? "开" : "关");
        int width = this.font.width(text) + 10;
        button(guiGraphics, mouseX, mouseY, x, rowY, width, 16, text, false,
                () -> send(action, 0, next, ""));
        return x + width + 4;
    }

    /** 「剿灭奖励」折叠标题；展开后画奖励列表与输入行。 */
    private void drawRewardSection(GuiGraphics guiGraphics, int mouseX, int mouseY, SpawnerView view) {
        int y = panelY + layout.rewardSectionY();
        int count = view.rewardItems().size();
        String title = (rewardsExpanded ? "▼ " : "▶ ") + "剿灭奖励"
                + (rewardsExpanded ? "" : "（线索 / 经验 / 梦鱼币 / 物品"
                        + (count > 0 ? "，" + count + " 项" : "") + "）");
        button(guiGraphics, mouseX, mouseY, contentX, y, contentWidth, 16, title, false,
                this::toggleRewards);
        rowY = y + layout.rowHeight();

        if (!rewardsExpanded) {
            return;
        }
        guiGraphics.drawString(this.font, "奖励物品（" + count + "）", contentX,
                panelY + layout.rewardListTop() - layout.rowHeight() + 3, COLOR_TEXT, false);

        int listY = panelY + layout.rewardListTop();
        int listRowHeight = Math.max(10, layout.rowHeight() - 2);
        int maxRows = layout.rewardListMaxRows();
        if (maxRows > 0) {
            if (count == 0) {
                guiGraphics.drawString(this.font, "（还没有奖励物品，可在下面输入 id 后点添加）",
                        contentX + LABEL_WIDTH, listY + 2, COLOR_MUTED, false);
            } else {
                int shown = Math.min(maxRows, count);
                for (int index = 0; index < shown; index++) {
                    SpawnerView.RewardLine line = view.rewardItems().get(index);
                    guiGraphics.drawString(this.font, "· " + line.itemId() + " ×" + line.count(),
                            contentX + LABEL_WIDTH, listY + 2, COLOR_TEXT, false);
                    int finalIndex = index;
                    button(guiGraphics, mouseX, mouseY, contentX + contentWidth - 40, listY, 40,
                            listRowHeight, "删除", false,
                            () -> send(Packet_SpawnerConfigRequest.Action.REMOVE_REWARD_ITEM,
                                    finalIndex, false, ""));
                    listY += listRowHeight;
                }
                if (count > shown) {
                    guiGraphics.drawString(this.font,
                            "（还有 " + (count - shown) + " 项未显示，删掉上面的即可看到）",
                            contentX + LABEL_WIDTH, listY + 2, COLOR_MUTED, false);
                }
            }
        }

        int inputY = panelY + layout.rewardInputY();
        button(guiGraphics, mouseX, mouseY, contentX + contentWidth - 44, inputY, 44, 16, "添加",
                false, () -> send(Packet_SpawnerConfigRequest.Action.ADD_REWARD_ITEM, 0, false,
                        rewardField == null ? "" : rewardField.getValue()));
    }

    private void drawStatus(GuiGraphics guiGraphics, SpawnerView view, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font,
                (view.inHordeArea() ? "尸潮区域内" : "不在尸潮区域内") + " · "
                        + (view.active() ? "工作中" : "停机") + " · 批次 "
                        + view.batchesSpawned() + "/" + view.batches() + " · 场上 "
                        + view.aliveCount() + " 只",
                contentX, panelY + layout.statusY(), view.inHordeArea() ? COLOR_OK : COLOR_FAIL, false);

        // 不在区域内时，服务端会带上"为什么"（含高度超出 / 没开开关），直接画出来省得玩家猜。
        String message = SpawnerConfigClientCache.message();
        String detail = message.isBlank() ? view.areaHint() : message;
        boolean ok = message.isBlank() ? view.inHordeArea() : SpawnerConfigClientCache.lastSuccess();
        if (!detail.isBlank()) {
            guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(detail, contentWidth),
                    contentX, panelY + layout.messageY(), ok ? COLOR_OK : COLOR_FAIL, false);
        }

        int buttonY = panelY + layout.buttonsY();
        button(guiGraphics, mouseX, mouseY, contentX, buttonY, 70, 16, "确认", true,
                this::confirmAndClose);
        button(guiGraphics, mouseX, mouseY, contentX + 76, buttonY, 70, 16, "重置本轮", false,
                () -> send(Packet_SpawnerConfigRequest.Action.RESET_ROUND, 0, false, ""));
    }

    /** 切换折叠状态：重建控件（输入框只在可见时创建）。 */
    private void toggleRewards() {
        rewardsExpanded = !rewardsExpanded;
        this.rebuildWidgets();
    }

    /** 「确认」= 提交正在编辑的那一格，然后关闭界面。 */
    private void confirmAndClose() {
        commitFocusedField();
        this.onClose();
    }

    /** 提交当前聚焦的数值输入框（回车与「确认」都走这里）。 */
    private boolean commitFocusedField() {
        for (int index = 0; index < valueFields.size(); index++) {
            EditBox box = valueFields.get(index);
            if (box.isFocused()) {
                Integer parsed = parseAmount(box.getValue());
                if (parsed != null) {
                    send(visibleRows.get(index).action(), parsed, false, "");
                }
                return true;
            }
        }
        if (entityField != null && entityField.isFocused()) {
            send(Packet_SpawnerConfigRequest.Action.SET_ENTITY, 0, false, entityField.getValue());
            return true;
        }
        if (rewardField != null && rewardField.isFocused()) {
            send(Packet_SpawnerConfigRequest.Action.ADD_REWARD_ITEM, 0, false,
                    rewardField.getValue());
            return true;
        }
        return false;
    }

    /** 画一个按钮并登记点击区域。 */
    private void button(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y,
                        int width, int height, String label, boolean primary, Runnable action) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        int fill = primary
                ? (hovered ? COLOR_PRIMARY_HOVER : COLOR_PRIMARY)
                : (hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
        guiGraphics.fill(x, y, x + width, y + height, fill);
        guiGraphics.fill(x, y, x + width, y + 1, COLOR_BORDER);
        String text = this.font.width(label) > width - 4
                ? this.font.plainSubstrByWidth(label, width - 6) : label;
        guiGraphics.drawString(this.font, text,
                x + (width - this.font.width(text)) / 2, y + (height - 8) / 2, COLOR_TEXT, false);
        hits.add(new Hit(x, y, x + width, y + height, action));
    }

    // ==================== 数据 ====================

    private static int currentValue(SpawnerView view, Packet_SpawnerConfigRequest.Action action) {
        if (view == null) {
            return 0;
        }
        return switch (action) {
            case SET_DETECTION_RADIUS -> view.detectionRadius();
            case SET_SPAWN_RADIUS -> view.spawnRadius();
            case SET_SPAWN_COUNT -> view.spawnCount();
            case SET_COOLDOWN -> view.cooldownTicks();
            case SET_BATCHES -> view.batches();
            case SET_CLUE_ID -> view.clueId();
            case SET_REWARD_EXPERIENCE -> view.rewardExperience();
            case SET_REWARD_COINS -> view.rewardCoins();
            default -> 0;
        };
    }

    /** 解析输入框里的整数；空或非法返回 null（不提交）。 */
    static Integer parseAmount(String raw) {
        return SpawnerConfigInput.parseAmount(raw);
    }
    // ==================== 输入 ====================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 回车 = 提交当前聚焦的那一格（原版会在 EditBox 聚焦时吞掉回车之外的键）。
        if ((keyCode == 257 || keyCode == 335) && commitFocusedField()) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

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
