package com.hhy.dreamingfishcore.gameplay.water_gun_system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * 呲水枪配方的数据守卫测试。
 *
 * <p>为什么值得单独测：配方 JSON 里的物品 id 打错了**不会报错**——游戏只会静默地用另一个物品、
 * 或者整条配方不生效，服主到游戏里才发现"合成不出来"。这里直接把数据文件读进来断言关键事实：
 * 产物是水枪、材料恰好是玻璃板 + 空桶 + 铁锭 + 铁粒（服主指定的四样）。</p>
 */
class WaterGunRecipeTest {

    private static final String RECIPE_PATH = "/data/dreamingfishcore/recipe/water_gun.json";

    private static JsonObject recipe() throws Exception {
        try (InputStream stream = WaterGunRecipeTest.class.getResourceAsStream(RECIPE_PATH)) {
            assertNotNull(stream, "找不到配方文件：" + RECIPE_PATH);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    @Test
    void recipeProducesTheWaterGun() throws Exception {
        JsonObject recipe = recipe();
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString(),
                "应该是工作台有序合成");
        JsonObject result = recipe.getAsJsonObject("result");
        assertEquals("dreamingfishcore:water_gun", result.get("id").getAsString(),
                "产物必须是呲水枪");
        assertEquals(1, result.get("count").getAsInt(), "一次合成一把");
    }

    @Test
    void materialsAreExactlyGlassPaneBucketIronIngotAndNugget() throws Exception {
        JsonObject keys = recipe().getAsJsonObject("key");
        Set<String> materials = new TreeSet<>();
        for (JsonElement element : keys.asMap().values()) {
            materials.add(element.getAsJsonObject().get("item").getAsString());
        }
        assertEquals(Set.of("minecraft:glass_pane", "minecraft:bucket",
                "minecraft:iron_ingot", "minecraft:iron_nugget"), materials,
                "材料恰好是玻璃板、空桶、铁锭、铁粒四样，多一样少一样都说明配方被改错了");
    }

    @Test
    void patternIsThreeByThreeAndOnlyUsesDeclaredKeys() throws Exception {
        JsonObject recipe = recipe();
        JsonArray pattern = recipe.getAsJsonArray("pattern");
        assertEquals(3, pattern.size(), "应该是 3 行");
        Set<String> declared = recipe().getAsJsonObject("key").keySet();
        for (JsonElement row : pattern) {
            String line = row.getAsString();
            assertEquals(3, line.length(), "每行 3 格：" + line);
            for (char symbol : line.toCharArray()) {
                assertTrue(declared.contains(String.valueOf(symbol)),
                        "图案里用了没在 key 里声明的符号：" + symbol);
            }
        }
    }

    @Test
    void patternConsumesTheWholeBucketAndAtLeastOneOfEachMaterial() throws Exception {
        // 空桶在图案里只能出现一次：它是"水箱"，两三个桶没有道理，也容易是复制粘贴写错
        JsonArray pattern = recipe().getAsJsonArray("pattern");
        StringBuilder all = new StringBuilder();
        pattern.forEach(row -> all.append(row.getAsString()));
        String grid = all.toString();
        assertEquals(1, grid.chars().filter(character -> character == 'B').count(),
                "空桶应该只出现一次（作为水箱）");
        assertTrue(grid.indexOf('G') >= 0, "玻璃板应该出现在图案里");
        assertTrue(grid.chars().filter(character -> character == 'N').count() >= 1,
                "铁粒应该出现在图案里");
        assertTrue(grid.chars().filter(character -> character == 'I').count() >= 1,
                "铁锭应该出现在图案里");
    }
}
