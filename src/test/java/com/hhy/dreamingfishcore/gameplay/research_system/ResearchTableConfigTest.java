package com.hhy.dreamingfishcore.gameplay.research_system;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 研究桌配置：默认值、数量区间修正、命名空间过滤、损坏回退。 */
class ResearchTableConfigTest {

    private static Path write(Path dir, String json) throws Exception {
        Path path = dir.resolve("research_table.json");
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return path;
    }

    @Test
    void missingFileCreatesDefaults(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("research_table.json");

        ResearchTableConfig config = ResearchTableConfig.load(path);

        assertTrue(Files.exists(path), "缺失配置应写入默认文件");
        assertTrue(config.isEnabled());
        assertEquals(100, config.getCostExperiencePoints());
        assertEquals(10, config.getMinRecipes());
        assertEquals(15, config.getMaxRecipes());
        assertEquals(List.of("minecraft"), config.getNamespaces());
        assertTrue(config.isSkipLearned());
        assertEquals(4, config.getSubmitDivisor(), "默认提交四分之一组");
    }

    // ==================== 提交除数（四分之一组） ====================

    @Test
    void submitDivisorDefaultsToFourWhenFieldMissing(@TempDir Path dir) throws Exception {
        // 老配置文件里没有这个字段：必须落到默认值 4，而不是 0（那会变成"交 0 个白拿配方"）。
        Path config = write(dir, """
                {"schemaVersion": 1, "costExperiencePoints": 100}
                """);

        ResearchTableConfig loaded = ResearchTableConfig.load(config);

        assertEquals(ResearchTableConfig.DEFAULT_SUBMIT_DIVISOR, loaded.getSubmitDivisor());
        assertEquals(16, ResearchMath.requiredSubmitCount(64, loaded.getSubmitDivisor()));
    }

    @Test
    void submitDivisorZeroIsClampedToOne(@TempDir Path dir) throws Exception {
        Path config = write(dir, """
                {"schemaVersion": 1, "submitDivisor": 0}
                """);

        ResearchTableConfig loaded = ResearchTableConfig.load(config);

        assertEquals(ResearchTableConfig.MIN_SUBMIT_DIVISOR, loaded.getSubmitDivisor());
        assertEquals(64, ResearchMath.requiredSubmitCount(64, loaded.getSubmitDivisor()),
                "除数为 1 时提交一整组，绝不能变成 0 个");
    }

    @Test
    void negativeSubmitDivisorIsClamped(@TempDir Path dir) throws Exception {
        Path config = write(dir, """
                {"schemaVersion": 1, "submitDivisor": -20}
                """);

        ResearchTableConfig loaded = ResearchTableConfig.load(config);

        assertEquals(ResearchTableConfig.MIN_SUBMIT_DIVISOR, loaded.getSubmitDivisor());
    }

    @Test
    void absurdSubmitDivisorIsClampedToLimit(@TempDir Path dir) throws Exception {
        Path config = write(dir, """
                {"schemaVersion": 1, "submitDivisor": 9999}
                """);

        ResearchTableConfig loaded = ResearchTableConfig.load(config);

        assertEquals(ResearchTableConfig.MAX_SUBMIT_DIVISOR, loaded.getSubmitDivisor());
        // 夹到 64 之后，不可堆叠的物品依然只要 1 个（不会出现"需要 0 个"或"需要 1/64 个"）。
        assertEquals(1, ResearchMath.requiredSubmitCount(1, loaded.getSubmitDivisor()));
    }

    @Test
    void damagedFileWithSubmitDivisorFallsBackToDefaults(@TempDir Path dir) throws Exception {
        String damaged = "{ \"schemaVersion\": 1, \"submitDivisor\": ";
        Path config = write(dir, damaged);

        ResearchTableConfig loaded = ResearchTableConfig.load(config);

        assertEquals(ResearchTableConfig.DEFAULT_SUBMIT_DIVISOR, loaded.getSubmitDivisor());
        assertEquals(damaged, Files.readString(config, StandardCharsets.UTF_8),
                "损坏的服主文件不能被默认值覆盖");
    }

