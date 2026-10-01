package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.ui.framework.anim.AnimatedFloat;
import com.hhy.dreamingfishcore.client.ui.framework.anim.Spring;
import com.hhy.dreamingfishcore.client.ui.framework.core.UiClock;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.Size;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import java.util.List;
import java.util.function.Supplier;

/**
 * 终端里带世界观色彩的小部件：中继信号线、信号格、阶段时间线、模板储备格与扫描头像框。
 */
final class TerminalWidgets {
    private TerminalWidgets() {
    }

    // ==================== 中继信号线 ====================

    /** 顶栏下方的分隔线：一束信号脉冲周期性地从左向右掠过，经过中段时跳出心电般的起伏。 */
    static UiNode<?> relayLine() {
        return new RelayLine();
    }

    private static final class RelayLine extends UiNode<RelayLine> {
        RelayLine() {
            height(7.0F);
            pointerEvents(false);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            float w = width();
            float y = 3.0F;
            float inset = 10.0F;
            float span = w - inset * 2.0F;
            canvas.fill(inset, y, span, 1.0F, 0x10FFFFFF);
            canvas.shape(inset, y, Math.min(180.0F, span * 0.3F), 1.0F)
                    .horizontalGradient(UiColor.withAlpha(TerminalUi.CYAN, 0.5F), UiColor.withAlpha(TerminalUi.CYAN, 0.0F)).draw();

            double period = 3600.0;
            float phase = (float) ((UiClock.now() % period) / period);
            float head = inset + phase * (span + 120.0F) - 60.0F;
            float tail = 90.0F;
            float from = Math.max(inset, head - tail);
            float to = Math.min(inset + span, head);
            if (to > from) {
                canvas.shape(from, y - 0.25F, to - from, 1.5F).radius(0.75F)
                        .horizontalGradient(UiColor.withAlpha(TerminalUi.CYAN, 0.0F), UiColor.withAlpha(TerminalUi.CYAN, 0.9F)).draw();
            }
            // 掠过中段时的一次起伏
            float center = inset + span * 0.5F;
            float distance = Math.abs(head - center);
            if (distance < 40.0F) {
                float a = 1.0F - distance / 40.0F;
                int color = UiColor.withAlpha(TerminalUi.CYAN, 0.85F * a);
                float x = center;
                canvas.line(x - 9.0F, y + 0.5F, x - 5.0F, y + 0.5F, 1.0F, color, true);
                canvas.line(x - 5.0F, y + 0.5F, x - 3.0F, y - 2.5F, 1.0F, color, true);
                canvas.line(x - 3.0F, y - 2.5F, x, y + 3.0F, 1.0F, color, true);
                canvas.line(x, y + 3.0F, x + 2.0F, y + 0.5F, 1.0F, color, true);
                canvas.line(x + 2.0F, y + 0.5F, x + 9.0F, y + 0.5F, 1.0F, color, true);
            }
            if (head > inset && head < inset + span) {
                canvas.circle(head, y + 0.5F, 1.6F, UiColor.withAlpha(0xFFE8FBFF, 0.9F));
                canvas.circle(head, y + 0.5F, 4.0F, UiColor.withAlpha(TerminalUi.CYAN, 0.18F));
            }
        }
    }

    // ==================== 信号格 ====================

    /** 四格信号：按服务器 TPS 点亮，卡顿时转黄、转红。 */
    static UiNode<?> signalBars(Supplier<Float> tps) {
        return new SignalBars(tps);
    }

    private static final class SignalBars extends UiNode<SignalBars> {
        private final Supplier<Float> tps;

        SignalBars(Supplier<Float> tps) {
            this.tps = tps;
            size(11.0F, 9.0F);
            pointerEvents(false);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            float value = tps.get();
            int bars = !Float.isFinite(value) ? 0 : value >= 18.0F ? 4 : value >= 15.0F ? 3 : value >= 10.0F ? 2 : 1;
            int color = bars == 0 ? TerminalUi.STEEL : bars >= 4 ? TerminalUi.MINT : bars == 3 ? TerminalUi.GOLD : TerminalUi.ROSE;
            for (int i = 0; i < 4; i++) {
                float bh = 3.0F + i * 2.0F;
                canvas.shape(i * 3.0F, height() - bh, 2.0F, bh).radius(0.6F)
                        .fill(i < bars ? color : UiColor.withAlpha(color, 0.22F)).draw();
            }
        }
    }

    // ==================== 阶段时间线 ====================

    /** 故事阶段时间线：已走过的阶段实心，当前阶段带呼吸光环，尚未揭示的阶段为空心。 */
    static UiNode<?> stageTimeline(Supplier<List<StoryStageData>> stages, Supplier<StoryStageData> current) {
        return new StageTimeline(stages, current);
    }

    private static final class StageTimeline extends UiNode<StageTimeline> {
        private final Supplier<List<StoryStageData>> stages;
        private final Supplier<StoryStageData> current;
        private final AnimatedFloat progress = AnimatedFloat.spring(0.0F, Spring.GENTLE);

        StageTimeline(Supplier<List<StoryStageData>> stages, Supplier<StoryStageData> current) {
            this.stages = stages;
            this.current = current;
            height(22.0F);
            pointerEvents(false);
        }

