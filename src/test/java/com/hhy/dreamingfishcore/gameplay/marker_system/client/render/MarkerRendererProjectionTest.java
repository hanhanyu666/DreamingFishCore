package com.hhy.dreamingfishcore.gameplay.marker_system.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkerRendererProjectionTest {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 180;
    private static final int MARGIN = 18;

    @Test
    void horizontalDirectionChoosesTheMatchingSide() {
        assertEquals(MarkerRenderer.ScreenEdge.RIGHT,
                MarkerRenderer.projectToEdge(WIDTH, HEIGHT, MARGIN, 2.0F, 0.2F).edge());
        assertEquals(MarkerRenderer.ScreenEdge.LEFT,
                MarkerRenderer.projectToEdge(WIDTH, HEIGHT, MARGIN, -2.0F, 0.2F).edge());
    }

    @Test
    void verticalDirectionChoosesTheMatchingSide() {
        assertEquals(MarkerRenderer.ScreenEdge.TOP,
                MarkerRenderer.projectToEdge(WIDTH, HEIGHT, MARGIN, 0.2F, 2.0F).edge());
        assertEquals(MarkerRenderer.ScreenEdge.BOTTOM,
                MarkerRenderer.projectToEdge(WIDTH, HEIGHT, MARGIN, 0.2F, -2.0F).edge());
    }

    @Test
    void directlyBehindFallsBackToBottomCenter() {
        MarkerRenderer.ScreenPoint point =
                MarkerRenderer.projectToEdge(WIDTH, HEIGHT, MARGIN, 0.0F, 0.0F);

        assertEquals(MarkerRenderer.ScreenEdge.BOTTOM, point.edge());
        assertEquals(WIDTH / 2.0F, point.x(), 0.0001F);
    }
}
