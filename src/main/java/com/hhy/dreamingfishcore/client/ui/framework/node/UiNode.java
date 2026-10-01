package com.hhy.dreamingfishcore.client.ui.framework.node;

import com.hhy.dreamingfishcore.client.ui.framework.anim.AnimatedFloat;
import com.hhy.dreamingfishcore.client.ui.framework.anim.Spring;
import com.hhy.dreamingfishcore.client.ui.framework.core.UiClock;
import com.hhy.dreamingfishcore.client.ui.framework.core.UiSounds;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 界面节点。保存布局样式、视觉样式、交互状态与布局结果。
 *
 * <p>子节点坐标相对父节点左上角。布局只在节点被标脏后重新计算；绘制每帧进行，
 * 绘制时记录节点在 GUI 坐标中的范围，供命中测试使用。</p>
 *
 * <p>样式设置方法均返回自身类型，可以链式调用：
 * {@code Ui.row(...).gap(8).padding(12).background(color).radius(6)}。</p>
 *
 * @param <S> 子类自身类型
 */
public abstract class UiNode<S extends UiNode<S>> {
    public static final float AUTO = Float.NaN;
    static final float INF = Float.POSITIVE_INFINITY;

    // ---------- 树 ----------
    UiNode<?> parent;
    final ArrayList<UiNode<?>> children = new ArrayList<>();
    UiRoot root;
    Object key;
    String debugName;
    Theme.Kind themeKind;

    // ---------- 布局样式 ----------
    Flow flow = Flow.COLUMN;
    float width = AUTO;
    float height = AUTO;
    float widthPercent = AUTO;
    float heightPercent = AUTO;
    float minWidth;
    float minHeight;
    float maxWidth = INF;
    float maxHeight = INF;
    float padLeft;
    float padTop;
    float padRight;
    float padBottom;
    float marginLeft;
    float marginTop;
    float marginRight;
    float marginBottom;
    float gap;
    float crossGap = AUTO;
    float grow;
    float shrink = AUTO;
    float basis = AUTO;
    Align alignItems = Align.STRETCH;
    Align alignSelf = Align.AUTO;
    Justify justify = Justify.START;
    boolean wrap;
    boolean absolute;
    float insetLeft = AUTO;
    float insetTop = AUTO;
    float insetRight = AUTO;
    float insetBottom = AUTO;
    float aspectRatio = AUTO;
    boolean visible = true;

    // ---------- 布局结果（相对父节点） ----------
    float x;
    float y;
    float w;
    float h;
    boolean needsLayout = true;
    boolean measureValid;
    float measureAvailW = Float.NaN;
    float measureAvailH = Float.NaN;
    float measuredW;
    float measuredH;
    float laidOutW = -1.0F;
    float laidOutH = -1.0F;

    // ---------- 视觉样式 ----------
    int background;
    int backgroundEnd;
    boolean gradient;
    float radiusTl;
    float radiusTr;
    float radiusBr;
    float radiusBl;
    float borderWidth;
    int borderColor;
    Theme.Shadow shadow;
    float opacity = 1.0F;
    float translateX;
    float translateY;
    float scale = 1.0F;
    boolean clipChildren;
    boolean cullable = true;

    private AnimatedFloat animOpacity;
    private AnimatedFloat animTranslateX;
    private AnimatedFloat animTranslateY;
    private AnimatedFloat animScale;
    private EnterEffect enterEffect = EnterEffect.NONE;
    private AnimatedFloat enterProgress;

    // ---------- 交互 ----------
    boolean hovered;
    boolean pressed;
    boolean focused;
    boolean disabled;
    boolean focusable;
    boolean pointerEvents = true;
    Cursor cursor;
    Runnable onClick;
    Consumer<Boolean> onHover;
    Supplier<List<Component>> tooltip;
    private List<Runnable> updaters;

    // ---------- 绘制记录（GUI 坐标） ----------
    float paintX0;
    float paintY0;
    float paintX1;
    float paintY1;
    float paintClipX0;
    float paintClipY0;
    float paintClipX1;
    float paintClipY1;
    long paintedFrame = -1L;

