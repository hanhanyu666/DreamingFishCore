package com.hhy.dreamingfishcore.gameplay.creeper_system.phantom;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 幻翼苦力怕的端到端 gametest：无重力、沿用原版苦力怕的底子、会爬到目标头顶的巡航高度。
 *
 * <p>用 {@code commander_arena}（24×24 全空气）而不是 3×3 的 {@code empty}：实体在
 * {@code empty} 里会因为「只强加载了一个区块」被判定为不可 tick，出现「刷出来了但查不到、
 * 也不动」的假失败。这是项目里踩过的坑，新用例一律用大模板。</p>
 *
 * <p><b>目标用僵尸，不用模拟玩家。</b>模拟玩家的连接没有协商过自定义通道，任何自定义载荷下发都会
 * 抛 {@code "may not be sent to the client!"}；只要登录/tick 链路上还有别的系统在推包，
 * 这条用例就会被连坐成红灯（项目里 research 用例的注释记过同一件事）。</p>
 */
@GameTestHolder(DreamingFishCore.MODID)
@PrefixGameTestTemplate(false)
public class PhantomCreeperGameTest {
    /**
     * 地面层：实体站在 FLOOR_Y + 1 上。
     *
     * <p>必须铺在 11 这一层，不能贴地铺：{@code commander_arena} 模板在下方（rel y=2）有一层
     * {@code minecraft:barrier} 作为边界地板，贴着它铺会让实体整只卡在屏障里，
     * 表现为瞬移落点全部被判「有碰撞」、怎么试都失败。既有的指挥官用例同样用 11。</p>
     */
    private static final int FLOOR_Y = 11;
    /** 够它从地面爬到巡航高度的 tick 数。 */
    private static final int FLIGHT_TICKS = 40;

    /** 底子仍然是原版苦力怕：继承关系、生命、掉落表都对得上，而且一出生就是无重力。 */
    @GameTest(template = "commander_arena")
    public static void phantomCreeperKeepsVanillaCreeperTraitsAndNeverFalls(GameTestHelper helper) {
        buildFloor(helper);
        PhantomCreeperEntity creeper = helper.spawn(
                PhantomCreeperEntities.PHANTOM_CREEPER.get(), new BlockPos(3, FLOOR_Y + 1, 3));
        try {
            helper.assertTrue(creeper instanceof Creeper, "幻翼苦力怕必须直接继承原版苦力怕");
            helper.assertTrue(Math.abs(creeper.getMaxHealth() - 20.0F) < 1.0E-4F,
                    "生命上限应当与原版苦力怕一致（20），实际 " + creeper.getMaxHealth());
            ResourceKey<LootTable> expected = ResourceKey.create(
                    Registries.LOOT_TABLE,
                    ResourceLocation.withDefaultNamespace("entities/creeper"));
            helper.assertTrue(expected.equals(creeper.getLootTable()),
                    "掉落应当沿用原版苦力怕战利品表，实际 " + creeper.getLootTable());

            creeper.tick();
            helper.assertTrue(creeper.isNoGravity(),
                    "幻翼苦力怕应当常驻无重力，否则它会掉到地上变成一只普通苦力怕");
            helper.assertTrue(creeper.isFlyingPose(),
                    "不在水里时应当使用趴平姿势（渲染分支的依据）");
        } finally {
            creeper.discard();
        }
        helper.succeed();
    }

    /**
     * 巡航：有目标时应当爬到「目标位置 + hoverHeight」的高度。
     *
     * <p>只断言方向（明显升高），不断言精确高度——精确值受惯性系数与起飞 tick 影响，
     * 硬算会让用例变成「调一次手感就得改测试」。数值本身由 {@code PhantomCreeperConfigTest} 钉。</p>
     */
    @GameTest(template = "commander_arena")
    public static void phantomCreeperClimbsToHoverHeightAboveItsTarget(GameTestHelper helper) {
        buildFloor(helper);
        PhantomCreeperEntity creeper = helper.spawn(
                PhantomCreeperEntities.PHANTOM_CREEPER.get(), new BlockPos(4, FLOOR_Y + 1, 4));
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, FLOOR_Y + 1, 9));
        try {
            creeper.setTarget(target);
            double startY = creeper.getY();
            for (int i = 0; i < FLIGHT_TICKS; i++) {
                creeper.tick();
            }
            helper.assertTrue(creeper.getY() > startY + 1.0D,
                    "它应当爬到目标头顶的巡航高度。起始 Y=" + startY
                            + "，当前 Y=" + creeper.getY()
                            + "，速度=" + creeper.getDeltaMovement()
                            + "，目标=" + target.position()
                            + "，hoverHeight=" + creeper.runtimeSettings().hoverHeight()
                            + "，无重力=" + creeper.isNoGravity());
        } finally {
            creeper.discard();
            target.discard();
        }
        helper.succeed();
    }

    /** 铺一层地板，避免实体在 24×24 全空气模板里往下掉。 */
    private static void buildFloor(GameTestHelper helper) {
        for (int x = 0; x < 20; x++) {
            for (int z = 0; z < 20; z++) {
                helper.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.STONE);
            }
        }
    }
}
