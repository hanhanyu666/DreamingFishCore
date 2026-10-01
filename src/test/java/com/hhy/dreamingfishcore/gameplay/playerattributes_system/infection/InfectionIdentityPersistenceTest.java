package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 感染身份「重启后保留」的落地证据。
 *
 * <p>这里走的是玩家档案真正使用的持久化通道（{@link JsonDataStore} 的原子写 + 读取），
 * 而不是内存里的 Gson 往返：字段名一旦改动，旧存档就会被读成"没有复发"，
 * 因此本测试同时把磁盘契约钉住。</p>
 */
class InfectionIdentityPersistenceTest {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final Type TYPE = new TypeToken<Map<UUID, PlayerAttributesData>>() {
    }.getType();

    @Test
    void identityAndRelapseWindowSurviveARealSaveAndReload(@TempDir Path directory) throws IOException {
        JsonDataStore.resetSession();
        Path file = directory.resolve("player_attributes.json");

        UUID survivorId = UUID.randomUUID();
        UUID unstableId = UUID.randomUUID();
        UUID relapsingId = UUID.randomUUID();

        Map<UUID, PlayerAttributesData> before = new LinkedHashMap<>();
        before.put(survivorId, new PlayerAttributesData());

        PlayerAttributesData unstable = new PlayerAttributesData();
        unstable.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_ONE);
        unstable.startInfectionTreatmentWindow(24_000L);
        before.put(unstableId, unstable);

        PlayerAttributesData relapsing = new PlayerAttributesData();
        relapsing.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_TWO);
        relapsing.setCurrentInfection(200.0F);
        relapsing.beginRelapse(9_000L);
        before.put(relapsingId, relapsing);

        JsonDataStore.writeAtomic(file, GSON, before);

        // 模拟服务器重启：清掉会话状态后重新读盘。
        JsonDataStore.resetSession();
        Map<UUID, PlayerAttributesData> after = JsonDataStore.read(file, GSON, TYPE, LinkedHashMap::new);

        assertEquals(3, after.size());
        for (PlayerAttributesData data : after.values()) {
            // 读档会跑一次归一化；合法数据不应因此被改写（否则每次重启都会产生一次无意义写盘）。
            assertFalse(data.normalizeInfectionState(), "读档归一化不应改写合法数据");
        }

        assertEquals(InfectionIdentity.SURVIVOR, after.get(survivorId).getInfectionIdentity());
        assertEquals(InfectionIdentity.UNSTABLE, after.get(unstableId).getInfectionIdentity());
        assertEquals(24_000L, after.get(unstableId).getInfectionTreatmentDeadlineActiveTick());
        assertEquals(InfectionIdentity.RELAPSE, after.get(relapsingId).getInfectionIdentity());
        assertEquals(9_000L, after.get(relapsingId).getRelapseUntilActiveTick());
        assertEquals(200.0F, after.get(relapsingId).getCurrentInfection());

        String json = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(json.contains("relapseUntilActiveTick"), "复发窗口字段名是磁盘契约");
        assertTrue(json.contains("relapseCooldownUntilActiveTick"), "复发冷却字段名是磁盘契约");
        assertTrue(json.contains("infectionTreatmentDeadlineActiveTick"), "治疗窗口字段名是磁盘契约");
    }

    @Test
    void legacySaveWithoutRelapseFieldsLoadsAsStableInfected(@TempDir Path directory) throws IOException {
        JsonDataStore.resetSession();
        Path file = directory.resolve("player_attributes.json");
        // 四态改造之前的存档：只有 isInfected 与 infectionLevel，没有任何复发字段。
        Files.writeString(file,
                "{\"" + UUID.randomUUID() + "\":{\"currentInfection\":200.0,\"isInfected\":true,\"infectionLevel\":2}}",
                StandardCharsets.UTF_8);

        Map<UUID, PlayerAttributesData> after = JsonDataStore.read(file, GSON, TYPE, LinkedHashMap::new);
        PlayerAttributesData data = after.values().iterator().next();

        assertFalse(data.normalizeInfectionState(), "旧存档缺省值就是合法状态，不需要迁移写回");
        assertEquals(InfectionIdentity.STABLE, data.getInfectionIdentity());
        assertEquals(-1L, data.getRelapseUntilActiveTick());
        assertEquals(-1L, data.getRelapseCooldownUntilActiveTick());
    }
}