    @SuppressWarnings("unchecked")
    protected final S self() {
        return (S) this;
    }

    // ==================== 树操作 ====================

    public S add(UiNode<?>... nodes) {
        for (UiNode<?> node : nodes) {
            if (node != null) {
                attachChild(children.size(), node);
            }
        }
        markDirty();
        return self();
    }

    public S addAll(Collection<? extends UiNode<?>> nodes) {
        for (UiNode<?> node : nodes) {
            if (node != null) {
                attachChild(children.size(), node);
            }
        }
        markDirty();
        return self();
    }

    public S insert(int index, UiNode<?> node) {
        attachChild(Math.max(0, Math.min(index, children.size())), node);
        markDirty();
        return self();
    }

    public S remove(UiNode<?> node) {
        if (children.remove(node)) {
            detachTree(node);
            markDirty();
        }
        return self();
    }

    public S clearChildren() {
        for (UiNode<?> child : children) {
            detachTree(child);
        }
        children.clear();
        markDirty();
        return self();
    }

    /** 用新列表替换全部子节点；已存在的节点保留（不会触发重新挂载）。 */
    public S setChildren(List<? extends UiNode<?>> nodes) {
        for (UiNode<?> child : children) {
            if (!nodes.contains(child)) {
                detachTree(child);
            }
        }
        List<UiNode<?>> previous = new ArrayList<>(children);
        children.clear();
        for (UiNode<?> node : nodes) {
            if (node == null) {
                continue;
            }
            if (previous.contains(node)) {
                children.add(node);
            } else {
                attachChild(children.size(), node);
            }
        }
        markDirty();
        return self();
    }

    public List<UiNode<?>> children() {
        return children;
    }

    public UiNode<?> parent() {
        return parent;
    }

    public UiRoot root() {
        return root;
    }

    private void attachChild(int index, UiNode<?> node) {
        if (node.parent != null) {
            node.parent.children.remove(node);
            node.parent.markDirty();
        }
        node.parent = this;
        children.add(index, node);
        if (root != null) {
            attachTree(node, root);
        }
    }

    static void attachTree(UiNode<?> node, UiRoot root) {
        boolean newlyAttached = node.root != root;
        node.root = root;
        if (newlyAttached) {
            node.onAttach();
        }
        for (UiNode<?> child : node.children) {
            attachTree(child, root);
        }
    }

    static void detachTree(UiNode<?> node) {
        for (UiNode<?> child : node.children) {
            detachTree(child);
        }
        if (node.root != null) {
            node.root.onNodeDetached(node);
            node.root = null;
            node.onDetach();
        }
        node.hovered = false;
        node.pressed = false;
        node.focused = false;
    }

    /** 节点挂到界面上时调用。 */
    protected void onAttach() {
    }

    /** 节点从界面上移除时调用。 */
    protected void onDetach() {
    }

    /** 标记需要重新测量与布局（向上传播到根）。 */
    public final void markDirty() {
        for (UiNode<?> node = this; node != null; node = node.parent) {
            node.measureValid = false;
            node.needsLayout = true;
        }
    }

    // ==================== 主题 ====================

    /** 为本节点及其子树指定主题（终端或 HUD）。 */
    public S theme(Theme.Kind kind) {
        themeKind = kind;
        return self();
    }

    /** 沿父链查找主题，默认终端主题。 */
    public Theme theme() {
        for (UiNode<?> node = this; node != null; node = node.parent) {
            if (node.themeKind != null) {
                return Theme.of(node.themeKind);
            }
        }
        return Theme.terminal();
    }

    /** 当前主题中某个颜色角色的色值。 */
    public int themeColor(com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole role) {
        return theme().color(role);
    }

    // ==================== 布局样式 ====================

    public S key(Object value) {
        this.key = value;
        return self();
    }

    public Object key() {
        return key;
    }

    public S name(String value) {
        this.debugName = value;
        return self();
    }

    public S flow(Flow value) {
        if (flow != value) {
            flow = value;
            markDirty();
        }
        return self();
    }

    public S row() {
        return flow(Flow.ROW);
    }

