package com.hhy.dreamingfishcore.gameplay.hospital_system;

/** 每人一份，与余量保存在同一属性文件。游戏日期倒退不能再次领取。 */
public final class DailyTemplateSupportProgress {
    private long lastClaimDay = -1L;
    private String lastPaymentId = "";

    public long getLastClaimDay() { return lastClaimDay; }
    public String getLastPaymentId() { return lastPaymentId; }

    public boolean canClaim(long day, float points) {
        return day >= 0 && day > lastClaimDay && Float.isFinite(points) && points >= 0 && points < 100;
    }

    /** 重试已付款交易时只应用一次；先保存付款记录，再调用此方法。 */
    public boolean applyPayment(String paymentId, long day) {
        if (paymentId == null || paymentId.isBlank() || day < 0) {
            throw new IllegalArgumentException("医疗维护收据非法");
        }
        if (day <= lastClaimDay || paymentId.equals(lastPaymentId)) return false;
        lastClaimDay = day;
        lastPaymentId = paymentId;
        return true;
    }

    public void restore(long day, String paymentId) {
        lastClaimDay = day;
        lastPaymentId = paymentId;
    }

    public void validate() {
        if (lastClaimDay < -1 || lastPaymentId == null || lastPaymentId.length() > 64) {
            throw new IllegalStateException("每日模板维护记录非法");
        }
    }

    public static float restoredPoints(float current, float amount) {
        if (!Float.isFinite(current) || current < 0 || current > 100
                || !Float.isFinite(amount) || amount <= 0 || amount > 100) {
            throw new IllegalArgumentException("模板重建余量参数非法");
        }
        return Math.min(100, current + amount);
    }

    public static long dayAt(long overworldDayTime) { return Math.floorDiv(overworldDayTime, 24_000L); }
}
