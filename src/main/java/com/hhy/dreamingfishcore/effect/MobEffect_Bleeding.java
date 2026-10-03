package com.hhy.dreamingfishcore.effect;

import com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieDamageTypes;
import com.hhy.dreamingfishcore.gameplay.zombie_system.archer.PlayerMotionTracker;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 「流血」：射手僵尸的骨刺命中后附加的持续掉血。
 *
 * <p>实现方式是每秒结算一次穿透护甲的少量伤害（走自定义伤害类型
 * {@code dreamingfishcore:bleeding}，{@code scaling: never}，因此不受原版难度缩放影响，
 * 死亡提示也是专门的「失血过多」），等级每 +1 每秒多 1 点。用持续小伤害而不是
 * 「命中时一次性扣更多」，是为了让中弹后有明确的、可感知的止血需求，而不是单纯叠高单次伤害。</p>
 *
 * <p><b>玩家边跑边掉血、站着不动就不掉</b>：对玩家来说，结算那一 tick 必须有 WASD 级别的水平
 * 位移，伤口才会继续撕裂；站着不动等于压迫止血。位移来自 {@link PlayerMotionTracker}
 * （在 {@code PlayerTickEvent.Post} 采样上一 tick 的位移），不是本效果自己现算——原因见那个类的
 * 注释：{@code tickEffects()} 跑在移动之前，现算永远是 0。</p>
 *
 * <p>结算频率仍是每秒一次（{@code duration % 20 == 0}），所以实际手感是"每秒采样一次这一刻是否
 * 在移动"，持续跑动时会稳定掉血，走走停停则按移动的占比掉血。非玩家生物（村民、动物）
 * 不吃这条限制，照常每秒结算——这条规则是给玩家移动决策用的。</p>
 *
 * <p>数值不在效果里改：每秒伤害 = {@code 1 + 等级}，要调强弱请改骨刺的
 * {@code bleedAmplifier} / {@code bleedDurationTicks}（见 {@code archer_zombie.json}）。</p>
 */
public class MobEffect_Bleeding extends DreamingFishMobEffect {
    /** 每秒基础伤害；等级每 +1 再 +1。 */
    private static final float DAMAGE_PER_SECOND = 1.0F;
    /** 结算间隔：每 20 tick（1 秒）掉一次血。 */
    private static final int TICK_INTERVAL = 20;
    /** 图标颜色：暗红。 */
    private static final int COLOR = 0x9B1B1B;

    public MobEffect_Bleeding() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof Player player && !PlayerMotionTracker.isMoving(player.getUUID())) {
            // 站着不动：这一秒不结算，但效果保留（下一次边界再采样）。
            return true;
        }
        entity.hurt(entity.damageSources().source(ZombieDamageTypes.BLEEDING),
                DAMAGE_PER_SECOND + amplifier);
        // 返回 true：流血不会因为一次结算就自行结束，只在持续时间用完后移除。
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % TICK_INTERVAL == 0;
    }
}
