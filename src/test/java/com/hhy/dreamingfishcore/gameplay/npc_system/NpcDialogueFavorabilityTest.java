package com.hhy.dreamingfishcore.gameplay.npc_system;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcDialogueFavorabilityTest {
    @Test
    void firstDialogueRewardCannotBeRepeatedEvenAfterPersistence() {
        NpcRelationData relation = new NpcRelationData(101, UUID.randomUUID());

        assertTrue(relation.applyFavorabilityEffect(
                NpcManager.DIALOGUE_FAVORABILITY_EFFECT_ID, 1));
        assertFalse(relation.applyFavorabilityEffect(
                NpcManager.DIALOGUE_FAVORABILITY_EFFECT_ID, 1));
        assertEquals(1, relation.getFavorability());

        NpcRelationData restored = new Gson().fromJson(
                new Gson().toJson(relation), NpcRelationData.class);
        assertFalse(restored.applyFavorabilityEffect(
                NpcManager.DIALOGUE_FAVORABILITY_EFFECT_ID, 1));
        assertEquals(1, restored.getFavorability());
    }
}
