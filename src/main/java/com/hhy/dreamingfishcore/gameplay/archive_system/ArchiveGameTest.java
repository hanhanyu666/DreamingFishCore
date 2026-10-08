package com.hhy.dreamingfishcore.gameplay.archive_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.block.DreamingFishCore_Blocks;
import com.hhy.dreamingfishcore.gameplay.archive_system.event.ArchiveEventHandler;
import com.hhy.dreamingfishcore.gameplay.blueprint_system.BlueprintConfig;
import com.hhy.dreamingfishcore.gameplay.blueprint_system.PlayerBlueprintData;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 资料库的端到端 gametest：<b>右键存入 → 左键学习 → 库内容不消耗</b>。
 *
 * <p>走的是真实链路：{@link ArchiveService#onDeposit} / 真实的
 * {@link PlayerInteractEvent.LeftClickBlock} 事件对象（直接构造后交给
 * {@link ArchiveEventHandler}），而不是绕开入口直接调内部方法——本项目吃过一次
 * 「手工构造命中结果把真实入口旁路掉、逻辑全绿而游戏里是坏的」的亏。</p>
 *
 * <p><b>配置</b>：gametest 服务器与开发客户端共用 {@code run/config/dreamingfishcore} 下的文件，
 * 所以这里先备份、临时改成确定值（蓝图启用且没有任何免蓝图条目——否则
 * {@code getLearnedBlueprintItems} 恒为空，什么都测不出来），结束时原样恢复。</p>
 */
@GameTestHolder(DreamingFishCore.MODID)
@PrefixGameTestTemplate(false)
public class ArchiveGameTest {

    /** 测试用的临时蓝图配置：启用、且免蓝图名单全空。 */
    private static final String TEST_BLUEPRINT_JSON = """
            {
              "schemaVersion": 1,
              "enabled": true,
              "siegeZombieDropPercent": 5.0,
              "chestDropPercent": 25.0,
              "defaultUnlockedItems": [],
              "exemptNamespaces": [],
              "blueprintWhitelist": [],
              "blueprintBlacklist": []
            }
            """;

    /** 存入的两条测试配方。用原版物品 ID 只是图省事：这里比的是字符串集合，与物品本身无关。 */
    private static final String RECIPE_A = "minecraft:stick";
    private static final String RECIPE_B = "minecraft:torch";

    @GameTest(template = "empty")
    public static void depositThenLearnKeepsTheLibraryIntact(GameTestHelper helper) {
        Path blueprintPath = BlueprintConfig.getConfigPath();
        byte[] backup = readOrNull(blueprintPath);
        ServerPlayer player = null;
        try {
            write(blueprintPath, TEST_BLUEPRINT_JSON);
            BlueprintConfig.reload();
            PlayerBlueprintData.markPoolDirty();

            helper.setBlock(new BlockPos(1, 1, 1), DreamingFishCore_Blocks.ARCHIVE.get());
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            player = spawnPlayerAt(helper, pos);
            String dimension = player.level().dimension().location().toString();

            // 先让玩家"学过两条需要蓝图的配方"。
            PlayerBlueprintData.unlockItem(player, RECIPE_A);
            PlayerBlueprintData.unlockItem(player, RECIPE_B);

            // ---- 右键存入 ----
            ArchiveService.onDeposit(player, pos);
            List<String> stored = ArchiveRegistry.recipesAt(dimension, pos);
            helper.assertTrue(stored.contains(RECIPE_A) && stored.contains(RECIPE_B),
                    "存入之后库里应当有这两条配方，实际 " + stored);

            // ---- 重复存入不产生重复条目 ----
            ArchiveService.onDeposit(player, pos);
            helper.assertTrue(ArchiveRegistry.countAt(dimension, pos) == 2,
                    "重复存入不该产生重复条目，实际 " + ArchiveRegistry.countAt(dimension, pos));

            // ---- 清空玩家进度，再用左键学回来 ----
            PlayerBlueprintData.clearAllUnlocks(player);
            helper.assertFalse(PlayerBlueprintData.hasLearned(player, RECIPE_A),
                    "清空之后应当处于未学会状态（否则下面的断言测不到东西）");

            PlayerInteractEvent.LeftClickBlock event = leftClick(player, pos);
            ArchiveEventHandler.onLeftClickBlock(event);

            helper.assertTrue(event.isCanceled(), "不潜行左键资料库应当取消原版挖掘");
            helper.assertTrue(PlayerBlueprintData.hasLearned(player, RECIPE_A)
                            && PlayerBlueprintData.hasLearned(player, RECIPE_B),
                    "左键之后应当把库里两条配方都学会");

            // ---- 不消耗：库内容必须原封不动 ----
            helper.assertTrue(ArchiveRegistry.countAt(dimension, pos) == 2,
                    "学习不能消耗库内容，实际 " + ArchiveRegistry.countAt(dimension, pos));

            // ---- 再点一次：不报错、内容依旧在、玩家依旧会 ----
            ArchiveEventHandler.onLeftClickBlock(leftClick(player, pos));
            helper.assertTrue(ArchiveRegistry.countAt(dimension, pos) == 2
                            && PlayerBlueprintData.hasLearned(player, RECIPE_A),
                    "重复学习应当是无副作用的空操作");
        } finally {
            restore(blueprintPath, backup);
            BlueprintConfig.reload();
            PlayerBlueprintData.markPoolDirty();
            if (player != null) {
                helper.getLevel().getServer().getPlayerList().remove(player);
            }
        }
        helper.succeed();
    }

    /** 潜行左键 = 原版挖掘，必须放行（玩家得能把它挖掉）。 */
    @GameTest(template = "empty")
    public static void sneakingLeftClickIsLeftToVanilla(GameTestHelper helper) {
        ServerPlayer player = null;
        try {
            helper.setBlock(new BlockPos(1, 1, 1), DreamingFishCore_Blocks.ARCHIVE.get());
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            player = spawnPlayerAt(helper, pos);
            player.setShiftKeyDown(true);

            PlayerInteractEvent.LeftClickBlock event = leftClick(player, pos);
            ArchiveEventHandler.onLeftClickBlock(event);

            helper.assertFalse(event.isCanceled(),
                    "潜行左键资料库必须放行，否则玩家没办法把它挖掉");
        } finally {
            if (player != null) {
                helper.getLevel().getServer().getPlayerList().remove(player);
            }
        }
        helper.succeed();
    }

    /** 左键别的方块：一律不管（这个事件是全局的，不能顺手拦掉别人的交互）。 */
    @GameTest(template = "empty")
    public static void leftClickingOtherBlocksIsUntouched(GameTestHelper helper) {
        ServerPlayer player = null;
        try {
            helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            player = spawnPlayerAt(helper, pos);

            PlayerInteractEvent.LeftClickBlock event = leftClick(player, pos);
            ArchiveEventHandler.onLeftClickBlock(event);

            helper.assertFalse(event.isCanceled(), "左键普通方块不该被资料库拦截");
        } finally {
            if (player != null) {
                helper.getLevel().getServer().getPlayerList().remove(player);
            }
        }
        helper.succeed();
    }

    /** 空库左键：照样取消挖掘（规则单一：不潜行就不会挖掉），但不能出异常。 */
    @GameTest(template = "empty")
    public static void leftClickingAnEmptyLibraryStillConsumesTheClick(GameTestHelper helper) {
        ServerPlayer player = null;
        try {
            helper.setBlock(new BlockPos(1, 1, 1), DreamingFishCore_Blocks.ARCHIVE.get());
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            player = spawnPlayerAt(helper, pos);

            PlayerInteractEvent.LeftClickBlock event = leftClick(player, pos);
            ArchiveEventHandler.onLeftClickBlock(event);

            helper.assertTrue(event.isCanceled(),
                    "空库也应当取消挖掘——「不潜行 = 不会挖掉」这条规则要单一，不能取决于库里有没有东西");
        } finally {
            if (player != null) {
                helper.getLevel().getServer().getPlayerList().remove(player);
            }
        }
        helper.succeed();
    }

    // ==================== 辅助 ====================

    private static PlayerInteractEvent.LeftClickBlock leftClick(ServerPlayer player, BlockPos pos) {
        return new PlayerInteractEvent.LeftClickBlock(
                player, pos, Direction.UP,
                PlayerInteractEvent.LeftClickBlock.Action.START);
    }

    /** 造一个模拟玩家并站到方块旁：交互有 8 格距离判定，站远了会被直接忽略。 */
    private static ServerPlayer spawnPlayerAt(GameTestHelper helper, BlockPos pos) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        AuthSessionGuard.markAuthenticated(player);
        player.teleportTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        return player;
    }

    private static byte[] readOrNull(Path path) {
        try {
            return Files.exists(path) ? Files.readAllBytes(path) : null;
        } catch (IOException exception) {
            return null;
        }
    }

    private static void write(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            // 写不进去的话下面的断言会带着实际值失败——不在这里抛，是为了让失败信息可读。
            DreamingFishCore.LOGGER.error("gametest 写入配置失败：{}", path, exception);
        }
    }

    private static void restore(Path path, byte[] backup) {
        try {
            if (backup == null) {
                Files.deleteIfExists(path);
            } else {
                Files.write(path, backup);
            }
        } catch (IOException exception) {
            DreamingFishCore.LOGGER.error("gametest 恢复配置失败：{}", path, exception);
        }
    }
}
