package com.hhy.dreamingfishcore.client.ui.framework.node;

import java.util.ArrayList;
import java.util.List;

/**
 * 布局引擎：flex（行 / 列）、叠放与绝对定位。
 *
 * <p>规则基本对应 CSS flexbox 的常用子集：basis / grow / shrink、换行、主轴分布、
 * 交叉轴对齐与拉伸、gap、padding、margin、百分比尺寸、最小 / 最大尺寸、宽高比。
 * 与 CSS 的差异：纵向排布时子节点默认不收缩（相当于 min-height: auto），
 * 横向排布时默认可收缩到 0，文字会随之换行或省略。</p>
 *
 * <p>测量结果按可用空间缓存在节点上，节点被标脏时失效；未标脏且尺寸不变的子树跳过重新布局。
 * 最终位置与尺寸对齐到物理像素。</p>
 */
public final class LayoutEngine {
    private static final float INF = Float.POSITIVE_INFINITY;

    private float pixel = 1.0F;

    /** 以给定视口尺寸布局整棵树。{@code pixelSize} 为一个物理像素对应的 GUI 长度。 */
    public void layoutRoot(UiNode<?> root, float width, float height, float pixelSize) {
        pixel = pixelSize > 0.0F ? pixelSize : 1.0F;
        root.x = 0.0F;
        root.y = 0.0F;
        layout(root, width, height, true);
    }

    /** 测量节点在给定可用空间下的外框尺寸（不含 margin）。 */
    public void measure(UiNode<?> node, float availW, float availH, Size out) {
        if (node.measureValid && same(node.measureAvailW, availW) && same(node.measureAvailH, availH)) {
            out.set(node.measuredW, node.measuredH);
            return;
        }
        float w = resolve(node.width, node.widthPercent, availW);
        float h = resolve(node.height, node.heightPercent, availH);
        if (!Float.isNaN(node.aspectRatio) && node.aspectRatio > 0.0F) {
            if (Float.isNaN(w) && !Float.isNaN(h)) {
                w = h * node.aspectRatio;
            } else if (Float.isNaN(h) && !Float.isNaN(w)) {
                h = w / node.aspectRatio;
            }
        }
        float padH = node.padLeft + node.padRight;
        float padV = node.padTop + node.padBottom;
        if (Float.isNaN(w) || Float.isNaN(h)) {
            float innerAvailW = Math.max(0.0F, (Float.isNaN(w) ? availW : w) - padH);
            float innerAvailH = Math.max(0.0F, (Float.isNaN(h) ? availH : h) - padV);
            Size content = new Size();
            if (hasFlowChildren(node)) {
                float innerW = Float.isNaN(w) ? Float.NaN : Math.max(0.0F, w - padH);
                float innerH = Float.isNaN(h) ? Float.NaN : Math.max(0.0F, h - padV);
                if (node.flow == Flow.STACK) {
                    stack(node, innerW, innerH, innerAvailW, innerAvailH, true, content);
                } else {
                    flex(node, innerW, innerH, innerAvailW, innerAvailH, true, content);
                }
            } else {
                node.measureContent(innerAvailW, innerAvailH, content);
            }
            if (Float.isNaN(w)) {
                w = content.width + padH;
                if (Float.isFinite(availW)) {
                    w = Math.min(w, Math.max(availW, node.minWidth));
                }
            }
            if (Float.isNaN(h)) {
                h = content.height + padV;
            }
            if (!Float.isNaN(node.aspectRatio) && node.aspectRatio > 0.0F && Float.isNaN(resolve(node.height, node.heightPercent, availH))) {
                h = w / node.aspectRatio;
            }
        }
        w = clamp(w, node.minWidth, node.maxWidth);
        h = clamp(h, node.minHeight, node.maxHeight);
        node.measureValid = true;
        node.measureAvailW = availW;
        node.measureAvailH = availH;
        node.measuredW = w;
        node.measuredH = h;
        out.set(w, h);
    }

    void layout(UiNode<?> node, float width, float height, boolean force) {
        node.w = width;
        node.h = height;
        if (!force && !node.needsLayout && width == node.laidOutW && height == node.laidOutH) {
            return;
        }
        node.laidOutW = width;
        node.laidOutH = height;
        node.needsLayout = false;
        if (!node.children.isEmpty()) {
            float innerW = Math.max(0.0F, width - node.padLeft - node.padRight);
            float innerH = Math.max(0.0F, height - node.padTop - node.padBottom);
            Size ignored = new Size();
            if (hasFlowChildren(node)) {
                if (node.flow == Flow.STACK) {
                    stack(node, innerW, innerH, innerW, innerH, false, ignored);
                } else {
                    flex(node, innerW, innerH, innerW, innerH, false, ignored);
                }
            }
            layoutAbsolute(node, width, height);
        }
        node.onLayout();
    }

