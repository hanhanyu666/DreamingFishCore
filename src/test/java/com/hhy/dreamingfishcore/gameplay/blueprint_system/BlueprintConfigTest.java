package com.hhy.dreamingfishcore.gameplay.blueprint_system;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 蓝图配置：默认关闭、默认放行、命名空间赦免、白/黑名单与越界修正。 */
class BlueprintConfigTest {

    private static Path write(Path dir, String json) throws Exception {
        Path path = dir.resolve("blueprint.json");
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return path;
    }

    @Test
    void missingFileCreatesDisabledDefaults(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("blueprint.json");

        BlueprintConfig config = BlueprintConfig.load(path);

        assertTrue(Files.exists(path), "缺失配置应写入默认文件");
        assertFalse(config.isEnabled(), "会显著改变合成体验，默认必须是关的");
        assertEquals(0.0D, config.getSiegeZombieDropPercent(), 1.0E-9D,
                "关闭状态下概率一律报 0，避免调用方以为还会掉落");
        assertEquals(0.0D, config.getChestDropPercent(), 1.0E-9D);
        // 常量本身才是「默认值」的准确定义。
        assertEquals(5.0D, BlueprintConfig.DEFAULT_SIEGE_ZOMBIE_DROP_PERCENT, 1.0E-9D);
        assertEquals(25.0D, BlueprintConfig.DEFAULT_CHEST_DROP_PERCENT, 1.0E-9D);
    }

    @Test
    void enabledConfigFallsBackToDefaultPercents(@TempDir Path dir) throws Exception {
        Path config = write(dir, """
                {"schemaVersion": 1, "enabled": true}
                """);

        BlueprintConfig loaded = BlueprintConfig.load(config);

        assertEquals(5.0D, loaded.getSiegeZombieDropPercent(), 1.0E-9D,
                "未写明的字段应保留构造器里的默认值（5%）");
        assertEquals(25.0D, loaded.getChestDropPercent(), 1.0E-9D);
    }

    @Test
    void disabledConfigLetsEverythingThrough(@TempDir Path dir) throws Exception {
        Path config = write(dir, """
                {"schemaVersion": 1, "enabled": false,
                 "defaultUnlockedItems": [], "blueprintBlacklist": ["minecraft:diamond_sword"]}
                """);

        BlueprintConfig loaded = BlueprintConfig.load(config);

        assertTrue(loaded.isExemptFromBlueprint("minecraft:diamond_sword"), "关闭后连黑名单也不拦");
        assertTrue(loaded.isExemptFromBlueprint("minecraft:beacon"));
        assertEquals(0.0D, loaded.getSiegeZombieDropPercent(), 1.0E-9D, "关闭后不掉蓝图");
        assertEquals(0.0D, loaded.getChestDropPercent(), 1.0E-9D, "关闭后不掉蓝图");
        assertFalse(loaded.isInBlueprintPool("minecraft:beacon"), "关闭后抽取池为空");
    }

    @Test
    void defaultListedItemsAreExemptWhenEnabled(@TempDir Path dir) throws Exception {
        Path config = write(dir, """
                {"schemaVersion": 1, "enabled": true}
                """);

        BlueprintConfig loaded = BlueprintConfig.load(config);

        assertTrue(loaded.isExemptFromBlueprint("minecraft:oak_planks"), "默认规则里有 *_planks");
        assertTrue(loaded.isExemptFromBlueprint("minecraft:stone_pickaxe"), "木石工具默认放行");
        assertTrue(loaded.isExemptFromBlueprint("minecraft:crafting_table"));
        assertFalse(loaded.isExemptFromBlueprint("minecraft:diamond_sword"), "铁以上装备需要蓝图");
        assertFalse(loaded.isExemptFromBlueprint("minecraft:oak_stairs"), "建材需要蓝图");
    }

    @Test
    void exemptNamespaceFreesAWholeMod(@TempDir Path dir) throws Exception {
        Path config = write(dir, """
                {"schemaVersion": 1, "enabled": true,
                 "exemptNamespaces": ["  Create  "]}
                """);

        BlueprintConfig loaded = BlueprintConfig.load(config);

        assertTrue(loaded.isExemptFromBlueprint("create:cogwheel"), "赦免命名空间大小写不敏感");
        assertFalse(loaded.isExemptFromBlueprint("createadditions:cogwheel"));
        assertFalse(loaded.isInBlueprintPool("create:cogwheel"), "免蓝图的物品不该出现在抽取池里");
    }

