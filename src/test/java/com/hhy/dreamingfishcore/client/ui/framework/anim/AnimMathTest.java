package com.hhy.dreamingfishcore.client.ui.framework.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimMathTest {
    @Test
    void cubicBezierMatchesEndpointsAndSymmetry() {
        Easing easeInOut = Easing.cubicBezier(0.42F, 0.0F, 0.58F, 1.0F);
        assertEquals(0.0F, easeInOut.apply(0.0F), 1.0e-4F);
        assertEquals(1.0F, easeInOut.apply(1.0F), 1.0e-4F);
        assertEquals(0.5F, easeInOut.apply(0.5F), 2.0e-3F);
        assertEquals(1.0F - easeInOut.apply(0.3F), easeInOut.apply(0.7F), 2.0e-3F);
    }

    @Test
    void standardEasingIsMonotonic() {
        float previous = 0.0F;
        for (int i = 1; i <= 100; i++) {
            float value = Easing.STANDARD.apply(i / 100.0F);
            assertTrue(value >= previous - 1.0e-4F);
            previous = value;
        }
    }

    @Test
    void springSettlesOnTarget() {
        float[] state = {0.0F, 0.0F};
        boolean resting = false;
        for (int i = 0; i < 240 && !resting; i++) {
            resting = Spring.GENTLE.step(state, 10.0F, 1.0F / 60.0F);
        }
        assertTrue(resting);
        assertEquals(10.0F, state[0], 1.0e-3F);
    }

    @Test
    void bouncySpringOvershoots() {
        float[] state = {0.0F, 0.0F};
        float max = 0.0F;
        for (int i = 0; i < 120; i++) {
            Spring.BOUNCY.step(state, 1.0F, 1.0F / 60.0F);
            max = Math.max(max, state[0]);
        }
        assertTrue(max > 1.01F);
    }
}
