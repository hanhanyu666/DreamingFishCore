package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.client.ui;

import com.hhy.dreamingfishcore.client.ui.framework.anim.AnimatedFloat;
import com.hhy.dreamingfishcore.client.ui.framework.anim.Easing;
import com.hhy.dreamingfishcore.client.ui.framework.anim.Spring;
import com.hhy.dreamingfishcore.client.ui.framework.core.UiClock;
import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.Cursor;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.Justify;
import com.hhy.dreamingfishcore.client.ui.framework.node.Size;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.screen.ScreenHost;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.InteractiveNode;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Responsive;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.client.ui.vanilla.VanillaChrome;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.TemplateReconstructionRules;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.client.cache.DeathScreenDataStorage;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.network.Packet_KeepInventoryRequest;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.network.Packet_NormalRespawnRequest;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.Locale;

/**
 * 死亡界面：游戏画面褪色压暗，标题逐字打出后移到左侧，随后浮现死亡原因、尸体位置、
 * 模板重建余量与三个选择。悬停重生选项时，余量条会预演扣除后的结果。
 */
public final class DeathScreenUi {
    private static final String TITLE = "布豪，您趋势了！";
    private static final float TYPE_DELAY_MS = 360.0F;
    private static final float TYPE_INTERVAL_MS = 92.0F;
    private static final float SETTLE_MS = 260.0F;
    private static final float SLIDE_MS = 620.0F;
    private static final float TITLE_SCALE = 3.0F;

    static final int RED = 0xFFD23E3A;
    static final int RED_DEEP = 0xFF8F1917;
    static final int GREEN = 0xFF8FC274;
    static final int AMBER = 0xFFD2A157;
    static final int ROSE = 0xFFC46A63;
    static final int STEEL = 0xFF5C9FC2;
    static final int BONE = 0xFFE9E2DA;
    static final int ASH = 0xFF9E9892;
    static final int ASH_DARK = 0xFF77716A;

    private static final int ACTION_NONE = 0;
    private static final int ACTION_RESPAWN = 1;
    private static final int ACTION_KEEP = 2;
    private static final int ACTION_TITLE = 3;

    private final DeathScreen screen;
    private final ScreenHost host;
    private final double openedAt = UiClock.now();
    private boolean lockCorpse = true;
    private int hoveredAction = ACTION_NONE;
    private boolean submitted;

    public DeathScreenUi(DeathScreen screen) {
        this.screen = screen;
        this.host = new ScreenHost(this::build);
    }

    public ScreenHost host() {
        return host;
    }

    public void render(GuiGraphics graphics) {
        if (DeathScreenDataStorage.needsReinit()) {
            // 数据包晚于界面到达：界面内容都是按帧读取的，这里只需清掉标记
            DeathScreenDataStorage.setNeedsReinit(false);
        }
        host.render(screen, graphics);
    }

    private static DeathScreenDataStorage.DeathScreenData data() {
        return DeathScreenDataStorage.getData();
    }

    private float elapsed() {
        return (float) (UiClock.now() - openedAt);
    }

    /** 标题打完、开始滑动的时刻。 */
    private static float titleDoneMs() {
        return TYPE_DELAY_MS + TITLE.length() * TYPE_INTERVAL_MS + SETTLE_MS;
    }

    private static float contentDelay(float extra) {
        return titleDoneMs() + 180.0F + extra;
    }

    // ==================== 构建 ====================

    private UiNode<?> build() {
        return Ui.stack(new Backdrop(), Responsive.of(this::layout)).alignItems(Align.STRETCH);
    }

