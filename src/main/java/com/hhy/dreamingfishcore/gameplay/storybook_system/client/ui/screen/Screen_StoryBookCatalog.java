package com.hhy.dreamingfishcore.gameplay.storybook_system.client.ui.screen;

import com.hhy.dreamingfishcore.client.ui.framework.anim.AnimatedFloat;
import com.hhy.dreamingfishcore.client.ui.framework.anim.Easing;
import com.hhy.dreamingfishcore.client.ui.framework.core.UiSounds;
import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.Cursor;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.Justify;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.screen.UiScreen;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.client.ui.framework.widget.CustomPaint;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Dynamic;
import com.hhy.dreamingfishcore.client.ui.framework.widget.InteractiveNode;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Responsive;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.gameplay.storybook_system.StoryBookEntryViewData;
import com.hhy.dreamingfishcore.gameplay.storybook_system.network.Packet_UpdateStoryBookOrder;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 随记本：摊开的手账。先按章节浏览，再进入章节查看残页卡片；卡片可拖动到另一张卡片上交换顺序，
 * 轻点卡片阅读。
 */
public class Screen_StoryBookCatalog extends UiScreen {
    static final int PAGE = 0xFFF0DFC1;
    static final int PAGE_EDGE = 0xFFE3CDA6;
    static final int INK_TITLE = 0xFF51341D;
    static final int INK_TEXT = 0xFF6D4A2A;
    static final int INK_ACCENT = 0xFF8A5B31;
    static final int PIN = 0xFFC44B36;
    private static final int COVER = 0xFF6B4B2D;
    private static final int COVER_DARK = 0xFF4A3119;
    private static final int PAPER_TEXT = 0xFFF4E7CF;
    private static final int PAPER_MUTED = 0xFFD6C1A0;

    private final List<StoryBookEntryViewData> order = new ArrayList<>();
    private final AnimatedFloat reveal = AnimatedFloat.tween(0.0F, 360.0F, Easing.EMPHASIZED);
    private Integer chapter;
    private int page;
    private int version;
    private FragmentCard dragCard;

    public Screen_StoryBookCatalog(List<StoryBookEntryViewData> entries) {
        super(Component.literal("随记本"));
        order.addAll(entries);
        setBackground(Background.NONE);
    }

    private record Key(Integer chapter, int page, int version, Responsive.Size size) {
    }

    @Override
    protected UiNode<?> build() {
        reveal.set(1.0F);
        UiNode<?> book = Responsive.of(size -> Dynamic.of(() -> new Key(chapter, page, version, size), this::spread));
        Text title = Text.of("随记本").style(TextStyle.HEADLINE).color(PAPER_TEXT).centered().singleLine();
        Text subtitle = Text.of(() -> Component.literal(chapter == null ? "先翻看章节，再进入该章节的片段"
                : chapterLabel(chapter) + " · 点击卡片阅读，拖动卡片交换顺序")).style(TextStyle.LABEL).color(PAPER_MUTED).centered()
                .singleLine();
        Text footerLeft = Text.of(() -> Component.literal(chapter == null ? "点击章节进入内容" : "拖动卡片到另一张卡片的位置，可交换顺序"))
                .style(TextStyle.CAPTION).color(PAPER_MUTED).singleLine();
        Box column = Ui.column(
                Ui.column(title, subtitle).gap(2.0F),
                book.grow(1.0F).basis(0.0F).maxWidth(780.0F).enter(EnterEffect.ZOOM),
                Ui.row(footerLeft, Ui.spacer(), Text.of("ESC 关闭").style(TextStyle.CAPTION).color(PAPER_MUTED).singleLine())
                        .maxWidth(780.0F)
        ).gap(Theme.Space.MD).alignItems(Align.STRETCH).padding(Theme.Space.XL, Theme.Space.LG);
        return Ui.stack(new Overlay(), Ui.row(column.grow(1.0F).maxWidth(800.0F)).justify(Justify.CENTER)
                        .alignItems(Align.STRETCH))
                .alignItems(Align.STRETCH);
    }