    public S column() {
        return flow(Flow.COLUMN);
    }

    public S stack() {
        return flow(Flow.STACK);
    }

    public S width(float value) {
        width = value;
        widthPercent = AUTO;
        markDirty();
        return self();
    }

    public S height(float value) {
        height = value;
        heightPercent = AUTO;
        markDirty();
        return self();
    }

    public S size(float w, float h) {
        width(w);
        return height(h);
    }

    /** 宽度为父节点内容宽度的比例（0–1）。 */
    public S widthPercent(float fraction) {
        width = AUTO;
        widthPercent = fraction;
        markDirty();
        return self();
    }

    public S heightPercent(float fraction) {
        height = AUTO;
        heightPercent = fraction;
        markDirty();
        return self();
    }

    public S fillWidth() {
        return widthPercent(1.0F);
    }

    public S fillHeight() {
        return heightPercent(1.0F);
    }

    public S minWidth(float value) {
        minWidth = value;
        markDirty();
        return self();
    }

    public S minHeight(float value) {
        minHeight = value;
        markDirty();
        return self();
    }

    public S maxWidth(float value) {
        maxWidth = value;
        markDirty();
        return self();
    }

    public S maxHeight(float value) {
        maxHeight = value;
        markDirty();
        return self();
    }

    public S padding(float all) {
        return padding(all, all, all, all);
    }

    public S padding(float horizontal, float vertical) {
        return padding(horizontal, vertical, horizontal, vertical);
    }

    public S padding(float left, float top, float right, float bottom) {
        padLeft = left;
        padTop = top;
        padRight = right;
        padBottom = bottom;
        markDirty();
        return self();
    }

    public S margin(float all) {
        return margin(all, all, all, all);
    }

    public S margin(float horizontal, float vertical) {
        return margin(horizontal, vertical, horizontal, vertical);
    }

    public S margin(float left, float top, float right, float bottom) {
        marginLeft = left;
        marginTop = top;
        marginRight = right;
        marginBottom = bottom;
        markDirty();
        return self();
    }

    public S gap(float value) {
        gap = value;
        markDirty();
        return self();
    }

    /** 换行时行与行之间的间距；未设置时与 {@link #gap} 相同。 */
    public S crossGap(float value) {
        crossGap = value;
        markDirty();
        return self();
    }

    public S grow(float value) {
        grow = value;
        markDirty();
        return self();
    }

    public S shrink(float value) {
        shrink = value;
        markDirty();
        return self();
    }

    public S basis(float value) {
        basis = value;
        markDirty();
        return self();
    }

    public S alignItems(Align value) {
        alignItems = value;
        markDirty();
        return self();
    }

    public S alignSelf(Align value) {
        alignSelf = value;
        markDirty();
        return self();
    }

    public S justify(Justify value) {
        justify = value;
        markDirty();
        return self();
    }

    /** 主轴、交叉轴都居中。 */
    public S center() {
        justify = Justify.CENTER;
        alignItems = Align.CENTER;
        markDirty();
        return self();
    }

    public S wrap(boolean value) {
        wrap = value;
        markDirty();
        return self();
    }

    /** 绝对定位：不参与 flex 排布，按 inset 相对父节点定位。 */
    public S absolute(float left, float top, float right, float bottom) {
        absolute = true;
        insetLeft = left;
        insetTop = top;
        insetRight = right;
        insetBottom = bottom;
        markDirty();
        return self();
    }

    public S aspectRatio(float ratio) {
        aspectRatio = ratio;
        markDirty();
        return self();
    }

    public S visible(boolean value) {
        if (visible != value) {
            visible = value;
            markDirty();
        }
        return self();
    }

    public boolean isVisible() {
        return visible;
    }

    // ==================== 视觉样式 ====================

    public S background(int color) {
        background = color;
        backgroundEnd = color;
        gradient = false;
        return self();
    }

    /** 纵向渐变背景。 */
    public S backgroundGradient(int top, int bottom) {
        background = top;
        backgroundEnd = bottom;
        gradient = true;
        return self();
    }

    public S radius(float all) {
        radiusTl = radiusTr = radiusBr = radiusBl = all;
        return self();
    }

