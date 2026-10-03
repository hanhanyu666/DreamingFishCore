package com.hhy.dreamingfishcore.gameplay.zombie_system.adamant;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.effect.DreamingFishCore_Effects;
import com.hhy.dreamingfishcore.gameplay.water_gun_system.WaterGunConfig;
import com.hhy.dreamingfishcore.gameplay.water_gun_system.WaterJetEntity;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 金刚僵尸的端到端 gametest：免疫与硬直、分级生锈的伤害递增、水柱浇锈。
 *
 * <p>这里刻意只断言**接线与方向**（未生锈 0 伤害、锈后掉血、锈级越高越疼、水柱能上锈），
 * 精确倍率交给 {@code AdamantZombieRulesTest} 的纯逻辑用例。原因是实体伤害还要过一遍原版
 * 护甲公式，硬在这里算浮点尾数会让用例变成「护甲公式改了就得改测试」。</p>
 */
@GameTestHolder(DreamingFishCore.MODID)
@PrefixGameTestTemplate(false)
public class AdamantZombieGameTest {
    /** 平台层：实体站在 platformY + 1 上。 */
    private static final int PLATFORM_Y = 11;
    /** 无敌帧默认 20 tick，命中间隔要跨过它，否则连续两次 hurt 里第二次会被静默忽略。 */
    private static final int INVULNERABLE_TICKS = 25;
    /** ServerPlayer 出生后的私有无敌帧长度（默认 60）；跑完它玩家才是正常状态。 */
    private static final int SPAWN_INVULNERABLE_TICKS = 70;

    /** 未生锈：不掉血，但「命中」必须生效，而且攻击者要吃硬直。 */
    @GameTest(template = "empty")
    public static void unrustedNullifiesDamageAndStaggersTheAttacker(GameTestHelper helper) {
        buildPlatform(helper);
        AdamantZombieEntity adamant = spawnAdamant(helper);
        ServerPlayer player = spawnPlayer(helper, new BlockPos(4, PLATFORM_Y + 1, 2));
        try {
            float before = adamant.getHealth();
            boolean hit = adamant.hurt(adamant.damageSources().playerAttack(player), 12.0F);

            helper.assertTrue(hit,
                    "未生锈也要「命中生效」——受伤动画、音效与仇恨都挂在这个返回值上");
            helper.assertTrue(Math.abs(adamant.getHealth() - before) < 1.0E-4F,
                    "未生锈不该掉血，实际 " + adamant.getHealth() + " / " + before);
            helper.assertTrue(adamant.rustStage() == 0, "被打不该让它生锈——水才会");
            helper.assertTrue(player.hasEffect(DreamingFishCore_Effects.STAGGER),
                    "攻击未生锈的金刚僵尸应当让攻击者吃硬直"
                            + "（创造=" + player.isCreative()
                            + "，旁观=" + player.isSpectator()
                            + "，硬直开关=" + adamant.runtimeSettings().staggerEnabled()
                            + "，时长=" + adamant.runtimeSettings().staggerDurationTicks() + "）");
        } finally {
            adamant.discard();
            dispose(player);
        }
        helper.succeed();
    }

    /** 生锈破防：第 1 层就是正常伤害，第 2 层再明显更疼（+20%）。 */
    @GameTest(template = "empty")
    public static void rustBreaksTheImmunityAndScalesIncomingDamage(GameTestHelper helper) {
        buildPlatform(helper);
        AdamantZombieEntity adamant = spawnAdamant(helper);
        try {
            // 先浇一层：未生锈是免疫的，直接打只会得到 0 伤害。
            helper.assertTrue(adamant.applyRust(1), "浇水应当能涨一层锈");
            float stage1Taken = hitForDamage(adamant, 10.0F);
            helper.assertTrue(stage1Taken > 9.0F && stage1Taken <= 10.5F,
                    "第 1 层锈应当按正常伤害结算（10 点，扣掉护甲后略少），实际 " + stage1Taken);

            helper.assertTrue(adamant.applyRust(1), "浇水应当能涨到第 2 层");
            float stage2Taken = hitForDamage(adamant, 10.0F);
            helper.assertTrue(stage2Taken > stage1Taken * 1.15F,
                    "第 2 层锈应当比第 1 层明显更疼（配置 +20%），实际 "
                            + stage2Taken + " vs " + stage1Taken);
        } finally {
            adamant.discard();
        }
        helper.succeed();
    }

    /** 锈级封顶：浇到上限之后再浇不会继续涨。 */
    @GameTest(template = "empty")
    public static void rustStopsAtTheConfiguredCeiling(GameTestHelper helper) {
        buildPlatform(helper);
        AdamantZombieEntity adamant = spawnAdamant(helper);
        try {
            int ceiling = adamant.runtimeSettings().maxRustStages();
            for (int i = 0; i < ceiling + 3; i++) {
                adamant.applyRust(1);
            }
            helper.assertTrue(adamant.rustStage() == ceiling,
                    "锈级应当停在配置上限 " + ceiling + "，实际 " + adamant.rustStage());
            helper.assertTrue(!adamant.applyRust(1), "已经锈透时再浇不该被算作「涨了层」");
        } finally {
            adamant.discard();
        }
        helper.succeed();
    }