    // ==================== 书页 ====================

    private UiNode<?> spread(Key key) {
        boolean single = key.size() == Responsive.Size.COMPACT;
        int perPage = 2;
        int perSpread = single ? perPage : perPage * 2;
        List<UiNode<?>> items = new ArrayList<>();
        int total;
        if (chapter == null) {
            Map<Integer, int[]> chapters = chapterStats();
            total = chapters.size();
            int start = page * perSpread;
            int index = 0;
            for (Map.Entry<Integer, int[]> entry : chapters.entrySet()) {
                if (index >= start && index < start + perSpread) {
                    items.add(chapterCard(entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
                }
                index++;
            }
        } else {
            List<StoryBookEntryViewData> cards = chapterEntries();
            total = cards.size();
            for (int i = page * perSpread; i < Math.min(total, page * perSpread + perSpread); i++) {
                items.add(new FragmentCard(cards.get(i)));
            }
        }
        int pages = Math.max(1, (int) Math.ceil(total / (double) perSpread));
        if (page >= pages) {
            page = pages - 1;
        }

        Box left = pageColumn(items, 0, perPage);
        Box body;
        if (single) {
            body = Ui.row(left.grow(1.0F).basis(0.0F)).alignItems(Align.STRETCH);
        } else {
            Box right = pageColumn(items, perPage, perPage);
            body = Ui.row(left.grow(1.0F).basis(0.0F), new Spine().width(20.0F), right.grow(1.0F).basis(0.0F))
                    .alignItems(Align.STRETCH);
        }

        Box nav = Ui.row(
                new PaperButton("‹ 上页", page > 0, () -> {
                    page--;
                }),
                Ui.spacer(),
                Text.of((page + 1) + " / " + pages).style(TextStyle.LABEL).color(PAPER_MUTED).singleLine(),
                Ui.spacer(),
                new PaperButton("下页 ›", page < pages - 1, () -> {
                    page++;
                })
        ).alignItems(Align.CENTER).padding(Theme.Space.LG, 0.0F);

        Box book = new BookFrame().column().alignItems(Align.STRETCH).padding(14.0F, 12.0F, 14.0F, 8.0F).gap(Theme.Space.SM);
        if (chapter != null) {
            book.add(Ui.row(new PaperButton("‹ 返回章节", true, () -> {
                chapter = null;
                page = 0;
            })).padding(Theme.Space.LG, 0.0F));
        }
        book.add(body.grow(1.0F).basis(0.0F), nav);
        return book;
    }

    private static Box pageColumn(List<UiNode<?>> items, int from, int count) {
        Box column = new PageSheet().column().alignItems(Align.STRETCH).gap(Theme.Space.LG)
                .padding(Theme.Space.LG, Theme.Space.XL);
        if (from >= items.size()) {
            // 空白页：只留一行淡淡的页脚字
            column.justify(Justify.CENTER).add(Text.of("— 此页尚无记录 —").style(TextStyle.LABEL).color(0x805A3D1E).centered());
            return column;
        }
        for (int i = from; i < from + count; i++) {
            // 不足一页时用空位占住，卡片保持统一高度
            column.add(i < items.size() ? items.get(i).enter(EnterEffect.FADE_UP.delayed((i - from) * 50.0F))
                    : Ui.stack().grow(1.0F).basis(0.0F));
        }
        return column;
    }

    private Map<Integer, int[]> chapterStats() {
        Map<Integer, int[]> stats = new TreeMap<>();
        for (StoryBookEntryViewData entry : order) {
            int[] value = stats.computeIfAbsent(entry.getChapterId(), key -> new int[2]);
            value[0]++;
            if (entry.isRead()) {
                value[1]++;
            }
        }
        return new LinkedHashMap<>(stats);
    }

    private List<StoryBookEntryViewData> chapterEntries() {
        List<StoryBookEntryViewData> result = new ArrayList<>();
        for (StoryBookEntryViewData entry : order) {
            if (chapter != null && entry.getChapterId() == chapter) {
                result.add(entry);
            }
        }
        return result;
    }

    /** 直接打开某一章节的片段列表。 */
    public void openChapter(int chapterId) {
        chapter = chapterId;
        page = 0;
    }

    static String chapterLabel(int chapterId) {
        return chapterId <= 0 ? "序章" : "第 " + chapterId + " 章";
    }

    private UiNode<?> chapterCard(int chapterId, int total, int read) {
        PaperCard card = new PaperCard(true);
        float ratio = total <= 0 ? 0.0F : read / (float) total;
        card.add(Text.of(chapterLabel(chapterId)).style(TextStyle.TITLE).color(INK_TITLE).singleLine(),
                Text.of("已收录片段 " + total + " · 已阅读 " + read + " / " + total).style(TextStyle.LABEL).color(INK_TEXT).singleLine(),
                Ui.spacer(),
                CustomPaint.of((canvas, w, h) -> {
                    canvas.shape(0.0F, 0.0F, w, h).radius(h * 0.5F).fill(0x305A3D1E).draw();
                    if (ratio > 0.0F) {
                        canvas.shape(0.0F, 0.0F, Math.max(h, w * ratio), h).radius(h * 0.5F).fill(INK_ACCENT).draw();
                    }
                }).height(3.0F).margin(0.0F, 0.0F, 0.0F, 4.0F),
                Text.of("点击查看本章节内容 ›").style(TextStyle.LABEL).color(0xFF86562C).singleLine());
        card.onClick(() -> openChapter(chapterId));
        return card.grow(1.0F).basis(0.0F);
    }

    private void swap(StoryBookEntryViewData first, StoryBookEntryViewData second) {
        int a = order.indexOf(first);
        int b = order.indexOf(second);
        if (a < 0 || b < 0 || a == b) {
            return;
        }
        order.set(a, second);
        order.set(b, first);
        List<Integer> ids = new ArrayList<>();
        for (StoryBookEntryViewData entry : order) {
            ids.add(entry.getFragmentId());
        }
        DreamingFishCore_NetworkManager.sendToServer(new Packet_UpdateStoryBookOrder(ids));
        version++;
    }

    private void open(StoryBookEntryViewData entry) {
        Minecraft.getInstance().setScreen(new Screen_StoryFragment(entry.getFragmentId(), entry.getStageId(),
                entry.getChapterId(), entry.getTitle(), entry.getContent(), entry.getTime(), entry.getAuthorName()));
    }

    @Override
    protected boolean onEscape() {
        if (chapter != null) {
            chapter = null;
            page = 0;
            return true;
        }
        return false;
    }

    // ==================== 部件 ====================

    private final class Overlay extends UiNode<Overlay> {
        Overlay() {
            pointerEvents(false);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            canvas.fill(0.0F, 0.0F, width(), height(), UiColor.multiplyAlpha(0xB0140D07, reveal.get()));
            canvas.shape(0.0F, 0.0F, width(), height())
                    .radial(0x00000000, UiColor.multiplyAlpha(0x88000000, reveal.get()), width() * 0.5F, height() * 0.5F,
                            (float) Math.hypot(width(), height()) * 0.55F).draw();
        }
    }

    /** 书的封皮：深棕皮革，带内阴影与压边；拖动中的卡片画在最上层。 */
    private final class BookFrame extends Box {
        @Override
        protected void paintChildren(UiCanvas canvas) {
            super.paintChildren(canvas);
            FragmentCard card = dragCard;
            if (card == null) {
                return;
            }
            float ox = 0.0F;
            float oy = 0.0F;
            for (UiNode<?> node = card.parent(); node != null && node != this; node = node.parent()) {
                ox += node.x();
                oy += node.y();
            }
            canvas.newLayer();
            canvas.push();
            canvas.translate(ox, oy);
            card.opacity(1.0F);
            card.paint(canvas);
            card.opacity(0.0F);
            canvas.pop();
            canvas.newLayer();
        }

        @Override
        protected void paintBackground(UiCanvas canvas) {
            float w = width();
            float h = height();
            canvas.shape(0.0F, 0.0F, w, h).radius(Theme.Radius.LG).verticalGradient(COVER, COVER_DARK)
                    .border(1.0F, 0xFF2E1E0F).shadow(new Theme.Shadow(0.0F, 10.0F, 28.0F, 0.0F, 0xAA000000))
                    .innerShadow(new Theme.Shadow(0.0F, 0.0F, 10.0F, 0.0F, 0x66000000)).draw();
            canvas.shape(5.0F, 5.0F, w - 10.0F, h - 10.0F).radius(Theme.Radius.MD).fill(0)
                    .border(1.0F, 0x33FFD9A0).draw();
        }
    }

    /** 纸页：暖色纸张渐变与细边。 */
    private static final class PageSheet extends Box {
        @Override
        protected void paintBackground(UiCanvas canvas) {
            float w = width();
            float h = height();
            canvas.shape(0.0F, 0.0F, w, h).radius(3.0F).verticalGradient(PAGE, PAGE_EDGE)
                    .border(1.0F, 0x80755634).shadow(new Theme.Shadow(2.0F, 4.0F, 6.0F, 0.0F, 0x40000000)).draw();
            // 手账横线与页边红线
            for (float y = 26.0F; y < h - 8.0F; y += 14.0F) {
                canvas.fill(10.0F, y, w - 20.0F, 0.5F, 0x1C5A3D1E);
            }
            canvas.fill(14.0F, 6.0F, 0.75F, h - 12.0F, 0x30C44B36);
        }
    }

    /** 书脊：两页之间的阴影折痕。 */
    private static final class Spine extends UiNode<Spine> {
        Spine() {
            pointerEvents(false);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            float w = width();
            float h = height();
            canvas.shape(w * 0.5F - 8.0F, 6.0F, 16.0F, h - 12.0F).radius(4.0F)
                    .horizontalGradient(0x66000000, 0x00000000).draw();
            canvas.shape(w * 0.5F - 2.0F, 14.0F, 4.0F, h - 28.0F).radius(2.0F).fill(0x30FFFFFF).draw();
        }
    }

    /** 纸质按钮：墨水色文字，悬停时下划线。 */
    private final class PaperButton extends InteractiveNode<PaperButton> {
        private final Text label;
        private final boolean enabled;

        PaperButton(String text, boolean enabled, Runnable action) {
            this.enabled = enabled;
            label = Text.of(text).style(TextStyle.LABEL_STRONG).singleLine();
            add(label);
            padding(4.0F, 3.0F);
            if (enabled) {
                cursor(Cursor.POINTER);
                onClick(action);
            }
        }

        @Override
        protected void update() {
            label.color(!enabled ? 0x66D6C1A0 : UiColor.lerp(PAPER_MUTED, 0xFFFFE2A8, hover()));
        }

        @Override
        protected void paintOverlay(UiCanvas canvas) {
            float h = hover();
            if (enabled && h > 0.01F) {
                float w = (width() - 8.0F) * h;
                canvas.fill(4.0F + (width() - 8.0F - w) * 0.5F, height() - 2.0F, w, 1.0F, 0x88FFE2A8);
            }
        }
    }

    /** 纸质卡片（章节）。 */
    static class PaperCard extends InteractiveNode<PaperCard> {
        private final boolean chapterStyle;

        PaperCard(boolean chapterStyle) {
            this.chapterStyle = chapterStyle;
            column().gap(Theme.Space.XS).padding(Theme.Space.LG, Theme.Space.MD);
            minHeight(64.0F).maxHeight(118.0F);
        }

        @Override
        protected void onStateChanged() {
            super.onStateChanged();
            if (onClickHandler() != null) {
                animateTranslate(0.0F, isHovered() ? -1.5F : 0.0F);
            }
        }

        @Override
        protected void paintBackground(UiCanvas canvas) {
            float h = hover();
            int fill = UiColor.lerp(chapterStyle ? 0xFFE8D5AF : 0xFFEEDAAE, 0xFFF4E5C1, h);
            canvas.shape(0.0F, 0.0F, width(), height()).radius(3.0F).fill(fill).border(1.0F, 0x60825D34)
                    .shadow(new Theme.Shadow(2.0F, 3.0F + h * 3.0F, 5.0F + h * 6.0F, 0.0F, 0x40160D07)).draw();
            if (chapterStyle) {
                canvas.shape(0.0F, 0.0F, 6.0F, height()).radius(3.0F, 0.0F, 0.0F, 3.0F).fill(INK_ACCENT).draw();
            }
        }
    }

    /** 残页卡片：图钉 + 标题 + 记录信息；可拖动交换顺序，轻点阅读。 */
    private final class FragmentCard extends PaperCard {
        private final StoryBookEntryViewData entry;
        private double pressX;
        private double pressY;
        private boolean dragging;

        FragmentCard(StoryBookEntryViewData entry) {
            super(false);
            this.entry = entry;
            cursor(Cursor.GRAB);
            add(Text.of(entry.getTitle()).style(TextStyle.SUBTITLE).color(INK_TITLE).singleLine(),
                    Text.of("记录者 · " + entry.getAuthorName()).style(TextStyle.CAPTION).color(INK_TEXT).singleLine(),
                    Text.of("时间 · " + entry.getTime()).style(TextStyle.CAPTION).color(INK_TEXT).singleLine(),
                    Ui.row(Text.of("阶段 " + entry.getStageId() + " · 片段 " + entry.getFragmentId()).style(TextStyle.CAPTION)
                                    .color(INK_TEXT).singleLine(), Ui.spacer(),
                            Text.of(entry.isRead() ? "已阅读" : "未阅读").style(TextStyle.CAPTION_STRONG)
                                    .color(entry.isRead() ? 0xFF6C5A43 : 0xFFA5462C).singleLine()));
            grow(1.0F).basis(0.0F);
        }

        @Override
        protected boolean onMouseDown(double guiX, double guiY, int button) {
            if (button != 0) {
                return false;
            }
            pressX = guiX;
            pressY = guiY;
            dragging = false;
            return true;
        }

        @Override
        protected boolean onMouseDrag(double guiX, double guiY, int button, double dragX, double dragY) {
            float scale = width() > 0.0F ? (guiRight() - guiLeft()) / width() : 1.0F;
            double dx = (guiX - pressX) / scale;
            double dy = (guiY - pressY) / scale;
            if (!dragging && Math.hypot(dx, dy) >= 6.0) {
                dragging = true;
                dragCard = this;
                opacity(0.0F);
            }
            if (dragging) {
                translate((float) dx, (float) dy);
                scale(1.03F);
            }
            return true;
        }

        @Override
        protected void onMouseUp(double guiX, double guiY, int button, boolean inside) {
            if (!dragging) {
                UiSounds.click();
                open(entry);
                return;
            }
            dragging = false;
            dragCard = null;
            opacity(1.0F);
            FragmentCard target = findTarget(guiX, guiY);
            if (target != null) {
                UiSounds.soft();
                translate(0.0F, 0.0F).scale(1.0F);
                swap(entry, target.entry);
            } else {
                animateTranslate(0.0F, 0.0F);
                animateScale(1.0F);
            }
        }

        private FragmentCard findTarget(double guiX, double guiY) {
            UiNode<?> rootContent = ui().content();
            return rootContent == null ? null : search(rootContent, guiX, guiY);
        }

        private FragmentCard search(UiNode<?> node, double guiX, double guiY) {
            if (node instanceof FragmentCard card && card != this && card.containsPoint(guiX, guiY)) {
                return card;
            }
            for (UiNode<?> child : node.children()) {
                FragmentCard found = search(child, guiX, guiY);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        @Override
        protected void paintOverlay(UiCanvas canvas) {
            float cx = width() * 0.5F;
            canvas.circle(cx, 1.0F, 4.5F, PIN);
            canvas.circle(cx - 1.2F, -0.2F, 1.5F, 0x7AFFFFFF);
        }
    }
}