    private UiNode<?> layout(Responsive.Size size) {
        boolean compact = size == Responsive.Size.COMPACT;
        if (compact) {
            Box column = Ui.column(
                    Ui.spacer(),
                    new TitleBlock(false),
                    summaryCard().margin(0.0F, Theme.Space.LG, 0.0F, 0.0F),
                    Ui.spacer(),
                    reservePanel(),
                    actionRow()
            ).alignItems(Align.STRETCH).gap(Theme.Space.MD).padding(Theme.Space.XL, Theme.Space.LG);
            return Ui.row(column.grow(1.0F).maxWidth(420.0F)).justify(Justify.CENTER).alignItems(Align.STRETCH);
        }
        float rightWidth = size == Responsive.Size.WIDE ? 300.0F : 262.0F;
        Box left = Ui.column(
                Ui.spacer().grow(0.7F),
                new TitleBlock(true),
                summaryCard().margin(0.0F, Theme.Space.LG, 0.0F, 0.0F).maxWidth(340.0F),
                Ui.spacer(),
                new ActionHint()
        ).alignItems(Align.START).grow(1.0F).basis(0.0F);
        Box right = Ui.column(
                minimapPanel(),
                Ui.spacer(),
                reservePanel(),
                actionRow()
        ).alignItems(Align.STRETCH).gap(Theme.Space.MD).width(rightWidth);
        return Ui.row(left, right).alignItems(Align.STRETCH).gap(Theme.Space.XL)
                .padding(Theme.Space.XXL, Theme.Space.XL);
    }

    private UiNode<?> summaryCard() {
        Box card = Ui.row(
                Ui.stack().width(3.0F).radius(1.5F).background(UiColor.withAlpha(0xFFB83B37, 0.85F)),
                Ui.column(
                        Text.of("死亡原因").style(TextStyle.CAPTION_STRONG).color(ASH).singleLine(),
                        Text.of(() -> data().deathMessage()).style(TextStyle.BODY).color(0xFFDAD4CC).maxLines(2)
                ).gap(3.0F).grow(1.0F).shrink(1.0F).padding(Theme.Space.MD, Theme.Space.SM)
        ).alignItems(Align.STRETCH).radius(Theme.Radius.SM).background(0x40050608);
        return card.enter(EnterEffect.FADE_UP.delayed(contentDelay(0.0F)));
    }

    private UiNode<?> minimapPanel() {
        Box panel = VanillaChrome.glassPanel(0xFFB83B37).column().alignItems(Align.STRETCH).gap(Theme.Space.SM)
                .padding(Theme.Space.MD);
        panel.add(
                Ui.row(Icon.of(Icons.PIN, 11.0F).color(RED),
                        Text.of("尸体位置").style(TextStyle.LABEL_STRONG).color(BONE).singleLine(),
                        Ui.spacer(),
                        Text.of(() -> Component.literal(formatDimension(data().dimension()))).style(TextStyle.CAPTION)
                                .color(ASH).singleLine()).gap(Theme.Space.SM).alignItems(Align.CENTER),
                new CorpseMap().height(124.0F),
                Text.of(() -> Component.literal(String.format(Locale.ROOT, "X %d   Y %d   Z %d", (int) data().deathX(),
                        (int) data().deathY(), (int) data().deathZ()))).style(TextStyle.CAPTION).color(ASH).singleLine()
        );
        return panel.enter(EnterEffect.FADE_LEFT.delayed(contentDelay(60.0F)));
    }

    private UiNode<?> reservePanel() {
        Text label = Text.of(() -> Component.literal(previewLabel())).style(TextStyle.LABEL_STRONG).singleLine();
        label.onUpdate(() -> label.color(accentFor(hoveredAction)));
        Text value = Text.of(() -> Component.literal(previewValue())).style(TextStyle.LABEL_STRONG).color(BONE).singleLine();
        Text hint = Text.of(() -> Component.literal(previewHint())).style(TextStyle.CAPTION).color(ASH).maxLines(2);
        Box panel = new ReservePanel().column().alignItems(Align.STRETCH).gap(Theme.Space.SM).padding(Theme.Space.MD);
        panel.add(Ui.row(label, Ui.spacer(), value).alignItems(Align.CENTER),
                new ReserveBar().height(7.0F),
                hint,
                new CorpseToggle());
        return panel.enter(EnterEffect.FADE_LEFT.delayed(contentDelay(120.0F)));
    }

