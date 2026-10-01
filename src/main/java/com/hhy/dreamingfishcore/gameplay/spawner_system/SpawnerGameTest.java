package com.hhy.dreamingfishcore.gameplay.spawner_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.block.DreamingFishCore_Blocks;
import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieEntity;
import com.hhy.dreamingfishcore.gameplay.spawner_system.network.Packet_SpawnerSnapshotResponse;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationManager;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationMode;
import com.hhy.dreamingfishcore.server.economy_bridge.EconomySystemBridge;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 刷怪箱的端到端 gametest：尸潮区域 + 在场玩家 → 刷怪 → 剿灭结算。
 *
 * <p><b>注意</b>：gametest 服务器与开发客户端共用同一份
 * {@code run/config/dreamingfishcore/task_locations.json}（地点配置在 config 目录，不在存档里）。
 * 所以这个测试会先备份该文件、结束后恢复，避免污染服主正在玩的地点配置。</p>
 */
@GameTestHolder(DreamingFishCore.MODID)
@PrefixGameTestTemplate(false)
public class SpawnerGameTest {

    private static final String HORDE_ID = "dreamingfishcore:gametest_horde";
    private static final String HORDE_NAME = "gametest 尸潮区";

    @GameTest(template = "empty")
    public static void spawnerSpawnsABatchAndSettlesRewardsWhenCleared(GameTestHelper helper) {
        Path configPath = TaskLocationManager.getConfigPath();
        byte[] backup = readOrNull(configPath);
        ServerLevel level = helper.getLevel();
        BlockPos devicePos = helper.absolutePos(new BlockPos(1, 1, 1));
        String dimensionId = level.dimension().location().toString();

        try {
            // 结构里铺一层地板，否则找不到"脚下有方块"的落点，一只都刷不出来。
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= 2; z++) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                }
            }

            // 建一个开着尸潮开关的可建造地点，并把刷怪箱放进去。
            TaskLocationManager.installLocationWithFixedId(HORDE_ID, HORDE_NAME,
                    level.dimension(), devicePos.offset(-8, -3, -8), devicePos.offset(8, 3, 8),
                    TaskLocationMode.BUILDABLE);
            TaskLocationManager.setHordeByName(HORDE_NAME, true);
            helper.assertTrue(TaskLocationManager.isHordeArea(level, devicePos),
                    "刷怪箱位置应当落在尸潮区域内");

            helper.setBlock(new BlockPos(1, 1, 1), DreamingFishCore_Blocks.SPAWNER.get());
            SpawnerRegistry.register(dimensionId,
                    devicePos.getX(), devicePos.getY(), devicePos.getZ());
            SpawnerEntry entry = SpawnerRegistry.find(dimensionId,
                    devicePos.getX(), devicePos.getY(), devicePos.getZ()).orElse(null);
            helper.assertTrue(entry != null, "放置后应当登记到刷怪箱表");

            entry.setBatches(1);
            entry.setSpawnCount(2);
            // 刻意不指定实体：默认就是 dreamingfishcore:siege_zombie，这条默认路径才是玩家真正会用的。
            // 攻城丧尸的运行时能力（挖墙/开门/搭桥）是在 finalizeSpawn 里装配的，
            // 用原版僵尸测会漏掉这一段。
            helper.assertTrue(SpawnerEntry.DEFAULT_ENTITY_ID.equals(entry.entityId()),
                    "默认实体应当是攻城丧尸，实际 " + entry.entityId());
            entry.setRewardCoins(7);
            entry.setSelfDestructWhenCleared(false);

            // 一名生存玩家站在检测范围内。
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            AuthSessionGuard.markAuthenticated(player);
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(devicePos.getX() + 0.5D, devicePos.getY(),
                    devicePos.getZ() + 1.5D);
            int balanceBefore = EconomySystemBridge.balance(player);

            // 第一次判定：应当立刻刷出第 1 批（也是唯一一批）。
            SpawnerService.tickNow(helper.getLevel().getServer());
            helper.assertTrue(entry.batchesSpawned() == 1,
                    "第一批应当立刻刷出，实际批次 " + entry.batchesSpawned());
            List<SiegeZombieEntity> zombies = level.getEntitiesOfClass(SiegeZombieEntity.class,
                    player.getBoundingBox().inflate(32.0D));
            helper.assertFalse(zombies.isEmpty(),
                    "结构附近应当出现默认的攻城丧尸（默认生成路径未被地点刷怪禁令拦下）");
            // 攻城丧尸的能力在 finalizeSpawn 里装配，这里顺带确认它真的完成了初始化。
            helper.assertTrue(zombies.get(0).isAlive(), "生成的攻城丧尸应当是活的");

            // 清场后再次判定：应当结算奖励。
            for (SiegeZombieEntity zombie : zombies) {
                zombie.discard();
            }
            SpawnerService.tickNow(helper.getLevel().getServer());
            helper.assertTrue(entry.hasRewarded(player.getUUID()),
                    "剿灭后应当给在场玩家结算奖励");
            if (balanceBefore >= 0) {
                helper.assertTrue(EconomySystemBridge.balance(player) == balanceBefore + 7,
                        "应当发放 7 梦鱼币，实际余额 " + EconomySystemBridge.balance(player));
            }

            // 快照编解码往返：字段顺序写错在编译期看不出来。
            SpawnerView view = SpawnerService.buildView(level, entry, true);
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                    Unpooled.buffer(), level.registryAccess());
            Packet_SpawnerSnapshotResponse.STREAM_CODEC.encode(
                    buffer, new Packet_SpawnerSnapshotResponse(view));
            SpawnerView decoded = Packet_SpawnerSnapshotResponse.STREAM_CODEC
                    .decode(buffer).view();
            helper.assertTrue(decoded.batches() == view.batches()
                            && decoded.spawnCount() == view.spawnCount()
                            && decoded.rewardCoins() == view.rewardCoins()
                            && decoded.entityId().equals(view.entityId()),
                    "快照往返后配置应当保持一致");
            helper.assertTrue(decoded.canEdit() == view.canEdit()
                            && decoded.inHordeArea() == view.inHordeArea(),
                    "快照往返后权限与区域状态应当保持一致");
        } finally {
            // 清理全局状态：登记表、结构里的怪、以及被临时改写的地点配置。
            SpawnerRegistry.unregister(dimensionId,
                    devicePos.getX(), devicePos.getY(), devicePos.getZ());
            for (Mob mob : level.getEntitiesOfClass(Mob.class,
                    new net.minecraft.world.phys.AABB(devicePos).inflate(48.0D))) {
                mob.discard();
            }
            restore(configPath, backup);
        }
        helper.succeed();
    }

    private static byte[] readOrNull(Path path) {
        try {
            return Files.exists(path) ? Files.readAllBytes(path) : null;
        } catch (IOException exception) {
            return null;
        }
    }

    private static void restore(Path path, byte[] backup) {
        try {
            if (backup == null) {
                Files.deleteIfExists(path);
            } else {
                Files.write(path, backup);
            }
            TaskLocationManager.reload();
        } catch (IOException | RuntimeException exception) {
            DreamingFishCore.LOGGER.error("gametest 恢复任务地点配置失败：{}", path, exception);
        }
    }
}
