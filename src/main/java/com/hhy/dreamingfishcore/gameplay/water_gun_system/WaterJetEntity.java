package com.hhy.dreamingfishcore.gameplay.water_gun_system;

import com.hhy.dreamingfishcore.gameplay.zombie_system.adamant.AdamantZombieEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 呲水枪射出的水柱。
 *
 * <p>它<b>不造成任何伤害</b>——水枪不是武器，它的作用只有三个：灭火、把金刚僵尸浇生锈、把营火浇灭。
 * 所以这里完全没有 {@code hurt} 调用，玩家也不会因为「被水枪打到」而掉血。</p>
 *
 * <p>结构照搬 {@code BoneSpikeEntity}（同样是 {@link net.minecraft.world.entity.projectile.ThrowableProjectile}）：
 * 轻微下坠 + 扫掠命中 + 命中即消散 + 射程上限；参数在创建时从配置固化进实例并写 NBT，
 * 这样在线改配置不会让已经在飞的水柱半路变参数。</p>
 */
public class WaterJetEntity extends net.minecraft.world.entity.projectile.ThrowableProjectile {
    private static final String TAG_GRAVITY = "WaterJetGravity";
    private static final String TAG_MAX_RANGE = "WaterJetMaxRange";
    private static final String TAG_RUST_STAGES = "WaterJetRustStages";
    private static final String TAG_EXTINGUISH_ENTITIES = "WaterJetExtinguishEntities";
    private static final String TAG_EXTINGUISH_BLOCKS = "WaterJetExtinguishBlocks";
    private static final String TAG_ORIGIN = "WaterJetOrigin";

    private static final double DEFAULT_GRAVITY = 0.02D;
    private static final double DEFAULT_MAX_RANGE = 8.0D;
    /** 灭火时检查的范围半径：水柱有体积，玩家瞄的是「那团火」而不是某个方块面。 */
    private static final int EXTINGUISH_RADIUS = 1;

    private double jetGravity = DEFAULT_GRAVITY;
    private double maxRange = DEFAULT_MAX_RANGE;
    private int rustStagesPerHit = 1;
    private boolean extinguishEntities = true;
    private boolean extinguishBlockFire = true;

    /** 发射点，用于射程判定；为 null 时在第一次 tick 里补齐。 */
    @Nullable
    private Vec3 origin;

    public WaterJetEntity(EntityType<? extends WaterJetEntity> entityType, Level level) {
        super(entityType, level);
    }

    /** 由水枪开火时使用：把当前配置一次性固化到这一发水柱上。 */
    public WaterJetEntity(Level level, LivingEntity shooter, WaterGunConfig.Resolved settings) {
        this(WaterGunEntities.WATER_JET.get(), level);
        this.setOwner(shooter);
        this.setPos(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
        this.origin = this.position();
        this.jetGravity = settings.jetGravity();
        this.maxRange = settings.jetMaxRange();
        this.rustStagesPerHit = settings.rustStagesPerHit();
        this.extinguishEntities = settings.extinguishEntities();
        this.extinguishBlockFire = settings.extinguishBlockFire();

        Vec3 look = shooter.getViewVector(1.0F);
        this.shoot(look.x, look.y, look.z, (float) settings.jetSpeed(), 0.0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // 参数由发射方一次性写死并通过 NBT 保存，不需要额外的同步字段。
    }

    @Override
    protected double getDefaultGravity() {
        return this.jetGravity;
    }

    /** 只排除发射者自己：水柱可以浇到任何其他生物身上（包括别的玩家）。 */
    @Override
    protected boolean canHitEntity(Entity target) {
        Entity owner = this.getOwner();
        return (owner == null || owner != target) && super.canHitEntity(target);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.origin == null) {
            this.origin = this.position();
        }

        if (this.level().isClientSide) {
            // 一点点水花拖尾：水柱是半透明的，靠粒子才看得出在飞。
            this.level().addParticle(
                    ParticleTypes.SPLASH,
                    this.getX(), this.getY(), this.getZ(),
                    0.0D, 0.0D, 0.0D);
            return;
        }

        if (this.origin.distanceToSqr(this.position()) > this.maxRange * this.maxRange) {
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide) {
            return;
        }
        Entity hit = result.getEntity();

        // 金刚僵尸：浇一下涨一层锈（这是水枪的主要用途）。
        if (this.rustStagesPerHit > 0 && hit instanceof AdamantZombieEntity adamant) {
            adamant.applyRust(this.rustStagesPerHit);
        }

        // 灭火：身上着火的生物被浇一下就熄灭。
        if (this.extinguishEntities && hit instanceof LivingEntity living) {
            living.clearFire();
        }

        this.playSplashFeedback();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            if (this.extinguishBlockFire && result instanceof BlockHitResult blockHit) {
                this.extinguishNearbyFire(blockHit);
            }
            this.playSplashFeedback();
        }
        // 两侧都立刻消散：只用服务端消散的话，客户端的本地预测会让水柱穿过墙再飞一段。
        this.discard();
    }

