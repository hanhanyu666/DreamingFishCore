package com.hhy.dreamingfishcore.gameplay.hospital_system;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 付款持久化、属性入账的顺序边界，独立于 Minecraft 以便注入保存失败进行验证。 */
public final class TemplateSupportSettlement {
    private TemplateSupportSettlement() { }

    public static float settle(DailyTemplateSupportProgress progress, String paymentId, long day,
                               float current, float amount, Runnable ensurePaymentSaved,
                               Consumer<Float> setPoints, BooleanSupplier saveAccount) {
        float after = DailyTemplateSupportProgress.restoredPoints(current, amount);
        if (paymentId == null || paymentId.isBlank() || day < 0) throw new IllegalArgumentException("维护付款非法");
        if (day <= progress.getLastClaimDay() || paymentId.equals(progress.getLastPaymentId())) return 0;
        ensurePaymentSaved.run();
        long previousDay = progress.getLastClaimDay();
        String previousId = progress.getLastPaymentId();
        try {
            progress.applyPayment(paymentId, day);
            setPoints.accept(after);
            if (!saveAccount.getAsBoolean()) throw new IllegalStateException("余量保存失败，已付款申请保留待重试");
            return after - current;
        } catch (RuntimeException exception) {
            progress.restore(previousDay, previousId);
            setPoints.accept(current);
            throw exception;
        }
    }
}
