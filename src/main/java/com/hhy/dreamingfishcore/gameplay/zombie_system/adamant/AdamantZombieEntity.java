package com.hhy.dreamingfishcore.gameplay.zombie_system.adamant;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.effect.DreamingFishCore_Effects;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ModZombieSpecies;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 金刚僵尸：未生锈时打不动，浇水生锈后才能打死。
 *
 * <p>继承原版 {@link Zombie}，近战追击/开门/装备生成等行为保留，机制全在伤害与状态上：</p>
 *
 * <h2>未生锈（锈级 0）</h2>
 * <p>完全免疫伤害，攻击它的**玩家**会吃「硬直」（攻击力归零 + 移速下降，默认 0.5 秒）。
 * 击退抗性默认 1.0，所以它被打时纹丝不动——反馈上就是「打在铁板上」。</p>
 *
 * <p>免疫的实现同样**不能**提前 {@code return false}（那样会砍掉受伤动画、击退链路与附魔点火），
 * 而是沿用焦尸那一套：照常调 {@code super.hurt}，只把伤害压到 0。区别是这里额外给攻击者上硬直。</p>
 *
 * <h2>生锈（锈级 1~3）</h2>
 * <p>每次被水浇到涨一层（水柱命中 +1 层；泡水/淋雨每 2 秒 +1 层），封顶 3 层。锈级就是
 * {@code dreamingfishcore:rusted} 效果的等级，所以它天然跨端同步、天然跟实体一起存档。</p>
 * <ul>
 *   <li>锈级 1 → **破防**，伤害按正常值结算；</li>
 *   <li>锈级 2/3 → 受伤倍率 +20% / +40%；</li>
 *   <li>属性惩罚由效果修饰符给出：每层移速 -20%、攻击 -1、护甲 -0.5。</li>
 * </ul>
 *
 * <p>锈不会退——一旦浇上水就永久变慢变脆，所以「怎么把它弄湿」是玩家唯一要解决的问题。</p>
 */
