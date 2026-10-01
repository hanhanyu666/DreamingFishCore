package com.hhy.dreamingfishcore.gameplay.spawner_system;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 刷怪箱服务的纯逻辑：检测半径与刷怪落点。
 *
 * <p>实体解析、落点安全性（脚下要有方块）、启用判定这些都要真实世界，放在 gametest 里验；
 * 这里只锁"不会因为半径算错而刷到范围外"这类一眼看不出、后果又很烦的错误。</p>
 */
class SpawnerServiceTest {

    @Test
    void detectionRangeUsesHorizontalDistance() {
        assertTrue(SpawnerService.withinRange(0, 0, 32));
        assertTrue(SpawnerService.withinRange(32, 0, 32));
        assertTrue(SpawnerService.withinRange(0, -32, 32));
        assertFalse(SpawnerService.withinRange(33, 0, 32));
        assertTrue(SpawnerService.withinRange(22, 22, 32), "斜向按真实距离比较");
        assertFalse(SpawnerService.withinRange(23, 23, 32));
        assertFalse(SpawnerService.withinRange(0, 0, 0), "半径为 0 表示不检测");
        assertFalse(SpawnerService.withinRange(5, 5, -1));
    }

    @Test
    void spawnPositionsNeverLeaveTheConfiguredRadius() {
        RandomSource random = RandomSource.create(20260930L);
        BlockPos origin = new BlockPos(120, 64, -340);
        int radius = 8;

        boolean sawCentre = false;
        for (int attempt = 0; attempt < 500; attempt++) {
            BlockPos picked = SpawnerService.pickSpawnPosition(random, origin, radius);
            int dx = picked.getX() - origin.getX();
            int dz = picked.getZ() - origin.getZ();
            assertEquals(0, picked.getY() - origin.getY(), "落点的 Y 由安全落点搜索决定，这里不该偏移");
            assertTrue(Math.abs(dx) <= radius && Math.abs(dz) <= radius,
                    "落点必须落在刷怪半径的方形内：dx=" + dx + " dz=" + dz);
            if (dx == 0 && dz == 0) {
                sawCentre = true;
            }
        }
        assertTrue(sawCentre, "500 次采样里应当出现过中心点");
    }

    @Test
    void spawnRadiusOneStillPicksFromTheThreeByThreeArea() {
        RandomSource random = RandomSource.create(7L);
        BlockPos origin = new BlockPos(0, 0, 0);
        for (int attempt = 0; attempt < 40; attempt++) {
            BlockPos picked = SpawnerService.pickSpawnPosition(random, origin, 1);
            assertTrue(Math.abs(picked.getX()) <= 1 && Math.abs(picked.getZ()) <= 1);
        }
    }
}
