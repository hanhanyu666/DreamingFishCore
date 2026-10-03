package com.hhy.dreamingfishcore.gameplay.zombie_system.archer;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ModZombieSpecies;
import com.hhy.dreamingfishcore.gameplay.zombie_system.archer.ai.ArcherZombieRangedAttackGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

/**
 * 射手僵尸：普通僵尸的远程变体。
 *
 * <p>刻意继承原版 {@link Zombie}，所以近战追击、开门、白天着火、水中腐化、原版模型与音效
 * 全部原样保留——差异只体现在属性（生命更低、移速略慢）和多出来的一个
 * {@link ArcherZombieRangedAttackGoal}。</p>
 *
 * <p>与围攻僵尸 {@code SiegeZombieEntity} 不同，这只生物**不**参与挖掘/搭桥/破门那套攻城
 * 逻辑，也不读取故事阶段的丧尸能力开关：它的定位是尸潮里的持续压制火力，数值全部走
 * {@link ArcherZombieConfig}（{@code config/dreamingfishcore/archer_zombie.json}）。</p>
 */
public class ArcherZombieEntity extends Zombie implements ModZombieSpecies {
    /** 是否处于蓄力（抬手）状态；同步给客户端驱动模型动画。 */
    private static final EntityDataAccessor<Boolean> DATA_CHARGING =
            SynchedEntityData.defineId(ArcherZombieEntity.class, EntityDataSerializers.BOOLEAN);
    /** 本次蓄力的总时长；客户端据此把蓄力进度换算成 0~1 的抬手幅度。 */
    private static final EntityDataAccessor<Integer> DATA_CHARGE_DURATION =
            SynchedEntityData.defineId(ArcherZombieEntity.class, EntityDataSerializers.INT);
    private static final String TAG_CHARGING = "ArcherZombieCharging";
    private static final String TAG_CHARGE_DURATION = "ArcherZombieChargeDuration";