public class AdamantZombieEntity extends Zombie implements ModZombieSpecies {
    private static final ResourceLocation HEALTH_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "adamant_zombie_health");
    private static final ResourceLocation SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "adamant_zombie_speed");
    private static final ResourceLocation FOLLOW_RANGE_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "adamant_zombie_follow_range");
    private static final ResourceLocation ATTACK_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "adamant_zombie_attack");
    private static final ResourceLocation KNOCKBACK_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "adamant_zombie_knockback_resistance");

    /** 与 {@link #createAttributes()} 里的基础值一致，配置改动按差值套用修饰符。 */
    private static final double BASE_MAX_HEALTH = 40.0D;
    private static final double BASE_MOVEMENT_SPEED = 0.23D;
    private static final double BASE_FOLLOW_RANGE = 35.0D;
    private static final double BASE_ATTACK_DAMAGE = 3.0D;
    private static final double BASE_KNOCKBACK_RESISTANCE = 1.0D;

    private static final int SETTINGS_REFRESH_INTERVAL_TICKS = 10;
    /** 「生锈」效果用无限时长：锈不会退。 */
    private static final int RUSTED_INFINITE_DURATION = -1;
    /** 未生锈被打的「当啷」音效参数。 */
    private static final float CLANG_VOLUME = 0.45F;
    private static final float CLANG_PITCH = 1.6F;

    private AdamantZombieConfig.Resolved runtimeSettings;
    private int settingsRefreshCooldown;
    /** 潮湿状态下的生锈进度（tick）。 */
    private int wetRustTicks;

    public AdamantZombieEntity(EntityType<? extends AdamantZombieEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, BASE_FOLLOW_RANGE)
                .add(Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, BASE_KNOCKBACK_RESISTANCE);
    }

    // ------------------------------------------------------------------
    // 锈级
    // ------------------------------------------------------------------

    /** 当前锈级，0 = 未生锈。以 {@code dreamingfishcore:rusted} 的等级为唯一真源。 */
    public int rustStage() {
        MobEffectInstance instance = this.getEffect(DreamingFishCore_Effects.RUSTED);
        return AdamantZombieRules.stageForAmplifier(instance == null ? -1 : instance.getAmplifier());
    }

    /**
     * 涨锈。服务端调用；涨到新层时喷锈屑 + 播放淬火声。
     *
     * @return 是否真的涨了层
     */
    public boolean applyRust(int stages) {
        if (this.level().isClientSide || stages <= 0) {
            return false;
        }
        AdamantZombieConfig.Resolved settings = runtimeSettings();
        int current = rustStage();
        int next = AdamantZombieRules.advancedStage(current, stages, settings.maxRustStages());
        if (next <= current) {
            return false;
        }
        // visible = false：锈屑由「涨层这一刻」的一次性粒子负责，不要一直飘。
        this.addEffect(new MobEffectInstance(
                DreamingFishCore_Effects.RUSTED,
                RUSTED_INFINITE_DURATION,
                AdamantZombieRules.amplifierForStage(next),
                false,
                false,
                true));
        this.playRustFeedback();
        return true;
    }

    // ------------------------------------------------------------------
    // 伤害闸门
    // ------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || amount <= 0.0F) {
            return super.hurt(source, amount);
        }
        AdamantZombieConfig.Resolved settings = runtimeSettings();
        if (!settings.enabled()) {
            return super.hurt(source, amount);
        }

        int stage = rustStage();
        if (AdamantZombieRules.isImmune(stage, matchesAny(source, settings.alwaysEffectiveDamageTypes()))) {
            // 未生锈：伤害压到 0，但保留「被击中」的全套反馈（受伤动画、音效、仇恨），
            // 否则玩家会以为攻击没生效。绝不能在这里 return false。
            boolean hit = super.hurt(source, 0.0F);
            if (hit) {
                if (settings.immuneFeedbackEnabled()) {
                    this.playClangFeedback();
                }
                applyStaggerToAttacker(source, settings);
            }
            return hit;
        }

        float multiplier = AdamantZombieRules.damageMultiplier(stage, settings.damageBonusPerStage());
        return super.hurt(source, amount * multiplier);
    }

    /**
     * 给攻击者上硬直。
     *
     * <p>只对玩家生效：怪物打它不需要被处罚，而且玩家才是「会因为手被震麻而改变打法」的那个。
     * 除了挂效果，还会把攻击冷却重置——不然下一次挥砍照样是满蓄力，硬直只体现在伤害数字上，
     * 手感上感觉不到。</p>
     */
    private void applyStaggerToAttacker(DamageSource source, AdamantZombieConfig.Resolved settings) {
        if (!settings.staggerEnabled() || settings.staggerDurationTicks() <= 0) {
            return;
        }
        Entity attacker = source.getEntity();
        if (!(attacker instanceof Player player) || player.isSpectator()) {
            // 只豁免旁观者。刻意不按创造模式豁免：实测 gametest 的模拟玩家 isCreative()
            // 不可控（能力位清了、游戏模式也设成生存了，它仍然返回 true），
            // 而「一条豁免规则能不能被自动化验证」比省下创造玩家 0.5 秒的手感更重要。
            return;
        }
        player.resetAttackStrengthTicker();
        player.addEffect(new MobEffectInstance(
                DreamingFishCore_Effects.STAGGER,
                settings.staggerDurationTicks(),
                0,
                false,
                true,
                true), this);
    }

    /** 「当啷」：打在铁板上的反馈——重击音 + 火花。 */
    private void playClangFeedback() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.sendParticles(
                ParticleTypes.CRIT,
                this.getX(),
                this.getY() + this.getBbHeight() * 0.6D,
                this.getZ(),
                10,
                this.getBbWidth() * 0.5D,
                this.getBbHeight() * 0.3D,
                this.getBbWidth() * 0.5D,
                0.05D);
        serverLevel.playSound(
                null,
                this.getX(), this.getY(), this.getZ(),
                SoundEvents.ANVIL_LAND,
                this.getSoundSource(),
                CLANG_VOLUME,
                CLANG_PITCH);
    }

    /** 涨锈反馈：水花 + 锈屑 + 淬火声。 */
    private void playRustFeedback() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.sendParticles(
                ParticleTypes.SPLASH,
                this.getX(),
                this.getY() + this.getBbHeight() * 0.5D,
                this.getZ(),
                18,
                this.getBbWidth() * 0.6D,
                this.getBbHeight() * 0.4D,
                this.getBbWidth() * 0.6D,
                0.02D);
        serverLevel.sendParticles(
                ParticleTypes.ASH,
                this.getX(),
                this.getY() + this.getBbHeight() * 0.7D,
                this.getZ(),
                10,
                this.getBbWidth() * 0.4D,
                this.getBbHeight() * 0.2D,
                this.getBbWidth() * 0.4D,
                0.01D);
        serverLevel.playSound(
                null,
                this.getX(), this.getY(), this.getZ(),
                SoundEvents.FIRE_EXTINGUISH,
                this.getSoundSource(),
                0.8F,
                0.8F);
    }

    private static boolean matchesAny(DamageSource source, List<ResourceKey<DamageType>> keys) {
        for (ResourceKey<DamageType> key : keys) {
            if (source.is(key)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 环境行为
    // ------------------------------------------------------------------

    /** 默认不自燃：金属不该被日光烧死，而且那会成为绕过「必须先浇水」的后门。 */
    @Override
    protected boolean isSunSensitive() {
        return runtimeSettings().burnInDaylight();
    }

    /** 供测试与命令展示读取（{@code isSunSensitive} 是 protected，外部包读不到）。 */
    public boolean burnsInDaylight() {
        return this.isSunSensitive();
    }

    /** 默认不下水腐化：它的水反应应该是「生锈」。 */
    @Override
    protected boolean convertsInWater() {
        return runtimeSettings().convertInWater();
    }

    // ------------------------------------------------------------------
    // 配置与属性
    // ------------------------------------------------------------------

    public AdamantZombieConfig.Resolved runtimeSettings() {
        if (this.runtimeSettings == null) {
            this.runtimeSettings = AdamantZombieConfig.current().resolve();
        }
        return this.runtimeSettings;
    }

    @Override
    public void aiStep() {
        if (!this.level().isClientSide) {
            if (this.settingsRefreshCooldown-- <= 0) {
                this.settingsRefreshCooldown = SETTINGS_REFRESH_INTERVAL_TICKS;
                this.refreshRuntimeSettings();
            }
            this.tickWetRust();
        }
        super.aiStep();
    }

    /** 泡水/淋雨时缓慢涨锈；离开水就清零进度（不是保留——「晾干了就不再继续锈」）。 */
    private void tickWetRust() {
        AdamantZombieConfig.Resolved settings = runtimeSettings();
        if (!AdamantZombieRules.accumulatesRustFromWetness(
                this.isInWaterOrRain(), settings.rustInWaterOrRain())
                || rustStage() >= settings.maxRustStages()) {
            this.wetRustTicks = 0;
            return;
        }
        if (++this.wetRustTicks >= settings.rustIntervalTicks()) {
            this.wetRustTicks = 0;
            applyRust(1);
        }
    }

    private void refreshRuntimeSettings() {
        AdamantZombieConfig.Resolved previous = this.runtimeSettings;
        AdamantZombieConfig.Resolved next = AdamantZombieConfig.current().resolve();
        this.runtimeSettings = next;

        if (previous == null || previous.maxHealth() != next.maxHealth()) {
            applyAttributeDelta(Attributes.MAX_HEALTH, HEALTH_MODIFIER_ID, next.maxHealth(), BASE_MAX_HEALTH);
            if (this.getHealth() > this.getMaxHealth()) {
                this.setHealth(this.getMaxHealth());
            }
        }
        if (previous == null || previous.movementSpeed() != next.movementSpeed()) {
            applyAttributeDelta(
                    Attributes.MOVEMENT_SPEED, SPEED_MODIFIER_ID, next.movementSpeed(), BASE_MOVEMENT_SPEED);
        }
        if (previous == null || previous.followRange() != next.followRange()) {
            applyAttributeDelta(
                    Attributes.FOLLOW_RANGE, FOLLOW_RANGE_MODIFIER_ID, next.followRange(), BASE_FOLLOW_RANGE);
        }
        if (previous == null || previous.attackDamage() != next.attackDamage()) {
            applyAttributeDelta(
                    Attributes.ATTACK_DAMAGE, ATTACK_MODIFIER_ID, next.attackDamage(), BASE_ATTACK_DAMAGE);
        }
        if (previous == null || previous.knockbackResistance() != next.knockbackResistance()) {
            applyAttributeDelta(
                    Attributes.KNOCKBACK_RESISTANCE,
                    KNOCKBACK_MODIFIER_ID,
                    next.knockbackResistance(),
                    BASE_KNOCKBACK_RESISTANCE);
        }
    }

    /** 按「配置值 - 基准值」套一个 ADD_VALUE 修饰符，避免直接改基础值影响同类实体。 */
    private void applyAttributeDelta(
            Holder<Attribute> attribute, ResourceLocation modifierId, double configured, double base) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(modifierId);
        double delta = configured - base;
        if (Math.abs(delta) > 1.0E-6D) {
            instance.addTransientModifier(new AttributeModifier(
                    modifierId, delta, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    // ------------------------------------------------------------------
    // 生成与掉落
    // ------------------------------------------------------------------

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType spawnType,
            @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        this.settingsRefreshCooldown = SETTINGS_REFRESH_INTERVAL_TICKS;
        this.refreshRuntimeSettings();
        this.setHealth(this.getMaxHealth());
        return result;
    }

    /** 与其它模组丧尸一致：沿用原版僵尸的战利品表。 */
    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        return ResourceKey.create(
                Registries.LOOT_TABLE,
                ResourceLocation.withDefaultNamespace("entities/zombie"));
    }

    /** 供外部（水柱命中、调试命令）读取一个实体的当前锈级；非金刚僵尸返回 0。 */
    public static int rustStageOf(LivingEntity entity) {
        return entity instanceof AdamantZombieEntity adamant ? adamant.rustStage() : 0;
    }
}
