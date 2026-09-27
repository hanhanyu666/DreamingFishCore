package com.hhy.dreamingfishcore.gameplay.hospital_system;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DailyTemplateSupportTest {
    @Test
    void defaultsMatchTwoGoldenApplesAndFivePoints() {
        var config = HospitalConfig.Settings.defaults();
        config.validate();
        assertEquals("minecraft:golden_apple", config.dailyItemId());
        assertEquals(2, config.dailyItemCount());
        assertEquals(5, config.dailyRestorePoints());
        assertEquals(3, config.requiredPlayers());
        assertThrows(IllegalArgumentException.class, () -> new HospitalConfig.Settings(1, 0, "minecraft:golden_apple", 2, 5).validate());
        assertThrows(IllegalArgumentException.class, () -> new HospitalConfig.Settings(1, 3, "minecraft:golden_apple", 0, Float.NaN).validate());
    }

    @Test
    void usesOverworldDaysIncludingSleepAndDoesNotResetOnClockRollback() {
        var progress = new DailyTemplateSupportProgress();
        assertEquals(0, DailyTemplateSupportProgress.dayAt(23_999));
        assertEquals(1, DailyTemplateSupportProgress.dayAt(24_000));
        assertEquals(2, DailyTemplateSupportProgress.dayAt(48_000));
        assertTrue(progress.canClaim(0, 95));
        assertTrue(progress.applyPayment("first", 0));
        assertFalse(progress.canClaim(0, 20));
        assertTrue(progress.canClaim(1, 20));
        assertTrue(progress.applyPayment("next", 2));
        assertFalse(progress.canClaim(1, 20));
        assertFalse(progress.canClaim(-1, 20));
    }

    @Test
    void fullPointsNeverConsumeTheDailyOpportunityAndPartialRestoreIsCapped() {
        var progress = new DailyTemplateSupportProgress();
        assertFalse(progress.canClaim(10, 100));
        assertEquals(-1, progress.getLastClaimDay());
        assertTrue(progress.canClaim(10, 99));
        assertEquals(100, DailyTemplateSupportProgress.restoredPoints(99, 5));
        assertEquals(5, DailyTemplateSupportProgress.restoredPoints(0, 5));
        assertFalse(progress.canClaim(10, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> DailyTemplateSupportProgress.restoredPoints(95, Float.POSITIVE_INFINITY));
    }

    @Test
    void restartPreservesDailyReceiptAndAllowsOnlyANewDay() {
        var progress = new DailyTemplateSupportProgress();
        progress.applyPayment("paid", 7);
        Gson gson = new Gson();
        var restored = gson.fromJson(gson.toJson(progress), DailyTemplateSupportProgress.class);
        restored.validate();
        assertFalse(restored.canClaim(7, 0));
        assertFalse(restored.applyPayment("paid", 7));
        assertFalse(restored.applyPayment("other", 7));
        assertTrue(restored.canClaim(8, 0));
    }

    @Test
    void persistedPaymentPrecedesCreditAndDuplicateRecoveryDoesNothing() {
        var progress = new DailyTemplateSupportProgress();
        float[] points = {30};
        List<String> events = new ArrayList<>();
        float restored = TemplateSupportSettlement.settle(progress, "paid", 5, points[0], 5,
                () -> events.add("payment saved"), value -> { points[0] = value; events.add("points changed"); },
                () -> { events.add("account saved"); return true; });
        assertEquals(List.of("payment saved", "points changed", "account saved"), events);
        assertEquals(5, restored);
        assertEquals(35, points[0]);
        assertEquals(0, TemplateSupportSettlement.settle(progress, "paid", 5, points[0], 5,
                () -> fail("must not save payment twice"), value -> fail("must not add points twice"), () -> false));
    }

    @Test
    void paymentSaveFailureCannotCreditOrConsumeTheDay() {
        var progress = new DailyTemplateSupportProgress();
        assertThrows(IllegalStateException.class, () -> TemplateSupportSettlement.settle(progress, "paid", 5, 20, 5,
                () -> { throw new IllegalStateException("disk unavailable"); }, value -> fail("no credit before payment save"), () -> true));
        assertEquals(-1, progress.getLastClaimDay());
    }

    @Test
    void failedAttributeSaveCanRetryTheSamePaymentWithoutChargingAgain() {
        var progress = new DailyTemplateSupportProgress();
        float[] points = {20};
        assertThrows(IllegalStateException.class, () -> TemplateSupportSettlement.settle(progress, "paid", 5, points[0], 5,
                () -> { }, value -> points[0] = value, () -> false));
        assertEquals(20, points[0]);
        assertEquals(-1, progress.getLastClaimDay());
        assertEquals(5, TemplateSupportSettlement.settle(progress, "paid", 5, points[0], 5,
                () -> { }, value -> points[0] = value, () -> true));
        assertEquals(25, points[0]);
        assertFalse(progress.canClaim(5, 25));
    }

    @Test
    void applyingAlreadyPaidWorkOnNextDayPreservesItsOriginalDay() {
        var progress = new DailyTemplateSupportProgress();
        float[] points = {98};
        assertEquals(2, TemplateSupportSettlement.settle(progress, "yesterday", 5, points[0], 5,
                () -> { }, value -> points[0] = value, () -> true));
        assertEquals(100, points[0]);
        // 玩家后来消耗余量，当天仍可使用新的维护机会；不是把昨天的收据改成今天。
        assertTrue(progress.canClaim(6, 80));
        assertFalse(progress.canClaim(5, 80));
    }
}
