package com.hhy.dreamingfishcore.client.ui.framework.render;

import com.hhy.dreamingfishcore.client.ui.framework.text.TextLayout;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * 统一绘制入口。形状走 SDF 批次，文字、物品和原版绘制作为"原生命令"延后执行。
 *
 * <p><b>分层合批</b>：同一层内先画全部形状（一次提交），再按记录顺序执行原生命令（文字合并提交）。
 * 如果新形状与本层已记录的原生命令重叠，先结束本层再开新层，保证与记录顺序一致的遮挡关系。
 * 阴影按图形本体参与重叠判断，柔和的外扩部分不会打断合批。</p>
 *
 * <p>一帧的使用方式：{@link #begin} → 各种绘制 → {@link #end}。期间不要绕过画布直接使用
 * {@link GuiGraphics} 绘制，需要时用 {@link #custom}。</p>
 */
public final class UiCanvas {
    private static final float NO_CLIP = 1.0e6F;
    private static final float AA_PAD = 1.0F;

    private final ShapeBuffer shapes = new ShapeBuffer();
    private final Shape shape = new Shape(this);
    private final ArrayList<NativeCommand> natives = new ArrayList<>();
    private final ArrayList<NativeCommand> pool = new ArrayList<>();
    private float[] nativeBounds = new float[64];
    private int nativeCount;

    private float[] clipStack = new float[5 * 16];
    private int clipDepth;
    private float[] alphaStack = new float[16];
    private int alphaDepth;

    private GuiGraphics graphics;
    private Font font;
    private boolean active;
    private boolean fallback;
    private int layerCount;
    private int shapeCount;

    // 当前生效的裁剪（GUI 坐标）
    private float clipX0 = -NO_CLIP;
    private float clipY0 = -NO_CLIP;
    private float clipX1 = NO_CLIP;
    private float clipY1 = NO_CLIP;
    private float clipRadius;
    private float alpha = 1.0F;

    public void begin(GuiGraphics guiGraphics) {
        if (active) {
            end();
        }
        graphics = guiGraphics;
        font = Minecraft.getInstance().font;
        active = true;
        fallback = !SdfRenderer.available();
        clipDepth = 0;
        alphaDepth = 0;
        alpha = 1.0F;
        clipX0 = -NO_CLIP;
        clipY0 = -NO_CLIP;
        clipX1 = NO_CLIP;
        clipY1 = NO_CLIP;
        clipRadius = 0.0F;
        layerCount = 0;
        shapeCount = 0;
    }

    public void end() {
        if (!active) {
            return;
        }
        flushLayer();
        active = false;
        graphics = null;
    }

    public boolean isActive() {
        return active;
    }

    public GuiGraphics graphics() {
        return graphics;
    }

    public Font font() {
        return font;
    }

    public PoseStack pose() {
        return graphics.pose();
    }

    /** 本帧提交的层数（调试统计）。 */
    public int layerCount() {
        return layerCount;
    }

    public int shapeCount() {
        return shapeCount;
    }

    // ==================== 变换 ====================

    public void push() {
        graphics.pose().pushPose();
    }

    public void pop() {
        graphics.pose().popPose();
    }

    public void translate(float x, float y) {
        graphics.pose().translate(x, y, 0.0F);
    }

    /** 以 {@code (pivotX, pivotY)} 为中心缩放。 */
    public void scale(float scale, float pivotX, float pivotY) {
        PoseStack pose = graphics.pose();
        pose.translate(pivotX, pivotY, 0.0F);
        pose.scale(scale, scale, 1.0F);
        pose.translate(-pivotX, -pivotY, 0.0F);
    }

    /** 把绘制坐标换算为 GUI 坐标（只考虑平移与缩放）。 */
    public float toGuiX(float x, float y) {
        Matrix4f m = graphics.pose().last().pose();
        return m.m00() * x + m.m10() * y + m.m30();
    }

    public float toGuiY(float x, float y) {
        Matrix4f m = graphics.pose().last().pose();
        return m.m01() * x + m.m11() * y + m.m31();
    }

    public float poseScaleX() {
        return graphics.pose().last().pose().m00();
    }

    public float poseScaleY() {
        return graphics.pose().last().pose().m11();
    }

    // ==================== 透明度 ====================

    public void pushAlpha(float factor) {
        if (alphaDepth == alphaStack.length) {
            float[] grown = new float[alphaStack.length * 2];
            System.arraycopy(alphaStack, 0, grown, 0, alphaStack.length);
            alphaStack = grown;
        }
        alphaStack[alphaDepth++] = alpha;
        alpha *= Math.max(0.0F, Math.min(1.0F, factor));
    }

    public void popAlpha() {
        if (alphaDepth > 0) {
            alpha = alphaStack[--alphaDepth];
        }
    }

    public float alpha() {
        return alpha;
    }

    // ==================== 裁剪 ====================

    /** 压入裁剪区（绘制坐标），与当前裁剪区求交。{@code radius} 只对形状生效，文字与物品按矩形裁剪。 */
    public void pushClip(float x, float y, float width, float height, float radius) {
        float gx0 = toGuiX(x, y);
        float gy0 = toGuiY(x, y);
        float gx1 = toGuiX(x + width, y + height);
        float gy1 = toGuiY(x + width, y + height);
        if (clipDepth * 5 + 5 > clipStack.length) {
            float[] grown = new float[clipStack.length * 2];
            System.arraycopy(clipStack, 0, grown, 0, clipStack.length);
            clipStack = grown;
        }
        int base = clipDepth * 5;
        clipStack[base] = clipX0;
        clipStack[base + 1] = clipY0;
        clipStack[base + 2] = clipX1;
        clipStack[base + 3] = clipY1;
        clipStack[base + 4] = clipRadius;
        clipDepth++;
        clipX0 = Math.max(clipX0, Math.min(gx0, gx1));
        clipY0 = Math.max(clipY0, Math.min(gy0, gy1));
        clipX1 = Math.min(clipX1, Math.max(gx0, gx1));
        clipY1 = Math.min(clipY1, Math.max(gy0, gy1));
        clipRadius = radius * Math.abs(poseScaleX());
    }

    public void popClip() {
        if (clipDepth == 0) {
            return;
        }
        clipDepth--;
        int base = clipDepth * 5;
        clipX0 = clipStack[base];
        clipY0 = clipStack[base + 1];
        clipX1 = clipStack[base + 2];
        clipY1 = clipStack[base + 3];
        clipRadius = clipStack[base + 4];
    }

    /** GUI 坐标矩形是否与当前裁剪区相交。 */
    public boolean visibleGui(float x0, float y0, float x1, float y1) {
        return x1 > clipX0 && x0 < clipX1 && y1 > clipY0 && y0 < clipY1;
    }

    public boolean insideClip(double guiX, double guiY) {
        return guiX >= clipX0 && guiX < clipX1 && guiY >= clipY0 && guiY < clipY1;
    }

    public float clipLeft() {
        return clipX0;
    }

    public float clipTop() {
        return clipY0;
    }

    public float clipRight() {
        return clipX1;
    }

    public float clipBottom() {
        return clipY1;
    }

    // ==================== 形状 ====================

    /** 开始描述一个矩形类图形；链式设置后调用 {@link Shape#draw()}。返回对象会被复用，不要保存。 */
    public Shape shape(float x, float y, float width, float height) {
        return shape.reset(x, y, width, height);
    }

    public void fill(float x, float y, float width, float height, int color) {
        shape(x, y, width, height).fill(color).draw();
    }

    public void roundRect(float x, float y, float width, float height, float radius, int color) {
        shape(x, y, width, height).radius(radius).fill(color).draw();
    }

    public void circle(float centerX, float centerY, float radius, int color) {
        shape(centerX - radius, centerY - radius, radius * 2.0F, radius * 2.0F).radius(radius).fill(color).draw();
    }

    /** 线段；{@code round} 为 true 时两端为圆头。 */
    public void line(float x0, float y0, float x1, float y1, float width, int color, boolean round) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0e-4F || UiColor.alpha(color) == 0) {
            return;
        }
        float ux = dx / length;
        float uy = dy / length;
        float halfW = width * 0.5F;
        float halfL = length * 0.5F + (round ? halfW : 0.0F);
        float cx = (x0 + x1) * 0.5F;
        float cy = (y0 + y1) * 0.5F;
        float ex = halfL + AA_PAD;
        float ey = halfW + AA_PAD;
        float minX = Math.min(x0, x1) - width;
        float minY = Math.min(y0, y1) - width;
        float maxX = Math.max(x0, x1) + width;
        float maxY = Math.max(y0, y1) + width;
        if (!beforeShape(minX, minY, maxX - minX, maxY - minY)) {
            return;
        }
        int fill = UiColor.multiplyAlpha(color, alpha);
        shapes.beginQuad(-1);
        shapes.setHalf(halfL, halfW);
        float r = round ? halfW : 0.0F;
        shapes.setRadii(r, r, r, r);
        shapes.setStyle(0.0F, 0.0F, 0.0F, 0.0F);
        shapes.setGrad(0.0F, 0.0F, 0.0F, 0.0F);
        applyClip();
        shapes.setExtra(0.0F, 0.0F, 0.0F);
        shapes.setColors(fill, fill, 0);
        Matrix4f pose = graphics.pose().last().pose();
        // 局部坐标系：x 沿线段方向，y 垂直于线段
        shapes.vertex(pose, cx - ux * ex + uy * ey, cy - uy * ex - ux * ey, -ex, -ey);
        shapes.vertex(pose, cx - ux * ex - uy * ey, cy - uy * ex + ux * ey, -ex, ey);
        shapes.vertex(pose, cx + ux * ex - uy * ey, cy + uy * ex + ux * ey, ex, ey);
        shapes.vertex(pose, cx + ux * ex + uy * ey, cy + uy * ex - ux * ey, ex, -ey);
        shapeCount++;
    }

    /**
     * 圆弧 / 圆环。角度为弧度，0 指向右侧、顺时针增加；{@code sweep >= 2π} 为整圆环。
     * 可选锥形渐变：{@code colorEnd} 与 {@code color} 不同时沿圆弧方向渐变。
     */
    public void arc(float centerX, float centerY, float radius, float thickness,
                    float startAngle, float sweep, int color, int colorEnd) {
        if (sweep <= 0.0F || thickness <= 0.0F) {
            return;
        }
        float outer = radius + thickness * 0.5F;
        float x = centerX - outer;
        float y = centerY - outer;
        float size = outer * 2.0F;
        if (!beforeShape(x, y, size, size)) {
            return;
        }
        int c0 = UiColor.multiplyAlpha(color, alpha);
        int c1 = UiColor.multiplyAlpha(colorEnd, alpha);
        shapes.beginQuad(-1);
        shapes.setHalf(outer, outer);
        shapes.setRadii(radius, thickness * 0.5F, startAngle, Math.min(sweep, (float) (Math.PI * 2.0)));
        shapes.setStyle(0.0F, 0.0F, 0.0F, 0.0F);
        boolean gradient = c0 != c1;
        // 锥形渐变沿弧长从起点到终点
        shapes.setGrad(0.0F, 0.0F, startAngle, Math.max(sweep, 1.0e-3F));
        applyClip();
        shapes.setExtra(0.0F, 1.0F, gradient ? 3.0F : 0.0F);
        shapes.setColors(c0, gradient ? c1 : c0, 0);
        emitRect(x - AA_PAD, y - AA_PAD, x + size + AA_PAD, y + size + AA_PAD, centerX, centerY);
        shapeCount++;
    }

    /** 贴图（可带圆角与描边），{@code tint} 为着色。 */
    public void image(ResourceLocation texture, float x, float y, float width, float height,
                      float u0, float v0, float u1, float v1, float radius, int tint) {
        if (fallback) {
            int ix = Math.round(x);
            int iy = Math.round(y);
            custom(x, y, width, height, g -> {
                RenderSystem.enableBlend();
                g.blit(texture, ix, iy, Math.round(width), Math.round(height), u0 * 256.0F, v0 * 256.0F,
                        Math.round((u1 - u0) * 256.0F), Math.round((v1 - v0) * 256.0F), 256, 256);
            });
            return;
        }
        if (!beforeShape(x, y, width, height)) {
            return;
        }
        int textureId = Minecraft.getInstance().getTextureManager().getTexture(texture).getId();
        int color = UiColor.multiplyAlpha(tint, alpha);
        shapes.beginQuad(textureId);
        float hw = width * 0.5F;
        float hh = height * 0.5F;
        shapes.setHalf(hw, hh);
        shapes.setRadii(radius, radius, radius, radius);
        shapes.setStyle(0.0F, 0.0F, 0.0F, 0.0F);
        shapes.setGrad(u0, v0, u1, v1);
        applyClip();
        shapes.setExtra(0.0F, 0.0F, 0.0F);
        shapes.setColors(color, color, 0);
        emitRect(x, y, x + width, y + height, x + hw, y + hh);
        shapeCount++;
    }

    /** 玩家头像（脸 + 帽子层），可带圆角。 */
    public void playerFace(ResourceLocation skin, float x, float y, float size, float radius, int tint) {
        float u = 8.0F / 64.0F;
        image(skin, x, y, size, size, u, u, u * 2.0F, u * 2.0F, radius, tint);
        image(skin, x, y, size, size, 40.0F / 64.0F, u, 48.0F / 64.0F, u * 2.0F, radius, tint);
    }

    // ==================== 文字 ====================

    /** 绘制一行已排版的文字，{@code (x, y)} 为左上角。 */
    public void text(FormattedCharSequence text, float x, float y, int color, float scale, boolean shadow) {
        int finalColor = UiColor.multiplyAlpha(color, alpha);
        if (UiColor.alpha(finalColor) < 5) {
            return;
        }
        float width = font.width(text) * scale;
        float height = font.lineHeight * scale;
        if (!recordNative(x, y, width, height)) {
            return;
        }
        NativeCommand command = obtain(NativeCommand.TEXT);
        command.sequence = text;
        command.color = finalColor;
        command.shadow = shadow;
        snapshot(command, x, y, scale);
        natives.add(command);
    }

    public void text(String text, float x, float y, int color, float scale, boolean shadow) {
        int finalColor = UiColor.multiplyAlpha(color, alpha);
        if (text == null || text.isEmpty() || UiColor.alpha(finalColor) < 5) {
            return;
        }
        float width = font.width(text) * scale;
        float height = font.lineHeight * scale;
        if (!recordNative(x, y, width, height)) {
            return;
        }
        NativeCommand command = obtain(NativeCommand.STRING);
        command.string = text;
        command.color = finalColor;
        command.shadow = shadow;
        snapshot(command, x, y, scale);
        natives.add(command);
    }

    /**
     * 绘制排版结果。{@code align}：0 左对齐，0.5 居中，1 右对齐（相对 {@code boxWidth}）。
     */
    public void text(TextLayout layout, float x, float y, float boxWidth, float align, int color, boolean shadow) {
        float lineY = y;
        for (int i = 0; i < layout.lineCount(); i++) {
            float offset = align <= 0.0F ? 0.0F : (boxWidth - layout.lineWidth(i)) * align;
            text(layout.lines().get(i), x + offset, lineY, color, layout.scale(), shadow);
            lineY += layout.lineAdvance();
        }
    }

    // ==================== 物品与原生绘制 ====================

    /** 物品图标，{@code size} 为边长（原版为 16）。 */
    public void item(ItemStack stack, float x, float y, float size, boolean decorations) {
        if (stack == null || stack.isEmpty() || alpha < 0.05F) {
            return;
        }
        if (!recordNative(x, y, size, size)) {
            return;
        }
        NativeCommand command = obtain(NativeCommand.ITEM);
        command.stack = stack;
        command.decorations = decorations;
        command.alpha = alpha;
        snapshot(command, x, y, size / 16.0F);
        natives.add(command);
    }

    /**
     * 任意原版绘制。回调在本层形状之后执行，此时 pose 已设为记录时的状态、裁剪已生效。
     * {@code (x, y, width, height)} 为回调的大致范围，用于分层判断。
     */
    public void custom(float x, float y, float width, float height, Consumer<GuiGraphics> drawer) {
        if (!recordNative(x, y, width, height)) {
            return;
        }
        NativeCommand command = obtain(NativeCommand.CUSTOM);
        command.custom = drawer;
        command.alpha = alpha;
        snapshot(command, 0.0F, 0.0F, 1.0F);
        natives.add(command);
    }

    /** 立即结束当前层并开始新层，之后绘制的内容一定在之前内容之上。 */
    public void newLayer() {
        flushLayer();
    }

    // ==================== 内部：形状提交 ====================

    boolean beforeShape(float x, float y, float width, float height) {
        if (!active) {
            throw new IllegalStateException("UiCanvas 未开始绘制");
        }
        float gx0 = toGuiX(x, y);
        float gy0 = toGuiY(x, y);
        float gx1 = toGuiX(x + width, y + height);
        float gy1 = toGuiY(x + width, y + height);
        float minX = Math.min(gx0, gx1);
        float minY = Math.min(gy0, gy1);
        float maxX = Math.max(gx0, gx1);
        float maxY = Math.max(gy0, gy1);
        if (!visibleGui(minX, minY, maxX, maxY)) {
            return false;
        }
        if (overlapsNative(Math.max(minX, clipX0), Math.max(minY, clipY0),
                Math.min(maxX, clipX1), Math.min(maxY, clipY1))) {
            flushLayer();
        }
        return true;
    }

    void emitShape(Shape s) {
        if (fallback) {
            emitFallback(s);
            return;
        }
        float x = s.x;
        float y = s.y;
        float w = s.width;
        float h = s.height;
        float cx = x + w * 0.5F;
        float cy = y + h * 0.5F;
        float hw = w * 0.5F;
        float hh = h * 0.5F;
        float maxRadius = Math.min(hw, hh);
        float rTl = Math.min(s.rTl, maxRadius);
        float rTr = Math.min(s.rTr, maxRadius);
        float rBr = Math.min(s.rBr, maxRadius);
        float rBl = Math.min(s.rBl, maxRadius);

        Theme.Shadow drop = s.shadow;
        if (drop != null && UiColor.alpha(drop.color()) > 0 && drop.blur() > 0.0F) {
            float sigma = Math.max(0.5F, drop.blur() * 0.5F);
            float extent = sigma * 3.0F + Math.abs(drop.spread());
            int shadowColor = UiColor.multiplyAlpha(drop.color(), alpha * s.shadowAlpha);
            shapes.beginQuad(-1);
            shapes.setHalf(hw, hh);
            shapes.setRadii(rTl, rTr, rBr, rBl);
            shapes.setStyle(0.0F, sigma, drop.spread(), 0.0F);
            shapes.setGrad(drop.offsetX(), drop.offsetY(), 0.0F, 0.0F);
            applyClip();
            shapes.setExtra(0.0F, 0.0F, 0.0F);
            shapes.setColors(shadowColor, shadowColor, 0);
            emitRect(x + Math.min(0.0F, drop.offsetX()) - extent, y + Math.min(0.0F, drop.offsetY()) - extent,
                    x + w + Math.max(0.0F, drop.offsetX()) + extent, y + h + Math.max(0.0F, drop.offsetY()) + extent,
                    cx, cy);
            shapeCount++;
        }

        int fill0 = UiColor.multiplyAlpha(s.fill0, alpha);
        int fill1 = UiColor.multiplyAlpha(s.fill1, alpha);
        int border = UiColor.multiplyAlpha(s.borderColor, alpha);
        boolean hasBorder = s.borderWidth > 0.0F && UiColor.alpha(border) > 0;
        if (UiColor.alpha(fill0) > 0 || UiColor.alpha(fill1) > 0 || hasBorder) {
            shapes.beginQuad(-1);
            shapes.setHalf(hw, hh);
            shapes.setRadii(rTl, rTr, rBr, rBl);
            shapes.setStyle(hasBorder ? s.borderWidth : 0.0F, 0.0F, 0.0F, 0.0F);
            if (s.gradientType == 1) {
                shapes.setGrad(s.g0 - hw, s.g1 - hh, s.g2 - hw, s.g3 - hh);
            } else if (s.gradientType == 2) {
                shapes.setGrad(s.g0 - hw, s.g1 - hh, s.g2, 0.0F);
            } else {
                shapes.setGrad(0.0F, 0.0F, 0.0F, 0.0F);
            }
            applyClip();
            shapes.setExtra(0.0F, 0.0F, s.gradientType);
            shapes.setColors(fill0, s.gradientType == 0 ? fill0 : fill1, border);
            emitRect(x - AA_PAD, y - AA_PAD, x + w + AA_PAD, y + h + AA_PAD, cx, cy);
            shapeCount++;
        }

        Theme.Shadow inner = s.innerShadow;
        if (inner != null && UiColor.alpha(inner.color()) > 0 && inner.blur() > 0.0F) {
            float sigma = Math.max(0.5F, inner.blur() * 0.5F);
            int shadowColor = UiColor.multiplyAlpha(inner.color(), alpha);
            shapes.beginQuad(-1);
            shapes.setHalf(hw, hh);
            shapes.setRadii(rTl, rTr, rBr, rBl);
            shapes.setStyle(0.0F, sigma, inner.spread(), 0.0F);
            shapes.setGrad(inner.offsetX(), inner.offsetY(), 0.0F, 0.0F);
            applyClip();
            shapes.setExtra(1.0F, 0.0F, 0.0F);
            shapes.setColors(shadowColor, shadowColor, 0);
            emitRect(x - AA_PAD, y - AA_PAD, x + w + AA_PAD, y + h + AA_PAD, cx, cy);
            shapeCount++;
        }
    }

    /** 着色器未就绪时（首次资源加载期间）用原版矩形近似。 */
    private void emitFallback(Shape s) {
        if (s.gradientType == 2) {
            // 径向渐变多用于光晕和暗角，近似成纯色反而突兀，干脆不画
            return;
        }
        int fill = UiColor.multiplyAlpha(s.fill0, alpha);
        int fillEnd = UiColor.multiplyAlpha(s.fill1, alpha);
        int border = UiColor.multiplyAlpha(s.borderColor, alpha);
        boolean vertical = s.gradientType == 1 && Math.abs(s.g3 - s.g1) >= Math.abs(s.g2 - s.g0);
        boolean horizontal = s.gradientType == 1 && !vertical;
        int x0 = Math.round(s.x);
        int y0 = Math.round(s.y);
        int x1 = Math.round(s.x + s.width);
        int y1 = Math.round(s.y + s.height);
        boolean hasBorder = s.borderWidth > 0.0F && UiColor.alpha(border) > 0;
        custom(s.x, s.y, s.width, s.height, g -> {
            int ix0 = x0;
            int iy0 = y0;
            int ix1 = x1;
            int iy1 = y1;
            if (hasBorder) {
                g.fill(x0, y0, x1, y1, border);
                int inset = Math.max(1, Math.round(s.borderWidth));
                ix0 += inset;
                iy0 += inset;
                ix1 -= inset;
                iy1 -= inset;
            }
            if (vertical) {
                g.fillGradient(ix0, iy0, ix1, iy1, fill, fillEnd);
            } else if (horizontal) {
                int strips = Math.max(1, Math.min(24, ix1 - ix0));
                for (int i = 0; i < strips; i++) {
                    int sx0 = ix0 + (ix1 - ix0) * i / strips;
                    int sx1 = ix0 + (ix1 - ix0) * (i + 1) / strips;
                    g.fill(sx0, iy0, sx1, iy1, UiColor.lerp(fill, fillEnd, (i + 0.5F) / strips));
                }
            } else {
                g.fill(ix0, iy0, ix1, iy1, fill);
            }
        });
    }

    private void emitRect(float x0, float y0, float x1, float y1, float centerX, float centerY) {
        Matrix4f pose = graphics.pose().last().pose();
        shapes.vertex(pose, x0, y0, x0 - centerX, y0 - centerY);
        shapes.vertex(pose, x0, y1, x0 - centerX, y1 - centerY);
        shapes.vertex(pose, x1, y1, x1 - centerX, y1 - centerY);
        shapes.vertex(pose, x1, y0, x1 - centerX, y0 - centerY);
    }

    private void applyClip() {
        shapes.setClip(clipX0, clipY0, clipX1, clipY1, clipRadius);
    }

    // ==================== 内部：原生命令 ====================

    private boolean recordNative(float x, float y, float width, float height) {
        if (!active) {
            throw new IllegalStateException("UiCanvas 未开始绘制");
        }
        float gx0 = toGuiX(x, y);
        float gy0 = toGuiY(x, y);
        float gx1 = toGuiX(x + width, y + height);
        float gy1 = toGuiY(x + width, y + height);
        float minX = Math.min(gx0, gx1);
        float minY = Math.min(gy0, gy1);
        float maxX = Math.max(gx0, gx1);
        float maxY = Math.max(gy0, gy1);
        if (!visibleGui(minX, minY, maxX, maxY)) {
            return false;
        }
        if (nativeCount * 4 + 4 > nativeBounds.length) {
            float[] grown = new float[nativeBounds.length * 2];
            System.arraycopy(nativeBounds, 0, grown, 0, nativeBounds.length);
            nativeBounds = grown;
        }
        int base = nativeCount * 4;
        nativeBounds[base] = Math.max(minX, clipX0);
        nativeBounds[base + 1] = Math.max(minY, clipY0);
        nativeBounds[base + 2] = Math.min(maxX, clipX1);
        nativeBounds[base + 3] = Math.min(maxY, clipY1);
        nativeCount++;
        return true;
    }

    private boolean overlapsNative(float x0, float y0, float x1, float y1) {
        for (int i = 0; i < nativeCount; i++) {
            int base = i * 4;
            if (x1 > nativeBounds[base] && x0 < nativeBounds[base + 2]
                    && y1 > nativeBounds[base + 1] && y0 < nativeBounds[base + 3]) {
                return true;
            }
        }
        return false;
    }

    private NativeCommand obtain(int type) {
        NativeCommand command = pool.isEmpty() ? new NativeCommand() : pool.remove(pool.size() - 1);
        command.type = type;
        return command;
    }

    private void snapshot(NativeCommand command, float x, float y, float scale) {
        Matrix4f pose = graphics.pose().last().pose();
        command.pose.set(pose);
        if (command.type != NativeCommand.CUSTOM) {
            command.pose.translate(x, y, 0.0F);
            if (scale != 1.0F) {
                command.pose.scale(scale, scale, 1.0F);
            }
            snapToPixel(command.pose);
        }
        command.clipX0 = clipX0;
        command.clipY0 = clipY0;
        command.clipX1 = clipX1;
        command.clipY1 = clipY1;
    }

    /** 把文字与物品的原点对齐到物理像素，避免原版位图字模糊。 */
    private static void snapToPixel(Matrix4f pose) {
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        if (guiScale <= 0.0) {
            return;
        }
        pose.m30((float) (Math.round(pose.m30() * guiScale) / guiScale));
        pose.m31((float) (Math.round(pose.m31() * guiScale) / guiScale));
    }

    private void flushLayer() {
        if (shapes.isEmpty() && natives.isEmpty()) {
            return;
        }
        layerCount++;
        if (!shapes.isEmpty()) {
            graphics.flush();
            shapes.draw();
        }
        if (!natives.isEmpty()) {
            runNatives();
        }
        nativeCount = 0;
    }

    private void runNatives() {
        GuiGraphics g = graphics;
        g.drawManaged(() -> {
            boolean scissor = false;
            float sx0 = 0.0F;
            float sy0 = 0.0F;
            float sx1 = 0.0F;
            float sy1 = 0.0F;
            PoseStack poseStack = g.pose();
            for (NativeCommand command : natives) {
                boolean clipped = command.clipX0 > -NO_CLIP || command.clipY0 > -NO_CLIP
                        || command.clipX1 < NO_CLIP || command.clipY1 < NO_CLIP;
                if (clipped) {
                    if (!scissor || sx0 != command.clipX0 || sy0 != command.clipY0
                            || sx1 != command.clipX1 || sy1 != command.clipY1) {
                        if (scissor) {
                            g.disableScissor();
                        }
                        g.enableScissor((int) Math.floor(command.clipX0), (int) Math.floor(command.clipY0),
                                (int) Math.ceil(command.clipX1), (int) Math.ceil(command.clipY1));
                        scissor = true;
                        sx0 = command.clipX0;
                        sy0 = command.clipY0;
                        sx1 = command.clipX1;
                        sy1 = command.clipY1;
                    }
                } else if (scissor) {
                    g.disableScissor();
                    scissor = false;
                }
                poseStack.pushPose();
                poseStack.last().pose().set(command.pose);
                try {
                    command.execute(g, font);
                } finally {
                    poseStack.popPose();
                }
            }
            if (scissor) {
                g.disableScissor();
            }
        });
        for (NativeCommand command : natives) {
            command.release();
            pool.add(command);
        }
        natives.clear();
    }

    /** 可复用的矩形图形描述。 */
    public static final class Shape {
        private final UiCanvas canvas;
        float x;
        float y;
        float width;
        float height;
        float rTl;
        float rTr;
        float rBr;
        float rBl;
        int fill0;
        int fill1;
        int gradientType;
        float g0;
        float g1;
        float g2;
        float g3;
        float borderWidth;
        int borderColor;
        Theme.Shadow shadow;
        Theme.Shadow innerShadow;
        float shadowAlpha;

        Shape(UiCanvas canvas) {
            this.canvas = canvas;
        }

        Shape reset(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = Math.max(0.0F, width);
            this.height = Math.max(0.0F, height);
            rTl = rTr = rBr = rBl = 0.0F;
            fill0 = fill1 = 0;
            gradientType = 0;
            borderWidth = 0.0F;
            borderColor = 0;
            shadow = null;
            innerShadow = null;
            shadowAlpha = 1.0F;
            return this;
        }

        public Shape radius(float radius) {
            rTl = rTr = rBr = rBl = Math.max(0.0F, radius);
            return this;
        }

        public Shape radius(float topLeft, float topRight, float bottomRight, float bottomLeft) {
            rTl = topLeft;
            rTr = topRight;
            rBr = bottomRight;
            rBl = bottomLeft;
            return this;
        }

        public Shape fill(int color) {
            fill0 = color;
            fill1 = color;
            gradientType = 0;
            return this;
        }

        /** 线性渐变：从上到下。 */
        public Shape verticalGradient(int top, int bottom) {
            return linear(top, bottom, 0.0F, 0.0F, 0.0F, height);
        }

        /** 线性渐变：从左到右。 */
        public Shape horizontalGradient(int left, int right) {
            return linear(left, right, 0.0F, 0.0F, width, 0.0F);
        }

        /** 线性渐变，起止点为相对图形左上角的坐标。 */
        public Shape linear(int from, int to, float x0, float y0, float x1, float y1) {
            fill0 = from;
            fill1 = to;
            gradientType = 1;
            g0 = x0;
            g1 = y0;
            g2 = x1;
            g3 = y1;
            return this;
        }

        /** 径向渐变，圆心为相对图形左上角的坐标。 */
        public Shape radial(int inner, int outer, float centerX, float centerY, float radius) {
            fill0 = inner;
            fill1 = outer;
            gradientType = 2;
            g0 = centerX;
            g1 = centerY;
            g2 = radius;
            return this;
        }

        public Shape border(float width, int color) {
            borderWidth = width;
            borderColor = color;
            return this;
        }

        public Shape shadow(Theme.Shadow value) {
            shadow = value;
            return this;
        }

        /** 阴影整体不透明度系数，用于 hover 时渐变加深。 */
        public Shape shadowAlpha(float factor) {
            shadowAlpha = factor;
            return this;
        }

        public Shape innerShadow(Theme.Shadow value) {
            innerShadow = value;
            return this;
        }

        public void draw() {
            if (width <= 0.0F || height <= 0.0F) {
                return;
            }
            float ox = 0.0F;
            float oy = 0.0F;
            float extent = 0.0F;
            if (shadow != null && shadow.blur() > 0.0F) {
                extent = shadow.blur() * 1.5F + Math.abs(shadow.spread());
                ox = shadow.offsetX();
                oy = shadow.offsetY();
            }
            // 分层判断按图形本体；阴影只要可见就绘制
            float bx = x;
            float by = y;
            float bw = width;
            float bh = height;
            if (!canvas.visibleShape(bx - extent + Math.min(0.0F, ox), by - extent + Math.min(0.0F, oy),
                    bw + extent * 2.0F + Math.abs(ox), bh + extent * 2.0F + Math.abs(oy))) {
                return;
            }
            if (!canvas.beforeShapeCore(bx, by, bw, bh)) {
                return;
            }
            canvas.emitShape(this);
        }
    }

    boolean visibleShape(float x, float y, float width, float height) {
        float gx0 = toGuiX(x, y);
        float gy0 = toGuiY(x, y);
        float gx1 = toGuiX(x + width, y + height);
        float gy1 = toGuiY(x + width, y + height);
        return visibleGui(Math.min(gx0, gx1), Math.min(gy0, gy1), Math.max(gx0, gx1), Math.max(gy0, gy1));
    }

    /** 按图形本体做重叠判断；本体完全在裁剪区外（仅阴影可见）时也允许绘制。 */
    boolean beforeShapeCore(float x, float y, float width, float height) {
        if (!active) {
            throw new IllegalStateException("UiCanvas 未开始绘制");
        }
        float gx0 = toGuiX(x, y);
        float gy0 = toGuiY(x, y);
        float gx1 = toGuiX(x + width, y + height);
        float gy1 = toGuiY(x + width, y + height);
        float minX = Math.max(Math.min(gx0, gx1), clipX0);
        float minY = Math.max(Math.min(gy0, gy1), clipY0);
        float maxX = Math.min(Math.max(gx0, gx1), clipX1);
        float maxY = Math.min(Math.max(gy0, gy1), clipY1);
        if (maxX > minX && maxY > minY && overlapsNative(minX, minY, maxX, maxY)) {
            flushLayer();
        }
        return true;
    }

    private static final class NativeCommand {
        static final int TEXT = 0;
        static final int STRING = 1;
        static final int ITEM = 2;
        static final int CUSTOM = 3;

        final Matrix4f pose = new Matrix4f();
        int type;
        FormattedCharSequence sequence;
        String string;
        ItemStack stack;
        Consumer<GuiGraphics> custom;
        int color;
        boolean shadow;
        boolean decorations;
        float alpha;
        float clipX0;
        float clipY0;
        float clipX1;
        float clipY1;

        void execute(GuiGraphics g, Font font) {
            switch (type) {
                case TEXT -> g.drawString(font, sequence, 0.0F, 0.0F, color, shadow);
                case STRING -> g.drawString(font, string, 0.0F, 0.0F, color, shadow);
                case ITEM -> {
                    boolean faded = alpha < 0.999F;
                    if (faded) {
                        g.setColor(1.0F, 1.0F, 1.0F, alpha);
                    }
                    g.renderItem(stack, 0, 0);
                    if (decorations) {
                        g.renderItemDecorations(font, stack, 0, 0);
                    }
                    if (faded) {
                        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                    }
                }
                case CUSTOM -> {
                    boolean faded = alpha < 0.999F;
                    if (faded) {
                        g.setColor(1.0F, 1.0F, 1.0F, alpha);
                    }
                    custom.accept(g);
                    if (faded) {
                        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                    }
                }
                default -> {
                }
            }
        }

        void release() {
            sequence = null;
            string = null;
            stack = null;
            custom = null;
        }
    }
}
