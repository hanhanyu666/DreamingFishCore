package com.hhy.dreamingfishcore.gameplay.zombie_system.charred;

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
 * 焦尸相关资源的存在性与一致性。
 *
 * <p>守三类会静默出错的东西：贴图缺失或尺寸不对（换原版僵尸 UV 会直接渲染错乱但不报错）、
 * 眼睛层贴图不透明像素太多（整只怪会变成一块全亮的白板）、以及语言键漏写（游戏里显示成
 * {@code entity.dreamingfishcore.charred_zombie} 这种原始键名）。</p>
 *
 * <p>注意焦尸**没有** {@code sounds.json}：免疫反馈直接复用原版 {@code block.fire.extinguish}
 * 与灰烬粒子，不需要注册新音效事件。</p>
 */
class CharredZombieAssetTest {

    @Test
    void charredZombiePlaceholderTextureUsesVanillaZombieUv() throws IOException {
        BufferedImage image = readTexture(
                "/assets/dreamingfishcore/textures/entity/charred_zombie/charred_zombie.png");
        assertEquals(64, image.getWidth());
        assertEquals(64, image.getHeight());
        assertTrue(countOpaquePixels(image) >= 1000, "占位皮肤也要能盖住原版僵尸 UV 的可见面");
    }

    /**
     * 眼睛层必须「几乎全透明」。
     *
     * <p>{@code RenderType.eyes} 走满亮度叠加渲染，只要不透明像素一多，整只怪就会变成一块
     * 全亮的白板——这是眼睛层最容易踩的坑，所以这里同时卡上下界。</p>
     */
    @Test
    void eyesOverlayOnlyLightsUpTheEyePixels() throws IOException {
        BufferedImage image = readTexture(
                "/assets/dreamingfishcore/textures/entity/charred_zombie/charred_zombie_eyes.png");
        assertEquals(64, image.getWidth());
        assertEquals(64, image.getHeight());
        int opaque = countOpaquePixels(image);
        assertTrue(opaque > 0, "眼睛层不能是空图，否则焦尸脸上没有任何活着的迹象");
        assertTrue(opaque <= 16, "眼睛层的不透明像素必须极少，否则会整只全亮，实际 " + opaque);
    }

    @Test
    void vulnerableEffectIconExists() throws IOException {
        BufferedImage image = readTexture(
                "/assets/dreamingfishcore/textures/mob_effect/vulnerable.png");
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
        assertTrue(countOpaquePixels(image) > 20, "易损图标不能是空图，否则 HUD 上是透明格子");
    }

    @Test
    void displayNamesExistInBothLanguages() {
        assertHasKeys("/assets/dreamingfishcore/lang/zh_cn.json",
                "entity.dreamingfishcore.charred_zombie",
                "item.dreamingfishcore.charred_zombie_spawn_egg",
                "effect.dreamingfishcore.vulnerable");
        assertHasKeys("/assets/dreamingfishcore/lang/en_us.json",
                "entity.dreamingfishcore.charred_zombie",
                "item.dreamingfishcore.charred_zombie_spawn_egg",
                "effect.dreamingfishcore.vulnerable");
    }

    /**
     * 「易损」是这套机制唯一的反馈信号，中英文都必须是真的译名。
     *
     * <p>钉死到具体字符串而不是只查「键存在」：漏译时游戏会把原始键名直接显示在 HUD 上，
     * 而「键存在」这件事并不能发现这种情况。</p>
     */
    @Test
    void vulnerableNameIsTranslatedNotLeftAsTheRawKey() {
        JsonObject zh = readJson("/assets/dreamingfishcore/lang/zh_cn.json");
        assertEquals("易损", zh.get("effect.dreamingfishcore.vulnerable").getAsString());
        JsonObject en = readJson("/assets/dreamingfishcore/lang/en_us.json");
        assertEquals("Brittle", en.get("effect.dreamingfishcore.vulnerable").getAsString());
    }

    private static void assertHasKeys(String resource, String... keys) {
        JsonObject root = readJson(resource);
        for (String key : keys) {
            assertTrue(root.has(key), resource + " 缺少语言键 " + key);
            assertTrue(!root.get(key).getAsString().isBlank(), resource + " 的 " + key + " 是空串");
        }
    }

    private static BufferedImage readTexture(String resource) throws IOException {
        try (InputStream input = CharredZombieAssetTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, "missing resource: " + resource);
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image, "unreadable image: " + resource);
            return image;
        }
    }

    private static JsonObject readJson(String resource) {
        try (InputStream input = CharredZombieAssetTest.class.getResourceAsStream(resource)) {
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
}