    private static final ResourceLocation HEALTH_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "archer_zombie_health");
    private static final ResourceLocation SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "archer_zombie_speed");
    private static final ResourceLocation FOLLOW_RANGE_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "archer_zombie_follow_range");

    /** 与 {@link #createAttributes()} 里的基础值一致，配置改动按差值套用修饰符。 */
    private static final double BASE_MAX_HEALTH = 16.0D;
    private static final double BASE_MOVEMENT_SPEED = 0.21D;
    private static final double BASE_FOLLOW_RANGE = 35.0D;

    private static final int SETTINGS_REFRESH_INTERVAL_TICKS = 10;

    private ArcherZombieConfig.Resolved runtimeSettings;
    private int settingsRefreshCooldown;
    /** 只在客户端累加，用于把同步过来的蓄力时长换算成动画进度。 */
    private int clientChargeTicks;

    public ArcherZombieEntity(EntityType<? extends ArcherZombieEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // 优先级 1：比原版近战（优先级 2）高，所以只要进入射程就由远程 AI 接管；
        // 目标贴脸或没有视线时该 Goal 会自行放弃，控制权自然回到原版近战追击。
        this.goalSelector.addGoal(1, new ArcherZombieRangedAttackGoal(this));
    }

    /**
     * 不参与破门。
     *
     * <p>两个原因：一是它站在远处射击，破门不是它的职责（破门/挖掘留给围攻僵尸）；二是原版
     * 破门 Goal 也占优先级 1，与远程 Goal 撞车，关掉后优先级分配是确定的。</p>
     */
    @Override
    protected boolean supportsBreakDoorGoal() {
        return false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        // 生命 16（原版 20，远程压制单位刻意更脆）、移速 0.21（原版 0.23）、索敌距离沿用原版 35。
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, BASE_FOLLOW_RANGE);
    }

    // ------------------------------------------------------------------
    // 蓄力状态（同步给客户端做抬手动画）
    // ------------------------------------------------------------------

    public boolean isCharging() {
        return this.entityData.get(DATA_CHARGING);
    }

    public void setCharging(boolean charging) {
        this.entityData.set(DATA_CHARGING, charging);
    }

    public int getChargeDurationTicks() {
        return Math.max(1, this.entityData.get(DATA_CHARGE_DURATION));
    }

    public void setChargeDurationTicks(int ticks) {
        this.entityData.set(DATA_CHARGE_DURATION, Mth.clamp(ticks, 1, 200));
    }

    /**
     * 客户端渲染用的 0~1 蓄力进度。
     *
     * <p>刻意不在服务端每 tick 同步进度：只同步一个布尔值 + 一个时长，客户端本地自行累加，
     * 大量同屏幕实体时同步开销近似为零。</p>
     */
    public float getChargeProgress() {
        if (!this.isCharging()) {
            return 0.0F;
        }
        return Mth.clamp((float) this.clientChargeTicks / (float) this.getChargeDurationTicks(), 0.0F, 1.0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CHARGING, false);
        builder.define(DATA_CHARGE_DURATION, 20);
    }

    @Override
    public void tick() {
        if (this.level().isClientSide) {
            if (this.isCharging()) {
                this.clientChargeTicks++;
            } else {
                this.clientChargeTicks = 0;
            }
        }
        super.tick();
    }

    // ------------------------------------------------------------------
    // 配置与属性
    // ------------------------------------------------------------------

    /** 当前的不可变数值快照；AI 与投射物只读它。 */
    public ArcherZombieConfig.Resolved runtimeArcherSettings() {
        if (this.runtimeSettings == null) {
            this.runtimeSettings = ArcherZombieConfig.current().resolve();
        }
        return this.runtimeSettings;
    }

    @Override
    public void aiStep() {
        if (!this.level().isClientSide && this.settingsRefreshCooldown-- <= 0) {
            this.settingsRefreshCooldown = SETTINGS_REFRESH_INTERVAL_TICKS;
            this.refreshRuntimeSettings();
        }
        super.aiStep();
    }

    private void refreshRuntimeSettings() {
        ArcherZombieConfig.Resolved previous = this.runtimeSettings;
        ArcherZombieConfig.Resolved next = ArcherZombieConfig.current().resolve();
        this.runtimeSettings = next;

        if (previous == null || previous.maxHealth() != next.maxHealth()) {
            this.applyHealthModifier(next);
        }
        if (previous == null || previous.movementSpeed() != next.movementSpeed()) {
            this.applySpeedModifier(next);
        }
        if (previous == null || previous.followRange() != next.followRange()) {
            this.applyFollowRangeModifier(next);
        }
    }

    private void applyHealthModifier(ArcherZombieConfig.Resolved settings) {
        AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) {
            return;
        }
        health.removeModifier(HEALTH_MODIFIER_ID);
        double delta = settings.maxHealth() - BASE_MAX_HEALTH;
        if (Math.abs(delta) > 1.0E-6D) {
            health.addTransientModifier(new AttributeModifier(
                    HEALTH_MODIFIER_ID, delta, AttributeModifier.Operation.ADD_VALUE));
        }
        // 生命上限调小后当前生命必须一起夹回去，否则会出现「血条超出上限」的残留。
        if (this.getHealth() > this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }
    }

    private void applySpeedModifier(ArcherZombieConfig.Resolved settings) {
        AttributeInstance movement = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement == null) {
            return;
        }
        movement.removeModifier(SPEED_MODIFIER_ID);
        double delta = settings.movementSpeed() - BASE_MOVEMENT_SPEED;
        if (Math.abs(delta) > 1.0E-6D) {
            movement.addTransientModifier(new AttributeModifier(
                    SPEED_MODIFIER_ID, delta, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private void applyFollowRangeModifier(ArcherZombieConfig.Resolved settings) {
        AttributeInstance followRange = this.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange == null) {
            return;
        }
        followRange.removeModifier(FOLLOW_RANGE_MODIFIER_ID);
        double delta = settings.followRange() - BASE_FOLLOW_RANGE;
        if (Math.abs(delta) > 1.0E-6D) {
            followRange.addTransientModifier(new AttributeModifier(
                    FOLLOW_RANGE_MODIFIER_ID, delta, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    // ------------------------------------------------------------------
    // 生成
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
        // 套完生命修饰符后再按配置满血出生。
        this.setHealth(this.getMaxHealth());
        return result;
    }

    public static boolean checkSpawnRules(
            EntityType<ArcherZombieEntity> type,
            ServerLevelAccessor level,
            MobSpawnType spawnType,
            BlockPos pos,
            RandomSource random) {
        if (com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieTaskLocationRules
                .blocksMonsterSpawn(type, level, pos, spawnType)) {
            return false;
        }
        return Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    /** 与围攻僵尸一致：沿用原版僵尸的战利品表（腐肉 + 稀有铁锭/胡萝卜/土豆）。 */
    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        return ResourceKey.create(
                Registries.LOOT_TABLE,
                ResourceLocation.withDefaultNamespace("entities/zombie"));
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        // 蓄力只是瞬时状态，但重启正好卡在蓄力中时不该丢：存下来至少姿势和冷却不会错乱。
        compound.putBoolean(TAG_CHARGING, this.isCharging());
        compound.putInt(TAG_CHARGE_DURATION, this.getChargeDurationTicks());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains(TAG_CHARGE_DURATION)) {
            this.setChargeDurationTicks(compound.getInt(TAG_CHARGE_DURATION));
        }
        this.setCharging(compound.getBoolean(TAG_CHARGING));
    }
}
