package com.hhy.dreamingfishcore.item.items;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 物品模型的资源护栏：把两类真实踩过的坑钉死。
 *
 * <p>坑一：Blockbench 导出的是 **Bedrock 格式**，元素旋转写成 {@code {"x":0,"y":-90,"z":0,"origin":[…]}}，
 * 而 Java 版只认 {@code {"origin":[…] , "axis":"y", "angle":-90}}。直接丢进 {@code models/item}
 * 不会报错，只是模型静静地不转、游戏里看着歪。</p>
 *
 * <p>坑二：Blockbench 模板会留下别人的贴图名（我们的文件里就写着 {@code "苹果饼干"}），
 * 拼错或忘改时同样不报错，客户端只会显示紫黑方块。</p>
 *
 * <p>另外顺手挡一条：带 {@code elements} 的三维物品模型如果没有 {@code gui} 变换，
 * 在背包/快捷栏里会按方块体积渲染、大到溢出格子。</p>
 */
class ItemModelResourceTest {

    private static final Gson GSON = new Gson();
    private static final String MOD_ID = "dreamingfishcore";

    /** 两种感染抑制剂的物品 ID：与 Item 注册名保持一致。 */
    private static final List<String> ITEM_IDS = List.of(
            "infection_suppressant", "strong_infection_suppressant");

    /**
     * Java 版元素旋转允许的角度。
     *
     * <p>不是随便写的：原版 {@code BlockElement.Deserializer#getAngle} 只放行这几个值，
     * 其余一律抛 {@code JsonParseException: Invalid rotation}，整个模型加载失败（表现为
     * 物品显示不出来）。Blockbench 从 Bedrock 格式导出的模型常见任意角度，
     * 直接搬过来就会踩这个坑。</p>
     */
    private static final List<Float> ALLOWED_ELEMENT_ANGLES =
            List.of(-45.0F, -22.5F, 0.0F, 22.5F, 45.0F);

    @Test
    void itemModelsUseJavaFormatAndResolveTheirTextures() throws Exception {
        for (String itemId : ITEM_IDS) {
            String resource = "/assets/" + MOD_ID + "/models/item/" + itemId + ".json";
            JsonObject model = readJson(resource);
            assertNotNull(model, "缺少物品模型：" + resource);

            if (model.has("elements")) {
                assertJavaRotationFormat(model, itemId);
                assertElementShapeIsValid(model, itemId);
                assertTrue(model.has("display") && model.getAsJsonObject("display").has("gui"),
                        "三维物品模型 " + itemId + " 必须给 gui 变换，否则背包里会大到溢出格子");
            }

            JsonObject textures = model.getAsJsonObject("textures");
            assertNotNull(textures, "模型没有 textures 段：" + itemId);
            for (String key : textures.keySet()) {
                String reference = textures.get(key).getAsString();
                assertTrue(reference.contains(":"),
                        "贴图引用必须是 命名空间:路径（模板遗留的裸名字会导致紫黑方块）："
                                + itemId + " → " + reference);
                String[] parts = reference.split(":", 2);
                String png = "/assets/" + parts[0] + "/textures/" + parts[1] + ".png";
                try (InputStream stream = ItemModelResourceTest.class.getResourceAsStream(png)) {
                    assertNotNull(stream, "模型引用的贴图不存在：" + itemId + " → " + png);
                }
            }
        }
    }

    @Test
    void eachItemModelIsDefinedInExactlyOneSourceRoot() {
        Path root = projectRoot();
        if (root == null) {
            return;   // 拿不到仓库根（例如在别的目录跑测试）时跳过，不影响主断言
        }
        for (String itemId : ITEM_IDS) {
            String relative = "assets/" + MOD_ID + "/models/item/" + itemId + ".json";
            Path main = root.resolve("src/main/resources").resolve(relative);
            Path generated = root.resolve("src/generated/resources").resolve(relative);
            long count = (Files.exists(main) ? 1 : 0) + (Files.exists(generated) ? 1 : 0);
            assertTrue(count == 1,
                    "物品模型 " + itemId + " 必须恰好定义在一处（手写模型与 datagen 各写一份会互相覆盖）："
                            + "main=" + Files.exists(main) + " generated=" + Files.exists(generated));
        }
    }

    private static void assertJavaRotationFormat(JsonObject model, String itemId) {
        for (var element : model.getAsJsonArray("elements")) {
            JsonObject faceRoot = element.getAsJsonObject();
            if (!faceRoot.has("rotation")) {
                continue;
            }
            JsonObject rotation = faceRoot.getAsJsonObject("rotation");
            assertTrue(rotation.has("axis") && rotation.has("angle"),
                    "元素旋转必须是 Java 格式（axis + angle）：" + itemId + " → " + rotation);
            assertTrue(!rotation.has("x") && !rotation.has("y") && !rotation.has("z"),
                    "元素旋转看起来是 Bedrock 格式（x/y/z 角度），需要转成 axis + angle："
                            + itemId + " → " + rotation);
            assertTrue(rotation.has("origin"),
                    "元素旋转必须带 origin 枢轴：" + itemId + " → " + rotation);

            String axis = rotation.get("axis").getAsString();
            assertTrue(List.of("x", "y", "z").contains(axis),
                    "元素旋转轴只能是 x/y/z：" + itemId + " → " + axis);

            // 真正的坑：Java 的元素旋转只接受这几个角度（BlockElement.Deserializer#getAngle），
            // Blockbench 从 Bedrock 导出的任意角度（例如 -90）会让整个模型加载失败。
            float angle = rotation.get("angle").getAsFloat();
            assertTrue(ALLOWED_ELEMENT_ANGLES.contains(angle),
                    "元素旋转角度非法：Java 只允许 " + ALLOWED_ELEMENT_ANGLES + "，实际 "
                            + angle + "（" + itemId + "）；需要 -90 之类的朝向请改用 display 变换");
        }
    }

    /** 元素几何与面的基本形状校验：对齐原版反序列化器会拒绝的情况。 */
    private static void assertElementShapeIsValid(JsonObject model, String itemId) {
        for (var element : model.getAsJsonArray("elements")) {
            JsonObject box = element.getAsJsonObject();
            assertTrue(box.has("from") && box.has("to"),
                    "元素缺少 from/to：" + itemId);
            assertTrue(box.getAsJsonArray("from").size() == 3
                            && box.getAsJsonArray("to").size() == 3,
                    "元素 from/to 必须是三个数：" + itemId);
            JsonObject faces = box.getAsJsonObject("faces");
            assertTrue(faces != null && !faces.isEmpty(), "元素没有任何面：" + itemId);
            for (String direction : faces.keySet()) {
                assertTrue(List.of("north", "south", "east", "west", "up", "down")
                                .contains(direction),
                        "非法面方向：" + direction + "（" + itemId + "）");
                JsonObject face = faces.getAsJsonObject(direction);
                assertTrue(face.has("uv") && face.getAsJsonArray("uv").size() == 4,
                        "面的 uv 必须是四个数：" + itemId + " → " + direction);
            }
        }
    }

    private static JsonObject readJson(String resource) throws Exception {
        try (InputStream stream = ItemModelResourceTest.class.getResourceAsStream(resource)) {
            if (stream == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return GSON.fromJson(reader, JsonObject.class);
            }
        }
    }

    /** 从工作目录向上找仓库根（含 gradle.properties 与 src/main/resources 的那一层）。 */
    private static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 6 && current != null; depth++) {
            if (Files.exists(current.resolve("gradle.properties"))
                    && Files.exists(current.resolve("src/main/resources"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }
}
