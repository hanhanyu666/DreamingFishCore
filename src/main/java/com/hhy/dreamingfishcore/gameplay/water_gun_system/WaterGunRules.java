package com.hhy.dreamingfishcore.gameplay.water_gun_system;

/**
 * 呲水枪的纯逻辑：水量数学与「能不能喷」。
 *
 * <p>拆出来是为了能在单测里把边界枚举干净——水量这种「减到负数/加到超上限」的小事最容易
 * 在实机里变成一个显示 11/10 的水枪。</p>
 */
public final class WaterGunRules {

    private WaterGunRules() {
    }

    /** 能不能喷：开关打开且至少有一发水。 */
    public static boolean canFire(int water, boolean enabled) {
        return enabled && water > 0;
    }

    /** 喷一发之后剩多少（不会变成负数）。 */
    public static int afterFire(int water) {
        return Math.max(0, water - 1);
    }

    /**
     * 装水之后有多少。
     *
     * @param toFull 一次装满；否则只加 {@code amount}
     */
    public static int afterRefill(int water, int capacity, int amount, boolean toFull) {
        int ceiling = Math.max(0, capacity);
        if (toFull) {
            return ceiling;
        }
        return Math.min(ceiling, Math.max(0, water) + Math.max(0, amount));
    }

    /** 水枪是不是满的（满的时候对着水源右键不该再消耗锅里的水）。 */
    public static boolean isFull(int water, int capacity) {
        return water >= Math.max(0, capacity);
    }

    /** 目标是不是正在着火（原版用 {@code remainingFireTicks > 0} 表示在燃烧）。 */
    public static boolean isBurning(int remainingFireTicks) {
        return remainingFireTicks > 0;
    }

    /** 水量条比例（0~1），用于物品栏上那条蓝色的水量条。 */
    public static float waterFraction(int water, int capacity) {
        if (capacity <= 0) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (float) water / (float) capacity));
    }
}
