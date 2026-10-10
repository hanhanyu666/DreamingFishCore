package com.hhy.dreamingfishcore.gameplay.zombie_system.boss;

/** 远程僵尸 Boss 的纯函数规则，不访问实体、世界或注册表。 */
public final class ZombieCommanderRules {
    private ZombieCommanderRules() {
    }

    /** 有效生命比例小于或等于阈值时进入狂暴；非法或非有限输入不触发阶段切换。 */
    public static boolean shouldEnrage(double health, double maxHealth, double fraction) {
        if (!Double.isFinite(health) || !Double.isFinite(maxHealth) || !Double.isFinite(fraction)
                || maxHealth <= 0.0D || health < 0.0D || health > maxHealth
                || fraction < 0.0D || fraction > 1.0D) {
            return false;
        }
        return health / maxHealth <= fraction;
    }

    /** 按非负的存活数、上限和请求数计算可召唤数量，永远不超过正剩余配额。 */
    public static int availableSummons(int active, int cap, int requested) {
        if (cap <= 0 || requested <= 0) {
            return 0;
        }
        int remaining = Math.max(0, cap - Math.max(0, active));
        return Math.min(remaining, requested);
    }

    /** 难度 ID：和平 0、简单 1、普通 2、困难 3；未知 ID 使用普通倍率。 */
    public static double projectileDamageMultiplier(int difficultyId) {
        return switch (difficultyId) {
            case 0 -> 0.0D;
            case 1 -> 0.75D;
            case 2 -> 1.0D;
            case 3 -> 1.5D;
            default -> 1.0D;
        };
    }

    // ------------------------------------------------------------------
    // 近战三段连招
    //
    // 这里的 tick 数**必须和 tools/commander_animations.py 里的关键帧一一对应**：
    // 命中判定落在第几 tick、每段动画多长，都是照着动画里那一刀挥到最前的那一帧定的。
    // 数值对不上就会出现「刀还没到人身上就已经掉血」或者「挥完了才结算」。
    // ZombieCommanderAnimationTest 会拿资源包里的动画 JSON 反查这些常量，改错了会红。
    // ------------------------------------------------------------------

    /** 连招段数。 */
    public static final int MELEE_STEPS = 3;
    /** 每段命中结算的 tick（动画里 xRot 挥到最深的那一帧）。 */
    private static final int[] MELEE_IMPACT_TICKS = {6, 6, 11};
    /** 每段动作的总时长 tick（动画 length × 20）。 */
    private static final int[] MELEE_LENGTH_TICKS = {10, 10, 16};
    /** 每段的伤害倍率：收尾那一刀明显更重。 */
    private static final double[] MELEE_DAMAGE_MULTIPLIER = {1.0D, 1.15D, 1.65D};
    /** 每段的击退强度：前两刀几乎不退人，收尾那刀才把人推开。 */
    private static final double[] MELEE_KNOCKBACK = {0.0D, 0.15D, 0.6D};

    /** 把 1..3 的段号夹到合法范围；非法输入返回第 1 段。 */
    public static int clampMeleeStep(int step) {
        return Math.min(Math.max(step, 1), MELEE_STEPS);
    }

    /** 第 step 段命中判定的 tick；越界按最后一段算。 */
    public static int meleeImpactTick(int step) {
        return MELEE_IMPACT_TICKS[clampMeleeStep(step) - 1];
    }

    /** 第 step 段动作的总时长 tick。 */
    public static int meleeLengthTick(int step) {
        return MELEE_LENGTH_TICKS[clampMeleeStep(step) - 1];
    }

    /** 第 step 段的伤害倍率；非法段号按第 1 段（不放大）算。 */
    public static double meleeDamageMultiplier(int step) {
        return MELEE_DAMAGE_MULTIPLIER[clampMeleeStep(step) - 1];
    }

    /** 第 step 段的击退强度。 */
    public static double meleeKnockback(int step) {
        return MELEE_KNOCKBACK[clampMeleeStep(step) - 1];
    }

    /**
     * 一段打完之后要不要接下一段。
     *
     * @param step       刚打完的段号
     * @param distance   与目标的当前距离（方块）
     * @param chainRange 连段允许的最大距离
     * @return 下一段的段号；不接则返回 0（连招结束，进入冷却）
     */
    public static int meleeChainStep(int step, double distance, double chainRange) {
        int current = clampMeleeStep(step);
        if (current >= MELEE_STEPS) {
            return 0;
        }
        if (!Double.isFinite(distance) || !Double.isFinite(chainRange) || distance > chainRange) {
            return 0;
        }
        return current + 1;
    }

    /** 三段全部命中时的总倍率，用于展示与平衡核对。 */
    public static double meleeComboTotalMultiplier() {
        double sum = 0.0D;
        for (double value : MELEE_DAMAGE_MULTIPLIER) {
            sum += value;
        }
        return sum;
    }
}
