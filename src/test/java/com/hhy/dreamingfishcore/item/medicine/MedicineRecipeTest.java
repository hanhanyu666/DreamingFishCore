package com.hhy.dreamingfishcore.item.medicine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
 * <p>配方 JSON 里物品 id 打错**不会报错**：游戏只会静默用别的物品，或整条配方失效，
 * 服主到游戏里才发现"合成不出来"。所以这里把数据文件读进来，断言产物与材料清单
 * 与服主定的公式一致（初级：药草 + 纸×2；高级：药草 + 纸×2 + 荧光浆果×2）。</p>
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

    /** 把 ingredients 汇总成 物品id -> 数量（同种材料会合并计数）。 */
    private static Map<String, Integer> materials(JsonObject recipe) {
        Map<String, Integer> counts = new TreeMap<>();
        for (JsonElement ingredient : recipe.getAsJsonArray("ingredients")) {
            String id = ingredient.getAsJsonObject().get("item").getAsString();
            counts.merge(id, 1, Integer::sum);
        }
        return counts;
    }

    @Test
    void easyAidKitIsHerbPlusTwoPaper() throws Exception {
        JsonObject recipe = recipe("easy_aid_kit");
        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
        assertEquals("dreamingfishcore:easy_aid_kit",
                recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(Map.of("dreamingfishcore:herb", 1, "minecraft:paper", 2), materials(recipe),
                "初级急救包必须是 药草 + 纸×2，多一样少一样都说明被改错了");
    }

    @Test
    void advancedAidKitAddsTwoGlowBerries() throws Exception {
        JsonObject recipe = recipe("advanced_aid_kit");
        assertEquals("dreamingfishcore:advanced_aid_kit",
                recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(Map.of("dreamingfishcore:herb", 1, "minecraft:paper", 2,
                "minecraft:glow_berries", 2), materials(recipe),
                "高级急救包 = 药草 + 纸×2 + 荧光浆果×2");
    }

    @Test
    void everyMedicineRecipeProducesExactlyOne() throws Exception {
        for (String name : new String[]{"easy_aid_kit", "advanced_aid_kit"}) {
            JsonObject result = recipe(name).getAsJsonObject("result");
            assertEquals(1, result.get("count").getAsInt(), name + " 一次合成一个");
        }
    }
}
