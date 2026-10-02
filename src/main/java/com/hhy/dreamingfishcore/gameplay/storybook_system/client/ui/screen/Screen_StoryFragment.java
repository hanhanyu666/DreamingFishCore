package com.hhy.dreamingfishcore.gameplay.storybook_system.client.ui.screen;

import com.hhy.dreamingfishcore.client.ui.framework.anim.AnimatedFloat;
import com.hhy.dreamingfishcore.client.ui.framework.anim.Easing;
import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.Justify;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.screen.UiScreen;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ScrollView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import net.minecraft.network.chat.Component;

/**
 * 残页阅读：一张边缘撕裂的旧纸，标题、记录信息与正文，可滚动阅读。
 */
public class Screen_StoryFragment extends UiScreen {
    private static final int PAPER = 0xFFF1E3BE;
    private static final int PAPER_DARK = 0xFFE6D2A6;
    private static final int PAPER_BORDER = 0xB08E6B3D;
    private static final int TITLE = 0xFF6A4321;
    private static final int META = 0xFF7B4F29;
    private static final int BODY = 0xFF2A1F14;
    private static final int TEAR_DEPTH = 10;

    private final int fragmentId;
    private final int stageId;
    private final int chapterId;
    private final String title;
    private final String content;
    private final String time;
    private final String authorName;
    private final String source;
    private final String observationSpan;
    private final String sample;
    private final String conditions;
    private final String clueLabel;
    private final AnimatedFloat reveal = AnimatedFloat.tween(0.0F, 260.0F, Easing.DECELERATE);

    public Screen_StoryFragment(String clueId, int fragmentId, int stageId, int chapterId, String title, String content, String time, String authorName, String source, String observationSpan, String sample, String conditions) {
        super(Component.literal("残页"));
        this.fragmentId = fragmentId;
        this.stageId = stageId;
        this.chapterId = chapterId;
        this.title = title == null ? "未命名残页" : title;
        this.content = content == null ? "" : content;
        this.time = time == null ? "" : time;
        this.authorName = authorName == null ? "" : authorName;
        this.source = source == null ? "" : source;
        this.observationSpan = observationSpan == null ? "" : observationSpan;
        this.sample = sample == null ? "" : sample;
        this.conditions = conditions == null ? "" : conditions;
        this.clueLabel = fragmentId > 0 ? String.valueOf(fragmentId)
                : (clueId == null || clueId.isBlank() ? ""
                        : clueId.substring(clueId.lastIndexOf('/') + 1));
        setBackground(Background.NONE);
    }

    @Override
    protected UiNode<?> build() {
        reveal.set(1.0F);
        Box meta = Ui.column(
                metaLine("时间", time.isEmpty() ? "未知" : time),
                metaLine("记录者", authorName),
                metaLine("出处", Screen_StoryBookCatalog.chapterLabel(chapterId) + " · 阶段 " + stageId
                        + (clueLabel.isEmpty() ? "" : " · 片段 " + clueLabel)),
                metaLine("来源", source.isEmpty() ? "未标注" : source),
                metaLine("观察跨度", observationSpan.isEmpty() ? "未标注" : observationSpan),
                metaLine("样本", sample.isEmpty() ? "未标注" : sample),
                metaLine("观察条件", conditions.isEmpty() ? "未标注" : conditions)
        ).gap(3.0F);

        Box body = Ui.column().gap(6.0F);
        for (String paragraph : content.split("\\n")) {
            if (paragraph.trim().isEmpty()) {
                body.add(Ui.space(2.0F));
            } else {
                body.add(Text.of(paragraph).style(TextStyle.BODY.withLineGap(3.0F)).color(BODY));
            }
        }

        ScrollView scroll = ScrollView.of(
                Text.of(title).style(TextStyle.HEADLINE).color(TITLE),
                meta,
                Ui.stack().height(1.0F).background(0x907C5A33).margin(0.0F, 4.0F),
                body
        ).gap(Theme.Space.MD).padding(26.0F, 24.0F, 30.0F, 18.0F);
        scroll.grow(1.0F).basis(0.0F);

        Box paper = new TornPaper().column().alignItems(Align.STRETCH);
        paper.add(scroll, Ui.row(Ui.spacer(), Text.of("滚轮阅读 · ESC 关闭").style(TextStyle.CAPTION).color(META).singleLine())
                .padding(26.0F, 0.0F, 30.0F, 16.0F));
        paper.maxWidth(620.0F).grow(1.0F).enter(new EnterEffect(0, 10, 0.98F, 0, 320, 0, Easing.EMPHASIZED));

        return Ui.stack(new Overlay(),
                Ui.row(paper).justify(Justify.CENTER).alignItems(Align.STRETCH).padding(Theme.Space.XL, Theme.Space.XL))
                .alignItems(Align.STRETCH);
    }

    private static UiNode<?> metaLine(String label, String value) {
        return Ui.row(Text.of(label).style(TextStyle.LABEL_STRONG).color(META).singleLine().width(44.0F),
                Text.of(value).style(TextStyle.LABEL).color(META).singleLine().shrink(1.0F)).gap(Theme.Space.SM);
    }

    private final class Overlay extends UiNode<Overlay> {
        Overlay() {
            pointerEvents(false);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            canvas.fill(0.0F, 0.0F, width(), height(), UiColor.multiplyAlpha(0xC0150F09, reveal.get()));
        }
    }

    /** 撕边纸：左右边缘按噪声函数凹凸，带投影与细边。 */
    private static final class TornPaper extends Box {
        private static int tearInset(int row, int side) {
            double base = Math.sin((row + side * 23) * 0.14) * 4.2;
            double detail = Math.cos((row + side * 41) * 0.37) * 2.4;
            int inset = (int) Math.round(Math.abs(base + detail)) + (row % 24 == 0 ? TEAR_DEPTH / 2 : 0);
            return Math.max(2, Math.min(TEAR_DEPTH, inset));
        }

        @Override
        protected void paintBackground(UiCanvas canvas) {
            float w = width();
            float h = height();
            int rows = (int) h;
            for (int i = 0; i < rows; i += 2) {
                float left = tearInset(i, 0) * 0.5F;
                float right = tearInset(i, 1) * 0.5F;
                canvas.fill(6.0F + left, 8.0F + i, w - left - right, 2.0F, 0x405A3D1E);
            }
            for (int i = 0; i < rows; i += 2) {
                float left = tearInset(i, 0);
                float right = tearInset(i, 1);
                float t = i / Math.max(1.0F, h);
                int color = UiColor.lerp(PAPER, PAPER_DARK, t);
                canvas.fill(left, i, w - left - right, Math.min(2.0F, h - i), color);
                canvas.fill(left, i, 1.0F, 2.0F, PAPER_BORDER);
                canvas.fill(w - right - 1.0F, i, 1.0F, 2.0F, PAPER_BORDER);
            }
        }
    }
}
