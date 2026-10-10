package com.hhy.dreamingfishcore.gameplay.zombie_system.boss;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 指挥官 BGM 的资源一致性。
 *
 * <p>守的是三类「写错只会静音、不会报错」的坑：事件 ID 与 {@code sounds.json} 的键对不上、
 * 键指向的 ogg 不存在（或不是真的 Ogg 容器）、以及长音轨漏掉 {@code stream} 而把整首读进内存。</p>
 */
class ZombieCommanderAssetTest {
    /** 必须与 {@link ZombieCommanderSounds} 里 register(...) 的字符串逐字一致。 */
    private static final String MUSIC_EVENT_ID = "music.zombie_commander";
    /** sounds.json 里该键应当指向的 ogg（相对 {@code assets/dreamingfishcore/sounds/}）。 */
    private static final String OGG_RESOURCE =
            "/assets/dreamingfishcore/sounds/zombie_commander_bgm.ogg";

    @Test
    void musicKeyExistsAndPointsAtTheStreamedOgg() {
        JsonObject root = readJson("/assets/dreamingfishcore/sounds.json");
        assertTrue(root.has(MUSIC_EVENT_ID),
                "sounds.json 缺少 Boss BGM 的事件键 " + MUSIC_EVENT_ID);

        JsonArray sounds = root.getAsJsonObject(MUSIC_EVENT_ID).getAsJsonArray("sounds");
        assertNotNull(sounds, MUSIC_EVENT_ID + " 缺少 sounds 数组");
        assertEquals(1, sounds.size(), "BGM 应当只有一个音源文件");

        JsonElement element = sounds.get(0);
        JsonObject sound = element.getAsJsonObject();
        assertEquals("dreamingfishcore:zombie_commander_bgm", sound.get("name").getAsString(),
                "BGM 的音源应当指向模组自己的 ogg（不是原版事件别名）");
        assertTrue(sound.has("stream") && sound.get("stream").getAsBoolean(),
                "长音轨必须标 stream，否则整首会被一次性解码进内存");
    }

    @Test
    void bgmOggExistsAndIsARealOggContainer() throws IOException {
        try (InputStream input = ZombieCommanderAssetTest.class.getResourceAsStream(OGG_RESOURCE)) {
            assertNotNull(input, "missing resource: " + OGG_RESOURCE);
            byte[] magic = input.readNBytes(4);
            assertEquals(4, magic.length, "ogg 文件不应当为空");
            // "OggS" —— 只检查文件存在还不够：改名过来的 WAV/MIDI 也会「存在」，但游戏解不了。
            assertEquals("OggS", new String(magic, StandardCharsets.US_ASCII),
                    "BGM 必须是真正的 Ogg 容器");
        }
    }

    private static JsonObject readJson(String resource) {
        try (InputStream input = ZombieCommanderAssetTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, "missing resource: " + resource);
            InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException exception) {
            throw new AssertionError("failed to read " + resource, exception);
        }
    }
}
