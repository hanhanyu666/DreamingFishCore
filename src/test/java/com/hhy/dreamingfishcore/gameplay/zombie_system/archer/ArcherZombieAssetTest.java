package com.hhy.dreamingfishcore.gameplay.zombie_system.archer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 射手僵尸相关资源的存在性与一致性。
 *
 * <p>这里守两类容易静默出错的坑：一是贴图缺失/尺寸不对（换原版僵尸 UV 会直接渲染错乱，但不报错）；
 * 二是 {@code sounds.json} 的事件键与 {@link ArcherZombieSounds} 里注册的 ID 不一致——原版是按
 * 「文件命名空间 + 键」拼事件 ID 的，写错只会静音，不会有任何报错。</p>
 */
class ArcherZombieAssetTest {
    /** 必须与 {@link ArcherZombieSounds} 里 register(...) 的字符串逐字一致。 */
    private static final Set<String> SOUND_EVENT_IDS = Set.of(
            "entity.archer_zombie.charge",
            "entity.archer_zombie.shoot",
            "entity.bone_spike.hit");

    @Test
    void archerZombiePlaceholderTextureUsesVanillaZombieUv() throws IOException {
        BufferedImage image = readTexture(
                "/assets/dreamingfishcore/textures/entity/archer_zombie/archer_zombie.png");
        assertEquals(64, image.getWidth());
        assertEquals(64, image.getHeight());
        assertTrue(countOpaquePixels(image) >= 1000, "占位皮肤也要能盖住原版僵尸 UV 的可见面");
    }

    @Test
    void boneSpikeTextureIsMostlyTransparentWithASolidCore() throws IOException {
        BufferedImage image = readTexture(
                "/assets/dreamingfishcore/textures/entity/projectile/bone_spike.png");
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
        int opaque = countOpaquePixels(image);
        assertTrue(opaque > 20, "骨刺纹理必须有不透明像素");
        assertTrue(opaque < 16 * 16, "骨刺纹理应当有透明背景，否则会渲染成一整块方块");
        // 中心行必须是实心的，交叉四边形才有可见的刺身。
        assertTrue((image.getRGB(8, 8) >>> 24) != 0, "骨刺中心像素不应透明");
    }

    @Test
    void bleedingEffectIconExists() throws IOException {
        BufferedImage image = readTexture(
                "/assets/dreamingfishcore/textures/mob_effect/bleeding.png");
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
        assertTrue(countOpaquePixels(image) > 20, "流血图标不能是空图，否则 HUD 上是透明格子");
    }

    @Test
    void soundsJsonKeysMatchRegisteredEventIdsAndAliasVanillaSounds() {
        JsonObject root = readJson("/assets/dreamingfishcore/sounds.json");

        // 这里只能断言「包含」而不是「相等」：sounds.json 是全模组共用的，别的功能（如 Boss 战 BGM）
        // 也会往里面加键，用相等断言会把无关的改动变成扣分项。
        assertTrue(root.keySet().containsAll(SOUND_EVENT_IDS),
                "sounds.json 缺少 ArcherZombieSounds 注册的事件键：" + SOUND_EVENT_IDS.stream()
                        .filter(id -> !root.keySet().contains(id)).toList());

        for (String eventId : SOUND_EVENT_IDS) {
            JsonObject registration = root.getAsJsonObject(eventId);
            assertTrue(registration.has("sounds"), eventId + " 缺少 sounds 数组");
            JsonArray sounds = registration.getAsJsonArray("sounds");
            assertFalse(sounds.isEmpty(), eventId + " 的 sounds 不能为空");

            for (JsonElement element : sounds) {
                JsonObject sound = element.getAsJsonObject();
                // 用 "type": "event" 复指原版音效，所以 name 必须是原版命名空间的事件 ID；
                // 若误写成 file 类型，游戏会去找并不存在的 ogg 并静音。
                assertEquals("event", sound.get("type").getAsString(),
                        eventId + " 应当以事件别名方式复用原版音效");
                assertTrue(sound.get("name").getAsString().startsWith("minecraft:"),
                        eventId + " 的别名必须指向原版事件");
            }
        }
    }

    private static BufferedImage readTexture(String resource) throws IOException {
        try (InputStream input = ArcherZombieAssetTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, "missing resource: " + resource);
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image, "unreadable image: " + resource);
            return image;
        }
    }

    private static JsonObject readJson(String resource) {
        try (InputStream input = ArcherZombieAssetTest.class.getResourceAsStream(resource)) {
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