        @Override
        protected void measureContent(float availableWidth, float availableHeight, Size out) {
            out.set(Math.min(availableWidth, 160.0F), 22.0F);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            List<StoryStageData> list = stages.get();
            StoryStageData now = current.get();
            int count = Math.max(1, list.size());
            int currentIndex = 0;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) == now || (now != null && list.get(i).getStageNumber() == now.getStageNumber())) {
                    currentIndex = i;
                }
            }
            // 末端多留一个“未知”节点，暗示故事还在继续
            int nodes = count + 1;
            float w = width();
            float y = 6.0F;
            float left = 6.0F;
            float right = w - 6.0F;
            float step = nodes > 1 ? (right - left) / (nodes - 1) : 0.0F;
            progress.set(currentIndex);
            float reached = left + progress.get() * step;
            canvas.shape(left, y - 0.5F, right - left, 1.0F).fill(0x22FFFFFF).draw();
            canvas.shape(left, y - 0.75F, Math.max(0.0F, reached - left), 1.5F).radius(0.75F)
                    .horizontalGradient(UiColor.withAlpha(TerminalUi.MINT, 0.35F), TerminalUi.MINT).draw();
            Font font = Minecraft.getInstance().font;
            double t = UiClock.now() / 1000.0;
            for (int i = 0; i < nodes; i++) {
                float x = left + i * step;
                boolean unknown = i >= count;
                boolean isCurrent = i == currentIndex && !unknown;
                boolean past = i < currentIndex;
                if (isCurrent) {
                    float pulse = (float) ((t % 1.8) / 1.8);
                    canvas.circle(x, y, 3.5F + pulse * 5.0F, UiColor.withAlpha(TerminalUi.MINT, 0.35F * (1.0F - pulse)));
                    canvas.circle(x, y, 4.0F, UiColor.withAlpha(TerminalUi.MINT, 0.25F));
                    canvas.circle(x, y, 2.6F, TerminalUi.MINT);
                } else if (past) {
                    canvas.circle(x, y, 2.2F, UiColor.withAlpha(TerminalUi.MINT, 0.85F));
                } else {
                    canvas.shape(x - 2.5F, y - 2.5F, 5.0F, 5.0F).radius(2.5F).fill(0xFF0D141A)
                            .border(1.0F, unknown ? 0x40FFFFFF : 0x66FFFFFF).draw();
                }
                String label = unknown ? "?" : String.format("%02d", list.get(i).getStageNumber());
                float lw = font.width(label) * 0.6F;
                int color = isCurrent ? TerminalUi.MINT : past ? 0xB0C9D6DE : 0x66C9D6DE;
                canvas.text(label, x - lw / 2.0F, y + 6.5F, color, 0.6F, false);
            }
        }
    }

    // ==================== 模板储备格 ====================

    /**
     * 模板重建余量以十格储备单元显示，每格 10 点；不足一次重建时转为警示色。
     */
    static UiNode<?> reserveCells(Supplier<Float> points, Supplier<Float> cost) {
        return new ReserveCells(points, cost);
    }

    private static final class ReserveCells extends UiNode<ReserveCells> {
        private final Supplier<Float> points;
        private final Supplier<Float> cost;
        private final AnimatedFloat shown = AnimatedFloat.spring(0.0F, Spring.GENTLE);

        ReserveCells(Supplier<Float> points, Supplier<Float> cost) {
            this.points = points;
            this.cost = cost;
            height(7.0F);
            pointerEvents(false);
        }

        @Override
        protected void measureContent(float availableWidth, float availableHeight, Size out) {
            out.set(availableWidth, 7.0F);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            float value = Math.max(0.0F, Math.min(100.0F, points.get()));
            shown.set(value);
            float display = shown.get();
            float need = Math.max(1.0F, cost.get());
            int color = value < need ? TerminalUi.ROSE : value < need * 2.0F ? TerminalUi.GOLD : TerminalUi.CYAN;
            int cells = 10;
            float gap = 2.0F;
            float cellW = (width() - gap * (cells - 1)) / cells;
            float h = height();
            for (int i = 0; i < cells; i++) {
                float x = i * (cellW + gap);
                float fill = Math.max(0.0F, Math.min(1.0F, (display - i * 10.0F) / 10.0F));
                canvas.shape(x, 0.0F, cellW, h).radius(1.5F).fill(0xFF0B1116).border(1.0F, UiColor.withAlpha(color, 0.25F)).draw();
                if (fill > 0.0F) {
                    canvas.shape(x + 1.0F, 1.0F, Math.max(0.0F, (cellW - 2.0F) * fill), h - 2.0F).radius(1.0F)
                            .verticalGradient(UiColor.lighten(color, 0.2F), UiColor.withAlpha(color, 0.8F)).draw();
                }
            }
        }
    }

    // ==================== 扫描头像框 ====================

    /** 头像外的取景框：四角括号与缓慢旋转的扫描弧。 */
    static Box scanFrame(UiNode<?> content, Supplier<Integer> color) {
        return new ScanFrame(content, color);
    }

    private static final class ScanFrame extends Box {
        private final Supplier<Integer> color;

        ScanFrame(UiNode<?> content, Supplier<Integer> color) {
            this.color = color;
            stack().padding(4.0F);
            add(content);
        }

        @Override
        protected void paintOverlay(UiCanvas canvas) {
            int accent = color.get();
            float w = width();
            float h = height();
            TerminalChrome.paintCornerTicks(canvas, w, h, 5.0F, 0.0F, UiColor.withAlpha(accent, 0.8F));
            float t = (float) (UiClock.now() / 1000.0);
            float r = Math.min(w, h) * 0.5F + 1.5F;
            canvas.arc(w * 0.5F, h * 0.5F, r, 1.0F, t * 0.9F, 0.9F, UiColor.withAlpha(accent, 0.0F),
                    UiColor.withAlpha(accent, 0.7F));
        }
    }
}
