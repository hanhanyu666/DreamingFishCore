package com.hhy.dreamingfishcore.gameplay.zombie_system.archer;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.effect.DreamingFishCore_Effects;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieDamageTypes;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 射手僵尸的端到端 gametest：在真实服务端里验证「开火 → 命中 → 伤害与减益 → 死亡与掉落」。
 *
 * <p>两个刻意的做法，都是为了让它确定，而不是"跑起来碰运气"：</p>
 * <ul>
 *   <li><b>自建空中平台</b>：{@code empty} 模板在世界底部，实体摆在相对坐标 y=1 会落在地形里
 *       （实测表现是视线为 false、骨刺第一 tick 就碰到方块消散）。这里先在相对 y=11 铺一层
 *       石头，再把双方放在 y=12 的同一平面上，位置与视线就不再受地形影响。</li>
 *   <li><b>手动驱动 tick</b>：AI 与弹道都由测试自己推进（{@code archer.tick()} /
 *       {@code spike.tick()}），不依赖世界时钟，也就不会出现"偶尔差一 tick"的假红灯。</li>
 * </ul>
 *
 * <p>另外，gametest 的模拟玩家把 {@code isCreative()} 硬编码为 true、能力位里
 * {@code invulnerable} 跟着置位，而 {@code Player#canBeSeenAsEnemy}（丧尸 {@code canAttack} 会查）
 * 与 {@code Player#hurt} 都会读这个标志——不显式关掉的话它既不能被选为目标、也不会掉血。</p>
 */
@GameTestHolder(DreamingFishCore.MODID)
@PrefixGameTestTemplate(false)
public class ArcherZombieGameTest {
    /** AI 推进上限：蓄力 22 tick + 首帧路径，80 tick 有足够余量。 */
    private static final int MAX_AI_TICKS = 80;
    /** 骨刺推进上限。 */
    private static final int MAX_PROJECTILE_TICKS = 60;
    /** 平台层：实体站在 platformY + 1 上。 */
    private static final int PLATFORM_Y = 11;
    /** ServerPlayer 出生后的私有无敌帧长度（默认 60）；跑完它玩家才真的可被伤害。 */
    private static final int SPAWN_INVULNERABLE_TICKS = 70;

    @GameTest(template = "empty")
    public static void archerZombieFiresABoneSpikeAtAnInRangeTarget(GameTestHelper helper) {
        buildPlatform(helper);
        ServerPlayer player = spawnPlayer(helper, new BlockPos(14, PLATFORM_Y + 1, 2));
        ArcherZombieEntity archer = null;
        BoneSpikeEntity spike = null;
        try {
            archer = spawnArcher(helper, new BlockPos(2, PLATFORM_Y + 1, 2));
            archer.setTarget(player);

            double distance = archer.distanceTo(player);
            helper.assertTrue(distance > 2.5D && distance < 16.0D,
                    "站位应当落在「大于近战交接距离、小于射程」的区间里，实际 " + distance);
            helper.assertTrue(archer.getSensing().hasLineOfSight(player),
                    "同一平台上的目标必须有视线，实际 " + describeLineOfSight(helper, archer, player));

            for (int i = 0; i < MAX_AI_TICKS && spike == null; i++) {
                archer.tick();
                spike = findSpike(helper, archer);
            }

            helper.assertTrue(spike != null,
                    "射手僵尸应当在 " + MAX_AI_TICKS + " tick 内向射程内的目标发射骨刺"
                            + "（距离 " + distance
                            + "，可攻击 " + archer.canAttack(player)
                            + "，视线 " + archer.getSensing().hasLineOfSight(player)
                            + "，蓄力中 " + archer.isCharging()
                            + "，目标 " + describeTarget(archer) + "）");
            helper.assertTrue(spike.getDeltaMovement().lengthSqr() > 0.0D,
                    "刚发射的骨刺应当有初速度");
        } finally {
            if (spike != null) {
                spike.discard();
            }
            if (archer != null) {
                archer.discard();
            }
            dispose(player);
        }
        helper.succeed();
    }

    /**
     * 骨刺命中生物：穿刺伤害 + 减速 + 流血 + 命中即消散。
     *
     * <p>靶子用普通动物（牛）而不是模拟玩家：gametest 的模拟玩家把 {@code isCreative()} 硬编码为
     * true，{@code player.hurt(...)} 会直接返回 false（同一次运行里的
     * {@link #mockPlayerCanBeHurtByBoneSpikeDamage} 会把玩家可受击这件事单独钉下来），
     * 所以它既不能被弹射物伤到、也当不了受害者。牛是普通的 LivingEntity，走的是同一条
     * {@code LivingEntity.hurt} + {@code addEffect} 路径。</p>
     */
    @GameTest(template = "empty")
    public static void boneSpikeHurtsAndDebuffsTheLivingEntityItHits(GameTestHelper helper) {
        buildPlatform(helper);
        ServerLevel level = helper.getLevel();
        ArcherZombieEntity archer = null;
        BoneSpikeEntity spike = null;
        Cow victim = null;
        Cow probeVictim = null;
        try {
            // 射手只作为「发射者」提供 mob_projectile 伤害归属，关掉 AI 避免它自己也开枪干扰断言。
            archer = spawnArcher(helper, new BlockPos(2, PLATFORM_Y + 1, 2));
            archer.setNoAi(true);
            victim = helper.spawn(EntityType.COW, new BlockPos(6, PLATFORM_Y + 1, 2));
            // 探针牛放在射手的另一侧，不在弹道上：只回答"这份伤害源能不能直接伤到牛"。
            probeVictim = helper.spawn(EntityType.COW, new BlockPos(2, PLATFORM_Y + 1, 4));

            // 关掉散布与重力：这一条只验证「命中后的伤害与减益」，弹道随机性另行由瞄准公式负责。
            ArcherZombieConfig.Resolved deterministic = new ArcherZombieConfig.Resolved(
                    true, true, 40, 16.0D, 0.21D, 35.0D, 2.5D, 6.0D, 16.0D, 22, 40,
                    4.0D, 1.6D, 0.0D, 0.0D, 24.0D, true, 60, 0, true, 100, 0);

            spike = new BoneSpikeEntity(level, archer, deterministic);
            Vec3 from = spike.position();
            Vec3 to = victim.getEyePosition();
            HitResult path = level.clip(new ClipContext(
                    from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, spike));

            float probeHealthBefore = probeVictim.getHealth();
            boolean probeDamaged = probeVictim.hurt(
                    level.damageSources().mobProjectile(spike, archer), 4.0F);
            String probeReport = "探针直接施伤=" + probeDamaged
                    + "（" + probeHealthBefore + "→" + probeVictim.getHealth() + "）";

            spike.shoot(to.x - from.x, to.y - from.y, to.z - from.z, 1.6F, 0.0F);
            level.addFreshEntity(spike);

            float healthBefore = victim.getHealth();
            int ticks = 0;
            for (int i = 0; i < MAX_PROJECTILE_TICKS && !spike.isRemoved(); i++) {
                spike.tick();
                ticks++;
            }

            helper.assertTrue(victim.getHealth() < healthBefore,
                    "骨刺命中应当造成穿刺伤害，实际血量 " + victim.getHealth() + " / " + healthBefore
                            + "（推进 " + ticks + " tick，骨刺已消散 " + spike.isRemoved()
                            + "，发射点 " + from + "，目标点 " + to
                            + "，两者之间第一处碰撞 " + path.getType() + "@" + path.getLocation()
                            + "，" + probeReport + "）");
            helper.assertTrue(victim.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "骨刺命中应当附加短暂减速");
            helper.assertTrue(victim.hasEffect(DreamingFishCore_Effects.BLEEDING),
                    "骨刺命中应当附加流血");
            helper.assertTrue(spike.isRemoved(), "命中之后骨刺应当消散");
        } finally {
            if (spike != null) {
                spike.discard();
            }
            if (victim != null) {
                victim.discard();
            }
            if (probeVictim != null) {
                probeVictim.discard();
            }
            if (archer != null) {
                archer.discard();
            }
        }
        helper.succeed();
    }

    /**
     * 直接施伤探针：用骨刺的伤害源对模拟玩家调用一次 {@code hurt}。
     *
     * <p>这条与弹道无关，只回答"模拟玩家能不能被这根骨刺伤到"。它是另外两条用例的前提：
     * 玩家必须真的可受击，才能验证「骨刺命中玩家造成伤害」与「流血只在移动时结算」。</p>
     */
    @GameTest(template = "empty")
    public static void mockPlayerCanBeHurtByBoneSpikeDamage(GameTestHelper helper) {
        buildPlatform(helper);
        ServerPlayer player = spawnPlayer(helper, new BlockPos(6, PLATFORM_Y + 1, 2));
        ServerLevel level = helper.getLevel();
        ArcherZombieEntity archer = null;
        BoneSpikeEntity spike = null;
        try {
            archer = spawnArcher(helper, new BlockPos(2, PLATFORM_Y + 1, 2));
            archer.setNoAi(true);

            ArcherZombieConfig.Resolved settings = new ArcherZombieConfig.Resolved(
                    true, true, 40, 16.0D, 0.21D, 35.0D, 2.5D, 6.0D, 16.0D, 22, 40,
                    4.0D, 1.6D, 0.0D, 0.0D, 24.0D, true, 60, 0, true, 100, 0);
            spike = new BoneSpikeEntity(level, archer, settings);

            float healthBefore = player.getHealth();
            DamageSource source = level.damageSources().source(
                    ZombieDamageTypes.BONE_SPIKE, archer, spike);
            boolean damaged = player.hurt(source, (float) (4.0D
                    * ArcherZombieConfig.damageMultiplierFor(level.getDifficulty())));

            helper.assertTrue(damaged && player.getHealth() < healthBefore,
                    "模拟玩家应当能被骨刺伤害源伤到，实际 damaged=" + damaged
                            + "，血量 " + player.getHealth() + " / " + healthBefore
                            + "，不灭标志=" + player.isInvulnerable()
                            + "，能力不灭=" + player.getAbilities().invulnerable
                            + "，创造=" + player.isCreative());
        } finally {
            if (spike != null) {
                spike.discard();
            }
            if (archer != null) {
                archer.discard();
            }
            dispose(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void archerZombieSpawnsWithDifferentiatedAttributesAndVanillaZombieDrops(
            GameTestHelper helper) {
        buildPlatform(helper);
        ServerPlayer player = spawnPlayer(helper, new BlockPos(12, PLATFORM_Y + 1, 2));
        ArcherZombieEntity archer = null;
        try {
            archer = spawnArcher(helper, new BlockPos(2, PLATFORM_Y + 1, 2));

            // 与普通僵尸的差异只体现在属性上：生命 16（原版 20）、移速 0.21（原版 0.23）。
            helper.assertTrue(Math.abs(archer.getMaxHealth() - 16.0F) < 1.0E-4F,
                    "生命上限应当是 16（比原版僵尸的 20 更脆），实际 " + archer.getMaxHealth());
            double speed = archer.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            helper.assertTrue(Math.abs(speed - 0.21D) < 1.0E-4D,
                    "移速应当是 0.21，实际 " + speed);
            // 基础行为沿用普通僵尸：它确实就是原版 Zombie 的子类，因此日光燃烧、火焰额外伤害、
            // 开门、水中腐化这些都由原版实现继承下来（本类没有覆写 isSunSensitive/aiStep 的日光段）。
            helper.assertTrue(archer instanceof net.minecraft.world.entity.monster.Zombie,
                    "射手僵尸必须直接继承原版僵尸，才能沿用阳光/火焰克制等基础行为");

            // 掉落沿用原版僵尸战利品表（腐肉 + 稀有铁锭/胡萝卜/土豆）。
            ResourceKey<LootTable> expectedTable = ResourceKey.create(
                    Registries.LOOT_TABLE,
                    ResourceLocation.withDefaultNamespace("entities/zombie"));
            helper.assertTrue(expectedTable.equals(archer.getLootTable()),
                    "掉落应当沿用原版僵尸战利品表，实际 " + archer.getLootTable());

            // 受击与死亡：玩家造成的致命伤必须真的把它打死。
            archer.hurt(player.damageSources().playerAttack(player), 1000.0F);
            helper.assertTrue(!archer.isAlive(), "被玩家致命一击后射手僵尸应当死亡");
        } finally {
            if (archer != null) {
                archer.discard();
            }
            dispose(player);
        }
        helper.succeed();
    }

    /**
     * 流血只在玩家"按着 WASD 在动"时结算：站着不动等于压迫止血。
     *
     * <p>位移信号来自 {@link PlayerMotionTracker}（{@code PlayerTickEvent.Post} 采样上一 tick 的
     * 水平位移）。这里用"每 tick 推 0.12 格"模拟持续走动——正常行走约 0.2 格/tick，0.12 稳妥地
     * 落在阈值之内，又不会把玩家推出平台。</p>
     */
    @GameTest(template = "empty")
    public static void bleedingOnlyTicksWhileThePlayerMoves(GameTestHelper helper) {
        buildPlatform(helper);
        ServerPlayer player = spawnPlayer(helper, new BlockPos(6, PLATFORM_Y + 1, 2));
        ArcherZombieEntity archer = null;
        try {
            archer = spawnArcher(helper, new BlockPos(2, PLATFORM_Y + 1, 2));
            archer.setNoAi(true);

            // 站定后采样器应当判"没在动"（spawnPlayer 里已经跑过足够多的 tick 让它稳定）。
            tickPlayer(player);
            tickPlayer(player);
            helper.assertTrue(!PlayerMotionTracker.isMoving(player.getUUID()),
                    "站定的玩家不应被判定为移动中");

            player.addEffect(new MobEffectInstance(
                    DreamingFishCore_Effects.BLEEDING, 20 * 30, 0), archer);
            helper.assertTrue(player.hasEffect(DreamingFishCore_Effects.BLEEDING),
                    "流血效果应当成功挂上（addEffect 判定失败会让整条用例失去意义）");

            float stationaryBefore = player.getHealth();
            int stationaryMovingTicks = 0;
            for (int i = 0; i < 40; i++) {
                tickPlayer(player);
                if (PlayerMotionTracker.isMoving(player.getUUID())) {
                    stationaryMovingTicks++;
                }
            }
            helper.assertTrue(player.getHealth() >= stationaryBefore,
                    "站着不动时流血不应结算，实际血量 " + player.getHealth()
                            + " / " + stationaryBefore
                            + "（这 40 tick 里被判定为移动的次数 " + stationaryMovingTicks + "）");

            // 模拟持续走动：每 tick 推 0.12 格，采样器就会一直判定为移动。
            float walkingBefore = player.getHealth();
            int walkingMovingTicks = 0;
            for (int i = 0; i < 40; i++) {
                player.setPos(player.getX() + 0.12D, player.getY(), player.getZ());
                tickPlayer(player);
                if (PlayerMotionTracker.isMoving(player.getUUID())) {
                    walkingMovingTicks++;
                }
            }
            helper.assertTrue(player.getHealth() < walkingBefore,
                    "持续移动时流血应当结算，实际血量 " + player.getHealth()
                            + " / " + walkingBefore
                            + "（这 40 tick 里被判定为移动的次数 " + walkingMovingTicks
                            + "，剩余持续时间 "
                            + player.getEffect(DreamingFishCore_Effects.BLEEDING) + "）");
        } finally {
            if (archer != null) {
                archer.discard();
            }
            dispose(player);
        }
        helper.succeed();
    }

    /**
     * 骨刺伤害按**原版难度**（和平/简单/普通/困难）缩放，倍率取自 {@code archer_zombie.json}。
     *
     * <p>断言的是"实际掉血 == 基础伤害 × 当前难度倍率"，所以不管 gametest 世界当时是哪个难度都成立；
     * 倍率本身的映射关系（简单 0.75 / 普通 1.0 / 困难 1.5）由
     * {@code ArcherZombieConfigTest} 的单元测试逐档钉住。</p>
     */
    @GameTest(template = "empty")
    public static void boneSpikeDamageFollowsTheConfiguredDifficultyMultiplier(GameTestHelper helper) {
        buildPlatform(helper);
        ServerLevel level = helper.getLevel();
        ArcherZombieEntity archer = null;
        BoneSpikeEntity spike = null;
        Cow victim = null;
        try {
            archer = spawnArcher(helper, new BlockPos(2, PLATFORM_Y + 1, 2));
            archer.setNoAi(true);
            victim = helper.spawn(EntityType.COW, new BlockPos(6, PLATFORM_Y + 1, 2));

            // 关掉散布与重力：只测伤害换算，不掺弹道随机。
            ArcherZombieConfig.Resolved deterministic = new ArcherZombieConfig.Resolved(
                    true, true, 40, 16.0D, 0.21D, 35.0D, 2.5D, 6.0D, 16.0D, 22, 40,
                    4.0D, 1.6D, 0.0D, 0.0D, 24.0D, true, 60, 0, true, 100, 0);

            spike = new BoneSpikeEntity(level, archer, deterministic);
            Vec3 from = spike.position();
            Vec3 to = victim.getEyePosition();
            spike.shoot(to.x - from.x, to.y - from.y, to.z - from.z, 1.6F, 0.0F);
            level.addFreshEntity(spike);

            float healthBefore = victim.getHealth();
            for (int i = 0; i < MAX_PROJECTILE_TICKS && !spike.isRemoved(); i++) {
                spike.tick();
            }
            float dealt = healthBefore - victim.getHealth();

            double expected = 4.0D * ArcherZombieConfig.damageMultiplierFor(level.getDifficulty());
            helper.assertTrue(Math.abs(dealt - expected) < 0.01D,
                    "骨刺伤害应当等于基础伤害 × 当前难度倍率；当前难度 " + level.getDifficulty()
                            + "，倍率 " + ArcherZombieConfig.damageMultiplierFor(level.getDifficulty())
                            + "，期望 " + expected + "，实际掉血 " + dealt
                            + "（骨刺已消散 " + spike.isRemoved() + "）");
        } finally {
            if (spike != null) {
                spike.discard();
            }
            if (victim != null) {
                victim.discard();
            }
            if (archer != null) {
                archer.discard();
            }
        }
        helper.succeed();
    }

    /** 在相对 y=11 铺一条石头平台，让双方都站在脱离地形的同一平面上。 */
    private static void buildPlatform(GameTestHelper helper) {
        for (int x = 0; x <= 16; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, PLATFORM_Y, z), Blocks.STONE);
            }
        }
    }

    private static ArcherZombieEntity spawnArcher(GameTestHelper helper, BlockPos relativePos) {
        ArcherZombieEntity archer = helper.spawn(ArcherZombieEntities.ARCHER_ZOMBIE.get(), relativePos);
        // 平台仍在露天：戴顶帽子，把「日光燃烧」这个与 AI 无关的变量消掉。
        archer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        archer.setPersistenceRequired();
        return archer;
    }

    /** 从场上找一发骨刺；找不到返回 null。 */
    private static BoneSpikeEntity findSpike(GameTestHelper helper, ArcherZombieEntity archer) {
        List<BoneSpikeEntity> spikes = helper.getLevel().getEntitiesOfClass(
                BoneSpikeEntity.class, archer.getBoundingBox().inflate(64.0D));
        return spikes.isEmpty() ? null : spikes.get(0);
    }

    private static String describeLineOfSight(
            GameTestHelper helper, ArcherZombieEntity archer, ServerPlayer player) {
        Vec3 from = archer.getEyePosition();
        Vec3 to = player.getEyePosition();
        HitResult hit = helper.getLevel().clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, archer));
        return "from=" + from + " to=" + to
                + " clip=" + hit.getType() + "@" + hit.getLocation()
                + " blockAtArcherEye=" + helper.getLevel().getBlockState(BlockPos.containing(from))
                + " blockAtPlayerEye=" + helper.getLevel().getBlockState(BlockPos.containing(to));
    }

    /**
     * 造一个"真的能被打"的模拟生存玩家，并直接站到指定位置。
     *
     * <p>要清掉三层拦路的东西，否则后面所有伤害断言都会被静默吃掉：</p>
     * <ol>
     *   <li>{@code makeMockServerPlayerInLevel()} 把 {@code isCreative()} 硬编码为 true，
     *       于是能力位 {@code abilities.invulnerable} 为真（{@code Player#hurt} 直接返回 false），
     *       并且 {@code Player#canBeSeenAsEnemy} 为假（丧尸 {@code canAttack} 判否、当不了猎物）；</li>
     *   <li>实体自身的 {@code invulnerable} 标志；</li>
     *   <li>{@code ServerPlayer} 的私有 {@code spawnInvulnerableTime}（默认 60）：新玩家出生后
     *       60 tick 内 {@code hurt} 一律返回 false，且没有公开 setter，只能把这 60 tick 跑完。</li>
     * </ol>
     */
    private static ServerPlayer spawnPlayer(GameTestHelper helper, BlockPos relativePos) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        AuthSessionGuard.markAuthenticated(player);
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().invulnerable = false;
        player.getAbilities().instabuild = false;
        player.getAbilities().mayfly = false;
        player.setInvulnerable(false);
        teleportPlayer(helper, player, relativePos);
        for (int i = 0; i < SPAWN_INVULNERABLE_TICKS; i++) {
            tickPlayer(player);
        }
        return player;
    }

    private static String describeTarget(ArcherZombieEntity archer) {
        return archer.getTarget() == null ? "null" : archer.getTarget().getName().getString();
    }

    /**
     * 完整推进一次"服务端玩家的一个游戏 tick"。
     *
     * <p>必须两个都调，少一个就会静默漏掉半条链路：</p>
     * <ul>
     *   <li>{@code tick()}：玩家记账（容器菜单、出生无敌帧 {@code spawnInvulnerableTime}），
     *       由世界的实体 tick 列表驱动；</li>
     *   <li>{@code doTick()}：真正的实体 tick（{@code super.tick()}），效果结算与
     *       {@code PlayerTickEvent.Pre/Post} 都在这里——流血判定的移动采样就挂在 Post 上。
     *       原版里它由 {@code ServerGamePacketListenerImpl.tick()} 调用。</li>
     * </ul>
     * <p>只调 {@code tick()} 的话，效果持续时间不会走、移动采样也不会更新，
     * 断言会以"什么都没发生"的形式假绿/假红。</p>
     */
    private static void tickPlayer(ServerPlayer player) {
        player.tick();
        player.doTick();
    }

    private static void teleportPlayer(GameTestHelper helper, ServerPlayer player, BlockPos relativePos) {
        BlockPos absolute = helper.absolutePos(relativePos);
        player.teleportTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
    }

    /**
     * 把模拟玩家从服务器上摘掉。gametest 共用同一个测试世界与 {@code PlayerList}，
     * 留下一个就会污染后面用例的"附近有没有玩家"扫描。
     */
    private static void dispose(ServerPlayer player) {
        if (player != null && player.getServer() != null) {
            player.getServer().getPlayerList().remove(player);
        }
    }
}
