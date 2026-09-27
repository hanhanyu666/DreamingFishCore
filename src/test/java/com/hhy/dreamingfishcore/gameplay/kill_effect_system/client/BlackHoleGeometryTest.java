package com.hhy.dreamingfishcore.gameplay.kill_effect_system.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlackHoleGeometryTest {
    private static final BlackHoleGeometry.View VIEW = new BlackHoleGeometry.View(
            new BlackHoleGeometry.Point(1, 0, 0), new BlackHoleGeometry.Point(0, 0.8F, -0.6F),
            new BlackHoleGeometry.Point(0, 0.6F, 0.8F));

    @Test
    void waveExpandsMonotonicallyEvenWhileItsLightFades() {
        float previous = 0;
        for (int i = 0; i <= 1000; i++) {
            float travel = BlackHoleGeometry.waveTravel(i / 1000F, BlackHoleGeometry.RELEASE_PROGRESS, 1);
            assertTrue(travel >= previous && travel <= 1);
            previous = travel;
        }
        assertEquals(1, previous);
    }

    @Test
    void coreActuallyCollapsesBeforeTheShockwaveFinishes() {
        var active = frame(0.40F, false);
        var collapsed = frame(0.75F, false);
        assertTrue(collapsed.coreRadius() < active.coreRadius() * 0.03F);
        assertEquals(0, frame(0.80F, false).coreAlpha());
        assertTrue(count(BlackHoleGeometry.Pass.FRONT_LIGHT, frame(0.85F, false)) > 0);
        for (var pass : BlackHoleGeometry.Pass.values()) {
            assertEquals(0, count(pass, frame(0, false)));
            assertEquals(0, count(pass, frame(1, false)));
        }
    }

    @Test
    void verticesRemainFiniteInsideCullingBoundsAcrossSizesViewsAndLifetime() {
        float[][] sizes = {{0.01F, 0.01F}, {0.6F, 1.95F}, {16, 32}};
        var overhead = new BlackHoleGeometry.View(new BlackHoleGeometry.Point(1, 0, 0),
                new BlackHoleGeometry.Point(0, 0, -1), new BlackHoleGeometry.Point(0, 1, 0));
        for (float[] size : sizes) {
            float reach = BlackHoleGeometry.reachFor(size[0], size[1]);
            for (var view : new BlackHoleGeometry.View[]{VIEW, overhead}) {
                for (int tick = 0; tick <= 144; tick++) {
                    var f = BlackHoleGeometry.sample(size[0], size[1], tick * 0.25F, 36, size[1], 1729, false);
                    int total = 0;
                    for (var pass : BlackHoleGeometry.Pass.values()) {
                        int[] count = {0};
                        BlackHoleGeometry.render(pass, f, view, (x, y, z, r, g, b, a) -> {
                            assertTrue(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z));
                            assertTrue(Math.abs(x) <= reach && Math.abs(z) <= reach);
                            assertTrue(y >= -reach && y <= size[1] + reach);
                            assertTrue(r >= 0 && r <= 1 && g >= 0 && g <= 1 && b >= 0 && b <= 1);
                            assertTrue(a >= 0 && a <= 1);
                            count[0]++;
                        });
                        assertEquals(0, count[0] % 4, "Incomplete quad in " + pass);
                        total += count[0];
                    }
                    assertTrue(total <= 10_000, "Per-effect geometry budget exceeded: " + total);
                }
            }
        }
    }

    @Test
    void crowdedModeReducesGeometryButRetainsCoreAndWaves() {
        for (float progress : new float[]{0.30F, 0.85F}) {
            int full = 0;
            int reduced = 0;
            for (var pass : BlackHoleGeometry.Pass.values()) {
                full += count(pass, frame(progress, false));
                reduced += count(pass, frame(progress, true));
            }
            assertTrue(reduced > 0 && reduced < full * 0.75F);
        }
        assertTrue(count(BlackHoleGeometry.Pass.HORIZON, frame(0.3F, true)) > 0);
        assertTrue(count(BlackHoleGeometry.Pass.FRONT_LIGHT, frame(0.85F, true)) > 0);
    }

    private static BlackHoleGeometry.Frame frame(float progress, boolean reduced) {
        return BlackHoleGeometry.sample(0.6F, 1.95F, progress * 36, 36, 1.95F, 42, reduced);
    }

    private static int count(BlackHoleGeometry.Pass pass, BlackHoleGeometry.Frame frame) {
        int[] count = {0};
        BlackHoleGeometry.render(pass, frame, VIEW, (x, y, z, r, g, b, a) -> count[0]++);
        return count[0];
    }
}
