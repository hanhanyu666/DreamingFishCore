package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 复活结算时找不到尸体的场景：待领取意图必须能持久化到玩家数据，
 * 否则死亡记录被清除后尸体永远停在“等待复活结算”。
 */
class SettledCorpseMarkerTest {

    private static PendingDeathData.SettledCorpse sample(UUID corpseId, boolean locked) {
        return new PendingDeathData.SettledCorpse(
                corpseId, locked, "minecraft:overworld", 12.5D, 71.0D, -33.25D);
    }

    @Test
    void markerSurvivesAroundTripThroughPlayerPersistentData() {
        UUID corpseId = UUID.randomUUID();
        CompoundTag persistentData = new CompoundTag();

        PendingDeathData.SettledCorpse.write(persistentData, sample(corpseId, false));

        Optional<PendingDeathData.SettledCorpse> restored = PendingDeathData.SettledCorpse.read(persistentData);
        assertTrue(restored.isPresent());
        assertEquals(corpseId, restored.get().corpseId());
        assertFalse(restored.get().locked());
        assertEquals("minecraft:overworld", restored.get().dimension());
        assertEquals(12.5D, restored.get().x());
        assertEquals(71.0D, restored.get().y());
        assertEquals(-33.25D, restored.get().z());
    }

    @Test
    void missingLocationStillKeepsTheCorpseReference() {
        UUID corpseId = UUID.randomUUID();
        CompoundTag persistentData = new CompoundTag();

        PendingDeathData.SettledCorpse.write(
                persistentData, new PendingDeathData.SettledCorpse(corpseId, true, "", 0, 0, 0));

        Optional<PendingDeathData.SettledCorpse> restored = PendingDeathData.SettledCorpse.read(persistentData);
        assertTrue(restored.isPresent());
        assertEquals(corpseId, restored.get().corpseId());
        assertTrue(restored.get().locked());
        assertEquals("", restored.get().dimension());
    }

    @Test
    void clearOnlyRemovesTheMarkerOfTheSameCorpse() {
        UUID settledCorpse = UUID.randomUUID();
        CompoundTag persistentData = new CompoundTag();
        PendingDeathData.SettledCorpse.write(persistentData, sample(settledCorpse, true));

        assertFalse(PendingDeathData.SettledCorpse.clear(persistentData, UUID.randomUUID()));
        assertTrue(PendingDeathData.SettledCorpse.read(persistentData).isPresent());

        assertTrue(PendingDeathData.SettledCorpse.clear(persistentData, settledCorpse));
        assertTrue(PendingDeathData.SettledCorpse.read(persistentData).isEmpty());
    }

    @Test
    void emptyOrCorruptDataNeverYieldsAMarker() {
        assertTrue(PendingDeathData.SettledCorpse.read(new CompoundTag()).isEmpty());
        assertTrue(PendingDeathData.SettledCorpse.read(null).isEmpty());

        CompoundTag corrupt = new CompoundTag();
        corrupt.putString("DreamingFishCore_SettledCorpse", "not-a-compound");
        assertTrue(PendingDeathData.SettledCorpse.read(corrupt).isEmpty());

        CompoundTag missingId = new CompoundTag();
        missingId.put("DreamingFishCore_SettledCorpse", new CompoundTag());
        assertTrue(PendingDeathData.SettledCorpse.read(missingId).isEmpty());
        assertFalse(PendingDeathData.SettledCorpse.clear(missingId, UUID.randomUUID()));
    }

    @Test
    void laterSettlementReplacesThePreviousMarker() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        CompoundTag persistentData = new CompoundTag();

        PendingDeathData.SettledCorpse.write(persistentData, sample(first, true));
        PendingDeathData.SettledCorpse.write(persistentData, sample(second, false));

        Optional<PendingDeathData.SettledCorpse> restored = PendingDeathData.SettledCorpse.read(persistentData);
        assertTrue(restored.isPresent());
        assertEquals(second, restored.get().corpseId());
        assertFalse(PendingDeathData.SettledCorpse.clear(persistentData, first));
        assertEquals(second, PendingDeathData.SettledCorpse.read(persistentData).orElseThrow().corpseId());
    }
}