    public S radius(float topLeft, float topRight, float bottomRight, float bottomLeft) {
        radiusTl = topLeft;
        radiusTr = topRight;
        radiusBr = bottomRight;
        radiusBl = bottomLeft;
        return self();
    }

    public S border(float widthValue, int color) {
        borderWidth = widthValue;
        borderColor = color;
        return self();
    }

    public S shadow(Theme.Shadow value) {
        shadow = value;
        return self();
    }

    public S opacity(float value) {
        opacity = value;
        if (animOpacity != null) {
            animOpacity.snap(value);
        }
        return self();
    }

    public S translate(float dx, float dy) {
        translateX = dx;
        translateY = dy;
        if (animTranslateX != null) {
            animTranslateX.snap(dx);
            animTranslateY.snap(dy);
        }
        return self();
    }

    public S scale(float value) {
        scale = value;
        if (animScale != null) {
            animScale.snap(value);
        }
        return self();
    }

    public S clip(boolean value) {
        clipChildren = value;
        return self();
    }

    /** 进场效果；节点首次绘制时播放一次。 */
    public S enter(EnterEffect effect) {
        enterEffect = effect == null ? EnterEffect.NONE : effect;
        enterProgress = null;
        return self();
    }

    /** 重新播放进场效果。 */
    public S replayEnter() {
        enterProgress = null;
        return self();
    }

    /** 以弹簧动画过渡到目标不透明度。 */
    public S animateOpacity(float target) {
        if (animOpacity == null) {
            animOpacity = AnimatedFloat.tween(opacity, Theme.Motion.NORMAL, Theme.Motion.STANDARD);
        }
        animOpacity.set(target);
        opacity = target;
        return self();
    }

    public S animateTranslate(float dx, float dy) {
        if (animTranslateX == null) {
            animTranslateX = AnimatedFloat.spring(translateX, Spring.GENTLE);
            animTranslateY = AnimatedFloat.spring(translateY, Spring.GENTLE);
        }
        animTranslateX.set(dx);
        animTranslateY.set(dy);
        translateX = dx;
        translateY = dy;
        return self();
    }

    public S animateScale(float target) {
        if (animScale == null) {
            animScale = AnimatedFloat.spring(scale, Spring.SNAPPY);
        }
        animScale.set(target);
        scale = target;
        return self();
    }

    // ==================== 交互 ====================

    public S onClick(Runnable action) {
        onClick = action;
        if (action != null && cursor == null) {
            cursor = Cursor.POINTER;
        }
        return self();
    }

    public Runnable onClickHandler() {
        return onClick;
    }

    public S onHover(Consumer<Boolean> listener) {
        onHover = listener;
        return self();
    }

    public S tooltip(Component text) {
        tooltip = text == null ? null : () -> List.of(text);
        return self();
    }

    public S tooltip(Supplier<List<Component>> lines) {
        tooltip = lines;
        return self();
    }

    public S cursor(Cursor value) {
        cursor = value;
        return self();
    }

    public S disabled(boolean value) {
        if (disabled != value) {
            disabled = value;
            onStateChanged();
        }
        return self();
    }

    public S focusable(boolean value) {
        focusable = value;
        return self();
    }

    public S pointerEvents(boolean value) {
        pointerEvents = value;
        return self();
    }

    /** 每帧布局前执行的更新逻辑。 */
    public S onUpdate(Runnable updater) {
        if (updaters == null) {
            updaters = new ArrayList<>(2);
        }
        updaters.add(updater);
        return self();
    }

    /**
     * 拉取式数据绑定：每帧读取 {@code source}，值变化（equals 比较）时调用 {@code apply}。
     * 首次挂载时立即应用一次。
     */
    public <T> S bind(Supplier<T> source, Consumer<T> apply) {
        Object[] last = {new Object()};
        return onUpdate(() -> {
            T value = source.get();
            if (!Objects.equals(value, last[0])) {
                last[0] = value;
                apply.accept(value);
            }
        });
    }

    public boolean isHovered() {
        return hovered;
    }

    public boolean isPressed() {
        return pressed;
    }