    /** 把命中点周围一圈的火方块扑灭、把点着的营火浇灭。 */
    private void extinguishNearbyFire(BlockHitResult hit) {
        BlockPos center = hit.getBlockPos();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-EXTINGUISH_RADIUS, -EXTINGUISH_RADIUS, -EXTINGUISH_RADIUS),
                center.offset(EXTINGUISH_RADIUS, EXTINGUISH_RADIUS, EXTINGUISH_RADIUS))) {
            BlockState state = this.level().getBlockState(pos);
            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                this.level().removeBlock(pos, false);
            } else if (state.getBlock() instanceof CampfireBlock && CampfireBlock.isLitCampfire(state)) {
                this.level().setBlock(pos, state.setValue(CampfireBlock.LIT, false), 3);
            }
        }
    }

    private void playSplashFeedback() {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.SPLASH,
                    this.getX(), this.getY(), this.getZ(),
                    12, 0.2D, 0.2D, 0.2D, 0.03D);
            serverLevel.playSound(
                    null,
                    this.getX(), this.getY(), this.getZ(),
                    SoundEvents.GENERIC_SPLASH,
                    SoundSource.NEUTRAL,
                    0.6F,
                    1.2F);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putDouble(TAG_GRAVITY, this.jetGravity);
        compound.putDouble(TAG_MAX_RANGE, this.maxRange);
        compound.putInt(TAG_RUST_STAGES, this.rustStagesPerHit);
        compound.putBoolean(TAG_EXTINGUISH_ENTITIES, this.extinguishEntities);
        compound.putBoolean(TAG_EXTINGUISH_BLOCKS, this.extinguishBlockFire);
        if (this.origin != null) {
            compound.putDouble(TAG_ORIGIN + "X", this.origin.x);
            compound.putDouble(TAG_ORIGIN + "Y", this.origin.y);
            compound.putDouble(TAG_ORIGIN + "Z", this.origin.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains(TAG_GRAVITY)) {
            this.jetGravity = compound.getDouble(TAG_GRAVITY);
        }
        if (compound.contains(TAG_MAX_RANGE)) {
            this.maxRange = compound.getDouble(TAG_MAX_RANGE);
        }
        this.rustStagesPerHit = compound.getInt(TAG_RUST_STAGES);
        this.extinguishEntities = !compound.contains(TAG_EXTINGUISH_ENTITIES)
                || compound.getBoolean(TAG_EXTINGUISH_ENTITIES);
        this.extinguishBlockFire = !compound.contains(TAG_EXTINGUISH_BLOCKS)
                || compound.getBoolean(TAG_EXTINGUISH_BLOCKS);
        if (compound.contains(TAG_ORIGIN + "X")) {
            this.origin = new Vec3(
                    compound.getDouble(TAG_ORIGIN + "X"),
                    compound.getDouble(TAG_ORIGIN + "Y"),
                    compound.getDouble(TAG_ORIGIN + "Z"));
        }
    }
}
