package com.hhy.dreamingfishcore.gameplay.zombie_system.charred;

/**
 * 焦尸伤害闸门的纯逻辑。
 *
 * <p>把「要不要归零」「结算倍率是多少」拆成不依赖 {@code DamageSource} 与任何注册表的纯函数，
 * 这样单元测试可以直接覆盖整张判定表，不用起服务端。{@link CharredZombieEntity} 只负责把
 * 伤害源翻译成三个布尔值再调这里。</p>
 *
 * <p>判定表（{@code fire} = 火焰类伤害，{@code vulnerable} = 身上有「易损」，{@code bypass} = 兜底放行）：</p>
 * <ul>
 *   <li>火 → 结算，并按「易燃体质」倍率放大；</li>
 *   <li>非火 + 无易损 + 非兜底 → 归零（归零不等于无事发生，命中反馈与附魔点火在实体侧保留）；</li>
 *   <li>非火 + 有易损 → 结算，并按「易损」等级放大（这就是「被火打裂了才吃得进别的伤害」）；</li>
 *   <li>兜底类（虚空/溺水/摔落）→ 始终结算，倍率 1，避免出现清不掉的怪。</li>
 * </ul>
 */
public final class CharredZombieRules {

    private CharredZombieRules() {
    }

    /**
     * 本次伤害是否应当被完全归零。
     *
     * @param fireDamage        是否火焰类伤害
     * @param vulnerable        身上是否有「易损」效果
     * @param bypassesImmunity  是否是配置里声明的兜底伤害类型
     */
    public static boolean isImmune(boolean fireDamage, boolean vulnerable, boolean bypassesImmunity) {
        return !fireDamage && !vulnerable && !bypassesImmunity;
    }

    /**
     * 最终伤害倍率。
     *
     * <p>两个倍率可以叠加：着火中的焦尸被打上「易损」时，火伤既吃易燃体质的放大，也吃易损的放大。
     * 兜底类伤害不吃任何倍率（调用方传 {@code fireDamage = false} 且无易损时结果恒为 1）。</p>
     *
     * <p><b>等级口径</b>：原版效果里 {@code amplifier = 0} 就是「I 级」，所以这里按
     * {@code amplifier + 1} 计算层数，保证默认配置（{@code vulnerableAmplifier = 0}）下
     * 一次火焰类命中打出的是「易损 I」，也就是 +10% 而不是 +0%。</p>
     *
     * @param vulnerableAmplifier      当前「易损」等级；未生效时传 {@code -1}
     * @param fireDamageMultiplier     易燃体质：火焰伤害倍率（默认 2.0）
     * @param vulnerableBonusPerLevel  「易损」每级增伤（默认 0.10，即 I 级 +10%）
     */
    public static float damageMultiplier(
            boolean fireDamage,
            boolean vulnerable,
            int vulnerableAmplifier,
            double fireDamageMultiplier,
            double vulnerableBonusPerLevel) {
        float multiplier = 1.0F;
        if (fireDamage) {
            multiplier *= (float) Math.max(0.0D, fireDamageMultiplier);
        }
        if (vulnerable) {
            int level = Math.max(0, vulnerableAmplifier) + 1;
            multiplier *= (float) (1.0D + level * Math.max(0.0D, vulnerableBonusPerLevel));
        }
        return multiplier;
    }

    /** 着火期间是否应当挂上增强修饰符（易燃体质的「烧起来更凶」）。 */
    public static boolean shouldBoostWhileBurning(boolean boostEnabled, boolean onFire) {
        return boostEnabled && onFire;
    }

    /**
     * 刷新「易损」时应当采用的等级：只升不降。
     *
     * @param currentAmplifier 当前等级，{@code -1} 表示身上还没有这个效果
     */
    public static int refreshedAmplifier(int currentAmplifier, int configuredAmplifier) {
        return Math.max(currentAmplifier, Math.max(0, configuredAmplifier));
    }
}
