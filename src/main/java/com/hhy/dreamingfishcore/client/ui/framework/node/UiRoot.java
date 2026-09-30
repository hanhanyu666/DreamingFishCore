package com.hhy.dreamingfishcore.client.ui.framework.node;

import com.hhy.dreamingfishcore.client.ui.framework.core.UiClock;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 节点树的宿主：每帧执行"更新绑定 → 按需布局 → 悬停计算 → 绘制"，并分发输入。
 *
 * <p>根节点是一个叠放容器，第 0 层为内容，之后是浮层（弹窗、菜单）。命中测试使用上一帧绘制时
 * 记录的 GUI 坐标范围，因此动画中的元素也能被准确点中。</p>
 */
public final class UiRoot {
    private static final double TOOLTIP_DELAY_MS = 450.0;
    private static final double DRAG_THRESHOLD = 2.0;

    private final Box container = new Box().stack().alignItems(Align.STRETCH);
    private final LayoutEngine engine = new LayoutEngine();
    private final UiCanvas canvas = new UiCanvas();
    private final List<UiNode<?>> hoverChain = new ArrayList<>();
    private final List<UiNode<?>> scratchChain = new ArrayList<>();

    private float viewportW = -1.0F;
    private float viewportH = -1.0F;
    private double mouseX = -1.0;
    private double mouseY = -1.0;
    private long paintFrame = -1L;
    private UiNode<?> pressedNode;
    private int pressedButton = -1;
    private double pressX;
    private double pressY;
    private boolean dragging;
    private UiNode<?> focusedNode;
    private UiNode<?> tooltipNode;
    private double tooltipSince;
    private Cursor appliedCursor = Cursor.DEFAULT;
    private int lastLayerCount;

    public UiRoot() {
        container.root = this;
        container.name("root");
    }

    public UiRoot(UiNode<?> content) {
        this();
        setContent(content);
    }

    /** 替换内容层（第 0 层）。 */
    public void setContent(UiNode<?> content) {
        List<UiNode<?>> overlays = container.children.isEmpty()
                ? List.of() : new ArrayList<>(container.children.subList(1, container.children.size()));
        List<UiNode<?>> next = new ArrayList<>();
        next.add(content);
        next.addAll(overlays);
        container.setChildren(next);
        UiNode.attachTree(container, this);
    }

    public UiNode<?> content() {
        return container.children.isEmpty() ? null : container.children.get(0);
    }

    /** 添加浮层（绘制在内容之上）。 */
    public void pushOverlay(UiNode<?> overlay) {
        container.add(overlay);
        UiNode.attachTree(container, this);
    }

    public void removeOverlay(UiNode<?> overlay) {
        if (container.children.indexOf(overlay) > 0) {
            container.remove(overlay);
        }
    }

    public boolean hasOverlay() {
        return container.children.size() > 1;
    }

    public UiNode<?> topOverlay() {
        return container.children.size() > 1 ? container.children.get(container.children.size() - 1) : null;
    }

    public float width() {
        return viewportW;
    }

    public float height() {
        return viewportH;
    }

    public double mouseX() {
        return mouseX;
    }

    public double mouseY() {
        return mouseY;
    }

    public int lastLayerCount() {
        return lastLayerCount;
    }

    /** 手动触发一次完整重新布局（例如字体资源重载后）。 */
    public void invalidateAll() {
        invalidate(container);
    }

    private static void invalidate(UiNode<?> node) {
        node.measureValid = false;
        node.needsLayout = true;
        for (UiNode<?> child : node.children) {
            invalidate(child);
        }
    }

    // ==================== 帧 ====================

    public void render(GuiGraphics graphics, double mouseGuiX, double mouseGuiY, float width, float height) {
        mouseX = mouseGuiX;
        mouseY = mouseGuiY;
        update(container);
        if (width != viewportW || height != viewportH || container.needsLayout) {
            viewportW = width;
            viewportH = height;
            double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
            engine.layoutRoot(container, width, height, (float) (1.0 / Math.max(1.0, guiScale)));
        }
        updateHover();

        canvas.begin(graphics);
        container.paint(canvas);
        paintFrame = UiClock.frame();
        canvas.end();
        lastLayerCount = canvas.layerCount();

        renderTooltip(graphics);
        applyCursor();
    }