    @Test
    void blacklistedItemLeavesThePoolButIsStillBlocked(@TempDir Path dir) throws Exception {
        Path config = write(dir, """
                {"schemaVersion": 1, "enabled": true,
                 "blueprintBlacklist": ["minecraft:beacon"]}
                """);

        BlueprintConfig loaded = BlueprintConfig.load(config);

        assertFalse(loaded.isInBlueprintPool("minecraft:beacon"), "黑名单物品不进抽取池");
        assertFalse(loaded.isExemptFromBlueprint("minecraft:beacon"),
                "黑名单只挡抽取，不放行——这是刻意的：这类物品应当有别的获取途径");
    }

    @Test
    void whitelistRestrictsThePool(@TempDir Path dir) throws Exception {
        Path config = write(dir, """
                {"schemaVersion": 1, "enabled": true,
                 "blueprintWhitelist": ["minecraft:diamond_*"]}
                """);

        BlueprintConfig loaded = BlueprintConfig.load(config);

        assertTrue(loaded.isInBlueprintPool("minecraft:diamond_sword"));
        assertFalse(loaded.isInBlueprintPool("minecraft:iron_sword"), "白名单非空时只放白名单内的");
    }

    @Test
    void emptyWhitelistMeansNoExtraRestriction(@TempDir Path dir) throws Exception {
        Path config = write(dir, """
                {"schemaVersion": 1, "enabled": true, "blueprintWhitelist": []}
                """);

        BlueprintConfig loaded = BlueprintConfig.load(config);

        assertTrue(loaded.isInBlueprintPool("minecraft:iron_sword"));
        assertTrue(loaded.isInBlueprintPool("dreamingfishcore:settlement_filter"),
                "模组自带配方同样要蓝图");
    }

    @Test
    void outOfRangePercentIsClampedAndWrittenBack(@TempDir Path dir) throws Exception {
        Path config = write(dir, """
                {"schemaVersion": 1, "enabled": true,
                 "siegeZombieDropPercent": 150, "chestDropPercent": -5}
                """);

        BlueprintConfig loaded = BlueprintConfig.load(config);

        assertEquals(100.0D, loaded.getSiegeZombieDropPercent(), 1.0E-9D);
        assertEquals(0.0D, loaded.getChestDropPercent(), 1.0E-9D);
        assertTrue(Files.readString(config, StandardCharsets.UTF_8).contains("100.0"),
                "修正结果应写回文件");
    }

    @Test
    void damagedFileFallsBackToDefaultsWithoutOverwriting(@TempDir Path dir) throws Exception {
        String damaged = "{ not json at all";
        Path config = write(dir, damaged);

        BlueprintConfig loaded = BlueprintConfig.load(config);

        assertFalse(loaded.isEnabled(), "损坏时回落到默认（关闭）");
        assertEquals(0.0D, loaded.getSiegeZombieDropPercent(), 1.0E-9D);
        assertEquals(damaged, Files.readString(config, StandardCharsets.UTF_8),
                "损坏的服主文件不能被默认值覆盖");
    }

    @Test
    void unknownSchemaVersionFallsBackToDefaults(@TempDir Path dir) throws Exception {
        Path config = write(dir, """
                {"schemaVersion": 99, "enabled": true}
                """);

        BlueprintConfig loaded = BlueprintConfig.load(config);

        assertFalse(loaded.isEnabled());
    }

    @Test
    void blankEntriesInListsAreStripped(@TempDir Path dir) throws Exception {
        Path config = write(dir, """
                {"schemaVersion": 1, "enabled": true,
                 "exemptNamespaces": ["create", "  ", ""]}
                """);

        BlueprintConfig loaded = BlueprintConfig.load(config);

        assertEquals(List.of("create"), loaded.getExemptNamespaces());
        String writtenBack = Files.readString(config, StandardCharsets.UTF_8);
        assertFalse(writtenBack.contains("\"\""), "空白项应被清掉并写回：" + writtenBack);
    }

    @Test
    void percentClampHandlesNaNAndInfinity() {
        assertEquals(0.0D, BlueprintConfig.clampPercent(Double.NaN), 1.0E-9D);
        assertEquals(0.0D, BlueprintConfig.clampPercent(Double.POSITIVE_INFINITY), 1.0E-9D);
        assertEquals(30.0D, BlueprintConfig.clampPercent(30.0D), 1.0E-9D);
    }
}