    /** 水柱命中：上锈，而且一滴血都不该掉（水枪不是武器）。 */
    @GameTest(template = "empty")
    public static void waterJetRustsTheAdamantZombieWithoutDamagingIt(GameTestHelper helper) {
        buildPlatform(helper);
        AdamantZombieEntity adamant = spawnAdamant(helper);
        ArmorStand shooter = null;
        WaterJetEntity jet = null;
        try {
            // 发射者必须与目标**分开**：水柱若从目标包围盒内部出发，扫掠相交是测不到的。
            shooter = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(2, PLATFORM_Y + 1, 5));
            jet = new WaterJetEntity(
                    helper.getLevel(), shooter, WaterGunConfig.current().resolve());
            // 瞄胸口而不是眼睛：保证弹道接近水平，不会先扎进平台。
            Vec3 aimTarget = adamant.position().add(0.0D, 0.9D, 0.0D);
            aimAt(jet, aimTarget);
            Vec3 start = jet.position();
            helper.getLevel().addFreshEntity(jet);

            float before = adamant.getHealth();
            for (int i = 0; i < 40 && !jet.isRemoved(); i++) {
                jet.tick();
            }

            helper.assertTrue(adamant.rustStage() >= 1,
                    "水柱命中应当给金刚僵尸上锈，实际锈级 " + adamant.rustStage()
                            + "（消散 " + jet.isRemoved()
                            + "，起点 " + start
                            + "，瞄准 " + aimTarget
                            + "，终点 " + jet.position()
                            + "，速度 " + jet.getDeltaMovement()
                            + "，金刚僵尸 " + adamant.position()
                            + "，rustPerHit=" + WaterGunConfig.current().resolve().rustStagesPerHit()
                            + "，射程=" + WaterGunConfig.current().resolve().jetMaxRange() + "）");
            helper.assertTrue(Math.abs(adamant.getHealth() - before) < 1.0E-4F,
                    "水柱不该造成任何伤害，实际 " + adamant.getHealth() + " / " + before);
        } finally {
            if (jet != null) {
                jet.discard();
            }
            if (shooter != null) {
                shooter.discard();
            }
            adamant.discard();
        }
        helper.succeed();
    }

    /** 与原版僵尸的关系：属性差异化、不掉击退、掉落沿用、日光不自燃。 */
    @GameTest(template = "empty")
    public static void adamantZombieKeepsVanillaTraitsWithoutDaylightBurning(GameTestHelper helper) {
        buildPlatform(helper);
        AdamantZombieEntity adamant = spawnAdamant(helper);
        try {
            helper.assertTrue(adamant instanceof Zombie, "金刚僵尸必须直接继承原版僵尸");
            helper.assertTrue(Math.abs(adamant.getMaxHealth() - 40.0F) < 1.0E-4F,
                    "生命上限应当是 40，实际 " + adamant.getMaxHealth());
            helper.assertTrue(
                    Math.abs(adamant.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - 1.0D) < 1.0E-4D,
                    "击退抗性应当是 1.0（纹丝不动），实际 "
                            + adamant.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            helper.assertTrue(!adamant.burnsInDaylight(),
                    "金刚僵尸不该在白天自燃：那会成为绕过「必须先浇水」的后门");

            ResourceKey<LootTable> expectedTable = ResourceKey.create(
                    Registries.LOOT_TABLE,
                    ResourceLocation.withDefaultNamespace("entities/zombie"));
            helper.assertTrue(expectedTable.equals(adamant.getLootTable()),
                    "掉落应当沿用原版僵尸战利品表，实际 " + adamant.getLootTable());
        } finally {
            adamant.discard();
        }
        helper.succeed();
    }

    /** 打一下并返回实际扣掉的血量；先跑完无敌帧，否则第二次命中会被静默忽略。 */
    private static float hitForDamage(AdamantZombieEntity adamant, float amount) {
        for (int i = 0; i < INVULNERABLE_TICKS; i++) {
            adamant.tick();
        }
        float before = adamant.getHealth();
        boolean damaged = adamant.hurt(adamant.damageSources().generic(), amount);
        if (!damaged) {
            return -1.0F;
        }
        return before - adamant.getHealth();
    }

    private static void aimAt(WaterJetEntity jet, Vec3 target) {
        Vec3 from = jet.position();
        jet.shoot(
                target.x - from.x,
                target.y - from.y,
                target.z - from.z,
                (float) WaterGunConfig.current().resolve().jetSpeed(),
                0.0F);
    }

    private static AdamantZombieEntity spawnAdamant(GameTestHelper helper) {
        AdamantZombieEntity adamant = helper.spawn(
                AdamantZombieEntities.ADAMANT_ZOMBIE.get(), new BlockPos(2, PLATFORM_Y + 1, 2));
        adamant.setNoAi(true);
        return adamant;
    }

    /**
     * 造一个「真的能攻击」的模拟生存玩家。
     *
     * <p>{@code makeMockServerPlayerInLevel()} 把 {@code isCreative()} 硬编码为 true，而硬直逻辑
     * 刻意跳过创造模式玩家（他在调试），所以这里必须显式退回生存：清掉能力位 {@code instabuild}
     * 与 {@code invulnerable}，再把出生后 60 tick 的无敌帧跑完。</p>
     */
    private static ServerPlayer spawnPlayer(GameTestHelper helper, BlockPos relativePos) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        AuthSessionGuard.markAuthenticated(player);
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().invulnerable = false;
        player.getAbilities().instabuild = false;
        player.getAbilities().mayfly = false;
        player.setInvulnerable(false);
        BlockPos absolute = helper.absolutePos(relativePos);
        player.moveTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        for (int i = 0; i < SPAWN_INVULNERABLE_TICKS; i++) {
            player.tick();
            player.doTick();
        }
        return player;
    }

    private static void dispose(ServerPlayer player) {
        if (player != null) {
            player.discard();
        }
    }

    /** 在相对 y=11 铺一层小平台，把实体放在脱离地形的同一平面上。 */
    private static void buildPlatform(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(new BlockPos(x, PLATFORM_Y, z), Blocks.STONE);
            }
        }
    }
}