    // ==================== flex ====================

    private void flex(UiNode<?> node, float innerW, float innerH, float availW, float availH,
                      boolean measureOnly, Size out) {
        boolean row = node.flow == Flow.ROW;
        List<UiNode<?>> items = flowChildren(node);
        int count = items.size();
        if (count == 0) {
            out.set(0.0F, 0.0F);
            return;
        }
        float innerMain = row ? innerW : innerH;
        float innerCross = row ? innerH : innerW;
        float availMain = row ? availW : availH;
        float availCross = row ? availH : availW;
        float mainLimit = !Float.isNaN(innerMain) ? innerMain : (row ? availMain : INF);
        float crossLimit = !Float.isNaN(innerCross) ? innerCross : availCross;

        float[] main = new float[count];
        float[] hypothetical = new float[count];
        float[] cross = new float[count];
        float[] marginMainStart = new float[count];
        float[] marginMainEnd = new float[count];
        float[] marginCrossStart = new float[count];
        float[] marginCrossEnd = new float[count];
        float[] shrinkFactor = new float[count];
        boolean[] autoCross = new boolean[count];
        Size tmp = new Size();

        for (int i = 0; i < count; i++) {
            UiNode<?> child = items.get(i);
            marginMainStart[i] = row ? child.marginLeft : child.marginTop;
            marginMainEnd[i] = row ? child.marginRight : child.marginBottom;
            marginCrossStart[i] = row ? child.marginTop : child.marginLeft;
            marginCrossEnd[i] = row ? child.marginBottom : child.marginRight;
            shrinkFactor[i] = !Float.isNaN(child.shrink) ? child.shrink : (row ? 1.0F : 0.0F);

            float mainSpec = row ? resolve(child.width, child.widthPercent, innerMain)
                    : resolve(child.height, child.heightPercent, innerMain);
            float basis;
            if (!Float.isNaN(child.basis)) {
                basis = child.basis;
            } else if (!Float.isNaN(mainSpec)) {
                basis = mainSpec;
            } else {
                float childCross = crossAvailable(node, child, row, innerCross, crossLimit,
                        marginCrossStart[i] + marginCrossEnd[i]);
                if (row) {
                    measure(child, INF, childCross, tmp);
                    basis = tmp.width;
                } else {
                    measure(child, childCross, INF, tmp);
                    basis = tmp.height;
                }
            }
            float minMain = row ? child.minWidth : child.minHeight;
            float maxMain = row ? child.maxWidth : child.maxHeight;
            hypothetical[i] = clamp(basis, minMain, maxMain);
            main[i] = hypothetical[i];
        }

        // 分行
        float gapMain = node.gap;
        float gapCross = Float.isNaN(node.crossGap) ? node.gap : node.crossGap;
        List<int[]> lines = new ArrayList<>();
        if (node.wrap && Float.isFinite(mainLimit)) {
            int start = 0;
            float used = 0.0F;
            for (int i = 0; i < count; i++) {
                float itemSize = hypothetical[i] + marginMainStart[i] + marginMainEnd[i];
                float next = used + (i > start ? gapMain : 0.0F) + itemSize;
                if (i > start && next > mainLimit + 0.01F) {
                    lines.add(new int[]{start, i});
                    start = i;
                    used = itemSize;
                } else {
                    used = next;
                }
            }
            lines.add(new int[]{start, count});
        } else {
            lines.add(new int[]{0, count});
        }
        boolean singleLine = lines.size() == 1;

        // 伸缩
        for (int[] line : lines) {
            float used = 0.0F;
            float totalGrow = 0.0F;
            float totalShrink = 0.0F;
            for (int i = line[0]; i < line[1]; i++) {
                used += hypothetical[i] + marginMainStart[i] + marginMainEnd[i];
                totalGrow += items.get(i).grow;
                totalShrink += shrinkFactor[i] * hypothetical[i];
            }
            used += gapMain * (line[1] - line[0] - 1);
            float free = mainLimit - used;
            if (free > 0.0F && !Float.isNaN(innerMain) && totalGrow > 0.0F) {
                distributeGrow(items, line, main, hypothetical, free, totalGrow, row);
            } else if (free < 0.0F && Float.isFinite(mainLimit) && totalShrink > 0.0F) {
                for (int i = line[0]; i < line[1]; i++) {
                    UiNode<?> child = items.get(i);
                    float portion = shrinkFactor[i] * hypothetical[i] / totalShrink;
                    float minMain = row ? child.minWidth : child.minHeight;
                    main[i] = Math.max(minMain, hypothetical[i] + free * portion);
                }
            }
        }

        // 交叉轴尺寸
        float[] lineCross = new float[lines.size()];
        for (int l = 0; l < lines.size(); l++) {
            int[] line = lines.get(l);
            float maxCross = 0.0F;
            for (int i = line[0]; i < line[1]; i++) {
                UiNode<?> child = items.get(i);
                Align align = effectiveAlign(node, child);
                float crossMargins = marginCrossStart[i] + marginCrossEnd[i];
                float crossSpec = row ? resolve(child.height, child.heightPercent, innerCross)
                        : resolve(child.width, child.widthPercent, innerCross);
                float minCross = row ? child.minHeight : child.minWidth;
                float maxCrossLimit = row ? child.maxHeight : child.maxWidth;
                autoCross[i] = Float.isNaN(crossSpec);
                if (!Float.isNaN(crossSpec)) {
                    cross[i] = clamp(crossSpec, minCross, maxCrossLimit);
                } else if (align == Align.STRETCH && !Float.isNaN(innerCross) && singleLine) {
                    cross[i] = clamp(innerCross - crossMargins, minCross, maxCrossLimit);
                } else {
                    float available = Math.max(0.0F, crossLimit - crossMargins);
                    if (row) {
                        measure(child, main[i], available, tmp);
                        cross[i] = tmp.height;
                    } else {
                        measure(child, available, main[i], tmp);
                        cross[i] = tmp.width;
                    }
                }
                maxCross = Math.max(maxCross, cross[i] + crossMargins);
            }
            lineCross[l] = singleLine && !Float.isNaN(innerCross) ? innerCross : maxCross;
            for (int i = line[0]; i < line[1]; i++) {
                UiNode<?> child = items.get(i);
                if (autoCross[i] && effectiveAlign(node, child) == Align.STRETCH) {
                    float minCross = row ? child.minHeight : child.minWidth;
                    float maxCrossLimit = row ? child.maxHeight : child.maxWidth;
                    cross[i] = clamp(lineCross[l] - marginCrossStart[i] - marginCrossEnd[i], minCross, maxCrossLimit);
                }
            }
        }

        float contentMain = 0.0F;
        float contentCross = 0.0F;
        for (int l = 0; l < lines.size(); l++) {
            int[] line = lines.get(l);
            float used = gapMain * (line[1] - line[0] - 1);
            for (int i = line[0]; i < line[1]; i++) {
                used += main[i] + marginMainStart[i] + marginMainEnd[i];
            }
            contentMain = Math.max(contentMain, used);
            contentCross += lineCross[l] + (l > 0 ? gapCross : 0.0F);
        }
        out.set(row ? contentMain : contentCross, row ? contentCross : contentMain);
        if (measureOnly) {
            return;
        }

        // 定位
        float originMain = row ? node.padLeft : node.padTop;
        float originCross = row ? node.padTop : node.padLeft;
        float crossPos = 0.0F;
        for (int l = 0; l < lines.size(); l++) {
            int[] line = lines.get(l);
            int lineCount = line[1] - line[0];
            float used = gapMain * (lineCount - 1);
            for (int i = line[0]; i < line[1]; i++) {
                used += main[i] + marginMainStart[i] + marginMainEnd[i];
            }
            float remaining = innerMain - used;
            float start = 0.0F;
            float between = gapMain;
            if (remaining > 0.0F) {
                switch (node.justify) {
                    case CENTER -> start = remaining * 0.5F;
                    case END -> start = remaining;
                    case SPACE_BETWEEN -> between = lineCount > 1 ? gapMain + remaining / (lineCount - 1) : gapMain;
                    case SPACE_AROUND -> {
                        float each = remaining / lineCount;
                        start = each * 0.5F;
                        between = gapMain + each;
                    }
                    case SPACE_EVENLY -> {
                        float each = remaining / (lineCount + 1);
                        start = each;
                        between = gapMain + each;
                    }
                    default -> {
                    }
                }
            }
            float mainPos = start;
            for (int i = line[0]; i < line[1]; i++) {
                UiNode<?> child = items.get(i);
                mainPos += marginMainStart[i];
                float crossOffset;
                Align align = effectiveAlign(node, child);
                float slack = lineCross[l] - cross[i] - marginCrossStart[i] - marginCrossEnd[i];
                crossOffset = switch (align) {
                    case CENTER -> marginCrossStart[i] + slack * 0.5F;
                    case END -> marginCrossStart[i] + slack;
                    default -> marginCrossStart[i];
                };
                float mainStart = originMain + mainPos;
                float crossStart = originCross + crossPos + crossOffset;
                float childX = row ? mainStart : crossStart;
                float childY = row ? crossStart : mainStart;
                float childW = row ? main[i] : cross[i];
                float childH = row ? cross[i] : main[i];
                place(child, childX, childY, childW, childH);
                mainPos += main[i] + marginMainEnd[i] + between;
            }
            crossPos += lineCross[l] + gapCross;
        }
    }

