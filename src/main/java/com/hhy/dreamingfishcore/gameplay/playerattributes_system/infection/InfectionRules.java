package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection;

/**
 * 感染身份状态机的全部占位数值与纯规则。
 *
 * <p>路线图里程碑 1 的要求是"保留当前硬编码数值，先把规则和状态拆开，不在本里程碑做配置化平衡"。
 * 因此这里集中承载所有待平衡的数字：内容设计（PROJECT-M.D.G.A 的「待决策事项」：
 * 不稳定感染者的形成条件、稳定感染者的判定过程、感染复发的触发方式、两类感染者的治疗代价、
 * 不同身份的重生点消耗）一旦定稿，只需要改动这个类，不必再翻遍调用点。</p>
 *
 * <p>本类必须保持纯函数、无 Minecraft 依赖，以便服务端与客户端共用同一份数值
 * （重生代价同时用于服务端扣费与死亡界面预览）。</p>
 */
public final class InfectionRules {
    private InfectionRules() {
    }

    // ==================== 接触暴露（CONTEXT.md「接触暴露」） ====================

    /** 暴露判定间隔；沿用既有的 20 秒邻近检查节奏。 */
    public static final int EXPOSURE_INTERVAL_TICKS = 400;
    /** 暴露判定半径；沿用既有的 32 格传播半径。 */
    public static final double EXPOSURE_RADIUS = 32.0;
    /** 每次判定在传播范围内累积的暴露量。 */
    public static final float EXPOSURE_GAIN_PER_INTERVAL = 1.0F;
    /** 离开传播范围后每次判定衰减的暴露量。 */
    public static final float EXPOSURE_DECAY_PER_INTERVAL = 1.0F;
    /** 暴露量上限；达到后在下次判定时转化为真实感染增长。 */
    public static final float EXPOSURE_THRESHOLD = 6.0F;
    /** 一次暴露转化的实际感染增长；之后暴露量清零，重新累积。 */
    public static final float EXPOSURE_CONVERSION_INFECTION = 8.0F;

    /** 警告档位上限：0=无警告，1=初次提示，2=明显警告，3=即将转化。 */
    public static final int EXPOSURE_WARNING_STEPS = 3;

    /**
     * 推进一次暴露判定。
     *
     * @param charge  当前暴露量
     * @param exposed 本次判定是否处于传播范围内且未受保护
     * @return 新的暴露量，范围 {@code [0, EXPOSURE_THRESHOLD]}
     */
    public static float advanceExposure(float charge, boolean exposed) {
        float bounded = boundExposure(charge);
        float delta = exposed ? EXPOSURE_GAIN_PER_INTERVAL : -EXPOSURE_DECAY_PER_INTERVAL;
        return boundExposure(bounded + delta);
    }

    public static float boundExposure(float charge) {
        if (Float.isNaN(charge) || Float.isInfinite(charge) || charge < 0.0F) {
            return 0.0F;
        }
        return Math.min(charge, EXPOSURE_THRESHOLD);
    }

    /** 暴露量是否已经达到转化阈值（转化在达到阈值的下一次判定执行）。 */
    public static boolean convertsOnThreshold(float charge) {
        return boundExposure(charge) >= EXPOSURE_THRESHOLD;
    }

    /**
     * 当前暴露量对应的警告档位，用于选择提示文案。
     * 刚累积到 1 点就给第一次警告，随后逐级升级，避免"经过身边立即感染"。
     */
    public static int exposureWarningStep(float charge) {
        float bounded = boundExposure(charge);
        if (bounded <= 0.0F) {
            return 0;
        }
        if (bounded >= EXPOSURE_THRESHOLD * 2.0F / 3.0F) {
            return 3;
        }
        if (bounded >= EXPOSURE_THRESHOLD / 3.0F) {
            return 2;
        }
        return 1;
    }

    // ==================== 传播复发 ====================

    /** 触发复发所需的单次实际生命损失（重伤）。 */
    public static final float RELAPSE_DAMAGE_THRESHOLD = 6.0F;
    /** 复发持续时间：可观察、可结束，到点自动消退。 */
    public static final long RELAPSE_DURATION_TICKS = 6_000L;
    /** 复发结束后的冷却时间，避免同一名玩家被连续触发。 */
    public static final long RELAPSE_COOLDOWN_TICKS = 6_000L;

    /**
     * 一次伤害是否应当触发传播复发。
     *
     * @param healthLoss      本次实际损失的生命值
     * @param relapsing       当前是否已经在复发中
     * @param cooldownActive  是否处于复发冷却期
     */
    public static boolean shouldTriggerRelapse(float healthLoss, boolean relapsing, boolean cooldownActive) {
        if (relapsing || cooldownActive) {
            return false;
        }
        if (Float.isNaN(healthLoss) || Float.isInfinite(healthLoss)) {
            return false;
        }
        return healthLoss >= RELAPSE_DAMAGE_THRESHOLD;
    }

    // ==================== 分层治疗（ADR 0005） ====================

    /** 抑制剂单次降低的感染值；只对尚未突变的幸存者有效。 */
    public static final float SUPPRESSANT_INFECTION_REDUCTION = 30.0F;

    // ==================== 重生代价（ADR 0016） ====================

    /** 幸存者的标准重建消耗最低。 */
    public static final float RESPAWN_COST_SURVIVOR = 5.0F;
    /** 稳定感染者需要维持已经形成的稳定结构，消耗高于幸存者。 */
    public static final float RESPAWN_COST_STABLE = 10.0F;
    /** 不稳定感染者需要压制扩散与模板冲突，消耗最高。 */
    public static final float RESPAWN_COST_UNSTABLE = 20.0F;
    /** 保留物品栏的额外消耗，叠加在身份基础消耗之上。 */
    public static final float KEEP_INVENTORY_COST = 30.0F;

    /** 身份对应的标准重建消耗。传播复发沿用稳定感染者的基础消耗。 */
    public static float respawnCost(InfectionIdentity identity) {
        if (identity == null) {
            return RESPAWN_COST_SURVIVOR;
        }
        return switch (identity) {
            case SURVIVOR -> RESPAWN_COST_SURVIVOR;
            case UNSTABLE -> RESPAWN_COST_UNSTABLE;
            case STABLE, RELAPSE -> RESPAWN_COST_STABLE;
        };
    }

    /** 身份对应的保留物品栏消耗。 */
    public static float keepInventoryCost(InfectionIdentity identity) {
        return respawnCost(identity) + KEEP_INVENTORY_COST;
    }
}