    private void update(UiNode<?> node) {
        node.runUpdaters();
        List<UiNode<?>> children = node.children;
        for (int i = 0; i < children.size(); i++) {
            update(children.get(i));
        }
    }

    /** 界面关闭时调用：恢复鼠标指针、清理状态。 */
    public void close() {
        setCursor(Cursor.DEFAULT);
        clearHover();
        pressedNode = null;
        if (focusedNode != null) {
            focusedNode.focused = false;
            focusedNode.onStateChanged();
            focusedNode = null;
        }
    }

    // ==================== 命中测试与悬停 ====================

    public UiNode<?> hitTest(double gx, double gy) {
        return hitTest(container, gx, gy);
    }

    private UiNode<?> hitTest(UiNode<?> node, double gx, double gy) {
        if (!node.visible || node.paintedFrame != paintFrame) {
            return null;
        }
        boolean inside = node.containsGui(gx, gy);
        if (!inside && node.clipChildren) {
            return null;
        }
        for (int i = node.children.size() - 1; i >= 0; i--) {
            UiNode<?> hit = hitTest(node.children.get(i), gx, gy);
            if (hit != null) {
                return hit;
            }
        }
        return inside && node.pointerEvents && node != container ? node : null;
    }

    private void updateHover() {
        scratchChain.clear();
        UiNode<?> leaf = mouseX < 0 ? null : hitTest(mouseX, mouseY);
        for (UiNode<?> node = leaf; node != null && node != container; node = node.parent) {
            scratchChain.add(node);
        }
        for (UiNode<?> old : hoverChain) {
            if (!scratchChain.contains(old)) {
                setHovered(old, false);
            }
        }
        for (UiNode<?> node : scratchChain) {
            if (!node.hovered) {
                setHovered(node, true);
            }
        }
        hoverChain.clear();
        hoverChain.addAll(scratchChain);

        UiNode<?> tip = null;
        for (UiNode<?> node : hoverChain) {
            if (node.tooltip != null) {
                tip = node;
                break;
            }
        }
        if (tip != tooltipNode) {
            tooltipNode = tip;
            tooltipSince = UiClock.now();
        }
    }

    private void clearHover() {
        for (UiNode<?> node : hoverChain) {
            setHovered(node, false);
        }
        hoverChain.clear();
        tooltipNode = null;
    }

    private static void setHovered(UiNode<?> node, boolean value) {
        node.hovered = value;
        node.onStateChanged();
        if (node.onHover != null) {
            node.onHover.accept(value);
        }
    }

    private void renderTooltip(GuiGraphics graphics) {
        if (tooltipNode == null || pressedNode != null || UiClock.now() - tooltipSince < TOOLTIP_DELAY_MS) {
            return;
        }
        List<Component> lines = tooltipNode.tooltip.get();
        if (lines == null || lines.isEmpty()) {
            return;
        }
        graphics.renderComponentTooltip(Minecraft.getInstance().font, lines, (int) mouseX, (int) mouseY);
    }

    // ==================== 指针形状 ====================

    private void applyCursor() {
        Cursor wanted = Cursor.DEFAULT;
        if (dragging && pressedNode != null && pressedNode.cursor != null) {
            wanted = pressedNode.cursor;
        } else {
            for (UiNode<?> node : hoverChain) {
                if (node.cursor != null) {
                    wanted = node.isEffectivelyDisabled() ? Cursor.DEFAULT : node.cursor;
                    break;
                }
            }
        }
        setCursor(wanted);
    }

    private void setCursor(Cursor cursor) {
        if (cursor == appliedCursor) {
            return;
        }
        appliedCursor = cursor;
        UiCursors.apply(cursor);
    }

    // ==================== 输入 ====================

    public boolean mouseMoved(double gx, double gy) {
        mouseX = gx;
        mouseY = gy;
        return false;
    }

