package com.hhy.dreamingfishcore.gameplay.zombie_system.charred;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.effect.DreamingFishCore_Effects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 焦尸的端到端 gametest：在真实服务端里验证伤害闸门的四个分支。
 *
 * <p>做法与 {@code ArcherZombieGameTest} 一致：自建空中平台 + 直接调 {@code hurt} 而不是等 AI
 * 动手，这样每条断言只测一个规则，不会因为寻路、视线、时机而假红。</p>
 *
 * <p>两个刻意的设定：</p>
 * <ul>
 *   <li><b>清零护甲</b>：原版僵尸自带 2 点护甲，会把伤害乘上约 0.92，期望值就对不上整数了。
 *       测试里 {@code setBaseValue(0)} 之后，「火焰 4 点 ×2 = 8 点」这类断言才是逐位精确的。</li>
 *   <li><b>关掉 AI</b>：避免它在断言期间走开或打别的实体，让血量变化只来自测试自己发的那一下。</li>
 * </ul>
 */
@GameTestHolder(DreamingFishCore.MODID)
@PrefixGameTestTemplate(false)
public class CharredZombieGameTest {
    /** 平台层：实体站在 platformY + 1 上。 */
    private static final int PLATFORM_Y = 11;

    /**
     * 非火伤害：不掉血，但「命中」必须生效。
     *
     * <p>这条同时守着本设计里最容易写错的地方：火焰附加的点燃挂在 {@code Player#attack} 里
     * {@code EnchantmentHelper.doPostAttackEffects(...)} 的调用条件上，而那一段被包在
     * {@code if (target.hurt(...))} 里。所以免疫**不能**通过返回 false 实现——那样玩家用火剑
     * 既打不动也点不着，最顺手的反制路径直接消失。</p>
     */
    @GameTest(template = "empty")
    public static void nonFireDamageIsNullifiedButTheHitStillRegisters(GameTestHelper helper) {
        buildPlatform(helper);
        CharredZombieEntity charred = spawnCharred(helper);
        try {
            float before = charred.getHealth();
            boolean hit = charred.hurt(charred.damageSources().generic(), 6.0F);

            helper.assertTrue(hit,
                    "非火伤害必须仍然「命中生效」——返回值决定附魔点火、击退与受伤反馈是否成立");
            helper.assertTrue(Math.abs(charred.getHealth() - before) < 1.0E-4F,
                    "非火伤害必须完全不掉血，实际 " + charred.getHealth() + " / " + before);
            helper.assertTrue(!charred.hasEffect(DreamingFishCore_Effects.VULNERABLE),
                    "非火伤害不应当打出「易损」——破防只能由火造成");
        } finally {
            charred.discard();
        }
        helper.succeed();
    }

    /** 火焰类伤害：照常结算，并被易燃体质放大到 2 倍，同时打出「易损」。 */
    @GameTest(template = "empty")
    public static void fireDamageIsDoubledAndAppliesVulnerable(GameTestHelper helper) {
        buildPlatform(helper);
        CharredZombieEntity charred = spawnCharred(helper);
        try {
            float before = charred.getHealth();
            boolean damaged = charred.hurt(charred.damageSources().inFire(), 4.0F);
            float taken = before - charred.getHealth();

            helper.assertTrue(damaged, "火焰类伤害应当照常结算");
            helper.assertTrue(Math.abs(taken - 8.0F) < 1.0E-3F,
                    "4 点火焰伤害应当被易燃体质放大成 8 点（护甲已在测试里清零），实际 " + taken);
            helper.assertTrue(charred.hasEffect(DreamingFishCore_Effects.VULNERABLE),
                    "火焰类命中应当打出「易损」");
        } finally {
            charred.discard();
        }
        helper.succeed();
    }

