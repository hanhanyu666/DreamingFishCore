package com.hhy.dreamingfishcore.server.server_ui_system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerTpsCalculatorTest {
    private static final long TARGET_20_TICK_NANOS = 50_000_000L;

    @Test
    void capsHealthyServerAtConfiguredTarget() {
        assertEquals(20.0F,
                ServerTpsCalculator.calculate(10_000_000L, TARGET_20_TICK_NANOS, 20.0F),
                0.0001F);
    }

    @Test
    void reportsLowerTpsWhenTicksExceedTargetInterval() {
        assertEquals(10.0F,
                ServerTpsCalculator.calculate(100_000_000L, TARGET_20_TICK_NANOS, 20.0F),
                0.0001F);
        assertEquals(4.0F,
                ServerTpsCalculator.calculate(250_000_000L, TARGET_20_TICK_NANOS, 20.0F),
                0.0001F);
    }

    @Test
    void usesConfiguredRateBeforeTheFirstSampleAndForCustomRates() {
        assertEquals(20.0F,
                ServerTpsCalculator.calculate(0L, TARGET_20_TICK_NANOS, 20.0F),
                0.0001F);
        assertEquals(10.0F,
                ServerTpsCalculator.calculate(80_000_000L, 100_000_000L, 10.0F),
                0.0001F);
    }
}