    public boolean mouseClicked(double gx, double gy, int button) {
        mouseX = gx;
        mouseY = gy;
        UiNode<?> target = hitTest(gx, gy);
        UiNode<?> focusTarget = null;
        for (UiNode<?> node = target; node != null && node != container; node = node.parent) {
            if (node.focusable && !node.isEffectivelyDisabled()) {
                focusTarget = node;
                break;
            }
        }
        focus(focusTarget);
        for (UiNode<?> node = target; node != null && node != container; node = node.parent) {
            if (node.onMouseDown(gx, gy, button)) {
                pressedNode = node;
                pressedButton = button;
                pressX = gx;
                pressY = gy;
                dragging = false;
                node.pressed = true;
                node.onStateChanged();
                return true;
            }
        }
        return target != null;
    }

    public boolean mouseReleased(double gx, double gy, int button) {
        mouseX = gx;
        mouseY = gy;
        if (pressedNode == null || button != pressedButton) {
            return false;
        }
        UiNode<?> node = pressedNode;
        pressedNode = null;
        dragging = false;
        node.pressed = false;
        node.onStateChanged();
        boolean inside = false;
        for (UiNode<?> hit = hitTest(gx, gy); hit != null; hit = hit.parent) {
            if (hit == node) {
                inside = true;
                break;
            }
        }
        node.onMouseUp(gx, gy, button, inside);
        return true;
    }

    public boolean mouseDragged(double gx, double gy, int button, double dragX, double dragY) {
        mouseX = gx;
        mouseY = gy;
        if (pressedNode == null || button != pressedButton) {
            return false;
        }
        if (!dragging && Math.hypot(gx - pressX, gy - pressY) >= DRAG_THRESHOLD) {
            dragging = true;
        }
        return pressedNode.onMouseDrag(gx, gy, button, dragX, dragY);
    }

    public boolean mouseScrolled(double gx, double gy, double scrollX, double scrollY) {
        mouseX = gx;
        mouseY = gy;
        for (UiNode<?> node = hitTest(gx, gy); node != null && node != container; node = node.parent) {
            if (node.onScroll(gx, gy, scrollX, scrollY)) {
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            moveFocus((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1);
            return true;
        }
        for (UiNode<?> node = focusedNode; node != null && node != container; node = node.parent) {
            if (node.onKeyDown(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(char character, int modifiers) {
        for (UiNode<?> node = focusedNode; node != null && node != container; node = node.parent) {
            if (node.onCharTyped(character, modifiers)) {
                return true;
            }
        }
        return false;
    }

    // ==================== 焦点 ====================

    public UiNode<?> focusedNode() {
        return focusedNode;
    }

    public void focus(UiNode<?> node) {
        if (node == focusedNode) {
            return;
        }
        if (focusedNode != null) {
            focusedNode.focused = false;
            focusedNode.onStateChanged();
        }
        focusedNode = node;
        if (node != null) {
            node.focused = true;
            node.onStateChanged();
        }
    }

    private void moveFocus(int direction) {
        List<UiNode<?>> focusables = new ArrayList<>();
        collectFocusables(container, focusables);
        if (focusables.isEmpty()) {
            return;
        }
        int index = focusables.indexOf(focusedNode);
        int next = index < 0 ? (direction > 0 ? 0 : focusables.size() - 1)
                : Math.floorMod(index + direction, focusables.size());
        focus(focusables.get(next));
    }

    private void collectFocusables(UiNode<?> node, List<UiNode<?>> out) {
        if (!node.visible || node.paintedFrame != paintFrame) {
            return;
        }
        if (node.focusable && !node.isEffectivelyDisabled()) {
            out.add(node);
        }
        for (UiNode<?> child : node.children) {
            collectFocusables(child, out);
        }
    }

    void onNodeDetached(UiNode<?> node) {
        hoverChain.remove(node);
        if (pressedNode == node) {
            pressedNode = null;
            dragging = false;
        }
        if (focusedNode == node) {
            focusedNode = null;
        }
        if (tooltipNode == node) {
            tooltipNode = null;
        }
    }
}
