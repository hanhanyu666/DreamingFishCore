package com.hhy.dreamingfishcore.gameplay.zombie_system.boss;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ModZombieSpecies;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieTaskLocationRules;
import com.hhy.dreamingfishcore.gameplay.zombie_system.archer.BoneSpikeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 尸潮指挥官：远程连射、有限召唤、半血二阶段。所有战斗结算由服务器执行。 */
public final class ZombieCommanderEntity extends Zombie implements ModZombieSpecies {
    private static final EntityDataAccessor<Boolean> ENRAGED =
            SynchedEntityData.defineId(ZombieCommanderEntity.class, EntityDataSerializers.BOOLEAN);
    /** 0 空闲，1 射击蓄力/连发，2 召唤仪式；仅同步姿势，不逐帧发送计时。 */
    private static final EntityDataAccessor<Integer> CAST =
            SynchedEntityData.defineId(ZombieCommanderEntity.class, EntityDataSerializers.INT);
    /** 近战连招进行到第几段（0 表示没在连招）；客户端据此播对应的那一段动画。 */
    private static final EntityDataAccessor<Integer> MELEE =
            SynchedEntityData.defineId(ZombieCommanderEntity.class, EntityDataSerializers.INT);

    /**
     * 五个动作动画的播放状态。
     *
     * <p>原版的 {@code AnimationState} 不参与游戏逻辑，放在实体里、只在客户端驱动就够了 ——
     * 服务端只同步「现在是第几段 / 在不在施法」这两个整数。</p>
     */
    public final AnimationState melee1AnimationState = new AnimationState();
    public final AnimationState melee2AnimationState = new AnimationState();
    public final AnimationState melee3AnimationState = new AnimationState();
    public final AnimationState shootAnimationState = new AnimationState();
    public final AnimationState summonAnimationState = new AnimationState();
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            this.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    /** 保留未加载护卫的配额，不能把区块卸载当作死亡而无限补兵。 */
    private final Map<UUID, Guard> minions = new LinkedHashMap<>();

    /**
     * 一只召唤护卫的台账。
     *
     * @param expiresAt 到期时间（游戏刻）；到点无条件清掉
     * @param position  召唤时所在的位置，用于在实体表里找不到它时区分
     *                  「区块卸载」与「真的被清掉了」
     */
    private record Guard(long expiresAt, BlockPos position) { }
    private ZombieCommanderConfig.Resolved settings;
    private int refreshCooldown;
    private int attackCooldown;
    private int summonCooldown = 200;

    public ZombieCommanderEntity(EntityType<? extends ZombieCommanderEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
        if (!level.isClientSide) {
            this.refreshSettings();
            this.setHealth(this.getMaxHealth());
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes().add(Attributes.MAX_HEALTH, 240.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D).add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D).add(Attributes.ARMOR, 4.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0D);
    }