    private UiNode<?> actionRow() {
        Box row = Ui.row(
                new ActionButton(ACTION_RESPAWN, Icons.RESPAWN, "重生", GREEN),
                new ActionButton(ACTION_KEEP, Icons.BAG, "保留物品", AMBER),
                new ActionButton(ACTION_TITLE, Icons.HOME, "返回标题", ROSE)
        ).gap(Theme.Space.SM).alignItems(Align.STRETCH);
        return row.enter(EnterEffect.FADE_UP.delayed(contentDelay(180.0F)));
    }

    // ==================== 文案与数值 ====================

    private static int accentFor(int action) {
        return switch (action) {
            case ACTION_RESPAWN -> GREEN;
            case ACTION_KEEP -> AMBER;
            case ACTION_TITLE -> ROSE;
            default -> STEEL;
        };
    }

    private float previewCost() {
        DeathScreenDataStorage.DeathScreenData data = data();
        return switch (hoveredAction) {
            case ACTION_RESPAWN -> TemplateReconstructionRules.standardCharge(data.respawnPoint(), data.normalCost());
            case ACTION_KEEP -> data.keepInventoryCost();
            default -> 0.0F;
        };
    }

    private boolean previewing() {
        return hoveredAction == ACTION_RESPAWN || hoveredAction == ACTION_KEEP;
    }

    private String previewLabel() {
        return switch (hoveredAction) {
            case ACTION_RESPAWN -> "重生扣除  " + format1(previewCost());
            case ACTION_KEEP -> "保留扣除  " + format1(previewCost());
            case ACTION_TITLE -> "返回标题";
            default -> "模板重建余量";
        };
    }

    private String previewValue() {
        float current = data().respawnPoint();
        if (previewing()) {
            return "剩余 " + format1(Math.max(0.0F, current - previewCost()));
        }
        return String.format(Locale.ROOT, "%.0f / 100", current);
    }

    private String previewHint() {
        DeathScreenDataStorage.DeathScreenData data = data();
        float current = data.respawnPoint();
        float cost = previewCost();
        if (previewing()) {
            if (current < cost) {
                return "余量不足，还差 " + format1(cost - current);
            }
            int times = TemplateReconstructionRules.remainingReconstructions(Math.max(0.0F, current - cost), data.normalCost());
            String type = data.isInfected() ? "感染者" : "幸存者";
            String corpse = hoveredAction == ACTION_RESPAWN ? (lockCorpse ? " · 尸体仅自己可取" : " · 尸体允许他人拾取") : "";
            return "确认后作为" + type + (hoveredAction == ACTION_KEEP ? "保留物品重生" : "重生") + " · 之后可复活 " + times + " 次" + corpse;
        }
        if (hoveredAction == ACTION_TITLE) {
            return "返回标题不会消耗模板重建余量";
        }
        return "预计剩余复活次数  " + TemplateReconstructionRules.remainingReconstructions(current, data.normalCost());
    }