    public boolean isFocused() {
        return focused;
    }

    public boolean isDisabled() {
        return disabled;
    }

    /** 本节点或祖先被禁用。 */
    public boolean isEffectivelyDisabled() {
        for (UiNode<?> node = this; node != null; node = node.parent) {
            if (node.disabled) {
                return true;
            }
        }
        return false;
    }

    /** 悬停、按下、焦点、禁用状态变化后调用；子类据此更新动画目标。 */
    protected void onStateChanged() {
    }

    // ---------- 事件（子类覆盖；返回 true 表示已处理，不再冒泡） ----------

    /** 鼠标按下。返回 true 后本节点捕获后续的拖拽与抬起。 */
    protected boolean onMouseDown(double guiX, double guiY, int button) {
        return button == 0 && onClick != null && !isEffectivelyDisabled();
    }

    /** 鼠标抬起；{@code inside} 表示抬起时指针仍在节点上。 */
    protected void onMouseUp(double guiX, double guiY, int button, boolean inside) {
        if (inside && button == 0 && onClick != null && !isEffectivelyDisabled()) {
            UiSounds.click();
            onClick.run();
        }
    }

    protected boolean onMouseDrag(double guiX, double guiY, int button, double dragX, double dragY) {
        return false;
    }

    protected boolean onScroll(double guiX, double guiY, double scrollX, double scrollY) {
        return false;
    }

    protected boolean onKeyDown(int keyCode, int scanCode, int modifiers) {
        if (focused && onClick != null && !isEffectivelyDisabled()
                && (keyCode == 257 || keyCode == 335 || keyCode == 32)) {
            UiSounds.click();
            onClick.run();
            return true;
        }
        return false;
    }

    protected boolean onCharTyped(char character, int modifiers) {
        return false;
    }

    // ==================== 布局查询 ====================

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float width() {
        return w;
    }

    public float height() {
        return h;
    }

    public float padLeft() {
        return padLeft;
    }

    public float padTop() {
        return padTop;
    }

    public float padRight() {
        return padRight;
    }

    public float padBottom() {
        return padBottom;
    }

    public float radiusValue() {
        return Math.min(Math.min(radiusTl, radiusTr), Math.min(radiusBr, radiusBl));
    }

    public int backgroundColor() {
        return background;
    }

    public float innerWidth() {
        return Math.max(0.0F, w - padLeft - padRight);
    }

    public float innerHeight() {
        return Math.max(0.0F, h - padTop - padBottom);
    }

    /** 最近一次绘制时的 GUI 坐标范围。 */
    public float guiLeft() {
        return paintX0;
    }

    public float guiTop() {
        return paintY0;
    }

    public float guiRight() {
        return paintX1;
    }

    public float guiBottom() {
        return paintY1;
    }

    boolean paintedIn(long frame) {
        return paintedFrame == frame;
    }

    /** GUI 坐标点是否落在节点最近一次绘制的可见范围内。 */
    public boolean containsPoint(double gx, double gy) {
        return paintedFrame >= 0 && containsGui(gx, gy);
    }

    boolean containsGui(double gx, double gy) {
        return gx >= paintX0 && gx < paintX1 && gy >= paintY0 && gy < paintY1
                && gx >= paintClipX0 && gx < paintClipX1 && gy >= paintClipY0 && gy < paintClipY1;
    }

    /**
     * 测量内容尺寸（不含 padding）。叶子节点覆盖此方法；有子节点的容器由布局引擎计算。
     */
    protected void measureContent(float availableWidth, float availableHeight, Size out) {
        out.set(0.0F, 0.0F);
    }

    /** 布局完成后调用，子类可据此更新依赖尺寸的缓存。 */
    protected void onLayout() {
    }

    // ==================== 更新与绘制 ====================

    void runUpdaters() {
        if (updaters != null) {
            for (int i = 0; i < updaters.size(); i++) {
                updaters.get(i).run();
            }
        }
        update();
    }

    /** 每帧布局前调用。 */
    protected void update() {
    }

    protected float currentOpacity() {
        return animOpacity != null ? animOpacity.get() : opacity;
    }

