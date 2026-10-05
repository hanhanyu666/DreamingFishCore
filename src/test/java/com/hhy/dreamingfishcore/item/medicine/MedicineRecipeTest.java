package com.hhy.dreamingfishcore.item.medicine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * 药物配方的数据守卫。
 *
 * <p>配方 JSON 里物品 id 或图案打错**都不会报错**：游戏只会静默用别的物品、
 * 或者整条配方失效，服主到游戏里才发现"合成不出来"。所以把数据文件读进来，
 * 把图案展开成"物品 → 数量"再断言，等价于在游戏里数一遍格子。</p>
 */
class MedicineRecipeTest {

    private static JsonObject recipe(String name) throws Exception {
        String path = "/data/dreamingfishcore/recipe/" + name + ".json";
        try (InputStream stream = MedicineRecipeTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "找不到配方文件：" + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    /** 把有序配方的图案展开成 物品id -> 数量（空格与 "." 视为空位）。 */
    private static Map<String, Integer> shapedMaterials(JsonObject recipe) {
        JsonObject keys = recipe.getAsJsonObject("key");
        JsonArray pattern = recipe.getAsJsonArray("pattern");
        Map<String, Integer> counts = new TreeMap<>();
        for (JsonElement row : pattern) {
            for (char symbol : row.getAsString().toCharArray()) {
                if (symbol == ' ' || symbol == '.') {
                    continue;
                }
                String id = keys.getAsJsonObject(String.valueOf(symbol)).get("item").getAsString();
                counts.merge(id, 1, Integer::sum);
            }
        }
        return counts;
    }

    private static Map<String, Integer> shapelessMaterials(JsonObject recipe) {
        Map<String, Integer> counts = new TreeMap<>();
        for (JsonElement ingredient : recipe.getAsJsonArray("ingredients")) {
            counts.merge(ingredient.getAsJsonObject().get("item").getAsString(), 1, Integer::sum);
        }
        return counts;
    }

    private static String resultId(JsonObject recipe) {
        return recipe.getAsJsonObject("result").get("id").getAsString();
    }

    // ---------------------------------------------------------------- 感染抑制剂

    @Test
    void suppressantIsHerbPlusTwoPaper() throws Exception {
        JsonObject recipe = recipe("infection_suppressant");
        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
        assertEquals("dreamingfishcore:infection_suppressant", resultId(recipe));
        assertEquals(Map.of("dreamingfishcore:herb", 1, "minecraft:paper", 2), shapelessMaterials(recipe));
    }

    @Test
    void strongSuppressantAddsTwoGlowBerries() throws Exception {
        JsonObject recipe = recipe("strong_infection_suppressant");
        assertEquals("dreamingfishcore:strong_infection_suppressant", resultId(recipe));
        assertEquals(Map.of("dreamingfishcore:herb", 1, "minecraft:paper", 2,
                "minecraft:glow_berries", 2), shapelessMaterials(recipe));
    }

    // ---------------------------------------------------------------- 急救包

    @Test
    void easyAidKitIsHerbSurroundedByFourGoldAndFourIronNuggets() throws Exception {
        JsonObject recipe = recipe("easy_aid_kit");
        assertEquals("dreamingfishcore:easy_aid_kit", resultId(recipe));
        assertEquals(Map.of("dreamingfishcore:herb", 1,
                "minecraft:gold_nugget", 4, "minecraft:iron_nugget", 4), shapedMaterials(recipe),
                "急救包 = 中心药草 + 四周 4 金粒 + 4 铁粒（共 9 格全满）");
        // 九格必须全满：中心一格 + 外圈八格
        int used = shapedMaterials(recipe).values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(9, used, "图案应该正好填满 3x3");
    }

    @Test
    void advancedAidKitSurroundsTheBasicOneWithFourHerbs() throws Exception {
        JsonObject recipe = recipe("advanced_aid_kit");
        assertEquals("dreamingfishcore:advanced_aid_kit", resultId(recipe));
        assertEquals(Map.of("dreamingfishcore:easy_aid_kit", 1, "dreamingfishcore:herb", 4),
                shapedMaterials(recipe), "高级急救包 = 初级急救包 + 围 4 个药草");
    }

    // ---------------------------------------------------------------- 复苏符

    @Test
    void revivalCharmNeedsTwoEnchantedApplesThreeCarrotsAndADiamond() throws Exception {
        JsonObject recipe = recipe("revival_charm");
        assertEquals("dreamingfishcore:revival_charm", resultId(recipe));
        assertEquals(Map.of("minecraft:enchanted_golden_apple", 2,
                "minecraft:golden_carrot", 3, "minecraft:diamond", 1), shapedMaterials(recipe),
                "复苏符 = 2 附魔金苹果 + 3 金胡萝卜 + 1 钻石");
    }

    @Test
    void everyMedicineRecipeProducesExactlyOne() throws Exception {
        for (String name : new String[]{"infection_suppressant", "strong_infection_suppressant",
                "easy_aid_kit", "advanced_aid_kit", "revival_charm"}) {
            assertEquals(1, recipe(name).getAsJsonObject("result").get("count").getAsInt(),
                    name + " 一次合成一个");
        }
    }
}