    @Test
    void minAboveMaxIsPulledDownToMax(@TempDir Path dir) throws Exception {
        Path config = write(dir, """
                {"schemaVersion": 1, "minRecipes": 20, "maxRecipes": 15}
                """);

        ResearchTableConfig loaded = ResearchTableConfig.load(config);

        assertEquals(15, loaded.getMaxRecipes());
        assertEquals(15, loaded.getMinRecipes(), "最小值不能大于最大值");
    }

    @Test
    void absurdMaxIsClampedToLimit(@TempDir Path dir) throws Exception {
        Path config = write(dir, """
                {"schemaVersion": 1, "minRecipes": 10, "maxRecipes": 9999}
                """);

        ResearchTableConfig loaded = ResearchTableConfig.load(config);

        assertEquals(ResearchTableConfig.MAX_RECIPES_LIMIT, loaded.getMaxRecipes(),
                "否则一次研究能把整本配方书倒给玩家");
        assertEquals(10, loaded.getMinRecipes());
    }

    @Test
    void negativeCostBecomesZero(@TempDir Path dir) throws Exception {
        Path config = write(dir, """
                {"schemaVersion": 1, "costExperiencePoints": -50}
                """);

        ResearchTableConfig loaded = ResearchTableConfig.load(config);

        assertEquals(0, loaded.getCostExperiencePoints());
    }

    @Test
    void namespaceFilterDefaultsToVanillaOnly(@TempDir Path dir) throws Exception {
        Path config = write(dir, """
                {"schemaVersion": 1}
                """);

        ResearchTableConfig loaded = ResearchTableConfig.load(config);

        assertTrue(loaded.allowsNamespace("minecraft:iron_sword"));
        assertFalse(loaded.allowsNamespace("create:cogwheel"));
        assertFalse(loaded.allowsNamespace("dreamingfishcore:settlement_filter"));
    }

    @Test
    void emptyNamespaceListMeansNoRestriction(@TempDir Path dir) throws Exception {
        Path config = write(dir, """
                {"schemaVersion": 1, "namespaces": []}
                """);

        ResearchTableConfig loaded = ResearchTableConfig.load(config);

        assertTrue(loaded.allowsNamespace("create:cogwheel"), "空列表表示不限命名空间");
    }

    @Test
    void namespaceMatchingIsCaseInsensitiveAndTrimmed(@TempDir Path dir) throws Exception {
        Path config = write(dir, """
                {"schemaVersion": 1, "namespaces": ["  Create ", "Minecraft"]}
                """);

        ResearchTableConfig loaded = ResearchTableConfig.load(config);

        assertTrue(loaded.allowsNamespace("create:cogwheel"));
        assertEquals(2, loaded.getNamespaces().size());
        assertFalse(loaded.allowsNamespace("createadditions:cogwheel"));
    }

    @Test
    void blankNamespacesAreStripped(@TempDir Path dir) throws Exception {
        Path config = write(dir, """
                {"schemaVersion": 1, "namespaces": ["minecraft", "   ", ""]}
                """);

        ResearchTableConfig loaded = ResearchTableConfig.load(config);

        assertEquals(List.of("minecraft"), loaded.getNamespaces());
    }

    @Test
    void damagedFileFallsBackToDefaultsWithoutOverwriting(@TempDir Path dir) throws Exception {
        String damaged = "{ not json";
        Path config = write(dir, damaged);

        ResearchTableConfig loaded = ResearchTableConfig.load(config);

        assertTrue(loaded.isEnabled());
        assertEquals(100, loaded.getCostExperiencePoints());
        assertEquals(damaged, Files.readString(config, StandardCharsets.UTF_8),
                "损坏的服主文件不能被默认值覆盖");
    }

    @Test
    void unknownSchemaVersionFallsBackToDefaults(@TempDir Path dir) throws Exception {
        Path config = write(dir, """
                {"schemaVersion": 99, "costExperiencePoints": 5}
                """);

        ResearchTableConfig loaded = ResearchTableConfig.load(config);

        assertEquals(100, loaded.getCostExperiencePoints(), "版本不认识就回落到默认值");
    }
}