    private void distributeGrow(List<UiNode<?>> items, int[] line, float[] main, float[] hypothetical,
                                float free, float totalGrow, boolean row) {
        // 两轮：受最大尺寸限制的项目冻结后，剩余空间再分给其他项目
        boolean[] frozen = new boolean[main.length];
        float remaining = free;
        float growSum = totalGrow;
        for (int pass = 0; pass < 3 && remaining > 0.01F && growSum > 0.0F; pass++) {
            float passRemaining = remaining;
            float nextGrowSum = 0.0F;
            for (int i = line[0]; i < line[1]; i++) {
                UiNode<?> child = items.get(i);
                if (frozen[i] || child.grow <= 0.0F) {
                    continue;
                }
                float add = passRemaining * child.grow / growSum;
                float maxMain = row ? child.maxWidth : child.maxHeight;
                float target = main[i] + add;
                if (target > maxMain) {
                    remaining -= maxMain - main[i];
                    main[i] = maxMain;
                    frozen[i] = true;
                } else {
                    main[i] = target;
                    remaining -= add;
                    nextGrowSum += child.grow;
                }
            }
            growSum = nextGrowSum;
        }
    }

    private float crossAvailable(UiNode<?> node, UiNode<?> child, boolean row, float innerCross,
                                 float crossLimit, float crossMargins) {
        float crossSpec = row ? resolve(child.height, child.heightPercent, innerCross)
                : resolve(child.width, child.widthPercent, innerCross);
        if (!Float.isNaN(crossSpec)) {
            return crossSpec;
        }
        return Math.max(0.0F, crossLimit - crossMargins);
    }

