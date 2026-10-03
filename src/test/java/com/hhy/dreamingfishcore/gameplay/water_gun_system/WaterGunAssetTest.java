package com.hhy.dreamingfishcore.gameplay.water_gun_system;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 呲水枪与金刚僵尸相关资源的存在性与一致性。
 *
 * <p>守三类静默错误：贴图/模型缺失（游戏里变成紫黑方块或什么都不显示）、物品模型里的
 * {@code layer0} 指错路径（同样只会显示成缺失贴图）、以及语言键漏写或漏译。</p>
 *
 * <p>另外这里对金刚僵尸的四级皮肤做了一个**性质断言**：锈级越高，贴图整体应当越偏「锈红」。
 * 这比只检查文件存在有意义得多——它同时保证了「锈级真的接到了贴图上」这件事没有接反。</p>
 */
class WaterGunAssetTest {

    @Test
    void waterGunItemTextureAndModelArePresent() throws IOException {
        BufferedImage texture = readTexture(
                "/assets/dreamingfishcore/textures/item/water_gun.png");
        assertEquals(16, texture.getWidth());
        assertEquals(16, texture.getHeight());
        assertTrue(countOpaquePixels(texture) > 30, "水枪物品贴图不能是空图");

        JsonObject model = readJson("/assets/dreamingfishcore/models/item/water_gun.json");
        assertEquals("item/handheld", model.get("parent").getAsString(),
                "水枪是手持道具，用 handheld 才有握持角度");
        assertEquals("dreamingfishcore:item/water_gun",
                model.getAsJsonObject("textures").get("layer0").getAsString(),
                "layer0 必须指向刚生成的那张贴图，写错只会显示成缺失贴图");
    }

    @Test
    void waterJetTextureIsATranslucentBlob() throws IOException {
        BufferedImage image = readTexture(
                "/assets/dreamingfishcore/textures/entity/projectile/water_jet.png");
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
        int opaque = countOpaquePixels(image);
        assertTrue(opaque > 20, "水柱纹理必须有不透明像素，否则水柱是隐形的");
        assertTrue(opaque < 16 * 16, "水柱应当是柔边水团，不能铺满整张图（那样会是一个方块）");
    }

    @Test
    void adamantZombieSkinsUseVanillaZombieUv() throws IOException {
        for (String name : new String[] {
                "adamant_zombie.png",
                "adamant_zombie_rust1.png",
                "adamant_zombie_rust2.png",
                "adamant_zombie_rust3.png"}) {
            BufferedImage image = readTexture(
                    "/assets/dreamingfishcore/textures/entity/adamant_zombie/" + name);
            assertEquals(64, image.getWidth(), name + " 的宽度必须是原版僵尸 UV 的 64");
            assertEquals(64, image.getHeight(), name + " 的高度必须是原版僵尸 UV 的 64");
            assertTrue(countOpaquePixels(image) >= 1000, name + " 必须能盖住原版僵尸 UV 的可见面");
        }
    }

    /**
     * 锈级越高，贴图整体越偏红（锈色），且最终一级明显偏暖。
     *
     * <p>用「红色通道 - 蓝色通道」的均值当锈度指标：干净皮肤是冷灰（负值），锈色是暖褐（正值）。</p>
     */
    @Test
    void rustedSkinsGetProportionallyWarmer() throws IOException {
        double previous = Double.NEGATIVE_INFINITY;
        for (String name : new String[] {
                "adamant_zombie.png",
                "adamant_zombie_rust1.png",
                "adamant_zombie_rust2.png",
                "adamant_zombie_rust3.png"}) {
            double warmth = averageRedMinusBlue(readTexture(
                    "/assets/dreamingfishcore/textures/entity/adamant_zombie/" + name));
            assertTrue(warmth > previous,
                    name + " 的锈度应当高于上一级，实际 " + warmth + "，上一级 " + previous);
            previous = warmth;
        }
        assertTrue(previous > 30.0D, "锈透应明显偏暖（红-蓝均值），实际 " + previous);
    }

    @Test
    void effectIconsExist() throws IOException {
        for (String effect : new String[] {"rusted", "stagger"}) {
            BufferedImage image = readTexture(
                    "/assets/dreamingfishcore/textures/mob_effect/" + effect + ".png");
            assertEquals(16, image.getWidth(), effect + " 图标宽度应为 16");
            assertEquals(16, image.getHeight(), effect + " 图标高度应为 16");
            assertTrue(countOpaquePixels(image) > 20,
                    effect + " 图标不能是空图，否则 HUD 上是透明格子");
        }
    }

    @Test
    void displayNamesExistInBothLanguages() {
        String[] keys = {
                "item.dreamingfishcore.water_gun",
                "item.dreamingfishcore.water_gun.water",
                "item.dreamingfishcore.water_gun.usage",
                "item.dreamingfishcore.water_gun.empty",
                "item.dreamingfishcore.water_gun.refilled",
                "item.dreamingfishcore.adamant_zombie_spawn_egg",
                "entity.dreamingfishcore.adamant_zombie",
                "entity.dreamingfishcore.water_jet",
                "effect.dreamingfishcore.rusted",
                "effect.dreamingfishcore.stagger",
        };
        for (String resource : new String[] {
                "/assets/dreamingfishcore/lang/zh_cn.json",
                "/assets/dreamingfishcore/lang/en_us.json"}) {
            JsonObject root = readJson(resource);
            for (String key : keys) {
                assertTrue(root.has(key), resource + " 缺少语言键 " + key);
                assertTrue(!root.get(key).getAsString().isBlank(), resource + " 的 " + key + " 是空串");
            }
        }
    }

    /** tooltip 里的占位符必须和代码里传的参数个数对得上，否则游戏里会显示成 %s/%s 原样。 */
    @Test
    void waterAmountTooltipKeepsBothPlaceholders() {
        JsonObject zh = readJson("/assets/dreamingfishcore/lang/zh_cn.json");
        String water = zh.get("item.dreamingfishcore.water_gun.water").getAsString();
        assertEquals(2, water.split("%s", -1).length - 1, "水量提示需要两个 %s（当前/上限）：" + water);
        String refilled = zh.get("item.dreamingfishcore.water_gun.refilled").getAsString();
        assertEquals(2, refilled.split("%s", -1).length - 1, "装满提示需要两个 %s：" + refilled);
    }

    private static BufferedImage readTexture(String resource) throws IOException {
        try (InputStream input = WaterGunAssetTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, "missing resource: " + resource);
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image, "unreadable image: " + resource);
            return image;
        }
    }

    private static JsonObject readJson(String resource) {
        try (InputStream input = WaterGunAssetTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, "missing resource: " + resource);
            InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException exception) {
            throw new AssertionError("failed to read " + resource, exception);
        }
    }

    private static int countOpaquePixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    /** 不透明像素的「红色通道 - 蓝色通道」均值：冷灰为负，锈褐为正。 */
    private static double averageRedMinusBlue(BufferedImage image) {
        long total = 0;
        long count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) == 0) {
                    continue;
                }
                total += ((argb >> 16) & 0xFF) - (argb & 0xFF);
                count++;
            }
        }
        return count == 0 ? 0.0D : (double) total / (double) count;
    }
}
