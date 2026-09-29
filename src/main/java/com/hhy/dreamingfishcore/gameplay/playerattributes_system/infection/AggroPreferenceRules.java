package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection;

/**
 * 丧尸仇恨的软身份规则（ADR 0016「幸存者与感染者采用软身份差异」）。
 *
 * <p>为什么单独抽出这个类：软差异的边界必须能被反复检查，而事件处理器里遍布服务端实体、
 * 玩家表和维度对象，只有在没有 Minecraft 依赖的纯函数上才能低成本地固定这些边界。
 * 因此这里不查询任何玩家、不掷随机数，随机结果由调用方以 {@code dropRoll} 传入。</p>
 *
 * <p>规则只描述"稳定感染者降低普通丧尸仇恨"，不描述"免疫"：找不到更近的幸存者时仍然有
 * {@link #DROP_CHANCE} 的概率放弃目标，剩下的概率照常被仇恨。</p>
 */
public final class AggroPreferenceRules {

    /** 丧尸面对目标时的三种处置。 */
    public enum Decision {
        /** 不干预，保持仇恨当前目标。 */
        KEEP,
        /** 放弃目标，表现为降低仇恨而不是免疫。 */
        DROP,
        /** 把仇恨转移给更近的幸存者。 */
        REDIRECT
    }

    /** 可以把仇恨转移给幸存者的最大距离；丧尸不会为了一个远处的幸存者放弃眼前的目标。 */
    public static final double REDIRECT_RANGE = 24.0;

    /** 没有可转移的幸存者时，丧尸放弃当前目标的概率。 */
    public static final float DROP_CHANCE = 0.6F;

    /** "附近没有幸存者"的距离约定值；调用方用它代替 null，避免在规则层引入可空参数。 */
    public static final double NO_SURVIVOR_DISTANCE = -1.0;

    private AggroPreferenceRules() {
    }

    /**
     * 决定丧尸对当前目标的处置。
     *
     * <p>语义（按顺序判断）：</p>
     * <ol>
     *     <li>{@code targetIdentity != STABLE} 时返回 {@code KEEP}，忽略其余参数。幸存者、不稳定
     *     感染者和传播复发照常被仇恨；幸存者"更容易吸引丧尸"是与稳定感染者相对而言的差异，
     *     不能再额外加成一次。</li>
     *     <li>目标是稳定感染者，且存在可用幸存者距离（{@code >= 0} 且非 NaN）、该距离同时
     *     {@code <= targetDistance} 与 {@code <= REDIRECT_RANGE} 时返回 {@code REDIRECT}。</li>
     *     <li>其余情况返回 {@code dropRoll ? DROP : KEEP}。</li>
     * </ol>
     *
     * <p>NaN 与负数距离不抛异常，一律按"没有幸存者"处理：负数就是
     * {@link #NO_SURVIVOR_DISTANCE} 约定，而 NaN 来自距离计算失败，比较运算对它永远为假，
     * 如果不用显式判断就会静默落到"可转移"一侧，那会把异常数据变成改仇恨。</p>
     *
     * @param targetIdentity          丧尸当前目标的感染身份
     * @param targetDistance          丧尸到当前目标的距离
     * @param nearestSurvivorDistance 丧尸到最近的符合条件的幸存者的距离，没有则为负数
     * @param dropRoll                调用方掷出的随机判定，{@code true} 表示这次允许放弃目标
     */
    public static Decision decide(InfectionIdentity targetIdentity, double targetDistance,
                                  double nearestSurvivorDistance, boolean dropRoll) {
        if (targetIdentity != InfectionIdentity.STABLE) {
            return Decision.KEEP;
        }
        if (isUsableDistance(nearestSurvivorDistance)
                && isUsableDistance(targetDistance)
                && nearestSurvivorDistance <= targetDistance
                && nearestSurvivorDistance <= REDIRECT_RANGE) {
            return Decision.REDIRECT;
        }
        return dropRoll ? Decision.DROP : Decision.KEEP;
    }

    /** 是否是仇恨可以转移过去的目标身份；目前只有幸存者。 */
    public static boolean isEligibleSurvivor(InfectionIdentity identity) {
        return identity == InfectionIdentity.SURVIVOR;
    }

    /** 只有有限非负距离才能参与比较；NaN、无穷和负数都表示"这个距离不可用"。 */
    private static boolean isUsableDistance(double distance) {
        return Double.isFinite(distance) && distance >= 0.0D;
    }
}