    private static String format1(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String formatDimension(String dimension) {
        return switch (dimension == null ? "" : dimension) {
            case "minecraft:overworld" -> "主世界";
            case "minecraft:the_nether" -> "下界";
            case "minecraft:the_end" -> "末地";
            default -> dimension == null ? "未知" : dimension;
        };
    }

    // ==================== 动作 ====================

    private boolean enabled(int action) {
        DeathScreenDataStorage.DeathScreenData data = data();
        return switch (action) {
            case ACTION_RESPAWN -> TemplateReconstructionRules.canReconstruct(data.respawnPoint());
            case ACTION_KEEP -> data.respawnPoint() >= data.keepInventoryCost();
            default -> true;
        };
    }

    private String costLabel(int action) {
        DeathScreenDataStorage.DeathScreenData data = data();
        return switch (action) {
            case ACTION_RESPAWN -> "-" + format1(TemplateReconstructionRules.standardCharge(data.respawnPoint(), data.normalCost()));
            case ACTION_KEEP -> "-" + format1(data.keepInventoryCost());
            default -> "不消耗余量";
        };
    }

    private void perform(int action) {
        // 内容尚未浮现时忽略点击，避免死亡瞬间的连点误触
        if (submitted || elapsed() < contentDelay(300.0F) || !enabled(action)) {
            return;
        }
        DeathScreenDataStorage.DeathScreenData data = data();
        switch (action) {
            case ACTION_RESPAWN -> {
                submitted = true;
                DreamingFishCore_NetworkManager.sendToServer(new Packet_NormalRespawnRequest(data.deathId(), lockCorpse));
            }
            case ACTION_KEEP -> {
                submitted = true;
                DreamingFishCore_NetworkManager.sendToServer(new Packet_KeepInventoryRequest(data.deathId()));
            }
            case ACTION_TITLE -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level != null) {
                    minecraft.level.disconnect();
                }
                minecraft.disconnect();
                minecraft.setScreen(null);
            }
            default -> {
            }
        }
        // 重生请求发出后若服务端拒绝（例如余量变化），一秒后允许再次尝试
        if (submitted) {
            double sentAt = UiClock.now();
            host.ui().content().onUpdate(() -> {
                if (submitted && UiClock.now() - sentAt > 1000.0) {
                    submitted = false;
                }
            });
        }
    }

    // ==================== 部件 ====================

    /** 褪色遮罩：画面逐渐变灰变暗，上下压暗，偶有一道暗红的信号线扫过。 */
    private final class Backdrop extends UiNode<Backdrop> {
        Backdrop() {
            pointerEvents(false);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            float w = width();
            float h = height();
            float p = Easing.OUT_CUBIC.apply(Math.min(1.0F, elapsed() / 1300.0F));
            float alpha = (72.0F + p * 156.0F) / 255.0F;
            canvas.shape(0.0F, 0.0F, w, h).verticalGradient(UiColor.withAlpha(0xFF101113, alpha),
                    UiColor.withAlpha(0xFF0B0C0E, Math.min(1.0F, alpha + 0.04F))).draw();
            canvas.shape(0.0F, 0.0F, w, h).radial(0x00000000, UiColor.withAlpha(0xFF000000, 0.55F * p),
                    w * 0.5F, h * 0.45F, (float) Math.hypot(w, h) * 0.62F).draw();
            canvas.shape(0.0F, 0.0F, w, 54.0F).verticalGradient(UiColor.withAlpha(0xFF000000, 0.34F * p), 0x00000000).draw();
            canvas.shape(0.0F, h - 68.0F, w, 68.0F).verticalGradient(0x00000000, UiColor.withAlpha(0xFF000000, 0.34F * p)).draw();
            double t = UiClock.now() / 1000.0;
            for (int i = 0; i < 2; i++) {
                float y = (float) (((t * (9.0 + i * 5.0) + i * 0.37 * h) % (h + 20.0)) - 10.0);
                canvas.shape(0.0F, y, w, 1.0F).horizontalGradient(0x00D23E3A, UiColor.withAlpha(RED, 0.07F * p)).draw();
            }
        }
    }

    /** 逐字打出的标题，打完后从屏幕中央滑到左栏。 */
    private final class TitleBlock extends UiNode<TitleBlock> {
        private final boolean slide;

        TitleBlock(boolean slide) {
            this.slide = slide;
            pointerEvents(false);
        }

        private float fullWidth() {
            return Minecraft.getInstance().font.width("§l" + TITLE) * TITLE_SCALE;
        }

        @Override
        protected void measureContent(float availableWidth, float availableHeight, Size out) {
            out.set(Math.min(availableWidth, fullWidth()), 9.0F * TITLE_SCALE + 12.0F);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            float elapsed = elapsed();
            int visible = Math.max(0, Math.min(TITLE.length(), (int) ((elapsed - TYPE_DELAY_MS) / TYPE_INTERVAL_MS)));
            if (visible == 0 && elapsed < TYPE_DELAY_MS) {
                return;
            }
            float fade = Math.min(1.0F, 0.25F + Easing.OUT_CUBIC.apply(Math.min(1.0F, elapsed / 1300.0F)) * 0.75F);
            float full = fullWidth();
            float scale = Math.min(TITLE_SCALE, TITLE_SCALE * width() / Math.max(1.0F, full));
            float drawnWidth = full * scale / TITLE_SCALE;
            float offset;
            if (slide && root() != null) {
                float centerOffset = root().width() * 0.5F - (guiLeft() + (guiRight() - guiLeft()) * 0.5F);
                float progress = Easing.OUT_CUBIC.apply(Math.max(0.0F, Math.min(1.0F, (elapsed - titleDoneMs()) / SLIDE_MS)));
                offset = centerOffset * (1.0F - progress) + (width() - drawnWidth) * 0.5F * (1.0F - progress);
            } else {
                offset = (width() - drawnWidth) * 0.5F;
            }
            String text = "§l" + TITLE.substring(0, visible);
            if (visible < TITLE.length() && (int) (elapsed / 260.0F) % 2 == 0) {
                text += "_";
            }
            canvas.text(text, offset + 1.2F, 1.2F, UiColor.withAlpha(0xFF000000, 0.8F * fade), scale, false);
            canvas.text(text, offset, 0.0F, UiColor.withAlpha(RED, fade), scale, false);
            float lineWidth = Math.min(drawnWidth, 34.0F + visible * 22.0F * scale / TITLE_SCALE);
            float lineY = 9.0F * scale + 7.0F;
            float lineX = slide ? offset : offset + (drawnWidth - lineWidth) * 0.5F;
            canvas.shape(lineX, lineY, lineWidth, 2.0F).radius(1.0F)
                    .horizontalGradient(UiColor.withAlpha(RED_DEEP, 0.9F * fade), UiColor.withAlpha(RED_DEEP, 0.2F * fade)).draw();
        }
    }

    /** 悬停重生选项时出现的说明卡。 */
    private final class ActionHint extends Box {
        private final Text action = Text.of("").style(TextStyle.LABEL).color(0xFFDCD5CA);
        private final AnimatedFloat shown = AnimatedFloat.spring(0.0F, Spring.SNAPPY);
        private int accent = GREEN;

        ActionHint() {
            column().gap(4.0F).padding(Theme.Space.LG, Theme.Space.MD).maxWidth(360.0F).radius(Theme.Radius.SM);
            pointerEvents(false);
            add(action, Text.of("最后一次标准重建可将余量扣至零；耗尽后的下一次死亡需要他人救援。").style(TextStyle.CAPTION)
                    .color(0xFFE0605A));
        }

        @Override
        protected void update() {
            boolean active = hoveredAction == ACTION_RESPAWN || hoveredAction == ACTION_KEEP;
            if (active) {
                accent = accentFor(hoveredAction);
                action.text(hoveredAction == ACTION_RESPAWN ? "消耗模板重建余量恢复身体，物品留在死亡地点的尸体中。"
                        : "足额消耗更多模板重建余量，同时保留随身物品。");
            }
            shown.set(active ? 1.0F : 0.0F);
            opacity(shown.get());
            translate((1.0F - shown.get()) * -8.0F, 0.0F);
        }

        @Override
        protected void paintBackground(UiCanvas canvas) {
            canvas.shape(0.0F, 0.0F, width(), height()).radius(radiusValue()).fill(0x6B050608).draw();
            canvas.shape(0.0F, 0.0F, 2.0F, height()).radius(1.0F).fill(UiColor.withAlpha(accent, 0.86F)).draw();
        }
    }

    /** 余量面板：描边颜色跟随悬停的选项变化。 */
    private final class ReservePanel extends Box {
        private final AnimatedFloat tint = AnimatedFloat.tween(0.0F, 180.0F, Easing.STANDARD);
        private int from = STEEL;
        private int to = STEEL;

        ReservePanel() {
            radius(Theme.Radius.LG);
        }

        @Override
        protected void update() {
            int target = accentFor(hoveredAction);
            if (target != to) {
                from = UiColor.lerp(from, to, tint.get());
                to = target;
                tint.snap(0.0F);
                tint.set(1.0F);
            }
        }

        @Override
        protected void paintBackground(UiCanvas canvas) {
            VanillaChrome.paintGlass(canvas, width(), height(), radiusValue(), UiColor.lerp(from, to, tint.get()));
        }
    }

    /** 余量条：悬停时把扣除部分标成红色，数值平滑过渡。 */
    private final class ReserveBar extends UiNode<ReserveBar> {
        private final AnimatedFloat kept = AnimatedFloat.spring(0.0F, Spring.SNAPPY);
        private final AnimatedFloat total = AnimatedFloat.spring(0.0F, Spring.GENTLE);

        ReserveBar() {
            pointerEvents(false);
        }

        @Override
        protected void update() {
            float current = Math.max(0.0F, Math.min(1.0F, data().respawnPoint() / 100.0F));
            float after = previewing() ? Math.max(0.0F, data().respawnPoint() - previewCost()) / 100.0F : current;
            total.set(current);
            kept.set(Math.max(0.0F, Math.min(1.0F, after)));
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            float w = width();
            float h = height();
            float r = h * 0.5F;
            canvas.shape(0.0F, 0.0F, w, h).radius(r).fill(0xFF161A1D).border(1.0F, 0x14FFFFFF).draw();
            float totalW = w * total.get();
            float keptW = w * kept.get();
            if (totalW > keptW + 0.5F) {
                canvas.shape(keptW - r, 0.0F, totalW - keptW + r, h).radius(0.0F, r, r, 0.0F)
                        .horizontalGradient(0xFFB0302B, 0xFFE0564F).draw();
            }
            if (keptW > 0.5F) {
                int accent = accentFor(hoveredAction);
                canvas.shape(0.0F, 0.0F, Math.max(h, keptW), h).radius(r)
                        .horizontalGradient(UiColor.darken(accent, 0.25F), accent).draw();
            }
            for (int i = 1; i < 10; i++) {
                canvas.fill(w * i / 10.0F, 1.0F, 0.5F, h - 2.0F, 0x30000000);
            }
        }
    }

    /** 尸体拾取权限开关。 */
    private final class CorpseToggle extends InteractiveNode<CorpseToggle> {
        private final Icon lock = Icon.of(Icons.LOCK, 10.0F);
        private final Text state = Text.of("").style(TextStyle.LABEL_STRONG).singleLine();
        private final Box pill;

        CorpseToggle() {
            row().alignItems(Align.CENTER).gap(Theme.Space.SM).padding(Theme.Space.SM, 4.0F).radius(Theme.Radius.MD);
            pill = Ui.stack(state).alignItems(Align.CENTER).padding(6.0F, 2.0F).radius(Theme.Radius.SM);
            add(lock, Text.of("尸体拾取权限").style(TextStyle.LABEL).color(0xFFD4DADB).singleLine().shrink(1.0F),
                    Ui.spacer(), pill, Text.of("切换").style(TextStyle.CAPTION).color(ASH).singleLine());
            cursor(Cursor.POINTER);
            onClick(() -> lockCorpse = !lockCorpse);
            tooltip(Component.literal("选择尸体中的物品是否允许其他玩家拾取"));
        }

        @Override
        protected void update() {
            int accent = lockCorpse ? 0xFF72B7D4 : AMBER;
            lock.icon(lockCorpse ? Icons.LOCK : Icons.USERS).color(accent);
            state.text(lockCorpse ? "仅自己" : "允许他人").color(accent);
            pill.background(UiColor.withAlpha(accent, 0.16F));
        }

        @Override
        protected void paintBackground(UiCanvas canvas) {
            float h = hover();
            canvas.shape(0.0F, 0.0F, width(), height()).radius(radiusValue())
                    .fill(UiColor.lerp(0x40141B20, 0x80253036, h))
                    .border(1.0F, UiColor.lerp(0x18FFFFFF, 0x50FFFFFF, h)).draw();
        }
    }

    /** 选择按钮：图标 + 名称 + 消耗；悬停时预演扣除。 */
    private final class ActionButton extends InteractiveNode<ActionButton> {
        private final int action;
        private final int accent;
        private final Icon icon;
        private final Text label;
        private final Text cost;

        ActionButton(int action, Icons iconType, String text, int accent) {
            this.action = action;
            this.accent = accent;
            icon = Icon.of(iconType, 12.0F);
            label = Text.of(text).style(TextStyle.LABEL_STRONG).singleLine();
            cost = Text.of(() -> Component.literal(costLabel(action))).style(TextStyle.CAPTION).singleLine();
            column().alignItems(Align.CENTER).gap(3.0F).padding(Theme.Space.SM, Theme.Space.MD).radius(Theme.Radius.MD);
            grow(1.0F).basis(0.0F);
            add(icon, label, cost);
            cursor(Cursor.POINTER);
            onClick(() -> perform(action));
            onHover(hovered -> {
                if (hovered) {
                    hoveredAction = action;
                } else if (hoveredAction == action) {
                    hoveredAction = ACTION_NONE;
                }
            });
        }

        @Override
        protected void update() {
            boolean enabled = enabled(action);
            disabled(!enabled);
            float h = enabled ? hover() : 0.0F;
            int tone = enabled ? accent : 0xFF5E5B57;
            icon.color(UiColor.lerp(UiColor.lerp(0xFFB9B2AA, tone, 0.5F), tone, h));
            label.color(enabled ? UiColor.lerp(0xFFD8D0C6, 0xFFF4EEE5, h) : ASH_DARK);
            cost.color(enabled ? UiColor.lerp(ASH, tone, h) : 0xFF5E5B57);
        }

        @Override
        protected void paintBackground(UiCanvas canvas) {
            boolean enabled = enabled(action);
            float h = enabled ? hover() : 0.0F;
            float w = width();
            float ht = height();
            canvas.shape(0.0F, 0.0F, w, ht).radius(radiusValue())
                    .fill(UiColor.lerp(0x9A0A0C0E, UiColor.lerp(0xE0101418, UiColor.withAlpha(accent, 0xE0), 0.12F), h))
                    .border(1.0F, UiColor.lerp(0x1EFFFFFF, UiColor.withAlpha(accent, 0.65F), h)).draw();
            float lineW = (w - 20.0F) * (0.3F + 0.7F * h);
            canvas.shape((w - lineW) * 0.5F, ht - 3.0F, lineW, 1.5F).radius(0.75F)
                    .fill(UiColor.withAlpha(enabled ? accent : 0xFF3F3F3F, enabled ? 0.35F + 0.65F * h : 0.4F)).draw();
            float press = press();
            if (press > 0.01F) {
                canvas.shape(0.0F, 0.0F, w, ht).radius(radiusValue()).fill(UiColor.withAlpha(accent, 0.15F * press)).draw();
            }
        }
    }

    /** 尸体周边俯视图：按地表方块的地图色采样，每秒刷新一次。 */
    private static final class CorpseMap extends UiNode<CorpseMap> {
        private static final int CELLS = 31;
        private static final int SAMPLE_STEP = 8;
        private final int[] colors = new int[CELLS * CELLS];
        private double sampledAt = -1.0E9;

        CorpseMap() {
            pointerEvents(false);
        }

        private void sample() {
            DeathScreenDataStorage.DeathScreenData data = data();
            ClientLevel level = Minecraft.getInstance().level;
            int baseX = (int) Math.floor(data.deathX());
            int baseZ = (int) Math.floor(data.deathZ());
            for (int row = 0; row < CELLS; row++) {
                for (int col = 0; col < CELLS; col++) {
                    int worldX = baseX + (col - CELLS / 2) * SAMPLE_STEP;
                    int worldZ = baseZ + (row - CELLS / 2) * SAMPLE_STEP;
                    colors[row * CELLS + col] = sampleColor(level, worldX, worldZ, data.deathY());
                }
            }
        }

        private static int sampleColor(ClientLevel level, int worldX, int worldZ, double fallbackY) {
            if (level == null) {
                return 0xFF16191B;
            }
            try {
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
                if (surfaceY <= level.getMinBuildHeight()) {
                    surfaceY = (int) Math.floor(fallbackY);
                }
                BlockPos pos = new BlockPos(worldX, Math.max(level.getMinBuildHeight(), surfaceY - 1), worldZ);
                BlockState state = level.getBlockState(pos);
                MapColor mapColor = state.getMapColor(level, pos);
                if (mapColor == MapColor.NONE) {
                    return 0xFF101316;
                }
                int rgb = mapColor.col;
                int shade = Math.floorMod(worldX * 13 + worldZ * 7, 24) - 12;
                int r = Math.max(0, Math.min(255, ((rgb >> 16) & 255) + shade));
                int g = Math.max(0, Math.min(255, ((rgb >> 8) & 255) + shade));
                int b = Math.max(0, Math.min(255, (rgb & 255) + shade));
                // 褪色处理，与死亡界面的灰调一致
                int gray = (r * 3 + g * 6 + b) / 10;
                r = (r + gray * 2) / 3;
                g = (g + gray * 2) / 3;
                b = (b + gray * 2) / 3;
                return 0xFF000000 | (r << 16) | (g << 8) | b;
            } catch (RuntimeException ignored) {
                return 0xFF11161A;
            }
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            if (UiClock.now() - sampledAt > 1000.0) {
                sampledAt = UiClock.now();
                sample();
            }
            float size = Math.min(width(), height());
            float x0 = (width() - size) * 0.5F;
            float cell = size / CELLS;
            canvas.pushClip(x0, 0.0F, size, size, Theme.Radius.MD);
            for (int row = 0; row < CELLS; row++) {
                for (int col = 0; col < CELLS; col++) {
                    canvas.fill(x0 + col * cell, row * cell, cell + 0.35F, cell + 0.35F,
                            UiColor.withAlpha(colors[row * CELLS + col], 0.85F));
                }
            }
            canvas.shape(x0, 0.0F, size, size).radial(0x00000000, 0x90000000, x0 + size * 0.5F, size * 0.5F, size * 0.72F).draw();
            canvas.popClip();
            canvas.shape(x0, 0.0F, size, size).radius(Theme.Radius.MD).fill(0).border(1.0F, 0x30FFFFFF).draw();

            float cx = x0 + size * 0.5F;
            float cy = size * 0.5F;
            double t = UiClock.now() / 1600.0 % 1.0;
            float pulse = (float) t;
            canvas.shape(cx - 4.0F - pulse * 14.0F, cy - 4.0F - pulse * 14.0F, 8.0F + pulse * 28.0F, 8.0F + pulse * 28.0F)
                    .radius(9999.0F).fill(0).border(1.0F, UiColor.withAlpha(RED, 0.7F * (1.0F - pulse))).draw();
            int ring = 0xB0E6DDD1;
            canvas.fill(cx - 11.0F, cy - 0.5F, 6.0F, 1.0F, ring);
            canvas.fill(cx + 5.0F, cy - 0.5F, 6.0F, 1.0F, ring);
            canvas.fill(cx - 0.5F, cy - 11.0F, 1.0F, 6.0F, ring);
            canvas.fill(cx - 0.5F, cy + 5.0F, 1.0F, 6.0F, ring);
            canvas.line(cx - 4.0F, cy - 4.0F + 0.8F, cx + 4.0F, cy + 4.0F + 0.8F, 2.6F, 0xC0000000, true);
            canvas.line(cx + 4.0F, cy - 4.0F + 0.8F, cx - 4.0F, cy + 4.0F + 0.8F, 2.6F, 0xC0000000, true);
            canvas.line(cx - 4.0F, cy - 4.0F, cx + 4.0F, cy + 4.0F, 2.0F, RED, true);
            canvas.line(cx + 4.0F, cy - 4.0F, cx - 4.0F, cy + 4.0F, 2.0F, RED, true);
        }
    }
}
