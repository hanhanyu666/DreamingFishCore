package com.hhy.dreamingfishcore.gameplay.raid_system.loot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * 开发存档里的示例数据包必须能被**真正的解析器**读懂。
 *
 * <p>为什么值得单独写一条：给服主放进存档的配置是**手写 JSON**，字段名写错一个字母
 * （例如 {@code spawn_cost} 写成 {@code spawnCost}）不会报错，只会静默地走默认值或整个条目被跳过，
 * 表现为"游戏里什么都没刷"。这条测试用与运行时同一套拆解与解析代码去读那份文件，
 * 写错字段就会在这里红。</p>
 *
 * <p>文件不存在时自动跳过（示例数据包放在被 gitignore 的开发存档里，别人机器上可能没有）。</p>
 */
class LootConfigFileTest {

    private static final Path SAVE_PACK = Path.of("run", "saves", "新的世界", "datapacks",
            "dreamingfishcore_raid", "data", "dreamingfishcore");

    private static List<JsonObject> read(Path path) throws IOException {
        JsonElement element;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            element = JsonParser.parseReader(reader);
        }
        List<JsonObject> objects = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        RaidLootService.collectObjects(ResourceLocation.fromNamespaceAndPath("dreamingfishcore",
                path.getFileName().toString()), element, objects, problems);
        assertTrue(problems.isEmpty(), () -> "拆解文件时不该有问题：" + problems);
        return objects;
    }

    @Test
    void starterItemTableParsesWithTheRealParser() throws IOException {
        Path path = SAVE_PACK.resolve("raid_items").resolve("starter.json");
        assumeTrue(Files.isRegularFile(path), "开发存档里没有示例物品表，跳过");

        List<JsonObject> objects = read(path);
        assertFalse(objects.isEmpty(), "物品表应当拆出至少一条物品");

        int parsed = 0;
        for (JsonObject json : objects) {
            LootConfig.ParseResult<LootConfig.Item> result = LootConfig.Item.fromJson(json);
            assertTrue(result.ok(), () -> "物品条目解析失败：" + json + " -> " + result.problems());
            assertTrue(result.problems().isEmpty(),
                    () -> "物品条目有可选字段写错：" + result.value().itemId() + " -> " + result.problems());
            assertTrue(result.value().spawnCost() >= 1, "生成成本必须 ≥1");
            assertTrue(result.value().rarityWeight() >= 1, "抽取权重必须 ≥1，否则永远抽不到");
            parsed++;
        }
        assertTrue(parsed >= 5, "示例物品表不该这么少，实际 " + parsed);
    }

    @Test
    void starterZoneTemplateParsesWithTheRealParser() throws IOException {
        Path path = SAVE_PACK.resolve("raid_zones").resolve("factory_office.json");
        assumeTrue(Files.isRegularFile(path), "开发存档里没有示例区域模板，跳过");

        List<JsonObject> objects = read(path);
        assertFalse(objects.isEmpty(), "区域模板应当拆出至少一个区域");

        for (JsonObject json : objects) {
            LootConfig.ParseResult<LootConfig.Zone> result = LootConfig.Zone.fromJson(json);
            assertTrue(result.ok(), () -> "区域模板解析失败：" + json + " -> " + result.problems());
            assertTrue(result.problems().isEmpty(),
                    () -> "区域模板有字段写错：" + result.value().zoneId() + " -> " + result.problems());
            LootConfig.Zone zone = result.value();
            assertTrue(zone.budget().min() >= 1, "区域预算必须为正");
            assertTrue(zone.containerCount().max() >= zone.containerCount().min());
            // 稀有物品规则里的物品必须真的在物品表里，否则永远不会被放进去（配置里最容易漏的一环）
            for (LootConfig.RareRule rule : zone.rareRules()) {
                boolean found = false;
                Path items = SAVE_PACK.resolve("raid_items").resolve("starter.json");
                if (Files.isRegularFile(items)) {
                    for (JsonObject itemJson : read(items)) {
                        LootConfig.Item item = LootConfig.Item.fromJson(itemJson).value();
                        if (item != null && item.itemId().equals(rule.itemId())) {
                            found = true;
                            break;
                        }
                    }
                }
                assertTrue(found, "稀有物品规则引用了物品表里没有的物品：" + rule.itemId());
            }
        }
    }
}
