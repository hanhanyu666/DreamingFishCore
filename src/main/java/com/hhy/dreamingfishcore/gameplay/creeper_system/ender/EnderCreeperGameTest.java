package com.hhy.dreamingfishcore.gameplay.creeper_system.ender;

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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 末影人苦力怕的端到端 gametest：会瞬移拉近、已点燃时绝不瞬移。
 *
 * <p>两条用例是<b>成对</b>的：单看「点燃时不瞬移」很容易假绿——瞬移功能整个坏掉时它也会通过。
 * 所以必须同时有「远距离目标确实被拉近了」这条正向用例，才证明瞬移链路真的在跑。</p>
 *
 * <p><b>目标用僵尸，不用模拟玩家</b>：模拟玩家的连接没协商过自定义通道，自定义载荷下发会抛
 * {@code "may not be sent to the client!"}，把用例连坐成红灯（与 research 用例同一个坑）。</p>
 */
@GameTestHolder(DreamingFishCore.MODID)
@PrefixGameTestTemplate(false)
public class EnderCreeperGameTest {
    private static final int FLOOR_Y = 11;
    /** 瞬移是瞬时的，几 tick 内就该发生；跑太久反而会等到冷却。 */
    private static final int TELEPORT_WINDOW_TICKS = 5;
    /** 点燃后的观察窗口。必须远小于原版引信 30 tick，否则它会在用例里炸掉。 */
    private static final int IGNITED_TICKS = 10;

    /** 远距离目标：应当被瞬移拉近（并且移动幅度远超走路）。 */
    @GameTest(template = "commander_arena")
    public static void enderCreeperTeleportsTowardADistantTarget(GameTestHelper helper) {
        buildFloor(helper);
        EnderCreeperEntity creeper = helper.spawn(
                EnderCreeperEntities.ENDER_CREEPER.get(), new BlockPos(2, FLOOR_Y + 1, 2));
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(14, FLOOR_Y + 1, 14));
        try {
            helper.assertTrue(creeper instanceof Creeper, "末影人苦力怕必须直接继承原版苦力怕");
            ResourceKey<LootTable> expected = ResourceKey.create(
                    Registries.LOOT_TABLE,
                    ResourceLocation.withDefaultNamespace("entities/creeper"));
            helper.assertTrue(expected.equals(creeper.getLootTable()),
                    "掉落应当沿用原版苦力怕战利品表，实际 " + creeper.getLootTable());

            creeper.setTarget(target);
            Vec3 start = creeper.position();
            double distanceBefore = creeper.distanceTo(target);
            boolean readyBefore = creeper.isTeleportReady();
            for (int i = 0; i < TELEPORT_WINDOW_TICKS; i++) {
                creeper.tick();
            }
            double moved = start.distanceTo(creeper.position());
            double distanceAfter = creeper.distanceTo(target);
            BlockPos targetBlock = target.blockPosition();

            helper.assertTrue(moved > 2.0D,
                    "远距离目标应当触发瞬移（走路 " + TELEPORT_WINDOW_TICKS + " tick 挪不了 2 格）。"
                            + "起点=" + start + "，现在=" + creeper.position()
                            + "，位移=" + moved
                            + "，距离 " + distanceBefore + " -> " + distanceAfter
                            + "，开始时就绪=" + readyBefore
                            + "，结束时就绪=" + creeper.isTeleportReady()
                            + "，目标=" + target.position()
                            + "，目标区块已加载=" + helper.getLevel().hasChunkAt(targetBlock)
                            + "，目标下方方块=" + helper.getLevel().getBlockState(targetBlock.below())
                            + "，落点高度目标=" + (target.getY() + 0.5D));
            helper.assertTrue(distanceAfter < distanceBefore,
                    "瞬移之后应当比之前更靠近目标：" + distanceBefore + " -> " + distanceAfter);
        } finally {
            creeper.discard();
            target.discard();
        }
        helper.succeed();
    }

    /** 已点燃时绝不瞬移：否则引信一响它闪走，玩家永远炸不到，等于无敌。 */
    @GameTest(template = "commander_arena")
    public static void enderCreeperNeverTeleportsWhileIgnited(GameTestHelper helper) {
        buildFloor(helper);
        EnderCreeperEntity creeper = helper.spawn(
                EnderCreeperEntities.ENDER_CREEPER.get(), new BlockPos(2, FLOOR_Y + 1, 2));
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(14, FLOOR_Y + 1, 14));
        try {
            creeper.setTarget(target);
            creeper.ignite();
            Vec3 start = creeper.position();
            for (int i = 0; i < IGNITED_TICKS; i++) {
                creeper.tick();
            }
            double moved = start.distanceTo(creeper.position());
            helper.assertTrue(creeper.isIgnited(), "前置条件：它应当仍处于点燃状态");
            helper.assertTrue(moved < 1.0D,
                    "已点燃就不许瞬移（否则等于无敌）。起点=" + start
                            + "，现在=" + creeper.position()
                            + "，位移=" + moved);
        } finally {
            creeper.discard();
            target.discard();
        }
        helper.succeed();
    }

    private static void buildFloor(GameTestHelper helper) {
        for (int x = 0; x < 20; x++) {
            for (int z = 0; z < 20; z++) {
                helper.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.STONE);
            }
        }
    }
}