    @Override protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new ZombieCommanderCombatGoal(this));
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ENRAGED, false);
        builder.define(CAST, 0);
        builder.define(MELEE, 0);
    }
    @Override protected boolean supportsBreakDoorGoal() { return false; }
    @Override protected boolean isSunSensitive() { return false; }
    /** {@code isSunSensitive} 是 protected，状态命令与 gametest 需要它来判断「白天不自燃」。 */
    public boolean burnsInDaylight() { return this.isSunSensitive(); }
    @Override protected boolean convertsInWater() { return false; }
    @Override public void setBaby(boolean baby) { super.setBaby(false); }
    /** 禁止原版随机首领血量/增援强化，否则配置血量与随从配额会失真。 */
    @Override protected void handleAttributes(float difficulty) { }

    /**
     * 与其它模组丧尸一致：沿用原版僵尸的战利品表。
     *
     * <p>必须显式覆写：{@code EntityType} 的默认掉落表是按注册名推出来的
     * （{@code dreamingfishcore:entities/zombie_commander}），那张表并不存在，
     * 不覆写的话 Boss 被打死会什么都不掉。</p>
     */
    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        return ResourceKey.create(
                Registries.LOOT_TABLE,
                ResourceLocation.withDefaultNamespace("entities/zombie"));
    }

    public boolean isEnraged() { return this.entityData.get(ENRAGED); }
    public int getCastState() { return this.entityData.get(CAST); }
    public void setCastState(int state) { this.entityData.set(CAST, Mth.clamp(state, 0, 2)); }
    /** 当前连招段号：0 没在连招、1..3 是第几段。 */
    public int getMeleeStep() { return this.entityData.get(MELEE); }
    public void setMeleeStep(int step) {
        this.entityData.set(MELEE, Mth.clamp(step, 0, ZombieCommanderRules.MELEE_STEPS));
    }
    public ZombieCommanderConfig.Resolved runtimeSettings() {
        return this.settings == null ? ZombieCommanderConfig.current().resolve() : this.settings;
    }
    int attackCooldown() { return this.attackCooldown; }
    int summonCooldown() { return this.summonCooldown; }
    void setAttackCooldown(int ticks) { this.attackCooldown = Math.max(0, ticks); }
    void setSummonCooldown(int ticks) { this.summonCooldown = Math.max(0, ticks); }
    int activeMinionCount() { this.pruneMinions(); return this.minions.size(); }
    ServerBossEvent bossEvent() { return this.bossEvent; }
    /** {@code Mob#xpReward} 是 protected，gametest 需要它来断言击杀经验跟随配置。 */
    int experienceReward() { return this.xpReward; }

    @Override
    public void tick() {
        if (this.level().isClientSide) {
            // 动画完全由两个同步整数驱动：近战看段号、射击/召唤看施法状态。
            // animateWhen 是幂等的（条件为真只会在第一次启动），所以每 tick 调用没问题。
            int step = this.getMeleeStep();
            this.melee1AnimationState.animateWhen(step == 1, this.tickCount);
            this.melee2AnimationState.animateWhen(step == 2, this.tickCount);
            this.melee3AnimationState.animateWhen(step == 3, this.tickCount);
            this.shootAnimationState.animateWhen(this.getCastState() == 1, this.tickCount);
            this.summonAnimationState.animateWhen(this.getCastState() == 2, this.tickCount);
        }
        super.tick();
        if (!(this.level() instanceof ServerLevel)) return;
        if (this.refreshCooldown-- <= 0) {
            this.refreshCooldown = 10;
            this.refreshSettings();
            this.pruneMinions();
        }
        if (this.isAlive() && !this.isNoAi()) {
            if (this.attackCooldown > 0) this.attackCooldown--;
            if (this.summonCooldown > 0) this.summonCooldown--;
            if (runtimeSettings().enabled() && !this.isEnraged()
                    && ZombieCommanderRules.shouldEnrage(this.getHealth(), this.getMaxHealth(),
                    runtimeSettings().enrageHealthFraction())) {
                this.entityData.set(ENRAGED, true);
                this.summonCooldown = Math.min(this.summonCooldown, 40);
                this.playSound(SoundEvents.ZOMBIE_VILLAGER_CURE, 1.2F, 0.6F);
                this.burstAtRelease();
            }
        }
        this.updateBossBar();
    }

    private void updateBossBar() {
        this.bossEvent.setProgress(Mth.clamp(this.getHealth() / Math.max(1.0F, this.getMaxHealth()), 0, 1));
        this.bossEvent.setColor(this.isEnraged() ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PURPLE);
        this.bossEvent.setName(this.isEnraged()
                ? Component.translatable("boss.dreamingfishcore.zombie_commander.enraged", this.getDisplayName())
                : this.getDisplayName());
        this.bossEvent.setVisible(this.isAlive());
    }

    private void refreshSettings() {
        ZombieCommanderConfig.Resolved next = ZombieCommanderConfig.current().resolve();
        if (next.equals(this.settings)) return;
        this.settings = next;
        applyModifier(this.getAttribute(Attributes.MAX_HEALTH), "health", next.maxHealth() - 240.0D);
        applyModifier(this.getAttribute(Attributes.MOVEMENT_SPEED), "speed", next.movementSpeed() - 0.26D);
        applyModifier(this.getAttribute(Attributes.FOLLOW_RANGE), "range", next.followRange() - 40.0D);
        applyModifier(this.getAttribute(Attributes.ATTACK_DAMAGE), "attack", next.attackDamage() - 6.0D);
        applyModifier(this.getAttribute(Attributes.ARMOR), "armor", next.armor() - 4.0D);
        applyModifier(this.getAttribute(Attributes.KNOCKBACK_RESISTANCE), "knockback", next.knockbackResistance() - 0.5D);
        this.xpReward = next.experienceReward();
        if (this.getHealth() > this.getMaxHealth()) this.setHealth(this.getMaxHealth());
    }

    private static void applyModifier(@Nullable AttributeInstance attribute, String suffix, double amount) {
        if (attribute == null) return;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "commander_" + suffix);
        attribute.removeModifier(id);
        if (Math.abs(amount) > 1.0E-6D) attribute.addTransientModifier(
                new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    void fireAt(LivingEntity target, ZombieCommanderConfig.Resolved shot) {
        if (!(this.level() instanceof ServerLevel server) || !this.isAlive()) return;
        BoneSpikeEntity spike = new BoneSpikeEntity(server, this, shot.projectileDamage(),
                shot.projectileGravity(), shot.projectileMaxRange(),
                ZombieCommanderRules.projectileDamageMultiplier(server.getDifficulty().getId()));
        Vec3 offset = target.position().add(0, target.getBbHeight() * 0.5D, 0).subtract(spike.position());
        double flightTicks = offset.length() / shot.projectileSpeed();
        Vec3 aim = offset.add(0, shot.projectileGravity() * flightTicks * (flightTicks + 1) * 0.5D, 0);
        spike.shoot(aim.x, aim.y, aim.z, (float) shot.projectileSpeed(), 0.8F);
        server.addFreshEntity(spike);
        this.muzzleFlash();
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, this.isEnraged() ? 0.8F : 0.6F);
    }

    /**
     * 连招里的一刀。伤害 = 攻击属性 × 该段倍率，收尾那刀额外把人推开。
     *
     * <p>不复用原版 {@code doHurtTarget}：它只会用属性里的基础伤害，没法按段放大。
     * 这里走 {@code hurt} 并自己补击退，方向照抄原版 ——
     * 原版给 {@code knockback} 传的是「单位向量的相反数」（它内部会取负），
     * 所以参数写成 {@code (sin(yaw), -cos(yaw))} 才是把目标往正前方推。</p>
     *
     * @return 这一刀是否真的打中了（没打中不会触发音效与击退）
     */
    boolean meleeStrike(LivingEntity target, int step) {
        if (!(this.level() instanceof ServerLevel server) || !this.isAlive()) return false;
        float base = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float amount = (float) (base * ZombieCommanderRules.meleeDamageMultiplier(step)
                * ZombieCommanderRules.projectileDamageMultiplier(server.getDifficulty().getId()));
        if (base > 0.0F && amount <= 0.0F) {
            // 和平难度：projectileDamageMultiplier 返回 0，近战同样不结算
            return false;
        }
        if (step > 1) {
            // 玩家的受击无敌帧是 20 tick，而三段连招的间隔只有 10~16 tick —— 不清掉的话
            // 第 2、3 段会被无敌帧整个吃掉，"三段连招"就只剩第一刀有伤害了。
            // 只清自己这一轮连招的后续段，不影响别的伤害来源的判定。
            target.invulnerableTime = 0;
        }
        boolean hurt = target.hurt(this.damageSources().mobAttack(this), amount);
        if (!hurt) return false;
        double knockback = ZombieCommanderRules.meleeKnockback(step);
        if (knockback > 0.0D) {
            float yaw = this.getYRot() * Mth.DEG_TO_RAD;
            target.knockback(knockback, Mth.sin(yaw), -Mth.cos(yaw));
        }
        this.playSound(switch (ZombieCommanderRules.clampMeleeStep(step)) {
            case 1 -> SoundEvents.PLAYER_ATTACK_SWEEP;
            case 2 -> SoundEvents.PLAYER_ATTACK_STRONG;
            default -> SoundEvents.PLAYER_ATTACK_CRIT;
        }, 1.0F, this.isEnraged() ? 0.8F : 1.0F);
        this.setLastHurtMob(target);
        return true;
    }

    /** 找安全的真实地面；不挖块/不填块，不在未加载区块、墙内或液体中强行生成。 */
    int summonGuards(ZombieCommanderConfig.Resolved cast) {
        if (!(this.level() instanceof ServerLevel server) || !this.isAlive() || !cast.enabled()) return 0;
        int wanted = ZombieCommanderRules.availableSummons(this.activeMinionCount(), cast.maxMinions(),
                this.isEnraged() ? cast.enragedSummonCount() : cast.summonCount());
        int spawned = 0;
        for (int attempt = 0; attempt < 24 && spawned < wanted; attempt++) {
            double angle = this.random.nextDouble() * Math.PI * 2;
            double radius = 3.0D + this.random.nextDouble() * 2.0D;
            BlockPos column = BlockPos.containing(this.getX() + Math.cos(angle) * radius,
                    this.getY(), this.getZ() + Math.sin(angle) * radius);
            for (int dy = 2; dy >= -3; dy--) {
                BlockPos pos = column.offset(0, dy, 0);
                if (!server.hasChunkAt(pos) || !server.getWorldBorder().isWithinBounds(pos)
                        || !server.getBlockState(pos.below()).isFaceSturdy(server, pos.below(), net.minecraft.core.Direction.UP)
                        || ZombieTaskLocationRules.blocksMonsterSpawn(ZombieCommanderEntities.COMMANDER_MINION.get(),
                        server, pos, MobSpawnType.SPAWNER)) continue;
                CommanderMinionEntity guard = ZombieCommanderEntities.COMMANDER_MINION.get().create(server);
                if (guard == null) break;
                guard.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, this.getYRot(), 0);
                if (!server.noCollision(guard) || server.containsAnyLiquid(guard.getBoundingBox())
                        || !server.isUnobstructed(guard)) continue;
                guard.bindTo(this, cast.minionLifetimeTicks());
                if (server.addFreshEntity(guard)) {
                    this.minions.put(guard.getUUID(),
                            new Guard(server.getGameTime() + cast.minionLifetimeTicks(), pos));
                    server.sendParticles(ParticleTypes.SOUL, guard.getX(), guard.getY() + 1, guard.getZ(),
                            12, 0.25, 0.5, 0.25, 0.02);
                    spawned++;
                }
                break;
            }
        }
        return spawned;
    }

    private void pruneMinions() {
        if (!(this.level() instanceof ServerLevel server)) return;
        long now = server.getGameTime();
        this.minions.entrySet().removeIf(entry -> {
            Guard guard = entry.getValue();
            if (now >= guard.expiresAt()) {
                Entity entity = server.getEntity(entry.getKey());
                if (entity instanceof CommanderMinionEntity minion) minion.discard();
                return true;
            }
            Entity entity = server.getEntity(entry.getKey());
            if (entity != null) {
                return !entity.isAlive() || entity.isRemoved();
            }
            // 实体不在实体表里有两种可能：区块卸载（护卫还在，只是没加载）与护卫真的没了
            // （被打死、被别的逻辑清掉）。用「它当初所在的位置还加载着吗」区分：
            // 加载着却找不到实体，就是真没了，名额必须还回来 —— 否则护卫被杀之后，
            // 这个名额会一直被一个不存在的实体占着，直到它原本的寿命走完。
            return server.hasChunkAt(guard.position());
        });
    }

    /** 配置里的粒子强度；0 表示完全关闭，不影响任何战斗结算。 */
    private double particleScale() {
        return this.runtimeSettings().particleIntensity();
    }

    /**
     * 蓄力期间每 tick 的能量汇聚。
     *
     * <p>观感是一条从外圈向内收拢、同时抬升的螺旋：半径随蓄力进度收缩，粒子也从青色的
     * {@code SOUL} 换成更冷的 {@code SCULK_SOUL}，最后一段再换成电火花 —— 玩家不用看血条也能
     * 读出「它充到第几成了」。过半之后眼睛处持续冒灵魂火，给出最直白的瞄准预警。</p>
     *
     * <p>全部是原版粒子，不新增任何资源；密度随配置的 {@code particleIntensity} 缩放。</p>
     */
    void tickChargeAura(int castTicks, int chargeTicks) {
        double scale = this.particleScale();
        if (scale <= 0.0D || !(this.level() instanceof ServerLevel server)) return;
        double progress = Mth.clamp(castTicks / (double) Math.max(1, chargeTicks), 0.0D, 1.0D);
        // 半径随进度收缩并抬高：读起来就是「能量被吸进身体」
        double radius = Mth.lerp(progress, this.isEnraged() ? 3.1D : 2.5D, 0.45D);
        double angle = castTicks * 0.85D;
        double x = this.getX() + Math.cos(angle) * radius;
        double z = this.getZ() + Math.sin(angle) * radius;
        double y = this.getY() + Mth.lerp(progress, 0.15D, 2.0D);
        int count = Math.max(1, (int) Math.ceil(scale * (progress >= 0.5D ? 3.0D : 2.0D)));

        if (progress < 0.5D) {
            server.sendParticles(ParticleTypes.SOUL, x, y, z, count, 0.05D, 0.05D, 0.05D, 0.01D);
        } else if (progress < 0.9D) {
            server.sendParticles(ParticleTypes.SCULK_SOUL, x, y, z, count, 0.05D, 0.05D, 0.05D, 0.01D);
        } else {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, count, 0.1D, 0.1D, 0.1D, 0.02D);
        }

        // 眼睛亮起来：进入凝练阶段后持续冒灵魂火
        if (progress >= 0.5D && castTicks % 2 == 0) {
            server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    this.getX(), this.getEyeY() - 0.12D, this.getZ(),
                    Math.max(1, (int) Math.ceil(scale)), 0.1D, 0.06D, 0.1D, 0.0D);
        }
        // 濒临发射：贴着身体闪一层附魔光，作为「马上要打出去」的强信号
        if (progress >= 0.9D && castTicks % 3 == 0) {
            server.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    this.getX(), this.getY() + 1.1D, this.getZ(),
                    Math.max(1, (int) Math.ceil(scale * 4.0D)), 0.45D, 0.6D, 0.45D, 0.15D);
        }
    }

    /** 蓄满、弹丸即将离膛的那一刻：音爆环 + 向外炸开的白色光点。每轮只放一次。 */
    void burstAtRelease() {
        double scale = this.particleScale();
        if (scale <= 0.0D || !(this.level() instanceof ServerLevel server)) return;
        double y = this.getEyeY() - 0.15D;
        server.sendParticles(
                ParticleTypes.SONIC_BOOM, this.getX(), y, this.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        server.sendParticles(ParticleTypes.END_ROD, this.getX(), y, this.getZ(),
                Math.max(1, (int) Math.ceil(scale * 10.0D)), 0.35D, 0.3D, 0.35D, 0.12D);
        if (this.isEnraged()) {
            server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), y, this.getZ(),
                    Math.max(1, (int) Math.ceil(scale * 8.0D)), 0.3D, 0.25D, 0.3D, 0.03D);
        }
    }

    /** 每一发弹丸离膛的小闪光，让连发的节奏看得出来。 */
    private void muzzleFlash() {
        double scale = this.particleScale();
        if (scale <= 0.0D || !(this.level() instanceof ServerLevel server)) return;
        double y = this.getEyeY() - 0.15D;
        server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), y, this.getZ(),
                Math.max(1, (int) Math.ceil(scale * 3.0D)), 0.12D, 0.12D, 0.12D, 0.02D);
        server.sendParticles(ParticleTypes.END_ROD, this.getX(), y, this.getZ(),
                Math.max(1, (int) Math.ceil(scale * 2.0D)), 0.1D, 0.1D, 0.1D, 0.05D);
    }

    /** 召唤收尾的金色脉冲，与「护卫凭空出现」这件事对上。 */
    void summonBurst() {
        double scale = this.particleScale();
        if (scale <= 0.0D || !(this.level() instanceof ServerLevel server)) return;
        server.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                this.getX(), this.getY() + 1.2D, this.getZ(),
                Math.max(1, (int) Math.ceil(scale * 12.0D)), 1.2D, 0.8D, 1.2D, 0.35D);
    }

    @Override public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.updateBossBar();
        this.bossEvent.addPlayer(player);
    }
    @Override public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }
    @Override public void remove(Entity.RemovalReason reason) {
        if (!this.level().isClientSide) {
            this.bossEvent.removeAllPlayers();
            if (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED) {
                if (this.level() instanceof ServerLevel server) for (UUID id : this.minions.keySet()) {
                    Entity guard = server.getEntity(id);
                    if (guard instanceof CommanderMinionEntity) guard.discard();
                }
            }
        }
        super.remove(reason);
    }

    @Override @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, new ZombieGroupData(false, false));
        this.refreshSettings();
        this.setHealth(this.getMaxHealth());
        this.setCanPickUpLoot(false);
        return result;
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("CommanderEnraged", this.isEnraged());
        tag.putInt("CommanderAttackCooldown", this.attackCooldown);
        tag.putInt("CommanderSummonCooldown", this.summonCooldown);
        ListTag list = new ListTag();
        for (var entry : this.minions.entrySet()) {
            CompoundTag minion = new CompoundTag();
            minion.putUUID("Id", entry.getKey());
            minion.putLong("ExpiresAt", entry.getValue().expiresAt());
            minion.putLong("Pos", entry.getValue().position().asLong());
            list.add(minion);
        }
        tag.put("CommanderMinions", list);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.settings = null;
        this.refreshSettings();
        this.entityData.set(ENRAGED, tag.getBoolean("CommanderEnraged"));
        // 瞬时施法不恢复：重启后重新蓄力；已保存的攻击/召唤冷却不会被清零。
        this.setCastState(0);
        this.attackCooldown = Mth.clamp(tag.getInt("CommanderAttackCooldown"), 0, 12000);
        this.summonCooldown = tag.contains("CommanderSummonCooldown")
                ? Mth.clamp(tag.getInt("CommanderSummonCooldown"), 0, 12000) : 200;
        this.minions.clear();
        ListTag list = tag.getList("CommanderMinions", 10);
        for (int i = 0; i < Math.min(24, list.size()); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("Id")) {
                // 旧记录可能没有位置：退回本体位置，最坏情况只是这一次的卸载判定偏保守。
                BlockPos position = entry.contains("Pos")
                        ? BlockPos.of(entry.getLong("Pos")) : this.blockPosition();
                this.minions.put(entry.getUUID("Id"),
                        new Guard(entry.getLong("ExpiresAt"), position));
            }
        }
        this.setCanPickUpLoot(false);
        this.updateBossBar();
    }
}
