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
 * 感染抑制剂两档配方的数据守卫。
 *
 * <p>配方 JSON 里物品 id 打错**不会报错**：游戏只会静默用别的物品，或整条配方失效，
 * 服主到游戏里才发现"合成不出来"。所以把数据文件读进来断言材料清单精确匹配
 * （初级：药草 + 纸×2；高级：药草 + 纸×2 + 荧光浆果×2）。</p>
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

    private static Map<String, Integer> materials(JsonObject recipe) {
        Map<String, Integer> counts = new TreeMap<>();
        for (JsonElement ingredient : recipe.getAsJsonArray("ingredients")) {
            String id = ingredient.getAsJsonObject().get("item").getAsString();
            counts.merge(id, 1, Integer::sum);
        }
        return counts;
    }

    @Test
    void suppressantIsHerbPlusTwoPaper() throws Exception {
        JsonObject recipe = recipe("infection_suppressant");
        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
        assertEquals("dreamingfishcore:infection_suppressant",
                recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(Map.of("dreamingfishcore:herb", 1, "minecraft:paper", 2), materials(recipe),
                "感染抑制剂必须是 药草 + 纸×2，多一样少一样都说明被改错了");
    }

    @Test
    void strongSuppressantAddsTwoGlowBerries() throws Exception {
        JsonObject recipe = recipe("strong_infection_suppressant");
        assertEquals("dreamingfishcore:strong_infection_suppressant",
                recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(Map.of("dreamingfishcore:herb", 1, "minecraft:paper", 2,
                "minecraft:glow_berries", 2), materials(recipe),
                "强效感染抑制剂 = 药草 + 纸×2 + 荧光浆果×2");
    }

    @Test
    void aidKitsHaveNoRecipeYet() {
        // 我一度误把"初级/高级"当成急救包并加了配方；记录一笔，避免以后有人当成漏做又加回来。
        assertEquals(null, MedicineRecipeTest.class.getResource(
                "/data/dreamingfishcore/recipe/easy_aid_kit.json"), "急救包配方不该存在（服主指的是抑制剂）");
        assertEquals(null, MedicineRecipeTest.class.getResource(
                "/data/dreamingfishcore/recipe/advanced_aid_kit.json"), "高级急救包配方同理");
    }
}