package com.hhy.dreamingfishcore.gameplay.zombie_system.boss;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.zombie_system.archer.BoneSpikeEntity;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 尸潮指挥官的端到端 gametest：弹丸命中、战斗 Goal 真的会开火、召唤配额、半血二阶段、
 * 护卫寿命与随指挥官同灭。
 *
 * <p>这里只断言**接线与方向**（有没有弹丸、有没有掉血、有没有封顶），精确倍率与阈值边界交给
 * {@code ZombieCommanderRulesTest} 的纯函数用例 —— 理由与金刚僵尸那份相同：实体伤害还要过一遍
 * 原版护甲与无敌帧公式，硬在这里算浮点尾数会让用例变成「公式改了就得改测试」。</p>
 *
 * <p><b>凡是「需要时间流逝」的断言，一律交给游戏自己 tick</b>（{@link GameTestHelper#succeedWhen}），
 * 而不是在一次调用里循环 {@code entity.tick()}：实体自己的 tick 不会推进
 * {@code level.getGameTime()}，而护卫到期、弹丸飞行这类逻辑读的正是游戏时间。同理，Boss 的
 * 阶段切换与冷却递减都写在 {@code !isNoAi()} 分支里，需要它们的用例必须显式 {@code setNoAi(false)}。</p>
 *
 * <p>坐标有两套：{@code helper.setBlock/spawn} 收**相对**坐标，而世界里的位置（瞄准、AABB、
 * {@code level.getEntitiesOfClass}）必须是**绝对**坐标，换算统一走 {@code helper.absolutePos}。</p>
 */
@GameTestHolder(DreamingFishCore.MODID)
@PrefixGameTestTemplate(false)
public class ZombieCommanderGameTest {
    /** 平台层：实体站在 platformY + 1 上。 */
    private static final int PLATFORM_Y = 11;
    /** 竞技场边长；必须明显大于「期望最小交战距离」，否则 Boss 后退走位会被挤下平台。 */
    private static final int ARENA = 20;
    /** Boss 站一角，目标站另一侧，初始间距约 10 格（大于配置的期望最小距离 8）。 */
    private static final BlockPos COMMANDER_RELATIVE = new BlockPos(6, PLATFORM_Y + 1, 6);
    private static final BlockPos TARGET_RELATIVE = new BlockPos(16, PLATFORM_Y + 1, 6);
    /**
     * 弹丸命中用例的靶子：贴着 Boss 放（1 格）。
     *
     * <p>贴这么近有两个原因，都是被实测逼出来的：</p>
     * <ul>
     *   <li>gametest 的所有结构共享同一个世界，别的用例的模拟玩家可能正好站在弹道上，
     *       骨刺会先打中它然后消散（症状是「弹丸在离目标 1.86 格处消散、目标一点血没掉」）。
     *       弹道短到 1 格就塞不下第三方实体。</li>
     *   <li>弹道短，这一发就不会飞过区块边界 —— 见 {@link #buildArena} 里「模板必须覆盖整个
     *       场地」的说明；把飞行距离压在 1 格内，这条用例就额外多一层不依赖区块状态的保障。</li>
     * </ul>
     */
    private static final BlockPos NEAR_TARGET_RELATIVE = new BlockPos(7, PLATFORM_Y + 1, 6);
    /** 召唤/配额类用例把 Boss 放在场地中央，方便四周找落脚点。 */
    private static final BlockPos CENTER_RELATIVE = new BlockPos(10, PLATFORM_Y + 1, 10);
    private static final BlockPos GUARD_RELATIVE = new BlockPos(8, PLATFORM_Y + 1, 8);
    /** ServerPlayer 出生后的私有无敌帧长度（默认 60）；跑完它玩家才是正常状态。 */
    private static final int SPAWN_INVULNERABLE_TICKS = 70;

    /**
     * 弹丸链路：指挥官发射的骨刺带着正确的伤害（含难度倍率），命中后确实扣血。
     *
     * <p>靶子放在 5 格内、并把飞行交给游戏自己 tick（{@code succeedWhen}）。这条要验证的是
     * 「fireAt 造出来的这一发能打成伤害」，而不是投掷物的扫掠命中精度 —— 后者是
     * {@code BoneSpikeEntity} 的既有行为（射手僵尸那边已覆盖），在这里复现只会把用例的成败
     * 绑到测试环境的时序上（实测在 10 格外确有偶发）。</p>
     */
    @GameTest(template = "commander_arena", timeoutTicks = 200)
    public static void commanderFiresABoneSpikeThatDamagesTheTarget(GameTestHelper helper) {
        buildArena(helper);
        ZombieCommanderEntity commander = spawnCommander(helper, COMMANDER_RELATIVE, true);
        ServerPlayer target = spawnTarget(helper, NEAR_TARGET_RELATIVE);

        // 前置探针：先确认这个模拟玩家本身是会被打掉血的，不然后面万一失败就分不清是
        // 「玩家不可受伤」还是「弹丸没造成伤害」。
        float probeBefore = target.getHealth();
        target.hurt(target.damageSources().generic(), 1.0F);
        helper.assertTrue(target.getHealth() < probeBefore,
                "前置：模拟玩家应当能被普通伤害打到掉血（当前血量 " + target.getHealth()
                        + "，可被投射物命中=" + target.canBeHitByProjectile()
                        + "，旁观=" + target.isSpectator()
                        + "，invulnerable=" + target.getAbilities().invulnerable + "）");
        // 探针那一下会给玩家 20 tick 受击无敌帧，而靶子就在 1 格外、骨刺一两个 tick 就飞到了 ——
        // 不清掉的话整发都会被无敌帧吞掉（表现是"弹丸贴到身上消散了、人一点血不掉"）。
        target.invulnerableTime = 0;
        target.hurtTime = 0;

        float before = target.getHealth();
        commander.fireAt(target, commander.runtimeSettings());
        List<BoneSpikeEntity> spikes = spikesOf(helper, commander);
        helper.assertTrue(!spikes.isEmpty(), "fireAt 应当把骨刺放进世界");
        BoneSpikeEntity spike = spikes.get(0);

        // 手动把这一发驱动到命中，不交给世界 tick。骨刺的飞行只用自身的 delta movement + 重力 +
        // 自带的扫掠命中，不读 gameTime，所以手动 tick 与真实飞行等价；而交给世界 tick 会让这条
        // 用例的成败取决于区块加载状态（见 NEAR_TARGET_RELATIVE 的注释）。
        for (int i = 0; i < 20 && target.getHealth() >= before && !spike.isRemoved(); i++) {
            spike.tick();
        }

        helper.assertTrue(target.getHealth() < before,
                "指挥官发射的骨刺命中后应当扣血：命中前 " + before + "，现在 " + target.getHealth()
                        + "（弹丸已消散=" + spike.isRemoved()
                        + "，弹丸位置=" + spike.position()
                        + "，距目标 " + spike.distanceTo(target)
                        + "，配置弹丸伤害=" + commander.runtimeSettings().projectileDamage()
                        + "，难度=" + helper.getLevel().getDifficulty()
                        + "，难度倍率=" + ZombieCommanderRules.projectileDamageMultiplier(
                                helper.getLevel().getDifficulty().getId()) + "）");
        // 这里不用 succeedWhen：断言已经在同一次调用里做完了，不需要框架再逐 tick 驱动。
        helper.succeed();
    }

    /**
     * 战斗 Goal 端到端：给 Boss 一个射程内的目标，它应当走完「蓄力 → 连发」把整轮弹幕打出来。
     *
     * <p>这条只验证 <b>AI 决策 + 施法状态机</b> 这一层：是否真的走到发射、是否按配置的每轮弹数打出。
     * 「弹丸能不能命中、命中掉多少血」是弹丸自己的职责，已经由
     * {@link #commanderFiresABoneSpikeThatDamagesTheTarget} 单独覆盖 —— 在这里再断一次命中，
     * 只会把弹道这个正交维度混进来，让用例的成败取决于两种 tick 驱动方式的叠加。</p>
     *
     * <p>先断言 {@code canUse()}，把「目标 / 距离 / 可攻击 / 可达」这层调度条件验证掉；随后手动驱动
     * Goal，<b>但不 tick 弹丸</b>：让它们停在发射点，就能直接数出一轮打了几发。之所以不用
     * {@code goalSelector} 自己跑，是因为原版 {@code Mob#serverAiStep} 会按 {@code tickCount + id}
     * 的奇偶把 Goal 的 tick 节流成每两游戏刻一次，会让用例的成败取决于测试环境的 tick 节奏。</p>
     */
    @GameTest(template = "commander_arena")
    public static void combatGoalCastsAndFiresItsFullBurstAtAnInRangeTarget(GameTestHelper helper) {
        buildArena(helper);
        ZombieCommanderEntity commander = spawnCommander(helper, COMMANDER_RELATIVE, true);
        ServerPlayer target = spawnTarget(helper, TARGET_RELATIVE);
        try {
            commander.setTarget(target);
            ZombieCommanderCombatGoal goal = new ZombieCommanderCombatGoal(commander);
            helper.assertTrue(goal.canUse(),
                    "有存活且在射程内的目标时，战斗 Goal 应当进入调度"
                            + "（距离 " + commander.distanceTo(target)
                            + "，索敌 " + commander.runtimeSettings().followRange()
                            + "，视线 " + commander.getSensing().hasLineOfSight(target)
                            + "，可攻击 " + commander.canAttack(target) + "）");

            goal.start();
            int burst = commander.runtimeSettings().burstCount();
            // 数「累计打出的不同弹丸」，而不是「某一刻同时在场的弹丸数」：弹丸会飞出去、
            // 打到就会消散，用后者去断言等于要求「三发必须同时在空中」——
            // 那取决于弹速与目标的距离，是在测环境，不是测连发。
            java.util.Set<java.util.UUID> fired = new java.util.LinkedHashSet<>();
            for (int i = 0; i < 200 && fired.size() < burst; i++) {
                commander.tick();
                goal.tick();
                for (BoneSpikeEntity spike : spikesOf(helper, commander)) fired.add(spike.getUUID());
            }

            int firedCount = fired.size();
            helper.assertTrue(firedCount >= burst,
                    "战斗 Goal 应当走完蓄力（" + commander.runtimeSettings().chargeTicks()
                            + " tick）并打出一轮 " + burst + " 发骨刺，实际累计打出 " + firedCount + " 发"
                            + "（施法状态 " + commander.getCastState()
                            + "，攻击冷却 " + commander.attackCooldown()
                            + "，弹间隔 " + commander.runtimeSettings().burstSpacingTicks() + " tick"
                            // 冷却是 50 只可能来自「三发打完」，也就是说 fireAt 确实被调了三次；
                            // 这时还数不到弹丸，只可能是「Boss 已被移出关卡（fireAt 早退）」或
                            // 「弹丸不在实体查询能看到的区块里」。把这两项带进失败信息，
                            // 下次它再红就能直接定位，不用重跑一遍复现。
                            + "；Boss 是否被移除 " + commander.isRemoved()
                            + "，Boss 是否还在关卡实体表 "
                            + (helper.getLevel().getEntities().get(commander.getUUID()) != null)
                            + "，Boss 血量 " + commander.getHealth()
                            + "，场地内骨刺总数（不按 owner 过滤）"
                            + helper.getLevel().getEntitiesOfClass(BoneSpikeEntity.class,
                                    new AABB(commander.position(), commander.position())
                                            .inflate(128.0D)).size() + "）");
        } finally {
            commander.discard();
            target.discard();
        }
        helper.succeed();
    }

    /**
     * 召唤配额：无论请求多少次，Boss 守住的护卫数都不会超过配置上限，而且确实召得出来。
     *
     * <p>刻意只断言这条<b>不变量</b>，不追加「额度用尽时必须返回 0」：后者等价于要求
     * 「存量恰好顶满」，而 gametest 的所有结构共享同一个世界，存量随时可能因为别的用例或
     * 环境回收掉一只而差一格 —— 那是在测环境，不是在测规则。配额计算本身
     * （含 cap 用尽返回 0 的边界）由 {@code ZombieCommanderRulesTest} 的纯函数用例负责。</p>
     */
    @GameTest(template = "commander_arena")
    public static void summonsAreCappedByTheActiveMinionLimit(GameTestHelper helper) {
        buildArena(helper);
        ZombieCommanderEntity commander = spawnCommander(helper, CENTER_RELATIVE, true);
        try {
            ZombieCommanderConfig.Resolved settings = commander.runtimeSettings();
            int cap = settings.maxMinions();

            for (int i = 0; i < cap * 3; i++) {
                commander.summonGuards(settings);
                int active = commander.activeMinionCount();
                helper.assertTrue(active <= cap,
                        "第 " + (i + 1) + " 次召唤后存量 " + active + " 超过了上限 " + cap);
            }

            helper.assertTrue(commander.activeMinionCount() >= 1,
                    "反复召唤之后 Boss 至少应当守住一只护卫，实际 "
                            + commander.activeMinionCount() + "（上限 " + cap + "）");
        } finally {
            commander.discard();
        }
        helper.succeed();
    }

    /**
     * 半血二阶段：略高于阈值不切换（反向对照），到阈值才狂暴，并让 Boss 条换色。
     *
     * <p>必须 {@code setNoAi(false)}：阶段切换写在 {@code tick()} 的 {@code !isNoAi()} 分支里，
     * 关掉 AI 的话这条用例会变成「怎么都不狂暴」的假绿灯。</p>
     */
    @GameTest(template = "commander_arena")
    public static void enrageSwitchesOnAtTheHalfHealthThreshold(GameTestHelper helper) {
        buildArena(helper);
        ZombieCommanderEntity commander = spawnCommander(helper, COMMANDER_RELATIVE, false);
        try {
            float max = commander.getMaxHealth();
            helper.assertTrue(commander.bossEvent().getColor() == BossEvent.BossBarColor.PURPLE,
                    "未狂暴时 Boss 条应当是紫色");

            double threshold = commander.runtimeSettings().enrageHealthFraction();
            // 反向对照：血量还在阈值之上时不该切阶段。
            commander.setHealth(max * (float) (threshold + 0.1D));
            commander.tick();
            helper.assertTrue(!commander.isEnraged(),
                    "生命 " + (threshold + 0.1D) * 100.0D + "%（高于阈值 " + threshold + "）时不该狂暴");

            commander.setHealth(max * (float) threshold);
            commander.tick();
            helper.assertTrue(commander.isEnraged(),
                    "生命降到阈值 " + threshold + " 时应当进入狂暴二阶段"
                            + "（当前血量 " + commander.getHealth() + "/" + max + "）");
            helper.assertTrue(commander.bossEvent().getColor() == BossEvent.BossBarColor.RED,
                    "狂暴后 Boss 条应当变红");
            helper.assertTrue(Math.abs(commander.bossEvent().getProgress() - (float) threshold) < 0.01F,
                    "Boss 条进度应当跟着血量走，实际 " + commander.bossEvent().getProgress());
            helper.assertTrue(commander.summonCooldown() <= 40,
                    "进入狂暴应当立刻缩短召唤冷却，实际 " + commander.summonCooldown());
        } finally {
            commander.discard();
        }
        helper.succeed();
    }

    /** 护卫寿命：绑定短寿命后，到点必须自己消散，不能留在场上。 */
    @GameTest(template = "commander_arena", timeoutTicks = 200)
    public static void minionsExpireAfterTheirBindLifetime(GameTestHelper helper) {
        buildArena(helper);
        ZombieCommanderEntity commander = spawnCommander(helper, COMMANDER_RELATIVE, true);
        CommanderMinionEntity guard = helper.spawn(
                ZombieCommanderEntities.COMMANDER_MINION.get(), GUARD_RELATIVE);
        guard.bindTo(commander, 20);

        // 20 tick 的寿命读的是游戏时间，必须让游戏真的 tick 过去。
        helper.succeedWhen(() -> helper.assertTrue(guard.isRemoved(),
                "护卫应当在 20 tick 寿命结束后消散"
                        + "（还活着=" + guard.isAlive()
                        + "，已移除=" + guard.isRemoved()
                        + "，游戏时间=" + helper.getLevel().getGameTime() + "）"));
    }

    /** 指挥官阵亡或消失时，场上由它召唤的护卫必须一起清掉，不能变成野怪。 */
    @GameTest(template = "commander_arena")
    public static void commanderRemovalDiscardsItsSummonedGuards(GameTestHelper helper) {
        buildArena(helper);
        ZombieCommanderEntity commander = spawnCommander(helper, CENTER_RELATIVE, true);
        try {
            helper.assertTrue(commander.summonGuards(commander.runtimeSettings()) > 0,
                    "用例前提：至少要能召唤出一只护卫");
            helper.assertTrue(commander.activeMinionCount() > 0,
                    "召唤之后 Boss 记录的存活护卫不应当为空");

            commander.discard();

            // 台账只保留「还没被移除」的护卫，所以它归零等价于那些护卫确实已经消散。
            helper.assertTrue(commander.activeMinionCount() == 0,
                    "指挥官被移除后，它召唤的护卫应当一起消散；台账里还剩 "
                            + commander.activeMinionCount() + " 只");
        } finally {
            commander.discard();
        }
        helper.succeed();
    }

    /** 与原版僵尸的关系：属性差异化、不捡装备、不自燃、掉落与经验沿用配置值。 */
    @GameTest(template = "commander_arena")
    public static void commanderKeepsVanillaZombieTraitsWithoutDaylightBurning(GameTestHelper helper) {
        buildArena(helper);
        ZombieCommanderEntity commander = spawnCommander(helper, COMMANDER_RELATIVE, true);
        try {
            helper.assertTrue(commander instanceof Zombie, "尸潮指挥官必须直接继承原版僵尸");
            helper.assertTrue(
                    Math.abs(commander.getMaxHealth() - (float) commander.runtimeSettings().maxHealth())
                            < 1.0E-3F,
                    "生命上限应当跟随配置 " + commander.runtimeSettings().maxHealth()
                            + "，实际 " + commander.getMaxHealth());
            helper.assertTrue(!commander.canPickUpLoot(), "Boss 不该捡装备");
            helper.assertTrue(!commander.burnsInDaylight(),
                    "Boss 不该在白天自燃：那会让玩家可以不打它、只等天亮");
            helper.assertTrue(commander.experienceReward() == commander.runtimeSettings().experienceReward(),
                    "击杀经验应当跟随配置 " + commander.runtimeSettings().experienceReward()
                            + "，实际 " + commander.experienceReward());

            ResourceKey<LootTable> expectedTable = ResourceKey.create(
                    Registries.LOOT_TABLE,
                    ResourceLocation.withDefaultNamespace("entities/zombie"));
            helper.assertTrue(expectedTable.equals(commander.getLootTable()),
                    "掉落应当沿用原版僵尸战利品表，实际 " + commander.getLootTable());
        } finally {
            commander.discard();
        }
        helper.succeed();
    }


    /**
     * 近战三段连招端到端：手动驱动战斗 Goal，三段应当依次打完，且总伤害 = 基础伤害 × 三段倍率之和。
     *
     * <p>刻意手动驱动而不是交给 {@code goalSelector}：原版 {@code Mob#serverAiStep} 会按
     * {@code tickCount + id} 的奇偶把 Goal 的 tick 节流成每两刻一次，交给它跑这篇用例的成败
     * 就要看测试环境的节奏了 —— 而这里要验证的是连招状态机本身。</p>
     */
    @GameTest(template = "commander_arena")
    public static void meleeComboChainsThreeStepsAndEscalatesDamage(GameTestHelper helper) {
        buildArena(helper);
        ZombieCommanderEntity commander = spawnCommander(helper, COMMANDER_RELATIVE, true);
        ServerPlayer target = spawnTarget(helper, NEAR_TARGET_RELATIVE);
        try {
            // 三段全中是基础伤害的 3.8 倍，玩家 20 点血扛不住，先把血上限顶上去再打。
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400.0D);
            target.setHealth(400.0F);
            commander.setTarget(target);
            ZombieCommanderCombatGoal goal = new ZombieCommanderCombatGoal(commander);
            helper.assertTrue(goal.canUse(),
                    "贴到 " + commander.distanceTo(target) + " 格时战斗 Goal 应当进入调度");

            float before = target.getHealth();
            int maxStep = 0;
            // 三段总长 10 + 10 + 16 = 36 tick；连招后的冷却是 40 tick，所以跑 50 tick 只会打完一轮。
            for (int i = 0; i < 50; i++) {
                goal.tick();
                maxStep = Math.max(maxStep, commander.getMeleeStep());
            }

            helper.assertTrue(maxStep == ZombieCommanderRules.MELEE_STEPS,
                    "三段连招应当打到第 " + ZombieCommanderRules.MELEE_STEPS + " 段，实际最高 " + maxStep);
            helper.assertTrue(commander.getMeleeStep() == 0,
                    "一轮打完之后应当退出连招状态，实际 " + commander.getMeleeStep());
            float expected = (float) (commander.getAttributeValue(Attributes.ATTACK_DAMAGE)
                    * ZombieCommanderRules.meleeComboTotalMultiplier()
                    * ZombieCommanderRules.projectileDamageMultiplier(
                            helper.getLevel().getDifficulty().getId()));
            float dealt = before - target.getHealth();
            helper.assertTrue(Math.abs(dealt - expected) < 0.05F,
                    "三段总伤害应当是 " + expected + "（基础 × 3.8），实际 " + dealt
                            + "（目标 " + before + " -> " + target.getHealth() + "）");
        } finally {
            commander.discard();
            target.discard();
        }
        helper.succeed();
    }

    /** 目标被第一刀推开之后，连招必须停在第一段并进入冷却，不能隔着几格继续挥。 */
    @GameTest(template = "commander_arena")
    public static void meleeComboStopsWhenTheTargetLeavesRange(GameTestHelper helper) {
        buildArena(helper);
        ZombieCommanderEntity commander = spawnCommander(helper, COMMANDER_RELATIVE, true);
        ServerPlayer target = spawnTarget(helper, NEAR_TARGET_RELATIVE);
        try {
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400.0D);
            target.setHealth(400.0F);
            commander.setTarget(target);
            ZombieCommanderCombatGoal goal = new ZombieCommanderCombatGoal(commander);

            float before = target.getHealth();
            int maxStep = 0;
            boolean pushed = false;
            for (int i = 0; i < 50; i++) {
                goal.tick();
                maxStep = Math.max(maxStep, commander.getMeleeStep());
                if (!pushed && target.getHealth() < before) {
                    // 第一刀命中之后立刻把目标挪走，模拟"被击退/跑掉了"
                    target.teleportTo(commander.getX() + 12.0D, commander.getY(), commander.getZ());
                    pushed = true;
                }
            }

            helper.assertTrue(pushed, "第一刀应当命中目标");
            helper.assertTrue(maxStep == 1, "目标离开后不应继续接第二段，实际最高 " + maxStep);
            float dealt = before - target.getHealth();
            float oneHit = (float) (commander.getAttributeValue(Attributes.ATTACK_DAMAGE)
                    * ZombieCommanderRules.meleeDamageMultiplier(1)
                    * ZombieCommanderRules.projectileDamageMultiplier(
                            helper.getLevel().getDifficulty().getId()));
            helper.assertTrue(Math.abs(dealt - oneHit) < 0.05F,
                    "只应当结算第一刀 " + oneHit + " 点，实际 " + dealt);
        } finally {
            commander.discard();
            target.discard();
        }
        helper.succeed();
    }

    private static ZombieCommanderEntity spawnCommander(GameTestHelper helper, BlockPos relative, boolean noAi) {
        ZombieCommanderEntity commander =
                helper.spawn(ZombieCommanderEntities.ZOMBIE_COMMANDER.get(), relative);
        // 大部分用例只想验证机制本身，关掉 AI 免得它自己走动；需要 AI 的用例显式传 false。
        commander.setNoAi(noAi);
        return commander;
    }

    /**
     * 靶子用模拟玩家而不是村民/装甲架：
     * <ul>
     *   <li>装甲架的 {@code hurt} 只认 {@code CAN_BREAK_ARMOR_STAND} /
     *       {@code ALWAYS_KILLS_ARMOR_STANDS} 那几个标签，而骨刺走的是自定义伤害类型
     *       {@code dreamingfishcore:bone_spike} —— 打上去直接返回 false，一点血都不掉，
     *       拿它当靶子会把用例变成永远绿的假绿灯；</li>
     *   <li>村民没有任何持久化标记，会跟着其它用例留下的模拟玩家被判为「远离玩家」而
     *       被原版 {@code checkDespawn} 直接丢掉，靶子半路消失；</li>
     *   <li>{@link ServerPlayer} 既不会 despawn，受伤也只受 {@code abilities.invulnerable}
     *       约束（没有创造模式豁免），跑完出生无敌帧就是个正常会掉血的玩家。</li>
     * </ul>
     */
    private static ServerPlayer spawnTarget(GameTestHelper helper, BlockPos relative) {
        ServerPlayer target = helper.makeMockServerPlayerInLevel();
        AuthSessionGuard.markAuthenticated(target);
        target.setGameMode(GameType.SURVIVAL);
        target.getAbilities().invulnerable = false;
        target.getAbilities().instabuild = false;
        target.getAbilities().mayfly = false;
        target.setInvulnerable(false);

        BlockPos absolute = helper.absolutePos(relative);
        target.moveTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        // 出生自带 60 tick 无敌帧，跑完它才是个会被打掉血的正常玩家。
        for (int i = 0; i < SPAWN_INVULNERABLE_TICKS; i++) {
            target.tick();
            target.doTick();
        }
        return target;
    }

    /**
     * 取这只 Boss 自己发射的骨刺。
     *
     * <p><b>必须按 owner 过滤。</b>gametest 的所有结构共享同一个世界，同一批次里可能有好几只
     * 指挥官同时在跑，而 {@code getEntitiesOfClass} 只看坐标范围 —— 直接用「附近有没有骨刺」
     * 当判据会捡到别人的弹丸（甚至 tick 了别人的、自己的却纹丝不动），用例的成败就悄悄变成了
     * 「同一批次里还有谁在跑」。这个坑表现为时红时绿，且失败信息里弹丸终点落在射程边界上。</p>
     */
    private static List<BoneSpikeEntity> spikesOf(GameTestHelper helper, ZombieCommanderEntity commander) {
        Vec3 at = commander.position();
        return helper.getLevel()
                .getEntitiesOfClass(BoneSpikeEntity.class, new AABB(at, at).inflate(128.0D))
                .stream()
                .filter(spike -> spike.getOwner() == commander)
                .toList();
    }

    /**
     * 铺一层 ARENA × ARENA 的石台，让 Boss 与目标站在脱离地形的同一平面上。
     *
     * <p><b>场地覆盖的区块由结构模板负责强制加载</b>：本类的 {@code @GameTest} 统一用自带的
     * {@code dreamingfishcore:commander_arena}（24×24 的空模板），框架在测试开始前就会把它
     * 包围盒相交的区块全部 {@code setChunkForced}。这里<b>不能</b>改回 {@code "empty"}：</p>
     *
     * <p>原版空模板只有一格大，{@code StructureUtils.forceLoadChunks} 于是只强制加载结构原点
     * 所在的那<b>一个</b>区块，而原点落在区块内的偏移是随机的（每次跑都不同），场地必定横跨两个
     * 区块；隔壁那个区块只是被 {@code setBlock} 顺带加载进来、{@code chunkVisibility} 里没有登记
     * （登记由 {@code ChunkMap} 在每个 tick 回调写入）。落到那里的实体会被当成「不可见 / 不可
     * tick」——实测症状是 {@code addFreshEntity} 返回 true，但 {@code ServerLevel#getEntity} 与
     * {@code getEntitiesOfClass} 都查不到它（于是护卫台账误判护卫已死、连发用例数不到弹丸），
     * 飞过边界的弹丸更会被 {@code UNLOADED_TO_CHUNK} 直接清掉。换成大模板后，这些区块在测试
     * 开始前就已经就位。</p>
     *
     * <p>在这里自己补 {@code setChunkForced} 是没用的：票据要等下一个 tick 的
     * {@code ChunkMap.tick()} 才反映到可见性上，而本类的用例体都在同一个 tick 里跑完。</p>
     */
    private static void buildArena(GameTestHelper helper) {
        for (int x = 0; x < ARENA; x++) {
            for (int z = 0; z < ARENA; z++) {
                helper.setBlock(new BlockPos(x, PLATFORM_Y, z), Blocks.STONE);
            }
        }
    }
}