    /** 「易损」期间：非火伤害破防放行，并按 +10% × 等级放大。 */
    @GameTest(template = "empty")
    public static void vulnerableOpensTheGateAndAmplifiesNonFireDamage(GameTestHelper helper) {
        buildPlatform(helper);
        CharredZombieEntity charred = spawnCharred(helper);
        try {
            charred.addEffect(new MobEffectInstance(DreamingFishCore_Effects.VULNERABLE, 20 * 30, 0));

            float before = charred.getHealth();
            boolean damaged = charred.hurt(charred.damageSources().generic(), 10.0F);
            float taken = before - charred.getHealth();

            helper.assertTrue(damaged, "破防之后非火伤害应当能打进去");
            helper.assertTrue(Math.abs(taken - 11.0F) < 1.0E-3F,
                    "10 点普通伤害在「易损 I」下应当是 11 点（+10%），实际 " + taken);
        } finally {
            charred.discard();
        }
        helper.succeed();
    }

    /** 兜底通道：虚空/溺水/摔落照旧生效，不吃免疫也不吃倍率。 */
    @GameTest(template = "empty")
    public static void fallbackDamageTypesStayEffective(GameTestHelper helper) {
        buildPlatform(helper);
        CharredZombieEntity charred = spawnCharred(helper);
        try {
            float before = charred.getHealth();
            boolean damaged = charred.hurt(charred.damageSources().drown(), 7.0F);
            float taken = before - charred.getHealth();

            helper.assertTrue(damaged,
                    "兜底通道（默认含溺水）必须能生效，否则一旦它卡住位置就只能靠 /kill 清理");
            helper.assertTrue(Math.abs(taken - 7.0F) < 1.0E-3F,
                    "兜底伤害不吃易燃体质倍率，7 点就该是 7 点，实际 " + taken);
        } finally {
            charred.discard();
        }
        helper.succeed();
    }

    /**
     * 与原版僵尸的关系：属性差异化、掉落沿用、日光自燃关掉。
     *
     * <p>日光这条是本设计的核心取舍——日光燃烧走 {@code on_fire} 伤害类型，也就是免费的火焰
     * 伤害；若保留它，玩家等到白天就能免费看它烧死，「必须用火」这条机制就没有意义了。</p>
     */
    @GameTest(template = "empty")
    public static void charredZombieKeepsVanillaTraitsButNotDaylightBurning(GameTestHelper helper) {
        buildPlatform(helper);
        CharredZombieEntity charred = spawnCharred(helper);
        try {
            helper.assertTrue(charred instanceof Zombie,
                    "焦尸必须直接继承原版僵尸，才能沿用近战追击、开门、水中腐化等基础行为");
            helper.assertTrue(Math.abs(charred.getMaxHealth() - 22.0F) < 1.0E-4F,
                    "生命上限应当是 22（原版僵尸 20），实际 " + charred.getMaxHealth());
            helper.assertTrue(Math.abs(charred.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.20D) < 1.0E-4D,
                    "移速应当是 0.20（原版僵尸 0.23），实际 "
                            + charred.getAttributeValue(Attributes.MOVEMENT_SPEED));
            helper.assertTrue(!charred.burnsInDaylight(),
                    "焦尸不该在白天自燃：日光就是免费的火焰伤害，会直接破解「必须用火」这条机制");

            ResourceKey<LootTable> expectedTable = ResourceKey.create(
                    Registries.LOOT_TABLE,
                    ResourceLocation.withDefaultNamespace("entities/zombie"));
            helper.assertTrue(expectedTable.equals(charred.getLootTable()),
                    "掉落应当沿用原版僵尸战利品表，实际 " + charred.getLootTable());
        } finally {
            charred.discard();
        }
        helper.succeed();
    }

    private static CharredZombieEntity spawnCharred(GameTestHelper helper) {
        CharredZombieEntity charred = helper.spawn(
                CharredZombieEntities.CHARRED_ZOMBIE.get(), new BlockPos(2, PLATFORM_Y + 1, 2));
        charred.setNoAi(true);
        // 原版僵尸自带 2 点护甲，会把伤害乘上约 0.92；清零后期望值才是整数。
        AttributeInstance armor = charred.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.setBaseValue(0.0D);
        }
        return charred;
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