    public final void paint(UiCanvas canvas) {
        if (!visible) {
            paintedFrame = -1L;
            return;
        }
        float alpha = currentOpacity();
        float tx = animTranslateX != null ? animTranslateX.get() : translateX;
        float ty = animTranslateY != null ? animTranslateY.get() : translateY;
        float sc = animScale != null ? animScale.get() : scale;
        if (!enterEffect.isNone()) {
            if (enterProgress == null) {
                enterProgress = AnimatedFloat.tween(0.0F, enterEffect.duration(), enterEffect.easing());
                enterProgress.delay(enterEffect.delay()).set(1.0F);
            }
            float p = enterProgress.get();
            if (p < 1.0F) {
                float inv = 1.0F - p;
                tx += enterEffect.offsetX() * inv;
                ty += enterEffect.offsetY() * inv;
                sc *= enterEffect.scale() + (1.0F - enterEffect.scale()) * p;
                alpha *= enterEffect.alpha() + (1.0F - enterEffect.alpha()) * Math.min(1.0F, p * 1.4F);
            }
        }
        if (alpha <= 0.003F) {
            paintedFrame = -1L;
            return;
        }

        canvas.push();
        canvas.translate(x + tx, y + ty);
        if (sc != 1.0F) {
            canvas.scale(sc, w * 0.5F, h * 0.5F);
        }
        paintX0 = canvas.toGuiX(0.0F, 0.0F);
        paintY0 = canvas.toGuiY(0.0F, 0.0F);
        paintX1 = canvas.toGuiX(w, h);
        paintY1 = canvas.toGuiY(w, h);
        paintClipX0 = canvas.clipLeft();
        paintClipY0 = canvas.clipTop();
        paintClipX1 = canvas.clipRight();
        paintClipY1 = canvas.clipBottom();
        paintedFrame = UiClock.frame();

        float margin = 48.0F;
        if (cullable && !canvas.visibleGui(paintX0 - margin, paintY0 - margin, paintX1 + margin, paintY1 + margin)) {
            canvas.pop();
            return;
        }
        boolean faded = alpha < 0.999F;
        if (faded) {
            canvas.pushAlpha(alpha);
        }
        paintBackground(canvas);
        paintContent(canvas);
        if (clipChildren) {
            canvas.pushClip(0.0F, 0.0F, w, h, Math.min(Math.min(radiusTl, radiusTr), Math.min(radiusBr, radiusBl)));
            paintChildren(canvas);
            canvas.popClip();
        } else {
            paintChildren(canvas);
        }
        paintOverlay(canvas);
        if (faded) {
            canvas.popAlpha();
        }
        canvas.pop();
    }

    protected void paintBackground(UiCanvas canvas) {
        boolean hasShadow = shadow != null && shadow.blur() > 0.0F && UiColor.alpha(shadow.color()) > 0;
        boolean hasFill = UiColor.alpha(background) > 0 || gradient && UiColor.alpha(backgroundEnd) > 0;
        boolean hasBorder = borderWidth > 0.0F && UiColor.alpha(borderColor) > 0;
        if (!hasShadow && !hasFill && !hasBorder) {
            return;
        }
        UiCanvas.Shape shape = canvas.shape(0.0F, 0.0F, w, h).radius(radiusTl, radiusTr, radiusBr, radiusBl);
        if (gradient) {
            shape.verticalGradient(background, backgroundEnd);
        } else {
            shape.fill(background);
        }
        if (hasBorder) {
            shape.border(borderWidth, borderColor);
        }
        if (hasShadow) {
            shape.shadow(shadow);
        }
        shape.draw();
    }

    protected void paintContent(UiCanvas canvas) {
    }

    protected void paintChildren(UiCanvas canvas) {
        for (int i = 0; i < children.size(); i++) {
            children.get(i).paint(canvas);
        }
    }

    protected void paintOverlay(UiCanvas canvas) {
    }

    @Override
    public String toString() {
        String name = debugName != null ? debugName : getClass().getSimpleName();
        return name + "[" + x + "," + y + " " + w + "x" + h + "]";
    }
}
