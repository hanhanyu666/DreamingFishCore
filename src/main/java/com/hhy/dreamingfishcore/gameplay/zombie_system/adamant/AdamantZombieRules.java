package com.hhy.dreamingfishcore.gameplay.zombie_system.adamant;

/**
 * 金刚僵尸「生锈」机制的纯逻辑。
 *
 * <p>拆成不依赖 {@code DamageSource} 与注册表的纯函数，整张表可以在单测里枚举干净。
 * 判定表（{@code rustStage} 为 0 表示未生锈）：</p>
 * <ul>
 *   <li>锈级 0 → 完全免疫（除兜底通道外），攻击它的人吃硬直；</li>
 *   <li>锈级 1 → **破防**，伤害按正常值结算（倍率 1.0）；</li>
 *   <li>锈级 2/3 → 每多一层再 +{@code bonusPerStage}（默认 +20%）。</li>
 * </ul>
 *
 * <p>移速/攻击/护甲的惩罚不在这里——那部分交给 {@code dreamingfishcore:rusted} 效果的属性
 * 修饰符（原版按 {@code amount × (等级 + 1)} 缩放，所以效果里只写「每层」数值）。</p>
 */
public final class AdamantZombieRules {

    private AdamantZombieRules() {
    }

    /**
     * 未生锈时是否完全免疫伤害。
     *
     * @param rustStage          当前锈级，0 = 未生锈
     * @param bypassesImmunity   是否是配置里声明的兜底伤害类型（默认只有虚空）
     */
    public static boolean isImmune(int rustStage, boolean bypassesImmunity) {
        return rustStage <= 0 && !bypassesImmunity;
    }

    /** 涨一层锈（每次浇水涨一层），封顶 {@code maxStages}。 */
    public static int advancedStage(int currentStage, int maxStages) {
        int ceiling = Math.max(0, maxStages);
        return Math.min(ceiling, Math.max(0, currentStage) + 1);
    }

    /** 涨 {@code stages} 层，一次调用可以涨多层（水柱伤害/次数可配）。 */
    public static int advancedStage(int currentStage, int stages, int maxStages) {
        int ceiling = Math.max(0, maxStages);
        return Math.min(ceiling, Math.max(0, currentStage) + Math.max(0, stages));
    }

    /**
     * 受伤倍率。
     *
     * <p>第 1 层就是 1.0（也就是「刚生锈就能正常打死」），第 2 层起每层再加
     * {@code bonusPerStage}。未生锈时返回 0——调用方在免疫分支里根本不会用到它，
     * 这里返回 0 只是让「免疫 = 打不出伤害」这件事在纯逻辑里也是自洽的。</p>
     */
    public static float damageMultiplier(int rustStage, double bonusPerStage) {
        if (rustStage <= 0) {
            return 0.0F;
        }
        return (float) (1.0D + (rustStage - 1) * Math.max(0.0D, bonusPerStage));
    }

    /** 效果等级换算：锈级 1 对应 amplifier 0（也就是「生锈 I」）。 */
    public static int amplifierForStage(int rustStage) {
        return Math.max(0, rustStage - 1);
    }

    /** 锈级换算回来：没有效果时按 0 层处理。 */
    public static int stageForAmplifier(int amplifier) {
        return amplifier < 0 ? 0 : amplifier + 1;
    }

    /** 潮湿地环境（泡水/淋雨）是否该累积生锈进度。 */
    public static boolean accumulatesRustFromWetness(boolean wet, boolean enabled) {
        return wet && enabled;
    }
}
