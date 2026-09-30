package com.hhy.dreamingfishcore.client.ui.framework.node;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayoutEngineTest {
    private static final float EPS = 0.001F;

    private static Box box(float w, float h) {
        return new Box().size(w, h);
    }

    private static void layout(UiNode<?> root, float w, float h) {
        new LayoutEngine().layoutRoot(root, w, h, 1.0F);
    }

    @Test
    void rowPlacesChildrenWithGapAndPadding() {
        Box a = box(10, 10);
        Box b = box(20, 10);
        Box root = new Box().row().padding(5).gap(4).alignItems(Align.START).add(a, b);
        layout(root, 100, 50);
        assertEquals(5, a.x, EPS);
        assertEquals(5, a.y, EPS);
        assertEquals(19, b.x, EPS);
        assertEquals(20, b.w, EPS);
    }

    @Test
    void growDistributesRemainingSpace() {
        Box a = box(10, 10);
        Box b = new Box().height(10).grow(1);
        Box c = new Box().height(10).grow(3);
        Box root = new Box().row().add(a, b, c);
        layout(root, 90, 10);
        assertEquals(20, b.w, EPS);
        assertEquals(60, c.w, EPS);
        assertEquals(30, c.x, EPS);
    }

    @Test
    void rowChildrenShrinkWhenOverflowing() {
        Box a = box(100, 10);
        Box b = box(100, 10);
        Box root = new Box().row().add(a, b);
        layout(root, 100, 10);
        assertEquals(50, a.w, EPS);
        assertEquals(50, b.w, EPS);
    }

    @Test
    void columnChildrenDoNotShrinkByDefault() {
        Box a = box(10, 80);
        Box b = box(10, 80);
        Box root = new Box().column().add(a, b);
        layout(root, 50, 100);
        assertEquals(80, a.h, EPS);
        assertEquals(80, b.y, EPS);
    }

    @Test
    void justifyAndAlignCenter() {
        Box a = box(20, 10);
        Box root = new Box().row().justify(Justify.CENTER).alignItems(Align.CENTER).add(a);
        layout(root, 100, 50);
        assertEquals(40, a.x, EPS);
        assertEquals(20, a.y, EPS);
    }

    @Test
    void spaceBetweenPushesItemsToEdges() {
        Box a = box(10, 10);
        Box b = box(10, 10);
        Box c = box(10, 10);
        Box root = new Box().row().justify(Justify.SPACE_BETWEEN).add(a, b, c);
        layout(root, 100, 10);
        assertEquals(0, a.x, EPS);
        assertEquals(45, b.x, EPS);
        assertEquals(90, c.x, EPS);
    }

    @Test
    void stretchFillsCrossAxis() {
        Box a = new Box().width(10);
        Box root = new Box().row().add(a);
        layout(root, 100, 40);
        assertEquals(40, a.h, EPS);
    }

    @Test
    void autoSizedContainerWrapsChildren() {
        Box inner = new Box().row().gap(2).padding(3).add(box(10, 5), box(10, 7));
        Box root = new Box().column().alignItems(Align.START).add(inner);
        layout(root, 200, 200);
        assertEquals(28, inner.w, EPS);
        assertEquals(13, inner.h, EPS);
    }

    @Test
    void wrapBreaksIntoLines() {
        Box a = box(40, 10);
        Box b = box(40, 10);
        Box c = box(40, 10);
        Box root = new Box().row().wrap(true).gap(4).alignItems(Align.START).add(a, b, c);
        layout(root, 90, 100);
        assertEquals(0, c.x, EPS);
        assertEquals(14, c.y, EPS);
    }

    @Test
    void marginsOffsetChildren() {
        Box a = box(10, 10).margin(3, 4, 0, 0);
        Box root = new Box().column().alignItems(Align.START).add(a);
        layout(root, 50, 50);
        assertEquals(3, a.x, EPS);
        assertEquals(4, a.y, EPS);
    }

    @Test
    void percentWidthResolvesAgainstParent() {
        Box a = new Box().widthPercent(0.25F).height(5);
        Box root = new Box().column().alignItems(Align.START).padding(10).add(a);
        layout(root, 120, 50);
        assertEquals(25, a.w, EPS);
    }

    @Test
    void absoluteChildUsesInsets() {
        Box abs = new Box().size(10, 10).absolute(Float.NaN, 5, 5, Float.NaN);
        Box root = new Box().add(abs);
        layout(root, 100, 100);
        assertEquals(85, abs.x, EPS);
        assertEquals(5, abs.y, EPS);
    }

    @Test
    void stackCentersChildren() {
        Box a = box(20, 20);
        Box root = new Box().stack().alignItems(Align.CENTER).add(a);
        layout(root, 100, 60);
        assertEquals(40, a.x, EPS);
        assertEquals(20, a.y, EPS);
    }

    @Test
    void relayoutAfterChildChanges() {
        Box a = box(10, 10);
        Box b = box(10, 10);
        Box root = new Box().row().add(a, b);
        LayoutEngine engine = new LayoutEngine();
        engine.layoutRoot(root, 100, 10, 1.0F);
        assertEquals(10, b.x, EPS);
        a.width(30);
        engine.layoutRoot(root, 100, 10, 1.0F);
        assertEquals(30, b.x, EPS);
    }

    @Test
    void positionsSnapToPixels() {
        Box a = new Box().grow(1).height(1);
        Box b = new Box().grow(1).height(1);
        Box c = new Box().grow(1).height(1);
        Box root = new Box().row().add(a, b, c);
        new LayoutEngine().layoutRoot(root, 100, 1, 0.5F);
        assertEquals(33.5F, b.x, EPS);
        assertEquals(100, c.x + c.w, EPS);
    }
}