    // ==================== stack ====================

    private void stack(UiNode<?> node, float innerW, float innerH, float availW, float availH,
                       boolean measureOnly, Size out) {
        List<UiNode<?>> items = flowChildren(node);
        Size tmp = new Size();
        float maxW = 0.0F;
        float maxH = 0.0F;
        float[] widths = new float[items.size()];
        float[] heights = new float[items.size()];
        for (int i = 0; i < items.size(); i++) {
            UiNode<?> child = items.get(i);
            Align align = effectiveAlign(node, child);
            float marginH = child.marginLeft + child.marginRight;
            float marginV = child.marginTop + child.marginBottom;
            float cw = resolve(child.width, child.widthPercent, innerW);
            float ch = resolve(child.height, child.heightPercent, innerH);
            boolean stretch = align == Align.STRETCH;
            if (Float.isNaN(cw) && stretch && !Float.isNaN(innerW)) {
                cw = clamp(innerW - marginH, child.minWidth, child.maxWidth);
            }
            if (Float.isNaN(ch) && stretch && !Float.isNaN(innerH)) {
                ch = clamp(innerH - marginV, child.minHeight, child.maxHeight);
            }
            if (Float.isNaN(cw) || Float.isNaN(ch)) {
                float aw = Float.isNaN(cw) ? Math.max(0.0F, (Float.isNaN(innerW) ? availW : innerW) - marginH) : cw;
                float ah = Float.isNaN(ch) ? Math.max(0.0F, (Float.isNaN(innerH) ? availH : innerH) - marginV) : ch;
                measure(child, aw, ah, tmp);
                if (Float.isNaN(cw)) {
                    cw = tmp.width;
                }
                if (Float.isNaN(ch)) {
                    ch = tmp.height;
                }
            }
            widths[i] = cw;
            heights[i] = ch;
            maxW = Math.max(maxW, cw + marginH);
            maxH = Math.max(maxH, ch + marginV);
        }
        out.set(maxW, maxH);
        if (measureOnly) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            UiNode<?> child = items.get(i);
            Align align = effectiveAlign(node, child);
            float slackX = innerW - widths[i] - child.marginLeft - child.marginRight;
            float slackY = innerH - heights[i] - child.marginTop - child.marginBottom;
            float factor = align == Align.CENTER ? 0.5F : align == Align.END ? 1.0F : 0.0F;
            float cx = node.padLeft + child.marginLeft + slackX * factor;
            float cy = node.padTop + child.marginTop + slackY * factor;
            place(child, cx, cy, widths[i], heights[i]);
        }
    }

    // ==================== 绝对定位 ====================

    private void layoutAbsolute(UiNode<?> node, float width, float height) {
        Size tmp = new Size();
        for (UiNode<?> child : node.children) {
            if (!child.visible || !child.absolute) {
                continue;
            }
            float left = child.insetLeft;
            float top = child.insetTop;
            float right = child.insetRight;
            float bottom = child.insetBottom;
            float cw = resolve(child.width, child.widthPercent, width);
            float ch = resolve(child.height, child.heightPercent, height);
            if (Float.isNaN(cw) && !Float.isNaN(left) && !Float.isNaN(right)) {
                cw = Math.max(0.0F, width - left - right);
            }
            if (Float.isNaN(ch) && !Float.isNaN(top) && !Float.isNaN(bottom)) {
                ch = Math.max(0.0F, height - top - bottom);
            }
            if (Float.isNaN(cw) || Float.isNaN(ch)) {
                float aw = Float.isNaN(cw) ? Math.max(0.0F, width - zeroIfNaN(left) - zeroIfNaN(right)) : cw;
                float ah = Float.isNaN(ch) ? Math.max(0.0F, height - zeroIfNaN(top) - zeroIfNaN(bottom)) : ch;
                measure(child, aw, ah, tmp);
                if (Float.isNaN(cw)) {
                    cw = tmp.width;
                }
                if (Float.isNaN(ch)) {
                    ch = tmp.height;
                }
            }
            cw = clamp(cw, child.minWidth, child.maxWidth);
            ch = clamp(ch, child.minHeight, child.maxHeight);
            float cx = !Float.isNaN(left) ? left : !Float.isNaN(right) ? width - right - cw : (width - cw) * 0.5F;
            float cy = !Float.isNaN(top) ? top : !Float.isNaN(bottom) ? height - bottom - ch : (height - ch) * 0.5F;
            place(child, cx, cy, cw, ch);
        }
    }

    // ==================== 工具 ====================

    private void place(UiNode<?> child, float x, float y, float width, float height) {
        float x0 = snap(x);
        float y0 = snap(y);
        float x1 = snap(x + Math.max(0.0F, width));
        float y1 = snap(y + Math.max(0.0F, height));
        child.x = x0;
        child.y = y0;
        layout(child, Math.max(0.0F, x1 - x0), Math.max(0.0F, y1 - y0), false);
    }

    private float snap(float value) {
        return Math.round(value / pixel) * pixel;
    }

    private static Align effectiveAlign(UiNode<?> parent, UiNode<?> child) {
        return child.alignSelf == Align.AUTO ? parent.alignItems : child.alignSelf;
    }

    private static boolean hasFlowChildren(UiNode<?> node) {
        for (UiNode<?> child : node.children) {
            if (child.visible && !child.absolute) {
                return true;
            }
        }
        return false;
    }

    private static List<UiNode<?>> flowChildren(UiNode<?> node) {
        List<UiNode<?>> result = new ArrayList<>(node.children.size());
        for (UiNode<?> child : node.children) {
            if (child.visible && !child.absolute) {
                result.add(child);
            }
        }
        return result;
    }

    private static float resolve(float fixed, float percent, float reference) {
        if (!Float.isNaN(fixed)) {
            return fixed;
        }
        if (!Float.isNaN(percent) && !Float.isNaN(reference) && Float.isFinite(reference)) {
            return percent * reference;
        }
        return Float.NaN;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float zeroIfNaN(float value) {
        return Float.isNaN(value) ? 0.0F : value;
    }

    private static boolean same(float a, float b) {
        return a == b || Float.isNaN(a) && Float.isNaN(b) || Math.abs(a - b) < 0.001F;
    }
}
